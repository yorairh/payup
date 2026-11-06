package com.payup

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.payup.domain.AppLanguage

/** The system per-app language (Android 13+), kept by AppCompat on older versions. */
class AndroidAppLanguage : AppLanguage {
    override fun current(): String {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return ""
        val code = if (locale.language == "iw") "he" else locale.language
        return AppLanguage.OPTIONS.firstOrNull { it.tag.substringBefore('-') == code }?.tag ?: ""
    }

    override fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
