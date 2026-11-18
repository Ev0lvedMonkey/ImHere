package com.imhere.app.ui

import android.content.Context
import com.imhere.app.R
import com.imhere.app.location.LocationFailure

/**
 * Single place that turns a technical location failure into a message the user
 * can act on. Used by the SOS notification and by the diagnostics screen.
 */
fun LocationFailure.toUserMessage(context: Context): String = when (this) {
    LocationFailure.PermissionDenied -> context.getString(R.string.notif_failure_permission)
    LocationFailure.FineLocationDenied -> context.getString(R.string.notif_failure_fine_denied)
    LocationFailure.LocationDisabled -> context.getString(R.string.notif_failure_disabled)
    LocationFailure.Timeout -> context.getString(R.string.notif_failure_timeout)
    LocationFailure.NoFix -> context.getString(R.string.notif_failure_no_fix)
    LocationFailure.Stale -> context.getString(R.string.notif_failure_stale)
    is LocationFailure.LowAccuracy -> bestAccuracyMeters?.let { accuracy ->
        context.getString(R.string.notif_failure_low_accuracy, accuracy)
    } ?: context.getString(R.string.notif_failure_low_accuracy_unknown)
}
