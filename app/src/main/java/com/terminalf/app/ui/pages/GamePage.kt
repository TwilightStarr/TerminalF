package com.terminalf.app.ui.pages

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.terminalf.app.core.DiagnosisKind
import com.terminalf.app.core.GameModeConfig
import com.terminalf.app.core.GamePower
import com.terminalf.app.core.GameKind
import com.terminalf.app.core.GamePreset
import com.terminalf.app.core.GraphicsBackend
import com.terminalf.app.core.ResolutionMethod
import com.terminalf.app.core.autoLockHz
import com.terminalf.app.core.kBedrockFpsCap
import com.terminalf.app.core.kBedrockPackage
import com.terminalf.app.core.customLines
import com.terminalf.app.core.fixed
import com.terminalf.app.core.formatSeconds
import com.terminalf.app.core.gameTips
import com.terminalf.app.core.modWarning
import com.terminalf.app.core.kGamePresets
import com.terminalf.app.core.kCustomMaxLines
import com.terminalf.app.core.kCpuCapChoices
import com.terminalf.app.core.kDimChoices
import com.terminalf.app.core.kFpsChoices
import com.terminalf.app.core.kGuardTempChoices
import com.terminalf.app.core.kHzChoices
import com.terminalf.app.core.kScaleChoices
import com.terminalf.app.core.thermalLabel
import com.terminalf.app.state.ControlController
import com.terminalf.app.state.GameModeController
import com.terminalf.app.state.GameRunResult
import com.terminalf.app.state.SavedGame
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.components.LocalDialogs
import com.terminalf.app.ui.components.LocalSheets
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.PrimaryButton
import com.terminalf.app.ui.components.RowDivider
import com.terminalf.app.ui.components.SectionHeader
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.components.StatusPill
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.sheets.showControlSetup
import com.terminalf.app.ui.sheets.showLogSheet
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Oyun Modu sayfası: istenen HER oyun için tek dokunuşla ısıyı, GPU yükünü ve pil tüketimini
 * azaltır; oyun bitince her şeyi eski haline getirir. Her oyunun ayarı ayrı saklanır.
 */
@Composable
fun GamePage(control: ControlController, game: GameModeController) {
    control.observe()
    game.observe()
    val c = AppTheme.colors
    val toast = LocalToaster.current
    val dialogs = LocalDialogs.current
    val sheets = LocalSheets.current
    val scope = rememberCoroutineScope()

    val ready = control.ready
    val probed = remember { booleanArrayOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    // Açılışta: kayıtlı oyunları oku, sonra seçili oyunun kurulu olup olmadığına bak.
    LaunchedEffect(Unit) {
        game.load()
        game.checkInstalled()
    }

    // Yetki hazır olunca bir kez: kurulu mu, Hz, izleyici durumu, ölçümler, ön plan.
    LaunchedEffect(ready) {
        if (ready && !probed[0]) {
            probed[0] = true
            scope.launch {
                game.checkInstalled()
                game.refreshDisplayHz()
                game.syncWatchdog()
                game.refreshStats()
                game.detectForeground()
            }
        }
    }

    // Oyundan Terminal'e dönülünce izleyicinin yaptıklarını ve yeni ölçümleri oku.
    DisposableEffect(control, game) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        var armed = false
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (armed && control.ready) {
                    scope.launch {
                        game.syncWatchdog()
                        game.refreshStats()
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        armed = true
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun start(launchGame: Boolean) {
        scope.launch {
            if (!control.ready) {
                sheets.showControlSetup(control)
                return@launch
            }
            val cfg = game.config
            if (cfg.method == ResolutionMethod.WholeScreen && cfg.reducesResolution) {
                val ok = dialogs.confirm(
                    title = "Tüm ekran çözünürlüğü düşürülecek",
                    message = "Telefonun tamamı oyun boyunca düşük çözünürlükte çalışır (yazılar bulanık " +
                        "görünür). Oyun kapanınca kendiliğinden eski haline döner; olmazsa Geri Al'a " +
                        "basın. Eski değerler kayıtlı olduğu için uygulamayı kapatsanız bile geri alınabilir.",
                    confirmLabel = "Uygula",
                )
                if (!ok) return@launch
            }
            if (launchGame && cfg.backend != GraphicsBackend.Default && game.isRunning()) {
                val ok = dialogs.confirm(
                    title = "Oyun yeniden başlatılacak",
                    message = "Grafik motoru değişikliği oyun açılırken geçerli olur. Oyun şu an açık; " +
                        "kapatılıp yeniden açılacak ve kaydedilmemiş ilerleme kaybolabilir. Önce oyunda " +
                        "kaydedin.",
                    confirmLabel = "Devam",
                )
                if (!ok) return@launch
            }
            val result = game.startSelected(launch = launchGame)
            control.reportDone("Oyun Modu", result.summary, ok = result.ok)
            toast.show(result.summary)
        }
    }

    fun stop() {
        scope.launch {
            val result = game.stop()
            control.reportDone("Oyun Modu kapatıldı", result.summary, ok = result.ok)
            toast.show(if (result.ok) "Her şey eski haline döndü" else result.summary)
        }
    }

    fun resetAll() {
        scope.launch {
            val result = game.forceReset()
            control.reportDone("Her şey eski haline döndürüldü", result.summary, ok = result.ok)
            toast.show(if (result.ok) "Her şey eski haline döndü" else result.summary)
        }
    }

    fun pickGame() {
        scope.launch {
            val existing = game.games.map { it.packageName }.toSet()
            val picked = sheets.show<SavedGame>(key = "game_picker") { close ->
                GamePickerSheet(existing = existing, close = close)
            }
            if (picked == null) return@launch
            val ok = game.addGame(picked.packageName, picked.name)
            if (!ok) toast.show("Geçersiz paket adı.")
        }
    }

    fun removeSelected() {
        val pkg = game.selectedPackage ?: return
        scope.launch {
            val ok = dialogs.confirm(
                title = "Oyunu listeden çıkar",
                message = "\"${game.selectedName}\" için kayıtlı Oyun Modu ayarları silinir. Oyunun " +
                    "kendisine dokunulmaz.",
                confirmLabel = "Çıkar",
                danger = true,
            )
            if (!ok) return@launch
            game.removeGame(pkg)
        }
    }

    val cfg = game.config
    val busy = game.busy
    val has = game.hasGame
    val suggestion = game.suggestion
    val lastResult = game.lastResult

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Oyun Modu",
                modifier = Modifier.weight(1f),
                color = c.text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            IconButton(onClick = { scope.launch { sheets.showLogSheet(control) } }) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = "İşlem günlüğü", tint = c.muted)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (!has) {
            GameEmptyHero(onAdd = { pickGame() })
        } else {
            // Oyun/mod seçimi hiçbir zaman kilitlenmez: uzun süren bir işlem seçimleri engellemez.
            GameStrip(game = game, busy = false, onAdd = { pickGame() })
            Spacer(Modifier.height(12.dp))
            GameHeroCard(
                control = control,
                game = game,
                onStart = { start(launchGame = true) },
                onApply = { start(launchGame = false) },
                onStop = { stop() },
                onReset = { resetAll() },
            )
            if (busy) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = c.primary,
                    trackColor = c.outline,
                )
            }
            SectionHeader("Mod seç")
            GamePresetList(game = game, busy = false)
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showAdvanced) "Gelişmiş ayarları gizle" else "Gelişmiş ayarlar", color = c.muted)
                }
            }
            if (showAdvanced) {
                if (suggestion != null) {
                    Spacer(Modifier.height(8.dp))
                    GameSuggestionCard(game = game, busy = false)
                }
                SectionHeader("Ayrıntılı ayarlar")
                GameGroups(game = game, cfg = cfg, busy = false)
                if (lastResult != null && lastResult.steps.isNotEmpty()) {
                    SectionHeader("Son işlem")
                    GameResultCard(result = lastResult)
                }
                if (control.ready) {
                    SectionHeader(
                        "Son oyun oturumu",
                        trailing = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        game.syncWatchdog()
                                        game.refreshStats()
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Yenile", tint = c.muted)
                            }
                        },
                    )
                    GameReportCard(game = game)
                }
                SectionHeader("Bilmeniz gerekenler")
                GameHonestyCard()
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { removeSelected() }, enabled = !busy) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Bu oyunu listeden çıkar", color = c.muted)
                }
            }
        }
    }
}

/** Mod kartında gösterilen tek satırlık özet. */
private fun presetLine(p: GamePreset): String {
    val tagline = when (p.id) {
        "ice" -> "En serin ve en uzun pil"
        "cool" -> "Isınmayı azaltır"
        "balanced" -> "Akıcılık ve ısı dengesi"
        "smooth" -> "En akıcı, daha çok ısınır"
        else -> ""
    }
    val scale = if (p.scale >= 0.999) "tam çözünürlük" else "%${(p.scale * 100).roundToInt()} çözünürlük"
    return "$tagline · ${p.targetFps} FPS · $scale"
}

private fun scaleLabel(v: Double): String =
    if (v >= 0.999) "Tam çözünürlük" else "%${(v * 100).roundToInt()} çözünürlük"

/** `enabled` ise [block], değilse `null` (denetimi devre dışı bırakır). */
private fun <T> enabledIf(enabled: Boolean, block: (T) -> Unit): ((T) -> Unit)? =
    if (enabled) block else null

private fun presetIcon(i: Int): ImageVector = when (i) {
    0 -> Icons.Rounded.AcUnit
    1 -> Icons.Rounded.Eco
    2 -> Icons.Rounded.Speed
    else -> Icons.Rounded.Bolt
}

/** Dört açılır-kapanır ayar grubu (Performans, Ekran, Sistem, Isı ve güvenlik). */
@Composable
private fun GameGroups(game: GameModeController, cfg: GameModeConfig, busy: Boolean) {
    fun set(next: GameModeConfig) = game.updateConfig(next)

    val screenOn = ArrayList<String>()
    cfg.lockHz?.let { screenOn.add("Ekran $it Hz") }
    if (cfg.dimScreen) screenOn.add("parlaklık %${cfg.dimPercent}")
    if (cfg.animationsOff) screenOn.add("animasyon kapalı")

    val systemOn = ArrayList<String>()
    if (cfg.killBackground) systemOn.add("arka plan temiz")
    if (cfg.dnd) systemOn.add("bildirim sessiz")
    if (cfg.batterySaver) systemOn.add("pil tasarrufu")
    if (cfg.cpuCap) systemOn.add("işlemci sınırı %${cfg.cpuCapPercent}")

    val safetyOn = ArrayList<String>()
    safetyOn.add(if (cfg.autoRevert) "otomatik geri alma" else "geri alma elle")
    if (cfg.autoRevert) {
        safetyOn.add(if (cfg.guardTempC == 0) "ısı koruması kapalı" else "koruma ${cfg.guardTempC} °C")
    }

    EngineGroup(game = game, cfg = cfg, busy = busy)
    Spacer(Modifier.height(10.dp))
    GameGroup(
        icon = Icons.Rounded.Speed,
        title = "Performans",
        summary = "${cfg.targetFps} FPS · ${scaleLabel(cfg.scale)} · ${cfg.powerProfile.label}",
    ) {
        GameLabel("Kare hızı üst sınırı")
        Spacer(Modifier.height(8.dp))
        GameChoices(
            values = kFpsChoices,
            selected = cfg.targetFps,
            label = { v: Int -> "$v FPS" },
            onSelected = enabledIf(!busy) { v: Int -> set(cfg.copy(targetFps = v)) },
        )
        if (game.kind == GameKind.MinecraftBedrock && cfg.targetFps > kBedrockFpsCap) {
            Spacer(Modifier.height(6.dp))
            GameHint(
                "Minecraft Bedrock Android'de oyunun kendisi 60 FPS'e sınırlı. ${cfg.targetFps} FPS " +
                    "seçmek FPS'i artırmaz, yalnızca telefonu ısıtır; 60 öneririz.",
            )
        }
        Spacer(Modifier.height(16.dp))
        GameLabel("Oyun çözünürlüğü")
        Spacer(Modifier.height(8.dp))
        GameChoices(
            values = kScaleChoices,
            selected = cfg.scale,
            label = { v: Double -> if (v >= 1) "Tam" else "%${(v * 100).roundToInt()}" },
            onSelected = enabledIf(!busy) { v: Double -> set(cfg.copy(scale = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(
            "Düşük çözünürlük GPU yükünü, ısınmayı ve pil tüketimini en çok azaltan ayardır. " +
                "Piksel sanat oyunlarda görüntü kaybı azdır.",
        )
        if (cfg.reducesResolution) {
            Spacer(Modifier.height(16.dp))
            GameLabel("Çözünürlük yöntemi")
            Spacer(Modifier.height(8.dp))
            GameChoices(
                values = ResolutionMethod.values().toList(),
                selected = cfg.method,
                label = { v: ResolutionMethod ->
                    if (v == ResolutionMethod.PerGame) "Yalnız oyun" else "Tüm ekran"
                },
                onSelected = enabledIf(!busy) { v: ResolutionMethod -> set(cfg.copy(method = v)) },
            )
            Spacer(Modifier.height(6.dp))
            GameHint(
                if (cfg.method == ResolutionMethod.PerGame) {
                    "Güvenli yöntem. Bazı üreticiler/oyunlar desteklemeyebilir; uygulanamazsa " +
                        "sonuç listesinde görürsünüz ve \"Tüm ekran\"ı deneyebilirsiniz."
                } else {
                    "Daha güçlü ve her cihazda çalışır, ama telefonun tamamı geçici olarak " +
                        "düşük çözünürlüğe geçer. Oyun bitince Geri Al'a basın."
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        GameLabel("Güç profili")
        Spacer(Modifier.height(8.dp))
        GameChoices(
            values = GamePower.values().toList(),
            selected = cfg.powerProfile,
            label = { v: GamePower -> v.label },
            onSelected = enabledIf(!busy) { v: GamePower -> set(cfg.copy(powerProfile = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(cfg.powerProfile.hint)
    }
    Spacer(Modifier.height(10.dp))
    GameGroup(
        icon = Icons.Rounded.Smartphone,
        title = "Ekran",
        summary = if (screenOn.isEmpty()) "Ekrana dokunulmaz" else screenOn.joinToString(" · "),
    ) {
        GameSwitchRow(
            title = "Ekranı ${if (cfg.refreshHz > 0) cfg.refreshHz else autoLockHz(cfg.targetFps)} Hz'e kilitle",
            subtitle = "Değişken yenileme hızının oyun sırasında yükselmesini engeller; " +
                "ekran ve GPU daha az çalışır. Oyun bitince geri alınır.",
            value = cfg.lockRefreshRate,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(lockRefreshRate = v)) },
        )
        if (cfg.lockRefreshRate) {
            Spacer(Modifier.height(4.dp))
            GameLabel("Kilitlenecek yenileme hızı")
            Spacer(Modifier.height(8.dp))
            GameChoices(
                values = kHzChoices,
                selected = cfg.refreshHz,
                label = { v: Int -> if (v == 0) "Otomatik" else "$v Hz" },
                onSelected = enabledIf(!busy) { v: Int -> set(cfg.copy(refreshHz = v)) },
            )
            Spacer(Modifier.height(6.dp))
            GameHint(
                "Otomatik: kare hızının tam katı olan en düşük panel hızı seçilir (30 FPS için 60 Hz). " +
                    "Panelinizin desteklemediği bir değeri üretici yazılımı yok sayabilir.",
            )
            Spacer(Modifier.height(8.dp))
        }
        RowDivider()
        GameSwitchRow(
            title = "Ekran parlaklığını kıs",
            subtitle = "Oyun boyunca parlaklık mevcut değerin %60'ına iner. AMOLED ekranda " +
                "ısınmayı ve pili en çok etkileyen ayarlardan biridir.",
            value = cfg.dimScreen,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(dimScreen = v)) },
        )
        if (cfg.dimScreen) {
            Spacer(Modifier.height(4.dp))
            GameLabel("Parlaklık oranı (mevcut değerin yüzdesi)")
            Spacer(Modifier.height(8.dp))
            GameChoices(
                values = kDimChoices,
                selected = cfg.dimPercent,
                label = { v: Int -> "%$v" },
                onSelected = enabledIf(!busy) { v: Int -> set(cfg.copy(dimPercent = v)) },
            )
            Spacer(Modifier.height(8.dp))
        }
        RowDivider()
        GameSwitchRow(
            title = "Sistem animasyonlarını kapat",
            subtitle = "Oyun boyunca geçiş efektleri kalkar; ekran birleştirme yükü biraz azalır.",
            value = cfg.animationsOff,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(animationsOff = v)) },
        )
    }
    Spacer(Modifier.height(10.dp))
    GameGroup(
        icon = Icons.Rounded.Memory,
        title = "Sistem",
        summary = if (systemOn.isEmpty()) "Sisteme dokunulmaz" else systemOn.joinToString(" · "),
    ) {
        GameSwitchRow(
            title = "Arka planı temizle",
            subtitle = "Başlamadan önce RAM'i boşaltır; işlemciye daha az rekabet kalır.",
            value = cfg.killBackground,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(killBackground = v)) },
        )
        RowDivider()
        GameSwitchRow(
            title = "Bildirimleri sustur",
            subtitle = "Oyun sırasında Rahatsız Etme (yalnızca öncelikliler) açılır; bildirimler " +
                "takılma ve ekran uyanması yaratmaz.",
            value = cfg.dnd,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(dnd = v)) },
        )
        RowDivider()
        GameSwitchRow(
            title = "Pil Tasarrufu'nu aç",
            subtitle = "Sistemin Pil Tasarrufu modu oyun boyunca açık kalır; çoğu cihazda " +
                "işlemciyi ve arka plan işlerini kısar. Oyun bitince eski haline döner.",
            value = cfg.batterySaver,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(batterySaver = v)) },
        )
        RowDivider()
        GameSwitchRow(
            title = "İşlemci hızını sınırla (root)",
            subtitle = "En güçlü iki çekirdek kümesi seçtiğiniz oranda çalışır; ısı ve hız dalgalanması azalır. " +
                "Yalnızca root ile çalışır, Shizuku ile uygulanamaz.",
            value = cfg.cpuCap,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(cpuCap = v)) },
        )
        if (cfg.cpuCap) {
            Spacer(Modifier.height(4.dp))
            GameLabel("İşlemci hız sınırı (azami hızın yüzdesi)")
            Spacer(Modifier.height(8.dp))
            GameChoices(
                values = kCpuCapChoices,
                selected = cfg.cpuCapPercent,
                label = { v: Int -> "%$v" },
                onSelected = enabledIf(!busy) { v: Int -> set(cfg.copy(cpuCapPercent = v)) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    GameGroup(
        icon = Icons.Rounded.ShieldMoon,
        title = "Isı ve güvenlik",
        summary = safetyOn.joinToString(" · "),
    ) {
        GameSwitchRow(
            title = "Oyun kapanınca otomatik geri al",
            subtitle = "Cihazda küçük bir izleyici çalışır: oyun yaklaşık 40 saniye ön planda " +
                "olmazsa ayarlar kendiliğinden eski haline döner; oyun boyunca FPS/ısı/işlemci " +
                "hızı kaydedilir. Terminal kapansa da çalışır.",
            value = cfg.autoRevert,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(autoRevert = v)) },
        )
        RowDivider()
        Spacer(Modifier.height(12.dp))
        GameLabel("Isı koruması eşiği (pil sıcaklığı)")
        Spacer(Modifier.height(8.dp))
        GameChoices(
            values = kGuardTempChoices,
            selected = cfg.guardTempC,
            label = { v: Int -> if (v == 0) "Kapalı" else "$v °C" },
            onSelected = enabledIf(!busy && cfg.autoRevert) { v: Int -> set(cfg.copy(guardTempC = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(
            if (cfg.autoRevert) {
                "Pil sıcaklığı (ya da sistemin ısıl durumu) art arda iki ölçümde eşiğe ulaşırsa " +
                    "izleyici bir kez daha sıkılaştırır: parlaklığı daha çok kısar, Pil Tasarrufu'nu " +
                    "açar (seçiliyse) ve root varsa işlemciyi daha çok sınırlar. Oyun bitince hepsi geri alınır."
            } else {
                "Isı koruması izleyiciye bağlıdır; açmak için otomatik geri almayı açın."
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    val customCount = customLines(cfg.customStart).size + customLines(cfg.customEnd).size
    GameGroup(
        icon = Icons.Rounded.Code,
        title = "Özel komutlar",
        summary = if (customCount == 0) "Kendi komutlarını ekle" else "$customCount özel komut",
    ) {
        GameLabel("Başlarken çalışsın")
        Spacer(Modifier.height(8.dp))
        GameCommandField(
            initial = cfg.customStart,
            resetKey = cfg.packageName + ":start",
            placeholder = "Her satıra bir komut\nörn: settings put global some_key 1",
            onChange = { v: String -> set(game.config.copy(customStart = v)) },
        )
        Spacer(Modifier.height(16.dp))
        GameLabel("Geri Al'a basınca çalışsın")
        Spacer(Modifier.height(8.dp))
        GameCommandField(
            initial = cfg.customEnd,
            resetKey = cfg.packageName + ":end",
            placeholder = "Her satıra bir komut",
            onChange = { v: String -> set(game.config.copy(customEnd = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(
            "Komutlar sistem yetkisiyle (Shizuku/root) sırayla çalışır; boş satırlar ve # ile başlayan " +
                "satırlar atlanır (en çok $kCustomMaxLines satır). Bitiş komutları yalnızca Geri Al ile " +
                "çalışır; oyun kapanınca cihazdaki otomatik geri almada çalışmaz. Komutları kendi " +
                "sorumluluğunda ekle.",
        )
    }
}

/** Play Store'da uygulama sayfasını açar (Play yoksa tarayıcıda). Başarısızsa sessizce vazgeçer. */
private fun openPlayStore(context: android.content.Context, pkg: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        try {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** Grafik motoru: sürücü arka ucu, sabit performans, önceden derleme ve türe özel ipuçları. */
@Composable
private fun EngineGroup(game: GameModeController, cfg: GameModeConfig, busy: Boolean) {
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val kind = game.kind
    val tips = gameTips(kind)
    val rec = game.recommended

    val summary = ArrayList<String>()
    summary.add(cfg.backend.label)
    if (cfg.fixedPerf) summary.add("sabit performans")

    GameGroup(
        icon = Icons.Rounded.Bolt,
        title = "Grafik motoru",
        summary = summary.joinToString(" · "),
    ) {
        val warning = modWarning(kind)
        if (warning != null) {
            Text(
                warning,
                color = c.warn,
                fontSize = 12.5.sp,
                lineHeight = (12.5f * 1.4f).sp,
            )
            Spacer(Modifier.height(14.dp))
        }
        GameLabel("Grafik sürücüsü arka ucu")
        Spacer(Modifier.height(8.dp))
        GameChoices(
            values = GraphicsBackend.values().toList(),
            selected = cfg.backend,
            label = { v: GraphicsBackend -> v.label },
            onSelected = enabledIf(!busy) { v: GraphicsBackend -> game.updateConfig(cfg.copy(backend = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(cfg.backend.hint)
        if (cfg.backend != GraphicsBackend.Default) {
            Spacer(Modifier.height(6.dp))
            GameHint(
                "Sürücü oyun açılırken seçilir: Başlat'a basınca oyun açıksa kapatılıp yeniden açılır. " +
                    "Oyun bitince seçim eski haline döner.",
            )
        }
        Spacer(Modifier.height(10.dp))
        RowDivider()
        GameSwitchRow(
            title = "Sabit performans modu (deneysel)",
            subtitle = "İşlemci/GPU hızını oyun boyunca sabit tutar; kare süreleri daha düzgün olabilir " +
                "ama telefon daha çok ısınır. Android 12+ gerekir, her cihazda çalışmaz. Isı koruması " +
                "devreye girerse otomatik kapanır.",
            value = cfg.fixedPerf,
            onChanged = enabledIf(!busy) { v: Boolean -> game.updateConfig(cfg.copy(fixedPerf = v)) },
        )
        RowDivider()
        Spacer(Modifier.height(12.dp))
        GameLabel("Oyunu önceden derle")
        Spacer(Modifier.height(4.dp))
        GameHint(
            "Java/Kotlin tarafı ağır oyunlarda ilk açılışı ve takılmaları azaltabilir; tamamen yerel " +
                "(C++) motorlarda etkisi sınırlıdır. Tek seferliktir, geri alınacak bir şey yapmaz.",
        )
        Spacer(Modifier.height(10.dp))
        SoftButton(
            label = "Şimdi derle",
            onClick = if (busy || !game.control.ready) {
                null
            } else {
                ({
                    scope.launch {
                        val r = game.compileSelected()
                        toast.show(if (r.ok) "Oyun derlendi" else "Derlenemedi: ${r.detail}")
                    }
                })
            },
            icon = Icons.Rounded.Memory,
        )
        if (tips.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GameLabel("${kind.title} için ipuçları")
            for (t in tips) {
                Spacer(Modifier.height(6.dp))
                GameHint("• $t")
            }
        }
        if (kind == GameKind.MinecraftBedrock) {
            val context = LocalContext.current
            Spacer(Modifier.height(16.dp))
            GameLabel("Minecraft'ı güncelle")
            Spacer(Modifier.height(4.dp))
            GameHint(
                "Google Play'deki Minecraft sayfasını açar; güncellemeyi orada yaparsınız. Başka " +
                    "kaynaktan APK kurmayın.",
            )
            Spacer(Modifier.height(10.dp))
            SoftButton(
                label = "Google Play'de aç",
                onClick = { openPlayStore(context, kBedrockPackage) },
                icon = Icons.Rounded.Refresh,
            )
        }
        if (rec != null) {
            Spacer(Modifier.height(14.dp))
            SoftButton(
                label = "Önerilen ayarı uygula",
                onClick = if (busy) null else ({ game.applyRecommended() }),
                icon = Icons.Rounded.AutoFixHigh,
            )
            Spacer(Modifier.height(6.dp))
            GameHint(
                "Bu bir deneme noktasıdır, garanti değildir. Bir oyun oturumu oynayıp \"Son oyun " +
                    "oturumu\" ortalama FPS'ini varsayılanla karşılaştırın.",
            )
        }
    }
}

@Composable
private fun GameEmptyHero(onAdd: () -> Unit) {
    val c = AppTheme.colors
    AppCard(padding = PaddingValues(20.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.primary.faded(0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = c.primary)
        }
        Spacer(Modifier.height(14.dp))
        Text("Bir oyun seçin", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Yüklü herhangi bir oyunu ekleyin. Her oyunun ayarı ayrı saklanır; tek dokunuşla " +
                "ısıyı, GPU yükünü ve pil tüketimini azaltıp oyunu açarız. Oyun kapanınca her şey " +
                "eski haline döner.",
            color = c.muted,
            fontSize = 13.sp,
            lineHeight = (13f * 1.45f).sp,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(label = "Oyun Seç", onClick = onAdd, icon = Icons.Rounded.Add)
    }
}

@Composable
private fun GameStrip(game: GameModeController, busy: Boolean, onAdd: () -> Unit) {
    val scope = rememberCoroutineScope()
    val games = game.games
    val selectedPkg = game.selectedPackage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (g in games) {
            GameChoiceChip(
                selected = g.packageName == selectedPkg,
                onClick = if (busy) null else ({ scope.launch { game.selectGame(g.packageName) } }),
            ) {
                Text(
                    g.name,
                    modifier = Modifier.widthIn(max = 160.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        GameActionChip(
            onClick = if (busy) null else onAdd,
            leadingIcon = Icons.Rounded.Add,
            label = "Oyun ekle",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameHeroCard(
    control: ControlController,
    game: GameModeController,
    onStart: () -> Unit,
    onApply: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    val c = AppTheme.colors
    val ready = control.ready
    val busy = game.busy
    val here = game.activeForSelected
    val otherActive = game.active && !here
    val installed = game.installed
    val hz = game.displayHz
    val alive = game.watchdogAlive
    val syncNote = game.syncNote
    val color = if (!ready) c.warn else if (here) c.good else c.primary
    val name = game.selectedName
    val letter = if (name.isEmpty()) {
        "?"
    } else {
        name.substring(0, Character.charCount(name.codePointAt(0))).uppercase()
    }

    AppCard(borderColor = color.faded(0.45f), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.faded(0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    game.config.packageName,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.5.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(
                label = if (here) "Oyun modu AÇIK" else "Oyun modu kapalı",
                color = if (here) c.good else c.muted,
            )
            if (otherActive) StatusPill(label = "Başka bir oyunda açık", color = c.warn)
            if (ready && installed != null) {
                StatusPill(
                    label = if (installed) "Oyun yüklü" else "Oyun bulunamadı",
                    color = if (installed) c.good else c.warn,
                )
            }
            if (hz != null) StatusPill(label = "Ekran ${hz.roundToInt()} Hz", color = c.muted)
            if (game.config.backend != GraphicsBackend.Default) {
                StatusPill(label = "Motor: ${game.config.backend.label}", color = c.primary)
            }
            if (here && alive != null) {
                StatusPill(
                    label = if (alive) "Otomatik geri alma: izliyor" else "Otomatik geri alma: kapalı",
                    color = if (alive) c.good else c.warn,
                )
            }
        }
        if (!ready) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Oyun modu için tek seferlik kurulum gerekiyor. Başlat'a basınca kurulum açılır.",
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = (12.5f * 1.4f).sp,
            )
        }
        if (syncNote != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                syncNote,
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = (12.5f * 1.35f).sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            label = if (here) "Yeniden Uygula ve Başlat" else "Başlat",
            onClick = if (busy) null else onStart,
            icon = Icons.Rounded.PlayArrow,
            busy = busy,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            SoftButton(
                label = "Sadece Uygula",
                onClick = if (busy) null else onApply,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Tune,
            )
            Spacer(Modifier.width(10.dp))
            SoftButton(
                label = "Geri Al",
                onClick = if (busy || !game.active) null else onStop,
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Rounded.Undo,
                color = if (game.active) c.warn else null,
            )
        }
        Spacer(Modifier.height(10.dp))
        SoftButton(
            label = "Her Şeyi Eski Haline Döndür",
            onClick = if (busy) null else onReset,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Refresh,
            color = c.warn,
        )
    }
}

@Composable
private fun GamePresetList(game: GameModeController, busy: Boolean) {
    val c = AppTheme.colors
    val cfg = game.config
    var matched: GamePreset? = null
    for (p in kGamePresets) {
        if (p.matches(cfg)) matched = p
    }
    Column {
        for (p in kGamePresets) {
            val isMatch = matched != null && p.id == matched.id
            val tint = if (isMatch) c.primary else c.muted
            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                AppCard(
                    onClick = if (busy) null else ({ game.applyPreset(p) }),
                    borderColor = if (isMatch) c.primary.faded(0.6f) else null,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tint.faded(0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                presetIcon(p.icon),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.title, color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                            Spacer(Modifier.height(3.dp))
                            GameHint(presetLine(p))
                        }
                        if (isMatch) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(22.dp),
                            )
                        }
                    }
                }
            }
        }
        if (matched == null) {
            GameHint(
                "Özel ayar: gelişmiş ayarlarda değişiklik yaptınız.",
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun GameSuggestionCard(game: GameModeController, busy: Boolean) {
    val c = AppTheme.colors
    val sug = game.suggestion ?: return
    val pct = (sug.scale * 100).roundToInt()
    val label = if (sug.fps == null) {
        "Uygula: ${if (sug.scale >= 1) "Tam" else "%$pct"} çözünürlük"
    } else {
        "Uygula: %$pct · ${sug.fps} FPS"
    }
    AppCard(borderColor = c.warn.faded(0.5f)) {
        Text("Öneri", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
        Spacer(Modifier.height(4.dp))
        GameHint(sug.reason)
        Spacer(Modifier.height(10.dp))
        SoftButton(
            label = label,
            onClick = if (busy) null else ({ game.applySuggestion() }),
            icon = Icons.Rounded.AutoFixHigh,
        )
    }
}

@Composable
private fun GameReportCard(game: GameModeController) {
    val c = AppTheme.colors
    val s = game.stats
    val d = game.diagnosis
    if (s == null) {
        AppCard {
            Text(
                "Henüz kayıt yok. Oyun Modu açıkken oynayın; oyun ön plandayken her 10 saniyede " +
                    "FPS, ısıl durum ve işlemci hızı kaydedilir. Bitince buraya dönün.",
                color = c.muted,
                fontSize = 13.sp,
                lineHeight = (13f * 1.45f).sp,
            )
        }
        return
    }

    val color = if (d == null) {
        c.muted
    } else if (d.kind == DiagnosisKind.OnTarget) {
        c.good
    } else if (d.kind == DiagnosisKind.Unknown) {
        c.muted
    } else {
        c.warn
    }

    val avgFps = s.avgFps
    val minFps = s.minFps
    val maxThermal = s.maxThermal
    val cpuRatio = s.avgCpuRatio

    AppCard(borderColor = color.faded(0.4f)) {
        ReportRow("Süre", formatSeconds(s.durationSec))
        ReportRow("Ortalama FPS", if (avgFps == null) "ölçülemedi" else fixed(avgFps, 1))
        ReportRow("En düşük FPS", if (minFps == null) "ölçülemedi" else fixed(minFps, 1))
        ReportRow("En yüksek ısıl durum", if (maxThermal == null) "okunamadı" else thermalLabel(maxThermal))
        ReportRow(
            "Büyük çekirdek hızı",
            if (cpuRatio == null) "okunamadı" else "%${(cpuRatio * 100).roundToInt()}",
        )
        if (s.guardTriggered) ReportRow("Isı koruması", "devreye girdi")
        if (d != null) {
            Spacer(Modifier.height(10.dp))
            Text(d.title, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                d.advice,
                color = c.muted,
                fontSize = 13.sp,
                lineHeight = (13f * 1.45f).sp,
            )
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = c.muted, fontSize = 13.5.sp)
        Text(value, color = c.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun GameResultCard(result: GameRunResult) {
    val c = AppTheme.colors
    AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        for (i in result.steps.indices) {
            val step = result.steps[i]
            val icon = if (step.ok) {
                Icons.Rounded.CheckCircle
            } else if (step.optional) {
                Icons.Rounded.RemoveCircleOutline
            } else {
                Icons.Rounded.Error
            }
            val tint = if (step.ok) c.good else if (step.optional) c.muted else c.danger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(step.label, color = c.text, fontWeight = FontWeight.SemiBold)
                    if (!step.ok) {
                        Text(
                            if (step.optional) {
                                "Bu cihazda desteklenmiyor olabilir. ${step.detail}"
                            } else {
                                step.detail
                            },
                            modifier = Modifier.padding(top = 2.dp),
                            color = c.muted,
                            fontSize = 12.5.sp,
                            lineHeight = (12.5f * 1.35f).sp,
                        )
                    }
                }
            }
            if (i != result.steps.size - 1) RowDivider()
        }
    }
}

@Composable
private fun GameHonestyCard() {
    val items = listOf(
        "Bir oyunun kare hızı dışarıdan YÜKSELTİLEMEZ; burada oyunun GPU yükü azaltılır ve " +
            "kare hızına üst sınır konur. 90 FPS, ancak oyunun işlemcisi o hızı üretebiliyorsa görülür.",
        "Çok sayıda birim, büyük hesaplar ya da yoğun fizik içeren oyunlarda yavaşlamanın büyük " +
            "kısmı işlemciden gelir. Bu durumda çözünürlük düşürmek ısınmayı ve pili belirgin azaltır, " +
            "akıcılığı ise sınırlı artırır.",
        "Ana sayfadaki sıcaklık PİL sıcaklığıdır. Oyun bittikten sonra da bir süre yüksek kalır; " +
            "şarj olurken oynamak, kılıf ve sıcak ortam üstüne ekler. \"Buz\" modu ve 38 °C ısı koruması " +
            "ısıyı en çok düşüren seçimdir; yine de oyun işlemci kaynaklıysa sıcaklık tamamen önlenemez.",
        "Oyunun kendi motoru (ör. Minecraft'ın RenderDragon'ı) değiştirilemez; seçilebilen şey " +
            "oyunun kullandığı grafik sürücüsü yoludur (yerel GLES ya da ANGLE/Vulkan). Etkisi GPU'ya " +
            "göre artı ya da eksi olabilir; Son oyun oturumundan ölçerek karşılaştırın.",
        "Ekran Hz'si ve çözünürlük ayarları üreticiye (HyperOS) göre farklı davranabilir. " +
            "Uygulanamayan adımlar sonuç listesinde açıkça işaretlenir; başarı uydurulmaz.",
        "FPS değeri, oyunun kare zamanlarından (SurfaceFlinger) cihazda ölçülür; okunamazsa " +
            "\"ölçülemedi\" yazar. Teşhis sezgiseldir ve \"olası\" der, kesin hüküm vermez.",
        "Oyun bitince Geri Al'a basın: ekran 90 Hz'de kilitli kalırsa pil gereksiz harcanır. " +
            "Eski değerler kayıtlıdır, uygulama kapansa bile geri alınır.",
    )
    val c = AppTheme.colors
    AppCard {
        for (text in items) {
            Text(
                text,
                modifier = Modifier.padding(bottom = 10.dp),
                color = c.muted,
                fontSize = 13.sp,
                lineHeight = (13f * 1.45f).sp,
            )
        }
    }
}
