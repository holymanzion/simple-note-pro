package com.holymanzion.simplenotepro.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private const val MIN_LENGTH = 8

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    isError: Boolean = false,
    supportingText: String? = null,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** Set, change or remove the backup password. Saving null removes it. */
@Composable
fun BackupPasswordDialog(isSet: Boolean, onSave: (CharArray?) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = password.isNotEmpty() && password.length < MIN_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val valid = password.length >= MIN_LENGTH && confirm == password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSet) "Change backup password" else "Backup password") },
        text = {
            Column {
                Text(
                    "Exported and automatic backups will be encrypted with this password. " +
                        "You'll need it to restore them on another phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "If you forget it, those backups can't be opened. There's no way to recover it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = if (isSet) "New password" else "Password",
                    tag = "backup-password",
                    isError = tooShort,
                    supportingText = if (tooShort) "At least $MIN_LENGTH characters" else null,
                )
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm password",
                    tag = "backup-password-confirm",
                    isError = mismatch,
                    supportingText = if (mismatch) "Passwords don't match" else null,
                    onDone = { if (valid) onSave(password.toCharArray()) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(password.toCharArray()) }, enabled = valid) { Text("Save") }
        },
        dismissButton = {
            if (isSet) {
                TextButton(onClick = { onSave(null) }) { Text("Remove password", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Asks for the password of a protected backup being imported. */
@Composable
fun EnterBackupPasswordDialog(wrongPassword: Boolean, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password-protected backup") },
        text = {
            Column {
                Text("Enter the password this backup was made with.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Backup password",
                    tag = "import-password",
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) "That password didn't work. Try again." else null,
                    onDone = { if (password.isNotEmpty()) onSubmit(password.toCharArray()) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password.toCharArray()) }, enabled = password.isNotEmpty()) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
