package com.terminalf.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.terminalf.app.core.tr
import com.terminalf.app.ui.theme.AppTheme

/** "F" logosunun üç çubuğu (1024 birimlik kare). `icon.svg` ve Android ikonlarıyla AYNI geometri. */
private const val BAR_TOP =
    "M318,286.6 C318,261.7 330.8,250 358,250 L714,250 C732.3,250 743.8,266.2 735.3,287.3 " +
        "L703.5,366.5 C692,395.1 679.7,400 648.2,400 L354.8,400 C327.6,400 318,386.4 318,359.9 Z"
private const val BAR_MID =
    "M318,466.5 C318,441.5 330.7,430 358.2,430 L627.8,430 C647.2,430 658.7,445.8 651.1,467.8 " +
        "L623.9,546 C614.5,572.8 602.8,580 571.6,580 L354.4,580 C328.1,580 318,566.7 318,539.8 Z"
private const val BAR_BOT =
    "M318,644 C318,624.7 332.4,610 350,610 L436,610 C455.6,610 470,624.4 470,641.2 " +
        "L470,672.2 C470,694.3 473.3,707.7 453.3,732.2 L442.6,745.4 C419.3,774 390.9,774 372.3,774 " +
        "L358.6,774 C328.3,774 318,761.8 318,733.8 Z"

/** Çubuk gradyanlarının başlangıç/bitiş noktaları (kullanıcı uzayı, 1024 birim). */
private val GRADIENT_AXES = listOf(
    Offset(318f, 250f) to Offset(738f, 400f),
    Offset(318f, 430f) to Offset(654f, 580f),
    Offset(318f, 610f) to Offset(470f, 774f),
)

/**
 * TerminalF logosu: koyu yuvarlatılmış karo üzerinde, üstten alta açıktan koyuya geçen üç çubuktan
 * oluşan "F". Rengi Cihaz > Görünüm bölümünden değiştirilir (gümüş = özgün logo).
 * [framed] false ise karo çizilmez, yalnızca "F" çizilir.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 40.dp, framed: Boolean = true) {
    val c = AppTheme.colors
    val stops = c.logoStops
    val tile = c.logoFrameA
    val bars = remember {
        listOf(BAR_TOP, BAR_MID, BAR_BOT).map { PathParser().parsePathString(it).toPath() }
    }
    val description = tr("TerminalF logo", "TerminalF logosu")
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val scale = this.size.width / 1024f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            if (framed) {
                drawRoundRect(
                    color = tile,
                    size = Size(1024f, 1024f),
                    cornerRadius = CornerRadius(229f),
                )
            }
            for (i in bars.indices) {
                val (start, end) = GRADIENT_AXES[i]
                drawPath(
                    path = bars[i],
                    brush = Brush.linearGradient(
                        listOf(stops.getOrElse(i * 2) { Color.White }, stops.getOrElse(i * 2 + 1) { Color.Gray }),
                        start = start,
                        end = end,
                    ),
                )
            }
        }
    }
}
