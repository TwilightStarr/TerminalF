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

/// Bir tweak komutu Shizuku ÜZERİNDEN ÇALIŞTI (hazır durum kontrolünü
/// geçti, `runCommand` hata fırlatmadı) AMA ya çıktısı bilinen bir hata
/// izi içeriyor YA DA `settings get` ile geri okuma, yazılmak istenen
/// değeri doğrulayamadı. `ShizukuUnavailableException`'dan farkı: orada
/// Shizuku hiç hazır değildi; burada Shizuku hazırdı ve komut
/// gönderildi, ama sistemde beklenen etkiyi yaratmadığı doğrulandı - bkz.
/// [ShizukuService.runTweak] yorumu ve kullanıcı geri bildirimi ("60 fps
/// sabitliyorum, hiçbir değişiklik olmuyor, hâlâ 120").
class ShizukuCommandFailedException implements Exception {
  const ShizukuCommandFailedException(this.reason);
  final String reason;
  @override
  String toString() => reason;
}

/// `settings put <namespace> <key> <value...>` biçiminde ayrıştırılmış
/// tek bir alt komut - bkz. `ShizukuService._parseSettingsPut`.
class _SettingsPut {
  const _SettingsPut(this.namespace, this.key, this.value);
  final String namespace;
  final String key;
  final String value;
}

/// `settings delete <namespace> <key>` biçiminde ayrıştırılmış tek bir
/// alt komut - bkz. `ShizukuService._parseSettingsDelete`.
class _SettingsDelete {
  const _SettingsDelete(this.namespace, this.key);
  final String namespace;
  final String key;
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

  /// Bir alt komutun çıktısında bilinen hata izleri var mı? `settings`,
  /// `pm`, `cmd` gibi araçlar başarısız olduklarında genelde sıfırdan
  /// farklı bir çıkış koduyla DEĞİL, stdout'a yazılmış bir hata metniyle
  /// başarısız olur ve `shizuku_api` bunu ayrı raporlamaz - bu yüzden
  /// metni kendimiz tarıyoruz (bkz. `runTweak` yorumu).
  static const _failureMarkers = [
    'exception',
    'error:',
    'unknown command',
    'permission denied',
    'not allowed',
    'securityexception',
    'illegalstateexception',
    'illegalargumentexception',
    'usage:',
  ];

  bool _looksLikeFailure(String output) {
    final lower = output.toLowerCase();
    return _failureMarkers.any(lower.contains);
  }

  _SettingsPut? _parseSettingsPut(String statement) {
    final parts = statement.trim().split(RegExp(r'\s+'));
    if (parts.length < 4 || parts[0] != 'settings' || parts[1] != 'put') {
      return null;
    }
    return _SettingsPut(parts[2], parts[3], parts.sublist(4).join(' '));
  }

  _SettingsDelete? _parseSettingsDelete(String statement) {
    final parts = statement.trim().split(RegExp(r'\s+'));
    if (parts.length < 3 || parts[0] != 'settings' || parts[1] != 'delete') {
      return null;
    }
    return _SettingsDelete(parts[2], parts[3]);
  }

  /// Sayısal değerleri ("60" ile "60.0" gibi) biçim farkına takılmadan
  /// karşılaştırır; sayısal değilse düz metin karşılaştırması yapar.
  bool _valuesMatch(String actual, String expected) {
    final a = double.tryParse(actual.trim());
    final e = double.tryParse(expected.trim());
    if (a != null && e != null) return a == e;
    return actual.trim() == expected.trim();
  }

  Future<String?> _readSetting(String namespace, String key) async {
    try {
      final out = await _api.runCommand('settings get $namespace $key');
      return out?.trim();
    } catch (_) {
      return null;
    }
  }

  /// [tweak]'in komutunu (`apply` ise [Tweak.applyCommand], değilse
  /// [Tweak.revertCommand]) Shizuku üzerinden çalıştırır ve çıktısını
  /// satır satır akıtır. Servis hazır değilse hiçbir şey çalıştırmadan
  /// [ShizukuUnavailableException] fırlatır.
  ///
  /// ÖNEMLİ DÜZELTME (bkz. kullanıcı bildirimi: "Shizuku ile 60 fps
  /// sabitliyorum, hiçbir değişiklik olmuyor, hâlâ 120"):
  ///
  /// `WinUtilService._stream` (kök/root yolu) komutu `Process.start('su',
  /// ['-c', command])` ile çalıştırır; `su -c` argümanını TAM BİR KABUĞA
  /// (`sh`) verir, bu yüzden `;` ile ayrılmış çoklu komutlar
  /// (`tweaks.json`'daki refresh-rate tweak'i gibi: "settings put system
  /// peak_refresh_rate 60.0; settings put system min_refresh_rate
  /// 60.0") sorunsuz çalışır.
  ///
  /// Eski `runTweak` ise TÜM komut dizisini TEK BİR `_api.runCommand(...)`
  /// çağrısıyla, olduğu gibi Shizuku'ya iletiyordu. `shizuku_api`
  /// eklentisi bunu altta `Shizuku.newProcess` ile, bir kabuk ÜZERİNDEN
  /// DEĞİL doğrudan `exec()` semantiğiyle çalıştırıyor - yani `;` bir
  /// kabuk operatörü olarak yorumlanmıyor, düz bir argüman gibi
  /// `settings` komutuna geçiyor. Sonuç: zincirin yalnızca ilk parçası
  /// (`peak_refresh_rate`) yazılıyor, ikinci parçası (`min_refresh_rate`)
  /// hiç çalışmıyor (ya da tam tersi) - ekran o yüzden hep eski hızında
  /// kalıyor gibi görünüyor, çünkü iki değerden yalnızca biri değişmiş
  /// oluyor ve sistem o durumda yine desteklediği en yüksek hızı seçiyor.
  /// Bu da eski kodun hatayı hiç fark etmeden `› tamamlandı` yazdırmasıyla
  /// birleşince, kullanıcıya "çalıştı ama hiçbir şey olmadı" izlenimi
  /// veriyordu.
  ///
  /// Düzeltme iki parçalı:
  /// 1) Komutu `;` üzerinden ayrı alt komutlara bölüp HER BİRİNİ AYRI BİR
  ///    `runCommand` çağrısıyla, sırayla çalıştırıyoruz. Böylece eklentinin
  ///    dahili kabuk davranışından tamamen bağımsız hâle geliyoruz: tekil
  ///    bir "settings put system peak_refresh_rate 60.0" zaten boşlukla
  ///    ayrılmış düz argümanlardan oluşuyor, kabuğa ihtiyaç duymuyor.
  /// 2) Her `settings put`/`settings delete` alt komutundan HEMEN SONRA
  ///    aynı anahtarı `settings get` ile geri okuyup değerin GERÇEKTEN
  ///    değiştiğini doğruluyoruz. Doğrulama tutmazsa (ör. cihaz üreticisi
  ///    bu ayarı kendi katmanında geçersiz kılıyorsa - bazı Samsung/MIUI
  ///    sürümlerinde bilinen bir durum) artık sessizce "tamamlandı"
  ///    denmiyor; [ShizukuCommandFailedException] fırlatılıyor ve arayüz
  ///    bunu gerçek bir hata olarak gösteriyor (bkz. `winutil_tab.dart` >
  ///    `_toggleTweak`).
  Stream<String> runTweak(Tweak tweak, {required bool apply}) async* {
    if (!await isReady()) {
      throw const ShizukuUnavailableException(
        'Shizuku servisi çalışmıyor ya da izin verilmedi.',
      );
    }
    final command = apply ? tweak.applyCommand : tweak.revertCommand;
    if (command == null || command.isEmpty) return;

    final statements = command
        .split(';')
        .map((statement) => statement.trim())
        .where((statement) => statement.isNotEmpty)
        .toList();

    var anyFailure = false;

    for (final statement in statements) {
      yield '› Shizuku (ADB) üzerinden çalıştırılıyor: $statement';

      String? result;
      try {
        result = await _api.runCommand(statement);
      } catch (e) {
        anyFailure = true;
        yield '  ✗ çalıştırılamadı: $e';
        continue;
      }

      final trimmed = result?.trim() ?? '';
      if (trimmed.isNotEmpty) {
        for (final line in trimmed.split('\n')) {
          yield '  $line';
        }
        if (_looksLikeFailure(trimmed)) {
          anyFailure = true;
          yield '  ✗ cihaz bu komutu reddetti (yukarıdaki çıktıya bakın)';
          continue;
        }
      }

      final put = _parseSettingsPut(statement);
      if (put != null) {
        final verify = await _readSetting(put.namespace, put.key);
        if (verify == null) {
          yield '  ⚠ doğrulanamadı (geri okuma başarısız)';
        } else if (_valuesMatch(verify, put.value)) {
          yield '  ✓ doğrulandı: ${put.key} = $verify';
        } else {
          anyFailure = true;
          yield '  ✗ doğrulama başarısız: ${put.key} hâlâ "$verify" '
              '(beklenen "${put.value}") - cihaz üreticisi bu ayarı kendi '
              'katmanında geçersiz kılıyor olabilir';
        }
        continue;
      }

      final delete = _parseSettingsDelete(statement);
      if (delete != null) {
        final verify = await _readSetting(delete.namespace, delete.key);
        if (verify == null || verify.isEmpty || verify.toLowerCase() == 'null') {
          yield '  ✓ doğrulandı: ${delete.key} temizlendi';
        } else {
          anyFailure = true;
          yield '  ✗ doğrulama başarısız: ${delete.key} hâlâ "$verify"';
        }
      }
    }

    if (anyFailure) {
      throw const ShizukuCommandFailedException(
        'Komut(lar) Shizuku üzerinden çalıştı ama sistemde beklenen etkiyi '
        'yaratmadığı doğrulandı - konsol satırlarına bakın.',
      );
    }
    yield '› tamamlandı (doğrulandı)';
  }
}
