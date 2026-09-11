import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/theme/app_colors.dart';

/// Godot'taki `_add_row()` fonksiyonunun karsiligi: solda etiket
/// (muted), sagda deger. `valueColor` verilirse deger o renkte
/// gosterilir (ornegin pil kritik seviyedeyken kirmizi).
///
/// `copyable: true` verilirse satir sadece veriyi **izlemekle**
/// kalmaz; dokunulunca degeri panoya kopyalayarak veriyi baska bir
/// yerde kullanilabilir/yonetilebilir hale getirir (ornegin yerel IP
/// adresini bir SSH istemcisine yapistirmak icin).
class InfoRow extends StatelessWidget {
  const InfoRow({
    super.key,
    required this.label,
    required this.value,
    this.valueColor,
    this.copyable = false,
  });

  final String label;
  final String value;
  final Color? valueColor;
  final bool copyable;

  Future<void> _copyToClipboard(BuildContext context) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text('Kopyalandı: $value'),
          duration: const Duration(seconds: 2),
          backgroundColor: AppColors.card,
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    final row = Row(
      children: [
        Expanded(
          child: Text(
            label,
            style: const TextStyle(color: AppColors.muted, fontSize: 14),
          ),
        ),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.right,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(color: valueColor ?? AppColors.text, fontSize: 14),
          ),
        ),
        if (copyable) ...[
          const SizedBox(width: 6),
          const Icon(Icons.copy, size: 14, color: AppColors.muted),
        ],
      ],
    );

    if (!copyable) {
      return Padding(padding: const EdgeInsets.symmetric(vertical: 4), child: row);
    }

    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(6),
        onTap: () => _copyToClipboard(context),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: row,
        ),
      ),
    );
  }
}
