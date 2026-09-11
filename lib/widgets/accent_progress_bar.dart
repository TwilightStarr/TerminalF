import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';

/// Godot'taki `_add_progress_bar()` ile olusturulan, ince ve yuvarlak
/// koseli, tek renk dolgulu `ProgressBar`'in Flutter karsiligi.
class AccentProgressBar extends StatelessWidget {
  const AccentProgressBar({super.key, required this.value, required this.color});

  /// 0.0 - 1.0 arasi doluluk orani.
  final double value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: SizedBox(
          height: 8,
          child: LinearProgressIndicator(
            value: value.isFinite ? value.clamp(0.0, 1.0) : 0.0,
            backgroundColor: AppColors.border,
            valueColor: AlwaysStoppedAnimation<Color>(color),
          ),
        ),
      ),
    );
  }
}
