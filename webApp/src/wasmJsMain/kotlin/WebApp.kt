import com.payup.ui.routeForLink
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import com.payup.resources.close
import com.payup.resources.install_hint_body
import com.payup.resources.install_hint_title
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentParameters
import com.payup.AppDependencies
import com.payup.LocalAppDependencies
import com.payup.PayUpNavHost
import com.payup.data.AppDatabase
import com.payup.data.GameRepository
import com.payup.platform.LocalPlatform
import com.payup.platform.PlatformServices
import com.payup.sync.ShareIds
import com.payup.resources.Res
import com.payup.resources.already_open_in_another_tab
import com.payup.resources.payup
import com.payup.ui.Route
import com.payup.ui.theme.PayUpTheme
import com.payup.web.resources.Res as WebRes
import com.payup.web.resources.noto_color_emoji
import com.payup.web.resources.noto_sans_arabic
import com.payup.web.resources.noto_sans_hebrew
import com.payup.web.resources.noto_sans_sc
import com.payup.web.resources.noto_sans_symbols2
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.preloadFont
import org.jetbrains.compose.resources.stringResource

/** Tells index.html the first real screen is up, so it can remove its loading screen. */
@JsFun("() => { if (window.payupReady) window.payupReady() }")
private external fun notifyReady()

/** Colours the page behind the app and the browser's own bars, and remembers it for the next start (see index.html). */
@JsFun(
    """(hex) => {
        document.documentElement.style.background = hex;
        const meta = document.querySelector('meta[name=theme-color]');
        if (meta) meta.setAttribute('content', hex);
        try { localStorage.setItem('themeColor', hex); } catch (e) {}
    }""",
)
private external fun applyThemeColor(hex: String)

/** The address path of the page, like "/g/abc"; a shared game link opens straight on its screen. */
@JsFun("() => location.pathname")
private external fun pagePath(): String

/** The part of the address after the #, like "#abc"; a host link keeps its secret key there. */
@JsFun("() => location.hash")
private external fun pageHash(): String

/** Takes the secret key out of the address bar (and so out of the browser history) once the app has read it. */
@JsFun("() => history.replaceState(null, '', location.pathname)")
private external fun clearPageHash()

/** The screen a link opened (a shared game link or a host link), or null for the plain app address. */
private fun linkRoute(): Route? {
    val path = pagePath()
    val route = routeForLink(path + pageHash())
    if (path.startsWith("/h/")) clearPageHash()
    return route
}

private val rightToLeftLanguages = setOf("he", "iw", "ar")

@Composable
fun WebApp() {
    // The browser does not tell Compose which way the text runs, so it is derived from the language here.
    val language = remember { browserLanguage().substringBefore('-').lowercase() }
    val direction = if (language in rightToLeftLanguages) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        val theme by WebSettings.themeStore.theme.collectAsState()
        PayUpTheme(theme) {
            // Safari colours its top and bottom bars from the page, so they follow the theme's background.
            val background = MaterialTheme.colorScheme.background
            LaunchedEffect(background) {
                applyThemeColor("#" + (background.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0'))
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Gates {
                    SingleTab { database ->
                        Providers(database) {
                            Box(Modifier.fillMaxSize()) {
                                PayUpNavHost(initialRoute = remember { linkRoute() }, bindToBrowser = { BrowserHistory(it) })
                                InstallHintBanner(Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** On iPhone, a closable note on how to add the app to the Home Screen, which also keeps the saved games safe. */
@Composable
private fun InstallHintBanner(modifier: Modifier) {
    var visible by remember { mutableStateOf(WebSettings.installHint.shouldShow()) }
    if (!visible) return
    Surface(
        modifier.padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.install_hint_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.install_hint_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = {
                    WebSettings.installHint.closed()
                    visible = false
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(Res.string.close)) }
        }
    }
}

/** Everything the screens expect from the platform, built once the database is open. */
@Composable
private fun Providers(database: AppDatabase, content: @Composable () -> Unit) {
    val fonts = LocalFontFamilyResolver.current
    val dependencies = remember(database) {
        AppDependencies(
            repository = GameRepository(database),
            themeStore = WebSettings.themeStore,
            currencyStore = WebSettings.currencyStore,
            appLanguage = WebSettings.language,
            appVersion = APP_VERSION,
            gameSync = WebGameSync(),
            shareIds = ShareIds(webRandomBytes),
        )
    }
    val platform = remember(fonts) {
        val dates = WebDateFormatter()
        PlatformServices(
            dates = dates,
            share = WebShareService { game, palette, rtl -> renderSharePng(game, palette, rtl, dates, fonts) },
            backupFiles = WebBackupFiles(),
        )
    }
    CompositionLocalProvider(LocalAppDependencies provides dependencies, LocalPlatform provides platform, content = content)
}

/** The browser's back and forward buttons, the swipe-back on iPhone and the address bar follow the screens. */
@Composable
private fun BrowserHistory(back: NavBackStack<NavKey>) {
    ChronologicalBrowserNavigation(
        backStack = back,
        saveKey = { key ->
            when (key) {
                is Route.Home -> buildBrowserHistoryFragment("home")
                is Route.Settings -> buildBrowserHistoryFragment("settings")
                is Route.Leaderboard -> buildBrowserHistoryFragment("leaderboard")
                is Route.Setup -> buildBrowserHistoryFragment("setup")
                is Route.History -> buildBrowserHistoryFragment("history")
                is Route.Friends -> buildBrowserHistoryFragment("friends")
                is Route.Deal -> buildBrowserHistoryFragment("deal", mapOf("id" to key.gameId.toString()))
                is Route.Table -> buildBrowserHistoryFragment("table", mapOf("id" to key.gameId.toString()))
                is Route.CashOut -> buildBrowserHistoryFragment("cashout", mapOf("id" to key.gameId.toString()))
                is Route.Result -> buildBrowserHistoryFragment("result", mapOf("id" to key.gameId.toString()))
                is Route.Watch -> buildBrowserHistoryFragment("watch", mapOf("id" to key.shareId))
                else -> null
            }
        },
        restoreKey = { fragment ->
            val id = getBrowserHistoryFragmentParameters(fragment)["id"]?.toLongOrNull()
            when (getBrowserHistoryFragmentName(fragment)) {
                "home" -> Route.Home
                "settings" -> Route.Settings
                "leaderboard" -> Route.Leaderboard
                "setup" -> Route.Setup
                "history" -> Route.History
                "friends" -> Route.Friends
                "deal" -> id?.let { Route.Deal(it) }
                "table" -> id?.let { Route.Table(it) }
                "cashout" -> id?.let { Route.CashOut(it) }
                "result" -> id?.let { Route.Result(it) }
                "watch" -> getBrowserHistoryFragmentParameters(fragment)["id"]?.let { Route.Watch(it) }
                else -> null
            }
        },
    )
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

/** Opens the database, unless another tab of PayUp already has it open. */
@Composable
private fun SingleTab(content: @Composable (AppDatabase) -> Unit) {
    var database by remember { mutableStateOf<AppDatabase?>(null) }
    var blocked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (acquireSingleTabLock()) database = openWebDatabase() else blocked = true
    }
    when {
        blocked -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.already_open_in_another_tab), style = MaterialTheme.typography.titleMedium)
        }
        database != null -> content(database!!)
    }
}
