package com.samsungmodes.poc.restriction

import com.samsungmodes.poc.model.ModeOperationResult
import com.samsungmodes.poc.model.RestrictionProfile
import com.samsungmodes.poc.model.RestrictionState
import com.samsungmodes.poc.samsung.SamsungModeController

/**
 * Restriction controller implementation that delegates to [SamsungModeController]
 * to trigger Samsung's native "Restrict app usage" Mode action.
 */
class SamsungModesRestrictionController(
    private val modeController: SamsungModeController
) : RestrictionController {

    private var activeProfile: RestrictionProfile? = null

    override suspend fun enable(profile: RestrictionProfile): RestrictionState {
        if (!modeController.isSupported()) {
            return RestrictionState.Error("SamsungModeController is not supported on this device.")
        }

        val result = modeController.startMode(profile.samsungModeUuid)
        return when (result) {
            is ModeOperationResult.Success -> {
                activeProfile = profile
                RestrictionState.Active(
                    profileId = profile.id,
                    verified = result.verified
                )
            }
            is ModeOperationResult.PermissionDenied -> {
                RestrictionState.Error("Permission denied: ${result.reason}")
            }
            is ModeOperationResult.InvocationFailed -> {
                RestrictionState.Error("Invocation failed: ${result.reason}")
            }
            is ModeOperationResult.NotSupported -> {
                RestrictionState.Error("Not supported: ${result.reason}")
            }
            is ModeOperationResult.VerificationFailed -> {
                RestrictionState.Error("Verification failed: ${result.reason}")
            }
        }
    }

    override suspend fun disable(profile: RestrictionProfile): RestrictionState {
        val result = modeController.stopMode(profile.samsungModeUuid)
        return when (result) {
            is ModeOperationResult.Success -> {
                if (activeProfile?.id == profile.id) {
                    activeProfile = null
                }
                RestrictionState.Inactive
            }
            is ModeOperationResult.PermissionDenied -> {
                RestrictionState.Error("Permission denied: ${result.reason}")
            }
            is ModeOperationResult.InvocationFailed -> {
                RestrictionState.Error("Invocation failed: ${result.reason}")
            }
            is ModeOperationResult.NotSupported -> {
                RestrictionState.Error("Not supported: ${result.reason}")
            }
            is ModeOperationResult.VerificationFailed -> {
                RestrictionState.Error("Verification failed: ${result.reason}")
            }
        }
    }

    override suspend fun currentState(): RestrictionState {
        val currentMode = modeController.getCurrentMode()
        return if (currentMode.isModeActive) {
            RestrictionState.Active(
                profileId = currentMode.activeModeUuid ?: activeProfile?.id ?: "unknown",
                verified = true
            )
        } else {
            RestrictionState.Inactive
        }
    }
}
