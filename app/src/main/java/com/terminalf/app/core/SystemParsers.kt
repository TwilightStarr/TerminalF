package com.terminalf.app.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** `/proc/meminfo` ve `df` çıktılarını okunabilir sayılara çeviren saf yardımcılar. */

data class MemInfo(val totalBytes: Long, val availableBytes: Long) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceIn(0L, maxOf(totalBytes, 0L))
    val usedRatio: Double get() = if (totalBytes <= 0) 0.0 else usedBytes.toDouble() / totalBytes
}

/**
 * `MemTotal:  3800000 kB` biçimindeki satırlardan toplam ve kullanılabilir belleği okur.
 * Okunamazsa `null`.
 */
fun parseMemInfo(text: String): MemInfo? {
    fun read(key: String): Long? {
        val m = Regex("^$key:\\s*(\\d+)\\s*kB", RegexOption.MULTILINE).find(text) ?: return null
        val kb = m.groupValues[1].toLongOrNull() ?: return null
        return kb * 1024
    }

    val total = read("MemTotal")
    if (total == null || total <= 0) return null
    val available = read("MemAvailable") ?: read("MemFree") ?: return null
    return MemInfo(totalBytes = total, availableBytes = available)
}

data class DiskInfo(val totalBytes: Long, val usedBytes: Long) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceIn(0L, maxOf(totalBytes, 0L))
    val usedRatio: Double get() = if (totalBytes <= 0) 0.0 else usedBytes.toDouble() / totalBytes
}

/**
 * `df -k <yol>` çıktısının son veri satırından toplam/kullanılan boyutu okur (sütunlar:
 * Filesystem 1K-blocks Used Available Use% Mounted on).
 */
fun parseDfOutput(text: String): DiskInfo? {
    val lines = text.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 2) return null
    val cols = lines.last().split(Regex("\\s+"))
    if (cols.size < 4) return null
    val total = cols[1].toLongOrNull()
    val used = cols[2].toLongOrNull()
    if (total == null || used == null || total <= 0) return null
    return DiskInfo(totalBytes = total * 1024, usedBytes = used * 1024)
}

/** `/proc/uptime` ilk sayısı (saniye). */
fun parseUptime(text: String): Duration? {
    val first = text.trim().split(Regex("\\s+")).firstOrNull() ?: return null
    val seconds = first.toDoubleOrNull()
    if (seconds == null || seconds < 0) return null
    return Math.round(seconds).toInt().seconds
}
