package com.payup

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.payup.platform.AndroidBackupFiles
import com.payup.platform.AndroidDateFormatter
import com.payup.platform.AndroidShareService
import com.payup.platform.LocalPlatform
import com.payup.platform.PlatformServices
import com.payup.ui.theme.PayUpTheme
import com.payup.ui.theme.palette

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    PayUpNavHost()
                }
            }
        }
    }
}
