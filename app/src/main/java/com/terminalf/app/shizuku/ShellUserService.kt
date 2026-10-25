package com.terminalf.app.shizuku

import android.content.Context
import androidx.annotation.Keep
import com.terminalf.app.core.EXIT_MARKER
import com.terminalf.app.core.parseWrappedOutput
import com.terminalf.app.core.wrapForShell
import com.terminalf.app.data.ProcessRunner
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Shizuku tarafından `shell` (ya da `root`) kimliğiyle ayrı bir süreçte örneklenir. Bu sınıf
 * uygulamanın kendi APK'sından yüklenir; bu yüzden uygulamanın diğer sınıflarını kullanabilir.
 * Yeniden adlandırılırsa `proguard-rules.pro` ve ShizukuService içindeki ComponentName de
 * güncellenmelidir.
 */
class ShellUserService() : IShellService.Stub() {

    /** Shizuku yeni sürümlerde Context alan kurucuyu tercih eder; ikisi de desteklenir. */
    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String, timeoutSeconds: Int): String {
        return try {
            val timeoutMs = timeoutSeconds.coerceIn(1, 600) * 1000L
            val r = runBlocking {
                ProcessRunner.run(listOf("sh", "-c", wrapForShell(command)), timeoutMs)
            }
            val parsed = parseWrappedOutput(r.stdout)
            val body = if (parsed.output.length > MAX_CHARS) {
                parsed.output.substring(0, MAX_CHARS) + "\n…(çıktı kısaltıldı)"
            } else {
                parsed.output
            }
            "$body\n$EXIT_MARKER:${parsed.exitCode ?: r.exitCode}"
        } catch (e: Exception) {
            "${e.message ?: e.javaClass.simpleName}\n$EXIT_MARKER:124"
        }
    }

    private companion object {
        /** Binder işlem sınırı (~1 MB) aşılmasın diye çıktı kısaltılır. */
        const val MAX_CHARS = 300_000
    }
}
