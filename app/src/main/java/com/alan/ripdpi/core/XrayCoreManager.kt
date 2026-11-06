package com.alan.ripdpi.core

import android.content.Context
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("unused")
class XrayCoreManager(
    context: Context,
    private val statusLogger: ((String) -> Unit)? = null
) {
    @Volatile var probeSilentMode: Boolean = false


    private val appContext = context.applicationContext
    private val lock = ReentrantLock()
    private val envInitialized = AtomicBoolean(false)
    private var activeController: CoreController? = null

    @Volatile
    var lastErrorMessage: String = ""
        private set

    private fun ensureInitialized(): Boolean {
        if (envInitialized.get()) return true
        return try {
            try {
                File(appContext.filesDir, ".xray_env_key").delete()
            } catch (_: Throwable) {
            }
            Seq.setContext(appContext)
            Libv2ray.initCoreEnv(appContext.filesDir.absolutePath, "")
            envInitialized.set(true)
            true
        } catch (t: Throwable) {
            val root = t.cause ?: t
            lastErrorMessage = "${root.javaClass.simpleName}: ${root.message ?: "native load failed"}"
            statusLogger?.invoke("Xray init error -> $lastErrorMessage")
            false
        }
    }

    fun getXrayVersion(): String {
        if (!ensureInitialized()) return "Unavailable ($lastErrorMessage)"
        return try {
            Libv2ray.checkVersionX()
        } catch (t: Throwable) {
            "Xray-core"
        }
    }

    fun startCore(configJson: String): Boolean {
        return lock.withLock {
            stopCoreUnlocked()
            if (!ensureInitialized()) return@withLock false
            lastErrorMessage = ""
            try {
                val callback = object : CoreCallbackHandler {
                    override fun onEmitStatus(code: Long, msg: String?): Long {
                        if (!msg.isNullOrBlank()) {
                            statusLogger?.invoke("Xray[$code]: $msg")
                        }
                        return 0L
                    }

                    override fun shutdown(): Long = 0L
                    override fun startup(): Long = 0L
                }
                val ctrl = Libv2ray.newCoreController(callback)
                ctrl.startLoop(configJson, 0)
                activeController = ctrl
                true
            } catch (e: Throwable) {
                val root = e.cause ?: e
                lastErrorMessage = "${root.javaClass.simpleName}: ${root.message ?: e.message ?: "unknown core error"}"
                statusLogger?.invoke("Xray startLoop error -> $lastErrorMessage")
                activeController = null
                false
            }
        }
    }

    fun stopCore() {
        lock.withLock {
            stopCoreUnlocked()
        }
    }

    fun isRunning(): Boolean {
        return lock.withLock {
            try {
                activeController?.isRunning == true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun stopCoreUnlocked() {
        val ctrl = activeController ?: return
        activeController = null
        try {
            ctrl.stopLoop()
        } catch (_: Throwable) {
        }
    }
}
