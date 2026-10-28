package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val DEF = DEFAULT_GAME_PACKAGE

private fun cmds(c: GameModeConfig, size: PixelSize? = null, density: Int? = null, brightness: Int? = null, cpu: List<CpuPolicy>? = null) =
    buildApplySteps(c, size, density, brightness, cpu)

class WmParsersTest {
    @get:org.junit.Rule val language = TestLanguage()

    @Test fun size() {
        val s = parseWmSize("Physical size: 1220x2712\nOverride size: 813x1808\n")!!
        assertEquals("1220x2712", s.physical.toString())
        assertEquals("813x1808", s.override.toString())
    }

    @Test fun noOverride() {
        assertNull(parseWmSize("Physical size: 1220x2712")!!.override)
        assertNull(parseWmSize("hata"))
    }

    @Test fun density() {
        val d = parseWmDensity("Physical density: 440\nOverride density: 293")!!
        assertEquals(440, d.physical)
        assertEquals(293, d.override)
        assertNull(parseWmDensity("Physical density: 440")!!.override)
    }

    @Test fun scaling() {
        val s = scaledSize(PixelSize(1220, 2712), 0.6)
        assertTrue(s.width % 2 == 0 && s.height % 2 == 0)
        assertEquals(732, s.width)
        assertEquals(1628, s.height)
        assertEquals(220, scaledDensity(440, 0.5))
        assertEquals(120, scaledDensity(200, 0.4))
        assertEquals(400, scaledSize(PixelSize(1000, 2000), 0.1).width)
    }

    @Test fun refreshRate() {
        assertEquals(90.0, parseActiveRefreshRate("mActiveMode={id=2, width=1220, height=2712, fps=90.0}")!!, 0.0)
        assertNull(parseActiveRefreshRate("alakasız"))
        assertNull(parseActiveRefreshRate("fps=3.0"))
    }

    @Test fun downscale() {
        assertEquals("disable", downscaleArg(1.0))
        assertEquals("0.6", downscaleArg(0.6))
        assertEquals("0.75", downscaleArg(0.75))
        assertEquals("0.5", downscaleArg(0.5))
        assertEquals("0.6", downscaleArg(0.62))
    }
}

class GameCommandsTest {
    @get:org.junit.Rule val language = TestLanguage()

    @Test fun defaults() {
        val c = cmds(GameModeConfig()).map { it.command }
        assertTrue("am kill-all" in c)
        assertTrue(c.any { it.startsWith("cmd game set --mode 2 --fps 90 --downscale 0.6 ") })
        assertTrue(c.any { it == "cmd game mode performance $DEF" })
        assertTrue(c.any { it.contains("min_refresh_rate 90.0") })
        assertFalse(c.any { it.startsWith("wm size") })
    }

    @Test fun fps30Locks60() {
        assertTrue(cmds(GameModeConfig(targetFps = 30)).any { it.command.contains("min_refresh_rate 60.0") })
    }

    @Test fun lockOff() {
        assertFalse(cmds(GameModeConfig(targetFps = 30, lockRefreshRate = false)).any { it.command.contains("refresh_rate") })
    }

    @Test fun fullResNoDownscale() {
        assertFalse(cmds(GameModeConfig(scale = 1.0)).any { it.command.contains("--downscale") })
    }

    @Test fun wholeScreen() {
        val steps = cmds(
            GameModeConfig(method = ResolutionMethod.WholeScreen, scale = 0.5),
            size = PixelSize(1220, 2712), density = 440,
        )
        val wm = steps.first { it.command.startsWith("wm size") }
        assertEquals("wm size 610x1356; wm density 220", wm.command)
        assertFalse(steps.any { it.command.contains("--downscale") })
        assertFalse(wm.optional)
    }

    @Test fun injectionBlocked() {
        assertTrue(cmds(GameModeConfig(packageName = "a.b; rm -rf /")).isEmpty())
    }

    @Test fun optionalFlags() {
        val steps = cmds(GameModeConfig())
        assertTrue(steps.first { it.command.startsWith("cmd game set") }.optional)
        assertFalse(steps.first { it.command.contains("refresh_rate") }.optional)
    }
}

class GameRevertTest {
    @get:org.junit.Rule val language = TestLanguage()

    private fun session(screen: Boolean = false) = GameSession(
        packageName = DEF,
        settings = mapOf(
            "system:peak_refresh_rate" to "120.0",
            "system:min_refresh_rate" to null,
            "system:user_refresh_rate" to null,
            "secure:miui_refresh_rate" to "120",
        ),
        touchedGameManager = true,
        changedScreen = screen,
        sizeOverride = null,
        densityOverride = "420",
        startedAtMs = 1,
    )

    @Test fun putOrDelete() {
        val cmd = buildRevertSteps(session()).first { it.command.contains("settings") }.command
        assertTrue(cmd.contains("settings put system peak_refresh_rate 120.0"))
        assertTrue(cmd.contains("settings delete system min_refresh_rate"))
        assertTrue(cmd.contains("settings put secure miui_refresh_rate 120"))
    }

    @Test fun screenReset() {
        assertEquals("wm size reset; wm density 420", buildRevertSteps(session(screen = true)).first().command)
    }

    @Test fun noWmWhenUnchanged() {
        assertFalse(buildRevertSteps(session()).any { it.command.startsWith("wm ") })
    }

    @Test fun roundTrip() {
        val s = GameSession.decode(session(screen = true).encode())!!
        assertEquals("120.0", s.settings["system:peak_refresh_rate"])
        assertNull(s.settings["system:min_refresh_rate"])
        assertTrue(s.changedScreen)
        assertEquals("420", s.densityOverride)
    }

    @Test fun badRecordsRejected() {
        assertNull(GameSession.decode("bozuk{"))
        assertNull(GameSession.decode(""))
        val evil = GameSession(packageName = "x.y; reboot", settings = emptyMap(), touchedGameManager = true, startedAtMs = 0)
        assertNull(GameSession.decode(evil.encode()))
    }

    @Test fun dangerousOldValue() {
        val s = GameSession(
            packageName = DEF, settings = mapOf("system:peak_refresh_rate" to "1; reboot"),
            touchedGameManager = false, startedAtMs = 0,
        )
        val cmd = buildRevertSteps(s).first().command
        assertFalse(cmd.contains("reboot"))
        assertTrue(cmd.contains("settings delete system peak_refresh_rate"))
    }

    @Test fun configRoundTrip() {
        val c = GameModeConfig(targetFps = 60, scale = 0.5, method = ResolutionMethod.WholeScreen)
        val back = GameModeConfig.decode(c.encode())
        assertEquals(60, back.targetFps)
        assertEquals(0.5, back.scale, 0.0)
        assertEquals(ResolutionMethod.WholeScreen, back.method)
        assertEquals(90, GameModeConfig.decode("zzz").targetFps)
        assertNull(normalizeSettingValue("null"))
        assertEquals("90.0", normalizeSettingValue("90.0\n"))
    }
}

class GamePresetsTest {
    @get:org.junit.Rule val language = TestLanguage()

    @Test fun defaults() {
        val c = GameModeConfig()
        assertFalse(c.animationsOff)
        assertFalse(c.dnd)
        assertTrue(c.autoRevert)
    }

    @Test fun presetApplies() {
        val base = GameModeConfig(packageName = "a.b", method = ResolutionMethod.WholeScreen, killBackground = false)
        val cool = kGamePresets.first { it.id == "cool" }.applyTo(base)
        assertEquals("a.b", cool.packageName)
        assertEquals(ResolutionMethod.WholeScreen, cool.method)
        assertFalse(cool.killBackground)
        assertEquals(30, cool.targetFps)
        assertEquals(0.5, cool.scale, 0.0)
        assertTrue(cool.animationsOff)
        assertTrue(cool.dnd)
        assertTrue(cool.lockRefreshRate)
        assertTrue(cool.dimScreen)
        assertTrue(cool.cpuCap)
    }

    @Test fun matches() {
        val p = kGamePresets.first { it.id == "balanced" }
        assertTrue(p.matches(p.applyTo(GameModeConfig())))
        assertFalse(p.matches(p.applyTo(GameModeConfig()).copy(scale = 0.5)))
    }

    @Test fun animDnd() {
        assertFalse(cmds(GameModeConfig()).any { it.command.contains("animation_scale") })
        val steps = cmds(GameModeConfig(animationsOff = true, dnd = true))
        val anim = steps.first { it.command.contains("window_animation_scale") }
        assertTrue(anim.command.contains("settings put global transition_animation_scale 0"))
        assertTrue(anim.command.contains("settings put global animator_duration_scale 0"))
        assertTrue(anim.optional)
        assertTrue(steps.any { it.command == "settings put global zen_mode 1" })
    }

    @Test fun revertAnimDnd() {
        val s = GameSession(
            packageName = DEF,
            settings = mapOf(
                "global:window_animation_scale" to "1.0",
                "global:transition_animation_scale" to null,
                "global:animator_duration_scale" to "0.5",
                "global:zen_mode" to "0",
            ),
            touchedGameManager = false, startedAtMs = 0,
        )
        val steps = buildRevertSteps(s)
        val anim = steps.first { it.command.contains("window_animation_scale") }.command
        assertTrue(anim.contains("settings put global window_animation_scale 1.0"))
        assertTrue(anim.contains("settings put global transition_animation_scale 1.0"))
        assertTrue(anim.contains("settings put global animator_duration_scale 0.5"))
        assertTrue(steps.any { it.command == "settings put global zen_mode 0" })
    }

    @Test fun newFieldsJson() {
        val c = GameModeConfig(autoRevert = false, animationsOff = true, dnd = true)
        val back = GameModeConfig.decode(c.encode())
        assertFalse(back.autoRevert)
        assertTrue(back.animationsOff)
        assertTrue(back.dnd)
        val old = GameModeConfig.decode("{\"package\":\"a.b\",\"targetFps\":60,\"scale\":0.5}")
        assertTrue(old.autoRevert)
        assertFalse(old.animationsOff)
    }
}

class GameBrightnessCpuTest {
    @get:org.junit.Rule val language = TestLanguage()

    private val policies = listOf(
        CpuPolicy("policy0", 2000000, 2000000),
        CpuPolicy("policy4", 2850000, 2850000),
        CpuPolicy("policy7", 3400000, 3400000),
    )

    @Test fun parse() {
        val p = parseCpuPolicies("policy0 2000000 2000000\nçöp\npolicy7 3400000 3400000\npolicyX 1 2\npolicy4 0 5")
        assertEquals(listOf("policy0", "policy7"), p.map { it.name })
    }

    @Test fun top() {
        assertEquals(listOf("policy7", "policy4"), topPolicies(policies).map { it.name })
    }

    @Test fun dim() {
        val steps = cmds(GameModeConfig(dimScreen = true), brightness = 1000)
        val s = steps.first { it.command.contains("screen_brightness ") }
        assertEquals("settings put system screen_brightness_mode 0; settings put system screen_brightness 600", s.command)
        assertFalse(cmds(GameModeConfig(dimScreen = true)).any { it.command.contains("brightness") })
    }

    @Test fun cap() {
        val cmd = cmds(GameModeConfig(cpuCap = true), cpu = policies).first { it.command.contains("scaling_max_freq") }.command
        assertTrue(cmd.contains("echo 2380000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"))
        assertTrue(cmd.contains("echo 1995000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq"))
        assertFalse(cmd.contains("policy0"))
    }

    @Test fun revert() {
        val s = GameSession(
            packageName = DEF,
            settings = mapOf("system:screen_brightness" to "1000", "system:screen_brightness_mode" to "1"),
            touchedGameManager = false,
            cpuMax = mapOf("policy7" to "3400000", "policy4" to "2850000"),
            startedAtMs = 0,
        )
        val steps = buildRevertSteps(s)
        assertEquals(
            "settings put system screen_brightness 1000; settings put system screen_brightness_mode 1",
            steps.first { it.command.contains("screen_brightness ") }.command,
        )
        val c = steps.first { it.command.contains("scaling_max_freq") }.command
        assertTrue(c.contains("echo 3400000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"))
        assertTrue(c.contains("echo 2850000 > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq"))
    }

    @Test fun badCpuMax() {
        val s = GameSession.fromJson(
            mapOf(
                "package" to DEF,
                "settings" to emptyMap<String, Any?>(),
                "cpuMax" to mapOf("policy7; reboot" to "3400000", "policy4" to "1; reboot"),
            ),
        )
        assertTrue(s.cpuMax.isEmpty())
    }

    @Test fun guard() {
        val s = GameSession(
            packageName = DEF, settings = mapOf("system:screen_brightness" to "1000"),
            touchedGameManager = false, cpuMax = mapOf("policy7" to "3400000"), startedAtMs = 0,
        )
        val c = buildGuardCommands(s).joinToString("\n")
        assertTrue(c.contains("echo 1870000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"))
        assertTrue(c.contains("settings put system screen_brightness 350"))
        val none = GameSession(packageName = DEF, settings = emptyMap(), touchedGameManager = false, startedAtMs = 0)
        assertTrue(buildGuardCommands(none).isEmpty())
    }
}

class AnimationHealTest {
    @get:org.junit.Rule val language = TestLanguage()

    @Test fun zeroOrMissingBecomesDefault() {
        assertEquals("1.0", healAnimationValue(null))
        assertEquals("1.0", healAnimationValue("0"))
        assertEquals("1.0", healAnimationValue("0.0"))
        assertEquals("0.5", healAnimationValue("0.5"))
    }

    @Test fun safeResetWorksWithoutSession() {
        val c = buildSafeResetSteps(DEF).map { it.command }
        assertTrue(c.any { it.contains("settings put global window_animation_scale 1.0") })
        assertTrue(c.any { it.contains("settings delete system peak_refresh_rate") })
        assertTrue(c.any { it.startsWith("cmd game reset $DEF") })
        assertTrue(c.any { it == "wm size reset; wm density reset" })
    }
}

class GamePowerTest {
    @get:org.junit.Rule val language = TestLanguage()

    @Test fun coolProfile() {
        val c = cmds(GameModeConfig(powerProfile = GamePower.Battery)).map { it.command }
        assertTrue(c.any { it.startsWith("cmd game set --mode 3 ") })
        assertTrue(c.any { it == "cmd game mode battery $DEF" })
    }

    @Test fun batterySaver() {
        assertFalse(cmds(GameModeConfig()).any { it.command.contains("low_power") })
        assertTrue(cmds(GameModeConfig(batterySaver = true)).any { it.command == "settings put global low_power 1" && it.optional })
        val s = GameSession(packageName = DEF, settings = mapOf("global:low_power" to "0"), touchedGameManager = false, startedAtMs = 0)
        assertTrue(buildRevertSteps(s).any { it.command == "settings put global low_power 0" })
    }

    @Test fun guardLowPower() {
        val s = GameSession(packageName = DEF, settings = mapOf("global:low_power" to null), touchedGameManager = false, startedAtMs = 0)
        assertTrue("settings put global low_power 1" in buildGuardCommands(s))
    }

    @Test fun fieldsPersist() {
        val c = GameModeConfig(powerProfile = GamePower.Battery, batterySaver = true, guardTempC = 38)
        val back = GameModeConfig.decode(c.encode())
        assertEquals(GamePower.Battery, back.powerProfile)
        assertTrue(back.batterySaver)
        assertEquals(38, back.guardTempC)
        assertEquals(40, GameModeConfig.fromJson(mapOf("guardTempC" to 77.0)).guardTempC)
    }

    @Test fun icePreset() {
        val ice = kGamePresets.first { it.id == "ice" }
        val cfg = ice.applyTo(GameModeConfig())
        assertEquals(GamePower.Battery, cfg.powerProfile)
        assertTrue(cfg.batterySaver)
        assertEquals(38, cfg.guardTempC)
        assertEquals(0.4, cfg.scale, 0.0)
        assertTrue(ice.matches(cfg))
        assertEquals(kGamePresets.size, kGamePresets.map { it.id }.toSet().size)
        assertNotNull(kGamePresets)
    }
}
