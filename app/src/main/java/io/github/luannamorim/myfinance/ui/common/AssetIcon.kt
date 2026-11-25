package io.github.luannamorim.myfinance.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.ui.feature.settings.CategoryBadge
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * An asset's icon: its [logo], whole on a white circle (a dark logo on a clear background stays visible in the
 * dark theme), or its category's icon when it has none or the file can't be read.
 */
@Composable
fun AssetIcon(logo: File?, categoryIcon: String?, modifier: Modifier = Modifier, size: Dp = 36.dp, dimmed: Boolean = false) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    // null while decoding; a failed decode is an empty Logo.
    val decoded by produceState<Logo?>(if (logo == null) Logo(null) else null, logo, px) {
        value = Logo(logo?.let { withContext(Dispatchers.IO) { decode(it, px) } })
    }
    val bitmap = decoded?.bitmap
    if (decoded != null && bitmap == null) {
        CategoryBadge(CategoryIcons.of(categoryIcon ?: "category"), modifier, dimmed)
        return
    }
    Box(
        modifier.size(size).alpha(if (dimmed) 0.4f else 1f).clip(CircleShape).background(Color.White)
            .border(1.dp, FinanceTheme.colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) Image(bitmap, contentDescription = null, Modifier.padding(size / 10), contentScale = ContentScale.Fit)
    }
}

private class Logo(val bitmap: ImageBitmap?)

/** Decodes [file] no larger than needed for a [px]-wide circle (older assets stored a 1600 px photo). */
private fun decode(file: File, px: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
