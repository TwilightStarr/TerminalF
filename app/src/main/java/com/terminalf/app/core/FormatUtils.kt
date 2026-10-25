package com.terminalf.app.core

import java.util.Locale
import kotlin.time.Duration

/**
 * Sabit noktalı biçim. Her zaman nokta ayraçlıdır: Türkçe yerelde `String.format` virgül
 * üretir ve "1,5 MB" gibi karışık çıktılar oluşur.
 */
fun fixed(value: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", value)

/** Bayt değerini okunabilir B / KB / MB / GB birimine çevirir. */
fun formatBytes(bytes: Number): String {
    val b = bytes.toDouble()
    if (b < 1024) return "${b.toLong()} B"
    if (b < 1024.0 * 1024) return "${fixed(b / 1024, 1)} KB"
    if (b < 1024.0 * 1024 * 1024) return "${fixed(b / (1024.0 * 1024), 1)} MB"
    return "${fixed(b / (1024.0 * 1024 * 1024), 2)} GB"
}

/** Bir süreyi "1 sa 24 dk" / "42 dk" / "< 1 dk" biçimine çevirir (pil kalan-süre tahmini için). */
fun formatDuration(d: Duration): String {
    val totalMinutes = d.inWholeMinutes
    if (totalMinutes < 1) return "< 1 dk"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    if (hours <= 0) return "$minutes dk"
    if (minutes == 0L) return "$hours sa"
    return "$hours sa $minutes dk"
}

/** Saniyeyi "42 sn" / "7 dk" / "1 sa 5 dk" biçimine çevirir (oyun oturumu süresi). */
fun formatSeconds(seconds: Int): String {
    if (seconds < 60) return "$seconds sn"
    val m = seconds / 60
    if (m < 60) return "$m dk"
    return "${m / 60} sa ${m % 60} dk"
}
