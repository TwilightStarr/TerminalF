import 'dart:async';

import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:vibration/vibration.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../core/theme/app_colors.dart';
import '../services/battery_service.dart';
import '../services/brightness_service.dart';
import '../services/device_service.dart';
import '../services/fps_counter.dart';
import '../services/memory_service.dart';
import '../services/network_service.dart';
import '../services/settings_service.dart';
import '../services/storage_service.dart';
import '../tabs/apps_tab.dart';
import '../tabs/controls_tab.dart';
import '../tabs/device_tab.dart';
import '../tabs/overview_tab.dart';
import '../tabs/performance_tab.dart';
import '../widgets/battery_badge.dart';
import '../widgets/segmented_tab_bar.dart';

/// Uygulamanın tek ana ekranı.
///
/// Godot sürümündeki `Main.gd` (`_ready()` + 1 saniyelik `Timer` +
/// `_refresh_dynamic_info()`) mimarisinin Flutter karşılığı: tüm veri
/// okuma servisleri burada bir araya getiriliyor, sekme içerikleri ise
/// saf (stateless) widget'lara devrediliyor.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const _tabs = [
    TerminalTabDef('overview', 'Genel Bakış'),
    TerminalTabDef('device', 'Cihaz'),
    TerminalTabDef('performance', 'Performans'),
    TerminalTabDef('controls', 'Kontroller'),
    TerminalTabDef('apps', 'Uygulamalar'),
  ];

  final _batteryService = BatteryService();
  final _brightnessService = BrightnessService();
  final _deviceService = DeviceService();
  final _memoryService = MemoryService();
  final _storageService = StorageService();
  final _networkService = NetworkService();
  final _settingsService = SettingsService();
  final _fpsCounter = FpsCounter();

  Timer? _timer;

  String _activeTab = 'overview';

  BatteryReading? _battery;
  DeviceStaticInfo? _device;
  MemoryReading _memory = MemoryReading.empty;
  StorageReading? _storage;
  String _localIp = '-';
  double _currentFps = 0;
  final List<double> _fpsHistory = [];

  bool _keepScreenOn = false;
  DateTime? _lastUpdated;

  /// Uygulama-seviyesi ekran parlaklığı (0.0 - 1.0). Gerçek değer
  /// `_bootstrap()` içinde okunana kadar makul bir varsayılan.
  double _brightness = 1.0;

  /// Ayrı ayrı yönetilen izinler. Yeni bir izin eklemek icin buraya
  /// eklemek ve `_permissionLabel()`'a bir etiket tanımlamak yeterli.
  ///
  /// `Permission.storage` bilinçli olarak listede DEĞİL: Android 13+
  /// (API 33) itibarıyla bu izin parçalanmış/kullanımdan kaldırılmış
  /// durumda (yerini ayrı medya izinleri aldı) ve Terminal'in kullandığı
  /// tek depolama erişimi zaten `path_provider` ile uygulamaya özel
  /// klasörler - bunlar hiçbir Android sürümünde runtime izni
  /// gerektirmez. Eskiden burada olması, hiçbir zaman anlamlı şekilde
  /// yönetilemeyen bir "Reddedildi" satırı gösteriyordu (bkz. README > YGL).
  static const _managedPermissions = [Permission.notification];
  Map<Permission, PermissionStatus> _permissionStatuses = {};

  int _vibrationDurationMs = 300;
  String _defaultTabId = 'overview';

  @override
  void initState() {
    super.initState();
    _fpsCounter.start();
    _bootstrap();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _refreshDynamic());
  }

  @override
  void dispose() {
    _timer?.cancel();
    _fpsCounter.stop();
    super.dispose();
  }

  Future<void> _bootstrap() async {
    final keepOn = await _settingsService.getKeepScreenOn();
    if (keepOn) {
      try {
        await WakelockPlus.enable();
      } catch (_) {
        // Platform desteklemiyorsa sessizce yok say (bkz. README > YGL).
      }
    }

    final vibrationMs = await _settingsService.getVibrationDurationMs();
    final defaultTab = await _settingsService.getDefaultTab();
    final brightness = await _bootstrapBrightness();
    await _loadPermissionStatuses();

    if (mounted) {
      setState(() {
        _keepScreenOn = keepOn;
        _vibrationDurationMs = vibrationMs;
        _defaultTabId = defaultTab;
        _activeTab = defaultTab;
        if (brightness != null) _brightness = brightness;
      });
    }

    await _refreshStatic();
    await _refreshDynamic();
  }

  /// Kayıtlı bir parlaklık tercihi varsa uygular; yoksa uygulamanın su
  /// anki (sistem) değerini okuyup öyle bırakır. Ya uygulanan/okunan
  /// değeri ya da hiçbir şey yapılamadıysa `null` döner.
  Future<double?> _bootstrapBrightness() async {
    final saved = await _settingsService.getBrightness();
    if (saved != null) {
      final applied = await _brightnessService.setBrightness(saved);
      if (applied) return saved;
    }
    return _brightnessService.current();
  }

  /// Henüz istek göndermeden, mevcut izin durumlarını okur - "İZİN
  /// YÖNETİMİ" kartının açılışta doğru durumla gelmesi için.
  Future<void> _loadPermissionStatuses() async {
    final entries = <Permission, PermissionStatus>{};
    for (final permission in _managedPermissions) {
      try {
        entries[permission] = await permission.status;
      } catch (_) {
        entries[permission] = PermissionStatus.denied;
      }
    }
    if (mounted) setState(() => _permissionStatuses = entries);
  }

  Future<void> _refreshStatic() async {
    final device = await _deviceService.read();
    final storage = await _storageService.read();
    final ip = await _networkService.localIpAddress();
    if (!mounted) return;
    setState(() {
      _device = device;
      _storage = storage;
      _localIp = ip;
    });
  }

  Future<void> _refreshDynamic() async {
    final battery = await _batteryService.read();
    final memory = _memoryService.read();
    final fps = _fpsCounter.sample();
    if (!mounted) return;
    setState(() {
      _battery = battery;
      _memory = memory;
      _currentFps = fps;
      _fpsHistory.add(fps);
      while (_fpsHistory.length > 30) {
        _fpsHistory.removeAt(0);
      }
      _lastUpdated = DateTime.now();
    });
  }

  Future<void> _onManualRefresh() async {
    await _refreshStatic();
    await _refreshDynamic();
  }

  Future<void> _onKeepScreenOnChanged(bool value) async {
    setState(() => _keepScreenOn = value);
    try {
      if (value) {
        await WakelockPlus.enable();
      } else {
        await WakelockPlus.disable();
      }
    } catch (_) {
      // Platform desteklemiyorsa sessizce yok say.
    }
    await _settingsService.setKeepScreenOn(value);
  }

  /// Sürükleme sırasında her tikte çağrılır: ekranı anında günceller,
  /// henüz kalıcı depolamaz (bkz. `_onBrightnessCommitted`) - sürükleme
  /// bitmeden diskte tekrar tekrar yazmamak için.
  Future<void> _onBrightnessChanged(double value) async {
    setState(() => _brightness = value);
    await _brightnessService.setBrightness(value);
  }

  Future<void> _onBrightnessCommitted(double value) async {
    await _settingsService.setBrightness(value);
  }

  Future<void> _onResetBrightness() async {
    await _brightnessService.reset();
    await _settingsService.clearBrightness();
    final current = await _brightnessService.current();
    if (!mounted) return;
    setState(() => _brightness = current ?? _brightness);
  }

  Future<void> _onVibrationDurationChanged(int ms) async {
    setState(() => _vibrationDurationMs = ms);
    await _settingsService.setVibrationDurationMs(ms);
  }

  Future<void> _onVibrateTest() async {
    try {
      await Vibration.vibrate(duration: _vibrationDurationMs);
    } catch (_) {
      // Cihazda titreşim donanımı/izni yoksa sessizce yok say.
    }
  }

  /// Tek bir izni ister ve sonucu ilgili satıra yansıtır - bulk
  /// isteğin aksine, kullanıcı hangi izni yönettiğini tam olarak görür.
  Future<void> _requestSinglePermission(Permission permission) async {
    try {
      final status = await permission.request();
      if (mounted) {
        setState(() => _permissionStatuses = {..._permissionStatuses, permission: status});
      }
    } catch (_) {
      // İstek başarısız olursa mevcut durumu koru.
    }
  }

  Future<void> _onRequestAllPermissions() async {
    for (final permission in _managedPermissions) {
      await _requestSinglePermission(permission);
    }
  }

  /// Kalıcı reddedilmiş bir izni yönetebilmek için kullanıcıyı
  /// doğrudan sistem ayarlarına yönlendirir.
  Future<void> _onOpenAppSettings() async {
    try {
      await openAppSettings();
    } catch (_) {
      // Platform desteklemiyorsa sessizce yok say.
    }
  }

  /// Önbelleği temizler ve depolama okumasını yeniler ki "DEPOLAMA
  /// YÖNETİMİ" kartındaki boyut anında güncellensin.
  Future<int> _onClearCache() async {
    final freed = await _storageService.clearCache();
    await _refreshStatic();
    return freed;
  }

  Future<void> _onDefaultTabChanged(String tabId) async {
    setState(() {
      _defaultTabId = tabId;
      _activeTab = tabId;
    });
    await _settingsService.setDefaultTab(tabId);
  }

  String _permissionLabel(Permission permission) {
    switch (permission) {
      case Permission.notification:
        return 'Bildirim';
      default:
        return permission.toString().split('.').last;
    }
  }

  String _formatTime(DateTime t) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(t.hour)}:${two(t.minute)}:${two(t.second)}';
  }

  /// NOT: 'apps' sekmesi burada DEĞİL — kendi bağımsız, sanallaştırılmış
  /// (`ListView.builder`) kaydırma alanına ihtiyaç duyduğu için
  /// `_buildScrollableBody()` içinde ayrıca ele alınıyor (bkz. yorum
  /// orada ve README > YGL).
  Widget _buildActiveTab() {
    switch (_activeTab) {
      case 'device':
        return DeviceTab(key: const ValueKey('device'), device: _device);
      case 'performance':
        return PerformanceTab(
          key: const ValueKey('performance'),
          memory: _memory,
          fps: _currentFps,
          fpsHistory: _fpsHistory,
        );
      case 'controls':
        return ControlsTab(
          key: const ValueKey('controls'),
          keepScreenOn: _keepScreenOn,
          onKeepScreenOnChanged: _onKeepScreenOnChanged,
          brightness: _brightness,
          onBrightnessChanged: _onBrightnessChanged,
          onBrightnessCommitted: _onBrightnessCommitted,
          onResetBrightness: _onResetBrightness,
          vibrationDurationMs: _vibrationDurationMs,
          onVibrationDurationChanged: _onVibrationDurationChanged,
          onVibrateTest: _onVibrateTest,
          permissionRows: [
            for (final permission in _managedPermissions)
              PermissionRow(
                label: _permissionLabel(permission),
                status: _permissionStatuses[permission] ?? PermissionStatus.denied,
                onRequest: () => _requestSinglePermission(permission),
              ),
          ],
          onRequestAllPermissions: _onRequestAllPermissions,
          onOpenAppSettings: _onOpenAppSettings,
          cacheBytes: _storage?.cacheBytes ?? 0,
          onClearCache: _onClearCache,
          tabs: _tabs,
          defaultTabId: _defaultTabId,
          onDefaultTabChanged: _onDefaultTabChanged,
        );
      case 'overview':
      default:
        return OverviewTab(
          key: const ValueKey('overview'),
          battery: _battery,
          storage: _storage,
          localIp: _localIp,
        );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'TERMINAL',
                          style: TextStyle(
                            color: AppColors.accent,
                            fontSize: 34,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 1,
                          ),
                        ),
                        SizedBox(height: 2),
                        Text(
                          'Sistem ve Cihaz Bilgi Paneli',
                          style: TextStyle(color: AppColors.muted, fontSize: 16),
                        ),
                      ],
                    ),
                  ),
                  BatteryBadge(percent: _battery?.percent, state: _battery?.state ?? BatteryState.unknown),
                  const SizedBox(width: 10),
                  _RefreshButton(onTap: _onManualRefresh),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                _lastUpdated == null ? 'Son güncelleme: -' : 'Son güncelleme: ${_formatTime(_lastUpdated!)}',
                style: const TextStyle(color: AppColors.muted, fontSize: 12),
              ),
              const SizedBox(height: 14),
              SegmentedTabBar(
                tabs: _tabs,
                activeId: _activeTab,
                onChanged: (id) => setState(() => _activeTab = id),
              ),
              const SizedBox(height: 18),
              // Başlık ve sekme çubuğu artık sabit; yalnızca aktif
              // sekmenin içeriği kendi sınırlı alanında kayar (bkz.
              // `_buildScrollableBody()`).
              Expanded(child: _buildScrollableBody()),
            ],
          ),
        ),
      ),
    );
  }

  /// Aktif sekmenin kaydırma davranışını belirler.
  ///
  /// "Uygulamalar" sekmesi kendi `ListView.builder`'ı ile **gerçek
  /// sanallaştırma** sağlar (yalnızca ekranda görünen uygulama satırları
  /// oluşturulur) — bunun çalışabilmesi için sınırlı bir yüksekliğe
  /// (`Expanded`) ihtiyaç duyar, bu yüzden burada tek başına, kendi
  /// alanında döndürülür. Diğer sekmeler değişmeyen `SingleChildScrollView`
  /// + `Column` desenini kullanmaya devam eder — içerikleri zaten kısa
  /// ve sabit sayıda kart olduğundan sanallaştırmaya ihtiyaç duymaz.
  /// Önceki sürümde tüm ekran (başlık dahil) tek bir dış
  /// `SingleChildScrollView` içindeydi ve "Uygulamalar" listesi bunun
  /// içine iç içe bir `ListView` olarak yerleştirilseydi sanallaştırma
  /// bozulurdu (bkz. README > YGL, "çok sayıda uygulama" maddesi) — bu
  /// yeniden yapılanma tam olarak bunu çözüyor.
  Widget _buildScrollableBody() {
    if (_activeTab == 'apps') {
      return const AppsTab(key: ValueKey('apps'));
    }
    return SingleChildScrollView(
      padding: const EdgeInsets.only(bottom: 36),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildActiveTab(),
        ],
      ),
    );
  }
}

class _RefreshButton extends StatelessWidget {
  const _RefreshButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      shape: const CircleBorder(),
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: Container(
          width: 52,
          height: 52,
          alignment: Alignment.center,
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            border: Border.fromBorderSide(BorderSide(color: AppColors.accentCyan)),
          ),
          child: const Icon(Icons.refresh, color: AppColors.accentCyan, size: 22),
        ),
      ),
    );
  }
}
