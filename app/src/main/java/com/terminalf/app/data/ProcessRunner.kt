package com.terminalf.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.TimeoutException

/** Bir süreç çalıştırmanın ham sonucu. */
data class ProcessOutput(val stdout: String, val stderr: String, val exitCode: Int)

/**
 * `ProcessBuilder` üzerinde, zaman aşımı ve iptal destekli ince bir sarmalayıcı. Android API'si
 * kullanmaz (JVM'de de çalışır). Bloklayan iş `Dispatchers.IO`'da yapılır.
 */
object ProcessRunner {

    /**
     * [args] komutunu çalıştırır. [timeoutMs] dolarsa süreç öldürülür ve [TimeoutException]
     * fırlatılır; coroutine iptal edilirse süreç yine öldürülür.
     */
    suspend fun run(args: List<String>, timeoutMs: Long): ProcessOutput = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(args).start()
        try {
            process.outputStream.close()
        } catch (_: Exception) {
        }
        val out = Collector(process.inputStream)
        val err = Collector(process.errorStream)
        out.start()
        err.start()

        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var exit: Int? = null
        try {
            while (exit == null) {
                try {
                    exit = process.exitValue()
                } catch (_: IllegalThreadStateException) {
                    currentCoroutineContext().ensureActive()
                    if (System.nanoTime() > deadline) {
                        throw TimeoutException("Komut ${timeoutMs / 1000} sn içinde bitmedi.")
                    }
                    Thread.sleep(15)
                }
            }
        } catch (e: Throwable) {
            process.destroy()
            throw e
        }
        out.join(2000)
        err.join(2000)
        ProcessOutput(out.text(), err.text(), exit)
    }

    private class Collector(private val stream: InputStream) : Thread() {
        private val buffer = java.io.ByteArrayOutputStream()

        init {
            isDaemon = true
        }

        override fun run() {
            try {
                val chunk = ByteArray(4096)
                while (true) {
                    val n = stream.read(chunk)
                    if (n < 0) break
                    synchronized(buffer) { buffer.write(chunk, 0, n) }
                }
            } catch (_: Exception) {
            }
        }

        fun text(): String = synchronized(buffer) { buffer.toString("UTF-8") }
    }
}
