package com.terminalf.app.core

/**
 * winutil'deki "Debloat / Performance" bölümlerinin Android karşılığı. Üçüncü kategori olarak
 * "Gizlilik" eklendi: Android'de debloat ve gizlilik ayarları (reklam takibi, hata raporları vb.)
 * kendi başına anlamlı ayrı bir grup oluşturur.
 */
enum class TweakCategory {
    Debloat,
    Performance,
    Privacy;

    /** Section title shown in the UI (follows the active language). */
    val label: String
        get() = when (this) {
            Debloat -> "Debloat"
            Performance -> tr("Performance", "Performans")
            Privacy -> tr("Privacy", "Gizlilik")
        }

    companion object {
        fun fromJson(value: String): TweakCategory = when (value) {
            "debloat" -> Debloat
            "privacy" -> Privacy
            else -> Performance
        }
    }
}

/**
 * Tek bir sistem ayarı/optimizasyonu.
 *
 * ÖNEMLİ PLATFORM GERÇEĞİ: Android, üçüncü parti bir uygulamanın `pm disable-user`,
 * `settings put global` gibi komutları KENDİ BAŞINA çalıştırmasına izin vermez - bu yalnızca
 * `adb shell` (kabuk kullanıcısı) veya kök (root) erişimiyle mümkündür. Bu yüzden
 * `ControlController`, yetki yoksa komutu sessizce atlamaz/uydurmaz; arayüz durumu dürüstçe gösterir.
 */
data class Tweak(
    val id: String,
    val title: String,
    val description: String,
    val category: TweakCategory,
    /** Root yolunda `su -c` altında, Shizuku yolunda `sh -c` sarmalayıcısıyla çalıştırılacak komut. */
    val applyCommand: String,
    /**
     * Varsa, tweak'i geri almak için çalıştırılacak komut. `null`/boş ise bu tweak bir anahtar
     * (toggle) değil, tek seferlik bir eylemdir - arayüzde switch yerine "Çalıştır" düğmesi çıkar.
     */
    val revertCommand: String? = null,
    /**
     * Tweak'in cihazda gerçekten AÇIK olup olmadığını okuyan, yan etkisiz (salt okunur) komut.
     * Opsiyoneldir; yoksa durum cihazdan okunamaz.
     */
    val checkCommand: String? = null,
    /**
     * [checkCommand] çıktısının, tweak AÇIKKEN alması beklenen değeri (satır satır, boşluklar
     * kırpılarak ve sayılar sayısal olarak karşılaştırılır - bkz. [outputsMatch]).
     */
    val expectedOutput: String? = null,
    val requiresRoot: Boolean = true,
    /** `true` ise uygulanmadan önce ekstra bir onay diyaloğu gösterilir. */
    val dangerous: Boolean = false,
    /** English title / description (`titleEn` / `descriptionEn` in tweaks.json); [title] and [description] are Turkish. */
    val titleEn: String = "",
    val descriptionEn: String = "",
) {
    /** Title in the active language (falls back to the Turkish text when no English text exists). */
    val displayTitle: String get() = tr(titleEn.ifBlank { title }, title)

    /** Description in the active language. */
    val displayDescription: String get() = tr(descriptionEn.ifBlank { description }, description)

    /** Cihazdan durum okumaya yetecek bilgi var mı? İki alan da dolu olmalı. */
    val hasCheck: Boolean get() = !checkCommand.isNullOrEmpty() && expectedOutput != null

    /** `revertCommand` varsa anahtar (toggle), yoksa tek seferlik eylem. */
    val isToggle: Boolean get() = !revertCommand.isNullOrEmpty()

    companion object {
        fun fromJson(json: Map<String, Any?>): Tweak = Tweak(
            id = json.str("id") ?: error("tweak id missing"),
            title = json.str("title") ?: error("tweak title missing"),
            description = json.str("description") ?: "",
            category = TweakCategory.fromJson(json.str("category") ?: ""),
            applyCommand = json.str("applyCommand") ?: error("tweak applyCommand missing"),
            revertCommand = json.str("revertCommand"),
            checkCommand = json.str("checkCommand"),
            expectedOutput = json.str("expectedOutput"),
            requiresRoot = json.bool("requiresRoot") ?: true,
            dangerous = json.bool("dangerous") ?: false,
            titleEn = json.str("titleEn") ?: "",
            descriptionEn = json.str("descriptionEn") ?: "",
        )
    }
}

/** `applications.json`'daki tek bir önerilen (açık kaynak) uygulama girdisi. */
data class RecommendedApp(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    /** "F-Droid", "GitHub" vb. - kullanıcıya nereye yönlendirildiğini gösterir. */
    val sourceLabel: String,
    val url: String,
    /** English texts (`nameEn`, `descriptionEn`, `categoryEn` in applications.json). */
    val descriptionEn: String = "",
    val categoryEn: String = "",
) {
    /** Description in the active language (falls back to the Turkish text). */
    val displayDescription: String get() = tr(descriptionEn.ifBlank { description }, description)

    /** Category label in the active language. [category] stays the stable grouping key. */
    val displayCategory: String get() = tr(categoryEn.ifBlank { category }, category)

    companion object {
        fun fromJson(json: Map<String, Any?>): RecommendedApp = RecommendedApp(
            id = json.str("id") ?: error("app id missing"),
            name = json.str("name") ?: error("app name missing"),
            description = json.str("description") ?: "",
            category = json.str("category") ?: "Genel",
            sourceLabel = json.str("sourceLabel") ?: "Bağlantı",
            url = json.str("url") ?: error("app url missing"),
            descriptionEn = json.str("descriptionEn") ?: "",
            categoryEn = json.str("categoryEn") ?: "",
        )
    }
}

/**
 * `features.json`'daki hızlı ayar kısayolu - KÖK GEREKTİRMEZ, doğrudan gerçek bir Android
 * sistem ayarları ekranını açar.
 */
data class FeatureShortcut(
    val id: String,
    val title: String,
    val description: String,
    /** "developer", "location" gibi düz metin; `CatalogService` bunu gerçek bir Settings eylemine çevirir. */
    val settingsType: String,
    /** English texts (`titleEn` / `descriptionEn` in features.json). */
    val titleEn: String = "",
    val descriptionEn: String = "",
) {
    val displayTitle: String get() = tr(titleEn.ifBlank { title }, title)

    val displayDescription: String get() = tr(descriptionEn.ifBlank { description }, description)

    companion object {
        fun fromJson(json: Map<String, Any?>): FeatureShortcut = FeatureShortcut(
            id = json.str("id") ?: error("shortcut id missing"),
            title = json.str("title") ?: error("shortcut title missing"),
            description = json.str("description") ?: "",
            settingsType = json.str("settingsType") ?: error("shortcut settingsType missing"),
            titleEn = json.str("titleEn") ?: "",
            descriptionEn = json.str("descriptionEn") ?: "",
        )
    }
}

private fun <T> parseList(source: String, map: (Map<String, Any?>) -> T): List<T> {
    val data = Json.parse(source).asList() ?: error("Expected: JSON array")
    return data.map { map(it.asMap() ?: error("Expected: JSON object")) }
}

fun tweaksFromJsonString(source: String): List<Tweak> = parseList(source, Tweak::fromJson)

fun appsFromJsonString(source: String): List<RecommendedApp> = parseList(source, RecommendedApp::fromJson)

fun featuresFromJsonString(source: String): List<FeatureShortcut> = parseList(source, FeatureShortcut::fromJson)
