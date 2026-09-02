package com.samsungmodes.poc.proximity

import com.samsungmodes.poc.ble.model.BleDeviceId
import com.samsungmodes.poc.proximity.model.ProximityProfile
import com.samsungmodes.poc.proximity.model.RssiDistributionMetrics
import com.samsungmodes.poc.proximity.model.ThresholdCalculationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orchestrator for the 2-step calibration process:
 * Step 1: Outside calibration (30s sample collection)
 * Step 2: Inside calibration (30s sample collection)
 * Step 3: Distribution analysis, threshold computation, and profile generation.
 */
class CalibrationEngine(
    private val coroutineScope: CoroutineScope
) {
    enum class Step {
        IDLE,
        RECORDING_OUTSIDE,
        OUTSIDE_COMPLETE,
        RECORDING_INSIDE,
        INSIDE_COMPLETE,
        CALIBRATION_READY
    }

    data class CalibrationState(
        val step: Step = Step.IDLE,
        val targetDeviceKey: String? = null,
        val targetDeviceName: String = "Smart Tag",
        val targetDeviceId: BleDeviceId? = null,
        val countdownSecondsRemaining: Int = 0,
        val totalDurationSeconds: Int = 30,
        val outsideSamples: List<Int> = emptyList(),
        val insideSamples: List<Int> = emptyList(),
        val outsideMetrics: RssiDistributionMetrics? = null,
        val insideMetrics: RssiDistributionMetrics? = null,
        val calculationResult: ThresholdCalculationResult? = null,
        val calibratedProfile: ProximityProfile? = null,
        val currentLiveRssi: Int? = null
    )

    private val _state = MutableStateFlow(CalibrationState())
    val state: StateFlow<CalibrationState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private val currentSamplesBuffer = mutableListOf<Int>()

    fun startOutsideCalibration(
        deviceKey: String,
        deviceName: String,
        targetDeviceId: BleDeviceId? = null,
        durationSec: Int = 30
    ) {
        countdownJob?.cancel()
        currentSamplesBuffer.clear()

        val resolvedDeviceId = targetDeviceId ?: BleDeviceId(
            primaryKey = deviceKey,
            deviceName = deviceName
        )

        _state.value = _state.value.copy(
            step = Step.RECORDING_OUTSIDE,
            targetDeviceKey = deviceKey,
            targetDeviceName = deviceName,
            targetDeviceId = resolvedDeviceId,
            countdownSecondsRemaining = durationSec,
            totalDurationSeconds = durationSec,
            outsideSamples = emptyList(),
            outsideMetrics = null,
            calculationResult = null
        )

        launchCountdown(durationSec) {
            val metrics = RssiDistributionMetrics.calculateFromSamples(currentSamplesBuffer, durationSec)
            _state.value = _state.value.copy(
                step = Step.OUTSIDE_COMPLETE,
                outsideSamples = currentSamplesBuffer.toList(),
                outsideMetrics = metrics
            )
        }
    }

    fun startInsideCalibration(durationSec: Int = 30) {
        countdownJob?.cancel()
        currentSamplesBuffer.clear()

        _state.value = _state.value.copy(
            step = Step.RECORDING_INSIDE,
            countdownSecondsRemaining = durationSec,
            totalDurationSeconds = durationSec,
            insideSamples = emptyList(),
            insideMetrics = null,
            calculationResult = null
        )

        launchCountdown(durationSec) {
            val insideMetrics = RssiDistributionMetrics.calculateFromSamples(currentSamplesBuffer, durationSec)
            val outsideMetrics = _state.value.outsideMetrics

            val calcResult = if (outsideMetrics != null) {
                ThresholdCalculator.calculate(inside = insideMetrics, outside = outsideMetrics)
            } else null

            val targetDeviceId = _state.value.targetDeviceId ?: BleDeviceId(
                primaryKey = _state.value.targetDeviceKey ?: "unknown",
                deviceName = _state.value.targetDeviceName
            )

            val profile = calcResult?.let {
                ProximityProfile(
                    profileName = "${_state.value.targetDeviceName} Proximity",
                    targetDeviceId = targetDeviceId,
                    targetDisplayName = _state.value.targetDeviceName,
                    insideMetrics = insideMetrics,
                    outsideMetrics = outsideMetrics,
                    enterThresholdRssi = it.suggestedEnterThreshold,
                    exitThresholdRssi = it.suggestedExitThreshold,
                    enterDurationSeconds = 5,
                    exitDurationSeconds = 10
                )
            }

            _state.value = _state.value.copy(
                step = Step.CALIBRATION_READY,
                insideSamples = currentSamplesBuffer.toList(),
                insideMetrics = insideMetrics,
                calculationResult = calcResult,
                calibratedProfile = profile
            )
        }
    }

    fun feedRssiSample(deviceKey: String, rssi: Int) {
        val target = _state.value.targetDeviceKey
        if (target != null && (deviceKey == target || deviceKey.contains(target) || target.contains(deviceKey))) {
            _state.value = _state.value.copy(currentLiveRssi = rssi)
            if (_state.value.step == Step.RECORDING_OUTSIDE || _state.value.step == Step.RECORDING_INSIDE) {
                currentSamplesBuffer.add(rssi)
                if (_state.value.step == Step.RECORDING_OUTSIDE) {
                    _state.value = _state.value.copy(outsideSamples = currentSamplesBuffer.toList())
                } else {
                    _state.value = _state.value.copy(insideSamples = currentSamplesBuffer.toList())
                }
            }
        }
    }

    fun cancelCalibration() {
        countdownJob?.cancel()
        currentSamplesBuffer.clear()
        _state.value = _state.value.copy(
            step = Step.IDLE,
            countdownSecondsRemaining = 0
        )
    }

    fun reset() {
        cancelCalibration()
        _state.value = CalibrationState()
    }

    private fun launchCountdown(durationSec: Int, onComplete: () -> Unit) {
        countdownJob = coroutineScope.launch {
            var remaining = durationSec
            while (isActive && remaining > 0) {
                _state.value = _state.value.copy(countdownSecondsRemaining = remaining)
                delay(1000L)
                remaining--
            }
            if (isActive) {
                _state.value = _state.value.copy(countdownSecondsRemaining = 0)
                onComplete()
            }
        }
    }
}
