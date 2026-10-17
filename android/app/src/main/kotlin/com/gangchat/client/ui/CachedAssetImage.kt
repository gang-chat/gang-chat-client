package com.gangchat.client.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.app.MediaCacheRequest
import com.gangchat.client.foundation.failedFuture
import com.gangchat.client.foundation.future
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.FutureBuilder
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.toContentScale
import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dart's `CachedAssetImageErrorBuilder` (the stack trace is part of [error] on the JVM). */
typealias CachedAssetImageErrorBuilder = @Composable (context: BuildContext, error: Throwable) -> Unit

/**
 * Loads [request] through [cache] as an image file: the on-disk cache (the
 * native client never takes Dart's web-only in-memory path). Dart returned a
 * `FileImage`; the file is what the Compose image widgets draw.
 */
suspend fun cachedMediaImageProvider(cache: MediaCacheController, request: MediaCacheRequest): File =
    cache.getOrDownload(request = request)

/**
 * The pixel size of the image in [file] (Dart decoded the first frame through
 * `ImageStream`); throws when the file is not a decodable image.
 */
suspend fun decodeImageFileSize(file: File): androidx.compose.ui.geometry.Size =
    withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IllegalStateException("Invalid image data")
        androidx.compose.ui.geometry.Size(options.outWidth.toFloat(), options.outHeight.toFloat())
    }

/**
 * The app's Coil image loader. It decodes animated GIF and WebP (Flutter's
 * `Image` animates both): Android's `ImageDecoder` on API 28+, Coil's
 * `Movie`-based GIF decoder below that.
 */
object GangImageLoader {
    @Volatile
    private var _loader: ImageLoader? = null

    /**
     * Replaces the loader (tests: Robolectric cannot run `ImageDecoder`, so
     * they install one with `imageDecoderEnabled(false)`); null restores the
     * default on the next [get].
     */
    fun install(loader: ImageLoader?) {
        _loader = loader
    }

    fun get(context: Context): ImageLoader =
        _loader ?: synchronized(this) {
            _loader ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
                }
                .build()
                .also { _loader = it }
        }
}

/**
 * Draws the image in [file] with Coil (animated GIF/WebP animate), sized and
 * fitted like Flutter's `Image(image: FileImage(file), width:, height:, fit:)`.
 */
@Composable
fun FileImage(
    file: File,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    fit: BoxFit? = null,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(file).build(),
        contentDescription = null,
        imageLoader = GangImageLoader.get(context),
        modifier = modifier.imageSize(width, height),
        contentScale = fit.toContentScale(),
    )
}

private fun Modifier.imageSize(width: Dp?, height: Dp?): Modifier =
    this.then(if (width != null) Modifier.width(width) else Modifier).then(if (height != null) Modifier.height(height) else Modifier)

@Composable
fun CachedAssetImage(
    url: String,
    modifier: Modifier = Modifier,
    filename: String? = null,
    mimeType: String? = null,
    expectedBytes: Long? = null,
    namespace: String = "asset",
    cache: MediaCacheController? = null,
    width: Dp? = null,
    height: Dp? = null,
    fit: BoxFit? = null,
    loadingBuilder: (@Composable (BuildContext) -> Unit)? = null,
    errorBuilder: CachedAssetImageErrorBuilder? = null,
) = StatefulWidget(
    CachedAssetImageWidget(url, filename, mimeType, expectedBytes, namespace, cache, width, height, fit, loadingBuilder, errorBuilder, modifier),
) { _CachedAssetImageState() }

class CachedAssetImageWidget(
    val url: String,
    val filename: String?,
    val mimeType: String?,
    val expectedBytes: Long?,
    val namespace: String,
    val cache: MediaCacheController?,
    val width: Dp?,
    val height: Dp?,
    val fit: BoxFit?,
    val loadingBuilder: (@Composable (BuildContext) -> Unit)?,
    val errorBuilder: CachedAssetImageErrorBuilder?,
    val modifier: Modifier,
)

private class _CachedAssetImageState : ViewState<CachedAssetImageWidget>() {
    private var _image: Deferred<File>? = null
    private var _scopeCache: MediaCacheController? = null

    // Dart's didChangeDependencies: a new MediaCacheScope reloads.
    private fun _syncScopeCache() {
        val scopeCache = MediaCacheScope.of(context)
        if (_scopeCache !== scopeCache) {
            _scopeCache = scopeCache
            _image = _load()
        }
    }

    override fun didUpdateWidget(oldWidget: CachedAssetImageWidget) {
        if (oldWidget.url != widget.url ||
            oldWidget.filename != widget.filename ||
            oldWidget.mimeType != widget.mimeType ||
            oldWidget.expectedBytes != widget.expectedBytes ||
            oldWidget.namespace != widget.namespace ||
            oldWidget.cache !== widget.cache
        ) {
            if (_scopeCache != null) _image = _load()
        }
    }

    private fun _load(): Deferred<File> {
        val request =
            MediaCacheRequest.tryFromUrl(
                url = widget.url,
                filename = widget.filename,
                mimeType = widget.mimeType,
                expectedBytes = widget.expectedBytes,
                namespace = widget.namespace,
            ) ?: return failedFuture(IllegalStateException("图片地址无效"))
        val cache = widget.cache ?: _scopeCache ?: MediaCacheScope.of(context)
        return future { cachedMediaImageProvider(cache, request) }
    }

    @Composable
    override fun build() {
        _syncScopeCache()
        FutureBuilder(_image) { snapshot ->
            val image = snapshot.data
            val error = snapshot.error
            when {
                image != null -> FileImage(image, modifier = widget.modifier, width = widget.width, height = widget.height, fit = widget.fit)
                error != null -> {
                    val builder = widget.errorBuilder
                    if (builder != null) {
                        Box(widget.modifier, propagateMinConstraints = true) { builder(context, error) }
                    } else {
                        _DefaultCachedAssetImageError(widget.width, widget.height, widget.modifier)
                    }
                }
                else -> {
                    val builder = widget.loadingBuilder
                    if (builder != null) {
                        Box(widget.modifier, propagateMinConstraints = true) { builder(context) }
                    } else {
                        _DefaultCachedAssetImageLoading(widget.width, widget.height, widget.modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun _DefaultCachedAssetImageLoading(width: Dp?, height: Dp?, modifier: Modifier) {
    Box(modifier.imageSize(width, height).fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = UiColors.textSecondary,
            strokeWidth = 2.dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Square,
        )
    }
}

@Composable
private fun _DefaultCachedAssetImageError(width: Dp?, height: Dp?, modifier: Modifier) {
    Box(modifier.imageSize(width, height).fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.BrokenImage, color = UiColors.textMuted, size = 24.dp)
    }
}
