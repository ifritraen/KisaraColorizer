package com.raen.kisaracolorizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raen.kisaracolorizer.data.engine.ColorizeDiagnostics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticInspectorSheet(
    diagnostics: ColorizeDiagnostics?,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Colorizer Diagnostics",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Real-time ONNX Runtime output & channel statistics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (diagnostics == null) {
                Text(
                    text = "No inference run yet. Process an image to see diagnostics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DiagRow("Inference Time", "${diagnostics.durationMs} ms")
                        DiagRow("Execution Provider", if (diagnostics.isNnapi) "NNAPI (Hardware)" else "CPU (ARM64)")
                        DiagRow("Input Shape", diagnostics.inputDim)
                        DiagRow("Output Shape", diagnostics.outputDim)
                        DiagRow("Buffer Size", "${diagnostics.bufSize} floats (${diagnostics.plane} per plane)")
                        DiagRow("Pipeline Mode", diagnostics.pipelineMode.name)
                        DiagRow("Saturation Intensity", "${diagnostics.intensity}x")

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Channel Means & Chrominance Separation",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        DiagRow("Channel 0 Mean", String.format("%.4f", diagnostics.ch0Mean))
                        DiagRow("Channel 1 Mean", String.format("%.4f", diagnostics.ch1Mean))
                        DiagRow("Channel 2 Mean", String.format("%.4f", diagnostics.ch2Mean))
                        DiagRow(
                            name = "ch0 - ch1 Separation",
                            value = String.format("%.4f", diagnostics.chDiff),
                            highlight = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagRow(name: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
