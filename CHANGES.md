# Flutter → Kotlin dönüşümü

- Tüm Dart kodu Kotlin'e çevrildi; Flutter/Dart bağımlılığı kalmadı (Gradle Kotlin DSL, sürüm kataloğu).
- `ChangeNotifier` → `Observable` (`StateFlow` sayacı); `ListenableBuilder` → `controller.observe()`.
- `showModalBottomSheet`/`showDialog`/`SnackBar` → `LocalSheets`, `LocalDialogs`, `LocalToaster`.
- Shizuku: `UserService` + AIDL (`IShellService`), `ShizukuProvider`.
- Dart testleri JUnit 4'e taşındı: **100 test** (ayrıştırıcılar, oyun modu, gözcü betiği, tanılama, pil tahmini, tweak durumu, `ControlController`), JVM'de geçti.
- Taşınmayan: `widget_test` (arayüz testi) — Compose karşılığı yazılmadı.

## Doğrulama durumu (dürüst not)
- `core/` ve `state/` katmanları ile testler bu ortamda derlendi ve çalıştırıldı.
- `ui/`, `data/`, `shizuku/` (Android SDK/Compose'a bağımlı) bu ortamda **derlenemedi** (SDK/Maven erişimi yok).
  İçe aktarma/sembol taraması yapıldı ve bulunan hatalar giderildi; ancak ilk gerçek derleme CI'da olacak ve küçük derleme hataları çıkabilir.

## Oyun modu düzeltmeleri
- Durum kartları/alt sayfalar `observe()` çağırmadığı için bayat kalıyordu (Optimize durum kartı, Oyun hazır ayar listesi vb.); düzeltildi.
- Minecraft'ta GameManager `--downscale` görüntüyü köşeye sıkıştırıyordu: Minecraft paketlerinde uygulanmaz, önerilen çözünürlük %100.
- Yeni: Minecraft Bedrock görüş mesafesi / hafif grafik (seçenek dosyası yedeklenir, oyun bitince geri yazılır). FPS'i artırır, ısıyı azaltır.
- Önerilen ayar üst seviye yongalarda (cihaz adı kullanılmadan) ısı/FPS dengesine göre ayarlanır; parlaklık kısılır, güç profili standart kalır.
