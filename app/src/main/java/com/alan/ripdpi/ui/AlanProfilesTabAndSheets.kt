package com.alan.ripdpi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

fun copyPlainTextToClipboard(context: Context, label: String, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}

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
    onDeleteSubscription: (Int) -> Unit
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current

    var feedback by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var activeModalMenu by remember { mutableStateOf("") }
    var showSubsSheet by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val indexedFiltered = remember(profiles, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        profiles.mapIndexedNotNull { idx, item ->
            if (q.isEmpty()) idx to item
            else {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 14.dp)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "کانفیگ‌ها (${profiles.size})",
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = feedback.ifBlank { "برای انتخاب روی سرور بزنید" },
                    color = if (feedback.isNotBlank()) p.emerald else p.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TopIconBtn(Icons.Outlined.Add, accent = true) { activeModalMenu = "ADD" }
                TopIconBtn(Icons.Outlined.NetworkCheck) {
                    onQuickPingAll()
                    feedback = "تست پینگ آغاز شد..."
                }
                TopIconBtn(Icons.Outlined.Sort) { activeModalMenu = "SORT" }
                TopIconBtn(Icons.Outlined.MoreVert) { activeModalMenu = "MORE" }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AlanSheetTextField(
                label = "جستجوی کانفیگ...",
                value = searchQuery,
                onValueChange = { searchQuery = it },
                ltr = false,
                modifier = Modifier.weight(1f)
            )
            Surface(
                onClick = {
                    onImportFromClipboard()
                    feedback = "کلیپ‌بورد بررسی شد."
                },
                shape = RoundedCornerShape(11.dp),
                color = p.cardBg,
                border = BorderStroke(1.dp, p.glowBorder),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Outlined.ContentPaste, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
                    Text("کلیپ‌بورد", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            .padding(vertical = 44.dp)
                            .alanGlassCard(cornerRadius = 15.dp)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = if (profiles.isEmpty()) "کانفیگی ثبت نشده است؛ از دکمه + یا کلیپ‌بورد اضافه کنید." else "موردی یافت نشد.",
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
                        onScan = { onSendToMethodScanner(origIdx) },
                        onEdit = { editingIndex = origIdx },
                        onCopy = {
                            copyPlainTextToClipboard(context, item.name, item.uri)
                            feedback = "لینک سرور کپی شد."
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
                "وارد کردن از کلیپ‌بورد" to { onImportFromClipboard(); feedback = "از کلیپ‌بورد وارد شد." },
                "ساخت کانفیگ دستی" to { editingIndex = -1 },
                "مدیریت لینک‌های اشتراک (Sub)" to { showSubsSheet = true }
            )
            "SORT" -> listOf(
                "مرتب‌سازی بر اساس پینگ" to { onSortConfigs("ping"); feedback = "بر اساس پینگ مرتب شد." },
                "مرتب‌سازی بر اساس کشور" to { onSortConfigs("country"); feedback = "بر اساس کشور مرتب شد." },
                "مرتب‌سازی بر اساس نام" to { onSortConfigs("name"); feedback = "بر اساس نام مرتب شد." }
            )
            else -> listOf(
                "بروزرسانی اشتراک‌ها (${subscriptions.size})" to { onRefreshSubscriptions(); feedback = "در حال بروزرسانی..." },
                "شکار بهترین کانفیگ و اتصال" to { onHuntBest() },
                "حذف کانفیگ‌های تکراری" to { onDeduplicate(); feedback = "تکراری‌ها حذف شدند." },
                "پاک کردن سرورهای بدون پینگ" to { onRemoveDead(); feedback = "سرورهای خراب حذف شدند." },
                "کپی همه کانفیگ‌های سالم" to {
                    val out = onExportVerifiedEarlyData()
                    if (out.isNotBlank()) {
                        copyPlainTextToClipboard(context, "Configs", out)
                        feedback = "در کلیپ‌بورد کپی شد."
                    }
                },
                "حذف همه کانفیگ‌ها" to { onClearAll(); feedback = "همه کانفیگ‌ها پاک شدند." }
            )
        }

        DarkActionMenuDialog(
            title = when (activeModalMenu) {
                "ADD" -> "افزودن کانفیگ"
                "SORT" -> "مرتب‌سازی لیست"
                else -> "امکانات مخزن"
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
                feedback = "کانفیگ ذخیره شد."
            }
        )
    }
}

@Composable
private fun DarkActionMenuDialog(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    val p = LocalAlanPalette.current
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = p.surfaceBg,
                border = BorderStroke(1.2.dp, p.glowBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(title, color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    actions.forEach { (label, act) ->
                        val isDanger = label.contains("حذف همه")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.subtleBg)
                                .border(1.dp, p.cardBorder, RoundedCornerShape(11.dp))
                                .clickable {
                                    onDismiss()
                                    act()
                                }
                                .padding(horizontal = 13.dp, vertical = 11.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isDanger) p.danger else p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopIconBtn(
    icon: ImageVector,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val p = LocalAlanPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (accent) p.accentGold else p.subtleBg,
        border = BorderStroke(1.dp, if (accent) p.accentGold else p.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(35.dp)) {
            Icon(icon, null, tint = if (accent) p.onAccent else p.textPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun V2ServerRowCard(
    item: ProfileItem,
    onSelect: () -> Unit,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalAlanPalette.current
    val flagAccent = remember(item.accentPrimaryColor, p.accentGold) {
        item.accentPrimaryColor.toAdaptiveColor(p.accentGold)
    }
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(13.dp))
            .background(if (item.isSelected) p.accentSoftBg else p.cardBg)
            .border(
                width = if (item.isSelected) 1.4.dp else 1.dp,
                color = if (item.isSelected) p.accentGold else p.cardBorder,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onSelect)
    ) {
        Box(
            modifier = Modifier
                .width(if (item.isSelected) 5.5.dp else 3.5.dp)
                .fillMaxHeight()
                .background(if (item.isSelected) p.accentGold else flagAccent.copy(alpha = 0.55f))
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 11.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FlagStripeBadge(item.flagStripeColors, item.countryCode, 22.dp, 14.dp)
                    Text(
                        text = item.name,
                        color = p.textPrimary,
                        fontWeight = if (item.isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                LtrContent {
                    Text(
                        text = "${item.protocol} • ${item.host}:${item.port}",
                        color = p.textMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SmallIconAct(Icons.Outlined.Radar, p.accentGold, onScan)
                    SmallIconAct(Icons.Outlined.Edit, p.textSecondary, onEdit)
                    SmallIconAct(Icons.Outlined.Share, p.textSecondary, onCopy)
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
            .size(29.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
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
        containerColor = p.surfaceBg
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Text("لینک‌های اشتراک", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Surface(onClick = onRefreshAll, shape = RoundedCornerShape(9.dp), color = p.accentGold) {
                        Text("بروزرسانی", color = p.onAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                AlanSheetTextField("نام اشتراک", subTitle, { subTitle = it }, ltr = false)
                AlanSheetTextField("لینک (https://...)", subUrl, { subUrl = it }, ltr = true)
                Surface(
                    onClick = {
                        if (subUrl.isNotBlank()) {
                            onAdd(subUrl.trim(), subTitle.trim())
                            subUrl = ""
                            subTitle = ""
                        }
                    },
                    shape = RoundedCornerShape(11.dp),
                    color = p.accentGold,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                        Text("افزودن لینک اشتراک", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }

                subscriptions.forEachIndexed { idx, sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(p.subtleBg)
                            .border(1.dp, p.cardBorder, RoundedCornerShape(11.dp))
                            .padding(11.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            LtrContent {
                                Text(sub.url, color = p.textMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        SmallIconAct(Icons.Outlined.DeleteOutline, p.danger) { onDelete(idx) }
                    }
                }
                Spacer(Modifier.height(8.dp))
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

    val vectorOptions = remember(vectors) {
        listOf(SelectOptionItem("AUTO", "خودکار (Auto)")) +
            vectors.map { SelectOptionItem(it.id, it.name) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.surfaceBg
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (index < 0) "ساخت کانفیگ جدید" else "ویرایش کانفیگ",
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(p.subtleBg)
                        .border(1.dp, p.cardBorder, RoundedCornerShape(11.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text("وارد کردن مستقیم لینک خام", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    AlanPillSwitch(checked = useRawUri, onCheckedChange = { useRawUri = it })
                }

                if (useRawUri) {
                    AlanSheetTextField("لینک کانفیگ (vless / vmess / trojan / ss)", rawUri, { rawUri = it }, ltr = true, singleLine = false)
                } else {
                    AlanSheetTextField("نام سرور", name, { name = it }, ltr = false)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField("آدرس سرور", address, { address = it }, ltr = true, modifier = Modifier.weight(1.6f))
                        AlanSheetTextField("پورت", portText, { portText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(0.9f))
                    }

                    AlanSheetTextField("شناسه UUID / رمز عبور", uuidOrPassword, { uuidOrPassword = it }, ltr = true)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanCustomSelectBox(
                            label = "امنیت",
                            selectedValue = security,
                            options = listOf("tls", "reality", "none").map { SelectOptionItem(it, it.uppercase()) },
                            onSelect = { security = it },
                            modifier = Modifier.weight(1f)
                        )
                        AlanCustomSelectBox(
                            label = "متد عبور",
                            selectedValue = preferredVectorId,
                            options = vectorOptions,
                            onSelect = { preferredVectorId = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField("SNI", sni, { sni = it }, ltr = true, modifier = Modifier.weight(1f))
                        AlanSheetTextField("Host", hostHeader, { hostHeader = it }, ltr = true, modifier = Modifier.weight(1f))
                    }

                    AlanSheetTextField("مسیر (Path)", path, { path = it }, ltr = true)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 14.dp)
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(11.dp),
                        color = p.subtleBg,
                        border = BorderStroke(1.dp, p.cardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 11.dp)) {
                            Text("انصراف", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Surface(
                        onClick = {
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
                        },
                        shape = RoundedCornerShape(11.dp),
                        color = p.accentGold,
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 11.dp)) {
                            Text("ذخیره", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlanSheetTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ltr: Boolean,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val p = LocalAlanPalette.current
    val field: @Composable () -> Unit = {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 11.sp) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = TextStyle(
                color = p.textPrimary,
                fontFamily = if (ltr) FontFamily.Monospace else FontFamily.SansSerif,
                fontSize = 12.sp
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.accentGold,
                unfocusedBorderColor = p.cardBorder,
                focusedLabelColor = p.accentGold,
                unfocusedLabelColor = p.textMuted,
                cursorColor = p.accentGold
            ),
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
    Box(modifier = modifier) {
        if (ltr) LtrContent { field() } else field()
    }
}
