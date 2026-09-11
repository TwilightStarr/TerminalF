import 'dart:io';

/// Tek bir bellek okumasi (RSS - Resident Set Size, bayt cinsinden).
class MemoryReading {
  const MemoryReading({required this.currentRssBytes, required this.peakRssBytes});

  final int currentRssBytes;
  final int peakRssBytes;

  static const MemoryReading empty = MemoryReading(currentRssBytes: 0, peakRssBytes: 0);
}

/// Godot'taki `Performance.get_monitor(Performance.MEMORY_STATIC)` ve
/// `MEMORY_STATIC_MAX` cagrilarinin Flutter karsiligi.
///
/// `dart:io`'nun `ProcessInfo.currentRss` / `maxRss` degerleri, calisan
/// Dart/Flutter surecinin isletim sistemi tarafindan raporlanan gercek
/// bellek kullanimini verir - ek bir paket veya platform kanali
/// gerekmez. Bazi platform/derleme kombinasyonlarinda deger 0
/// donebilir; bu durumda arayuz "0 B" gosterir (bkz. README > YGL,
/// gercek cihazda dogrulama maddesi).
class MemoryService {
  MemoryReading read() {
    try {
      return MemoryReading(
        currentRssBytes: ProcessInfo.currentRss,
        peakRssBytes: ProcessInfo.maxRss,
      );
    } catch (_) {
      return MemoryReading.empty;
    }
  }
}
