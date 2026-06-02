package dev.helm.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HelmColorScheme = darkColorScheme(
    primary = HelmCpu,
    onPrimary = HelmBackground,
    primaryContainer = HelmCard,
    secondary = HelmRam,
    onSecondary = HelmBackground,
    secondaryContainer = HelmCard,
    tertiary = HelmMusic,
    onTertiary = HelmBackground,
    background = HelmBackground,
    onBackground = HelmTextPrimary,
    surface = HelmSurface,
    onSurface = HelmTextPrimary,
    surfaceVariant = HelmCard,
    onSurfaceVariant = HelmTextSecondary,
    outline = HelmBorder,
    outlineVariant = HelmBorder,
    error = HelmError,
    onError = HelmBackground,
)

@Composable
fun HelmTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = HelmBackground.toArgb()
            window.navigationBarColor = HelmBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HelmColorScheme,
        typography = HelmTypography,
        content = content,
    )
}
