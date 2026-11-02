package com.alan.ripdpi.core

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.alan.ripdpi.model.BypassVector
import com.alan.ripdpi.model.VectorCatalog
import com.alan.ripdpi.vpn.AlanVpnService
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FileDescriptor
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class FullDuplexMutatorBridge(
    private val listenPort: Int,
    initialRemoteHost: String,
    initialRemotePort: Int,
    initialVector: BypassVector = VectorCatalog.VECTOR_REGISTRY.last(),
    initialCustomStride: Int = 0,
    initialJitterBytes: Int = 20,
    @Suppress("UNUSED_PARAMETER")
    private val protectSocket: ((Socket) -> Boolean)? = null
) {
    companion object {
        private const val SOCKET_BUF_SIZE = 2 * 1024 * 1024
        private const val STREAM_CHUNK_SIZE = 65536
        private const val INITIAL_FLIGHT_TIMEOUT_MS = 6000
        private const val CONNECT_TIMEOUT_MS = 6500L
    }

    private class PosixRemoteHandle(val fd: FileDescriptor) {
        private val refCount = AtomicInteger(1)
        private val closedOnce = AtomicBoolean(false)
        val connected = AtomicBoolean(false)

        fun retain(): Boolean {
            while (true) {
                val cur = refCount.get()
                if (cur <= 0) return false
                if (refCount.compareAndSet(cur, cur + 1)) return true
            }
        }

        fun shutdownNow() {
            if (!fd.valid()) return
            try {
                Os.shutdown(fd, OsConstants.SHUT_RDWR)
            } catch (_: Throwable) {
            }
        }

        fun shutdownWrite() {
            if (!fd.valid()) return
            try {
                Os.shutdown(fd, OsConstants.SHUT_WR)
            } catch (_: Throwable) {
            }
        }

        fun release() {
            if (refCount.decrementAndGet() == 0 && closedOnce.compareAndSet(false, true)) {
                try {
                    if (fd.valid()) {
                        Os.close(fd)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    val txBytesTotal = AtomicLong(0L)
    val rxBytesTotal = AtomicLong(0L)
    val activeConnectionsCount = AtomicInteger(0)

    private val running = AtomicBoolean(false)
    private val activeVectorRef = AtomicReference(initialVector)
    private val remoteTargetRef = AtomicReference(initialRemoteHost to initialRemotePort)
    private val cachedAddressRef = AtomicReference<Pair<String, InetAddress>?>(null)
    private val customStrideRef = AtomicInteger(initialCustomStride)
    private val jitterBytesRef = AtomicInteger(initialJitterBytes)

    private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null
    private var watchdogScheduler: ScheduledExecutorService? = null
    private val liveClientSockets = Collections.synchronizedSet(HashSet<Socket>())
    private val liveRemoteHandles = Collections.synchronizedSet(HashSet<PosixRemoteHandle>())

    fun updateVector(
        vector: BypassVector,
        customStride: Int = customStrideRef.get(),
        jitterBytes: Int = jitterBytesRef.get()
    ) {
        activeVectorRef.set(vector)
        customStrideRef.set(customStride)
        jitterBytesRef.set(jitterBytes)
    }

    fun updateRemoteTarget(host: String, port: Int) {
        remoteTargetRef.set(host to port)
        cachedAddressRef.set(null)
    }

    fun getCurrentVector(): BypassVector = activeVectorRef.get()

    fun resetCounters() {
        txBytesTotal.set(0L)
        rxBytesTotal.set(0L)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val srv = ServerSocket()
        srv.reuseAddress = true
        srv.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), listenPort), 256)
        serverSocket = srv

        val exec = Executors.newCachedThreadPool { r ->
            Thread({
                try {
                    r.run()
                } catch (_: Throwable) {
                }
            }, "AlanMutatorBridge-$listenPort").apply { isDaemon = true }
        }
        pool = exec
        watchdogScheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread({
                try {
                    r.run()
                } catch (_: Throwable) {
                }
            }, "AlanConnectTimer-$listenPort").apply { isDaemon = true }
        }

        exec.execute {
            while (running.get()) {
                try {
                    val client = srv.accept()
                    exec.execute {
                        try {
                            handleSession(client)
                        } catch (_: Throwable) {
                            closeQuietly(client)
                        }
                    }
                } catch (_: Throwable) {
                    if (!running.get()) break
                }
            }
        }
    }

    fun dropActiveConnections() {
        val clients = synchronized(liveClientSockets) { liveClientSockets.toList() }
        for (c in clients) {
            closeQuietly(c)
        }
        val remotes = synchronized(liveRemoteHandles) { liveRemoteHandles.toList() }
        for (h in remotes) {
            h.shutdownNow()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
        dropActiveConnections()
        watchdogScheduler?.shutdownNow()
        watchdogScheduler = null
        pool?.shutdownNow()
        pool = null
    }

    private fun configureClientSocket(sock: Socket) {
        try {
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.sendBufferSize = SOCKET_BUF_SIZE
            sock.receiveBufferSize = SOCKET_BUF_SIZE
        } catch (_: Throwable) {
        }
    }

    private fun resolveTargetAddress(host: String): InetAddress {
        val cached = cachedAddressRef.get()
        if (cached != null && cached.first == host) {
            return cached.second
        }
        val addresses = InetAddress.getAllByName(host)
        val picked = addresses.firstOrNull { it is Inet4Address } ?: addresses.first()
        cachedAddressRef.set(host to picked)
        return picked
    }

    private fun openProtectedPosixRemote(host: String, port: Int): PosixRemoteHandle {
        val targetAddr = resolveTargetAddress(host)
        val domain = if (targetAddr is Inet6Address) OsConstants.AF_INET6 else OsConstants.AF_INET
        val rawFd = Os.socket(domain, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
        val handle = PosixRemoteHandle(rawFd)

        try {
            try {
                ParcelFileDescriptor.dup(rawFd).use { dupPfd ->
                    AlanVpnService.protectRawFd(dupPfd.fd)
                }
            } catch (_: Throwable) {
            }

            try {
                Os.setsockoptInt(rawFd, OsConstants.IPPROTO_TCP, OsConstants.TCP_NODELAY, 1)
                Os.setsockoptInt(rawFd, OsConstants.SOL_SOCKET, OsConstants.SO_KEEPALIVE, 1)
                Os.setsockoptInt(rawFd, OsConstants.SOL_SOCKET, OsConstants.SO_SNDBUF, SOCKET_BUF_SIZE)
                Os.setsockoptInt(rawFd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_BUF_SIZE)
            } catch (_: Throwable) {
            }

            liveRemoteHandles.add(handle)
            val timerFuture = watchdogScheduler?.schedule({
                if (!handle.connected.get()) {
                    handle.shutdownNow()
                }
            }, CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            try {
                Os.connect(rawFd, targetAddr, port)
                handle.connected.set(true)
            } finally {
                timerFuture?.cancel(false)
            }
            return handle
        } catch (e: Throwable) {
            liveRemoteHandles.remove(handle)
            handle.shutdownNow()
            handle.release()
            throw e
        }
    }

    private fun handleSession(clientSock: Socket) {
        liveClientSockets.add(clientSock)
        activeConnectionsCount.incrementAndGet()
        var remoteHandle: PosixRemoteHandle? = null

        try {
            configureClientSocket(clientSock)
            val (remoteHost, remotePort) = remoteTargetRef.get()
            val handle = openProtectedPosixRemote(remoteHost, remotePort)
            remoteHandle = handle

            val vec = activeVectorRef.get()
            val stride = customStrideRef.get()
            val jitter = jitterBytesRef.get()
            val passthrough = vec.family.equals("DIRECT", ignoreCase = true) || vec.vectorId == "VEC-00"

            val clientIn = clientSock.getInputStream()

            if (!passthrough) {
                val initialFlight = readExactInitialFlight(clientSock, clientIn)
                if (initialFlight.isEmpty()) {
                    return
                }
                if (TlsByteMutator.isTlsClientHello(initialFlight)) {
                    TlsByteMutator.transmitClientHello(
                        remoteFd = handle.fd,
                        chRecord = initialFlight,
                        vector = vec,
                        customStride = stride,
                        jitterBytes = jitter
                    )
                } else if (TlsByteMutator.isPlainHttpRequest(initialFlight)) {
                    TlsByteMutator.transmitPlainHttp(
                        remoteFd = handle.fd,
                        httpBuf = initialFlight,
                        vector = vec,
                        customStride = stride,
                        jitterBytes = jitter
                    )
                } else {
                    TlsByteMutator.writeAllFd(handle.fd, initialFlight)
                }
                txBytesTotal.addAndGet(initialFlight.size.toLong())
            }

            val exec = pool
            if (exec != null && handle.retain()) {
                try {
                    exec.execute {
                        try {
                            downlinkPump(handle, clientSock)
                        } catch (_: Throwable) {
                        } finally {
                            closeQuietly(clientSock)
                            handle.shutdownNow()
                            handle.release()
                        }
                    }
                } catch (t: Throwable) {
                    handle.release()
                    throw t
                }
            }

            uplinkPump(
                clientSock = clientSock,
                remoteHandle = handle,
                vec = vec,
                passthrough = passthrough
            )
        } catch (_: Throwable) {
        } finally {
            liveClientSockets.remove(clientSock)
            closeQuietly(clientSock)
            if (remoteHandle != null) {
                liveRemoteHandles.remove(remoteHandle)
                remoteHandle.shutdownNow()
                remoteHandle.release()
            }
            activeConnectionsCount.decrementAndGet()
        }
    }

    private fun readExactInitialFlight(sock: Socket, input: InputStream): ByteArray {
        val prevTimeout = sock.soTimeout
        try {
            sock.soTimeout = INITIAL_FLIGHT_TIMEOUT_MS
            val hdr = ByteArray(5)
            var got = 0
            while (got < 5) {
                val n = input.read(hdr, got, 5 - got)
                if (n < 0) {
                    return if (got > 0) hdr.copyOf(got) else ByteArray(0)
                }
                got += n
            }

            if ((hdr[0].toInt() and 0xFF) != 0x16) {
                val out = ByteArrayOutputStream(2048)
                out.write(hdr)
                try {
                    sock.soTimeout = 40
                    val extra = ByteArray(16384)
                    val extraRead = input.read(extra, 0, extra.size)
                    if (extraRead > 0) {
                        out.write(extra, 0, extraRead)
                    }
                } catch (_: SocketTimeoutException) {
                }
                return out.toByteArray()
            }

            val bodyLen = TlsByteMutator.readUint16BE(hdr, 3)
            if (bodyLen <= 0 || bodyLen > 32768) {
                return hdr
            }

            val fullRecord = ByteArray(5 + bodyLen)
            System.arraycopy(hdr, 0, fullRecord, 0, 5)
            var bodyGot = 0
            while (bodyGot < bodyLen) {
                val n = input.read(fullRecord, 5 + bodyGot, bodyLen - bodyGot)
                if (n < 0) throw EOFException("Incomplete TLS ClientHello record")
                bodyGot += n
            }
            return fullRecord
        } finally {
            try {
                sock.soTimeout = prevTimeout
            } catch (_: Throwable) {
            }
        }
    }

    private fun uplinkPump(
        clientSock: Socket,
        remoteHandle: PosixRemoteHandle,
        vec: BypassVector,
        passthrough: Boolean
    ) {
        val clientIn = clientSock.getInputStream()
        val buf = ByteArray(STREAM_CHUNK_SIZE)
        var flightNum = if (passthrough) 0 else 1

        while (running.get() && !clientSock.isClosed && remoteHandle.fd.valid()) {
            val n = clientIn.read(buf)
            if (n <= 0) break
            flightNum++

            val payload: ByteArray
            val payloadLen: Int

            if (!passthrough && flightNum == 2 && vec.coalesceFlight2 && n <= 180) {
                val merged = ByteArrayOutputStream(n + 4096)
                merged.write(buf, 0, n)
                val prevTimeout = clientSock.soTimeout
                try {
                    clientSock.soTimeout = 22
                    val extraBuf = ByteArray(16384)
                    val extraRead = clientIn.read(extraBuf)
                    if (extraRead > 0) {
                        merged.write(extraBuf, 0, extraRead)
                    }
                } catch (_: SocketTimeoutException) {
                } finally {
                    try {
                        clientSock.soTimeout = prevTimeout
                    } catch (_: Throwable) {
                    }
                }
                payload = merged.toByteArray()
                payloadLen = payload.size
            } else {
                payload = buf
                payloadLen = n
            }

            if (
                !passthrough &&
                vec.family.equals("FRAGMENT", ignoreCase = true) &&
                vec.fragFlights > 1 &&
                flightNum <= vec.fragFlights &&
                vec.fragSliceMax > 0
            ) {
                val exactSlice = if (payload === buf) payload.copyOfRange(0, payloadLen) else payload
                TlsByteMutator.transmitFragmentedBuffer(
                    remoteFd = remoteHandle.fd,
                    buf = exactSlice,
                    sliceMin = vec.fragSliceMin,
                    sliceMax = vec.fragSliceMax,
                    delayMinMs = vec.fragDelayMinMs,
                    delayMaxMs = vec.fragDelayMaxMs
                )
            } else {
                TlsByteMutator.writeAllFd(remoteHandle.fd, payload, 0, payloadLen)
            }

            txBytesTotal.addAndGet(payloadLen.toLong())
        }

        remoteHandle.shutdownWrite()
    }

    private fun downlinkPump(remoteHandle: PosixRemoteHandle, clientSock: Socket) {
        try {
            if (clientSock.isClosed) return
            val clientOut = clientSock.getOutputStream()
            val buf = ByteArray(STREAM_CHUNK_SIZE)
            while (running.get() && !clientSock.isClosed && remoteHandle.fd.valid()) {
                val n = Os.read(remoteHandle.fd, buf, 0, buf.size)
                if (n <= 0) break
                clientOut.write(buf, 0, n)
                clientOut.flush()
                rxBytesTotal.addAndGet(n.toLong())
            }
        } catch (_: Throwable) {
        } finally {
            try {
                if (!clientSock.isClosed) {
                    clientSock.shutdownOutput()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun closeQuietly(sock: Socket?) {
        if (sock == null) return
        try {
            sock.close()
        } catch (_: Throwable) {
        }
    }
}
