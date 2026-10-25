package com.terminalf.app.ui.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.core.AppPalette
import com.terminalf.app.core.AppPalettes
import com.terminalf.app.core.LogoStyle
import com.terminalf.app.state.AppSettings
import com.terminalf.app.state.Haptics
import com.terminalf.app.state.Motion
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.components.AppLogo
import com.terminalf.app.ui.components.RowDivider
import com.terminalf.app.ui.components.SectionHeader
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.theme.AppTheme
import kotlin.math.max
import kotlin.math.min

/** (ton 0-360, açıklık 0-1) çifti: sRGB renkten HSL'ye. */
private fun hueLightnessOf(argb: Long): Pair<Float, Float> {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    var h = 0f
    if (d > 0f) {
        h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        if (h < 0f) h += 360f
    }
    return h to l
}

private fun customColorOf(hue: Float, light: Float): Color = Color.hsl(hue.coerceIn(0f, 360f), 0.85f, light.coerceIn(0f, 1f))

/** Görünüm: tema seçimi ve logo rengi. Varsayılan tema siyah-beyazdır. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearancePage(settings: AppSettings, onBack: () -> Unit) {
    val s = settings.observe()
    val c = AppTheme.colors

    val initial = remember { hueLightnessOf(s.logoCustom) }
    var hue by remember { mutableFloatStateOf(initial.first) }
    var light by remember { mutableFloatStateOf(initial.second.coerceIn(0.35f, 0.85f)) }
    var dragging by remember { mutableStateOf(false) }

    val customColor = customColorOf(hue, light)

    fun commitCustom() {
        dragging = false
        Haptics.tap()
        s.setLogoCustom(customColor.toArgb().toLong() and 0xFFFFFFFFL)
    }

    @Composable
    fun ThemeGrid(dark: Boolean) {
        val palettes = AppPalettes.all.filter { it.isDark == dark }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pair in palettes.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (p in pair) {
                        ThemeTile(
                            palette = p,
                            selected = s.themeId == p.id,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                Haptics.tap()
                                s.setTheme(p.id)
                            },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    SubPageFrame(title = "Görünüm", onBack = onBack) {
        SectionHeader("Animasyonlar")
        AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Animation, contentDescription = null, tint = c.muted)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Animasyonlar", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Sekme geçişi, belirme ve tema geçiş efektleri", color = c.muted, fontSize = 12.5.sp)
                }
                Switch(
                    checked = s.animations,
                    onCheckedChange = { v ->
                        Haptics.tap()
                        s.setAnimations(v)
                    },
                    colors = appSwitchColors(),
                )
            }
        }
        SectionHeader("Koyu Temalar")
        ThemeGrid(dark = true)
        SectionHeader("Açık Temalar")
        ThemeGrid(dark = false)
        SectionHeader("Logo Rengi")
        AppCard {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                AppLogo(size = 84.dp)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (style in LogoStyle.presets) {
                    val colors: List<Long> = when {
                        style.colors.isEmpty() -> listOf(s.palette.logoA, s.palette.logoB)
                        style.colors.size == 1 -> listOf(style.colors[0], style.colors[0])
                        else -> style.colors
                    }
                    LogoSwatch(
                        label = style.name,
                        colors = colors.map { Color(it) },
                        isAuto = style.id == LogoStyle.AUTO_ID,
                        selected = s.logoId == style.id,
                        onClick = {
                            Haptics.tap()
                            s.setLogo(style.id)
                        },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            RowDivider()
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val customActive = dragging || s.logoId == LogoStyle.CUSTOM_ID
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (customActive) {
                                if (dragging) customColor else Color(s.logoCustom)
                            } else {
                                c.surfaceHigh
                            },
                        )
                        .border(1.dp, c.outline, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (s.logoId == LogoStyle.CUSTOM_ID) "Özel renk (seçili)" else "Özel renk",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            HueSlider(
                value = hue,
                onChange = {
                    hue = it
                    dragging = true
                },
                onChangeEnd = { commitCustom() },
            )
            Slider(
                value = light,
                onValueChange = {
                    light = it
                    dragging = true
                },
                valueRange = 0.35f..0.85f,
                onValueChangeFinished = { commitCustom() },
                colors = appSliderColors(),
            )
            Text("Üstteki çubuk rengi, alttaki açıklığı belirler.", color = c.muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ana ekran simgesi, uygulamadan çıkınca seçtiğin renge en yakın hazır simgeyle güncellenir " +
                    "(bazı başlatıcılarda birkaç saniye sürebilir).",
                color = c.muted,
                fontSize = 12.5.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        SoftButton(
            label = "Varsayılana Dön (Sade Siyah)",
            icon = Icons.Rounded.RestartAlt,
            onClick = {
                Haptics.tap()
                s.setTheme(AppPalettes.DEFAULT_ID)
                s.setLogo(LogoStyle.AUTO_ID)
            },
        )
    }
}

@Composable
private fun ThemeTile(palette: AppPalette, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val p = palette
    val shape = RoundedCornerShape(20.dp)
    val borderColor by animateColorAsState(
        targetValue = if (selected) c.primary else c.outline,
        animationSpec = tween(Motion.d(220)),
        label = "tileBorder",
    )
    Column(
        modifier
            .clip(shape)
            .background(c.surface, shape)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // Küçük önizleme: o temanın gerçek renkleriyle.
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(p.bg))
                .border(1.dp, Color(p.outline), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(p.surface))
                    .border(1.dp, Color(p.outline), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(p.primary), Color(p.secondary)))),
                )
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Box(
                        Modifier
                            .height(5.dp)
                            .width(34.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(p.text)),
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(22.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(p.muted)),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.primary)
            }
        }
    }
}

@Composable
private fun LogoSwatch(
    label: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    isAuto: Boolean = false,
) {
    val c = AppTheme.colors
    val ring by animateColorAsState(
        targetValue = if (selected) c.text else Color.Transparent,
        animationSpec = tween(Motion.d(180)),
        label = "swatchRing",
    )
    Column(
        Modifier
            .width(56.dp)
            .semantics {
                contentDescription = "Logo rengi: $label"
                this.selected = selected
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .border(2.dp, ring, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .border(1.dp, c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isAuto) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF111111),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = if (selected) c.text else c.muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** Gökkuşağı renk çubuğu (ton seçici). */
@Composable
private fun HueSlider(value: Float, onChange: (Float) -> Unit, onChangeEnd: () -> Unit) {
    val rainbow = remember {
        (0..360 step 30).map { h -> Color.hsl(h.toFloat().coerceIn(0f, 360f), 0.85f, 0.55f) }
    }
    Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(rainbow)),
        )
        Slider(
            value = value.coerceIn(0f, 360f),
            onValueChange = onChange,
            valueRange = 0f..360f,
            onValueChangeFinished = onChangeEnd,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
