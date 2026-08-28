package com.samsungmodes.poc.model

/**
 * Structured operation results for Samsung Mode controller invocations.
 * Strictly separates successful intent dispatch (INVOCATION SUCCESS)
 * from verified device state change (MODE STATE VERIFIED).
 */
sealed class ModeOperationResult {
    data class Success(
        val verified: Boolean,
        val details: String
    ) : ModeOperationResult()

    data class NotSupported(
        val reason: String
    ) : ModeOperationResult()

    data class PermissionDenied(
        val reason: String
    ) : ModeOperationResult()

    data class InvocationFailed(
        val reason: String,
        val exception: Throwable? = null
    ) : ModeOperationResult()

    data class VerificationFailed(
        val reason: String
    ) : ModeOperationResult()
}
