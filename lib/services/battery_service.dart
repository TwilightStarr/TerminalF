import 'package:battery_plus/battery_plus.dart';

import '../utils/battery_math.dart';

/// Tek bir pil okumasinin anlik sonucu.
///
/// `percent`, cihaz pil yuzdesini okuyamazsa `null` olur (Godot
/// tarafinda `OS.get_power_percent_left() < 0` durumuna karsilik gelir).
class BatteryReading {
  const BatteryReading({required this.percent, required this.state, this.estimatedRemaining});

  final int? percent;
  final BatteryState state;

  /// Godot çekirdeğinde karşılığı olmayan, bu Flutter sürümüne özgü
  /// kaba bir tahmin: deşarj/şarj hızına göre %0'a ya da %100'e kalan
  /// süre. Yeterli veri toplanmadıysa (uygulama yeni açıldıysa, yüzde
  /// henüz hiç değişmediyse) `null` olur - bkz. [BatteryService].
  final Duration? estimatedRemaining;

  static const BatteryReading unknown = BatteryReading(percent: null, state: BatteryState.unknown);
}

class _BatterySample {
  const _BatterySample(this.time, this.percent);
  final DateTime time;
  final int percent;
}

/// Godot'taki `OS.get_power_state()` / `OS.get_power_percent_left()`
/// çağrılarının Flutter karşılığı, artık kalan-süre tahmini de dahil.
///
/// `battery_plus` kalan süreyi (POWERSTATE ... seconds_left) cross-platform
/// olarak vermiyor (bkz. README > YGL); bu yüzden burada kendi basit
/// tahmin algoritmamızı yürütüyoruz: son [_historyWindow] penceresindeki
/// en eski ve en yeni yüzde örnekleri arasındaki değişim hızını
/// kullanarak %0'a (deşarj) ya da %100'e (şarj) kalan süreyi doğrusal
/// olarak ekstrapole ediyoruz. Kesin değil, kaba bir tahmindir - ama
/// hiçbir şey göstermemekten daha kullanışlıdır.
class BatteryService {
  final Battery _battery = Battery();
  final List<_BatterySample> _history = [];

  static const _historyWindow = Duration(minutes: 15);
  static const _minElapsedForEstimate = Duration(seconds: 60);

  Future<BatteryReading> read() async {
    try {
      final level = await _battery.batteryLevel;
      final state = await _battery.batteryState;
      _recordSample(level);
      return BatteryReading(percent: level, state: state, estimatedRemaining: _estimateRemaining(state));
    } catch (_) {
      return BatteryReading.unknown;
    }
  }

  void _recordSample(int percent) {
    final now = DateTime.now();
    _history.add(_BatterySample(now, percent));
    final cutoff = now.subtract(_historyWindow);
    _history.removeWhere((sample) => sample.time.isBefore(cutoff));
  }

  /// Tahmin matematiğinin kendisi (saat farkından hıza, hızdan kalan
  /// süreye) `estimateRemainingDuration()`'a taşındı — bkz.
  /// `lib/utils/battery_math.dart`. Amaç: birim testlerinin gerçek pil
  /// donanımını/`Battery()` platform kanalını mock'lamasına gerek
  /// kalmadan bu saf matematiği doğrulayabilmesi (bkz.
  /// `test/battery_math_test.dart` ve README > YGL).
  Duration? _estimateRemaining(BatteryState state) {
    if (_history.length < 2) return null;
    return estimateRemainingDuration(
      state: state,
      sampleCount: _history.length,
      firstTime: _history.first.time,
      firstPercent: _history.first.percent,
      lastTime: _history.last.time,
      lastPercent: _history.last.percent,
      minElapsedForEstimate: _minElapsedForEstimate,
    );
  }
}
