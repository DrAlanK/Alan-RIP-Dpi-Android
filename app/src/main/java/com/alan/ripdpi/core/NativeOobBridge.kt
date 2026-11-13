package com.alan.ripdpi.core

import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object NativeOobBridge {
    private var loaded = false
    private val rawRunning = AtomicBoolean(false)
    @Volatile
    private var activeRawFd: FileDescriptor? = null
    private val rawExecutor = Executors.newSingleThreadExecutor()

    init {
        loaded = try {
            System.loadLibrary("alan-oob")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isAvailable(): Boolean = loaded

    @JvmStatic
    external fun sendOobInlineByte(fd: Int, oobByte: Byte): Int

    @JvmStatic
    external fun sendSplitWithOob(
        fd: Int,
        payload: ByteArray,
        splitPos: Int,
        oobByte: Byte,
        postDelayUs: Int
    ): Int

    @JvmStatic
    external fun tuneSocketLowLatency(fd: Int, sendBufSize: Int): Int

    @JvmStatic
    external fun nativeEnterpriseProbe(host: String, port: Int, protoType: Int, timeoutMs: Int): Int

    @JvmStatic
    external fun nativeStartEnterpriseRaw(host: String, port: Int, protoType: Int): Int

    @JvmStatic
    external fun nativeStopEnterpriseRaw()

    fun extractFd(socket: Socket): Int {
        return try {
            val implField = Socket::class.java.getDeclaredField("impl").apply { isAccessible = true }
            val impl = implField.get(socket) ?: return -1
            var cls: Class<*>? = impl.javaClass
            while (cls != null) {
                try {
                    val fdField = cls.getDeclaredField("fd").apply { isAccessible = true }
                    val fdObj = fdField.get(impl) as? FileDescriptor ?: return -1
                    val intField = FileDescriptor::class.java.getDeclaredField("descriptor").apply { isAccessible = true }
                    return intField.getInt(fdObj)
                } catch (_: NoSuchFieldException) {
                    cls = cls.superclass
                }
            }
            -1
        } catch (_: Throwable) {
            -1
        }
    }

    private fun protoToCode(proto: String): Int = when (proto.trim().lowercase(Locale.US)) {
        "ikev2", "ipsec" -> 1
        "l2tp" -> 2
        else -> 3
    }

    fun probeEnterpriseInCCore(host: String, port: Int, proto: String, timeoutMs: Int = 1200): Int {
        if (loaded) {
            try {
                val r = nativeEnterpriseProbe(host.trim(), port, protoToCode(proto), timeoutMs)
                if (r > 0) return r
            } catch (_: UnsatisfiedLinkError) {
            } catch (_: Throwable) {
            }
        }
        return EnterpriseProtocolEngine.probeEnterpriseEndpointBlocking(host, port, proto, timeoutMs)
    }

    fun startEnterpriseRawInCCore(host: String, port: Int, proto: String): Boolean {
        stopEnterpriseRawInCCore()
        val code = protoToCode(proto)
        if (loaded) {
            try {
                if (nativeStartEnterpriseRaw(host.trim(), port, code) == 0) {
                    return true
                }
            } catch (_: UnsatisfiedLinkError) {
            } catch (_: Throwable) {
            }
        }
        // Raw Linux kernel syscall socket via android.system.Os (C/POSIX layer)
        rawRunning.set(true)
        rawExecutor.execute {
            var fd: FileDescriptor? = null
            try {
                val addr = InetAddress.getByName(host.trim())
                val isUdp = (code == 1 || code == 2)
                fd = if (isUdp) {
                    Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_UDP)
                } else {
                    Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
                }
                activeRawFd = fd
                val targetPort = if (port in 1..65535) port else EnterpriseProtocolEngine.defaultPortFor(proto)
                Os.connect(fd, InetSocketAddress(addr, targetPort))
                if (isUdp) {
                    val initPkt = ByteArray(48).apply {
                        this[0] = 0x41; this[1] = 0x4C; this[16] = 0x21; this[17] = 0x20; this[18] = 0x22; this[19] = 0x08; this[27] = 0x30
                    }
                    Os.write(fd, initPkt, 0, initPkt.size)
                }
                while (rawRunning.get() && activeRawFd == fd) {
                    Thread.sleep(15000L)
                    if (isUdp && rawRunning.get()) {
                        val ka = byteArrayOf(0xFF.toByte())
                        try { Os.write(fd, ka, 0, 1) } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { if (fd != null) Os.close(fd) } catch (_: Throwable) {}
            }
        }
        return true
    }

    fun stopEnterpriseRawInCCore() {
        rawRunning.set(false)
        val fd = activeRawFd
        activeRawFd = null
        if (fd != null) {
            try { Os.close(fd) } catch (_: Throwable) {}
        }
        if (loaded) {
            try {
                nativeStopEnterpriseRaw()
            } catch (_: UnsatisfiedLinkError) {
            } catch (_: Throwable) {
            }
        }
    }
}
