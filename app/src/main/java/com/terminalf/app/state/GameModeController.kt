package com.terminalf.app.state

import com.terminalf.app.core.AngleSelection
import com.terminalf.app.core.CpuPolicy
import com.terminalf.app.core.Diagnosis
import com.terminalf.app.core.GameKind
import com.terminalf.app.core.GameModeConfig
import com.terminalf.app.core.GamePreset
import com.terminalf.app.core.GameSession
import com.terminalf.app.core.GameStep
import com.terminalf.app.core.GraphicsBackend
import com.terminalf.app.core.Json
import com.terminalf.app.core.PixelSize
import com.terminalf.app.core.ResolutionMethod
import com.terminalf.app.core.ScaleSuggestion
import com.terminalf.app.core.SessionStats
import com.terminalf.app.core.asList
import com.terminalf.app.core.asMap
import com.terminalf.app.core.buildApplySteps
import com.terminalf.app.core.buildRevertSteps
import com.terminalf.app.core.buildWatchdogKill
import com.terminalf.app.core.buildWatchdogLaunch
import com.terminalf.app.core.buildWatchdogScript
import com.terminalf.app.core.buildWatchdogStatus
import com.terminalf.app.core.customLines
import com.terminalf.app.core.detectGameKind
import com.terminalf.app.core.diagnose
import com.terminalf.app.core.isSafePackage
import com.terminalf.app.core.kAngleKeys
import com.terminalf.app.core.kAnimationKeys
import com.terminalf.app.core.kBrightnessKeys
import com.terminalf.app.core.kCpuPolicyReadCommand
import com.terminalf.app.core.kGamePresets
import com.terminalf.app.core.kLowPowerKey
import com.terminalf.app.core.kRefreshKeys
import com.terminalf.app.core.kWatchdogClearMark
import com.terminalf.app.core.kWatchdogReadLog
import com.terminalf.app.core.kZenKey
import com.terminalf.app.core.normalizeSettingValue
import com.terminalf.app.core.parseActiveRefreshRate
import com.terminalf.app.core.parseListSetting
import com.terminalf.app.core.parseCpuPolicies
import com.terminalf.app.core.parseFocusedPackage
import com.terminalf.app.core.parseWatchLog
import com.terminalf.app.core.parseWatchdogStatus
import com.terminalf.app.core.parseWmDensity
import com.terminalf.app.core.parseWmSize
import com.terminalf.app.core.recommendedConfig
import com.terminalf.app.core.str
import com.terminalf.app.core.suggestSettings
import com.terminalf.app.core.topPolicies
import com.terminalf.app.core.SettingKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Tek bir adımın sonucu. */
data class GameStepResult(
    val label: String,
    val ok: Boolean,
    val optional: Boolean,
    val detail: String = "",
)

/** Oyun modunu açma/kapama işleminin toplu sonucu. */
data class GameRunResult(val steps: List<GameStepResult>, val note: String? = null) {
    /** Zorunlu adımların hepsi başarılı mı? (Opsiyonel adımlar sayılmaz.) */
    val ok: Boolean get() = steps.all { it.ok || it.optional } && note == null

    val applied: Int get() = steps.count { it.ok }
    val optionalFailed: List<GameStepResult> get() = steps.filter { !it.ok && it.optional }
    val requiredFailed: List<GameStepResult> get() = steps.filter { !it.ok && !it.optional }

    val summary: String
        get() {
            note?.let { return it }
            val parts = ArrayList<String>()
            parts.add("${steps.count { it.ok }}/${steps.size} adım uygulandı")
            if (optionalFailed.isNotEmpty()) parts.add("${optionalFailed.size} adım bu cihazda desteklenmedi")
            if (requiredFailed.isNotEmpty()) parts.add("${requiredFailed.size} adım başarısız")
            return parts.joinToString(" · ")
        }
}

/** Oyun Modu'na eklenmiş bir oyun (her oyunun kendi ayarı vardır). */
data class SavedGame(val packageName: String, val name: String)

private data class Snapshot(
    val session: GameSession,
    val physicalSize: PixelSize?,
    val physicalDensity: Int?,
    val brightness: Int?,
    val cpuPolicies: List<CpuPolicy>,
    /** Mevcut ANGLE seçimi; `null` = grafik motoru istenmedi ya da ayar güvenle okunamadı. */
    val angle: AngleSelection? = null,
    /** `getprop ro.gfx.angle.supported` sonucu (`null` = denetlenmedi). */
    val angleSupported: Boolean? = null,
)

/**
 * "Oyun Modu": ağır oyunlar için GPU yükünü azaltır, ekranı sabit hıza kilitler, kare üst sınırı
 * koyar ve OYUN BİTİNCE her şeyi ESKİ HALİNE döndürür.
 *
 * Güvenlik: değişiklik yapılmadan ÖNCE eski değerler diske yazılır; uygulama kapansa ya da çökse
 * bile "Geri Al" doğru değerlere döner.
 */
class GameModeController(
    val control: ControlController,
    private val store: SettingsStore,
    private val launcher: suspend (String) -> Unit = {},
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) : Observable() {

    companion object {
        private const val CONFIG_KEY = "game_config"
        private const val SESSION_KEY = "game_session"
        private const val PROFILES_KEY = "game_profiles"
        private const val LAST_RUN_KEY = "game_last_run"

        /** Ayarları uygulamak için tanınan en uzun süre; dolunca oyun yine de açılır. */
        private const val APPLY_BUDGET_MS = 30_000L
    }

    /** Paket adı -> o oyunun ayarı. Sıra, ekleme sırasıdır. */
    private val profiles = LinkedHashMap<String, GameModeConfig>()
    private val names = HashMap<String, String>()
    private var selected: String? = null
    private var lastPackage: String? = null
    private var lastTargetFps: Int? = null
    private var sessionState: GameSession? = null

    var busy: Boolean = false
        private set

    /** Okunabildiyse etkin ekran yenileme hızı. */
    var displayHz: Double? = null
        private set

    /** `null` = henüz denetlenmedi. */
    var installed: Boolean? = null
        private set
    var lastResult: GameRunResult? = null
        private set

    /** Cihazdaki izleyici (otomatik geri alma + ölçüm) çalışıyor mu? `null` = henüz denetlenmedi. */
    var watchdogAlive: Boolean? = null
        private set

    /** İzleyicinin kaydettiği son oturumun özeti (yoksa `null`). */
    var stats: SessionStats? = null
        private set

    /** `dumpsys window` ile okunabilen ön plan paketi; `null` = okunamadı. */
    var foregroundPackage: String? = null
        private set

    /** İzleyicinin kendiliğinden yaptığı bir işlemin kullanıcıya notu. */
    var syncNote: String? = null
        private set

    /** Seçili oyunun ayarı. Hiç oyun seçilmediyse varsayılan yapılandırma. */
    val config: GameModeConfig
        get() {
            val s = selected
            return (if (s == null) null else profiles[s]) ?: GameModeConfig()
        }

    /** Eklenmiş oyunlar (ekleme sırasıyla). */
    val games: List<SavedGame>
        get() = profiles.keys.map { SavedGame(it, names[it] ?: it) }

    /** Oyun Modu için bir oyun seçili mi? */
    val hasGame: Boolean get() = selected != null && profiles.containsKey(selected)

    val selectedPackage: String? get() = selected

    /** Seçili oyunun görünen adı. */
    val selectedName: String get() = names[selected] ?: selected ?: ""

    /** Açık oturum seçili oyuna mı ait? */
    val activeForSelected: Boolean
        get() = sessionState != null && sessionState!!.packageName == config.packageName

    /** Son oturum ölçümüne göre bir sonraki oturum için ayar önerisi. */
    val suggestion: ScaleSuggestion?
        get() = if (lastPackage == config.packageName) suggestSettings(stats, config) else null

    /** Seçili oyunun türü (yalnızca ipucu ve önerilen ayar için). */
    val kind: GameKind get() = detectGameKind(config.packageName)

    /** Seçili oyunun türüne göre önerilen ayar (yoksa `null`). */
    val recommended: GameModeConfig? get() = recommendedConfig(kind, config)

    /** Oyun modu şu an açık mı (geri alınacak bir şey var mı)? */
    val active: Boolean get() = sessionState != null
    val session: GameSession? get() = sessionState

    /** Son oturumun olası darboğaz teşhisi. */
    val diagnosis: Diagnosis?
        get() {
            val s = stats ?: return null
            return diagnose(s, lastTargetFps ?: config.targetFps)
        }

    suspend fun load() {
        try {
            sessionState = GameSession.decode(store.readString(SESSION_KEY, ""))
            loadProfiles(store.readString(PROFILES_KEY, ""))
            if (profiles.isEmpty()) {
                // Eski sürümde tek bir oyun ayarı vardı; varsa onu ilk oyun yap.
                val legacy = store.readString(CONFIG_KEY, "")
                if (legacy.isNotEmpty()) {
                    val cfg = GameModeConfig.decode(legacy)
                    profiles[cfg.packageName] = cfg
                    names[cfg.packageName] = cfg.packageName
                    selected = cfg.packageName
                }
            }
            val last = store.readString(LAST_RUN_KEY, "").split(':')
            if (last.size == 2) {
                lastPackage = if (isSafePackage(last[0])) last[0] else null
                lastTargetFps = last[1].toIntOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Okunamazsa varsayılanlarla devam.
        }
        notifyChanged()
    }

    private fun loadProfiles(raw: String) {
        if (raw.isEmpty()) return
        try {
            val json = Json.parse(raw).asMap() ?: error("bozuk")
            for (item in json["games"].asList() ?: emptyList()) {
                val m = item.asMap() ?: error("bozuk")
                val cfg = GameModeConfig.fromJson(m["config"].asMap() ?: error("bozuk"))
                profiles[cfg.packageName] = cfg
                val n = m.str("name")
                names[cfg.packageName] = if (n != null && n.trim().isNotEmpty()) n else cfg.packageName
            }
            val sel = json.str("selected")
            selected = if (sel != null && profiles.containsKey(sel)) {
                sel
            } else if (profiles.isEmpty()) {
                null
            } else {
                profiles.keys.first()
            }
        } catch (_: Exception) {
            profiles.clear()
            names.clear()
            selected = null
        }
    }

    private fun persistProfiles() {
        val json = linkedMapOf<String, Any?>(
            "selected" to selected,
            "games" to profiles.entries.map { e ->
                linkedMapOf<String, Any?>("name" to (names[e.key] ?: e.key), "config" to e.value.toJson())
            },
        )
        try {
            store.writeString(PROFILES_KEY, Json.stringify(json))
        } catch (_: Exception) {
        }
    }

    /** Seçili oyunun ayarını günceller. Hiç oyun yoksa, ayardaki paketle bir oyun oluşturur. */
    fun updateConfig(next: GameModeConfig) {
        if (!isSafePackage(next.packageName)) return
        val sel = selected
        if (sel == null || !profiles.containsKey(sel)) {
            profiles[next.packageName] = next
            if (!names.containsKey(next.packageName)) names[next.packageName] = next.packageName
            selected = next.packageName
            installed = null
        } else if (sel == next.packageName) {
            profiles[sel] = next
        } else {
            installed = null
            // Paket adı değiştiyse kaydı yeni pakete taşı (sıra korunur).
            val entries = profiles.entries.map { it.key to it.value }
            profiles.clear()
            for ((k, v) in entries) {
                if (k == sel) profiles[next.packageName] = next else profiles[k] = v
            }
            names[next.packageName] = names.remove(sel) ?: next.packageName
            selected = next.packageName
        }
        notifyChanged()
        persistProfiles()
    }

    /** Hazır ayarı seçili oyuna uygular. */
    fun applyPreset(preset: GamePreset) = updateConfig(preset.applyTo(config))

    /** Oyun ekler (varsa yalnızca seçer). Yeni oyun, "Serin" hazır ayarıyla başlar. */
    suspend fun addGame(packageName: String, name: String): Boolean {
        if (!isSafePackage(packageName)) return false
        if (!profiles.containsKey(packageName)) {
            val cool = kGamePresets.first { it.id == "cool" }
            profiles[packageName] = cool.applyTo(GameModeConfig(packageName = packageName))
            names[packageName] = if (name.trim().isEmpty()) packageName else name.trim()
        }
        selected = packageName
        installed = null
        notifyChanged()
        persistProfiles()
        checkInstalled()
        return true
    }

    suspend fun selectGame(packageName: String) {
        if (!profiles.containsKey(packageName) || selected == packageName) return
        selected = packageName
        installed = null
        notifyChanged()
        persistProfiles()
        checkInstalled()
    }

    /** Oyunu listeden çıkarır (ayarları silinir; açık oturum varsa dokunulmaz). */
    fun removeGame(packageName: String) {
        if (!profiles.containsKey(packageName)) return
        profiles.remove(packageName)
        names.remove(packageName)
        if (selected == packageName) {
            selected = if (profiles.isEmpty()) null else profiles.keys.first()
        }
        installed = null
        notifyChanged()
        persistProfiles()
    }

    /** Türe özel önerilen ayarı seçili oyuna uygular. */
    fun applyRecommended() {
        val rec = recommended ?: return
        updateConfig(rec)
    }

    /**
     * Oyunu önceden derler (`speed-profile`): Java/Kotlin tarafı ağır oyunlarda ilk açılış ve
     * takılmaları azaltabilir; yerel (C++) motorlarda etkisi sınırlıdır. Geri alınacak bir
     * değişiklik yapmaz.
     */
    suspend fun compileSelected(): GameStepResult {
        val label = "Oyunu önceden derle"
        if (!control.ready) return GameStepResult(label, false, true, "Önce tam kontrolü etkinleştirin.")
        val pkg = config.packageName
        if (!isSafePackage(pkg)) return GameStepResult(label, false, true, "Geçersiz paket adı.")
        if (busy) return GameStepResult(label, false, true, "Başka bir işlem sürüyor.")
        busy = true
        notifyChanged()
        try {
            val o = control.shell(label, "cmd package compile -m speed-profile -f $pkg")
            return GameStepResult(label, o.ok, true, o.message)
        } finally {
            busy = false
            notifyChanged()
        }
    }

    /** Önerilen çözünürlüğü (ve varsa kare hızını) seçili oyuna uygular. */
    fun applySuggestion() {
        val sug = suggestion ?: return
        updateConfig(config.copy(scale = sug.scale, targetFps = sug.fps ?: config.targetFps))
    }

    /** Tek dokunuş: seçili oyunun ayarlarını uygular ve oyunu açar. */
    suspend fun startSelected(launch: Boolean = true): GameRunResult = start(launch = launch)

    private suspend fun read(command: String): String {
        return try {
            control.exec(command).output
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    /** Oyun bu cihazda kurulu mu? (`pm path`) */
    suspend fun checkInstalled() {
        if (!control.ready || !isSafePackage(config.packageName)) return
        val out = read("pm path ${config.packageName}")
        installed = out.contains("package:")
        notifyChanged()
    }

    /** Seçili oyunun işlemi şu an çalışıyor mu? (`pidof`; okunamazsa `false`) */
    suspend fun isRunning(): Boolean {
        if (!control.ready || !isSafePackage(config.packageName)) return false
        return read("pidof ${config.packageName}").trim().isNotEmpty()
    }

    /** Etkin ekran yenileme hızını okur (okunamazsa `null`). */
    suspend fun refreshDisplayHz() {
        if (!control.ready) return
        val out = read("dumpsys display | grep -m3 mActiveMode")
        displayHz = parseActiveRefreshRate(out)
        notifyChanged()
    }

    /** Ön plandaki uygulamayı okur (izleyicinin oyunu algılayabildiğini gösterir). */
    suspend fun detectForeground() {
        if (!control.ready) return
        val out = read("dumpsys window | grep -m1 mCurrentFocus")
        foregroundPackage = parseFocusedPackage(out)
        notifyChanged()
    }

    /**
     * İzleyicinin durumunu okur. İzleyici oyun kapanınca ayarları kendisi geri aldıysa (işaret
     * dosyası `ok`), kayıtlı oturum silinir; geri alma başarısız olduysa oturum SAKLANIR ve
     * kullanıcıya söylenir.
     */
    suspend fun syncWatchdog() {
        if (!control.ready) return
        val status = parseWatchdogStatus(read(buildWatchdogStatus()))
        watchdogAlive = status.alive
        if (!status.alive && sessionState != null) {
            if (status.revertedOk) {
                sessionState = null
                persistSession(null)
                read(kWatchdogClearMark)
                syncNote = "Oyun kapandığı için ayarlar otomatik geri alındı."
            } else if (status.revertFailed) {
                syncNote = "Otomatik geri alma tamamlanamadı; \"Geri Al\"a basın."
            }
        }
        notifyChanged()
    }

    /** İzleyicinin kaydettiği oturum günlüğünü okuyup özetler. */
    suspend fun refreshStats() {
        if (!control.ready) return
        stats = parseWatchLog(read(kWatchdogReadLog))
        notifyChanged()
    }

    private fun persistSession(s: GameSession?) {
        try {
            store.writeString(SESSION_KEY, s?.encode() ?: "")
        } catch (_: Exception) {
        }
    }

    private suspend fun runSteps(steps: List<GameStep>): MutableList<GameStepResult> {
        val results = ArrayList<GameStepResult>()
        for (step in steps) {
            val o = control.shell(step.label, step.command)
            results.add(GameStepResult(step.label, o.ok, step.optional, o.message))
        }
        return results
    }

    /**
     * Oyun modunu açar; [launch] ise oyunu da başlatır. Önce (varsa) önceki oturumu geri alır,
     * böylece eski değerler HER ZAMAN gerçek orijinaldir.
     */
    suspend fun start(launch: Boolean = true, overrideConfig: GameModeConfig? = null): GameRunResult {
        if (!control.ready) return GameRunResult(emptyList(), note = "Önce tam kontrolü etkinleştirin.")
        if (busy) return GameRunResult(emptyList(), note = "Başka bir işlem sürüyor.")
        val cfg = overrideConfig ?: config
        if (!isSafePackage(cfg.packageName)) return GameRunResult(emptyList(), note = "Geçersiz paket adı.")
        busy = true
        notifyChanged()
        try {
            // Ayarların uygulanması en çok APPLY_BUDGET_MS sürer; yetki yolu yanıt vermese bile oyun
            // her durumda açılır (kullanıcı sonsuza dek beklemez).
            var timedOut = false
            var result = withTimeoutOrNull(APPLY_BUDGET_MS) { applyPhase(cfg, launch) }
            if (result == null) {
                timedOut = true
                result = GameRunResult(
                    emptyList(),
                    note = "Ayarlar zamanında uygulanamadı (yetki yolu yanıt vermiyor). Geri Al ile kontrol edin.",
                )
            }
            lastResult = result

            // Önceki oturum geri alınamadıysa yeni oturum başlatılmaz; oyun da açılmaz.
            val blocked = !timedOut && result.note != null
            if (launch && !blocked) {
                delayFn(900)
                launcher(cfg.packageName)
            }
            return result
        } finally {
            busy = false
            notifyChanged()
        }
    }

    /** Eski değerleri kaydeder, ayarları uygular, izleyiciyi başlatır. */
    private suspend fun applyPhase(cfg: GameModeConfig, launch: Boolean): GameRunResult {
        // Eski izleyici ve eski işaret, yeni oturumu yanlışlıkla kapatmasın.
        read(buildWatchdogKill())
        read(kWatchdogClearMark)
        syncNote = null

        if (sessionState != null) {
            val undo = revertInternal()
            if (!undo.ok) {
                return GameRunResult(undo.steps, note = "Önceki oyun modu geri alınamadı; yeniden deneyin.")
            }
        }

        // 1) Eski değerleri oku ve DİSKE yaz (değişiklikten önce).
        val snap = snapshot(cfg)
        sessionState = snap.session
        persistSession(sessionState)
        lastPackage = cfg.packageName
        lastTargetFps = cfg.targetFps
        try {
            store.writeString(LAST_RUN_KEY, "${cfg.packageName}:${cfg.targetFps}")
        } catch (_: Exception) {
        }

        // 2) Uygula.
        val steps = buildApplySteps(
            cfg,
            physicalSize = snap.physicalSize,
            physicalDensity = snap.physicalDensity,
            brightness = snap.brightness,
            cpuPolicies = snap.cpuPolicies,
            angle = snap.angle,
            restartGame = launch,
        )
        val results = runSteps(steps)
        val extra = ArrayList<GameStepResult>()
        if (cfg.method == ResolutionMethod.WholeScreen && cfg.reducesResolution && snap.physicalSize == null) {
            extra.add(GameStepResult("Ekran çözünürlüğü", false, false, "Ekran boyutu okunamadı, atlandı."))
        }
        if (cfg.cpuCap && control.path != AccessPath.Root) {
            extra.add(
                GameStepResult(
                    "İşlemci hızını sınırla", false, true, "Bu adım root gerektirir; Shizuku ile yapılamaz.",
                ),
            )
        } else if (cfg.cpuCap && snap.cpuPolicies.isEmpty()) {
            extra.add(GameStepResult("İşlemci hızını sınırla", false, true, "İşlemci frekans bilgisi okunamadı."))
        }
        if (cfg.backend != GraphicsBackend.Default && snap.angle == null) {
            extra.add(
                GameStepResult(
                    "Grafik motoru: ${cfg.backend.label}", false, true,
                    "Mevcut sürücü ayarı güvenle okunamadı; başka uygulamaların ayarı bozulmasın diye dokunulmadı.",
                ),
            )
        } else if (cfg.backend == GraphicsBackend.Angle && snap.angleSupported == false) {
            extra.add(
                GameStepResult(
                    "ANGLE desteği", false, true,
                    "Cihaz ANGLE desteği bildirmiyor; ayar yazıldı ama etkisiz kalabilir.",
                ),
            )
        }
        if (cfg.dimScreen && snap.brightness == null) {
            extra.add(GameStepResult("Ekran parlaklığını kıs", false, true, "Mevcut parlaklık okunamadı."))
        }
        val custom = customLines(cfg.customStart)
        if (custom.isNotEmpty()) {
            results.addAll(runSteps(custom.map { GameStep("Özel komut: ${it.take(40)}", it, optional = true) }))
        }
        if (cfg.autoRevert) {
            results.add(startWatchdog(snap.session, cfg))
        }
        val result = GameRunResult(results + extra)
        lastResult = result
        refreshDisplayHz()
        return result
    }

    /**
     * İzleyiciyi başlatır ve gerçekten çalıştığını doğrular; çalışmıyorsa adım BAŞARISIZ görünür
     * (başarı uydurulmaz).
     */
    private suspend fun startWatchdog(session: GameSession, cfg: GameModeConfig): GameStepResult {
        val label = "Oyun kapanınca otomatik geri al"
        val script = buildWatchdogScript(session, guardTempC = cfg.guardTempC)
        if (script.isEmpty()) {
            return GameStepResult(label, false, true, "Geçersiz paket adı.")
        }
        val o = control.shell(label, buildWatchdogLaunch(script))
        delayFn(600)
        syncWatchdog()
        if (!o.ok) return GameStepResult(label, false, true, o.message)
        if (watchdogAlive != true) {
            return GameStepResult(
                label, false, true,
                "İzleyici başlatılamadı; oyun bitince Geri Al'a kendiniz basın.",
            )
        }
        return GameStepResult(label, true, true)
    }

    /** Her şeyi eski haline getirir. */
    suspend fun stop(): GameRunResult {
        if (!control.ready) return GameRunResult(emptyList(), note = "Önce tam kontrolü etkinleştirin.")
        if (busy) return GameRunResult(emptyList(), note = "Başka bir işlem sürüyor.")
        if (sessionState == null) return GameRunResult(emptyList(), note = "Geri alınacak bir şey yok.")
        busy = true
        notifyChanged()
        try {
            val r = revertInternal()
            lastResult = r
            refreshDisplayHz()
            return r
        } finally {
            busy = false
            notifyChanged()
        }
    }

    private suspend fun revertInternal(): GameRunResult {
        val session = sessionState ?: return GameRunResult(emptyList())
        // İzleyici, geri alma sırasında araya girmesin.
        read(buildWatchdogKill())
        watchdogAlive = false
        val results = runSteps(buildRevertSteps(session))
        val endCommands = profiles[session.packageName]?.let { customLines(it.customEnd) } ?: emptyList()
        if (endCommands.isNotEmpty()) {
            results.addAll(runSteps(endCommands.map { GameStep("Özel komut: ${it.take(40)}", it, optional = true) }))
        }
        val result = GameRunResult(results)
        // Yalnızca zorunlu adımlar başarılıysa oturumu kapat; aksi halde "Geri Al" tekrar
        // denenebilsin diye anlık görüntü saklanır.
        if (result.requiredFailed.isEmpty()) {
            sessionState = null
            persistSession(null)
            read(kWatchdogClearMark)
        }
        return result
    }

    private suspend fun snapshot(cfg: GameModeConfig): Snapshot {
        val settings = LinkedHashMap<String, String?>()
        val keys = ArrayList<SettingKey>()
        keys.addAll(kRefreshKeys)
        if (cfg.animationsOff) keys.addAll(kAnimationKeys)
        if (cfg.dnd) keys.add(kZenKey)
        if (cfg.batterySaver) keys.add(kLowPowerKey)
        if (cfg.dimScreen) keys.addAll(kBrightnessKeys)
        for (key in keys) {
            settings[key.id] = normalizeSettingValue(read(key.readCommand))
        }

        var angle: AngleSelection? = null
        var angleSupported: Boolean? = null
        if (cfg.backend != GraphicsBackend.Default && isSafePackage(cfg.packageName)) {
            val pk = parseListSetting(read(kAngleKeys[0].readCommand))
            val vs = parseListSetting(read(kAngleKeys[1].readCommand))
            if (pk.safe && vs.safe) {
                angle = AngleSelection(pk.value, vs.value)
                settings[kAngleKeys[0].id] = pk.value
                settings[kAngleKeys[1].id] = vs.value
            }
            if (cfg.backend == GraphicsBackend.Angle) {
                angleSupported = read("getprop ro.gfx.angle.supported").trim() == "true"
            }
        }

        var brightness: Int? = null
        if (cfg.dimScreen) brightness = (settings["system:screen_brightness"] ?: "").toIntOrNull()

        var cpuPolicies: List<CpuPolicy> = emptyList()
        val cpuMax = LinkedHashMap<String, String>()
        if (cfg.cpuCap && control.path == AccessPath.Root) {
            cpuPolicies = parseCpuPolicies(read(kCpuPolicyReadCommand))
            for (p in topPolicies(cpuPolicies)) cpuMax[p.name] = "${p.curMax}"
        }

        var physical: PixelSize? = null
        var density: Int? = null
        var sizeOverride: String? = null
        var densityOverride: String? = null
        var changedScreen = false
        if (cfg.method == ResolutionMethod.WholeScreen && cfg.reducesResolution) {
            val ws = parseWmSize(read("wm size"))
            val wd = parseWmDensity(read("wm density"))
            if (ws != null && wd != null) {
                physical = ws.physical
                density = wd.physical
                sizeOverride = ws.override?.toString()
                densityOverride = wd.override?.toString()
                changedScreen = true
            }
        }

        return Snapshot(
            session = GameSession(
                packageName = cfg.packageName,
                settings = settings,
                touchedGameManager = true,
                sizeOverride = sizeOverride,
                densityOverride = densityOverride,
                changedScreen = changedScreen,
                cpuMax = cpuMax,
                startedAtMs = clock(),
                fixedPerf = cfg.fixedPerf,
            ),
            physicalSize = physical,
            physicalDensity = density,
            brightness = brightness,
            cpuPolicies = cpuPolicies,
            angle = angle,
            angleSupported = angleSupported,
        )
    }
}
