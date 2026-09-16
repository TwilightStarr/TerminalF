import 'package:shared_preferences/shared_preferences.dart';

/// Godot'taki `ConfigFile` + `user://terminal_settings.cfg` ikilisinin
/// Flutter karsiligi. Her tercih icin ayri bir anahtar/metod cifti
/// tutuluyor; yeni bir yonetilebilir tercih eklendikce buraya ayni
/// desende bir cift daha eklenmesi yeterli.
class SettingsService {
  static const _keepScreenOnKey = 'keep_screen_on';
  static const _defaultTabKey = 'default_tab';
  static const _vibrationDurationMsKey = 'vibration_duration_ms';
  static const _brightnessKey = 'app_brightness';
  static const _themeKey = 'app_theme';
  static const _appliedTweakIdsKey = 'applied_tweak_ids';

  Future<bool> getKeepScreenOn() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keepScreenOnKey) ?? false;
  }

  Future<void> setKeepScreenOn(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keepScreenOnKey, value);
  }

  /// Uygulama acilista hangi sekmenin gosterilecegini belirler
  /// (`overview` / `device` / `performance` / `controls`).
  Future<String> getDefaultTab() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_defaultTabKey) ?? 'overview';
  }

  Future<void> setDefaultTab(String tabId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_defaultTabKey, tabId);
  }

  /// "Titreşimi Test Et" butonunun kullanacagi milisaniye degeri.
  Future<int> getVibrationDurationMs() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(_vibrationDurationMsKey) ?? 300;
  }

  Future<void> setVibrationDurationMs(int ms) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_vibrationDurationMsKey, ms);
  }

  /// Kullanıcının son seçtiği ekran parlaklığı (0.0 - 1.0). Hiç
  /// ayarlanmadıysa `null` döner - bu durumda sistem varsayılanı
  /// kullanılır (bkz. `BrightnessService`).
  Future<double?> getBrightness() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_brightnessKey);
  }

  Future<void> setBrightness(double value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_brightnessKey, value);
  }

  /// "Sistem Değerine Sıfırla" eyleminde kayıtlı tercihi tamamen kaldırır.
  Future<void> clearBrightness() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_brightnessKey);
  }

  /// Kullanıcının son seçtiği tema anahtarı ('amoled' / 'claude').
  /// Hiç ayarlanmadıysa 'amoled' (varsayılan tema) döner - bkz.
  /// `AppThemeCodec` (`core/theme/app_colors.dart`).
  Future<String> getThemeName() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_themeKey) ?? 'amoled';
  }

  Future<void> setThemeName(String themeKey) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_themeKey, themeKey);
  }

  /// Kullanıcının en son "uyguladım" dediği tweak id'leri (bkz.
  /// `winutil_tab.dart` > `_appliedIds`). Bu, gerçek cihaz durumunun canlı
  /// bir sorgusu DEĞİL — ama en azından uygulama kapatılıp açıldığında
  /// switch'lerin sıfırlanıp "hiçbiri uygulanmamış gibi" görünmesini
  /// engeller (önceki davranış: bu liste yalnızca State içinde tutuluyordu,
  /// hiç kalıcı değildi).
  Future<Set<String>> getAppliedTweakIds() async {
    final prefs = await SharedPreferences.getInstance();
    return (prefs.getStringList(_appliedTweakIdsKey) ?? const []).toSet();
  }

  Future<void> setAppliedTweakIds(Set<String> ids) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_appliedTweakIdsKey, ids.toList());
  }
}
