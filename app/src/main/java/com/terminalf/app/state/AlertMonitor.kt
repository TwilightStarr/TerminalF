package com.terminalf.app.state

import com.terminalf.app.core.AlertKind
import com.terminalf.app.core.BatteryState
import com.terminalf.app.core.evaluateAlerts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Canlı ölçümleri dinler ve eşik aşılınca BİR KEZ bildirim gönderir. Ölçümler yalnızca uygulama
 * çalışırken güncellenir (pil dostu); arka plan servisi yoktur.
 */
class AlertMonitor(
    private val live: LiveStats,
    private val notifier: Notifier,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    private var active: Set<AlertKind> = emptySet()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            live.changes.collect { onTick() }
        }
    }

    fun dispose() {
        job?.cancel()
        job = null
    }

    /** Her yeni ölçümde çağrılır (testlerde doğrudan da çağrılabilir). */
    suspend fun onTick() {
        if (!settings.healthAlerts) {
            active = emptySet()
            return
        }
        val snap = live.snapshot
        if (snap.time == null) return // henüz gerçek ölçüm yok
        val battery = snap.battery
        val charging = battery.state == BatteryState.Charging || battery.state == BatteryState.Full
        val result = evaluateAlerts(
            thresholds = settings.thresholds,
            active = active,
            temperatureC = battery.temperatureCelsius,
            storageUsedRatio = snap.system.disk?.usedRatio,
            batteryPercent = battery.percent,
            charging = charging,
            memoryUsedRatio = snap.system.memory?.usedRatio,
        )
        active = result.active
        for (event in result.fire) {
            try {
                notifier.showAlert(event)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bildirim gönderilemezse akış bozulmaz.
            }
        }
    }
}
