package com.imhere.app.location

/** A location fix that passed the accuracy/age checks at validation time. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val ageMillis: Long,
    val provider: String,
)

sealed interface LocationFailure {
    data object PermissionDenied : LocationFailure
    data object LocationDisabled : LocationFailure
    data object Timeout : LocationFailure
    data object NoFix : LocationFailure
    data object Stale : LocationFailure
    data class LowAccuracy(val bestAccuracyMeters: Float?) : LocationFailure
}

sealed interface LocationOutcome {
    data class Success(val fix: LocationFix) : LocationOutcome
    data class Failure(val reason: LocationFailure) : LocationOutcome
}
