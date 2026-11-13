package io.github.luannamorim.myfinance.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object FinanceShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
}

internal val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = FinanceShapes.small,
    medium = FinanceShapes.medium,
    large = FinanceShapes.large,
    extraLarge = RoundedCornerShape(28.dp),
)
