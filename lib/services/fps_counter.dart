import 'package:flutter/scheduler.dart';

/// Godot'taki `Engine.get_frames_per_second()` cagrisinin Flutter
/// karsiligi.
///
/// `SchedulerBinding` her uretilen kare icin bir `FrameTiming` bildirimi
/// yayinlar; bu sinif sadece kareleri sayar. `sample()` cagrildiginda
/// biriken sayi "son periyottaki FPS" olarak dondurulur ve sayac
/// sifirlanir. Tipik kullanimda `sample()`, ana ekrandaki 1 saniyelik
/// yenileme dongusunden cagrilir - Godot'taki gibi ayri bir zamanlayiciya
/// ihtiyac yoktur.
class FpsCounter {
  int _frameCount = 0;
  bool _running = false;

  void start() {
    if (_running) return;
    _running = true;
    SchedulerBinding.instance.addTimingsCallback(_onTimings);
  }

  void stop() {
    if (!_running) return;
    _running = false;
    SchedulerBinding.instance.removeTimingsCallback(_onTimings);
  }

  void _onTimings(List<FrameTiming> timings) {
    _frameCount += timings.length;
  }

  /// Son ornekten bu yana uretilen kare sayisini FPS olarak dondurur
  /// ve sayaci sifirlar. Cagirici, bunu yaklasik 1 saniyelik araliklarla
  /// cagirmalidir (bkz. HomeScreen'deki periyodik zamanlayici).
  double sample() {
    final fps = _frameCount.toDouble();
    _frameCount = 0;
    return fps;
  }
}
