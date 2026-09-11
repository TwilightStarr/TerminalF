import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';

/// Ust cubukta, hangi sekmede olunursa olunsun her zaman gorunen
/// kompakt pil rozeti. Godot'taki `header_battery_badge` /
/// `_refresh_header_battery_badge()` ikilisinin Flutter karsiligi.
class BatteryBadge extends StatelessWidget {
  const BatteryBadge({super.key, required this.percent, required this.state});

  final int? percent;
  final BatteryState state;

  bool get _isCharging => state == BatteryState.charging || state == BatteryState.full;

  @override
  Widget build(BuildContext context) {
    final accent =
        percent == null ? AppColors.muted : AppColors.accentForPercent(percent!, isCharging: _isCharging);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: accent.withOpacity(0.10),
        border: Border.all(color: accent),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            _isCharging ? Icons.flash_on : Icons.battery_std,
            size: 15,
            color: accent,
          ),
          const SizedBox(width: 6),
          Text(
            percent == null ? '--%' : '$percent%',
            style: const TextStyle(color: AppColors.text, fontSize: 14),
          ),
        ],
      ),
    );
  }
}
