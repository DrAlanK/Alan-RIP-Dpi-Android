package com.alan.ripdpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.alan.ripdpi.model.CountryPaletteRegistry
import java.util.Locale

@Composable
fun ProfilesTabScreen(
    profiles: List<ProfileItem>,
    subscriptions: List<SubscriptionItem>,
    vectors: List<VectorSpec>,
    onSelectConfig: (Int) -> Unit,
    onImportFromClipboard: () -> Unit,
    onHuntBest: () -> Unit,
    onQuickPingAll: () -> Unit,
    onSortConfigs: (String) -> Unit,
    onDeduplicate: () -> Unit,
    onRemoveDead: () -> Unit,
    onExportVerifiedEarlyData: () -> String,
    onClearAll: () -> Unit,
    onDeleteConfig: (Int) -> Unit,
    onGetEditable: (Int) -> EditableConfigFields,
    onSaveEditable: (Int, EditableConfigFields) -> Unit,
    onSendToMethodScanner: (Int) -> Unit,
    onAddSubscription: (String, String) -> Unit,
    onRefreshSubscriptions: () -> Unit,
    onDeleteSubscription: (Int) -> Unit,
    onQuickCloneCleanIp: (Int, Int) -> Unit = { _, _ -> }
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current
    val isFa = LocalAlanLang.current == AlanLang.FA

    var feedback by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var countryFilter by remember { mutableStateOf("ALL") }
    var activeModalMenu by remember { mutableStateOf("") }
    var showSubsSheet by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var cloneTargetIdx by remember { mutableStateOf<Int?>(null) }

    val availableCountries = remember(profiles) {
        profiles
            .map { it.countryCode.uppercase(Locale.US) }
            .distinct()
            .filter { it.isNotBlank() && it != "UN" }
    }

    val indexedFiltered = remember(profiles, searchQuery, countryFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        profiles.mapIndexedNotNull { idx, item ->
            val matchCountry = countryFilter == "ALL" || item.countryCode.equals(countryFilter, ignoreCase = true)
            if (!matchCountry) {
                null
            } else if (q.isEmpty()) {
                idx to item
            } else {
                val hay = "${item.name} ${item.countryName} ${item.protocol} ${item.host} ${item.sni}".lowercase(Locale.ROOT)
                if (hay.contains(q)) idx to item else null
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Top Header & Actions Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 15.dp)
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr("کانفیگ‌ها (${profiles.size})", "Profiles (${profiles.size})"),
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = feedback.ifBlank {
                        tr("برای انتخاب روی سرور بزنید", "Tap any server to select")
                    },
                    color = if (feedback.isNotBlank()) p.emerald else p.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TopIconBtn(Icons.Outlined.Add, accent = true) { activeModalMenu = "ADD" }
                TopIconBtn(Icons.Outlined.NetworkCheck) {
                    onQuickPingAll()
                    feedback = if (isFa) "تست پینگ آغاز شد..." else "Pinging all configs..."
                }
                TopIconBtn(Icons.Outlined.Sort) { activeModalMenu = "SORT" }
                TopIconBtn(Icons.Outlined.MoreVert) { activeModalMenu = "MORE" }
            }
        }

        // Aligned 48.dp Search + Paste + Quick Clean-IP Clone Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AlanSheetTextField(
                label = tr("جستجوی کانفیگ...", "Search configs..."),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                ltr = false,
                modifier = Modifier.weight(1f)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(48.dp)
                    .alanGlassCard(cornerRadius = 12.dp)
                    .clickable {
                        onImportFromClipboard()
                        feedback = if (isFa) "کلیپ‌بورد بررسی شد." else "Imported from clipboard."
                    }
                    .padding(horizontal = 11.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.ContentPaste, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
                    Text(
                        text = tr("کلیپ‌بورد", "Paste"),
                        color = p.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }

            if (profiles.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.accentGold)
                        .clickable {
                            val selIdx = profiles.indexOfFirst { it.isSelected }.let { if (it >= 0) it else 0 }
                            cloneTargetIdx = selIdx
                        }
                        .padding(horizontal = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.Bolt, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                        Text(
                            text = tr("کلون IP", "Clean IP"),
                            color = p.onAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // Country Location Filter Chips (Auto-shown when 2+ countries exist)
        if (availableCountries.size >= 2) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                val allSelected = countryFilter == "ALL"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(32.dp)
                        .then(
                            if (allSelected) {
                                Modifier
                                    .alanNeuInset(10.dp)
                                    .border(1.dp, p.accentGold, RoundedCornerShape(10.dp))
                            } else {
                                Modifier.alanGlassCard(cornerRadius = 10.dp)
                            }
                        )
                        .clickable { countryFilter = "ALL" }
                        .padding(horizontal = 11.dp)
                ) {
                    Text(
                        text = tr("همه لوکیشن‌ها", "All Locations"),
                        color = if (allSelected) p.accentGold else p.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                availableCountries.forEach { code ->
                    val spec = CountryPaletteRegistry.resolveByCode(code)
                    val sel = countryFilter.equals(code, ignoreCase = true)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .then(
                                if (sel) {
                                    Modifier
                                        .alanNeuInset(10.dp)
                                        .border(1.dp, p.accentGold, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier.alanGlassCard(cornerRadius = 10.dp)
                                }
                            )
                            .clickable { countryFilter = if (sel) "ALL" else code }
                            .padding(horizontal = 10.dp)
                    ) {
                        FlagStripeBadge(spec.stripeColors, code, 18.dp, 12.dp)
                        Text(
                            text = if (isFa) spec.nameFa else spec.nameEn,
                            color = if (sel) p.accentGold else p.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Profiles List
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
                            .alanNeuInset(cornerRadius = 16.dp)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = if (profiles.isEmpty()) {
                                tr(
                                    "کانفیگی ثبت نشده است؛ از دکمه + یا کلیپ‌بورد اضافه کنید.",
                                    "No configs yet. Tap + or Paste to add servers."
                                )
                            } else {
                                tr("موردی مطابق جستجو یافت نشد.", "No matching configs found.")
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
                    V2ServerRowCard(
                        item = item,
                        onSelect = { onSelectConfig(origIdx) },
                        onCloneCleanIp = { cloneTargetIdx = origIdx },
                        onScan = { onSendToMethodScanner(origIdx) },
                        onEdit = { editingIndex = origIdx },
                        onCopy = {
                            copyPlainTextToClipboard(context, item.name, item.uri)
                            feedback = if (isFa) "لینک سرور کپی شد." else "Server URI copied."
                        },
                        onDelete = { onDeleteConfig(origIdx) }
                    )
                }
            }
        }
    }

    if (activeModalMenu.isNotEmpty()) {
        val actions: List<Pair<String, () -> Unit>> = when (activeModalMenu) {
            "ADD" -> listOf(
                tr("وارد کردن از کلیپ‌بورد", "Import from Clipboard") to {
                    onImportFromClipboard()
                    feedback = if (isFa) "از کلیپ‌بورد وارد شد." else "Imported from clipboard."
                },
                tr("ساخت کانفیگ دستی", "Create Custom Config") to { editingIndex = -1 },
                tr("مدیریت لینک‌های اشتراک (Sub)", "Manage Subscriptions") to { showSubsSheet = true }
            )
            "SORT" -> listOf(
                tr("مرتب‌سازی بر اساس پینگ", "Sort by Ping") to {
                    onSortConfigs("ping")
                    feedback = if (isFa) "بر اساس پینگ مرتب شد." else "Sorted by ping."
                },
                tr("مرتب‌سازی بر اساس کشور", "Sort by Country") to {
                    onSortConfigs("country")
                    feedback = if (isFa) "بر اساس کشور مرتب شد." else "Sorted by country."
                },
                tr("مرتب‌سازی بر اساس نام", "Sort by Name") to {
                    onSortConfigs("name")
                    feedback = if (isFa) "بر اساس نام مرتب شد." else "Sorted by name."
                }
            )
            else -> listOf(
                tr("بروزرسانی اشتراک‌ها (${subscriptions.size})", "Update Subscriptions (${subscriptions.size})") to {
                    onRefreshSubscriptions()
                    feedback = if (isFa) "در حال بروزرسانی اشتراک‌ها..." else "Updating subscriptions..."
                },
                tr("شکار بهترین کانفیگ و اتصال", "Hunt Best & Connect") to { onHuntBest() },
                tr("حذف کانفیگ‌های تکراری", "Remove Duplicates") to {
                    onDeduplicate()
                    feedback = if (isFa) "تکراری‌ها حذف شدند." else "Duplicates removed."
                },
                tr("پاک کردن سرورهای بدون پینگ", "Remove Dead Configs") to {
                    onRemoveDead()
                    feedback = if (isFa) "سرورهای بدون پینگ حذف شدند." else "Dead configs removed."
                },
                tr("کپی همه کانفیگ‌های سالم", "Export Verified Configs") to {
                    val out = onExportVerifiedEarlyData()
                    if (out.isNotBlank()) {
                        copyPlainTextToClipboard(context, "Configs", out)
                        feedback = if (isFa) "کانفیگ‌های سالم کپی شدند." else "Verified configs copied."
                    }
                },
                tr("حذف همه کانفیگ‌ها", "Clear All Configs") to {
                    onClearAll()
                    feedback = if (isFa) "همه کانفیگ‌ها پاک شدند." else "All configs cleared."
                }
            )
        }

        DarkActionMenuDialog(
            title = when (activeModalMenu) {
                "ADD" -> tr("افزودن کانفیگ", "Add Config")
                "SORT" -> tr("مرتب‌سازی لیست", "Sort Profiles")
                else -> tr("ابزارهای مخزن", "Profile Tools")
            },
            actions = actions,
            onDismiss = { activeModalMenu = "" }
        )
    }

    if (showSubsSheet) {
        SubscriptionManagerSheet(
            subscriptions = subscriptions,
            onDismiss = { showSubsSheet = false },
            onAdd = onAddSubscription,
            onRefreshAll = onRefreshSubscriptions,
            onDelete = onDeleteSubscription
        )
    }

    val quickCloneIdx = cloneTargetIdx
    if (quickCloneIdx != null && quickCloneIdx in profiles.indices) {
        val targetProf = profiles[quickCloneIdx]
        DarkActionMenuDialog(
            title = tr(
                "کلون با IP تمیز: ${targetProf.name.take(22)}",
                "Clean IP Clone: ${targetProf.name.take(22)}"
            ),
            actions = listOf(
                tr("شکار ۳ آی‌پی تمیز و ساخت ۳ کلون", "Hunt 3 Clean IPs & Clone x3") to {
                    onQuickCloneCleanIp(quickCloneIdx, 3)
                    feedback = if (isFa) "در حال ساخت ۳ کلون با IP تمیز..." else "Cloning x3 with Clean IPs..."
                },
                tr("شکار ۵ آی‌پی تمیز و ساخت ۵ کلون", "Hunt 5 Clean IPs & Clone x5") to {
                    onQuickCloneCleanIp(quickCloneIdx, 5)
                    feedback = if (isFa) "در حال ساخت ۵ کلون با IP تمیز..." else "Cloning x5 with Clean IPs..."
                },
                tr("شکار ۸ آی‌پی تمیز و ساخت ۸ کلون", "Hunt 8 Clean IPs & Clone x8") to {
                    onQuickCloneCleanIp(quickCloneIdx, 8)
                    feedback = if (isFa) "در حال ساخت ۸ کلون با IP تمیز..." else "Cloning x8 with Clean IPs..."
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
                feedback = if (isFa) "کانفیگ ذخیره شد." else "Config saved."
            }
        )
    }
}

@Composable
private fun TopIconBtn(
    icon: ImageVector,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val p = LocalAlanPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .then(
                if (accent) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.accentGold)
                } else {
                    Modifier.alanNeuInset(cornerRadius = 10.dp)
                }
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) p.onAccent else p.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun V2ServerRowCard(
    item: ProfileItem,
    onSelect: () -> Unit,
    onCloneCleanIp: () -> Unit,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalAlanPalette.current
    val flagAccent = item.accentPrimaryColor.toThemeAwareAccent(p.accentGold)
    val pingColor = when {
        item.status.equals("DEAD", true) || item.pingMs < 0 -> p.danger
        item.pingMs in 1..260 -> p.emerald
        item.pingMs > 260 -> p.accentGold
        else -> p.textMuted
    }
    val pingText = when {
        item.status.equals("DEAD", true) || item.pingMs < 0 -> "Timeout"
        item.pingMs > 0 -> "${item.pingMs} ms"
        else -> "—"
    }
    val vectorTag = item.cachedVectorId.ifBlank { item.preferredVectorId.ifBlank { "AUTO" } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(
                if (item.isSelected) {
                    Modifier
                        .alanNeuInset(15.dp)
                        .border(1.3.dp, p.accentGold, RoundedCornerShape(15.dp))
                } else {
                    Modifier.alanGlassCard(cornerRadius = 15.dp)
                }
            )
            .clickable(onClick = onSelect)
    ) {
        Box(
            modifier = Modifier
                .width(if (item.isSelected) 5.dp else 3.5.dp)
                .fillMaxHeight()
                .background(if (item.isSelected) p.accentGold else flagAccent.copy(alpha = 0.7f))
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
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
                    FlagStripeBadge(item.flagStripeColors, item.countryCode, 23.dp, 15.dp)
                    Text(
                        text = item.name,
                        color = p.textPrimary,
                        fontWeight = if (item.isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(6.dp))

                LtrContent {
                    Text(
                        text = pingText,
                        color = pingColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .alanNeuInset(cornerRadius = 6.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        LtrContent {
                            Text(
                                text = vectorTag,
                                color = p.accentGold,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp
                            )
                        }
                    }

                    LtrContent {
                        Text(
                            text = "${item.protocol} • ${item.host}:${item.port}",
                            color = p.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallIconAct(Icons.Outlined.Bolt, p.emerald, onCloneCleanIp)
                    SmallIconAct(Icons.Outlined.Radar, p.accentGold, onScan)
                    SmallIconAct(Icons.Outlined.Edit, p.textSecondary, onEdit)
                    SmallIconAct(Icons.Outlined.ContentCopy, p.textSecondary, onCopy)
                    SmallIconAct(Icons.Outlined.DeleteOutline, p.danger, onDelete)
                }
            }
        }
    }
}

@Composable
private fun SmallIconAct(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .alanNeuInset(cornerRadius = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
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
    val layoutDir = if (LocalAlanLang.current == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
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
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
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
                        text = tr("لینک‌های اشتراک", "Subscriptions"),
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
                            text = tr("بروزرسانی همه", "Refresh All"),
                            color = p.accentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                AlanSheetTextField(
                    label = tr("نام اشتراک", "Subscription Title"),
                    value = subTitle,
                    onValueChange = { subTitle = it },
                    ltr = false
                )
                AlanSheetTextField(
                    label = "URL (https://...)",
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
                                onAdd(subUrl.trim(), subTitle.trim().ifBlank { "Sub" })
                                subUrl = ""
                                subTitle = ""
                            }
                        }
                ) {
                    Text(
                        text = tr("افزودن لینک اشتراک", "Add Subscription"),
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
                            .alanNeuInset(cornerRadius = 12.dp)
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
                        SmallIconAct(Icons.Outlined.DeleteOutline, p.danger) { onDelete(idx) }
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
    val layoutDir = if (LocalAlanLang.current == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
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

    val vectorOptions = remember(vectors) {
        listOf(SelectOptionItem("AUTO", "AUTO")) +
            vectors.map { SelectOptionItem(it.id, it.name) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.surfaceBg,
        contentColor = p.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            Column(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (index < 0) tr("ساخت کانفیگ جدید", "New Config") else tr("ویرایش کانفیگ", "Edit Config"),
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanNeuInset(cornerRadius = 12.dp)
                        .clickable { useRawUri = !useRawUri }
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = tr("وارد کردن مستقیم لینک خام", "Direct Raw URI Mode"),
                        color = p.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    AlanPillSwitch(checked = useRawUri, onCheckedChange = { useRawUri = it })
                }

                if (useRawUri) {
                    AlanSheetTextField(
                        label = "URI (vless / vmess / trojan / ss)",
                        value = rawUri,
                        onValueChange = { rawUri = it },
                        ltr = true,
                        singleLine = false
                    )
                } else {
                    AlanSheetTextField(
                        label = tr("نام سرور", "Remarks / Name"),
                        value = name,
                        onValueChange = { name = it },
                        ltr = false
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanCustomSelectBox(
                            label = tr("پروتکل", "Protocol"),
                            selectedValue = protocol,
                            options = listOf("vless", "vmess", "trojan", "ss").map { SelectOptionItem(it, it.uppercase()) },
                            onSelect = { protocol = it },
                            modifier = Modifier.weight(1f)
                        )
                        AlanCustomSelectBox(
                            label = tr("نوع شبکه", "Network"),
                            selectedValue = network,
                            options = listOf("ws", "tcp", "grpc", "httpupgrade", "xhttp").map { SelectOptionItem(it, it.uppercase()) },
                            onSelect = { network = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField(
                            label = tr("آدرس سرور", "Address"),
                            value = address,
                            onValueChange = { address = it },
                            ltr = true,
                            modifier = Modifier.weight(1.6f)
                        )
                        AlanSheetTextField(
                            label = tr("پورت", "Port"),
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit) },
                            ltr = true,
                            numeric = true,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    AlanSheetTextField(
                        label = tr("شناسه UUID / رمز عبور", "UUID / Password"),
                        value = uuidOrPassword,
                        onValueChange = { uuidOrPassword = it },
                        ltr = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanCustomSelectBox(
                            label = tr("امنیت", "Security"),
                            selectedValue = security,
                            options = listOf("tls", "reality", "none").map { SelectOptionItem(it, it.uppercase()) },
                            onSelect = { security = it },
                            modifier = Modifier.weight(1f)
                        )
                        AlanCustomSelectBox(
                            label = tr("متد عبور", "DPI Vector"),
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
                        AlanSheetTextField("Path", path, { path = it }, ltr = true, modifier = Modifier.weight(1.2f))
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
                            text = tr("انصراف", "Cancel"),
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
                            text = tr("ذخیره", "Save"),
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