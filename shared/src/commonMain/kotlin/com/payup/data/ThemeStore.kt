package com.payup.data

import com.payup.domain.AppTheme
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The chosen color theme. Read synchronously, so it is known before the first frame. */
class ThemeStore(private val settings: Settings) {
    private val _theme = MutableStateFlow(
        AppTheme.entries.firstOrNull { it.name == settings.getStringOrNull(KEY) } ?: AppTheme.ORIGINAL,
    )
    val theme: StateFlow<AppTheme> = _theme

    fun set(theme: AppTheme) {
        _theme.value = theme
        settings.putString(KEY, theme.name)
    }

    private companion object {
        const val KEY = "theme"
    }
}
