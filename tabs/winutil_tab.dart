import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';
import '../models/tweak_model.dart';
import '../services/settings_service.dart';
import '../services/shizuku_service.dart';
import '../services/winutil_service.dart';
import '../widgets/segmented_tab_bar.dart';
import '../widgets/terminal_card.dart';

/// [tweak] bir açma/kapama (toggle) mı, yoksa tek seferlik bir eylem mi -
/// [Tweak.revertCommand] doluysa toggle'dır (bkz. `Tweak.revertCommand`
/// alan yorumu). `_TweakTile._isToggle` ile `_WinUtilSectionState`'in
/// "uygulanmış" durumunu neyin takip edeceği kararı AYNI mantığı
/// kullanmalı - bu yüzden ikisi de bu tek fonksiyona yönlendirilir (bkz.
/// `_toggleTweak` içindeki düzeltme notu).
bool _tweakIsToggle(Tweak tweak) =>
    tweak.revertCommand != null && tweak.revertCommand!.isNotEmpty;

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
  bool _shizukuRunning = false;
  bool _shizukuPermitted = false;
  bool _shizukuBusy = false;

  /// Shizuku servisi çalışıyor VE izin verilmiş mi.
  bool get _shizukuReady => _shizukuRunning && _shizukuPermitted;

  /// Kök (root) VEYA Shizuku üzerinden komut çalıştırılabilir mi -
  /// [_TweakTile]'ın kilit durumunu belirler.
  bool get _privileged => (_hasRoot ?? false) || _shizukuReady;

  List<Tweak> _tweaks = const [];
  List<RecommendedApp> _apps = const [];
  List<FeatureShortcut> _features = const [];

  /// Kullanıcının "uyguladım" dediği tweak id'leri - gerçek sistem
  /// durumunun canlı bir sorgusu DEĞİL, yalnızca arayüzün hangi switch'i
  /// açık göstereceğine dair uygulamanın kendi hafızası (her tweak için
  /// ayrı ayrı gerçek durumu sorgulamak - `settings get`, `pm list
  /// packages -d` - bu ilk sürümün kapsamı dışında bırakıldı). ANCAK artık
  /// `SettingsService` ile kalıcı: uygulama kapatılıp açıldığında ya da
  /// tuştan sonlandırıldığında switch'ler sıfırlanmıyor, en son bilinen
  /// durum geri yükleniyor (bkz. `_bootstrap` ve `_toggleTweak`).
  Set<String> _appliedIds = {};
  final Set<String> _busyIds = {};

  /// "Tümünü Durdur" (bkz. `_ActiveTweaksPanel`) çalışırken true - tekil
  /// satır düğmeleri zaten `_busyIds` ile kendi meşguliyetini gösteriyor,
  /// bu yalnızca toplu düğmenin kendisini geçici olarak devre dışı
  /// bırakmak için.
  bool _stopAllBusy = false;

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
      _shizukuService.isRunning(),
      _shizukuService.hasPermission(),
      _settingsService.getAppliedTweakIds(),
    ]);
    if (!mounted) return;
    setState(() {
      _tweaks = results[0] as List<Tweak>;
      _apps = results[1] as List<RecommendedApp>;
      _features = results[2] as List<FeatureShortcut>;
      _hasRoot = results[3] as bool;
      _shizukuRunning = results[4] as bool;
      _shizukuPermitted = results[5] as bool;
      // Bir önceki oturumdan kalan "uygulandı" durumunu geri yükle - bkz.
      // `_appliedIds` alanının yorumu. Bilinmeyen id'ler (ör. uzaktan
      // yapılandırma değiştiyse) sonraki adımda otomatik elenir çünkü
      // yalnızca `_tweaks` içinde eşleşen id'ler switch'te "açık" görünür.
      _appliedIds = results[6] as Set<String>;
      _loading = false;
    });
  }

  /// Kullanıcıya Shizuku izin diyaloğunu gösterir ve sonucu arayüze yansıtır.
  /// Kök zaten varsa çağrılmaz (bkz. `_PrivilegeStatusBanner`de düğmenin
  /// yalnızca kök yokken gösterilmesi).
  Future<void> _requestShizukuPermission() async {
    setState(() => _shizukuBusy = true);
    final running = await _shizukuService.isRunning();
    if (!running) {
      if (mounted) {
        setState(() {
          _shizukuRunning = false;
          _shizukuBusy = false;
        });
        _showSnack('Shizuku servisi çalışmıyor. Önce Shizuku uygulamasını başlatın.');
      }
      return;
    }
    final granted = await _shizukuService.requestPermission();
    if (!mounted) return;
    setState(() {
      _shizukuRunning = true;
      _shizukuPermitted = granted;
      _shizukuBusy = false;
    });
    _showSnack(granted ? 'Shizuku izni verildi.' : 'Shizuku izni reddedildi.');
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
    final useRoot = _hasRoot == true;
    final useShizuku = !useRoot && _shizukuReady;
    if (!useRoot && !useShizuku) {
      _log('✗ ${tweak.title}: kök erişimi ya da Shizuku izni yok, atlandı.');
      _showSnack('Bu tweak kök (root) ya da Shizuku erişimi gerektiriyor.');
      return;
    }
    if (apply && tweak.dangerous) {
      final confirmed = await _confirmDangerous(tweak);
      if (confirmed != true) return;
    }
    setState(() => _busyIds.add(tweak.id));
    final via = useRoot ? 'root' : 'Shizuku';
    _log('${apply ? '›' : '‹'} ${tweak.title} ($via) ${apply ? 'uygulanıyor' : 'geri alınıyor'}...');
    try {
      final stream = useRoot
          ? _service.runTweak(tweak, apply: apply)
          : _shizukuService.runTweak(tweak, apply: apply);
      await for (final line in stream) {
        _log('  $line');
      }
      // DÜZELTME: "uygulandı" durumu yalnızca AÇMA/KAPAMA (toggle)
      // tipindeki tweak'ler için anlamlıdır (bkz. `_tweakIsToggle`). Eskiden
      // burada koşulsuz `_appliedIds.add/remove` çağrılıyordu - bu da tek
      // seferlik eylemleri (ör. "Arka Plan Uygulamalarını Temizle",
      // `revertCommand: null`) her "Çalıştır" basışında kalıcı olarak
      // "açık" gibi işaretliyor, hem `_ActiveTweaksPanel`'i hem de eski
      // switch görünümünü yanlış bilgilendiriyordu, çünkü bu tür eylemlerin
      // "geri alınacak" bir açık durumu yoktur.
      if (mounted && _tweakIsToggle(tweak)) {
        setState(() {
          if (apply) {
            _appliedIds.add(tweak.id);
          } else {
            _appliedIds.remove(tweak.id);
          }
        });
        // Komut gerçekten başarıyla çalıştı VE (Shizuku yolunda) doğrulandı
        // (yukarıda hata fırlatılmadı) - yeni durumu kalıcı hale getir.
        // `setState` içindeki `_appliedIds` ile aynı anda değil, ondan
        // hemen sonra: `await` gerektirdiği için `setState`'in senkron
        // callback'i içine konamaz.
        await _settingsService.setAppliedTweakIds(_appliedIds);
      }
    } on WinUtilRootRequiredException {
      _log('✗ ${tweak.title}: kök erişimi yok.');
      _showSnack('Bu tweak kök (root) erişimi gerektiriyor.');
    } on ShizukuUnavailableException catch (e) {
      _log('✗ ${tweak.title}: $e');
      _showSnack('$e');
    } on ShizukuCommandFailedException catch (e) {
      // Komut Shizuku'ya gönderildi ama geri okuma doğrulaması tutmadı -
      // bkz. `ShizukuService.runTweak` yorumu. Bilerek `_appliedIds`'e
      // eklemiyoruz: switch/panel kullanıcıya "açık" göstermemeli, çünkü
      // cihazda gerçekten değişmedi.
      _log('✗ ${tweak.title}: $e');
      _showSnack('${tweak.title}: komut çalıştı ama etkisi doğrulanamadı - konsola bakın.');
    } catch (e) {
      _log('✗ ${tweak.title}: hata - $e');
    } finally {
      if (mounted) setState(() => _busyIds.remove(tweak.id));
    }
  }

  /// [_ActiveTweaksPanel]'deki "Tümünü Durdur" düğmesi: şu an "açık"
  /// görünen tüm toggle tipi tweak'leri, aynı `_toggleTweak(tweak, false)`
  /// yolundan (dolayısıyla aynı kök/Shizuku doğrulamasından) geçirerek
  /// TEK TEK, sırayla geri alır. Paralel değil sıralı çalıştırılır ki
  /// konsol çıktısı karışmasın ve her satırın kendi meşguliyet göstergesi
  /// (`_busyIds`) doğru görünsün.
  Future<void> _stopAllActive() async {
    final active = _tweaks.where((t) => _appliedIds.contains(t.id)).toList();
    if (active.isEmpty) return;
    setState(() => _stopAllBusy = true);
    for (final tweak in active) {
      if (!mounted) return;
      await _toggleTweak(tweak, false);
    }
    if (mounted) setState(() => _stopAllBusy = false);
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
          shizukuRunning: _shizukuRunning,
          shizukuPermitted: _shizukuPermitted,
          shizukuBusy: _shizukuBusy,
          onRequestShizukuPermission: _requestShizukuPermission,
        ),
        const SizedBox(height: 14),
        SegmentedTabBar(
          tabs: _segments,
          activeId: _segment,
          onChanged: (id) => setState(() => _segment = id),
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
    final activeTweaks = _tweaks.where((t) => _appliedIds.contains(t.id)).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _ActiveTweaksPanel(
          activeTweaks: activeTweaks,
          busyIds: _busyIds,
          stopAllBusy: _stopAllBusy,
          via: (_hasRoot ?? false) ? 'root' : 'Shizuku',
          onStop: (tweak) => _toggleTweak(tweak, false),
          onStopAll: _stopAllActive,
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
                    applied: _appliedIds.contains(tweak.id),
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
class _PrivilegeStatusBanner extends StatelessWidget {
  const _PrivilegeStatusBanner({
    required this.hasRoot,
    required this.shizukuRunning,
    required this.shizukuPermitted,
    required this.shizukuBusy,
    required this.onRequestShizukuPermission,
  });

  final bool hasRoot;
  final bool shizukuRunning;
  final bool shizukuPermitted;
  final bool shizukuBusy;
  final VoidCallback onRequestShizukuPermission;

  @override
  Widget build(BuildContext context) {
    final shizukuReady = shizukuRunning && shizukuPermitted;
    final ready = hasRoot || shizukuReady;
    final color = ready ? AppColors.accent : AppColors.warn;

    String message;
    Widget? action;
    if (hasRoot) {
      message = "Kök erişimi bulundu - tweak'ler doğrudan uygulanabilir.";
    } else if (shizukuReady) {
      message = "Kök yok, ama Shizuku hazır - tweak'ler ADB yetkisiyle çalıştırılabilir.";
    } else if (shizukuRunning) {
      message = "Shizuku çalışıyor ama izin verilmedi.";
      action = TextButton(
        onPressed: shizukuBusy ? null : onRequestShizukuPermission,
        style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(0, 32)),
        child: Text(
          shizukuBusy ? '...' : 'İzin iste',
          style: TextStyle(color: AppColors.warn, fontSize: 12),
        ),
      );
    } else {
      message = "Kök erişimi ve Shizuku bulunamadı - tweak'ler bu cihazda çalıştırılamaz. "
          "Kısayollar ve uygulama önerileri ikisi olmadan da çalışır. Shizuku "
          "kurup Kablosuz Hata Ayıklama ile başlattıktan sonra tekrar deneyin.";
      action = TextButton(
        onPressed: shizukuBusy ? null : onRequestShizukuPermission,
        style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(0, 32)),
        child: Text(
          shizukuBusy ? '...' : 'Tekrar dene',
          style: TextStyle(color: AppColors.warn, fontSize: 12),
        ),
      );
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

class _TweakTile extends StatelessWidget {
  const _TweakTile({
    required this.tweak,
    required this.applied,
    required this.busy,
    required this.rootAvailable,
    required this.onToggle,
    required this.onRun,
  });

  final Tweak tweak;
  final bool applied;
  final bool busy;
  final bool rootAvailable;
  final ValueChanged<bool> onToggle;
  final VoidCallback onRun;

  bool get _isToggle => _tweakIsToggle(tweak);

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
                Text(tweak.title, style: TextStyle(color: AppColors.text, fontSize: 14)),
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
                    ? Switch(value: applied, onChanged: locked ? null : onToggle)
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

/// Şu an "açık" (uygulanmış) durumdaki tüm tweak'leri tek bir yerde
/// listeleyen panel - bkz. talep: "aktif çalışanları görmem için bir
/// panel yeri ekle" ve "tüm tweakleri teker teker durdurabileceğim".
/// Kategori kartlarını tek tek gezip hangi switch'in açık olduğunu
/// aramak yerine, kullanıcı bunu Tweak'ler sekmesinin en üstünde tek
/// bakışta görür ve her satırdaki "Durdur" ile tek tek, ya da alttaki
/// "Tümünü Durdur" ile hepsini birden geri alabilir.
class _ActiveTweaksPanel extends StatelessWidget {
  const _ActiveTweaksPanel({
    required this.activeTweaks,
    required this.busyIds,
    required this.stopAllBusy,
    required this.via,
    required this.onStop,
    required this.onStopAll,
  });

  final List<Tweak> activeTweaks;
  final Set<String> busyIds;
  final bool stopAllBusy;

  /// Bu tweak'ler geri alınacak olursa hangi yoldan (root/Shizuku)
  /// çalıştırılacağı - yalnızca bilgilendirme amaçlı, satır başına
  /// gösterilir.
  final String via;
  final ValueChanged<Tweak> onStop;
  final VoidCallback onStopAll;

  @override
  Widget build(BuildContext context) {
    return TerminalCard(
      title: "› AKTİF TWEAK'LER (${activeTweaks.length})",
      children: [
        if (activeTweaks.isEmpty)
          Text(
            "Şu anda açık (uygulanmış) hiçbir tweak yok. Bir tweak'i "
            'açtığınızda burada listelenir; buradan tek tek ya da hepsini '
            'birden durdurabilirsiniz.',
            style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.4),
          )
        else ...[
          for (final tweak in activeTweaks) ...[
            _ActiveTweakRow(
              tweak: tweak,
              via: via,
              busy: busyIds.contains(tweak.id),
              onStop: () => onStop(tweak),
            ),
            if (tweak != activeTweaks.last) Divider(color: AppColors.border, height: 16),
          ],
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: _SmallButton(
              label: stopAllBusy ? '...' : 'Tümünü Durdur',
              onTap: stopAllBusy ? null : onStopAll,
            ),
          ),
        ],
      ],
    );
  }
}

class _ActiveTweakRow extends StatelessWidget {
  const _ActiveTweakRow({
    required this.tweak,
    required this.via,
    required this.busy,
    required this.onStop,
  });

  final Tweak tweak;
  final String via;
  final bool busy;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.radio_button_checked, color: AppColors.accent, size: 16),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(tweak.title, style: TextStyle(color: AppColors.text, fontSize: 13)),
                const SizedBox(height: 2),
                Text(
                  '${tweak.category.label} • $via ile çalışıyor',
                  style: TextStyle(color: AppColors.accent, fontSize: 11),
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
                : TextButton(
                    onPressed: onStop,
                    style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(56, 32)),
                    child: Text('Durdur', style: TextStyle(color: AppColors.danger, fontSize: 12)),
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
