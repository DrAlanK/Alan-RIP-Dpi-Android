package com.alan.ripdpi

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.alan.ripdpi.core.AlanAutoPilotEngine
import com.alan.ripdpi.ui.AlanMainScreen
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private var pendingVpnAction: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingVpnAction
        pendingVpnAction = null
        if (result.resultCode == Activity.RESULT_OK) {
            callback?.invoke()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashRecorder()
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {
        }
        requestNotificationPermissionIfNeeded()

        val crashFile = File(applicationContext.filesDir, "last_crash.log")
        val previousCrash = if (crashFile.exists()) {
            try {
                crashFile.readText(Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
        } else {
            ""
        }

        val engine = AlanAutoPilotEngine.getInstance(applicationContext)
        engine.bindVpnPermissionRequester { onGranted ->
            val prepareIntent = try {
                VpnService.prepare(this)
            } catch (_: Throwable) {
                null
            }
            if (prepareIntent == null) {
                onGranted()
            } else {
                pendingVpnAction = onGranted
                vpnPermissionLauncher.launch(prepareIntent)
            }
        }

        setContent {
            var crashReportText by remember { mutableStateOf(previousCrash) }

            Box(modifier = Modifier.fillMaxSize()) {
                AlanMainScreen(controller = engine)

                if (crashReportText.isNotBlank()) {
                    Dialog(onDismissRequest = {
                        crashFile.delete()
                        crashReportText = ""
                    }) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0E131E),
                            border = BorderStroke(1.dp, Color(0xFFF5C451)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "گزارش خطای اجرای قبلی",
                                    color = Color(0xFFF5C451),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .background(Color(0xFF06090F), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = crashReportText,
                                        color = Color(0xFFF87171),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        onClick = {
                                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("CrashLog", crashReportText))
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF5C451),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "کپی متن خطا",
                                            color = Color(0xFF090C12),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = {
                                            crashFile.delete()
                                            crashReportText = ""
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF1A2233),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "بستن",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun installCrashRecorder() {
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val abis = Build.SUPPORTED_ABIS.joinToString(",")
                val report = "SDK=${Build.VERSION.SDK_INT} ABIs=[$abis] Thread=${thread.name}\n$sw"
                File(applicationContext.filesDir, "last_crash.log").writeText(report, Charsets.UTF_8)
            } catch (_: Throwable) {
            }
            prevHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onDestroy() {
        AlanAutoPilotEngine.getInstance(applicationContext).bindVpnPermissionRequester(null)
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
