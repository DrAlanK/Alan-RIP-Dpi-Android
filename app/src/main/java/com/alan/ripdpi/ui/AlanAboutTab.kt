package com.alan.ripdpi.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alan.ripdpi.R
import com.alan.ripdpi.model.APP_NAME
import com.alan.ripdpi.model.APP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

@Composable
fun AboutTabScreen() {
    val p = LocalAlanPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("") }
    var latestReleaseUrl by remember { mutableStateOf("https://github.com/DrAlanK/Alan-RIP-Dpi-Android/releases") }
    var updateAvailable by remember { mutableStateOf(false) }
    var releaseNotes by remember { mutableStateOf("") }

    fun openExternalUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {}
    }

    fun isRemoteVersionNewer(remoteTag: String, localVer: String): Boolean {
        val rNums = remoteTag.removePrefix("v").substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        val lNums = localVer.removePrefix("v").substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rNums.size, lNums.size)
        for (i in 0 until maxLen) {
            val rv = rNums.getOrElse(i) { 0 }
            val lv = lNums.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    fun checkLatestVersion() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateStatus = "در حال بررسی آخرین نسخه اندروید از گیت‌هاب..."
        updateAvailable = false
        releaseNotes = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val client = com.alan.ripdpi.core.AlanCaSanitizer.applyTo(OkHttpClient.Builder())
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(8, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/DrAlanK/Alan-RIP-Dpi-Android/releases")
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "$APP_NAME/$APP_VERSION")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            return@withContext Triple(false, "خطا در ارتباط با گیت‌هاب (HTTP ${resp.code})", "")
                        }
                        val arr = JSONArray(resp.body?.string().orEmpty())
                        if (arr.length() == 0) {
                            return@withContext Triple(false, "نسخه‌ای ثبت نشده است.", "")
                        }
                        val latest = arr.getJSONObject(0)
                        val tag = latest.optString("tag_name", "").trim()
                        val htmlUrl = latest.optString("html_url", latestReleaseUrl).trim()
                        val body = latest.optString("body", "").trim()
                        latestReleaseUrl = htmlUrl
                        if (isRemoteVersionNewer(tag, APP_VERSION)) {
                            Triple(true, "نسخه جدید $tag در دسترس است!", body.take(360))
                        } else {
                            Triple(false, "شما روی جدیدترین نسخه (v$APP_VERSION • آخرین ریلیز: $tag) هستید.", body.take(220))
                        }
                    }
                } catch (t: Throwable) {
                    Triple(false, "بررسی ناموفق بود؛ اتصال اینترنت را چک کنید (${t.javaClass.simpleName}).", "")
                }
            }
            updateAvailable = result.first
            updateStatus = result.second
            releaseNotes = result.third
            checkingUpdate = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // 1. Brand Hero Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(highlighted = true, cornerRadius = 22.dp)
                .padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(74.dp)
                    .alanNeuInset(cornerRadius = 22.dp, accented = true)
                    .padding(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.alan_brand_logo),
                    contentDescription = APP_NAME,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LtrContent {
                Text(
                    text = APP_NAME,
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
            LtrContent {
                Box(
                    modifier = Modifier
                        .alanNeuInset(cornerRadius = 10.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v$APP_VERSION • 29 Anti-DPI Vectors + Xray-core",
                        color = p.accentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = "توسعه‌یافته توسط Doctor Alan (@DrAlanK)",
                color = p.textSecondary,
                fontSize = 11.5.sp
            )
        }

        // 2. Android Update Checker Card
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 18.dp)
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = p.emerald, modifier = Modifier.size(20.dp))
                Text(
                    text = "بروزرسانی نسخه اندروید",
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp
                )
            }

            if (updateStatus.isNotBlank()) {
                Text(
                    text = updateStatus,
                    color = if (updateAvailable) p.emerald else p.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
            if (releaseNotes.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanNeuInset(cornerRadius = 12.dp)
                        .padding(10.dp)
                ) {
                    Text(
                        text = releaseNotes,
                        color = p.textMuted,
                        fontSize = 10.5.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.accentGold)
                        .clickable { checkLatestVersion() }
                ) {
                    Text(
                        text = if (checkingUpdate) "در حال بررسی..." else "بررسی بروزرسانی جدید",
                        color = p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
                if (updateAvailable) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.emerald)
                            .clickable { openExternalUrl(latestReleaseUrl) }
                    ) {
                        Text(
                            text = "دانلود نسخه جدید",
                            color = Color(0xFF07130E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Official Channel & Repositories
        AboutLinkRowCard(
            icon = Icons.Outlined.Send,
            title = "کانال تلگرام (@LabAlan)",
            subtitle = "t.me/LabAlan",
            tint = p.cyan,
            onClick = { openExternalUrl("https://t.me/LabAlan") }
        )
        AboutLinkRowCard(
            icon = Icons.Outlined.PhoneAndroid,
            title = "ریپازیتوری اندروید (GitHub)",
            subtitle = "github.com/DrAlanK/Alan-RIP-Dpi-Android",
            tint = p.emerald,
            onClick = { openExternalUrl("https://github.com/DrAlanK/Alan-RIP-Dpi-Android") }
        )
        AboutLinkRowCard(
            icon = Icons.Outlined.DesktopWindows,
            title = "ریپازیتوری ویندوز (GitHub)",
            subtitle = "github.com/DrAlanK/Alan-RIP-Dpi-Windows",
            tint = p.accentGold,
            onClick = { openExternalUrl("https://github.com/DrAlanK/Alan-RIP-Dpi-Windows") }
        )
        AboutLinkRowCard(
            icon = Icons.Outlined.Download,
            title = "دانلود آخرین نسخه ویندوز",
            subtitle = "github.com/DrAlanK/Alan-RIP-Dpi-Windows/releases/latest",
            tint = p.emerald,
            onClick = { openExternalUrl("https://github.com/DrAlanK/Alan-RIP-Dpi-Windows/releases/latest") }
        )

        // 4. Special Thanks / Credits Card (Floxu1 — OOB Method)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(highlighted = true, cornerRadius = 18.dp)
                .clickable { openExternalUrl("https://github.com/Floxu1") }
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .alanNeuInset(cornerRadius = 13.dp, accented = true)
                ) {
                    Icon(Icons.Outlined.FavoriteBorder, null, tint = p.accentGold, modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = "قدردانی ویژه از Floxu1",
                        color = p.accentGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = "تشکر ویژه بابت ایده و متد ضد فیلترینگ OOB",
                        color = p.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    LtrContent {
                        Text(
                            text = "github.com/Floxu1",
                            color = p.textMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.OpenInNew, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
        }

        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun AboutLinkRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    val p = LocalAlanPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(cornerRadius = 16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .alanNeuInset(cornerRadius = 12.dp)
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                LtrContent {
                    Text(subtitle, color = p.textMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.OpenInNew, null, tint = p.textMuted, modifier = Modifier.size(16.dp))
    }
}
