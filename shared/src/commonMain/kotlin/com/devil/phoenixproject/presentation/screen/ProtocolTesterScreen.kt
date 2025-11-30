package com.devil.phoenixproject.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devil.phoenixproject.presentation.viewmodel.ProtocolTesterViewModel
import com.devil.phoenixproject.presentation.viewmodel.TestMode
import com.devil.phoenixproject.presentation.viewmodel.TestState
import com.devil.phoenixproject.ui.theme.Spacing
import com.devil.phoenixproject.util.ExerciseCyclePhaseResult
import com.devil.phoenixproject.util.ProtocolTester

/**
 * Protocol Tester Screen - BLE diagnostics and connection testing UI.
 * Allows users to test different initialization protocols to diagnose connection issues.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolTesterScreen(
    viewModel: ProtocolTesterViewModel,
    onNavigateBack: () -> Unit
) {
    val testState by viewModel.testState.collectAsState()
    val selectedMode by viewModel.selectedTestMode.collectAsState()
    val currentConfig by viewModel.currentConfig.collectAsState()
    val currentPhase by viewModel.currentPhase.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val exerciseCycleResults by viewModel.exerciseCycleResults.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scannedDevice by viewModel.scannedDevice.collectAsState()

    val isRunning = testState == TestState.SCANNING ||
            testState == TestState.CONNECTING ||
            testState == TestState.TESTING

    // Export dialog state
    var showExportDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protocol Tester") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (testResults.isNotEmpty() || exerciseCycleResults.isNotEmpty()) {
                        IconButton(onClick = {
                            exportContent = viewModel.generateTestReport()
                            showExportDialog = true
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Results")
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Export Dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Test Report") },
                text = {
                    Column {
                        Text(
                            text = "Copy the report below to share:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = exportContent,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(Spacing.small)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Header info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.medium)
                    ) {
                        Text(
                            text = "BLE Connection Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))
                        Text(
                            text = "Test different initialization protocols to find the best configuration for your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Test Mode Selection
            item {
                Text(
                    text = "Test Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(TestMode.entries) { mode ->
                TestModeCard(
                    mode = mode,
                    isSelected = mode == selectedMode,
                    enabled = !isRunning,
                    onClick = { viewModel.selectTestMode(mode) }
                )
            }

            // Device info (when scanning/connected)
            item {
                AnimatedVisibility(
                    visible = scannedDevice != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    scannedDevice?.let { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Column {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "RSSI: ${device.rssi} dBm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progress section
            item {
                AnimatedVisibility(
                    visible = isRunning,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))

                        // Current test info
                        currentConfig?.let { config ->
                            Text(
                                text = "Testing: ${config.protocol.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        currentPhase?.let { phase ->
                            Text(
                                text = "Phase: ${phase.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Status message
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (testState) {
                            TestState.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                            TestState.FAILED -> MaterialTheme.colorScheme.errorContainer
                            TestState.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (testState) {
                                TestState.IDLE -> Icons.Default.Info
                                TestState.SCANNING -> Icons.Default.Search
                                TestState.CONNECTING, TestState.TESTING -> Icons.Default.Sync
                                TestState.COMPLETED -> Icons.Default.CheckCircle
                                TestState.FAILED -> Icons.Default.Error
                                TestState.CANCELLED -> Icons.Default.Cancel
                            },
                            contentDescription = null,
                            tint = when (testState) {
                                TestState.COMPLETED -> Color(0xFF4CAF50)
                                TestState.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Error message
            item {
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    errorMessage?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    if (isRunning) {
                        Button(
                            onClick = { viewModel.cancelTest() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text("Cancel")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startTest() },
                            modifier = Modifier.weight(1f),
                            enabled = testState == TestState.IDLE ||
                                    testState == TestState.COMPLETED ||
                                    testState == TestState.FAILED ||
                                    testState == TestState.CANCELLED
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text("Start Test")
                        }

                        if (testState != TestState.IDLE) {
                            OutlinedButton(
                                onClick = { viewModel.resetTest() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Protocol Test Results
            if (testResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Protocol Test Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = Spacing.medium)
                    )
                }

                items(testResults) { result ->
                    ProtocolResultCard(result = result)
                }

                // Summary
                item {
                    val successCount = testResults.count { it.success }
                    val bestResult = testResults.filter { it.success }.minByOrNull { it.totalTimeMs }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(Spacing.medium)) {
                            Text(
                                text = "Summary: $successCount/${testResults.size} passed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            bestResult?.let {
                                Spacer(modifier = Modifier.height(Spacing.small))
                                Text(
                                    text = "Best: ${it.protocol.displayName} + ${it.delay.displayName} (${it.totalTimeMs}ms)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Exercise Cycle Results
            if (exerciseCycleResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Exercise Cycle Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = Spacing.medium)
                    )
                }

                items(exerciseCycleResults) { result ->
                    ExerciseCycleResultCard(result = result)
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(Spacing.large))
            }
        }
    }
}

@Composable
private fun TestModeCard(
    mode: TestMode,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            null
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProtocolResultCard(result: ProtocolTester.TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${result.protocol.displayName} + ${result.delay.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (result.success) {
                    Text(
                        text = "Connect: ${result.connectionTimeMs}ms | Init: ${result.initTimeMs}ms | Total: ${result.totalTimeMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    result.errorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCycleResultCard(result: ExerciseCyclePhaseResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.phase.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.phase.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${result.durationMs}ms",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
