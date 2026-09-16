import 'package:shizuku_api/shizuku_api.dart';

import '../models/tweak_model.dart';

/// Bir tweak, Shizuku servisi çalışmıyorken ya da izin verilmemişken
/// çalıştırılmaya çalışıldığında fırlatılır - bkz. sınıf yorumu.
class ShizukuUnavailableException implements Exception {
  const ShizukuUnavailableException(this.reason);
  final String reason;
  @override
  String toString() => reason;
}

/// [WinUtilService]'in kök (root) tabanlı yoluna paralel, KÖK GEREKTİRMEYEN
/// ikinci bir çalıştırma yolu: Shizuku (bkz. https://shizuku.rikka.app).
///
/// Kullanıcının cihazında bir kez yaptığı "Kablosuz hata ayıklama"
/// eşleştirmesi üzerinden Shizuku bir arka plan servisi başlatır; bu servis
/// çalıştığı sürece bu uygulama `adb shell` ile aynı yetki seviyesinde
/// komut çalıştırabilir:
/// - Kullanıcı Shizuku'yu normal (rootsuz) başlattıysa komutlar "shell"
///   kullanıcısı (adb ile aynı) kimliğiyle çalışır - `pm`, `settings`,
///   `cmd`, `dumpsys` gibi çoğu debloat/tweak komutu bu seviyede çalışır.
/// - Kullanıcı Shizuku'yu kökle (Sui/Magisk) başlattıysa komutlar root
///   kimliğiyle çalışır.
/// - Gerçekten tam kök isteyen birkaç tweak, ilk durumda yine başarısız
///   olabilir; bu servis bunu gizlemez, komutun gerçek çıktısına/hatasına
///   göre raporlar (bkz. `runTweak`).
///
/// `WinUtilService.runTweak` ile AYNI İMZAYI taşır ki arayüz (bkz.
/// `winutil_tab.dart`) iki çalıştırma yolunu birbirinin yerine kullanabilsin.
class ShizukuService {
  final _api = ShizukuApi();

  /// Cihazda Shizuku servisi kurulu ve çalışıyor mu?
  Future<bool> isRunning() async {
    try {
      return await _api.pingBinder() ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Uygulamaya daha önce Shizuku izni verilmiş mi?
  Future<bool> hasPermission() async {
    try {
      return await _api.checkPermission() ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Kullanıcıya Shizuku izin diyaloğunu gösterir ve sonucu döner.
  Future<bool> requestPermission() async {
    try {
      return await _api.requestPermission() ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Servis çalışıyor VE izin verilmiş mi - [runTweak] için ön koşul.
  Future<bool> isReady() async {
    if (!await isRunning()) return false;
    return hasPermission();
  }

  /// [tweak]'in komutunu (`apply` ise [Tweak.applyCommand], değilse
  /// [Tweak.revertCommand]) Shizuku üzerinden çalıştırır ve çıktısını
  /// satır satır akıtır. Servis hazır değilse hiçbir şey çalıştırmadan
  /// [ShizukuUnavailableException] fırlatır.
  Stream<String> runTweak(Tweak tweak, {required bool apply}) async* {
    if (!await isReady()) {
      throw const ShizukuUnavailableException(
        'Shizuku servisi çalışmıyor ya da izin verilmedi.',
      );
    }
    final command = apply ? tweak.applyCommand : tweak.revertCommand;
    if (command == null || command.isEmpty) return;
    yield '› Shizuku (ADB) üzerinden çalıştırılıyor: $command';
    final result = await _api.runCommand(command);
    if (result != null && result.trim().isNotEmpty) {
      for (final line in result.trim().split('\n')) {
        yield '  $line';
      }
    }
    yield '› tamamlandı';
  }
}
