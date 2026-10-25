package com.terminalf.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.LocalContainer
import com.terminalf.app.core.Advice
import com.terminalf.app.core.AdviceAction
import com.terminalf.app.core.AdviceLevel
import com.terminalf.app.core.BatteryState
import com.terminalf.app.core.buildAdvice
import com.terminalf.app.core.computeHealthScore
import com.terminalf.app.core.fixed
import com.terminalf.app.core.formatBytes
import com.terminalf.app.core.formatDuration
import com.terminalf.app.core.healthLabel
import com.terminalf.app.core.profileById
import com.terminalf.app.state.BatteryReading
import com.terminalf.app.state.ControlController
import com.terminalf.app.state.LiveStats
import com.terminalf.app.state.TweakOutcome
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.components.AppLogo
import com.terminalf.app.ui.components.FadeSlideIn
import com.terminalf.app.ui.components.HealthRing
import com.terminalf.app.ui.components.LocalSheets
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.PrimaryButton
import com.terminalf.app.ui.components.SectionHeader
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.components.StatTile
import com.terminalf.app.ui.components.StatusPill
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.sheets.showControlSetup
import com.terminalf.app.ui.sheets.showLogSheet
import com.terminalf.app.ui.sheets.showOutcome
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ana Sayfa: cihaz sağlığı, öneriler, canlı durum ve hızlı işlemler. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(live: LiveStats, control: ControlController) {
    live.observe()
    control.observe()
    val c = AppTheme.colors
    val container = LocalContainer.current
    val toast = LocalToaster.current
    val sheets = LocalSheets.current
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun runAction(label: String, action: suspend () -> TweakOutcome) {
        if (running != null) return
        if (!control.ready) {
            sheets.showControlSetup(control)
            return
        }
        running = label
        val outcome: TweakOutcome
        try {
            outcome = action()
            live.refresh()
        } finally {
            running = null
        }
        control.reportDone(label, outcome.message, outcome.ok)
        showOutcome(toast, outcome)
    }

    suspend fun smartOptimize() {
        if (running != null) return
        if (!control.ready) {
            // Yetki yoksa bile yapılabilecek tek gerçek temizlik: kendi önbelleğimiz.
            running = "Akıllı optimizasyon"
            val freed: Long
            try {
                freed = container.storage.clearCache()
            } finally {
                running = null
            }
            toast.show(
                "${formatBytes(freed)} uygulama önbelleği temizlendi. Tam optimizasyon için kontrolü etkinleştirin.",
            )
            sheets.showControlSetup(control)
            return
        }
        running = "Akıllı optimizasyon"
        val a: TweakOutcome
        val b: TweakOutcome
        try {
            a = control.freeMemory()
            b = control.trimAllCaches()
            live.refresh()
        } finally {
            running = null
        }
        val allOk = a.ok && b.ok
        val message = if (allOk) {
            "Optimizasyon tamamlandı: bellek boşaltıldı, önbellekler temizlendi."
        } else {
            "Optimizasyon kısmen tamamlandı. Ayrıntılar için günlüğe bakın."
        }
        control.reportDone("Akıllı optimizasyon", message, allOk)
        toast.show(message)
    }

    suspend fun onAdvice(action: AdviceAction) {
        when (action) {
            AdviceAction.FreeMemory, AdviceAction.CoolDown -> runAction("Hızlandır") { control.freeMemory() }
            AdviceAction.CleanStorage -> runAction("Önbellek") { control.trimAllCaches() }
            AdviceAction.SaveBattery -> {
                if (!control.ready) {
                    sheets.showControlSetup(control)
                    return
                }
                val result = control.runProfile(profileById("battery")!!, apply = true)
                if (result.applied + result.failed > 0) {
                    control.reportDone("Pil Tasarrufu", result.summary, result.failed == 0)
                }
                toast.show("Pil Tasarrufu: ${result.summary}")
            }
            AdviceAction.EnableControl -> sheets.showControlSetup(control)
        }
    }

    val snap = live.snapshot
    val battery = snap.battery
    val ram = snap.system.memory
    val disk = snap.system.disk
    val charging = battery.state == BatteryState.Charging || battery.state == BatteryState.Full
    val temp = battery.temperatureCelsius
    val batteryPercent = battery.percent
    val uptime = snap.system.uptime

    val score = computeHealthScore(
        ramUsedRatio = ram?.usedRatio,
        storageUsedRatio = disk?.usedRatio,
        batteryPercent = batteryPercent,
        charging = charging,
        temperatureCelsius = temp,
    )
    val advice = buildAdvice(
        ramUsedRatio = ram?.usedRatio,
        storageUsedRatio = disk?.usedRatio,
        batteryPercent = batteryPercent,
        charging = charging,
        temperatureCelsius = temp,
        controlReady = control.ready,
    )

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    live.refresh()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = c.surfaceHigh,
                color = c.primary,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp),
        ) {
            FadeSlideIn(modifier = Modifier.fillMaxWidth(), index = 0) {
                HomeHeader(control = control, onTapStatus = { scope.launch { sheets.showControlSetup(control) } })
            }
            Spacer(Modifier.height(18.dp))
            FadeSlideIn(modifier = Modifier.fillMaxWidth(), index = 1) {
                HealthCard(
                    score = score,
                    hasData = ram != null || disk != null || batteryPercent != null,
                    busy = running == "Akıllı optimizasyon",
                    onOptimize = { scope.launch { smartOptimize() } },
                )
            }
            Spacer(Modifier.height(14.dp))
            for (item in advice) {
                AdviceCard(advice = item, onTap = { scope.launch { onAdvice(item.action) } })
                Spacer(Modifier.height(10.dp))
            }
            if (advice.isEmpty()) AllGoodCard()
            FadeSlideIn(modifier = Modifier.fillMaxWidth(), index = 3) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Anlık Durum")
                    StatGridFit(
                        first = {
                            StatTile(
                                icon = Icons.Rounded.Memory,
                                label = "Bellek (RAM)",
                                value = if (ram == null) "—" else "%${(ram.usedRatio * 100).roundToInt()}",
                                caption = if (ram == null) {
                                    "Okunamadı"
                                } else {
                                    "${formatBytes(ram.usedBytes)} / ${formatBytes(ram.totalBytes)}"
                                },
                                ratio = ram?.usedRatio,
                                color = c.forUsage(ram?.usedRatio ?: 0.0),
                                modifier = it,
                            )
                        },
                        second = {
                            StatTile(
                                icon = Icons.Rounded.SdStorage,
                                label = "Depolama",
                                value = if (disk == null) "—" else "%${(disk.usedRatio * 100).roundToInt()}",
                                caption = if (disk == null) "Okunamadı" else "${formatBytes(disk.freeBytes)} boş",
                                ratio = disk?.usedRatio,
                                color = c.forUsage(disk?.usedRatio ?: 0.0),
                                modifier = it,
                            )
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    StatGridFit(
                        first = {
                            StatTile(
                                icon = if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                                label = "Pil",
                                value = if (batteryPercent == null) "—" else "%$batteryPercent",
                                caption = batteryCaption(battery),
                                ratio = if (batteryPercent == null) null else batteryPercent / 100.0,
                                color = if (batteryPercent == null) c.muted else batteryColor(batteryPercent, charging),
                                modifier = it,
                            )
                        },
                        second = {
                            StatTile(
                                icon = Icons.Rounded.Thermostat,
                                label = "Sıcaklık",
                                value = if (temp == null) "—" else "${fixed(temp, 1)}°C",
                                caption = if (uptime == null) null else "Açık: ${formatDuration(uptime)}",
                                ratio = if (temp == null) null else ((temp - 20) / 30).coerceIn(0.0, 1.0),
                                color = if (temp == null) {
                                    c.muted
                                } else if (temp >= 45) {
                                    c.danger
                                } else if (temp >= 40) {
                                    c.warn
                                } else {
                                    c.primary
                                },
                                modifier = it,
                            )
                        },
                    )
                }
            }
            FadeSlideIn(modifier = Modifier.fillMaxWidth(), index = 4) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Hızlı İşlemler")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        QuickAction(
                            icon = Icons.Rounded.RocketLaunch,
                            label = "Hızlandır",
                            busy = running == "Hızlandır",
                            onTap = { scope.launch { runAction("Hızlandır") { control.freeMemory() } } },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        QuickAction(
                            icon = Icons.Rounded.CleaningServices,
                            label = "Önbellek Temizle",
                            busy = running == "Önbellek",
                            onTap = { scope.launch { runAction("Önbellek") { control.trimAllCaches() } } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        QuickAction(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            label = "İşlem Günlüğü",
                            busy = false,
                            onTap = { scope.launch { sheets.showLogSheet(control) } },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        QuickAction(
                            icon = Icons.Rounded.VerifiedUser,
                            label = "Kontrol Durumu",
                            busy = false,
                            onTap = { scope.launch { sheets.showControlSetup(control) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private fun batteryCaption(b: BatteryReading): String {
    val remaining = b.estimatedRemaining
    return when (b.state) {
        BatteryState.Charging ->
            if (remaining == null) "Şarj oluyor" else "${formatDuration(remaining)} doluma"
        BatteryState.Full -> "Dolu"
        BatteryState.Discharging ->
            if (remaining == null) "Pil kullanılıyor" else "${formatDuration(remaining)} kaldı"
        else -> "Durum bilinmiyor"
    }
}

@Composable
private fun batteryColor(percent: Int, charging: Boolean): Color {
    val c = AppTheme.colors
    if (charging) return c.good
    if (percent <= 20) return c.danger
    if (percent <= 50) return c.warn
    return c.primary
}

@Composable
private fun HomeHeader(control: ControlController, onTapStatus: () -> Unit) {
    val c = AppTheme.colors
    val ready = control.ready
    val color = if (control.checking) c.muted else (if (ready) c.good else c.warn)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppLogo(size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "TerminalF",
                color = c.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
            )
            Text("Telefon yönetim merkezi", color = c.muted, fontSize = 13.sp)
        }
        StatusPill(
            label = if (control.checking) "Denetleniyor" else (if (ready) "Kontrol açık" else "Kontrol kapalı"),
            color = color,
            icon = if (ready) Icons.Rounded.Verified else Icons.Outlined.Lock,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onTapStatus() },
        )
    }
}

@Composable
private fun HealthCard(score: Int, hasData: Boolean, busy: Boolean, onOptimize: () -> Unit) {
    val c = AppTheme.colors
    AppCard(padding = PaddingValues(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HealthRing(score = score)
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Cihaz Sağlığı", color = c.muted, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (hasData) healthLabel(score) else "Ölçülüyor…",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (hasData) c.forHealth(score) else c.muted,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    label = "Akıllı Optimizasyon",
                    icon = Icons.Rounded.AutoFixHigh,
                    busy = busy,
                    onClick = onOptimize,
                )
            }
        }
    }
}

@Composable
private fun AdviceCard(advice: Advice, onTap: () -> Unit) {
    val c = AppTheme.colors
    val color = when (advice.level) {
        AdviceLevel.Critical -> c.danger
        AdviceLevel.Warning -> c.warn
        AdviceLevel.Info -> c.secondary
    }
    AppCard(borderColor = color.faded(0.45f)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (advice.level == AdviceLevel.Info) Icons.Outlined.Info else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = color,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(advice.title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(advice.detail, color = c.muted, fontSize = 13.sp, lineHeight = (13 * 1.35).sp)
            }
            Spacer(Modifier.width(10.dp))
            SoftButton(label = advice.actionLabel, onClick = onTap, color = color)
        }
    }
}

@Composable
private fun AllGoodCard() {
    val c = AppTheme.colors
    AppCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.good)
            Spacer(Modifier.width(12.dp))
            Text(
                "Her şey yolunda. Yapmanız gereken bir şey yok.",
                modifier = Modifier.weight(1f),
                color = c.text,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * İki sütunlu satır: aynı satırdaki iki kart içeriğine göre yükselir ve eşit boyda olur
 * (Flutter `IntrinsicHeight` + `stretch`).
 */
@Composable
private fun StatGridFit(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        first(Modifier.weight(1f).fillMaxHeight())
        Spacer(Modifier.width(12.dp))
        second(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    busy: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    AppCard(
        modifier = modifier.aspectRatio(1.9f),
        onClick = if (busy) null else onTap,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(shape)
                    .background(c.primary.faded(0.14f), shape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.4.dp,
                        color = c.primary,
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}
