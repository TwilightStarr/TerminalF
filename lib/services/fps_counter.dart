import 'package:flutter/scheduler.dart';

/// Godot'taki `Engine.get_frames_per_second()` cagrisinin Flutter
/// karsiligi - ama Godot'tan farkli olarak Flutter bir oyun motoru
/// GIBI surekli kare uretmez: ekranda hicbir sey degismiyorsa motor
/// yeni bir kare CIZMEZ. Bu ekran (Performans sekmesi) buyuk olcude
/// statik oldugu icin, sadece `addTimingsCallback` ile dinlemek pratikte
/// saniyede 0-1 kare olcer (bkz. ekran goruntusundeki "FPS: 1" ve
/// bundan kaynaklanan, grafikte her yenilemede kirmizi "sivri uc" gibi
/// gorunen yanlis okuma) - bu, cihazin gercek performansini degil,
/// sadece "bu ekran boyandi mi" bilgisini yansitir.
///
/// Gercek, surekli bir FPS olcumu icin motoru surekli kare uretmeye
/// zorlamak gerekir: `Ticker`, her tick'te kendini bir sonraki vsync
/// icin yeniden planlayan (`SchedulerBinding.scheduleFrameCallback`)
/// hafif bir yapidir - widget agacini yeniden olusturmadan sadece
/// "bir kare daha uret" der. Bu sayede `addTimingsCallback` gercekten
/// cihazin yenileme hizina (60/90/120 Hz) yakin, anlamli bir deger
/// bildirir. Bilincli olarak yapilan bir odun: bu, ekran boskenki
/// pil tuketimini normalde Flutter'in yapacagindan biraz artirir -
/// ama "Performans" sekmesinin butun amaci canli bir FPS gostergesi
/// oldugu icin bu odun kabul edilebilir (bkz. README > YGL).
class FpsCounter {
  int _frameCount = 0;
  bool _running = false;
  Ticker? _ticker;

  void start() {
    if (_running) return;
    _running = true;
    SchedulerBinding.instance.addTimingsCallback(_onTimings);
    // Bos callback yeterli: `Ticker` calisirken kendiliginden her
    // vsync'te yeni bir kare talep eder, biz sadece motoru "canli"
    // tutuyoruz - asil sayim yine `_onTimings` uzerinden.
    _ticker = Ticker((_) {})..start();
  }

  void stop() {
    if (!_running) return;
    _running = false;
    SchedulerBinding.instance.removeTimingsCallback(_onTimings);
    _ticker?.stop();
    _ticker?.dispose();
    _ticker = null;
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
