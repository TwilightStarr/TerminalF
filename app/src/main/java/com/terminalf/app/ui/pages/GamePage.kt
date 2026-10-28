package com.terminalf.app.ui.pages

import com.terminalf.app.core.tr
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
import com.terminalf.app.core.kBedrockViewChoices
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
                    title = tr("The whole screen resolution will be lowered", "Tüm ekran çözünürlüğü düşürülecek"),
                    message = tr("The whole phone runs at a low resolution while the game is on (text looks blurry). When the game closes it returns to normal by itself; if not, press Revert. The old values are saved, so it can be reverted even if you close the app.", "Telefonun tamamı oyun boyunca düşük çözünürlükte çalışır (yazılar bulanık görünür). Oyun kapanınca kendiliğinden eski haline döner; olmazsa Geri Al'a basın. Eski değerler kayıtlı olduğu için uygulamayı kapatsanız bile geri alınabilir."),
                    confirmLabel = tr("Apply", "Uygula"),
                )
                if (!ok) return@launch
            }
            if (launchGame && cfg.backend != GraphicsBackend.Default && game.isRunning()) {
                val ok = dialogs.confirm(
                    title = tr("The game will be restarted", "Oyun yeniden başlatılacak"),
                    message = tr("The graphics engine change takes effect when the game launches. The game is open right now; it will be closed and reopened, and unsaved progress may be lost. Save in the game first.", "Grafik motoru değişikliği oyun açılırken geçerli olur. Oyun şu an açık; kapatılıp yeniden açılacak ve kaydedilmemiş ilerleme kaybolabilir. Önce oyunda kaydedin."),
                    confirmLabel = tr("Continue", "Devam"),
                )
                if (!ok) return@launch
            }
            val result = game.startSelected(launch = launchGame)
            control.reportDone(tr("Game Mode", "Oyun Modu"), result.summary, ok = result.ok)
            toast.show(result.summary)
        }
    }

    fun stop() {
        scope.launch {
            val result = game.stop()
            control.reportDone(tr("Game Mode turned off", "Oyun Modu kapatıldı"), result.summary, ok = result.ok)
            toast.show(if (result.ok) tr("Everything is back to normal", "Her şey eski haline döndü") else result.summary)
        }
    }

    fun resetAll() {
        scope.launch {
            val result = game.forceReset()
            control.reportDone(tr("Everything was restored", "Her şey eski haline döndürüldü"), result.summary, ok = result.ok)
            toast.show(if (result.ok) tr("Everything is back to normal", "Her şey eski haline döndü") else result.summary)
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
            if (!ok) toast.show(tr("Invalid package name.", "Geçersiz paket adı."))
        }
    }

    fun removeSelected() {
        val pkg = game.selectedPackage ?: return
        scope.launch {
            val ok = dialogs.confirm(
                title = tr("Remove game from the list", "Oyunu listeden çıkar"),
                message = tr("The saved Game Mode settings for \"${game.selectedName}\" will be deleted. The game itself is not touched.", "\"${game.selectedName}\" için kayıtlı Oyun Modu ayarları silinir. Oyunun kendisine dokunulmaz."),
                confirmLabel = tr("Remove", "Çıkar"),
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
                tr("Game Mode", "Oyun Modu"),
                modifier = Modifier.weight(1f),
                color = c.text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            IconButton(onClick = { scope.launch { sheets.showLogSheet(control) } }) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = tr("Activity log", "İşlem günlüğü"), tint = c.muted)
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
            SectionHeader(tr("Choose a mode", "Mod seç"))
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
                    Text(if (showAdvanced) tr("Hide advanced settings", "Gelişmiş ayarları gizle") else tr("Advanced settings", "Gelişmiş ayarlar"), color = c.muted)
                }
            }
            if (showAdvanced) {
                if (suggestion != null) {
                    Spacer(Modifier.height(8.dp))
                    GameSuggestionCard(game = game, busy = false)
                }
                SectionHeader(tr("Detailed settings", "Ayrıntılı ayarlar"))
                GameGroups(game = game, cfg = cfg, busy = false)
                if (lastResult != null && lastResult.steps.isNotEmpty()) {
                    SectionHeader(tr("Last action", "Son işlem"))
                    GameResultCard(result = lastResult)
                }
                if (control.ready) {
                    SectionHeader(
                        tr("Last game session", "Son oyun oturumu"),
                        trailing = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        game.syncWatchdog()
                                        game.refreshStats()
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = tr("Refresh", "Yenile"), tint = c.muted)
                            }
                        },
                    )
                    GameReportCard(game = game)
                }
                SectionHeader(tr("Good to know", "Bilmeniz gerekenler"))
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
                    Text(tr("Remove this game from the list", "Bu oyunu listeden çıkar"), color = c.muted)
                }
            }
        }
    }
}

/** Mod kartında gösterilen tek satırlık özet. */
private fun presetLine(p: GamePreset): String {
    val tagline = when (p.id) {
        "ice" -> tr("Coolest and longest battery", "En serin ve en uzun pil")
        "cool" -> tr("Reduces heat", "Isınmayı azaltır")
        "balanced" -> tr("Balance of smoothness and heat", "Akıcılık ve ısı dengesi")
        "smooth" -> tr("Smoothest, runs hotter", "En akıcı, daha çok ısınır")
        else -> ""
    }
    val scale = if (p.scale >= 0.999) tr("full resolution", "tam çözünürlük") else tr("${(p.scale * 100).roundToInt()}% resolution", "%${(p.scale * 100).roundToInt()} çözünürlük")
    return "$tagline · ${p.targetFps} FPS · $scale"
}

private fun scaleLabel(v: Double): String =
    if (v >= 0.999) tr("Full resolution", "Tam çözünürlük") else tr("${(v * 100).roundToInt()}% resolution", "%${(v * 100).roundToInt()} çözünürlük")

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
    game.observe()
    fun set(next: GameModeConfig) = game.updateConfig(next)

    val screenOn = ArrayList<String>()
    cfg.lockHz?.let { screenOn.add(tr("Display $it Hz", "Ekran $it Hz")) }
    if (cfg.dimScreen) screenOn.add(tr("brightness ${cfg.dimPercent}%", "parlaklık %${cfg.dimPercent}"))
    if (cfg.animationsOff) screenOn.add(tr("animations off", "animasyon kapalı"))

    val systemOn = ArrayList<String>()
    if (cfg.killBackground) systemOn.add(tr("background cleared", "arka plan temiz"))
    if (cfg.dnd) systemOn.add(tr("notifications silenced", "bildirim sessiz"))
    if (cfg.batterySaver) systemOn.add(tr("battery saver", "pil tasarrufu"))
    if (cfg.cpuCap) systemOn.add(tr("CPU cap ${cfg.cpuCapPercent}%", "işlemci sınırı %${cfg.cpuCapPercent}"))

    val safetyOn = ArrayList<String>()
    safetyOn.add(if (cfg.autoRevert) tr("auto revert", "otomatik geri alma") else tr("manual revert", "geri alma elle"))
    if (cfg.autoRevert) {
        safetyOn.add(if (cfg.guardTempC == 0) tr("thermal guard off", "ısı koruması kapalı") else tr("guard ${cfg.guardTempC} °C", "koruma ${cfg.guardTempC} °C"))
    }

    EngineGroup(game = game, cfg = cfg, busy = busy)
    Spacer(Modifier.height(10.dp))
    GameGroup(
        icon = Icons.Rounded.Speed,
        title = tr("Performance", "Performans"),
        summary = "${cfg.targetFps} FPS · ${scaleLabel(cfg.scale)} · ${cfg.powerProfile.label}" +
            (if (cfg.bedrockViewDistance > 0) tr(" · view ${cfg.bedrockViewDistance}", " · görüş ${cfg.bedrockViewDistance}") else ""),
    ) {
        GameLabel(tr("Frame rate cap", "Kare hızı üst sınırı"))
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
                tr("On Android, Minecraft Bedrock itself is capped at 60 FPS. Choosing ${cfg.targetFps} FPS does not raise FPS, it only heats the phone; we recommend 60.", "Minecraft Bedrock Android'de oyunun kendisi 60 FPS'e sınırlı. ${cfg.targetFps} FPS seçmek FPS'i artırmaz, yalnızca telefonu ısıtır; 60 öneririz."),
            )
        }
        Spacer(Modifier.height(16.dp))
        GameLabel(tr("Game resolution", "Oyun çözünürlüğü"))
        Spacer(Modifier.height(8.dp))
        GameChoices(
            values = kScaleChoices,
            selected = cfg.scale,
            label = { v: Double -> if (v >= 1) tr("Full", "Tam") else tr("${(v * 100).roundToInt()}%", "%${(v * 100).roundToInt()}") },
            onSelected = enabledIf(!busy) { v: Double -> set(cfg.copy(scale = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(
            tr("Lower resolution is the setting that reduces GPU load, heat and battery use the most. Pixel-art games lose very little image quality.", "Düşük çözünürlük GPU yükünü, ısınmayı ve pil tüketimini en çok azaltan ayardır. Piksel sanat oyunlarda görüntü kaybı azdır."),
        )
        if (cfg.reducesResolution) {
            Spacer(Modifier.height(16.dp))
            GameLabel(tr("Resolution method", "Çözünürlük yöntemi"))
            Spacer(Modifier.height(8.dp))
            GameChoices(
                values = ResolutionMethod.values().toList(),
                selected = cfg.method,
                label = { v: ResolutionMethod ->
                    if (v == ResolutionMethod.PerGame) tr("Game only", "Yalnız oyun") else tr("Whole screen", "Tüm ekran")
                },
                onSelected = enabledIf(!busy) { v: ResolutionMethod -> set(cfg.copy(method = v)) },
            )
            Spacer(Modifier.height(6.dp))
            GameHint(
                if (cfg.method == ResolutionMethod.PerGame) {
                    tr("Safe method. Some manufacturers/games may not support it; if it cannot be applied you will see it in the result list and can try \"Whole screen\".", "Güvenli yöntem. Bazı üreticiler/oyunlar desteklemeyebilir; uygulanamazsa sonuç listesinde görürsünüz ve \"Tüm ekran\"ı deneyebilirsiniz.")
                } else {
                    tr("More powerful and works on every device, but the whole phone temporarily switches to a low resolution. Press Revert when the game ends.", "Daha güçlü ve her cihazda çalışır, ama telefonun tamamı geçici olarak düşük çözünürlüğe geçer. Oyun bitince Geri Al'a basın.")
                },
            )
        }
        if (cfg.packageName.startsWith("com.mojang.minecraft")) {
            Spacer(Modifier.height(6.dp))
            GameHint(
                tr("Lowering the resolution is not applied to Minecraft because it squeezes the image into a corner of the screen. Use the view distance setting below instead.", "Minecraft'ta çözünürlük düşürme görüntüyü ekranın köşesine sıkıştırdığı için uygulanmaz. Bunun yerine aşağıdaki görüş mesafesi ayarını kullanın."),
            )
        }
        if (game.kind == GameKind.MinecraftBedrock) {
            Spacer(Modifier.height(16.dp))
            GameLabel(tr("Minecraft view distance (the most effective setting for FPS and heat)", "Minecraft görüş mesafesi (FPS ve ısı için en etkili ayar)"))
            Spacer(Modifier.height(8.dp))
            GameChoices(
                values = kBedrockViewChoices,
                selected = cfg.bedrockViewDistance,
                label = { v: Int -> if (v == 0) tr("Don't touch", "Dokunma") else tr("$v chunks", "$v parça") },
                onSelected = enabledIf(!busy) { v: Int -> set(cfg.copy(bedrockViewDistance = v)) },
            )
            Spacer(Modifier.height(6.dp))
            GameHint(
                tr("Before the game starts, the view distance is lowered in the options file; fancy graphics, fancy skies and transparent leaves are turned off. CPU and GPU load drops, which keeps the frame rate steady and lowers heat. When the game ends the file is restored. Open the game after changing this (it is restarted if it is already open).", "Oyun açılmadan önce seçenek dosyasında görüş mesafesi düşürülür; süslü grafik, süslü gökyüzü ve saydam yapraklar kapanır. İşlemci ve GPU yükü azalır, bu da hem kare hızını sabit tutar hem ısıyı düşürür. Oyun bitince dosya eski haline döner. Oyunu bu ayardan sonra açın (açıksa yeniden başlatılır)."),
            )
        }
        Spacer(Modifier.height(16.dp))
        GameLabel(tr("Power profile", "Güç profili"))
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
        title = tr("Display", "Ekran"),
        summary = if (screenOn.isEmpty()) tr("Display is left alone", "Ekrana dokunulmaz") else screenOn.joinToString(" · "),
    ) {
        GameSwitchRow(
            title = tr("Lock the display to ${if (cfg.refreshHz > 0) cfg.refreshHz else autoLockHz(cfg.targetFps)} Hz", "Ekranı ${if (cfg.refreshHz > 0) cfg.refreshHz else autoLockHz(cfg.targetFps)} Hz'e kilitle"),
            subtitle = tr("Prevents the variable refresh rate from rising during the game; the display and GPU work less. Reverted when the game ends.", "Değişken yenileme hızının oyun sırasında yükselmesini engeller; ekran ve GPU daha az çalışır. Oyun bitince geri alınır."),
            value = cfg.lockRefreshRate,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(lockRefreshRate = v)) },
        )
        if (cfg.lockRefreshRate) {
            Spacer(Modifier.height(4.dp))
            GameLabel(tr("Refresh rate to lock", "Kilitlenecek yenileme hızı"))
            Spacer(Modifier.height(8.dp))
            GameChoices(
                values = kHzChoices,
                selected = cfg.refreshHz,
                label = { v: Int -> if (v == 0) tr("Auto", "Otomatik") else "$v Hz" },
                onSelected = enabledIf(!busy) { v: Int -> set(cfg.copy(refreshHz = v)) },
            )
            Spacer(Modifier.height(6.dp))
            GameHint(
                tr("Auto: the lowest panel rate that is an exact multiple of the frame rate is chosen (60 Hz for 30 FPS). The firmware may ignore a value your panel does not support.", "Otomatik: kare hızının tam katı olan en düşük panel hızı seçilir (30 FPS için 60 Hz). Panelinizin desteklemediği bir değeri üretici yazılımı yok sayabilir."),
            )
            Spacer(Modifier.height(8.dp))
        }
        RowDivider()
        GameSwitchRow(
            title = tr("Dim the screen brightness", "Ekran parlaklığını kıs"),
            subtitle = tr("During the game brightness drops to 60% of the current value. On an AMOLED display it is one of the settings that affects heat and battery the most.", "Oyun boyunca parlaklık mevcut değerin %60'ına iner. AMOLED ekranda ısınmayı ve pili en çok etkileyen ayarlardan biridir."),
            value = cfg.dimScreen,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(dimScreen = v)) },
        )
        if (cfg.dimScreen) {
            Spacer(Modifier.height(4.dp))
            GameLabel(tr("Brightness level (percent of the current value)", "Parlaklık oranı (mevcut değerin yüzdesi)"))
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
            title = tr("Turn off system animations", "Sistem animasyonlarını kapat"),
            subtitle = tr("Transition effects are removed during the game; display compositing load drops a little.", "Oyun boyunca geçiş efektleri kalkar; ekran birleştirme yükü biraz azalır."),
            value = cfg.animationsOff,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(animationsOff = v)) },
        )
    }
    Spacer(Modifier.height(10.dp))
    GameGroup(
        icon = Icons.Rounded.Memory,
        title = tr("System", "Sistem"),
        summary = if (systemOn.isEmpty()) tr("System is left alone", "Sisteme dokunulmaz") else systemOn.joinToString(" · "),
    ) {
        GameSwitchRow(
            title = tr("Clear the background", "Arka planı temizle"),
            subtitle = tr("Frees RAM before the game starts; there is less competition for the CPU.", "Başlamadan önce RAM'i boşaltır; işlemciye daha az rekabet kalır."),
            value = cfg.killBackground,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(killBackground = v)) },
        )
        RowDivider()
        GameSwitchRow(
            title = tr("Silence notifications", "Bildirimleri sustur"),
            subtitle = tr("Do Not Disturb (priority only) is turned on during the game; notifications do not cause stutter or wake the screen.", "Oyun sırasında Rahatsız Etme (yalnızca öncelikliler) açılır; bildirimler takılma ve ekran uyanması yaratmaz."),
            value = cfg.dnd,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(dnd = v)) },
        )
        RowDivider()
        GameSwitchRow(
            title = tr("Turn on Battery Saver", "Pil Tasarrufu'nu aç"),
            subtitle = tr("The system's Battery Saver mode stays on during the game; on most devices it limits the CPU and background work. It returns to normal when the game ends.", "Sistemin Pil Tasarrufu modu oyun boyunca açık kalır; çoğu cihazda işlemciyi ve arka plan işlerini kısar. Oyun bitince eski haline döner."),
            value = cfg.batterySaver,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(batterySaver = v)) },
        )
        RowDivider()
        GameSwitchRow(
            title = tr("Limit CPU speed (root)", "İşlemci hızını sınırla (root)"),
            subtitle = tr("The two strongest core clusters run at the percentage you choose; heat and speed fluctuations drop. Works only with root, cannot be applied with Shizuku.", "En güçlü iki çekirdek kümesi seçtiğiniz oranda çalışır; ısı ve hız dalgalanması azalır. Yalnızca root ile çalışır, Shizuku ile uygulanamaz."),
            value = cfg.cpuCap,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(cpuCap = v)) },
        )
        if (cfg.cpuCap) {
            Spacer(Modifier.height(4.dp))
            GameLabel(tr("CPU speed limit (percent of max speed)", "İşlemci hız sınırı (azami hızın yüzdesi)"))
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
        title = tr("Heat & safety", "Isı ve güvenlik"),
        summary = safetyOn.joinToString(" · "),
    ) {
        GameSwitchRow(
            title = tr("Auto-revert when the game closes", "Oyun kapanınca otomatik geri al"),
            subtitle = tr("A small watcher runs on the device: if the game is not in the foreground for about 40 seconds the settings are restored automatically; FPS / heat / CPU speed are recorded during the game. It keeps working even if TerminalF is closed.", "Cihazda küçük bir izleyici çalışır: oyun yaklaşık 40 saniye ön planda olmazsa ayarlar kendiliğinden eski haline döner; oyun boyunca FPS/ısı/işlemci hızı kaydedilir. Terminal kapansa da çalışır."),
            value = cfg.autoRevert,
            onChanged = enabledIf(!busy) { v: Boolean -> set(cfg.copy(autoRevert = v)) },
        )
        RowDivider()
        Spacer(Modifier.height(12.dp))
        GameLabel(tr("Thermal guard threshold (battery temperature)", "Isı koruması eşiği (pil sıcaklığı)"))
        Spacer(Modifier.height(8.dp))
        GameChoices(
            values = kGuardTempChoices,
            selected = cfg.guardTempC,
            label = { v: Int -> if (v == 0) tr("Off", "Kapalı") else "$v °C" },
            onSelected = enabledIf(!busy && cfg.autoRevert) { v: Int -> set(cfg.copy(guardTempC = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(
            if (cfg.autoRevert) {
                tr("If the battery temperature (or the system thermal state) reaches the threshold in two consecutive readings, the watcher tightens things once more: it dims the brightness further, turns on Battery Saver (if selected) and, with root, limits the CPU more. Everything is reverted when the game ends.", "Pil sıcaklığı (ya da sistemin ısıl durumu) art arda iki ölçümde eşiğe ulaşırsa izleyici bir kez daha sıkılaştırır: parlaklığı daha çok kısar, Pil Tasarrufu'nu açar (seçiliyse) ve root varsa işlemciyi daha çok sınırlar. Oyun bitince hepsi geri alınır.")
            } else {
                tr("The thermal guard relies on the watcher; turn on auto-revert to enable it.", "Isı koruması izleyiciye bağlıdır; açmak için otomatik geri almayı açın.")
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    val customCount = customLines(cfg.customStart).size + customLines(cfg.customEnd).size
    GameGroup(
        icon = Icons.Rounded.Code,
        title = tr("Custom commands", "Özel komutlar"),
        summary = if (customCount == 0) tr("Add your own commands", "Kendi komutlarını ekle") else tr("$customCount custom commands", "$customCount özel komut"),
    ) {
        GameLabel(tr("Run at start", "Başlarken çalışsın"))
        Spacer(Modifier.height(8.dp))
        GameCommandField(
            initial = cfg.customStart,
            resetKey = cfg.packageName + ":start",
            placeholder = tr("One command per line\ne.g. settings put global some_key 1", "Her satıra bir komut\nörn: settings put global some_key 1"),
            onChange = { v: String -> set(game.config.copy(customStart = v)) },
        )
        Spacer(Modifier.height(16.dp))
        GameLabel(tr("Run when Revert is pressed", "Geri Al'a basınca çalışsın"))
        Spacer(Modifier.height(8.dp))
        GameCommandField(
            initial = cfg.customEnd,
            resetKey = cfg.packageName + ":end",
            placeholder = tr("One command per line", "Her satıra bir komut"),
            onChange = { v: String -> set(game.config.copy(customEnd = v)) },
        )
        Spacer(Modifier.height(6.dp))
        GameHint(
            tr("Commands run in order with system permission (Shizuku/root); blank lines and lines starting with # are skipped (at most $kCustomMaxLines lines). End commands run only with Revert; they do not run in the on-device auto-revert when the game closes. Add commands at your own risk.", "Komutlar sistem yetkisiyle (Shizuku/root) sırayla çalışır; boş satırlar ve # ile başlayan satırlar atlanır (en çok $kCustomMaxLines satır). Bitiş komutları yalnızca Geri Al ile çalışır; oyun kapanınca cihazdaki otomatik geri almada çalışmaz. Komutları kendi sorumluluğunda ekle."),
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
    game.observe()
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val kind = game.kind
    val tips = gameTips(kind)
    val rec = game.recommended

    val summary = ArrayList<String>()
    summary.add(cfg.backend.label)
    if (cfg.fixedPerf) summary.add(tr("fixed performance", "sabit performans"))

    GameGroup(
        icon = Icons.Rounded.Bolt,
        title = tr("Graphics engine", "Grafik motoru"),
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
        GameLabel(tr("Graphics driver backend", "Grafik sürücüsü arka ucu"))
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
                tr("The driver is chosen when the game launches: if the game is open when you press Start it is closed and reopened. The choice is restored when the game ends.", "Sürücü oyun açılırken seçilir: Başlat'a basınca oyun açıksa kapatılıp yeniden açılır. Oyun bitince seçim eski haline döner."),
            )
        }
        Spacer(Modifier.height(10.dp))
        RowDivider()
        GameSwitchRow(
            title = tr("Fixed performance mode (experimental)", "Sabit performans modu (deneysel)"),
            subtitle = tr("Keeps CPU/GPU speed constant during the game; frame times may be smoother but the phone runs hotter. Requires Android 12+ and does not work on every device. It turns off automatically if the thermal guard kicks in.", "İşlemci/GPU hızını oyun boyunca sabit tutar; kare süreleri daha düzgün olabilir ama telefon daha çok ısınır. Android 12+ gerekir, her cihazda çalışmaz. Isı koruması devreye girerse otomatik kapanır."),
            value = cfg.fixedPerf,
            onChanged = enabledIf(!busy) { v: Boolean -> game.updateConfig(cfg.copy(fixedPerf = v)) },
        )
        RowDivider()
        Spacer(Modifier.height(12.dp))
        GameLabel(tr("Precompile the game", "Oyunu önceden derle"))
        Spacer(Modifier.height(4.dp))
        GameHint(
            tr("In games heavy on the Java/Kotlin side it can reduce first-launch time and stutter; the effect is limited on fully native (C++) engines. It is a one-time action and has nothing to revert.", "Java/Kotlin tarafı ağır oyunlarda ilk açılışı ve takılmaları azaltabilir; tamamen yerel (C++) motorlarda etkisi sınırlıdır. Tek seferliktir, geri alınacak bir şey yapmaz."),
        )
        Spacer(Modifier.height(10.dp))
        SoftButton(
            label = tr("Compile now", "Şimdi derle"),
            onClick = if (busy || !game.control.ready) {
                null
            } else {
                ({
                    scope.launch {
                        val r = game.compileSelected()
                        toast.show(if (r.ok) tr("Game compiled", "Oyun derlendi") else tr("Could not compile: ${r.detail}", "Derlenemedi: ${r.detail}"))
                    }
                })
            },
            icon = Icons.Rounded.Memory,
        )
        if (tips.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GameLabel(tr("Tips for ${kind.title}", "${kind.title} için ipuçları"))
            for (t in tips) {
                Spacer(Modifier.height(6.dp))
                GameHint("• $t")
            }
        }
        if (kind == GameKind.MinecraftBedrock) {
            val context = LocalContext.current
            Spacer(Modifier.height(16.dp))
            GameLabel(tr("Update Minecraft", "Minecraft'ı güncelle"))
            Spacer(Modifier.height(4.dp))
            GameHint(
                tr("Opens the Minecraft page on Google Play; update it there. Do not install APKs from other sources.", "Google Play'deki Minecraft sayfasını açar; güncellemeyi orada yaparsınız. Başka kaynaktan APK kurmayın."),
            )
            Spacer(Modifier.height(10.dp))
            SoftButton(
                label = tr("Open in Google Play", "Google Play'de aç"),
                onClick = { openPlayStore(context, kBedrockPackage) },
                icon = Icons.Rounded.Refresh,
            )
        }
        if (rec != null) {
            Spacer(Modifier.height(14.dp))
            SoftButton(
                label = tr("Apply recommended settings", "Önerilen ayarı uygula"),
                onClick = if (busy) null else ({ game.applyRecommended() }),
                icon = Icons.Rounded.AutoFixHigh,
            )
            Spacer(Modifier.height(6.dp))
            GameHint(
                tr("This is a starting point, not a guarantee. Play a session and compare the average FPS in \"Last game session\" with the default.", "Bu bir deneme noktasıdır, garanti değildir. Bir oyun oturumu oynayıp \"Son oyun oturumu\" ortalama FPS'ini varsayılanla karşılaştırın."),
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
                .background(c.green.faded(0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = c.green)
        }
        Spacer(Modifier.height(14.dp))
        Text(tr("Pick a game", "Bir oyun seçin"), color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Add any installed game. Each game keeps its own settings; with one tap we reduce heat, GPU load and battery use and launch the game. When the game closes everything returns to normal.", "Yüklü herhangi bir oyunu ekleyin. Her oyunun ayarı ayrı saklanır; tek dokunuşla ısıyı, GPU yükünü ve pil tüketimini azaltıp oyunu açarız. Oyun kapanınca her şey eski haline döner."),
            color = c.muted,
            fontSize = 13.sp,
            lineHeight = (13f * 1.45f).sp,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(label = tr("Choose Game", "Oyun Seç"), onClick = onAdd, icon = Icons.Rounded.Add)
    }
}

@Composable
private fun GameStrip(game: GameModeController, busy: Boolean, onAdd: () -> Unit) {
    game.observe()
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
            label = tr("Add game", "Oyun ekle"),
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
    control.observe()
    game.observe()
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
                label = if (here) tr("Game Mode ON", "Oyun modu AÇIK") else tr("Game Mode off", "Oyun modu kapalı"),
                color = if (here) c.good else c.muted,
            )
            if (otherActive) StatusPill(label = tr("On for another game", "Başka bir oyunda açık"), color = c.warn)
            if (ready && installed != null) {
                StatusPill(
                    label = if (installed) tr("Game installed", "Oyun yüklü") else tr("Game not found", "Oyun bulunamadı"),
                    color = if (installed) c.good else c.warn,
                )
            }
            if (hz != null) StatusPill(label = tr("Display ${hz.roundToInt()} Hz", "Ekran ${hz.roundToInt()} Hz"), color = c.muted)
            if (game.config.backend != GraphicsBackend.Default) {
                StatusPill(label = tr("Engine: ${game.config.backend.label}", "Motor: ${game.config.backend.label}"), color = c.primary)
            }
            if (here && alive != null) {
                StatusPill(
                    label = if (alive) tr("Auto revert: watching", "Otomatik geri alma: izliyor") else tr("Auto revert: off", "Otomatik geri alma: kapalı"),
                    color = if (alive) c.good else c.warn,
                )
            }
        }
        if (!ready) {
            Spacer(Modifier.height(10.dp))
            Text(
                tr("A one-time setup is needed for Game Mode. Pressing Start opens the setup.", "Oyun modu için tek seferlik kurulum gerekiyor. Başlat'a basınca kurulum açılır."),
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
            label = if (here) tr("Re-apply & Start", "Yeniden Uygula ve Başlat") else tr("Start", "Başlat"),
            onClick = if (busy) null else onStart,
            icon = Icons.Rounded.PlayArrow,
            busy = busy,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            SoftButton(
                label = tr("Apply Only", "Sadece Uygula"),
                onClick = if (busy) null else onApply,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Tune,
            )
            Spacer(Modifier.width(10.dp))
            SoftButton(
                label = tr("Revert", "Geri Al"),
                onClick = if (busy || !game.active) null else onStop,
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Rounded.Undo,
                color = if (game.active) c.warn else null,
            )
        }
        Spacer(Modifier.height(10.dp))
        SoftButton(
            label = tr("Restore Everything", "Her Şeyi Eski Haline Döndür"),
            onClick = if (busy) null else onReset,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Refresh,
            color = c.warn,
        )
    }
}

@Composable
private fun GamePresetList(game: GameModeController, busy: Boolean) {
    game.observe()
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
                tr("Custom: you changed the advanced settings.", "Özel ayar: gelişmiş ayarlarda değişiklik yaptınız."),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun GameSuggestionCard(game: GameModeController, busy: Boolean) {
    game.observe()
    val c = AppTheme.colors
    val sug = game.suggestion ?: return
    val pct = (sug.scale * 100).roundToInt()
    val label = if (sug.fps == null) {
        tr("Apply: ${if (sug.scale >= 1) "Full" else "$pct%"} resolution", "Uygula: ${if (sug.scale >= 1) "Tam" else "%$pct"} çözünürlük")
    } else {
        tr("Apply: $pct% · ${sug.fps} FPS", "Uygula: %$pct · ${sug.fps} FPS")
    }
    AppCard(borderColor = c.warn.faded(0.5f)) {
        Text(tr("Suggestion", "Öneri"), color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
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
    game.observe()
    val c = AppTheme.colors
    val s = game.stats
    val d = game.diagnosis
    if (s == null) {
        AppCard {
            Text(
                tr("No records yet. Play with Game Mode on; while the game is in the foreground FPS, thermal state and CPU speed are recorded every 10 seconds. Come back here when you are done.", "Henüz kayıt yok. Oyun Modu açıkken oynayın; oyun ön plandayken her 10 saniyede FPS, ısıl durum ve işlemci hızı kaydedilir. Bitince buraya dönün."),
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
        ReportRow(tr("Duration", "Süre"), formatSeconds(s.durationSec))
        ReportRow(tr("Average FPS", "Ortalama FPS"), if (avgFps == null) tr("not measured", "ölçülemedi") else fixed(avgFps, 1))
        ReportRow(tr("Lowest FPS", "En düşük FPS"), if (minFps == null) tr("not measured", "ölçülemedi") else fixed(minFps, 1))
        ReportRow(tr("Highest thermal state", "En yüksek ısıl durum"), if (maxThermal == null) tr("unavailable", "okunamadı") else thermalLabel(maxThermal))
        ReportRow(
            tr("Big core speed", "Büyük çekirdek hızı"),
            if (cpuRatio == null) tr("unavailable", "okunamadı") else tr("${(cpuRatio * 100).roundToInt()}%", "%${(cpuRatio * 100).roundToInt()}"),
        )
        if (s.guardTriggered) ReportRow(tr("Thermal guard", "Isı koruması"), tr("triggered", "devreye girdi"))
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
                                tr("May not be supported on this device. ${step.detail}", "Bu cihazda desteklenmiyor olabilir. ${step.detail}")
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
        tr("A game's frame rate cannot be RAISED from the outside; here the game's GPU load is reduced and a frame rate cap is set. 90 FPS is only seen if the game's CPU can produce that speed.", "Bir oyunun kare hızı dışarıdan YÜKSELTİLEMEZ; burada oyunun GPU yükü azaltılır ve kare hızına üst sınır konur. 90 FPS, ancak oyunun işlemcisi o hızı üretebiliyorsa görülür."),
        tr("In games with many units, heavy calculations or intense physics, most of the slowdown comes from the CPU. In that case lowering the resolution noticeably reduces heat and battery use but improves smoothness only a little.", "Çok sayıda birim, büyük hesaplar ya da yoğun fizik içeren oyunlarda yavaşlamanın büyük kısmı işlemciden gelir. Bu durumda çözünürlük düşürmek ısınmayı ve pili belirgin azaltır, akıcılığı ise sınırlı artırır."),
        tr("The temperature on the Home page is the BATTERY temperature. It stays high for a while after the game ends; playing while charging, a case and a hot environment add to it. The \"Ice\" mode and the 38 °C thermal guard are the choices that lower heat the most; still, if the game is CPU-bound the heat cannot be fully prevented.", "Ana sayfadaki sıcaklık PİL sıcaklığıdır. Oyun bittikten sonra da bir süre yüksek kalır; şarj olurken oynamak, kılıf ve sıcak ortam üstüne ekler. \"Buz\" modu ve 38 °C ısı koruması ısıyı en çok düşüren seçimdir; yine de oyun işlemci kaynaklıysa sıcaklık tamamen önlenemez."),
        tr("A game's own engine (e.g. Minecraft's RenderDragon) cannot be replaced; what can be chosen is the graphics driver path the game uses (native GLES or ANGLE/Vulkan). The effect can be positive or negative depending on the GPU; measure with Last game session and compare.", "Oyunun kendi motoru (ör. Minecraft'ın RenderDragon'ı) değiştirilemez; seçilebilen şey oyunun kullandığı grafik sürücüsü yoludur (yerel GLES ya da ANGLE/Vulkan). Etkisi GPU'ya göre artı ya da eksi olabilir; Son oyun oturumundan ölçerek karşılaştırın."),
        tr("Display Hz and resolution settings may behave differently depending on the manufacturer (HyperOS). Steps that cannot be applied are clearly marked in the result list; success is never faked.", "Ekran Hz'si ve çözünürlük ayarları üreticiye (HyperOS) göre farklı davranabilir. Uygulanamayan adımlar sonuç listesinde açıkça işaretlenir; başarı uydurulmaz."),
        tr("FPS is measured on the device from the game's frame times (SurfaceFlinger); if it cannot be read it says \"not measured\". The diagnosis is heuristic and says \"likely\", never a definite verdict.", "FPS değeri, oyunun kare zamanlarından (SurfaceFlinger) cihazda ölçülür; okunamazsa \"ölçülemedi\" yazar. Teşhis sezgiseldir ve \"olası\" der, kesin hüküm vermez."),
        tr("Press Revert when the game ends: if the display stays locked at 90 Hz the battery is wasted. The old values are saved and are reverted even if the app is closed.", "Oyun bitince Geri Al'a basın: ekran 90 Hz'de kilitli kalırsa pil gereksiz harcanır. Eski değerler kayıtlıdır, uygulama kapansa bile geri alınır."),
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
