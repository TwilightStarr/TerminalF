import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';

/// Godot'taki `FPSGraphView` ic sinifinin birebir Flutter karsiligi:
/// son N FPS ornegini polyline olarak cizen, tek sorumlulugu cizim
/// olan hafif bir widget. Harici bir grafik paketi kullanilmiyor.
class FpsGraph extends StatelessWidget {
  const FpsGraph({super.key, required this.history, this.height = 54});

  final List<double> history;
  final double height;

  Color get _lineColor {
    if (history.isEmpty) return AppColors.accent;
    final last = history.last;
    if (last < 30) return AppColors.danger;
    if (last < 50) return AppColors.warn;
    return AppColors.accent;
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      width: double.infinity,
      child: CustomPaint(
        painter: _FpsGraphPainter(history: history, lineColor: _lineColor),
      ),
    );
  }
}

class _FpsGraphPainter extends CustomPainter {
  _FpsGraphPainter({required this.history, required this.lineColor});

  final List<double> history;
  final Color lineColor;

  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = AppColors.border
      ..strokeWidth = 1;
    canvas.drawLine(
      Offset(0, size.height - 1),
      Offset(size.width, size.height - 1),
      gridPaint,
    );

    if (history.length < 2) return;

    final peak = history.reduce((a, b) => a > b ? a : b);
    final maxVal = (peak <= 0 ? 1.0 : peak) * 1.15; // zirve deger ust kenara yapismasin
    final step = size.width / (history.length - 1);

    final path = Path();
    for (var i = 0; i < history.length; i++) {
      final y = (size.height - (history[i] / maxVal) * size.height).clamp(0.0, size.height);
      final point = Offset(i * step, y);
      if (i == 0) {
        path.moveTo(point.dx, point.dy);
      } else {
        path.lineTo(point.dx, point.dy);
      }
    }

    final linePaint = Paint()
      ..color = lineColor
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeJoin = StrokeJoin.round
      ..strokeCap = StrokeCap.round;
    canvas.drawPath(path, linePaint);
  }

  @override
  bool shouldRepaint(covariant _FpsGraphPainter oldDelegate) {
    // `history`, çağıran tarafta aynı liste referansı üzerinde güncellenebildiği
    // için (bkz. HomeScreen._fpsHistory) kimlik/içerik karşılaştırması güvenilir
    // değil. Grafik zaten saniyede bir tetiklendiği için her seferinde yeniden
    // çizmenin performans maliyeti yok denecek kadar azdır.
    return true;
  }
}
