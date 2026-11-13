package io.github.luannamorim.myfinance.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.luannamorim.myfinance.core.format.Formats
import io.github.luannamorim.myfinance.domain.model.AppSettings

/** Number and date formatters for the current settings and UI language. */
val LocalFormats = staticCompositionLocalOf { Formats.from(AppSettings()) }
