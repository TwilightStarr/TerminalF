package com.terminalf.app.core

/**
 * Kabuk (shell) komutu çalıştırmayla ilgili saf (platform bağımsız) yardımcılar. Root
 * (`RootService`) ve Shizuku (`ShizukuService`) yolları tarafından ortak kullanılır.
 * Android'e bağlı olmadığı için birim testleri gerçek cihaz gerektirmez.
 */

/**
 * Bir kabuk komutunun sonucu.
 *
 * [exitCode] `null` ise çıkış kodu ELDE EDİLEMEMİŞTİR; bu durumda başarı, [failureReason]
 * içinde çıktı metnine bakılarak tahmin edilir - kesin değildir.
 */
data class ShellResult(val output: String, val exitCode: Int? = null) {
    val exitCodeKnown: Boolean get() = exitCode != null

    /** Çıkış kodu biliniyor VE sıfırdan farklı. */
    val failedByExitCode: Boolean get() = exitCode != null && exitCode != 0
}

/**
 * Bir tweak komutu çalıştı ama başarısız sayıldığında fırlatılır (sıfırdan farklı çıkış kodu
 * ya da çıktıda hata izi). Böylece arayüz başarısız komutu "tamamlandı" diye göstermez.
 */
class TweakCommandFailedException(override val message: String) : Exception(message)

/** `id` komutunun çıktısından okunan çalışma kimliği. */
data class ShellIdentity(val uid: Int, val name: String, val raw: String) {
    val isRoot: Boolean get() = uid == 0

    /** Kullanıcıya gösterilen kısa ad: `root`, `shell` vb. */
    val label: String get() = if (isRoot) "root" else name
}

private val idPattern = Regex("""uid=(\d+)(?:\(([^)]*)\))?""")

/** `uid=2000(shell) gid=2000(shell) ...` biçimindeki `id` çıktısını ayrıştırır; `uid=` yoksa `null`. */
fun parseIdOutput(output: String): ShellIdentity? {
    val match = idPattern.find(output) ?: return null
    val uid = match.groupValues[1].toInt()
    val name = match.groups[2]?.value ?: if (uid == 0) "root" else "uid $uid"
    return ShellIdentity(uid = uid, name = name, raw = output.trim())
}

/** Bir değeri tek tırnak içine güvenle alır: `it's` -> `'it'\''s'`. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/** [wrapForShell]'in çıktıya eklediği çıkış kodu satırının öneki. */
const val EXIT_MARKER = "__TF_EXIT__"

/**
 * Komutu `sh -c` ile sarar: alt kabukta çalıştırır (böylece `;` ile zincirlenmiş komutların
 * hepsi çalışır), stderr'i stdout'a katar ve son satıra çıkış kodunu `__TF_EXIT__:<kod>`
 * olarak yazar. Sonucu [parseWrappedOutput] ile çözün.
 *
 * Çıkış kodu alt kabuğun SON komutunun kodudur; zincirin ortasındaki bir komutun hatası bu
 * koda yansımaz - o yüzden [failureReason] çıktıya da bakar.
 */
fun wrapForShell(command: String): String {
    val script = "( $command ) 2>&1; echo \"$EXIT_MARKER:\$?\""
    return "sh -c ${shellQuote(script)}"
}

private val markerPattern = Regex("^$EXIT_MARKER:(-?\\d+)$")

/**
 * [wrapForShell] ile sarılmış bir komutun ham çıktısından çıkış kodu satırını ayıklar. Satır
 * yoksa (`exitCode == null`) çıkış kodu doğrulanamamıştır.
 */
fun parseWrappedOutput(raw: String): ShellResult {
    var code: Int? = null
    val kept = ArrayList<String>()
    for (line in raw.replace("\r", "").split("\n")) {
        val match = markerPattern.find(line.trim())
        if (match != null) {
            code = match.groupValues[1].toIntOrNull()
        } else {
            kept.add(line)
        }
    }
    return ShellResult(output = kept.joinToString("\n").trim(), exitCode = code)
}

/**
 * `a; b; c` biçimindeki zinciri tek tek komutlara böler. YALNIZCA `sh -c` sarmalayıcısının
 * çalışmadığı durumda yedek olarak kullanılır. Tırnak içindeki `;` karakterlerini AYIRT ETMEZ.
 */
fun splitCommandChain(command: String): List<String> =
    command.split(';').map { it.trim() }.filter { it.isNotEmpty() }

/** Boş olmayan, kırpılmış çıktı satırları. */
fun outputLines(output: String): List<String> =
    output.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }

private val failurePattern = Regex(
    "(exception|permission denial|permission denied|unknown package|" +
        "not found|no such file|failure|unknown command|not allowed|" +
        "inaccessible|\\berror\\b)",
    RegexOption.IGNORE_CASE,
)

/**
 * Çıktı, tipik Android kabuk hata izlerini (`Exception`, `Unknown package`, `Permission
 * denied` ...) içeriyor mu? Çıkış kodunun bilinmediği ya da zincirin ortasındaki bir hatanın
 * kodu gizlediği durumlar için sezgisel bir kontroldür - kesin değildir.
 */
fun outputLooksLikeFailure(output: String): Boolean = failurePattern.containsMatchIn(output)

/** Sonuç başarısızsa kullanıcıya gösterilecek açıklama, başarılıysa `null`. */
fun failureReason(result: ShellResult): String? {
    if (result.failedByExitCode) {
        return tr("command failed (exit code ${result.exitCode})", "komut hata verdi (çıkış kodu ${result.exitCode})")
    }
    if (outputLooksLikeFailure(result.output)) {
        return if (result.exitCodeKnown) {
            tr("exit code 0 but the output contains an error", "çıkış kodu 0 ama çıktı hata içeriyor")
        } else {
            tr("the output contains an error (exit code unavailable)", "çıktı hata içeriyor (çıkış kodu alınamadı)")
        }
    }
    return null
}
