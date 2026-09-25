import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:app_settings/app_settings.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';

import '../models/tweak_model.dart';
import '../utils/shell_utils.dart';

/// Bir tweak, kök (root) erişimi olmadan çalıştırılmaya çalışıldığında
/// fırlatılır — bkz. `Tweak` sınıf yorumu.
class WinUtilRootRequiredException implements Exception {
  const WinUtilRootRequiredException();
  @override
  String toString() => 'Bu islem kok (root) erisimi gerektiriyor.';
}

/// winutil'in (Chris Titus Tech) Windows PowerShell mantığının Android
/// karşılığı: sistem tweak'leri, önerilen açık kaynak uygulamalar ve
/// ayarlar kısayolları.
///
/// PLATFORM GERÇEĞİ (bkz. `apps_service.dart`'taki benzer not): winutil,
/// Windows'ta "Yönetici olarak çalıştır" ile PowerShell üzerinden
/// doğrudan sistem ayarlarını değiştirir. Android'de üçüncü parti bir
/// uygulamanın eşdeğer yetkiye sahip olmasının tek yolu KÖK (root)
/// erişimidir. Bu servis bu sınırı gizlemez:
/// - Kök varsa: [runTweak] komutu gerçekten `su -c` ile çalıştırır.
/// - Kök yoksa: [runTweak] hiçbir şey çalıştırmadan
///   [WinUtilRootRequiredException] fırlatır ki arayüz durumu dürüstçe
///   göstersin (bkz. `winutil_tab.dart`).
class WinUtilService {
  bool? _rootCache;
  ShellIdentity? _rootIdentity;

  /// Son başarılı kök testinde `su -c id` ile okunan kimlik (kök yoksa
  /// `null`) - arayüzde "root (uid 0)" olarak gösterilir.
  ShellIdentity? get rootIdentity => _rootIdentity;

  /// Cihazda kök erişimi olup olmadığını dener ve sonucu bir kez
  /// önbelleğe alır (her tweak öncesi `su` izin diyaloğu tekrar tekrar
  /// açılmasın diye). `forceRecheck: true` ile önbellek atlanabilir.
  Future<bool> hasRoot({bool forceRecheck = false}) async {
    if (_rootCache != null && !forceRecheck) return _rootCache!;
    try {
      final result = await Process.run('su', ['-c', 'id']).timeout(
        const Duration(seconds: 8),
      );
      final out = '${result.stdout}';
      _rootCache = result.exitCode == 0 && out.contains('uid=0');
      _rootIdentity = _rootCache == true ? parseIdOutput(out) : null;
    } catch (_) {
      // `su` ikili dosyası yok (cihaz rootlu değil) ya da izin isteği
      // zaman aşımına uğradı - her iki durumda da kök yok say.
      _rootCache = false;
      _rootIdentity = null;
    }
    return _rootCache!;
  }

  /// [command]'ı `su -c` altında çalıştırıp sonucunu (çıktı + çıkış kodu)
  /// döner - durum okuma (`checkCommand`) için. Kök yoksa
  /// [WinUtilRootRequiredException] fırlatır. Çıkış kodu sıfırdan farklıysa
  /// stderr da çıktıya eklenir ki hata nedeni görülebilsin.
  Future<ShellResult> exec(String command) async {
    if (!await hasRoot()) {
      throw const WinUtilRootRequiredException();
    }
    final result = await Process.run('su', ['-c', command]).timeout(
      const Duration(seconds: 15),
    );
    var output = '${result.stdout}'.trim();
    final err = '${result.stderr}'.trim();
    if (result.exitCode != 0 && err.isNotEmpty) {
      output = '$output\n$err'.trim();
    }
    return ShellResult(output: output, exitCode: result.exitCode);
  }

  /// [tweak]'in komutunu (`apply` ise [Tweak.applyCommand], değilse
  /// [Tweak.revertCommand]) `su -c` altında çalıştırır ve çıktısını satır
  /// satır akıtır. Kök yoksa hiçbir şey çalıştırmadan
  /// [WinUtilRootRequiredException] fırlatır; komut başarısız olursa
  /// (sıfırdan farklı çıkış kodu ya da çıktıda hata izi)
  /// [TweakCommandFailedException] fırlatır - başarısız komut ASLA
  /// "tamamlandı" diye görünmez.
  Stream<String> runTweak(Tweak tweak, {required bool apply}) async* {
    if (!await hasRoot()) {
      throw const WinUtilRootRequiredException();
    }
    final command = apply ? tweak.applyCommand : tweak.revertCommand;
    if (command == null || command.isEmpty) return;
    yield* _stream(command);
  }

  Stream<String> _stream(String command) async* {
    final process = await Process.start('su', ['-c', command]);
    final controller = StreamController<String>();
    final collected = StringBuffer();

    Future<void> pump(Stream<List<int>> source) {
      return source
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .forEach((line) {
        collected.writeln(line);
        controller.add(line);
      });
    }

    unawaited(() async {
      try {
        try {
          await Future.wait([pump(process.stdout), pump(process.stderr)]);
        } catch (_) {
          // Akış okuma hatası çıkış kodunu etkilemez; aşağıda yine de
          // çıkış kodu değerlendirilir.
        }
        final code = await process.exitCode;
        final result = ShellResult(output: collected.toString().trim(), exitCode: code);
        final reason = failureReason(result);
        if (reason == null) {
          controller.add('› tamamlandı (kod $code)');
        } else {
          controller.addError(TweakCommandFailedException(reason));
        }
      } catch (e) {
        controller.addError(TweakCommandFailedException('komut sonucu okunamadı: $e'));
      } finally {
        await controller.close();
      }
    }());

    yield* controller.stream;
  }

  Future<List<Tweak>> loadBundledTweaks() async {
    final raw = await rootBundle.loadString('assets/config/tweaks.json');
    return tweaksFromJsonString(raw);
  }

  Future<List<RecommendedApp>> loadBundledApplications() async {
    final raw = await rootBundle.loadString('assets/config/applications.json');
    return appsFromJsonString(raw);
  }

  Future<List<FeatureShortcut>> loadBundledFeatures() async {
    final raw = await rootBundle.loadString('assets/config/features.json');
    return featuresFromJsonString(raw);
  }

  /// `tweaks.json` ile aynı şemaya sahip uzak bir JSON adresinden güncel
  /// tweak listesini çeker. Başarısız olursa `null` döner; çağıran taraf
  /// (bkz. `winutil_tab.dart`) bunu kullanıcıya bir hata mesajı olarak
  /// gösterir, sessizce yutmaz.
  Future<List<Tweak>?> fetchRemoteTweaks(String url) async {
    try {
      final response = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 12));
      if (response.statusCode != 200) return null;
      return tweaksFromJsonString(response.body);
    } catch (_) {
      return null;
    }
  }

  /// Belirtilen sistem ayarları ekranını açar - KÖK GEREKTİRMEZ, bu
  /// yüzden [FeatureShortcut] listesindeki tüm kısayollar rootsuz
  /// cihazlarda da gerçekten çalışır (bkz. `app_settings` paketi).
  Future<void> openFeatureSettings(FeatureShortcut feature) async {
    final type = _settingsTypeFromString(feature.settingsType);
    try {
      await AppSettings.openAppSettings(type: type);
    } catch (_) {
      // Cihaz/OEM bu ekranı desteklemiyorsa sessizce yok say - aynı
      // desen `apps_service.dart` > `openSystemSettings()` icin de gecerli.
    }
  }

  AppSettingsType _settingsTypeFromString(String value) {
    switch (value) {
      case 'developer':
        return AppSettingsType.developer;
      case 'batteryOptimization':
        return AppSettingsType.batteryOptimization;
      case 'location':
        return AppSettingsType.location;
      case 'internalStorage':
        return AppSettingsType.internalStorage;
      case 'accessibility':
        return AppSettingsType.accessibility;
      case 'security':
        return AppSettingsType.security;
      case 'display':
        return AppSettingsType.display;
      default:
        return AppSettingsType.settings;
    }
  }

  /// Önerilen bir uygulamanın indirme/mağaza bağlantısını varsayılan
  /// tarayıcıda açar. Bu uygulama APK'ları sessizce indirip kurmaz (bu,
  /// Android'de `REQUEST_INSTALL_PACKAGES` + kullanıcının açık onayını
  /// gerektirir) — bunun yerine kullanıcıyı doğrudan resmi kaynağa
  /// (F-Droid/GitHub) yönlendirir.
  Future<bool> openAppLink(RecommendedApp app) async {
    final uri = Uri.parse(app.url);
    try {
      return await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      return false;
    }
  }
}
