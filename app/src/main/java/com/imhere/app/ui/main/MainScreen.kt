package com.imhere.app.ui.main

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imhere.app.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val availability by viewModel.location.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val stubMessage = stringResource(R.string.main_stage_stub)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.main_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LocationStatusCard(
                availability = availability,
                onGrantPermissions = { permissionLauncher.launch(requiredPermissions()) },
                onOpenSystemSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )

            Button(
                onClick = { scope.launch { snackbarHostState.showSnackbar(stubMessage) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 170.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.main_sos),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.main_sos_hint),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            OutlinedButton(
                onClick = { scope.launch { snackbarHostState.showSnackbar(stubMessage) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.main_send_location),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.main_sos_contacts_title),
                    value = if (state.sosContacts.isEmpty()) {
                        stringResource(R.string.main_sos_contacts_none)
                    } else {
                        stringResource(R.string.main_sos_contacts_count, state.sosContacts.size)
                    },
                    onClick = onOpenContacts,
                )
                InfoCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Place,
                    title = stringResource(R.string.main_sim_title),
                    value = stringResource(R.string.main_sim_none),
                    onClick = null,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.main_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationStatusCard(
    availability: LocationAvailability,
    onGrantPermissions: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val icon = Icons.Filled.Place
    val title: String
    val supporting: String?
    when (availability) {
        LocationAvailability.AVAILABLE -> {
            title = stringResource(R.string.main_location_available)
            supporting = null
        }

        LocationAvailability.DISABLED -> {
            title = stringResource(R.string.main_location_disabled)
            supporting = stringResource(R.string.main_location_disabled_hint)
        }

        LocationAvailability.APPROXIMATE_ONLY -> {
            title = stringResource(R.string.main_location_approximate)
            supporting = stringResource(R.string.main_location_approximate_hint)
        }

        LocationAvailability.NO_PERMISSION -> {
            title = stringResource(R.string.main_location_no_permission)
            supporting = stringResource(R.string.main_location_no_permission_hint)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (availability == LocationAvailability.AVAILABLE) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (availability) {
                LocationAvailability.NO_PERMISSION -> TextButton(onClick = onGrantPermissions) {
                    Text(stringResource(R.string.main_grant_permissions))
                }

                LocationAvailability.DISABLED -> TextButton(onClick = onOpenSystemSettings) {
                    Text(stringResource(R.string.main_open_location_settings))
                }

                LocationAvailability.APPROXIMATE_ONLY -> TextButton(onClick = onOpenAppSettings) {
                    Text(stringResource(R.string.main_open_app_settings))
                }

                LocationAvailability.AVAILABLE -> Unit
            }
        }
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)?,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Card(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
    ) {
        content()
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
