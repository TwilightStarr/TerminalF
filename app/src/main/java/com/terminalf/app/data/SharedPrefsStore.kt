package com.terminalf.app.data

import android.content.Context
import android.content.SharedPreferences
import com.terminalf.app.state.SettingsStore

/**
 * SharedPreferences tabanlı kalıcı depo.
 *
 * GEÇİŞ: Önceki Flutter sürümü tercihlerini `FlutterSharedPreferences` dosyasında, anahtarları
 * `flutter.` önekiyle saklıyordu (ondalıklar `VGhpcy...` önekli metin olarak). Uygulama kimliği
 * aynı kaldığı için bu sürüm ilk açılışta o kayıtları bir kez içeri aktarır; böylece tema, oyun
 * ayarları, açık oyun oturumu (geri alma kaydı) ve diğer tercihler kaybolmaz.
 */
class SharedPrefsStore(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    init {
        migrateFlutterPrefs(context.applicationContext)
    }

    private fun migrateFlutterPrefs(context: Context) {
        if (prefs.getBoolean(MIGRATED_KEY, false)) return
        try {
            val legacy = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for ((rawKey, value) in legacy.all) {
                if (!rawKey.startsWith(LEGACY_PREFIX)) continue
                val key = rawKey.removePrefix(LEGACY_PREFIX)
                if (prefs.contains(key)) continue
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putLong(key, value.toLong())
                    is String -> {
                        if (value.startsWith(LEGACY_DOUBLE_PREFIX)) {
                            // Flutter ondalığı: önek + metin. Düz metin olarak saklanır.
                            editor.putString(key, value.removePrefix(LEGACY_DOUBLE_PREFIX))
                        } else {
                            editor.putString(key, value)
                        }
                    }
                    else -> Unit
                }
            }
            editor.putBoolean(MIGRATED_KEY, true)
            editor.apply()
        } catch (_: Exception) {
            // Geçiş başarısızsa varsayılanlarla devam; bir sonraki açılışta yeniden denenir.
        }
    }

    override fun readBool(key: String, fallback: Boolean): Boolean =
        (prefs.all[key] as? Boolean) ?: fallback

    override fun writeBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun readDouble(key: String, fallback: Double): Double = when (val v = prefs.all[key]) {
        is String -> v.toDoubleOrNull() ?: fallback
        is Number -> v.toDouble()
        else -> fallback
    }

    override fun writeDouble(key: String, value: Double) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    override fun readString(key: String, fallback: String): String = when (val v = prefs.all[key]) {
        is String -> v
        is Number -> v.toString()
        else -> fallback
    }

    override fun writeString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun readInt(key: String, fallback: Int): Int = when (val v = prefs.all[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: fallback
        else -> fallback
    }

    override fun writeInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val FILE = "terminalf_prefs"
        const val MIGRATED_KEY = "__migrated_flutter_prefs"
        const val LEGACY_FILE = "FlutterSharedPreferences"
        const val LEGACY_PREFIX = "flutter."
        const val LEGACY_DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
    }
}
