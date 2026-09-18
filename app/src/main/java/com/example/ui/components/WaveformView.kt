package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun WaveformView(
    waveform: FloatArray,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    currentPlayheadMs: Long,
    fadeInMs: Long = 0L,
    fadeOutMs: Long = 0L,
    onRangeChange: (start: Long, end: Long) -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingHandle by remember { mutableStateOf<String?>(null) } // "start", "end", or null

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val playheadColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .testTag("waveform_canvas_container")
            .pointerInput(waveform, durationMs, startMs, endMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        val touchRatio = (offset.x / size.width).coerceIn(0f, 1f)
                        val touchMs = (touchRatio * durationMs).toLong()
                        onSeek(touchMs)
                    }
                }
            }
            .pointerInput(waveform, durationMs, startMs, endMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (durationMs <= 0) return@detectDragGestures
                        val startX = (startMs.toFloat() / durationMs) * size.width
                        val endX = (endMs.toFloat() / durationMs) * size.width
                        val handleRadiusPx = 40.dp.toPx()

                        draggingHandle = when {
                            kotlin.math.abs(offset.x - startX) <= handleRadiusPx -> "start"
                            kotlin.math.abs(offset.x - endX) <= handleRadiusPx -> "end"
                            offset.x < startX -> "start"
                            offset.x > endX -> "end"
                            else -> {
                                val distToStart = kotlin.math.abs(offset.x - startX)
                                val distToEnd = kotlin.math.abs(offset.x - endX)
                                if (distToStart < distToEnd) "start" else "end"
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (durationMs <= 0) return@detectDragGestures
                        val touchRatio = (change.position.x / size.width).coerceIn(0f, 1f)
                        val newTimeMs = (touchRatio * durationMs).toLong()

                        when (draggingHandle) {
                            "start" -> {
                                val safeStart = newTimeMs.coerceIn(0L, endMs - 100L)
                                onRangeChange(safeStart, endMs)
                            }
                            "end" -> {
                                val safeEnd = newTimeMs.coerceIn(startMs + 100L, durationMs)
                                onRangeChange(startMs, safeEnd)
                            }
                        }
                    },
                    onDragEnd = {
                        draggingHandle = null
                    },
                    onDragCancel = {
                        draggingHandle = null
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("waveform_canvas")) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val bars = if (waveform.isNotEmpty()) waveform else FloatArray(100) { 0.1f }
            val barCount = bars.size
            val barSpacing = width / barCount
            val barWidth = (barSpacing * 0.65f).coerceAtLeast(1.5f)

            // Background canvas container
            drawRoundRect(
                color = surfaceColor,
                size = size,
                cornerRadius = CornerRadius(14.dp.toPx())
            )

            val safeDuration = if (durationMs > 0) durationMs else 1L
            val startX = (startMs.toFloat() / safeDuration) * width
            val endX = (endMs.toFloat() / safeDuration) * width
            val playheadX = (currentPlayheadMs.toFloat() / safeDuration) * width

            // 1. Draw Waveform Bars
            for (i in 0 until barCount) {
                val x = i * barSpacing + (barSpacing - barWidth) / 2f
                val barProgress = i.toFloat() / barCount
                val barTimeMs = (barProgress * safeDuration).toLong()

                val amp = bars[i]
                val barHeight = (amp * (height * 0.76f)).coerceAtLeast(4.dp.toPx())
                val top = centerY - barHeight / 2f

                val isSelected = barTimeMs in startMs..endMs
                val color = if (isSelected) primaryColor else onSurfaceColor.copy(alpha = 0.22f)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }

            // 2. Draw Dim Overlay outside selection
            if (startX > 0) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    topLeft = Offset(0f, 0f),
                    size = Size(startX, height)
                )
            }
            if (endX < width) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    topLeft = Offset(endX, 0f),
                    size = Size(width - endX, height)
                )
            }

            // 3. Draw Fade-In Triangle if active
            if (fadeInMs > 0 && endX > startX) {
                val fadeInWidth = ((fadeInMs.toFloat() / safeDuration) * width).coerceAtMost(endX - startX)
                val path = Path().apply {
                    moveTo(startX, height)
                    lineTo(startX + fadeInWidth, 0f)
                    lineTo(startX, 0f)
                    close()
                }
                drawPath(path, secondaryColor.copy(alpha = 0.25f))
            }

            // 4. Draw Fade-Out Triangle if active
            if (fadeOutMs > 0 && endX > startX) {
                val fadeOutWidth = ((fadeOutMs.toFloat() / safeDuration) * width).coerceAtMost(endX - startX)
                val path = Path().apply {
                    moveTo(endX - fadeOutWidth, 0f)
                    lineTo(endX, height)
                    lineTo(endX, 0f)
                    close()
                }
                drawPath(path, Color(0xFFF43F5E).copy(alpha = 0.25f))
            }

            // 5. Draw Start Marker & Handle
            drawLine(
                color = primaryColor,
                start = Offset(startX, 0f),
                end = Offset(startX, height),
                strokeWidth = 3.dp.toPx()
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(startX - 6.dp.toPx(), 0f),
                size = Size(12.dp.toPx(), 24.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(startX - 6.dp.toPx(), height - 24.dp.toPx()),
                size = Size(12.dp.toPx(), 24.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // 6. Draw End Marker & Handle
            drawLine(
                color = primaryColor,
                start = Offset(endX, 0f),
                end = Offset(endX, height),
                strokeWidth = 3.dp.toPx()
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(endX - 6.dp.toPx(), 0f),
                size = Size(12.dp.toPx(), 24.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(endX - 6.dp.toPx(), height - 24.dp.toPx()),
                size = Size(12.dp.toPx(), 24.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // 7. Draw Playhead (current playing position)
            if (playheadX in 0f..width) {
                drawLine(
                    color = playheadColor,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    color = playheadColor,
                    radius = 5.dp.toPx(),
                    center = Offset(playheadX, 10.dp.toPx())
                )
            }
        }
    }
}
