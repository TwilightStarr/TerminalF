import 'package:flutter/material.dart';

/// Uygulamanın desteklediği tema kimlikleri.
///
/// Yeni bir tema eklemek için: 1) buraya bir isim ekleyin, 2) aşağıdaki
/// palet sabitlerinden birini tanımlayıp `_palettes` haritasına ekleyin,
/// 3) [AppThemeCodec] içindeki depolama anahtarını ve [AppThemeLabel]
/// içindeki görünen adı tanımlayın. Başka hiçbir yeri değiştirmeniz
/// gerekmez - tüm uygulama zaten `AppColors.xxx` üzerinden okuyor.
enum AppThemeName { amoled, claudeMobile }

/// Tek bir temanın renk seti.
class _ThemePalette {
  const _ThemePalette({
    required this.background,
    required this.card,
    required this.border,
    required this.text,
    required this.muted,
    required this.accent,
    required this.accentCyan,
    required this.warn,
    required this.danger,
  });

  final Color background;
  final Color card;
  final Color border;
  final Color text;
  final Color muted;
  final Color accent;
  final Color accentCyan;
  final Color warn;
  final Color danger;
}

/// Terminal AMOLED renk paleti (orijinal / varsayılan tema).
///
/// Godot sürümündeki `scripts/Main.gd` dosyasının en üstünde tanımlı
/// `const COLOR_...` sabitleriyle birebir eşleşir (bkz. eski README
/// "Renk paleti (AMOLED)" tablosu). Bu tema aynen korunuyor; hiçbir
/// değeri değiştirilmedi.
const _amoledPalette = _ThemePalette(
  background: Color(0xFF000000),
  card: Color(0xFF0A0A0A),
  border: Color(0xFF1C1C1C),
  text: Color(0xFFE8E8E8),
  muted: Color(0xFF7A7A7A),
  accent: Color(0xFF00E676),
  accentCyan: Color(0xFF18FFFF),
  warn: Color(0xFFFFAB00),
  danger: Color(0xFFFF5252),
);

/// Claude mobil uygulamasının karanlık temasından ilham alan ikinci
/// tema: sıcak antrasit zemin + kilden/turuncu vurgu rengi.
const _claudeMobilePalette = _ThemePalette(
  background: Color(0xFF262624),
  card: Color(0xFF30302E),
  border: Color(0xFF3E3D3A),
  text: Color(0xFFF5F4EF),
  muted: Color(0xFFA39E95),
  accent: Color(0xFFCC785C),
  accentCyan: Color(0xFFDDA57B),
  warn: Color(0xFFE8A33D),
  danger: Color(0xFFD1603D),
);

const Map<AppThemeName, _ThemePalette> _palettes = {
  AppThemeName.amoled: _amoledPalette,
  AppThemeName.claudeMobile: _claudeMobilePalette,
};

/// [AppThemeName] için kalıcı depolamada kullanılan anahtar <-> enum
/// dönüşümü (bkz. `SettingsService.getThemeName` / `setThemeName`).
extension AppThemeCodec on AppThemeName {
  String get storageKey => switch (this) {
        AppThemeName.amoled => 'amoled',
        AppThemeName.claudeMobile => 'claude',
      };

  static AppThemeName fromStorageKey(String? key) => switch (key) {
        'claude' => AppThemeName.claudeMobile,
        _ => AppThemeName.amoled,
      };
}

/// Tema seçim arayüzünde (Kontroller sekmesi) gösterilen isim.
extension AppThemeLabel on AppThemeName {
  String get label => switch (this) {
        AppThemeName.amoled => 'AMOLED',
        AppThemeName.claudeMobile => 'Claude',
      };
}

/// Aktif tema rengi kaynağı.
///
/// Değerler artık derleme-zamanı sabiti değil (tema çalışırken
/// değişebildiği için); bu yüzden bu sınıftan okuma yapan widget'larda
/// `const` anahtar kelimesi kaldırıldı - davranış aynen korundu, tek
/// fark renklerin artık çalışma zamanında seçilen temaya göre
/// belirlenmesi.
class AppColors {
  AppColors._();

  static AppThemeName _themeName = AppThemeName.amoled;

  /// Tema değiştiğinde tetiklenir; kök widget (`main.dart`) bunu
  /// dinleyerek `MaterialApp`'i ve sistem çubuğu renklerini yeniden
  /// kurar.
  static final ValueNotifier<AppThemeName> notifier = ValueNotifier(_themeName);

  static AppThemeName get themeName => _themeName;

  static _ThemePalette get _palette => _palettes[_themeName]!;

  /// Aktif temayı değiştirir. Kalıcı depolama burada YAPILMAZ -
  /// çağıran taraf (bkz. `SettingsService` + `main.dart`) tercihi
  /// ayrıca kaydetmeli.
  static void setTheme(AppThemeName name) {
    if (_themeName == name) return;
    _themeName = name;
    notifier.value = name;
  }

  static Color get background => _palette.background;
  static Color get card => _palette.card;
  static Color get border => _palette.border;
  static Color get text => _palette.text;
  static Color get muted => _palette.muted;
  static Color get accent => _palette.accent;
  static Color get accentCyan => _palette.accentCyan;
  static Color get warn => _palette.warn;
  static Color get danger => _palette.danger;

  /// Pil / performans yüzdesine göre vurgu rengi döndürür.
  ///
  /// Godot'taki `_battery_accent_color()` fonksiyonuyla aynı eşik
  /// mantığı: şarj olurken kritik renge dönmez, aksi halde %20 ve
  /// altı kırmızı, %50 ve altı turuncu, üstü yeşildir.
  static Color accentForPercent(num percent, {bool isCharging = false}) {
    if (percent <= 20 && !isCharging) return danger;
    if (percent <= 50) return warn;
    return accent;
  }
}
