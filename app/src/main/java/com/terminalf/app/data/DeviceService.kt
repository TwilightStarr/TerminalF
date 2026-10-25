package com.terminalf.app.data

import android.os.Build
import java.util.Locale

/** Uygulama açılışında bir kez okunan, değişmeyen cihaz bilgileri. */
data class DeviceStaticInfo(
    val model: String,
    val os: String,
    val cpuArch: String,
    val coreCount: Int,
    val locale: String,
) {
    companion object {
        val Unknown = DeviceStaticInfo("Bilinmiyor", "Bilinmiyor", "Bilinmiyor", 0, "Bilinmiyor")
    }
}

/**
 * Model, Android sürümü, işlemci mimarisi (desteklenen ilk ABI), çekirdek sayısı ve yerel ayar.
 * Tam işlemci modeli adı için genel bir Android API'si yoktur; bu yüzden mimari gösterilir.
 */
class DeviceService {
    fun read(): DeviceStaticInfo {
        var model = "Bilinmiyor"
        var os = "Bilinmiyor"
        var cpuArch = "Bilinmiyor"
        try {
            model = "${Build.MANUFACTURER} ${Build.MODEL}"
            os = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: "Bilinmiyor"
        } catch (_: Exception) {
            // Okuma başarısız olursa varsayılan "Bilinmiyor" değerleriyle devam et.
        }
        return DeviceStaticInfo(
            model = model,
            os = os,
            cpuArch = cpuArch,
            coreCount = Runtime.getRuntime().availableProcessors(),
            locale = Locale.getDefault().toString(),
        )
    }
}
