package com.terminalf.app.ui.sheets

import com.terminalf.app.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.LocalContainer
import com.terminalf.app.state.AccessPath
import com.terminalf.app.state.ControlController
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.PrimaryButton
import com.terminalf.app.ui.components.SheetHost
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded
import kotlinx.coroutines.launch

private const val ShizukuPackage = "moe.shizuku.privileged.api"
private const val ShizukuUrl = "https://shizuku.rikka.app/download/"

/**
 * "Tam Kontrolü Etkinleştir" sihirbazı. Kullanıcı yol seçmez: kök varsa otomatik kullanılır,
 * yoksa Shizuku adım adım hazırlanır.
 */
suspend fun SheetHost.showControlSetup(control: ControlController) {
    show<Unit>(key = "control_setup") { close -> SetupBody(control = control, onClose = { close(null) }) }
}

@Composable
private fun SetupBody(control: ControlController, onClose: () -> Unit) {
    control.observe()
    val c = AppTheme.colors
    val container = LocalContainer.current
    val toast = LocalToaster.current
    val scope = rememberCoroutineScope()

    val running = control.shizukuRunning || control.path == AccessPath.Root
    val permitted = control.shizukuPermitted || control.path == AccessPath.Root
    val ready = control.ready
    val error = control.error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 14.dp, end = 22.dp, bottom = 18.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Enable Full Control", "Tam Kontrolü Etkinleştir"),
            color = c.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "To truly manage the phone (stop apps, clear caches, optimize system settings) system permission is required. Root is used automatically if available; otherwise the free Shizuku app is enough. No computer needed.",
                "Telefonu gerçekten yönetebilmek (uygulamaları durdurmak, önbellek temizlemek, sistem ayarlarını optimize etmek) için sistem yetkisi gerekir. Root varsa otomatik kullanılır; yoksa ücretsiz Shizuku uygulaması yeterlidir. Bilgisayar gerekmez.",
            ),
            color = c.muted,
            lineHeight = (14 * 1.45).sp,
        )
        Spacer(Modifier.height(18.dp))
        SetupStep(
            index = 1,
            done = running,
            title = tr("Shizuku is running", "Shizuku çalışıyor"),
            detail = tr("Open Shizuku and use the \"Start via Wireless debugging\" option (Android 11+).", "Shizuku'yu açın ve \"Kablosuz hata ayıklama ile başlat\" seçeneğini kullanın (Android 11+)."),
        )
        SetupStep(
            index = 2,
            done = permitted,
            title = tr("Grant permission to TerminalF", "TerminalF'e izin ver"),
            detail = tr("The button below opens the permission dialog.", "Aşağıdaki düğme izin penceresini açar."),
        )
        SetupStep(
            index = 3,
            done = ready && !control.verifying,
            title = tr("Test command succeeded", "Test komutu başarılı"),
            detail = if (control.verifying) {
                tr("Verifying in the background; you can use it right away.", "Arka planda doğrulanıyor; beklemeden kullanabilirsin.")
            } else {
                tr("Verified by running a real command.", "Gerçek bir komut çalıştırılarak doğrulanır.")
            },
            last = true,
        )
        if (!ready && error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = c.warn, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        if (ready) {
            PrimaryButton(
                label = tr("Ready — Close", "Hazır — Kapat"),
                icon = Icons.Rounded.Check,
                onClick = onClose,
            )
        } else if (!running) {
            PrimaryButton(
                label = tr("Open Shizuku", "Shizuku'yu Aç"),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = {
                    container.appsService.launch(ShizukuPackage)
                    toast.show(tr("Opening Shizuku… If it does not open, download it first.", "Shizuku açılıyor… Açılmazsa önce indirin."))
                },
            )
        } else {
            PrimaryButton(
                label = tr("Grant Permission & Finish", "İzin Ver ve Bitir"),
                icon = Icons.Rounded.LockOpen,
                busy = control.settingUp,
                onClick = { scope.launch { control.setUp() } },
            )
        }
        if (!ready) {
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SoftButton(
                    label = tr("Download Shizuku", "Shizuku'yu İndir"),
                    icon = Icons.Rounded.Download,
                    onClick = { container.catalog.openUrl(ShizukuUrl) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SoftButton(
                    label = tr("Check Again", "Yeniden Denetle"),
                    icon = Icons.Rounded.Refresh,
                    onClick = if (control.checking) null else ({ scope.launch { control.detect() } }),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SetupStep(index: Int, done: Boolean, title: String, detail: String, last: Boolean = false) {
    val c = AppTheme.colors
    val color = if (done) c.good else c.muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.faded(0.16f), CircleShape)
                .border(1.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            } else {
                Text("$index", color = color, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(detail, color = c.muted, fontSize = 13.sp, lineHeight = (13 * 1.35).sp)
        }
    }
}
