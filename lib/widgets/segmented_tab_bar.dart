import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';

/// Bir sekmenin kimligi ve gorunen etiketi.
class TerminalTabDef {
  const TerminalTabDef(this.id, this.label);

  final String id;
  final String label;
}

/// Godot'taki ozel segmented-control'un (ButtonGroup + toggle butonlar,
/// bkz. `_style_tab_button()`) Flutter karsiligi. `TabBar` yerine ozel
/// bir widget kullanilmasinin sebebi, projenin kendi buton/stil diliyle
/// (kenarlikli, dolgulu, koseleri yuvarlak) birebir tutarli kalmasi.
class SegmentedTabBar extends StatelessWidget {
  const SegmentedTabBar({
    super.key,
    required this.tabs,
    required this.activeId,
    required this.onChanged,
  });

  final List<TerminalTabDef> tabs;
  final String activeId;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final tab in tabs) ...[
          Expanded(
            child: _TabButton(
              label: tab.label,
              active: tab.id == activeId,
              onTap: () => onChanged(tab.id),
            ),
          ),
          if (tab.id != tabs.last.id) const SizedBox(width: 6),
        ],
      ],
    );
  }
}

class _TabButton extends StatelessWidget {
  const _TabButton({required this.label, required this.active, required this.onTap});

  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const accent = AppColors.accentCyan;
    return Material(
      color: active ? accent.withOpacity(0.16) : Colors.transparent,
      borderRadius: BorderRadius.circular(10),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          height: 40,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: active ? accent : Colors.transparent),
          ),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 13,
              color: active ? accent : AppColors.muted,
              fontWeight: active ? FontWeight.w600 : FontWeight.normal,
            ),
          ),
        ),
      ),
    );
  }
}
