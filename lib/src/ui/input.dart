import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'text_context_menu.dart';
import 'tokens.dart';

const double _inputHorizontalPadding = 12;
const double _inputIconSize = 17;
const double _inputVisualLift = 3;
const double _inputBaseDepth = 5;
const double _inputTextVerticalOffset = 4;
const double _inputIconVerticalOffset = 5;
const double _inputUnboundedBottomPadding = 8;
const double _inputClearButtonSize = 26;
const double _inputClearIconSize = 15;

class Input extends StatefulWidget {
  const Input({
    super.key,
    this.controller,
    this.focusNode,
    this.hintText = '输入消息',
    this.enabled = true,
    this.obscureText = false,
    this.autofillHints,
    this.keyboardType,
    this.textInputAction,
    this.prefixIcon,
    this.suffix,
    this.showClearButton = false,
    this.clearTooltip = '清空搜索',
    this.minLines = 1,
    this.maxLines = 5,
    this.onSubmitted,
    this.onChanged,
    this.inputFormatters,
    this.style = UiTypography.body,
    this.hintStyle = const TextStyle(color: UiColors.textMuted),
    this.textAlign = TextAlign.start,
    this.onTapOutside,
    this.tapRegionGroupId,
    this.height = defaultHeight,
    this.undoController,
    this.canPasteNonText,
    this.enableInteractiveSelection = true,
  });

  static const double defaultHeight = 40;

  final TextEditingController? controller;
  final FocusNode? focusNode;
  final String hintText;
  final bool enabled;
  final bool obscureText;
  final Iterable<String>? autofillHints;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final IconData? prefixIcon;
  final Widget? suffix;
  final bool showClearButton;
  final String clearTooltip;
  final int minLines;
  final int? maxLines;
  final ValueChanged<String>? onSubmitted;
  final ValueChanged<String>? onChanged;
  final List<TextInputFormatter>? inputFormatters;
  final TextStyle style;
  final TextStyle hintStyle;
  final TextAlign textAlign;
  final TapRegionCallback? onTapOutside;
  final Object? tapRegionGroupId;
  final UndoHistoryController? undoController;
  final Future<bool> Function()? canPasteNonText;
  final bool enableInteractiveSelection;

  /// The collapsed (single-line) height of the field. Defaults to
  /// [defaultHeight]; callers can shrink it for tighter, denser layouts.
  final double height;

  @override
  State<Input> createState() => _InputState();
}

class _InputState extends State<Input> {
  TextEditingController? _localController;
  FocusNode? _localFocusNode;
  UndoHistoryController? _localUndoController;
  late final EditableTextContextMenuBuilder _contextMenuBuilder =
      _buildContextMenu;
  String _lastControllerText = '';
  bool _hovered = false;

  TextEditingController get _effectiveController =>
      widget.controller ?? _localController!;

  FocusNode get _effectiveFocusNode => widget.focusNode ?? _localFocusNode!;

  UndoHistoryController get _effectiveUndoController =>
      widget.undoController ?? _localUndoController!;

  bool get _isSearchInput {
    final hintText = widget.hintText.trim();
    final clearTooltip = widget.clearTooltip.trim();
    return widget.prefixIcon == Icons.search ||
        hintText.contains('搜索') ||
        (widget.showClearButton && clearTooltip.contains('搜索'));
  }

  int get _effectiveMinLines =>
      widget.obscureText || _isSearchInput ? 1 : widget.minLines;

  int? get _effectiveMaxLines =>
      widget.obscureText || _isSearchInput ? 1 : widget.maxLines;

  TextInputAction? get _effectiveTextInputAction {
    if (widget.textInputAction != null) return widget.textInputAction;
    return _isSearchInput ? TextInputAction.search : null;
  }

  @override
  void initState() {
    super.initState();
    _localController = widget.controller == null
        ? TextEditingController()
        : null;
    _localFocusNode = widget.focusNode == null ? FocusNode() : null;
    _localUndoController = widget.undoController == null
        ? UndoHistoryController()
        : null;
    _lastControllerText = _effectiveController.text;
    _effectiveController.addListener(_handleTextChanged);
    _effectiveFocusNode.addListener(_handleFocusChanged);
  }

  @override
  void didUpdateWidget(Input oldWidget) {
    super.didUpdateWidget(oldWidget);
    final oldController = oldWidget.controller ?? _localController;
    final needsLocalController =
        oldWidget.controller != null && widget.controller == null;
    final noLongerNeedsLocalController =
        oldWidget.controller == null && widget.controller != null;

    if (oldWidget.controller != widget.controller) {
      oldController?.removeListener(_handleTextChanged);
      if (needsLocalController) {
        _localController = TextEditingController();
      } else if (noLongerNeedsLocalController) {
        _localController?.dispose();
        _localController = null;
      }
      _lastControllerText = _effectiveController.text;
      _effectiveController.addListener(_handleTextChanged);
    }

    final oldFocusNode = oldWidget.focusNode ?? _localFocusNode;
    final needsLocalFocusNode =
        oldWidget.focusNode != null && widget.focusNode == null;
    final noLongerNeedsLocalFocusNode =
        oldWidget.focusNode == null && widget.focusNode != null;

    if (oldWidget.focusNode != widget.focusNode) {
      oldFocusNode?.removeListener(_handleFocusChanged);
      if (needsLocalFocusNode) {
        _localFocusNode = FocusNode();
      } else if (noLongerNeedsLocalFocusNode) {
        _localFocusNode?.dispose();
        _localFocusNode = null;
      }
      _effectiveFocusNode.addListener(_handleFocusChanged);
    }

    if (oldWidget.undoController != widget.undoController) {
      if (oldWidget.undoController == null && widget.undoController != null) {
        _localUndoController?.dispose();
        _localUndoController = null;
      } else if (oldWidget.undoController != null &&
          widget.undoController == null) {
        _localUndoController = UndoHistoryController();
      }
    }
  }

  @override
  void dispose() {
    _effectiveController.removeListener(_handleTextChanged);
    _effectiveFocusNode.removeListener(_handleFocusChanged);
    _localController?.dispose();
    _localFocusNode?.dispose();
    _localUndoController?.dispose();
    super.dispose();
  }

  void _handleTextChanged() {
    final text = _effectiveController.text;
    if (_lastControllerText == text) return;
    _lastControllerText = text;
    setState(() {});
  }

  void _handleFocusChanged() {
    setState(() {});
  }

  void _handleHoverChanged(bool hovered) {
    if (_hovered == hovered) return;
    _hovered = hovered;
    if (_effectiveFocusNode.hasFocus) return;
    setState(() {});
  }

  void _clearText() {
    if (_effectiveController.text.isEmpty) return;
    _effectiveController.clear();
    widget.onChanged?.call('');
    _effectiveFocusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final focused = _effectiveFocusNode.hasFocus;
    final suffix = _effectiveSuffix();
    final content = ConstrainedBox(
      constraints: BoxConstraints(minHeight: widget.height),
      child: TextFieldEditingShortcuts(
        controller: _effectiveController,
        focusNode: _effectiveFocusNode,
        undoController: _effectiveUndoController,
        child: TextField(
          controller: _effectiveController,
          focusNode: _effectiveFocusNode,
          enabled: widget.enabled,
          obscureText: widget.obscureText,
          autofillHints: widget.autofillHints,
          keyboardType: widget.keyboardType,
          textInputAction: _effectiveTextInputAction,
          minLines: _effectiveMinLines,
          maxLines: _effectiveMaxLines,
          inputFormatters: widget.inputFormatters,
          undoController: _effectiveUndoController,
          onSubmitted: widget.onSubmitted,
          onChanged: widget.onChanged,
          enableInteractiveSelection: widget.enableInteractiveSelection,
          cursorColor: UiColors.accent,
          mouseCursor: widget.enabled
              ? SystemMouseCursors.text
              : SystemMouseCursors.basic,
          style: widget.style,
          textAlign: widget.textAlign,
          textAlignVertical: TextAlignVertical.center,
          onTapOutside: widget.onTapOutside,
          groupId: widget.tapRegionGroupId ?? EditableText,
          contextMenuBuilder: widget.enableInteractiveSelection
              ? _contextMenuBuilder
              : null,
          decoration: InputDecoration(
            isDense: true,
            hintText: widget.hintText,
            hintStyle: widget.hintStyle,
            border: InputBorder.none,
            enabledBorder: InputBorder.none,
            focusedBorder: InputBorder.none,
            disabledBorder: InputBorder.none,
            contentPadding: _contentPaddingFor(context),
            prefixIcon: widget.prefixIcon == null
                ? null
                : Padding(
                    padding: const EdgeInsets.only(
                      left: _inputHorizontalPadding,
                      right: UiSpacing.sm,
                    ),
                    child: Transform.translate(
                      offset: Offset(
                        0,
                        _verticalOffsetFor(context, icon: true),
                      ),
                      child: Icon(
                        widget.prefixIcon,
                        size: _inputIconSize,
                        color: UiColors.textMuted,
                      ),
                    ),
                  ),
            prefixIconConstraints: const BoxConstraints(
              minWidth: 0,
              minHeight: 0,
            ),
            suffixIcon: suffix == null
                ? null
                : Padding(
                    padding: const EdgeInsets.only(
                      left: UiSpacing.sm,
                      right: _inputHorizontalPadding,
                    ),
                    child: Transform.translate(
                      offset: Offset(
                        0,
                        _verticalOffsetFor(context, icon: true),
                      ),
                      child: suffix,
                    ),
                  ),
            suffixIconConstraints: const BoxConstraints(
              minWidth: 0,
              minHeight: 0,
            ),
          ),
        ),
      ),
    );

    final enabled = widget.enabled;
    final capTop = enabled && (_hovered || focused) ? 0.0 : _inputVisualLift;
    final surfaceDepth = _inputVisualLift + _inputBaseDepth;
    final background = enabled
        ? focused
              ? UiColors.selected
              : UiColors.surface
        : UiColors.disabledSurface;
    final borderColor = enabled
        ? focused
              ? UiColors.accentBorder
              : UiColors.border
        : UiColors.disabledBorder;
    final shadowColor = Color.lerp(background, Colors.black, 0.46)!;

    return MouseRegion(
      cursor: enabled ? SystemMouseCursors.text : SystemMouseCursors.basic,
      onEnter: (_) => _handleHoverChanged(true),
      onExit: (_) => _handleHoverChanged(false),
      child: Stack(
        fit: StackFit.passthrough,
        clipBehavior: Clip.hardEdge,
        children: [
          Positioned(
            left: 0,
            right: 0,
            top: surfaceDepth,
            bottom: 0,
            child: _InputLayer(
              background: shadowColor,
              borderColor: borderColor,
            ),
          ),
          AnimatedPadding(
            duration: const Duration(milliseconds: 95),
            curve: Curves.easeOutCubic,
            padding: EdgeInsets.only(
              top: capTop,
              bottom: surfaceDepth - capTop,
            ),
            child: _InputLayer(
              background: background,
              borderColor: borderColor,
              child: content,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildContextMenu(
    BuildContext context,
    EditableTextState editableTextState,
  ) {
    return buildTextFieldContextMenu(
      context,
      editableTextState,
      undoController: _effectiveUndoController,
      canPasteNonText: widget.canPasteNonText,
      tapRegionGroupId: widget.tapRegionGroupId,
      allowCut: !widget.obscureText,
      allowCopy: !widget.obscureText,
    );
  }

  Widget? _effectiveSuffix() {
    final canClear =
        widget.showClearButton &&
        widget.enabled &&
        _effectiveController.text.isNotEmpty;
    if (!canClear) return widget.suffix;
    return _InputClearButton(
      tooltip: widget.clearTooltip,
      onPressed: _clearText,
    );
  }

  EdgeInsets _contentPaddingFor(BuildContext context) {
    final painter = TextPainter(
      text: TextSpan(text: ' ', style: widget.style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
    )..layout();
    final lineHeight = painter.preferredLineHeight;
    final verticalPadding = math.max(0.0, (widget.height - lineHeight) / 2);
    final textOffset = math.min(_verticalOffsetFor(context), verticalPadding);
    final unboundedBottomPadding =
        _effectiveMaxLines == null &&
            Theme.of(context).platform != TargetPlatform.android
        ? _inputUnboundedBottomPadding
        : 0.0;
    return EdgeInsets.fromLTRB(
      _inputHorizontalPadding,
      verticalPadding + textOffset,
      _inputHorizontalPadding,
      verticalPadding - textOffset + unboundedBottomPadding,
    );
  }

  double _verticalOffsetFor(BuildContext context, {bool icon = false}) {
    if (Theme.of(context).platform == TargetPlatform.android) return 0;
    return icon ? _inputIconVerticalOffset : _inputTextVerticalOffset;
  }
}

class _InputClearButton extends StatelessWidget {
  const _InputClearButton({required this.tooltip, required this.onPressed});

  final String tooltip;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: Semantics(
        button: true,
        label: tooltip,
        child: MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: onPressed,
            child: const SizedBox.square(
              dimension: _inputClearButtonSize,
              child: Center(
                child: Icon(
                  Icons.close,
                  size: _inputClearIconSize,
                  color: UiColors.textMuted,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _InputLayer extends StatelessWidget {
  const _InputLayer({
    required this.background,
    required this.borderColor,
    this.child,
  });

  final Color background;
  final Color borderColor;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(UiRadii.md),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(color: borderColor),
        ),
        child: child ?? const SizedBox.expand(),
      ),
    );
  }
}
