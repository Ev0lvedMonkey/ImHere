package com.imhere.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imhere.app.BuildConfig
import com.imhere.app.R
import com.imhere.app.data.model.AppState
import com.imhere.app.data.model.Contact
import com.imhere.app.sms.SimAvailability
import com.imhere.app.sms.resolveSimAvailability
import com.imhere.app.ui.sim.SimPickerDialog
import com.imhere.app.ui.sim.displayLabel
import com.imhere.app.util.MessageTemplate

private const val LANGUAGE_SYSTEM = ""

/** Present only in the debug variant; the release APK has no such activity. */
private const val PROTOTYPE_ACTIVITY = "com.imhere.app.ui.PrototypeActivity"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val simState by viewModel.simState.collectAsStateWithLifecycle()
    var showSimDialog by remember { mutableStateOf(false) }

    val simPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshSims() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshSims()
        onPauseOrDispose { }
    }

    val simAvailability = remember(simState, state.selectedSubscriptionId) {
        resolveSimAvailability(state.selectedSubscriptionId, simState)
    }
    val simSubtitle = when (simAvailability) {
        is SimAvailability.Ready -> simAvailability.sim.displayLabel(context)
        SimAvailability.PermissionRequired,
        is SimAvailability.NotSelected -> stringResource(R.string.settings_sim_none)

        is SimAvailability.Unavailable -> stringResource(R.string.settings_sim_unavailable)
        SimAvailability.NoSims -> stringResource(R.string.main_sim_no_sim)
    }

    val defaultSosTemplate = stringResource(R.string.sos_template_default)
    val defaultPlainTemplate = stringResource(R.string.plain_template_default)

    var editSos by remember { mutableStateOf(false) }
    var editPlain by remember { mutableStateOf(false) }
    var editRecipients by remember { mutableStateOf(false) }
    var editLanguage by remember { mutableStateOf(false) }

    val languageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val languageLabel = when (languageTag) {
        "" -> stringResource(R.string.settings_language_system)
        "ru" -> stringResource(R.string.settings_language_ru)
        "en" -> stringResource(R.string.settings_language_en)
        else -> languageTag
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_messages)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_sos_template),
                    subtitle = null,
                    onClick = { editSos = true },
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_plain_template),
                    subtitle = null,
                    onClick = { editPlain = true },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_recipients)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_sos_contacts),
                    subtitle = stringResource(
                        R.string.settings_sos_contacts_count,
                        state.sosContacts.size,
                    ),
                    onClick = { editRecipients = true },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_sim)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_sim),
                    subtitle = simSubtitle,
                    onClick = { showSimDialog = true },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.settings_sim_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = languageLabel,
                    onClick = { editLanguage = true },
                )
            }
            if (BuildConfig.DEBUG) {
                item {
                    SettingsRow(
                        title = stringResource(R.string.settings_diagnostics),
                        subtitle = stringResource(R.string.settings_diagnostics_hint),
                        onClick = {
                            context.startActivity(
                                Intent().setClassName(context, PROTOTYPE_ACTIVITY),
                            )
                        },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_version,
                            BuildConfig.VERSION_NAME,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (editSos) {
        TemplateEditorDialog(
            title = stringResource(R.string.settings_sos_template),
            initial = state.sosTemplate ?: defaultSosTemplate,
            defaultTemplate = defaultSosTemplate,
            onDismiss = { editSos = false },
            onSave = { text ->
                viewModel.setSosTemplate(text)
                editSos = false
            },
        )
    }

    if (editPlain) {
        TemplateEditorDialog(
            title = stringResource(R.string.settings_plain_template),
            initial = state.plainTemplate ?: defaultPlainTemplate,
            defaultTemplate = defaultPlainTemplate,
            onDismiss = { editPlain = false },
            onSave = { text ->
                viewModel.setPlainTemplate(text)
                editPlain = false
            },
        )
    }

    if (editRecipients) {
        SosContactsDialog(
            state = state,
            onDismiss = { editRecipients = false },
            onToggle = { contact, checked -> viewModel.setSos(contact.id, checked) },
        )
    }

    if (editLanguage) {
        LanguageDialog(
            current = languageTag,
            onDismiss = { editLanguage = false },
            onSelect = { tag ->
                val locales = if (tag == LANGUAGE_SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
                editLanguage = false
            },
        )
    }

    if (showSimDialog) {
        SimPickerDialog(
            state = simState,
            selectedId = state.selectedSubscriptionId,
            onDismiss = { showSimDialog = false },
            onSelect = { sim ->
                viewModel.selectSim(sim.subscriptionId)
                showSimDialog = false
            },
            onRequestPermission = {
                simPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
            onRefresh = { viewModel.refreshSims() },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TemplateEditorDialog(
    title: String,
    initial: String,
    defaultTemplate: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    var error by remember { mutableStateOf(false) }
    val previewEmpty = stringResource(R.string.template_preview_empty)

    val sampleValues = mapOf(
        MessageTemplate.ADDRESS to stringResource(R.string.template_sample_address),
        MessageTemplate.LATITUDE to stringResource(R.string.template_sample_latitude),
        MessageTemplate.LONGITUDE to stringResource(R.string.template_sample_longitude),
        MessageTemplate.MAP_LINK to stringResource(R.string.template_sample_map_link),
        MessageTemplate.TIME to stringResource(R.string.template_sample_time),
    )
    val preview = remember(value.text) {
        MessageTemplate.render(value.text, sampleValues)
    }
    val previewText = preview.ifBlank { previewEmpty }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    isError = error,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MessageTemplate.variables.forEach { variable ->
                        AssistChip(
                            onClick = {
                                val insertion = "{$variable}"
                                val start = value.selection.start.coerceIn(0, value.text.length)
                                val end = value.selection.end.coerceIn(start, value.text.length)
                                value = TextFieldValue(
                                    text = value.text.replaceRange(start, end, insertion),
                                    selection = androidx.compose.ui.text.TextRange(start + insertion.length),
                                )
                                error = false
                            },
                            label = { Text("{$variable}") },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.template_preview),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error) {
                    Text(
                        text = stringResource(R.string.template_error_empty),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.text.isBlank()) {
                        error = true
                    } else {
                        onSave(value.text)
                    }
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { value = TextFieldValue(defaultTemplate); error = false }) {
                    Text(stringResource(R.string.template_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}

@Composable
private fun SosContactsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onToggle: (Contact, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sos_contacts)) },
        text = {
            if (state.contacts.isEmpty()) {
                Text(stringResource(R.string.settings_no_contacts))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(items = state.contacts, key = { it.id }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(contact, !contact.isSos) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = contact.isSos,
                                onCheckedChange = { checked -> onToggle(contact, checked) },
                            )
                            Column {
                                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = contact.phoneE164,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        },
    )
}

@Composable
private fun LanguageDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        LANGUAGE_SYSTEM to stringResource(R.string.settings_language_system),
        "ru" to stringResource(R.string.settings_language_ru),
        "en" to stringResource(R.string.settings_language_en),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                options.forEach { (tag, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tag) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == tag, onClick = { onSelect(tag) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
