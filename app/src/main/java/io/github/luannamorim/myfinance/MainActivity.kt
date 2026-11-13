package io.github.luannamorim.myfinance

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.luannamorim.myfinance.core.format.Formats
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.navigation.AppViewModel
import io.github.luannamorim.myfinance.ui.navigation.FinanceNavHost
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Incremented whenever an intent asks to show the backup screen (backup failure notification). */
    private val openBackupRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val app: AppViewModel = hiltViewModel()
            val loaded by app.settings.collectAsStateWithLifecycle()
            val settings = loaded ?: AppSettings()
            val dark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val locale = LocalConfiguration.current.locales[0]
            val formats = remember(settings.numberFormat, settings.dateFormat, locale) { Formats.from(settings, locale) }
            FinanceTheme(settings.themeMode) {
                CompositionLocalProvider(LocalFormats provides formats) {
                    FinanceNavHost(openBackupRequest = openBackupRequest.intValue)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_BACKUP) openBackupRequest.intValue++
    }

    companion object {
        const val ACTION_OPEN_BACKUP = "io.github.luannamorim.myfinance.action.OPEN_BACKUP"
    }
}
