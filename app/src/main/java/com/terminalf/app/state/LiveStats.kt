package com.terminalf.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveSnapshot(
    val battery: BatteryReading,
    val system: SystemStats,
    /** Ölçümün alındığı an (epoch ms); henüz gerçek ölçüm yoksa `null`. */
    val time: Long? = null,
) {
    companion object {
        val Empty = LiveSnapshot(BatteryReading.Unknown, SystemStats.Empty)
    }
}

/**
 * Canlı ölçümler (pil, bellek, depolama).
 * - Yalnızca bu nesneyi dinleyen küçük parçalar yeniden çizilir.
 * - Uygulama arka plana geçince zamanlayıcı DURUR (pil ve CPU harcamaz): [onBackground].
 * - Okumalar üst üste binmez.
 */
class LiveStats(
    private val battery: BatteryReader,
    private val system: SystemStatsReader,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 4000,
    private val clock: () -> Long = System::currentTimeMillis,
) : Observable() {

    var snapshot: LiveSnapshot = LiveSnapshot.Empty
        private set

    private var timer: Job? = null
    private var reading = false
    private var started = false

    fun start() {
        if (started) return
        started = true
        startTimer()
        scope.launch { refresh() }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = scope.launch {
            while (isActive) {
                delay(intervalMs)
                refresh()
            }
        }
    }

    /** Uygulama öne geldi: zamanlayıcıyı yeniden kur ve hemen oku. */
    fun onForeground() {
        if (!started) return
        startTimer()
        scope.launch { refresh() }
    }

    /** Uygulama arka plana geçti: zamanlayıcıyı durdur. */
    fun onBackground() {
        timer?.cancel()
        timer = null
    }

    suspend fun refresh() {
        if (reading || isDisposed) return
        reading = true
        try {
            val (b, s) = coroutineScope {
                val bd = async { battery.read() }
                val sd = async { system.read() }
                bd.await() to sd.await()
            }
            if (isDisposed) return
            snapshot = LiveSnapshot(battery = b, system = s, time = clock())
            notifyChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Tek bir başarısız okuma bir sonraki turu etkilemesin.
        } finally {
            reading = false
        }
    }

    override fun dispose() {
        super.dispose()
        timer?.cancel()
        timer = null
        battery.dispose()
    }
}
