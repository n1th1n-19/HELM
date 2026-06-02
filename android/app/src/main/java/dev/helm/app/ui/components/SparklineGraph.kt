package dev.helm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

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
        val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val step = w / (values.size - 1)

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / max) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2f))
    }
}
