package com.terminalf.app.core

import kotlin.math.roundToInt

/** Cihaz sağlığı puanı ve "şimdi ne yapmalıyım?" önerileri. Saf mantık: platforma bağlı değil. */

enum class AdviceAction { FreeMemory, CleanStorage, SaveBattery, CoolDown, EnableControl }

enum class AdviceLevel { Info, Warning, Critical }

data class Advice(
    val title: String,
    val detail: String,
    val level: AdviceLevel,
    val action: AdviceAction,
    val actionLabel: String,
)

/** 0-100 arası sağlık puanı. Veri yoksa (null) o kalem puanı düşürmez. */
fun computeHealthScore(
    ramUsedRatio: Double? = null,
    storageUsedRatio: Double? = null,
    batteryPercent: Int? = null,
    charging: Boolean = false,
    temperatureCelsius: Double? = null,
): Int {
    var penalty = 0.0
    if (ramUsedRatio != null && ramUsedRatio > 0.6) {
        penalty += ((ramUsedRatio - 0.6) / 0.4).coerceIn(0.0, 1.0) * 25
    }
    if (storageUsedRatio != null && storageUsedRatio > 0.7) {
        penalty += ((storageUsedRatio - 0.7) / 0.3).coerceIn(0.0, 1.0) * 25
    }
    if (batteryPercent != null && !charging && batteryPercent < 50) {
        penalty += ((50 - batteryPercent) / 50.0).coerceIn(0.0, 1.0) * 15
    }
    if (temperatureCelsius != null && temperatureCelsius > 38) {
        penalty += ((temperatureCelsius - 38) / 10).coerceIn(0.0, 1.0) * 20
    }
    return Math.round(100 - penalty).toInt().coerceIn(0, 100)
}

fun healthLabel(score: Int): String = when {
    score >= 85 -> tr("Excellent", "Mükemmel")
    score >= 70 -> tr("Good", "İyi")
    score >= 45 -> tr("Needs attention", "Dikkat gerekiyor")
    else -> tr("Critical", "Kritik")
}

private fun pct(ratio: Double): String = "%${(ratio * 100).roundToInt()}"

/** Mevcut ölçümlerden, önem sırasına göre öneri listesi üretir. */
fun buildAdvice(
    ramUsedRatio: Double? = null,
    storageUsedRatio: Double? = null,
    batteryPercent: Int? = null,
    charging: Boolean = false,
    temperatureCelsius: Double? = null,
    controlReady: Boolean,
): List<Advice> {
    val out = ArrayList<Advice>()

    if (temperatureCelsius != null && temperatureCelsius >= 42) {
        out.add(
            Advice(
                title = tr("Device is heating up", "Cihaz ısınıyor"),
                detail = tr("Battery temperature is ${fixed(temperatureCelsius, 1)}°C. Closing background apps helps.", "Pil sıcaklığı ${fixed(temperatureCelsius, 1)}°C. Arka plan uygulamalarını kapatmak yardımcı olur."),
                level = if (temperatureCelsius >= 45) AdviceLevel.Critical else AdviceLevel.Warning,
                action = AdviceAction.CoolDown,
                actionLabel = tr("Cool down", "Soğut"),
            ),
        )
    }
    if (ramUsedRatio != null && ramUsedRatio >= 0.8) {
        out.add(
            Advice(
                title = tr("Memory is full (${pct(ramUsedRatio)})", "Bellek dolu (${pct(ramUsedRatio)})"),
                detail = tr("Background apps are holding memory. Clearing it speeds up the device.", "Arka plandaki uygulamalar belleği tutuyor. Temizlemek cihazı hızlandırır."),
                level = if (ramUsedRatio >= 0.9) AdviceLevel.Critical else AdviceLevel.Warning,
                action = AdviceAction.FreeMemory,
                actionLabel = tr("Boost", "Hızlandır"),
            ),
        )
    }
    if (storageUsedRatio != null && storageUsedRatio >= 0.85) {
        out.add(
            Advice(
                title = tr("Storage is almost full (${pct(storageUsedRatio)})", "Depolama dolmak üzere (${pct(storageUsedRatio)})"),
                detail = tr("You can free up space by clearing caches.", "Önbellekleri temizleyerek yer açabilirsiniz."),
                level = if (storageUsedRatio >= 0.95) AdviceLevel.Critical else AdviceLevel.Warning,
                action = AdviceAction.CleanStorage,
                actionLabel = tr("Clean", "Temizle"),
            ),
        )
    }
    if (batteryPercent != null && batteryPercent <= 20 && !charging) {
        out.add(
            Advice(
                title = tr("Battery is low ($batteryPercent%)", "Pil azalıyor (%$batteryPercent)"),
                detail = tr("The Battery Saver profile limits the screen and background work.", "Pil Tasarrufu profili ekranı ve arka plan işlerini kısıtlar."),
                level = if (batteryPercent <= 10) AdviceLevel.Critical else AdviceLevel.Warning,
                action = AdviceAction.SaveBattery,
                actionLabel = tr("Save", "Tasarruf"),
            ),
        )
    }
    if (!controlReady) {
        out.add(
            Advice(
                title = tr("Full control is off", "Tam kontrol kapalı"),
                detail = tr("Finish setup with one tap to unlock optimization and app management.", "Tek dokunuşla kurulumu tamamlayın; optimizasyon ve uygulama yönetimi açılır."),
                level = AdviceLevel.Info,
                action = AdviceAction.EnableControl,
                actionLabel = tr("Enable", "Etkinleştir"),
            ),
        )
    }
    return out
}
