package com.terminalf.app.core

/**
 * Oyun Modu "grafik motoru" mantığı (saf, birim testli).
 *
 * DÜRÜST SINIR: Kapalı kaynaklı bir oyunun kendi motoru (ör. Minecraft'ın RenderDragon'ı) başka
 * bir motorla DEĞİŞTİRİLEMEZ. Sistem düzeyinde yapılabilen, oyunun kullandığı GRAFİK SÜRÜCÜSÜ
 * arka ucunu seçmektir: cihazın yerel OpenGL ES sürücüsü ya da ANGLE (OpenGL ES çağrılarını
 * Vulkan üzerinden çalıştıran katman). Sonuç cihaza ve sürücüye göre değişir: bazı GPU'larda
 * kare hızı artar, bazılarında düşer; bu yüzden uygulama sonucu ölçer (Son oyun oturumu) ve
 * karşılaştırmanızı ister.
 */

/** Oyunun kullanacağı grafik sürücüsü arka ucu. */
enum class GraphicsBackend(
    val json: String,
    val label: String,
    /** `angle_gl_driver_selection_values` içine yazılan değer; `null` = hiçbir şey yazılmaz. */
    val settingValue: String?,
    val hint: String,
) {
    Default(
        "default",
        "Sistem varsayılanı",
        null,
        "Grafik sürücüsüne dokunulmaz; oyun cihazın kendi seçtiği yolu kullanır.",
    ),
    Native(
        "native",
        "Yerel GLES",
        "native",
        "Oyunu cihazın yerel OpenGL ES sürücüsüne zorlar. Sistem tüm uygulamaları ANGLE'a " +
            "yönlendiriyorsa ve bu oyunda takılma varsa işe yarar.",
    ),
    Angle(
        "angle",
        "ANGLE (Vulkan)",
        "angle",
        "OpenGL ES çağrılarını Vulkan üzerinden çalıştırır. Bazı GPU'larda sürücü yükünü ve " +
            "takılmayı azaltıp FPS'i artırır, bazılarında düşürür; Son oyun oturumundan ölçerek " +
            "karşılaştırın. Cihaz ANGLE desteği bildirmiyorsa etkisiz kalır.",
    ),
    ;

    companion object {
        fun fromJson(value: Any?): GraphicsBackend =
            values().firstOrNull { it.json == value } ?: Default
    }
}

/** Seçilebilir değerler (arayüz çipleri). Geçersiz kayıtlar bu kümeye göre düzeltilir. */
val kFpsChoices: List<Int> = listOf(30, 45, 60, 72, 90, 120, 144)

/** Ekran yenileme hızı seçenekleri; 0 = otomatik (kare hızına göre). */
val kHzChoices: List<Int> = listOf(0, 60, 90, 120, 144)

/** Arayüzde gösterilen ince çözünürlük kademeleri (öneri mantığı kScaleSteps'i kullanır). */
val kScaleChoices: List<Double> = listOf(1.0, 0.9, 0.8, 0.75, 0.7, 0.6, 0.5, 0.4)

/** Parlaklık kısma yüzdeleri (mevcut değerin yüzdesi). */
val kDimChoices: List<Int> = listOf(30, 40, 50, 60, 70, 80)

/** İşlemci hız sınırı yüzdeleri (donanım azami hızının yüzdesi). */
val kCpuCapChoices: List<Int> = listOf(50, 60, 70, 80, 90)

private val kPanelHz = listOf(60, 90, 120, 144)

/**
 * Otomatik ekran kilidi: kare hızının tam katı olan en küçük panel hızı (30 -> 60, 45 -> 90,
 * 72 -> 144). Bulunamazsa kare hızının kendisi (en az 60).
 */
fun autoLockHz(fps: Int): Int {
    if (fps <= 0) return 60
    return kPanelHz.firstOrNull { it % fps == 0 } ?: maxOf(fps, 60)
}

private val listSafe = Regex("^[A-Za-z0-9._,-]+$")

/** ANGLE seçiminin tutulduğu iki sistem ayarı (paket listesi ve aynı sıradaki değer listesi). */
val kAngleKeys: List<SettingKey> = listOf(
    SettingKey("global", "angle_gl_driver_selection_pkgs"),
    SettingKey("global", "angle_gl_driver_selection_values"),
)

/** Virgüllü liste ayarının okunmuş hali. */
data class ListSetting(
    /** `null` = ayar yok (ya da okunamadı; bkz. [safe]). */
    val value: String?,
    /** `false` = çıktı boş/bozuk/güvensiz; bu durumda ayara DOKUNULMAZ. */
    val safe: Boolean,
)

/**
 * `settings get` çıktısı. "null" = ayar yok. BOŞ çıktı okuma hatasıdır (yetki yolu yanıt
 * vermedi); onu "yok" saymak, geri alırken başka uygulamaların ayarını silebilirdi.
 */
fun parseListSetting(output: String): ListSetting {
    val v = output.trim()
    if (v.isEmpty()) return ListSetting(null, safe = false)
    if (v == "null") return ListSetting(null, safe = true)
    return if (listSafe.matches(v)) ListSetting(v, safe = true) else ListSetting(null, safe = false)
}

/** Sistemde şu an kayıtlı ANGLE seçimi. */
data class AngleSelection(val pkgs: String?, val values: String?)

/**
 * [pkg] için [value]'yu mevcut seçime ekler/günceller; başka uygulamaların girdileri korunur.
 * İki liste aynı uzunlukta tutulur (eksik değerler `default` ile tamamlanır).
 */
fun mergeAngleSelection(current: AngleSelection, pkg: String, value: String): AngleSelection {
    val pk = (current.pkgs ?: "").split(',').filter { it.isNotEmpty() }.toMutableList()
    val vs = (current.values ?: "").split(',').filter { it.isNotEmpty() }.toMutableList()
    while (vs.size < pk.size) vs.add("default")
    while (vs.size > pk.size) vs.removeAt(vs.size - 1)
    val i = pk.indexOf(pkg)
    if (i >= 0) {
        vs[i] = value
    } else {
        pk.add(pkg)
        vs.add(value)
    }
    return AngleSelection(pk.joinToString(","), vs.joinToString(","))
}

/**
 * Seçili grafik motorunu yazan adım. Sistem varsayılanı seçiliyse, paket adı geçersizse ya da
 * mevcut ayar okunamadıysa ([current] `null`) adım ÜRETİLMEZ (başkasının ayarı bozulmaz).
 */
fun buildBackendStep(config: GameModeConfig, current: AngleSelection?): GameStep? {
    val value = config.backend.settingValue ?: return null
    if (current == null || !isSafePackage(config.packageName)) return null
    val m = mergeAngleSelection(current, config.packageName, value)
    return GameStep(
        "Grafik motoru: ${config.backend.label}",
        "settings put ${kAngleKeys[0].namespace} ${kAngleKeys[0].key} ${m.pkgs}; " +
            "settings put ${kAngleKeys[1].namespace} ${kAngleKeys[1].key} ${m.values}",
        optional = true,
    )
}

/**
 * [keys] içinden oturumda kayıtlı olanların eski (virgüllü) değerini geri yazar; eski değer
 * yoksa `settings delete`. Güvensiz değer komuta girmez.
 */
fun restoreListCommands(session: GameSession, keys: List<SettingKey>): List<String> {
    val cmds = ArrayList<String>()
    for (key in keys) {
        if (!session.settings.containsKey(key.id)) continue
        val old = session.settings[key.id]
        if (old != null && listSafe.matches(old)) {
            cmds.add("settings put ${key.namespace} ${key.key} $old")
        } else {
            cmds.add("settings delete ${key.namespace} ${key.key}")
        }
    }
    return cmds
}

/** Sabit performans modu (Android 12+): işlemci/GPU hızını sabit tutar; ısıyı artırabilir. */
const val kFixedPerfOn = "cmd power set-fixed-performance-mode-enabled true"
const val kFixedPerfOff = "cmd power set-fixed-performance-mode-enabled false"

/** Oyun türü: yalnızca ipucu ve önerilen ayar seçmek için kullanılır. */
enum class GameKind(val title: String) {
    Generic("Genel"),
    MinecraftBedrock("Minecraft (Bedrock)"),
    MinecraftJava("Minecraft (Java başlatıcı)"),
}

private val javaLaunchers = setOf(
    "net.kdt.pojavlaunch",
    "net.kdt.pojavlaunch.debug",
    "com.tungsten.fcl",
    "com.movtery.zalithlauncher",
)

/** Paket adından oyun türü. Kullanıcı paketi/adı değiştirebilir; bu yalnızca bir öneridir. */
fun detectGameKind(packageName: String): GameKind = when {
    packageName.startsWith("com.mojang.minecraft") -> GameKind.MinecraftBedrock
    packageName in javaLaunchers -> GameKind.MinecraftJava
    else -> GameKind.Generic
}

/** Türe özel, oyun İÇİ ayar ipuçları (bu uygulama oyunun kendi ayarlarını değiştiremez). */
fun gameTips(kind: GameKind): List<String> = when (kind) {
    GameKind.MinecraftBedrock -> listOf(
        "En büyük FPS kazancı oyun içinden gelir: Ayarlar > Video'da görüş mesafesini 6-10'a, " +
            "simülasyon mesafesini 4-6'ya indirin.",
        "Güzel grafikler, güzel gökyüzü, yumuşak aydınlatma ve parçacıkları kapatmak GPU yükünü " +
            "belirgin azaltır.",
        "Oyun Modu çözünürlüğü %75 civarında blok dokularda genelde fark edilmez; arayüz biraz " +
            "yumuşar.",
        "Bedrock Android'de oyunun kendisi 60 FPS'e sınırlı; 90 hedeflemek FPS'i artırmaz, yalnızca " +
            "ısıtır. Akıcılık için kare süresini düzgün tutmaya bakın.",
        "ANGLE (Vulkan) motorunu isterseniz deneyip Son oyun oturumundaki ortalama FPS ve ısıyı " +
            "varsayılanla karşılaştırın; hangisi daha iyiyse onu bırakın.",
    )
    GameKind.MinecraftJava -> listOf(
        "Java sürümü başlatıcının kendi renderer'ını kullanır; o seçim başlatıcının ayarlarında " +
            "yapılır ve bu uygulama onu değiştiremez.",
        "Görüş mesafesini düşürmek, başlatıcıda RAM'i cihazın boş belleğine göre ayarlamak ve " +
            "sürümünüzle uyumlu bir optimizasyon modu kullanmak FPS'i en çok artıran adımlardır.",
        "Burada seçtiğiniz grafik motoru yalnızca başlatıcı uygulamasının sürücü yolunu etkiler; " +
            "etkisi oyun/renderer'a göre değişir, ölçerek karşılaştırın.",
    )
    GameKind.Generic -> emptyList()
}

/**
 * Türe göre önerilen başlangıç ayarı (paket, yöntem ve özel komutlar korunur); öneri yoksa
 * `null`. Bir deneme noktasıdır, garanti değildir.
 */
fun recommendedConfig(kind: GameKind, base: GameModeConfig): GameModeConfig? = when (kind) {
    // Bedrock Android'de 60 FPS'e sınırlıdır: 60 üstü hedef yalnızca ısıtır. Serin tutmak için
    // kare hızı 60, ekran 60 Hz'e kilitli, güç profili standart, çözünürlük düşük.
    GameKind.MinecraftBedrock -> base.copy(
        targetFps = kBedrockFpsCap,
        scale = 0.7,
        lockRefreshRate = true,
        refreshHz = 60,
        killBackground = true,
        animationsOff = true,
        dnd = true,
        powerProfile = GamePower.Standard,
        fixedPerf = false,
        guardTempC = 40,
        backend = GraphicsBackend.Default,
    )
    GameKind.MinecraftJava -> base.copy(
        targetFps = 60,
        scale = 0.6,
        lockRefreshRate = true,
        animationsOff = true,
        dnd = true,
        powerProfile = GamePower.Standard,
        guardTempC = 42,
        backend = GraphicsBackend.Default,
    )
    GameKind.Generic -> null
}

/** Bedrock'un Android'deki kare hızı sınırı (oyunun kendisi koyar; dışarıdan aşılamaz). */
const val kBedrockFpsCap = 60

/** Minecraft Bedrock'un Google Play paket adı. */
const val kBedrockPackage = "com.mojang.minecraftpe"

/**
 * Üçüncü taraf "FPS motoru", değiştirilmiş (modlu) APK ya da enjekte edilen kütüphane uyarısı.
 * YALNIZCA Minecraft türlerinde gösterilir; diğer oyunlarda `null`. Terminal bunları indirmez ya
 * da kurmaz; yalnızca sistem ayarlarını değiştirir.
 */
fun modWarning(kind: GameKind): String? = when (kind) {
    GameKind.Generic -> null
    else ->
        "Dikkat: Minecraft için dışarıdan indirilen \"FPS motoru\", değiştirilmiş APK ya da " +
            "enjekte edilen kütüphaneler hesabınızın (ve Realms/sunucu erişiminizin) banlanmasına, " +
            "zararlı yazılım bulaşmasına ve dünya verinizin kaybolmasına yol açabilir. Terminal bu " +
            "tür dosyaları indirmez ya da kurmaz; yalnızca sistem ayarlarını (sürücü yolu, ekran, " +
            "güç profili) değiştirir. Güncellemeleri yalnızca Google Play'den yapın."
}
