package com.terminalf.app.ui.sheets

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.state.Notifier
import com.terminalf.app.ui.components.AppLogo
import com.terminalf.app.ui.components.PrimaryButton
import com.terminalf.app.ui.components.SheetHost
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * İlk açılışta bir kez gösterilen kısa karşılama. Bildirim izni burada, nedeniyle birlikte
 * istenir (bağlamsız izin penceresi yerine).
 */
suspend fun SheetHost.showWelcomeSheet(notifier: Notifier) {
    show<Unit>(key = "welcome") { close -> WelcomeBody(notifier = notifier, onDone = { close(null) }) }
}

@Composable
private fun WelcomeBody(notifier: Notifier, onDone: () -> Unit) {
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun start(askPermission: Boolean) {
        scope.launch {
            if (askPermission) {
                busy = true
                try {
                    notifier.requestPermission()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // İzin penceresi açılamazsa yine de karşılamayı kapat.
                }
            }
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 20.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AppLogo(size = 84.dp)
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "TerminalF'e hoş geldiniz",
                color = c.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(20.dp))
        WelcomePoint(
            icon = Icons.Rounded.MonitorHeart,
            title = "Gerçek ölçümler",
            text = "RAM, depolama, pil ve sıcaklık doğrudan cihazdan okunur.",
        )
        WelcomePoint(
            icon = Icons.Rounded.Bolt,
            title = "Tek dokunuşla optimizasyon",
            text = "Hazır profiller ve akıllı öneriler karmaşık ayarları sizin yerinize halleder.",
        )
        WelcomePoint(
            icon = Icons.Rounded.NotificationsActive,
            title = "Haberdar olun",
            text = "İşlem bitince ya da telefon ısınınca, pil azalınca, depolama dolunca bildirim alın.",
        )
        WelcomePoint(
            icon = Icons.Rounded.Lock,
            title = "Verileriniz cihazda kalır",
            text = "Hiçbir veri dışarı gönderilmez. Bildirimleri istediğiniz zaman Cihaz > Bildirimler ve Uyarılar bölümünden kapatabilirsiniz.",
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            label = "Bildirimlere İzin Ver ve Başla",
            icon = Icons.Rounded.Notifications,
            busy = busy,
            onClick = { start(askPermission = true) },
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = { start(askPermission = false) },
                enabled = !busy,
            ) {
                Text("Şimdilik geç", color = c.muted)
            }
        }
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, title: String, text: String) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(shape)
                .background(c.primary.faded(0.10f), shape)
                .border(1.dp, c.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(text, color = c.muted, fontSize = 13.sp, lineHeight = (13 * 1.35).sp)
        }
    }
}
