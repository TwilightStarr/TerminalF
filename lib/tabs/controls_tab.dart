import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../core/theme/app_colors.dart';
import '../utils/format_utils.dart';
import '../widgets/segmented_tab_bar.dart';
import '../widgets/terminal_card.dart';

/// Tek bir izin satırının görüntülenmesi için gereken veri.
///
/// `Controls` sekmesi artık izinleri sadece toplu bir metinle
/// **izlemiyor**; her izni ayrı ayrı **yönetebiliyor** (tek tek
/// isteme, kalıcı reddedilmişse doğrudan ayarlara yönlendirme).
class PermissionRow {
  const PermissionRow({required this.label, required this.status, required this.onRequest});

  final String label;
  final PermissionStatus status;
  final VoidCallback onRequest;
}

/// "Kontroller" sekmesi: ekranı açık tutma, titreşim yönetimi, izin
/// yönetimi, depolama (önbellek) yönetimi ve başlangıç sekmesi tercihi.
///
/// Godot roadmap'indeki "sadece izleme değil, yönetim" hedefi burada
/// somutlaşıyor: her kart bir *bilgiyi göstermekle* kalmıyor, kullanıcının
/// o bilgiyi değiştirebileceği bir eylem de sunuyor.
class ControlsTab extends StatelessWidget {
  const ControlsTab({
    super.key,
    required this.keepScreenOn,
    required this.onKeepScreenOnChanged,
    required this.brightness,
    required this.onBrightnessChanged,
    required this.onBrightnessCommitted,
    required this.onResetBrightness,
    required this.vibrationDurationMs,
    required this.onVibrationDurationChanged,
    required this.onVibrateTest,
    required this.permissionRows,
    required this.onRequestAllPermissions,
    required this.onOpenAppSettings,
    required this.cacheBytes,
    required this.onClearCache,
    required this.tabs,
    required this.defaultTabId,
    required this.onDefaultTabChanged,
  });

  final bool keepScreenOn;
  final ValueChanged<bool> onKeepScreenOnChanged;

  /// 0.0 - 1.0 arası uygulama-seviyesi ekran parlaklığı.
  final double brightness;

  /// Sürükleme sırasında her tikte çağrılır (anlık, kalıcı kaydetmez).
  final ValueChanged<double> onBrightnessChanged;

  /// Sürükleme bittiğinde çağrılır - tercihi kalıcı depolar.
  final ValueChanged<double> onBrightnessCommitted;

  final VoidCallback onResetBrightness;

  final int vibrationDurationMs;
  final ValueChanged<int> onVibrationDurationChanged;
  final VoidCallback onVibrateTest;

  final List<PermissionRow> permissionRows;
  final VoidCallback onRequestAllPermissions;
  final VoidCallback onOpenAppSettings;

  final int cacheBytes;

  /// Önbelleği temizler ve temizlenen bayt miktarını döndürür.
  final Future<int> Function() onClearCache;

  final List<TerminalTabDef> tabs;
  final String defaultTabId;
  final ValueChanged<String> onDefaultTabChanged;

  static const _vibrationOptions = [
    (label: 'Kısa', ms: 100),
    (label: 'Orta', ms: 300),
    (label: 'Uzun', ms: 600),
  ];

  Future<void> _confirmClearCache(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: AppColors.border),
        ),
        title: const Text('Önbelleği Temizle?', style: TextStyle(color: AppColors.text)),
        content: Text(
          'Geçici önbellek verisi (${formatBytes(cacheBytes)}) silinecek. '
          'Kalıcı uygulama verileriniz ve tercihleriniz etkilenmez.',
          style: const TextStyle(color: AppColors.muted, fontSize: 13),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Vazgeç', style: TextStyle(color: AppColors.muted)),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Temizle', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    final freed = await onClearCache();
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text('${formatBytes(freed)} temizlendi.'),
          backgroundColor: AppColors.card,
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    // num.clamp() returns num, not double - Slider.value needs an exact
    // double, so the conversion happens once here and is reused below.
    final clampedBrightness = brightness.clamp(0.05, 1.0).toDouble();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TerminalCard(
          title: '› GENEL',
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text('Ekranı Açık Tut', style: TextStyle(color: AppColors.muted, fontSize: 14)),
                ),
                Switch(value: keepScreenOn, onChanged: onKeepScreenOnChanged),
              ],
            ),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› EKRAN PARLAKLIĞI',
          delay: const Duration(milliseconds: 40),
          children: [
            Row(
              children: [
                const Icon(Icons.brightness_low, color: AppColors.muted, size: 18),
                Expanded(
                  child: SliderTheme(
                    data: SliderThemeData(
                      activeTrackColor: AppColors.accentCyan,
                      inactiveTrackColor: AppColors.border,
                      thumbColor: AppColors.accentCyan,
                      overlayColor: AppColors.accentCyan.withOpacity(0.16),
                    ),
                    child: Slider(
                      value: clampedBrightness,
                      min: 0.05,
                      max: 1.0,
                      onChanged: onBrightnessChanged,
                      onChangeEnd: onBrightnessCommitted,
                    ),
                  ),
                ),
                const Icon(Icons.brightness_high, color: AppColors.muted, size: 18),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: Text(
                    '%${(clampedBrightness * 100).round()}',
                    style: const TextStyle(color: AppColors.text, fontSize: 13),
                  ),
                ),
                TextButton(
                  style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(0, 32)),
                  onPressed: onResetBrightness,
                  child: const Text(
                    'Sistem Değerine Sıfırla',
                    style: TextStyle(color: AppColors.accentCyan, fontSize: 12),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            const Text(
              'Yalnızca Terminal ön plandayken etkilidir; uygulama arka '
              'plana alınınca ekran sistemin genel parlaklığına döner.',
              style: TextStyle(color: AppColors.muted, fontSize: 11, height: 1.3),
            ),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› TİTREŞİM YÖNETİMİ',
          delay: const Duration(milliseconds: 80),
          children: [
            const Text('Süre', style: TextStyle(color: AppColors.muted, fontSize: 13)),
            const SizedBox(height: 8),
            Row(
              children: [
                for (final option in _vibrationOptions) ...[
                  Expanded(
                    child: _ChoiceChip(
                      label: '${option.label} (${option.ms}ms)',
                      selected: vibrationDurationMs == option.ms,
                      onTap: () => onVibrationDurationChanged(option.ms),
                    ),
                  ),
                  if (option != _vibrationOptions.last) const SizedBox(width: 8),
                ],
              ],
            ),
            const SizedBox(height: 12),
            _ControlButton(label: 'Titreşimi Test Et', accent: AppColors.accent, onTap: onVibrateTest),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› İZİN YÖNETİMİ',
          delay: const Duration(milliseconds: 120),
          children: [
            for (final row in permissionRows) ...[
              _PermissionTile(row: row),
              if (row != permissionRows.last) const Divider(color: AppColors.border, height: 16),
            ],
            const SizedBox(height: 12),
            _ControlButton(
              label: 'Tümünü İste',
              accent: AppColors.accentCyan,
              onTap: onRequestAllPermissions,
            ),
            const SizedBox(height: 10),
            _ControlButton(
              label: 'Uygulama Ayarlarını Aç',
              accent: AppColors.muted,
              onTap: onOpenAppSettings,
            ),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› DEPOLAMA YÖNETİMİ',
          delay: const Duration(milliseconds: 160),
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text('Önbellek Boyutu', style: TextStyle(color: AppColors.muted, fontSize: 14)),
                ),
                Text(formatBytes(cacheBytes), style: const TextStyle(color: AppColors.text, fontSize: 14)),
              ],
            ),
            const SizedBox(height: 12),
            _ControlButton(
              label: 'Önbelleği Temizle',
              accent: AppColors.danger,
              onTap: () => _confirmClearCache(context),
            ),
          ],
        ),
        const SizedBox(height: 18),
        TerminalCard(
          title: '› BAŞLANGIÇ SEKMESİ',
          delay: const Duration(milliseconds: 200),
          children: [
            const Text(
              'Uygulama açılışında hangi sekmenin gösterileceğini seçin.',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
            const SizedBox(height: 10),
            SegmentedTabBar(tabs: tabs, activeId: defaultTabId, onChanged: onDefaultTabChanged),
          ],
        ),
      ],
    );
  }
}

class _PermissionTile extends StatelessWidget {
  const _PermissionTile({required this.row});

  final PermissionRow row;

  String get _statusLabel {
    switch (row.status) {
      case PermissionStatus.granted:
        return 'Verildi';
      case PermissionStatus.permanentlyDenied:
        return 'Kalıcı Reddedildi';
      case PermissionStatus.restricted:
        return 'Kısıtlı';
      case PermissionStatus.limited:
        return 'Sınırlı';
      case PermissionStatus.provisional:
        return 'Geçici Verildi';
      case PermissionStatus.denied:
        return 'Reddedildi';
    }
  }

  Color get _statusColor {
    switch (row.status) {
      case PermissionStatus.granted:
      case PermissionStatus.provisional:
        return AppColors.accent;
      case PermissionStatus.permanentlyDenied:
        return AppColors.danger;
      case PermissionStatus.restricted:
      case PermissionStatus.limited:
        return AppColors.warn;
      case PermissionStatus.denied:
        return AppColors.muted;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isGranted = row.status == PermissionStatus.granted;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(
            flex: 3,
            child: Text(row.label, style: const TextStyle(color: AppColors.text, fontSize: 14)),
          ),
          Expanded(
            flex: 2,
            child: Text(
              _statusLabel,
              style: TextStyle(color: _statusColor, fontSize: 12),
            ),
          ),
          SizedBox(
            width: 64,
            child: isGranted
                ? const Icon(Icons.check_circle, color: AppColors.accent, size: 20)
                : TextButton(
                    style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(56, 32)),
                    onPressed: row.onRequest,
                    child: const Text('İste', style: TextStyle(color: AppColors.accentCyan, fontSize: 12)),
                  ),
          ),
        ],
      ),
    );
  }
}

class _ChoiceChip extends StatelessWidget {
  const _ChoiceChip({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final accent = AppColors.accentCyan;
    return Material(
      color: selected ? accent.withOpacity(0.16) : Colors.transparent,
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          height: 36,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: selected ? accent : AppColors.border),
          ),
          child: Text(
            label,
            style: TextStyle(fontSize: 12, color: selected ? accent : AppColors.muted),
          ),
        ),
      ),
    );
  }
}

class _ControlButton extends StatelessWidget {
  const _ControlButton({required this.label, required this.accent, required this.onTap});

  final String label;
  final Color accent;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          height: 44,
          width: double.infinity,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            border: Border.all(color: accent),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(label, style: TextStyle(color: accent, fontSize: 14)),
        ),
      ),
    );
  }
}
