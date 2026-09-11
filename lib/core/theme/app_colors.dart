import 'package:flutter/material.dart';

/// Terminal AMOLED renk paleti.
///
/// Godot surumundeki `scripts/Main.gd` dosyasinin en ustunde tanimli
/// `const COLOR_...` sabitleriyle birebir eslesir (bkz. eski README
/// "Renk paleti (AMOLED)" tablosu). Temayi degistirmek icin sadece
/// buradaki degerleri guncellemeniz yeterli; tum uygulama buradan
/// besleniyor.
class AppColors {
  AppColors._();

  /// Zemin - tam siyah (AMOLED piksellerin tamamen kapanmasi icin).
  static const Color background = Color(0xFF000000);

  /// Kart zemini.
  static const Color card = Color(0xFF0A0A0A);

  /// Kart / ayirici kenarligi.
  static const Color border = Color(0xFF1C1C1C);

  /// Ana metin rengi.
  static const Color text = Color(0xFFE8E8E8);

  /// Ikincil / etiket metin rengi.
  static const Color muted = Color(0xFF7A7A7A);

  /// Vurgu - yesil.
  static const Color accent = Color(0xFF00E676);

  /// Vurgu - camgobegi.
  static const Color accentCyan = Color(0xFF18FFFF);

  /// Uyari.
  static const Color warn = Color(0xFFFFAB00);

  /// Kritik.
  static const Color danger = Color(0xFFFF5252);

  /// Pil / performans yuzdesine gore vurgu rengi dondurur.
  ///
  /// Godot'taki `_battery_accent_color()` fonksiyonuyla ayni esik
  /// mantigi: sarj olurken kritik renge donmez, aksi halde %20 ve
  /// alti kirmizi, %50 ve alti turuncu, ustu yesildir.
  static Color accentForPercent(num percent, {bool isCharging = false}) {
    if (percent <= 20 && !isCharging) return danger;
    if (percent <= 50) return warn;
    return accent;
  }
}
