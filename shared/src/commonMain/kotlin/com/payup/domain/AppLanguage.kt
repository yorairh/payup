package com.payup.domain

/** A language the app is translated into. [nativeName] is shown in its own script so it can be found from any language. */
data class LanguageOption(val tag: String, val nativeName: String)

/**
 * The language picker's model. The tag is "" for "follow the system". Each platform decides how the choice is stored and applied:
 * Android uses the system per-app language, the web will use the browser language plus its own saved choice.
 */
interface AppLanguage {
    /** The tag of the language the user picked, or "" for the system default. */
    fun current(): String

    /** Applies [tag] ("" for the system default). Android recreates the screen. */
    fun set(tag: String)

    companion object {
        val OPTIONS = listOf(
            LanguageOption("en", "English"),
            LanguageOption("he", "עברית"),
            LanguageOption("es", "Español"),
            LanguageOption("ru", "Русский"),
            LanguageOption("ar", "العربية"),
            LanguageOption("fr", "Français"),
            LanguageOption("de", "Deutsch"),
            LanguageOption("pt", "Português"),
            LanguageOption("zh-CN", "中文"),
        )
    }
}
