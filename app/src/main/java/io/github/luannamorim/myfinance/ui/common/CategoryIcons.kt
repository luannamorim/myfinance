package io.github.luannamorim.myfinance.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.StackedLineChart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Category icons by key (stored in the database). One consistent family: Material Symbols Rounded. */
object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "work" to Icons.Rounded.Work,
        "laptop" to Icons.Rounded.Laptop,
        "handyman" to Icons.Rounded.Handyman,
        "savings" to Icons.Rounded.Savings,
        "storefront" to Icons.Rounded.Storefront,
        "payments" to Icons.Rounded.Payments,
        "add_circle" to Icons.Rounded.AddCircle,
        "home" to Icons.Rounded.Home,
        "key" to Icons.Rounded.Key,
        "bolt" to Icons.Rounded.Bolt,
        "water_drop" to Icons.Rounded.WaterDrop,
        "wifi" to Icons.Rounded.Wifi,
        "smartphone" to Icons.Rounded.Smartphone,
        "shopping_basket" to Icons.Rounded.ShoppingBasket,
        "restaurant" to Icons.Rounded.Restaurant,
        "local_cafe" to Icons.Rounded.LocalCafe,
        "directions_bus" to Icons.Rounded.DirectionsBus,
        "directions_car" to Icons.Rounded.DirectionsCar,
        "local_gas_station" to Icons.Rounded.LocalGasStation,
        "flight" to Icons.Rounded.Flight,
        "medical_services" to Icons.Rounded.MedicalServices,
        "fitness_center" to Icons.Rounded.FitnessCenter,
        "spa" to Icons.Rounded.Spa,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "movie" to Icons.Rounded.Movie,
        "music_note" to Icons.Rounded.MusicNote,
        "sports_esports" to Icons.Rounded.SportsEsports,
        "celebration" to Icons.Rounded.Celebration,
        "school" to Icons.Rounded.School,
        "menu_book" to Icons.AutoMirrored.Rounded.MenuBook,
        "shopping_bag" to Icons.Rounded.ShoppingBag,
        "checkroom" to Icons.Rounded.Checkroom,
        "card_giftcard" to Icons.Rounded.CardGiftcard,
        "pets" to Icons.Rounded.Pets,
        "child_care" to Icons.Rounded.ChildCare,
        "volunteer_activism" to Icons.Rounded.VolunteerActivism,
        "build" to Icons.Rounded.Build,
        "credit_card" to Icons.Rounded.CreditCard,
        "receipt" to Icons.Rounded.Receipt,
        "receipt_long" to Icons.AutoMirrored.Rounded.ReceiptLong,
        "account_balance" to Icons.Rounded.AccountBalance,
        "show_chart" to Icons.AutoMirrored.Rounded.ShowChart,
        "stacked_line_chart" to Icons.Rounded.StackedLineChart,
        "trending_up" to Icons.AutoMirrored.Rounded.TrendingUp,
        "apartment" to Icons.Rounded.Apartment,
        "pie_chart" to Icons.Rounded.PieChart,
        "currency_bitcoin" to Icons.Rounded.CurrencyBitcoin,
        "business_center" to Icons.Rounded.BusinessCenter,
        "rocket_launch" to Icons.Rounded.RocketLaunch,
        "home_work" to Icons.Rounded.HomeWork,
        "more_horiz" to Icons.Rounded.MoreHoriz,
        "category" to Icons.Rounded.Category,
    )

    fun of(key: String): ImageVector = all[key] ?: Icons.Rounded.Category
}
