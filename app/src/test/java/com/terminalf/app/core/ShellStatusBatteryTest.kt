package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TweakStatusTest {
    @Test fun outputsMatchExact() {
        assertTrue(outputsMatch("1", "1"))
        assertFalse(outputsMatch("1", "0"))
    }

    @Test fun whitespaceIgnored() {
        assertTrue(outputsMatch("0\n0", " 0 \n\n0\n"))
    }

    @Test fun numbersCompareNumerically() {
        assertTrue(outputsMatch("0\n0\n0", "0.0\n0\n0.0"))
        assertTrue(outputsMatch("120.0", "120"))
        assertFalse(outputsMatch("60.0", "120.0"))
    }

    @Test fun lineCountMustMatch() {
        assertFalse(outputsMatch("a\nb", "a"))
        assertFalse(outputsMatch("package:x", ""))
    }

    @Test fun stateFromCheck() {
        assertEquals(TweakState.On, stateFromCheckResult("1", ShellResult("1", 0)))
        assertEquals(TweakState.Off, stateFromCheckResult("1", ShellResult("null", 0)))
        assertEquals(TweakState.Off, stateFromCheckResult("package:com.x", ShellResult("", 0)))
        assertEquals(TweakState.Unknown, stateFromCheckResult("1", ShellResult("", 127)))
        assertEquals(
            TweakState.Unknown,
            stateFromCheckResult("1", ShellResult("java.lang.SecurityException: denied")),
        )
    }

    @Test fun isLive() {
        assertTrue(TweakStatus(TweakState.On).isLive)
        assertFalse(TweakStatus(TweakState.On, estimated = true).isLive)
        assertFalse(TweakStatus(TweakState.Unknown).isLive)
    }

    @Test fun tweakModel() {
        val minimal = Tweak.fromJson(mapOf("id" to "x", "title" to "X", "applyCommand" to "a"))
        assertNull(minimal.checkCommand)
        assertFalse(minimal.hasCheck)
        assertFalse(minimal.isToggle)
        val full = Tweak.fromJson(
            mapOf(
                "id" to "x", "title" to "X", "applyCommand" to "a", "revertCommand" to "b",
                "checkCommand" to "settings get global foo", "expectedOutput" to "1",
            ),
        )
        assertTrue(full.hasCheck)
        assertTrue(full.isToggle)
    }

    @Test fun bundledTweaksAllHaveChecks() {
        val tweaks = tweaksFromJsonString(File("src/main/assets/config/tweaks.json").readText())
        assertTrue(tweaks.isNotEmpty())
        for (t in tweaks.filter { it.isToggle }) {
            assertTrue("${t.id} için checkCommand/expectedOutput eksik", t.hasCheck)
        }
        // Profiller yalnızca var olan tweak kimliklerine başvurmalı.
        val ids = tweaks.map { it.id }.toSet()
        for (p in kProfiles) for (id in p.tweakIds) assertTrue("${p.id}: $id yok", id in ids)
        assertTrue(appsFromJsonString(File("src/main/assets/config/applications.json").readText()).isNotEmpty())
        assertTrue(featuresFromJsonString(File("src/main/assets/config/features.json").readText()).isNotEmpty())
    }
}

class BatteryMathTest {
    private val base = 1_700_000_000_000L
    private fun ms(min: Long = 0, sec: Long = 0) = base + min * 60_000 + sec * 1000

    @Test fun nullWhenNotChargingOrDischarging() {
        assertNull(estimateRemainingDuration(BatteryState.Full, 5, base, 80, ms(5), 82))
    }

    @Test fun nullWithoutSamples() {
        assertNull(estimateRemainingDuration(BatteryState.Discharging, 1, base, 80, base, 80))
    }

    @Test fun nullWhenTooShort() {
        assertNull(estimateRemainingDuration(BatteryState.Discharging, 2, base, 80, ms(sec = 10), 79))
    }

    @Test fun nullWhenUnchanged() {
        assertNull(estimateRemainingDuration(BatteryState.Discharging, 3, base, 80, ms(5), 80))
    }

    @Test fun discharge() {
        assertEquals(9000.seconds, estimateRemainingDuration(BatteryState.Discharging, 2, base, 80, ms(10), 75))
    }

    @Test fun charge() {
        assertEquals(1800.seconds, estimateRemainingDuration(BatteryState.Charging, 2, base, 60, ms(10), 70))
    }

    @Test fun wrongDirection() {
        assertNull(estimateRemainingDuration(BatteryState.Discharging, 2, base, 70, ms(10), 75))
        assertNull(estimateRemainingDuration(BatteryState.Charging, 2, base, 75, ms(10), 70))
    }

    @Test fun customMinElapsed() {
        assertNotNull(
            estimateRemainingDuration(
                BatteryState.Discharging, 2, base, 50, ms(sec = 10), 49, minElapsedForEstimate = 5.seconds,
            ),
        )
        assertTrue(10.minutes > 1.minutes)
    }
}
