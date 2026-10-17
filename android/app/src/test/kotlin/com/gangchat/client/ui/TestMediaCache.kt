package com.gangchat.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.WindowMediaQuery
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import coil3.imageDecoderEnabled
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.testing.MockClient
import java.io.ByteArrayOutputStream
import java.nio.file.Files

/**
 * A hermetic [MediaCacheController]: downloads come from [files] (by URL),
 * anything else is a 404, and the cache lives in a fresh temp directory.
 */
fun testMediaCacheController(files: Map<String, ByteArray> = emptyMap()): MediaCacheController {
    val directory = Files.createTempDirectory("gang-media-cache").toFile()
    return MediaCacheController(
        httpClientFactory = {
            MockClient { request ->
                val bytes = files[request.url.toString()]
                if (bytes == null) HttpResponse("", 404) else HttpResponse.bytes(bytes, 200, headers = mapOf("content-type" to "image/png"))
            }
        },
        cacheDirectoryProvider = { directory },
    )
}

/**
 * Installs a Coil loader that decodes with `BitmapFactory`: Robolectric has
 * no `ImageDecoder`, which Coil uses for static images on API 29+.
 */
fun installTestImageLoader() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    GangImageLoader.install(
        coil3.ImageLoader.Builder(context)
            .imageDecoderEnabled(false)
            .build(),
    )
}

/** A solid [width]x[height] PNG of [color] (for image fixtures). */
fun solidPng(width: Int, height: Int, color: Int): ByteArray {
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)
    val out = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

/** The node's bounds in root coordinates, in dp. */
fun SemanticsNodeInteraction.dpBounds(density: Float): DpRect {
    val rect = fetchSemanticsNode().boundsInRoot
    return DpRect((rect.left / density).dp, (rect.top / density).dp, (rect.right / density).dp, (rect.bottom / density).dp)
}

/** Flutter's `RenderParagraph.didExceedMaxLines` for a text node (ellipsized or cut). */
fun SemanticsNodeInteraction.didExceedMaxLines(): Boolean {
    val layout = textLayout()
    val lastLine = layout.lineCount - 1
    return layout.multiParagraph.didExceedMaxLines || (lastLine >= 0 && layout.isLineEllipsized(lastLine)) || layout.didOverflowWidth
}

/** Matches a node whose text (or merged text) contains exactly [text] as one of its parts. */
fun hasTextPart(text: String): SemanticsMatcher =
    SemanticsMatcher("has text part '$text'") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }

/** The colour at window position ([xPx], [yPx]) in pixels. */
fun ComposeContentTestRule.rootPixel(xPx: Int, yPx: Int): Color = onRoot().captureToImage().toPixelMap()[xPx, yPx]

/**
 * [setUiContent] with [cache] provided above the root navigator, so routes
 * (dialogs, the image preview) see it too. In the app those routes fall back
 * to the default controller, which shares the same on-disk cache.
 */
fun ComposeContentTestRule.setUiContentWithMediaCache(
    cache: MediaCacheController,
    notifications: Boolean = false,
    content: @Composable (BuildContext) -> Unit,
) {
    setContent {
        UiTheme {
            WindowMediaQuery {
                MediaCacheScope(cache) {
                    val app: @Composable () -> Unit = {
                        RootNavigator { context ->
                            Box(Modifier.fillMaxSize().background(UiColors.background)) { content(context) }
                        }
                    }
                    if (notifications) AppNotificationHost { app() } else app()
                }
            }
        }
    }
}
