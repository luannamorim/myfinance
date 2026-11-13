package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class ComponentCatalogScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun brand() = compose.snapBothThemes("brand") { BrandCatalog() }

    @Test
    fun components() = compose.snapBothThemes("components") { ComponentCatalog() }
}

@Composable
private fun BrandCatalog() {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        FinanceWordmark(fontSize = 40.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(96, 48, 32, 24, 16).forEach { FinanceMark(size = it.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Launcher icon preview: adaptive background + foreground, circular and squircle masks.
            listOf(RoundedCornerShape(50), RoundedCornerShape(28)).forEach { shape ->
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(108.dp).clip(shape).background(Color(0xFF0B0B0C)),
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                modifier = Modifier.size(108.dp).clip(RoundedCornerShape(50)).background(Color(0xFF3A4A3A)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Splash: the launcher foreground on the brand background.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(160.dp).background(Color(0xFF0B0B0C)),
            )
            Image(
                painter = painterResource(R.drawable.ic_stat_myfinance),
                contentDescription = null,
                modifier = Modifier.size(24.dp).background(Color(0xFF3A3A40)),
            )
        }
    }
}

@Composable
private fun ComponentCatalog() {
    val c = FinanceTheme.colors
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MonthSwitcher(YearMonth.of(2026, 11), onMonth = {})
        SectionHeader("Upcoming", action = { TextAction("See all", onClick = {}) })
        FinanceCard(onClick = {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rent", style = FinanceTheme.type.title, color = c.textPrimary)
                    Text("Housing · due 05/12", style = FinanceTheme.type.caption, color = c.textSecondary)
                }
                AmountDisplay(Money(-250_000, Currency.BRL))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Available cash", "R$ 12.430", Modifier.weight(1f), delta = "+R$ 1.175,73", deltaPositive = true)
            StatTile("Projected", "R$ 9.870", Modifier.weight(1f), delta = "−R$ 2.560,15", deltaPositive = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AmountDisplay(Money(900_000, Currency.BRL), signed = true, tone = AmountTone.POSITIVE)
            AmountDisplay(Money(-31_240, Currency.BRL), tone = AmountTone.NEGATIVE)
            AmountDisplay(Money(110_000, Currency.USD), tone = AmountTone.AUTO, approximate = true)
        }
        CurrencyInput(digits = "31240", currency = Currency.BRL, onDigits = {}, label = "Amount", onCurrency = {})
        DateSelector("Due date", LocalDate.of(2026, 11, 12), onDate = {})
        PrimaryButton("Add transaction", onClick = {}, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Mark as unpaid", onClick = {})
            PrimaryButton("Disabled", onClick = {}, enabled = false)
        }
        FilterChipRow(
            options = listOf("Income", "Expense", "Overdue", "Paid", "US$"),
            selected = setOf("Expense"),
            label = { it },
            onToggle = {},
        )
        FinanceTextField(value = "Electricity", onValueChange = {}, label = "Name")
        EmptyState("No transactions yet", "Add your first income or expense to see your month.", action = { PrimaryButton("Add transaction", onClick = {}) })
    }
}
