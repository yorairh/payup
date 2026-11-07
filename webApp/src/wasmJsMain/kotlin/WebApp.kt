import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.payup.domain.AppTheme
import com.payup.domain.Currency
import com.payup.resources.Res
import com.payup.resources.payup
import com.payup.resources.players_count
import com.payup.ui.common.isRtl
import com.payup.ui.common.moneyText
import com.payup.ui.theme.PayUpTheme
import com.payup.web.resources.Res as WebRes
import com.payup.web.resources.noto_color_emoji
import com.payup.web.resources.noto_sans_arabic
import com.payup.web.resources.noto_sans_hebrew
import com.payup.web.resources.noto_sans_sc
import com.payup.web.resources.noto_sans_symbols2
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.preloadFont
import org.jetbrains.compose.resources.stringResource

/** Tells index.html the first real screen is up, so it can remove its loading screen. */
@JsFun("() => { if (window.payupReady) window.payupReady() }")
private external fun notifyReady()

/** The browser's first language, like "he" or "en-US". */
@JsFun("() => (navigator.languages && navigator.languages[0]) || navigator.language || 'en'")
private external fun browserLanguage(): String

private val rightToLeftLanguages = setOf("he", "iw", "ar")

@Composable
fun WebApp() {
    // The browser does not tell Compose which way the text runs, so it is derived from the language here.
    val language = remember { browserLanguage().substringBefore('-').lowercase() }
    val direction = if (language in rightToLeftLanguages) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        PayUpTheme(AppTheme.ORIGINAL) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Gates { Shell() }
            }
        }
    }
}

/**
 * Nothing is drawn until the fonts are registered and the strings are loaded. Otherwise Compose shows empty boxes for
 * Hebrew and Arabic until it downloads fonts from Google, and labels flash empty on web, where strings load late.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun Gates(content: @Composable () -> Unit) {
    val resolver = LocalFontFamilyResolver.current
    val files = listOf(
        preloadFont(WebRes.font.noto_sans_hebrew).value,
        preloadFont(WebRes.font.noto_sans_arabic).value,
        preloadFont(WebRes.font.noto_sans_sc).value,
        preloadFont(WebRes.font.noto_color_emoji).value,
        preloadFont(WebRes.font.noto_sans_symbols2).value,
    )
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(files.all { it != null }) {
        if (files.all { it != null }) {
            // One call per font: fonts given together are treated as variants of one family, and only one is used.
            files.forEach { resolver.preload(FontFamily(it!!)) }
            getString(Res.string.payup) // loads the strings file for the browser's language
            ready = true
        }
    }
    if (ready) {
        content()
        LaunchedEffect(Unit) { notifyReady() }
    }
}

/** A stand-in for the real screens, which arrive once the web database and platform pieces exist (roadmap 16 and 17). */
@Composable
private fun Shell() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.payup), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(pluralStringResource(Res.plurals.players_count, 3, 3), style = MaterialTheme.typography.titleLarge)
        Text(moneyText(5000, Currency.ILS, isRtl()), style = MaterialTheme.typography.headlineMedium)
        Text(moneyText(-1825, Currency.USD, isRtl()), style = MaterialTheme.typography.headlineMedium)
        // Letters and symbols the app draws, to see that none show as empty boxes.
        Text("גבמר · مرحبا · 你好 · Привет · é ñ ü", style = MaterialTheme.typography.titleMedium)
        Text("♠ ♥ ♣ ♦  👑 🪙 🌧️", style = MaterialTheme.typography.titleMedium)
        Text("Web version: the screens are coming", style = MaterialTheme.typography.bodyMedium)
    }
}
