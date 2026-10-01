package com.raen.kisaracolorizer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raen.kisaracolorizer.data.logger.AppLogger
import com.raen.kisaracolorizer.data.logger.LogLevel

@Composable
fun LogConsoleWindow(
    modifier: Modifier = Modifier,
) {
    val logs by AppLogger.logs.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val maxAvailableHeightDp = (configuration.screenHeightDp * 0.72f).coerceAtLeast(300f)

    // Dynamic draggable height (starts at 240dp, can slide up to maxAvailableHeightDp)
    var currentHeightDp by remember { mutableFloatStateOf(240f) }

    val activeHeight = when {
        isFullScreen -> maxAvailableHeightDp.dp
        else -> currentHeightDp.dp
    }

    // Auto-scroll to bottom on new logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = Color(0xFF141416),
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Draggable Pill Indicator & Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val deltaDp = -dragAmount / density
                            if (!isExpanded && deltaDp > 10) {
                                isExpanded = true
                            }
                            if (isExpanded) {
                                val next = (currentHeightDp + deltaDp).coerceIn(140f, maxAvailableHeightDp)
                                currentHeightDp = next
                                if (deltaDp < -35 && currentHeightDp <= 150f) {
                                    isExpanded = false
                                }
                            }
                        }
                    }
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF424242))
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Engine Logs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF28282B))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "${logs.size}",
                                fontSize = 9.sp,
                                color = Color(0xFFB0B0B0),
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        // Collapsed live snippet
                        if (!isExpanded && logs.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = logs.last().message,
                                fontSize = 10.sp,
                                color = Color(0xFF888888),
                                maxLines = 1,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExpanded) {
                            // Maximize / Restore full screen button
                            IconButton(
                                onClick = { isFullScreen = !isFullScreen },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle full size",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            // Copy logs
                            IconButton(
                                onClick = {
                                    val allText = logs.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("KisaraColorizer Logs", allText))
                                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy logs",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            // Clear logs
                            IconButton(
                                onClick = { AppLogger.clear() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = "Clear logs",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Expanded Console Body (slideable up to 72% screen!)
            AnimatedVisibility(visible = isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(activeHeight)
                        .background(Color(0xFF0C0C0D))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs recorded yet. Start colorizing to view trace.",
                            color = Color(0xFF555555),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(logs) { entry ->
                                LogEntryRow(entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: com.raen.kisaracolorizer.data.logger.LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.INFO -> Color(0xFFE0E0E0)
        LogLevel.AI_STEP -> Color(0xFFFFD54F)
        LogLevel.WARN -> Color(0xFFFFB74D)
        LogLevel.ERROR -> Color(0xFFEF5350)
        LogLevel.SUCCESS -> Color(0xFF00E676)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Top,
    ) {
        // Timestamp
        Text(
            text = entry.timestamp,
            color = Color(0xFF555555),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.width(6.dp))

        // Level tag
        Text(
            text = "[${entry.level.name.take(4)}]",
            color = levelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.width(6.dp))

        // Log message
        Text(
            text = entry.message,
            color = levelColor,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
