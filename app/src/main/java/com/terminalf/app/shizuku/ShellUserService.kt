package com.terminalf.app.shizuku

import android.content.Context
import androidx.annotation.Keep
import com.terminalf.app.core.EXIT_MARKER
import com.terminalf.app.core.parseWrappedOutput
import com.terminalf.app.core.wrapForShell
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
            // Bu süreç Shizuku tarafından ayrı başlatılır: coroutine/Android bağımlılığı olmadan,
            // yalnızca düz Java süreç API'siyle çalışır (sınıf yükleme riski en aza iner).
            val process = ProcessBuilder("sh", "-c", wrapForShell(command))
                .redirectErrorStream(true)
                .start()
            try {
                process.outputStream.close()
            } catch (_: Exception) {
            }
            val out = java.io.ByteArrayOutputStream()
            val reader = Thread {
                try {
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = process.inputStream.read(buf)
                        if (n < 0) break
                        synchronized(out) { out.write(buf, 0, n) }
                    }
                } catch (_: Exception) {
                }
            }
            reader.isDaemon = true
            reader.start()

            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            var exit: Int? = null
            var timedOut = false
            while (exit == null) {
                try {
                    exit = process.exitValue()
                } catch (_: IllegalThreadStateException) {
                    if (System.nanoTime() > deadline) {
                        timedOut = true
                        process.destroy()
                        break
                    }
                    Thread.sleep(15)
                }
            }
            reader.join(2000)
            val raw = synchronized(out) { out.toString("UTF-8") }
            val parsed = parseWrappedOutput(raw)
            val text = if (parsed.output.length > MAX_CHARS) {
                parsed.output.substring(0, MAX_CHARS) + "\n…(output truncated)"
            } else {
                parsed.output
            }
            val code = if (timedOut) 124 else (parsed.exitCode ?: exit ?: 1)
            "$text\n$EXIT_MARKER:$code"
        } catch (e: Exception) {
            "${e.message ?: e.javaClass.simpleName}\n$EXIT_MARKER:124"
        }
    }

    private companion object {
        /** Binder işlem sınırı (~1 MB) aşılmasın diye çıktı kısaltılır. */
        const val MAX_CHARS = 300_000
    }
}
