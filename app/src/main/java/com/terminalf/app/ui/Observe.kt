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
 * `inline` olması şarttır: okuma, çağıran composable'ın kapsamına yazılsın diye.
 */
@Composable
inline fun <T : Observable> T.observe(): T {
    @Suppress("UNUSED_VARIABLE")
    val tick = changes.collectAsState().value
    return this
}
