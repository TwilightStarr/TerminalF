package com.terminalf.app.data

import android.app.Activity
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * Etkinlik (Activity) gerektiren birkaç işlem için köprü: ekranı açık tutma, uygulama parlaklığı
 * ve bildirim izni isteği. `MainActivity` kendini buraya bağlar; etkinlik yokken çağrılar sessizce
 * etkisiz kalır.
 */
class ActivityBridge {
    @Volatile
    private var ref: WeakReference<Activity>? = null

    /** `MainActivity` bildirim izni isteğini (sonucu bekleyerek) buraya bağlar. */
    @Volatile
    var notificationPermissionRequester: (suspend () -> Boolean)? = null

    val activity: Activity? get() = ref?.get()

    fun attach(activity: Activity) {
        ref = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (ref?.get() === activity) {
            ref = null
            notificationPermissionRequester = null
        }
    }

    /** Ekranı açık tut (wakelock yerine pencere bayrağı: ek izin gerektirmez). */
    fun setKeepScreenOn(on: Boolean): Boolean {
        val a = activity ?: return false
        a.runOnUiThread {
            if (on) {
                a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        return true
    }
}
