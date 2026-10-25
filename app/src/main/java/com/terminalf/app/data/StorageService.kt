package com.terminalf.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Tek bir depolama okuması. */
data class StorageReading(val totalBytes: Long, val cacheBytes: Long, val path: String) {
    companion object {
        val Empty = StorageReading(totalBytes = 0, cacheBytes = 0, path = "-")
    }
}

/**
 * Uygulamanın kendi klasörlerinin boyutunu ölçer ve önbelleği temizler. Yalnızca izlemekle
 * kalmaz; [clearCache] ile gerçek bir yönetim (temizleme) eylemi de sunar.
 */
class StorageService(context: Context) {
    private val appContext = context.applicationContext

    suspend fun read(): StorageReading = withContext(Dispatchers.IO) {
        try {
            val dir = appContext.filesDir
            val bytes = dirSize(dir)
            var cache = 0L
            try {
                cache = cacheDirs().sumOf { dirSize(it) }
            } catch (_: Exception) {
                // Geçici klasör okunamazsa 0 olarak bırak, toplam okumayı bozma.
            }
            StorageReading(totalBytes = bytes, cacheBytes = cache, path = dir.path)
        } catch (_: Exception) {
            StorageReading.Empty
        }
    }

    /**
     * Uygulamanın geçici (temizlenebilir) klasörlerindeki tüm dosya ve alt klasörleri siler.
     * Kalıcı uygulama verilerine (dosya klasörü, tercihler) DOKUNMAZ - yalnızca yeniden
     * üretilebilir önbellek kaldırılır. Silinen toplam bayt miktarını döndürür.
     */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (dir in cacheDirs()) {
            try {
                val children = dir.listFiles() ?: continue
                for (entity in children) {
                    try {
                        val size = if (entity.isDirectory) dirSize(entity) else entity.length()
                        val ok = if (entity.isDirectory) entity.deleteRecursively() else entity.delete()
                        if (ok) freed += size
                    } catch (_: Exception) {
                        // Tek bir dosya/klasör silinemiyorsa atla, diğerlerine devam et.
                    }
                }
            } catch (_: Exception) {
            }
        }
        freed
    }

    private fun cacheDirs(): List<File> = listOfNotNull(appContext.cacheDir, appContext.externalCacheDir)

    private fun dirSize(dir: File): Long {
        var total = 0L
        try {
            val stack = ArrayDeque<File>()
            stack.add(dir)
            while (stack.isNotEmpty()) {
                val d = stack.removeLast()
                val children = d.listFiles() ?: continue
                for (c in children) {
                    // Sembolik bağları izleme.
                    if (c.isDirectory && c.canonicalPath == c.absolutePath) {
                        stack.add(c)
                    } else if (c.isFile) {
                        total += c.length()
                    }
                }
            }
        } catch (_: Exception) {
            // Klasör listelenemedi; elde ne varsa onu döndür.
        }
        return total
    }
}
