package io.github.luannamorim.myfinance.ui.feature.cashflow

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.ui.components.EmptyState
import io.github.luannamorim.myfinance.ui.components.TabScreen

@Composable
fun CashFlowScreen(modifier: Modifier = Modifier) {
    TabScreen(title = stringResource(R.string.nav_cash_flow), modifier = modifier) {
        EmptyState(stringResource(R.string.cashflow_empty_title), stringResource(R.string.cashflow_empty_message))
    }
}
