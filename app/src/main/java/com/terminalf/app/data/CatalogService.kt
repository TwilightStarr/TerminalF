package com.terminalf.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.terminalf.app.core.FeatureShortcut
import com.terminalf.app.core.RecommendedApp
import com.terminalf.app.core.Tweak
import com.terminalf.app.core.appsFromJsonString
import com.terminalf.app.core.featuresFromJsonString
import com.terminalf.app.core.tweaksFromJsonString
import com.terminalf.app.state.TweakCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Paketle gelen yapılandırma dosyalarını (tweak'ler, önerilen uygulamalar, sistem kısayolları)
 * okur ve kısayolları/bağlantıları açar. Kök ya da Shizuku GEREKTİRMEZ.
 */
class CatalogService(context: Context) : TweakCatalog {
    private val appContext = context.applicationContext

    private suspend fun asset(name: String): String = withContext(Dispatchers.IO) {
        appContext.assets.open("config/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override suspend fun loadTweaks(): List<Tweak> = tweaksFromJsonString(asset("tweaks.json"))

    suspend fun loadApplications(): List<RecommendedApp> = appsFromJsonString(asset("applications.json"))

    suspend fun loadFeatures(): List<FeatureShortcut> = featuresFromJsonString(asset("features.json"))

    fun openFeatureSettings(feature: FeatureShortcut) {
        try {
            appContext.startActivity(Intent(actionOf(feature.settingsType)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Cihaz bu ekranı desteklemiyorsa sessizce yok say.
        }
    }

    private fun actionOf(value: String): String = when (value) {
        "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        "batteryOptimization" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "internalStorage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        "security" -> Settings.ACTION_SECURITY_SETTINGS
        "display" -> Settings.ACTION_DISPLAY_SETTINGS
        else -> Settings.ACTION_SETTINGS
    }

    /** Bir bağlantıyı (mağaza, GitHub, Shizuku sayfası) tarayıcıda açar. */
    fun openUrl(url: String): Boolean = try {
        appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
