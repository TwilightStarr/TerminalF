package com.terminalf.app.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.LocalContainer
import com.terminalf.app.core.FeatureShortcut
import com.terminalf.app.core.RecommendedApp
import com.terminalf.app.core.formatBytes
import com.terminalf.app.core.formatDuration
import com.terminalf.app.data.DeviceStaticInfo
import com.terminalf.app.data.StorageReading
import com.terminalf.app.data.appVersion
import com.terminalf.app.state.AppSettings
import com.terminalf.app.state.LiveStats
import com.terminalf.app.state.Motion
import com.terminalf.app.state.Notifier
import com.terminalf.app.state.clearBrightness
import com.terminalf.app.state.getBrightness
import com.terminalf.app.state.getKeepScreenOn
import com.terminalf.app.state.setBrightness
import com.terminalf.app.state.setKeepScreenOn
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.components.AppLogo
import com.terminalf.app.ui.components.EaseOutCubicCurve
import com.terminalf.app.ui.components.InfoRow
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.RowDivider
import com.terminalf.app.ui.components.SectionHeader
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class DeviceSub { Appearance, Settings }

/** Anahtar (Switch) renkleri: Flutter `switchTheme` karşılığı. */
@Composable
internal fun appSwitchColors(): SwitchColors {
    val c = AppTheme.colors
    return SwitchDefaults.colors(
        checkedThumbColor = c.bg,
        checkedTrackColor = c.primary,
        checkedBorderColor = c.primary,
        uncheckedThumbColor = c.muted,
        uncheckedTrackColor = c.surfaceHigh,
        uncheckedBorderColor = c.outline,
        disabledCheckedThumbColor = c.bg,
        disabledCheckedTrackColor = c.primary,
        disabledCheckedBorderColor = c.primary,
        disabledUncheckedThumbColor = c.muted,
        disabledUncheckedTrackColor = c.surfaceHigh,
        disabledUncheckedBorderColor = c.outline,
    )
}

/** Kaydırıcı (Slider) renkleri: Flutter `sliderTheme` karşılığı. */
@Composable
internal fun appSliderColors(): SliderColors {
    val c = AppTheme.colors
    val activeTick = c.bg.copy(alpha = 0.5f)
    val inactiveTick = c.muted.copy(alpha = 0.5f)
    return SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        activeTickColor = activeTick,
        inactiveTrackColor = c.outline,
        inactiveTickColor = inactiveTick,
        disabledThumbColor = c.primary,
        disabledActiveTrackColor = c.primary,
        disabledActiveTickColor = activeTick,
        disabledInactiveTrackColor = c.outline,
        disabledInactiveTickColor = inactiveTick,
    )
}

/**
 * Alt sayfa çerçevesi (Flutter `Scaffold` + `AppBar` karşılığı): geri okunlu başlık çubuğu ve
 * kaydırılabilir içerik. Alttaki sayfaya dokunuşlar geçmesin diye dokunmaları tüketir.
 */
@Composable
internal fun SubPageFrame(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(c.bg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(56.dp).height(56.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri", tint = c.text)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
            content = content,
        )
    }
}

/**
 * Cihaz: bilgiler, ekran ve izin ayarları, sistem kısayolları, önerilen uygulamalar.
 * Görünüm ve Bildirimler sayfaları (Flutter'da `Navigator.push`) burada yerinde alt ekran olarak açılır.
 */
@Composable
fun DevicePage(live: LiveStats, settings: AppSettings, notifier: Notifier) {
    var sub by remember { mutableStateOf<DeviceSub?>(null) }
    BackHandler(enabled = sub != null) { sub = null }

    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(Motion.d(300), easing = EaseOutCubicCurve)

    Box(Modifier.fillMaxSize()) {
        DeviceMain(
            live = live,
            notifier = notifier,
            settingsOpen = sub == DeviceSub.Settings,
            onOpenAppearance = { sub = DeviceSub.Appearance },
            onOpenSettings = { sub = DeviceSub.Settings },
        )
        AnimatedVisibility(
            visible = sub == DeviceSub.Appearance,
            enter = slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { fullWidth -> fullWidth }),
            exit = slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { fullWidth -> fullWidth }),
        ) {
            AppearancePage(settings = settings, onBack = { sub = null })
        }
        AnimatedVisibility(
            visible = sub == DeviceSub.Settings,
            enter = slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { fullWidth -> fullWidth }),
            exit = slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { fullWidth -> fullWidth }),
        ) {
            SettingsPage(settings = settings, notifier = notifier, onBack = { sub = null })
        }
    }
}

@Composable
private fun DeviceMain(
    live: LiveStats,
    notifier: Notifier,
    settingsOpen: Boolean,
    onOpenAppearance: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = AppTheme.colors
    val container = LocalContainer.current
    val store = container.store
    val toast = LocalToaster.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var device by remember { mutableStateOf<DeviceStaticInfo?>(null) }
    var ip by remember { mutableStateOf("…") }
    var storage by remember { mutableStateOf<StorageReading?>(null) }
    var features by remember { mutableStateOf<List<FeatureShortcut>>(emptyList()) }
    var recommended by remember { mutableStateOf<List<RecommendedApp>>(emptyList()) }
    var keepScreenOn by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(1f) }
    var notificationGranted by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf("…") }

    LaunchedEffect(Unit) {
        version = appVersion()
        keepScreenOn = try {
            store.getKeepScreenOn()
        } catch (e: Exception) {
            false
        }
        val saved = try {
            store.getBrightness()
        } catch (e: Exception) {
            null
        }
        val currentBrightness = saved ?: container.brightness.current()
        if (currentBrightness != null) brightness = currentBrightness.toFloat()
        device = withContext(Dispatchers.IO) { container.device.read() }
        ip = withContext(Dispatchers.IO) { container.network.localIpAddress() }
        storage = container.storage.read()
        try {
            features = container.catalog.loadFeatures()
            recommended = container.catalog.loadApplications()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Yapılandırma okunamazsa bu bölümler boş kalır.
        }
    }

    // Bildirim izni durumu: açılışta ve Bildirimler sayfasından dönünce (orada değişmiş olabilir).
    LaunchedEffect(settingsOpen) {
        if (!settingsOpen) {
            try {
                notificationGranted = notifier.hasPermission()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sessizce yok say.
            }
        }
    }

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // Sessizce yok say.
        }
    }

    fun setKeepOn(value: Boolean) {
        keepScreenOn = value
        try {
            container.activityBridge.setKeepScreenOn(value)
        } catch (e: Exception) {
            // Platform desteklemiyorsa sessizce yok say.
        }
        try {
            store.setKeepScreenOn(value)
        } catch (e: Exception) {
        }
    }

    fun resetBrightness() {
        container.brightness.reset()
        try {
            store.clearBrightness()
        } catch (e: Exception) {
        }
        val current = container.brightness.current()
        if (current != null) brightness = current.toFloat()
        toast.show("Parlaklık sistem değerine döndü")
    }

    fun requestNotification() {
        scope.launch {
            try {
                val started = System.currentTimeMillis()
                val ok = notifier.requestPermission()
                notificationGranted = ok
                // Sistem izin penceresi açılmadan hemen reddedildiyse kalıcı olarak reddedilmiştir:
                // uygulama ayarlarını aç.
                if (!ok && System.currentTimeMillis() - started < 400L) openAppSettings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sessizce yok say.
            }
        }
    }

    fun clearOwnCache() {
        scope.launch {
            clearing = true
            val freed = container.storage.clearCache()
            val reading = container.storage.read()
            clearing = false
            storage = reading
            toast.show("${formatBytes(freed)} temizlendi")
        }
    }

    val dm = ctx.resources.displayMetrics
    val physW = dm.widthPixels
    val physH = dm.heightPixels
    val dpi = (dm.density * 160f).roundToInt()
    val hz = remember {
        try {
            @Suppress("DEPRECATION")
            val r = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
            r
        } catch (e: Exception) {
            0f
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp),
    ) {
        Text("Cihaz", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        SectionHeader("Bu Telefon")
        AppCard { PhoneInfo(live = live, device = device) }

        SectionHeader("Ekran ve Ağ")
        AppCard {
            InfoRow(label = "Çözünürlük", value = "$physW × $physH")
            RowDivider()
            InfoRow(label = "Yoğunluk", value = "$dpi dpi")
            RowDivider()
            InfoRow(label = "Yenileme hızı", value = if (hz > 0f) "${hz.roundToInt()} Hz" else "Bilinmiyor")
            RowDivider()
            InfoRow(label = "Yerel IP", value = ip, copyable = ip != "Bulunamadı" && ip != "…")
        }

        SectionHeader("Ayarlar")
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ekranı açık tut", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = keepScreenOn, onCheckedChange = { v -> setKeepOn(v) }, colors = appSwitchColors())
            }
            RowDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Brightness6, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Uygulama parlaklığı", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("%${(brightness * 100f).roundToInt()}", color = c.muted)
            }
            Slider(
                value = brightness.coerceIn(0f, 1f),
                onValueChange = { v ->
                    brightness = v
                    container.brightness.setBrightness(v.toDouble())
                },
                onValueChangeFinished = {
                    try {
                        store.setBrightness(brightness.toDouble())
                    } catch (e: Exception) {
                    }
                },
                colors = appSliderColors(),
            )
            Row {
                SoftButton(
                    label = "Sisteme Sıfırla",
                    icon = Icons.Rounded.RestartAlt,
                    onClick = { resetBrightness() },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SoftButton(
                    label = "Titreşim Testi",
                    icon = Icons.Rounded.Vibration,
                    onClick = { vibrate(ctx) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            RowDivider()
            NavTile(
                title = "Görünüm",
                subtitle = "Tema · logo rengi",
                leading = Icons.Rounded.Palette,
                trailing = Icons.Rounded.ChevronRight,
                onClick = onOpenAppearance,
            )
            RowDivider()
            NavTile(
                title = "Bildirimler ve Uyarılar",
                subtitle = "Isınma, pil, depolama uyarıları · eşikler · titreşim",
                leading = Icons.Rounded.Notifications,
                trailing = Icons.Rounded.ChevronRight,
                onClick = onOpenSettings,
            )
            RowDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bildirim izni", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (notificationGranted) "Verildi" else "Verilmedi",
                        color = if (notificationGranted) c.good else c.warn,
                        fontSize = 13.sp,
                    )
                }
                if (!notificationGranted) {
                    SoftButton(label = "İzin Ver", onClick = { requestNotification() })
                }
            }
            Spacer(Modifier.height(14.dp))
            RowDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Uygulama önbelleği", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(formatBytes(storage?.cacheBytes ?: 0L), color = c.muted, fontSize = 13.sp)
                }
                SoftButton(
                    label = if (clearing) "Temizleniyor…" else "Temizle",
                    onClick = if (clearing) null else ({ clearOwnCache() }),
                )
            }
        }

        if (features.isNotEmpty()) {
            SectionHeader("Sistem Kısayolları")
            AppCard(padding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                features.forEachIndexed { i, feature ->
                    NavTile(
                        title = feature.title,
                        subtitle = feature.description,
                        trailing = Icons.Rounded.ChevronRight,
                        onClick = { container.catalog.openFeatureSettings(feature) },
                        horizontalPadding = 16.dp,
                        titleSize = 15.sp,
                    )
                    if (i != features.size - 1) RowDivider()
                }
            }
        }

        if (recommended.isNotEmpty()) {
            SectionHeader("Önerilen Açık Kaynak Uygulamalar")
            AppCard(padding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                recommended.forEachIndexed { i, app ->
                    NavTile(
                        title = app.name,
                        subtitle = "${app.description}\n${app.sourceLabel}",
                        trailing = Icons.AutoMirrored.Rounded.OpenInNew,
                        trailingSize = 20.dp,
                        onClick = { container.catalog.openUrl(app.url) },
                        horizontalPadding = 16.dp,
                        titleSize = 15.sp,
                        minHeight = 88.dp,
                    )
                    if (i != recommended.size - 1) RowDivider()
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(size = 56.dp)
            Spacer(Modifier.height(10.dp))
            Text("TerminalF", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text("Sürüm $version", color = c.muted, fontSize = 12.sp)
        }
    }
}

/** "Bu Telefon" kartının içi: yalnızca canlı ölçümler değişince yeniden çizilir. */
@Composable
private fun PhoneInfo(live: LiveStats, device: DeviceStaticInfo?) {
    live.observe()
    val sys = live.snapshot.system
    val mem = sys.memory
    val disk = sys.disk
    val uptime = sys.uptime
    Column {
        InfoRow(label = "Model", value = device?.model ?: "…", copyable = device != null)
        RowDivider()
        InfoRow(label = "Sistem", value = device?.os ?: "…")
        RowDivider()
        InfoRow(label = "İşlemci mimarisi", value = device?.cpuArch ?: "…")
        RowDivider()
        InfoRow(label = "Çekirdek sayısı", value = if (device != null) "${device.coreCount}" else "…")
        RowDivider()
        InfoRow(label = "Toplam bellek", value = if (mem == null) "—" else formatBytes(mem.totalBytes))
        RowDivider()
        InfoRow(label = "Toplam depolama", value = if (disk == null) "—" else formatBytes(disk.totalBytes))
        RowDivider()
        InfoRow(label = "Açık kalma süresi", value = if (uptime == null) "—" else formatDuration(uptime))
    }
}

/** Flutter `ListTile` karşılığı: (simge) başlık + alt yazı (>) ; dokununca [onClick]. */
@Composable
private fun NavTile(
    title: String,
    subtitle: String,
    trailing: ImageVector,
    onClick: () -> Unit,
    leading: ImageVector? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    titleSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    trailingSize: androidx.compose.ui.unit.Dp = 24.dp,
    minHeight: androidx.compose.ui.unit.Dp = 72.dp,
) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = minHeight)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
                Icon(leading, contentDescription = null, tint = c.muted)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = titleSize)
            Text(subtitle, color = c.muted, fontSize = 12.5.sp)
        }
        Spacer(Modifier.width(16.dp))
        Icon(trailing, contentDescription = null, tint = c.muted, modifier = Modifier.size(trailingSize))
    }
}

/** 300 ms titreşim; donanım yoksa sessizce yok sayılır. */
private fun vibrate(ctx: Context) {
    try {
        @Suppress("DEPRECATION")
        val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(300L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300L)
        }
    } catch (e: Exception) {
        // Titreşim donanımı yoksa sessizce yok say.
    }
}
