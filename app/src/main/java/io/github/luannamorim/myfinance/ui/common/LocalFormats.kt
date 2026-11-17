package io.github.luannamorim.myfinance.ui.common

import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.ui.platform.LocalConfiguration
import io.github.luannamorim.myfinance.core.format.Formats
import io.github.luannamorim.myfinance.domain.model.AppSettings

/**
 * Number and date formatters for the current settings and UI language. MainActivity provides it; the
 * default (tests, previews) is computed from each composition's locale, not cached for the whole process.
 */
val LocalFormats = compositionLocalWithComputedDefaultOf { Formats.from(AppSettings(), LocalConfiguration.currentValue.locales[0]) }
