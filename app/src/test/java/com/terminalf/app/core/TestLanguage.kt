package com.terminalf.app.core

import org.junit.rules.ExternalResource

/**
 * Most assertions in this suite were written against the Turkish texts; this rule runs a test in the
 * given language and always restores the default (English) afterwards, so tests stay independent.
 */
class TestLanguage(private val language: AppLanguage = AppLanguage.TR) : ExternalResource() {
    override fun before() {
        I18n.language = language
    }

    override fun after() {
        I18n.language = AppLanguage.DEFAULT
    }
}
