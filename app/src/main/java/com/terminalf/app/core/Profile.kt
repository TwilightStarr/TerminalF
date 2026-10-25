package com.terminalf.app.core

/**
 * Tek dokunuşla uygulanan hazır optimizasyon paketi. Her profil, `assets/config/tweaks.json`
 * içindeki tweak kimliklerinden oluşur.
 *
 * [iconKey], arayüzün simge seçimi içindir (`bolt`, `battery`, `shield`, `cleaning`); saf mantık
 * katmanı arayüz kitaplığına bağlı değildir.
 */
data class OptimizationProfile(
    val id: String,
    val title: String,
    val description: String,
    val iconKey: String,
    val tweakIds: List<String>,
    /** `false` ise profil tek seferlik eylemlerden oluşur (geri alınacak bir şey yok). */
    val reversible: Boolean = true,
)

val kProfiles: List<OptimizationProfile> = listOf(
    OptimizationProfile(
        id = "performance",
        title = "Performans",
        description = "Animasyonları kapatır, GPU ve yüksek yenileme hızını zorlar, arka planı temizler.",
        iconKey = "bolt",
        tweakIds = listOf(
            "performance_disable_animations",
            "performance_force_gpu_rendering",
            "performance_disable_adaptive_battery",
            "performance_force_peak_refresh_rate",
            "performance_kill_background",
        ),
    ),
    OptimizationProfile(
        id = "battery",
        title = "Pil Tasarrufu",
        description = "Yenileme hızını 60 Hz'e sabitler, pil tasarrufu modunu ve arka plan taramasını kısar.",
        iconKey = "battery",
        tweakIds = listOf(
            "performance_cap_refresh_rate_battery",
            "battery_saver_mode",
            "privacy_disable_always_scanning",
        ),
    ),
    OptimizationProfile(
        id = "privacy",
        title = "Gizlilik",
        description = "Reklam takibini ve otomatik hata raporlarını kapatır.",
        iconKey = "shield",
        tweakIds = listOf(
            "privacy_limit_ad_tracking",
            "privacy_disable_error_reports",
            "privacy_disable_always_scanning",
        ),
    ),
    OptimizationProfile(
        id = "cleanup",
        title = "Temizlik",
        description = "Önbellekleri ve arka plan uygulamalarını temizler, gereksiz Google/yazdırma bileşenlerini kapatır.",
        iconKey = "cleaning",
        reversible = false,
        tweakIds = listOf(
            "cleanup_trim_caches",
            "performance_kill_background",
            "debloat_google_feedback",
            "debloat_onetime_initializer",
            "debloat_print_recommendation",
        ),
    ),
)

fun profileById(id: String): OptimizationProfile? = kProfiles.firstOrNull { it.id == id }
