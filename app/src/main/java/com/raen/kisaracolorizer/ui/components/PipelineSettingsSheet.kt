package com.raen.kisaracolorizer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raen.kisaracolorizer.data.engine.ColorizeImageUtils
import com.raen.kisaracolorizer.data.service.ColorizationService
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineSettingsSheet(
    service: ColorizationService,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pipelineMode by service.pipelineMode.collectAsState()
    val intensity by service.intensity.collectAsState()
    val useNnapi by service.useNnapi.collectAsState()
    val tuningParams by service.tuningParams.collectAsState()
    val activePreset by service.activePresetName.collectAsState()

    var section1Expanded by remember { mutableStateOf(false) }
    var section2Expanded by remember { mutableStateOf(false) }
    var section3Expanded by remember { mutableStateOf(true) }

    val presets = listOf("Optimized", "Colorful", "Heavy Darkish", "Brighter", "Less Color", "V20", "Custom")

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row with Title and Free RAM Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Pipeline & Filter Tuning",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "15 Metrics + Presets + Master Intensity (up to 30x)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(
                    onClick = { service.freeMemory() },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Free RAM", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ========================================================
            // 1. MASTER INTENSITY SLIDER (UP TO 30X FOR ALL MODES!)
            // ========================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Master Color Intensity Boost",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "${(intensity * 10f).roundToInt() / 10f}x",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Slider(
                        value = intensity.coerceIn(0.5f, 30.0f),
                        onValueChange = { service.setIntensity(it) },
                        valueRange = 0.5f..30.0f,
                        steps = 295,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Range: 0.5x to 30.0x. Controls global color boost across all blend and tuning modes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. PIPELINE BLEND MODES
            // ==========================================
            Text(
                text = "Pipeline Blend Mode",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Mode 1: Tuned Preset Filter Pipeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = pipelineMode == ColorizeImageUtils.PipelineMode.TUNED_PRESET,
                    onClick = { service.setPipelineMode(ColorizeImageUtils.PipelineMode.TUNED_PRESET) }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Catmikku / Komikku Tuning Filter Pipeline",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Applies preset filters: skin warmth, blue suppression, gamma, inking & paper whitening.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Mode 2: Direct RGB Blend (Option B)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = pipelineMode == ColorizeImageUtils.PipelineMode.DIRECT_RGB_BLEND,
                    onClick = { service.setPipelineMode(ColorizeImageUtils.PipelineMode.DIRECT_RGB_BLEND) }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Direct Full-RGB Line-Art Blend (Option B)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Direct model RGB with line-art multiplication. Simple and natural.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Mode 3: LAB Splicing (Option A)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = pipelineMode == ColorizeImageUtils.PipelineMode.LAB_SPLICING,
                    onClick = { service.setPipelineMode(ColorizeImageUtils.PipelineMode.LAB_SPLICING) }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "CIE-LAB Chrominance Splicing (Option A)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Replaces luminance with original manga screentone. Best for DeOldify.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ========================================================
            // 3. PRESETS SELECTION CHIPS (JUST ABOVE FILTER METRICS!)
            // ========================================================
            Text(
                text = "Tuning Presets",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Tap a preset to instantly update all 15 metrics below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = activePreset.equals(preset, ignoreCase = true),
                        onClick = {
                            if (preset.equals("Custom", ignoreCase = true)) {
                                service.setPipelineMode(ColorizeImageUtils.PipelineMode.TUNED_PRESET)
                            } else {
                                service.setPipelineMode(ColorizeImageUtils.PipelineMode.TUNED_PRESET)
                                service.setPreset(preset)
                            }
                        },
                        label = { Text(preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // 4. ALL 15 FILTER TUNING METRICS / SLIDERS
            // ==========================================
            Text(
                text = "15 Tuning Filter Metrics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // SECTION 1: Character Skin & Flesh Tone (5 parameters)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { section1Expanded = !section1Expanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🌸 Section 1: Character Skin & Flesh Tone",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Icon(
                            imageVector = if (section1Expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }

                    AnimatedVisibility(visible = section1Expanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // 1. skinHue (0..360 deg)
                            MetricSlider(
                                label = "1. Target Skin Hue Angle (skinHue)",
                                valueText = "${tuningParams.skinHue}°",
                                value = tuningParams.skinHue.toFloat(),
                                range = 0f..360f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(skinHue = it.toInt())) },
                            )

                            // 2. skinSat (0..300%)
                            MetricSlider(
                                label = "2. Skin Warmth Saturation (skinSat)",
                                valueText = "${tuningParams.skinSat}%",
                                value = tuningParams.skinSat.toFloat(),
                                range = 0f..300f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(skinSat = it.toInt())) },
                            )

                            // 3. blueCap (0..150%)
                            MetricSlider(
                                label = "3. Anatomy Blue/Cyan Suppression (blueCap)",
                                valueText = if (tuningParams.blueCap == 100) "100% (Raw Off)" else "${tuningParams.blueCap}%",
                                value = tuningParams.blueCap.toFloat(),
                                range = 0f..150f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(blueCap = it.toInt())) },
                            )

                            // 4. redFlush (0.50x .. 3.00x)
                            MetricSlider(
                                label = "4. Red Flush Compensation (redFlush)",
                                valueText = "${(tuningParams.redFlush) / 100f}x",
                                value = tuningParams.redFlush.toFloat(),
                                range = 50f..300f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(redFlush = it.toInt())) },
                            )

                            // 5. skinMinLum (0..100%)
                            MetricSlider(
                                label = "5. Skin Shadow Depth Limit (skinMinLum)",
                                valueText = "${tuningParams.skinMinLum}%",
                                value = tuningParams.skinMinLum.toFloat(),
                                range = 0f..100f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(skinMinLum = it.toInt())) },
                            )
                        }
                    }
                }
            }

            // SECTION 2: Illumination, Paper & Contrast (5 parameters)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { section2Expanded = !section2Expanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "☀️ Section 2: Illumination, Paper & Contrast",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Icon(
                            imageVector = if (section2Expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }

                    AnimatedVisibility(visible = section2Expanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // 6. paperThresh (30..100%)
                            MetricSlider(
                                label = "6. Speech Bubble / Paper Whiten (paperThresh)",
                                valueText = if (tuningParams.paperThresh == 100) "100% (Natural)" else "${tuningParams.paperThresh}% (Whitened)",
                                value = tuningParams.paperThresh.toFloat(),
                                range = 30f..100f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(paperThresh = it.toInt())) },
                            )

                            // 7. paperFeather (0..50%)
                            MetricSlider(
                                label = "7. Paper Whiten Feathering (paperFeather)",
                                valueText = "${tuningParams.paperFeather}%",
                                value = tuningParams.paperFeather.toFloat(),
                                range = 0f..50f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(paperFeather = it.toInt())) },
                            )

                            // 8. gamma (10..350%)
                            MetricSlider(
                                label = "8. Midtone Brightness Lift (gamma)",
                                valueText = "${tuningParams.gamma}%",
                                value = tuningParams.gamma.toFloat(),
                                range = 10f..350f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(gamma = it.toInt())) },
                            )

                            // 9. contrast (-100..100%)
                            MetricSlider(
                                label = "9. Filmic S-Curve Contrast (contrast)",
                                valueText = "${if (tuningParams.contrast >= 0) "+" else ""}${tuningParams.contrast}%",
                                value = tuningParams.contrast.toFloat(),
                                range = -100f..100f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(contrast = it.toInt())) },
                            )

                            // 10. blackFloor (-100..100)
                            MetricSlider(
                                label = "10. Shadow Floor / Black Depth (blackFloor)",
                                valueText = "${if (tuningParams.blackFloor >= 0) "+" else ""}${tuningParams.blackFloor}",
                                value = tuningParams.blackFloor.toFloat(),
                                range = -100f..100f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(blackFloor = it.toInt())) },
                            )
                        }
                    }
                }
            }

            // SECTION 3: Line Art & Palette Vibrance (5 parameters)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { section3Expanded = !section3Expanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🎨 Section 3: Line Art & Palette Vibrance",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Icon(
                            imageVector = if (section3Expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }

                    AnimatedVisibility(visible = section3Expanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // 11. lineThresh (0..90%)
                            MetricSlider(
                                label = "11. Line Art Ink Deepness (lineThresh)",
                                valueText = "${tuningParams.lineThresh}%",
                                value = tuningParams.lineThresh.toFloat(),
                                range = 0f..90f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(lineThresh = it.toInt())) },
                            )

                            // 12. lineExp (0.10x .. 6.00x)
                            MetricSlider(
                                label = "12. Line Art Density Curve (lineExp)",
                                valueText = "${(tuningParams.lineExp) / 100f}x",
                                value = tuningParams.lineExp.toFloat(),
                                range = 10f..600f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(lineExp = it.toInt())) },
                            )

                            // 13. satMul (0.00x .. 6.00x)
                            MetricSlider(
                                label = "13. Overall Color Vibrance (satMul)",
                                valueText = "${(tuningParams.satMul) / 100f}x",
                                value = tuningParams.satMul.toFloat(),
                                range = 0f..600f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(satMul = it.toInt())) },
                            )

                            // 14. colorMix (0..300%)
                            MetricSlider(
                                label = "14. Midtone Color Retention (colorMix)",
                                valueText = "${tuningParams.colorMix}%",
                                value = tuningParams.colorMix.toFloat(),
                                range = 0f..300f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(colorMix = it.toInt())) },
                            )

                            // 15. clarity (0..400%)
                            MetricSlider(
                                label = "15. Unsharp Texture Clarity (clarity)",
                                valueText = "${tuningParams.clarity}%",
                                value = tuningParams.clarity.toFloat(),
                                range = 0f..400f,
                                onValueChange = { service.updateTuningParams(tuningParams.copy(clarity = it.toInt())) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Hardware Acceleration Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NNAPI Hardware Acceleration",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "Leverages NPU/GPU drivers. Auto-probed with 576x576 sanity tensor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useNnapi,
                        onCheckedChange = { service.setUseNnapi(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ========================================================
            // 4. SPEED & HARDWARE PROFILES (4 LEVELS)
            // ========================================================
            val speedTier by service.speedTier.collectAsState()
            val perfConfig = ColorizeImageUtils.PerformanceConfig.fromLevel(speedTier)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Speed Profile",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        Text(
                            text = perfConfig.tier.title.uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${perfConfig.description} • Optimal ARM NEON multi-core speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ColorizeImageUtils.PerformanceTier.entries.forEach { tier ->
                            val isSelected = speedTier == tier.level
                            FilterChip(
                                selected = isSelected,
                                onClick = { service.setSpeedTier(tier.level) },
                                label = {
                                    Text(
                                        text = tier.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = valueText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
