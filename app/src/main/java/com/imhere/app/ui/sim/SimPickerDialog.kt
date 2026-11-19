package com.imhere.app.ui.sim

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imhere.app.R
import com.imhere.app.sms.SimInfo
import com.imhere.app.sms.SimState

/** "SIM 2 · MTS RUS", or "SIM 2" when the carrier name is unavailable. */
fun SimInfo.displayLabel(context: Context): String {
    val name = label?.takeIf { it.isNotBlank() }
    return if (name == null) {
        context.getString(R.string.sim_label_slot_only, slotIndex + 1)
    } else {
        context.getString(R.string.sim_label_with_name, slotIndex + 1, name)
    }
}

@Composable
fun SimPickerDialog(
    state: SimState,
    selectedId: Int?,
    onDismiss: () -> Unit,
    onSelect: (SimInfo) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val checkedId = selectedId ?: state.sims.singleOrNull()?.subscriptionId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sim_picker_title)) },
        text = {
            when {
                !state.permissionGranted -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sim_permission_rationale))
                        Text(
                            text = stringResource(R.string.sim_permission_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.sims.isEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sim_picker_empty))
                        Text(
                            text = stringResource(R.string.sim_picker_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRefresh) {
                            Text(stringResource(R.string.sim_picker_refresh))
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (selectedId != null && state.sims.none { it.subscriptionId == selectedId }) {
                            Text(
                                text = stringResource(R.string.sim_selected_unavailable),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(items = state.sims, key = { it.subscriptionId }) { sim ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(sim) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = sim.subscriptionId == checkedId,
                                        onClick = { onSelect(sim) },
                                    )
                                    Column(Modifier.padding(start = 4.dp)) {
                                        Text(
                                            text = sim.displayLabel(context),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.permissionGranted) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_done))
                }
            } else {
                TextButton(onClick = onRequestPermission) {
                    Text(stringResource(R.string.sim_permission_grant))
                }
            }
        },
        dismissButton = {
            Row {
                if (!state.permissionGranted) {
                    TextButton(onClick = onOpenAppSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}
