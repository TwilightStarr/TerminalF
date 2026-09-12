# Terminal — Cihaz ve Sistem Bilgi Paneli

> **Sürüm:** 0.1 beta

## Neler var? (temel sürüm)

- **Sekmeli ana ekran**: "Genel Bakış", "Cihaz", "Performans", "Kontroller" — Godot'taki özel segmented-control'ün Flutter karşılığı (`SegmentedTabBar`), `TabBar` yerine bilinçli olarak elle yazıldı ki proje kendi buton/stil diliyle tutarlı kalsın.
- **Pil**: durum, yüzde, görsel şarj çubuğu (`battery_plus`), deşarj/şarj hızından tahmin edilen kalan süre.
- **Cihaz**: model, işletim sistemi, işlemci mimarisi, çekirdek sayısı, dil (`device_info_plus` + `dart:io Platform`).
- **Ekran**: çözünürlük, yaklaşık DPI, yenileme hızı, ölçek — `dart:ui` Display API ile, ek paket gerekmedi.
- **Bellek / Performans**: gerçek RSS bellek kullanımı (`dart:io ProcessInfo`), FPS sayacı ve son 30 saniyeyi gösteren mini çizgi grafik (`CustomPainter`, harici grafik paketi yok).
- **Depolama**: uygulama veri klasörünün boyutu ve yolu (`path_provider` + `dart:io`).
- **Ağ**: yerel IP adresi (`network_info_plus`).
- **Kontroller**: ekranı açık tutma anahtarı (`wakelock_plus`, tercih `shared_preferences` ile kalıcı), **ekran parlaklığı yönetimi** (`screen_brightness`, uygulama-seviyesi, tercih kalıcı), titreşim testi (`vibration`), "Gerekli İzinleri İste" butonu (`permission_handler`).
- **Uygulamalar** *(yeni sekme, Godot çekirdeğinde karşılığı yok)*: cihazdaki yüklü uygulamaları listeler (`installed_apps`, arama + sistem uygulamalarını gösterme anahtarı ile), her biri için **Aç**, **Durdur** (sistemin gerçek "Zorla Durdur" ekranını açar) ve **Kaldır** (gerçek kaldırma) eylemleri sunar. Android'in üçüncü parti uygulamalara diğer uygulamaların anlık RAM kullanımını okumayı veya onları doğrudan durdurmayı yasakladığı ekranda açıkça belirtilir — bkz. "sınırlar" bölümü.
- **Üst çubuk**: manuel yenileme butonu, son güncelleme saati, her sekmede görünen kompakt pil rozeti.
- **AMOLED tema**: tek `ThemeData` kaynağı (`lib/core/theme/`), Godot paletiyle birebir aynı renk kodları.

Veriler saniyede bir otomatik güncellenir; bir sekmeye her geçişte o sekmenin kartları sırayla belirerek (fade-in) görünür — Godot'taki `_animate_section_cards()` davranışının birebir karşılığı.

## Proje yapısı

```
lib/
  main.dart                    # Giriş noktası, sistem UI stili, tema kurulumu
  core/theme/                  # Renk paleti + ThemeData (tek tema kaynağı)
  services/                    # Platform/veri erişimi (pil, cihaz, bellek, depolama, ağ,
                                # ekran parlaklığı, ayarlar, fps, uygulama listesi)
  widgets/                     # Yeniden kullanılabilir UI parçaları (kart, satır, progress bar, rozet, grafik, tab bar)
  tabs/                        # 5 sekmenin içerik widget'ları (çoğu stateless, veriyi props ile alır;
                                # `apps_tab.dart` kendi veri yükünü kendi yönetir, bkz. YGL)
  screens/home_screen.dart     # Tüm state + 1 saniyelik yenileme döngüsü burada toplanıyor
  utils/                       # format_bytes, formatDuration, estimateRemainingDuration
                                # gibi saf (platform bağımsız) yardımcı fonksiyonlar
assets/icon/icon.svg           # Godot projesindeki ikon, referans olarak taşındı
test/                          # widget_test.dart + saf fonksiyon/servis birim testleri
                                # (format_utils_test.dart, fps_counter_test.dart,
                                # battery_math_test.dart)
```

## Kurulum

Bu depo yalnızca Dart/Flutter kaynak kodunu içerir; platforma özel `android/`, `ios/`, `web/` klasörleri henüz üretilmedi (bilinçli olarak — "sadece temeli at" kapsamında). Projeyi çalıştırılabilir hale getirmek için:

```bash
# 1) Bağımlılıkları indir
flutter pub get

# 2) Platform klasörlerini üret (android/ ios/ vb.)
flutter create . --platforms=android,ios

# 3) Android izinlerini AndroidManifest.xml'e ekleyin (titreşim ve
#    ekranı açık tutma için gerekli olabilir; wakelock_plus ve
#    vibration paketleri genelde kendi manifest birleştirmelerini
#    otomatik yapar, ama derleme sonrası android/app/src/main/AndroidManifest.xml
#    dosyasını bir kez kontrol edin):
#    <uses-permission android:name="android.permission.VIBRATE" />
#
#    "Uygulamalar" sekmesinin yüklü uygulama listesini görebilmesi için
#    (Android 11 / API 30+), aşağıdaki izin de eklenmeli. Google Play,
#    bu izni yalnızca gerçek bir kullanım gerekçesiyle kabul eder (bir
#    "cihaz yönetimi/güvenlik" uygulaması için genelde geçerlidir, ama
#    yayınlamadan önce Play Console'daki ilgili formu doldurmanız gerekir):
#    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
#        tools:ignore="QueryAllPackagesPermission" />
#
#    Not: "Ekran Parlaklığı" kartı için manifest'e EK BİR İZİN GEREKMEZ -
#    `screen_brightness` paketinin yalnızca uygulama-seviyesi API'si
#    kullanılıyor (bkz. YGL ve "sınırlar").

# 4) Çalıştır
flutter run
```

## Yapılması Gerekenler Listesi (YGL)

### Önceki oturum odağı: "izlemekten" "yönetmeye" (1. aşama — Kontroller sekmesi)

Bu oturumda **tüm proje yeniden yazılmadı** — bilinçli olarak sadece "Kontroller"
sekmesini ve ona veri sağlayan birkaç dosyayı kapsayan, dar ve verimli bir
değişiklik seti uygulandı. Amaç: uygulama artık pil/bellek/depolama gibi
değerleri sadece **göstermiyor**, kullanıcının bu değerlerle ilgili gerçek bir
**eylem** almasını da sağlıyor. Dokunulan dosyalar:

- `lib/services/storage_service.dart` — `cacheBytes` alanı ve gerçek bir yönetim
  eylemi olan `clearCache()` eklendi (yalnızca gecici klasörü temizler, kalıcı
  veriye dokunmaz).
- `lib/services/settings_service.dart` — `defaultTab` ve `vibrationDurationMs`
  tercihleri için kalıcı depolama eklendi (mevcut `keepScreenOn` deseniyle birebir).
- `lib/widgets/info_row.dart` — opsiyonel `copyable` parametresi: satıra
  dokununca değeri panoya kopyalıyor (Yerel IP, Veri Yolu, Cihaz Modeli'nde kullanıldı).
- `lib/tabs/controls_tab.dart` — **tamamen yeniden düzenlendi**: Genel, Titreşim
  Yönetimi, İzin Yönetimi, Depolama Yönetimi, Başlangıç Sekmesi kartları.
- `lib/screens/home_screen.dart` — yukarıdaki kartları besleyen state ve
  callback'ler eklendi (izin durumları haritası, seçili titreşim süresi,
  önbellek temizleme + yenileme, başlangıç sekmesi tercihi).
- `lib/tabs/overview_tab.dart`, `lib/tabs/device_tab.dart` — yeni `cacheBytes`
  satırı ve ilgili yerlerde `copyable: true` kullanımı için küçük eklemeler.

`main.dart`, tema dosyaları, `fps_counter.dart`, `memory_service.dart`,
`device_service.dart`, `network_service.dart`, `battery_service.dart`,
widget'lardan `fps_graph.dart` / `battery_badge.dart` / `accent_progress_bar.dart`
/ `terminal_card.dart` ve testler bu oturumda **değiştirilmedi** — mevcut
mantıkları zaten doğruydu, gereksiz yere dokunulmadı.

> Not: Bu ortamda çalışır bir Flutter/Dart SDK'sı yok, dolayısıyla kod
> `flutter analyze` / `flutter run` ile doğrulanamadı. Kod elle, mevcut proje
> desenleri (servis/widget/sekme katmanları, hata yutma stiliyle `try/catch`,
> `SettingsService` deseni) birebir takip edilerek yazıldı. Native klasörler
> (`android/`, `ios/`) hâlâ üretilmedi; `flutter pub get` sonrası ilk derlemede
> özellikle `permission_handler` ve `vibration` paketlerinin API yüzeyini
> gerçek bir SDK ile bir kez doğrulamanız önerilir.

### Önceki oturum odağı: "izlemekten" "yönetmeye" (2. aşama — Uygulama Yönetimi)

Bu oturumda da **tüm proje yeniden yazılmadı** — istenen tek yeni yetenek olan
"cihazdaki uygulamaları RAM/durum bilgisiyle listeleme ve durdurabilme"
isteğine odaklanan, dar bir değişiklik seti uygulandı. Dokunulan/eklenen dosyalar:

- `pubspec.yaml` — `installed_apps` bağımlılığı eklendi.
- `lib/services/apps_service.dart` *(yeni)* — `installed_apps` paketini
  sarmalayan servis: uygulama listesi okuma, uygulama başlatma, sistemin
  "Uygulama Bilgisi" ekranını açma, uygulama kaldırma.
- `lib/tabs/apps_tab.dart` *(yeni)* — "Uygulamalar" sekmesi: arama kutusu,
  sistem uygulamalarını gösterme anahtarı, her uygulama için ikon/isim/paket
  adı ve Aç / Durdur / Kaldır eylemleri.
- `lib/screens/home_screen.dart` — `_tabs` listesine `apps` sekmesi eklendi
  (bu, aynı listeyi kullanan "Başlangıç Sekmesi" seçiciye de otomatik olarak
  yansır) ve `_buildActiveTab()`'a bir `case` eklendi. `AppsTab` kendi veri
  yükünü kendi yönettiği için (bkz. aşağıdaki not) başka hiçbir state veya
  callback eklenmedi.

**Önemli — RAM ve "durdurma" hakkında dürüst bir sınır:** Android, API 21
(Lollipop) itibarıyla üçüncü parti uygulamaların diğer uygulamaların **anlık
RAM kullanımını okumasına** ya da onları **programatik olarak doğrudan
durdurmasına** (force-stop) izin vermiyor; bu yetkiler yalnızca sistem
uygulamalarına/kök erişimine açık. Bu bilinen bir işletim sistemi kısıtı
olduğu için "Uygulamalar" sekmesinde uydurma bir RAM sayısı **gösterilmiyor**
— bunun yerine gerçekten çalışan üç eylem sunuluyor: **Aç** (uygulamayı
gerçekten başlatır), **Durdur** (sistemin gerçek "Zorla Durdur" düğmesinin
bulunduğu "Uygulama Bilgisi" ekranını açar) ve **Kaldır** (uygulamayı gerçekten
kaldırır). Kullanıcıya bu kısıt, sekmenin üst kartında açıkça yazılı.

> Not: Bu oturumda da çalışır bir Flutter/Dart SDK'sı yoktu, kod
> `flutter analyze` / `flutter run` ile doğrulanamadı; elle yazılırken mevcut
> proje desenleri (servis/widget/sekme katmanları, `try/catch` ile hata
> yutma, `TerminalCard`/`InfoRow` bileşenlerinin yeniden kullanımı) birebir
> takip edildi. `installed_apps` paketi yalnızca Android'i destekliyor (bkz.
> paket belgeleri) — iOS'ta metotlar sessizce boş/başarısız sonuç döner,
> bu servis katmanındaki `try/catch` bloklarıyla zaten yönetiliyor.

### Bu oturumda yapılan odak: "izlemekten" "yönetmeye" (3. aşama — Ekran Parlaklığı + Pil Tahmini + İzin Temizliği)

Bu oturumda da **tüm proje yeniden yazılmadı** — YGL'nin "Sıradaki adımlar"
listesindeki, native SDK/cihaz gerektirmeyen (dolayısıyla bu ortamda güvenle
yazılabilecek) maddelere odaklanan dar bir değişiklik seti uygulandı.
Dokunulan/eklenen dosyalar:

- `pubspec.yaml` — `screen_brightness` bağımlılığı eklendi.
- `lib/services/brightness_service.dart` *(yeni)* — `screen_brightness`
  paketini sarmalayan servis: uygulama-seviyesi parlaklık okuma, ayarlama,
  sıfırlama. Sistem-seviyesi (kalıcı, `WRITE_SETTINGS` gerektiren) parlaklık
  bilinçli olarak kapsam dışı bırakıldı — bkz. "sınırlar" bölümü.
- `lib/services/settings_service.dart` — `getBrightness()` /
  `setBrightness()` / `clearBrightness()` eklendi (mevcut `keepScreenOn`
  deseniyle birebir).
- `lib/tabs/controls_tab.dart` — yeni "EKRAN PARLAKLIĞI" kartı: slider +
  yüzde göstergesi + "Sistem Değerine Sıfırla" butonu. Diğer kartların
  staggered fade-in gecikmeleri buna göre kaydırıldı.
- `lib/screens/home_screen.dart` — parlaklık için state, `_bootstrap()`
  içinde kayıtlı tercihi uygulama/okuma, sürükleme sırasında anlık
  uygulama + sürükleme bitince kalıcı kaydetme ayrımı yapan callback'ler.
  Ayrıca `_managedPermissions` listesinden `Permission.storage` çıkarıldı
  (aşağıya bakın).
- `lib/services/battery_service.dart` — 15 dakikalık bir örnek geçmişi
  tutarak deşarj/şarj hızından kalan süre tahmini eklendi (`BatteryReading`
  artık nullable bir `estimatedRemaining` alanı taşıyor).
- `lib/utils/format_utils.dart` — `formatDuration()` eklendi.
- `lib/tabs/overview_tab.dart` — "Kalan Süre" satırı artık sabit
  "Hesaplanamıyor" metni yerine gerçek tahmini (ya da "Hesaplanıyor…" /
  "Doldu" durumlarını) gösteriyor.

**`Permission.storage` neden listeden çıkarıldı:** Android 13+ (API 33)
itibarıyla bu izin parçalanmış durumda (yerini ayrı medya izinleri aldı) ve
zaten Terminal'in kullandığı tek depolama erişimi `path_provider` ile
uygulamaya özel klasörler — bunlar hiçbir Android sürümünde runtime izni
gerektirmiyor. Eskiden listede olması, kullanıcıya hiçbir zaman anlamlı
şekilde "yönetilemeyen", sürekli "Reddedildi" görünen bir satır
gösteriyordu. Bu, YGL'deki "Permission.storage'ın Android 13+ davranışının
gözden geçirilmesi" maddesinin çözümüdür.

> Not: Bu oturumda da çalışır bir Flutter/Dart SDK'sı yoktu, kod
> `flutter analyze` / `flutter run` ile doğrulanamadı. `screen_brightness`
> paketinin `ScreenBrightness.instance.application` /
> `setApplicationScreenBrightness()` / `resetApplicationScreenBrightness()`
> API yüzeyi pub.dev belgelerine göre elle yazıldı — ilk derlemede bir kez
> doğrulanması önerilir (bkz. Kurulum notu). Pil tahmini saf Dart matematiği
> olduğu için platform API'sine bağımlı değil, ama gerçek bir cihazda
> (özellikle şarj/deşarj geçişlerinde) davranışının gözlemlenmesi faydalı
> olur.

### Bu oturumda yapılan odak: kod kalitesi ve sağlamlaştırma (4. aşama)

Önceki üç oturum "izlemekten yönetmeye" temasında yeni yönetim
eylemleri ekledi (depolama, uygulamalar, parlaklık, titreşim, izinler).
Bu oturumda **yeni bir yönetim özelliği eklenmedi** — bunun yerine
YGL'nin "Sıradaki adımlar" listesindeki, gerçek bir Flutter SDK'sı ya
da cihaz *gerektirmeyen* (dolayısıyla bu ortamda güvenle
tamamlanabilecek) üç maddeye dar ve verimli bir şekilde odaklanıldı.
Native klasör üretimi, manifest/Info.plist düzenlemesi ve gerçek
cihaz doğrulaması gibi SDK/cihaz gerektiren maddelere **dokunulmadı** —
bkz. aşağıdaki güncel "Sıradaki adımlar" listesi. Dokunulan/eklenen
dosyalar:

- `lib/screens/home_screen.dart` — ekranın gövdesi, başlık ve sekme
  çubuğu sabit kalacak, yalnızca aktif sekmenin içeriği kendi sınırlı
  alanında kayacak şekilde yeniden düzenlendi (`Expanded` +
  `_buildScrollableBody()`). Bu, "Uygulamalar" sekmesinin kendi
  `ListView.builder`'ına gerçek (sınırsız değil, ekranla sınırlı)
  bir yükseklik verebilmesi için gerekliydi.
- `lib/tabs/apps_tab.dart` — **gerçek liste sanallaştırması**: yüklü
  uygulamalar artık tek bir `Column` içinde `for` döngüsüyle anında
  değil, yeni `_AppsListSection` widget'ı içindeki `ListView.builder`
  ile yalnızca ekranda görünen satırlar oluşturularak çiziliyor (bkz.
  YGL'nin eski "çok sayıda uygulama yüklü cihazlarda performans"
  maddesi). Görsel stil (kenarlık, başlık, ayırıcı, staggered fade-in)
  `TerminalCard` ile birebir aynı kalacak şekilde elle eşlendi.
- `lib/tabs/apps_tab.dart` — **iOS'a özel platform bildirimi**:
  `installed_apps` iOS'ta desteklenmediği için (bkz. paket belgeleri),
  liste her zaman boş dönüyordu ve kullanıcıya yanıltıcı bir şekilde
  "Uygulama bulunamadı." gösteriliyordu. `Platform.isIOS` kontrolüyle
  artık bunun yerine dürüst bir "iOS henüz desteklenmiyor" açıklaması
  gösteriliyor (bkz. YGL'nin eski "installed_apps iOS'ta
  desteklenmiyor" maddesi).
- `lib/utils/battery_math.dart` *(yeni)* — `BatteryService` içindeki
  deşarj/şarj hızı tahmin matematiği, birim testlerinin gerçek pil
  donanımına/`Battery()` platform kanalına ihtiyaç duymadan
  çalışabilmesi için saf bir fonksiyona (`estimateRemainingDuration()`)
  çıkarıldı. Davranış değişmedi — yalnızca aynı hesaplama artık ayrı,
  test edilebilir bir yerde.
- `lib/services/battery_service.dart` — `_estimateRemaining()` artık
  yukarıdaki saf fonksiyona delege ediyor.
- `test/format_utils_test.dart`, `test/fps_counter_test.dart`,
  `test/battery_math_test.dart` *(yeni)* — sırasıyla `formatBytes` /
  `formatDuration`, `FpsCounter` sözleşmesi (başlat/durdur, `sample()`
  sayacı sıfırlar) ve `estimateRemainingDuration()` için birim
  testleri. Üçü de saf Dart/Flutter API'lerine dayanıyor, herhangi bir
  platform kanalı mock'u gerektirmiyor.
- `test/widget_test.dart` — mevcut açılış testine ek olarak, sekme
  değiştirmenin ilgili sekmenin içeriğini gösterdiğini doğrulayan
  ikinci bir `testWidgets` bloğu eklendi (var olan "aç → pump → kaldır"
  deseniyle birebir, periyodik `Timer` yüzünden `pumpAndSettle`
  kullanılmadı — bkz. dosyadaki yorum).

**Bilinçli olarak yapılmayanlar (dürüstlük):** YGL'nin aynı maddesi,
`StorageService.clearCache()` (path_provider) ve izin akışları
(permission_handler) için de birim testi istiyordu. Bu ikisi gerçek
platform kanalı çağrıları yapıyor; anlamlı bir testin bu paketlerin
sahte (mock/fake) platform implementasyonlarını kurmasını gerektiriyor
ve bu kurulum, çalışan bir SDK ile en az bir kez derlenip
doğrulanmadan bu ortamda güvenle yazılamaz (yanlış bir mock, hiç
mock olmamasından daha kötü, yanıltıcı bir "yeşil" test verir). Bu
yüzden bilinçli olarak ertelendi — bkz. aşağıdaki "Sıradaki adımlar".

> Not: Bu oturumda da çalışır bir Flutter/Dart SDK'sı yoktu, kod
> `flutter analyze` / `flutter test` ile doğrulanamadı. `ListView.builder`
> + `Expanded` yerleşimi (sabit yükseklik veren ebeveyn zinciri) elle,
> dikkatle kuruldu, ama gerçek bir düzen (layout) hatası yalnızca
> gerçek bir derlemede ortaya çıkar; ilk `flutter run`'da "Uygulamalar"
> sekmesinin kaydırmasının kısıtlar hatası (`RenderFlex` overflow vb.)
> vermeden çalıştığının bir kez gözlemlenmesi önerilir.

### Tamamlanan adımlar

- [Yapıldı] Proje iskeleti ve klasör yapısı oluşturuldu (`core/`, `services/`, `widgets/`, `tabs/`, `screens/`, `utils/`)
- [Yapıldı] AMOLED tema tek kaynaktan yönetiliyor: `AppColors` (Godot `COLOR_...` sabitleriyle birebir) + `AppTheme.dark`
- [Yapıldı] Sekmeli ana ekran: Genel Bakış / Cihaz / Performans / Kontroller, özel `SegmentedTabBar` ile
- [Yapıldı] Ortak `TerminalCard` (kart) ve `InfoRow` (satır) widget'ları — Godot'taki `_add_card()` / `_add_row()` karşılığı
- [Yapıldı] Kart giriş animasyonu (staggered fade-in), sekme değişince yeniden tetikleniyor; veri güncellemesi (her saniye) animasyonu tekrar oynatmıyor
- [Yapıldı] Pil servisi (`battery_plus`): durum + yüzde, renkli `AccentProgressBar`
- [Yapıldı] Üst çubukta her zaman görünen kompakt pil rozeti (`BatteryBadge`), `AppColors.accentForPercent()` ortak eşik fonksiyonunu pil çubuğuyla paylaşıyor
- [Yapıldı] Cihaz servisi (`device_info_plus`): model, işletim sistemi, işlemci mimarisi, çekirdek sayısı, dil
- [Yapıldı] Ekran servisi: çözünürlük, yaklaşık DPI, yenileme hızı, ölçek — `dart:ui` Display API (`View.of(context).display`) ile, ek paket gerekmedi
- [Yapıldı] Bellek servisi: `dart:io ProcessInfo.currentRss` / `maxRss` ile **gerçek** RSS bellek verisi + görsel bar
- [Yapıldı] FPS sayacı: `SchedulerBinding.addTimingsCallback` tabanlı, mevcut 1 saniyelik yenileme döngüsüne oturuyor (yeni zamanlayıcı gerekmedi — Godot'taki FPS grafiği yaklaşımıyla aynı prensip)
- [Yapıldı] FPS mini çizgi grafiği: `CustomPainter` ile, Godot'taki `FPSGraphView` iç sınıfının birebir karşılığı, son 30 örnek, eşiğe göre renk (kritik/uyarı/normal)
- [Yapıldı] Depolama servisi: uygulama veri klasörü boyutu (`path_provider` + `dart:io` recursive tarama) ve yol gösterimi
- [Yapıldı] Ağ servisi: yerel IP adresi (`network_info_plus`)
- [Yapıldı] Kontroller sekmesi: Ekranı Açık Tut anahtarı (`wakelock_plus`) + tercih `shared_preferences` ile kalıcı (Godot'taki `ConfigFile` karşılığı)
- [Yapıldı] Titreşim testi butonu (`vibration`, 300 ms)
- [Yapıldı] "Gerekli İzinleri İste" butonu `permission_handler` ile bağlandı, sonuçlar ekranda listeleniyor
- [Yapıldı] Manuel "Şimdi Yenile" butonu + "son güncelleme" saati üst çubukta
- [Yapıldı] `pubspec.yaml` bağımlılıkları ve `analysis_options.yaml` (flutter_lints) tanımlandı
- [Yapıldı] Başlangıç seviyesi widget testi (`test/widget_test.dart`)
- [Yapıldı] **Depolama yönetimi**: `StorageService.clearCache()` ile gerçek önbellek temizleme eylemi + onay diyaloğu + temizlenen bayt miktarını gösteren sonuç mesajı (`ControlsTab` › "DEPOLAMA YÖNETİMİ")
- [Yapıldı] **İzin yönetimi (detaylı)**: her izin ayrı satırda, kendi durumuyla (Verildi/Reddedildi/Kalıcı Reddedildi/…) listeleniyor; tek tek "İste" butonu, toplu "Tümünü İste" ve kalıcı reddedilmiş izinler için "Uygulama Ayarlarını Aç" (`openAppSettings()`) eklendi
- [Yapıldı] **Titreşim yönetimi**: sabit 300ms yerine Kısa/Orta/Uzun süre seçimi, `SettingsService` ile kalıcı
- [Yapıldı] **Başlangıç sekmesi tercihi**: Kontroller sekmesinden, mevcut `SegmentedTabBar` yeniden kullanılarak seçiliyor, `SettingsService` ile kalıcı ve seçildiği an uygulanıyor
- [Yapıldı] **Satır değerlerine dokunarak panoya kopyalama**: `InfoRow`'a `copyable` parametresi eklendi; Yerel IP, Veri Yolu ve Cihaz Modeli satırlarında aktif
- [Yapıldı] **Uygulama yönetimi (yeni "Uygulamalar" sekmesi)**: yüklü uygulamaları arama + sistem uygulamalarını gösterme anahtarıyla listeleme (`installed_apps`), her uygulama için gerçek **Aç**, **Durdur** (sistemin "Zorla Durdur" ekranını açar) ve **Kaldır** eylemleri; RAM/force-stop konusundaki Android kısıtı sekmede açıkça belirtiliyor (bkz. yukarıdaki YGL notu)
- [Yapıldı] **Ekran parlaklığı yönetimi** (`BrightnessService` + `screen_brightness`): Kontroller sekmesinde slider ile canlı ayarlama, "Sistem Değerine Sıfırla" eylemi, tercih `SettingsService` ile kalıcı; yalnızca uygulama-seviyesi API kullanıldığı için ek izin gerekmiyor
- [Yapıldı] **Pil kalan-süre tahmini**: sabit "Hesaplanamıyor" yerine, son 15 dakikalık örneklerden hesaplanan gerçek bir deşarj/şarj hızı tahmini (`BatteryService`); yeterli veri yoksa dürüstçe "Hesaplanıyor…" gösteriyor
- [Yapıldı] **`Permission.storage` temizliği**: Android 13+'ta parçalanmış ve zaten uygulamanın ihtiyaç duymadığı bu izin, yönetilen izinler listesinden çıkarıldı (bkz. yukarıdaki YGL notu)
- [Yapıldı] **"Uygulamalar" sekmesinde gerçek liste sanallaştırması**: `ListView.builder` tabanlı `_AppsListSection`, ana ekranın sabit başlık + sekme başına kaydırma alanı olacak şekilde yeniden yapılandırılmasıyla (bkz. `HomeScreen._buildScrollableBody()`) birlikte; artık yalnızca ekranda görünen uygulama satırları oluşturuluyor
- [Yapıldı] **iOS'a özel platform bildirimi**: `installed_apps` iOS'ta desteklenmediği için "Uygulamalar" sekmesi artık yanıltıcı "Uygulama bulunamadı." yerine dürüst bir platform açıklaması gösteriyor
- [Yapıldı] **Pil tahmini matematiğinin test edilebilir hale getirilmesi**: `estimateRemainingDuration()` `lib/utils/battery_math.dart`'a çıkarıldı, `BatteryService` ona delege ediyor
- [Yapıldı] **Birim/widget testlerinin genişletilmesi**: `formatBytes`, `formatDuration`, `FpsCounter` sözleşmesi, sekme geçişleri ve `estimateRemainingDuration()` için testler eklendi (`test/format_utils_test.dart`, `test/fps_counter_test.dart`, `test/battery_math_test.dart`, `test/widget_test.dart`) — `StorageService.clearCache()` ve izin akışları için testler bilinçli olarak ertelendi (bkz. yukarıdaki "Bilinçli olarak yapılmayanlar" notu)
- [Yapıldı] **Ekran sekmesi ölçüm düzeltmesi**: "Çözünürlük" artık mantıksal (dp) yerine gerçek fiziksel piksel değerini gösteriyor ve `MediaQuery` bağımlılığıyla döndürme/katlama gibi metrik değişikliklerinde otomatik güncelleniyor (`lib/tabs/device_tab.dart`)

### Sıradaki adımlar (öncelik sırasıyla, henüz yapılmadı)

- [ ] `flutter create . --platforms=android,ios` ile native klasörlerin üretilmesi ve gerçek cihazda ilk derleme/çalıştırma testi — bu oturumdaki tüm yeni kod da dahil, henüz gerçek bir SDK ile derlenmedi (yukarıdaki nota bakın)
- [ ] Android `AndroidManifest.xml` izinlerinin gözden geçirilmesi (VIBRATE, gerekiyorsa bildirim izni) ve iOS `Info.plist` tarafında ağ/izin açıklama metinlerinin (`NSLocalNetworkUsageDescription` vb.) eklenmesi
- [ ] `ProcessInfo.currentRss` / `maxRss` değerlerinin gerçek Android/iOS cihazda doğrulanması (bazı platform/derleme kombinasyonlarında 0 dönebilir; gerekirse platform kanalıyla native bellek API'sine geçiş)
- [ ] `StorageService.clearCache()` ve izin akışları (`permission_handler`) için birim testi: `path_provider` / `permission_handler` platform kanallarının sahte (mock/fake) implementasyonlarının kurulması gerekiyor; bu kurulum çalışan bir SDK ile bir kez doğrulanmadan bu ortamda güvenle yazılamaz — bkz. bu oturumun "Bilinçli olarak yapılmayanlar" notu (`FpsCounter`, `formatBytes`, `formatDuration`, sekme geçişleri ve pil tahmini için testler zaten eklendi)
- [ ] Android `AndroidManifest.xml`'e `QUERY_ALL_PACKAGES` izninin eklenmesi (yukarıdaki Kurulum notuna bakın) ve Play Console'da bu izin için gerekçe formunun doldurulması
- [ ] `screen_brightness` paketinin ilk gerçek derlemede API yüzeyinin doğrulanması (bkz. yukarıdaki YGL notu)
- [ ] "Uygulamalar" sekmesindeki yeni `ListView.builder` + `Expanded` yerleşiminin (bkz. bu oturumun notu) gerçek bir cihazda/derlemede bir kez çalıştırılıp düzen (layout) hatası vermediğinin doğrulanması

### Diğer geliştirme fikirleri (sırayla ele alınabilir)

- [ ] Sekmeler arasında yatay kaydırma (swipe) jesti ile geçiş desteği
- [ ] Sistem-seviyesi (kalıcı, uygulama kapansa da geçerli) ekran parlaklığı: `screen_brightness`'ın `system` API'si + Android `WRITE_SETTINGS` izin akışı — bilinçli olarak bu oturumun kapsamı dışında tutuldu, bkz. "sınırlar"
- [ ] Kartlara dokunulduğunda genişleyip daralan (expand/collapse) detay görünümü
- [ ] Ayarlar kartına tema rengi seçici (yeşil/camgöbeği dışında ek vurgu renkleri)
- [ ] Pil geçmişi mini grafiği — `FpsGraph`'ın genelleştirilip (renk/aralık parametreleri dışarıdan verilebilir hale getirilip) yeniden kullanılması
- [ ] Üst çubuktaki pil rozetine dokununca "Genel Bakış" sekmesine otomatik geçiş
- [ ] İlk açılışta kısa bir yükleniyor/iskelet (skeleton) durumu, statik veriler okunana kadar
- [ ] **"Uygulamalar" sekmesinde çoklu seçim + toplu kaldırma**: her satıra bir onay kutusu eklenip birden fazla uygulamanın tek bir onay diyaloğuyla arka arkaya kaldırılması — "izlemekten yönetmeye" temasını bir adım daha ileri taşıyan, gerçekçi bir sonraki adım (`AppsService.uninstall()` zaten var, yalnızca UI ve sıralı çağrı gerekiyor)
- [ ] **Uygulama listesine sıralama seçenekleri** (isim / paket adı / yaklaşık boyut): `installed_apps`'in `AppInfo` sınıfının hangi alanları sağladığı ilk gerçek derlemede doğrulanmalı, sıralamaya göre buna göre karar verilmeli
- [ ] **Depolama yönetiminde ayrıntılı kırılım**: `StorageService`'in şu an tek bir toplam (`totalBytes`) döndürdüğü belge klasörü, alt klasör bazında ("hangi klasör ne kadar yer kaplıyor") listelenip yalnızca seçilen alt klasörün silinebilmesi — mevcut `clearCache()` deseninin genişletilmesi, yeni bir pakete ihtiyaç duymuyor
- [ ] **Ayarların JSON olarak dışa/içe aktarılması**: `SettingsService`'teki tüm tercihlerin tek bir JSON dosyasına yazılıp (paylaşma için `share_plus`) veya bir dosyadan geri okunması — cihaz değişikliğinde/yedeklemede kullanışlı, gerçekçi ve küçük kapsamlı bir özellik
- [ ] Ağ sekmesine bağlantı türü (Wi-Fi / mobil veri) ve Wi-Fi ağ adı (SSID) bilgisinin eklenmesi — `network_info_plus` paketi zaten kullanılıyor, yalnızca ek alanların okunması gerekiyor

## Flutter'ın (bu temel sürümün) sınırları — önemli

Godot çekirdeğinde doğrudan gelen bazı veriler Flutter tarafında ya bir paket ile karşılanıyor ya da resmi/cross-platform bir API bulunmadığı için eksik bırakıldı:

- **Fener/flaş açma**: bu temel sürümde yok.
- **Ekran parlaklığı**: yalnızca **uygulama-seviyesi** (`screen_brightness`'ın `application` API'si) yönetiliyor - ek izin gerekmiyor ama uygulama kapanınca/arka plana atılınca ekran sistemin genel değerine döner. Kalıcı, sistem genelinde değişiklik için `system` API'si + Android'de `WRITE_SETTINGS` izin akışı gerekir; bu bilinçli olarak kapsam dışı - bkz. YGL "Diğer geliştirme fikirleri".
- **Tam işlemci model adı**: `device_info_plus` çoğunlukla mimari (ABI) bilgisi veriyor, Godot'taki gibi tam model adı için platform kanalı (native kod) gerekir.
- **Pilde kalan süre**: `battery_plus` bu veriyi doğrudan vermiyor; `BatteryService` kendi ölçtüğü deşarj/şarj hızından kaba bir tahmin üretiyor (bkz. YGL). Kesin bir değer değil - üretici/donanım seviyesinde bir hesaplama değil, sadece gözlemlenen değişim hızının doğrusal ekstrapolasyonu.
- **Diğer uygulamaların anlık RAM kullanımı ve doğrudan durdurulması**: Android API 21'den beri üçüncü parti uygulamalara kapalı (yalnızca sistem uygulamaları/kök erişimi yapabilir). "Uygulamalar" sekmesi bu yüzden RAM göstermiyor; "Durdur" gerçek "Zorla Durdur" düğmesinin bulunduğu sistem ekranını açıyor. Bkz. `AppsService` belgesi ve YGL.

## Renk paleti (AMOLED)

| Kullanım           | Renk      | Dart sabiti                  |
|--------------------|-----------|-------------------------------|
| Zemin              | `#000000` | `AppColors.background`        |
| Kart zemini        | `#0a0a0a` | `AppColors.card`               |
| Kart kenarlığı     | `#1c1c1c` | `AppColors.border`             |
| Ana metin          | `#e8e8e8` | `AppColors.text`               |
| İkincil metin      | `#7a7a7a` | `AppColors.muted`              |
| Vurgu (yeşil)      | `#00e676` | `AppColors.accent`             |
| Vurgu (camgöbeği)  | `#18ffff` | `AppColors.accentCyan`         |
| Uyarı              | `#ffab00` | `AppColors.warn`               |
| Kritik             | `#ff5252` | `AppColors.danger`             |

Bu renkleri değiştirmek için tek yapmanız gereken `lib/core/theme/app_colors.dart` dosyasını düzenlemek — tüm uygulama (kartlar, rozetler, grafik, butonlar) buradan besleniyor. Proje bilinçli olarak **yalnızca AMOLED tema** için tasarlanıyor; açık mod bilinçli olarak sunulmuyor.

## Sonraki adım

`flutter pub get` çalıştırıp ardından `flutter create . --platforms=android,ios` ile native klasörleri üretin, gerçek bir cihazda (tercihen AMOLED ekranlı) çalıştırıp yukarıdaki YGL listesindeki "Sıradaki adımlar" bölümünden devam edin. `flutter pub get` sonrası `flutter test` ile `test/` altındaki birim/widget testlerini de bu ortamda ilk kez gerçek bir SDK üzerinden çalıştırıp doğrulamanız önerilir (bkz. bu oturumun notu).
