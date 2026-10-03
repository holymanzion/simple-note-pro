package com.holymanzion.simplenotepro.ui.labels

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.holymanzion.simplenotepro.data.Label
import com.holymanzion.simplenotepro.data.NoteRepository
import com.holymanzion.simplenotepro.ui.components.ConfirmDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(repository: NoteRepository, onBack: () -> Unit) {
    val labels by repository.labels.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Label?>(null) }

    fun create() {
        val name = newName.trim()
        if (name.isEmpty()) return
        scope.launch {
            if (labels.any { it.name.equals(name, ignoreCase = true) }) {
                snackbar.showSnackbar("A label named “$name” already exists")
            } else {
                repository.createLabel(name)
                newName = ""
            }
        }
    }

    fun saveRename(id: Long) {
        scope.launch {
            if (repository.renameLabel(id, editText)) editing = null
            else snackbar.showSnackbar("Choose a different, non-empty name")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit labels") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            item {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(50) },
                    placeholder = { Text("Create new label") },
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = ::create, enabled = newName.isNotBlank()) {
                            Icon(Icons.Outlined.Check, contentDescription = "Create")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                HorizontalDivider()
            }
            if (labels.isEmpty()) {
                item {
                    Text(
                        "Labels let you group notes by topic, project or anything you like. " +
                            "Create one above, then add it to notes from the editor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(labels, key = { it.id }) { label ->
                if (editing == label.id) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        IconButton(onClick = { editing = null }) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") }
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it.take(50) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { saveRename(label.id) }),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { saveRename(label.id) }) { Icon(Icons.Outlined.Check, contentDescription = "Save") }
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(label.name) },
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = label.id; editText = label.name }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { toDelete = label }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    toDelete?.let { label ->
        ConfirmDialog(
            title = "Delete label?",
            message = "“${label.name}” will be removed from all notes. The notes themselves are kept.",
            confirmLabel = "Delete",
            onConfirm = { scope.launch { repository.deleteLabel(label.id) } },
            onDismiss = { toDelete = null },
        )
    }
}
