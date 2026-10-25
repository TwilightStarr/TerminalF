package com.terminalf.app.data

import com.terminalf.app.BuildConfig

/** Yüklü uygulamanın sürümü (derleme parametresinden). Okunamazsa `—`; uydurma değer üretilmez. */
fun appVersion(): String = BuildConfig.VERSION_NAME.ifBlank { "—" }
