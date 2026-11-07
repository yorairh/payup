package com.payup.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.payup.domain.AppTheme

/**
 * Every color the app draws with. The names come from the original "poker felt" look:
 * the felt tiers are the background and cards, gold is the accent, cream is the text.
 */
@Immutable
class PayUpPalette(
    val dark: Boolean,
    val feltDark: Color,
    val felt: Color,
    val feltLight: Color,
    val gold: Color,
    val goldDeep: Color,
    val onGold: Color,
    val cream: Color,
    val win: Color,
    val lose: Color,
    val loserRowA: Color,
    val loserRowB: Color,
)

private val OriginalPalette = PayUpPalette(
    dark = true,
    feltDark = Color(0xFF0B3D2E),
    felt = Color(0xFF14593F),
    feltLight = Color(0xFF1E7352),
    gold = Color(0xFFF2C14E),
    goldDeep = Color(0xFFC9932A),
    onGold = Color(0xFF2B1F00),
    cream = Color(0xFFF5F0E1),
    win = Color(0xFF6BEB98),
    lose = Color(0xFFFF7A7A),
    loserRowA = Color(0xFF3A4440),
    loserRowB = Color(0xFF2C3330),
)

// Blue vs orange stays distinguishable for red-green and blue-yellow color blindness.
private val ColorBlindPalette = OriginalPalette.copyWith(
    win = Color(0xFF6CC8FF),
    lose = Color(0xFFFF8040),
)

private val RichPalette = PayUpPalette(
    dark = true,
    feltDark = Color(0xFF3A0D18),
    felt = Color(0xFF55172A),
    feltLight = Color(0xFF6E2238),
    gold = Color(0xFFE2BC62),
    goldDeep = Color(0xFFB8913A),
    onGold = Color(0xFF2A1A00),
    cream = Color(0xFFFFF8E7),
    win = Color(0xFF8FE3B0),
    lose = Color(0xFFFFA39A),
    loserRowA = Color(0xFF2B0A12),
    loserRowB = Color(0xFF220810),
)

private val WhiteBlackPalette = PayUpPalette(
    dark = false,
    feltDark = Color(0xFFFFFFFF),
    felt = Color(0xFFF1F1F1),
    feltLight = Color(0xFFE2E2E2),
    gold = Color(0xFF111111),
    goldDeep = Color(0xFF3A3A3A),
    onGold = Color(0xFFFFFFFF),
    cream = Color(0xFF111111),
    win = Color(0xFF111111),
    lose = Color(0xFF6B6B6B),
    loserRowA = Color(0xFFD6D6D6),
    loserRowB = Color(0xFFCACACA),
)

private val DarkPalette = PayUpPalette(
    dark = true,
    feltDark = Color(0xFF121212),
    felt = Color(0xFF1E1E1E),
    feltLight = Color(0xFF2B2B2B),
    gold = Color(0xFF8AB4F8),
    goldDeep = Color(0xFF5C8FE0),
    onGold = Color(0xFF0A1A33),
    cream = Color(0xFFE6E6E6),
    win = Color(0xFF7BD88F),
    lose = Color(0xFFFF8A8A),
    loserRowA = Color(0xFF2E2424),
    loserRowB = Color(0xFF241C1C),
)

private fun PayUpPalette.copyWith(win: Color, lose: Color) = PayUpPalette(
    dark, feltDark, felt, feltLight, gold, goldDeep, onGold, cream, win, lose, loserRowA, loserRowB,
)

fun AppTheme.palette(): PayUpPalette = when (this) {
    AppTheme.ORIGINAL -> OriginalPalette
    AppTheme.COLOR_BLIND -> ColorBlindPalette
    AppTheme.RICH -> RichPalette
    AppTheme.WHITE_BLACK -> WhiteBlackPalette
    AppTheme.DARK -> DarkPalette
}

val LocalPayUp = staticCompositionLocalOf { OriginalPalette }

// The same names the screens always used, now resolved from the current theme.
val FeltDark: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.feltDark
val Felt: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.felt
val FeltLight: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.feltLight
val Gold: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.gold
val GoldDeep: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.goldDeep
val OnGoldColor: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.onGold
val Cream: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.cream
val WinGreen: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.win
val LoseRed: Color @Composable @ReadOnlyComposable get() = LocalPayUp.current.lose

private fun PayUpPalette.toColorScheme(): ColorScheme {
    val onError = if (dark) Color(0xFF3B0000) else Color.White
    return if (dark) {
        darkColorScheme(
            primary = gold, onPrimary = onGold, primaryContainer = goldDeep, onPrimaryContainer = onGold,
            secondary = win, onSecondary = feltDark, secondaryContainer = feltLight, onSecondaryContainer = cream,
            background = feltDark, onBackground = cream, surface = feltDark, onSurface = cream,
            surfaceVariant = felt, onSurfaceVariant = cream.copy(alpha = 0.75f),
            surfaceContainerLowest = feltDark, surfaceContainerLow = felt, surfaceContainer = felt,
            surfaceContainerHigh = feltLight, surfaceContainerHighest = feltLight,
            error = lose, onError = onError, errorContainer = loserRowA, onErrorContainer = cream,
            outline = cream.copy(alpha = 0.4f), outlineVariant = cream.copy(alpha = 0.2f),
        )
    } else {
        lightColorScheme(
            primary = gold, onPrimary = onGold, primaryContainer = goldDeep, onPrimaryContainer = onGold,
            secondary = win, onSecondary = feltDark, secondaryContainer = feltLight, onSecondaryContainer = cream,
            background = feltDark, onBackground = cream, surface = feltDark, onSurface = cream,
            surfaceVariant = felt, onSurfaceVariant = cream.copy(alpha = 0.75f),
            surfaceContainerLowest = feltDark, surfaceContainerLow = felt, surfaceContainer = felt,
            surfaceContainerHigh = feltLight, surfaceContainerHighest = feltLight,
            error = lose, onError = onError, errorContainer = loserRowA, onErrorContainer = cream,
            outline = cream.copy(alpha = 0.4f), outlineVariant = cream.copy(alpha = 0.2f),
        )
    }
}

@Composable
fun PayUpTheme(theme: AppTheme = AppTheme.ORIGINAL, content: @Composable () -> Unit) {
    val palette = theme.palette()
    CompositionLocalProvider(LocalPayUp provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}
