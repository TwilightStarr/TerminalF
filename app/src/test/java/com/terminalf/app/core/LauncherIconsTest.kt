package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconsTest {
    @get:org.junit.Rule val language = TestLanguage()

    @Test fun presetsMapToThemselves() {
        for (style in LogoStyle.presets) {
            if (style.colors.isEmpty()) continue
            assertEquals(style.id, LauncherIcons.keyFor(style.colors))
        }
    }

    @Test fun customColorMapsToNearestPreset() {
        assertEquals("red", LauncherIcons.keyFor(listOf(0xFFFF0000L, 0xFFFF0000L)))
        assertEquals("blue", LauncherIcons.keyFor(listOf(0xFF3070FFL)))
        assertEquals("silver", LauncherIcons.keyFor(listOf(0xFFD0D0D4L)))
        // Varsayılan temanın otomatik logo renkleri varsayılan (Gümüş) simgeye eşlenir.
        assertEquals(LauncherIcons.DEFAULT_KEY, LauncherIcons.keyFor(listOf(AppPalettes.monoDark.logoA, AppPalettes.monoDark.logoB)))
    }

    @Test fun emptyFallsBackToDefault() {
        assertEquals(LauncherIcons.DEFAULT_KEY, LauncherIcons.keyFor(emptyList()))
    }

    @Test fun aliasNames() {
        assertEquals("com.terminalf.app.LauncherSilver", LauncherIcons.aliasClassName("silver"))
        assertEquals("com.terminalf.app.LauncherGOcean", LauncherIcons.aliasClassName("g_ocean"))
        assertEquals("com.terminalf.app.LauncherSilver", LauncherIcons.aliasClassName(LauncherIcons.DEFAULT_KEY))
        assertEquals("silver", LauncherIcons.DEFAULT_KEY)
        assertTrue(LauncherIcons.keys.size >= 13)
    }
}
