import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';

/// Godot'taki `_add_card()` ile olusturulan `PanelContainer` +
/// `StyleBoxFlat` kart gorunumunun Flutter karsiligi.
///
/// `delay`, bir sekme icindeki kartlarin sirayla (staggered) belirmesini
/// saglar - Godot'taki `_animate_section_cards()` fonksiyonunun
/// karsiligi. Widget her yeniden olusturuldugunda (ornegin sekme
/// degistiginde) animasyon bastan oynar; ayni sekme icinde veri
/// guncellemesi (setState) animasyonu tekrar tetiklemez, cunku widget
/// agactaki konumunu korur.
class TerminalCard extends StatefulWidget {
  const TerminalCard({
    super.key,
    required this.title,
    required this.children,
    this.delay = Duration.zero,
  });

  final String title;
  final List<Widget> children;
  final Duration delay;

  @override
  State<TerminalCard> createState() => _TerminalCardState();
}

class _TerminalCardState extends State<TerminalCard> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _opacity;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 300));
    _opacity = CurvedAnimation(parent: _controller, curve: Curves.easeInOutSine);
    Future.delayed(widget.delay, () {
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
    return FadeTransition(
      opacity: _opacity,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: AppColors.card,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              widget.title,
              style: TextStyle(
                color: AppColors.accentCyan,
                fontSize: 15,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.5,
              ),
            ),
            Divider(color: AppColors.border, height: 18),
            ...widget.children,
          ],
        ),
      ),
    );
  }
}
