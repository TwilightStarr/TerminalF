package com.terminalf.app.state

import com.terminalf.app.core.OptimizationProfile
import com.terminalf.app.core.ShellResult
import com.terminalf.app.core.Tweak
import com.terminalf.app.core.TweakState
import com.terminalf.app.core.TweakStatus
import com.terminalf.app.core.failureReason
import com.terminalf.app.core.isSafePackage
import com.terminalf.app.core.outputLines
import com.terminalf.app.core.stateFromCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

/** Komutların hangi yetki yolundan çalıştığı. */
enum class AccessPath { None, Root, Shizuku }

enum class ControlPhase { Checking, Ready, NeedsSetup }

/** Yetkili komut çalıştırılamadığında (yol yok / servis yanıt vermiyor). */
class ControlUnavailableException(val reason: String) : Exception(reason)

data class TweakOutcome(val ok: Boolean, val message: String)

data class ProfileResult(val applied: Int, val skipped: Int, val failed: Int) {
    val summary: String
        get() {
            val parts = ArrayList<String>()
            if (applied > 0) parts.add("$applied uygulandı")
            if (skipped > 0) parts.add("$skipped zaten hazırdı")
            if (failed > 0) parts.add("$failed başarısız")
            return if (parts.isEmpty()) "Yapılacak bir şey yok" else parts.joinToString(" · ")
        }
}

/**
 * Paket adı güvenli mi? Kabuk komutuna yerleştirilmeden önce MUTLAKA doğrulanır (komut
 * enjeksiyonunu önler).
 */
fun isValidPackageName(value: String): Boolean = isSafePackage(value)

/**
 * "Karmaşık kısmı kendisi halleden" yönetim katmanı.
 *
 * - Kök varsa kökü, yoksa Shizuku'yu OTOMATİK seçer; kullanıcı yol seçmez.
 * - Hazır olup olmadığını gerçek bir `id` komutuyla kanıtlar.
 * - Başarısız komut asla "tamamlandı" görünmez.
 * - Tweak'lerin AÇIK/KAPALI durumunu cihazdan okur.
 *
 * [scope], arka planda başlatılan işler (durum tazeleme) içindir. Uygulamada ana iş parçacığı
 * bağlamıdır; testlerde `Dispatchers.Unconfined` verilebilir.
 */
class ControlController(
    private val root: RootAccess,
    private val shizuku: ShizukuAccess,
    private val catalog: TweakCatalog,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : Observable() {

    /**
     * Bildirim gönderen katman ve "işlem sonucu bildirimi açık mı?" sorusu (uygulama tarafından
     * bağlanır; testlerde boş bırakılabilir).
     */
    var notifier: Notifier? = null
    var taskNotificationsEnabled: () -> Boolean = { true }

    /**
     * Bir işlem bitince çağrılır: dokunsal geri bildirim verir ve (açıksa, uygulama arka
     * plandayken) sonuç bildirimi gönderir.
     */
    fun reportDone(title: String, body: String, ok: Boolean = true) {
        if (ok) Haptics.success() else Haptics.warning()
        val n = notifier
        if (n != null && taskNotificationsEnabled()) {
            scope.launch {
                try {
                    n.showTask(title, body)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Bildirim gönderilemezse akış bozulmaz.
                }
            }
        }
    }

    var phase: ControlPhase = ControlPhase.Checking
        private set
    var path: AccessPath = AccessPath.None
        private set
    var identity: String = ""
        private set
    var error: String? = null
        private set
    var shizukuRunning: Boolean = false
        private set
    var shizukuPermitted: Boolean = false
        private set
    var settingUp: Boolean = false
        private set

    /** Shizuku komut servisi arka planda doğrulanıyor (kullanıcı beklemeden kullanabilir). */
    var verifying: Boolean = false
        private set

    private var verifyGeneration = 0
    var tweaks: List<Tweak> = emptyList()
        private set

    private val statuses = HashMap<String, TweakStatus>()
    private val working = LinkedHashSet<String>()
    private val logLines = ArrayList<String>()

    var busyLabel: String? = null
        private set

    val ready: Boolean get() = phase == ControlPhase.Ready
    val checking: Boolean get() = phase == ControlPhase.Checking
    val busy: Boolean get() = busyLabel != null
    val log: List<String> get() = logLines.toList()

    fun isWorking(tweakId: String): Boolean = tweakId in working

    fun stateOf(tweakId: String): TweakState = statuses[tweakId]?.state ?: TweakState.Unknown

    /** Kullanıcıya gösterilen tek satırlık durum. */
    val headline: String
        get() = when (phase) {
            ControlPhase.Checking -> "Denetleniyor…"
            ControlPhase.Ready -> if (path == AccessPath.Root) "Tam kontrol · root" else "Tam kontrol · $identity"
            ControlPhase.NeedsSetup -> "Tam kontrol kapalı"
        }

    fun tweakById(id: String): Tweak? = tweaks.firstOrNull { it.id == id }

    private fun addLog(line: String) {
        val cal = Calendar.getInstance().apply { timeInMillis = clock() }
        fun two(n: Int) = n.toString().padStart(2, '0')
        logLines.add(
            "${two(cal.get(Calendar.HOUR_OF_DAY))}:${two(cal.get(Calendar.MINUTE))}:${two(cal.get(Calendar.SECOND))}  $line",
        )
        while (logLines.size > 200) logLines.removeAt(0)
    }

    fun clearLog() {
        logLines.clear()
        notifyChanged()
    }

    suspend fun init() {
        try {
            tweaks = catalog.loadTweaks()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tweaks = emptyList()
            addLog("✗ Tweak listesi okunamadı: $e")
        }
        notifyChanged()
        detect()
    }

    /**
     * Yetki yolunu yeniden bulur: önce kök, sonra Shizuku.
     *
     * Shizuku için kullanıcı BEKLETİLMEZ: servis çalışıyor ve izin verilmişse hemen "hazır"
     * sayılır; gerçek test komutu (`id`) arka planda koşar. Test başarısız olursa durum açıkça
     * "kapalı"ya döner ve neden gösterilir (başarısızlık asla başarı gibi kalmaz).
     */
    suspend fun detect(recheckRoot: Boolean = true) {
        phase = ControlPhase.Checking
        error = null
        verifying = false
        verifyGeneration++
        notifyChanged()

        if (root.hasRoot(forceRecheck = recheckRoot)) {
            path = AccessPath.Root
            identity = root.identity?.label ?: "root"
            phase = ControlPhase.Ready
            addLog("✓ Kök erişimi bulundu.")
            notifyChanged()
            scope.launch { refreshStatuses() }
            return
        }

        val running = shizuku.isRunning()
        val permitted = running && shizuku.hasPermission()
        shizukuRunning = running
        shizukuPermitted = permitted
        if (permitted) {
            path = AccessPath.Shizuku
            identity = shizuku.identity?.label ?: "Shizuku"
            phase = ControlPhase.Ready
            verifying = true
            val generation = verifyGeneration
            addLog("✓ Shizuku izni var; komut servisi arka planda doğrulanıyor.")
            notifyChanged()
            scope.launch { verifyShizuku(generation) }
        } else {
            path = AccessPath.None
            identity = ""
            phase = ControlPhase.NeedsSetup
            error = if (!running) "Shizuku servisi çalışmıyor." else "Shizuku izni verilmedi."
            notifyChanged()
        }
    }

    /** Shizuku'nun gerçekten komut çalıştırdığını arka planda kanıtlar. */
    private suspend fun verifyShizuku(generation: Int) {
        val probe = try {
            shizuku.probe()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ShizukuProbe(running = true, permitted = true, error = "Doğrulama başarısız: ${e.message ?: e.javaClass.simpleName}")
        }
        // Bu arada yeniden denetim yapıldıysa eski sonuç yok sayılır.
        if (generation != verifyGeneration) return
        verifying = false
        if (probe.ready) {
            identity = probe.identity?.label ?: "shell"
            addLog("✓ Shizuku doğrulandı ($identity).")
            notifyChanged()
            refreshStatuses()
        } else {
            path = AccessPath.None
            identity = ""
            phase = ControlPhase.NeedsSetup
            shizukuRunning = probe.running
            shizukuPermitted = probe.permitted
            error = probe.error
            addLog("✗ Shizuku doğrulanamadı: ${probe.error ?: "bilinmeyen neden"}")
            notifyChanged()
        }
    }

    /**
     * "Tam kontrolü etkinleştir" düğmesi: o an yapılabilecek bir SONRAKİ adımı kendisi yapar
     * (izin ister) ve ardından yeniden denetler.
     */
    suspend fun setUp() {
        if (settingUp) return
        settingUp = true
        notifyChanged()
        try {
            detect()
            if (!ready && shizukuRunning && !shizukuPermitted) {
                val granted = shizuku.requestPermission()
                addLog(if (granted) "✓ Shizuku izni verildi." else "✗ Shizuku izni verilmedi.")
                detect(recheckRoot = false)
            }
        } finally {
            settingUp = false
            notifyChanged()
        }
    }

    suspend fun exec(command: String): ShellResult = when (path) {
        AccessPath.Root -> root.exec(command)
        AccessPath.Shizuku -> shizuku.exec(command)
        AccessPath.None -> throw ControlUnavailableException("Tam kontrol etkin değil.")
    }

    suspend fun refreshStatuses() {
        if (!ready) return
        for (tweak in tweaks) {
            if (!tweak.hasCheck) continue
            readStatus(tweak)
            if (!ready) return
        }
        notifyChanged()
    }

    private suspend fun readStatus(tweak: Tweak) {
        try {
            val result = exec(tweak.checkCommand!!)
            statuses[tweak.id] = TweakStatus(stateFromCheckResult(expected = tweak.expectedOutput!!, result = result))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            statuses[tweak.id] = TweakStatus(TweakState.Unknown)
        }
    }

    /** Tek bir komutu çalıştırır; sonucu günlüğe yazar ve sade bir sonuç döner. */
    suspend fun shell(label: String, command: String): TweakOutcome {
        if (!ready) return TweakOutcome(false, "Tam kontrol etkin değil.")
        return try {
            val result = exec(command)
            val reason = failureReason(result)
            for (line in outputLines(result.output).take(6)) addLog("  $line")
            if (reason != null) {
                addLog("✗ $label: $reason")
                TweakOutcome(false, "$label başarısız: $reason")
            } else {
                addLog("✓ $label")
                TweakOutcome(true, "$label tamamlandı")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ShizukuUnavailableException) {
            addLog("✗ $label: ${e.reason}")
            TweakOutcome(false, e.reason)
        } catch (e: ControlUnavailableException) {
            TweakOutcome(false, e.reason)
        } catch (e: Exception) {
            addLog("✗ $label: $e")
            TweakOutcome(false, "$label çalıştırılamadı.")
        }
    }

    private suspend fun runTweak(tweak: Tweak, apply: Boolean): TweakOutcome {
        val command = if (apply) tweak.applyCommand else tweak.revertCommand
        if (command.isNullOrEmpty()) return TweakOutcome(false, "Bu işlem geri alınamaz.")
        addLog("› ${tweak.title} ${if (apply) "" else "(geri al)"}")
        val outcome = shell(tweak.title, command)
        if (outcome.ok && tweak.hasCheck) {
            readStatus(tweak)
            // Komut "başarılı" döndü ama değer değişmediyse bunu gizleme.
            val wanted = if (apply) TweakState.On else TweakState.Off
            val actual = stateOf(tweak.id)
            if (actual != TweakState.Unknown && actual != wanted) {
                val msg = "Komut çalıştı ama cihaz ayarı değişmedi (üretici kısıtlıyor olabilir)."
                addLog("✗ ${tweak.title}: $msg")
                return TweakOutcome(false, msg)
            }
        }
        return outcome
    }

    /** Tek bir tweak'i açar/kapatır (ya da tek seferlik eylemi çalıştırır). */
    suspend fun setTweak(tweak: Tweak, apply: Boolean): TweakOutcome {
        if (!ready) return TweakOutcome(false, "Önce tam kontrolü etkinleştirin.")
        working.add(tweak.id)
        notifyChanged()
        try {
            return runTweak(tweak, apply)
        } finally {
            working.remove(tweak.id)
            notifyChanged()
        }
    }

    /** Bir profilin tüm ayarlarını sırayla uygular ya da geri alır. */
    suspend fun runProfile(profile: OptimizationProfile, apply: Boolean): ProfileResult {
        if (!ready || busy) return ProfileResult(applied = 0, skipped = 0, failed = 0)
        busyLabel = "${profile.title} ${if (apply) "uygulanıyor" else "geri alınıyor"}…"
        notifyChanged()
        var applied = 0
        var skipped = 0
        var failed = 0
        try {
            for (id in profile.tweakIds) {
                val tweak = tweakById(id) ?: continue
                if (!apply && !tweak.isToggle) continue
                val state = stateOf(id)
                if (tweak.isToggle) {
                    if (apply && state == TweakState.On) {
                        skipped++
                        continue
                    }
                    if (!apply && state == TweakState.Off) {
                        skipped++
                        continue
                    }
                }
                working.add(id)
                notifyChanged()
                val outcome = runTweak(tweak, apply)
                working.remove(id)
                if (outcome.ok) applied++ else failed++
                notifyChanged()
            }
        } finally {
            busyLabel = null
            working.clear()
            notifyChanged()
        }
        return ProfileResult(applied = applied, skipped = skipped, failed = failed)
    }

    /** Bir profilde kaç ayar şu an AÇIK (rozet için). */
    fun activeCount(profile: OptimizationProfile): Int =
        profile.tweakIds.count { id ->
            val t = tweakById(id)
            t != null && t.isToggle && stateOf(id) == TweakState.On
        }

    fun toggleCount(profile: OptimizationProfile): Int =
        profile.tweakIds.count { id -> tweakById(id)?.isToggle == true }

    // ---- Hızlı eylemler (Ana Sayfa) ------------------------------------

    /** RAM'i boşaltır: arka plan uygulamalarını sonlandırır. */
    suspend fun freeMemory(): TweakOutcome = shell("Bellek temizliği", "am kill-all")

    /** Tüm uygulamaların önbelleğini temizler. */
    suspend fun trimAllCaches(): TweakOutcome = shell("Önbellek temizliği", "pm trim-caches 999999999999")

    // ---- Uygulama eylemleri (Uygulamalar sayfası) -----------------------

    suspend fun forceStop(pkg: String) = pkgAction("Durdur", pkg) { "am force-stop $it" }
    suspend fun clearAppCache(pkg: String) = pkgAction("Önbellek temizle", pkg) { "pm clear --cache-only $it" }
    suspend fun freeze(pkg: String) = pkgAction("Dondur", pkg) { "pm disable-user --user 0 $it" }
    suspend fun unfreeze(pkg: String) = pkgAction("Etkinleştir", pkg) { "pm enable $it" }
    suspend fun uninstallForUser(pkg: String) = pkgAction("Kaldır", pkg) { "pm uninstall --user 0 $it" }

    private suspend fun pkgAction(label: String, pkg: String, build: (String) -> String): TweakOutcome {
        if (!isValidPackageName(pkg)) return TweakOutcome(false, "Geçersiz paket adı.")
        return shell("$label: $pkg", build(pkg))
    }
}
