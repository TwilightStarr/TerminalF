package com.terminalf.app.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Flutter `ChangeNotifier` karşılığı. Denetleyiciler (controller) okunabilir alanlarını açık tutar;
 * her değişiklikte [changes] bir artar. Arayüz (Compose) bu akışı dinleyerek yeniden çizilir, mantık
 * katmanı ise Android'e bağlı kalmaz (JVM'de test edilebilir).
 *
 * İş parçacığı kuralı: durum alanları yalnızca denetleyicinin kendi coroutine bağlamında
 * (uygulamada ana iş parçacığı) değişir; bloklayan işler (süreç çalıştırma, dosya okuma)
 * hizmetlerin içinde `Dispatchers.IO`'ya taşınır.
 */
open class Observable {
    private val version = MutableStateFlow(0L)
    private var disposed = false

    /** Her değişiklikte artan sayaç; Compose'ta `collectAsState()` ile dinlenir. */
    val changes: StateFlow<Long> get() = version

    protected val isDisposed: Boolean get() = disposed

    open fun notifyChanged() {
        if (!disposed) version.update { it + 1 }
    }

    open fun dispose() {
        disposed = true
    }
}
