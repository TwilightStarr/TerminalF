package com.terminalf.app.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.terminalf.app.core.fixed
import com.terminalf.app.state.AppSettings
import com.terminalf.app.state.Notifier
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.RowDivider
import com.terminalf.app.ui.components.SectionHeader
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Bildirimler, uyarı eşikleri ve geri bildirim tercihleri. */
@Composable
fun SettingsPage(settings: AppSettings, notifier: Notifier, onBack: () -> Unit) {
    val s = settings.observe()
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var permitted by remember { mutableStateOf(false) }

    // Açılışta ve kullanıcı sistem ayarlarından izni değiştirip dönünce yeniden oku.
    LifecycleResumeEffect(Unit) {
        scope.launch { permitted = notifier.hasPermission() }
        onPauseOrDispose { }
    }

    suspend fun requestPermission() {
        val ok = notifier.requestPermission()
        permitted = ok
        if (!ok) toast.show("İzin verilmedi. Sistem ayarlarından açabilirsiniz.")
    }

    SubPageFrame(title = "Bildirimler ve Uyarılar", onBack = onBack) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (permitted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = if (permitted) c.good else c.warn,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bildirim izni", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (permitted) "Verildi" else "Verilmedi: bildirim gönderilemez",
                        color = if (permitted) c.good else c.warn,
                        fontSize = 13.sp,
                    )
                }
                if (!permitted) {
                    SoftButton(label = "İzin Ver", onClick = { scope.launch { requestPermission() } })
                }
            }
        }
        SectionHeader("Bildirimler")
        AppCard {
            SwitchRow(
                title = "İşlem sonuçları",
                subtitle = "Optimizasyon bitince, uygulama arka plandaysa bildir.",
                value = s.taskNotifications,
                onChanged = { s.setTaskNotifications(it) },
            )
            RowDivider()
            SwitchRow(
                title = "Sağlık uyarıları",
                subtitle = "Isınma, düşük pil, dolu depolama ve bellek. Uygulama çalışırken izlenir.",
                value = s.healthAlerts,
                onChanged = { s.setHealthAlerts(it) },
            )
            Spacer(Modifier.height(12.dp))
            SoftButton(
                label = "Test Bildirimi Gönder",
                icon = Icons.AutoMirrored.Rounded.Send,
                onClick = {
                    scope.launch {
                        if (!permitted) {
                            requestPermission()
                            if (!permitted) return@launch
                        }
                        notifier.showTest()
                        toast.show("Test bildirimi gönderildi")
                    }
                },
            )
        }
        SectionHeader("Uyarı Eşikleri")
        AppCard {
            SliderRow(
                title = "Sıcaklık",
                valueLabel = "${fixed(s.temperatureC, 0)} °C ve üstü",
                value = s.temperatureC.toFloat(),
                min = 35f,
                max = 50f,
                divisions = 15,
                enabled = s.healthAlerts,
                onChanged = { s.setTemperature(it.toDouble()) },
            )
            SliderRow(
                title = "Depolama doluluğu",
                valueLabel = "%${s.storagePercent} ve üstü",
                value = s.storagePercent.toFloat(),
                min = 70f,
                max = 98f,
                divisions = 28,
                enabled = s.healthAlerts,
                onChanged = { s.setStoragePercent(it.roundToInt()) },
            )
            SliderRow(
                title = "Düşük pil",
                valueLabel = "%${s.batteryPercent} ve altı",
                value = s.batteryPercent.toFloat(),
                min = 5f,
                max = 30f,
                divisions = 25,
                enabled = s.healthAlerts,
                onChanged = { s.setBatteryPercent(it.roundToInt()) },
            )
            SliderRow(
                title = "Bellek (RAM) doluluğu",
                valueLabel = "%${s.memoryPercent} ve üstü",
                value = s.memoryPercent.toFloat(),
                min = 80f,
                max = 98f,
                divisions = 18,
                enabled = s.healthAlerts,
                onChanged = { s.setMemoryPercent(it.roundToInt()) },
            )
        }
        SectionHeader("Geri Bildirim")
        AppCard {
            SwitchRow(
                title = "Dokunsal geri bildirim",
                subtitle = "Sekme geçişi ve işlem sonuçlarında hafif titreşim.",
                value = s.haptics,
                onChanged = { s.setHaptics(it) },
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, value: Boolean, onChanged: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = c.muted, fontSize = 12.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = value, onCheckedChange = onChanged, colors = appSwitchColors())
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    min: Float,
    max: Float,
    divisions: Int,
    enabled: Boolean,
    onChanged: (Float) -> Unit,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(valueLabel, color = c.muted, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChanged,
            valueRange = min..max,
            steps = (divisions - 1).coerceAtLeast(0),
            enabled = enabled,
            colors = appSliderColors(),
        )
    }
}
