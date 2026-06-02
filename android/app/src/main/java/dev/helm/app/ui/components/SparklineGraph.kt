package dev.helm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Draws a sparkline graph of [values] (0f–100f) using [color].
 * Typically used to show CPU history over the last 60s.
 */
@Composable
fun SparklineGraph(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        // Normalize against 100 (not local max) so 50% CPU renders at mid-height.
        val step = w / (values.size - 1)

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v.coerceIn(0f, 100f) / 100f) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}
