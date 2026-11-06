package com.alan.ripdpi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
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
    } catch (_: Throwable) {}
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
    onDeleteSubscription: (Int) -> Unit,
    onQuickCloneCleanIp: (Int, Int) -> Unit = { _, _ -> }
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current

    var feedback by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var activeModalMenu by remember { mutableStateOf("") }
    var showSubsSheet by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var cloneTargetIdx by remember { mutableStateOf<Int?>(null) }

    val indexedFiltered = remember(profiles, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        profiles.mapIndexedNotNull { idx, item ->
            if (q.isEmpty()) idx to item else {
                val hay = "${item.name} ${item.countryName} ${item.protocol} ${item.host} ${item.sni}".lowercase(Locale.ROOT)
                if (hay.contains(q)) idx to item else null
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().alanGlassCard(cornerRadius = 18.dp).padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("کانفیگ‌ها (${profiles.size})", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(feedback.ifBlank { "برای انتخاب روی سرور بزنید" }, color = if (feedback.isNotBlank()) p.emerald else p.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopIconBtn(Icons.Outlined.Add) { activeModalMenu = "ADD" }
                TopIconBtn(Icons.Outlined.NetworkCheck) { onQuickPingAll(); feedback = "تست پینگ آغاز شد..." }
                TopIconBtn(Icons.Outlined.Sort) { activeModalMenu = "SORT" }
                TopIconBtn(Icons.Outlined.MoreVert) { activeModalMenu = "MORE" }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AlanSheetTextField("جستجوی کانفیگ...", searchQuery, { searchQuery = it }, ltr = false, modifier = Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.alanGlassCard(cornerRadius = 14.dp).clickable { onImportFromClipboard(); feedback = "کلیپ‌بورد بررسی شد." }.padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ContentPaste, null, tint = p.accentGold, modifier = Modifier.size(18.dp))
                    Text("کلیپ‌بورد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (indexedFiltered.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp).alanNeuInset(cornerRadius = 20.dp).padding(24.dp)
                    ) {
                        Text(if (profiles.isEmpty()) "کانفیگی ثبت نشده است؛ اضافه کنید." else "موردی یافت نشد.", color = p.textMuted, fontSize = 13.sp)
                    }
                }
            } else {
                itemsIndexed(items = indexedFiltered, key = { idx, pair -> "$idx-${pair.first}-${pair.second.uid}" }) { _, (origIdx, item) ->
                    V2ServerRowCard(
                        item = item,
                        onSelect = { onSelectConfig(origIdx) },
                        onCloneCleanIp = { cloneTargetIdx = origIdx },
                        onScan = { onSendToMethodScanner(origIdx) },
                        onEdit = { editingIndex = origIdx },
                        onCopy = { copyPlainTextToClipboard(context, item.name, item.uri); feedback = "لینک کپی شد." },
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
                "مدیریت لینک‌های اشتراک" to { showSubsSheet = true }
            )
            "SORT" -> listOf(
                "مرتب‌سازی بر اساس پینگ" to { onSortConfigs("ping"); feedback = "بر اساس پینگ مرتب شد." },
                "مرتب‌سازی بر اساس کشور" to { onSortConfigs("country"); feedback = "بر اساس کشور مرتب شد." },
                "مرتب‌سازی بر اساس نام" to { onSortConfigs("name"); feedback = "بر اساس نام مرتب شد." }
            )
            else -> listOf(
                "بروزرسانی اشتراک‌ها (${subscriptions.size})" to { onRefreshSubscriptions(); feedback = "در حال بروزرسانی..." },
                "شکار بهترین کانفیگ" to { onHuntBest() },
                "حذف کانفیگ‌های تکراری" to { onDeduplicate(); feedback = "تکراری‌ها حذف شدند." },
                "پاک کردن سرورهای بدون پینگ" to { onRemoveDead(); feedback = "سرورهای خراب حذف شدند." },
                "حذف همه کانفیگ‌ها" to { onClearAll(); feedback = "همه کانفیگ‌ها پاک شدند." }
            )
        }

        DarkActionMenuDialog(
            title = when (activeModalMenu) { "ADD" -> "افزودن کانفیگ"; "SORT" -> "مرتب‌سازی لیست"; else -> "امکانات مخزن" },
            actions = actions,
            onDismiss = { activeModalMenu = "" }
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

    val editIdx = editingIndex
    if (editIdx != null) {
        val initFields = remember(editIdx) { onGetEditable(editIdx) }
        ConfigEditorSheet(
            index = editIdx,
            initial = initFields,
            vectors = vectors,
            onDismiss = { editingIndex = null },
            onSave = { fields -> onSaveEditable(editIdx, fields); editingIndex = null; feedback = "کانفیگ ذخیره شد." }
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
            Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.alanGlassCard(cornerRadius = 24.dp, highlighted = true).padding(20.dp)) {
                    Text(title, color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    actions.forEach { (label, act) ->
                        val isDanger = label.contains("حذف همه")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alanNeuInset(cornerRadius = 14.dp)
                                .clickable { onDismiss(); act() }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(label, color = if (isDanger) p.danger else p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopIconBtn(icon: ImageVector, onClick: () -> Unit) {
    val p = LocalAlanPalette.current
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).alanGlassCard(cornerRadius = 14.dp).clickable(onClick = onClick)) {
        Icon(icon, null, tint = p.textPrimary, modifier = Modifier.size(20.dp))
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
    val flagAccent = remember(item.accentPrimaryColor, p.accentGold) { item.accentPrimaryColor.toAdaptiveColor(p.accentGold) }
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
            .then(if (item.isSelected) Modifier.alanNeuInset(18.dp) else Modifier.alanGlassCard(cornerRadius = 18.dp))
            .clickable(onClick = onSelect)
    ) {
        if (item.isSelected) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(p.accentGold))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    FlagStripeBadge(item.flagStripeColors, item.countryCode, 26.dp, 18.dp)
                    Text(item.name, color = p.textPrimary, fontWeight = if (item.isSelected) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LtrContent {
                    Text(pingText, color = pingColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                LtrContent {
                    Text("${item.protocol} • ${item.host}:${item.port}", color = p.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallIconAct(Icons.Outlined.Bolt, p.emerald, onCloneCleanIp)
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
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).alanGlassCard(cornerRadius = 12.dp).clickable(onClick = onClick)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.Transparent) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().alanGlassCard(cornerRadius = 24.dp).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("لینک‌های اشتراک", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Box(modifier = Modifier.alanGlassCard(cornerRadius = 12.dp).clickable { onRefreshAll() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("بروزرسانی", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                AlanSheetTextField("نام اشتراک", subTitle, { subTitle = it }, ltr = false)
                AlanSheetTextField("لینک (https://...)", subUrl, { subUrl = it }, ltr = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().alanGlassCard(cornerRadius = 14.dp, highlighted = true).clickable { if (subUrl.isNotBlank()) { onAdd(subUrl.trim(), subTitle.trim()); subUrl = ""; subTitle = "" } }.padding(vertical = 14.dp)
                ) {
                    Text("افزودن لینک اشتراک", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }

                subscriptions.forEachIndexed { idx, sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().alanNeuInset(cornerRadius = 16.dp).padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            LtrContent { Text(sub.url, color = p.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        SmallIconAct(Icons.Outlined.DeleteOutline, p.danger) { onDelete(idx) }
                    }
                }
                Spacer(Modifier.height(16.dp))
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.Transparent) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().alanGlassCard(cornerRadius = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Text(if (index < 0) "ساخت کانفیگ جدید" else "ویرایش کانفیگ", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().alanNeuInset(cornerRadius = 14.dp).padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("وارد کردن مستقیم لینک خام", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    AlanPillSwitch(checked = useRawUri, onCheckedChange = { useRawUri = it })
                }

                if (useRawUri) {
                    AlanSheetTextField("لینک کانفیگ (vless / vmess / trojan / ss)", rawUri, { rawUri = it }, ltr = true, singleLine = false)
                } else {
                    AlanSheetTextField("نام سرور", name, { name = it }, ltr = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanCustomSelectBox("پروتکل", protocol, listOf("vless", "vmess", "trojan", "ss").map { SelectOptionItem(it, it.uppercase()) }, { protocol = it }, Modifier.weight(1f))
                        AlanCustomSelectBox("نوع شبکه", network, listOf("ws", "tcp", "grpc", "httpupgrade", "xhttp").map { SelectOptionItem(it, it.uppercase()) }, { network = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField("آدرس سرور", address, { address = it }, ltr = true, modifier = Modifier.weight(1.6f))
                        AlanSheetTextField("پورت", portText, { portText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(0.9f))
                    }
                    AlanSheetTextField("شناسه UUID / رمز عبور", uuidOrPassword, { uuidOrPassword = it }, ltr = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanCustomSelectBox("امنیت", security, listOf("tls", "reality", "none").map { SelectOptionItem(it, it.uppercase()) }, { security = it }, Modifier.weight(1f))
                        AlanCustomSelectBox("متد عبور", preferredVectorId, vectorOptions, { preferredVectorId = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        AlanSheetTextField("SNI", sni, { sni = it }, ltr = true, modifier = Modifier.weight(1f))
                        AlanSheetTextField("Host", hostHeader, { hostHeader = it }, ltr = true, modifier = Modifier.weight(1f))
                    }
                    AlanSheetTextField("مسیر (Path)", path, { path = it }, ltr = true)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).alanGlassCard(cornerRadius = 14.dp).clickable { onDismiss() }.padding(vertical = 14.dp)) {
                        Text("انصراف", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1.4f).alanGlassCard(cornerRadius = 14.dp, highlighted = true).clickable {
                        onSave(EditableConfigFields(name.trim(), protocol, address.trim(), portText.toIntOrNull() ?: 443, uuidOrPassword.trim(), network, security, sni.trim(), hostHeader.trim(), path.trim(), alpn.trim(), fingerprint.trim(), flow.trim(), publicKey.trim(), shortId.trim(), preferredVectorId, rawUri.trim(), useRawUri))
                    }.padding(vertical = 14.dp)) {
                        Text("ذخیره", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
            label = { Text(label, fontSize = 12.sp) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = TextStyle(color = p.textPrimary, fontFamily = if (ltr) FontFamily.Monospace else FontFamily.SansSerif, fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedLabelColor = p.accentGold,
                unfocusedLabelColor = p.textMuted,
                cursorColor = p.accentGold
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().alanNeuInset(cornerRadius = 14.dp)
        )
    }
    Box(modifier = modifier) {
        if (ltr) LtrContent { field() } else field()
    }
}