package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconsTest {
    @Test fun presetsMapToThemselves() {
        for (style in LogoStyle.presets) {
            if (style.colors.isEmpty()) continue
            assertEquals(style.id, LauncherIcons.keyFor(style.colors))
        }
    }

    @Test fun customColorMapsToNearestPreset() {
        assertEquals("red", LauncherIcons.keyFor(listOf(0xFFFF0000L, 0xFFFF0000L)))
        assertEquals("blue", LauncherIcons.keyFor(listOf(0xFF3070FFL)))
        assertEquals("white", LauncherIcons.keyFor(listOf(0xFFF2F2F3L)))
    }

    @Test fun emptyFallsBackToDefault() {
        assertEquals(LauncherIcons.DEFAULT_KEY, LauncherIcons.keyFor(emptyList()))
    }

    @Test fun aliasNames() {
        assertEquals("com.terminalf.app.LauncherWhite", LauncherIcons.aliasClassName("white"))
        assertEquals("com.terminalf.app.LauncherGOcean", LauncherIcons.aliasClassName("g_ocean"))
        assertTrue(LauncherIcons.keys.size >= 13)
    }
}
