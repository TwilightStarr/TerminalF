package com.terminalf.app.data

import com.terminalf.app.core.ShellIdentity
import com.terminalf.app.core.ShellResult
import com.terminalf.app.core.parseIdOutput
import com.terminalf.app.state.RootAccess
import kotlinx.coroutines.CancellationException

/**
 * Kök (root) yetkisiyle komut çalıştırma. Cihaz köklü değilse sessizce "kök yok" der; hiçbir şey
 * uydurmaz.
 */
class RootService : RootAccess {
    @Volatile
    private var cache: Boolean? = null

    @Volatile
    override var identity: ShellIdentity? = null
        private set

    /** `su -c id` ile kök erişimini dener; sonuç önbelleğe alınır. */
    override suspend fun hasRoot(forceRecheck: Boolean): Boolean {
        val cached = cache
        if (cached != null && !forceRecheck) return cached
        try {
            val r = ProcessRunner.run(listOf("su", "-c", "id"), 8_000)
            val ok = r.exitCode == 0 && r.stdout.contains("uid=0")
            cache = ok
            identity = if (ok) parseIdOutput(r.stdout) else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // `su` yok (cihaz köklü değil) ya da izin isteği zaman aşımına uğradı.
            cache = false
            identity = null
        }
        return cache == true
    }

    /**
     * [command]'ı `su -c` altında çalıştırır. Çıkış kodu sıfırdan farklıysa stderr da çıktıya
     * eklenir ki hata nedeni görülebilsin.
     */
    override suspend fun exec(command: String): ShellResult {
        val r = ProcessRunner.run(listOf("su", "-c", command), 30_000)
        var output = r.stdout.trim()
        val err = r.stderr.trim()
        if (r.exitCode != 0 && err.isNotEmpty()) output = "$output\n$err".trim()
        return ShellResult(output = output, exitCode = r.exitCode)
    }
}
