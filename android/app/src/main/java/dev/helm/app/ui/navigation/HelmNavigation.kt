package dev.helm.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import dev.helm.app.data.websocket.ConnectionState
import dev.helm.app.ui.components.ConnectionBanner
import dev.helm.app.ui.development.DevelopmentScreen
import dev.helm.app.ui.git.GitScreen
import dev.helm.app.ui.media.MediaScreen
import dev.helm.app.ui.overview.OverviewScreen
import dev.helm.app.ui.system.SystemScreen
import dev.helm.app.ui.terminal.TerminalScreen
import dev.helm.app.ui.theme.*
import kotlinx.coroutines.delay

enum class HelmTab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Outlined.Dashboard),
    Development("Dev", Icons.Outlined.Code),
    Git("Git", Icons.Outlined.AccountTree),
    Media("Media", Icons.Outlined.MusicNote),
    Terminal("Actions", Icons.Outlined.Terminal),
    System("System", Icons.Outlined.Memory),
}

@Composable
fun HelmNavigation(
    windowWidthSizeClass: WindowWidthSizeClass,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(HelmTab.Overview) }
    var navVisible by remember { mutableStateOf(true) }

    // Auto-hide after 3s of inactivity
    LaunchedEffect(navVisible) {
        if (navVisible) {
            delay(3000)
            navVisible = false
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = HelmBackground,
        bottomBar = {
            AnimatedVisibility(
                visible = navVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = HelmSurface) {
                    HelmTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                navVisible = true
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HelmCpu,
                                selectedTextColor = HelmCpu,
                                unselectedIconColor = HelmTextSecondary,
                                unselectedTextColor = HelmTextSecondary,
                                indicatorColor = HelmCard,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Tap anywhere to show nav bar
                .pointerInput(Unit) {
                    detectTapGestures { navVisible = true }
                },
        ) {
            ConnectionBanner(connectionState = connectionState)
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    HelmTab.Overview    -> OverviewScreen(windowWidthSizeClass = windowWidthSizeClass)
                    HelmTab.Development -> DevelopmentScreen()
                    HelmTab.Git         -> GitScreen()
                    HelmTab.Media       -> MediaScreen()
                    HelmTab.Terminal    -> TerminalScreen()
                    HelmTab.System      -> SystemScreen()
                }
            }
        }
    }
}
