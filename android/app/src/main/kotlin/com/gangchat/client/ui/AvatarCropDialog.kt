package com.gangchat.client.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.Dialog
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The avatar crop dialog: drag to move and zoom (buttons, slider, mouse wheel)
 * an image under a 280 dp circle; "确定" pops the dialog route with the
 * 512 × 512 PNG crop (`ByteArray`), "取消"/close pops with null. Show it with
 * `showDialog<ByteArray>(context) { AvatarCropDialog(bytes) }`.
 */
@Composable
fun AvatarCropDialog(bytes: ByteArray, title: String = "裁剪头像") =
    StatefulWidget(AvatarCropDialogWidget(bytes, title)) { _AvatarCropDialogState() }

class AvatarCropDialogWidget(val bytes: ByteArray, val title: String)

private class _AvatarCropDialogState : ViewState<AvatarCropDialogWidget>() {
    private var _image: Bitmap? = null
    private var _imageBitmap: ImageBitmap? = null
    private var _decodeFailed = false
    private var _baseScale = 1.0
    private var _zoom = 1.0
    private var _offset = Offset.Zero // dp
    private var _rendering = false
    private var _dragging = false
    private var _dragPointer: PointerId? = null
    private var scope: CoroutineScope? = null

    private suspend fun _decodeImage() {
        val bitmap =
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(widget.bytes, 0, widget.bytes.size) }
        if (!mounted) return
        if (bitmap == null) {
            setState { _decodeFailed = true }
            return
        }
        setState {
            _image = bitmap
            _imageBitmap = bitmap.asImageBitmap()
            _baseScale = max(_frameSize / bitmap.width, _frameSize / bitmap.height)
        }
    }

    override fun dispose() {
        _image = null
        _imageBitmap = null
    }

    private fun _handleWheel(scrollDeltaY: Float, image: Bitmap) {
        val direction = if (scrollDeltaY < 0) 1 else -1
        _setZoom((_zoom + direction * 0.1).coerceIn(_minZoom, _maxZoom), image)
    }

    private fun _startDrag(pointer: PointerId, kind: PointerType, primaryOnly: Boolean) {
        if (_rendering) return
        if (kind == PointerType.Mouse && !primaryOnly) return
        setState {
            _dragging = true
            _dragPointer = pointer
        }
    }

    private fun _moveDrag(pointer: PointerId, delta: Offset, image: Bitmap) {
        if (!_dragging || _dragPointer != pointer) return
        setState { _offset = _clampOffset(_offset + delta, image) }
    }

    private fun _endDrag(pointer: PointerId) {
        if (!_dragging || _dragPointer != pointer) return
        setState {
            _dragging = false
            _dragPointer = null
        }
    }

    private fun _setZoom(value: Double, image: Bitmap) {
        setState {
            _zoom = value.coerceIn(_minZoom, _maxZoom)
            _offset = _clampOffset(_offset, image)
        }
    }

    private fun _zoomSliderValue(): Double {
        if (_zoom <= 1) {
            val normalized = (_zoom - _minZoom) / (1 - _minZoom)
            return (normalized * 0.5).coerceIn(0.0, 0.5)
        }
        val normalized = (_zoom - 1) / (_maxZoom - 1)
        return (0.5 + normalized * 0.5).coerceIn(0.5, 1.0)
    }

    private fun _setZoomFromSlider(value: Double, image: Bitmap) {
        if (value <= 0.5) {
            _setZoom(_minZoom + (value / 0.5) * (1 - _minZoom), image)
            return
        }
        _setZoom(1 + ((value - 0.5) / 0.5) * (_maxZoom - 1), image)
    }

    private fun _adjustZoom(delta: Double, image: Bitmap) {
        _setZoom((_zoom + delta).coerceIn(_minZoom, _maxZoom), image)
    }

    private fun _clampOffset(value: Offset, image: Bitmap): Offset {
        val displayWidth = image.width * _baseScale * _zoom
        val displayHeight = image.height * _baseScale * _zoom
        val maxX = abs(displayWidth - _frameSize) / 2
        val maxY = abs(displayHeight - _frameSize) / 2
        return Offset(value.x.toDouble().coerceIn(-maxX, maxX).toFloat(), value.y.toDouble().coerceIn(-maxY, maxY).toFloat())
    }

    private suspend fun _confirm() {
        val image = _image ?: return
        if (_rendering) return
        setState { _rendering = true }
        try {
            val bytes = withContext(Dispatchers.Default) { _renderCrop(image) }
            if (!mounted) return
            Navigator.of(context).pop(bytes)
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            if (!mounted) return
            setState { _rendering = false }
            showFloatingErrorNotice(context, "裁剪图片失败")
        }
    }

    private fun _renderCrop(image: Bitmap): ByteArray {
        val output = Bitmap.createBitmap(_outputSize, _outputSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val outputScale = _outputSize / _frameSize
        val scale = _baseScale * _zoom
        val displayedWidth = image.width * scale
        val displayedHeight = image.height * scale
        val imageLeft = _workSize / 2 + _offset.x - displayedWidth / 2
        val imageTop = _workSize / 2 + _offset.y - displayedHeight / 2
        val cropLeft = (_workSize - _frameSize) / 2
        val cropTop = (_workSize - _frameSize) / 2
        val dest =
            RectF(
                ((imageLeft - cropLeft) * outputScale).toFloat(),
                ((imageTop - cropTop) * outputScale).toFloat(),
                ((imageLeft - cropLeft + displayedWidth) * outputScale).toFloat(),
                ((imageTop - cropTop + displayedHeight) * outputScale).toFloat(),
            )
        canvas.drawBitmap(image, Rect(0, 0, image.width, image.height), dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val stream = ByteArrayOutputStream()
        val ok = output.compress(Bitmap.CompressFormat.PNG, 100, stream)
        output.recycle()
        if (!ok) throw IllegalStateException("没有可用的图片数据")
        return stream.toByteArray()
    }

    @Composable
    override fun build() {
        scope = rememberCoroutineScope()
        LaunchedEffect(widget.bytes) { _decodeImage() }
        val density = LocalDensity.current
        val shape = RoundedCornerShape(UiRadii.lg)
        Dialog {
            Box(
                Modifier
                    .widthIn(max = 440.dp)
                    .decoration(UiColors.surfaceLow, shape)
                    // Material paints the shape's side over the dialog content.
                    .border(1.dp, UiColors.border, shape),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 18.dp)) {
                    val image = _image
                    val imageBitmap = _imageBitmap
                    val zoomPercent = (_zoom * 100).roundToInt()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            widget.title,
                            modifier = Modifier.weight(1f),
                            style = uiTextStyle(color = UiColors.text, fontSize = 18.0, fontWeight = FontWeight.W600),
                        )
                        ButtonIcon(
                            onPressed = if (_rendering) null else ({ Navigator.of(context).pop() }),
                            tooltip = "关闭",
                            icon = { Icon(Icons.Filled.Close) },
                            size = 32.dp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (_decodeFailed) {
                        _CropError(message = "无法读取图片")
                    } else if (image == null || imageBitmap == null) {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = UiColors.accent)
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(_workSize.dp)
                                    .pointerInput(image) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                when (event.type) {
                                                    PointerEventType.Scroll ->
                                                        event.changes.firstOrNull()?.let { _handleWheel(it.scrollDelta.y, image) }
                                                    PointerEventType.Press ->
                                                        for (change in event.changes) {
                                                            if (change.pressed && !change.previousPressed) {
                                                                _startDrag(change.id, change.type, event.buttons.isPrimaryPressed)
                                                            }
                                                        }
                                                    PointerEventType.Move ->
                                                        for (change in event.changes) {
                                                            val delta = change.position - change.previousPosition
                                                            _moveDrag(change.id, Offset(delta.x / density.density, delta.y / density.density), image)
                                                        }
                                                    PointerEventType.Release ->
                                                        for (change in event.changes) if (!change.pressed) _endDrag(change.id)
                                                    else -> Unit
                                                }
                                            }
                                        }
                                    },
                            ) {
                                Box(Modifier.fillMaxSize().decoration(UiColors.background))
                                Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                                    // Like Flutter's RawImage under Center, the image box is
                                    // limited to the work area and the bitmap fits inside it.
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        filterQuality = FilterQuality.High,
                                        modifier =
                                            Modifier
                                                .graphicsLayer {
                                                    translationX = _offset.x * density.density
                                                    translationY = _offset.y * density.density
                                                    scaleX = _zoom.toFloat()
                                                    scaleY = _zoom.toFloat()
                                                }
                                                .size((image.width * _baseScale).dp, (image.height * _baseScale).dp),
                                    )
                                }
                                _CropShade(frameSize = _frameSize)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ButtonIcon(
                                onPressed = if (_rendering) null else ({ _adjustZoom(-0.15, image) }),
                                tooltip = "缩小 15%",
                                icon = { Icon(Icons.Filled.ZoomOut) },
                                size = 30.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                _MaterialSlider(
                                    value = _zoomSliderValue(),
                                    onChanged = if (_rendering) null else ({ value -> _setZoomFromSlider(value, image) }),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            ButtonIcon(
                                onPressed = if (_rendering) null else ({ _adjustZoom(0.6, image) }),
                                tooltip = "放大 60%",
                                icon = { Icon(Icons.Filled.ZoomIn) },
                                size = 30.dp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("$zoomPercent%", style = uiTextStyle(color = UiColors.textSecondary, fontSize = 12.0, fontWeight = FontWeight.W600))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ResponsiveDialogActionBar(
                        modifier = Modifier.fillMaxWidth(),
                        actions =
                            listOf(
                                ResponsiveDialogAction(label = "取消", onPressed = if (_rendering) null else ({ Navigator.of(context).pop() })),
                                ResponsiveDialogAction(
                                    label = "确定",
                                    icon = Icons.Filled.Crop,
                                    tone = ButtonTone.primary,
                                    loading = _rendering,
                                    onPressed = if (image == null || _rendering) null else ({ scope?.launch { _confirm() } }),
                                ),
                            ),
                    )
                }
            }
        }
    }

    companion object {
        private const val _workSize = 320.0
        private const val _frameSize = 280.0
        private const val _outputSize = 512
        private const val _minZoom = 0.25
        private const val _maxZoom = 4.0
    }
}

@Composable
private fun _CropShade(frameSize: Double) {
    Canvas(Modifier.fillMaxSize()) {
        val frameSizePx = frameSize.dp.toPx()
        val frame =
            ComposeRect(
                center = Offset(size.width / 2, size.height / 2),
                radius = frameSizePx / 2,
            )
        val overlay =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(ComposeRect(Offset.Zero, size))
                addOval(frame)
            }
        drawPath(overlay, Color.Black.copy(alpha = 0.34f))
        drawOval(UiColors.accent, topLeft = frame.topLeft, size = Size(frame.width, frame.height), style = Stroke(width = 1.4.dp.toPx()))
    }
}

@Composable
private fun _CropError(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .decoration(UiColors.dangerSurface, borderColor = UiColors.dangerBorder)
            .padding(12.dp),
    ) {
        Text(message, style = uiTextStyle(color = UiColors.danger, fontSize = 12.0))
    }
}

/**
 * The Material `Slider` the crop dialog uses (track 4 dp, lime active track
 * 2 dp taller, 20 dp text-coloured thumb, a 48 dp tall touch area with the
 * track inset by the 24 dp overlay radius, and the translucent overlay while
 * dragging).
 */
@Composable
private fun _MaterialSlider(value: Double, onChanged: ((Double) -> Unit)?) {
    val enabled = onChanged != null
    val activeColor = if (enabled) UiColors.accent else UiColors.text.copy(alpha = 0.38f)
    val inactiveColor = if (enabled) UiColors.border else UiColors.text.copy(alpha = 0.12f)
    val thumbColor = if (enabled) UiColors.text else UiColors.text.copy(alpha = 0.38f)
    val overlayColor = UiColors.accent.copy(alpha = 0.14f)
    val dragging = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val inset = 24.dp.toPx()
                    fun emit(x: Float) {
                        val travel = size.width - inset * 2
                        if (travel <= 0f) return
                        onChanged?.invoke(((x - inset) / travel).toDouble().coerceIn(0.0, 1.0))
                    }
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Press -> {
                                    dragging.value = true
                                    emit(change.position.x)
                                    change.consume()
                                }
                                PointerEventType.Move ->
                                    if (dragging.value) {
                                        emit(change.position.x)
                                        change.consume()
                                    }
                                PointerEventType.Release -> dragging.value = false
                                else -> Unit
                            }
                        }
                    }
                },
        ) {
            val inset = 24.dp.toPx()
            val trackHeight = 4.dp.toPx()
            val activeHeight = trackHeight + 2.dp.toPx()
            val centerY = size.height / 2
            val trackLeft = inset
            val trackRight = size.width - inset
            val thumbX = trackLeft + (trackRight - trackLeft) * value.toFloat().coerceIn(0f, 1f)
            drawRoundRect(
                inactiveColor,
                topLeft = Offset(thumbX, centerY - trackHeight / 2),
                size = Size((trackRight - thumbX).coerceAtLeast(0f), trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
            )
            drawRoundRect(
                activeColor,
                topLeft = Offset(trackLeft - activeHeight / 2, centerY - activeHeight / 2),
                size = Size((thumbX - trackLeft + activeHeight / 2).coerceAtLeast(0f), activeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(activeHeight / 2),
            )
            if (dragging.value) drawCircle(overlayColor, radius = 24.dp.toPx(), center = Offset(thumbX, centerY))
            drawCircle(thumbColor, radius = 10.dp.toPx(), center = Offset(thumbX, centerY))
        }
    }
}
