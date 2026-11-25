package io.github.luannamorim.myfinance.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** An asset's image is its logo: small, and transparent logos keep their transparency. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ImageStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val images = ImageStore(context, Dispatchers.Unconfined)

    private fun source(name: String, width: Int, height: Int, format: Bitmap.CompressFormat, draw: Bitmap.() -> Unit): Uri {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply(draw)
        file.outputStream().use { bitmap.compress(format, 100, it) }
        return Uri.fromFile(file)
    }

    private fun stored(path: String): Bitmap = BitmapFactory.decodeFile(images.file(path).path)

    @Test
    fun transparentLogo_keepsItsTransparency() = runBlocking {
        val uri = source("logo.png", 200, 100, Bitmap.CompressFormat.PNG) { eraseColor(Color.TRANSPARENT); setPixel(100, 50, Color.RED) }
        val path = images.import(uri, ImageKind.ASSET)
        assertTrue(path, path.startsWith("images/assets/") && path.endsWith(".png"))
        assertEquals(0, Color.alpha(stored(path).getPixel(0, 0)))
    }

    @Test
    fun opaqueImage_staysJpeg_atLogoSize() = runBlocking {
        val uri = source("photo.jpg", 2000, 1000, Bitmap.CompressFormat.JPEG) { eraseColor(Color.CYAN) }
        val path = images.import(uri, ImageKind.ASSET)
        assertTrue(path, path.endsWith(".jpg"))
        val bitmap = stored(path)
        assertEquals(512, maxOf(bitmap.width, bitmap.height))
    }
}
