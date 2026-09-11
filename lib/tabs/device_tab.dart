import 'package:flutter/material.dart';

import '../services/device_service.dart';
import '../widgets/info_row.dart';
import '../widgets/terminal_card.dart';

/// "Cihaz" sekmesi: Cihaz ve Ekran kartları.
class DeviceTab extends StatelessWidget {
  const DeviceTab({super.key, required this.device});

  final DeviceStaticInfo? device;

  @override
  Widget build(BuildContext context) {
    // Ekran bilgisi icin dart:ui'nin Display API'si kullaniliyor;
    // ek bir paket gerekmiyor (Godot'taki DisplayServer cagrilarinin
    // dogrudan karsiligi).
    final view = View.of(context);
    final logicalSize = view.physicalSize / view.devicePixelRatio;
    final refreshRate = view.display.refreshRate;
    final approxDpi = (view.devicePixelRatio * 160).round();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TerminalCard(
          title: '› CİHAZ',
          children: [
            InfoRow(label: 'Model', value: device?.model ?? '-', copyable: device != null),
            InfoRow(label: 'İşletim Sistemi', value: device?.os ?? '-'),
            InfoRow(label: 'İşlemci Mimarisi', value: device?.cpuArch ?? '-'),
            InfoRow(label: 'Çekirdek Sayısı', value: '${device?.coreCount ?? '-'}'),
            InfoRow(label: 'Dil / Bölge', value: device?.locale ?? '-'),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› EKRAN',
          delay: const Duration(milliseconds: 60),
          children: [
            InfoRow(label: 'Çözünürlük', value: '${logicalSize.width.round()} x ${logicalSize.height.round()}'),
            InfoRow(label: 'Yaklaşık DPI', value: '$approxDpi'),
            InfoRow(
              label: 'Yenileme Hızı',
              value: refreshRate > 0 ? '${refreshRate.round()} Hz' : 'Bilinmiyor',
            ),
            InfoRow(label: 'Ölçek', value: '${view.devicePixelRatio.toStringAsFixed(2)}x'),
          ],
        ),
      ],
    );
  }
}
