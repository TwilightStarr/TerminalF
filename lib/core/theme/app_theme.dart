import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Uygulamanin tek `ThemeData` kaynagi. AMOLED tema disinda alternatif
/// bir tema (ac.k mod vb.) bilincli olarak sunulmuyor - proje tamamen
/// AMOLED cihazlar icin tasarlaniyor (bkz. README).
class AppTheme {
  AppTheme._();

  static ThemeData get dark {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: AppColors.background,
      canvasColor: AppColors.background,
      fontFamily: 'monospace',
      colorScheme: const ColorScheme.dark(
        surface: AppColors.background,
        primary: AppColors.accent,
        secondary: AppColors.accentCyan,
        error: AppColors.danger,
      ),
      textTheme: const TextTheme(
        bodyLarge: TextStyle(color: AppColors.text),
        bodyMedium: TextStyle(color: AppColors.text),
        bodySmall: TextStyle(color: AppColors.muted),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected) ? AppColors.accent : AppColors.muted,
        ),
        trackColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected)
              ? AppColors.accent.withOpacity(0.4)
              : AppColors.border,
        ),
      ),
      splashColor: AppColors.accent.withOpacity(0.08),
      highlightColor: Colors.transparent,
      dividerColor: AppColors.border,
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.accent,
        linearTrackColor: AppColors.border,
      ),
    );
  }
}
