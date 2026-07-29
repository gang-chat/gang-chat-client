import 'package:flutter/material.dart';

import 'text_context_menu.dart';
import 'tokens.dart';

class TextInput extends StatefulWidget {
  const TextInput({
    super.key,
    this.controller,
    this.label,
    this.hint,
    this.prefixIcon,
    this.suffix,
    this.obscureText = false,
    this.minLines = 1,
    this.maxLines = 1,
    this.onSubmitted,
    this.undoController,
  });

  final TextEditingController? controller;
  final String? label;
  final String? hint;
  final IconData? prefixIcon;
  final Widget? suffix;
  final bool obscureText;
  final int minLines;
  final int maxLines;
  final ValueChanged<String>? onSubmitted;
  final UndoHistoryController? undoController;

  @override
  State<TextInput> createState() => _TextInputState();
}

class _TextInputState extends State<TextInput> {
  TextEditingController? _localController;
  final FocusNode _focusNode = FocusNode();
  UndoHistoryController? _localUndoController;
  late final EditableTextContextMenuBuilder _contextMenuBuilder =
      _buildContextMenu;

  TextEditingController get _effectiveController =>
      widget.controller ?? _localController!;

  UndoHistoryController get _effectiveUndoController =>
      widget.undoController ?? _localUndoController!;

  @override
  void initState() {
    super.initState();
    _localController = widget.controller == null
        ? TextEditingController()
        : null;
    _localUndoController = widget.undoController == null
        ? UndoHistoryController()
        : null;
  }

  @override
  void didUpdateWidget(TextInput oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      if (oldWidget.controller == null && widget.controller != null) {
        _localController?.dispose();
        _localController = null;
      } else if (oldWidget.controller != null && widget.controller == null) {
        _localController = TextEditingController();
      }
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
    _localController?.dispose();
    _focusNode.dispose();
    _localUndoController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final field = DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surface,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(color: UiColors.border),
      ),
      child: Row(
        crossAxisAlignment: widget.maxLines > 1
            ? CrossAxisAlignment.start
            : CrossAxisAlignment.center,
        children: [
          if (widget.prefixIcon != null)
            Padding(
              padding: const EdgeInsets.only(left: 12, top: 12),
              child: Icon(
                widget.prefixIcon,
                size: 18,
                color: UiColors.textMuted,
              ),
            ),
          Expanded(
            child: TextFieldEditingShortcuts(
              controller: _effectiveController,
              focusNode: _focusNode,
              undoController: _effectiveUndoController,
              child: TextField(
                controller: _effectiveController,
                focusNode: _focusNode,
                obscureText: widget.obscureText,
                minLines: widget.minLines,
                maxLines: widget.maxLines,
                undoController: _effectiveUndoController,
                onSubmitted: widget.onSubmitted,
                cursorColor: UiColors.accent,
                style: UiTypography.body,
                contextMenuBuilder: _contextMenuBuilder,
                decoration: InputDecoration(
                  isDense: true,
                  hintText: widget.hint,
                  hintStyle: const TextStyle(color: UiColors.textMuted),
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 12,
                  ),
                ),
              ),
            ),
          ),
          if (widget.suffix != null)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: widget.suffix,
            ),
        ],
      ),
    );

    if (widget.label == null) return field;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(widget.label!, style: UiTypography.label),
        const SizedBox(height: 7),
        field,
      ],
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
      allowCut: !widget.obscureText,
      allowCopy: !widget.obscureText,
    );
  }
}
