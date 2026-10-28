package com.terminalf.app.core

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Tema paletleri ve logo renkleri. Saf veri: renkler `0xAARRGGBB` olarak tutulur (Long), böylece
 * mantık katmanı arayüz kitaplığına bağlı kalmaz ve JVM'de test edilebilir. Compose tarafı bunları
 * `Color(long)` ile renge çevirir.
 */

/** Bir tema paleti: arka plan, yüzey, metin ve vurgu renklerinin tamamı. */
data class AppPalette(
    val id: String,
    val nameEn: String,
    val nameTr: String,
    val isDark: Boolean,
    val bg: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val outline: Long,
    val text: Long,
    val muted: Long,
    val primary: Long,
    val secondary: Long,
    val good: Long,
    val warn: Long,
    val danger: Long,
    /** Logonun zemini (uygulama ikonu gibi her zaman koyu). */
    val logoFrameA: Long,
    val logoFrameB: Long,
    /** "Tema rengi" seçiliyken kullanılan logo renkleri. */
    val logoA: Long,
    val logoB: Long,
) {
    /** Görünen ad (etkin dile göre). */
    val name: String get() = tr(nameEn, nameTr)
}

/** Logo karosunun düz zemin rengi (uygulama ikonuyla aynı). */
const val LOGO_TILE: Long = 0xFF0A0B0D

/** Hazır temalar. Varsayılan: [monoDark] (sade siyah). */
object AppPalettes {
    const val DEFAULT_ID = "mono_dark"

    val monoDark = AppPalette(
        id = "mono_dark",
        nameEn = "AMOLED Black",
        nameTr = "AMOLED Siyah",
        isDark = true,
        // Zemin gerçek siyah (AMOLED'de piksel kapanır); kartlar zeminden belli belirsiz ayrılır.
        // Vurgu canlı mavi -> mor; durum renkleri yeşil / sarı / kırmızı.
        bg = 0xFF000000,
        surface = 0xFF0B0B0E,
        surfaceHigh = 0xFF15151A,
        outline = 0xFF222229,
        text = 0xFFF4F4F6,
        muted = 0xFF8E8E99,
        primary = 0xFF5B9DFF,
        secondary = 0xFF9A7CFF,
        good = 0xFF34D399,
        warn = 0xFFFBBF24,
        danger = 0xFFF87171,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = LogoStyle.SILVER_TINT,
        logoB = LogoStyle.SILVER_TINT,
    )

    val monoLight = AppPalette(
        id = "mono_light",
        nameEn = "Light",
        nameTr = "Aydınlık",
        isDark = false,
        bg = 0xFFF6F6F8,
        surface = 0xFFFFFFFF,
        surfaceHigh = 0xFFEEEEF2,
        outline = 0xFFDEDEE4,
        text = 0xFF0A0A0C,
        muted = 0xFF6B6B76,
        primary = 0xFF2563EB,
        secondary = 0xFF7C3AED,
        good = 0xFF059669,
        warn = 0xFFD97706,
        danger = 0xFFDC2626,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = LogoStyle.SILVER_TINT,
        logoB = LogoStyle.SILVER_TINT,
    )

    val neon = AppPalette(
        id = "neon",
        nameEn = "Neon Teal",
        nameTr = "Neon Turkuaz",
        isDark = true,
        bg = 0xFF07090D,
        surface = 0xFF0F131A,
        surfaceHigh = 0xFF161B24,
        outline = 0xFF232A36,
        text = 0xFFF1F4F8,
        muted = 0xFF8A94A6,
        primary = 0xFF2DE2C0,
        secondary = 0xFF4D8BFF,
        good = 0xFF2DE2C0,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF2DE2C0,
        logoB = 0xFF4D8BFF,
    )

    val ocean = AppPalette(
        id = "ocean",
        nameEn = "Ocean",
        nameTr = "Okyanus",
        isDark = true,
        bg = 0xFF060A12,
        surface = 0xFF0D131F,
        surfaceHigh = 0xFF141C2C,
        outline = 0xFF212C42,
        text = 0xFFF0F5FF,
        muted = 0xFF8593AD,
        primary = 0xFF5AA9FF,
        secondary = 0xFF7C6CFF,
        good = 0xFF4ADE9B,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF5AA9FF,
        logoB = 0xFF7C6CFF,
    )

    val sunset = AppPalette(
        id = "sunset",
        nameEn = "Sunset",
        nameTr = "Gün Batımı",
        isDark = true,
        bg = 0xFF0C0807,
        surface = 0xFF16100E,
        surfaceHigh = 0xFF211815,
        outline = 0xFF362823,
        text = 0xFFFFF4EE,
        muted = 0xFFA8918A,
        primary = 0xFFFF8A4D,
        secondary = 0xFFFF4D8D,
        good = 0xFF6EE7A0,
        warn = 0xFFFFC84D,
        danger = 0xFFFF5C5C,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFFF8A4D,
        logoB = 0xFFFF4D8D,
    )

    val forest = AppPalette(
        id = "forest",
        nameEn = "Forest",
        nameTr = "Orman",
        isDark = true,
        bg = 0xFF060A08,
        surface = 0xFF0E1511,
        surfaceHigh = 0xFF151F19,
        outline = 0xFF23332A,
        text = 0xFFF1F8F3,
        muted = 0xFF86998C,
        primary = 0xFF4ADE80,
        secondary = 0xFF22D3EE,
        good = 0xFF4ADE80,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF4ADE80,
        logoB = 0xFF22D3EE,
    )

    val violet = AppPalette(
        id = "violet",
        nameEn = "Violet",
        nameTr = "Mor",
        isDark = true,
        bg = 0xFF09070F,
        surface = 0xFF110E1B,
        surfaceHigh = 0xFF191426,
        outline = 0xFF2B2440,
        text = 0xFFF6F2FF,
        muted = 0xFF9188AD,
        primary = 0xFFA78BFA,
        secondary = 0xFFF472B6,
        good = 0xFF4ADE9B,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFA78BFA,
        logoB = 0xFFF472B6,
    )

    val crimson = AppPalette(
        id = "crimson",
        nameEn = "Crimson",
        nameTr = "Kızıl",
        isDark = true,
        bg = 0xFF0B0607,
        surface = 0xFF150D0E,
        surfaceHigh = 0xFF201415,
        outline = 0xFF352125,
        text = 0xFFFFF2F3,
        muted = 0xFFA88B8E,
        primary = 0xFFFF4D5E,
        secondary = 0xFFFF8A4D,
        good = 0xFF4ADE80,
        warn = 0xFFFFC84D,
        danger = 0xFFFF8A8A,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFFF4D5E,
        logoB = 0xFFFF8A4D,
    )

    val amber = AppPalette(
        id = "amber",
        nameEn = "Amber",
        nameTr = "Altın",
        isDark = true,
        bg = 0xFF0A0805,
        surface = 0xFF141009,
        surfaceHigh = 0xFF1E1810,
        outline = 0xFF342B1B,
        text = 0xFFFFF8EB,
        muted = 0xFFA89A7E,
        primary = 0xFFFFC84D,
        secondary = 0xFFFF9F43,
        good = 0xFF6EE7A0,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFFFC84D,
        logoB = 0xFFFF9F43,
    )

    val ice = AppPalette(
        id = "ice",
        nameEn = "Ice",
        nameTr = "Buz",
        isDark = true,
        bg = 0xFF050A0D,
        surface = 0xFF0C141A,
        surfaceHigh = 0xFF131E26,
        outline = 0xFF1F3140,
        text = 0xFFEFFAFF,
        muted = 0xFF84A0AD,
        primary = 0xFF5FE3FF,
        secondary = 0xFF8AA8FF,
        good = 0xFF4ADE9B,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF5FE3FF,
        logoB = 0xFF8AA8FF,
    )

    val rose = AppPalette(
        id = "rose",
        nameEn = "Rose",
        nameTr = "Gül",
        isDark = true,
        bg = 0xFF0D0709,
        surface = 0xFF170E12,
        surfaceHigh = 0xFF22151B,
        outline = 0xFF3A2330,
        text = 0xFFFFF1F6,
        muted = 0xFFAD8A9A,
        primary = 0xFFFF7AB6,
        secondary = 0xFFC084FC,
        good = 0xFF4ADE9B,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFFF7AB6,
        logoB = 0xFFC084FC,
    )

    val nord = AppPalette(
        id = "nord",
        nameEn = "Nord",
        nameTr = "Nord",
        isDark = true,
        bg = 0xFF1B1F27,
        surface = 0xFF242933,
        surfaceHigh = 0xFF2E3440,
        outline = 0xFF3B4252,
        text = 0xFFECEFF4,
        muted = 0xFF8F9BB3,
        primary = 0xFF88C0D0,
        secondary = 0xFF81A1C1,
        good = 0xFFA3BE8C,
        warn = 0xFFEBCB8B,
        danger = 0xFFBF616A,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF88C0D0,
        logoB = 0xFF81A1C1,
    )

    val dracula = AppPalette(
        id = "dracula",
        nameEn = "Dracula",
        nameTr = "Drakula",
        isDark = true,
        bg = 0xFF191A21,
        surface = 0xFF21222C,
        surfaceHigh = 0xFF282A36,
        outline = 0xFF3A3C4E,
        text = 0xFFF8F8F2,
        muted = 0xFF9AA0C0,
        primary = 0xFFBD93F9,
        secondary = 0xFFFF79C6,
        good = 0xFF50FA7B,
        warn = 0xFFF1FA8C,
        danger = 0xFFFF5555,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFBD93F9,
        logoB = 0xFFFF79C6,
    )

    val matrix = AppPalette(
        id = "matrix",
        nameEn = "Matrix",
        nameTr = "Matrix",
        isDark = true,
        bg = 0xFF000000,
        surface = 0xFF060D08,
        surfaceHigh = 0xFF0C1710,
        outline = 0xFF15301E,
        text = 0xFFD6FFE3,
        muted = 0xFF6FA582,
        primary = 0xFF00FF66,
        secondary = 0xFF00C2A8,
        good = 0xFF00FF66,
        warn = 0xFFFFD84D,
        danger = 0xFFFF5C5C,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF00FF66,
        logoB = 0xFF00C2A8,
    )

    val sky = AppPalette(
        id = "sky",
        nameEn = "Sky",
        nameTr = "Gökyüzü",
        isDark = false,
        bg = 0xFFEEF4FB,
        surface = 0xFFFFFFFF,
        surfaceHigh = 0xFFE4ECF7,
        outline = 0xFFD3DEEE,
        text = 0xFF0B1626,
        muted = 0xFF5B6B82,
        primary = 0xFF1F6FEB,
        secondary = 0xFF7C5CFF,
        good = 0xFF138A5B,
        warn = 0xFFC77700,
        danger = 0xFFD62F2F,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF5AA9FF,
        logoB = 0xFF7C6CFF,
    )

    val mint = AppPalette(
        id = "mint",
        nameEn = "Mint",
        nameTr = "Nane",
        isDark = false,
        bg = 0xFFEEF7F2,
        surface = 0xFFFFFFFF,
        surfaceHigh = 0xFFE2F0E8,
        outline = 0xFFCFE3D8,
        text = 0xFF0B1F16,
        muted = 0xFF55705F,
        primary = 0xFF138A5B,
        secondary = 0xFF0E7490,
        good = 0xFF138A5B,
        warn = 0xFFC77700,
        danger = 0xFFD62F2F,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFF4ADE80,
        logoB = 0xFF22D3EE,
    )

    val cream = AppPalette(
        id = "cream",
        nameEn = "Cream",
        nameTr = "Krem",
        isDark = false,
        bg = 0xFFF7F2E9,
        surface = 0xFFFFFDF8,
        surfaceHigh = 0xFFEFE8DA,
        outline = 0xFFE0D6C3,
        text = 0xFF1F1710,
        muted = 0xFF7A6B58,
        primary = 0xFFB4540A,
        secondary = 0xFF9A3412,
        good = 0xFF3F7D20,
        warn = 0xFFB7791F,
        danger = 0xFFC0392B,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFFF9F43,
        logoB = 0xFFFF6B3D,
    )

    val lavender = AppPalette(
        id = "lavender",
        nameEn = "Lavender",
        nameTr = "Lavanta",
        isDark = false,
        bg = 0xFFF3F0FA,
        surface = 0xFFFFFFFF,
        surfaceHigh = 0xFFE9E4F5,
        outline = 0xFFD9D2EC,
        text = 0xFF16102A,
        muted = 0xFF6B6385,
        primary = 0xFF6D4AE0,
        secondary = 0xFFD946A8,
        good = 0xFF138A5B,
        warn = 0xFFC77700,
        danger = 0xFFD62F2F,
        logoFrameA = LOGO_TILE,
        logoFrameB = LOGO_TILE,
        logoA = 0xFFA78BFA,
        logoB = 0xFFF472B6,
    )

    val all: List<AppPalette> = listOf(
        monoDark,
        neon,
        ocean,
        sunset,
        forest,
        violet,
        crimson,
        amber,
        ice,
        rose,
        nord,
        dracula,
        matrix,
        monoLight,
        sky,
        mint,
        cream,
        lavender,
    )

    fun byId(id: String?): AppPalette = all.firstOrNull { it.id == id } ?: monoDark

    /**
     * Kullanıcının seçimlerinden son paleti üretir: tema + (koyu temalarda) AMOLED siyah zemin +
     * isteğe bağlı vurgu rengi. Saf fonksiyon: JVM'de test edilir.
     */
    fun resolve(
        themeId: String?,
        amoled: Boolean,
        accentId: String = AccentStyle.THEME_ID,
        accentCustom: Long = AccentStyle.DEFAULT_CUSTOM,
    ): AppPalette {
        var p = byId(themeId)
        if (amoled && p.isDark) p = p.toAmoled()
        return p.withAccent(accentId, accentCustom)
    }
}

/** Koyu paleti gerçek siyah zemine çeker; kartlar ve çizgiler orantılı koyulaşır. */
fun AppPalette.toAmoled(): AppPalette {
    if (!isDark || bg == 0xFF000000L) return this
    val black = 0xFF000000L
    return copy(
        bg = black,
        surface = mixArgb(surface, black, 0.35f),
        surfaceHigh = mixArgb(surfaceHigh, black, 0.25f),
        outline = mixArgb(outline, black, 0.10f),
    )
}

/** Vurgu rengini (ve onu izleyen logo rengini) değiştirir; "theme" ise paleti olduğu gibi bırakır. */
fun AppPalette.withAccent(accentId: String, custom: Long): AppPalette {
    val pair = AccentStyle.colorsFor(accentId, custom, isDark) ?: return this
    return copy(primary = pair.first, secondary = pair.second, logoA = pair.first, logoB = pair.second)
}

/**
 * Çok renkli arayüz için sabit vurgu seti. Ana ekrandaki her kutu/düğme kendi rengini buradan alır;
 * böylece her şey tek renk (beyaz) görünmez. Koyu zeminde parlak, açık zeminde koyu tonlar.
 */
data class AccentSet(
    val blue: Long,
    val cyan: Long,
    val green: Long,
    val yellow: Long,
    val orange: Long,
    val red: Long,
    val pink: Long,
    val violet: Long,
) {
    companion object {
        val dark = AccentSet(
            blue = 0xFF5B9DFF, cyan = 0xFF22D3EE, green = 0xFF34D399, yellow = 0xFFFBBF24,
            orange = 0xFFFB923C, red = 0xFFF87171, pink = 0xFFF472B6, violet = 0xFFA78BFA,
        )
        val light = AccentSet(
            blue = 0xFF2563EB, cyan = 0xFF0891B2, green = 0xFF059669, yellow = 0xFFD97706,
            orange = 0xFFEA580C, red = 0xFFDC2626, pink = 0xFFDB2777, violet = 0xFF7C3AED,
        )

        fun forMode(isDark: Boolean): AccentSet = if (isDark) dark else light
    }
}

/** Seçilebilir vurgu rengi: birincil + eşlik eden ikincil (marka gradyanı için). */
data class AccentStyle(
    val id: String,
    val nameEn: String,
    val nameTr: String,
    val darkPrimary: Long,
    val darkSecondary: Long,
    val lightPrimary: Long,
    val lightSecondary: Long,
) {
    val name: String get() = tr(nameEn, nameTr)

    companion object {
        const val THEME_ID = "theme"
        const val CUSTOM_ID = "custom"
        const val DEFAULT_CUSTOM: Long = 0xFF5B9DFF

        val presets: List<AccentStyle> = listOf(
            AccentStyle("blue", "Blue", "Mavi", 0xFF5B9DFF, 0xFF9A7CFF, 0xFF2563EB, 0xFF7C3AED),
            AccentStyle("cyan", "Cyan", "Camgöbeği", 0xFF22D3EE, 0xFF5B9DFF, 0xFF0891B2, 0xFF2563EB),
            AccentStyle("green", "Green", "Yeşil", 0xFF34D399, 0xFF22D3EE, 0xFF059669, 0xFF0891B2),
            AccentStyle("yellow", "Yellow", "Sarı", 0xFFFBBF24, 0xFFFB923C, 0xFFD97706, 0xFFEA580C),
            AccentStyle("orange", "Orange", "Turuncu", 0xFFFB923C, 0xFFF472B6, 0xFFEA580C, 0xFFDB2777),
            AccentStyle("red", "Red", "Kırmızı", 0xFFF87171, 0xFFFB923C, 0xFFDC2626, 0xFFEA580C),
            AccentStyle("pink", "Pink", "Pembe", 0xFFF472B6, 0xFFA78BFA, 0xFFDB2777, 0xFF7C3AED),
            AccentStyle("violet", "Violet", "Mor", 0xFFA78BFA, 0xFFF472B6, 0xFF7C3AED, 0xFFDB2777),
        )

        fun byId(id: String?): AccentStyle? = presets.firstOrNull { it.id == id }

        /** Geçerli bir vurgu kimliği mi? (kayıtlı değer bozuksa varsayılana dön) */
        fun normalize(id: String?): String = when {
            id == CUSTOM_ID -> CUSTOM_ID
            byId(id) != null -> id!!
            else -> THEME_ID
        }

        /** (birincil, ikincil) ya da vurgu seçilmediyse null. Özel renk koyu/açık zemine uyarlanır. */
        fun colorsFor(id: String, custom: Long, isDark: Boolean): Pair<Long, Long>? {
            if (id == CUSTOM_ID) {
                val primary = fitToMode(custom, isDark)
                val (h, s, l) = argbToHsl(primary)
                return primary to hslToArgb(h + 35f, s, l)
            }
            val a = byId(id) ?: return null
            return if (isDark) a.darkPrimary to a.darkSecondary else a.lightPrimary to a.lightSecondary
        }

        /** Özel rengi okunur aralığa çeker: koyu zeminde açık, açık zeminde koyu. */
        fun fitToMode(argb: Long, isDark: Boolean): Long {
            val (h, s, l) = argbToHsl(argb)
            val fixedS = s.coerceAtLeast(0.55f)
            val fixedL = if (isDark) l.coerceIn(0.55f, 0.75f) else l.coerceIn(0.30f, 0.48f)
            return hslToArgb(h, fixedS, fixedL)
        }
    }
}

/** 0xAARRGGBB -> (ton 0-360, doygunluk 0-1, açıklık 0-1). */
fun argbToHsl(argb: Long): Triple<Float, Float, Float> {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    var h = when (mx) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    if (h < 0f) h += 360f
    return Triple(h, s.coerceIn(0f, 1f), l)
}

/** (ton, doygunluk, açıklık) -> 0xFFRRGGBB. Ton herhangi bir değer olabilir; 0-360'a sarılır. */
fun hslToArgb(h: Float, s: Float, l: Float): Long {
    val hh = ((h % 360f) + 360f) % 360f
    val ss = s.coerceIn(0f, 1f)
    val ll = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * ll - 1f)) * ss
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = ll - c / 2f
    val r: Float
    val g: Float
    val b: Float
    when {
        hh < 60f -> { r = c; g = x; b = 0f }
        hh < 120f -> { r = x; g = c; b = 0f }
        hh < 180f -> { r = 0f; g = c; b = x }
        hh < 240f -> { r = 0f; g = x; b = c }
        hh < 300f -> { r = x; g = 0f; b = c }
        else -> { r = c; g = 0f; b = x }
    }
    fun ch(v: Float): Long = ((v + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** İki rengi karıştırır: t=0 -> a, t=1 -> b. Alfa her zaman opak. */
fun mixArgb(a: Long, b: Long, t: Float): Long {
    val k = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Long {
        val x = ((a shr shift) and 0xFF).toFloat()
        val y = ((b shr shift) and 0xFF).toFloat()
        return (x + (y - x) * k).roundToInt().coerceIn(0, 255).toLong()
    }
    return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Göreli parlaklık (0 siyah, 1 beyaz): düğme üzerindeki yazının rengini seçmek için. */
fun luminanceOf(argb: Long): Double {
    fun lin(v: Long): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
}

/**
 * Ana ekrandaki (ve karşılama / cihaz sayfasındaki) logonun rengi. Logo, üç çubuklu "F"dir ve
 * çubuklar üstten alta açıktan koyuya doğru bir "rampa" ile boyanır (bkz. [logoRamp]). `auto` tema
 * renklerini izler; diğerleri kullanıcının seçtiği sabit renktir.
 *
 * [colors]: boş = tema renkleri; tek renk = bütün çubuklar bu tonda; iki renk = üst çubuktan alt
 * çubuğa geçiş. Gümüş (varsayılan) özgün logodur.
 */
data class LogoStyle(
    val id: String,
    val nameEn: String,
    val nameTr: String,
    val colors: List<Long>,
) {
    val name: String get() = tr(nameEn, nameTr)

    companion object {
        const val AUTO_ID = "auto"
        const val CUSTOM_ID = "custom"

        /** Gümüş: özgün logonun gri-beyaz rampası için gösterge tonu. */
        const val SILVER_TINT: Long = 0xFFC4C7CD

        val Auto = LogoStyle(AUTO_ID, "Theme color", "Tema rengi", emptyList())

        val presets: List<LogoStyle> = listOf(
            Auto,
            LogoStyle("silver", "Silver", "Gümüş", listOf(SILVER_TINT)),
            LogoStyle("g_nova", "Nova", "Nova", listOf(0xFF4FC3FF, 0xFF9A7CFF)),
            LogoStyle("g_ocean", "Ocean", "Okyanus", listOf(0xFF2DE2C0, 0xFF4D8BFF)),
            LogoStyle("g_sunset", "Sunset", "Gün Batımı", listOf(0xFFFF8A4D, 0xFFFF4D8D)),
            LogoStyle("g_aurora", "Aurora", "Aurora", listOf(0xFF4ADE80, 0xFFA78BFA)),
            LogoStyle("teal", "Teal", "Turkuaz", listOf(0xFF2DE2C0)),
            LogoStyle("blue", "Blue", "Mavi", listOf(0xFF4D8BFF)),
            LogoStyle("violet", "Violet", "Mor", listOf(0xFFA78BFA)),
            LogoStyle("pink", "Pink", "Pembe", listOf(0xFFF472B6)),
            LogoStyle("red", "Red", "Kırmızı", listOf(0xFFFF5C5C)),
            LogoStyle("orange", "Orange", "Turuncu", listOf(0xFFFF8A4D)),
            LogoStyle("yellow", "Yellow", "Sarı", listOf(0xFFFFD84D)),
            LogoStyle("green", "Green", "Yeşil", listOf(0xFF4ADE80)),
        )

        fun byId(id: String?): LogoStyle = presets.firstOrNull { it.id == id } ?: Auto
    }
}

/** Logo çiziminde kullanılacak iki renk (tek renkse iki kez döner). */
fun logoColorsFor(palette: AppPalette, logoId: String, customLogo: Long): List<Long> {
    if (logoId == LogoStyle.CUSTOM_ID) return listOf(customLogo, customLogo)
    val c = LogoStyle.byId(logoId).colors
    return when {
        c.isEmpty() -> listOf(palette.logoA, palette.logoB)
        c.size == 1 -> listOf(c[0], c[0])
        else -> c
    }
}

/**
 * "F" logosunun altı renk durağı: üst çubuk (başlangıç, bitiş), orta çubuk, alt çubuk. Gri/gümüş
 * tonlar özgün logonun değerlerini AYNEN verir; renkli tonlar aynı açıklık basamaklarını kullanır
 * (üst çubuk neredeyse beyaz, alt çubuk koyu ve doygun). İki renk verilirse ton üstten alta
 * birinciden ikinciye geçer. Saf fonksiyon: JVM'de test edilir, Compose ve ikon üreticisi aynı
 * değerleri kullanır.
 */
fun logoRamp(colors: List<Long>): List<Long> {
    if (colors.isEmpty()) return LogoRamp.SILVER
    val first = colors.first()
    val last = colors.last()
    if (argbToHsl(first).second < 0.12f && argbToHsl(last).second < 0.12f) return LogoRamp.SILVER
    return List(6) { i ->
        val bar = i / 2
        val tint = mixArgb(first, last, bar / 2f)
        val (h, s, _) = argbToHsl(tint)
        hslToArgb(h, s.coerceAtLeast(0.6f) * LogoRamp.SATURATION[i], LogoRamp.LEVELS[i])
    }
}

object LogoRamp {
    val SILVER: List<Long> = listOf(
        0xFFFDFDFE, 0xFFDFE1E5, // üst çubuk
        0xFFCDD0D6, 0xFFA1A5AD, // orta çubuk
        0xFF8A8E96, 0xFF60646C, // alt çubuk
    )
    internal val LEVELS = floatArrayOf(0.97f, 0.86f, 0.78f, 0.64f, 0.56f, 0.40f)
    internal val SATURATION = floatArrayOf(0.30f, 0.45f, 0.65f, 0.80f, 0.90f, 1.00f)
}
