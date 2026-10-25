package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PKG = "com.mojang.minecraftpe"

private fun session(settings: Map<String, String?> = emptyMap(), fixedPerf: Boolean = false) = GameSession(
    packageName = PKG,
    settings = settings,
    touchedGameManager = false,
    startedAtMs = 1,
    fixedPerf = fixedPerf,
)

class AngleSelectionTest {
    @Test fun addsPackageWhenListsAreEmpty() {
        val m = mergeAngleSelection(AngleSelection(null, null), PKG, "angle")
        assertEquals(PKG, m.pkgs)
        assertEquals("angle", m.values)
    }

    @Test fun keepsOtherAppsEntries() {
        val m = mergeAngleSelection(AngleSelection("a.b,c.d", "angle,native"), PKG, "angle")
        assertEquals("a.b,c.d,$PKG", m.pkgs)
        assertEquals("angle,native,angle", m.values)
    }

    @Test fun updatesExistingEntryInPlace() {
        val m = mergeAngleSelection(AngleSelection("a.b,$PKG", "angle,native"), PKG, "angle")
        assertEquals("a.b,$PKG", m.pkgs)
        assertEquals("angle,angle", m.values)
    }

    @Test fun mismatchedListsAreRepaired() {
        val shortValues = mergeAngleSelection(AngleSelection("a.b,c.d", "angle"), PKG, "native")
        assertEquals("angle,default,native", shortValues.values)
        val longValues = mergeAngleSelection(AngleSelection("a.b", "angle,native,angle"), PKG, "native")
        assertEquals("a.b,$PKG", longValues.pkgs)
        assertEquals("angle,native", longValues.values)
    }

    @Test fun parseListSetting() {
        assertEquals(ListSetting(null, true), parseListSetting("null\n"))
        assertEquals(ListSetting("a.b,c.d", true), parseListSetting("a.b,c.d\n"))
        // Boş çıktı okuma hatasıdır: "yok" sayılmaz.
        assertFalse(parseListSetting("").safe)
        assertFalse(parseListSetting("  \n").safe)
        assertFalse(parseListSetting("a.b; reboot").safe)
        assertFalse(parseListSetting("a.b\nc.d").safe)
    }
}

class BackendStepTest {
    @Test fun defaultBackendProducesNoStep() {
        assertNull(buildBackendStep(GameModeConfig(packageName = PKG), AngleSelection(null, null)))
    }

    @Test fun unreadableSettingSkipsStepInsteadOfGuessing() {
        assertNull(buildBackendStep(GameModeConfig(packageName = PKG, backend = GraphicsBackend.Angle), null))
    }

    @Test fun angleStepWritesMergedLists() {
        val step = buildBackendStep(
            GameModeConfig(packageName = PKG, backend = GraphicsBackend.Angle),
            AngleSelection("a.b", "native"),
        )!!
        assertTrue(step.optional)
        assertTrue(step.command.contains("settings put global angle_gl_driver_selection_pkgs a.b,$PKG"))
        assertTrue(step.command.contains("settings put global angle_gl_driver_selection_values native,angle"))
    }

    @Test fun nativeStepUsesNativeValue() {
        val step = buildBackendStep(
            GameModeConfig(packageName = PKG, backend = GraphicsBackend.Native),
            AngleSelection(null, null),
        )!!
        assertTrue(step.command.endsWith("angle_gl_driver_selection_values native"))
    }

    @Test fun applyStepsIncludeBackendAndRestartOnlyWhenAsked() {
        val cfg = GameModeConfig(packageName = PKG, backend = GraphicsBackend.Angle)
        val angle = AngleSelection(null, null)
        val without = buildApplySteps(cfg, angle = angle, restartGame = false).map { it.command }
        assertTrue(without.any { it.contains("angle_gl_driver_selection_pkgs") })
        assertFalse(without.any { it.contains("force-stop") })
        val with = buildApplySteps(cfg, angle = angle, restartGame = true).map { it.command }
        assertTrue(with.contains("am force-stop $PKG"))
        // Ayar okunamadıysa oyun da kapatılmaz.
        val unread = buildApplySteps(cfg, angle = null, restartGame = true).map { it.command }
        assertFalse(unread.any { it.contains("force-stop") })
        // Sistem varsayılanında hiçbir sürücü adımı yoktur.
        val plain = buildApplySteps(GameModeConfig(packageName = PKG), angle = angle, restartGame = true)
        assertFalse(plain.any { it.command.contains("angle_gl") || it.command.contains("force-stop") })
    }

    @Test fun revertRestoresOrDeletesAngleKeys() {
        val s = session(
            mapOf(
                "global:angle_gl_driver_selection_pkgs" to "a.b",
                "global:angle_gl_driver_selection_values" to null,
            ),
        )
        val cmd = buildRevertSteps(s).first { it.label.contains("Grafik motoru") }
        assertFalse(cmd.optional)
        assertTrue(cmd.command.contains("settings put global angle_gl_driver_selection_pkgs a.b"))
        assertTrue(cmd.command.contains("settings delete global angle_gl_driver_selection_values"))
    }

    @Test fun revertNeverEmbedsUnsafeOldValue() {
        val s = session(mapOf("global:angle_gl_driver_selection_pkgs" to "a.b; reboot"))
        val cmd = buildRevertSteps(s).first { it.label.contains("Grafik motoru") }.command
        assertFalse(cmd.contains("reboot"))
        assertTrue(cmd.contains("settings delete global angle_gl_driver_selection_pkgs"))
    }

    @Test fun noAngleRevertWhenNothingWasTouched() {
        assertFalse(buildRevertSteps(session()).any { it.label.contains("Grafik motoru") })
    }

    @Test fun watchdogScriptRevertsBackendToo() {
        val s = session(
            mapOf(
                "global:angle_gl_driver_selection_pkgs" to null,
                "global:angle_gl_driver_selection_values" to null,
            ),
            fixedPerf = true,
        )
        val script = buildWatchdogScript(s)
        assertTrue(script.contains("settings delete global angle_gl_driver_selection_pkgs"))
        assertTrue(script.contains(kFixedPerfOff))
    }
}

class FixedPerfTest {
    @Test fun appliedOnlyWhenEnabled() {
        assertFalse(buildApplySteps(GameModeConfig(packageName = PKG)).any { it.command == kFixedPerfOn })
        val on = buildApplySteps(GameModeConfig(packageName = PKG, fixedPerf = true))
        assertTrue(on.any { it.command == kFixedPerfOn && it.optional })
    }

    @Test fun revertAndGuardTurnItOff() {
        val s = session(fixedPerf = true)
        assertTrue(buildRevertSteps(s).any { it.command == kFixedPerfOff && it.optional })
        assertTrue(kFixedPerfOff in buildGuardCommands(s))
        assertFalse(buildRevertSteps(session()).any { it.command == kFixedPerfOff })
    }

    @Test fun sessionRoundTripKeepsFlag() {
        val back = GameSession.decode(session(fixedPerf = true).encode())!!
        assertTrue(back.fixedPerf)
        assertFalse(GameSession.decode(session().encode())!!.fixedPerf)
    }
}

class CustomValuesTest {
    @Test fun autoLockHzPicksPanelMultiple() {
        assertEquals(60, autoLockHz(30))
        assertEquals(90, autoLockHz(45))
        assertEquals(60, autoLockHz(60))
        assertEquals(144, autoLockHz(72))
        assertEquals(90, autoLockHz(90))
        assertEquals(120, autoLockHz(120))
        assertEquals(144, autoLockHz(144))
    }

    @Test fun lockHzHonoursManualChoice() {
        assertEquals(120, GameModeConfig(targetFps = 60, refreshHz = 120).lockHz)
        assertEquals(60, GameModeConfig(targetFps = 30).lockHz)
        assertNull(GameModeConfig(refreshHz = 120, lockRefreshRate = false).lockHz)
    }

    @Test fun dimPercentIsUsed() {
        val step = buildApplySteps(GameModeConfig(packageName = PKG, dimScreen = true, dimPercent = 40), brightness = 1000)
            .first { it.command.contains("screen_brightness ") }
        assertTrue(step.command.endsWith("screen_brightness 400"))
    }

    @Test fun defaultDimStillSixtyPercent() {
        val step = buildApplySteps(GameModeConfig(packageName = PKG, dimScreen = true), brightness = 1000)
            .first { it.command.contains("screen_brightness ") }
        assertTrue(step.command.endsWith("screen_brightness 600"))
    }

    @Test fun cpuCapPercentIsUsed() {
        val policies = listOf(CpuPolicy("policy7", 3000000, 3400000))
        val step = buildApplySteps(GameModeConfig(packageName = PKG, cpuCap = true, cpuCapPercent = 50), cpuPolicies = policies)
            .first { it.command.contains("scaling_max_freq") }
        assertTrue(step.command.contains("echo 1700000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"))
        assertTrue(step.label.contains("%50"))
        val def = buildApplySteps(GameModeConfig(packageName = PKG, cpuCap = true), cpuPolicies = policies)
            .first { it.command.contains("scaling_max_freq") }
        assertTrue(def.command.contains("echo 2380000 >"))
    }

    @Test fun newFieldsRoundTrip() {
        val c = GameModeConfig(
            packageName = PKG, targetFps = 120, backend = GraphicsBackend.Angle, fixedPerf = true,
            refreshHz = 144, dimPercent = 40, cpuCapPercent = 80,
        )
        val back = GameModeConfig.decode(c.encode())
        assertEquals(120, back.targetFps)
        assertEquals(GraphicsBackend.Angle, back.backend)
        assertTrue(back.fixedPerf)
        assertEquals(144, back.refreshHz)
        assertEquals(40, back.dimPercent)
        assertEquals(80, back.cpuCapPercent)
    }

    @Test fun oldConfigGetsSafeDefaults() {
        val back = GameModeConfig.decode("{\"package\":\"a.b\",\"targetFps\":60,\"scale\":0.5}")
        assertEquals(GraphicsBackend.Default, back.backend)
        assertFalse(back.fixedPerf)
        assertEquals(0, back.refreshHz)
        assertEquals(60, back.dimPercent)
        assertEquals(70, back.cpuCapPercent)
    }

    @Test fun invalidValuesAreCorrected() {
        val back = GameModeConfig.decode(
            "{\"package\":\"a.b\",\"targetFps\":77,\"backend\":\"zzz\",\"refreshHz\":75,\"dimPercent\":5,\"cpuCapPercent\":200}",
        )
        assertEquals(90, back.targetFps)
        assertEquals(GraphicsBackend.Default, back.backend)
        assertEquals(0, back.refreshHz)
        assertEquals(20, back.dimPercent)
        assertEquals(95, back.cpuCapPercent)
    }

    @Test fun choiceListsContainDefaults() {
        assertTrue(90 in kFpsChoices)
        assertTrue(60 in kDimChoices)
        assertTrue(70 in kCpuCapChoices)
        assertTrue(0 in kHzChoices)
        for (v in kScaleSteps) assertTrue(v in kScaleChoices)
    }
}

class GameKindTest {
    @Test fun detection() {
        assertEquals(GameKind.MinecraftBedrock, detectGameKind("com.mojang.minecraftpe"))
        assertEquals(GameKind.MinecraftJava, detectGameKind("net.kdt.pojavlaunch"))
        assertEquals(GameKind.Generic, detectGameKind(DEFAULT_GAME_PACKAGE))
    }

    @Test fun tipsAndRecommendation() {
        assertTrue(gameTips(GameKind.MinecraftBedrock).isNotEmpty())
        assertTrue(gameTips(GameKind.Generic).isEmpty())
        val base = GameModeConfig(packageName = PKG, customStart = "echo a")
        val rec = recommendedConfig(GameKind.MinecraftBedrock, base)
        assertNotNull(rec)
        assertEquals(PKG, rec!!.packageName)
        assertEquals("echo a", rec.customStart)
        assertNull(recommendedConfig(GameKind.Generic, base))
    }

    @Test fun banWarningOnlyForMinecraft() {
        assertNull(modWarning(GameKind.Generic))
        for (k in listOf(GameKind.MinecraftBedrock, GameKind.MinecraftJava)) {
            val w = modWarning(k)!!
            assertTrue(w.contains("banlan"))
            assertTrue(w.contains("indirmez"))
        }
    }

    @Test fun bedrockRecommendationIsCoolAndCapped() {
        val rec = recommendedConfig(GameKind.MinecraftBedrock, GameModeConfig(packageName = PKG))!!
        assertEquals(kBedrockFpsCap, rec.targetFps)
        assertEquals(60, rec.lockHz)
        assertEquals(GamePower.Standard, rec.powerProfile)
        assertEquals(GraphicsBackend.Default, rec.backend)
        assertFalse(rec.fixedPerf)
    }
}
