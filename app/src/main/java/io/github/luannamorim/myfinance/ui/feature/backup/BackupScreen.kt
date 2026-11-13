package io.github.luannamorim.myfinance.ui.feature.backup

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.data.backup.KeyState
import io.github.luannamorim.myfinance.data.prefs.BackupFailure
import io.github.luannamorim.myfinance.domain.backup.BackupFileInfo
import io.github.luannamorim.myfinance.domain.backup.Retention
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.components.ConfirmDialog
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.SwitchRow
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BackupActions(
    val onBack: () -> Unit = {},
    val onSetPassword: (CharArray) -> Unit = {},
    val onUnlock: (CharArray) -> Unit = {},
    val onChooseFolder: () -> Unit = {},
    val onAutoBackup: (Boolean) -> Unit = {},
    val onRunNow: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onRestoreFile: (BackupFileInfo) -> Unit = {},
    val onConfirmRestore: (CharArray?) -> Unit = {},
    val onCancelRestore: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Failure notifications need the permission on Android 13+; asked once a folder is chosen.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            viewModel.chooseFolder(uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.prepareRestore(uri)
    }
    BackupContent(
        state,
        BackupActions(
            onBack = onBack,
            onSetPassword = viewModel::setPassword,
            onUnlock = viewModel::unlock,
            onChooseFolder = { folderPicker.launch(null) },
            onAutoBackup = viewModel::setAutoBackup,
            onRunNow = viewModel::runNow,
            onExport = { exportPicker.launch(Retention.fileName(System.currentTimeMillis(), ZoneId.systemDefault())) },
            onImport = { importPicker.launch(arrayOf("*/*")) },
            onRestoreFile = viewModel::prepareRestore,
            onConfirmRestore = viewModel::confirmRestore,
            onCancelRestore = viewModel::cancelRestore,
            onMessageShown = viewModel::consumeMessage,
        ),
    )
}

@Composable
fun BackupContent(state: BackupUiState, actions: BackupActions) {
    val c = FinanceTheme.colors
    var passwordDialog by remember { mutableStateOf(false) }
    var unlockDialog by remember { mutableStateOf(false) }
    val ready = state.keyState == KeyState.READY
    val hasFolder = state.backup.folderUri != null

    DetailScaffold(title = stringResource(R.string.backup_title), onBack = actions.onBack) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceRaised)
        state.message?.let { MessageCard(it, actions.onMessageShown) }

        SectionHeader(stringResource(R.string.backup_password_section))
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    when (state.keyState) {
                        KeyState.NONE -> R.string.backup_password_none
                        KeyState.READY -> R.string.backup_password_set
                        KeyState.LOCKED -> R.string.backup_password_locked
                    },
                ),
                style = FinanceTheme.type.body,
                color = if (state.keyState == KeyState.LOCKED) c.warning else c.textSecondary,
            )
            Spacer(Modifier.height(Spacing.m))
            SecondaryButton(
                stringResource(
                    when (state.keyState) {
                        KeyState.NONE -> R.string.backup_password_define
                        KeyState.READY -> R.string.backup_password_change
                        KeyState.LOCKED -> R.string.backup_password_enter
                    },
                ),
                onClick = { if (state.keyState == KeyState.LOCKED) unlockDialog = true else passwordDialog = true },
                icon = Icons.Rounded.Key,
            )
        }

        SectionHeader(stringResource(R.string.backup_folder_section))
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(
                state.backup.folderName ?: stringResource(if (hasFolder) R.string.backup_folder_unnamed else R.string.backup_folder_none),
                style = FinanceTheme.type.bodyStrong,
                color = if (hasFolder) c.textPrimary else c.textSecondary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(R.string.backup_folder_hint), style = FinanceTheme.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Spacing.m))
            SecondaryButton(
                stringResource(if (hasFolder) R.string.backup_folder_change else R.string.backup_folder_choose),
                onClick = actions.onChooseFolder,
                icon = Icons.Rounded.Folder,
            )
        }

        SectionHeader(stringResource(R.string.backup_status_section))
        FinanceCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.backup_last), style = FinanceTheme.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text(
                    state.backup.lastSuccessMs?.let { dateTimeText(it) } ?: stringResource(R.string.backup_never),
                    style = FinanceTheme.type.bodyStrong,
                    color = c.textPrimary,
                )
            }
            val error = state.backup.lastError
            if (error != null || state.folderError) {
                Spacer(Modifier.height(Spacing.s))
                Text(stringResource(failureText(error)), style = FinanceTheme.type.caption, color = c.danger)
            }
            Spacer(Modifier.height(Spacing.s))
            SwitchRow(stringResource(R.string.backup_auto), stringResource(R.string.backup_auto_hint), state.autoBackup, actions.onAutoBackup)
            Spacer(Modifier.height(Spacing.s))
            PrimaryButton(
                stringResource(if (state.running) R.string.backup_running else R.string.backup_now),
                onClick = actions.onRunNow,
                enabled = !state.running && hasFolder && ready,
                icon = Icons.Rounded.CloudUpload,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!hasFolder || !ready) {
                Spacer(Modifier.height(Spacing.s))
                Text(stringResource(R.string.backup_not_configured), style = FinanceTheme.type.caption, color = c.textSecondary)
            }
        }

        if (hasFolder) {
            SectionHeader(stringResource(R.string.backup_files_section))
            if (state.files.isEmpty()) {
                Text(stringResource(R.string.backup_files_empty), style = FinanceTheme.type.body, color = c.textSecondary)
            } else {
                Text(stringResource(R.string.backup_files_hint), style = FinanceTheme.type.caption, color = c.textSecondary)
            }
            state.files.forEach { file -> BackupFileRow(file, onClick = { actions.onRestoreFile(file) }) }
        }

        SectionHeader(stringResource(R.string.backup_file_section))
        SecondaryButton(
            stringResource(R.string.backup_export),
            onClick = actions.onExport,
            modifier = Modifier.fillMaxWidth(),
            enabled = ready,
            icon = Icons.Rounded.SaveAlt,
        )
        SecondaryButton(stringResource(R.string.backup_import), onClick = actions.onImport, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.FileOpen)
    }

    if (passwordDialog) {
        PasswordDialog(confirm = true, onDismiss = { passwordDialog = false }, onSubmit = { actions.onSetPassword(it); passwordDialog = false })
    }
    if (unlockDialog) {
        PasswordDialog(confirm = false, onDismiss = { unlockDialog = false }, onSubmit = { actions.onUnlock(it); unlockDialog = false })
    }
    state.pending?.let { pending ->
        val message = stringResource(R.string.backup_restore_message, dateTimeText(pending.createdAtMs))
        if (pending.needsPassword) {
            PasswordDialog(
                confirm = false,
                title = stringResource(R.string.backup_restore_title),
                message = message + "\n\n" + stringResource(R.string.backup_restore_password),
                error = if (pending.passwordError) stringResource(R.string.backup_password_wrong) else null,
                submitText = stringResource(R.string.backup_restore_confirm),
                destructive = true,
                onDismiss = actions.onCancelRestore,
                onSubmit = { actions.onConfirmRestore(it) },
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.backup_restore_title),
                message = message,
                confirmText = stringResource(R.string.backup_restore_confirm),
                destructive = true,
                onConfirm = { actions.onConfirmRestore(null) },
                onDismiss = actions.onCancelRestore,
            )
        }
    }
}

/** Result of the last action, until dismissed. Errors are red. */
@Composable
private fun MessageCard(message: BackupMessage, onDismiss: () -> Unit) {
    val c = FinanceTheme.colors
    val error = message != BackupMessage.EXPORTED && message != BackupMessage.RESTORED
    FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m, borderColor = if (error) c.danger else c.outline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(message.textRes()),
                style = FinanceTheme.type.body,
                color = if (error) c.danger else c.textPrimary,
                modifier = Modifier.weight(1f).padding(start = Spacing.xs),
            )
            TextAction(stringResource(R.string.action_ok), onClick = onDismiss, color = c.textSecondary)
        }
    }
}

/** One backup in the folder; tapping it starts a restore. */
@Composable
private fun BackupFileRow(file: BackupFileInfo, onClick: () -> Unit) {
    val c = FinanceTheme.colors
    val style = LocalFormats.current.money.style
    FinanceCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Restore, contentDescription = null, tint = c.textSecondary)
            Spacer(Modifier.width(Spacing.m))
            Text(dateTimeText(file.createdAtMs), style = FinanceTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(sizeText(file.sizeBytes, style), style = FinanceTheme.type.numeric, color = c.textSecondary)
        }
    }
}

@Composable
private fun PasswordDialog(
    confirm: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
    title: String = stringResource(R.string.backup_password_dialog_title),
    message: String = stringResource(R.string.backup_password_dialog_message),
    error: String? = null,
    submitText: String = stringResource(R.string.action_save),
    destructive: Boolean = false,
) {
    val c = FinanceTheme.colors
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val tooShort = stringResource(R.string.backup_password_too_short)
    val mismatch = stringResource(R.string.backup_password_mismatch)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        shape = FinanceShapes.large,
        title = { Text(title, style = FinanceTheme.type.title, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(message, style = FinanceTheme.type.body, color = c.textSecondary)
                FinanceTextField(
                    value = password,
                    onValueChange = { password = it; localError = null },
                    label = stringResource(R.string.backup_password_field),
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (confirm) {
                    FinanceTextField(
                        value = repeat,
                        onValueChange = { repeat = it; localError = null },
                        label = stringResource(R.string.backup_password_confirm),
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                (localError ?: error)?.let { Text(it, style = FinanceTheme.type.caption, color = c.danger) }
            }
        },
        confirmButton = {
            TextAction(
                submitText,
                color = if (destructive) c.danger else c.accent,
                onClick = {
                    when {
                        confirm && password.length < MIN_PASSWORD -> localError = tooShort
                        confirm && password != repeat -> localError = mismatch
                        password.isEmpty() -> Unit
                        else -> onSubmit(password.toCharArray())
                    }
                },
            )
        },
        dismissButton = { TextAction(stringResource(R.string.action_cancel), onClick = onDismiss, color = c.textSecondary) },
    )
}

private const val MIN_PASSWORD = 8
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "today at 14:32" or "12/11/2026 at 14:32" (date in the user's format, local time). */
@Composable
private fun dateTimeText(ms: Long): String {
    val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val time = TIME.format(dt)
    return if (dt.toLocalDate() == LocalDate.now()) {
        stringResource(R.string.backup_today_at, time)
    } else {
        stringResource(R.string.backup_date_at, LocalFormats.current.dates.date(dt.toLocalDate()), time)
    }
}

private fun sizeText(bytes: Long, style: NumberFormatStyle): String = if (bytes >= 1_048_576) {
    val mb = String.format(Locale.ROOT, "%.1f", bytes / 1_048_576.0)
    (if (style == NumberFormatStyle.COMMA_DECIMAL) mb.replace('.', ',') else mb) + " MB"
} else {
    "${(bytes / 1024).coerceAtLeast(1)} KB"
}

private fun failureText(failure: BackupFailure?): Int = when (failure) {
    BackupFailure.NO_PASSWORD -> R.string.backup_error_password
    BackupFailure.NO_SPACE -> R.string.backup_error_space
    BackupFailure.UNKNOWN -> R.string.backup_error_unknown
    BackupFailure.NO_FOLDER, BackupFailure.FOLDER_UNAVAILABLE, null -> R.string.backup_error_folder
}

private fun BackupMessage.textRes(): Int = when (this) {
    BackupMessage.EXPORTED -> R.string.backup_msg_exported
    BackupMessage.RESTORED -> R.string.backup_msg_restored
    BackupMessage.WRONG_PASSWORD -> R.string.backup_msg_wrong_password
    BackupMessage.CORRUPTED -> R.string.backup_msg_corrupted
    BackupMessage.UNSUPPORTED -> R.string.backup_msg_unsupported
    BackupMessage.NO_SPACE -> R.string.backup_msg_space
    BackupMessage.NO_PASSWORD -> R.string.backup_msg_no_password
    BackupMessage.FOLDER_ERROR -> R.string.backup_msg_folder_error
    BackupMessage.ERROR -> R.string.backup_msg_error
}
