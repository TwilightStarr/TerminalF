package com.terminalf.app.data

import com.terminalf.app.core.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.coroutineContext
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
        val job = coroutineContext[Job]
        collect(ProcessBuilder(args).start(), timeoutMs) { job?.isActive != false }
    }

    /**
     * Zaten başlatılmış bir [process]'in (ör. Shizuku'nun uzak süreci) çıktısını toplar. Süre dolarsa
     * süreç öldürülür ve [TimeoutException] fırlatılır. Çıkış kodu `exitValue()` yoklamasıyla değil,
     * ayrı iş parçacığında `waitFor()` ile alınır (uzak süreçlerde yoklama güvenilir değildir).
     */
    suspend fun runExisting(process: Process, timeoutMs: Long): ProcessOutput = withContext(Dispatchers.IO) {
        collectBlocking(process, timeoutMs)
    }

    private fun collectBlocking(process: Process, timeoutMs: Long): ProcessOutput {
        try {
            process.outputStream.close()
        } catch (_: Exception) {
        }
        val out = Collector(process.inputStream)
        val err = Collector(process.errorStream)
        out.start()
        err.start()
        val exit = java.util.concurrent.atomic.AtomicReference<Int?>(null)
        val done = java.util.concurrent.CountDownLatch(1)
        val waiter = Thread {
            try {
                exit.set(process.waitFor())
            } catch (_: Throwable) {
            }
            done.countDown()
        }
        waiter.isDaemon = true
        waiter.start()
        if (!done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            try {
                process.destroy()
            } catch (_: Throwable) {
            }
            throw TimeoutException(tr("The command did not finish within ${timeoutMs / 1000} s.", "Komut ${timeoutMs / 1000} sn içinde bitmedi."))
        }
        out.join(2000)
        err.join(2000)
        return ProcessOutput(out.text(), err.text(), exit.get() ?: -1)
    }

    private fun collect(process: Process, timeoutMs: Long, active: () -> Boolean): ProcessOutput {
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
                    if (!active()) throw kotlinx.coroutines.CancellationException(tr("cancelled", "iptal edildi"))
                    if (System.nanoTime() > deadline) {
                        throw TimeoutException(tr("The command did not finish within ${timeoutMs / 1000} s.", "Komut ${timeoutMs / 1000} sn içinde bitmedi."))
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
        return ProcessOutput(out.text(), err.text(), exit)
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
