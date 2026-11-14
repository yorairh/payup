package com.payup

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.payup.platform.AndroidBackupFiles
import com.payup.platform.AndroidDateFormatter
import com.payup.platform.AndroidShareService
import com.payup.platform.LocalPlatform
import com.payup.platform.PlatformServices
import com.payup.ui.Route
import com.payup.ui.routeForLink
import com.payup.ui.theme.PayUpTheme
import com.payup.ui.theme.palette

class MainActivity : AppCompatActivity() {
    /** The screen a tapped game link asks for, until the navigation has taken it. */
    private var incomingRoute by mutableStateOf<Route?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a rotation the same launch intent is delivered again; only a fresh start should act on it.
        if (savedInstanceState == null) incomingRoute = routeOf(intent)
        val dependencies = (application as PayUpApp).dependencies
        val dates = AndroidDateFormatter()
        val platform = PlatformServices(dates, AndroidShareService(this, dates), AndroidBackupFiles(this))
        setContent {
            CompositionLocalProvider(LocalAppDependencies provides dependencies, LocalPlatform provides platform) {
                val theme by dependencies.themeStore.theme.collectAsStateWithLifecycle()
                val dark = theme.palette().dark
                // Status/navigation bar icons must flip with the theme (dark icons on the white theme).
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                }
                PayUpTheme(theme) {
                    PayUpNavHost(incomingRoute = incomingRoute, onIncomingHandled = { incomingRoute = null })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingRoute = routeOf(intent)
    }

    private fun routeOf(intent: Intent?): Route? =
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString?.let(::routeForLink) else null
}
