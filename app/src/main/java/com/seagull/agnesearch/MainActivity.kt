package com.seagull.agnesearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.net.Http
import com.seagull.agnesearch.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Http.applyProxy(Store.settings.value.proxyUrl)

        setContent {
            val settings by Store.settings.collectAsState()
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            SideEffect {
                try {
                    val c = WindowCompat.getInsetsController(window, window.decorView)
                    c.isAppearanceLightStatusBars = !dark
                    c.isAppearanceLightNavigationBars = !dark
                } catch (e: Exception) {
                    // 个别 ROM 上拿不到 controller，忽略
                }
            }
            AppRoot()
        }
    }
}
