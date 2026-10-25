package com.terminalf.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.terminalf.app.data.ActivityBridge
import com.terminalf.app.data.AppsService
import com.terminalf.app.data.BatteryService
import com.terminalf.app.data.BrightnessService
import com.terminalf.app.data.CatalogService
import com.terminalf.app.data.DeviceService
import com.terminalf.app.data.LauncherIconService
import com.terminalf.app.data.NetworkService
import com.terminalf.app.data.NotificationService
import com.terminalf.app.data.RootService
import com.terminalf.app.data.SharedPrefsStore
import com.terminalf.app.data.ShizukuService
import com.terminalf.app.data.StorageService
import com.terminalf.app.data.SystemStatsService
import com.terminalf.app.state.AlertMonitor
import com.terminalf.app.state.AppSettings
import com.terminalf.app.state.ControlController
import com.terminalf.app.state.GameModeController
import com.terminalf.app.state.LiveStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Uygulamanın tekil nesneleri (elle bağımlılık enjeksiyonu). `TerminalApplication` oluşturur;
 * `MainActivity` yeniden oluşturulsa bile bu nesneler (ve devam eden işler) korunur.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val activityBridge = ActivityBridge()
    val store = SharedPrefsStore(appContext)
    val settings = AppSettings(store)

    val catalog = CatalogService(appContext)
    val appsService = AppsService(appContext)
    val storage = StorageService(appContext)
    val device = DeviceService()
    val network = NetworkService(appContext)
    val brightness = BrightnessService(activityBridge)
    val notifier = NotificationService(appContext, activityBridge)
    val launcherIcons = LauncherIconService(appContext)

    val root = RootService()
    val shizuku = ShizukuService(appContext)
    val control = ControlController(root, shizuku, catalog, scope)
    val game = GameModeController(
        control = control,
        store = store,
        launcher = { pkg -> appsService.launch(pkg) },
    )

    val live = LiveStats(BatteryService(appContext), SystemStatsService(), scope)
    val monitor = AlertMonitor(live, notifier, settings, scope)

    private var started = false

    /** Tek seferlik başlatma (idempotent). */
    fun start() {
        if (started) return
        started = true
        settings.load()
        game.highEndDevice = try {
            val soc = if (android.os.Build.VERSION.SDK_INT >= 31) android.os.Build.SOC_MODEL else ""
            com.terminalf.app.core.isHighEndSoc(android.os.Build.HARDWARE, soc)
        } catch (_: Exception) {
            false
        }
        control.notifier = notifier
        control.taskNotificationsEnabled = { settings.taskNotifications }
        scope.launch { notifier.init() }
        scope.launch { control.init() }
        scope.launch { game.load() }
        live.start()
        monitor.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = live.onForeground()
            override fun onStop(owner: LifecycleOwner) {
                live.onBackground()
                // Ana ekran simgesi seçili logoya uysun (çalışırken değiştirmek uygulamayı kapatabilir).
                launcherIcons.apply(settings.logoColors)
            }
        })
    }
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer sağlanmadı") }
