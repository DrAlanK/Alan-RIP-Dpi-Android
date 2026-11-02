package com.alan.ripdpi.core

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.alan.ripdpi.model.BypassVector
import java.io.EOFException
import java.io.FileDescriptor
import kotlin.random.Random

data class SniOffsets(
    val extTotalLenPos: Int,
    val sniExtPos: Int,
    val sniListLenPos: Int,
    val sniNameLenPos: Int,
    val sniNamePos: Int,
    val sniNameLen: Int
)

object TlsByteMutator {

    private val HTTP_METHODS = arrayOf(
        "GET ".encodeToByteArray(),
        "POST ".encodeToByteArray(),
        "HEAD ".encodeToByteArray(),
        "PUT ".encodeToByteArray(),
        "OPTIONS ".encodeToByteArray()
    )

    fun readUint16BE(buf: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 1 >= buf.size) return 0
        return ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
    }

    fun isTlsClientHello(buf: ByteArray): Boolean {
        return buf.size >= 6 &&
            (buf[0].toInt() and 0xFF) == 0x16 &&
            (buf[5].toInt() and 0xFF) == 0x01
    }

    fun isPlainHttpRequest(buf: ByteArray): Boolean {
        if (buf.size < 16) return false
        for (prefix in HTTP_METHODS) {
            if (buf.size >= prefix.size) {
                var matched = true
                for (i in prefix.indices) {
                    if (buf[i] != prefix[i]) {
                        matched = false
                        break
                    }
                }
                if (matched) return true
            }
        }
        return false
    }

    fun locateSniOffsets(record: ByteArray): SniOffsets {
        val recLen = record.size
        var extTotalLenPos = 80
        var sniExtPos = (recLen / 3).coerceIn(5, 180)
        var sniListLenPos = ((recLen / 3) + 4).coerceIn(9, 184)
        var sniNameLenPos = ((recLen / 3) + 7).coerceIn(12, 187)
        var sniNamePos = ((recLen / 3) + 9).coerceIn(14, 189)
        var sniNameLen = 16

        if (recLen >= 44 && (record[0].toInt() and 0xFF) == 0x16 && (record[5].toInt() and 0xFF) == 0x01) {
            var pos = 43
            if (pos < recLen) {
                val sidLen = record[pos].toInt() and 0xFF
                pos += 1 + sidLen
                if (pos + 2 <= recLen) {
                    val ciphLen = readUint16BE(record, pos)
                    pos += 2 + ciphLen
                    if (pos < recLen) {
                        val compLen = record[pos].toInt() and 0xFF
                        pos += 1 + compLen
                        if (pos + 2 <= recLen) {
                            extTotalLenPos = pos
                            val extTotalLen = readUint16BE(record, pos)
                            pos += 2
                            val end = minOf(recLen, pos + extTotalLen)
                            while (pos + 4 <= end) {
                                val etype = readUint16BE(record, pos)
                                val elen = readUint16BE(record, pos + 2)
                                if (etype == 0x0000 && pos + 9 <= end) {
                                    sniExtPos = pos
                                    sniListLenPos = pos + 4
                                    sniNameLenPos = pos + 7
                                    sniNameLen = readUint16BE(record, pos + 7)
                                    sniNamePos = pos + 9
                                    break
                                } else {
                                    pos += 4 + elen
                                }
                            }
                        }
                    }
                }
            }
        }

        return SniOffsets(
            extTotalLenPos = extTotalLenPos,
            sniExtPos = sniExtPos,
            sniListLenPos = sniListLenPos,
            sniNameLenPos = sniNameLenPos,
            sniNamePos = sniNamePos,
            sniNameLen = sniNameLen
        )
    }

    fun rewriteOuterVersion(record: ByteArray, version: Int): ByteArray {
        val copy = record.copyOf()
        val safeVer = if (version in 0x0301..0x0303) version else 0
        if (copy.size >= 5 && copy[0] == 0x16.toByte() && safeVer > 0) {
            copy[1] = ((safeVer shr 8) and 0xFF).toByte()
            copy[2] = (safeVer and 0xFF).toByte()
        }
        return copy
    }

    private fun wrapTlsHandshakeRecord(verHi: Byte, verLo: Byte, body: ByteArray): ByteArray {
        val out = ByteArray(5 + body.size)
        out[0] = 0x16.toByte()
        out[1] = verHi
        out[2] = verLo
        out[3] = ((body.size shr 8) and 0xFF).toByte()
        out[4] = (body.size and 0xFF).toByte()
        System.arraycopy(body, 0, out, 5, body.size)
        return out
    }

    private fun pulseNoDelay(fd: FileDescriptor) {
        try {
            Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, OsConstants.TCP_NODELAY, 1)
        } catch (_: Throwable) {
        }
    }

    fun writeAllFd(fd: FileDescriptor, buf: ByteArray, offset: Int = 0, length: Int = buf.size - offset) {
        if (length <= 0) return
        var pos = offset
        val end = offset + length
        while (pos < end) {
            val written = Os.write(fd, buf, pos, end - pos)
            if (written <= 0) {
                throw EOFException("Remote fd write closed")
            }
            pos += written
        }
        pulseNoDelay(fd)
    }

    fun safeSendUrgent(
        remoteFd: FileDescriptor,
        chunk: ByteArray,
        urgentByte: Byte
    ) {
        if (chunk.isEmpty()) return
        val combined = ByteArray(chunk.size + 1)
        System.arraycopy(chunk, 0, combined, 0, chunk.size)
        combined[chunk.size] = urgentByte

        if (remoteFd.valid()) {
            try {
                val sent = Os.sendto(remoteFd, combined, 0, combined.size, OsConstants.MSG_OOB, null, 0)
                if (sent == combined.size) {
                    pulseNoDelay(remoteFd)
                    return
                }
                if (sent in 1 until chunk.size) {
                    writeAllFd(remoteFd, chunk, sent, chunk.size - sent)
                    return
                }
                if (sent == chunk.size) {
                    return
                }
            } catch (_: ErrnoException) {
            } catch (_: Throwable) {
            }
        }

        writeAllFd(remoteFd, chunk, 0, chunk.size)
    }

    fun transmitFragmentedBuffer(
        remoteFd: FileDescriptor,
        buf: ByteArray,
        sliceMin: Int,
        sliceMax: Int,
        delayMinMs: Int,
        delayMaxMs: Int
    ) {
        if (buf.isEmpty()) return
        val minS = maxOf(1, minOf(sliceMin, sliceMax))
        val maxS = maxOf(minS, sliceMax)
        val minD = maxOf(0, minOf(delayMinMs, delayMaxMs))
        val maxD = maxOf(minD, delayMaxMs)

        var offset = 0
        while (offset < buf.size) {
            val step = if (maxS > minS) Random.nextInt(minS, maxS + 1) else minS
            val end = minOf(buf.size, offset + step)
            writeAllFd(remoteFd, buf, offset, end - offset)
            offset = end
            if (offset < buf.size && maxD > 0) {
                val pause = if (maxD > minD) Random.nextInt(minD, maxD + 1) else minD
                if (pause > 0) {
                    sleepQuietly(pause.toLong())
                }
            }
        }
    }

    fun transmitTailSegments(
        remoteFd: FileDescriptor,
        tail: ByteArray,
        vector: BypassVector,
        customStride: Int,
        jitterBytes: Int
    ) {
        if (tail.isEmpty()) return

        val effectiveCuts: List<Int> = if (customStride > 0 && vector.forwardCuts.size == 1) {
            listOf(customStride)
        } else {
            vector.forwardCuts
        }

        if (effectiveCuts.isEmpty()) {
            writeAllFd(remoteFd, tail)
            return
        }

        var cursor = 0
        val tailLen = tail.size
        for (idx in effectiveCuts.indices) {
            val baseDelta = effectiveCuts[idx]
            val jitter = if (baseDelta > 60 && jitterBytes > 0) {
                Random.nextInt(-jitterBytes, jitterBytes + 1)
            } else {
                0
            }
            val desiredAbs = maxOf(cursor + 8, baseDelta + jitter)
            if (desiredAbs < tailLen) {
                writeAllFd(remoteFd, tail, cursor, desiredAbs - cursor)
                cursor = desiredAbs
                sleepQuietly(4L)
            } else if (idx == 0 && cursor == 0 && tailLen > 48) {
                val fallbackCut = ((tailLen * 6) / 10).coerceIn(1, tailLen - 1)
                writeAllFd(remoteFd, tail, 0, fallbackCut)
                cursor = fallbackCut
                sleepQuietly(4L)
                break
            } else {
                break
            }
        }

        if (cursor < tailLen) {
            writeAllFd(remoteFd, tail, cursor, tailLen - cursor)
        }
    }

    fun transmitClientHello(
        remoteFd: FileDescriptor,
        chRecord: ByteArray,
        vector: BypassVector,
        customStride: Int,
        jitterBytes: Int
    ) {
        if (vector.family.equals("DIRECT", ignoreCase = true) || vector.vectorId == "VEC-00" || chRecord.size < 16) {
            writeAllFd(remoteFd, chRecord)
            return
        }

        val offsets = locateSniOffsets(chRecord)
        val mutCh = if (vector.outerVersion in 0x0301..0x0303) {
            rewriteOuterVersion(chRecord, vector.outerVersion)
        } else {
            chRecord
        }

        val midSniAbs = (offsets.sniNamePos + (offsets.sniNameLen / 2).coerceAtLeast(2))
            .coerceIn(6, maxOf(6, mutCh.size - 2))

        when (vector.splitMode.lowercase()) {
            "tcp_sni_mid" -> {
                writeAllFd(remoteFd, mutCh, 0, midSniAbs)
                sleepQuietly(6L)
                val rem = mutCh.copyOfRange(midSniAbs, mutCh.size)
                transmitTailSegments(remoteFd, rem, vector, customStride, jitterBytes)
                return
            }

            "tcp_sni_micro" -> {
                val sniStart = offsets.sniExtPos.coerceIn(5, mutCh.size - 4)
                val sniEnd = (offsets.sniNamePos + offsets.sniNameLen + 2).coerceIn(sniStart + 2, mutCh.size)
                writeAllFd(remoteFd, mutCh, 0, sniStart)
                sleepQuietly(3L)
                var cur = sniStart
                while (cur < sniEnd) {
                    val step = minOf(2, sniEnd - cur)
                    writeAllFd(remoteFd, mutCh, cur, step)
                    cur += step
                    if (cur < sniEnd) sleepQuietly(3L)
                }
                if (sniEnd < mutCh.size) {
                    sleepQuietly(3L)
                    writeAllFd(remoteFd, mutCh, sniEnd, mutCh.size - sniEnd)
                }
                return
            }

            "tcp_sni_dots" -> {
                val hostStart = offsets.sniNamePos.coerceIn(5, mutCh.size - 2)
                val hostEnd = (offsets.sniNamePos + offsets.sniNameLen).coerceIn(hostStart + 1, mutCh.size)
                val cutPoints = ArrayList<Int>(6)
                for (i in hostStart until hostEnd) {
                    if (mutCh[i] == '.'.code.toByte() && i + 1 < mutCh.size) {
                        cutPoints.add(i + 1)
                    }
                }
                if (cutPoints.isEmpty()) {
                    cutPoints.add(midSniAbs)
                }
                var prev = 0
                for (cp in cutPoints) {
                    if (cp > prev && cp < mutCh.size) {
                        writeAllFd(remoteFd, mutCh, prev, cp - prev)
                        prev = cp
                        sleepQuietly(5L)
                    }
                }
                if (prev < mutCh.size) {
                    writeAllFd(remoteFd, mutCh, prev, mutCh.size - prev)
                }
                return
            }

            "tcp_sni_1b" -> {
                val c1 = (offsets.sniNamePos + 1).coerceIn(6, mutCh.size - 3)
                val c2 = (offsets.sniNamePos + 4).coerceIn(c1 + 1, mutCh.size - 1)
                writeAllFd(remoteFd, mutCh, 0, c1)
                sleepQuietly(6L)
                writeAllFd(remoteFd, mutCh, c1, c2 - c1)
                sleepQuietly(6L)
                writeAllFd(remoteFd, mutCh, c2, mutCh.size - c2)
                return
            }

            "rec_sni_mid", "1pkt_midsni" -> {
                val verHi = mutCh[1]
                val verLo = mutCh[2]
                val hs = mutCh.copyOfRange(5, mutCh.size)
                val cutRel = (midSniAbs - 5).coerceIn(1, maxOf(1, hs.size - 1))
                val rec1 = wrapTlsHandshakeRecord(verHi, verLo, hs.copyOfRange(0, cutRel))
                val rec2 = wrapTlsHandshakeRecord(verHi, verLo, hs.copyOfRange(cutRel, hs.size))
                writeAllFd(remoteFd, rec1)
                sleepQuietly(5L)
                transmitTailSegments(remoteFd, rec2, vector, customStride, jitterBytes)
                return
            }

            "rec_sni_tri" -> {
                val verHi = mutCh[1]
                val verLo = mutCh[2]
                val hs = mutCh.copyOfRange(5, mutCh.size)
                val c1Rel = (offsets.sniExtPos + 4 - 5).coerceIn(1, maxOf(1, hs.size - 3))
                val c2Rel = (midSniAbs - 5).coerceIn(c1Rel + 1, maxOf(c1Rel + 1, hs.size - 1))
                val rec1 = wrapTlsHandshakeRecord(verHi, verLo, hs.copyOfRange(0, c1Rel))
                val rec2 = wrapTlsHandshakeRecord(verHi, verLo, hs.copyOfRange(c1Rel, c2Rel))
                val rec3 = wrapTlsHandshakeRecord(verHi, verLo, hs.copyOfRange(c2Rel, hs.size))
                writeAllFd(remoteFd, rec1)
                sleepQuietly(4L)
                writeAllFd(remoteFd, rec2)
                sleepQuietly(4L)
                writeAllFd(remoteFd, rec3)
                return
            }

            "rec_sni_frag" -> {
                val verHi = mutCh[1]
                val verLo = mutCh[2]
                val hs = mutCh.copyOfRange(5, mutCh.size)
                val cutRel = (midSniAbs - 5).coerceIn(1, maxOf(1, hs.size - 1))
                val rec1 = wrapTlsHandshakeRecord(verHi, verLo, hs.copyOfRange(0, cutRel))
                val rec2 = wrapTlsHandshakeRecord(verHi, verLo, hs.copyOfRange(cutRel, hs.size))
                val combined = ByteArray(rec1.size + rec2.size)
                System.arraycopy(rec1, 0, combined, 0, rec1.size)
                System.arraycopy(rec2, 0, combined, rec1.size, rec2.size)
                transmitFragmentedBuffer(
                    remoteFd = remoteFd,
                    buf = combined,
                    sliceMin = if (vector.fragSliceMin > 0) vector.fragSliceMin else 10,
                    sliceMax = if (vector.fragSliceMax > 0) vector.fragSliceMax else 24,
                    delayMinMs = if (vector.fragDelayMinMs > 0) vector.fragDelayMinMs else 2,
                    delayMaxMs = if (vector.fragDelayMaxMs > 0) vector.fragDelayMaxMs else 4
                )
                return
            }
        }

        if (vector.family.equals("FRAGMENT", ignoreCase = true) && vector.fragSliceMax > 0) {
            transmitFragmentedBuffer(
                remoteFd = remoteFd,
                buf = mutCh,
                sliceMin = vector.fragSliceMin,
                sliceMax = vector.fragSliceMax,
                delayMinMs = vector.fragDelayMinMs,
                delayMaxMs = vector.fragDelayMaxMs
            )
            return
        }

        when (vector.urgentMode.lowercase()) {
            "sni_tag" -> {
                val cut = minOf(mutCh.size - 2, offsets.sniExtPos + 1).coerceAtLeast(1)
                safeSendUrgent(remoteFd, mutCh.copyOfRange(0, cut), 0x78.toByte())
                sleepQuietly(3L)
                transmitTailSegments(remoteFd, mutCh.copyOfRange(cut, mutCh.size), vector, customStride, jitterBytes)
            }
            "mid_host" -> {
                safeSendUrgent(remoteFd, mutCh.copyOfRange(0, midSniAbs), 0x2D.toByte())
                sleepQuietly(3L)
                transmitTailSegments(remoteFd, mutCh.copyOfRange(midSniAbs, mutCh.size), vector, customStride, jitterBytes)
            }
            "dual" -> {
                val c1 = (offsets.sniExtPos + 1).coerceIn(1, mutCh.size - 3)
                val c2 = midSniAbs.coerceAtLeast(c1 + 1)
                safeSendUrgent(remoteFd, mutCh.copyOfRange(0, c1), 0x78.toByte())
                sleepQuietly(2L)
                safeSendUrgent(remoteFd, mutCh.copyOfRange(c1, c2), 0x2D.toByte())
                sleepQuietly(2L)
                transmitTailSegments(remoteFd, mutCh.copyOfRange(c2, mutCh.size), vector, customStride, jitterBytes)
            }
            else -> {
                transmitTailSegments(remoteFd, mutCh, vector, customStride, jitterBytes)
            }
        }
    }

    fun transmitPlainHttp(
        remoteFd: FileDescriptor,
        httpBuf: ByteArray,
        vector: BypassVector,
        customStride: Int,
        jitterBytes: Int
    ) {
        if (vector.family.equals("DIRECT", ignoreCase = true) || vector.vectorId == "VEC-00" || httpBuf.size < 16) {
            writeAllFd(remoteFd, httpBuf)
            return
        }

        val asciiLower = String(httpBuf, Charsets.ISO_8859_1).lowercase()
        val markerIdx = asciiLower.indexOf("\r\nhost:")
        if (markerIdx < 0) {
            writeAllFd(remoteFd, httpBuf)
            return
        }

        var domainStart = markerIdx + 7
        while (
            domainStart < httpBuf.size &&
            (httpBuf[domainStart] == ' '.code.toByte() || httpBuf[domainStart] == '\t'.code.toByte())
        ) {
            domainStart++
        }
        val c1 = (domainStart + 2).coerceIn(1, httpBuf.size - 3)
        val c2 = (domainStart + 5).coerceIn(c1 + 1, httpBuf.size - 1)

        if (!vector.urgentMode.equals("none", ignoreCase = true)) {
            safeSendUrgent(remoteFd, httpBuf.copyOfRange(0, c1), 0x78.toByte())
            sleepQuietly(3L)
            transmitTailSegments(remoteFd, httpBuf.copyOfRange(c1, httpBuf.size), vector, customStride, jitterBytes)
        } else {
            writeAllFd(remoteFd, httpBuf, 0, c1)
            sleepQuietly(4L)
            writeAllFd(remoteFd, httpBuf, c1, c2 - c1)
            sleepQuietly(4L)
            writeAllFd(remoteFd, httpBuf, c2, httpBuf.size - c2)
        }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
