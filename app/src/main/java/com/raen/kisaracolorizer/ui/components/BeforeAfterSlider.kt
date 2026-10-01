package com.raen.kisaracolorizer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Interactive split-view slider comparing original B&W and Colorized images.
 */
@Composable
fun BeforeAfterSlider(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val splitX = widthPx * splitFraction

        val beforeImage = remember(beforeBitmap) { beforeBitmap.asImageBitmap() }
        val afterImage = remember(afterBitmap) { afterBitmap.asImageBitmap() }

        // Canvas with custom clipping to render Before on left and After on right
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (widthPx > 0f) {
                            splitFraction = (splitFraction + dragAmount.x / widthPx).coerceIn(0.01f, 0.99f)
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val currentSplitX = canvasWidth * splitFraction

            // 1. Draw Colorized image (right side / background)
            drawImage(
                image = afterImage,
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                dstSize = androidx.compose.ui.unit.IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
            )

            // 2. Draw Original B&W image clipped to [0 .. currentSplitX]
            val leftClipPath = Path().apply {
                addRect(Rect(0f, 0f, currentSplitX, canvasHeight))
            }

            clipPath(leftClipPath) {
                drawImage(
                    image = beforeImage,
                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    dstSize = androidx.compose.ui.unit.IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
                )
            }

            // 3. Draw vertical divider line
            drawLine(
                color = Color.White,
                start = Offset(currentSplitX, 0f),
                end = Offset(currentSplitX, canvasHeight),
                strokeWidth = 3.dp.toPx(),
            )
        }

        // Circular draggable handle in center
        Surface(
            modifier = Modifier
                .offset { IntOffset(splitX.roundToInt() - 20.dp.roundToPx(), (heightPx / 2f - 20.dp.toPx()).roundToInt()) }
                .size(40.dp)
                .pointerInput(widthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (widthPx > 0f) {
                            splitFraction = (splitFraction + dragAmount.x / widthPx).coerceIn(0.01f, 0.99f)
                        }
                    }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Drag divider",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Top badges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = "Original B&W",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = "Colorized",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
