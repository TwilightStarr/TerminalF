# TerminalF (Kotlin)

Android cihaz yönetim uygulaması TerminalF'in **Kotlin + Jetpack Compose** sürümü. Flutter sürümünün tamamı
(Ana Sayfa, Optimize, Uygulamalar, Oyun Modu, Cihaz/Görünüm/Ayarlar, Shizuku ve root erişimi, bildirimler,
oyun gözcüsü, ısı koruması) Kotlin'e taşındı.

## Mimari

| Katman | Yol | Not |
|---|---|---|
| Saf mantık | `core/` | Android'e bağımlı değil; JVM'de test edilir (komut üretimi, ayrıştırıcılar, oyun modu, gözcü betiği, tavsiye motoru, paletler) |
| Durum | `state/` | `StateFlow` tabanlı denetleyiciler (`ControlController`, `GameModeController`, `LiveStats`, `AlertMonitor`, `AppSettings`) |
| Veri/Android | `data/`, `shizuku/` | Root (`su -c`), Shizuku UserService (AIDL), pil, depolama, bildirim, SharedPreferences |
| Arayüz | `ui/` | Compose (Material 3), 5 sekmeli kabuk, bottom-sheet/diyalog/toast ana bilgisayarları |

## Derleme

```
./gradlew :app:testDebugUnitTest      # birim testleri
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease -PversionName=3.0.0 -PversionCode=1
```

Gereksinimler: JDK 17, Android SDK 35. Kalıcı imza için kök dizine `key.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) koyun; yoksa debug anahtarıyla imzalanır.
GitHub Actions: `main`'e push → test + imzalı APK + Release (`.github/workflows/build.yml`).

## Önemli notlar

- `applicationId` bilinçli olarak `com.example.terminal_flutter` bırakıldı: Flutter sürümünün üzerine **güncelleme** olarak kurulur
  (aynı imza anahtarıyla). Flutter'ın `SharedPreferences` ayarları ilk açılışta otomatik taşınır.
- Erişim: önce root, yoksa Shizuku. Hazırlık gerçek bir `id` komutuyla kanıtlanır; başarısızlık asla başarı gibi gösterilmez.
- minSdk 24, targetSdk 34.
