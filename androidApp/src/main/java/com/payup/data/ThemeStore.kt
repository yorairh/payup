package com.payup.data

import android.content.Context
import com.payup.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The chosen color theme, kept in SharedPreferences so it is known before the first frame. */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        runCatching { AppTheme.valueOf(prefs.getString(KEY, null).orEmpty()) }.getOrDefault(AppTheme.ORIGINAL),
    )
    val theme: StateFlow<AppTheme> = _theme

    fun set(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY, theme.name).apply()
    }

    private companion object {
        const val KEY = "theme"
    }
}
