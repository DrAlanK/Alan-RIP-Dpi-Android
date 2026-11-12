package com.alan.ripdpi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alan.ripdpi.model.CountryPaletteRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.Locale

@Composable
fun ProfilesTabScreen(
    profiles: List<ProfileItem>,
    subscriptions: List<SubscriptionItem>,
    vectors: List<VectorSpec>,
    onSelectConfig: (Int) -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportConfigsRaw: (String) -> Unit = {},
    onImportQrPayload: (String) -> Int = { 0 },
    onHuntBest: () -> Unit,
    onQuickPingAll: () -> Unit,
    onRealTunnelPingConfigs: (selectedIndices: List<Int>, autoRenameWithLocation: Boolean) -> Unit = { _, _ -> onQuickPingAll() },
    onPingSingleConfig: (index: Int, realTunnelPing: Boolean) -> Unit = { _, _ -> onQuickPingAll() },
    onAutoRenameConfigsWithLocation: (onlySelectedIndices: List<Int>) -> Unit = {},
    onSortConfigs: (String) -> Unit,
    onDeduplicate: () -> Unit,
    onRemoveDead: () -> Unit,
    onExportVerifiedEarlyData: () -> String,
    onShareOrExportSelected: (indices: List<Int>, asBase64Sub: Boolean) -> String = { _, _ -> "" },
    onClearAll: () -> Unit,
    onDeleteConfig: (Int) -> Unit,
    onDeleteSelectedConfigs: (List<Int>) -> Unit = { list -> list.sortedDescending().forEach(onDeleteConfig) },
    onGetEditable: (Int) -> EditableConfigFields,
    onSaveEditable: (Int, EditableConfigFields) -> Unit,
    onSendToMethodScanner: (Int) -> Unit,
    onSendAllToDpiLab: () -> Unit = {},
    onAddSubscription: (String, String) -> Unit,
    onRefreshSubscriptions: () -> Unit,
    onDeleteSubscription: (Int) -> Unit,
    onQuickCloneCleanIp: (Int, Int) -> Unit = { _, _ -> }
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current

    var feedback by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var countryFilter by remember { mutableStateOf("ALL") }
    var activeModalMenu by remember { mutableStateOf("") }
    var showSubsSheet by remember { mutableStateOf(false) }
    var showBatchImportDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var cloneTargetIdx by remember { mutableStateOf<Int?>(null) }
    var itemMoreMenuIdx by remember { mutableStateOf<Int?>(null) }
    var qrPreviewProfile by remember { mutableStateOf<ProfileItem?>(null) }

    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    // 1. Camera QR Code Scanner Launcher (ZXing Embedded)
    val qrCameraLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val qrText = result.contents?.trim().orEmpty()
        if (qrText.isNotEmpty()) {
            val count = onImportQrPayload(qrText)
            feedback = "اسکن QR انجام شد ($count مورد اضافه شد)."
        } else {
            feedback = "اسکن QR لغو شد."
        }
    }

    // 2. Gallery Image QR Code Reader Launcher (GetContent + ZXing MultiFormatReader)
    val qrGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val decoded = decodeQrFromGalleryUri(context, uri)
            if (!decoded.isNullOrBlank()) {
                val count = onImportQrPayload(decoded)
                feedback = "کد QR از تصویر خوانده شد ($count مورد اضافه شد)."
            } else {
                feedback = "کد QR معتبری در تصویر انتخابی پیدا نشد."
            }
        }
    }

    // Group user's profiles by Subscription / Group Name (like v2rayNG Subscription Groups!)
    val normalizedGroupOf: (ProfileItem) -> String = remember(subscriptions) {
        { item ->
            val g = item.groupName.trim()
            when {
                g.isEmpty() || g.equals("Manual", ignoreCase = true) || g == "دستی" -> "دستی (Manual)"
                else -> g
            }
        }
    }

    val subscriptionGroups = remember(profiles, subscriptions) {
        val map = LinkedHashMap<String, MutableList<Pair<Int, ProfileItem>>>()
        // Ensure registered subscriptions always appear as tabs
        subscriptions.forEach { sub ->
            val title = sub.title.trim().ifBlank { "اشتراک" }
            map.putIfAbsent(title, mutableListOf())
        }
        profiles.forEachIndexed { idx, item ->
            val grp = normalizedGroupOf(item)
            map.getOrPut(grp) { mutableListOf() }.add(idx to item)
        }
        map.filterValues { it.isNotEmpty() }.ifEmpty { map }
    }

    val indexedFiltered = remember(profiles, searchQuery, countryFilter, subscriptionGroups) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        profiles.mapIndexedNotNull { idx, item ->
            val grp = normalizedGroupOf(item)
            val matchesGroup = countryFilter == "ALL" || grp.equals(countryFilter, ignoreCase = true)
            if (!matchesGroup) {
                null
            } else if (q.isEmpty()) {
                idx to item
            } else {
                val hay = "${item.name} $grp ${item.protocol} ${item.host} ${item.sni}".lowercase(Locale.ROOT)
                if (hay.contains(q)) idx to item else null
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Tactile Neumorphic Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 18.dp)
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "کانفیگ‌ها (${profiles.size})",
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = feedback.ifBlank {
                        if (multiSelectMode) "${selectedIndices.size} کانفیگ انتخاب شده"
                        else "مدیریت کانفیگ، پینگ واقعی و QR"
                    },
                    color = if (feedback.isNotBlank()) p.emerald else p.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AlanNeuIconButton(
                    icon = Icons.Outlined.Add,
                    tint = p.accentGold,
                    size = 38.dp,
                    onClick = { activeModalMenu = "ADD" }
                )
                AlanNeuIconButton(
                    icon = Icons.Outlined.Bolt,
                    tint = p.emerald,
                    size = 38.dp,
                    onClick = { activeModalMenu = "PING" }
                )
                AlanNeuIconButton(
                    icon = Icons.Outlined.Sort,
                    tint = p.textPrimary,
                    size = 38.dp,
                    onClick = { activeModalMenu = "SORT" }
                )
                AlanNeuIconButton(
                    icon = Icons.Outlined.Checklist,
                    tint = if (multiSelectMode) p.accentGold else p.textPrimary,
                    size = 38.dp,
                    isPressed = multiSelectMode,
                    onClick = {
                        multiSelectMode = !multiSelectMode
                        if (!multiSelectMode) selectedIndices.clear()
                    }
                )
                AlanNeuIconButton(
                    icon = Icons.Outlined.MoreVert,
                    tint = p.textPrimary,
                    size = 38.dp,
                    onClick = { activeModalMenu = "MORE" }
                )
            }
        }

        // Multi-Select Batch Action Bar
        AnimatedVisibility(visible = multiSelectMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .alanNeuInset(cornerRadius = 15.dp, accented = true)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(32.dp)
                            .alanGlassCard(cornerRadius = 9.dp)
                            .clickable {
                                selectedIndices.clear()
                                selectedIndices.addAll(indexedFiltered.map { it.first })
                            }
                            .padding(horizontal = 10.dp)
                    ) {
                        Text("انتخاب همه", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(32.dp)
                            .alanGlassCard(cornerRadius = 9.dp)
                            .clickable { selectedIndices.clear() }
                            .padding(horizontal = 9.dp)
                    ) {
                        Text("لغو", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Real Ping Selected
                    AlanNeuIconButton(
                        icon = Icons.Outlined.Bolt,
                        tint = p.emerald,
                        size = 34.dp,
                        onClick = {
                            if (selectedIndices.isNotEmpty()) {
                                onRealTunnelPingConfigs(selectedIndices.toList(), true)
                                feedback = "تست پینگ واقعی ${selectedIndices.size} کانفیگ آغاز شد..."
                            }
                        }
                    )
                    // Copy Selected as Links / Base64
                    AlanNeuIconButton(
                        icon = Icons.Outlined.ContentCopy,
                        tint = p.accentGold,
                        size = 34.dp,
                        onClick = {
                            if (selectedIndices.isNotEmpty()) {
                                val exported = onShareOrExportSelected(selectedIndices.toList(), false)
                                if (exported.isNotBlank()) {
                                    copyPlainTextToClipboard(context, "SelectedConfigs", exported)
                                    feedback = "${selectedIndices.size} کانفیگ در کلیپ‌بورد کپی شد."
                                }
                            }
                        }
                    )
                    // Share Selected
                    AlanNeuIconButton(
                        icon = Icons.Outlined.Share,
                        tint = p.textPrimary,
                        size = 34.dp,
                        onClick = {
                            if (selectedIndices.isNotEmpty()) {
                                val exported = onShareOrExportSelected(selectedIndices.toList(), false)
                                if (exported.isNotBlank()) {
                                    sharePlainText(context, "Alan-RIP-Dpi Configs", exported)
                                }
                            }
                        }
                    )
                    // Delete Selected
                    AlanNeuIconButton(
                        icon = Icons.Outlined.DeleteOutline,
                        tint = p.danger,
                        size = 34.dp,
                        onClick = {
                            if (selectedIndices.isNotEmpty()) {
                                val cnt = selectedIndices.size
                                onDeleteSelectedConfigs(selectedIndices.toList())
                                selectedIndices.clear()
                                multiSelectMode = false
                                feedback = "$cnt کانفیگ انتخابی حذف شد."
                            }
                        }
                    )
                }
            }
        }

        // Concave Search Row + Quick QR Scan + Quick Clipboard Paste
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AlanSheetTextField(
                label = "جستجوی نام، آدرس، SNI یا کشور...",
                value = searchQuery,
                onValueChange = { searchQuery = it },
                ltr = false,
                modifier = Modifier.weight(1f)
            )

            AlanNeuIconButton(
                icon = Icons.Outlined.QrCodeScanner,
                tint = p.accentGold,
                size = 48.dp,
                onClick = {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("اسکن QR کانفیگ یا لینک اشتراک")
                        setBeepEnabled(false)
                        setOrientationLocked(true)
                    }
                    qrCameraLauncher.launch(options)
                }
            )

            AlanNeuIconButton(
                icon = Icons.Outlined.ContentPaste,
                tint = p.textPrimary,
                size = 48.dp,
                onClick = {
                    onImportFromClipboard()
                    feedback = "محتوای کلیپ‌بورد بررسی و وارد شد."
                }
            )
        }

        
        if (profiles.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(38.dp)
                        .alanGlassCard(highlighted = true, cornerRadius = 12.dp, accentBorderColor = p.accentGold)
                        .clickable { onSendAllToDpiLab() }
                        .padding(horizontal = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.Radar, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
                        Text(
                            text = "ارسال کل مخزن به آزمایشگاه DPI",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .alanNeuInset(cornerRadius = 12.dp)
                        .clickable {
                            onRealTunnelPingConfigs(emptyList(), false)
                            feedback = "پینگ واقعی تونل روی کل مخزن آغاز شد..."
                        }
                        .padding(horizontal = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.NetworkCheck, null, tint = p.emerald, modifier = Modifier.size(15.dp))
                        Text(
                            text = "پینگ واقعی همه",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Subscription Group Tabs Row (جداسازی ساب‌ها از یکدیگر مشابه v2rayNG)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            val allSelected = countryFilter == "ALL"
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .then(
                        if (allSelected) {
                            Modifier.alanGlassCard(highlighted = true, cornerRadius = 11.dp)
                        } else {
                            Modifier.alanNeuInset(cornerRadius = 11.dp)
                        }
                    )
                    .clickable { countryFilter = "ALL" }
                    .padding(horizontal = 13.dp)
            ) {
                Text(
                    text = "همه گروه‌ها (${profiles.size})",
                    color = if (allSelected) p.accentGold else p.textSecondary,
                    fontWeight = if (allSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 11.5.sp
                )
            }

            subscriptionGroups.forEach { (grpName, listInGroup) ->
                val isSel = countryFilter.equals(grpName, ignoreCase = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(36.dp)
                        .then(
                            if (isSel) {
                                Modifier.alanGlassCard(highlighted = true, cornerRadius = 11.dp)
                            } else {
                                Modifier.alanNeuInset(cornerRadius = 11.dp)
                            }
                        )
                        .clickable {
                            countryFilter = if (isSel) "ALL" else grpName
                        }
                        .padding(horizontal = 13.dp)
                ) {
                    Text(
                        text = "$grpName (${listInGroup.size})",
                        color = if (isSel) p.emerald else p.textSecondary,
                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .alanGlassCard(cornerRadius = 11.dp)
                    .clickable { showSubsSheet = true }
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "+ مدیریت ساب‌ها",
                    color = p.accentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // Profiles LazyColumn
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (indexedFiltered.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                            .alanNeuInset(cornerRadius = 18.dp)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = if (profiles.isEmpty()) {
                                "کانفیگی ثبت نشده است؛ از دکمه + یا اسکنر QR اضافه کنید."
                            } else {
                                "کانفیگی مطابق فیلتر یا جستجوی فعلی یافت نشد."
                            },
                            color = p.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = indexedFiltered,
                    key = { idx, pair -> "$idx-${pair.first}-${pair.second.uid}" }
                ) { _, (origIdx, item) ->
                    val isChecked = origIdx in selectedIndices
                    V2ServerRowCard(
                        item = item,
                        multiSelectMode = multiSelectMode,
                        isChecked = isChecked,
                        onSelect = {
                            if (multiSelectMode) {
                                if (isChecked) selectedIndices.remove(origIdx) else selectedIndices.add(origIdx)
                            } else {
                                onSelectConfig(origIdx)
                            }
                        },
                        onPingSingle = {
                            onPingSingleConfig(origIdx, true)
                            feedback = "تست پینگ واقعی «${item.name.take(18)}» آغاز شد..."
                        },
                        onShowQr = { qrPreviewProfile = item },
                        onOpenActions = { itemMoreMenuIdx = origIdx }
                    )
                }
            }
        }
    }

    // Top Toolbar Action Menus (ADD, PING, SORT, MORE)
    if (activeModalMenu.isNotEmpty()) {
        val actions: List<Pair<String, () -> Unit>> = when (activeModalMenu) {
            "ADD" -> listOf(
                "اسکن QR Code با دوربین" to {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("اسکن QR کانفیگ یا لینک اشتراک")
                        setBeepEnabled(false)
                        setOrientationLocked(true)
                    }
                    qrCameraLauncher.launch(options)
                },
                "خواندن QR Code از تصویر گالری" to {
                    qrGalleryLauncher.launch("image/*")
                },
                "افزودن از کلیپ‌بورد" to {
                    onImportFromClipboard()
                    feedback = "از کلیپ‌بورد وارد شد."
                },
                "وارد کردن متنی / لینک گروهی" to {
                    showBatchImportDialog = true
                },
                "ساخت کانفیگ دستی" to {
                    editingIndex = -1
                },
                "مدیریت لینک‌های اشتراک (Subscriptions)" to {
                    showSubsSheet = true
                }
            )
            "PING" -> listOf(
                "تست پینگ واقعی تونل (Real-Ping)" to {
                    val targets = if (multiSelectMode && selectedIndices.isNotEmpty()) selectedIndices.toList() else emptyList()
                    onRealTunnelPingConfigs(targets, false)
                    feedback = "تست پینگ واقعی تونل آغاز شد..."
                },
                "پینگ سریع TCP همه کانفیگ‌ها" to {
                    onQuickPingAll()
                    feedback = "پینگ سریع TCP آغاز شد..."
                },
                "ارسال کل مخزن به آزمایشگاه DPI" to {
                    onSendAllToDpiLab()
                }
            )
            "SORT" -> listOf(
                "مرتب‌سازی بر اساس پینگ (سریع‌ترین)" to {
                    onSortConfigs("ping")
                    feedback = "بر اساس پینگ مرتب شد."
                },
                "مرتب‌سازی بر اساس کشور و لوکیشن" to {
                    onSortConfigs("country")
                    feedback = "بر اساس کشور مرتب شد."
                },
                "مرتب‌سازی بر اساس نام" to {
                    onSortConfigs("name")
                    feedback = "بر اساس نام مرتب شد."
                },
                "مرتب‌سازی بر اساس پروتکل" to {
                    onSortConfigs("protocol")
                    feedback = "بر اساس پروتکل مرتب شد."
                }
            )
            else -> listOf(
                "بروزرسانی لینک‌های اشتراک (${subscriptions.size})" to {
                    onRefreshSubscriptions()
                    feedback = "در حال بروزرسانی اشتراک‌ها..."
                },
                "شکار بهترین کانفیگ و اتصال هوشمند" to {
                    onHuntBest()
                },
                "حذف کانفیگ‌های تکراری" to {
                    onDeduplicate()
                    feedback = "کانفیگ‌های تکراری حذف شدند."
                },
                "حذف کانفیگ‌های ازکارافتاده (DEAD)" to {
                    onRemoveDead()
                    feedback = "سرورهای ازکارافتاده حذف شدند."
                },
                "کپی همه کانفیگ‌های سالم" to {
                    val out = onExportVerifiedEarlyData()
                    if (out.isNotBlank()) {
                        copyPlainTextToClipboard(context, "VerifiedConfigs", out)
                        feedback = "کانفیگ‌های سالم در کلیپ‌بورد کپی شدند."
                    }
                },
                "خروجی Base64 همه کانفیگ‌ها" to {
                    val b64 = onShareOrExportSelected(emptyList(), true)
                    if (b64.isNotBlank()) {
                        copyPlainTextToClipboard(context, "Base64Sub", b64)
                        feedback = "خروجی Base64 در کلیپ‌بورد کپی شد."
                    }
                },
                "حذف همه کانفیگ‌ها" to {
                    onClearAll()
                    feedback = "تمام کانفیگ‌ها پاک شدند."
                }
            )
        }

        DarkActionMenuDialog(
            title = when (activeModalMenu) {
                "ADD" -> "افزودن کانفیگ یا اشتراک"
                "PING" -> "تست پینگ هوشمند و نام‌گذاری"
                "SORT" -> "مرتب‌سازی لیست کانفیگ‌ها"
                else -> "ابزارها و پاک‌سازی مخزن"
            },
            actions = actions,
            onDismiss = { activeModalMenu = "" }
        )
    }

    // Per-Item Context Menu Dialog
    val menuTargetIdx = itemMoreMenuIdx
    if (menuTargetIdx != null && menuTargetIdx in profiles.indices) {
        val targetItem = profiles[menuTargetIdx]
        DarkActionMenuDialog(
            title = targetItem.name.take(28),
            actions = listOf(
                "ویرایش کامل کانفیگ" to { editingIndex = menuTargetIdx },
                "کلون سریع با IP تمیز کلادفلر" to { cloneTargetIdx = menuTargetIdx },
                "ارسال به آزمایشگاه متد ضد DPI" to { onSendToMethodScanner(menuTargetIdx) },
                "نمایش QR Code کانفیگ" to { qrPreviewProfile = targetItem },
                "کپی لینک کانفیگ" to {
                    copyPlainTextToClipboard(context, targetItem.name, targetItem.uri)
                    feedback = "لینک کانفیگ کپی شد."
                },
                "اشتراک‌گذاری متنی" to {
                    sharePlainText(context, targetItem.name, targetItem.uri)
                },
                "حذف کانفیگ" to {
                    onDeleteConfig(menuTargetIdx)
                    feedback = "کانفیگ حذف شد."
                }
            ),
            onDismiss = { itemMoreMenuIdx = null }
        )
    }

    // QR Code Display Dialog
    val qrTarget = qrPreviewProfile
    if (qrTarget != null) {
        QrCodeDisplayDialog(
            title = qrTarget.name,
            subtitle = "${qrTarget.protocol} • ${qrTarget.host}:${qrTarget.port}",
            qrContent = qrTarget.uri.ifBlank { "${qrTarget.protocol.lowercase()}://${qrTarget.host}:${qrTarget.port}#${qrTarget.name}" },
            onDismiss = { qrPreviewProfile = null }
        )
    }

    // Batch Text / Links Import Dialog
    if (showBatchImportDialog) {
        BatchTextImportDialog(
            onDismiss = { showBatchImportDialog = false },
            onImport = { rawText ->
                showBatchImportDialog = false
                onImportConfigsRaw(rawText)
                feedback = "لینک‌های گروهی بررسی و وارد شدند."
            }
        )
    }

    if (showSubsSheet) {
        SubscriptionManagerSheet(subscriptions, { showSubsSheet = false }, onAddSubscription, onRefreshSubscriptions, onDeleteSubscription)
    }

    val quickCloneIdx = cloneTargetIdx
    if (quickCloneIdx != null && quickCloneIdx in profiles.indices) {
        DarkActionMenuDialog(
            title = "تزریق IP تمیز و کلون",
            actions = listOf(
                "شکار ۳ آی‌پی تمیز و کلون" to { onQuickCloneCleanIp(quickCloneIdx, 3); feedback = "در حال شکار ۳ آی‌پی تمیز..." },
                "شکار ۵ آی‌پی تمیز و کلون" to { onQuickCloneCleanIp(quickCloneIdx, 5); feedback = "در حال شکار ۵ آی‌پی تمیز..." },
                "شکار ۸ آی‌پی تمیز و کلون" to { onQuickCloneCleanIp(quickCloneIdx, 8); feedback = "در حال شکار ۸ آی‌پی تمیز..." }
            ),
            onDismiss = { cloneTargetIdx = null }
        )
    }

    val quickCloneIdx = cloneTargetIdx
    if (quickCloneIdx != null && quickCloneIdx in profiles.indices) {
        val targetProf = profiles[quickCloneIdx]
        DarkActionMenuDialog(
            title = "تزریق IP تمیز و کلون: ${targetProf.name.take(22)}",
            actions = listOf(
                "شکار ۳ آی‌پی تمیز و ساخت ۳ کلون" to {
                    onQuickCloneCleanIp(quickCloneIdx, 3)
                    feedback = "در حال ساخت ۳ کلون مجهز به IP تمیز..."
                },
                "شکار ۵ آی‌پی تمیز و ساخت ۵ کلون" to {
                    onQuickCloneCleanIp(quickCloneIdx, 5)
                    feedback = "در حال ساخت ۵ کلون مجهز به IP تمیز..."
                },
                "شکار ۸ آی‌پی تمیز و ساخت ۸ کلون" to {
                    onQuickCloneCleanIp(quickCloneIdx, 8)
                    feedback = "در حال ساخت ۸ کلون مجهز به IP تمیز..."
                }
            ),
            onDismiss = { cloneTargetIdx = null }
        )
    }

    val editIdx = editingIndex
    if (editIdx != null) {
        val initFields = remember(editIdx) { onGetEditable(editIdx) }
        ConfigEditorSheet(
            index = editIdx,
            initial = initFields,
            vectors = vectors,
            onDismiss = { editingIndex = null },
            onSave = { fields ->
                onSaveEditable(editIdx, fields)
                editingIndex = null
                feedback = "کانفیگ با موفقیت ذخیره شد."
            }
        )
    }
}

@Composable
private fun V2ServerRowCard(
    item: ProfileItem,
    multiSelectMode: Boolean,
    isChecked: Boolean,
    onSelect: () -> Unit,
    onPingSingle: () -> Unit,
    onShowQr: () -> Unit,
    onOpenActions: () -> Unit
) {
    val p = LocalAlanPalette.current
    val uriLow = remember(item.uri) { item.uri.lowercase(Locale.US) }
    val transportTag = remember(uriLow) {
        when {
            uriLow.contains("type=ws") || uriLow.contains("\"net\":\"ws\"") -> "ws"
            uriLow.contains("type=grpc") || uriLow.contains("\"net\":\"grpc\"") -> "grpc"
            uriLow.contains("type=httpupgrade") -> "httpupgrade"
            uriLow.contains("type=xhttp") || uriLow.contains("type=splithttp") -> "xhttp"
            else -> "tcp"
        }
    }
    val securityTag = remember(uriLow) {
        when {
            uriLow.contains("security=reality") -> "reality"
            uriLow.contains("security=tls") || uriLow.contains("\"tls\":\"tls\"") -> "tls"
            else -> "none"
        }
    }

    val pingColor = when {
        item.status.equals("DEAD", true) || item.pingMs < 0 -> p.danger
        item.pingMs in 1..260 -> p.emerald
        item.pingMs > 260 -> p.accentGold
        else -> p.textMuted
    }
    val pingText = when {
        item.status.equals("DEAD", true) || item.pingMs < 0 -> "timeout"
        item.pingMs > 0 -> "${item.pingMs} ms"
        else -> "—"
    }
    val vectorTag = item.cachedVectorId.ifBlank { item.preferredVectorId.ifBlank { "AUTO" } }
    val groupBadge = item.groupName.trim().let {
        if (it.isEmpty() || it.equals("Manual", true)) "دستی" else it
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(
                if (item.isSelected || isChecked) {
                    Modifier.alanNeuInset(cornerRadius = 15.dp, accented = true)
                } else {
                    Modifier.alanGlassCard(cornerRadius = 15.dp)
                }
            )
            .clickable(onClick = onSelect)
    ) {
        // v2rayNG-style Selection Indicator Bar
        Box(
            modifier = Modifier
                .width(if (item.isSelected || isChecked) 5.dp else 3.dp)
                .fillMaxHeight()
                .background(if (item.isSelected || isChecked) p.emerald else p.cardBorder)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Row 1: Remark Name + Numeric Ping
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (multiSelectMode) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .then(
                                    if (isChecked) {
                                        Modifier.clip(RoundedCornerShape(5.dp)).background(p.accentGold)
                                    } else {
                                        Modifier.alanNeuInset(cornerRadius = 5.dp)
                                    }
                                )
                        ) {
                            if (isChecked) {
                                Icon(Icons.Outlined.Check, null, tint = p.onAccent, modifier = Modifier.size(13.dp))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .alanNeuInset(cornerRadius = 6.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = groupBadge,
                            color = p.accentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = item.name.ifBlank { "${item.protocol} Server" },
                        color = p.textPrimary,
                        fontWeight = if (item.isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.width(8.dp))

                LtrContent {
                    Text(
                        text = pingText,
                        color = pingColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }

            // Row 2: v2rayNG Entity Metadata (Protocol | Transport | Security | Vector | Host:Port) + Action Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                LtrContent {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .alanNeuInset(cornerRadius = 5.dp)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.protocol.uppercase(Locale.US),
                                color = p.emerald,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .alanNeuInset(cornerRadius = 5.dp)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$transportTag+$securityTag",
                                color = p.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .alanNeuInset(cornerRadius = 5.dp)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = vectorTag,
                                color = p.accentGold,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }

                        Text(
                            text = "${item.host}:${item.port}",
                            color = p.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    SmallTactileActionBtn(Icons.Outlined.NetworkCheck, p.emerald, onPingSingle)
                    SmallTactileActionBtn(Icons.Outlined.QrCode2, p.accentGold, onShowQr)
                    SmallTactileActionBtn(Icons.Outlined.MoreVert, p.textSecondary, onOpenActions)
                }
            }
        }
    }
}

@Composable
private fun SmallTactileActionBtn(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(31.dp)
            .alanNeuInset(cornerRadius = 9.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun BatchTextImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val p = LocalAlanPalette.current
    var rawLinks by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = p.surfaceBg,
                border = BorderStroke(1.2.dp, p.cardBorder),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "وارد کردن لینک یا متن گروهی",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        AlanNeuIconButton(
                            icon = Icons.Outlined.Close,
                            tint = p.textSecondary,
                            size = 32.dp,
                            onClick = onDismiss
                        )
                    }

                    AlanSheetTextField(
                        label = "لینک‌های vless / vmess / trojan / ss یا متن Base64...",
                        value = rawLinks,
                        onValueChange = { rawLinks = it },
                        ltr = true,
                        singleLine = false
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .alanNeuInset(cornerRadius = 12.dp)
                                .clickable { onDismiss() }
                        ) {
                            Text("انصراف", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.accentGold)
                                .clickable {
                                    if (rawLinks.isNotBlank()) {
                                        onImport(rawLinks.trim())
                                    }
                                }
                        ) {
                            Text("افزودن به مخزن", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionManagerSheet(
    subscriptions: List<SubscriptionItem>,
    onDismiss: () -> Unit,
    onAdd: (url: String, title: String) -> Unit,
    onRefreshAll: () -> Unit,
    onDelete: (Int) -> Unit
) {
    val p = LocalAlanPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var subTitle by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.surfaceBg,
        contentColor = p.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "مدیریت لینک‌های اشتراک (Sub)",
                        color = p.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(34.dp)
                            .alanGlassCard(cornerRadius = 10.dp)
                            .clickable { onRefreshAll() }
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "بروزرسانی همه",
                            color = p.accentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                AlanSheetTextField(
                    label = "عنوان اشتراک",
                    value = subTitle,
                    onValueChange = { subTitle = it },
                    ltr = false
                )
                AlanSheetTextField(
                    label = "لینک اشتراک (https://...)",
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    ltr = true
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.accentGold)
                        .clickable {
                            if (subUrl.isNotBlank()) {
                                onAdd(subUrl.trim(), subTitle.trim().ifBlank { "Subscription" })
                                subUrl = ""
                                subTitle = ""
                            }
                        }
                ) {
                    Text(
                        text = "افزودن لینک اشتراک",
                        color = p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                subscriptions.forEachIndexed { idx, sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 13.dp)
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            LtrContent {
                                Text(
                                    text = sub.url,
                                    color = p.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallTactileActionBtn(Icons.Outlined.DeleteOutline, p.danger) { onDelete(idx) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorSheet(
    index: Int,
    initial: EditableConfigFields,
    vectors: List<VectorSpec>,
    onDismiss: () -> Unit,
    onSave: (EditableConfigFields) -> Unit
) {
    val p = LocalAlanPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initial.name) }
    var protocol by remember { mutableStateOf(initial.protocol) }
    var address by remember { mutableStateOf(initial.address) }
    var portText by remember { mutableStateOf(initial.port.toString()) }
    var uuidOrPassword by remember { mutableStateOf(initial.uuidOrPassword) }
    var network by remember { mutableStateOf(initial.network) }
    var security by remember { mutableStateOf(initial.security) }
    var sni by remember { mutableStateOf(initial.sni) }
    var hostHeader by remember { mutableStateOf(initial.hostHeader) }
    var path by remember { mutableStateOf(initial.path) }
    var alpn by remember { mutableStateOf(initial.alpn) }
    var fingerprint by remember { mutableStateOf(initial.fingerprint) }
    var flow by remember { mutableStateOf(initial.flow) }
    var publicKey by remember { mutableStateOf(initial.publicKey) }
    var shortId by remember { mutableStateOf(initial.shortId) }
    var preferredVectorId by remember { mutableStateOf(initial.preferredVectorId.ifBlank { "AUTO" }) }
    var rawUri by remember { mutableStateOf(initial.rawUri) }
    var useRawUri by remember { mutableStateOf(initial.useRawUri) }

    val vectorOptions = remember(vectors) { listOf(SelectOptionItem("AUTO", "خودکار (Auto)")) + vectors.map { SelectOptionItem(it.id, it.name) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.surfaceBg,
        contentColor = p.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (index < 0) "ساخت کانفیگ جدید" else "ویرایش کامل کانفیگ",
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanNeuInset(cornerRadius = 13.dp)
                        .clickable { useRawUri = !useRawUri }
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = "وارد کردن مستقیم لینک خام",
                        color = p.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    AlanPillSwitch(checked = useRawUri, onCheckedChange = { useRawUri = it })
                }

                if (useRawUri) {
                    AlanSheetTextField(
                        label = "لینک کانفیگ (vless / vmess / trojan / ss)",
                        value = rawUri,
                        onValueChange = { rawUri = it },
                        ltr = true,
                        singleLine = false
                    )
                } else {
                    AlanSheetTextField(
                        label = "نام سرور",
                        value = name,
                        onValueChange = { name = it },
                        ltr = false
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanCustomSelectBox(
                            label = "پروتکل",
                            selectedValue = protocol,
                            options = listOf("vless", "vmess", "trojan", "ss").map { SelectOptionItem(it, it.uppercase()) },
                            onSelect = { protocol = it },
                            modifier = Modifier.weight(1f)
                        )
                        AlanCustomSelectBox(
                            label = "نوع شبکه",
                            selectedValue = network,
                            options = listOf("ws", "tcp", "grpc", "httpupgrade", "xhttp").map { SelectOptionItem(it, it.uppercase()) },
                            onSelect = { network = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField(
                            label = "آدرس سرور",
                            value = address,
                            onValueChange = { address = it },
                            ltr = true,
                            modifier = Modifier.weight(1.6f)
                        )
                        AlanSheetTextField(
                            label = "پورت",
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit) },
                            ltr = true,
                            numeric = true,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    AlanSheetTextField(
                        label = "شناسه UUID / رمز عبور",
                        value = uuidOrPassword,
                        onValueChange = { uuidOrPassword = it },
                        ltr = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanCustomSelectBox(
                            label = "امنیت",
                            selectedValue = security,
                            options = listOf("tls", "reality", "none").map { SelectOptionItem(it, it.uppercase()) },
                            onSelect = { security = it },
                            modifier = Modifier.weight(1f)
                        )
                        AlanCustomSelectBox(
                            label = "متد عبور ضد DPI",
                            selectedValue = preferredVectorId,
                            options = vectorOptions,
                            onSelect = { preferredVectorId = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField("SNI", sni, { sni = it }, ltr = true, modifier = Modifier.weight(1f))
                        AlanSheetTextField("Host", hostHeader, { hostHeader = it }, ltr = true, modifier = Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField("مسیر (Path)", path, { path = it }, ltr = true, modifier = Modifier.weight(1.2f))
                        AlanCustomSelectBox(
                            label = "Fingerprint",
                            selectedValue = fingerprint,
                            options = listOf("custom_poly", "chrome", "firefox", "safari", "ios", "edge", "randomized")
                                .map { SelectOptionItem(it, it) },
                            onSelect = { fingerprint = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .alanNeuInset(cornerRadius = 12.dp)
                            .clickable { onDismiss() }
                    ) {
                        Text(
                            text = "انصراف",
                            color = p.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.accentGold)
                            .clickable {
                                onSave(
                                    EditableConfigFields(
                                        name = name.trim(),
                                        protocol = protocol,
                                        address = address.trim(),
                                        port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 443,
                                        uuidOrPassword = uuidOrPassword.trim(),
                                        network = network,
                                        security = security,
                                        sni = sni.trim(),
                                        hostHeader = hostHeader.trim(),
                                        path = path.trim(),
                                        alpn = alpn.trim(),
                                        fingerprint = fingerprint.trim(),
                                        flow = flow.trim(),
                                        publicKey = publicKey.trim(),
                                        shortId = shortId.trim(),
                                        preferredVectorId = preferredVectorId,
                                        rawUri = rawUri.trim(),
                                        useRawUri = useRawUri
                                    )
                                )
                            }
                    ) {
                        Text(
                            text = "ذخیره تغییرات",
                            color = p.onAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private fun decodeQrFromGalleryUri(context: Context, uri: Uri): String? {
    return try {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeStream(stream, null, opts)
        stream.close()
        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val result = MultiFormatReader().decode(binaryBitmap)
        result.text?.trim()
    } catch (_: Throwable) {
        null
    }
}