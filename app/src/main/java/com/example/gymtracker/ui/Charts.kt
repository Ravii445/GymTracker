package com.example.gymtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class ChartPoint(val x: Long, val y: Float)

@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Not enough data yet", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }.coerceAtLeast(1f)
    val xRange = (maxX - minX).coerceAtLeast(1L)

    Canvas(modifier = modifier.padding(8.dp)) {
        val w = size.width
        val h = size.height

        val offsets = points.map {
            Offset(
                x = ((it.x - minX).toFloat() / xRange) * w,
                y = h - (it.y / maxY) * h
            )
        }

        // grid lines
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 2f
            )
        }

        // filled area
        val path = Path().apply {
            moveTo(offsets.first().x, h)
            offsets.forEach { lineTo(it.x, it.y) }
            lineTo(offsets.last().x, h)
            close()
        }
        drawPath(path, color = color.copy(alpha = 0.15f))

        // line
        val linePath = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, color = color, style = Stroke(width = 5f))

        // points
        offsets.forEach { drawCircle(color = color, radius = 6f, center = it) }
    }
}
