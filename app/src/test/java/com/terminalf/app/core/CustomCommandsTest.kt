package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCommandsTest {
    @Test fun blankAndCommentLinesAreSkipped() {
        val lines = customLines("  settings put global a 1  \n\n# not\r\nam kill-all\n")
        assertEquals(listOf("settings put global a 1", "am kill-all"), lines)
    }

    @Test fun limitsLinesAndLength() {
        val many = (1..20).joinToString("\n") { "echo $it" }
        assertEquals(kCustomMaxLines, customLines(many).size)
        val long = "x".repeat(kCustomMaxChars + 50)
        assertEquals(kCustomMaxChars, customLines(long).single().length)
    }

    @Test fun customCommandsSurviveRoundTrip() {
        val c = GameModeConfig(packageName = "com.example.game", customStart = "echo a\necho b", customEnd = "echo z")
        val back = GameModeConfig.decode(c.encode())
        assertEquals("echo a\necho b", back.customStart)
        assertEquals("echo z", back.customEnd)
    }

    @Test fun oldConfigsWithoutCustomFieldsStillLoad() {
        val back = GameModeConfig.decode("{\"package\":\"com.example.game\",\"targetFps\":60}")
        assertEquals("", back.customStart)
        assertEquals("", back.customEnd)
        assertTrue(back.targetFps == 60)
    }

    @Test fun presetKeepsCustomCommands() {
        val base = GameModeConfig(packageName = "com.example.game", customStart = "echo a")
        val applied = kGamePresets.first().applyTo(base)
        assertEquals("echo a", applied.customStart)
    }
}
