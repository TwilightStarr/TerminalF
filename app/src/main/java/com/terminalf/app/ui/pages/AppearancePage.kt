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
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.core.AccentStyle
import com.terminalf.app.core.AppPalette
import com.terminalf.app.core.AppPalettes
import com.terminalf.app.core.LogoStyle
import com.terminalf.app.core.logoRamp
import com.terminalf.app.core.tr
import com.terminalf.app.core.argbToHsl
import com.terminalf.app.state.AppSettings
import com.terminalf.app.state.Haptics
import com.terminalf.app.state.Motion
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.components.AppLogo
import com.terminalf.app.ui.components.IconChip
import com.terminalf.app.ui.components.RowDivider
import com.terminalf.app.ui.components.SectionHeader
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.theme.AppTheme

/**
 * Görünüm: dört kısa bölüm. Tema (koyu/açık + AMOLED), Renkler (vurgu + renkli arayüz),
 * Logo (renk) ve Genel (animasyon). Her seçim anında uygulanır; en altta tek dokunuşla sıfırlama.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearancePage(settings: AppSettings, onBack: () -> Unit) {
    val s = settings.observe()
    val c = AppTheme.colors
    val basePalette = AppPalettes.byId(s.themeId)
    val isDark = s.palette.isDark

    var showDark by remember { mutableStateOf(basePalette.isDark) }
    var accentPicker by remember { mutableStateOf(s.accentId == AccentStyle.CUSTOM_ID) }
    var logoPicker by remember { mutableStateOf(s.logoId == LogoStyle.CUSTOM_ID) }

    SubPageFrame(title = tr("Appearance", "Görünüm"), onBack = onBack) {
        // ---- Önizleme ----
        AppCard(padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("TerminalF", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${basePalette.name}${if (s.amoled && basePalette.isDark) " · AMOLED" else ""}",
                        color = c.muted,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconChip(Icons.Rounded.Tune, c.orange)
                IconChip(Icons.Rounded.Palette, c.pink)
                IconChip(Icons.Rounded.ColorLens, c.violet)
                IconChip(Icons.Rounded.AutoAwesome, c.green)
                IconChip(Icons.Rounded.Brightness2, c.cyan)
            }
        }

        // ---- Tema ----
        SectionHeader(tr("Theme", "Tema"))
        Segmented(
            left = tr("Dark", "Koyu"),
            right = tr("Light", "Açık"),
            leftSelected = showDark,
            onLeft = { showDark = true },
            onRight = { showDark = false },
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (p in AppPalettes.all.filter { it.isDark == showDark }) {
                ThemeDot(
                    palette = p,
                    selected = s.themeId == p.id,
                    onClick = {
                        Haptics.tap()
                        s.setTheme(p.id)
                    },
                )
            }
        }
        if (showDark) {
            Spacer(Modifier.height(14.dp))
            AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                ToggleRow(
                    icon = Icons.Rounded.Brightness2,
                    color = c.blue,
                    title = tr("AMOLED black", "AMOLED siyah"),
                    subtitle = tr("The background becomes true black; pixels switch off and save battery.", "Zemin gerçek siyah olur; piksel kapanır, pil tasarrufu sağlar."),
                    checked = s.amoled,
                    onChange = { s.setAmoled(it) },
                )
            }
        }

        // ---- Renkler ----
        SectionHeader(tr("Colors", "Renkler"))
        AppCard {
            Text(tr("Accent color", "Vurgu rengi"), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(tr("Buttons, switches and selected items.", "Düğmeler, anahtarlar ve seçili öğeler."), color = c.muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Dot(
                    label = tr("Theme color", "Tema rengi"),
                    colors = listOf(Color(basePalette.primary), Color(basePalette.secondary)),
                    selected = s.accentId == AccentStyle.THEME_ID,
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = {
                        Haptics.tap()
                        accentPicker = false
                        s.setAccent(AccentStyle.THEME_ID)
                    },
                )
                for (a in AccentStyle.presets) {
                    val pair = AccentStyle.colorsFor(a.id, s.accentCustom, isDark)
                    Dot(
                        label = a.name,
                        colors = listOf(Color(pair?.first ?: a.darkPrimary), Color(pair?.second ?: a.darkSecondary)),
                        selected = s.accentId == a.id,
                        onClick = {
                            Haptics.tap()
                            accentPicker = false
                            s.setAccent(a.id)
                        },
                    )
                }
                val custom = AccentStyle.colorsFor(AccentStyle.CUSTOM_ID, s.accentCustom, isDark)
                Dot(
                    label = tr("Custom color", "Özel renk"),
                    colors = if (s.accentId == AccentStyle.CUSTOM_ID && custom != null) {
                        listOf(Color(custom.first), Color(custom.second))
                    } else {
                        listOf(c.pink, c.cyan)
                    },
                    selected = s.accentId == AccentStyle.CUSTOM_ID,
                    icon = Icons.Rounded.Palette,
                    onClick = {
                        Haptics.tap()
                        accentPicker = !accentPicker
                        if (accentPicker) s.setAccentCustom(s.accentCustom)
                    },
                )
            }
            if (accentPicker) {
                Spacer(Modifier.height(12.dp))
                CustomColorPicker(initial = s.accentCustom, onCommit = { s.setAccentCustom(it) })
            }
            Spacer(Modifier.height(16.dp))
            RowDivider()
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                icon = Icons.Rounded.ColorLens,
                color = c.pink,
                title = tr("Colorful interface", "Renkli arayüz"),
                subtitle = tr("Every box and button carries its own color. When off, everything uses the accent color.", "Her kutu ve düğme kendi rengini taşır. Kapalıysa hepsi vurgu renginde olur."),
                checked = s.colorful,
                onChange = { s.setColorful(it) },
            )
        }

        // ---- Logo ----
        SectionHeader(tr("Logo", "Logo"))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Logo color", "Logo rengi"), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (s.logoId == LogoStyle.CUSTOM_ID) tr("Custom color", "Özel renk") else LogoStyle.byId(s.logoId).name,
                        color = c.muted,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (style in LogoStyle.presets) {
                    val tint: List<Long> = when {
                        style.id == LogoStyle.AUTO_ID -> listOf(s.palette.logoA, s.palette.logoB)
                        else -> style.colors
                    }
                    val ramp = logoRamp(tint)
                    Dot(
                        label = style.name,
                        colors = listOf(Color(ramp[0]), Color(ramp[3]), Color(ramp[5])),
                        selected = s.logoId == style.id,
                        icon = if (style.id == LogoStyle.AUTO_ID) Icons.Rounded.AutoAwesome else null,
                        onClick = {
                            Haptics.tap()
                            logoPicker = false
                            s.setLogo(style.id)
                        },
                    )
                }
                Dot(
                    label = tr("Custom color", "Özel renk"),
                    colors = if (s.logoId == LogoStyle.CUSTOM_ID) {
                        logoRamp(listOf(s.logoCustom)).let { listOf(Color(it[0]), Color(it[3]), Color(it[5])) }
                    } else {
                        listOf(c.pink, c.cyan)
                    },
                    selected = s.logoId == LogoStyle.CUSTOM_ID,
                    icon = Icons.Rounded.Palette,
                    onClick = {
                        Haptics.tap()
                        logoPicker = !logoPicker
                        if (logoPicker) s.setLogoCustom(s.logoCustom)
                    },
                )
            }
            if (logoPicker) {
                Spacer(Modifier.height(12.dp))
                CustomColorPicker(initial = s.logoCustom, onCommit = { s.setLogoCustom(it) })
            }
            Spacer(Modifier.height(12.dp))
            Text(
                tr("When you leave the app, the home screen icon is updated to the ready-made icon closest to the color you picked (this can take a few seconds on some launchers).", "Ana ekran simgesi, uygulamadan çıkınca seçtiğin renge en yakın hazır simgeyle güncellenir (bazı başlatıcılarda birkaç saniye sürebilir)."),
                color = c.muted,
                fontSize = 12.5.sp,
            )
        }

        // ---- Genel ----
        SectionHeader(tr("General", "Genel"))
        AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            ToggleRow(
                icon = Icons.Rounded.Animation,
                color = c.cyan,
                title = tr("Animations", "Animasyonlar"),
                subtitle = tr("Tab transitions, fade-ins and theme transition effects.", "Sekme geçişi, belirme ve tema geçiş efektleri."),
                checked = s.animations,
                onChange = { s.setAnimations(it) },
            )
        }

        Spacer(Modifier.height(20.dp))
        SoftButton(
            label = tr("Reset Appearance", "Görünümü Sıfırla"),
            icon = Icons.Rounded.RestartAlt,
            onClick = {
                Haptics.tap()
                accentPicker = false
                logoPicker = false
                s.resetAppearance()
                showDark = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Başlık + açıklama + anahtar; solda renkli simge çipi. */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(icon = icon, color = color, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = c.muted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = { v ->
                Haptics.tap()
                onChange(v)
            },
            enabled = enabled,
            colors = appSwitchColors(),
        )
    }
}

/** İki seçenekli, yan yana segment düğmesi (Koyu / Açık). */
@Composable
private fun Segmented(left: String, right: String, leftSelected: Boolean, onLeft: () -> Unit, onRight: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceHigh, shape)
            .border(1.dp, c.outline, shape)
            .padding(4.dp),
    ) {
        for ((label, sel, click) in listOf(
            Triple(left, leftSelected, onLeft),
            Triple(right, !leftSelected, onRight),
        )) {
            val inner = RoundedCornerShape(11.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(inner)
                    .background(if (sel) c.primary.copy(alpha = 0.20f) else Color.Transparent, inner)
                    .clickable {
                        Haptics.tap()
                        click()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) c.primary else c.muted,
                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** Tema seçici: temanın gerçek zemini ve vurgusuyla küçük yuvarlak önizleme + ad. */
@Composable
private fun ThemeDot(palette: AppPalette, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val ring by animateColorAsState(
        targetValue = if (selected) c.primary else Color.Transparent,
        animationSpec = tween(Motion.d(180)),
        label = "themeRing",
    )
    Column(
        Modifier
            .width(58.dp)
            .semantics {
                contentDescription = tr("Theme: ${palette.name}", "Tema: ${palette.name}")
                this.selected = selected
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .border(2.dp, ring, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color(palette.bg))
                .border(1.dp, Color(palette.outline), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(palette.primary), Color(palette.secondary)))),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            palette.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = if (selected) c.text else c.muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** Renk seçici nokta (gradyan dolgulu daire). Etiket yalnızca erişilebilirlik içindir. */
@Composable
private fun Dot(
    label: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val c = AppTheme.colors
    val ring by animateColorAsState(
        targetValue = if (selected) c.text else Color.Transparent,
        animationSpec = tween(Motion.d(180)),
        label = "dotRing",
    )
    val fill = if (colors.size >= 2) colors else listOf(colors.firstOrNull() ?: c.primary, colors.firstOrNull() ?: c.primary)
    Box(
        Modifier
            .size(44.dp)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .border(2.dp, ring, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(fill))
            .border(1.dp, c.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF111111),
            )
        }
    }
}

/** Özel renk: üstte ton (gökkuşağı), altta açıklık çubuğu. Bırakınca uygulanır. */
@Composable
private fun CustomColorPicker(initial: Long, onCommit: (Long) -> Unit) {
    val c = AppTheme.colors
    val start = remember { argbToHsl(initial) }
    var hue by remember { mutableFloatStateOf(start.first) }
    var light by remember { mutableFloatStateOf(start.third.coerceIn(0.35f, 0.80f)) }

    fun commit() {
        Haptics.tap()
        val color = Color.hsl(hue.coerceIn(0f, 360f), 0.85f, light.coerceIn(0f, 1f))
        onCommit(color.toArgb().toLong() and 0xFFFFFFFFL)
    }

    Column(Modifier.fillMaxWidth()) {
        HueSlider(value = hue, onChange = { hue = it }, onChangeEnd = { commit() })
        Slider(
            value = light,
            onValueChange = { light = it },
            valueRange = 0.35f..0.80f,
            onValueChangeFinished = { commit() },
            colors = appSliderColors(),
        )
        Text(tr("The top slider sets the color, the bottom one the lightness.", "Üstteki çubuk rengi, alttaki açıklığı belirler."), color = c.muted, fontSize = 12.5.sp)
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
