package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = FinanceTheme.colors
    TopAppBar(
        title = { Text(title, style = FinanceTheme.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.background,
            scrolledContainerColor = c.surface,
            titleContentColor = c.textPrimary,
            navigationIconContentColor = c.textPrimary,
            actionIconContentColor = c.textPrimary,
        ),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    /** Null hides the dismiss button (an information dialog with a single "OK"). */
    dismissText: String? = stringResource(R.string.action_cancel),
) {
    val c = FinanceTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = FinanceShapes.large,
        containerColor = c.surfaceRaised,
        title = { Text(title, style = FinanceTheme.type.title, color = c.textPrimary) },
        text = { Text(message, style = FinanceTheme.type.body, color = c.textSecondary) },
        confirmButton = {
            TextAction(confirmText, onClick = onConfirm, color = if (destructive) c.danger else c.accent)
        },
        dismissButton = dismissText?.let { text -> { TextAction(text, onClick = onDismiss, color = c.textSecondary) } },
    )
}
