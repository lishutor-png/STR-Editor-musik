package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    var draggingHandle by remember { mutableStateOf<String?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val playheadColor = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .testTag("waveform_view")
    ) {
        val totalWidth = constraints.maxWidth.toFloat()
        val totalHeight = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(waveform, durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0 && totalWidth > 0) {
                            val tappedMs = ((offset.x / totalWidth) * durationMs).toLong()
                            onSeek(tappedMs.coerceIn(0L, durationMs))
                        }
                    }
                }
                .pointerInput(waveform, durationMs, startMs, endMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (durationMs <= 0 || totalWidth <= 0) return@detectDragGestures
                            val startX = (startMs.toFloat() / durationMs.toFloat()) * totalWidth
                            val endX = (endMs.toFloat() / durationMs.toFloat()) * totalWidth

                            val touchThreshold = 48.dp.toPx()
                            val distStart = abs(offset.x - startX)
                            val distEnd = abs(offset.x - endX)

                            draggingHandle = when {
                                distStart < touchThreshold && distStart <= distEnd -> "start"
                                distEnd < touchThreshold -> "end"
                                offset.x in startX..endX -> "middle"
                                else -> "playhead"
                            }
                        },
                        onDragEnd = {
                            draggingHandle = null
                        },
                        onDragCancel = {
                            draggingHandle = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (durationMs <= 0 || totalWidth <= 0) return@detectDragGestures

                            val deltaMs = ((dragAmount.x / totalWidth) * durationMs).toLong()

                            when (draggingHandle) {
                                "start" -> {
                                    val newStart = (startMs + deltaMs).coerceIn(0L, endMs - 500L)
                                    onRangeChange(newStart, endMs)
                                }
                                "end" -> {
                                    val newEnd = (endMs + deltaMs).coerceIn(startMs + 500L, durationMs)
                                    onRangeChange(startMs, newEnd)
                                }
                                "middle" -> {
                                    val length = endMs - startMs
                                    var newStart = startMs + deltaMs
                                    var newEnd = endMs + deltaMs
                                    if (newStart < 0) {
                                        newStart = 0
                                        newEnd = length
                                    } else if (newEnd > durationMs) {
                                        newEnd = durationMs
                                        newStart = durationMs - length
                                    }
                                    onRangeChange(newStart, newEnd)
                                }
                                "playhead" -> {
                                    val newPos = ((change.position.x / totalWidth) * durationMs).toLong()
                                    onSeek(newPos.coerceIn(0L, durationMs))
                                }
                            }
                        }
                    )
                }
        ) {
            val safeDuration = max(1L, durationMs)
            val startX = (startMs.toFloat() / safeDuration.toFloat()) * size.width
            val endX = (endMs.toFloat() / safeDuration.toFloat()) * size.width
            val playheadX = (currentPlayheadMs.toFloat() / safeDuration.toFloat()) * size.width

            // 1. Dimmed unselected regions
            drawRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(0f, 0f),
                size = Size(startX, size.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(endX, 0f),
                size = Size(size.width - endX, size.height)
            )

            // 2. Waveform bars
            if (waveform.isNotEmpty()) {
                val barWidth = size.width / waveform.size.toFloat()
                val gap = max(1f, barWidth * 0.25f)
                val effectiveBarWidth = max(2f, barWidth - gap)

                for (i in waveform.indices) {
                    val x = i * barWidth
                    val amp = waveform[i]
                    val barHeight = max(6f, amp * size.height * 0.85f)
                    val y = (size.height - barHeight) / 2f

                    val isInsideSelection = x in (startX - barWidth)..(endX + 2f)
                    val barColor = if (isInsideSelection) {
                        secondaryColor
                    } else {
                        surfaceColor.copy(alpha = 0.65f)
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(effectiveBarWidth, barHeight),
                        cornerRadius = CornerRadius(effectiveBarWidth / 2f, effectiveBarWidth / 2f)
                    )
                }
            }

            // 3. Selection border
            drawRect(
                color = primaryColor,
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // 4. Fade in curve
            if (fadeInMs > 0) {
                val fadeWidth = (fadeInMs.toFloat() / safeDuration.toFloat()) * size.width
                val fadePath = Path().apply {
                    moveTo(startX, size.height)
                    lineTo(startX + fadeWidth, 0f)
                    lineTo(startX, 0f)
                    close()
                }
                drawPath(fadePath, color = Color.White.copy(alpha = 0.15f))
            }

            // 5. Fade out curve
            if (fadeOutMs > 0) {
                val fadeWidth = (fadeOutMs.toFloat() / safeDuration.toFloat()) * size.width
                val fadePath = Path().apply {
                    moveTo(endX, size.height)
                    lineTo(endX - fadeWidth, 0f)
                    lineTo(endX, 0f)
                    close()
                }
                drawPath(fadePath, color = Color.White.copy(alpha = 0.15f))
            }

            // 6. Start Handle (Left)
            val handleWidth = 8.dp.toPx()
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(startX - (handleWidth / 2f), 0f),
                size = Size(handleWidth, size.height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // 7. End Handle (Right)
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(endX - (handleWidth / 2f), 0f),
                size = Size(handleWidth, size.height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // 8. Playhead cursor
            if (currentPlayheadMs in 0..safeDuration) {
                drawLine(
                    color = playheadColor,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
                drawCircle(
                    color = playheadColor,
                    radius = 6.dp.toPx(),
                    center = Offset(playheadX, 6.dp.toPx())
                )
            }
        }
    }
}
