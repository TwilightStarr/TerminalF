package com.terminalf.app.state

import com.terminalf.app.core.AlertThresholds
import com.terminalf.app.core.AccentStyle
import com.terminalf.app.core.AppLanguage
import com.terminalf.app.core.AppPalette
import com.terminalf.app.core.AppPalettes
import com.terminalf.app.core.I18n
import com.terminalf.app.core.LogoStyle
import com.terminalf.app.core.logoColorsFor
import kotlin.math.roundToInt

/**
 * Kullanıcı tercihlerinin bellekteki (dinlenebilir) hâli. Bir kez diskten okunur; değişiklikler
 * hem arayüze hem diske yansır.
 */
class AppSettings(private val store: SettingsStore) : Observable() {

    companion object {
        const val ONBOARDED_KEY = "onboarded"
        private const val TASK_NOTIF_KEY = "notif_tasks"
        private const val ALERTS_KEY = "notif_alerts"
        private const val HAPTICS_KEY = "haptics"
        private const val TEMP_KEY = "alert_temp"
        private const val STORAGE_KEY = "alert_storage"
        private const val BATTERY_KEY = "alert_battery"
        private const val MEMORY_KEY = "alert_memory"
        private const val ANIM_KEY = "animations"
        private const val LANGUAGE_KEY = "language"
        private const val THEME_KEY = "theme_id"
        private const val LOGO_KEY = "logo_id"
        private const val LOGO_CUSTOM_KEY = "logo_custom"
        private const val AMOLED_KEY = "amoled"
        private const val COLORFUL_KEY = "colorful"
        private const val ACCENT_KEY = "accent_id"
        private const val ACCENT_CUSTOM_KEY = "accent_custom"
        const val DEFAULT_LOGO_CUSTOM: Long = 0xFF5B9DFF
    }

    /**
     * İlk açılış karşılama sayfası görüldü mü? Okuma başarısız olursa karşılama gösterilmesin diye
     * `true` başlar.
     */
    var onboarded: Boolean = true
        private set

    /** Uygulama arka plandayken işlem sonucu bildirimi. */
    var taskNotifications: Boolean = true
        private set

    /** Isınma, depolama, pil, bellek uyarıları. */
    var healthAlerts: Boolean = true
        private set

    var haptics: Boolean = true
        private set

    /** Arayüz dili. İlk açılışta İngilizce; seçim diske yazılır ve [I18n.language] ile eşitlenir. */
    var language: AppLanguage = AppLanguage.DEFAULT
        private set

    /** Arayüz animasyonları (geçişler, belirme efektleri). */
    var animations: Boolean = true
        private set

    /** Seçili tema (varsayılan: siyah-beyaz) ve logo rengi. */
    var themeId: String = AppPalettes.DEFAULT_ID
        private set
    var logoId: String = LogoStyle.AUTO_ID
        private set
    var logoCustom: Long = DEFAULT_LOGO_CUSTOM
        private set

    /** Koyu temalarda zemin gerçek siyah (AMOLED'de piksel kapanır). */
    var amoled: Boolean = true
        private set

    /** Açık: her kutu/düğme kendi rengini alır. Kapalı: her şey tek vurgu rengiyle çizilir. */
    var colorful: Boolean = true
        private set

    /** Vurgu rengi: "theme" (temanın kendi rengi), hazır renk kimliği ya da "custom". */
    var accentId: String = AccentStyle.THEME_ID
        private set
    var accentCustom: Long = AccentStyle.DEFAULT_CUSTOM
        private set

    var temperatureC: Double = 42.0
        private set
    var storagePercent: Int = 90
        private set
    var batteryPercent: Int = 15
        private set
    var memoryPercent: Int = 92
        private set

    /** Görünüm değişti mi anlamak için kısa imza. */
    val appearanceKey: String
        get() = "$themeId|$amoled|$colorful|$accentId|$accentCustom|$logoId|$logoCustom"

    val palette: AppPalette get() = AppPalettes.resolve(themeId, amoled, accentId, accentCustom)

    /** Logo çiziminde kullanılacak iki renk (0xAARRGGBB). */
    val logoColors: List<Long> get() = logoColorsFor(palette, logoId, logoCustom)

    val thresholds: AlertThresholds
        get() = AlertThresholds(
            temperatureC = temperatureC,
            storageUsedPercent = storagePercent,
            batteryPercent = batteryPercent,
            memoryUsedPercent = memoryPercent,
        )

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        try {
            onboarded = store.readBool(ONBOARDED_KEY, false)
            taskNotifications = store.readBool(TASK_NOTIF_KEY, true)
            healthAlerts = store.readBool(ALERTS_KEY, true)
            haptics = store.readBool(HAPTICS_KEY, true)
            animations = store.readBool(ANIM_KEY, true)
            language = AppLanguage.fromTag(store.readString(LANGUAGE_KEY, AppLanguage.DEFAULT.tag))
            temperatureC = store.readDouble(TEMP_KEY, 42.0).coerceIn(35.0, 50.0)
            storagePercent = store.readDouble(STORAGE_KEY, 90.0).roundToInt().coerceIn(70, 98)
            batteryPercent = store.readDouble(BATTERY_KEY, 15.0).roundToInt().coerceIn(5, 30)
            memoryPercent = store.readDouble(MEMORY_KEY, 92.0).roundToInt().coerceIn(80, 98)
            themeId = AppPalettes.byId(store.readString(THEME_KEY, AppPalettes.DEFAULT_ID)).id
            val savedLogo = store.readString(LOGO_KEY, LogoStyle.AUTO_ID)
            logoId = if (savedLogo == LogoStyle.CUSTOM_ID) savedLogo else LogoStyle.byId(savedLogo).id
            logoCustom = store.readString(LOGO_CUSTOM_KEY, DEFAULT_LOGO_CUSTOM.toString()).toLongOrNull()
                ?: DEFAULT_LOGO_CUSTOM
            amoled = store.readBool(AMOLED_KEY, true)
            colorful = store.readBool(COLORFUL_KEY, true)
            accentId = AccentStyle.normalize(store.readString(ACCENT_KEY, AccentStyle.THEME_ID))
            accentCustom = store.readString(ACCENT_CUSTOM_KEY, AccentStyle.DEFAULT_CUSTOM.toString()).toLongOrNull()
                ?: AccentStyle.DEFAULT_CUSTOM
        } catch (_: Exception) {
            // Tercihler okunamazsa varsayılanlarla devam et.
        }
        Haptics.enabled = haptics
        Motion.enabled = animations
        I18n.language = language
        notifyChanged()
    }

    /** Arayüz dilini değiştirir; arayüz yeni dille baştan çizilir (bkz. `AppRoot`). */
    fun setLanguage(value: AppLanguage) {
        if (language == value) return
        language = value
        I18n.language = value
        notifyChanged()
        safe { store.writeString(LANGUAGE_KEY, value.tag) }
    }

    private inline fun safe(write: () -> Unit) {
        try {
            write()
        } catch (_: Exception) {
            // Yazılamazsa bellekteki değer bu oturum için geçerli kalır.
        }
    }

    fun markOnboarded() {
        onboarded = true
        notifyChanged()
        safe { store.writeBool(ONBOARDED_KEY, true) }
    }

    fun setTaskNotifications(v: Boolean) {
        taskNotifications = v
        notifyChanged()
        safe { store.writeBool(TASK_NOTIF_KEY, v) }
    }

    fun setHealthAlerts(v: Boolean) {
        healthAlerts = v
        notifyChanged()
        safe { store.writeBool(ALERTS_KEY, v) }
    }

    fun setHaptics(v: Boolean) {
        haptics = v
        Haptics.enabled = v
        notifyChanged()
        safe { store.writeBool(HAPTICS_KEY, v) }
    }

    fun setTemperature(v: Double) {
        temperatureC = v.coerceIn(35.0, 50.0)
        notifyChanged()
        safe { store.writeDouble(TEMP_KEY, temperatureC) }
    }

    fun setStoragePercent(v: Int) {
        storagePercent = v.coerceIn(70, 98)
        notifyChanged()
        safe { store.writeDouble(STORAGE_KEY, storagePercent.toDouble()) }
    }

    fun setBatteryPercent(v: Int) {
        batteryPercent = v.coerceIn(5, 30)
        notifyChanged()
        safe { store.writeDouble(BATTERY_KEY, batteryPercent.toDouble()) }
    }

    fun setMemoryPercent(v: Int) {
        memoryPercent = v.coerceIn(80, 98)
        notifyChanged()
        safe { store.writeDouble(MEMORY_KEY, memoryPercent.toDouble()) }
    }

    fun setAnimations(v: Boolean) {
        animations = v
        Motion.enabled = v
        notifyChanged()
        safe { store.writeBool(ANIM_KEY, v) }
    }

    fun setTheme(id: String) {
        themeId = AppPalettes.byId(id).id
        notifyChanged()
        safe { store.writeString(THEME_KEY, themeId) }
    }

    fun setLogo(id: String) {
        logoId = if (id == LogoStyle.CUSTOM_ID) id else LogoStyle.byId(id).id
        notifyChanged()
        safe { store.writeString(LOGO_KEY, logoId) }
    }

    /** Özel logo rengi (0xAARRGGBB); seçildiği anda "Özel" moduna geçer. */
    fun setLogoCustom(argb: Long) {
        logoCustom = argb
        logoId = LogoStyle.CUSTOM_ID
        notifyChanged()
        safe {
            store.writeString(LOGO_CUSTOM_KEY, logoCustom.toString())
            store.writeString(LOGO_KEY, logoId)
        }
    }

    fun setAmoled(v: Boolean) {
        amoled = v
        notifyChanged()
        safe { store.writeBool(AMOLED_KEY, v) }
    }

    fun setColorful(v: Boolean) {
        colorful = v
        notifyChanged()
        safe { store.writeBool(COLORFUL_KEY, v) }
    }

    /** Hazır vurgu rengi ("theme" = temanın kendi rengi). */
    fun setAccent(id: String) {
        accentId = AccentStyle.normalize(id)
        notifyChanged()
        safe { store.writeString(ACCENT_KEY, accentId) }
    }

    /** Özel vurgu rengi (0xAARRGGBB); seçildiği anda "Özel" moduna geçer. */
    fun setAccentCustom(argb: Long) {
        accentCustom = argb
        accentId = AccentStyle.CUSTOM_ID
        notifyChanged()
        safe {
            store.writeString(ACCENT_CUSTOM_KEY, accentCustom.toString())
            store.writeString(ACCENT_KEY, accentId)
        }
    }

    /** Tüm görünüm tercihlerini varsayılana döndürür (animasyon ve titreşim ayarına dokunmaz). */
    fun resetAppearance() {
        themeId = AppPalettes.DEFAULT_ID
        amoled = true
        colorful = true
        accentId = AccentStyle.THEME_ID
        logoId = LogoStyle.AUTO_ID
        notifyChanged()
        safe {
            store.writeString(THEME_KEY, themeId)
            store.writeBool(AMOLED_KEY, true)
            store.writeBool(COLORFUL_KEY, true)
            store.writeString(ACCENT_KEY, accentId)
            store.writeString(LOGO_KEY, logoId)
        }
    }
}
