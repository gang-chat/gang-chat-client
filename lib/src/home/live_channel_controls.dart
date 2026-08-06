part of 'live_channel_pane.dart';

class _LiveControlBar extends StatelessWidget {
  const _LiveControlBar({
    required this.joined,
    required this.joining,
    required this.micMuted,
    required this.headphonesMuted,
    required this.voiceBlocked,
    required this.cameraOn,
    required this.screenSharing,
    required this.inputVolume,
    required this.outputVolume,
    required this.musicBox,
    required this.musicBoxEnabled,
    required this.musicBoxOpen,
    required this.onJoin,
    required this.onLeave,
    required this.onToggleMic,
    required this.onToggleHeadphones,
    required this.onToggleCamera,
    required this.onToggleShare,
    required this.onInputVolumeChanged,
    required this.onOutputVolumeChanged,
    required this.onToggleMusicBox,
    required this.onMusicBoxTogglePlayback,
    required this.onMusicBoxSkip,
    required this.onCollapse,
  });

  final bool joined;
  final bool joining;
  final bool micMuted;
  final bool headphonesMuted;
  final bool voiceBlocked;
  final bool cameraOn;
  final bool screenSharing;
  final double inputVolume;
  final double outputVolume;
  final MusicBoxState? musicBox;
  final bool musicBoxEnabled;
  final bool musicBoxOpen;
  final VoidCallback? onJoin;
  final VoidCallback onLeave;
  final VoidCallback? onToggleMic;
  final VoidCallback onToggleHeadphones;
  final VoidCallback onToggleCamera;
  final VoidCallback? onToggleShare;
  final ValueChanged<double> onInputVolumeChanged;
  final ValueChanged<double> onOutputVolumeChanged;
  final VoidCallback onToggleMusicBox;
  final VoidCallback onMusicBoxTogglePlayback;
  final VoidCallback onMusicBoxSkip;
  final VoidCallback onCollapse;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxWidth < 430;
        final micControl = live_display.liveMicControlState(
          micMuted: micMuted,
          voiceBlocked: voiceBlocked,
        );
        final controls = [
          _HoverVolumeButton(
            key: const ValueKey<String>('live-control:mic'),
            value: inputVolume,
            semanticLabel: '麦克风输入音量',
            infoMessage: micControl.mutedForDisplay ? '打开麦克风' : '关闭麦克风',
            onChanged: onInputVolumeChanged,
            child: ButtonIcon(
              icon: Icon(
                micControl.mutedForDisplay ? Icons.mic_off : Icons.mic,
              ),
              selected: micControl.active,
              onPressed: micControl.enabled ? onToggleMic : null,
              size: _controlButtonSize,
            ),
          ),
          _HoverVolumeButton(
            key: const ValueKey<String>('live-control:headphones'),
            value: outputVolume,
            semanticLabel: '语音输出音量',
            infoMessage: headphonesMuted ? '打开耳机' : '关闭耳机',
            onChanged: onOutputVolumeChanged,
            child: ButtonIcon(
              icon: Icon(
                headphonesMuted ? Icons.headset_off : Icons.headphones,
              ),
              selected: !headphonesMuted,
              onPressed: onToggleHeadphones,
              size: _controlButtonSize,
            ),
          ),
          _HoverInfo(
            message: cameraOn ? '关闭摄像头' : '开启摄像头',
            child: ButtonIcon(
              key: const ValueKey<String>('live-control:camera'),
              icon: Icon(cameraOn ? Icons.videocam : Icons.videocam_outlined),
              selected: cameraOn,
              onPressed: onToggleCamera,
              size: _controlButtonSize,
            ),
          ),
          if (onToggleShare != null)
            _HoverInfo(
              message: screenSharing ? '停止共享屏幕' : '共享屏幕',
              child: ButtonIcon(
                key: const ValueKey<String>('live-control:screen-share'),
                icon: Icon(
                  screenSharing
                      ? Icons.stop_screen_share
                      : Icons.screen_share_outlined,
                ),
                selected: screenSharing,
                onPressed: onToggleShare,
                size: _controlButtonSize,
              ),
            ),
          _HoverInfo(
            message: joining ? '正在加入语音' : '离开语音频道',
            child: ButtonIcon(
              key: const ValueKey<String>('live-control:leave'),
              icon: const Icon(Icons.call_end),
              tone: ButtonTone.danger,
              onPressed: joining ? null : onLeave,
              size: _controlButtonSize,
            ),
          ),
        ];

        final musicBoxStrip = (joined && musicBoxEnabled && musicBox != null)
            ? _InlineMusicBox(
                state: musicBox!,
                expanded: musicBoxOpen,
                onTogglePlayback: onMusicBoxTogglePlayback,
                onSkip: onMusicBoxSkip,
                onToggleExpand: onToggleMusicBox,
              )
            : null;

        Widget compactCollapseButton({required bool showLabel}) {
          return _HoverInfo(
            message: '收起语音频道',
            child: showLabel
                ? Button(
                    key: const ValueKey<String>('live-control:collapse'),
                    width: double.infinity,
                    height: _controlButtonSize,
                    icon: const Icon(Icons.keyboard_arrow_up),
                    onPressed: onCollapse,
                    child: const Text('收起'),
                  )
                : ButtonIcon(
                    key: const ValueKey<String>('live-control:collapse'),
                    icon: const Icon(Icons.keyboard_arrow_up),
                    onPressed: onCollapse,
                    size: _controlButtonSize,
                  ),
          );
        }

        if (compact) {
          final joinedControlsFitOneRow =
              (controls.length + 1) * _controlButtonSize +
                  controls.length * 10 <=
              constraints.maxWidth;
          final controlSection = !joined
              ? Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _HoverInfo(
                      message: joining ? '正在加入语音' : '加入语音频道',
                      child: Button(
                        key: const ValueKey<String>('live-control:join'),
                        width: double.infinity,
                        height: _controlButtonSize,
                        loading: joining,
                        icon: const Icon(Icons.call),
                        onPressed: onJoin,
                        child: const Text('加入'),
                      ),
                    ),
                    const SizedBox(height: 10),
                    compactCollapseButton(showLabel: true),
                  ],
                )
              : joinedControlsFitOneRow
              ? Center(
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: _withControlGaps([
                      ...controls,
                      compactCollapseButton(showLabel: false),
                    ]),
                  ),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Wrap(
                      alignment: WrapAlignment.center,
                      spacing: 10,
                      runSpacing: 10,
                      children: controls,
                    ),
                    const SizedBox(height: 10),
                    compactCollapseButton(showLabel: true),
                  ],
                );
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (musicBoxStrip != null) ...[
                Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 280),
                    child: musicBoxStrip,
                  ),
                ),
                const SizedBox(height: 10),
              ],
              controlSection,
            ],
          );
        }

        // The inline music box sits on its own centered row above the
        // transport buttons, so it never competes with them for horizontal
        // space — it just ellipsizes its title/artist within the 280 cap.
        final buttonRow = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!joined) ...[
              _HoverInfo(
                message: joining ? '正在加入语音' : '加入语音频道',
                child: Button(
                  key: const ValueKey<String>('live-control:join'),
                  height: _controlButtonSize,
                  loading: joining,
                  icon: const Icon(Icons.call),
                  onPressed: onJoin,
                  child: const Text('加入'),
                ),
              ),
              const SizedBox(width: 12),
            ] else ...[
              ..._withControlGaps(controls),
              const SizedBox(width: 10),
            ],
            _HoverInfo(
              message: '收起语音频道',
              child: !joined
                  ? Button(
                      key: const ValueKey<String>('live-control:collapse'),
                      height: _controlButtonSize,
                      icon: const Icon(Icons.keyboard_arrow_up),
                      onPressed: onCollapse,
                      child: const Text('收起'),
                    )
                  : ButtonIcon(
                      key: const ValueKey<String>('live-control:collapse'),
                      icon: const Icon(Icons.keyboard_arrow_up),
                      onPressed: onCollapse,
                      size: _controlButtonSize,
                    ),
            ),
          ],
        );

        if (musicBoxStrip == null) return Center(child: buttonRow);

        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 280),
                child: musicBoxStrip,
              ),
            ),
            const SizedBox(height: 10),
            Center(child: buttonRow),
          ],
        );
      },
    );
  }
}

List<Widget> _withControlGaps(List<Widget> children) {
  return [
    for (var index = 0; index < children.length; index++) ...[
      if (index > 0) const SizedBox(width: 10),
      children[index],
    ],
  ];
}

const _hoverVolumePanelHeight = 144.0;
const _hoverVolumeTrackThickness = 7.0;
const _hoverVolumeThumbWidth = 26.0;
const _hoverVolumeThumbHeight = 7.0;
const _hoverVolumePercentWidth = 40.0;
const _hoverVolumePercentHeight = 18.0;
const _hoverVolumePercentGap = 6.0;

IconData _volumeLevelIcon(double volume) {
  if (volume <= 0) return Icons.volume_off;
  if (volume < 0.5) return Icons.volume_down;
  return Icons.volume_up;
}

class _HoverInfo extends StatelessWidget {
  const _HoverInfo({required this.message, required this.child});

  final String message;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (message.isEmpty) return child;
    return Tooltip(
      message: message,
      preferBelow: true,
      verticalOffset: _controlHoverInfoVerticalOffset,
      triggerMode: Theme.of(context).platform == TargetPlatform.android
          ? TooltipTriggerMode.manual
          : null,
      child: child,
    );
  }
}

class _HoverVolumeButton extends StatefulWidget {
  const _HoverVolumeButton({
    super.key,
    required this.child,
    required this.value,
    required this.semanticLabel,
    required this.infoMessage,
    required this.onChanged,
    this.maxValue = 1.0,
    this.valueFormatter,
    this.panelWidth = _controlButtonSize,
    this.panelHeight = _hoverVolumePanelHeight,
  });

  final Widget child;
  final double value;
  final String semanticLabel;
  final String infoMessage;
  final ValueChanged<double> onChanged;
  final double maxValue;
  final String Function(double value)? valueFormatter;
  final double panelWidth;
  final double panelHeight;

  @override
  State<_HoverVolumeButton> createState() => _HoverVolumeButtonState();
}

class _HoverVolumeButtonState extends State<_HoverVolumeButton> {
  static _HoverVolumeButtonState? _active;

  OverlayEntry? _overlayEntry;
  Timer? _hideTimer;
  bool _targetHovered = false;
  bool _overlayHovered = false;
  bool _dragging = false;
  late double _value = _normalize(widget.value);

  double get _maxValue => widget.maxValue <= 0 ? 1.0 : widget.maxValue;
  double get _panelWidth =>
      widget.panelWidth.clamp(1.0, double.infinity).toDouble();
  double get _panelHeight =>
      widget.panelHeight.clamp(1.0, double.infinity).toDouble();

  double _normalize(double value) {
    return value.clamp(0.0, _maxValue).toDouble();
  }

  @override
  void didUpdateWidget(_HoverVolumeButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value ||
        oldWidget.maxValue != widget.maxValue) {
      _value = _normalize(widget.value);
      _markOverlayNeedsBuild();
    }
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    _hideOverlay();
    super.dispose();
  }

  void _showOverlay() {
    _hideTimer?.cancel();
    if (_active != null && _active != this) {
      _active!._hideOverlay();
    }
    _active = this;
    if (_overlayEntry != null) {
      _overlayEntry!.markNeedsBuild();
      return;
    }
    final overlay = Overlay.of(context);
    final targetBox = context.findRenderObject();
    final overlayBox = overlay.context.findRenderObject();
    if (targetBox is! RenderBox || overlayBox is! RenderBox) return;
    final targetOffset = targetBox.localToGlobal(
      Offset.zero,
      ancestor: overlayBox,
    );
    final targetSize = targetBox.size;
    final targetBottomRight = targetBox.localToGlobal(
      targetSize.bottomRight(Offset.zero),
      ancestor: overlayBox,
    );
    final targetRect = Rect.fromPoints(targetOffset, targetBottomRight);
    final overlaySize = overlayBox.size;
    final horizontalScale = targetSize.width <= 0
        ? 1.0
        : targetRect.width / targetSize.width;
    final verticalScale = targetSize.height <= 0
        ? 1.0
        : targetRect.height / targetSize.height;
    final panelWidth = _panelWidth * horizontalScale;
    final panelHeight = _panelHeight * verticalScale;
    final maxPanelLeft = (overlaySize.width - panelWidth).clamp(
      0.0,
      double.infinity,
    );
    final maxPanelTop = (overlaySize.height - panelHeight).clamp(
      0.0,
      double.infinity,
    );
    final panelLeft = (targetRect.left + (targetRect.width - panelWidth) / 2)
        .clamp(0.0, maxPanelLeft)
        .toDouble();
    final panelTop = (targetRect.top - panelHeight - 8 * verticalScale)
        .clamp(0.0, maxPanelTop)
        .toDouble();
    _overlayEntry = OverlayEntry(
      builder: (context) => _buildOverlay(
        context,
        left: panelLeft,
        top: panelTop,
        width: panelWidth,
        height: panelHeight,
      ),
    );
    overlay.insert(_overlayEntry!);
  }

  void _hideOverlay() {
    _hideTimer?.cancel();
    _hideTimer = null;
    if (_active == this) _active = null;
    _overlayEntry?.remove();
    _overlayEntry = null;
  }

  void _scheduleHide({Duration delay = const Duration(milliseconds: 90)}) {
    _hideTimer?.cancel();
    _hideTimer = Timer(delay, () {
      if (!_targetHovered && !_overlayHovered && !_dragging) {
        _hideOverlay();
      }
    });
  }

  void _markOverlayNeedsBuild() {
    if (_overlayEntry == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _overlayEntry?.markNeedsBuild();
    });
  }

  void _setValue(double value) {
    final normalized = _normalize(value);
    setState(() => _value = normalized);
    _overlayEntry?.markNeedsBuild();
    widget.onChanged(normalized);
  }

  void _showOverlayForAndroidLongPress() {
    _showOverlay();
    _scheduleHide(delay: const Duration(seconds: 3));
  }

  Widget _buildOverlay(
    BuildContext context, {
    required double left,
    required double top,
    required double width,
    required double height,
  }) {
    return Positioned(
      left: left,
      top: top,
      width: width,
      height: height,
      child: MouseRegion(
        onEnter: (_) {
          _overlayHovered = true;
          _showOverlay();
        },
        onExit: (_) {
          _overlayHovered = false;
          _scheduleHide();
        },
        child: _HoverVolumePanel(
          value: _value,
          maxValue: _maxValue,
          valueFormatter: widget.valueFormatter,
          width: width,
          height: height,
          semanticLabel: widget.semanticLabel,
          onChanged: _setValue,
          onChangeStart: (_) {
            _dragging = true;
            _showOverlay();
          },
          onChangeEnd: (_) {
            _dragging = false;
            _scheduleHide();
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isAndroid = Theme.of(context).platform == TargetPlatform.android;
    return _HoverInfo(
      message: widget.infoMessage,
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onLongPressStart: isAndroid
            ? (_) => _showOverlayForAndroidLongPress()
            : null,
        child: MouseRegion(
          onEnter: (_) {
            _targetHovered = true;
            _showOverlay();
          },
          onExit: (_) {
            _targetHovered = false;
            _scheduleHide();
          },
          child: widget.child,
        ),
      ),
    );
  }
}

class _HoverVolumePanel extends StatelessWidget {
  const _HoverVolumePanel({
    required this.value,
    required this.maxValue,
    required this.width,
    required this.height,
    required this.semanticLabel,
    required this.onChanged,
    required this.onChangeStart,
    required this.onChangeEnd,
    this.valueFormatter,
  });

  final double value;
  final double maxValue;
  final double width;
  final double height;
  final String semanticLabel;
  final ValueChanged<double> onChanged;
  final ValueChanged<double> onChangeStart;
  final ValueChanged<double> onChangeEnd;
  final String Function(double value)? valueFormatter;

  @override
  Widget build(BuildContext context) {
    final normalized = value.clamp(0.0, maxValue).toDouble();
    final scale = (width / _controlButtonSize).clamp(0.65, 1.0).toDouble();
    return Semantics(
      label: semanticLabel,
      value: _volumePercentText(normalized),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surface,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(color: UiColors.border),
          boxShadow: const [
            BoxShadow(
              color: Color(0x66080A0D),
              offset: Offset(0, 8),
              blurRadius: 18,
            ),
          ],
        ),
        child: SizedBox(
          key: ValueKey<String>('live-volume-panel:$semanticLabel'),
          width: width,
          height: height,
          child: Padding(
            padding: EdgeInsets.symmetric(vertical: 12 * scale),
            child: Center(
              child: _HoverVolumeSlider(
                value: normalized,
                maxValue: maxValue,
                scale: scale,
                valueFormatter: valueFormatter,
                semanticLabel: semanticLabel,
                onChanged: onChanged,
                onChangeStart: onChangeStart,
                onChangeEnd: onChangeEnd,
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _volumePercentText(double value) {
    return valueFormatter?.call(value) ?? audioVolumePercentText(value);
  }
}

class _HoverVolumeSlider extends StatefulWidget {
  const _HoverVolumeSlider({
    required this.value,
    required this.maxValue,
    required this.scale,
    required this.semanticLabel,
    required this.onChanged,
    required this.onChangeStart,
    required this.onChangeEnd,
    this.valueFormatter,
  });

  final double value;
  final double maxValue;
  final double scale;
  final String semanticLabel;
  final ValueChanged<double> onChanged;
  final ValueChanged<double> onChangeStart;
  final ValueChanged<double> onChangeEnd;
  final String Function(double value)? valueFormatter;

  @override
  State<_HoverVolumeSlider> createState() => _HoverVolumeSliderState();
}

class _HoverVolumeSliderState extends State<_HoverVolumeSlider> {
  int? _pointer;
  bool _thumbHovered = false;
  bool _dragging = false;
  late double _interactionValue = _value;

  double get _maxValue => widget.maxValue <= 0 ? 1.0 : widget.maxValue;
  double get _scale => widget.scale.clamp(0.65, 1.0).toDouble();
  double get _thumbWidth => _hoverVolumeThumbWidth * _scale;
  double get _thumbHeight => _hoverVolumeThumbHeight * _scale;
  double get _trackThickness => _hoverVolumeTrackThickness * _scale;
  double get _percentGap => _hoverVolumePercentGap * _scale;
  double get _percentWidth => _hoverVolumePercentWidth * _scale;
  double get _percentHeight => _hoverVolumePercentHeight * _scale;

  double get _value => widget.value.clamp(0.0, _maxValue).toDouble();
  double get _fraction => (_value / _maxValue).clamp(0.0, 1.0).toDouble();

  double _fractionFromPosition(Offset localPosition, Size size) {
    final travel = size.height - _thumbHeight;
    if (travel <= 0) return 0;
    return (1 - (localPosition.dy - _thumbHeight / 2) / travel)
        .clamp(0.0, 1.0)
        .toDouble();
  }

  void _emitFromPosition(Offset localPosition) {
    final renderObject = context.findRenderObject();
    if (renderObject is! RenderBox) return;
    final fraction = _fractionFromPosition(localPosition, renderObject.size);
    final value = fraction * _maxValue;
    _interactionValue = value;
    widget.onChanged(value);
  }

  void _handlePointerDown(PointerDownEvent event) {
    if (_pointer != null) return;
    _pointer = event.pointer;
    _interactionValue = _value;
    setState(() => _dragging = true);
    widget.onChangeStart(_interactionValue);
    _emitFromPosition(event.localPosition);
  }

  void _handlePointerMove(PointerMoveEvent event) {
    if (event.pointer != _pointer) return;
    _emitFromPosition(event.localPosition);
  }

  void _handlePointerUp(PointerUpEvent event) {
    if (event.pointer != _pointer) return;
    _pointer = null;
    setState(() => _dragging = false);
    widget.onChangeEnd(_interactionValue);
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    if (event.pointer != _pointer) return;
    _pointer = null;
    setState(() => _dragging = false);
    widget.onChangeEnd(_interactionValue);
  }

  @override
  void didUpdateWidget(_HoverVolumeSlider oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_dragging) _interactionValue = _value;
  }

  @override
  Widget build(BuildContext context) {
    final percentText =
        widget.valueFormatter?.call(_value) ?? audioVolumePercentText(_value);
    final showPercent = _thumbHovered || _dragging;

    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: Listener(
        behavior: HitTestBehavior.opaque,
        onPointerDown: _handlePointerDown,
        onPointerMove: _handlePointerMove,
        onPointerUp: _handlePointerUp,
        onPointerCancel: _handlePointerCancel,
        child: SizedBox(
          key: ValueKey<String>('live-volume-slider:${widget.semanticLabel}'),
          width: _thumbWidth,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final height = constraints.maxHeight;
              final thumbHeight = _thumbHeight;
              final thumbWidth = _thumbWidth;
              final trackThickness = _trackThickness;
              final percentGap = _percentGap;
              final percentWidth = _percentWidth;
              final percentHeight = _percentHeight;
              final travel = (height - thumbHeight).clamp(0.0, double.infinity);
              final thumbBottom = travel * _fraction;
              final labelBottom = _sidePercentBottom(
                height: height,
                thumbBottom: thumbBottom,
                thumbHeight: thumbHeight,
                percentHeight: percentHeight,
              );
              return Stack(
                clipBehavior: Clip.none,
                children: [
                  _HoverVolumeTrack(
                    thumbWidth: thumbWidth,
                    trackThickness: trackThickness,
                  ),
                  Positioned(
                    left: (thumbWidth - trackThickness) / 2,
                    bottom: 0,
                    width: trackThickness,
                    height: thumbBottom,
                    child: DecoratedBox(
                      key: ValueKey<String>(
                        'live-volume-fill:${widget.semanticLabel}',
                      ),
                      decoration: BoxDecoration(
                        color: UiColors.accent,
                        borderRadius: BorderRadius.vertical(
                          bottom: Radius.circular(trackThickness / 2),
                        ),
                      ),
                    ),
                  ),
                  if (showPercent)
                    Positioned(
                      left: thumbWidth + percentGap,
                      bottom: labelBottom,
                      width: percentWidth,
                      height: percentHeight,
                      child: DecoratedBox(
                        key: ValueKey<String>(
                          'live-volume-percent:${widget.semanticLabel}',
                        ),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(UiRadii.sm),
                        ),
                        child: Center(
                          child: Text(
                            percentText,
                            style: UiTypography.label.copyWith(
                              color: Colors.black87,
                              fontSize: 10 * _scale,
                            ),
                          ),
                        ),
                      ),
                    ),
                  Positioned(
                    left: 0,
                    bottom: thumbBottom,
                    width: thumbWidth,
                    height: thumbHeight,
                    child: MouseRegion(
                      onEnter: (_) => setState(() => _thumbHovered = true),
                      onExit: (_) => setState(() => _thumbHovered = false),
                      child: DecoratedBox(
                        key: ValueKey<String>(
                          'live-volume-thumb:${widget.semanticLabel}',
                        ),
                        decoration: BoxDecoration(
                          color: UiColors.text,
                          borderRadius: BorderRadius.all(
                            Radius.circular(thumbHeight / 2),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  double _sidePercentBottom({
    required double height,
    required double thumbBottom,
    required double thumbHeight,
    required double percentHeight,
  }) {
    return (thumbBottom + (thumbHeight - percentHeight) / 2)
        .clamp(0.0, height - percentHeight)
        .toDouble();
  }
}

class _HoverVolumeTrack extends StatelessWidget {
  const _HoverVolumeTrack({
    required this.thumbWidth,
    required this.trackThickness,
  });

  final double thumbWidth;
  final double trackThickness;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: (thumbWidth - trackThickness) / 2,
      top: 0,
      bottom: 0,
      width: trackThickness,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surfacePressed,
          borderRadius: BorderRadius.circular(trackThickness / 2),
        ),
      ),
    );
  }
}

/// The now-playing strip embedded inline at the right of the live control bar:
/// a small spinning vinyl, the current track, transport controls, and a button
/// that expands the full search + queue panel. Replaces the old standalone
/// music box button so the player no longer claims its own content column.
class _InlineMusicBox extends StatelessWidget {
  const _InlineMusicBox({
    required this.state,
    required this.expanded,
    required this.onTogglePlayback,
    required this.onSkip,
    required this.onToggleExpand,
  });

  final MusicBoxState state;
  final bool expanded;
  final VoidCallback onTogglePlayback;
  final VoidCallback onSkip;
  final VoidCallback onToggleExpand;

  @override
  Widget build(BuildContext context) {
    final current = state.currentItem;
    final spinning = music_box_display.musicBoxRecordSpinning(state);
    final transport = music_box_display.musicBoxPrimaryTransport(state);
    final hasQueue = state.queue.isNotEmpty;
    final isPause =
        transport == music_box_display.MusicBoxTransportAction.pause;

    return ConstrainedBox(
      key: const ValueKey<String>('live-control:music-inline'),
      constraints: const BoxConstraints(maxWidth: 280),
      child: SizedBox(
        height: _controlButtonSize,
        child: Stack(
          children: [
            LayoutBuilder(
              builder: (context, constraints) {
                // Drop the skip button once the strip narrows so the title/artist
                // can keep shrinking instead of the whole strip bottoming out at
                // the full button-row width.
                final showSkip = constraints.maxWidth >= 175;
                return Row(
                  children: [
                    _VinylRecord(
                      spinning: spinning,
                      label: current?.title,
                      size: 26,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            current?.title ?? '未在播放',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: current == null
                                  ? UiColors.textMuted
                                  : UiColors.text,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          Text(
                            current?.artist.isNotEmpty == true
                                ? current!.artist
                                : '点一首歌开始播放',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: UiColors.textSecondary,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 6),
                    _HoverInfo(
                      message: isPause ? '暂停音乐' : '播放音乐',
                      child: ButtonIcon(
                        key: const ValueKey<String>(
                          'live-control:music-toggle',
                        ),
                        icon: Icon(isPause ? Icons.pause : Icons.play_arrow),
                        tone: ButtonTone.primary,
                        onPressed: hasQueue ? onTogglePlayback : null,
                        size: 30,
                      ),
                    ),
                    if (showSkip) ...[
                      const SizedBox(width: 4),
                      _HoverInfo(
                        message: '下一首',
                        child: ButtonIcon(
                          key: const ValueKey<String>(
                            'live-control:music-skip',
                          ),
                          icon: const Icon(Icons.skip_next),
                          onPressed: hasQueue ? onSkip : null,
                          size: 30,
                        ),
                      ),
                    ],
                    const SizedBox(width: 4),
                    _HoverInfo(
                      message: expanded ? '收起音乐面板' : '打开音乐面板',
                      child: ButtonIcon(
                        key: const ValueKey<String>('live-control:music-queue'),
                        icon: const Icon(Icons.library_music),
                        selected: expanded,
                        onPressed: onToggleExpand,
                        size: 30,
                      ),
                    ),
                  ],
                );
              },
            ),
            // A hairline progress track pinned to the bottom edge of the strip,
            // spanning its full width — keeps the title/artist lines intact
            // while still reading playback position at a glance.
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: _InlineProgress(state: state),
            ),
          ],
        ),
      ),
    );
  }
}

/// A hairline server-authoritative progress track that hugs the bottom edge of
/// the inline music box. No time labels — just a 2px bar reflecting the
/// snapshot's reported position, set on a subtly darker groove so the unplayed
/// remainder still reads against the control bar.
class _InlineProgress extends StatelessWidget {
  const _InlineProgress({required this.state});

  final MusicBoxState state;

  @override
  Widget build(BuildContext context) {
    final progress = music_box_display.musicBoxProgress(state);
    return LinearProgressIndicator(
      value: progress.fraction,
      minHeight: 3,
      backgroundColor: UiColors.surfacePressed,
      valueColor: const AlwaysStoppedAnimation(UiColors.accent),
    );
  }
}
