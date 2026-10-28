package com.terminalf.app.core

import kotlin.math.roundToInt

/**
 * Sağlık uyarılarının kuralları. Saf mantık: platforma bağlı değil.
 *
 * Her uyarı bir kez tetiklenir; durum düzelmeden (histerezis payı kadar) tekrar bildirim
 * gönderilmez, böylece sınırda dolaşan bir değer kullanıcıyı bildirim yağmuruna tutmaz.
 */

enum class AlertKind { Heat, LowStorage, LowBattery, HighMemory }

data class AlertThresholds(
    /** Pil sıcaklığı bu değere ulaşınca uyarılır (°C). */
    val temperatureC: Double = 42.0,
    /** Depolama doluluğu bu yüzdeye ulaşınca uyarılır. */
    val storageUsedPercent: Int = 90,
    /** Pil bu yüzdenin altına inince (şarjda değilken) uyarılır. */
    val batteryPercent: Int = 15,
    /** RAM doluluğu bu yüzdeye ulaşınca uyarılır. */
    val memoryUsedPercent: Int = 92,
)

data class AlertEvent(val kind: AlertKind, val title: String, val body: String)

data class AlertEvaluation(
    /** Şimdi gönderilecek yeni uyarılar. */
    val fire: List<AlertEvent>,
    /** Hâlâ geçerli olan (tekrar bildirilmeyecek) uyarılar. */
    val active: Set<AlertKind>,
)

/** Ölçümleri eşiklerle karşılaştırır. Okunamayan (`null`) bir ölçüm o uyarının durumunu değiştirmez. */
fun evaluateAlerts(
    thresholds: AlertThresholds,
    active: Set<AlertKind>,
    temperatureC: Double? = null,
    storageUsedRatio: Double? = null,
    batteryPercent: Int? = null,
    charging: Boolean = false,
    memoryUsedRatio: Double? = null,
): AlertEvaluation {
    val next = LinkedHashSet(active)
    val fire = ArrayList<AlertEvent>()

    fun apply(kind: AlertKind, trigger: Boolean?, clear: Boolean?, title: String, body: String) {
        if (trigger == null || clear == null) return // ölçüm yok
        if (kind !in next) {
            if (trigger) {
                next.add(kind)
                fire.add(AlertEvent(kind, title, body))
            }
        } else if (clear) {
            next.remove(kind)
        }
    }

    val t = temperatureC
    apply(
        kind = AlertKind.Heat,
        trigger = t?.let { it >= thresholds.temperatureC },
        clear = t?.let { it < thresholds.temperatureC - 3 },
        title = tr("Device is hot", "Cihaz ısındı"),
        body = tr(
            "Battery temperature is ${t?.let { fixed(it, 1) }} °C. Close heavy apps and give the device a break from charging or the screen.",
            "Pil sıcaklığı ${t?.let { fixed(it, 1) }} °C. Ağır uygulamaları kapatıp şarjı ya da ekranı bir süre bırakmak iyi olur.",
        ),
    )

    val sPct = storageUsedRatio?.let { it * 100 }
    apply(
        kind = AlertKind.LowStorage,
        trigger = sPct?.let { it >= thresholds.storageUsedPercent },
        clear = sPct?.let { it < thresholds.storageUsedPercent - 2 },
        title = tr("Storage is almost full", "Depolama dolmak üzere"),
        body = tr("Storage is ${sPct?.roundToInt()}% full. Clearing caches can free up space.", "Depolama %${sPct?.roundToInt()} dolu. Önbellek temizliği yer açabilir."),
    )

    val b = batteryPercent
    apply(
        kind = AlertKind.LowBattery,
        trigger = b?.let { !charging && it <= thresholds.batteryPercent },
        clear = b?.let { charging || it >= thresholds.batteryPercent + 3 },
        title = tr("Battery is low", "Pil azalıyor"),
        body = tr("Battery is at $b%. Turning on the Battery Saver profile extends battery life.", "Pil %$b. Pil Tasarrufu profilini açmak ömrü uzatır."),
    )

    val mPct = memoryUsedRatio?.let { it * 100 }
    apply(
        kind = AlertKind.HighMemory,
        trigger = mPct?.let { it >= thresholds.memoryUsedPercent },
        clear = mPct?.let { it < thresholds.memoryUsedPercent - 4 },
        title = tr("Memory is full", "Bellek dolu"),
        body = tr(
            "RAM is ${mPct?.roundToInt()}% full. Use Boost to close background apps.",
            "RAM %${mPct?.roundToInt()} dolu. Hızlandır ile arka plan uygulamalarını kapatabilirsiniz.",
        ),
    )

    return AlertEvaluation(fire = fire, active = next)
}
