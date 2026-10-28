package com.terminalf.app.data

import com.terminalf.app.core.tr
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.terminalf.app.BuildConfig
import com.terminalf.app.core.ShellIdentity
import com.terminalf.app.core.ShellResult
import com.terminalf.app.core.parseIdOutput
import com.terminalf.app.core.parseWrappedOutput
import com.terminalf.app.core.wrapForShell
import com.terminalf.app.shizuku.IShellService
import com.terminalf.app.shizuku.ShellUserService
import com.terminalf.app.state.ShizukuAccess
import com.terminalf.app.state.ShizukuProbe
import com.terminalf.app.state.ShizukuUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * [RootService]'in kök (root) tabanlı yoluna paralel, KÖK GEREKTİRMEYEN ikinci çalıştırma yolu:
 * Shizuku (https://shizuku.rikka.app).
 *
 * Kullanıcının cihazında bir kez yaptığı "Kablosuz hata ayıklama" eşleştirmesi üzerinden Shizuku
 * bir arka plan servisi başlatır; bu servis çalıştığı sürece bu uygulama `adb shell` ile aynı
 * yetki seviyesinde komut çalıştırabilir:
 * - Shizuku normal (rootsuz) başlatıldıysa komutlar "shell" kullanıcısı kimliğiyle çalışır.
 * - Shizuku kökle başlatıldıysa komutlar root kimliğiyle çalışır.
 * - Gerçekten tam kök isteyen bazı tweak'ler shell kimliğiyle YİNE DE başarısız olabilir; bu servis
 *   bunu gizlemez, komutun gerçek çıktısına/çıkış koduna göre hata bildirilir.
 *
 * Komutlar Shizuku'nun UserService mekanizmasıyla ([ShellUserService], ayrı süreçte) çalıştırılır;
 * bu yüzden çıkış kodu GERÇEKTEN okunabilir. Hazır olup olmadığı, gerçek bir `id` komutuyla
 * kanıtlanır ([probe]).
 */
class ShizukuService(context: Context) : ShizukuAccess {

    private val appContext = context.applicationContext
    private val lock = Mutex()
    private val bindLock = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var connection: ServiceConnection? = null

    @Volatile
    override var identity: ShellIdentity? = null
        private set

    private val args: Shizuku.UserServiceArgs
        get() = Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .tag("terminalf-shell")
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    /** Cihazda Shizuku servisi kurulu ve çalışıyor mu? */
    override suspend fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Uygulamaya daha önce Shizuku izni verilmiş mi? */
    override suspend fun hasPermission(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Kullanıcıya Shizuku izin diyaloğunu gösterir ve sonucu döner. */
    override suspend fun requestPermission(): Boolean {
        try {
            if (!isRunning()) return false
            if (hasPermission()) return true
            if (Shizuku.isPreV11()) return false
            // Kullanıcı daha önce "bir daha sorma" ile reddettiyse diyalog hiç açılmaz.
            if (Shizuku.shouldShowRequestPermissionRationale()) return false
        } catch (_: Throwable) {
            return false
        }
        val result = withTimeoutOrNull(120_000) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val code = REQUEST_CODE
                    val listener = object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            if (requestCode != code) return
                            Shizuku.removeRequestPermissionResultListener(this)
                            if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(listener)
                    cont.invokeOnCancellation {
                        try {
                            Shizuku.removeRequestPermissionResultListener(listener)
                        } catch (_: Throwable) {
                        }
                    }
                    try {
                        Shizuku.requestPermission(code)
                    } catch (_: Throwable) {
                        try {
                            Shizuku.removeRequestPermissionResultListener(listener)
                        } catch (_: Throwable) {
                        }
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }
        return result ?: false
    }

    /**
     * Shizuku'nun GERÇEKTEN hazır olduğunu doğrular: servis çalışıyor mu, izin var mı ve `id`
     * test komutu hangi kimlikle (shell/root) çalışıyor. "Servis çalışıyor + izin var" tek başına
     * yeterli sayılmaz - komutların gerçekten çalıştığı ancak bu test komutuyla kanıtlanır.
     */
    override suspend fun probe(): ShizukuProbe = lock.withLock {
        identity = null
        if (!isRunning()) {
            return@withLock ShizukuProbe(running = false, permitted = false, error = tr("The Shizuku service is not running.", "Shizuku servisi çalışmıyor."))
        }
        val preV11 = try {
            Shizuku.isPreV11()
        } catch (_: Throwable) {
            false
        }
        if (preV11) {
            return@withLock ShizukuProbe(
                running = true,
                permitted = false,
                error = tr("The Shizuku version is too old; please update Shizuku.", "Shizuku sürümü çok eski; Shizuku'yu güncelleyin."),
            )
        }
        if (!hasPermission()) {
            return@withLock ShizukuProbe(running = true, permitted = false, error = tr("Shizuku permission was not granted.", "Shizuku izni verilmedi."))
        }
        try {
            // Yeniden denetim: önceki hızlı-hata kaydı silinir, iki yol da yeniden denenir.
            lastFailure = null
            val raw = run("id", 15)
            val parsed = parseWrappedOutput(raw)
            val id = parseIdOutput(parsed.output)
            if (id != null && parsed.exitCode == 0) {
                identity = id
                ShizukuProbe(running = true, permitted = true, identity = id)
            } else {
                val seen = parsed.output.trim()
                val short = if (seen.length > 80) seen.substring(0, 80) + "…" else seen
                ShizukuProbe(
                    running = true,
                    permitted = true,
                    error = tr("The test command (id) did not return the expected output", "Test komutu (id) beklenen çıktıyı döndürmedi") +
                        (if (short.isEmpty()) tr(" (empty response).", " (boş yanıt).") else tr(" (response: $short).", " (yanıt: $short).")),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ShizukuProbe(
                running = true,
                permitted = true,
                error = tr("The test command (id) could not be run: ${e.message ?: e.javaClass.simpleName}", "Test komutu (id) çalıştırılamadı: ${e.message ?: e.javaClass.simpleName}"),
            )
        }
    }

    /**
     * [command]'ı Shizuku üzerinden çalıştırır ve sonucunu döner. Servis hazır değilse ya da
     * Shizuku yanıt vermezse [ShizukuUnavailableException] fırlatır.
     */
    override suspend fun exec(command: String): ShellResult {
        if (identity == null) {
            val p = probe()
            if (!p.ready) throw ShizukuUnavailableException(p.error ?: tr("Shizuku is not ready.", "Shizuku hazır değil."))
        }
        val raw = try {
            run(command, 60)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ShizukuUnavailableException) {
            throw e
        } catch (e: Exception) {
            service = null
            identity = null
            throw ShizukuUnavailableException(tr("Shizuku could not run the command: ${e.message ?: e.javaClass.simpleName}", "Shizuku komutu çalıştıramadı: ${e.message ?: e.javaClass.simpleName}"))
        }
        return parseWrappedOutput(raw)
    }

    private enum class Mode { Unknown, UserService, Remote }

    @Volatile
    private var mode = Mode.Unknown

    /** Son başarısız bağlanma (zaman, neden): kısa süre içinde tekrar denemeden hemen hata verir. */
    @Volatile
    private var lastFailure: Pair<Long, String>? = null

    /**
     * Komutu çalıştırır. İki yol vardır:
     *  1) UserService (ayrı süreç): hızlıdır ama bazı cihazlarda/sürümlerde hiç açılmaz.
     *  2) Shizuku'nun doğrudan uzak süreci (`newProcess`): ek süreç/sınıf yüklemesi gerektirmez.
     * İlk çalışan yol hatırlanır; biri çalışmazsa diğeri denenir. İkisi de çalışmazsa kısa süre
     * boyunca tekrar beklenmez (art arda onlarca komutta dakikalarca takılmayı önler).
     */
    private suspend fun run(command: String, timeoutSeconds: Int): String {
        lastFailure?.let { (at, why) ->
            if (System.currentTimeMillis() - at < FAIL_FAST_MS) {
                throw ShizukuUnavailableException(why)
            }
        }
        if (mode != Mode.Remote) {
            try {
                val raw = callExec(bindLock.withLock { ensureService() }, command, timeoutSeconds)
                mode = Mode.UserService
                return raw
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                service = null
                // UserService açılmadı: doğrudan Shizuku sürecini dene.
                try {
                    val raw = runRemote(command, timeoutSeconds)
                    mode = Mode.Remote
                    return raw
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    val why = tr("${e.message ?: e.javaClass.simpleName}; fallback path: ${e2.message ?: e2.javaClass.simpleName}", "${e.message ?: e.javaClass.simpleName}; yedek yol: ${e2.message ?: e2.javaClass.simpleName}")
                    lastFailure = System.currentTimeMillis() to why
                    throw ShizukuUnavailableException(why)
                }
            }
        }
        try {
            return runRemote(command, timeoutSeconds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mode = Mode.Unknown
            val why = e.message ?: e.javaClass.simpleName
            lastFailure = System.currentTimeMillis() to why
            throw ShizukuUnavailableException(why)
        }
    }

    /** `Shizuku.newProcess` (13.x'te gizli) ile komutu Shizuku sunucusunda çalıştırır. */
    private suspend fun runRemote(command: String, timeoutSeconds: Int): String {
        val method = newProcessMethod
            ?: throw ShizukuUnavailableException(tr("The Shizuku direct process path is not available in this version", "Shizuku doğrudan süreç yolu bu sürümde yok"))
        val process = try {
            method.invoke(null, arrayOf("sh", "-c", wrapForShell(command)), null, null) as Process
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw ShizukuUnavailableException(tr("Shizuku could not start the process: ${(e.cause ?: e).message ?: "unknown error"}", "Shizuku süreci başlatamadı: ${(e.cause ?: e).message ?: "bilinmeyen hata"}"))
        }
        val out = ProcessRunner.runExisting(process, (timeoutSeconds + 5) * 1000L)
        return out.stdout + (if (out.stderr.isNotBlank()) "\n" + out.stderr else "")
    }

    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Komutu servise iletir. Binder çağrısı bloklayıcıdır ve iptal edilemez; servis takılırsa
     * sonsuza dek beklememek için çağrı ayrı iş parçacığında koşar ve süre dolunca bırakılır.
     */
    private suspend fun callExec(svc: IShellService, command: String, timeoutSeconds: Int): String {
        val call = ioScope.async { svc.exec(command, timeoutSeconds) ?: "" }
        val result = withTimeoutOrNull((timeoutSeconds + 5) * 1000L) { call.await() }
        if (result == null) {
            call.cancel()
            service = null
            identity = null
            throw ShizukuUnavailableException(tr("The command service did not respond within $timeoutSeconds s.", "Komut servisi $timeoutSeconds sn içinde yanıt vermedi."))
        }
        return result
    }

    /** Bağlı servisi döner; yoksa (ya da süreç öldüyse) yeniden bağlanır. */
    private suspend fun ensureService(): IShellService {
        val current = service
        if (current != null && current.asBinder().pingBinder()) return current

        val version = try {
            Shizuku.getVersion()
        } catch (_: Throwable) {
            -1
        }
        if (version in 0..9) {
            throw ShizukuUnavailableException(tr("The Shizuku version is too old (API $version); please update Shizuku.", "Shizuku sürümü çok eski (API $version); Shizuku'yu güncelleyin."))
        }

        // Bir kez yeniden dener: ilk bağlanma, Shizuku'nun süreci ilk kez başlatması yüzünden
        // yavaş olabilir. Her denemenin gerçek nedeni ayrıca kaydedilir.
        var lastReason = tr("no response", "yanıt gelmedi")
        for (attempt in 1..1) {
            unbind()
            val outcome = bindOnce(BIND_TIMEOUT_MS)
            if (outcome.service != null) return outcome.service
            lastReason = outcome.reason
            // Bağlanma sırasında özel bir hata varsa tekrar denemenin anlamı yok.
            if (outcome.fatal) break
        }
        unbind()
        throw ShizukuUnavailableException(tr("The Shizuku command service could not be started ($lastReason).", "Shizuku komut servisi başlatılamadı ($lastReason)."))
    }

    private class BindOutcome(val service: IShellService?, val reason: String, val fatal: Boolean = false)

    private suspend fun bindOnce(timeoutMs: Long): BindOutcome {
        var failure: String? = null
        var fatal = false
        val bound = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<IShellService?> { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (binder != null && binder.pingBinder()) {
                                val s = IShellService.Stub.asInterface(binder)
                                service = s
                                if (cont.isActive) cont.resume(s)
                            } else {
                                failure = tr("the service connected but is not responding", "servis bağlandı ancak yanıt vermiyor")
                                if (cont.isActive) cont.resume(null)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                            identity = null
                        }

                        override fun onNullBinding(name: ComponentName?) {
                            failure = tr("the service returned a null binding", "servis boş bağlantı döndürdü")
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onBindingDied(name: ComponentName?) {
                            failure = tr("the service process died while starting", "servis süreci başlarken öldü")
                            service = null
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    connection = conn
                    cont.invokeOnCancellation { unbind() }
                    try {
                        Shizuku.bindUserService(args, conn)
                    } catch (e: Throwable) {
                        failure = tr("binding error: ${e.javaClass.simpleName}", "bağlanma hatası: ${e.javaClass.simpleName}") +
                            (e.message?.let { ": $it" } ?: "")
                        fatal = true
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        return when {
            bound != null -> BindOutcome(bound, "")
            failure != null -> BindOutcome(null, failure!!, fatal)
            else -> BindOutcome(null, tr("No response within ${timeoutMs / 1000} s; restart Shizuku and try again", "${timeoutMs / 1000} sn içinde yanıt gelmedi; Shizuku'yu yeniden başlatıp tekrar deneyin"))
        }
    }

    private fun unbind() {
        val conn = connection ?: return
        connection = null
        service = null
        try {
            Shizuku.unbindUserService(args, conn, true)
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val REQUEST_CODE = 4242
        const val BIND_TIMEOUT_MS = 8_000L
        const val FAIL_FAST_MS = 20_000L
    }
}
