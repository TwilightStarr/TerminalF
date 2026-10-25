package com.terminalf.app.core

import kotlin.math.abs

/**
 * Oyun Modu için saf (platform bağımsız, birim testli) mantık: çıktı ayrıştırıcıları, komut
 * üreticileri ve "geri alma" anlık görüntüsü.
 *
 * DÜRÜST SINIR: Dışarıdan bir oyunun kare hızı ARTIRILAMAZ. Yapılabilen, oyunun GPU yükünü
 * azaltmak (çözünürlük), ekranı sabit hıza kilitlemek ve üst sınır koymaktır. Oyun işlemci
 * (CPU) kaynaklı yavaşlıyorsa kazanç sınırlı olur; ısınma/pil tüketimi ise çözünürlük
 * düşüşüyle azalır.
 */

/**
 * Yapılandırma için yer tutucu paket adı (oyun seçilmediyse). Arayüz, kullanıcı bir oyun
 * eklemeden bu paketi göstermez ya da başlatmaz.
 */
const val DEFAULT_GAME_PACKAGE = "com.playdigious.ninekings"

private val pkgPattern = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
private val safeValue = Regex("^[A-Za-z0-9._-]+$")
private val sizePattern = Regex("^(\\d{3,5})x(\\d{3,5})$")

fun isSafePackage(value: String): Boolean = pkgPattern.matches(value)

/** Piksel boyutu (`1220x2712`). */
data class PixelSize(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"

    companion object {
        fun tryParse(value: String): PixelSize? {
            val m = sizePattern.find(value.trim()) ?: return null
            return PixelSize(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }
}

/** `wm size` çıktısı. */
data class WmSize(val physical: PixelSize, val override: PixelSize? = null)

/** `wm density` çıktısı. */
data class WmDensity(val physical: Int, val override: Int? = null)

private val wmSizeLine = Regex("^(Physical|Override) size:\\s*(\\d+x\\d+)", RegexOption.IGNORE_CASE)
private val wmDensityLine = Regex("^(Physical|Override) density:\\s*(\\d+)", RegexOption.IGNORE_CASE)

/**
 * ```
 * Physical size: 1220x2712
 * Override size: 813x1808
 * ```
 */
fun parseWmSize(output: String): WmSize? {
    var physical: PixelSize? = null
    var override: PixelSize? = null
    for (line in outputLines(output)) {
        val m = wmSizeLine.find(line) ?: continue
        val size = PixelSize.tryParse(m.groupValues[2])
        if (m.groupValues[1].lowercase() == "physical") physical = size else override = size
    }
    if (physical == null) return null
    return WmSize(physical = physical, override = override)
}

/**
 * ```
 * Physical density: 440
 * Override density: 293
 * ```
 */
fun parseWmDensity(output: String): WmDensity? {
    var physical: Int? = null
    var override: Int? = null
    for (line in outputLines(output)) {
        val m = wmDensityLine.find(line) ?: continue
        val v = m.groupValues[2].toInt()
        if (m.groupValues[1].lowercase() == "physical") physical = v else override = v
    }
    if (physical == null) return null
    return WmDensity(physical = physical, override = override)
}

/**
 * `dumpsys display` satırlarından etkin ekran yenileme hızını (Hz) çıkarır. Bulunamazsa `null`
 * (biçim üreticiye göre değişir; uydurma yapılmaz).
 */
fun parseActiveRefreshRate(output: String): Double? {
    val m = Regex("(?:fps|refreshRate)=([0-9]+(?:\\.[0-9]+)?)").find(output) ?: return null
    val v = m.groupValues[1].toDoubleOrNull()
    if (v == null || v < 20 || v > 500) return null
    return v
}

private fun even(v: Double): Int {
    val r = Math.round(v).toInt()
    return if (r % 2 == 0) r else r + 1
}

/** Fiziksel boyutu [scale] (0.4-1.0) ile küçültür; en-boy oranı korunur, iki kenar da çift sayıdır. */
fun scaledSize(physical: PixelSize, scale: Double): PixelSize {
    val s = scale.coerceIn(0.4, 1.0)
    return PixelSize(even(physical.width * s), even(physical.height * s))
}

/**
 * Yoğunluğu aynı oranda küçültür; böylece arayüz öğeleri (dp) aynı boyutta kalır, yalnızca
 * işlenen piksel sayısı azalır.
 */
fun scaledDensity(physicalDensity: Int, scale: Double): Int {
    val s = scale.coerceIn(0.4, 1.0)
    val v = Math.round(physicalDensity * s).toInt()
    return v.coerceIn(minOf(120, physicalDensity), physicalDensity)
}

/** Oyun-başına çözünürlük (GameManager) yöntemi mi, tüm ekran (`wm size`) mi? */
enum class ResolutionMethod(val json: String) {
    PerGame("perGame"),
    WholeScreen("wholeScreen"),
}

/**
 * Android GameManager güç profili. `Battery` en serin, `Performance` en sıcak profildir;
 * birçok cihazda `Performance` işlemci/GPU hızını yükseltir.
 */
enum class GamePower(val arg: String, val code: Int, val label: String, val hint: String) {
    Battery("battery", 3, "Serin (pil)", "Sistem oyunu enerji tasarruflu çalıştırır; ısınma en az."),
    Standard("standard", 1, "Standart", "Üreticinin varsayılan oyun davranışı."),
    Performance(
        "performance",
        2,
        "Performans",
        "İşlemci/GPU daha çok çalışır; daha akıcı ama belirgin daha sıcak.",
    ),
}

/** Pil sıcaklığı koruması için seçilebilir eşikler (°C). 0 = kapalı. */
val kGuardTempChoices: List<Int> = listOf(0, 38, 40, 42, 44)

/** Kullanıcının seçtiği oyun modu ayarları. */
data class GameModeConfig(
    val packageName: String = DEFAULT_GAME_PACKAGE,
    /** [kFpsChoices] içinden bir değer. */
    val targetFps: Int = 90,
    /** 1.0 = çözünürlüğe dokunma; 0.5-0.9 = düşür. */
    val scale: Double = 0.6,
    val method: ResolutionMethod = ResolutionMethod.PerGame,
    /** Ekran yenileme hızını [targetFps]'e kilitle (yalnızca 60/90'da). */
    val lockRefreshRate: Boolean = true,
    val killBackground: Boolean = true,
    /**
     * Oyun kapanınca cihazda çalışan bir izleyici her şeyi kendiliğinden geri alır ve oturum
     * boyunca FPS/ısı örnekleri kaydeder (uygulama kapalı olsa da).
     */
    val autoRevert: Boolean = true,
    /** Oyun boyunca sistem animasyonlarını kapatır (SurfaceFlinger yükü azalır). */
    val animationsOff: Boolean = false,
    /** Oyun boyunca bildirimleri sustur (Rahatsız Etme: yalnızca öncelikliler). */
    val dnd: Boolean = false,
    /** Oyun boyunca ekran parlaklığını mevcut değerin %60'ına indirir. */
    val dimScreen: Boolean = false,
    /** En güçlü iki işlemci kümesinin azami hızını %70'e sınırlar (YALNIZCA root ile). */
    val cpuCap: Boolean = false,
    /** GameManager güç profili (serin/standart/performans). */
    val powerProfile: GamePower = GamePower.Performance,
    /** Oyun boyunca sistemin Pil Tasarrufu modunu açar (eski değer geri yazılır). */
    val batterySaver: Boolean = false,
    /** Pil sıcaklığı bu değere (°C) ulaşınca izleyici ek önlem alır; 0 = kapalı. */
    val guardTempC: Int = 40,
    /** Oyun modu başlarken sırayla çalıştırılacak özel kabuk komutları (satır başına bir komut). */
    val customStart: String = "",
    /** "Geri Al" ile oturum kapanırken çalıştırılacak özel komutlar (satır başına bir komut). */
    val customEnd: String = "",
    /** Oyunun kullanacağı grafik sürücüsü arka ucu (bkz. GameEngine.kt). */
    val backend: GraphicsBackend = GraphicsBackend.Default,
    /** Oyun boyunca sabit performans modu (Android 12+; deneysel, ısıyı artırabilir). */
    val fixedPerf: Boolean = false,
    /** Ekranın kilitleneceği Hz; 0 = kare hızına göre otomatik. */
    val refreshHz: Int = 0,
    /** Parlaklık kısılınca mevcut değerin yüzdesi (20-90). */
    val dimPercent: Int = 60,
    /** İşlemci hız sınırı: donanım azami hızının yüzdesi (40-95). */
    val cpuCapPercent: Int = 70,
) {
    val reducesResolution: Boolean get() = scale < 0.999

    /** Ekran kilidi Hz değeri; elle seçilmediyse kare hızının tam katı olan panel hızı (30 -> 60). */
    val lockHz: Int?
        get() = when {
            !lockRefreshRate -> null
            refreshHz > 0 -> refreshHz
            else -> autoLockHz(targetFps)
        }

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "package" to packageName,
        "targetFps" to targetFps,
        "scale" to scale,
        "method" to method.json,
        "lockRefreshRate" to lockRefreshRate,
        "killBackground" to killBackground,
        "autoRevert" to autoRevert,
        "animationsOff" to animationsOff,
        "dnd" to dnd,
        "dimScreen" to dimScreen,
        "cpuCap" to cpuCap,
        "powerProfile" to powerProfile.arg,
        "batterySaver" to batterySaver,
        "guardTempC" to guardTempC,
        "customStart" to customStart,
        "customEnd" to customEnd,
        "backend" to backend.json,
        "fixedPerf" to fixedPerf,
        "refreshHz" to refreshHz,
        "dimPercent" to dimPercent,
        "cpuCapPercent" to cpuCapPercent,
    )

    fun encode(): String = Json.stringify(toJson())

    companion object {
        fun fromJson(json: Map<String, Any?>): GameModeConfig {
            val pkg = json.str("package") ?: DEFAULT_GAME_PACKAGE
            val fps = json.int("targetFps") ?: 90
            val scale = json.dbl("scale") ?: 0.6
            val method = if (json["method"] == ResolutionMethod.WholeScreen.json) {
                ResolutionMethod.WholeScreen
            } else {
                ResolutionMethod.PerGame
            }
            val guard = json.int("guardTempC")
            return GameModeConfig(
                packageName = if (isSafePackage(pkg)) pkg else DEFAULT_GAME_PACKAGE,
                targetFps = if (fps in kFpsChoices) fps else 90,
                scale = scale.coerceIn(0.4, 1.0),
                method = method,
                lockRefreshRate = json.bool("lockRefreshRate") ?: true,
                killBackground = json.bool("killBackground") ?: true,
                autoRevert = json.bool("autoRevert") ?: true,
                animationsOff = json.bool("animationsOff") ?: false,
                dnd = json.bool("dnd") ?: false,
                dimScreen = json.bool("dimScreen") ?: false,
                cpuCap = json.bool("cpuCap") ?: false,
                powerProfile = GamePower.values().firstOrNull { it.arg == json["powerProfile"] }
                    ?: GamePower.Performance,
                batterySaver = json.bool("batterySaver") ?: false,
                guardTempC = if (guard != null && guard in kGuardTempChoices) guard else 40,
                customStart = json.str("customStart") ?: "",
                customEnd = json.str("customEnd") ?: "",
                backend = GraphicsBackend.fromJson(json["backend"]),
                fixedPerf = json.bool("fixedPerf") ?: false,
                refreshHz = json.int("refreshHz")?.takeIf { it in kHzChoices } ?: 0,
                dimPercent = (json.int("dimPercent") ?: 60).coerceIn(20, 90),
                cpuCapPercent = (json.int("cpuCapPercent") ?: 70).coerceIn(40, 95),
            )
        }

        fun decode(raw: String): GameModeConfig {
            if (raw.isEmpty()) return GameModeConfig()
            return try {
                fromJson(Json.parse(raw).asMap() ?: return GameModeConfig())
            } catch (_: Exception) {
                GameModeConfig()
            }
        }
    }
}

/** Hazır ayar kümesi (tek dokunuşla seçilir). */
data class GamePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val targetFps: Int,
    val scale: Double,
    val animationsOff: Boolean,
    val dnd: Boolean,
    val dimScreen: Boolean = false,
    val cpuCap: Boolean = false,
    val powerProfile: GamePower = GamePower.Performance,
    val batterySaver: Boolean = false,
    val guardTempC: Int = 40,
    /** Arayüzün seçtiği simge sırası (saf mantık katmanı arayüze bağlı değil). */
    val icon: Int = 0,
) {
    /** Paket, yöntem ve arka plan seçimini koruyup yalnızca ön ayarın değerlerini uygular. */
    fun applyTo(base: GameModeConfig): GameModeConfig = base.copy(
        targetFps = targetFps,
        scale = scale,
        lockRefreshRate = true,
        animationsOff = animationsOff,
        dnd = dnd,
        dimScreen = dimScreen,
        cpuCap = cpuCap,
        powerProfile = powerProfile,
        batterySaver = batterySaver,
        guardTempC = guardTempC,
    )

    /** [config] bu ön ayarla birebir aynı mı? */
    fun matches(config: GameModeConfig): Boolean =
        config.targetFps == targetFps &&
            abs(config.scale - scale) < 0.001 &&
            config.animationsOff == animationsOff &&
            config.dnd == dnd &&
            config.dimScreen == dimScreen &&
            config.cpuCap == cpuCap &&
            config.powerProfile == powerProfile &&
            config.batterySaver == batterySaver &&
            config.guardTempC == guardTempC
}

/** Özel komut metninin en çok kaç satırı ve satır başına kaç karakteri çalıştırılır. */
const val kCustomMaxLines = 8
const val kCustomMaxChars = 300

/**
 * Özel komut metnini çalıştırılacak satırlara böler: boş satırlar ve `#` ile başlayan yorum
 * satırları atılır; satır sayısı ve uzunluğu sınırlıdır.
 */
fun customLines(text: String): List<String> =
    text.replace("\r", "").split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.take(kCustomMaxChars) }
        .take(kCustomMaxLines)

/** Her oyunda kullanılabilen hazır ayarlar (en seriniden en akıcıya). */
val kGamePresets: List<GamePreset> = listOf(
    GamePreset(
        id = "ice",
        title = "Buz",
        subtitle = "En serin: 30 FPS · %40 çözünürlük · serin güç profili · Pil Tasarrufu · " +
            "parlaklık kısık · animasyonlar kapalı · bildirimler sessiz · ısı koruması 38 °C.",
        targetFps = 30,
        scale = 0.4,
        animationsOff = true,
        dnd = true,
        dimScreen = true,
        cpuCap = true,
        powerProfile = GamePower.Battery,
        batterySaver = true,
        guardTempC = 38,
        icon = 0,
    ),
    GamePreset(
        id = "cool",
        title = "Serin",
        subtitle = "30 FPS · %50 çözünürlük · serin güç profili · parlaklık kısık · animasyonlar kapalı · " +
            "bildirimler sessiz · ısı koruması 40 °C. Root varsa işlemci hızı da sınırlanır.",
        targetFps = 30,
        scale = 0.5,
        animationsOff = true,
        dnd = true,
        dimScreen = true,
        cpuCap = true,
        powerProfile = GamePower.Battery,
        batterySaver = false,
        guardTempC = 40,
        icon = 1,
    ),
    GamePreset(
        id = "balanced",
        title = "Dengeli",
        subtitle = "60 FPS · %60 çözünürlük · standart güç profili · animasyonlar kapalı · " +
            "ısı koruması 42 °C. Daha akıcı, daha çok ısınır.",
        targetFps = 60,
        scale = 0.6,
        animationsOff = true,
        dnd = false,
        powerProfile = GamePower.Standard,
        guardTempC = 42,
        icon = 2,
    ),
    GamePreset(
        id = "smooth",
        title = "Akıcı",
        subtitle = "60 FPS · %75 çözünürlük · performans profili · bildirimler sessiz · " +
            "ısı koruması 44 °C. Telefon belirgin ısınır.",
        targetFps = 60,
        scale = 0.75,
        animationsOff = true,
        dnd = true,
        powerProfile = GamePower.Performance,
        guardTempC = 44,
        icon = 3,
    ),
)

/** Değiştirilen bir sistem ayarı (geri almak için önceki değeri saklanır). */
data class SettingKey(
    /** system | secure | global */
    val namespace: String,
    val key: String,
) {
    val id: String get() = "$namespace:$key"
    val readCommand: String get() = "settings get $namespace $key"
}

/** Animasyon ölçeğinin Android varsayılanı. */
const val kAnimationDefault = "1.0"

/**
 * Animasyon anlık görüntüsü için güvenli değer. Ayar yoksa ya da 0 ise (önceki bir oturumun
 * bıraktığı kalıntı olabilir) varsayılan 1.0 kullanılır; böylece geri alma animasyonları
 * kapalı bırakmaz.
 */
fun healAnimationValue(old: String?): String {
    val v = old?.trim()
    if (v.isNullOrEmpty()) return kAnimationDefault
    val d = v.toDoubleOrNull() ?: return kAnimationDefault
    return if (d <= 0.0) kAnimationDefault else v
}

/** Animasyon ölçekleri (oyun boyunca 0'a çekilir). */
val kAnimationKeys: List<SettingKey> = listOf(
    SettingKey("global", "window_animation_scale"),
    SettingKey("global", "transition_animation_scale"),
    SettingKey("global", "animator_duration_scale"),
)

/** Rahatsız Etme modu (0 = kapalı, 1 = yalnızca öncelikli). */
val kZenKey = SettingKey("global", "zen_mode")

/** Ekran parlaklığı (önce değer, sonra mod geri yazılır). */
val kBrightnessKeys: List<SettingKey> = listOf(
    SettingKey("system", "screen_brightness"),
    SettingKey("system", "screen_brightness_mode"),
)

/** Sistem Pil Tasarrufu (0 = kapalı, 1 = açık). */
val kLowPowerKey = SettingKey("global", "low_power")

/** Ekranı sabit hıza kilitlemek için yazılan ayarlar (AOSP + Xiaomi). */
val kRefreshKeys: List<SettingKey> = listOf(
    SettingKey("system", "peak_refresh_rate"),
    SettingKey("system", "min_refresh_rate"),
    SettingKey("system", "user_refresh_rate"),
    SettingKey("secure", "miui_refresh_rate"),
)

/** Parlaklık düşürme oranı (mevcut değerin yüzdesi) ve ısı korumasındaki oran. */
const val kDimFraction = 0.6
const val kGuardDimFraction = 0.35

/** İşlemci hız sınırı oranları (normal / ısı koruması). */
const val kCpuCapFraction = 0.7
const val kGuardCpuFraction = 0.55

private val policyName = Regex("^policy\\d{1,2}$")
private val digits = Regex("^\\d{4,8}$")

/** Bir işlemci frekans kümesi (`policyN`): şimdiki azami ve donanım azami (kHz). */
data class CpuPolicy(val name: String, val curMax: Int, val hwMax: Int)

/** Okuma komutu: her küme için `policyN şimdikiAzami donanımAzami`. */
const val kCpuPolicyReadCommand =
    "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
        "echo \"\$(basename \$p) \$(cat \$p/scaling_max_freq) \$(cat \$p/cpuinfo_max_freq)\"; done"

fun parseCpuPolicies(output: String): List<CpuPolicy> {
    val result = ArrayList<CpuPolicy>()
    for (line in outputLines(output)) {
        val p = line.split(Regex("\\s+"))
        if (p.size != 3 || !policyName.matches(p[0])) continue
        val cur = p[1].toIntOrNull()
        val hw = p[2].toIntOrNull()
        if (cur == null || hw == null || cur <= 0 || hw <= 0) continue
        result.add(CpuPolicy(p[0], cur, hw))
    }
    return result
}

/** Donanım hızı en yüksek [count] küme (oyunu yükleyen büyük çekirdekler). */
fun topPolicies(all: List<CpuPolicy>, count: Int = 2): List<CpuPolicy> =
    all.sortedByDescending { it.hwMax }.take(count)

private fun cpuWrite(name: String, khz: Int): String =
    "echo $khz > /sys/devices/system/cpu/cpufreq/$name/scaling_max_freq"

/** Kümelerin azami hızını [fraction] oranına çeker. */
fun cpuCapCommand(policies: List<CpuPolicy>, fraction: Double): String =
    policies
        .filter { policyName.matches(it.name) }
        .joinToString("; ") { cpuWrite(it.name, Math.round(it.hwMax * fraction).toInt()) }

/** Çalıştırılacak tek bir adım. */
data class GameStep(
    val label: String,
    val command: String,
    /** `true` ise başarısızlık oyun modunu bozmaz (örn. üretici desteklemiyor). */
    val optional: Boolean = false,
)

/** `settings get` çıktısı "null" ise ayar daha önce yoktu. */
fun normalizeSettingValue(output: String): String? {
    val v = output.trim()
    if (v.isEmpty() || v == "null") return null
    return if (safeValue.matches(v)) v else null
}

/**
 * Oyun modu açılmadan ÖNCEKİ cihaz durumu. Diske yazılır; uygulama kapansa bile "Geri Al"
 * doğru değerlere döner.
 */
data class GameSession(
    val packageName: String,
    /** `namespace:key` -> önceki değer (`null` = ayar yoktu). */
    val settings: Map<String, String?>,
    val touchedGameManager: Boolean,
    /** Önceki `wm size` override'ı (`null` = yoktu). */
    val sizeOverride: String? = null,
    val densityOverride: String? = null,
    /** Bu oturum `wm size/density` değiştirdi mi? */
    val changedScreen: Boolean = false,
    /** `policyN` -> önceki `scaling_max_freq` (kHz); yalnızca işlemci sınırı uygulandıysa dolu. */
    val cpuMax: Map<String, String> = emptyMap(),
    val startedAtMs: Long,
    /** Bu oturum sabit performans modunu açtı mı? */
    val fixedPerf: Boolean = false,
) {
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "package" to packageName,
        "settings" to settings,
        "touchedGameManager" to touchedGameManager,
        "sizeOverride" to sizeOverride,
        "densityOverride" to densityOverride,
        "changedScreen" to changedScreen,
        "cpuMax" to cpuMax,
        "startedAtMs" to startedAtMs,
        "fixedPerf" to fixedPerf,
    )

    fun encode(): String = Json.stringify(toJson())

    companion object {
        fun fromJson(json: Map<String, Any?>): GameSession {
            val raw = json["settings"].asMap() ?: emptyMap()
            val cpuRaw = json["cpuMax"].asMap() ?: emptyMap()
            val cpu = LinkedHashMap<String, String>()
            for ((k, v) in cpuRaw) {
                val vs = v?.let { if (it is Double && it == Math.floor(it)) it.toLong().toString() else it.toString() }
                if (vs != null && policyName.matches(k) && digits.matches(vs)) cpu[k] = vs
            }
            return GameSession(
                packageName = json.str("package") ?: error("paket yok"),
                settings = raw.mapValues { it.value as? String },
                touchedGameManager = json.bool("touchedGameManager") ?: false,
                sizeOverride = json.str("sizeOverride"),
                densityOverride = json.str("densityOverride"),
                changedScreen = json.bool("changedScreen") ?: false,
                cpuMax = cpu,
                startedAtMs = (json["startedAtMs"] as? Number)?.toLong() ?: 0L,
                fixedPerf = json.bool("fixedPerf") ?: false,
            )
        }

        fun decode(raw: String): GameSession? {
            if (raw.isEmpty()) return null
            return try {
                val s = fromJson(Json.parse(raw).asMap() ?: return null)
                if (isSafePackage(s.packageName)) s else null
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** GameManager `--downscale` için izin verilen değerlere yuvarlar. */
fun downscaleArg(scale: Double): String {
    if (scale >= 0.999) return "disable"
    val stepped = Math.round(scale.coerceIn(0.3, 0.9) * 20) / 20.0 // 0.05 adım
    val text = fixed(stepped, 2)
    return if (text.endsWith("0")) text.substring(0, text.length - 1) else text
}

/**
 * Oyun modunu AÇAN adımlar. [physicalSize]/[physicalDensity] yalnızca
 * [ResolutionMethod.WholeScreen] için, [angle] (mevcut ANGLE seçimi) yalnızca grafik motoru
 * seçildiyse gerekir. [restartGame] ise sürücü değişiminin geçerli olması için çalışan oyunu
 * kapatır (oyun hemen ardından başlatılacaksa).
 */
fun buildApplySteps(
    config: GameModeConfig,
    physicalSize: PixelSize? = null,
    physicalDensity: Int? = null,
    brightness: Int? = null,
    cpuPolicies: List<CpuPolicy>? = null,
    angle: AngleSelection? = null,
    restartGame: Boolean = false,
): List<GameStep> {
    val pkg = config.packageName
    if (!isSafePackage(pkg)) return emptyList()
    val steps = ArrayList<GameStep>()

    if (config.killBackground) {
        steps.add(GameStep("Arka plan temizliği", "am kill-all", optional = true))
    }

    // Oyun-başına (Android GameManager) üst sınır + çözünürlük.
    val perGameScale = config.method == ResolutionMethod.PerGame && config.reducesResolution
    val mode = config.powerProfile.arg
    steps.add(
        GameStep(
            "Oyun profili (${config.targetFps} FPS sınırı" +
                (if (perGameScale) ", %${Math.round(config.scale * 100)} çözünürlük" else "") + ")",
            "cmd game set --mode ${config.powerProfile.code} --fps ${config.targetFps}" +
                (if (perGameScale) " --downscale ${downscaleArg(config.scale)}" else "") + " $pkg",
            optional = true,
        ),
    )
    steps.add(
        GameStep(
            "Güç profili: ${config.powerProfile.label}",
            "cmd game mode $mode $pkg",
            optional = true,
        ),
    )
    buildBackendStep(config, angle)?.let { steps.add(it) }
    if (config.fixedPerf) {
        steps.add(GameStep("Sabit performans modu", kFixedPerfOn, optional = true))
    }

    val hz = config.lockHz
    if (hz != null) {
        val v = "$hz.0"
        steps.add(
            GameStep(
                "Ekranı $hz Hz'e kilitle",
                "settings put system peak_refresh_rate $v; " +
                    "settings put system min_refresh_rate $v; " +
                    "settings put system user_refresh_rate $hz; " +
                    "settings put secure miui_refresh_rate $hz",
            ),
        )
    }

    if (config.animationsOff) {
        steps.add(
            GameStep(
                "Sistem animasyonlarını kapat",
                kAnimationKeys.joinToString("; ") { "settings put ${it.namespace} ${it.key} 0" },
                optional = true,
            ),
        )
    }
    if (config.dimScreen && brightness != null && brightness > 1) {
        val dimmed = Math.round(brightness * (config.dimPercent / 100.0)).toInt().coerceIn(1, brightness)
        steps.add(
            GameStep(
                "Ekran parlaklığını kıs",
                "settings put system screen_brightness_mode 0; settings put system screen_brightness $dimmed",
                optional = true,
            ),
        )
    }
    if (config.cpuCap && !cpuPolicies.isNullOrEmpty()) {
        val cmd = cpuCapCommand(topPolicies(cpuPolicies), config.cpuCapPercent / 100.0)
        if (cmd.isNotEmpty()) {
            steps.add(GameStep("İşlemci hızını sınırla (%${config.cpuCapPercent})", cmd, optional = true))
        }
    }
    if (config.batterySaver) {
        steps.add(
            GameStep(
                "Pil Tasarrufu'nu aç",
                "settings put ${kLowPowerKey.namespace} ${kLowPowerKey.key} 1",
                optional = true,
            ),
        )
    }
    if (config.dnd) {
        steps.add(
            GameStep(
                "Bildirimleri sustur (Rahatsız Etme)",
                "settings put ${kZenKey.namespace} ${kZenKey.key} 1",
                optional = true,
            ),
        )
    }

    if (config.method == ResolutionMethod.WholeScreen &&
        config.reducesResolution &&
        physicalSize != null &&
        physicalDensity != null
    ) {
        val size = scaledSize(physicalSize, config.scale)
        val density = scaledDensity(physicalDensity, config.scale)
        steps.add(
            GameStep(
                "Ekran çözünürlüğü $size",
                "wm size $size; wm density $density",
            ),
        )
    }
    if (restartGame && config.backend.settingValue != null && angle != null) {
        steps.add(
            GameStep(
                "Oyunu kapat (grafik motoru yeniden yüklensin)",
                "am force-stop $pkg",
                optional = true,
            ),
        )
    }
    return steps
}

/**
 * [keys] içinden oturumda kayıtlı olanların eski değerini yazan komutlar (eski değer yoksa
 * `settings delete`). Güvensiz değerler komuta girmez.
 */
private fun restoreCommands(session: GameSession, keys: List<SettingKey>): List<String> {
    val cmds = ArrayList<String>()
    for (key in keys) {
        if (!session.settings.containsKey(key.id)) continue
        val old = session.settings[key.id]
        if (old != null && safeValue.matches(old)) {
            cmds.add("settings put ${key.namespace} ${key.key} $old")
        } else {
            cmds.add("settings delete ${key.namespace} ${key.key}")
        }
    }
    return cmds
}

/** Oturum anlık görüntüsünden, her şeyi ESKİ HALİNE getiren adımlar. */
fun buildRevertSteps(session: GameSession): List<GameStep> {
    val steps = ArrayList<GameStep>()

    if (session.changedScreen) {
        val size = session.sizeOverride
        val density = session.densityOverride
        val sizeCmd = if (size != null && PixelSize.tryParse(size) != null) "wm size $size" else "wm size reset"
        val densityCmd = if (density != null && Regex("^\\d{2,4}$").matches(density)) {
            "wm density $density"
        } else {
            "wm density reset"
        }
        steps.add(GameStep("Ekran çözünürlüğünü geri al", "$sizeCmd; $densityCmd"))
    }

    val refresh = restoreCommands(session, kRefreshKeys)
    if (refresh.isNotEmpty()) {
        steps.add(GameStep("Ekran yenileme ayarlarını geri al", refresh.joinToString("; ")))
    }
    val anim = kAnimationKeys
        .filter { session.settings.containsKey(it.id) }
        .map { "settings put ${it.namespace} ${it.key} ${healAnimationValue(session.settings[it.id])}" }
    if (anim.isNotEmpty()) {
        steps.add(GameStep("Animasyon ayarlarını geri al", anim.joinToString("; ")))
    }
    val bright = restoreCommands(session, kBrightnessKeys)
    if (bright.isNotEmpty()) {
        steps.add(GameStep("Ekran parlaklığını geri al", bright.joinToString("; ")))
    }
    val cpu = session.cpuMax.entries
        .filter { policyName.matches(it.key) && digits.matches(it.value) }
        .map { cpuWrite(it.key, it.value.toInt()) }
    if (cpu.isNotEmpty()) {
        steps.add(GameStep("İşlemci sınırını geri al", cpu.joinToString("; "), optional = true))
    }
    val lowPower = restoreCommands(session, listOf(kLowPowerKey))
    if (lowPower.isNotEmpty()) {
        steps.add(GameStep("Pil Tasarrufu ayarını geri al", lowPower.joinToString("; "), optional = true))
    }
    val angleRestore = restoreListCommands(session, kAngleKeys)
    if (angleRestore.isNotEmpty()) {
        steps.add(GameStep("Grafik motoru ayarını geri al", angleRestore.joinToString("; ")))
    }
    if (session.fixedPerf) {
        steps.add(GameStep("Sabit performans modunu kapat", kFixedPerfOff, optional = true))
    }
    val zen = restoreCommands(session, listOf(kZenKey))
    if (zen.isNotEmpty()) {
        steps.add(GameStep("Rahatsız Etme ayarını geri al", zen.joinToString("; ")))
    }

    if (session.touchedGameManager && isSafePackage(session.packageName)) {
        steps.add(
            GameStep(
                "Oyun profilini sıfırla",
                "cmd game reset ${session.packageName}; cmd game mode standard ${session.packageName}",
                optional = true,
            ),
        )
    }
    return steps
}

/**
 * Isı koruması: oyun sırasında ısıl durum ya da pil sıcaklığı yükselirse izleyicinin uyguladığı
 * ek önlemler (Pil Tasarrufu, işlemci sınırı %55'e, parlaklık özgün değerin %35'ine). Geri alma,
 * özgün değerleri zaten yazdığı için ayrıca geri alınması gerekmez.
 */
fun buildGuardCommands(session: GameSession): List<String> {
    val cmds = ArrayList<String>()
    if (session.cpuMax.isNotEmpty()) {
        val policies = session.cpuMax.entries
            .filter { policyName.matches(it.key) && digits.matches(it.value) }
            .map { CpuPolicy(it.key, it.value.toInt(), it.value.toInt()) }
        val cmd = cpuCapCommand(policies, kGuardCpuFraction)
        if (cmd.isNotEmpty()) cmds.add(cmd)
    }
    if (session.settings.containsKey(kLowPowerKey.id)) {
        cmds.add("settings put ${kLowPowerKey.namespace} ${kLowPowerKey.key} 1")
    }
    if (session.fixedPerf) cmds.add(kFixedPerfOff)
    val key = "system:screen_brightness"
    val orig = session.settings[key]?.toIntOrNull()
    if (session.settings.containsKey(key) && orig != null && orig > 1) {
        val v = Math.round(orig * kGuardDimFraction).toInt().coerceIn(1, orig)
        cmds.add("settings put system screen_brightness_mode 0; settings put system screen_brightness $v")
    }
    return cmds
}

/** Oyunun çözünürlük kademeleri (net -> bulanık). */
val kScaleSteps: List<Double> = listOf(1.0, 0.75, 0.6, 0.5, 0.4)

/** Bir sonraki oturum için ayar önerisi. */
data class ScaleSuggestion(
    val scale: Double,
    val reason: String,
    /** Önerilen kare hızı sınırı (yalnızca değişecekse dolu). */
    val fps: Int? = null,
)

/** En yakın kademenin sırası. */
fun scaleIndex(scale: Double): Int {
    var best = 0
    var diff = Double.POSITIVE_INFINITY
    for (i in kScaleSteps.indices) {
        val d = abs(kScaleSteps[i] - scale)
        if (d < diff) {
            diff = d
            best = i
        }
    }
    return best
}


/**
 * Acil sıfırlama: oturum kaydı olmasa bile oyun modunun değiştirebildiği ayarları güvenli
 * varsayılanlara döndürür (animasyonlar 1.0, ekran Hz kilidi kalkar, oyun profili sıfırlanır).
 * Ekran çözünürlüğü ve işlemci sınırı da sıfırlanır. Rahatsız Etme, parlaklık ve Pil Tasarrufu
 * kullanıcının kendi tercihi olabileceğinden yalnızca oturum kaydı varsa eski değerine döner.
 */
fun buildSafeResetSteps(packageName: String?): List<GameStep> {
    val steps = ArrayList<GameStep>()
    steps.add(
        GameStep(
            "Animasyonları varsayılana döndür",
            kAnimationKeys.joinToString("; ") { "settings put ${it.namespace} ${it.key} $kAnimationDefault" },
            optional = true,
        ),
    )
    steps.add(
        GameStep(
            "Ekran yenileme kilidini kaldır",
            kRefreshKeys.joinToString("; ") { "settings delete ${it.namespace} ${it.key}" },
            optional = true,
        ),
    )
    steps.add(GameStep("Sabit performans modunu kapat", kFixedPerfOff, optional = true))
    steps.add(GameStep("Ekran çözünürlüğünü sıfırla", "wm size reset; wm density reset", optional = true))
    steps.add(
        GameStep(
            "İşlemci hız sınırını kaldır (root)",
            "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
                "cat \$p/cpuinfo_max_freq > \$p/scaling_max_freq; done",
            optional = true,
        ),
    )
    if (packageName != null && isSafePackage(packageName)) {
        steps.add(
            GameStep(
                "Oyun profilini sıfırla",
                "cmd game reset $packageName; cmd game mode standard $packageName",
                optional = true,
            ),
        )
    }
    return steps
}
