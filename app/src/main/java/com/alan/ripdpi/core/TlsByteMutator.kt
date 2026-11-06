package com.alan.ripdpi.core

import com.alan.ripdpi.model.BypassVector
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import java.util.Locale
import kotlin.random.Random

object TlsByteMutator {

    data class ClientHelloInfo(
        val isValidCh: Boolean,
        val recordLen: Int,
        val cipherSuitesPos: Int,
        val cipherSuitesLen: Int,
        val extTotalLenPos: Int,
        val sniExtPos: Int,
        val sniListLenPos: Int,
        val sniNameLenPos: Int,
        val sniNamePos: Int,
        val sniNameLen: Int,
        val sniFirstDotPos: Int,
        val sniLastDotPos: Int,
        val alpnExtPos: Int,
        val supVerExtPos: Int,
        val keyShareExtPos: Int
    )

    private val POLY_OOB_POOL: ByteArray = byteArrayOf(
        0x21, 0x23, 0x24, 0x25, 0x26, 0x2A, 0x2B, 0x2D, 0x2E, 0x33, 0x3B, 0x3D, 0x3F,
        0x40, 0x42, 0x47, 0x4B, 0x4E, 0x51, 0x57, 0x5A, 0x5E, 0x61, 0x6D, 0x71, 0x78, 0x7A, 0x7E
    )

    private val PREAMBLE_2X_ZERO = byteArrayOf(
        0x16, 0x03, 0x01, 0x00, 0x00,
        0x16, 0x03, 0x04, 0x00, 0x00
    )

    private val PREAMBLE_4X_ZERO = byteArrayOf(
        0x16, 0x03, 0x01, 0x00, 0x00,
        0x16, 0x03, 0x02, 0x00, 0x00,
        0x16, 0x03, 0x03, 0x00, 0x00,
        0x16, 0x03, 0x04, 0x00, 0x00
    )

    private val FIB_ALT_VERSIONS = intArrayOf(
        0x0301, 0x0303, 0x0303, 0x0301, 0x0303, 0x0303, 0x0301, 0x0303
    )

    private val CHAMELEON_ALT_VERSIONS = intArrayOf(
        0x0301, 0x0303, 0x0301, 0x0303, 0x0303
    )

    private fun polyOobByte(): Int =
        POLY_OOB_POOL[Random.nextInt(POLY_OOB_POOL.size)].toInt() and 0xFF

    private fun polyRecordVersion(baseVer: Int): Int {
        return if (baseVer == 0x0304) {
            when (Random.nextInt(3)) {
                0 -> 0x0301
                1 -> 0x0303
                else -> 0x0304
            }
        } else {
            if (Random.nextBoolean()) 0x0301 else 0x0303
        }
    }

    private fun u8(buf: ByteArray, idx: Int): Int = buf[idx].toInt() and 0xFF

    private fun u16(buf: ByteArray, idx: Int): Int =
        ((buf[idx].toInt() and 0xFF) shl 8) or (buf[idx + 1].toInt() and 0xFF)

    private fun packTlsRecord(
        contentType: Int,
        version: Int,
        body: ByteArray,
        offset: Int = 0,
        len: Int = body.size
    ): ByteArray {
        val out = ByteArray(5 + len)
        out[0] = (contentType and 0xFF).toByte()
        out[1] = ((version ushr 8) and 0xFF).toByte()
        out[2] = (version and 0xFF).toByte()
        out[3] = ((len ushr 8) and 0xFF).toByte()
        out[4] = (len and 0xFF).toByte()
        if (len > 0) {
            System.arraycopy(body, offset, out, 5, len)
        }
        return out
    }

    fun inspectClientHello(record: ByteArray): ClientHelloInfo {
        val recLen = record.size
        val midGuess = (recLen / 3).coerceIn(14, 180)
        var isValidCh = false
        var cipherSuitesPos = 43
        var cipherSuitesLen = 32
        var extTotalLenPos = (midGuess - 12).coerceIn(48, 120)
        var sniExtPos = midGuess
        var sniListLenPos = midGuess + 4
        var sniNameLenPos = midGuess + 7
        var sniNamePos = midGuess + 9
        var sniNameLen = 20
        var sniFirstDotPos = midGuess + 15
        var sniLastDotPos = midGuess + 21
        var alpnExtPos = -1
        var supVerExtPos = -1
        var keyShareExtPos = -1

        if (recLen < 48 || u8(record, 0) != 0x16 || u8(record, 5) != 0x01) {
            return ClientHelloInfo(
                isValidCh, recLen, cipherSuitesPos, cipherSuitesLen, extTotalLenPos,
                sniExtPos, sniListLenPos, sniNameLenPos, sniNamePos, sniNameLen,
                sniFirstDotPos, sniLastDotPos, alpnExtPos, supVerExtPos, keyShareExtPos
            )
        }

        try {
            var pos = 43
            if (pos >= recLen) {
                return ClientHelloInfo(
                    false, recLen, cipherSuitesPos, cipherSuitesLen, extTotalLenPos,
                    sniExtPos, sniListLenPos, sniNameLenPos, sniNamePos, sniNameLen,
                    sniFirstDotPos, sniLastDotPos, alpnExtPos, supVerExtPos, keyShareExtPos
                )
            }
            val sidLen = u8(record, pos)
            pos += 1 + sidLen
            if (pos + 2 > recLen) {
                return ClientHelloInfo(
                    false, recLen, cipherSuitesPos, cipherSuitesLen, extTotalLenPos,
                    sniExtPos, sniListLenPos, sniNameLenPos, sniNamePos, sniNameLen,
                    sniFirstDotPos, sniLastDotPos, alpnExtPos, supVerExtPos, keyShareExtPos
                )
            }

            cipherSuitesPos = pos
            cipherSuitesLen = u16(record, pos)
            pos += 2 + cipherSuitesLen
            if (pos + 1 > recLen) {
                return ClientHelloInfo(
                    false, recLen, cipherSuitesPos, cipherSuitesLen, extTotalLenPos,
                    sniExtPos, sniListLenPos, sniNameLenPos, sniNamePos, sniNameLen,
                    sniFirstDotPos, sniLastDotPos, alpnExtPos, supVerExtPos, keyShareExtPos
                )
            }

            val compLen = u8(record, pos)
            pos += 1 + compLen
            if (pos + 2 > recLen) {
                return ClientHelloInfo(
                    false, recLen, cipherSuitesPos, cipherSuitesLen, extTotalLenPos,
                    sniExtPos, sniListLenPos, sniNameLenPos, sniNamePos, sniNameLen,
                    sniFirstDotPos, sniLastDotPos, alpnExtPos, supVerExtPos, keyShareExtPos
                )
            }

            extTotalLenPos = pos
            val extTotalLen = u16(record, pos)
            pos += 2
            val end = minOf(recLen, pos + extTotalLen)
            isValidCh = true

            while (pos + 4 <= end) {
                val etype = u16(record, pos)
                val elen = u16(record, pos + 2)
                val extBodyEnd = pos + 4 + elen
                if (extBodyEnd > end) break

                when (etype) {
                    0x0000 -> {
                        if (pos + 9 <= extBodyEnd) {
                            val nameLen = u16(record, pos + 7)
                            val nameStart = pos + 9
                            val nameEnd = minOf(extBodyEnd, nameStart + nameLen)
                            sniExtPos = pos
                            sniListLenPos = pos + 4
                            sniNameLenPos = pos + 7
                            sniNamePos = nameStart
                            sniNameLen = maxOf(1, nameEnd - nameStart)

                            var firstDot = -1
                            var lastDot = -1
                            for (i in nameStart until nameEnd) {
                                if (record[i] == '.'.code.toByte()) {
                                    if (firstDot == -1) firstDot = i - nameStart
                                    lastDot = i - nameStart
                                }
                            }
                            sniFirstDotPos = if (firstDot > 0) {
                                nameStart + firstDot
                            } else {
                                nameStart + maxOf(1, sniNameLen / 2)
                            }
                            sniLastDotPos = if (lastDot > 0) {
                                nameStart + lastDot
                            } else {
                                sniFirstDotPos
                            }
                        }
                    }
                    0x0010 -> alpnExtPos = pos
                    0x002B -> supVerExtPos = pos
                    0x0033 -> keyShareExtPos = pos
                }
                pos = extBodyEnd
            }
        } catch (_: Throwable) {
        }

        return ClientHelloInfo(
            isValidCh, recLen, cipherSuitesPos, cipherSuitesLen, extTotalLenPos,
            sniExtPos, sniListLenPos, sniNameLenPos, sniNamePos, sniNameLen,
            sniFirstDotPos, sniLastDotPos, alpnExtPos, supVerExtPos, keyShareExtPos
        )
    }

    fun rewriteOuterVersion(record: ByteArray, version: Int): ByteArray {
        if (record.size < 5 || u8(record, 0) != 0x16) return record
        val copy = record.copyOf()
        copy[1] = ((version ushr 8) and 0xFF).toByte()
        copy[2] = (version and 0xFF).toByte()
        return copy
    }

    fun buildPreambleBytes(preambleMode: String): ByteArray {
        return when (preambleMode) {
            "zero0304" -> {
                val verLo: Byte = when (Random.nextInt(3)) {
                    0 -> 0x01
                    1 -> 0x03
                    else -> 0x04
                }
                byteArrayOf(0x16, 0x03, verLo, 0x00, 0x00)
            }
            "2xzero" -> PREAMBLE_2X_ZERO.copyOf()
            "4xzero" -> PREAMBLE_4X_ZERO.copyOf()
            else -> ByteArray(0)
        }
    }

    fun splitRecordAtomic(record: ByteArray, cutOffset: Int, version: Int): ByteArray {
        if (record.size < 10 || cutOffset <= 5 || cutOffset >= record.size) {
            return rewriteOuterVersion(record, version)
        }
        val hsLen = record.size - 5
        val cutRel = cutOffset - 5
        val r1 = packTlsRecord(0x16, 0x0301, record, 5, cutRel)
        val r2 = packTlsRecord(0x16, version, record, 5 + cutRel, hsLen - cutRel)
        return r1 + r2
    }

    fun reframeHandshakeChunks(
        hsBody: ByteArray,
        chunkSizes: List<Int>,
        version: Int,
        alternatingVersions: IntArray = intArrayOf()
    ): List<ByteArray> {
        val frames = ArrayList<ByteArray>(chunkSizes.size + 1)
        var cursor = 0
        val total = hsBody.size
        var idx = 0
        for (sz in chunkSizes) {
            if (cursor >= total) break
            val take = minOf(maxOf(1, sz), total - cursor)
            val recVer = if (alternatingVersions.isNotEmpty()) {
                alternatingVersions[idx % alternatingVersions.size]
            } else {
                version
            }
            frames.add(packTlsRecord(0x16, recVer, hsBody, cursor, take))
            cursor += take
            idx++
        }
        if (cursor < total) {
            val recVer = if (alternatingVersions.isNotEmpty()) {
                alternatingVersions[idx % alternatingVersions.size]
            } else {
                version
            }
            frames.add(packTlsRecord(0x16, recVer, hsBody, cursor, total - cursor))
        }
        return frames
    }

    private fun joinByteArrays(parts: List<ByteArray>): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArrayOutputStream(total)
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    fun safeSendUrgent(
        sock: Socket,
        out: OutputStream,
        nativeFd: Int,
        chunk: ByteArray,
        offset: Int,
        len: Int,
        explicitUrgentByte: Int = -1
    ): Int {
        if (len <= 0) return 0
        val decoy = if (explicitUrgentByte >= 0) (explicitUrgentByte and 0xFF) else polyOobByte()
        if (nativeFd >= 0) {
            val sentNative = AlanNativeSocket.sendOob(nativeFd, chunk, offset, len, decoy.toByte())
            if (sentNative > 0) {
                return sentNative
            }
        }
        out.write(chunk, offset, len)
        out.flush()
        return try {
            sock.sendUrgentData(decoy)
            len + 1
        } catch (_: Throwable) {
            len
        }
    }

    fun transmitUplinkShieldFrame(
        sock: Socket,
        out: OutputStream,
        nativeFd: Int,
        payload: ByteArray,
        payloadLen: Int = payload.size,
        injectOob: Boolean = false,
        mssClamp: Int = 0
    ): Int {
        if (payloadLen <= 0) return 0
        var totalSent = 0
        var startIdx = 0

        if (injectOob && payloadLen > 20 && (payload[0].toInt() and 0xFF) == 0x17 && (payload[1].toInt() and 0xFF) == 0x03) {
            totalSent += safeSendUrgent(sock, out, nativeFd, payload, 0, 3)
            startIdx = 3
        }

        val remLen = payloadLen - startIdx
        if (remLen <= 0) return totalSent

        if (mssClamp > 0 && remLen > mssClamp) {
            val jitMss = maxOf(512, mssClamp + Random.nextInt(-24, 25))
            var cursor = startIdx
            while (cursor < payloadLen) {
                val take = minOf(jitMss, payloadLen - cursor)
                out.write(payload, cursor, take)
                out.flush()
                totalSent += take
                cursor += take
            }
        } else {
            out.write(payload, startIdx, remLen)
            out.flush()
            totalSent += remLen
        }
        return totalSent
    }

    fun transmitFragmentedBuffer(
        out: OutputStream,
        payload: ByteArray,
        payloadLen: Int = payload.size,
        minLen: Int,
        maxLen: Int,
        minDelayMs: Int,
        maxDelayMs: Int
    ): Int {
        if (payloadLen <= 0) return 0
        var cursor = 0
        val lo = maxOf(1, minLen)
        val hi = maxOf(lo, maxLen)
        while (cursor < payloadLen) {
            val step = if (hi > lo) Random.nextInt(lo, hi + 1) else lo
            val take = minOf(step, payloadLen - cursor)
            out.write(payload, cursor, take)
            out.flush()
            cursor += take
            if (cursor < payloadLen && maxDelayMs > 0) {
                val delayMs = if (maxDelayMs > minDelayMs) {
                    Random.nextInt(maxOf(0, minDelayMs), maxDelayMs + 1).toLong()
                } else {
                    minDelayMs.toLong()
                }
                if (delayMs > 0L) {
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
        return payloadLen
    }

    private fun transmitTailSegments(
        sock: Socket,
        out: OutputStream,
        nativeFd: Int,
        tailBuf: ByteArray,
        offset: Int,
        tailLen: Int,
        forwardCuts: List<Int>,
        jitterBytes: Int = 20,
        secondUrgentDelta: Int = 0
    ): Int {
        if (tailLen <= 0) return 0

        if (secondUrgentDelta > 0) {
            val jitter = if (jitterBytes > 0) Random.nextInt(-jitterBytes, jitterBytes + 1) else 0
            val targetDelta = maxOf(24, secondUrgentDelta + jitter)
            val cutPos = if (targetDelta < tailLen) targetDelta else maxOf(1, (tailLen * 2) / 3)
            if (cutPos in 1 until tailLen) {
                val sentU = safeSendUrgent(sock, out, nativeFd, tailBuf, offset, cutPos)
                try { Thread.sleep(1L) } catch (_: InterruptedException) {}
                out.write(tailBuf, offset + cutPos, tailLen - cutPos)
                out.flush()
                return sentU + (tailLen - cutPos)
            }
        }

        if (forwardCuts.isEmpty()) {
            out.write(tailBuf, offset, tailLen)
            out.flush()
            return tailLen
        }

        val resolvedCuts = sortedSetOf(0, tailLen)
        for (baseDelta in forwardCuts) {
            val useJitter = baseDelta > 60 && jitterBytes > 0
            val jitter = if (useJitter) Random.nextInt(-jitterBytes, jitterBytes + 1) else 0
            val candidate = maxOf(20, baseDelta + jitter)
            when {
                candidate in 1 until tailLen -> resolvedCuts.add(candidate)
                baseDelta in 1 until tailLen -> resolvedCuts.add(baseDelta)
                tailLen > 48 -> resolvedCuts.add((tailLen * 7) / 10)
            }
        }

        val points = resolvedCuts.toList()
        var sentBytes = 0
        for (idx in 0 until points.size - 1) {
            val segLen = points[idx + 1] - points[idx]
            if (segLen > 0) {
                out.write(tailBuf, offset + points[idx], segLen)
                out.flush()
                sentBytes += segLen
                if (points.size > 2 && idx < points.size - 2) {
                    try { Thread.sleep(2L) } catch (_: InterruptedException) {}
                }
            }
        }
        return sentBytes
    }

    private fun transmitSniDotFibonacci(
        out: OutputStream,
        hsBody: ByteArray,
        offsets: ClientHelloInfo,
        jitterBytes: Int = 20
    ): Pair<Int, String> {
        val sniStartRel = maxOf(6, offsets.sniNamePos - 5)
        val dotRel = maxOf(sniStartRel + 1, offsets.sniFirstDotPos - 5)
        val dotAnchor = maxOf(4, dotRel - 3)
        val chunkPlan = ArrayList<Int>(8)

        if (dotAnchor > sniStartRel + 2) {
            val firstCut = sniStartRel + 2
            val bridgeCut = dotAnchor - firstCut
            chunkPlan.add(firstCut)
            chunkPlan.add(bridgeCut)
        } else {
            chunkPlan.add(dotAnchor)
        }
        chunkPlan.addAll(listOf(1, 2, 3, 5, 8))

        val frames = reframeHandshakeChunks(
            hsBody = hsBody,
            chunkSizes = chunkPlan,
            version = 0x0303,
            alternatingVersions = FIB_ALT_VERSIONS
        )

        var dotFrameIdx = if (dotAnchor > sniStartRel + 2) 4 else 3
        dotFrameIdx = minOf((frames.size - 1).coerceAtLeast(1), dotFrameIdx)

        val flightA = joinByteArrays(frames.subList(0, dotFrameIdx))
        val flightB = joinByteArrays(frames.subList(dotFrameIdx, frames.size))

        out.write(flightA)
        out.flush()

        val jit = if (jitterBytes > 0) Random.nextInt(-jitterBytes, jitterBytes + 1) else 0
        val tailStride = maxOf(64, 340 + jit)
        if (flightB.size > tailStride + 24) {
            out.write(flightB, 0, tailStride)
            out.flush()
            out.write(flightB, tailStride, flightB.size - tailStride)
            out.flush()
        } else if (flightB.isNotEmpty()) {
            out.write(flightB)
            out.flush()
        }

        val totalLen = flightA.size + flightB.size
        return totalLen to "Coalesced-Sync(${frames.size}F) | ${totalLen}B"
    }

    private fun transmitHybridVector(
        sock: Socket,
        out: OutputStream,
        nativeFd: Int,
        hsBody: ByteArray,
        offsets: ClientHelloInfo,
        vector: BypassVector,
        customStride: Int = 400,
        jitterBytes: Int = 20
    ): Pair<Int, String> {
        val bodyLen = hsBody.size
        val sniExtRel = maxOf(6, offsets.sniExtPos - 5)
        val sniNameRel = maxOf(10, offsets.sniNamePos - 5)
        val sniLen = maxOf(4, offsets.sniNameLen)

        if (vector.hybridMode == "phantom_seam_oob") {
            var c1 = if (sniExtRel > 44) maxOf(38, sniExtRel - 4) else maxOf(8, sniExtRel - 2)
            c1 = minOf((bodyLen - 16).coerceAtLeast(8), c1)

            val rec1 = packTlsRecord(0x16, 0x0301, hsBody, 0, c1)
            val rec2 = packTlsRecord(0x16, 0x0304, hsBody, c1, bodyLen - c1)
            val combined = rec1 + rec2

            val targetHostByte = minOf(
                combined.size - 4,
                sniNameRel + 10 + minOf(3, maxOf(1, sniLen / 3))
            ).coerceAtLeast(4)

            val sentHead = safeSendUrgent(sock, out, nativeFd, combined, 0, targetHostByte)
            val strideVal = if (customStride > 0) customStride else 400
            val sentTail = transmitTailSegments(
                sock = sock,
                out = out,
                nativeFd = nativeFd,
                tailBuf = combined,
                offset = targetHostByte,
                tailLen = combined.size - targetHostByte,
                forwardCuts = listOf(strideVal),
                jitterBytes = jitterBytes
            )
            val total = sentHead + sentTail
            return total to "Core-Omega | ${total}B"
        }

        if (vector.hybridMode == "sni_microcell_pincer") {
            val cellStart = maxOf(8, minOf(bodyLen - 16, sniNameRel + minOf(3, maxOf(1, sniLen / 4))))
            val cellLen = minOf(5, maxOf(2, minOf(sniLen / 2, (bodyLen - cellStart - 6).coerceAtLeast(2))))
            val cellEnd = minOf(bodyLen - 4, cellStart + cellLen)

            val rec1 = packTlsRecord(0x16, 0x0304, hsBody, 0, cellStart)
            val rec2 = packTlsRecord(0x16, 0x0301, hsBody, cellStart, cellEnd - cellStart)
            val rec3 = packTlsRecord(0x16, 0x0304, hsBody, cellEnd, bodyLen - cellEnd)

            val oob1Cut = minOf(rec1.size - 2, maxOf(10, offsets.sniExtPos + 1)).coerceAtLeast(2)
            val sent1 = safeSendUrgent(sock, out, nativeFd, rec1, 0, oob1Cut)

            val remPart1 = rec1.copyOfRange(oob1Cut, rec1.size)
            val remStream = remPart1 + rec2 + rec3
            val jit = if (jitterBytes > 0) Random.nextInt(-jitterBytes, jitterBytes + 1) else 0
            val pincerStride = maxOf(48, 360 + jit)

            val total = if (remStream.size > pincerStride + 24) {
                val sent2 = safeSendUrgent(sock, out, nativeFd, remStream, 0, pincerStride)
                try { Thread.sleep(2L) } catch (_: InterruptedException) {}
                out.write(remStream, pincerStride, remStream.size - pincerStride)
                out.flush()
                sent1 + sent2 + (remStream.size - pincerStride)
            } else {
                out.write(remStream)
                out.flush()
                sent1 + remStream.size
            }
            return total to "Core-Window | ${total}B"
        }

        // chameleon_interleave (VEC-23)
        val k1 = if (sniExtRel > 45) minOf(sniExtRel - 2, 41) else maxOf(8, sniExtRel / 2)
        val k2 = minOf(bodyLen - 20, maxOf(k1 + 4, sniNameRel + 2))
        val k3 = minOf(bodyLen - 14, k2 + maxOf(3, minOf(6, sniLen / 3)))
        val k4 = minOf(bodyLen - 8, k3 + maxOf(3, minOf(6, sniLen / 3)))

        val sizes = listOf(
            maxOf(1, k1),
            maxOf(1, k2 - k1),
            maxOf(1, k3 - k2),
            maxOf(1, k4 - k3)
        )
        val frames = reframeHandshakeChunks(
            hsBody = hsBody,
            chunkSizes = sizes,
            version = 0x0303,
            alternatingVersions = CHAMELEON_ALT_VERSIONS
        )
        val splitAt = minOf(3, frames.size)
        val burst1 = joinByteArrays(frames.subList(0, splitAt))
        val burst2 = if (splitAt < frames.size) joinByteArrays(frames.subList(splitAt, frames.size)) else ByteArray(0)

        out.write(burst1)
        out.flush()

        val jit = if (jitterBytes > 0) Random.nextInt(-jitterBytes, jitterBytes + 1) else 0
        val stride380 = maxOf(40, 380 + jit)
        if (burst2.size > stride380 + 20) {
            out.write(burst2, 0, stride380)
            out.flush()
            try { Thread.sleep(2L) } catch (_: InterruptedException) {}
            out.write(burst2, stride380, burst2.size - stride380)
            out.flush()
        } else if (burst2.isNotEmpty()) {
            out.write(burst2)
            out.flush()
        }

        val total = burst1.size + burst2.size
        return total to "Record-Sync | ${total}B"
    }

    fun transmitPlainHttp(
        sock: Socket,
        out: OutputStream,
        nativeFd: Int,
        httpPayload: ByteArray,
        vector: BypassVector,
        customStride: Int = 400,
        jitterBytes: Int = 20
    ): Pair<Int, String> {
        if (vector.family == "DIRECT" || httpPayload.size < 16) {
            out.write(httpPayload)
            out.flush()
            return httpPayload.size to "HTTP-Direct | ${httpPayload.size}B"
        }

        if (vector.family == "FRAGMENT" && vector.fragSliceMax > 0) {
            val sent = transmitFragmentedBuffer(
                out = out,
                payload = httpPayload,
                minLen = vector.fragSliceMin,
                maxLen = vector.fragSliceMax,
                minDelayMs = vector.fragDelayMinMs,
                maxDelayMs = vector.fragDelayMaxMs
            )
            return sent to "HTTP-Frag(${vector.fragSliceMin}-${vector.fragSliceMax}B)"
        }

        val asciiLower = String(httpPayload, Charsets.ISO_8859_1).lowercase(Locale.US)
        val hostIdx = asciiLower.indexOf("\r\nhost:")
        if (hostIdx != -1 && hostIdx + 10 < httpPayload.size) {
            var valStart = hostIdx + 7
            while (valStart < httpPayload.size &&
                (httpPayload[valStart] == ' '.code.toByte() || httpPayload[valStart] == '\t'.code.toByte())
            ) {
                valStart++
            }
            val cut = minOf(httpPayload.size - 2, valStart + 3)
            val usesOob = vector.urgentMode != "none" ||
                vector.hybridMode == "phantom_seam_oob" ||
                vector.hybridMode == "sni_microcell_pincer"

            if (cut > 0 && usesOob) {
                val sentU = safeSendUrgent(sock, out, nativeFd, httpPayload, 0, cut)
                val rem = transmitTailSegments(
                    sock = sock,
                    out = out,
                    nativeFd = nativeFd,
                    tailBuf = httpPayload,
                    offset = cut,
                    tailLen = httpPayload.size - cut,
                    forwardCuts = listOf(if (customStride > 0) customStride else 400),
                    jitterBytes = jitterBytes
                )
                return (sentU + rem) to "HTTP-HostDesync | ${sentU + rem}B"
            }
            if (cut > 0) {
                out.write(httpPayload, 0, cut)
                out.flush()
                try { Thread.sleep(2L) } catch (_: InterruptedException) {}
                out.write(httpPayload, cut, httpPayload.size - cut)
                out.flush()
                return httpPayload.size to "HTTP-HostSplit | ${httpPayload.size}B"
            }
        }

        out.write(httpPayload)
        out.flush()
        return httpPayload.size to "HTTP-Direct | ${httpPayload.size}B"
    }

    fun transmitClientHello(
        sock: Socket,
        out: OutputStream,
        nativeFd: Int,
        chRecord: ByteArray,
        vector: BypassVector,
        customStride: Int = 400,
        jitterBytes: Int = 20
    ): Pair<Int, String> {
        if (vector.family == "DIRECT" || chRecord.size < 16) {
            out.write(chRecord)
            out.flush()
            return chRecord.size to "TLS-Direct | ${chRecord.size}B"
        }

        val ver = polyRecordVersion(vector.outerVersion)
        val offsets = inspectClientHello(chRecord)
        val hsBody = chRecord.copyOfRange(5, chRecord.size)

        if (vector.hybridMode != "none" && hsBody.size >= 48) {
            return transmitHybridVector(
                sock = sock,
                out = out,
                nativeFd = nativeFd,
                hsBody = hsBody,
                offsets = offsets,
                vector = vector,
                customStride = customStride,
                jitterBytes = jitterBytes
            )
        }

        if (vector.recordEnvelope == "fibonacci_7rec") {
            return transmitSniDotFibonacci(
                out = out,
                hsBody = hsBody,
                offsets = offsets,
                jitterBytes = jitterBytes
            )
        }

        if (vector.recordEnvelope == "sni_dot_3rec") {
            val sniStartRel = maxOf(2, offsets.sniNamePos - 5)
            val dotRel = maxOf(sniStartRel + 2, offsets.sniFirstDotPos - 5)
            val c1 = dotRel.coerceIn(4, maxOf(4, hsBody.size - 8))
            val midSpan = 3
            val frames = reframeHandshakeChunks(
                hsBody = hsBody,
                chunkSizes = listOf(c1, midSpan, maxOf(1, hsBody.size - c1 - midSpan)),
                version = 0x0303,
                alternatingVersions = intArrayOf(0x0301, 0x0303, 0x0303)
            )
            val packed = joinByteArrays(frames)
            out.write(packed)
            out.flush()
            return packed.size to "Aligned-Relay | ${packed.size}B"
        }

        if (vector.recordEnvelope == "sni_mid_2rec") {
            val sniStartRel = maxOf(2, offsets.sniNamePos - 5)
            val sniLen = maxOf(4, offsets.sniNameLen)
            val cutRel = (sniStartRel + (sniLen / 2)).coerceIn(4, maxOf(4, hsBody.size - 4))
            val frames = reframeHandshakeChunks(
                hsBody = hsBody,
                chunkSizes = listOf(cutRel),
                version = 0x0303,
                alternatingVersions = intArrayOf(0x0301, 0x0303)
            )
            val packed = joinByteArrays(frames)
            out.write(packed)
            out.flush()
            return packed.size to "Stream-Balance | ${packed.size}B"
        }

        val mutCh = rewriteOuterVersion(chRecord, ver)
        val prefix = buildPreambleBytes(vector.preamble)

        if (vector.urgentMode == "cipher_pivot" && mutCh.size > 96) {
            val fullBuf = prefix + mutCh
            val pivotCut = minOf(fullBuf.size - 48, prefix.size + maxOf(44, offsets.cipherSuitesPos + 1))
            val anchor = if (vector.tailAnchorBytes > 0) vector.tailAnchorBytes else 36
            val anchorJit = maxOf(24, anchor + Random.nextInt(-8, 9))
            val tailStart = maxOf(pivotCut + 8, fullBuf.size - anchorJit)
            val sent1 = safeSendUrgent(sock, out, nativeFd, fullBuf, 0, pivotCut)
            out.write(fullBuf, pivotCut, tailStart - pivotCut)
            out.flush()
            out.write(fullBuf, tailStart, fullBuf.size - tailStart)
            out.flush()
            val totalSent = sent1 + (fullBuf.size - pivotCut)
            return totalSent to "Core-Delta | ${totalSent}B"
        }

        if (vector.family == "FRAGMENT" && vector.fragSliceMax > 0) {
            val fullFragBuf = prefix + mutCh
            val sent = transmitFragmentedBuffer(
                out = out,
                payload = fullFragBuf,
                minLen = vector.fragSliceMin,
                maxLen = vector.fragSliceMax,
                minDelayMs = vector.fragDelayMinMs,
                maxDelayMs = vector.fragDelayMaxMs
            )
            return sent to "Frag(${vector.fragSliceMin}-${vector.fragSliceMax}B) | ${sent}B"
        }

        val cuts = if (customStride > 0 && vector.forwardCuts.size == 1 && vector.forwardCuts[0] == 400) {
            listOf(customStride)
        } else {
            vector.forwardCuts
        }
        val secUrg = if (customStride > 0 && vector.secondUrgentDelta > 0) customStride else vector.secondUrgentDelta

        if (vector.splitMode == "1pkt_midsni") {
            val cut = offsets.sniNamePos + (offsets.sniNameLen / 2).coerceIn(3, 5)
            val splitPayload = prefix + splitRecordAtomic(chRecord, cut, ver)
            val sent = transmitTailSegments(sock, out, nativeFd, splitPayload, 0, splitPayload.size, cuts, jitterBytes, secUrg)
            return sent to "AtomicSplit | ${sent}B"
        }

        val fullBuf = prefix + mutCh
        val shift = prefix.size

        if (vector.urgentMode == "sni_tag") {
            val cut = minOf(fullBuf.size - 2, shift + offsets.sniExtPos + 1)
            if (cut > 0) {
                val sU = safeSendUrgent(sock, out, nativeFd, fullBuf, 0, cut)
                val rem = transmitTailSegments(sock, out, nativeFd, fullBuf, cut, fullBuf.size - cut, cuts, jitterBytes, secUrg)
                return (sU + rem) to "Core-Alpha | ${sU + rem}B"
            }
        }

        if (vector.urgentMode == "mid_host") {
            val cut = minOf(fullBuf.size - 2, shift + offsets.sniNamePos + (offsets.sniNameLen / 2).coerceIn(3, 5))
            if (cut > 0) {
                val sU = safeSendUrgent(sock, out, nativeFd, fullBuf, 0, cut)
                val rem = transmitTailSegments(sock, out, nativeFd, fullBuf, cut, fullBuf.size - cut, cuts, jitterBytes, secUrg)
                return (sU + rem) to "Core-Sigma | ${sU + rem}B"
            }
        }

        if (vector.urgentMode == "sni_len") {
            val cut = minOf(fullBuf.size - 2, shift + offsets.sniListLenPos + 1)
            if (cut > 0) {
                val sU = safeSendUrgent(sock, out, nativeFd, fullBuf, 0, cut)
                val rem = transmitTailSegments(sock, out, nativeFd, fullBuf, cut, fullBuf.size - cut, cuts, jitterBytes, secUrg)
                return (sU + rem) to "Core-Len | ${sU + rem}B"
            }
        }

        if (vector.urgentMode == "name_len") {
            val cut = minOf(fullBuf.size - 2, shift + offsets.sniNameLenPos + 1)
            if (cut > 0) {
                val sU = safeSendUrgent(sock, out, nativeFd, fullBuf, 0, cut)
                val rem = transmitTailSegments(sock, out, nativeFd, fullBuf, cut, fullBuf.size - cut, cuts, jitterBytes, secUrg)
                return (sU + rem) to "Core-NameLen | ${sU + rem}B"
            }
        }

        if (vector.urgentMode == "dual") {
            val c1 = shift + offsets.sniExtPos + 1
            val c2 = minOf(fullBuf.size - 2, shift + offsets.sniNamePos + 4)
            if (c1 > 0 && c1 < c2) {
                val u1 = safeSendUrgent(sock, out, nativeFd, fullBuf, 0, c1)
                val u2 = safeSendUrgent(sock, out, nativeFd, fullBuf, c1, c2 - c1)
                val rem = transmitTailSegments(sock, out, nativeFd, fullBuf, c2, fullBuf.size - c2, cuts, jitterBytes, secUrg)
                return (u1 + u2 + rem) to "Core-Dual | ${u1 + u2 + rem}B"
            }
        }

        if (vector.urgentMode == "triple") {
            val c0 = shift + offsets.extTotalLenPos + 1
            val c1 = shift + offsets.sniExtPos + 1
            val c2 = minOf(fullBuf.size - 2, shift + offsets.sniNamePos + 4)
            if (c0 > 0 && c0 < c1 && c1 < c2) {
                val u0 = safeSendUrgent(sock, out, nativeFd, fullBuf, 0, c0)
                val u1 = safeSendUrgent(sock, out, nativeFd, fullBuf, c0, c1 - c0)
                val u2 = safeSendUrgent(sock, out, nativeFd, fullBuf, c1, c2 - c1)
                val rem = transmitTailSegments(sock, out, nativeFd, fullBuf, c2, fullBuf.size - c2, cuts, jitterBytes, secUrg)
                return (u0 + u1 + u2 + rem) to "Core-Tri | ${u0 + u1 + u2 + rem}B"
            }
        }

        val sent = transmitTailSegments(sock, out, nativeFd, fullBuf, 0, fullBuf.size, cuts, jitterBytes, secUrg)
        return sent to "Coalesced | ${sent}B"
    }
}
