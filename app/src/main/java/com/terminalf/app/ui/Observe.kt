package com.terminalf.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.terminalf.app.state.Observable

/**
 * Bir denetleyicinin ([Observable]) değişikliklerini dinler: denetleyici `notifyChanged()`
 * dediğinde bunu çağıran composable yeniden çizilir. Flutter'daki `ListenableBuilder`
 * karşılığıdır. Her sayfa/parça, okuduğu denetleyici için en üstte bir kez çağırmalıdır:
 *
 * ```
 * control.observe()
 * ```
 *
 * Not: işlev değer (T) döndürdüğü için Compose onu yeniden başlatılabilir bir kapsam
 * olarak değil, çağıranın kapsamına gömülü işler; bu yüzden `inline` gerekmez ve durum okuması
 * çağıran composable'ı yeniden çizer.
 */
@Composable
fun <T : Observable> T.observe(): T {
    // Sayaç yalnızca okunur (abonelik için); değeri hiç negatif olmaz.
    check(changes.collectAsState().value >= 0L)
    return this
}
