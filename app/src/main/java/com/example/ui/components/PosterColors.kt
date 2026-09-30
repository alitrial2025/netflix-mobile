package com.example.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.ErrorResult
import com.example.data.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun posterModel(media: MediaItem?): Any? = media?.posterUrl?.takeIf { it.isNotBlank() } ?: media?.backdropUrl?.takeIf { it.isNotBlank() }

internal fun posterColorsLoaded(media: MediaItem): Boolean = posterColors.get("${media.id}:${posterModel(media)}") != null

private val posterColors = LruCache<String, Pair<Color, Color>>(48)

fun Color.toTopPosterColor(): Color = Color(ColorUtils.blendARGB(toArgbValue(), 0xFF252522.toInt(), .50f))
fun Color.toBottomPosterColor(): Color = Color(ColorUtils.blendARGB(toArgbValue(), 0xFF101111.toInt(), .72f))
private fun Color.toArgbValue() = android.graphics.Color.argb(255, (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

fun getFallbackPosterColors(media: MediaItem): Pair<Color, Color> {
    val base = Color(media.primaryColorHex.takeIf { it != 0L && it != 0xFFE50914L } ?: 0xFF343938)
    return base.toTopPosterColor() to base.toBottomPosterColor()
}

/** Small decoded images and a bounded cache shared by Home and the profile picker. */
@Composable
internal fun rememberPosterColors(media: MediaItem?): State<Pair<Color, Color>> {
    val context = LocalContext.current
    val model = posterModel(media)
    val key = "${media?.id}:$model"
    val fallback = media?.let(::getFallbackPosterColors) ?: (Color(0xFF343938) to Color(0xFF1D2221))
    return produceState(initialValue = posterColors.get(key) ?: fallback, key1 = key) {
        value = posterColors.get(key) ?: fallback
        if (model == null || posterColors.get(key) != null) return@produceState
        val result = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(model).size(96, 144).allowHardware(false).build()
                val response = context.imageLoader.execute(request)
                if (response is ErrorResult) android.util.Log.w("PosterColors", "Poster decoding failed", response.throwable)
                val bitmap = ((response as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null) null else {
                    val palette = Palette.from(bitmap).generate()
                    val lower = Palette.from(bitmap).setRegion(0, bitmap.height / 2, bitmap.width, bitmap.height).generate()
                    val base = Color(palette.dominantSwatch?.rgb ?: palette.mutedSwatch?.rgb ?: 0xFF343938.toInt())
                    val bottom = Color(lower.mutedSwatch?.rgb ?: lower.dominantSwatch?.rgb ?: base.toArgbValue())
                    base.toTopPosterColor() to Color(ColorUtils.blendARGB(bottom.toArgbValue(), 0xFF384340.toInt(), .65f))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("PosterColors", "Poster palette extraction failed", failure); null }
        }
        if (result != null) { posterColors.put(key, result); value = result }
    }
}
