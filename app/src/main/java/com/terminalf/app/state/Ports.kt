package com.terminalf.app.state

import com.terminalf.app.core.AlertEvent
import com.terminalf.app.core.BatteryState
import com.terminalf.app.core.DiskInfo
import com.terminalf.app.core.MemInfo
import com.terminalf.app.core.ShellIdentity
import com.terminalf.app.core.ShellResult
import com.terminalf.app.core.Tweak
import kotlin.time.Duration

/*
 * Mantık katmanının dış dünyayla konuştuğu arayüzler ("portlar"). Gerçek (Android) uygulamaları
 * `data` paketindedir; birim testleri bunların sahtelerini kullanır.
 */

/** Kök (root) yetkisiyle komut çalıştırma. Cihaz köklü değilse sessizce "kök yok" der. */
interface RootAccess {
    val identity: ShellIdentity?

    /** `su -c id` ile kök erişimini dener; sonuç önbelleğe alınır. */
    suspend fun hasRoot(forceRecheck: Boolean = false): Boolean

    suspend fun exec(command: String): ShellResult
}

/**
 * Bir komut, Shizuku servisi çalışmıyorken, izin verilmemişken ya da test komutu (`id`)
 * başarısız olmuşken çalıştırılmaya çalışıldığında fırlatılır.
 */
class ShizukuUnavailableException(val reason: String) : Exception(reason)

/** Shizuku yoklama sonucu: servis çalışıyor mu, izin var mı ve test komutu (`id`) hangi kimlikle çalıştı. */
data class ShizukuProbe(
    val running: Boolean,
    val permitted: Boolean,
    /** Test komutunun çalıştığı kimlik (`shell` ya da `root`). Yalnızca test başarılıysa doludur. */
    val identity: ShellIdentity? = null,
    /** Hazır değilse nedeni (kullanıcıya gösterilir). */
    val error: String? = null,
) {
    /** Servis çalışıyor, izin verilmiş VE test komutu başarılı. */
    val ready: Boolean get() = running && permitted && identity != null
}

/** [RootAccess] ile aynı imzaya sahip, KÖK GEREKTİRMEYEN ikinci yol: Shizuku. */
interface ShizukuAccess {
    val identity: ShellIdentity?

    suspend fun probe(): ShizukuProbe

    suspend fun isRunning(): Boolean

    suspend fun hasPermission(): Boolean

    suspend fun requestPermission(): Boolean

    /** Hazır değilse [ShizukuUnavailableException] fırlatır. */
    suspend fun exec(command: String): ShellResult
}

/** Paketle gelen yapılandırma (tweak listesi). */
interface TweakCatalog {
    suspend fun loadTweaks(): List<Tweak>
}

/**
 * Kalıcı anahtar-değer deposu. Android'de SharedPreferences, testlerde bellek içi sahte.
 * Kasıtlı olarak küçük tutuldu: yalnızca gerçekten işe yarayan şeyler saklanır.
 */
interface SettingsStore {
    fun readBool(key: String, fallback: Boolean): Boolean
    fun writeBool(key: String, value: Boolean)
    fun readDouble(key: String, fallback: Double): Double
    fun writeDouble(key: String, value: Double)
    fun readString(key: String, fallback: String): String
    fun writeString(key: String, value: String)
    fun readInt(key: String, fallback: Int): Int
    fun writeInt(key: String, value: Int)
    fun contains(key: String): Boolean
    fun remove(key: String)
}

object SettingsKeys {
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val BRIGHTNESS = "app_brightness"
    const val SHOW_SYSTEM_APPS = "show_system_apps"
}

fun SettingsStore.getKeepScreenOn(): Boolean = readBool(SettingsKeys.KEEP_SCREEN_ON, false)
fun SettingsStore.setKeepScreenOn(value: Boolean) = writeBool(SettingsKeys.KEEP_SCREEN_ON, value)

/** Kayıtlı uygulama parlaklığı (0-1); kayıt yoksa `null`. */
fun SettingsStore.getBrightness(): Double? =
    if (contains(SettingsKeys.BRIGHTNESS)) readDouble(SettingsKeys.BRIGHTNESS, 1.0) else null

fun SettingsStore.setBrightness(value: Double) = writeDouble(SettingsKeys.BRIGHTNESS, value.coerceIn(0.0, 1.0))
fun SettingsStore.clearBrightness() = remove(SettingsKeys.BRIGHTNESS)
fun SettingsStore.getShowSystemApps(): Boolean = readBool(SettingsKeys.SHOW_SYSTEM_APPS, false)
fun SettingsStore.setShowSystemApps(value: Boolean) = writeBool(SettingsKeys.SHOW_SYSTEM_APPS, value)

/** Bildirim gönderen katmanın arayüzü (testlerde sahtesi kullanılır). */
interface Notifier {
    suspend fun init()
    suspend fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean

    /** Bir işlem bitince gönderilir. Uygulama ön plandayken gönderilmez (sonuç zaten ekranda). */
    suspend fun showTask(title: String, body: String)

    /** Sağlık uyarısı (ısınma, depolama, pil, bellek). */
    suspend fun showAlert(event: AlertEvent)

    /** Kullanıcının "bildirimi dene" düğmesi için: ön planda da gösterilir. */
    suspend fun showTest()
}

/** Tek bir pil okumasının anlık sonucu. */
data class BatteryReading(
    /** Cihaz pil yüzdesini okuyamazsa `null`. */
    val percent: Int?,
    val state: BatteryState,
    /** Deşarj/şarj hızına göre kaba kalan süre tahmini; yeterli veri yoksa `null`. */
    val estimatedRemaining: Duration? = null,
    /** Pilin anlık sıcaklığı (°C); okunamazsa `null`. */
    val temperatureCelsius: Double? = null,
) {
    companion object {
        val Unknown = BatteryReading(percent = null, state = BatteryState.Unknown)
    }
}

/** Pil okuyucu arayüzü: testlerde platform kanalı açmadan sahte okuyucu verilebilsin diye ayrıldı. */
interface BatteryReader {
    suspend fun read(): BatteryReading
    fun dispose()
}

/**
 * Cihazın GERÇEK bellek, depolama ve çalışma süresi bilgisi. Bir kalem okunamazsa `null` kalır,
 * uydurma değer üretilmez.
 */
data class SystemStats(
    val memory: MemInfo? = null,
    val disk: DiskInfo? = null,
    val uptime: Duration? = null,
) {
    companion object {
        val Empty = SystemStats()
    }
}

interface SystemStatsReader {
    suspend fun read(): SystemStats
}

/** Dokunsal geri bildirim türleri (Flutter `HapticFeedback` karşılıkları). */
enum class HapticKind { Tap, Success, Warning }

/** Android tarafı bu arayüzü uygular (bkz. `ui`); cihaz desteklemiyorsa sessizce yok sayar. */
fun interface HapticPerformer {
    fun perform(kind: HapticKind)
}

/** Küçük dokunsal geri bildirimler. Ayarlardan kapatılabilir. */
object Haptics {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var performer: HapticPerformer? = null

    fun tap() = run(HapticKind.Tap)
    fun success() = run(HapticKind.Success)
    fun warning() = run(HapticKind.Warning)

    private fun run(kind: HapticKind) {
        if (!enabled) return
        try {
            performer?.perform(kind)
        } catch (_: Exception) {
            // Donanım yoksa sessizce yok say.
        }
    }
}

/**
 * Uygulama animasyonlarının tek anahtarı. Kullanıcı Görünüm sayfasından kapatabilir; sistem
 * "animasyonları kaldır" ayarı açıksa da animasyon oynatılmaz (bkz. arayüz tarafı).
 */
object Motion {
    @Volatile
    var enabled: Boolean = true

    /** Animasyon kapalıysa süreyi sıfırlar. */
    fun d(millis: Int): Int = if (enabled) millis else 0
}
