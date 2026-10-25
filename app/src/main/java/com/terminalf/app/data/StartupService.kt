package com.terminalf.app.data

import com.terminalf.app.state.SettingsStore
import com.terminalf.app.state.getBrightness
import com.terminalf.app.state.getKeepScreenOn

/**
 * Uygulama açılırken kayıtlı tercihleri (ekranı açık tut, parlaklık) uygular. Cihaz sayfası hiç
 * açılmasa bile geçerli olur. Etkinlik her yeniden oluşturulduğunda (örn. ekran döndürme) çağrılır.
 */
fun applySavedPreferences(store: SettingsStore, bridge: ActivityBridge, brightness: BrightnessService) {
    try {
        if (store.getKeepScreenOn()) bridge.setKeepScreenOn(true)
    } catch (_: Exception) {
    }
    try {
        val saved = store.getBrightness()
        if (saved != null) brightness.setBrightness(saved)
    } catch (_: Exception) {
    }
}
