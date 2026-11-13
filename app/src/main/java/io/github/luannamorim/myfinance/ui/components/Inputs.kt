package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

@Composable
fun FinanceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Default,
    textStyle: TextStyle = FinanceTheme.type.body,
    suffix: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val c = FinanceTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.textTertiary) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = textStyle,
        suffix = suffix?.let { { Text(it, color = c.textSecondary) } },
        visualTransformation = visualTransformation,
        shape = FinanceShapes.small,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.textPrimary,
            unfocusedBorderColor = c.outline,
            focusedLabelColor = c.textPrimary,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.accent,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            errorBorderColor = c.danger,
            errorLabelColor = c.danger,
            errorSupportingTextColor = c.danger,
        ),
    )
}
