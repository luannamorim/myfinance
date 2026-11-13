package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.format.MINUS
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

/** How an amount is coloured (docs/BRAND.md, "Money colour rule"). */
enum class AmountTone {
    /** Primary text colour; the sign carries the meaning (transaction rows). */
    NEUTRAL,

    /** Always green (income received, positive totals). */
    POSITIVE,

    /** Always red (expense totals, overdue amounts). */
    NEGATIVE,

    /** Green when ≥ 0, red when negative (gains, balances). */
    AUTO,
}

/**
 * A formatted amount with tabular figures. The spoken description says "minus" instead of the symbol.
 * It stays on one line and shrinks (down to half its size) rather than being cut: a clipped amount reads
 * as a different number.
 */
@Composable
fun AmountDisplay(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = FinanceTheme.type.numeric,
    signed: Boolean = false,
    tone: AmountTone = AmountTone.NEUTRAL,
    approximate: Boolean = false,
    textAlign: TextAlign? = null,
) {
    val c = FinanceTheme.colors
    val text = (if (approximate) "≈ " else "") + LocalFormats.current.money.format(money, signed)
    val color = when (tone) {
        AmountTone.NEUTRAL -> c.textPrimary
        AmountTone.POSITIVE -> c.positive
        AmountTone.NEGATIVE -> c.negative
        AmountTone.AUTO -> if (money.isNegative) c.negative else c.positive
    }
    val spoken = if (text.contains(MINUS)) stringResource(R.string.a11y_minus, text.replace(MINUS.toString(), "")) else text
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = spoken },
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        autoSize = shrinkToFit(style),
    )
}

/** Shrinks single-line text to fit its width, from the style's size down to half of it. */
fun shrinkToFit(style: TextStyle): TextAutoSize? =
    if (style.fontSize.isSp) TextAutoSize.StepBased(minFontSize = (style.fontSize.value / 2).sp, maxFontSize = style.fontSize, stepSize = 0.5.sp) else null
