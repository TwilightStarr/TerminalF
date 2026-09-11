import 'package:installed_apps/app_info.dart';
import 'package:installed_apps/installed_apps.dart';

/// Godot çekirdeğinde karşılığı olmayan, bu Flutter sürümüne özgü yeni
/// bir yetenek: cihazdaki diğer uygulamaları **listeleme ve yönetme**.
///
/// ÖNEMLİ PLATFORM KISITI: Android, güvenlik modeli gereği (API 21 /
/// Lollipop'tan beri) üçüncü parti uygulamaların diğer uygulamaların
/// **anlık RAM kullanımını okumasına** ya da onları **doğrudan/programatik
/// olarak durdurmasına** (force-stop) izin vermez — bu iki yetenek yalnızca
/// sistem uygulamalarına veya kök (root) erişimine açıktır. Bu, eksik
/// yazılmış bir kod değil, işletim sisteminin kasıtlı bir güvenlik
/// sınırıdır; bu yüzden burada sahte/tahmini bir RAM değeri
/// **üretilmiyor**. Bunun yerine, işletim sisteminin gerçekten izin
/// verdiği yönetim eylemleri sunuluyor:
/// - [launch]: uygulamayı gerçekten başlatır.
/// - [openSystemSettings]: uygulamanın sistem "Uygulama Bilgisi" ekranını
///   açar — gerçek "Zorla Durdur" düğmesi bu ekranda bulunur.
/// - [uninstall]: uygulamayı sistem onay diyaloğu üzerinden gerçekten kaldırır.
///
/// iOS'ta `installed_apps` paketi bu özellikleri desteklemez (bkz. paket
/// belgeleri); bu servis o durumda sessizce boş/başarısız sonuç döner.
class AppsService {
  /// Cihazdaki yüklü uygulamaları döndürür.
  ///
  /// `includeSystemApps` false ise yalnızca kullanıcının kendi kurduğu,
  /// başlatılabilir uygulamalar listelenir (varsayılan, Windows Görev
  /// Yöneticisi'ndeki "uygulamalar" sekmesine daha yakın bir görünüm).
  Future<List<AppInfo>> list({required bool includeSystemApps}) async {
    try {
      return await InstalledApps.getInstalledApps(
        excludeSystemApps: !includeSystemApps,
        excludeNonLaunchableApps: true,
        withIcon: true,
      );
    } catch (_) {
      return const [];
    }
  }

  Future<void> launch(String packageName) async {
    try {
      await InstalledApps.startApp(packageName);
    } catch (_) {
      // Platform desteklemiyorsa veya uygulama kaldırılmışsa sessizce yok say.
    }
  }

  /// Sistemin "Uygulama Bilgisi" ekranını açar. Gerçek "Zorla Durdur"
  /// düğmesi yalnızca burada bulunur — bkz. sınıf yorumundaki platform kısıtı.
  Future<void> openSystemSettings(String packageName) async {
    try {
      await InstalledApps.openSettings(packageName);
    } catch (_) {
      // Platform desteklemiyorsa sessizce yok say.
    }
  }

  /// Uygulamayı sistem onay diyaloğu üzerinden kaldırır. Başarılıysa
  /// `true` döner ki çağıran taraf listeyi yenileyip kullanıcıya somut
  /// bir sonuç gösterebilsin.
  Future<bool> uninstall(String packageName) async {
    try {
      return await InstalledApps.uninstallApp(packageName) == true;
    } catch (_) {
      return false;
    }
  }
}
