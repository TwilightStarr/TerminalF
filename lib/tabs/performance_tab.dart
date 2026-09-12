import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';
import '../services/memory_service.dart';
import '../utils/format_utils.dart';
import '../widgets/accent_progress_bar.dart';
import '../widgets/fps_graph.dart';
import '../widgets/info_row.dart';
import '../widgets/terminal_card.dart';

/// "Performans" sekmesi: Bellek (RSS) ve FPS + mini grafik kartı.
class PerformanceTab extends StatelessWidget {
  const PerformanceTab({
    super.key,
    required this.memory,
    required this.fps,
    required this.fpsHistory,
  });

  final MemoryReading memory;
  final double fps;
  final List<double> fpsHistory;

  @override
  Widget build(BuildContext context) {
    final ratio = memory.peakRssBytes > 0 ? memory.currentRssBytes / memory.peakRssBytes : 0.0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TerminalCard(
          title: '› BELLEK VE PERFORMANS',
          children: [
            InfoRow(label: 'Kullanılan Bellek (RSS)', value: formatBytes(memory.currentRssBytes)),
            AccentProgressBar(value: ratio, color: AppColors.accentCyan),
            InfoRow(label: 'Zirve Bellek (RSS)', value: formatBytes(memory.peakRssBytes)),
            InfoRow(label: 'FPS', value: fps.round().toString()),
            const SizedBox(height: 8),
            Text('Son 30 Saniye', style: TextStyle(color: AppColors.muted, fontSize: 11)),
            const SizedBox(height: 6),
            FpsGraph(history: fpsHistory),
          ],
        ),
      ],
    );
  }
}
