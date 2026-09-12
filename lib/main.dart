import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'screens/home_screen.dart';
import 'services/settings_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Uygulama açılışında kayıtlı temayı yükle; hiç seçilmediyse
  // varsayılan AMOLED tema kullanılır (bkz. `AppThemeCodec`).
  final savedThemeKey = await SettingsService().getThemeName();
  AppColors.setTheme(AppThemeCodec.fromStorageKey(savedThemeKey));

  _applySystemChrome();

  // Godot projesindeki `window/handheld/orientation="portrait"` ayarinin
  // karsiligi.
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  runApp(const TerminalApp());
}

/// AMOLED cihazlarda durum/gezinme cubuklarinin da secili temanin zemin
/// rengiyle uyumlu olmasini sagliyoruz; Godot tarafindaki
/// `boot_splash/bg_color` ayarinin Flutter karsiligi. Tema her
/// degistiginde (bkz. `_TerminalAppState`) yeniden cagrilir ki
/// gezinme cubugu rengi de aninda guncellensin.
void _applySystemChrome() {
  SystemChrome.setSystemUIOverlayStyle(
    SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: AppColors.background,
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );
}

class TerminalApp extends StatefulWidget {
  const TerminalApp({super.key});

  @override
  State<TerminalApp> createState() => _TerminalAppState();
}

class _TerminalAppState extends State<TerminalApp> {
  @override
  void initState() {
    super.initState();
    // Tema, uygulama içinden (Kontroller sekmesi) değiştirildiğinde
    // `AppColors.notifier` tetiklenir - burada dinleyip hem `ThemeData`
    // hem de sistem çubuğu rengini yeniden kuruyoruz.
    AppColors.notifier.addListener(_onThemeChanged);
  }

  @override
  void dispose() {
    AppColors.notifier.removeListener(_onThemeChanged);
    super.dispose();
  }

  void _onThemeChanged() {
    _applySystemChrome();
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Terminal',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.dark,
      home: const HomeScreen(),
    );
  }
}
