import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:terminal_flutter/main.dart';

void main() {
  testWidgets('Uygulama açılıyor ve TERMINAL başlığını gösteriyor', (tester) async {
    await tester.pumpWidget(const TerminalApp());
    // İlk kare: veri servisleri henüz async olarak dönmemiş olabilir,
    // yine de statik başlık anında görünür olmalı.
    await tester.pump();

    expect(find.text('TERMINAL'), findsOneWidget);
    expect(find.text('Genel Bakış'), findsOneWidget);

    // HomeScreen 1 saniyelik periyodik bir Timer başlatıyor; test
    // bitmeden önce ağacı kaldırıp State.dispose()'un tetiklenmesini
    // (ve Timer'ın iptal edilmesini) sağlıyoruz, aksi halde test
    // binding "still pending timer" hatası verir.
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Sekme değiştirince ilgili sekmenin içeriği görünür', (tester) async {
    await tester.pumpWidget(const TerminalApp());
    await tester.pump();

    // Başlangıçta "Genel Bakış" aktif; "Cihaz" sekmesine özel kart
    // henüz ağaçta olmamalı.
    expect(find.text('› CİHAZ'), findsNothing);

    // SegmentedTabBar'daki "Cihaz" butonuna dokun (bkz.
    // `HomeScreen._tabs` ve `SegmentedTabBar`).
    await tester.tap(find.text('Cihaz'));
    await tester.pump();

    expect(find.text('› CİHAZ'), findsOneWidget);
    // "Genel Bakış" sekmesine özgü bir kart artık ağaçta olmamalı —
    // sekmeler aynı anda değil, tek tek oluşturuluyor.
    expect(find.text('› PİL'), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
  });
}
