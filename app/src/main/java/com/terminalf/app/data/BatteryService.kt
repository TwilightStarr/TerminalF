package com.terminalf.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.terminalf.app.core.BatteryState
import com.terminalf.app.core.estimateRemainingDuration
import com.terminalf.app.state.BatteryReader
import com.terminalf.app.state.BatteryReading
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class BatterySample(val timeMs: Long, val percent: Int)

/**
 * Pil yüzdesi, durumu ve sıcaklığı: Android'in "yapışkan" `ACTION_BATTERY_CHANGED` yayınından
 * okunur (izin gerekmez). Kalan süre tahmini, son 15 dakikadaki en eski ve en yeni yüzde örnekleri
 * arasındaki değişim hızından doğrusal olarak çıkarılır (bkz. `estimateRemainingDuration`); kesin
 * değil, kaba bir tahmindir.
 */
class BatteryService(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : BatteryReader {

    private val appContext = context.applicationContext
    private val history = ArrayList<BatterySample>()

    override suspend fun read(): BatteryReading {
        return try {
            val intent: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) return BatteryReading.Unknown

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) Math.round(level * 100f / scale) else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val state = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.Charging
                BatteryManager.BATTERY_STATUS_FULL -> BatteryState.Full
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                -> BatteryState.Discharging
                else -> BatteryState.Unknown
            }
            // EXTRA_TEMPERATURE: onda bir °C. Okunamazsa (Int.MIN_VALUE) `null`.
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val temperature = if (tempTenths == Int.MIN_VALUE || tempTenths <= 0) null else tempTenths / 10.0

            if (percent < 0) {
                return BatteryReading(percent = null, state = state, temperatureCelsius = temperature)
            }
            recordSample(percent)
            BatteryReading(
                percent = percent,
                state = state,
                estimatedRemaining = estimate(state),
                temperatureCelsius = temperature,
            )
        } catch (_: Exception) {
            BatteryReading.Unknown
        }
    }

    private fun recordSample(percent: Int) {
        val now = clock()
        history.add(BatterySample(now, percent))
        val cutoff = now - HISTORY_WINDOW_MS
        history.removeAll { it.timeMs < cutoff }
    }

    private fun estimate(state: BatteryState) = if (history.size < 2) {
        null
    } else {
        estimateRemainingDuration(
            state = state,
            sampleCount = history.size,
            firstTimeMs = history.first().timeMs,
            firstPercent = history.first().percent,
            lastTimeMs = history.last().timeMs,
            lastPercent = history.last().percent,
            minElapsedForEstimate = MIN_ELAPSED_SECONDS.seconds,
        )
    }

    override fun dispose() {
        history.clear()
    }

    private companion object {
        val HISTORY_WINDOW_MS = 15.minutes.inWholeMilliseconds
        const val MIN_ELAPSED_SECONDS = 60
    }
}
