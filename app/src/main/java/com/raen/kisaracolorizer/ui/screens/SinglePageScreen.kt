package com.raen.kisaracolorizer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.raen.kisaracolorizer.data.engine.ColorizeImageUtils
import com.raen.kisaracolorizer.data.logger.AppLogger
import com.raen.kisaracolorizer.data.service.ColorizationService
import com.raen.kisaracolorizer.ui.components.BeforeAfterSlider
import kotlinx.coroutines.launch

@Composable
fun SinglePageScreen(
    service: ColorizationService,
    onNavigateToModels: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Persistent state connected to ColorizationService (never lost on tab switch!)
    val originalBitmap by service.originalBitmap.collectAsState()
    val colorizedBitmap by service.colorizedBitmap.collectAsState()
    val pcReferenceBitmap by service.pcReferenceBitmap.collectAsState()
    val isProcessing by service.isProcessing.collectAsState()
    val progressStep by service.progressStep.collectAsState()
    val progressPercent by service.progressPercent.collectAsState()
    val errorMessage by service.errorMessage.collectAsState()
    val lastDiagnostics by service.lastDiagnostics.collectAsState()

    // 0: Original vs Mobile, 1: Mobile vs PC, 2: Original vs PC
    var comparisonMode by remember { mutableIntStateOf(0) }
    var showFullscreenPreview by remember { mutableStateOf(false) }

    val isModelReady = service.modelManager.isModelReady()

    // Original image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(it)
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    stream?.close()
                    if (bmp != null) {
                        service.setSelectedImage(it, bmp)
                    }
                } catch (e: Exception) {
                    AppLogger.error("Failed to load image from storage", e)
                    Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Real-time comparison metrics
    val comparisonMetrics = remember(colorizedBitmap, pcReferenceBitmap) {
        if (colorizedBitmap != null && pcReferenceBitmap != null) {
            ColorizeImageUtils.compareBitmaps(colorizedBitmap!!, pcReferenceBitmap!!)
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Model Missing Warning Banner
        if (!isModelReady) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onNavigateToModels() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model Not Ready",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "Tap to open Models tab and download Manga V2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Button(
                        onClick = onNavigateToModels,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text("Models", fontSize = 12.sp)
                    }
                }
            }
        }

        // Comparison Mode Chips (when PC reference is present and image is colorized)
        if (colorizedBitmap != null && pcReferenceBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = comparisonMode == 0,
                    onClick = { comparisonMode = 0 },
                    label = { Text("B&W vs Mobile") }
                )
                FilterChip(
                    selected = comparisonMode == 1,
                    onClick = { comparisonMode = 1 },
                    label = { Text("Mobile vs PC") }
                )
                FilterChip(
                    selected = comparisonMode == 2,
                    onClick = { comparisonMode = 2 },
                    label = { Text("B&W vs PC") }
                )
            }

            // PC vs Mobile Similarity Metrics Card
            comparisonMetrics?.let { metrics ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "PC Similarity: ${(metrics.similarityPercent * 10).toInt() / 10f}%",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "MAE: ${(metrics.mae * 10).toInt() / 10f} / 255 | Max diff: ${metrics.maxChannelDiff}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            text = "ΔR: ${(metrics.meanCh0Diff).toInt()} ΔG: ${(metrics.meanCh1Diff).toInt()} ΔB: ${(metrics.meanCh2Diff).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Main Image Area with Comparison Slider
        if (originalBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        (originalBitmap!!.width.toFloat() / originalBitmap!!.height.toFloat()).coerceIn(0.5f, 2.0f)
                    )
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                when {
                    colorizedBitmap != null && pcReferenceBitmap != null && comparisonMode == 1 -> {
                        // Mobile vs PC
                        BeforeAfterSlider(
                            beforeBitmap = colorizedBitmap!!,
                            afterBitmap = pcReferenceBitmap!!,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    colorizedBitmap != null && pcReferenceBitmap != null && comparisonMode == 2 -> {
                        // Original vs PC
                        BeforeAfterSlider(
                            beforeBitmap = originalBitmap!!,
                            afterBitmap = pcReferenceBitmap!!,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    colorizedBitmap != null -> {
                        // Original vs Mobile
                        BeforeAfterSlider(
                            beforeBitmap = originalBitmap!!,
                            afterBitmap = colorizedBitmap!!,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Image(
                            bitmap = originalBitmap!!.asImageBitmap(),
                            contentDescription = "Original Manga Page",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        )

                        if (isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Floating Fullscreen/Expand button at bottom-right corner of the image
                IconButton(
                    onClick = { showFullscreenPreview = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen Preview",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap to Pick Manga Page",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "JPEG, PNG, WebP supported",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Live Step-by-Step Progress Card
        AnimatedVisibility(visible = isProcessing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = progressStep.ifEmpty { "Executing colorization..." },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(progressPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }

        // Time & Diagnostics Badge when complete
        AnimatedVisibility(visible = colorizedBitmap != null && !isProcessing) {
            val diag = lastDiagnostics
            val durationSec = if (diag != null && diag.durationMs > 0) {
                String.format(java.util.Locale.US, "%.2f", diag.durationMs / 1000f)
            } else null
            if (durationSec != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "⏱ Finished in ${durationSec}s" + if (diag?.isNnapi == true) " • NNAPI Hardware Accelerated" else " • Multi-core CPU",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (originalBitmap != null) "Change" else "Pick Image")
            }

            if (originalBitmap != null) {
                if (isProcessing) {
                    OutlinedButton(
                        onClick = { service.cancelColorization() },
                        modifier = Modifier.weight(1.2f),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel")
                    }
                } else {
                    Button(
                        onClick = { service.startColorization() },
                        enabled = isModelReady,
                        modifier = Modifier.weight(1.3f),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (colorizedBitmap != null) "Re-Colorize" else "Colorize")
                    }
                }
            }
        }

        // Save, Share & AI Export Row
        AnimatedVisibility(visible = colorizedBitmap != null && !isProcessing) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val file = service.saveBitmapToCache(colorizedBitmap!!)
                                    AppLogger.success("Saved colorized page to ${file.absolutePath}")
                                    Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    AppLogger.error("Save failed", e)
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save PNG")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val file = service.saveBitmapToCache(colorizedBitmap!!)
                                    val contentUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Colorized Manga Page"))
                                } catch (e: Exception) {
                                    AppLogger.error("Share failed", e)
                                    Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }

                    // Fullscreen preview action button
                    OutlinedButton(
                        onClick = { showFullscreenPreview = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View", maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Fullscreen Interactive Image Viewer Dialog
    if (showFullscreenPreview) {
        val currentDisplayBitmap = colorizedBitmap ?: originalBitmap
        if (currentDisplayBitmap != null) {
            Dialog(
                onDismissRequest = { showFullscreenPreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = currentDisplayBitmap.asImageBitmap(),
                        contentDescription = "Fullscreen View",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )

                    IconButton(
                        onClick = { showFullscreenPreview = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { service.clearError() },
            title = { Text("Colorization Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { service.clearError() }) {
                    Text("OK")
                }
            },
        )
    }
}
