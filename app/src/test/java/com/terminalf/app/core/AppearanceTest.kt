package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AppearanceTest {
    @get:org.junit.Rule val language = TestLanguage()

    @Test fun hslConvertsKnownColors() {
        assertEquals(0xFFFF0000L, hslToArgb(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00L, hslToArgb(120f, 1f, 0.5f))
        assertEquals(0xFF0000FFL, hslToArgb(240f, 1f, 0.5f))
        assertEquals(0xFFFFFFFFL, hslToArgb(77f, 0f, 1f))
        assertEquals(0xFF000000L, hslToArgb(300f, 1f, 0f))
    }

    @Test fun hueWrapsAround() {
        assertEquals(hslToArgb(10f, 0.8f, 0.5f), hslToArgb(370f, 0.8f, 0.5f))
        assertEquals(hslToArgb(350f, 0.8f, 0.5f), hslToArgb(-10f, 0.8f, 0.5f))
    }

    @Test fun hslRoundTrips() {
        for (argb in listOf(0xFF5B9DFFL, 0xFFF87171L, 0xFF34D399L, 0xFFFBBF24L, 0xFFA78BFAL)) {
            val (h, s, l) = argbToHsl(argb)
            val back = hslToArgb(h, s, l)
            for (shift in listOf(16, 8, 0)) {
                val a = ((argb shr shift) and 0xFF).toInt()
                val b = ((back shr shift) and 0xFF).toInt()
                assertTrue("kanal farkı büyük: $argb -> $back", abs(a - b) <= 1)
            }
        }
    }

    @Test fun grayHasNoSaturation() {
        val (_, s, l) = argbToHsl(0xFF808080L)
        assertEquals(0f, s, 0.001f)
        assertEquals(0.5f, l, 0.01f)
    }

    @Test fun mixBlendsChannels() {
        assertEquals(0xFF000000L, mixArgb(0xFF000000L, 0xFFFFFFFFL, 0f))
        assertEquals(0xFFFFFFFFL, mixArgb(0xFF000000L, 0xFFFFFFFFL, 1f))
        assertEquals(0xFF808080L, mixArgb(0xFF000000L, 0xFFFFFFFFL, 0.5f))
    }

    @Test fun luminanceOrdersColors() {
        assertTrue(luminanceOf(0xFF000000L) < 0.01)
        assertTrue(luminanceOf(0xFFFFFFFFL) > 0.99)
        // Düğme yazısı: açık vurguda koyu, koyu vurguda beyaz seçilir.
        assertTrue(luminanceOf(AppPalettes.monoDark.primary) > 0.30)
        assertTrue(luminanceOf(AppPalettes.monoLight.primary) < 0.30)
    }

    @Test fun defaultThemeIsPureBlackWithColorfulStatus() {
        val p = AppPalettes.resolve(AppPalettes.DEFAULT_ID, amoled = true)
        assertEquals(0xFF000000L, p.bg)
        assertNotEquals(p.text, p.primary)
        assertNotEquals(p.primary, p.good)
        assertNotEquals(p.good, p.warn)
        assertNotEquals(p.warn, p.danger)
    }

    @Test fun amoledDarkensDarkThemesOnly() {
        val nord = AppPalettes.byId("nord")
        val amoled = AppPalettes.resolve("nord", amoled = true)
        assertEquals(0xFF000000L, amoled.bg)
        assertTrue(luminanceOf(amoled.surface) < luminanceOf(nord.surface))
        assertTrue(luminanceOf(amoled.surface) > 0.0) // kartlar zeminden ayrılır
        assertEquals(nord.primary, amoled.primary)
        // AMOLED kapalıysa tema olduğu gibi kalır.
        assertEquals(nord, AppPalettes.resolve("nord", amoled = false))
        // Açık temalar etkilenmez.
        assertEquals(AppPalettes.byId("sky"), AppPalettes.resolve("sky", amoled = true))
    }

    @Test fun accentOverridesPrimaryAndLogo() {
        val base = AppPalettes.resolve("mono_dark", amoled = true)
        val red = AppPalettes.resolve("mono_dark", amoled = true, accentId = "red")
        assertNotEquals(base.primary, red.primary)
        assertEquals(red.primary, red.logoA)
        assertEquals(red.secondary, red.logoB)
        assertEquals(base.bg, red.bg)
        // "theme" paleti değiştirmez; bilinmeyen kimlik de.
        assertEquals(base, AppPalettes.resolve("mono_dark", amoled = true, accentId = AccentStyle.THEME_ID))
        assertEquals(base, AppPalettes.resolve("mono_dark", amoled = true, accentId = "yok"))
    }

    @Test fun accentUsesDarkerShadeOnLightThemes() {
        val dark = AccentStyle.colorsFor("blue", 0, isDark = true)!!.first
        val light = AccentStyle.colorsFor("blue", 0, isDark = false)!!.first
        assertTrue(luminanceOf(light) < luminanceOf(dark))
        assertNull(AccentStyle.colorsFor(AccentStyle.THEME_ID, 0, isDark = true))
    }

    @Test fun customAccentIsFittedToMode() {
        // Çok koyu bir renk koyu zeminde okunur aralığa açılır; çok açık renk açık zeminde koyulaşır.
        val onDark = AccentStyle.colorsFor(AccentStyle.CUSTOM_ID, 0xFF101040L, isDark = true)!!.first
        assertTrue(luminanceOf(onDark) > luminanceOf(0xFF101040L))
        val onLight = AccentStyle.colorsFor(AccentStyle.CUSTOM_ID, 0xFFFFFF80L, isDark = false)!!.first
        assertTrue(luminanceOf(onLight) < luminanceOf(0xFFFFFF80L))
    }

    @Test fun accentIdNormalization() {
        assertEquals(AccentStyle.THEME_ID, AccentStyle.normalize(null))
        assertEquals(AccentStyle.THEME_ID, AccentStyle.normalize("yok"))
        assertEquals(AccentStyle.CUSTOM_ID, AccentStyle.normalize("custom"))
        assertEquals("green", AccentStyle.normalize("green"))
    }

    @Test fun accentPresetsAreUniqueAndComplete() {
        val ids = AccentStyle.presets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size >= 8)
    }

    @Test fun statusColorsStayDistinctInEveryAccentSet() {
        for (set in listOf(AccentSet.dark, AccentSet.light)) {
            val all = listOf(set.blue, set.cyan, set.green, set.yellow, set.orange, set.red, set.pink, set.violet)
            assertEquals(all.size, all.toSet().size)
        }
    }

    @Test fun defaultLogoIsSilver() {
        val silver = LogoStyle.byId("silver")
        assertEquals(listOf(LogoStyle.SILVER_TINT), silver.colors)
        // Varsayılan tema, otomatik logo rengi olarak gümüşü kullanır.
        assertEquals(LogoStyle.SILVER_TINT, AppPalettes.monoDark.logoA)
        assertEquals(LogoStyle.SILVER_TINT, AppPalettes.monoDark.logoB)
    }

    @Test fun silverRampIsTheOriginalLogo() {
        assertEquals(LogoRamp.SILVER, logoRamp(listOf(LogoStyle.SILVER_TINT)))
        assertEquals(LogoRamp.SILVER, logoRamp(emptyList()))
        assertEquals(6, LogoRamp.SILVER.size)
        assertEquals(0xFFFDFDFEL, LogoRamp.SILVER.first())
        assertEquals(0xFF60646CL, LogoRamp.SILVER.last())
    }

    @Test fun coloredRampGetsDarkerFromTopToBottom() {
        for (style in LogoStyle.presets) {
            if (style.colors.isEmpty()) continue
            val ramp = logoRamp(style.colors)
            assertEquals(6, ramp.size)
            val lum = ramp.map { luminanceOf(it) }
            for (i in 1 until lum.size) assertTrue("${style.id} stop $i", lum[i] <= lum[i - 1] + 1e-9)
        }
        // Renkli tonlar gümüş rampadan farklıdır ve tonu korur (mavi: B kanalı en baskın).
        val blue = logoRamp(listOf(0xFF4D8BFFL))
        assertNotEquals(LogoRamp.SILVER, blue)
        assertTrue((blue.last() and 0xFF) > ((blue.last() shr 16) and 0xFF))
    }

    @Test fun logoTileIsFlatForEveryTheme() {
        for (p in AppPalettes.all) {
            assertEquals(LOGO_TILE, p.logoFrameA)
            assertEquals(LOGO_TILE, p.logoFrameB)
        }
        assertEquals(LOGO_TILE, AppPalettes.resolve("ocean", true).logoFrameA)
    }

    @Test fun namesFollowTheLanguage() {
        I18n.language = AppLanguage.EN
        assertEquals("AMOLED Black", AppPalettes.monoDark.name)
        assertEquals("Silver", LogoStyle.byId("silver").name)
        assertEquals("Theme color", LogoStyle.Auto.name)
        I18n.language = AppLanguage.TR
        assertEquals("AMOLED Siyah", AppPalettes.monoDark.name)
        assertEquals("Gümüş", LogoStyle.byId("silver").name)
        assertEquals("Mavi", AccentStyle.byId("blue")!!.name)
    }
}
