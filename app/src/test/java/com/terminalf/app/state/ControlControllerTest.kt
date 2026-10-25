package com.terminalf.app.state

import com.terminalf.app.core.OptimizationProfile
import com.terminalf.app.core.ShellIdentity
import com.terminalf.app.core.ShellResult
import com.terminalf.app.core.Tweak
import com.terminalf.app.core.TweakCategory
import com.terminalf.app.core.TweakState
import com.terminalf.app.core.isSafePackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRoot(private val available: Boolean = true) : RootAccess {
    val commands = ArrayList<String>()
    var responder: ((String) -> String)? = null
    override val identity: ShellIdentity? =
        if (available) ShellIdentity(uid = 0, name = "root", raw = "uid=0(root)") else null

    override suspend fun hasRoot(forceRecheck: Boolean): Boolean = available

    override suspend fun exec(command: String): ShellResult {
        commands.add(command)
        return ShellResult(responder?.invoke(command) ?: "", 0)
    }
}

private class FakeShizuku : ShizukuAccess {
    override val identity: ShellIdentity? = null
    override suspend fun probe() = ShizukuProbe(running = false, permitted = false, error = "Shizuku servisi çalışmıyor.")
    override suspend fun isRunning() = false
    override suspend fun hasPermission() = false
    override suspend fun requestPermission() = false
    override suspend fun exec(command: String): ShellResult = throw ShizukuUnavailableException("yok")
}

private class FakeCatalog(private val tweaks: List<Tweak>) : TweakCatalog {
    override suspend fun loadTweaks(): List<Tweak> = tweaks
}

private val animations = Tweak(
    id = "anim",
    title = "Animasyonlar",
    description = "",
    category = TweakCategory.Performance,
    applyCommand = "settings put global window_animation_scale 0",
    revertCommand = "settings put global window_animation_scale 1",
    checkCommand = "settings get global window_animation_scale",
    expectedOutput = "0",
)

private fun controller(root: RootAccess, tweaks: List<Tweak>) =
    ControlController(root, FakeShizuku(), FakeCatalog(tweaks), CoroutineScope(Dispatchers.Unconfined))

class ControlControllerTest {
    @Test fun needsSetupWithoutAccess() = runBlocking {
        val c = controller(FakeRoot(false), emptyList())
        c.init()
        assertEquals(ControlPhase.NeedsSetup, c.phase)
        assertFalse(c.ready)
        assertNotNull(c.error)
        assertFalse(c.setTweak(animations, apply = true).ok)
    }

    @Test fun rootAutoSelectedAndStatesRead() = runBlocking {
        val root = FakeRoot().also { it.responder = { cmd -> if (cmd.startsWith("settings get")) "0" else "" } }
        val c = controller(root, listOf(animations))
        c.init()
        c.refreshStatuses()
        assertTrue(c.ready)
        assertEquals(AccessPath.Root, c.path)
        assertEquals(TweakState.On, c.stateOf("anim"))
    }

    @Test fun noSuccessWhenValueUnchanged() = runBlocking {
        val root = FakeRoot().also { it.responder = { cmd -> if (cmd.startsWith("settings get")) "1" else "" } }
        val c = controller(root, listOf(animations))
        c.init()
        val outcome = c.setTweak(animations, apply = true)
        assertFalse(outcome.ok)
        assertTrue(animations.applyCommand in root.commands)
    }

    @Test fun profileSkipsAlreadyOn() = runBlocking {
        val root = FakeRoot().also { it.responder = { cmd -> if (cmd.startsWith("settings get")) "0" else "" } }
        val c = controller(root, listOf(animations))
        c.init()
        c.refreshStatuses()
        val profile = OptimizationProfile("t", "Test", "", "bolt", listOf("anim"))
        val result = c.runProfile(profile, apply = true)
        assertEquals(1, result.skipped)
        assertEquals(0, result.applied)
    }

    @Test fun packageValidationBlocksInjection() = runBlocking {
        assertTrue(isSafePackage("com.example.app"))
        assertFalse(isSafePackage("com.example.app; rm -rf /"))
        assertFalse(isSafePackage("\$(reboot)"))
        val root = FakeRoot()
        val c = controller(root, emptyList())
        c.init()
        val out = c.forceStop("x; reboot")
        assertFalse(out.ok)
        assertTrue(root.commands.none { it.contains("reboot") })
    }
}
