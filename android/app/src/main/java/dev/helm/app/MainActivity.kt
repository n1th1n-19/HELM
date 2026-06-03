package dev.helm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.helm.app.service.HelmForegroundService
import dev.helm.app.ui.MainViewModel
import dev.helm.app.ui.navigation.HelmNavigation
import dev.helm.app.ui.theme.HelmTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start foreground service (keeps connection alive when backgrounded)
        // Guard against redundant starts on config changes and activity recreations
        if (!HelmForegroundService.isRunning) {
            startForegroundService(Intent(this, HelmForegroundService::class.java))
        }

        // Enable kiosk mode
        KioskManager.enable(this)

        setContent {
            HelmTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                HelmNavigation(
                    windowWidthSizeClass = windowSizeClass.widthSizeClass,
                    connectionState = connectionState,
                )
            }
        }
    }
}
