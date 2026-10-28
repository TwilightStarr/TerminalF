# New logo, English language support (current)

- **New logo:** a near-black rounded tile with an "F" of three bars fading from white to gray. Used everywhere:
  in-app logo (`AppLogo`, drawn from vector paths), `icon.svg`, launcher icons (adaptive + legacy + monochrome),
  notification icon and README. The default logo color is **Silver** (the original logo); the other colors tint
  the same light-to-dark ramp (`logoRamp` in `Palettes.kt`, mirrored by `tools/logo_common.py`).
- **Appearance page** updated for the new logo: swatches show the real light-to-dark ramp of each color,
  the logo tile is flat in every theme, and the launcher icon follows the chosen color (13 variants).
- **English language support:** the app opens in English; Türkçe is available from Settings or the welcome sheet
  and the choice is saved. Texts use `tr("English", "Türkçe")`; JSON assets gained `titleEn` / `descriptionEn`.
  Changing language rebuilds the UI in place and keeps the current tab and sub-page.
- Tests: **169 tests** (new: `I18nTest`, logo ramp / tile / default-logo tests; existing suites run in Turkish via
  `TestLanguage`).
- Verification note: core/state layers and tests were compiled and run on the JVM; the Compose/Android layers were
  only syntax-checked without the Android SDK, so the first real build happens in CI.

# Görünüm yenilemesi

- **Renkler:** Varsayılan tema "AMOLED Siyah" oldu (zemin gerçek siyah). Ana ekrandaki her kutu ve düğme artık kendi
  rengini taşır (mavi / mor / yeşil / turuncu / camgöbeği / pembe / sarı); doluluk ve ısı uyarıları yeşil-sarı-kırmızı.
  Alt gezinme çubuğunda her sekmenin kendi rengi var. Düğme yazısı rengi zemine göre otomatik seçilir.
- **Görünüm sayfası sadeleşti:** Tema (Koyu/Açık + AMOLED anahtarı) · Renkler (vurgu rengi + "Renkli arayüz") ·
  Logo (renk) · Genel (animasyon) · tek dokunuşla sıfırlama. Özel renk seçici yalnızca "Özel renk"e dokununca açılır.
- **Yeni ayarlar:** AMOLED siyah, vurgu rengi (hazır + özel), renkli arayüz açık/kapalı.
- **Yeni logo:** gradyanlı komut istemi oku + hap imleç + kıvılcım. Varsayılan simge "Nova" (mavi → mor). Kaynaklar:
  `icon.svg`, `tools/gen_logo_assets.py` (PNG'ler), `tools/gen_launcher_icons.py` (renk başına vektör simgeler).
- Yeni birim testleri: `AppearanceTest` (renk matematiği, AMOLED, vurgu çözümleme).
- Doğrulama notu: bu değişiklikler Android SDK/Kotlin derleyicisi olmadan yapıldı; ilk gerçek derleme CI'da olacak.

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
