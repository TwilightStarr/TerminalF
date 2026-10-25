package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun session(pkg: String = DEFAULT_GAME_PACKAGE) = GameSession(
    packageName = pkg,
    settings = mapOf("system:peak_refresh_rate" to "120.0", "system:min_refresh_rate" to null),
    touchedGameManager = true,
    changedScreen = true,
    startedAtMs = 1,
)

class GameWatchdogTest {
    @Test fun scriptFilled() {
        val script = buildWatchdogScript(session())
        assertTrue(script.contains(kWatchdogTag))
        assertTrue(script.contains("PKG=$DEFAULT_GAME_PACKAGE"))
        assertTrue(script.contains("PIDF=$kWatchdogPid"))
        assertTrue(script.contains("MARK=$kWatchdogMark"))
        assertFalse("doldurulmamış yer tutucu var", script.contains("@"))
    }

    @Test fun revertCommandsInScript() {
        val lines = buildWatchdogScript(session()).split("\n")
        val wm = lines.first { it.contains("wm size reset") }
        assertTrue(wm.contains("|| OK=0"))
        val settings = lines.first { it.contains("settings put system peak_refresh_rate 120.0") }
        assertTrue(settings.contains("settings delete system min_refresh_rate"))
        val game = lines.first { it.contains("cmd game reset") }
        assertFalse(game.contains("OK=0"))
    }

    @Test fun noSingleQuotesOrDoubleSemicolon() {
        val script = buildWatchdogScript(session())
        assertFalse(script.contains("'"))
        assertFalse(script.contains(";;"))
    }

    @Test fun exitsWithoutRevertWhenNeverSeen() {
        val script = buildWatchdogScript(session())
        val idx = script.indexOf("exit 0")
        assertTrue(idx > 0)
        assertTrue(script.indexOf("OK=1") > idx)
    }

    @Test fun invalidPackageRejected() {
        assertTrue(buildWatchdogScript(session("a b; reboot")).isEmpty())
    }

    @Test fun parameters() {
        val script = buildWatchdogScript(session(), pollSeconds = 5, missLimit = 7, giveUpSeconds = 99)
        assertTrue(script.contains("sleep 5"))
        assertTrue(script.contains("-ge 7"))
        assertTrue(script.contains("-ge 99"))
    }

    @Test fun commands() {
        val cmd = buildWatchdogLaunch(buildWatchdogScript(session()))
        assertTrue(cmd.startsWith("nohup sh -c '"))
        assertTrue(cmd.endsWith("</dev/null >/dev/null 2>&1 &"))
        val kill = buildWatchdogKill()
        assertTrue(kill.contains(kWatchdogPid))
        assertTrue(kill.contains("grep -q $kWatchdogTag /proc/"))
        assertTrue(kill.contains("kill"))
        val status = buildWatchdogStatus()
        assertTrue(status.contains("echo alive"))
        assertTrue(status.contains("echo dead"))
        assertTrue(status.contains("mark:"))
    }

    @Test fun statusParsing() {
        val a = parseWatchdogStatus("alive\nmark:none")
        assertTrue(a.alive)
        assertFalse(a.revertedOk)
        assertFalse(a.revertFailed)
        val b = parseWatchdogStatus("dead\nmark:ok")
        assertFalse(b.alive)
        assertTrue(b.revertedOk)
        assertTrue(parseWatchdogStatus("dead\nmark:fail").revertFailed)
        val c = parseWatchdogStatus("")
        assertFalse(c.alive)
        assertEquals("none", c.mark)
        assertEquals("none", parseWatchdogStatus("mark:saçma").mark)
    }

    @Test fun guard() {
        val s = GameSession(
            packageName = DEFAULT_GAME_PACKAGE,
            settings = mapOf("system:screen_brightness" to "1000"),
            touchedGameManager = true,
            cpuMax = mapOf("policy7" to "3400000"),
            startedAtMs = 1,
        )
        val script = buildWatchdogScript(s)
        assertTrue(script.contains("echo GUARD"))
        assertTrue(script.contains("echo 1870000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"))
        assertTrue(script.contains("settings put system screen_brightness 350"))
        assertFalse(script.contains("@"))
        assertFalse(script.contains("'"))
    }

    @Test fun tempThreshold() {
        val s = session()
        assertTrue(buildWatchdogScript(s, guardTempC = 40).contains("[ 400 -gt 0 ]"))
        assertTrue(buildWatchdogScript(s).contains("[ 0 -gt 0 ]"))
        assertTrue(buildWatchdogScript(s, guardTempC = 40).contains("dumpsys battery"))
    }
}

private fun latency(frames: Int = 11, stepNs: Long = 100_000_000L): String {
    val lines = ArrayList<String>()
    lines.add("16666666")
    for (i in 0 until frames) {
        val t = 1_000_000_000_000L + i * stepNs
        lines.add("$t $t $t")
    }
    lines.add("9223372036854775807 9223372036854775807 9223372036854775807")
    lines.add("0 0 0")
    return lines.joinToString("\n")
}

class GameProbeTest {
    @Test fun focusedPackage() {
        val out = "  mCurrentFocus=Window{4f3a1b2 u0 com.playdigious.ninekings/com.unity3d.player.UnityPlayerActivity}"
        assertEquals("com.playdigious.ninekings", parseFocusedPackage(out))
        assertNull(parseFocusedPackage("  mCurrentFocus=null"))
        assertNull(parseFocusedPackage(""))
        assertNull(parseFocusedPackage("bambaşka çıktı"))
    }

    @Test fun layerPick() {
        val list = "Background for SurfaceView[com.playdigious.ninekings/com.unity3d.player.UnityPlayerActivity](BLAST)#123\n" +
            "SurfaceView[com.playdigious.ninekings/com.unity3d.player.UnityPlayerActivity](BLAST)#124\n" +
            "com.playdigious.ninekings/com.unity3d.player.UnityPlayerActivity#122\n" +
            "Bounds for - com.playdigious.ninekings/com.unity3d.player.UnityPlayerActivity#125\n" +
            "StatusBar#5"
        assertTrue(pickGameLayer(list, "com.playdigious.ninekings")!!.startsWith("SurfaceView[com.playdigious.ninekings"))
        assertEquals(
            "com.playdigious.ninekings/x.Activity#1",
            pickGameLayer("com.playdigious.ninekings/x.Activity#1\nStatusBar#5", "com.playdigious.ninekings"),
        )
        assertNull(pickGameLayer("StatusBar#5\nNavigationBar0#6", "com.playdigious.ninekings"))
    }

    @Test fun frameRate() {
        val s = parseLatency(latency())!!
        assertEquals(11, s.frames)
        assertEquals(10.0, s.fps, 0.01)
        assertEquals(100.0, s.worstFrameMs, 0.01)
        assertEquals(90.0, parseLatency(latency(60, 11_111_111L))!!.fps, 0.5)
        assertNull(parseLatency(""))
        assertNull(parseLatency("16666666\n1 1 1"))
        assertNull(parseLatency(latency(frames = 3)))
        assertNull(fpsFromWindow(3, 0, 1_000_000_000))
        assertNull(fpsFromWindow(20, 5, 5))
        assertEquals(10.0, fpsFromWindow(20, 1_000_000_000_000L, 1_001_900_000_000L)!!, 0.01)
    }

    @Test fun thermal() {
        assertEquals(2, parseThermalStatus("Thermal Status: 2\nOther: 1"))
        assertNull(parseThermalStatus("hiçbir şey"))
        assertEquals("Normal", thermalLabel(0))
        assertEquals("Orta", thermalLabel(2))
        assertEquals("Kritik", thermalLabel(5))
    }

    @Test fun watchLines() {
        val s = parseWatchLine("1700000010 20 1000000000000 1001900000000 2 3000000 3400000")!!
        assertEquals(10.0, s.fps!!, 0.01)
        assertEquals(2, s.thermal)
        assertEquals(0.882, s.cpuRatio!!, 0.001)
        val e = parseWatchLine("1700000010 - - - - - -")!!
        assertNull(e.fps)
        assertNull(e.thermal)
        assertNull(e.cpuRatio)
        assertNull(parseWatchLine("merhaba"))
        assertNull(parseWatchLine("x 1 2 3 4 5 6"))
    }

    @Test fun sessionSummary() {
        val log = "1700000000 20 1000000000000 1001900000000 1 3000000 3400000\n" +
            "1700000010 20 1000000000000 1000950000000 2 3200000 3400000\n" +
            "çöp satır\n" +
            "1700000020 - - - 3 - -"
        val s = parseWatchLog(log)!!
        assertEquals(3, s.samples)
        assertEquals(20, s.durationSec)
        assertEquals(15.0, s.avgFps!!, 0.01)
        assertEquals(10.0, s.minFps!!, 0.01)
        assertEquals(3, s.maxThermal)
        assertEquals(0.912, s.avgCpuRatio!!, 0.001)
        val g = parseWatchLog(
            "1700000000 20 1000000000000 1001900000000 2 3000000 3400000\nGUARD 1700000010\n" +
                "1700000020 20 1000000000000 1001900000000 2 3000000 3400000",
        )!!
        assertTrue(g.guardTriggered)
        assertEquals(2, g.samples)
        assertFalse(parseWatchLog("1700000000 - - - - - -")!!.guardTriggered)
        assertNull(parseWatchLog(""))
        assertNull(parseWatchLog("çöp"))
    }

    private fun stats(fps: Double? = 10.0, thermal: Int? = 0, ratio: Double? = null) = SessionStats(
        samples = 10, durationSec = 100, avgFps = fps, minFps = fps, maxThermal = thermal, avgCpuRatio = ratio,
    )

    @Test fun diagnosis() {
        assertEquals(DiagnosisKind.OnTarget, diagnose(stats(fps = 85.0), 90).kind)
        assertEquals(DiagnosisKind.Thermal, diagnose(stats(thermal = 3, ratio = 0.9), 90).kind)
        assertEquals(DiagnosisKind.CpuBound, diagnose(stats(ratio = 0.92), 90).kind)
        assertEquals(DiagnosisKind.GpuBound, diagnose(stats(ratio = 0.4), 90).kind)
        assertEquals(DiagnosisKind.Unknown, diagnose(stats(), 90).kind)
        assertEquals(DiagnosisKind.Unknown, diagnose(stats(fps = null), 90).kind)
        assertTrue(diagnose(stats(ratio = 0.92), 90).title.contains("olası"))
        assertTrue(diagnose(stats(thermal = 3), 90).title.contains("olası"))
        assertNotNull(diagnose(stats(), 90))
    }
}
