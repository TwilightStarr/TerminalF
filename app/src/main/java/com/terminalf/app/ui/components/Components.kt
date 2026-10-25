package com.terminalf.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.state.Motion
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded
import kotlinx.coroutines.delay

val EaseOutCubicCurve = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/** Tüm sayfalarda ortak, sade kart. İçerik dikey dizilir. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(c.surface, shape)
        .border(1.dp, borderColor ?: c.outline, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(padding), content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 22.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = c.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        if (trailing != null) trailing()
    }
}

/** İnce yatay ilerleme çubuğu. */
@Composable
fun MeterBar(value: Double, modifier: Modifier = Modifier, color: Color? = null, height: Dp = 8.dp) {
    val c = AppTheme.colors
    val v = if (value.isNaN()) 0f else value.coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(c.outline),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(v)
                .background(color ?: c.primary),
        )
    }
}

/** Üstte etiket, ortada büyük değer, altta ilerleme çubuğu: kısa gösterge. */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    ratio: Double? = null,
    color: Color? = null,
) {
    val c = AppTheme.colors
    val tint = color ?: c.primary
    AppCard(modifier = modifier, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(caption, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ratio != null) {
            Spacer(Modifier.height(10.dp))
            MeterBar(value = ratio, color = tint, height = 6.dp)
        }
    }
}

/** Etiket - değer satırı; `copyable` ise dokununca panoya kopyalar. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    copyable: Boolean = false,
) {
    val c = AppTheme.colors
    val clipboard = LocalClipboardManager.current
    val toast = LocalToaster.current
    var m = modifier.fillMaxWidth()
    if (copyable) {
        m = m.clickable {
            clipboard.setText(AnnotatedString(value))
            toast.show("Panoya kopyalandı")
        }
    }
    Row(modifier = m.padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(4f), color = c.muted, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(6f),
            textAlign = TextAlign.End,
            color = valueColor ?: c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = AppTheme.colors.outline)
}

/** Ana eylem düğmesi (marka gradyanı). */
@Composable
fun PrimaryButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    busy: Boolean = false,
    expanded: Boolean = true,
) {
    val c = AppTheme.colors
    val enabled = onClick != null && !busy
    val shape = RoundedCornerShape(16.dp)
    var m = modifier.alpha(if (enabled) 1f else 0.5f)
    if (expanded) m = m.fillMaxWidth()
    Box(
        modifier = m
            .height(50.dp)
            .clip(shape)
            .background(c.brand, shape)
            .clickable(enabled = enabled) { onClick?.invoke() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.4.dp, color = c.bg)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.bg, modifier = Modifier.size(20.dp))
            }
            if (busy || icon != null) Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = c.bg,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** İkincil, çerçeveli düğme. */
@Composable
fun SoftButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color? = null,
) {
    val c = AppTheme.colors
    val tint = color ?: c.text
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .alpha(if (onClick != null) 1f else 0.5f)
            .height(44.dp)
            .clip(shape)
            .background(c.surfaceHigh, shape)
            .border(1.dp, c.outline, shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                color = tint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.faded(0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Büyük dairesel gösterge (cihaz sağlığı puanı). 0 - 100. */
@Composable
fun HealthRing(score: Int, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val c = AppTheme.colors
    val color = c.forHealth(score)
    val animated by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(Motion.d(700), easing = EaseOutCubicCurve),
        label = "ring",
    )
    val track = c.outline
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                fontSize = (size.value * 0.30f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                lineHeight = (size.value * 0.30f).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text("/ 100", color = c.muted, fontSize = 12.sp)
        }
    }
}

/**
 * İçeriği hafifçe yukarı kayarak ve belirerek gösterir. [index] arttıkça gecikir (kademeli
 * giriş). Animasyonlar kapalıysa doğrudan gösterir.
 */
@Composable
fun FadeSlideIn(
    modifier: Modifier = Modifier,
    index: Int = 0,
    offset: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(if (Motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (Motion.enabled) {
            delay(minOf(index, 8) * 55L)
            progress.animateTo(1f, tween(420, easing = EaseOutCubicCurve))
        }
    }
    val offsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { offset.toPx() }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * offsetPx
        },
    ) { content() }
}
