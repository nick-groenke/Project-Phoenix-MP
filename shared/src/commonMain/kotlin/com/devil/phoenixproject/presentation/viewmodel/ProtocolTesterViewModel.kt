package com.devil.phoenixproject.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.devil.phoenixproject.data.repository.BleRepository
import com.devil.phoenixproject.data.repository.ScannedDevice
import com.devil.phoenixproject.domain.model.ConnectionState
import com.devil.phoenixproject.util.BlePacketFactory
import com.devil.phoenixproject.util.ExerciseCyclePhase
import com.devil.phoenixproject.util.ExerciseCyclePhaseResult
import com.devil.phoenixproject.util.KmpUtils
import com.devil.phoenixproject.util.ProtocolTester
import com.devil.phoenixproject.util.ProtocolTester.TestConfig
import com.devil.phoenixproject.util.ProtocolTester.TestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Test mode options for protocol testing
 */
enum class TestMode(val displayName: String, val description: String) {
    QUICK("Quick Test", "3 common configurations (~1 min)"),
    RECOMMENDED("Recommended", "7 configurations covering major variants (~2 min)"),
    FULL("Full Test", "All 35 combinations (~10 min)"),
    EXERCISE_CYCLE("Exercise Cycle", "Full workout simulation test (~30 sec)")
}

/**
 * Overall test state
 */
enum class TestState {
    IDLE,
    SCANNING,
    CONNECTING,
    TESTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * ViewModel for Protocol Tester screen.
 * Manages BLE connection testing and diagnostics.
 */
class ProtocolTesterViewModel(
    private val bleRepository: BleRepository
) : ViewModel() {

    private val _testState = MutableStateFlow(TestState.IDLE)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    private val _selectedTestMode = MutableStateFlow(TestMode.RECOMMENDED)
    val selectedTestMode: StateFlow<TestMode> = _selectedTestMode.asStateFlow()

    private val _currentConfig = MutableStateFlow<TestConfig?>(null)
    val currentConfig: StateFlow<TestConfig?> = _currentConfig.asStateFlow()

    private val _testResults = MutableStateFlow<List<TestResult>>(emptyList())
    val testResults: StateFlow<List<TestResult>> = _testResults.asStateFlow()

    private val _exerciseCycleResults = MutableStateFlow<List<ExerciseCyclePhaseResult>>(emptyList())
    val exerciseCycleResults: StateFlow<List<ExerciseCyclePhaseResult>> = _exerciseCycleResults.asStateFlow()

    private val _currentPhase = MutableStateFlow<ExerciseCyclePhase?>(null)
    val currentPhase: StateFlow<ExerciseCyclePhase?> = _currentPhase.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready to start protocol test")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _scannedDevice = MutableStateFlow<ScannedDevice?>(null)
    val scannedDevice: StateFlow<ScannedDevice?> = _scannedDevice.asStateFlow()

    private var testJob: Job? = null

    fun selectTestMode(mode: TestMode) {
        _selectedTestMode.value = mode
    }

    fun startTest() {
        if (_testState.value == TestState.TESTING || _testState.value == TestState.SCANNING) {
            Logger.w { "Test already in progress" }
            return
        }

        testJob?.cancel()
        testJob = viewModelScope.launch {
            try {
                _testState.value = TestState.SCANNING
                _statusMessage.value = "Scanning for Vitruvian device..."
                _testResults.value = emptyList()
                _exerciseCycleResults.value = emptyList()
                _progress.value = 0f
                _errorMessage.value = null

                // Scan for device
                bleRepository.startScanning()

                val device = withTimeoutOrNull(10000L) {
                    bleRepository.scannedDevices
                        .filter { it.isNotEmpty() }
                        .first()
                        .first()
                }

                bleRepository.stopScanning()

                if (device == null) {
                    _testState.value = TestState.FAILED
                    _errorMessage.value = "No Vitruvian device found"
                    _statusMessage.value = "Scan failed - no device found"
                    return@launch
                }

                _scannedDevice.value = device
                _statusMessage.value = "Found: ${device.name}"

                when (_selectedTestMode.value) {
                    TestMode.EXERCISE_CYCLE -> runExerciseCycleTest(device)
                    else -> runProtocolTests(device)
                }

            } catch (e: Exception) {
                Logger.e(e) { "Protocol test failed" }
                _testState.value = TestState.FAILED
                _errorMessage.value = e.message ?: "Unknown error"
                _statusMessage.value = "Test failed: ${e.message}"
            } finally {
                bleRepository.stopScanning()
            }
        }
    }

    private suspend fun runProtocolTests(device: ScannedDevice) {
        val configs = when (_selectedTestMode.value) {
            TestMode.QUICK -> ProtocolTester.generateQuickTestConfigs()
            TestMode.RECOMMENDED -> ProtocolTester.generateRecommendedTestConfigs()
            TestMode.FULL -> ProtocolTester.generateAllTestConfigs()
            TestMode.EXERCISE_CYCLE -> return // Handled separately
        }

        _testState.value = TestState.TESTING
        val results = mutableListOf<TestResult>()

        configs.forEachIndexed { index, config ->
            if (_testState.value == TestState.CANCELLED) return

            _currentConfig.value = config
            _progress.value = (index.toFloat() / configs.size)
            _statusMessage.value = "Testing: ${config.protocol.displayName} + ${config.delay.displayName}"

            val result = runSingleProtocolTest(device, config)
            results.add(result)
            _testResults.value = results.toList()

            // Brief delay between tests
            delay(500)
        }

        _testState.value = TestState.COMPLETED
        _progress.value = 1f

        val successCount = results.count { it.success }
        _statusMessage.value = "Complete: $successCount/${results.size} passed"
        _currentConfig.value = null
    }

    private suspend fun runSingleProtocolTest(device: ScannedDevice, config: TestConfig): TestResult {
        var connectionTime = 0L
        var initTime = 0L

        try {
            // Connect
            val connectStart = KmpUtils.currentTimeMillis()
            bleRepository.connect(device)

            val connected = withTimeoutOrNull(config.timeout) {
                bleRepository.connectionState
                    .filter { it is ConnectionState.Connected }
                    .first()
            }

            if (connected == null) {
                return TestResult(
                    protocol = config.protocol,
                    delay = config.delay,
                    success = false,
                    connectionTimeMs = KmpUtils.currentTimeMillis() - connectStart,
                    initTimeMs = 0,
                    errorMessage = "Connection timeout"
                )
            }

            connectionTime = KmpUtils.currentTimeMillis() - connectStart

            // Apply delay
            if (config.delay.delayMs > 0) {
                delay(config.delay.delayMs)
            }

            // Send init based on protocol
            val initStart = KmpUtils.currentTimeMillis()
            when (config.protocol) {
                ProtocolTester.InitProtocol.NO_INIT -> {
                    // No init command
                }
                ProtocolTester.InitProtocol.INIT_0x0A_NO_WAIT -> {
                    bleRepository.sendWorkoutCommand(BlePacketFactory.createInitCommand())
                }
                ProtocolTester.InitProtocol.INIT_0x0A_WAIT_0x0B -> {
                    bleRepository.sendWorkoutCommand(BlePacketFactory.createInitCommand())
                    delay(2000) // Wait for response
                }
                ProtocolTester.InitProtocol.INIT_0x0A_PLUS_PRESET -> {
                    bleRepository.sendWorkoutCommand(BlePacketFactory.createInitCommand())
                    delay(100)
                    bleRepository.sendWorkoutCommand(BlePacketFactory.createInitPreset())
                }
                ProtocolTester.InitProtocol.INIT_DOUBLE_0x0A -> {
                    bleRepository.sendWorkoutCommand(BlePacketFactory.createInitCommand())
                    delay(500)
                    bleRepository.sendWorkoutCommand(BlePacketFactory.createInitCommand())
                }
            }
            initTime = KmpUtils.currentTimeMillis() - initStart

            // Disconnect
            bleRepository.disconnect()

            return TestResult(
                protocol = config.protocol,
                delay = config.delay,
                success = true,
                connectionTimeMs = connectionTime,
                initTimeMs = initTime
            )

        } catch (e: Exception) {
            bleRepository.disconnect()
            return TestResult(
                protocol = config.protocol,
                delay = config.delay,
                success = false,
                connectionTimeMs = connectionTime,
                initTimeMs = initTime,
                errorMessage = e.message
            )
        }
    }

    private suspend fun runExerciseCycleTest(device: ScannedDevice) {
        _testState.value = TestState.TESTING
        val results = mutableListOf<ExerciseCyclePhaseResult>()
        val phases = ExerciseCyclePhase.entries

        try {
            phases.forEachIndexed { index, phase ->
                if (_testState.value == TestState.CANCELLED) return

                _currentPhase.value = phase
                _progress.value = (index.toFloat() / phases.size)
                _statusMessage.value = "${phase.displayName}: ${phase.description}"

                val result = runExerciseCyclePhase(device, phase)
                results.add(result)
                _exerciseCycleResults.value = results.toList()

                if (!result.success && phase != ExerciseCyclePhase.CLEANUP) {
                    // Non-cleanup failure - abort
                    _testState.value = TestState.FAILED
                    _errorMessage.value = "Failed at ${phase.displayName}: ${result.errorMessage}"
                    return
                }
            }

            _testState.value = TestState.COMPLETED
            _progress.value = 1f
            _statusMessage.value = "Exercise cycle test completed successfully"

        } catch (e: Exception) {
            _testState.value = TestState.FAILED
            _errorMessage.value = e.message
            _statusMessage.value = "Exercise cycle failed: ${e.message}"
        } finally {
            _currentPhase.value = null
            bleRepository.disconnect()
        }
    }

    private suspend fun runExerciseCyclePhase(
        device: ScannedDevice,
        phase: ExerciseCyclePhase
    ): ExerciseCyclePhaseResult {
        val startTime = KmpUtils.currentTimeMillis()

        try {
            when (phase) {
                ExerciseCyclePhase.SCAN -> {
                    // Already scanned
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = 0,
                        notes = "Device: ${device.name}"
                    )
                }
                ExerciseCyclePhase.CONNECT -> {
                    bleRepository.connect(device)
                    val connected = withTimeoutOrNull(10000L) {
                        bleRepository.connectionState
                            .filter { it is ConnectionState.Connected }
                            .first()
                    }
                    if (connected == null) {
                        return ExerciseCyclePhaseResult(
                            phase = phase,
                            success = false,
                            durationMs = KmpUtils.currentTimeMillis() - startTime,
                            errorMessage = "Connection timeout"
                        )
                    }
                }
                ExerciseCyclePhase.INITIALIZE -> {
                    val cmd = BlePacketFactory.createInitCommand()
                    bleRepository.sendWorkoutCommand(cmd)
                    delay(500)
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = KmpUtils.currentTimeMillis() - startTime,
                        commandSent = cmd
                    )
                }
                ExerciseCyclePhase.CONFIGURE -> {
                    // Send workout config (Old School mode, 20kg, 10 reps)
                    val cmd = BlePacketFactory.createProgramParams(
                        com.devil.phoenixproject.domain.model.WorkoutParameters(
                            workoutType = com.devil.phoenixproject.domain.model.WorkoutType.Program(
                                com.devil.phoenixproject.domain.model.ProgramMode.OldSchool
                            ),
                            weightPerCableKg = 20f,
                            reps = 10
                        )
                    )
                    bleRepository.sendWorkoutCommand(cmd)
                    delay(100)
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = KmpUtils.currentTimeMillis() - startTime,
                        commandSent = cmd
                    )
                }
                ExerciseCyclePhase.START -> {
                    val cmd = BlePacketFactory.createStartCommand()
                    bleRepository.sendWorkoutCommand(cmd)
                    delay(100)
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = KmpUtils.currentTimeMillis() - startTime,
                        commandSent = cmd
                    )
                }
                ExerciseCyclePhase.WAIT -> {
                    // Hold active for test duration (5 seconds for testing)
                    delay(5000)
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = 5000,
                        notes = "Held active for 5 seconds"
                    )
                }
                ExerciseCyclePhase.STOP_PRIMARY -> {
                    val cmd = BlePacketFactory.createStopCommand()
                    bleRepository.sendWorkoutCommand(cmd)
                    delay(100)
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = KmpUtils.currentTimeMillis() - startTime,
                        commandSent = cmd
                    )
                }
                ExerciseCyclePhase.STOP_OFFICIAL -> {
                    val cmd = BlePacketFactory.createOfficialStopPacket()
                    bleRepository.sendWorkoutCommand(cmd)
                    delay(100)
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = KmpUtils.currentTimeMillis() - startTime,
                        commandSent = cmd
                    )
                }
                ExerciseCyclePhase.CLEANUP -> {
                    bleRepository.disconnect()
                    return ExerciseCyclePhaseResult(
                        phase = phase,
                        success = true,
                        durationMs = KmpUtils.currentTimeMillis() - startTime
                    )
                }
            }

            return ExerciseCyclePhaseResult(
                phase = phase,
                success = true,
                durationMs = KmpUtils.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            return ExerciseCyclePhaseResult(
                phase = phase,
                success = false,
                durationMs = KmpUtils.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        _testState.value = TestState.CANCELLED
        _statusMessage.value = "Test cancelled"
        viewModelScope.launch {
            bleRepository.stopScanning()
            bleRepository.disconnect()
        }
    }

    fun resetTest() {
        testJob?.cancel()
        _testState.value = TestState.IDLE
        _testResults.value = emptyList()
        _exerciseCycleResults.value = emptyList()
        _currentConfig.value = null
        _currentPhase.value = null
        _progress.value = 0f
        _statusMessage.value = "Ready to start protocol test"
        _errorMessage.value = null
        _scannedDevice.value = null
    }

    /**
     * Generate shareable test report
     */
    fun generateTestReport(): String = buildString {
        appendLine("=== Vitruvian Protocol Test Report ===")
        appendLine("Test Mode: ${_selectedTestMode.value.displayName}")
        appendLine("Device: ${_scannedDevice.value?.name ?: "Unknown"}")
        appendLine("Status: ${_testState.value}")
        appendLine()

        if (_testResults.value.isNotEmpty()) {
            appendLine("Protocol Test Results:")
            appendLine("-".repeat(50))
            _testResults.value.forEach { result ->
                appendLine(result.toFormattedString())
            }
            appendLine()
            val successCount = _testResults.value.count { it.success }
            appendLine("Summary: $successCount/${_testResults.value.size} passed")
        }

        if (_exerciseCycleResults.value.isNotEmpty()) {
            appendLine("Exercise Cycle Results:")
            appendLine("-".repeat(50))
            _exerciseCycleResults.value.forEach { result ->
                val status = if (result.success) "OK" else "FAIL"
                appendLine("[$status] ${result.phase.displayName}: ${result.durationMs}ms")
                result.errorMessage?.let { appendLine("       Error: $it") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }
}
