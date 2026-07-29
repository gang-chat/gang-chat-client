import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'text_context_menu.dart';
import 'tokens.dart';

class ReadOnlySelectableText extends StatefulWidget {
  const ReadOnlySelectableText({
    super.key,
    required this.value,
    required this.style,
    this.fieldKey,
    this.maxLines = 1,
    this.textAlign = TextAlign.start,
    this.secondaryClickSelection,
    this.showSelectAllInContextMenu = true,
    this.contextMenuTapRegionGroupId,
    this.onContextMenuOpenChanged,
  });

  final String value;
  final TextStyle style;
  final Key? fieldKey;
  final int maxLines;
  final TextAlign textAlign;
  final TextSelection? secondaryClickSelection;
  final bool showSelectAllInContextMenu;
  final Object? contextMenuTapRegionGroupId;
  final ValueChanged<bool>? onContextMenuOpenChanged;

  @override
  State<ReadOnlySelectableText> createState() => _ReadOnlySelectableTextState();
}

class _ReadOnlySelectableTextState extends State<ReadOnlySelectableText> {
  late final TextEditingController _controller;
  final FocusNode _focusNode = FocusNode();
  final UndoHistoryController _undoController = UndoHistoryController();
  late final EditableTextContextMenuBuilder _contextMenuBuilder =
      _buildContextMenu;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.value);
  }

  @override
  void didUpdateWidget(ReadOnlySelectableText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value == widget.value) return;
    final selection = _controller.selection;
    _controller.value = TextEditingValue(
      text: widget.value,
      selection: _clampTextSelection(selection, widget.value.length),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    _undoController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextFieldEditingShortcuts(
      controller: _controller,
      focusNode: _focusNode,
      secondaryClickSelection: widget.secondaryClickSelection == null
          ? null
          : () => _clampTextSelection(
              widget.secondaryClickSelection,
              widget.value.length,
            ),
      undoController: _undoController,
      child: TextField(
        key: widget.fieldKey,
        controller: _controller,
        focusNode: _focusNode,
        readOnly: true,
        showCursor: false,
        enableInteractiveSelection: true,
        minLines: 1,
        maxLines: widget.maxLines,
        mouseCursor: SystemMouseCursors.text,
        textAlign: widget.textAlign,
        style: widget.style,
        cursorColor: UiColors.accent,
        undoController: _undoController,
        contextMenuBuilder: _contextMenuBuilder,
        decoration: const InputDecoration(
          isCollapsed: true,
          border: InputBorder.none,
          enabledBorder: InputBorder.none,
          focusedBorder: InputBorder.none,
          disabledBorder: InputBorder.none,
          contentPadding: EdgeInsets.zero,
        ),
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
      readOnly: true,
      showReadOnlySelectAll: widget.showSelectAllInContextMenu,
      tapRegionGroupId: widget.contextMenuTapRegionGroupId,
      onOpenChanged: widget.onContextMenuOpenChanged,
    );
  }
}

class ReadOnlyTextBox extends StatefulWidget {
  const ReadOnlyTextBox({
    super.key,
    required this.value,
    this.fieldKey,
    this.maxLines = 1,
    this.style,
    this.backgroundColor = UiColors.surfaceLow,
    this.borderColor = UiColors.border,
    this.padding = const EdgeInsets.fromLTRB(13, 11, 13, 11),
  });

  final String value;
  final Key? fieldKey;
  final int? maxLines;
  final TextStyle? style;
  final Color backgroundColor;
  final Color borderColor;
  final EdgeInsetsGeometry padding;

  @override
  State<ReadOnlyTextBox> createState() => _ReadOnlyTextBoxState();
}

class _ReadOnlyTextBoxState extends State<ReadOnlyTextBox> {
  late final TextEditingController _controller;
  final FocusNode _focusNode = FocusNode();
  final UndoHistoryController _undoController = UndoHistoryController();
  late final EditableTextContextMenuBuilder _contextMenuBuilder =
      _buildContextMenu;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.value);
  }

  @override
  void didUpdateWidget(ReadOnlyTextBox oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value == widget.value) return;
    final selection = _controller.selection;
    _controller.value = TextEditingValue(
      text: widget.value,
      selection: _clampTextSelection(selection, widget.value.length),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    _undoController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final style =
        widget.style ??
        UiTypography.body.copyWith(
          color: UiColors.textSecondary,
          fontWeight: FontWeight.w500,
        );
    return DecoratedBox(
      decoration: BoxDecoration(
        color: widget.backgroundColor,
        border: Border.all(color: widget.borderColor),
        borderRadius: BorderRadius.circular(UiRadii.md),
      ),
      child: TextFieldEditingShortcuts(
        controller: _controller,
        focusNode: _focusNode,
        undoController: _undoController,
        child: TextField(
          key: widget.fieldKey,
          controller: _controller,
          focusNode: _focusNode,
          readOnly: true,
          showCursor: false,
          enableInteractiveSelection: true,
          minLines: 1,
          maxLines: widget.maxLines,
          mouseCursor: SystemMouseCursors.text,
          style: style,
          cursorColor: UiColors.accent,
          undoController: _undoController,
          contextMenuBuilder: _contextMenuBuilder,
          decoration: InputDecoration(
            isDense: true,
            border: InputBorder.none,
            enabledBorder: InputBorder.none,
            focusedBorder: InputBorder.none,
            disabledBorder: InputBorder.none,
            contentPadding: widget.padding,
          ),
        ),
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
      readOnly: true,
    );
  }
}

TextSelection _clampTextSelection(TextSelection? selection, int length) {
  if (selection == null || !selection.isValid) {
    return TextSelection.collapsed(offset: length);
  }
  return TextSelection(
    baseOffset: math.min(selection.baseOffset, length),
    extentOffset: math.min(selection.extentOffset, length),
    affinity: selection.affinity,
    isDirectional: selection.isDirectional,
  );
}
