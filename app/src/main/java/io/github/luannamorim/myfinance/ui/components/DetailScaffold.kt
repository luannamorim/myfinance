package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/**
 * Full-screen page for editors and details: top bar with back, scrolling content capped at 640 dp, and an
 * optional bottom action area (e.g. the primary Save button) that stays above the keyboard.
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = FinanceTheme.colors
    Scaffold(
        modifier = modifier,
        containerColor = c.background,
        topBar = { FinanceTopBar(title = title, onBack = onBack, actions = actions) },
        bottomBar = {
            if (bottomBar != null) {
                Box(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.m)) {
                        bottomBar()
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.s, bottom = Spacing.xxl)),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                content = content,
            )
        }
    }
}
