import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';
import '../models/tweak_model.dart';
import '../services/settings_service.dart';
import '../services/shizuku_service.dart';
import '../services/winutil_service.dart';
import '../utils/shell_utils.dart';
import '../utils/tweak_status.dart';
import '../widgets/info_row.dart';
import '../widgets/segmented_tab_bar.dart';
import '../widgets/terminal_card.dart';

/// Tweak komutlarının/durum okumalarının hangi yetki yolundan geçtiği.
/// Kök her zaman önceliklidir; kök yoksa Shizuku (yalnızca `id` test
/// komutu geçtiyse), ikisi de yoksa [none].
enum _AccessPath { root, shizuku, none }

/// "Android Araçları" bileşeni - winutil'in (Chris Titus Tech) Windows
/// tweak/debloat/uygulama-yükleyici mantığının Android'e uyarlanmış hâli.
///
/// Kendi başına bağımsız bir üst-seviye sekme DEĞİL: `apps_tab.dart`
/// içindeki dahili "Yüklü Uygulamalar / Android Araçları" geçişinin ikinci
/// sekmesi olarak gömülür (bkz. o dosyadaki yorum).
class WinUtilSection extends StatefulWidget {
  const WinUtilSection({super.key});

  @override
  State<WinUtilSection> createState() => _WinUtilSectionState();
}

class _WinUtilSectionState extends State<WinUtilSection> {
  final _service = WinUtilService();
  final _shizukuService = ShizukuService();
  final _settingsService = SettingsService();

  static const _segments = [
    TerminalTabDef('tweaks', "Tweak'ler"),
    TerminalTabDef('apps', 'Uygulamalar'),
    TerminalTabDef('shortcuts', 'Kısayollar'),
  ];
  String _segment = 'tweaks';

  bool _loading = true;
  bool? _hasRoot;
  ShellIdentity? _rootIdentity;
  bool _shizukuRunning = false;
  bool _shizukuPermitted = false;
  bool _shizukuBusy = false;
  ShellIdentity? _shizukuIdentity;
  String? _shizukuError;

  /// Shizuku servisi çalışıyor, izin verilmiş VE `id` test komutu gerçekten
  /// çalışmış mı. "Çalışıyor + izinli" tek başına yetmez: komutların
  /// gerçekten çalıştığı yalnızca test komutuyla kanıtlanır (bkz.
  /// `ShizukuService.probe`).
  bool get _shizukuReady =>
      _shizukuRunning && _shizukuPermitted && _shizukuIdentity != null;

  /// Kök (root) VEYA Shizuku üzerinden komut çalıştırılabilir mi -
  /// [_TweakTile]'ın kilit durumunu belirler.
  bool get _privileged => (_hasRoot ?? false) || _shizukuReady;

  /// Aktif yetki yolu: kök varsa kök, yoksa hazır Shizuku, yoksa hiçbiri.
  /// Hem tweak çalıştırma hem durum okuma bu yolu kullanır.
  _AccessPath get _path {
    if (_hasRoot ?? false) return _AccessPath.root;
    if (_shizukuReady) return _AccessPath.shizuku;
    return _AccessPath.none;
  }

  List<Tweak> _tweaks = const [];
  List<RecommendedApp> _apps = const [];
  List<FeatureShortcut> _features = const [];

  /// Kullanıcının "uyguladım" dediği tweak id'leri - gerçek sistem
  /// durumunun canlı bir sorgusu DEĞİL, uygulamanın kendi hafızası.
  /// Artık yalnızca YEDEK: durum önce `checkCommand` ile cihazdan okunur
  /// (bkz. `_readStatus`); okunamazsa bu kayda düşülür ve sonuç "tahmini"
  /// diye işaretlenir. Cihazdan başarıyla okunan her durum bu kümeyi de
  /// günceller (uzlaştırır). `SettingsService` ile kalıcıdır.
  Set<String> _appliedIds = {};
  final Set<String> _busyIds = {};

  /// Her tweak'in cihazdan okunan (ya da `_appliedIds`'den tahmin edilen)
  /// güncel durumu - bkz. `_refreshStatuses` ve `_readStatus`.
  Map<String, TweakStatus> _statuses = {};
  bool _statusRefreshing = false;
  DateTime? _statusUpdatedAt;

  final List<String> _consoleLines = [];
  final _remoteUrlController = TextEditingController();
  bool _remoteLoading = false;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  @override
  void dispose() {
    _remoteUrlController.dispose();
    super.dispose();
  }

  Future<void> _bootstrap() async {
    final results = await Future.wait([
      _service.loadBundledTweaks(),
      _service.loadBundledApplications(),
      _service.loadBundledFeatures(),
      _service.hasRoot(),
      _shizukuService.probe(),
      _settingsService.getAppliedTweakIds(),
    ]);
    if (!mounted) return;
    setState(() {
      _tweaks = results[0] as List<Tweak>;
      _apps = results[1] as List<RecommendedApp>;
      _features = results[2] as List<FeatureShortcut>;
      _hasRoot = results[3] as bool;
      _rootIdentity = _service.rootIdentity;
      _applyProbe(results[4] as ShizukuProbe);
      // Bir önceki oturumdan kalan "uygulandı" durumunu geri yükle - bkz.
      // `_appliedIds` alanının yorumu. Bilinmeyen id'ler (ör. uzaktan
      // yapılandırma değiştiyse) sonraki adımda otomatik elenir çünkü
      // yalnızca `_tweaks` içinde eşleşen id'ler switch'te "açık" görünür.
      _appliedIds = results[5] as Set<String>;
      _loading = false;
    });
    // Sekme açılınca durumları cihazdan oku.
    await _refreshStatuses();
  }

  /// [ShizukuService.probe] sonucunu arayüz durumuna yansıtır
  /// (`setState` içinden çağrılır).
  void _applyProbe(ShizukuProbe probe) {
    _shizukuRunning = probe.running;
    _shizukuPermitted = probe.permitted;
    _shizukuIdentity = probe.identity;
    _shizukuError = probe.error;
  }

  /// Shizuku durumunu yeniden sınar: servis çalışıyor mu, izin yoksa
  /// izin diyaloğunu gösterir, ardından `id` test komutunu çalıştırıp hangi
  /// kimlikle (shell/root) çalıştığını arayüze yansıtır. Kök zaten varsa
  /// çağrılmaz (bkz. `_PrivilegeStatusBanner`de düğmenin yalnızca kök
  /// yokken gösterilmesi).
  Future<void> _requestShizukuPermission() async {
    setState(() => _shizukuBusy = true);
    var probe = await _shizukuService.probe();
    if (probe.running && !probe.permitted) {
      final granted = await _shizukuService.requestPermission();
      _showSnack(granted ? 'Shizuku izni verildi.' : 'Shizuku izni reddedildi.');
      if (granted) probe = await _shizukuService.probe();
    }
    if (!mounted) return;
    setState(() {
      _applyProbe(probe);
      _shizukuBusy = false;
    });
    if (!probe.running) {
      _showSnack('Shizuku servisi çalışmıyor. Önce Shizuku uygulamasını başlatın.');
    } else if (probe.ready) {
      _log('✓ Shizuku hazır: `id` ${probe.identity!.label} (uid ${probe.identity!.uid}) döndürdü.');
    } else if (probe.permitted) {
      _log('✗ Shizuku izinli ama test komutu başarısız: ${probe.error}');
    }
    await _refreshStatuses();
  }

  /// Durum Paneli'ndeki "Yenile": kök/Shizuku yetkisini yeniden sınar
  /// (Shizuku uygulama açıkken sonradan başlatılmış olabilir) ve tüm
  /// tweak durumlarını cihazdan tekrar okur.
  Future<void> _refreshEverything() async {
    if (_statusRefreshing) return;
    final hasRoot = await _service.hasRoot(forceRecheck: _hasRoot != true);
    final probe = await _shizukuService.probe();
    if (!mounted) return;
    setState(() {
      _hasRoot = hasRoot;
      _rootIdentity = _service.rootIdentity;
      _applyProbe(probe);
    });
    await _refreshStatuses();
  }

  void _log(String line) {
    if (!mounted) return;
    setState(() {
      _consoleLines.add(line);
      while (_consoleLines.length > 200) {
        _consoleLines.removeAt(0);
      }
    });
  }

  Future<void> _toggleTweak(Tweak tweak, bool apply) async {
    final path = _path;
    if (path == _AccessPath.none) {
      _log('✗ ${tweak.title}: kök erişimi ya da Shizuku izni yok, atlandı.');
      _showSnack('Bu tweak kök (root) ya da Shizuku erişimi gerektiriyor.');
      return;
    }
    if (apply && tweak.dangerous) {
      final confirmed = await _confirmDangerous(tweak);
      if (confirmed != true) return;
    }
    setState(() => _busyIds.add(tweak.id));
    final via = path == _AccessPath.root
        ? 'root'
        : 'Shizuku, ${_shizukuIdentity?.label ?? 'shell'}';
    _log('${apply ? '›' : '‹'} ${tweak.title} ($via) ${apply ? 'uygulanıyor' : 'geri alınıyor'}...');
    try {
      final stream = path == _AccessPath.root
          ? _service.runTweak(tweak, apply: apply)
          : _shizukuService.runTweak(tweak, apply: apply);
      await for (final line in stream) {
        _log('  $line');
      }
      // Buraya gelindiyse komut başarısız sayılmadı: başarısızlıkta servis
      // `TweakCommandFailedException` fırlatır ve aşağıdaki `catch`'e düşülür.
      if (mounted && tweak.isToggle) {
        setState(() {
          if (apply) {
            _appliedIds.add(tweak.id);
          } else {
            _appliedIds.remove(tweak.id);
          }
        });
      }
      // Komutun "başarılı" olması etkisi olduğu anlamına gelmez (özellikle
      // rootsuz Shizuku'da): durumu cihazdan tekrar oku ve doğrula.
      await _verifyAfterRun(tweak, apply: apply, path: path);
      // `setState`'in senkron callback'i içine `await` konamayacağı için
      // kalıcı kayıt setState'ten hemen sonra yapılıyor.
      await _settingsService.setAppliedTweakIds(_appliedIds);
    } on WinUtilRootRequiredException {
      _log('✗ ${tweak.title}: kök erişimi yok.');
      _showSnack('Bu tweak kök (root) erişimi gerektiriyor.');
    } on ShizukuUnavailableException catch (e) {
      _log('✗ ${tweak.title}: $e');
      _showSnack('$e');
    } on TweakCommandFailedException catch (e) {
      final shellOnly =
          path == _AccessPath.shizuku && !(_shizukuIdentity?.isRoot ?? false);
      _log('✗ ${tweak.title}: başarısız - $e.');
      if (shellOnly) {
        _log('  ! Shizuku "shell" yetkisiyle çalışıyor; bu komut kök yetkisi '
            'gerektiriyor olabilir (rootsuz cihazlarda bazı tweak\'ler '
            'çalışmaz).');
      }
      _showSnack(
        '${tweak.title} başarısız: $e.'
        '${shellOnly ? ' Shizuku shell yetkisi bu komut için yetersiz olabilir.' : ''}',
      );
      // Zincirlenmiş komutların bir kısmı çalışmış olabilir: kesin durumu
      // cihazdan tekrar oku (okunamazsa yedek/tahmin gösterilir).
      if (tweak.isToggle) {
        final status = await _refreshOne(tweak, path);
        _log('  ↻ Cihazdaki güncel durum: ${status.state.label}'
            '${status.estimated ? ' (tahmini)' : ''}');
        await _settingsService.setAppliedTweakIds(_appliedIds);
      }
    } catch (e) {
      _log('✗ ${tweak.title}: hata - $e');
    } finally {
      if (mounted) setState(() => _busyIds.remove(tweak.id));
    }
  }

  /// Komut hata vermeden bittikten sonra durumu cihazdan tekrar okuyup
  /// beklenenle karşılaştırır. Beklenmedik bir sonuç (komut başarılı ama
  /// durum değişmedi) sessizce geçilmez, konsola ve snackbar'a yazılır.
  Future<void> _verifyAfterRun(
    Tweak tweak, {
    required bool apply,
    required _AccessPath path,
  }) async {
    if (!tweak.isToggle) return; // tek seferlik eylemin kalıcı durumu yok
    final read = await _readStatus(tweak, path);
    final status = read.status;
    _storeStatus(tweak, status);
    final expected = apply ? TweakState.on : TweakState.off;
    if (status.isLive) {
      if (status.state == expected) {
        _log('  ✓ Durum cihazdan doğrulandı: ${status.state.label}');
      } else {
        _log('  ⚠ Komut hata vermedi ama cihazda durum ${status.state.label} '
            'okundu (beklenen: ${expected.label}). Tweak bu cihazda/yetkiyle '
            'etkisiz olabilir.');
        _showSnack('${tweak.title}: komut çalıştı ama cihazdaki durum değişmedi.');
      }
    } else {
      _log('  ℹ Durum cihazdan doğrulanamadı'
          '${read.error != null ? ' (${read.error})' : ' (kontrol komutu yok)'}; '
          'gösterilen değer tahmini.');
    }
  }

  /// [status]'u saklar; cihazdan gerçekten okunmuşsa `_appliedIds`'i de
  /// buna göre uzlaştırır (kalıcı yazma çağıranın işidir).
  void _storeStatus(Tweak tweak, TweakStatus status) {
    if (!mounted) return;
    setState(() {
      _statuses = {..._statuses, tweak.id: status};
      _statusUpdatedAt = DateTime.now();
      if (status.isLive && tweak.isToggle) {
        if (status.state == TweakState.on) {
          _appliedIds.add(tweak.id);
        } else {
          _appliedIds.remove(tweak.id);
        }
      }
    });
  }

  /// Tek bir tweak'in durumunu yeniden okuyup saklar ve döner.
  Future<TweakStatus> _refreshOne(Tweak tweak, _AccessPath path) async {
    final read = await _readStatus(tweak, path);
    _storeStatus(tweak, read.status);
    return read.status;
  }

  /// Uygulamanın kendi kaydından (`_appliedIds`) türetilen YEDEK durum:
  /// cihazdan okunamayan bir tweak, kayıtlıysa "AÇIK (tahmini)", değilse
  /// "BİLİNMİYOR" görünür. Kayıtlı olmaması KAPALI demek değildir (tweak
  /// uygulama dışında açılmış olabilir), bu yüzden KAPALI tahmin edilmez.
  TweakStatus _fallbackStatus(Tweak tweak) {
    if (tweak.isToggle && _appliedIds.contains(tweak.id)) {
      return const TweakStatus(TweakState.on, estimated: true);
    }
    return const TweakStatus(TweakState.unknown);
  }

  /// Tek bir tweak'in durumunu aktif yetki yolundan `checkCommand` ile
  /// okur. Kontrol komutu yoksa, yetki yoksa ya da komut başarısızsa
  /// [_fallbackStatus]'a düşer; okunamama nedeni `error` ile döner.
  Future<({TweakStatus status, String? error})> _readStatus(
    Tweak tweak,
    _AccessPath path,
  ) async {
    String? error;
    if (tweak.hasCheck && path != _AccessPath.none) {
      try {
        final result = path == _AccessPath.root
            ? await _service.exec(tweak.checkCommand!)
            : await _shizukuService.exec(tweak.checkCommand!);
        final state = stateFromCheckResult(
          expected: tweak.expectedOutput!,
          result: result,
        );
        if (state != TweakState.unknown) {
          return (status: TweakStatus(state), error: null);
        }
        error = failureReason(result) ?? 'çıktı yorumlanamadı';
      } catch (e) {
        error = '$e';
      }
    }
    return (status: _fallbackStatus(tweak), error: error);
  }

  /// Tüm tweak'lerin durumunu cihazdan (yetki yolu varsa) yeniden okur ve
  /// `_appliedIds`'i okunan gerçek durumla uzlaştırır. Okunamayanlar
  /// tahmini/bilinmiyor olarak işaretlenir ve bu konsola yazılır.
  Future<void> _refreshStatuses() async {
    if (_statusRefreshing || !mounted) return;
    setState(() => _statusRefreshing = true);
    final path = _path;
    final next = <String, TweakStatus>{};
    var failed = 0;
    String? firstError;
    for (final tweak in List<Tweak>.of(_tweaks)) {
      final read = await _readStatus(tweak, path);
      if (!mounted) return;
      next[tweak.id] = read.status;
      if (read.error != null) {
        failed++;
        firstError ??= '${tweak.title}: ${read.error}';
      }
    }
    var appliedChanged = false;
    for (final tweak in _tweaks) {
      final status = next[tweak.id];
      if (status == null || !status.isLive || !tweak.isToggle) continue;
      final changed = status.state == TweakState.on
          ? _appliedIds.add(tweak.id)
          : _appliedIds.remove(tweak.id);
      appliedChanged = appliedChanged || changed;
    }
    setState(() {
      _statuses = next;
      _statusUpdatedAt = DateTime.now();
      _statusRefreshing = false;
    });
    if (appliedChanged) await _settingsService.setAppliedTweakIds(_appliedIds);
    if (failed > 0) {
      _log('⚠ $failed tweak durumu cihazdan okunamadı (tahmini/bilinmiyor '
          'gösteriliyor). İlk hata - $firstError');
    }
  }

  String get _pathLabel {
    switch (_path) {
      case _AccessPath.root:
        return 'Root (uid ${_rootIdentity?.uid ?? 0})';
      case _AccessPath.shizuku:
        final id = _shizukuIdentity!;
        return 'Shizuku (${id.label}, uid ${id.uid})';
      case _AccessPath.none:
        return 'Yok';
    }
  }

  Future<bool?> _confirmDangerous(Tweak tweak) {
    return showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(color: AppColors.border),
        ),
        title: Text(tweak.title, style: TextStyle(color: AppColors.text)),
        content: Text(
          '${tweak.description}\n\nBu işlem bazı sistem bileşenlerini '
          'etkileyebilir. Devam etmek istiyor musunuz?',
          style: TextStyle(color: AppColors.muted, fontSize: 13),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text('Vazgeç', style: TextStyle(color: AppColors.muted)),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text('Uygula', style: TextStyle(color: AppColors.warn)),
          ),
        ],
      ),
    );
  }

  void _showSnack(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message), backgroundColor: AppColors.card));
  }

  Future<void> _loadRemoteTweaks() async {
    final url = _remoteUrlController.text.trim();
    if (url.isEmpty) return;
    setState(() => _remoteLoading = true);
    final remote = await _service.fetchRemoteTweaks(url);
    if (!mounted) return;
    setState(() => _remoteLoading = false);
    if (remote == null) {
      _showSnack('Uzak yapılandırma yüklenemedi.');
      return;
    }
    setState(() => _tweaks = remote);
    _showSnack('${remote.length} tweak yüklendi.');
    await _refreshStatuses();
  }

  Future<void> _openApp(RecommendedApp app) async {
    final ok = await _service.openAppLink(app);
    if (!ok) _showSnack('${app.name} bağlantısı açılamadı.');
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 40),
        child: Center(child: CircularProgressIndicator(color: AppColors.accentCyan)),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _PrivilegeStatusBanner(
          hasRoot: _hasRoot ?? false,
          rootIdentity: _rootIdentity,
          shizukuRunning: _shizukuRunning,
          shizukuPermitted: _shizukuPermitted,
          shizukuIdentity: _shizukuIdentity,
          shizukuError: _shizukuError,
          shizukuBusy: _shizukuBusy,
          onRequestShizukuPermission: _requestShizukuPermission,
        ),
        const SizedBox(height: 14),
        SegmentedTabBar(
          tabs: _segments,
          activeId: _segment,
          onChanged: (id) {
            setState(() => _segment = id);
            // Tweak sekmesi açılınca durumları cihazdan tekrar oku.
            if (id == 'tweaks') _refreshStatuses();
          },
        ),
        const SizedBox(height: 14),
        if (_segment == 'tweaks') _buildTweaksSegment(),
        if (_segment == 'apps') _buildAppsSegment(),
        if (_segment == 'shortcuts') _buildShortcutsSegment(),
        if (_consoleLines.isNotEmpty) ...[
          const SizedBox(height: 18),
          _ConsolePanel(lines: _consoleLines),
        ],
      ],
    );
  }

  Widget _buildTweaksSegment() {
    final byCategory = <TweakCategory, List<Tweak>>{};
    for (final tweak in _tweaks) {
      byCategory.putIfAbsent(tweak.category, () => []).add(tweak);
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _StatusPanel(
          tweaks: _tweaks,
          statuses: _statuses,
          pathLabel: _pathLabel,
          pathReady: _privileged,
          refreshing: _statusRefreshing,
          updatedAt: _statusUpdatedAt,
          onRefresh: _refreshEverything,
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› UZAKTAN YAPILANDIRMA (OPSİYONEL)',
          children: [
            Text(
              'Varsayılan olarak uygulama ile birlikte gelen yerel tweak '
              'listesi kullanılır. İsterseniz aynı şemaya sahip bir JSON '
              'adresinden güncel bir liste çekebilirsiniz.',
              style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.4),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _remoteUrlController,
                    style: TextStyle(color: AppColors.text, fontSize: 13),
                    decoration: InputDecoration(
                      hintText: 'https://.../tweaks.json',
                      hintStyle: TextStyle(color: AppColors.muted),
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                      filled: true,
                      fillColor: AppColors.background,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide(color: AppColors.border),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide(color: AppColors.border),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide(color: AppColors.accentCyan),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                _SmallButton(
                  label: _remoteLoading ? '...' : 'Yükle',
                  onTap: _remoteLoading ? null : _loadRemoteTweaks,
                ),
              ],
            ),
          ],
        ),
        for (final category in TweakCategory.values)
          if (byCategory[category]?.isNotEmpty ?? false) ...[
            const SizedBox(height: 18),
            TerminalCard(
              title: '› ${category.label.toUpperCase()}',
              children: [
                for (final tweak in byCategory[category]!) ...[
                  _TweakTile(
                    tweak: tweak,
                    status: _statuses[tweak.id] ?? _fallbackStatus(tweak),
                    busy: _busyIds.contains(tweak.id),
                    rootAvailable: _privileged,
                    onToggle: (value) => _toggleTweak(tweak, value),
                    onRun: () => _toggleTweak(tweak, true),
                  ),
                  if (tweak != byCategory[category]!.last)
                    Divider(color: AppColors.border, height: 18),
                ],
              ],
            ),
          ],
      ],
    );
  }

  Widget _buildAppsSegment() {
    final byCategory = <String, List<RecommendedApp>>{};
    for (final app in _apps) {
      byCategory.putIfAbsent(app.category, () => []).add(app);
    }
    final categories = byCategory.keys.toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final key in categories) ...[
          if (key != categories.first) const SizedBox(height: 18),
          TerminalCard(
            title: '› ${key.toUpperCase()}',
            children: [
              for (final app in byCategory[key]!) ...[
                _AppLinkTile(app: app, onTap: () => _openApp(app)),
                if (app != byCategory[key]!.last) Divider(color: AppColors.border, height: 18),
              ],
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildShortcutsSegment() {
    return TerminalCard(
      title: '› HIZLI AYAR KISAYOLLARI',
      children: [
        Text(
          'Bu kısayollar kök gerektirmez; doğrudan ilgili sistem ayarları '
          'ekranını açar.',
          style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.4),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            for (final feature in _features)
              _ShortcutChip(feature: feature, onTap: () => _service.openFeatureSettings(feature)),
          ],
        ),
      ],
    );
  }
}

/// Üç olası yetki durumunu gösterir: kök (root), Shizuku (ADB, kök
/// gerektirmez) veya hiçbiri. Kök zaten varsa Shizuku durumu hiç
/// gösterilmez - kök her zaman önceliklidir (bkz. `_toggleTweak`).
/// Shizuku "hazır" sayılmak için `id` test komutunun da geçmesi gerekir;
/// hangi kimlikle (shell/root) çalıştığı burada gösterilir.
class _PrivilegeStatusBanner extends StatelessWidget {
  const _PrivilegeStatusBanner({
    required this.hasRoot,
    required this.rootIdentity,
    required this.shizukuRunning,
    required this.shizukuPermitted,
    required this.shizukuIdentity,
    required this.shizukuError,
    required this.shizukuBusy,
    required this.onRequestShizukuPermission,
  });

  final bool hasRoot;
  final ShellIdentity? rootIdentity;
  final bool shizukuRunning;
  final bool shizukuPermitted;
  final ShellIdentity? shizukuIdentity;
  final String? shizukuError;
  final bool shizukuBusy;
  final VoidCallback onRequestShizukuPermission;

  @override
  Widget build(BuildContext context) {
    final shizukuReady = shizukuRunning && shizukuPermitted && shizukuIdentity != null;
    final ready = hasRoot || shizukuReady;
    final color = ready ? AppColors.accent : AppColors.warn;

    Widget retryButton(String label) => TextButton(
          onPressed: shizukuBusy ? null : onRequestShizukuPermission,
          style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(0, 32)),
          child: Text(
            shizukuBusy ? '...' : label,
            style: TextStyle(color: AppColors.warn, fontSize: 12),
          ),
        );

    String message;
    Widget? action;
    if (hasRoot) {
      message = "Kök erişimi bulundu (root, uid ${rootIdentity?.uid ?? 0}) - "
          "tweak'ler doğrudan uygulanabilir.";
    } else if (shizukuReady) {
      final id = shizukuIdentity!;
      message = id.isRoot
          ? "Kök yok, ama Shizuku hazır - komutlar root (uid 0) kimliğiyle çalışıyor."
          : "Kök yok, ama Shizuku hazır - komutlar '${id.name}' (uid ${id.uid}) "
              "kimliğiyle çalışıyor. Kök isteyen bazı tweak'ler bu yetkiyle "
              "başarısız olabilir; sonuç konsolda gösterilir.";
    } else if (shizukuRunning && shizukuPermitted) {
      message = "Shizuku çalışıyor ve izinli, ama test komutu (id) başarısız: "
          "${shizukuError ?? 'bilinmeyen hata'}. Tweak'ler çalıştırılamaz.";
      action = retryButton('Tekrar dene');
    } else if (shizukuRunning) {
      message = "Shizuku çalışıyor ama izin verilmedi.";
      action = retryButton('İzin iste');
    } else {
      message = "Kök erişimi ve Shizuku bulunamadı - tweak'ler bu cihazda çalıştırılamaz. "
          "Kısayollar ve uygulama önerileri ikisi olmadan da çalışır. Shizuku "
          "kurup Kablosuz Hata Ayıklama ile başlattıktan sonra tekrar deneyin.";
      action = retryButton('Tekrar dene');
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: color.withOpacity(0.08),
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Icon(ready ? Icons.verified_user : Icons.lock_outline, color: color, size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(message, style: TextStyle(color: color, fontSize: 12, height: 1.3)),
          ),
          if (action != null) ...[const SizedBox(width: 8), action],
        ],
      ),
    );
  }
}

/// Tweak sekmesinin üstündeki "Durum Paneli": toplam/aktif tweak sayısı,
/// kategoriye göre dağılım, aktif yetki yolu ve durumların cihazdan ne
/// kadarının gerçekten okunabildiği. "Yenile" yetkiyi ve tüm durumları
/// yeniden okur.
class _StatusPanel extends StatelessWidget {
  const _StatusPanel({
    required this.tweaks,
    required this.statuses,
    required this.pathLabel,
    required this.pathReady,
    required this.refreshing,
    required this.updatedAt,
    required this.onRefresh,
  });

  final List<Tweak> tweaks;
  final Map<String, TweakStatus> statuses;
  final String pathLabel;
  final bool pathReady;
  final bool refreshing;
  final DateTime? updatedAt;
  final VoidCallback onRefresh;

  TweakStatus _statusOf(Tweak tweak) =>
      statuses[tweak.id] ?? const TweakStatus(TweakState.unknown);

  bool _isActive(Tweak tweak) => _statusOf(tweak).state == TweakState.on;

  String _categoryValue(TweakCategory category) {
    final inCategory = tweaks.where((t) => t.category == category).toList();
    final active = inCategory.where(_isActive).length;
    return '$active / ${inCategory.length} aktif';
  }

  String _formatTime(DateTime t) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(t.hour)}:${two(t.minute)}:${two(t.second)}';
  }

  @override
  Widget build(BuildContext context) {
    final active = tweaks.where(_isActive).length;
    final estimatedActive =
        tweaks.where((t) => _isActive(t) && _statusOf(t).estimated).length;
    final unknown =
        tweaks.where((t) => _statusOf(t).state == TweakState.unknown).length;
    final checkable = tweaks.where((t) => t.hasCheck).length;
    final live = tweaks.where((t) => _statusOf(t).isLive).length;
    final activeSuffix = estimatedActive > 0 ? ' ($estimatedActive tahmini)' : '';

    return TerminalCard(
      title: '› DURUM PANELİ',
      children: [
        InfoRow(
          label: 'Yetki yolu',
          value: pathLabel,
          valueColor: pathReady ? AppColors.accent : AppColors.warn,
        ),
        InfoRow(label: 'Toplam tweak', value: '${tweaks.length}'),
        InfoRow(label: 'Aktif', value: '$active / ${tweaks.length}$activeSuffix'),
        InfoRow(label: 'Durumu bilinmeyen', value: '$unknown'),
        Divider(color: AppColors.border, height: 18),
        for (final category in TweakCategory.values)
          InfoRow(label: category.label, value: _categoryValue(category)),
        Divider(color: AppColors.border, height: 18),
        InfoRow(label: 'Cihazdan okunan', value: '$live / $checkable'),
        InfoRow(
          label: 'Son okuma',
          value: updatedAt == null ? '-' : _formatTime(updatedAt!),
        ),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerRight,
          child: _SmallButton(
            label: refreshing ? '...' : 'Yenile',
            onTap: refreshing ? null : onRefresh,
          ),
        ),
      ],
    );
  }
}

/// AÇIK / KAPALI / BİLİNMİYOR rozeti. Durum cihazdan okunamayıp
/// `_appliedIds`'den tahmin edildiyse "(tahmini)" eklenir.
class _StateBadge extends StatelessWidget {
  const _StateBadge({required this.status});

  final TweakStatus status;

  @override
  Widget build(BuildContext context) {
    final Color color;
    switch (status.state) {
      case TweakState.on:
        color = AppColors.accent;
      case TweakState.off:
        color = AppColors.danger;
      case TweakState.unknown:
        color = AppColors.warn;
    }
    final label = status.estimated ? '${status.state.label} (tahmini)' : status.state.label;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.bold),
      ),
    );
  }
}

class _TweakTile extends StatelessWidget {
  const _TweakTile({
    required this.tweak,
    required this.status,
    required this.busy,
    required this.rootAvailable,
    required this.onToggle,
    required this.onRun,
  });

  final Tweak tweak;
  final TweakStatus status;
  final bool busy;
  final bool rootAvailable;
  final ValueChanged<bool> onToggle;
  final VoidCallback onRun;

  bool get _isToggle => tweak.isToggle;

  @override
  Widget build(BuildContext context) {
    final locked = !rootAvailable;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            locked ? Icons.lock_outline : (tweak.dangerous ? Icons.warning_amber_rounded : Icons.tune),
            color: locked ? AppColors.muted : (tweak.dangerous ? AppColors.warn : AppColors.accentCyan),
            size: 18,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(tweak.title, style: TextStyle(color: AppColors.text, fontSize: 14)),
                    _StateBadge(status: status),
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  tweak.description,
                  style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.3),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 64,
            child: busy
                ? Center(
                    child: SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.accentCyan),
                    ),
                  )
                : _isToggle
                    ? Switch(
                        value: status.state == TweakState.on,
                        onChanged: locked ? null : onToggle,
                      )
                    : TextButton(
                        onPressed: locked ? null : onRun,
                        style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(56, 32)),
                        child: Text(
                          'Çalıştır',
                          style: TextStyle(color: locked ? AppColors.muted : AppColors.accentCyan, fontSize: 12),
                        ),
                      ),
          ),
        ],
      ),
    );
  }
}

class _AppLinkTile extends StatelessWidget {
  const _AppLinkTile({required this.app, required this.onTap});
  final RecommendedApp app;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(app.name, style: TextStyle(color: AppColors.text, fontSize: 14)),
                const SizedBox(height: 2),
                Text(app.description, style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.3)),
              ],
            ),
          ),
          const SizedBox(width: 8),
          _SmallButton(label: app.sourceLabel, onTap: onTap),
        ],
      ),
    );
  }
}

class _ShortcutChip extends StatelessWidget {
  const _ShortcutChip({required this.feature, required this.onTap});
  final FeatureShortcut feature;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: feature.description,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(10),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              border: Border.all(color: AppColors.border),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.settings_suggest, color: AppColors.accentCyan, size: 16),
                const SizedBox(width: 8),
                Text(feature.title, style: TextStyle(color: AppColors.text, fontSize: 13)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SmallButton extends StatelessWidget {
  const _SmallButton({required this.label, required this.onTap});
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null;
    final color = enabled ? AppColors.accentCyan : AppColors.muted;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          height: 36,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            border: Border.all(color: color),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(label, style: TextStyle(color: color, fontSize: 12)),
        ),
      ),
    );
  }
}

/// Çalıştırılan tweak komutlarının çıktısını akıtan mini konsol paneli
/// (bkz. talep edilen "komut çıktılarını konsol ekranına akış olarak
/// aktarma" gereksinimi).
class _ConsolePanel extends StatelessWidget {
  const _ConsolePanel({required this.lines});
  final List<String> lines;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      height: 180,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.background,
        border: Border.all(color: AppColors.border),
        borderRadius: BorderRadius.circular(10),
      ),
      child: ListView.builder(
        reverse: true,
        itemCount: lines.length,
        itemBuilder: (context, index) {
          final line = lines[lines.length - 1 - index];
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: Text(
              line,
              style: TextStyle(color: AppColors.accent, fontSize: 11, fontFamily: 'monospace'),
            ),
          );
        },
      ),
    );
  }
}
