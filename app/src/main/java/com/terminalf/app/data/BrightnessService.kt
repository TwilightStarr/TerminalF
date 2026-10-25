package com.terminalf.app.data

import android.provider.Settings
import android.view.WindowManager

/**
 * Ekran parlaklığını uygulama içinden ayarlama.
 *
 * Pencerenin (window) parlaklığı kullanılır: yalnızca TerminalF ön plandayken etkilidir ve
 * `WRITE_SETTINGS` gibi özel bir izin GEREKTİRMEZ. Sistem genelinde kalıcı parlaklık değiştirme
 * bilinçli olarak kapsam dışıdır.
 */
class BrightnessService(private val bridge: ActivityBridge) {

    /** Uygulamanın şu anki efektif parlaklığı (0.0 - 1.0); okunamazsa `null`. */
    fun current(): Double? {
        val a = bridge.activity ?: return null
        return try {
            val own = a.window.attributes.screenBrightness
            if (own >= 0f) {
                own.toDouble()
            } else {
                val sys = Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                (sys / 255.0).coerceIn(0.0, 1.0)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun setBrightness(value: Double): Boolean {
        val a = bridge.activity ?: return false
        return try {
            a.runOnUiThread {
                val lp = a.window.attributes
                // 0.0 tamamen karanlık olabileceği için en az %1.
                lp.screenBrightness = value.coerceIn(0.01, 1.0).toFloat()
                a.window.attributes = lp
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Uygulama-seviyesi parlaklığı sıfırlar; ekran sistemin genel değerine döner. */
    fun reset() {
        val a = bridge.activity ?: return
        try {
            a.runOnUiThread {
                val lp = a.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                a.window.attributes = lp
            }
        } catch (_: Exception) {
        }
    }
}
