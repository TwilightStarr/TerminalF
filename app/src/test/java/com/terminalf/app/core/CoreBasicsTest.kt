package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FormatUtilsTest {
    @Test fun bytesUnderOneKbStayInBytes() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test fun kbMbGb() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        assertEquals("5.0 MB", formatBytes(5 * 1024 * 1024))
        assertEquals("1.00 GB", formatBytes(1024L * 1024 * 1024))
        assertEquals("2.50 GB", formatBytes(2.5 * 1024 * 1024 * 1024))
    }

    @Test fun durations() {
        assertEquals("< 1 dk", formatDuration(30.seconds))
        assertEquals("< 1 dk", formatDuration(0.seconds))
        assertEquals("42 dk", formatDuration(42.minutes))
        assertEquals("2 sa", formatDuration(2.hours))
        assertEquals("1 sa 24 dk", formatDuration(1.hours + 24.minutes))
    }

    @Test fun fixedIgnoresDefaultLocale() {
        val old = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale("tr", "TR"))
        try {
            assertEquals("1.5", fixed(1.5, 1))
        } finally {
            java.util.Locale.setDefault(old)
        }
    }
}

class SystemParsersTest {
    @Test fun memInfo() {
        val sample = "MemTotal:        3800000 kB\nMemFree:          200000 kB\n" +
            "MemAvailable:    1900000 kB\nBuffers:           10000 kB\n"
        val info = parseMemInfo(sample)!!
        assertEquals(3800000L * 1024, info.totalBytes)
        assertEquals(1900000L * 1024, info.availableBytes)
        assertEquals(0.5, info.usedRatio, 0.001)
        assertEquals(250L * 1024, parseMemInfo("MemTotal: 1000 kB\nMemFree: 250 kB\n")!!.availableBytes)
        assertNull(parseMemInfo(""))
        assertNull(parseMemInfo("foo"))
    }

    @Test fun df() {
        val out = "Filesystem     1K-blocks     Used Available Use% Mounted on\n" +
            "/dev/block/dm-9 113000000 56500000  56000000  51% /data\n"
        val disk = parseDfOutput(out)!!
        assertEquals(113000000L * 1024, disk.totalBytes)
        assertEquals(56500000L * 1024, disk.usedBytes)
        assertEquals(0.5, disk.usedRatio, 0.001)
        assertNull(parseDfOutput("Filesystem 1K-blocks"))
        assertNull(parseDfOutput("a\nb c"))
        assertNull(parseDfOutput(""))
    }

    @Test fun uptime() {
        assertEquals(3601.seconds, parseUptime("3600.55 12345.00"))
        assertNull(parseUptime("abc"))
    }

    @Test fun packageSafety() {
        assertTrue(isProtectedPackage("android"))
        assertTrue(isProtectedPackage("com.android.systemui"))
        assertTrue(isProtectedPackage("com.example.terminal_flutter"))
        assertFalse(isProtectedPackage("com.facebook.katana"))
        assertEquals(
            listOf("com.a.app", "com.b.app"),
            parsePackageList("package:com.b.app\r\npackage:com.a.app\nWarning: foo\n"),
        )
    }
}

class AlertRulesTest {
    private val t = AlertThresholds()

    @Test fun firesOnceThenStaysQuiet() {
        var r = evaluateAlerts(t, emptySet(), temperatureC = 44.0)
        assertEquals(listOf(AlertKind.Heat), r.fire.map { it.kind })
        r = evaluateAlerts(t, r.active, temperatureC = 45.0)
        assertTrue(r.fire.isEmpty())
        assertEquals(setOf(AlertKind.Heat), r.active)
    }

    @Test fun hysteresis() {
        var r = evaluateAlerts(t, setOf(AlertKind.Heat), temperatureC = 41.0)
        assertEquals(setOf(AlertKind.Heat), r.active)
        r = evaluateAlerts(t, r.active, temperatureC = 38.0)
        assertTrue(r.active.isEmpty())
        r = evaluateAlerts(t, r.active, temperatureC = 43.0)
        assertEquals(1, r.fire.size)
    }

    @Test fun lowBatteryOnlyWhenNotCharging() {
        var r = evaluateAlerts(t, emptySet(), batteryPercent = 10, charging = true)
        assertTrue(r.fire.isEmpty())
        r = evaluateAlerts(t, emptySet(), batteryPercent = 10)
        assertEquals(AlertKind.LowBattery, r.fire.single().kind)
        r = evaluateAlerts(t, r.active, batteryPercent = 10, charging = true)
        assertTrue(r.active.isEmpty())
    }

    @Test fun storageAndMemory() {
        val r = evaluateAlerts(t, emptySet(), storageUsedRatio = 0.93, memoryUsedRatio = 0.95)
        val kinds = r.fire.map { it.kind }
        assertTrue(kinds.containsAll(listOf(AlertKind.LowStorage, AlertKind.HighMemory)))
        val calm = evaluateAlerts(t, emptySet(), storageUsedRatio = 0.5, memoryUsedRatio = 0.6)
        assertTrue(calm.fire.isEmpty())
    }

    @Test fun unreadableMeasurementKeepsState() {
        val r = evaluateAlerts(t, setOf(AlertKind.Heat))
        assertEquals(setOf(AlertKind.Heat), r.active)
        assertTrue(r.fire.isEmpty())
    }

    @Test fun customThresholds() {
        val strict = AlertThresholds(temperatureC = 36.0, batteryPercent = 30)
        val r = evaluateAlerts(strict, emptySet(), temperatureC = 37.0, batteryPercent = 25)
        assertTrue(r.fire.map { it.kind }.containsAll(listOf(AlertKind.Heat, AlertKind.LowBattery)))
    }
}

class AdvisorTest {
    @Test fun scores() {
        assertEquals(100, computeHealthScore())
        assertEquals(
            100,
            computeHealthScore(ramUsedRatio = 0.5, storageUsedRatio = 0.5, batteryPercent = 80, temperatureCelsius = 32.0),
        )
        assertEquals(75, computeHealthScore(ramUsedRatio = 1.0))
        assertEquals(100, computeHealthScore(batteryPercent = 5, charging = true))
        assertTrue(computeHealthScore(batteryPercent = 5, charging = false) < 100)
        val worst = computeHealthScore(1.0, 1.0, 0, false, 80.0)
        assertTrue(worst in 0..100)
        assertTrue(worst < 30)
    }

    @Test fun labels() {
        assertEquals("Mükemmel", healthLabel(95))
        assertEquals("İyi", healthLabel(75))
        assertEquals("Dikkat gerekiyor", healthLabel(50))
        assertEquals("Kritik", healthLabel(10))
    }

    @Test fun advice() {
        assertTrue(
            buildAdvice(0.4, 0.4, 90, false, 30.0, controlReady = true).isEmpty(),
        )
        assertEquals(AdviceAction.EnableControl, buildAdvice(controlReady = false).single().action)
        val list = buildAdvice(ramUsedRatio = 0.92, storageUsedRatio = 0.9, controlReady = true)
        val actions = list.map { it.action }
        assertTrue(AdviceAction.FreeMemory in actions)
        assertTrue(AdviceAction.CleanStorage in actions)
        assertEquals(AdviceLevel.Critical, list.first().level)
        assertTrue(AdviceAction.SaveBattery in buildAdvice(batteryPercent = 12, controlReady = true).map { it.action })
        assertTrue(buildAdvice(batteryPercent = 12, charging = true, controlReady = true).isEmpty())
    }
}

class ShellUtilsTest {
    @Test fun idOutput() {
        val id = parseIdOutput("uid=2000(shell) gid=2000(shell) groups=2000(shell)")
        assertNotNull(id)
        assertEquals(2000, id!!.uid)
        assertEquals("shell", id.name)
        assertFalse(id.isRoot)
        assertEquals("shell", id.label)
        val root = parseIdOutput("uid=0(root) gid=0(root) groups=0(root)")!!
        assertTrue(root.isRoot)
        assertEquals("root", root.label)
        assertNull(parseIdOutput(""))
        assertNull(parseIdOutput("Permission denied"))
    }

    @Test fun wrapping() {
        val wrapped = wrapForShell("a; b")
        assertTrue(wrapped.startsWith("sh -c "))
        assertTrue(wrapped.contains("( a; b ) 2>&1"))
        assertTrue(wrapped.contains(EXIT_MARKER))
        assertEquals("'it'\\''s'", shellQuote("it's"))
    }

    @Test fun parseWrapped() {
        val ok = parseWrappedOutput("merhaba\n$EXIT_MARKER:0\n")
        assertEquals("merhaba", ok.output)
        assertEquals(0, ok.exitCode)
        assertTrue(ok.exitCodeKnown)
        assertFalse(ok.failedByExitCode)
        val bad = parseWrappedOutput("hata metni\n$EXIT_MARKER:1")
        assertEquals(1, bad.exitCode)
        assertTrue(bad.failedByExitCode)
        val none = parseWrappedOutput("sadece çıktı")
        assertNull(none.exitCode)
        assertFalse(none.exitCodeKnown)
        assertFalse(none.failedByExitCode)
    }

    @Test fun failureReasons() {
        assertNull(failureReason(ShellResult("", 0)))
        assertNull(failureReason(ShellResult("Package com.x new state: disabled-user", 0)))
        assertNotNull(failureReason(ShellResult("", 1)))
        assertNotNull(
            failureReason(
                ShellResult(
                    "Exception occurred while executing 'disable-user': java.lang.IllegalArgumentException: Unknown package: com.x",
                    0,
                ),
            ),
        )
        assertNull(failureReason(ShellResult("")))
        assertNotNull(failureReason(ShellResult("Permission denied")))
    }

    @Test fun chain() {
        assertEquals(listOf("a b", "c", "d"), splitCommandChain("a b; c ;; d"))
    }
}

class PalettesTest {
    @Test fun idsUniqueAndEnough() {
        val ids = AppPalettes.all.map { it.id }.toSet()
        assertEquals(AppPalettes.all.size, ids.size)
        assertTrue(AppPalettes.all.size >= 15)
        assertEquals("mono_dark", AppPalettes.DEFAULT_ID)
        assertEquals(AppPalettes.DEFAULT_ID, AppPalettes.byId("yok_boyle_bir_tema").id)
        assertEquals(0xFFF2F2F3L, AppPalettes.monoDark.primary)
    }

    @Test fun logoColors() {
        assertEquals(listOf(0xFFFF5C5CL, 0xFFFF5C5CL), logoColorsFor(AppPalettes.byId("violet"), "red", 0))
        assertEquals(listOf(0xFF123456L, 0xFF123456L), logoColorsFor(AppPalettes.monoDark, LogoStyle.CUSTOM_ID, 0xFF123456L))
        assertEquals(
            listOf(AppPalettes.monoDark.logoA, AppPalettes.monoDark.logoB),
            logoColorsFor(AppPalettes.monoDark, LogoStyle.AUTO_ID, 0),
        )
    }
}
