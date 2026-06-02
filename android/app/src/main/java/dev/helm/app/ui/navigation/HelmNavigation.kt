package dev.helm.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.helm.app.data.websocket.ConnectionState
import dev.helm.app.ui.components.ConnectionBanner
import dev.helm.app.ui.development.DevelopmentScreen
import dev.helm.app.ui.git.GitScreen
import dev.helm.app.ui.media.MediaScreen
import dev.helm.app.ui.overview.OverviewScreen
import dev.helm.app.ui.system.SystemScreen
import dev.helm.app.ui.terminal.TerminalScreen
import dev.helm.app.ui.theme.*

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

    Scaffold(
        modifier = modifier,
        containerColor = HelmBackground,
        bottomBar = {
            NavigationBar(containerColor = HelmSurface) {
                HelmTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
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
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
