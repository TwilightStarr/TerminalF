package com.terminalf.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import com.terminalf.app.data.applySavedPreferences
import com.terminalf.app.state.HapticKind
import com.terminalf.app.state.HapticPerformer
import com.terminalf.app.state.Haptics
import com.terminalf.app.ui.AppRoot
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    private var permissionResult: CompletableDeferred<Boolean>? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionResult?.complete(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        val container = (application as TerminalApplication).container
        container.activityBridge.attach(this)
        container.activityBridge.notificationPermissionRequester = {
            if (Build.VERSION.SDK_INT >= 33) {
                val d = CompletableDeferred<Boolean>()
                permissionResult = d
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                d.await()
            } else {
                true
            }
        }

        val decor: View = window.decorView
        Haptics.performer = HapticPerformer { kind ->
            val constant = when (kind) {
                HapticKind.Tap -> HapticFeedbackConstants.CLOCK_TICK
                HapticKind.Success -> if (Build.VERSION.SDK_INT >= 30) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                HapticKind.Warning -> HapticFeedbackConstants.LONG_PRESS
            }
            decor.performHapticFeedback(constant)
        }

        applySavedPreferences(container.store, container.activityBridge, container.brightness)

        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                AppRoot(container)
            }
        }
    }

    override fun onDestroy() {
        (application as TerminalApplication).container.activityBridge.detach(this)
        super.onDestroy()
    }
}
