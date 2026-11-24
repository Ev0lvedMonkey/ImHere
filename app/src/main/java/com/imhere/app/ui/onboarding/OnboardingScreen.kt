package com.imhere.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.imhere.app.R

/**
 * Shown once, before the main screen of a fresh install.
 *
 * It explains what the app is and is not, and lets the user grant every runtime
 * permission in one place. Continuing without the permissions is allowed: the
 * main screen keeps asking on the SOS button, exactly as before.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var grants by remember { mutableStateOf(readGrants(context)) }
    var requestAttempted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requestAttempted = true
        grants = readGrants(context)
    }

    LifecycleResumeEffect(Unit) {
        grants = readGrants(context)
        onPauseOrDispose { }
    }

    val groups = permissionGroups()
    // Optional permissions must not hold the user on this screen: the primary
    // button becomes "Continue" as soon as the required ones are granted.
    val missingRequired = groups
        .filterNot { it.optional }
        .flatMap { it.permissions }
        .filterNot { grants[it] == true }
        .distinct()
    val missingAll = groups
        .flatMap { it.permissions }
        .filterNot { grants[it] == true }
        .distinct()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            DisclaimerCard()

            Text(
                text = stringResource(R.string.onboarding_permissions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            groups.forEach { group ->
                PermissionCard(
                    group = group,
                    grants = grants,
                    onRequest = {
                        permissionLauncher.launch(
                            group.permissions
                                .filterNot { grants[it] == true }
                                .toTypedArray(),
                        )
                    },
                )
            }

            if (requestAttempted && missingRequired.isNotEmpty()) {
                SettingsHint()
            }

            Text(
                text = stringResource(R.string.onboarding_later_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    if (missingRequired.isEmpty()) {
                        onFinish()
                    } else {
                        permissionLauncher.launch(missingAll.toTypedArray())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (missingRequired.isEmpty()) {
                            R.string.onboarding_continue
                        } else {
                            R.string.onboarding_grant_all
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (missingRequired.isNotEmpty()) {
                TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_continue_without))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_disclaimer_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.onboarding_disclaimer_text),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    group: PermissionGroup,
    grants: Map<String, Boolean>,
    onRequest: () -> Unit,
) {
    val granted = group.permissions.all { grants[it] == true }
    val approximate = group.permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
        !granted &&
        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onRequest),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = granted, onCheckedChange = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(group.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(group.textRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val status = when {
                    granted -> R.string.onboarding_permission_granted
                    approximate -> R.string.onboarding_location_approximate
                    group.optional -> R.string.onboarding_optional
                    else -> R.string.onboarding_permission_denied
                }
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        granted -> MaterialTheme.colorScheme.primary
                        group.optional -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
            if (!granted) {
                TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.main_grant_permissions))
                }
            }
        }
    }
}

@Composable
private fun SettingsHint() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_settings_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.permission_open_settings))
            }
        }
    }
}

private class PermissionGroup(
    val permissions: List<String>,
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int,
    val optional: Boolean = false,
)

private fun permissionGroups(): List<PermissionGroup> = buildList {
    add(
        PermissionGroup(
            permissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            titleRes = R.string.onboarding_location_title,
            textRes = R.string.onboarding_location_text,
        ),
    )
    add(
        PermissionGroup(
            permissions = listOf(Manifest.permission.SEND_SMS),
            titleRes = R.string.onboarding_sms_title,
            textRes = R.string.onboarding_sms_text,
        ),
    )
    add(
        PermissionGroup(
            permissions = listOf(Manifest.permission.READ_PHONE_STATE),
            titleRes = R.string.onboarding_phone_title,
            textRes = R.string.onboarding_phone_text,
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            PermissionGroup(
                permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                titleRes = R.string.onboarding_notifications_title,
                textRes = R.string.onboarding_notifications_text,
                optional = true,
            ),
        )
    }
    add(
        PermissionGroup(
            permissions = listOf(Manifest.permission.READ_CONTACTS),
            titleRes = R.string.onboarding_contacts_title,
            textRes = R.string.onboarding_contacts_text,
            optional = true,
        ),
    )
}

private fun readGrants(context: Context): Map<String, Boolean> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.associateWith { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
