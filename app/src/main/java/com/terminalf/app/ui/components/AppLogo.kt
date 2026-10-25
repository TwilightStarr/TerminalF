package com.terminalf.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.terminalf.app.ui.theme.AppTheme

/**
 * TerminalF logosu: komut istemi (`>`) + imleç (`_`). `icon.svg` ve Android ikonlarıyla AYNI
 * geometri (1024 birimlik kare). Rengi kullanıcı Cihaz > Görünüm bölümünden değiştirebilir.
 * [framed] ise yuvarlatılmış koyu zemin üzerinde (uygulama ikonu gibi) çizilir.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 40.dp, framed: Boolean = true) {
    val c = AppTheme.colors
    val marks = c.logoColors
    val markA = marks.getOrElse(0) { c.primary }
    val markB = marks.getOrElse(1) { markA }
    val frameA = c.logoFrameA
    val frameB = c.logoFrameB
    val cursor = if (framed) Color(0xFFF5F5F5) else c.text
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "TerminalF logosu" },
    ) {
        val scale = this.size.width / 1024f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            if (framed) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(frameA, frameB),
                        start = Offset.Zero,
                        end = Offset(1024f, 1024f),
                    ),
                    size = Size(1024f, 1024f),
                    cornerRadius = CornerRadius(232f),
                )
                drawRoundRect(
                    color = Color(0x1FFFFFFF),
                    topLeft = Offset(4f, 4f),
                    size = Size(1016f, 1016f),
                    cornerRadius = CornerRadius(228f),
                    style = Stroke(width = 8f),
                )
            }
            val chevron = Path().apply {
                moveTo(318f, 316f)
                lineTo(548f, 512f)
                lineTo(318f, 708f)
            }
            drawPath(
                path = chevron,
                brush = Brush.linearGradient(
                    listOf(markA, markB),
                    start = Offset(250f, 250f),
                    end = Offset(620f, 780f),
                ),
                style = Stroke(width = 132f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawRoundRect(
                color = cursor,
                topLeft = Offset(598f, 664f),
                size = Size(790f - 598f, 768f - 664f),
                cornerRadius = CornerRadius(34f),
            )
        }
    }
}
