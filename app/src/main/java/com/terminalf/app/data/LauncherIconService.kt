package com.terminalf.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.terminalf.app.core.LauncherIcons

/**
 * Ana ekran simgesini seçili logoya göre değiştirir: yalnızca seçilen rengin `activity-alias`'ı
 * etkin kalır. Uygulama arka plana geçtiğinde çağrılmalıdır (çalışırken değiştirmek bazı
 * başlatıcılarda uygulamayı kapatır).
 */
class LauncherIconService(context: Context) {
    private val appContext = context.applicationContext
    private val pm: PackageManager get() = appContext.packageManager

    private fun component(key: String) = ComponentName(appContext.packageName, LauncherIcons.aliasClassName(key))

    /** Şu an etkin olan simge anahtarı (hiçbiri yoksa varsayılan). */
    fun current(): String =
        LauncherIcons.keys.firstOrNull { isEnabled(it) } ?: LauncherIcons.DEFAULT_KEY

    private fun isEnabled(key: String): Boolean {
        val c = component(key)
        return when (pm.getComponentEnabledSetting(c)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // DEFAULT: manifestteki değer; yalnızca varsayılan simge manifestte etkin.
            else -> key == LauncherIcons.DEFAULT_KEY
        }
    }

    /** [logoColors] için en yakın simgeyi etkinleştirir. Değişiklik gerekmiyorsa hiçbir şey yapmaz. */
    fun apply(logoColors: List<Long>) {
        try {
            val target = LauncherIcons.keyFor(logoColors)
            if (current() == target && LauncherIcons.keys.count { isEnabled(it) } == 1) return
            // Önce yenisini aç, sonra eskileri kapat: bir an bile hiç simge kalmaz.
            pm.setComponentEnabledSetting(
                component(target),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for (key in LauncherIcons.keys) {
                if (key == target) continue
                pm.setComponentEnabledSetting(
                    component(key),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } catch (_: Exception) {
            // Başlatıcı simgesi değişmezse uygulama yine çalışır.
        }
    }
}
