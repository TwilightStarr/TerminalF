import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';
import '../models/tweak_model.dart';
import '../services/winutil_service.dart';
import '../widgets/segmented_tab_bar.dart';
import '../widgets/terminal_card.dart';

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

  static const _segments = [
    TerminalTabDef('tweaks', "Tweak'ler"),
    TerminalTabDef('apps', 'Uygulamalar'),
    TerminalTabDef('shortcuts', 'Kısayollar'),
  ];
  String _segment = 'tweaks';

  bool _loading = true;
  bool? _hasRoot;
  List<Tweak> _tweaks = const [];
  List<RecommendedApp> _apps = const [];
  List<FeatureShortcut> _features = const [];

  /// Kullanıcının bu oturumda "uyguladım" dediği tweak id'leri - gerçek
  /// sistem durumunun canlı bir sorgusu DEĞİL, yalnızca arayüzün hangi
  /// switch'i açık göstereceğine dair uygulamanın kendi hafızası. Kalıcı
  /// değildir: her tweak için ayrı ayrı gerçek durumu sorgulamak (`settings
  /// get`, `pm list packages -d`) bu ilk sürümün kapsamı dışında bırakıldı.
  final Set<String> _appliedIds = {};
  final Set<String> _busyIds = {};

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
    ]);
    if (!mounted) return;
    setState(() {
      _tweaks = results[0] as List<Tweak>;
      _apps = results[1] as List<RecommendedApp>;
      _features = results[2] as List<FeatureShortcut>;
      _hasRoot = results[3] as bool;
      _loading = false;
    });
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
    if (_hasRoot != true) {
      _log('✗ ${tweak.title}: kök erişimi yok, atlandı.');
      _showSnack('Bu tweak kök (root) erişimi gerektiriyor.');
      return;
    }
    if (apply && tweak.dangerous) {
      final confirmed = await _confirmDangerous(tweak);
      if (confirmed != true) return;
    }
    setState(() => _busyIds.add(tweak.id));
    _log('${apply ? '›' : '‹'} ${tweak.title} ${apply ? 'uygulanıyor' : 'geri alınıyor'}...');
    try {
      await for (final line in _service.runTweak(tweak, apply: apply)) {
        _log('  $line');
      }
      if (mounted) {
        setState(() {
          if (apply) {
            _appliedIds.add(tweak.id);
          } else {
            _appliedIds.remove(tweak.id);
          }
        });
      }
    } on WinUtilRootRequiredException {
      _log('✗ ${tweak.title}: kök erişimi yok.');
      _showSnack('Bu tweak kök (root) erişimi gerektiriyor.');
    } catch (e) {
      _log('✗ ${tweak.title}: hata - $e');
    } finally {
      if (mounted) setState(() => _busyIds.remove(tweak.id));
    }
  }

  Future<bool?> _confirmDangerous(Tweak tweak) {
    return showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: AppColors.border),
        ),
        title: Text(tweak.title, style: const TextStyle(color: AppColors.text)),
        content: Text(
          '${tweak.description}\n\nBu işlem bazı sistem bileşenlerini '
          'etkileyebilir. Devam etmek istiyor musunuz?',
          style: const TextStyle(color: AppColors.muted, fontSize: 13),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Vazgeç', style: TextStyle(color: AppColors.muted)),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Uygula', style: TextStyle(color: AppColors.warn)),
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
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 40),
        child: Center(child: CircularProgressIndicator(color: AppColors.accentCyan)),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _RootStatusBanner(hasRoot: _hasRoot ?? false),
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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TerminalCard(
          title: '› UZAKTAN YAPILANDIRMA (OPSİYONEL)',
          children: [
            const Text(
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
                    style: const TextStyle(color: AppColors.text, fontSize: 13),
                    decoration: InputDecoration(
                      hintText: 'https://.../tweaks.json',
                      hintStyle: const TextStyle(color: AppColors.muted),
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                      filled: true,
                      fillColor: AppColors.background,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: const BorderSide(color: AppColors.border),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: const BorderSide(color: AppColors.border),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: const BorderSide(color: AppColors.accentCyan),
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
                    rootAvailable: _hasRoot ?? false,
                    onToggle: (value) => _toggleTweak(tweak, value),
                    onRun: () => _toggleTweak(tweak, true),
                  ),
                  if (tweak != byCategory[category]!.last)
                    const Divider(color: AppColors.border, height: 18),
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
                if (app != byCategory[key]!.last) const Divider(color: AppColors.border, height: 18),
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
        const Text(
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

class _RootStatusBanner extends StatelessWidget {
  const _RootStatusBanner({required this.hasRoot});
  final bool hasRoot;

  @override
  Widget build(BuildContext context) {
    final color = hasRoot ? AppColors.accent : AppColors.warn;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: color.withOpacity(0.08),
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Icon(hasRoot ? Icons.verified_user : Icons.lock_outline, color: color, size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              hasRoot
                  ? "Kök erişimi bulundu - tweak'ler doğrudan uygulanabilir."
                  : "Kök erişimi bulunamadı - tweak'ler bu cihazda çalıştırılamaz. "
                      "Kısayollar ve uygulama önerileri kök gerektirmeden çalışır.",
              style: TextStyle(color: color, fontSize: 12, height: 1.3),
            ),
          ),
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

  bool get _isToggle => tweak.revertCommand != null && tweak.revertCommand!.isNotEmpty;

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
                Text(tweak.title, style: const TextStyle(color: AppColors.text, fontSize: 14)),
                const SizedBox(height: 2),
                Text(
                  tweak.description,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12, height: 1.3),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 64,
            child: busy
                ? const Center(
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
                Text(app.name, style: const TextStyle(color: AppColors.text, fontSize: 14)),
                const SizedBox(height: 2),
                Text(app.description, style: const TextStyle(color: AppColors.muted, fontSize: 12, height: 1.3)),
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
                const Icon(Icons.settings_suggest, color: AppColors.accentCyan, size: 16),
                const SizedBox(width: 8),
                Text(feature.title, style: const TextStyle(color: AppColors.text, fontSize: 13)),
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
              style: const TextStyle(color: AppColors.accent, fontSize: 11, fontFamily: 'monospace'),
            ),
          );
        },
      ),
    );
  }
}
