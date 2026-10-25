package com.terminalf.app.core

/**
 * Dondurma / kaldırma gibi geri dönüşü zahmetli eylemlerde, telefonu kullanılmaz hale
 * getirebilecek paketleri korur.
 */

private val neverTouch = setOf(
    "android",
    "com.android.systemui",
    "com.android.settings",
    "com.android.phone",
    "com.android.providers.telephony",
    "com.android.providers.settings",
    "com.android.inputmethod.latin",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "moe.shizuku.privileged.api",
)

/** Bu uygulamanın kendi paketi (`applicationId`; Flutter sürümüyle aynı, güncelleme uyumu için). */
const val SELF_PACKAGE_PREFIX = "com.example.terminal_flutter"

/** `true` ise bu paket asla dondurulmamalı/kaldırılmamalıdır. */
fun isProtectedPackage(packageName: String, selfPrefix: String = SELF_PACKAGE_PREFIX): Boolean {
    if (packageName.startsWith(selfPrefix)) return true
    return packageName in neverTouch
}

/** `pm list packages -d` çıktısından paket adlarını okur (sıralı). */
fun parsePackageList(output: String): List<String> {
    val out = ArrayList<String>()
    for (raw in output.replace("\r", "").split("\n")) {
        val line = raw.trim()
        if (line.startsWith("package:")) {
            val name = line.substring("package:".length).trim()
            if (name.isNotEmpty()) out.add(name)
        }
    }
    out.sort()
    return out
}
