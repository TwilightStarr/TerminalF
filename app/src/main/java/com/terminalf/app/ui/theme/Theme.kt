package com.terminalf.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.terminalf.app.core.AppPalette
import com.terminalf.app.core.AppPalettes

/**
 * Uygulamanın renk kaynağı. Değerler seçili temaya göre değişir; renkleri her zaman
 * `AppTheme.colors.xxx` ile okuyun (sabit olarak saklamayın). Tema/logo değişince tüm arayüz
 * yeniden çizilir (CompositionLocal).
 */
@Immutable
class AppColorScheme(val palette: AppPalette, logoArgb: List<Long>) {
    val isDark: Boolean = palette.isDark

    val bg = Color(palette.bg)
    val surface = Color(palette.surface)
    val surfaceHigh = Color(palette.surfaceHigh)
    val outline = Color(palette.outline)
    val text = Color(palette.text)
    val muted = Color(palette.muted)
    val primary = Color(palette.primary)
    val secondary = Color(palette.secondary)
    val good = Color(palette.good)
    val warn = Color(palette.warn)
    val danger = Color(palette.danger)
    val logoFrameA = Color(palette.logoFrameA)
    val logoFrameB = Color(palette.logoFrameB)

    /** Logo çiziminde kullanılan iki renk (tek renkse iki kez döner). */
    val logoColors: List<Color> = logoArgb.map { Color(it) }

    /** Marka gradyanı (sol üst -> sağ alt). */
    val brand: Brush = Brush.linearGradient(listOf(primary, secondary))

    /** Yüzdeye göre "ne kadar iyi" rengi (yüksek = iyi). */
    fun forHealth(score: Number): Color {
        val s = score.toDouble()
        return when {
            s >= 75 -> good
            s >= 45 -> warn
            else -> danger
        }
    }

    /** Doluluk oranına göre renk (yüksek = kötü): RAM / depolama için. */
    fun forUsage(ratio: Double): Color = when {
        ratio >= 0.9 -> danger
        ratio >= 0.75 -> warn
        else -> primary
    }
}

val LocalAppColors = staticCompositionLocalOf<AppColorScheme> {
    AppColorScheme(AppPalettes.monoDark, listOf(AppPalettes.monoDark.logoA, AppPalettes.monoDark.logoB))
}

object AppTheme {
    val colors: AppColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

/** Flutter'daki `Color.faded` karşılığı: rengin saydamlığını (0-1) ayarlar. */
fun Color.faded(opacity: Float): Color = copy(alpha = opacity.coerceIn(0f, 1f))

@Composable
fun TerminalFTheme(
    palette: AppPalette,
    logoArgb: List<Long>,
    content: @Composable () -> Unit,
) {
    val scheme = AppColorScheme(palette, logoArgb)
    val material = if (scheme.isDark) {
        darkColorScheme(
            primary = scheme.primary,
            onPrimary = scheme.bg,
            secondary = scheme.secondary,
            onSecondary = scheme.bg,
            background = scheme.bg,
            onBackground = scheme.text,
            surface = scheme.surface,
            onSurface = scheme.text,
            surfaceVariant = scheme.surfaceHigh,
            onSurfaceVariant = scheme.muted,
            surfaceTint = Color.Transparent,
            error = scheme.danger,
            outline = scheme.outline,
            outlineVariant = scheme.outline,
            surfaceContainerLowest = scheme.bg,
            surfaceContainerLow = scheme.surface,
            surfaceContainer = scheme.surface,
            surfaceContainerHigh = scheme.surfaceHigh,
            surfaceContainerHighest = scheme.surfaceHigh,
        )
    } else {
        lightColorScheme(
            primary = scheme.primary,
            onPrimary = scheme.bg,
            secondary = scheme.secondary,
            onSecondary = scheme.bg,
            background = scheme.bg,
            onBackground = scheme.text,
            surface = scheme.surface,
            onSurface = scheme.text,
            surfaceVariant = scheme.surfaceHigh,
            onSurfaceVariant = scheme.muted,
            surfaceTint = Color.Transparent,
            error = scheme.danger,
            outline = scheme.outline,
            outlineVariant = scheme.outline,
            surfaceContainerLowest = scheme.bg,
            surfaceContainerLow = scheme.surface,
            surfaceContainer = scheme.surface,
            surfaceContainerHigh = scheme.surfaceHigh,
            surfaceContainerHighest = scheme.surfaceHigh,
        )
    }

    // Durum çubuğu ve gezinme çubuğu simgeleri temaya uysun.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !scheme.isDark
                controller.isAppearanceLightNavigationBars = !scheme.isDark
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = scheme.surface.toArgb()
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides scheme) {
        MaterialTheme(colorScheme = material) {
            // Flutter'ın varsayılan metin boyutu (14sp) ve rengi.
            ProvideTextStyle(TextStyle(color = scheme.text, fontSize = 14.sp)) {
                content()
            }
        }
    }
}
