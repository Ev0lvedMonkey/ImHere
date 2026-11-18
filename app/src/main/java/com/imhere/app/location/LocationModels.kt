package com.imhere.app.location

/** A location fix that passed the accuracy and age checks at validation time. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val ageMillis: Long,
    val provider: String,
)

sealed interface LocationFailure {
    /** Neither fine nor coarse location access was granted. */
    data object PermissionDenied : LocationFailure

    /** Only approximate (coarse) access was granted, so the fix was too imprecise. */
    data object FineLocationDenied : LocationFailure

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
