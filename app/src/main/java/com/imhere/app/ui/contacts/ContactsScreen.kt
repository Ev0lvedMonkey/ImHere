package com.imhere.app.ui.contacts

import android.Manifest
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imhere.app.AppGraph
import com.imhere.app.R
import com.imhere.app.data.model.Contact
import com.imhere.app.util.ContactReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ContactDraft(
    val id: String?,
    val name: String,
    val phone: String,
    val isSos: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    viewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var draft by remember { mutableStateOf<ContactDraft?>(null) }
    var deleteTarget by remember { mutableStateOf<Contact?>(null) }
    var pendingRead by remember { mutableStateOf<Uri?>(null) }

    val importFailed = stringResource(R.string.contact_import_failed)
    val importDenied = stringResource(R.string.contact_import_denied)
    val duplicateError = stringResource(R.string.contact_error_duplicate)
    val nameError = stringResource(R.string.contact_error_name_empty)
    val phoneError = stringResource(R.string.contact_error_phone_invalid)
    val pickLabel = stringResource(R.string.contacts_pick)

    fun openImported(uri: Uri) {
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { ContactReader.read(context, uri) }) {
                is ContactReader.ReadResult.Success -> {
                    val picked = result.picked
                    val normalized = AppGraph.phoneNumbers.normalize(picked.phone)
                    draft = ContactDraft(
                        id = null,
                        name = picked.name,
                        phone = normalized ?: picked.phone,
                        isSos = false,
                    )
                }

                ContactReader.ReadResult.PermissionDenied -> pendingRead = uri
                ContactReader.ReadResult.Unavailable -> snackbarHostState.showSnackbar(importFailed)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) openImported(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingRead
        pendingRead = null
        if (granted && uri != null) {
            openImported(uri)
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar(importDenied) }
        }
    }

    // The permission is requested only after the system picker returned a contact
    // that the provider refused to hand over without READ_CONTACTS.
    LaunchedEffect(pendingRead) {
        if (pendingRead != null) {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun startImport() {
        picker.launch(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { startImport() }) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = pickLabel,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    draft = ContactDraft(id = null, name = "", phone = "", isSos = false)
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.contacts_add)) },
            )
        },
    ) { padding ->
        if (contacts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.contacts_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.contacts_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { startImport() }) {
                    Text(stringResource(R.string.contacts_pick))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = contacts, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = {
                            draft = ContactDraft(
                                id = contact.id,
                                name = contact.name,
                                phone = contact.phoneE164,
                                isSos = contact.isSos,
                            )
                        },
                        onSosChange = { checked -> viewModel.setSos(contact.id, checked) },
                    )
                }
            }
        }
    }

    draft?.let { current ->
        ContactEditDialog(
            draft = current,
            onDismiss = { draft = null },
            onDelete = if (current.id != null) {
                {
                    draft = null
                    deleteTarget = contacts.firstOrNull { it.id == current.id }
                }
            } else {
                null
            },
            onSave = { name, phone, isSos ->
                when (val result = viewModel.validate(current.id, name, phone)) {
                    is ContactValidation.Valid -> {
                        viewModel.save(current.id, name, result.phoneE164, isSos)
                        draft = null
                        null
                    }

                    ContactValidation.NameEmpty -> nameError
                    ContactValidation.PhoneInvalid -> phoneError
                    ContactValidation.Duplicate -> duplicateError
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.contacts_delete_title)) },
            text = { Text(stringResource(R.string.contacts_delete_text, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.contacts_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    onClick: () -> Unit,
    onSosChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = contact.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = AppGraph.phoneNumbers.formatForDisplay(contact.phoneE164),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.contacts_sos_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Checkbox(checked = contact.isSos, onCheckedChange = onSosChange)
            }
        }
    }
}

@Composable
private fun ContactEditDialog(
    draft: ContactDraft,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (name: String, phone: String, isSos: Boolean) -> String?,
) {
    var name by remember { mutableStateOf(draft.name) }
    var phone by remember { mutableStateOf(draft.phone) }
    var isSos by remember { mutableStateOf(draft.isSos) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (draft.id == null) R.string.contact_edit_new else R.string.contact_edit_existing,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.contact_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; error = null },
                    label = { Text(stringResource(R.string.contact_phone)) },
                    placeholder = { Text(stringResource(R.string.contact_phone_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSos, onCheckedChange = { isSos = it })
                    Text(text = stringResource(R.string.contact_sos_checkbox))
                }
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onSave(name, phone, isSos) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.contacts_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}
