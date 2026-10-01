package com.raen.kisaracolorizer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raen.kisaracolorizer.data.model.ColorizationModelManager
import com.raen.kisaracolorizer.data.model.ModelVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelDownloadCard(
    modelManager: ColorizationModelManager,
    modifier: Modifier = Modifier,
    onModelReadyChanged: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeVariant by modelManager.activeVariant.collectAsState()
    val customName by modelManager.customModelName.collectAsState()
    val isDownloading by modelManager.isDownloading.collectAsState()
    val progress by modelManager.downloadProgress.collectAsState()
    val statusText by modelManager.downloadStatus.collectAsState()

    val isModelReady = modelManager.isModelReady()
    val modelFile = modelManager.getActiveModelFile()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val displayName = it.lastPathSegment ?: "external_model.onnx"
                val success = modelManager.importModelFromUri(it, displayName)
                if (success) {
                    onModelReadyChanged?.invoke()
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isModelReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isModelReady) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isModelReady) "Model Ready" else "Model Required",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isModelReady) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        )
                        if (modelFile != null) {
                            Text(
                                text = "${modelFile.name} (${modelFile.length() / (1024 * 1024)} MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "Please download or import a model to begin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (isModelReady) {
                    IconButton(
                        onClick = {
                            modelManager.deleteActiveModel()
                            onModelReadyChanged?.invoke()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete model",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Variant Selector Chips
            Text(
                text = "Model Variant",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModelVariant.entries.forEach { variant ->
                    FilterChip(
                        selected = activeVariant == variant,
                        onClick = {
                            modelManager.setVariant(variant)
                            onModelReadyChanged?.invoke()
                        },
                        label = {
                            Text(
                                text = when (variant) {
                                    ModelVariant.MANGA_COLORIZER_V2 -> "Manga V2 (~123MB)"
                                    ModelVariant.DEOLDIFY -> "DeOldify (~254MB)"
                                    ModelVariant.CUSTOM -> customName ?: "Custom .onnx"
                                },
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Download Progress or Action Buttons
            AnimatedVisibility(visible = isDownloading) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            AnimatedVisibility(visible = !isDownloading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (activeVariant != ModelVariant.CUSTOM) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        modelManager.downloadActiveModel()
                                        onModelReadyChanged?.invoke()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isModelReady) "Re-download Model" else "Download Model")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        },
                        modifier = if (activeVariant == ModelVariant.CUSTOM) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import .onnx")
                    }
                }
            }
        }
    }
}
