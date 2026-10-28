package com.terminalf.app.core

import kotlin.math.abs

/**
 * Ana ekran (başlatıcı) simgesi seçimi. Android, çalışma anında rastgele renkli bir uygulama
 * simgesi üretemez; bu yüzden her hazır logo rengi için önceden hazırlanmış bir simge ve bir
 * `activity-alias` vardır. Seçilen logo (tema rengi / özel renk dahil) EN YAKIN hazır simgeye
 * eşlenir. Saf mantık: JVM'de test edilir.
 */
object LauncherIcons {
    /** Manifestteki alias adının son ekini ve simge kaynağını belirleyen anahtarlar. */
    val keys: List<String> = LogoStyle.presets.filter { it.colors.isNotEmpty() }.map { it.id }

    /** Varsayılan (kurulumdaki) simge: Gümüş (özgün logo). `tools/gen_launcher_icons.py` ile aynı. */
    const val DEFAULT_KEY = "silver"

    /** `silver` -> `LauncherSilver`, `g_ocean` -> `LauncherGOcean`. */
    fun aliasClassName(key: String): String =
        "com.terminalf.app.Launcher" + key.split('_').joinToString("") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }

    /** Verilen logo renklerine (0xAARRGGBB; 1 ya da 2 renk) en yakın hazır simgenin anahtarı. */
    fun keyFor(colors: List<Long>): String {
        if (colors.isEmpty()) return DEFAULT_KEY
        val a = colors[0]
        val b = colors.getOrElse(1) { a }
        var best = DEFAULT_KEY
        var bestDistance = Long.MAX_VALUE
        for (style in LogoStyle.presets) {
            if (style.colors.isEmpty()) continue
            val pa = style.colors[0]
            val pb = style.colors.getOrElse(1) { pa }
            val d = distance(a, pa) + distance(b, pb)
            if (d < bestDistance) {
                bestDistance = d
                best = style.id
            }
        }
        return best
    }

    private fun distance(x: Long, y: Long): Long {
        val dr = abs(((x shr 16) and 0xFF) - ((y shr 16) and 0xFF))
        val dg = abs(((x shr 8) and 0xFF) - ((y shr 8) and 0xFF))
        val db = abs((x and 0xFF) - (y and 0xFF))
        return dr * dr + dg * dg + db * db
    }
}
