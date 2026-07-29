import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'tokens.dart';

const double _contextMenuScreenPadding = 8;
const double _contextMenuWidth = 184;
const double _contextMenuMinItemHeight = 32;
const double _contextMenuHorizontalPadding = 12;
const Key _contextMenuPanelKey = ValueKey('text-context-menu-panel');
const Key _contextMenuDividerKey = ValueKey('text-context-menu-divider');
final GlobalKey _contextMenuPanelBoundsKey = GlobalKey(
  debugLabel: 'text-context-menu-panel-bounds',
);

bool isTextContextMenuPanelHit(Offset globalPosition) {
  final renderObject = _contextMenuPanelBoundsKey.currentContext
      ?.findRenderObject();
  if (renderObject is! RenderBox || !renderObject.hasSize) return false;
  final topLeft = renderObject.localToGlobal(Offset.zero);
  return (topLeft & renderObject.size).contains(globalPosition);
}

class TextFieldEditingShortcuts extends StatefulWidget {
  const TextFieldEditingShortcuts({
    super.key,
    this.controller,
    this.focusNode,
    this.secondaryClickSelection,
    this.onGlobalPrimaryPointerDownDuringSecondaryClickProtection,
    required this.undoController,
    required this.child,
  });

  final TextEditingController? controller;
  final FocusNode? focusNode;
  final TextSelection? Function()? secondaryClickSelection;
  final ValueChanged<PointerDownEvent>?
  onGlobalPrimaryPointerDownDuringSecondaryClickProtection;
  final UndoHistoryController undoController;
  final Widget child;

  @override
  State<TextFieldEditingShortcuts> createState() =>
      _TextFieldEditingShortcutsState();
}

class _TextFieldEditingShortcutsState extends State<TextFieldEditingShortcuts> {
  TextSelection? _secondaryClickSelection;
  bool _secondaryClickHadFocus = false;
  String? _lastControllerText;
  int _secondaryClickRestoreGeneration = 0;
  bool _trackingGlobalPointers = false;
  bool _restoringSecondaryClickSelection = false;

  @override
  void initState() {
    super.initState();
    _lastControllerText = widget.controller?.text;
    widget.controller?.addListener(_handleControllerSelectionChanged);
    widget.focusNode?.addListener(_handleFocusChanged);
  }

  @override
  void didUpdateWidget(TextFieldEditingShortcuts oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.removeListener(_handleControllerSelectionChanged);
      _clearSecondaryClickProtection();
      _lastControllerText = widget.controller?.text;
      widget.controller?.addListener(_handleControllerSelectionChanged);
    }
    if (oldWidget.focusNode != widget.focusNode) {
      oldWidget.focusNode?.removeListener(_handleFocusChanged);
      _clearSecondaryClickProtection();
      widget.focusNode?.addListener(_handleFocusChanged);
    }
  }

  @override
  void dispose() {
    _clearSecondaryClickProtection();
    widget.controller?.removeListener(_handleControllerSelectionChanged);
    widget.focusNode?.removeListener(_handleFocusChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final child = Actions(
      actions: <Type, Action<Intent>>{
        _RedoShortcutIntent: CallbackAction<_RedoShortcutIntent>(
          onInvoke: (_) {
            widget.undoController.redo();
            return null;
          },
        ),
      },
      child: Shortcuts(
        shortcuts: const <ShortcutActivator, Intent>{
          SingleActivator(LogicalKeyboardKey.keyY, control: true):
              _RedoShortcutIntent(),
        },
        child: widget.child,
      ),
    );
    if (widget.controller == null && widget.focusNode == null) return child;
    return Listener(
      onPointerDown: _handlePointerDown,
      onPointerUp: _handlePointerUp,
      onPointerCancel: (_) => _clearSecondaryClickProtection(),
      child: child,
    );
  }

  void _handlePointerDown(PointerDownEvent event) {
    if ((event.buttons & kSecondaryMouseButton) == 0) {
      _clearSecondaryClickProtection();
      return;
    }
    final currentSelection = _validSelection(widget.controller?.selection);
    _secondaryClickSelection =
        _nonCollapsedSelection(currentSelection) ??
        _validSelection(widget.secondaryClickSelection?.call()) ??
        currentSelection;
    _secondaryClickHadFocus = widget.focusNode?.hasFocus ?? false;
    if (!_hasSecondaryClickProtection) {
      _clearSecondaryClickProtection();
      return;
    }
    final generation = _beginSecondaryClickSelectionProtection();
    _restoreSecondaryClickEditingState();
    _scheduleSecondaryClickEditingStateRestore(generation);
  }

  void _handlePointerUp(PointerUpEvent _) {
    if (!_hasSecondaryClickProtection) return;
    _restoreSecondaryClickEditingState();
  }

  bool get _hasSecondaryClickProtection =>
      _secondaryClickSelection != null || _secondaryClickHadFocus;

  int _beginSecondaryClickSelectionProtection() {
    final generation = ++_secondaryClickRestoreGeneration;
    if (!_trackingGlobalPointers) {
      GestureBinding.instance.pointerRouter.addGlobalRoute(
        _handleGlobalPointerEvent,
      );
      _trackingGlobalPointers = true;
    }
    return generation;
  }

  void _scheduleSecondaryClickEditingStateRestore(int generation) {
    scheduleMicrotask(
      () => _restoreSecondaryClickEditingStateIfCurrent(generation),
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _restoreSecondaryClickEditingStateIfCurrent(generation);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _restoreSecondaryClickEditingStateIfCurrent(generation);
      });
    });
  }

  void _handleGlobalPointerEvent(PointerEvent event) {
    if (event is PointerDownEvent &&
        (event.buttons & kPrimaryMouseButton) != 0) {
      widget.onGlobalPrimaryPointerDownDuringSecondaryClickProtection?.call(
        event,
      );
      _clearSecondaryClickProtection();
    }
  }

  void _clearSecondaryClickProtection() {
    _secondaryClickSelection = null;
    _secondaryClickHadFocus = false;
    _secondaryClickRestoreGeneration++;
    if (_trackingGlobalPointers) {
      GestureBinding.instance.pointerRouter.removeGlobalRoute(
        _handleGlobalPointerEvent,
      );
      _trackingGlobalPointers = false;
    }
  }

  void _restoreSecondaryClickEditingStateIfCurrent(int generation) {
    if (!mounted || generation != _secondaryClickRestoreGeneration) return;
    _restoreSecondaryClickEditingState();
  }

  void _restoreSecondaryClickEditingState() {
    _restoreSecondaryClickFocus();
    _restoreSecondaryClickSelection();
  }

  void _restoreSecondaryClickFocus() {
    final focusNode = widget.focusNode;
    if (!_secondaryClickHadFocus || focusNode == null || focusNode.hasFocus) {
      return;
    }
    if (focusNode.canRequestFocus) focusNode.requestFocus();
  }

  void _restoreSecondaryClickSelection() {
    final controller = widget.controller;
    final selection = _secondaryClickSelection;
    if (controller == null || selection == null) return;
    if (!_isSelectionValidFor(controller, selection)) {
      _clearSecondaryClickProtection();
      return;
    }
    final current = controller.selection;
    if (!current.isValid || current != selection) {
      _restoringSecondaryClickSelection = true;
      try {
        controller.selection = selection;
      } finally {
        _restoringSecondaryClickSelection = false;
      }
    }
  }

  void _handleControllerSelectionChanged() {
    final controller = widget.controller;
    final currentText = controller?.text;
    final textChanged = currentText != _lastControllerText;
    _lastControllerText = currentText;
    if (textChanged && !_restoringSecondaryClickSelection) {
      _clearSecondaryClickProtection();
      return;
    }
    if (_secondaryClickSelection == null || _restoringSecondaryClickSelection) {
      return;
    }
    _restoreSecondaryClickSelection();
  }

  void _handleFocusChanged() {
    final focusNode = widget.focusNode;
    if (!_secondaryClickHadFocus || focusNode == null || focusNode.hasFocus) {
      return;
    }
    scheduleMicrotask(_restoreSecondaryClickFocus);
  }

  TextSelection? _validSelection(TextSelection? selection) {
    if (selection == null || !selection.isValid) return null;
    return selection;
  }

  TextSelection? _nonCollapsedSelection(TextSelection? selection) {
    if (selection == null || selection.isCollapsed) return null;
    return selection;
  }

  bool _isSelectionValidFor(
    TextEditingController controller,
    TextSelection selection,
  ) {
    return selection.isValid &&
        selection.start >= 0 &&
        selection.end <= controller.text.length;
  }
}

class _RedoShortcutIntent extends Intent {
  const _RedoShortcutIntent();
}

Widget buildTextFieldContextMenu(
  BuildContext context,
  EditableTextState editableTextState, {
  UndoHistoryController? undoController,
  Object? tapRegionGroupId,
  ValueChanged<bool>? onOpenChanged,
  VoidCallback? onActionPressed,
  Future<bool> Function()? canPasteNonText,
  bool readOnly = false,
  bool showReadOnlySelectAll = true,
  bool allowCut = true,
  bool allowCopy = true,
}) {
  return _TextFieldContextMenu(
    editableTextState: editableTextState,
    undoController: undoController,
    tapRegionGroupId: tapRegionGroupId,
    onOpenChanged: onOpenChanged,
    onActionPressed: onActionPressed,
    canPasteNonText: canPasteNonText,
    readOnly: readOnly,
    showReadOnlySelectAll: showReadOnlySelectAll,
    allowCut: allowCut,
    allowCopy: allowCopy,
  );
}

class _TextFieldContextMenu extends StatefulWidget {
  const _TextFieldContextMenu({
    required this.editableTextState,
    required this.undoController,
    required this.tapRegionGroupId,
    required this.onOpenChanged,
    required this.onActionPressed,
    required this.canPasteNonText,
    required this.readOnly,
    required this.showReadOnlySelectAll,
    required this.allowCut,
    required this.allowCopy,
  });

  final EditableTextState editableTextState;
  final UndoHistoryController? undoController;
  final Object? tapRegionGroupId;
  final ValueChanged<bool>? onOpenChanged;
  final VoidCallback? onActionPressed;
  final Future<bool> Function()? canPasteNonText;
  final bool readOnly;
  final bool showReadOnlySelectAll;
  final bool allowCut;
  final bool allowCopy;

  @override
  State<_TextFieldContextMenu> createState() => _TextFieldContextMenuState();
}

class _TextFieldContextMenuState extends State<_TextFieldContextMenu> {
  bool _openNotified = false;
  bool _canPasteNonText = false;
  int _canPasteNonTextGeneration = 0;

  ClipboardStatusNotifier get _clipboardStatus =>
      widget.editableTextState.clipboardStatus;

  UndoHistoryController? get _undoController => widget.undoController;

  @override
  void initState() {
    super.initState();
    _clipboardStatus.addListener(_handleMenuStateChanged);
    _undoController?.addListener(_handleMenuStateChanged);
    _refreshCanPasteNonText();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _openNotified) return;
      _openNotified = true;
      widget.onOpenChanged?.call(true);
    });
  }

  @override
  void didUpdateWidget(_TextFieldContextMenu oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.editableTextState.clipboardStatus != _clipboardStatus) {
      oldWidget.editableTextState.clipboardStatus.removeListener(
        _handleMenuStateChanged,
      );
      _clipboardStatus.addListener(_handleMenuStateChanged);
    }
    if (oldWidget.undoController != _undoController) {
      oldWidget.undoController?.removeListener(_handleMenuStateChanged);
      _undoController?.addListener(_handleMenuStateChanged);
    }
    if (oldWidget.canPasteNonText != widget.canPasteNonText) {
      _refreshCanPasteNonText();
    }
  }

  @override
  void dispose() {
    if (_openNotified) {
      final onOpenChanged = widget.onOpenChanged;
      if (onOpenChanged != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          onOpenChanged(false);
        });
      }
    }
    _clipboardStatus.removeListener(_handleMenuStateChanged);
    _undoController?.removeListener(_handleMenuStateChanged);
    _canPasteNonTextGeneration++;
    super.dispose();
  }

  void _handleMenuStateChanged() {
    if (mounted) setState(() {});
    _refreshCanPasteNonText();
  }

  void _refreshCanPasteNonText() {
    final checker = widget.canPasteNonText;
    final generation = ++_canPasteNonTextGeneration;
    if (checker == null) {
      if (_canPasteNonText && mounted) setState(() => _canPasteNonText = false);
      return;
    }
    checker()
        .then((value) {
          if (!mounted || generation != _canPasteNonTextGeneration) return;
          if (_canPasteNonText == value) return;
          setState(() => _canPasteNonText = value);
        })
        .catchError((_) {
          if (!mounted || generation != _canPasteNonTextGeneration) return;
          if (!_canPasteNonText) return;
          setState(() => _canPasteNonText = false);
        });
  }

  @override
  Widget build(BuildContext context) {
    final sections = _sectionsFor(context);
    if (sections.isEmpty) return const SizedBox.shrink();

    final menu = _ContextMenuToolbar(
      anchors: widget.editableTextState.contextMenuAnchors,
      child: KeyedSubtree(
        key: _contextMenuPanelBoundsKey,
        child: RepaintBoundary(
          key: _contextMenuPanelKey,
          child: _ContextMenuPanel(sections: sections),
        ),
      ),
    );
    final tapRegionGroupId = widget.tapRegionGroupId;
    if (tapRegionGroupId == null) return menu;
    return TapRegion(groupId: tapRegionGroupId, child: menu);
  }

  List<_ContextMenuSection> _sectionsFor(BuildContext context) {
    if (widget.readOnly) return _readOnlySectionsFor(context);

    final editItems = <_ContextMenuItemData>[];
    final selected = _hasSelection;
    final pasteItem = _itemOfType(ContextMenuButtonType.paste);
    final canPasteText =
        pasteItem?.onPressed != null &&
        _clipboardStatus.value == ClipboardStatus.pasteable;
    final canPasteNonText = _canPasteNonText && widget.canPasteNonText != null;
    final canPaste = canPasteText || canPasteNonText;
    final pasteAction = canPasteNonText
        ? pasteItem?.onPressed ?? _pasteIntentAction()
        : pasteItem?.onPressed;
    final selectAllItem = _itemOfType(ContextMenuButtonType.selectAll);
    final selectAllAction = selectAllItem?.onPressed ?? _selectAllFallback;

    if (selected) {
      editItems.addAll([
        if (widget.allowCut)
          _entry(
            context,
            label: '剪切',
            shortcut: _ShortcutKind.cut,
            action: _itemOfType(ContextMenuButtonType.cut)?.onPressed,
          ),
        if (widget.allowCopy)
          _entry(
            context,
            label: '复制',
            shortcut: _ShortcutKind.copy,
            action: _itemOfType(ContextMenuButtonType.copy)?.onPressed,
          ),
        if (canPaste)
          _entry(
            context,
            label: '粘贴',
            shortcut: _ShortcutKind.paste,
            action: pasteAction,
          ),
        if (selectAllAction != null)
          _entry(
            context,
            label: '全选',
            shortcut: _ShortcutKind.selectAll,
            action: selectAllAction,
          ),
      ]);
    } else {
      if (canPaste) {
        editItems.add(
          _entry(
            context,
            label: '粘贴',
            shortcut: _ShortcutKind.paste,
            action: pasteAction,
          ),
        );
      }
      if (selectAllAction != null) {
        editItems.add(
          _entry(
            context,
            label: '全选',
            shortcut: _ShortcutKind.selectAll,
            action: selectAllAction,
          ),
        );
      }
    }

    final historyItems = <_ContextMenuItemData>[];
    final undoController = _undoController;
    if (undoController != null) {
      if (undoController.value.canUndo) {
        historyItems.add(
          _entry(
            context,
            label: '撤销',
            shortcut: _ShortcutKind.undo,
            action: undoController.undo,
          ),
        );
      }
      if (undoController.value.canRedo) {
        historyItems.add(
          _entry(
            context,
            label: '重做',
            shortcut: _ShortcutKind.redo,
            action: undoController.redo,
          ),
        );
      }
    }

    return [
      if (editItems.isNotEmpty) _ContextMenuSection(editItems),
      if (historyItems.isNotEmpty) _ContextMenuSection(historyItems),
    ];
  }

  List<_ContextMenuSection> _readOnlySectionsFor(BuildContext context) {
    final editItems = <_ContextMenuItemData>[];
    if (_hasSelection) {
      editItems.add(
        _entry(
          context,
          label: '复制',
          shortcut: _ShortcutKind.copy,
          action:
              _itemOfType(ContextMenuButtonType.copy)?.onPressed ??
              _copySelectionFallback,
        ),
      );
    }
    if (widget.showReadOnlySelectAll) {
      final selectAllItem = _itemOfType(ContextMenuButtonType.selectAll);
      final selectAllAction = selectAllItem?.onPressed ?? _selectAllFallback;
      if (selectAllAction != null) {
        editItems.add(
          _entry(
            context,
            label: '全选',
            shortcut: _ShortcutKind.selectAll,
            action: selectAllAction,
          ),
        );
      }
    }
    return [if (editItems.isNotEmpty) _ContextMenuSection(editItems)];
  }

  bool get _hasSelection {
    final selection = widget.editableTextState.textEditingValue.selection;
    return selection.isValid && !selection.isCollapsed;
  }

  VoidCallback? get _selectAllFallback {
    final value = widget.editableTextState.textEditingValue;
    if (value.text.isEmpty || !value.selection.isValid) return null;
    final start = value.selection.start < value.selection.end
        ? value.selection.start
        : value.selection.end;
    final end = value.selection.start < value.selection.end
        ? value.selection.end
        : value.selection.start;
    if (start == 0 && end == value.text.length) return null;
    return () {
      widget.editableTextState.selectAll(SelectionChangedCause.toolbar);
    };
  }

  VoidCallback? get _copySelectionFallback {
    final value = widget.editableTextState.textEditingValue;
    if (!value.selection.isValid || value.selection.isCollapsed) return null;
    return () {
      widget.editableTextState.copySelection(SelectionChangedCause.toolbar);
    };
  }

  ContextMenuButtonItem? _itemOfType(ContextMenuButtonType type) {
    for (final item in widget.editableTextState.contextMenuButtonItems) {
      if (item.type == type) return item;
    }
    return null;
  }

  VoidCallback _pasteIntentAction() {
    return () {
      Actions.maybeInvoke(
        widget.editableTextState.context,
        const PasteTextIntent(SelectionChangedCause.toolbar),
      );
    };
  }

  _ContextMenuItemData _entry(
    BuildContext context, {
    required String label,
    required _ShortcutKind shortcut,
    required VoidCallback? action,
  }) {
    final keepAndroidSelectionMenu =
        Theme.of(context).platform == TargetPlatform.android &&
        shortcut == _ShortcutKind.selectAll;
    return _ContextMenuItemData(
      label: label,
      shortcut: _shortcutLabel(context, shortcut),
      onPressed: action == null
          ? null
          : () {
              widget.onActionPressed?.call();
              action();
              if (!keepAndroidSelectionMenu) {
                widget.editableTextState.hideToolbar();
              }
            },
    );
  }
}

class _ContextMenuToolbar extends StatelessWidget {
  const _ContextMenuToolbar({required this.anchors, required this.child});

  final TextSelectionToolbarAnchors anchors;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final paddingAbove =
        MediaQuery.paddingOf(context).top + _contextMenuScreenPadding;
    final localAdjustment = Offset(_contextMenuScreenPadding, paddingAbove);
    final isAndroid = Theme.of(context).platform == TargetPlatform.android;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        _contextMenuScreenPadding,
        paddingAbove,
        _contextMenuScreenPadding,
        _contextMenuScreenPadding,
      ),
      child: CustomSingleChildLayout(
        delegate: isAndroid
            ? TextSelectionToolbarLayoutDelegate(
                anchorAbove:
                    anchors.primaryAnchor -
                    const Offset(0, 8) -
                    localAdjustment,
                anchorBelow:
                    (anchors.secondaryAnchor ?? anchors.primaryAnchor) +
                    const Offset(
                      0,
                      TextSelectionToolbar.kToolbarContentDistanceBelow,
                    ) -
                    localAdjustment,
              )
            : DesktopTextSelectionToolbarLayoutDelegate(
                anchor: anchors.primaryAnchor - localAdjustment,
              ),
        child: child,
      ),
    );
  }
}

class _ContextMenuPanel extends StatelessWidget {
  const _ContextMenuPanel({required this.sections});

  final List<_ContextMenuSection> sections;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints.tightFor(width: _contextMenuWidth),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surfaceRaised,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(color: UiColors.borderStrong),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.34),
              blurRadius: 18,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(UiRadii.md),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 5),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (
                  var sectionIndex = 0;
                  sectionIndex < sections.length;
                  sectionIndex++
                ) ...[
                  if (sectionIndex > 0) const _ContextMenuDivider(),
                  for (final item in sections[sectionIndex].items)
                    _ContextMenuItem(data: item),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ContextMenuItem extends StatefulWidget {
  const _ContextMenuItem({required this.data});

  final _ContextMenuItemData data;

  @override
  State<_ContextMenuItem> createState() => _ContextMenuItemState();
}

class _ContextMenuItemState extends State<_ContextMenuItem> {
  bool _hovered = false;

  bool get _enabled => widget.data.onPressed != null;

  @override
  Widget build(BuildContext context) {
    final foreground = _enabled ? UiColors.text : UiColors.textMuted;
    final shortcutColor = _enabled
        ? UiColors.textSecondary
        : UiColors.textMuted.withValues(alpha: 0.68);
    return MouseRegion(
      cursor: _enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => _setHovered(true),
      onExit: (_) => _setHovered(false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.data.onPressed,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 70),
          curve: Curves.easeOutCubic,
          constraints: const BoxConstraints(
            minHeight: _contextMenuMinItemHeight,
          ),
          padding: const EdgeInsets.symmetric(
            horizontal: _contextMenuHorizontalPadding,
            vertical: 6,
          ),
          color: _enabled && _hovered ? UiColors.selected : Colors.transparent,
          child: Row(
            children: [
              Expanded(
                child: Text(
                  widget.data.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.body.copyWith(
                    color: foreground,
                    fontSize: 13,
                    height: 1.2,
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Text(
                widget.data.shortcut,
                maxLines: 1,
                style: UiTypography.label.copyWith(
                  color: shortcutColor,
                  fontSize: 12,
                  height: 1.2,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }
}

class _ContextMenuDivider extends StatelessWidget {
  const _ContextMenuDivider();

  @override
  Widget build(BuildContext context) {
    return Padding(
      key: _contextMenuDividerKey,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: SizedBox(
        width: double.infinity,
        height: 2,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: UiColors.accent.withValues(alpha: 0.78),
          ),
        ),
      ),
    );
  }
}

class _ContextMenuSection {
  const _ContextMenuSection(this.items);

  final List<_ContextMenuItemData> items;
}

class _ContextMenuItemData {
  const _ContextMenuItemData({
    required this.label,
    required this.shortcut,
    required this.onPressed,
  });

  final String label;
  final String shortcut;
  final VoidCallback? onPressed;
}

enum _ShortcutKind { cut, copy, paste, selectAll, undo, redo }

String _shortcutLabel(BuildContext context, _ShortcutKind shortcut) {
  final mac = Theme.of(context).platform == TargetPlatform.macOS;
  return switch (shortcut) {
    _ShortcutKind.cut => mac ? '⌘X' : 'Ctrl+X',
    _ShortcutKind.copy => mac ? '⌘C' : 'Ctrl+C',
    _ShortcutKind.paste => mac ? '⌘V' : 'Ctrl+V',
    _ShortcutKind.selectAll => mac ? '⌘A' : 'Ctrl+A',
    _ShortcutKind.undo => mac ? '⌘Z' : 'Ctrl+Z',
    _ShortcutKind.redo => mac ? '⇧⌘Z' : 'Ctrl+Y',
  };
}
