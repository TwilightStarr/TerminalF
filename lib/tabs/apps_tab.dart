import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:installed_apps/app_info.dart';

import '../core/theme/app_colors.dart';
import '../services/apps_service.dart';
import '../widgets/terminal_card.dart';

/// "Uygulamalar" sekmesi: cihazdaki yüklü uygulamaları listeler ve
/// işletim sisteminin izin verdiği ölçüde **yönetir** (aç / durdurma
/// ekranını aç / kaldır). Godot çekirdeğinde karşılığı olmayan, bu
/// Flutter sürümüne özgü yeni bir sekmedir.
///
/// Diğer sekmelerin aksine kendi veri yükünü kendi yönetir (tek seferlik,
/// ağır bir liste okuması olduğu için ana ekranın 1 saniyelik döngüsüne
/// dahil edilmedi) — bu yüzden `HomeScreen`'den prop almadan
/// çalışabiliyor. Ayrıca kendi bağımsız, sınırlı yükseklikli kaydırma
/// alanını da kendi yönetir (bkz. `HomeScreen._buildScrollableBody()`)
/// ki uzun uygulama listelerinde `ListView.builder` ile gerçek
/// sanallaştırma sağlanabilsin — bkz. `_AppsListSection`.
///
/// ÖNEMLİ: Android, üçüncü parti uygulamaların diğer uygulamaların anlık
/// RAM kullanımını okumasına ya da onları tek dokunuşla (Windows Görev
/// Yöneticisi'ndeki gibi) doğrudan durdurmasına izin vermez — bkz.
/// [AppsService] belgesi ve README > "sınırlar". Bu yüzden burada uydurma
/// bir RAM değeri gösterilmiyor; bunun yerine gerçekten çalışan eylemler
/// (aç, sistemin durdurma ekranını aç, kaldır) sunuluyor.
///
/// `installed_apps` paketi iOS'u desteklemiyor (bkz. paket belgeleri) —
/// bu durumda kullanıcıya boş bir liste yerine dürüst bir platform
/// açıklaması gösteriyoruz (bkz. [_PlatformUnsupportedNotice] ve README
/// > YGL, "installed_apps iOS'ta desteklenmiyor" maddesi).
class AppsTab extends StatefulWidget {
  const AppsTab({super.key});

  @override
  State<AppsTab> createState() => _AppsTabState();
}

class _AppsTabState extends State<AppsTab> {
  final _service = AppsService();

  bool _loading = true;
  bool _includeSystemApps = false;
  String _query = '';
  List<AppInfo> _apps = const [];
  String? _busyPackage;

  /// `dart:io Platform`, README'de zaten kullanılan diğer servislerle
  /// (`device_service.dart` vb.) aynı desen — ek bir paket gerekmez.
  bool get _isIOS => Platform.isIOS;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final apps = await _service.list(includeSystemApps: _includeSystemApps);
    apps.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    if (!mounted) return;
    setState(() {
      _apps = apps;
      _loading = false;
    });
  }

  List<AppInfo> get _filtered {
    if (_query.trim().isEmpty) return _apps;
    final q = _query.trim().toLowerCase();
    return _apps
        .where((a) => a.name.toLowerCase().contains(q) || a.packageName.toLowerCase().contains(q))
        .toList();
  }

  /// Gerçek bir "durdurma" API'si olmadığı için, kullanıcıya bunu açıkça
  /// söyleyip sistemin gerçek "Zorla Durdur" düğmesinin bulunduğu ekrana
  /// yönlendiriyoruz — sessizce farklı bir şey yapmış gibi davranmıyoruz.
  Future<void> _confirmStop(AppInfo app) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: AppColors.border),
        ),
        title: const Text('Uygulamayı Durdur', style: TextStyle(color: AppColors.text)),
        content: Text(
          'Android, güvenlik nedeniyle bir uygulamanın başka bir uygulamayı '
          'doğrudan durdurmasına izin vermiyor. "${app.name}" için sistemin '
          '"Uygulama Bilgisi" ekranı açılacak — gerçek "Zorla Durdur" düğmesi '
          'orada.',
          style: const TextStyle(color: AppColors.muted, fontSize: 13),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Vazgeç', style: TextStyle(color: AppColors.muted)),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Ayarları Aç', style: TextStyle(color: AppColors.accentCyan)),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await _service.openSystemSettings(app.packageName);
    }
  }

  Future<void> _confirmUninstall(AppInfo app) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: AppColors.border),
        ),
        title: const Text('Uygulamayı Kaldır?', style: TextStyle(color: AppColors.text)),
        content: Text(
          '"${app.name}" cihazdan kaldırılacak. Bu işlem geri alınamaz.',
          style: const TextStyle(color: AppColors.muted, fontSize: 13),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Vazgeç', style: TextStyle(color: AppColors.muted)),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Kaldır', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    setState(() => _busyPackage = app.packageName);
    final ok = await _service.uninstall(app.packageName);
    if (!mounted) return;
    setState(() => _busyPackage = null);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(ok ? '${app.name} kaldırıldı.' : 'Kaldırma işlemi tamamlanmadı.'),
          backgroundColor: AppColors.card,
        ),
      );
    if (ok) await _load();
  }

  @override
  Widget build(BuildContext context) {
    final apps = _filtered;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TerminalCard(
          title: '› UYGULAMA YÖNETİMİ',
          children: [
            const Text(
              'Android güvenlik modeli, bir uygulamanın diğerlerinin anlık RAM '
              'kullanımını okumasına ya da onları tek tuşla durdurmasına izin '
              'vermez (bunu yalnızca sistem uygulamaları/kök erişimi yapabilir). '
              'Bu yüzden burada uydurma bir RAM değeri gösterilmiyor: "Durdur" '
              'sistemin gerçek "Zorla Durdur" ekranını açar, "Kaldır" ise '
              'uygulamayı doğrudan siler.',
              style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.4),
            ),
            const SizedBox(height: 14),
            TextField(
              onChanged: (v) => setState(() => _query = v),
              style: const TextStyle(color: AppColors.text, fontSize: 14),
              decoration: InputDecoration(
                hintText: 'Uygulama ara...',
                hintStyle: const TextStyle(color: AppColors.muted),
                isDense: true,
                prefixIcon: const Icon(Icons.search, color: AppColors.muted, size: 18),
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
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
            const SizedBox(height: 10),
            Row(
              children: [
                const Expanded(
                  child: Text('Sistem Uygulamalarını Göster', style: TextStyle(color: AppColors.muted, fontSize: 13)),
                ),
                Switch(
                  value: _includeSystemApps,
                  onChanged: (v) {
                    setState(() => _includeSystemApps = v);
                    _load();
                  },
                ),
              ],
            ),
            const SizedBox(height: 2),
            Text(
              _loading ? 'Yükleniyor...' : 'Toplam: ${apps.length} uygulama',
              style: const TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ],
        ),
        const SizedBox(height: 18),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator(color: AppColors.accentCyan))
              : _isIOS
                  // iOS'ta `installed_apps` desteklenmediği için liste her
                  // zaman boş döner (bkz. AppsService belgesi) — bunu
                  // kullanıcıya "uygulama bulunamadı" gibi yanıltıcı bir
                  // mesajla değil, gerçek nedeniyle gösteriyoruz.
                  ? const _PlatformUnsupportedNotice()
                  : apps.isEmpty
                      ? const Center(
                          child: Text('Uygulama bulunamadı.', style: TextStyle(color: AppColors.muted)),
                        )
                      : _AppsListSection(
                          apps: apps,
                          busyPackage: _busyPackage,
                          onLaunch: (app) => _service.launch(app.packageName),
                          onStop: _confirmStop,
                          onUninstall: _confirmUninstall,
                        ),
        ),
      ],
    );
  }
}

/// `installed_apps`'in desteklemediği platformlarda (şu an için yalnızca
/// iOS) gösterilen açıklama — bkz. [AppsTab] sınıf yorumu ve README >
/// "sınırlar".
class _PlatformUnsupportedNotice extends StatelessWidget {
  const _PlatformUnsupportedNotice();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.info_outline, color: AppColors.muted, size: 28),
            SizedBox(height: 10),
            Text(
              'iOS henüz desteklenmiyor',
              style: TextStyle(color: AppColors.text, fontSize: 14, fontWeight: FontWeight.w600),
              textAlign: TextAlign.center,
            ),
            SizedBox(height: 6),
            Text(
              'iOS, üçüncü parti uygulamaların yüklü uygulama listesini '
              'okumasına izin vermiyor; bu yüzden "Uygulamalar" sekmesi bu '
              'platformda henüz bir liste gösteremiyor. Bu, eksik bir '
              'okuma değil — Apple\'ın platform kısıtı (bkz. README).',
              style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.4),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

/// Yüklü uygulama listesinin kart görünümlü, **gerçekten sanallaştırılmış**
/// bölümü. `TerminalCard`'ın aksine sabit boyutlu değildir — ebeveyninden
/// (`AppsTab`'daki `Expanded`) sınırlı bir yükseklik alır ve içindeki
/// `ListView.builder` yalnızca o an ekranda görünen (+ önbellek payı
/// kadar) uygulama satırlarını oluşturur.
///
/// Önceki sürümde tüm liste, `TerminalCard` içinde tek bir `Column`'a
/// `for` döngüsüyle aynı anda çiziliyordu — çok sayıda uygulama yüklü
/// cihazlarda gereksiz yere pahalıydı (bkz. README > YGL, "çok sayıda
/// uygulama yüklü cihazlarda performans" maddesi). Bu widget o maddeyi
/// çözer; görsel stil (kenarlık, başlık, ayırıcı) `TerminalCard` ile
/// birebir aynı kalacak şekilde elle eşlendi.
class _AppsListSection extends StatefulWidget {
  const _AppsListSection({
    required this.apps,
    required this.busyPackage,
    required this.onLaunch,
    required this.onStop,
    required this.onUninstall,
  });

  final List<AppInfo> apps;
  final String? busyPackage;
  final ValueChanged<AppInfo> onLaunch;
  final Future<void> Function(AppInfo app) onStop;
  final Future<void> Function(AppInfo app) onUninstall;

  @override
  State<_AppsListSection> createState() => _AppsListSectionState();
}

class _AppsListSectionState extends State<_AppsListSection> with SingleTickerProviderStateMixin {
  // `TerminalCard`'daki staggered fade-in ile aynı görsel dil (bkz. o
  // widget'ın yorumu) - burada elle tekrarlanıyor çünkü `TerminalCard`
  // sabit boyutlu (`mainAxisSize.min`) bir Column bekliyor ve `Expanded`
  // bir `ListView` barındıramıyor.
  late final AnimationController _controller;
  late final Animation<double> _opacity;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 300));
    _opacity = CurvedAnimation(parent: _controller, curve: Curves.easeInOutSine);
    Future.delayed(const Duration(milliseconds: 40), () {
      if (mounted) _controller.forward();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final apps = widget.apps;
    return FadeTransition(
      opacity: _opacity,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(18, 18, 18, 6),
        decoration: BoxDecoration(
          color: AppColors.card,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '› YÜKLÜ UYGULAMALAR',
              style: TextStyle(
                color: AppColors.accentCyan,
                fontSize: 15,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.5,
              ),
            ),
            const Divider(color: AppColors.border, height: 18),
            Expanded(
              child: ListView.builder(
                // Gerçek sanallaştırma burada gerçekleşiyor: Flutter,
                // yalnızca görünür (+ ~250 piksellik varsayılan önbellek
                // payındaki) satırlar için `itemBuilder`'ı çağırır.
                itemCount: apps.length,
                itemBuilder: (context, index) {
                  final app = apps[index];
                  final isLast = index == apps.length - 1;
                  return Padding(
                    padding: EdgeInsets.only(bottom: isLast ? 12 : 0),
                    child: Column(
                      children: [
                        _AppTile(
                          app: app,
                          busy: widget.busyPackage == app.packageName,
                          onLaunch: () => widget.onLaunch(app),
                          onStop: () => widget.onStop(app),
                          onUninstall: () => widget.onUninstall(app),
                        ),
                        if (!isLast) const Divider(color: AppColors.border, height: 16),
                      ],
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AppTile extends StatelessWidget {
  const _AppTile({
    required this.app,
    required this.busy,
    required this.onLaunch,
    required this.onStop,
    required this.onUninstall,
  });

  final AppInfo app;
  final bool busy;
  final VoidCallback onLaunch;
  final VoidCallback onStop;
  final VoidCallback onUninstall;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          _AppIcon(bytes: app.icon),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  app.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.text, fontSize: 14),
                ),
                Text(
                  app.packageName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.muted, fontSize: 11),
                ),
              ],
            ),
          ),
          if (busy)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 8),
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.accentCyan),
              ),
            )
          else ...[
            _RowIconButton(icon: Icons.play_arrow_rounded, color: AppColors.accent, tooltip: 'Aç', onTap: onLaunch),
            _RowIconButton(icon: Icons.power_settings_new, color: AppColors.warn, tooltip: 'Durdur', onTap: onStop),
            _RowIconButton(icon: Icons.delete_outline, color: AppColors.danger, tooltip: 'Kaldır', onTap: onUninstall),
          ],
        ],
      ),
    );
  }
}

class _AppIcon extends StatelessWidget {
  const _AppIcon({required this.bytes});

  final Uint8List? bytes;

  @override
  Widget build(BuildContext context) {
    final iconBytes = bytes;
    if (iconBytes == null || iconBytes.isEmpty) {
      return Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: AppColors.background,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.border),
        ),
        child: const Icon(Icons.apps, color: AppColors.muted, size: 18),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: Image.memory(iconBytes, width: 36, height: 36, fit: BoxFit.cover),
    );
  }
}

class _RowIconButton extends StatelessWidget {
  const _RowIconButton({required this.icon, required this.color, required this.tooltip, required this.onTap});

  final IconData icon;
  final Color color;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: Material(
        color: Colors.transparent,
        shape: const CircleBorder(),
        child: InkWell(
          onTap: onTap,
          customBorder: const CircleBorder(),
          child: Padding(
            padding: const EdgeInsets.all(6),
            child: Icon(icon, color: color, size: 20),
          ),
        ),
      ),
    );
  }
}
