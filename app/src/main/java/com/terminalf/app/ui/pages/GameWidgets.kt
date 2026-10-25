package com.terminalf.app.ui.pages

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.state.Motion
import com.terminalf.app.ui.components.AppCard
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded

/** Küçük, soluk açıklama metni (Flutter `_Hint`). */
@Composable
internal fun GameHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = AppTheme.colors.muted,
        fontSize = 12.5.sp,
        lineHeight = (12.5f * 1.4f).sp,
    )
}

/** Ayar başlığı (Flutter `_Label`). */
@Composable
internal fun GameLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
}

/** Uygulamanın seçim çipi (Flutter `ChoiceChip` + uygulama `chipTheme`'i). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameChoiceChip(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val enabled = onClick != null
    FilterChip(
        selected = selected,
        onClick = { onClick?.invoke() },
        label = {
            ProvideTextStyle(TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)) {
                content()
            }
        },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surfaceHigh,
            labelColor = c.text,
            disabledContainerColor = c.surfaceHigh,
            disabledLabelColor = c.text.faded(0.5f),
            selectedContainerColor = c.primary.faded(0.18f),
            selectedLabelColor = c.text,
            disabledSelectedContainerColor = c.primary.faded(0.18f),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = c.outline,
            selectedBorderColor = c.outline,
            disabledBorderColor = c.outline,
            disabledSelectedBorderColor = c.outline,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

/** Dokununca işlem yapan çip (Flutter `ActionChip`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameActionChip(
    onClick: (() -> Unit)?,
    leadingIcon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val enabled = onClick != null
    AssistChip(
        onClick = { onClick?.invoke() },
        label = {
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
        },
        shape = RoundedCornerShape(12.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = c.surfaceHigh,
            labelColor = c.text,
            disabledContainerColor = c.surfaceHigh,
            disabledLabelColor = c.text.faded(0.5f),
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = c.outline,
            disabledBorderColor = c.outline,
            borderWidth = 1.dp,
        ),
    )
}

/** Seçenek çipleri (Flutter `_Choices`). `onSelected == null` ise hepsi devre dışı. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> GameChoices(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: ((T) -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (v in values) {
            GameChoiceChip(
                selected = v == selected,
                onClick = if (onSelected == null) null else ({ onSelected(v) }),
            ) {
                Text(label(v))
            }
        }
    }
}

/** Başlık + açıklama + anahtar (Flutter `_SwitchRow`). */
@Composable
internal fun GameSwitchRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onChanged: ((Boolean) -> Unit)?,
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = (12.5f * 1.35f).sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = onChanged,
            enabled = onChanged != null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.bg,
                checkedTrackColor = c.primary,
                checkedBorderColor = c.primary,
                uncheckedThumbColor = c.muted,
                uncheckedTrackColor = c.surfaceHigh,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

/**
 * Açılır-kapanır ayar grubu (Flutter `_Group` = ExpansionTile): simge, başlık, en çok iki satırlık
 * özet ve açılınca içerik.
 */
@Composable
internal fun GameGroup(
    icon: ImageVector,
    title: String,
    summary: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Motion.d(200)),
        label = "groupChevron",
    )
    AppCard(padding = PaddingValues(horizontal = 16.dp)) {
        Column(
            modifier = if (Motion.enabled) Modifier.animateContentSize() else Modifier,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
                    Icon(icon, contentDescription = null, tint = c.muted)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(
                        summary,
                        modifier = Modifier.padding(top = 2.dp),
                        color = c.muted,
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.rotate(rotation),
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    content = content,
                )
            }
        }
    }
}
