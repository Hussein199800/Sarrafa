package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.notifications.WirdiNotificationChannels
import com.example.ui.MoreSubScreen
import com.example.ui.WirdiTab
import com.example.ui.WirdiViewModel
import com.example.ui.nav.WirdiAppRoot
import com.example.ui.theme.WirdiTheme

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer by lazy {
        (application as? WirdiApplication)?.container ?: AppContainer(applicationContext)
    }

    private val viewModel: WirdiViewModel by viewModels {
        WirdiViewModel.provideFactory(appContainer, applicationContext)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val uiState by viewModel.uiState.collectAsState()
            val settings = uiState.persisted.settings

            val shouldKeepScreenOn =
                (uiState.isDhikrSessionActive && settings.sessionKeepScreenOn) ||
                    (uiState.selectedTab == WirdiTab.MORE &&
                        uiState.selectedMoreSubScreen == MoreSubScreen.TASBIH &&
                        settings.tasbihKeepScreenOn)

            androidx.compose.runtime.DisposableEffect(shouldKeepScreenOn) {
                if (shouldKeepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            WirdiTheme(
                palette = settings.themePalette,
                darkModeConfig = settings.darkModeConfig,
                dynamicColor = settings.useDynamicColor
            ) {
                WirdiAppRoot(
                    viewModel = viewModel,
                    notificationScheduler = appContainer.notificationScheduler,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(WirdiNotificationChannels.EXTRA_OPEN_CURRENT_WIRD, false)) {
            intent.removeExtra(WirdiNotificationChannels.EXTRA_OPEN_CURRENT_WIRD)
            viewModel.openCurrentRecommendedWird(asFocusSession = false)
            return
        }
        val alarmType = intent.getStringExtra(WirdiNotificationChannels.EXTRA_ALARM_TYPE)
        if (!alarmType.isNullOrBlank()) {
            intent.removeExtra(WirdiNotificationChannels.EXTRA_ALARM_TYPE)
            viewModel.handleNotificationDeepLink(alarmType)
        }
    }
}
