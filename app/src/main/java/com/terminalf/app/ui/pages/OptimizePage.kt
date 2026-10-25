package com.terminalf.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.core.OptimizationProfile
import com.terminalf.app.core.Tweak
import com.terminalf.app.core.TweakCategory
import com.terminalf.app.core.TweakState
import com.terminalf.app.core.kProfiles
import com.terminalf.app.state.ControlController
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.components.LocalDialogs
import com.terminalf.app.ui.components.LocalSheets
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.PrimaryButton
import com.terminalf.app.ui.components.RowDivider
import com.terminalf.app.ui.components.SectionHeader
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.components.StatusPill
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.sheets.showControlSetup
import com.terminalf.app.ui.sheets.showLogSheet
import com.terminalf.app.ui.sheets.showOutcome
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded
import kotlinx.coroutines.launch

private fun profileIcon(key: String): ImageVector = when (key) {
    "bolt" -> Icons.Rounded.Bolt
    "battery" -> Icons.Rounded.BatterySaver
    "shield" -> Icons.Rounded.ShieldMoon
    else -> Icons.Rounded.CleaningServices
}

/** Optimize sayfası: önce hazır profiller (tek dokunuş), altında tek tek ayarlar. */
@Composable
fun OptimizePage(control: ControlController) {
    control.observe()
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val dialogs = LocalDialogs.current
    val sheets = LocalSheets.current

    fun runProfile(profile: OptimizationProfile, apply: Boolean) {
        scope.launch {
            if (!control.ready) {
                sheets.showControlSetup(control)
                return@launch
            }
            val result = control.runProfile(profile, apply)
            if (result.applied + result.failed > 0) {
                control.reportDone(profile.title, result.summary, ok = result.failed == 0)
            }
            toast.show("${profile.title}: ${result.summary}")
        }
    }

    fun toggle(tweak: Tweak, apply: Boolean) {
        scope.launch {
            if (!control.ready) {
                sheets.showControlSetup(control)
                return@launch
            }
            if (apply && tweak.dangerous) {
                val ok = dialogs.confirm(
                    title = tweak.title,
                    message = "${tweak.description}\n\nBu işlem sistem bileşenlerini etkiler. Geri alınabilir, ama emin misiniz?",
                    confirmLabel = "Uygula",
                    danger = true,
                )
                if (!ok) return@launch
            }
            val outcome = control.setTweak(tweak, apply)
            showOutcome(toast, outcome)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Optimize",
                modifier = Modifier.weight(1f),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            IconButton(onClick = { scope.launch { sheets.showLogSheet(control) } }) {
                Icon(Icons.Rounded.ReceiptLong, contentDescription = "İşlem günlüğü", tint = c.muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        AccessCard(control) { scope.launch { sheets.showControlSetup(control) } }
        if (control.busy) {
            Spacer(Modifier.height(12.dp))
            AppCard {
                Text(control.busyLabel ?: "", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = c.primary,
                    trackColor = c.outline,
                )
            }
        }
        SectionHeader("Hazır Profiller")
        for (profile in kProfiles) {
            ProfileCard(
                profile = profile,
                control = control,
                onApply = { runProfile(profile, true) },
                onRevert = { runProfile(profile, false) },
            )
            Spacer(Modifier.height(12.dp))
        }
        SectionHeader("Tüm Ayarlar")
        for (category in TweakCategory.values()) {
            val items = control.tweaks.filter { it.category == category }
            if (items.isEmpty()) continue
            Text(
                category.label.uppercase(),
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 8.dp),
                color = c.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items.forEachIndexed { i, tweak ->
                    TweakRow(
                        tweak = tweak,
                        state = control.stateOf(tweak.id),
                        working = control.isWorking(tweak.id),
                        locked = control.busy,
                        onChanged = { on -> toggle(tweak, on) },
                    )
                    if (i != items.size - 1) RowDivider()
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AccessCard(control: ControlController, onClick: () -> Unit) {
    val c = AppTheme.colors
    val ready = control.ready
    val color = if (control.checking) c.muted else if (ready) c.good else c.warn
    AppCard(borderColor = color.faded(0.4f), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.faded(0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (control.checking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.4.dp, color = c.primary)
                } else {
                    Icon(
                        if (ready) Icons.Rounded.Verified else Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = color,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(control.headline, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (ready) {
                        "Profiller ve ayarlar kullanıma hazır."
                    } else {
                        "Optimizasyon için tek seferlik kurulum gerekiyor. Dokunun."
                    },
                    color = c.muted,
                    fontSize = 13.sp,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.muted)
        }
    }
}

@Composable
private fun ProfileCard(
    profile: OptimizationProfile,
    control: ControlController,
    onApply: () -> Unit,
    onRevert: () -> Unit,
) {
    val c = AppTheme.colors
    val total = control.toggleCount(profile)
    val active = control.activeCount(profile)
    val allOn = profile.reversible && total > 0 && active == total
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.brand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(profileIcon(profile.iconKey), contentDescription = null, tint = c.bg)
            }
            Spacer(Modifier.width(12.dp))
            Text(profile.title, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            if (control.ready && profile.reversible && total > 0) {
                StatusPill(
                    label = if (allOn) "Etkin" else "$active/$total açık",
                    color = if (allOn) c.good else c.muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.description, color = c.muted, fontSize = 13.5.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                label = if (profile.reversible) (if (allOn) "Yeniden Uygula" else "Uygula") else "Çalıştır",
                icon = Icons.Rounded.PlayArrow,
                onClick = if (control.busy) null else onApply,
                modifier = Modifier.weight(1f),
            )
            if (profile.reversible) {
                Spacer(Modifier.width(10.dp))
                SoftButton(
                    label = "Geri Al",
                    icon = Icons.Rounded.Undo,
                    onClick = if (control.busy) null else onRevert,
                )
            }
        }
    }
}

@Composable
private fun TweakRow(
    tweak: Tweak,
    state: TweakState,
    working: Boolean,
    locked: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tweak.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                tweak.description,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
            if (tweak.isToggle && state == TweakState.Unknown) {
                Spacer(Modifier.height(4.dp))
                Text("Durum okunamadı", color = c.warn, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (working) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.4.dp, color = c.primary)
        } else if (tweak.isToggle) {
            Switch(
                checked = state == TweakState.On,
                onCheckedChange = if (locked) null else onChanged,
                enabled = !locked,
                colors = appSwitchColors(),
            )
        } else {
            SoftButton(label = "Çalıştır", onClick = if (locked) null else ({ onChanged(true) }))
        }
    }
}
