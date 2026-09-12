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
    final view = View.of(context);
    final mediaQuery = MediaQuery.of(context);
    final devicePixelRatio = mediaQuery.devicePixelRatio;
    final physicalWidth = (mediaQuery.size.width * devicePixelRatio).round();
    final physicalHeight = (mediaQuery.size.height * devicePixelRatio).round();
    final refreshRate = view.display.refreshRate;
    final approxDpi = (devicePixelRatio * 160).round();

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
            InfoRow(label: 'Çözünürlük', value: '$physicalWidth x $physicalHeight'),
            InfoRow(label: 'Yaklaşık DPI', value: '$approxDpi'),
            InfoRow(
              label: 'Yenileme Hızı',
              value: refreshRate > 0 ? '${refreshRate.round()} Hz' : 'Bilinmiyor',
            ),
            InfoRow(label: 'Ölçek', value: '${devicePixelRatio.toStringAsFixed(2)}x'),
          ],
        ),
      ],
    );
  }
}
