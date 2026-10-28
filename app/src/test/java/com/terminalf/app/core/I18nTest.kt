package com.terminalf.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class I18nTest {
    @get:org.junit.Rule val language = TestLanguage(AppLanguage.EN)

    @Test fun defaultsToEnglish() {
        assertEquals(AppLanguage.EN, AppLanguage.DEFAULT)
        assertEquals(AppLanguage.EN, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.TR, AppLanguage.fromTag("tr"))
    }

    @Test fun pickerShowsNativeNames() {
        assertEquals(listOf("English", "Türkçe"), AppLanguage.entries.map { it.nativeName })
    }

    @Test fun trFollowsTheActiveLanguage() {
        assertEquals("Hello", tr("Hello", "Merhaba"))
        I18n.language = AppLanguage.TR
        assertEquals("Merhaba", tr("Hello", "Merhaba"))
    }

    @Test fun englishPlural() {
        assertEquals("app", plural(1, "app", "apps"))
        assertEquals("apps", plural(0, "app", "apps"))
        assertEquals("apps", plural(2, "app", "apps"))
    }

    @Test fun modelTextsFallBackToTurkishWhenEnglishMissing() {
        val tweak = Tweak(
            id = "x", title = "Başlık", description = "Açıklama", category = TweakCategory.Performance,
            applyCommand = "true",
        )
        assertEquals("Başlık", tweak.displayTitle)
        val withEn = tweak.copy(titleEn = "Title", descriptionEn = "Description")
        assertEquals("Title", withEn.displayTitle)
        I18n.language = AppLanguage.TR
        assertEquals("Başlık", withEn.displayTitle)
    }
}
