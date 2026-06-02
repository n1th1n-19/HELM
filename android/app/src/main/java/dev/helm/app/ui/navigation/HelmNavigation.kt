package dev.helm.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.helm.app.data.websocket.ConnectionState
import dev.helm.app.ui.claude.ClaudeScreen
import dev.helm.app.ui.components.ConnectionBanner
import dev.helm.app.ui.development.DevelopmentScreen
import dev.helm.app.ui.git.GitScreen
import dev.helm.app.ui.overview.OverviewScreen
import dev.helm.app.ui.system.SystemScreen
import dev.helm.app.ui.theme.*

enum class HelmTab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Outlined.Dashboard),
    Development("Dev", Icons.Outlined.Code),
    Git("Git", Icons.Outlined.AccountTree),
    Claude("Claude", Icons.Outlined.AutoAwesome),
    System("System", Icons.Outlined.Memory),
}

@Composable
fun HelmNavigation(
    windowWidthSizeClass: WindowWidthSizeClass,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(HelmTab.Overview) }

    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(containerColor = HelmSurface) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))
                HelmTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = HelmBrand,
                            selectedTextColor = HelmBrand,
                            unselectedIconColor = HelmTextSecondary,
                            unselectedTextColor = HelmTextSecondary,
                            indicatorColor = HelmCard,
                        ),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            ConnectionBanner(connectionState = connectionState)
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    HelmTab.Overview    -> OverviewScreen(windowWidthSizeClass = windowWidthSizeClass)
                    HelmTab.Development -> DevelopmentScreen()
                    HelmTab.Git         -> GitScreen()
                    HelmTab.Claude      -> ClaudeScreen()
                    HelmTab.System      -> SystemScreen()
                }
            }
        }
    }
}
