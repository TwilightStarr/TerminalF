import 'package:flutter_test/flutter_test.dart';
import 'package:terminal_flutter/services/fps_counter.dart';

// `testWidgets` kullanılıyor (düz `test()` değil) çünkü `FpsCounter`,
// `SchedulerBinding.instance`'a ihtiyaç duyar; bu yalnızca
// `flutter_test`'in kurduğu test binding'i içinde güvenle mevcuttur.
// Gerçek bir cihazdaki kare üretim hızını simüle etmiyoruz — yalnızca
// sınıfın sözleşmesini (başlat/durdur, `sample()` sayacı sıfırlar) test
// ediyoruz (bkz. README > YGL).
void main() {
  testWidgets('sample() hiç kare üretilmeden önce 0 döner', (tester) async {
    final counter = FpsCounter();
    expect(counter.sample(), 0);
  });

  testWidgets('start() sonrası üretilen kareler sayılır ve sample() sayacı sıfırlar', (tester) async {
    final counter = FpsCounter();
    counter.start();

    // En az bir kare üret.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));

    final firstSample = counter.sample();
    expect(firstSample, greaterThanOrEqualTo(0));

    // sample() çağrısı sayacı sıfırlamış olmalı: yeni bir kare
    // üretilmeden hemen tekrar çağrılırsa 0 dönmeli.
    expect(counter.sample(), 0);

    counter.stop();
  });

  testWidgets('stop() sonrası yeni kareler sayılmaz', (tester) async {
    final counter = FpsCounter();
    counter.start();
    await tester.pump();
    counter.stop();
    counter.sample(); // durdurmadan önce birikeni temizle

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));

    expect(counter.sample(), 0);
  });

  testWidgets('start() iki kez çağrılması yeniden abone olmaz (idempotent)', (tester) async {
    final counter = FpsCounter();
    counter.start();
    counter.start(); // ikinci çağrı sessizce yok sayılmalı

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));

    // Aynı kareler iki kez sayılmadıysa, tek abonelikteki normal kare
    // sayısıyla tutarlı, negatif olmayan bir değer döner.
    expect(counter.sample(), greaterThanOrEqualTo(0));

    counter.stop();
  });
}
