package dev.helm.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.helm.app.ui.theme.HelmBorder

@Composable
fun HelmProgressBar(
    progress: Float,           // 0f–1f
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(HelmBorder, RoundedCornerShape(height / 2))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color, RoundedCornerShape(height / 2))
        )
    }
}
