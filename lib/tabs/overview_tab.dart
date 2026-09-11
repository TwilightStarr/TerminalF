import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';
import '../services/battery_service.dart';
import '../services/storage_service.dart';
import '../utils/format_utils.dart' show formatBytes, formatDuration;
import '../widgets/accent_progress_bar.dart';
import '../widgets/info_row.dart';
import '../widgets/terminal_card.dart';

/// "Genel Bakış" sekmesi: Pil, Depolama, Ağ kartları.
/// Godot README'sindeki sekme gruplamasıyla birebir aynı.
class OverviewTab extends StatelessWidget {
  const OverviewTab({
    super.key,
    required this.battery,
    required this.storage,
    required this.localIp,
  });

  final BatteryReading? battery;
  final StorageReading? storage;
  final String localIp;

  String _stateLabel(BatteryState? state) {
    switch (state) {
      case BatteryState.charging:
        return 'Şarj Oluyor';
      case BatteryState.full:
        return 'Şarj Doldu';
      case BatteryState.discharging:
        return 'Pilde Çalışıyor';
      case BatteryState.connectedNotCharging:
        return 'Bağlı (Şarj Olmuyor)';
      default:
        return 'Bilinmiyor';
    }
  }

  /// `BatteryService`'in tahminini okunabilir bir etikete çevirir.
  /// Tahmin için yeterli veri henüz toplanmadıysa (uygulama yeni
  /// açıldı, yüzde henüz değişmedi) "uydurma" bir sayı göstermek
  /// yerine bunu açıkça belirtir.
  String _remainingLabel(BatteryReading? battery) {
    final state = battery?.state;
    if (state == BatteryState.full) return 'Doldu';
    if (state != BatteryState.charging && state != BatteryState.discharging) {
      return 'Hesaplanamıyor';
    }
    final remaining = battery?.estimatedRemaining;
    if (remaining == null) return 'Hesaplanıyor…';
    final suffix = state == BatteryState.charging ? 'doluma' : 'kalan';
    return '${formatDuration(remaining)} $suffix';
  }

  Color _stateColor(BatteryState? state) {
    switch (state) {
      case BatteryState.charging:
        return AppColors.accent;
      case BatteryState.full:
        return AppColors.accentCyan;
      case BatteryState.discharging:
        return AppColors.warn;
      default:
        return AppColors.muted;
    }
  }

  @override
  Widget build(BuildContext context) {
    final percent = battery?.percent;
    final state = battery?.state;
    final isCharging = state == BatteryState.charging || state == BatteryState.full;
    final accent = percent == null ? AppColors.accent : AppColors.accentForPercent(percent, isCharging: isCharging);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TerminalCard(
          title: '› PİL',
          children: [
            InfoRow(label: 'Durum', value: _stateLabel(state), valueColor: _stateColor(state)),
            InfoRow(
              label: 'Şarj Yüzdesi',
              value: percent == null ? 'Bilinmiyor' : '$percent%',
              valueColor: (percent != null && percent <= 20 && !isCharging) ? AppColors.danger : null,
            ),
            AccentProgressBar(value: (percent ?? 0) / 100.0, color: accent),
            InfoRow(label: 'Kalan Süre', value: _remainingLabel(battery)),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› DEPOLAMA',
          delay: const Duration(milliseconds: 60),
          children: [
            InfoRow(label: 'Uygulama Verisi', value: formatBytes(storage?.totalBytes ?? 0)),
            InfoRow(label: 'Önbellek', value: formatBytes(storage?.cacheBytes ?? 0)),
            InfoRow(label: 'Veri Yolu', value: storage?.path ?? '-', copyable: true),
            const SizedBox(height: 4),
            Text(
              'Önbelleği yönetmek (temizlemek) için Kontroller sekmesine bakın.',
              style: const TextStyle(color: AppColors.muted, fontSize: 11),
            ),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› AĞ',
          delay: const Duration(milliseconds: 120),
          children: [
            InfoRow(label: 'Yerel IP', value: localIp, copyable: localIp != 'Bulunamadı'),
          ],
        ),
      ],
    );
  }
}
