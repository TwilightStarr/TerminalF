package com.terminalf.app.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Pil şarj durumu (Android `BatteryManager` durumlarının sadeleştirilmiş hâli). */
enum class BatteryState { Charging, Discharging, Full, Unknown }

/**
 * Deşarj/şarj hızı tahmini algoritmasının saf (platform bağımsız) matematik kısmı.
 *
 * [state] yalnızca [BatteryState.Charging] veya [BatteryState.Discharging] için bir tahmin
 * üretir. [sampleCount], çağıranın elindeki toplam örnek sayısıdır - en az 2 örnek olmadan
 * güvenilir bir hız hesaplanamaz. [minElapsedForEstimate], çok kısa aralıklarda gürültülü
 * tahmin üretmemek için asgari geçen süredir. Zamanlar epoch milisaniyesidir.
 *
 * Yeterli/güvenilir veri yoksa ya da yüzde beklenen yönde değişmiyorsa `null` döner; uydurma
 * bir süre göstermek yerine çağıranın "hesaplanıyor" gibi dürüst bir durum göstermesine izin verir.
 */
fun estimateRemainingDuration(
    state: BatteryState,
    sampleCount: Int,
    firstTimeMs: Long,
    firstPercent: Int,
    lastTimeMs: Long,
    lastPercent: Int,
    minElapsedForEstimate: Duration = 60.seconds,
): Duration? {
    if (state != BatteryState.Charging && state != BatteryState.Discharging) return null
    if (sampleCount < 2) return null

    val elapsedMs = lastTimeMs - firstTimeMs
    val elapsed = elapsedMs.toDouble().div(1000.0).seconds
    val percentDelta = lastPercent - firstPercent

    // Yeterli zaman geçmediyse ya da yüzde henüz hiç değişmediyse güvenilir bir hız
    // hesaplanamaz.
    if (elapsed < minElapsedForEstimate || percentDelta == 0) return null

    val elapsedSeconds = (elapsedMs / 1000L).toInt() // Dart: Duration.inSeconds (kesilir)
    if (elapsedSeconds == 0) return null
    val ratePerSecond = percentDelta.toDouble() / elapsedSeconds

    return if (state == BatteryState.Discharging) {
        if (ratePerSecond >= 0) return null // beklenmedik yön, tahmin verme
        Math.round(lastPercent / -ratePerSecond).toInt().seconds
    } else {
        if (ratePerSecond <= 0) return null // beklenmedik yön, tahmin verme
        val remainingPercent = 100 - lastPercent
        Math.round(remainingPercent / ratePerSecond).toInt().seconds
    }
}
