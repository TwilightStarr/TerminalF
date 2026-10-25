package com.terminalf.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cihazda kurulu, başlatılabilir bir uygulama. */
data class InstalledApp(
    val name: String,
    val packageName: String,
    val versionName: String?,
    val isSystem: Boolean,
    val icon: ImageBitmap?,
)

/**
 * Yüklü uygulamaları listeleyen ve (yetki gerektirmeyen) temel eylemleri yapan servis. Yetki
 * gerektiren eylemler (zorla durdur, dondur, kaldır) `ControlController` üzerinden çalışır.
 */
class AppsService(context: Context) {
    private val appContext = context.applicationContext
    private val pm: PackageManager get() = appContext.packageManager

    suspend fun list(includeSystemApps: Boolean): List<InstalledApp> = withContext(Dispatchers.Default) {
        try {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            val result = ArrayList<InstalledApp>()
            for (info in pm.queryIntentActivities(launcher, 0)) {
                val ai = info.activityInfo?.applicationInfo ?: continue
                val pkg = ai.packageName
                if (!seen.add(pkg)) continue
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem && !includeSystemApps) continue
                val name = try {
                    ai.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }
                val version = try {
                    pm.getPackageInfo(pkg, 0).versionName
                } catch (_: Exception) {
                    null
                }
                val icon = try {
                    ai.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                } catch (_: Exception) {
                    null
                }
                result.add(InstalledApp(name, pkg, version, isSystem, icon))
            }
            result.sortBy { it.name.lowercase() }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun launch(packageName: String) {
        try {
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Uygulama yoksa ya da başlatılamıyorsa sessizce yok say.
        }
    }

    /** Sistemin "Uygulama Bilgisi" ekranı (Zorla Durdur düğmesi burada). */
    fun openSystemSettings(packageName: String) {
        try {
            appContext.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }

    /** Sistem onay diyaloğuyla kaldırma ekranını açar (yetki gerekmez). Açılabildiyse `true`. */
    fun uninstall(packageName: String): Boolean = try {
        appContext.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val ICON_PX = 96
    }
}
