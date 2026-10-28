package com.terminalf.app.core

/**
 * Supported interface languages. The app starts in English; the user can switch to Turkish from
 * Device > Settings > Language. The choice is stored with the other preferences.
 *
 * [nativeName] is always written in the language itself, so the picker shows "English" / "Türkçe"
 * regardless of the language that is currently active.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    EN("en", "English"),
    TR("tr", "Türkçe");

    companion object {
        /** Language used on first launch and whenever a stored value is missing or unknown. */
        val DEFAULT: AppLanguage = EN

        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}

/**
 * Holder of the active language. Pure Kotlin (no Android dependency) so the logic layer and its
 * JVM unit tests can produce localized text as well.
 *
 * The UI re-composes from scratch when the language changes (see `AppRoot`), so text is simply
 * resolved at the moment it is built. Do NOT cache localized text in top-level `val`s or in
 * `object` initializers: use functions / getters so the text follows the language.
 */
object I18n {
    @Volatile
    var language: AppLanguage = AppLanguage.DEFAULT

    val isTurkish: Boolean get() = language == AppLanguage.TR
}

/**
 * Returns [en] or [turkish] depending on the active language. Always write the English text
 * first. String templates work as usual: `tr("$n apps", "$n uygulama")`.
 */
fun tr(en: String, turkish: String): String =
    if (I18n.language == AppLanguage.TR) turkish else en

/** English noun agreement helper: `plural(n, "app", "apps")`. Turkish does not pluralize after numerals. */
fun plural(n: Int, one: String, many: String): String = if (n == 1) one else many
