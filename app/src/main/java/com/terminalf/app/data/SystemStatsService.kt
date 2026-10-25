package com.terminalf.app.data

import android.os.SystemClock
import com.terminalf.app.core.DiskInfo
import com.terminalf.app.core.MemInfo
import com.terminalf.app.core.parseDfOutput
import com.terminalf.app.core.parseMemInfo
import com.terminalf.app.core.parseUptime
import com.terminalf.app.state.SystemStats
import com.terminalf.app.state.SystemStatsReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Cihazın GERÇEK (yalnızca uygulamaya ait değil) bellek, depolama ve çalışma süresi bilgisi.
 * Android'in herkese açık dosyalarından okunur; kök gerekmez. Bir kalem okunamazsa `null` kalır,
 * uydurma değer üretilmez.
 */
class SystemStatsService(
    private val procMeminfo: File = File("/proc/meminfo"),
    private val procUptime: File = File("/proc/uptime"),
    /** Açılıştan beri geçen süre (ms, derin uyku dahil). Android 16'da /proc/uptime okunamayabilir. */
    private val bootElapsedMs: () -> Long = { SystemClock.elapsedRealtime() },
) : SystemStatsReader {

    override suspend fun read(): SystemStats = coroutineScope {
        val m = async { memory() }
        val d = async { disk() }
        val u = async { uptime() }
        SystemStats(memory = m.await(), disk = d.await(), uptime = u.await())
    }

    private suspend fun memory(): MemInfo? = withContext(Dispatchers.IO) {
        try {
            parseMemInfo(procMeminfo.readText())
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun disk(): DiskInfo? {
        for (path in listOf("/data", "/storage/emulated/0")) {
            try {
                val r = ProcessRunner.run(listOf("df", "-k", path), 4_000)
                if (r.exitCode == 0) {
                    parseDfOutput(r.stdout)?.let { return it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sıradaki yolu dene.
            }
        }
        return null
    }

    private suspend fun uptime(): Duration? = withContext(Dispatchers.IO) {
        // 1) Resmî API: SystemClock.elapsedRealtime() = cihazın açılışından beri süre.
        val ms = try {
            bootElapsedMs()
        } catch (_: Throwable) {
            0L
        }
        if (ms > 0L) return@withContext ms.milliseconds
        // 2) Yedek: /proc/uptime (yeni Android sürümlerinde SELinux nedeniyle okunamayabilir).
        try {
            parseUptime(procUptime.readText())
        } catch (_: Exception) {
            null
        }
    }
}
