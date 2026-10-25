package com.terminalf.app.core

/** Bir tweak'in cihazdaki durumu. */
enum class TweakState(val label: String) {
    On("AÇIK"),
    Off("KAPALI"),
    Unknown("BİLİNMİYOR"),
}

/**
 * Durum + bu durumun nasıl elde edildiği.
 *
 * [estimated] `true` ise durum cihazdan OKUNMADI; kontrol komutu yoktu ya da çalıştırılamadı
 * ve değer uygulamanın kendi kaydından tahmin edildi.
 */
data class TweakStatus(val state: TweakState, val estimated: Boolean = false) {
    /** Cihazdan gerçekten okunmuş (kesin) bir durum mu? */
    val isLive: Boolean get() = !estimated && state != TweakState.Unknown
}

/**
 * İki çıktıyı satır satır karşılaştırır. Boşluklar kırpılır, boş satırlar yok sayılır ve iki
 * değer de sayıysa sayısal olarak karşılaştırılır (`settings get` bazı cihazlarda `0`, bazılarında
 * `0.0` döndürür).
 */
fun outputsMatch(expected: String, actual: String): Boolean {
    val e = outputLines(expected)
    val a = outputLines(actual)
    if (e.size != a.size) return false
    for (i in e.indices) {
        if (!valueEquals(e[i], a[i])) return false
    }
    return true
}

private fun valueEquals(a: String, b: String): Boolean {
    if (a == b) return true
    val x = a.toDoubleOrNull()
    val y = b.toDoubleOrNull()
    return x != null && y != null && x == y
}

/**
 * Bir `checkCommand` sonucundan durumu çıkarır:
 * - komut başarısız / çıktı hata içeriyor -> [TweakState.Unknown] (durum OKUNAMADI),
 * - çıktı [expected] ile eşleşiyor -> [TweakState.On],
 * - aksi halde -> [TweakState.Off].
 */
fun stateFromCheckResult(expected: String, result: ShellResult): TweakState {
    if (failureReason(result) != null) return TweakState.Unknown
    return if (outputsMatch(expected, result.output)) TweakState.On else TweakState.Off
}
