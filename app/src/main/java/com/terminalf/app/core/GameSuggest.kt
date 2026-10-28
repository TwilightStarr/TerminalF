package com.terminalf.app.core

/**
 * Son oyun oturumunun ölçümüne bakarak bir sonraki oturum için ayar önerir. Her oyun için aynı
 * mantık çalışır.
 *
 * Isındıysa çözünürlüğü (gerekirse kare hızını) düşürmeyi, ısınmadıysa ve hedef FPS tutulduysa bir
 * kademe daha net görüntüyü önerir. Veri yetersizse ya da değişiklik gerekmiyorsa `null`.
 */
fun suggestSettings(stats: SessionStats?, config: GameModeConfig): ScaleSuggestion? {
    val avg = stats?.avgFps
    if (stats == null || avg == null || stats.samples < 3) return null
    val hot = (stats.maxThermal ?: 0) >= 2 || stats.guardTriggered
    val i = scaleIndex(config.scale)
    if (hot) {
        if (i < kScaleSteps.size - 1) {
            return ScaleSuggestion(
                kScaleSteps[i + 1],
                tr(
                    "The phone got hot in the last game. One step lower resolution reduces heat.",
                    "Son oyunda telefon ısındı. Bir kademe daha düşük çözünürlük ısıyı düşürür.",
                ),
            )
        }
        if (config.targetFps > 30) {
            return ScaleSuggestion(
                0.4,
                tr(
                    "The phone got hot in the last game and the resolution is already at its lowest. Capping the frame rate at 30 FPS reduces heat further.",
                    "Son oyunda telefon ısındı ve çözünürlük zaten en düşükte. Kare hızını 30 FPS'e çekmek ısıyı daha çok düşürür.",
                ),
                fps = 30,
            )
        }
        return null
    }
    if (avg >= config.targetFps * 0.9 && i > 0) {
        return ScaleSuggestion(
            kScaleSteps[i - 1],
            tr(
                "The last game did not overheat and the target FPS was met. You can try one step sharper image.",
                "Son oyunda ısınma olmadı ve hedef FPS tutuldu. Bir kademe daha net görüntü deneyebilirsiniz.",
            ),
        )
    }
    return null
}
