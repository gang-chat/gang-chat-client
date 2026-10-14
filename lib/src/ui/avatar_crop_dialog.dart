import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import 'button.dart';
import 'feedback.dart';
import 'tokens.dart';

class AvatarCropDialog extends StatefulWidget {
  const AvatarCropDialog({super.key, required this.bytes, this.title = '裁剪头像'});

  final Uint8List bytes;
  final String title;

  @override
  State<AvatarCropDialog> createState() => _AvatarCropDialogState();
}

class _AvatarCropDialogState extends State<AvatarCropDialog> {
  static const _workSize = 320.0;
  static const _frameSize = 280.0;
  static const _outputSize = 512;
  static const _minZoom = 0.25;
  static const _maxZoom = 4.0;

  late final Future<ui.Image> _imageFuture = _decodeImage();
  ui.Image? _image;
  double _baseScale = 1;
  double _zoom = 1;
  Offset _offset = Offset.zero;
  bool _rendering = false;
  bool _dragging = false;
  int? _dragPointer;

  Future<ui.Image> _decodeImage() async {
    final codec = await ui.instantiateImageCodec(widget.bytes);
    final frame = await codec.getNextFrame();
    final image = frame.image;
    _image = image;
    _baseScale = math.max(_frameSize / image.width, _frameSize / image.height);
    return image;
  }

  @override
  void dispose() {
    _image?.dispose();
    super.dispose();
  }

  void _handleWheel(PointerSignalEvent event, ui.Image image) {
    if (event is! PointerScrollEvent) return;
    final direction = event.scrollDelta.dy < 0 ? 1 : -1;
    _setZoom(
      (_zoom + direction * 0.1).clamp(_minZoom, _maxZoom).toDouble(),
      image,
    );
  }

  void _startDrag(PointerDownEvent event, ui.Image image) {
    if (_rendering) return;
    if (event.kind == ui.PointerDeviceKind.mouse &&
        event.buttons != kPrimaryMouseButton) {
      return;
    }
    setState(() {
      _dragging = true;
      _dragPointer = event.pointer;
    });
  }

  void _moveDrag(PointerMoveEvent event, ui.Image image) {
    if (!_dragging || _dragPointer != event.pointer) return;
    setState(() {
      _offset = _clampOffset(_offset + event.delta, image);
    });
  }

  void _endDrag(PointerEvent event) {
    if (!_dragging || _dragPointer != event.pointer) return;
    setState(() {
      _dragging = false;
      _dragPointer = null;
    });
  }

  void _setZoom(double value, ui.Image image) {
    setState(() {
      _zoom = value.clamp(_minZoom, _maxZoom).toDouble();
      _offset = _clampOffset(_offset, image);
    });
  }

  double _zoomSliderValue() {
    if (_zoom <= 1) {
      final normalized = (_zoom - _minZoom) / (1 - _minZoom);
      return (normalized * 0.5).clamp(0.0, 0.5).toDouble();
    }
    final normalized = (_zoom - 1) / (_maxZoom - 1);
    return (0.5 + normalized * 0.5).clamp(0.5, 1.0).toDouble();
  }

  void _setZoomFromSlider(double value, ui.Image image) {
    if (value <= 0.5) {
      _setZoom(_minZoom + (value / 0.5) * (1 - _minZoom), image);
      return;
    }
    _setZoom(1 + ((value - 0.5) / 0.5) * (_maxZoom - 1), image);
  }

  void _adjustZoom(double delta, ui.Image image) {
    _setZoom((_zoom + delta).clamp(_minZoom, _maxZoom).toDouble(), image);
  }

  Offset _clampOffset(Offset value, ui.Image image) {
    final displayWidth = image.width * _baseScale * _zoom;
    final displayHeight = image.height * _baseScale * _zoom;
    final maxX = (displayWidth - _frameSize).abs() / 2;
    final maxY = (displayHeight - _frameSize).abs() / 2;
    return Offset(
      value.dx.clamp(-maxX, maxX).toDouble(),
      value.dy.clamp(-maxY, maxY).toDouble(),
    );
  }

  Future<void> _confirm() async {
    final image = _image;
    if (image == null || _rendering) return;
    setState(() => _rendering = true);
    try {
      final bytes = await _renderCrop(image);
      if (!mounted) return;
      Navigator.of(context).pop(bytes);
    } catch (e) {
      if (!mounted) return;
      setState(() => _rendering = false);
      showFloatingErrorNotice(context, '裁剪图片失败');
    }
  }

  Future<Uint8List> _renderCrop(ui.Image image) async {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    final outputScale = _outputSize / _frameSize;
    final scale = _baseScale * _zoom;
    final displayedWidth = image.width * scale;
    final displayedHeight = image.height * scale;
    final imageLeft = _workSize / 2 + _offset.dx - displayedWidth / 2;
    final imageTop = _workSize / 2 + _offset.dy - displayedHeight / 2;
    final cropLeft = (_workSize - _frameSize) / 2;
    final cropTop = (_workSize - _frameSize) / 2;
    final dest = Rect.fromLTWH(
      (imageLeft - cropLeft) * outputScale,
      (imageTop - cropTop) * outputScale,
      displayedWidth * outputScale,
      displayedHeight * outputScale,
    );

    canvas.drawImageRect(
      image,
      Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
      dest,
      Paint()..filterQuality = FilterQuality.high,
    );
    final picture = recorder.endRecording();
    final cropped = await picture.toImage(_outputSize, _outputSize);
    final data = await cropped.toByteData(format: ui.ImageByteFormat.png);
    cropped.dispose();
    if (data == null) {
      throw StateError('没有可用的图片数据');
    }
    return data.buffer.asUint8List();
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: UiColors.surfaceLow,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(UiRadii.lg)),
        side: BorderSide(color: UiColors.border),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 440),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 20, 22, 18),
          child: FutureBuilder<ui.Image>(
            future: _imageFuture,
            builder: (context, snapshot) {
              final image = snapshot.data;
              final zoomPercent = (_zoom * 100).round();
              return Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          widget.title,
                          style: const TextStyle(
                            color: UiColors.text,
                            fontSize: 18,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      ButtonIcon(
                        onPressed: _rendering
                            ? null
                            : () => Navigator.of(context).pop(),
                        tooltip: '关闭',
                        icon: const Icon(Icons.close),
                        size: 32,
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  if (snapshot.hasError)
                    const _CropError(message: '无法读取图片')
                  else if (image == null)
                    const SizedBox(
                      height: 180,
                      child: Center(
                        child: CircularProgressIndicator(
                          color: UiColors.accent,
                        ),
                      ),
                    )
                  else ...[
                    Center(
                      child: Listener(
                        behavior: HitTestBehavior.opaque,
                        onPointerSignal: (event) => _handleWheel(event, image),
                        onPointerDown: (event) => _startDrag(event, image),
                        onPointerMove: (event) => _moveDrag(event, image),
                        onPointerUp: _endDrag,
                        onPointerCancel: _endDrag,
                        child: MouseRegion(
                          cursor: _dragging
                              ? SystemMouseCursors.grabbing
                              : SystemMouseCursors.grab,
                          child: SizedBox.square(
                            dimension: _workSize,
                            child: Stack(
                              children: [
                                const Positioned.fill(
                                  child: ColoredBox(color: UiColors.background),
                                ),
                                Positioned.fill(
                                  child: ClipRect(
                                    child: Center(
                                      child: Transform.translate(
                                        offset: _offset,
                                        child: Transform.scale(
                                          scale: _zoom,
                                          child: RawImage(
                                            image: image,
                                            width: image.width * _baseScale,
                                            height: image.height * _baseScale,
                                            fit: BoxFit.contain,
                                            filterQuality: FilterQuality.high,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                                Positioned.fill(
                                  child: CustomPaint(
                                    painter: _CropShadePainter(
                                      frameSize: _frameSize,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        ButtonIcon(
                          onPressed: _rendering
                              ? null
                              : () => _adjustZoom(-0.15, image),
                          tooltip: '缩小 15%',
                          icon: const Icon(Icons.zoom_out),
                          size: 30,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: SliderTheme(
                            data: SliderTheme.of(context).copyWith(
                              activeTrackColor: UiColors.accent,
                              inactiveTrackColor: UiColors.border,
                              thumbColor: UiColors.text,
                              overlayColor: UiColors.accent.withValues(
                                alpha: 0.14,
                              ),
                              trackHeight: 4,
                            ),
                            child: Slider(
                              value: _zoomSliderValue(),
                              min: 0,
                              max: 1,
                              onChanged: _rendering
                                  ? null
                                  : (value) => _setZoomFromSlider(value, image),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        ButtonIcon(
                          onPressed: _rendering
                              ? null
                              : () => _adjustZoom(0.6, image),
                          tooltip: '放大 60%',
                          icon: const Icon(Icons.zoom_in),
                          size: 30,
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Center(
                      child: Text(
                        '$zoomPercent%',
                        style: const TextStyle(
                          color: UiColors.textSecondary,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 16),
                  ResponsiveDialogActionBar(
                    actions: [
                      ResponsiveDialogAction(
                        label: '取消',
                        onPressed: _rendering
                            ? null
                            : () => Navigator.of(context).pop(),
                      ),
                      ResponsiveDialogAction(
                        label: '确定',
                        icon: Icons.crop,
                        tone: ButtonTone.primary,
                        loading: _rendering,
                        onPressed: image == null || _rendering
                            ? null
                            : _confirm,
                      ),
                    ],
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _CropShadePainter extends CustomPainter {
  const _CropShadePainter({required this.frameSize});

  final double frameSize;

  @override
  void paint(Canvas canvas, Size size) {
    final frame = Rect.fromCenter(
      center: Offset(size.width / 2, size.height / 2),
      width: frameSize,
      height: frameSize,
    );
    final overlay = Path()
      ..fillType = PathFillType.evenOdd
      ..addRect(Offset.zero & size)
      ..addOval(frame);
    canvas.drawPath(
      overlay,
      Paint()
        ..color = Colors.black.withValues(alpha: 0.34)
        ..style = PaintingStyle.fill
        ..blendMode = BlendMode.srcOver,
    );
    canvas.drawOval(
      frame,
      Paint()
        ..color = UiColors.accent
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.4,
    );
  }

  @override
  bool shouldRepaint(_CropShadePainter oldDelegate) =>
      oldDelegate.frameSize != frameSize;
}

class _CropError extends StatelessWidget {
  const _CropError({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        color: UiColors.dangerSurface,
        border: Border.fromBorderSide(BorderSide(color: UiColors.dangerBorder)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Text(
          message,
          style: const TextStyle(color: UiColors.danger, fontSize: 12),
        ),
      ),
    );
  }
}
