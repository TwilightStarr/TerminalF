package com.terminalf.app.core

/**
 * Oyun Modu için ölçüm ve teşhis mantığı (saf, platform bağımsız): SurfaceFlinger kare
 * zamanları, ısıl durum, CPU frekansı ve izleyicinin günlük dosyası.
 *
 * DÜRÜST SINIR: Buradaki her sayı cihazdan OKUNAN bir değerdir; okunamayan değer `null`
 * kalır, tahmin edilip uydurulmaz. Teşhis ([diagnose]) bir sezgiseldir ve metinlerinde
 * "olası" ifadesini taşır.
 */

/** SurfaceFlinger "henüz sunulmadı" için kullandığı değer. */
const val PENDING_PRESENT: Long = Long.MAX_VALUE

private val focusPattern =
    Regex("""mCurrentFocus=Window\{[^ ]+ u\d+ ([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)/""")

/** `dumpsys window | grep mCurrentFocus` çıktısındaki ön plan paketi. Biçim tanınmazsa `null`. */
fun parseFocusedPackage(output: String): String? = focusPattern.find(output)?.groupValues?.get(1)

/**
 * `dumpsys SurfaceFlinger --list` içinden oyunun çizim katmanını seçer. `SurfaceView` içeren
 * katman tercih edilir; "Bounds"/"Background" yardımcı katmanları atlanır. Bulunamazsa `null`.
 */
fun pickGameLayer(listOutput: String, pkg: String): String? {
    val candidates = outputLines(listOutput).filter { line ->
        when {
            !line.contains(pkg) -> false
            line.contains("Bounds") || line.startsWith("Background") -> false
            else -> line.length < 300
        }
    }
    if (candidates.isEmpty()) return null
    for (line in candidates) {
        if (line.contains("SurfaceView")) return line
    }
    return candidates.first()
}

/** Bir kare penceresinin özeti. */
data class FrameStats(
    val frames: Int,
    val fps: Double,
    /** Penceredeki en uzun iki kare arası süre (takılma göstergesi). */
    val worstFrameMs: Double,
)

/** `fps = (kare-1) / geçen süre`. Veri yetersizse `null`. */
fun fpsFromWindow(frames: Int, firstNs: Long, lastNs: Long): Double? {
    if (frames < 5 || lastNs <= firstNs) return null
    val seconds = (lastNs - firstNs) / 1e9
    val fps = (frames - 1) / seconds
    if (fps.isNaN() || fps.isInfinite() || fps <= 0 || fps > 1000) return null
    return fps
}

/**
 * `dumpsys SurfaceFlinger --latency <katman>` çıktısından gerçek sunum zamanlarına (2. sütun)
 * bakarak kare hızını hesaplar.
 */
fun parseLatency(output: String): FrameStats? {
    val lines = outputLines(output)
    if (lines.size < 3) return null
    val times = ArrayList<Long>()
    for (line in lines.drop(1)) {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 3) continue
        val actual = parts[1].toLongOrNull()
        if (actual == null || actual <= 0 || actual == PENDING_PRESENT) continue
        times.add(actual)
    }
    if (times.size < 5) return null
    val fps = fpsFromWindow(times.size, times.first(), times.last()) ?: return null
    var worst = 0L
    for (i in 1 until times.size) {
        val gap = times[i] - times[i - 1]
        if (gap > worst) worst = gap
    }
    return FrameStats(frames = times.size, fps = fps, worstFrameMs = worst / 1e6)
}

/**
 * `dumpsys thermalservice` içindeki `Thermal Status: N` (0 = normal, 1 hafif, 2 orta,
 * 3 ciddi, 4 kritik...). Bulunamazsa `null`.
 */
fun parseThermalStatus(output: String): Int? =
    Regex("""Thermal Status:\s*(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()

/** Isıl durum sayısının okunabilir adı. */
fun thermalLabel(status: Int): String = when {
    status <= 0 -> tr("Normal", "Normal")
    status == 1 -> tr("Light", "Hafif")
    status == 2 -> tr("Moderate", "Orta")
    status == 3 -> tr("Severe", "Ciddi")
    else -> tr("Critical", "Kritik")
}

/** İzleyicinin günlüğündeki tek örnek. */
data class WatchSample(
    val epoch: Long,
    val fps: Double? = null,
    val thermal: Int? = null,
    /** Büyük çekirdek: anlık frekans / azami frekans (0-1). */
    val cpuRatio: Double? = null,
)

private fun intOrNull(s: String): Int? = if (s == "-") null else s.toIntOrNull()

private fun longOrNull(s: String): Long? = if (s == "-") null else s.toLongOrNull()

/** Günlük satırı: `epoch kare ilkNs sonNs ısıl curKHz maxKHz` (eksik değerler `-`). */
fun parseWatchLine(line: String): WatchSample? {
    val p = line.trim().split(Regex("\\s+"))
    if (p.size < 7) return null
    val epoch = p[0].toLongOrNull() ?: return null
    val frames = intOrNull(p[1])
    val first = longOrNull(p[2])
    val last = longOrNull(p[3])
    var fps: Double? = null
    if (frames != null && first != null && last != null) {
        fps = fpsFromWindow(frames, first, last)
    }
    val cur = intOrNull(p[5])
    val maxKhz = intOrNull(p[6])
    var ratio: Double? = null
    if (cur != null && maxKhz != null && maxKhz > 0) {
        ratio = (cur.toDouble() / maxKhz).coerceIn(0.0, 1.0)
    }
    return WatchSample(epoch = epoch, fps = fps, thermal = intOrNull(p[4]), cpuRatio = ratio)
}

/** Bir oyun oturumunun özeti. */
data class SessionStats(
    val samples: Int,
    val durationSec: Int,
    val avgFps: Double? = null,
    val minFps: Double? = null,
    val maxThermal: Int? = null,
    val avgCpuRatio: Double? = null,
    /** Isı koruması oyun sırasında devreye girdi mi? */
    val guardTriggered: Boolean = false,
) {
    /** FPS ölçülebilen örnek yoksa özetin anlamı yoktur. */
    val hasFps: Boolean get() = avgFps != null
}

/** Günlük metninden oturum özeti çıkarır; geçerli örnek yoksa `null`. */
fun parseWatchLog(raw: String): SessionStats? {
    val samples = ArrayList<WatchSample>()
    var guard = false
    for (line in outputLines(raw)) {
        if (line.startsWith("GUARD")) {
            guard = true
            continue
        }
        parseWatchLine(line)?.let { samples.add(it) }
    }
    if (samples.isEmpty()) return null

    val fpsList = samples.mapNotNull { it.fps }
    val ratios = samples.mapNotNull { it.cpuRatio }
    val maxThermal = samples.mapNotNull { it.thermal }.maxOrNull()

    var avgFps: Double? = null
    var minFps: Double? = null
    if (fpsList.isNotEmpty()) {
        avgFps = fpsList.sum() / fpsList.size
        minFps = fpsList.reduce { a, b -> if (a < b) a else b }
    }
    val avgRatio = if (ratios.isNotEmpty()) ratios.sum() / ratios.size else null

    return SessionStats(
        samples = samples.size,
        durationSec = (samples.last().epoch - samples.first().epoch).toInt(),
        avgFps = avgFps,
        minFps = minFps,
        maxThermal = maxThermal,
        avgCpuRatio = avgRatio,
        guardTriggered = guard,
    )
}

/** Teşhis türü. */
enum class DiagnosisKind { OnTarget, Thermal, CpuBound, GpuBound, Unknown }

data class Diagnosis(val kind: DiagnosisKind, val title: String, val advice: String)

/**
 * Oturum özetinden olası darboğazı söyler. Sezgiseldir:
 * - Hedefin >= %90'ı -> sorun yok.
 * - Isıl durum >= 2 -> ısıl kısıtlama olası (işlemci/GPU frekansı sistem tarafından kısılıyor).
 * - Büyük çekirdek >= %80 frekansta -> işlemci (oyun hesabı) sınırlı; çözünürlük düşürmek
 *   akıcılığı pek artırmaz.
 * - Büyük çekirdek < %60 frekansta ve ısıl sorun yok -> GPU/bekleme sınırlı olası.
 */
fun diagnose(stats: SessionStats, targetFps: Int): Diagnosis {
    val fps = stats.avgFps
    if (fps == null) {
        return Diagnosis(
            DiagnosisKind.Unknown,
            tr("FPS could not be measured", "FPS ölçülemedi"),
            tr(
                "The game's frame times could not be read on this device. Heat and CPU data still appear below if they were recorded.",
                "Bu cihazda oyunun kare zamanları okunamadı. Isı ve işlemci bilgisi yine de kaydedildiyse aşağıda görünür.",
            ),
        )
    }
    if (fps >= targetFps * 0.9) {
        return Diagnosis(
            DiagnosisKind.OnTarget,
            tr("Target reached", "Hedefe ulaşıldı"),
            tr(
                "At an average of ${Math.round(fps)} FPS you are close to the $targetFps FPS target.",
                "Ortalama ${Math.round(fps)} FPS ile hedef $targetFps FPS'e yakınsınız.",
            ),
        )
    }
    val thermal = stats.maxThermal
    val ratio = stats.avgCpuRatio
    if (thermal != null && thermal >= 2) {
        return Diagnosis(
            DiagnosisKind.Thermal,
            tr("Thermal throttling likely", "Isıl kısıtlama olası"),
            tr(
                "The system may be throttling the CPU/GPU because the phone is hot. Try 30 FPS and 50% resolution, play without charging, take the case off or take a short break.",
                "Telefon ısındığı için sistem işlemci/GPU hızını kısıyor olabilir. 30 FPS ve %50 çözünürlük ayarını deneyin, şarj etmeden oynayın, kılıfı çıkarın ya da kısa mola verin.",
            ),
        )
    }
    if (ratio != null && ratio >= 0.8) {
        return Diagnosis(
            DiagnosisKind.CpuBound,
            tr("CPU-bound (likely)", "İşlemci sınırlı (olası)"),
            tr(
                "The big core is running at almost full speed: the slowdown comes from the game's own calculations (many units, huge numbers). Lowering the resolution cuts heat and battery use but does not raise FPS much. Turning off speed-up / auto-battle in the game is more effective.",
                "Büyük çekirdek neredeyse tam hızda: yavaşlama oyunun kendi hesabından (çok sayıda birim, dev sayılar) geliyor. Çözünürlük düşürmek ısıyı ve pili azaltır ama FPS'i çok artırmaz. Oyun içinde hızlandırma/otomatik savaşı kapatmak daha etkilidir.",
            ),
        )
    }
    if (ratio != null && ratio < 0.6) {
        return Diagnosis(
            DiagnosisKind.GpuBound,
            tr("GPU- or wait-bound (likely)", "GPU ya da bekleme sınırlı (olası)"),
            tr(
                "The CPU is not running at full speed; the bottleneck may be rendering. Lowering the resolution to 50% helps.",
                "İşlemci tam hızda çalışmıyor; darboğaz çizimde olabilir. Çözünürlüğü %50'ye düşürmek işe yarar.",
            ),
        )
    }
    return Diagnosis(
        DiagnosisKind.Unknown,
        tr("No clear bottleneck", "Net bir darboğaz görünmüyor"),
        tr(
            "The data is inconclusive. Check again after a longer session.",
            "Veriler kesin bir sonuç vermiyor. Daha uzun bir oturumdan sonra tekrar bakın.",
        ),
    )
}
