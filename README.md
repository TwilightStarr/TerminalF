# TerminalF

Android cihazlar için hızlı, sade bir yönetim ve optimizasyon uygulaması. Root veya Shizuku ile sistem
düzeyinde ayarları tek dokunuşla uygular; oyun performansını, pili ve depolamayı tek yerden yönetmeni sağlar.

## Özellikler

- **Ana Sayfa** – Anlık CPU, bellek, ısı, pil ve ağ durumu; pil süresi tahmini ve uyarılar.
- **Optimize** – Sistem animasyonları, yenileme hızı, arka plan kısıtlamaları, önbellek temizliği gibi hazır ayarlar.
- **Uygulamalar** – Yüklü uygulamaları listele, dondur, kaldır; sistem için kritik paketler korunur.
- **Oyun** – Oyun başına çözünürlük ölçeği ve FPS hedefi, hazır profiller, oyun gözcüsü ve ısı koruması.
- **Cihaz / Görünüm / Ayarlar** – Cihaz bilgileri, 9 renk teması, ana ekran simgesi seçimi, bildirim ve uyarı ayarları.

## Erişim

Önce root denenir, yoksa Shizuku kullanılır. Hazır olma durumu gerçek bir komutla doğrulanır; başarısız bir işlem
asla başarılı gibi gösterilmez. Ağa veri gönderilmez, telemetri yoktur.

## Kurulum ve güncelleme

APK'yı [Releases](../../releases) sayfasından indirip kur. Sonraki sürümler **silip yeniden kurmadan**, mevcut
uygulamanın üzerine güncellenir; GitHub adresinden güncelleme yapan herhangi bir uygulamaya (ör. Obtainium)
bu deponun bağlantısını vermen yeterli.

Üst üste güncellemenin çalışması için iki şey sabit tutulur:

- **Aynı imza:** Her sürüm aynı anahtarla imzalanır. Anahtar depoda tutulmaz, GitHub Secrets'ta `KEYSTORE_BASE64` adıyla durur. Değiştirme.
- **Artan sürüm numarası:** `versionCode` her derlemede otomatik olarak büyür (dakika bazlı), elle artırmak gerekmez.

`applicationId` değerini (`com.example.terminal_flutter`) değiştirme; değişirse Android bunu yeni bir uygulama sayar.

## Derleme

```
./gradlew :app:testDebugUnitTest      # birim testleri
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # imzalı release APK
```

Gereksinimler: JDK 17, Android SDK 35. `main` dalına yapılan her push; test çalıştırır, imzalı APK üretir ve
otomatik olarak yeni bir Release yayınlar (`.github/workflows/build.yml`). Commit mesajına `[minor]` veya
`[major]` yazarak sürümü büyütebilirsin.

Release derlemesi için GitHub'da **Settings → Secrets and variables → Actions** altına `KEYSTORE_BASE64`
secret'ı eklenmelidir; yoksa derleme bilerek durur.

## Gereksinimler

minSdk 24, targetSdk 34.
