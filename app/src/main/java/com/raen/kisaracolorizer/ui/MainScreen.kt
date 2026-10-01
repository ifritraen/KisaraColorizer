package com.raen.kisaracolorizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raen.kisaracolorizer.data.service.BatchColorizer
import com.raen.kisaracolorizer.data.service.ColorizationService
import com.raen.kisaracolorizer.ui.components.DiagnosticInspectorSheet
import com.raen.kisaracolorizer.ui.components.LogConsoleWindow
import com.raen.kisaracolorizer.ui.components.ModelDownloadCard
import com.raen.kisaracolorizer.ui.components.PipelineSettingsSheet
import com.raen.kisaracolorizer.ui.components.ResourceBar
import com.raen.kisaracolorizer.ui.screens.BatchFolderScreen
import com.raen.kisaracolorizer.ui.screens.SinglePageScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    service: ColorizationService,
    batchColorizer: BatchColorizer,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDiagSheet by remember { mutableStateOf(false) }

    val modelManager = service.modelManager
    val isDownloading by modelManager.isDownloading.collectAsState()
    var isModelReady by remember { mutableStateOf(modelManager.isModelReady()) }
    val lastDiag by service.lastDiagnostics.collectAsState()

    Scaffold(
        // TopBar removed as requested by user!
        topBar = {},
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // 1. Ultra-slim Live Resource Monitor (CPU %, GPU %, RAM MB) - located directly above engine logs!
                ResourceBar(
                    onFreeRamClick = { service.freeMemory() }
                )

                // 2. Engine Logs Console Window (drags / slides up to 72% of the screen)
                LogConsoleWindow()

                // 3. Ultra-slim Bottom Navigation Bar (No text labels, includes Diag & Filter/Tune buttons)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Tab 0: Single Page
                        IconButton(
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Single Page",
                                tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Tab 1: Batch Folder
                        IconButton(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderCopy,
                                contentDescription = "Batch Folder",
                                tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Tab 2: Models
                        IconButton(
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Models",
                                tint = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Diag button in the bottom bar
                        IconButton(
                            onClick = { showDiagSheet = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Diagnostics",
                                tint = if (lastDiag != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Filter / Tune button in the bottom bar
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Pipeline Filters & Tuning",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues),
        ) {
            // Tab Content
            when (selectedTab) {
                0 -> SinglePageScreen(
                    service = service,
                    onNavigateToModels = { selectedTab = 2 },
                )
                1 -> BatchFolderScreen(
                    batchColorizer = batchColorizer,
                    isModelReady = isModelReady,
                )
                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        ModelDownloadCard(
                            modelManager = modelManager,
                            onModelReadyChanged = {
                                isModelReady = modelManager.isModelReady()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        PipelineSettingsSheet(
            service = service,
            onDismissRequest = { showSettingsSheet = false },
        )
    }

    if (showDiagSheet) {
        DiagnosticInspectorSheet(
            diagnostics = lastDiag,
            onDismissRequest = { showDiagSheet = false },
        )
    }
}
