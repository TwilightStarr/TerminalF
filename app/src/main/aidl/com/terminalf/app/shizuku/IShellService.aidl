package com.terminalf.app.shizuku;

/**
 * Shizuku'nun "shell" (ya da root) kimliğiyle başlattığı süreçte çalışan komut servisi.
 */
interface IShellService {
    /** Shizuku UserService sözleşmesi: bu işlem kodu (16777114) `destroy` için ayrılmıştır. */
    void destroy() = 16777114;

    /**
     * [command]'ı `sh` ile çalıştırır (stderr stdout'a katılır). Dönen metnin SON satırı
     * `__TF_EXIT__:<çıkış kodu>` biçimindedir. [timeoutSeconds] dolarsa süreç öldürülür.
     */
    String exec(String command, int timeoutSeconds) = 1;
}
