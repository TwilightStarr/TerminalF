package com.terminalf.app.core

/*
 * Tema paletleri ve logo renkleri. Saf veri: renkler `0xAARRGGBB` olarak tutulur (Long), böylece
 * mantık katmanı arayüz kitaplığına bağlı kalmaz ve JVM'de test edilebilir. Compose tarafı bunları
 * `Color(long)` ile renge çevirir.
 */

/** Bir tema paleti: arka plan, yüzey, metin ve vurgu renklerinin tamamı. */
data class AppPalette(
    val id: String,
    val name: String,
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
)

/** Hazır temalar. Varsayılan: [monoDark] (siyah-beyaz). */
object AppPalettes {
    const val DEFAULT_ID = "mono_dark"

    val monoDark = AppPalette(
        id = "mono_dark",
        name = "Siyah & Beyaz",
        isDark = true,
        bg = 0xFF000000,
        surface = 0xFF0D0D0D,
        surfaceHigh = 0xFF181818,
        outline = 0xFF2A2A2A,
        text = 0xFFFAFAFA,
        muted = 0xFF8E8E8E,
        primary = 0xFFFFFFFF,
        secondary = 0xFFB4B4B4,
        good = 0xFFFFFFFF,
        warn = 0xFFFFB84D,
        danger = 0xFFFF6B6B,
        logoFrameA = 0xFF000000,
        logoFrameB = 0xFF1B1B1B,
        logoA = 0xFFFFFFFF,
        logoB = 0xFFBDBDBD,
    )

    val monoLight = AppPalette(
        id = "mono_light",
        name = "Beyaz & Siyah",
        isDark = false,
        bg = 0xFFF6F6F6,
        surface = 0xFFFFFFFF,
        surfaceHigh = 0xFFEEEEEE,
        outline = 0xFFDEDEDE,
        text = 0xFF0A0A0A,
        muted = 0xFF6B6B6B,
        primary = 0xFF0A0A0A,
        secondary = 0xFF4A4A4A,
        good = 0xFF0A0A0A,
        warn = 0xFFC77700,
        danger = 0xFFD62F2F,
        logoFrameA = 0xFF0A0A0A,
        logoFrameB = 0xFF222222,
        logoA = 0xFFFFFFFF,
        logoB = 0xFFC8C8C8,
    )

    val neon = AppPalette(
        id = "neon",
        name = "Neon Turkuaz",
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
        logoFrameA = 0xFF0A0E14,
        logoFrameB = 0xFF151E2C,
        logoA = 0xFF2DE2C0,
        logoB = 0xFF4D8BFF,
    )

    val ocean = AppPalette(
        id = "ocean",
        name = "Okyanus",
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
        logoFrameA = 0xFF070C16,
        logoFrameB = 0xFF111B30,
        logoA = 0xFF5AA9FF,
        logoB = 0xFF7C6CFF,
    )

    val sunset = AppPalette(
        id = "sunset",
        name = "Gün Batımı",
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
        logoFrameA = 0xFF110B09,
        logoFrameB = 0xFF241713,
        logoA = 0xFFFF8A4D,
        logoB = 0xFFFF4D8D,
    )

    val forest = AppPalette(
        id = "forest",
        name = "Orman",
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
        logoFrameA = 0xFF08100B,
        logoFrameB = 0xFF12211A,
        logoA = 0xFF4ADE80,
        logoB = 0xFF22D3EE,
    )

    val violet = AppPalette(
        id = "violet",
        name = "Mor",
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
        logoFrameA = 0xFF0C0914,
        logoFrameB = 0xFF1A1428,
        logoA = 0xFFA78BFA,
        logoB = 0xFFF472B6,
    )

    val crimson = AppPalette(
        id = "crimson",
        name = "Kızıl",
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
        logoFrameA = 0xFF0F0708,
        logoFrameB = 0xFF24100F,
        logoA = 0xFFFF4D5E,
        logoB = 0xFFFF8A4D,
    )

    val amber = AppPalette(
        id = "amber",
        name = "Altın",
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
        logoFrameA = 0xFF100C06,
        logoFrameB = 0xFF231A0C,
        logoA = 0xFFFFC84D,
        logoB = 0xFFFF9F43,
    )

    val ice = AppPalette(
        id = "ice",
        name = "Buz",
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
        logoFrameA = 0xFF060D12,
        logoFrameB = 0xFF10202C,
        logoA = 0xFF5FE3FF,
        logoB = 0xFF8AA8FF,
    )

    val rose = AppPalette(
        id = "rose",
        name = "Gül",
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
        logoFrameA = 0xFF11080C,
        logoFrameB = 0xFF261320,
        logoA = 0xFFFF7AB6,
        logoB = 0xFFC084FC,
    )

    val nord = AppPalette(
        id = "nord",
        name = "Nord",
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
        logoFrameA = 0xFF1A1E26,
        logoFrameB = 0xFF2E3440,
        logoA = 0xFF88C0D0,
        logoB = 0xFF81A1C1,
    )

    val dracula = AppPalette(
        id = "dracula",
        name = "Drakula",
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
        logoFrameA = 0xFF15161C,
        logoFrameB = 0xFF282A36,
        logoA = 0xFFBD93F9,
        logoB = 0xFFFF79C6,
    )

    val matrix = AppPalette(
        id = "matrix",
        name = "Matrix",
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
        logoFrameA = 0xFF000000,
        logoFrameB = 0xFF07140B,
        logoA = 0xFF00FF66,
        logoB = 0xFF00C2A8,
    )

    val sky = AppPalette(
        id = "sky",
        name = "Gökyüzü",
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
        logoFrameA = 0xFF0B1626,
        logoFrameB = 0xFF16294A,
        logoA = 0xFF5AA9FF,
        logoB = 0xFF7C6CFF,
    )

    val mint = AppPalette(
        id = "mint",
        name = "Nane",
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
        logoFrameA = 0xFF08140E,
        logoFrameB = 0xFF12281C,
        logoA = 0xFF4ADE80,
        logoB = 0xFF22D3EE,
    )

    val cream = AppPalette(
        id = "cream",
        name = "Krem",
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
        logoFrameA = 0xFF140E08,
        logoFrameB = 0xFF2A1B10,
        logoA = 0xFFFF9F43,
        logoB = 0xFFFF6B3D,
    )

    val lavender = AppPalette(
        id = "lavender",
        name = "Lavanta",
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
        logoFrameA = 0xFF0E0A1C,
        logoFrameB = 0xFF1E1536,
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
}

/**
 * Ana ekrandaki (ve karşılama / cihaz sayfasındaki) logonun rengi. `auto` tema renklerini izler;
 * diğerleri kullanıcının seçtiği sabit renktir.
 */
data class LogoStyle(
    val id: String,
    val name: String,
    /** Boş = tema renkleri; tek renk = düz; iki renk = gradyan. */
    val colors: List<Long>,
) {
    companion object {
        const val AUTO_ID = "auto"
        const val CUSTOM_ID = "custom"

        val Auto = LogoStyle(AUTO_ID, "Tema rengi", emptyList())

        val presets: List<LogoStyle> = listOf(
            Auto,
        LogoStyle("white", "Beyaz", listOf(0xFFFFFFFF)),
        LogoStyle("silver", "Gümüş", listOf(0xFFBDBDBD)),
        LogoStyle("teal", "Turkuaz", listOf(0xFF2DE2C0)),
        LogoStyle("blue", "Mavi", listOf(0xFF4D8BFF)),
        LogoStyle("violet", "Mor", listOf(0xFFA78BFA)),
        LogoStyle("pink", "Pembe", listOf(0xFFF472B6)),
        LogoStyle("red", "Kırmızı", listOf(0xFFFF5C5C)),
        LogoStyle("orange", "Turuncu", listOf(0xFFFF8A4D)),
        LogoStyle("yellow", "Sarı", listOf(0xFFFFD84D)),
        LogoStyle("green", "Yeşil", listOf(0xFF4ADE80)),
        LogoStyle("g_ocean", "Okyanus", listOf(0xFF2DE2C0, 0xFF4D8BFF)),
        LogoStyle("g_sunset", "Gün Batımı", listOf(0xFFFF8A4D, 0xFFFF4D8D)),
        LogoStyle("g_aurora", "Aurora", listOf(0xFF4ADE80, 0xFFA78BFA)),
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
