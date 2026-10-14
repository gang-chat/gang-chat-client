import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'button.dart';
import 'input.dart';
import 'surface.dart';
import 'tokens.dart';

enum ComposerPanelType { list, static }

enum ComposerSuggestionNavigation { previous, next }

enum ComposerSuggestionAction { confirm }

/// Lets an owner drive the composer's open panel imperatively — chiefly to
/// retract a panel (e.g. the voice recorder) once its action completes, so the
/// composer collapses back to the input without the user tapping again.
class ChatComposerController extends ChangeNotifier {
  bool _closeRequested = false;
  bool _focusRequested = false;

  bool get closeRequested => _closeRequested;
  bool get focusRequested => _focusRequested;

  /// Asks the attached [ChatComposer] to close whatever panel is open.
  void closePanel() {
    _closeRequested = true;
    notifyListeners();
  }

  void requestInputFocus() {
    _focusRequested = true;
    notifyListeners();
  }

  void _consumeCloseRequest() => _closeRequested = false;
  void _consumeFocusRequest() => _focusRequested = false;
}

class ComposerAction {
  const ComposerAction({
    required this.id,
    required this.icon,
    required this.label,
    this.tooltip,
    this.panel,
    this.onPressed,
    this.tone = ButtonTone.neutral,
    this.selected = false,
    this.alignment = ComposerActionAlignment.leading,
  });

  final String id;
  final IconData icon;
  final String label;
  final String? tooltip;
  final ComposerPanel? panel;
  final VoidCallback? onPressed;
  final ButtonTone tone;
  final bool selected;

  /// Where the action sits on the button row below the input. Trailing actions
  /// (typically send) are pinned to the far right.
  final ComposerActionAlignment alignment;

  bool get opensPanel => panel != null;
}

enum ComposerActionAlignment { leading, trailing }

class ComposerPanel {
  const ComposerPanel.list({
    required this.itemCount,
    required this.itemBuilder,
    this.height = 220,
    this.padding = const EdgeInsets.fromLTRB(12, 12, 12, 14),
  }) : type = ComposerPanelType.list,
       child = null;

  const ComposerPanel.static({
    required this.child,
    this.height = 124,
    this.padding = const EdgeInsets.fromLTRB(12, 12, 12, 14),
  }) : type = ComposerPanelType.static,
       itemCount = 0,
       itemBuilder = null;

  final ComposerPanelType type;
  final int itemCount;
  final IndexedWidgetBuilder? itemBuilder;
  final Widget? child;
  final double height;
  final EdgeInsetsGeometry padding;
}

class ChatComposer extends StatefulWidget {
  const ChatComposer({
    super.key,
    required this.actions,
    this.controller,
    this.composerController,
    this.hintText = '输入消息',
    this.minLines = 1,
    this.maxLines = 5,
    this.onSubmitted,
    this.onChanged,
    this.suggestionShortcutsEnabled = false,
    this.onSuggestionNavigationPressed,
    this.onSuggestionActionPressed,
    this.inputFormatters,
    this.onPasteFiles,
    this.onCanPasteFiles,
    this.attachments,
    this.header,
  });

  final List<ComposerAction> actions;
  final TextEditingController? controller;

  /// Optional imperative handle for closing the open panel from outside.
  final ChatComposerController? composerController;
  final String hintText;
  final int minLines;
  final int maxLines;
  final ValueChanged<String>? onSubmitted;
  final ValueChanged<String>? onChanged;
  final bool suggestionShortcutsEnabled;
  final bool Function(ComposerSuggestionNavigation navigation)?
  onSuggestionNavigationPressed;
  final bool Function(ComposerSuggestionAction action)?
  onSuggestionActionPressed;
  final List<TextInputFormatter>? inputFormatters;

  /// Invoked when the user pastes into the composer. Should stage any clipboard
  /// files/image as attachments and return true when it consumed the paste, so
  /// the composer can skip the default text paste (a copied file also carries
  /// its name as plain text on macOS).
  final Future<bool> Function()? onPasteFiles;

  /// Checks whether the clipboard currently contains files/images that
  /// [onPasteFiles] can consume. Used to keep the context-menu paste action
  /// visible for non-text clipboard contents without showing it for an empty
  /// clipboard.
  final Future<bool> Function()? onCanPasteFiles;

  /// Optional strip rendered above the input, used to show files staged for
  /// the next message. Null (or empty) leaves the composer unchanged.
  final Widget? attachments;
  final Widget? header;

  @override
  State<ChatComposer> createState() => _ChatComposerState();
}

class _ChatComposerState extends State<ChatComposer> {
  final FocusNode _inputFocusNode = FocusNode();
  TextEditingController? _localController;
  String? _openActionId;

  TextEditingController get _effectiveController =>
      widget.controller ?? _localController!;

  ComposerAction? get _openAction {
    final id = _openActionId;
    if (id == null) return null;
    for (final action in widget.actions) {
      if (action.id == id && action.panel != null) return action;
    }
    return null;
  }

  @override
  void initState() {
    super.initState();
    _localController = widget.controller == null
        ? TextEditingController()
        : null;
    widget.composerController?.addListener(_handleComposerControllerChanged);
    HardwareKeyboard.instance.addHandler(_handleKeyboardEvent);
  }

  @override
  void didUpdateWidget(ChatComposer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.composerController != widget.composerController) {
      oldWidget.composerController?.removeListener(
        _handleComposerControllerChanged,
      );
      widget.composerController?.addListener(_handleComposerControllerChanged);
    }
    if (oldWidget.controller == widget.controller) return;

    if (widget.controller == null) {
      _localController = TextEditingController.fromValue(
        oldWidget.controller?.value ?? TextEditingValue.empty,
      );
      return;
    }

    _localController?.dispose();
    _localController = null;
  }

  @override
  void dispose() {
    widget.composerController?.removeListener(_handleComposerControllerChanged);
    HardwareKeyboard.instance.removeHandler(_handleKeyboardEvent);
    _localController?.dispose();
    _inputFocusNode.dispose();
    super.dispose();
  }

  void _handleComposerControllerChanged() {
    final controller = widget.composerController;
    if (controller == null) return;
    final closeRequested = controller.closeRequested;
    final focusRequested = controller.focusRequested;
    if (!closeRequested && !focusRequested) return;
    if (closeRequested) controller._consumeCloseRequest();
    if (focusRequested) controller._consumeFocusRequest();
    if (closeRequested && _openActionId != null) {
      setState(() => _openActionId = null);
    }
    if (focusRequested) _inputFocusNode.requestFocus();
  }

  bool _handleKeyboardEvent(KeyEvent event) {
    if (!_inputFocusNode.hasFocus || event is! KeyDownEvent) {
      return false;
    }
    final keyboard = HardwareKeyboard.instance;
    if (!_isEnterKey(event.logicalKey) ||
        keyboard.isAltPressed ||
        keyboard.isMetaPressed) {
      return false;
    }

    if (keyboard.isShiftPressed || keyboard.isControlPressed) {
      _insertNewline();
      return true;
    }

    final onSubmitted = widget.onSubmitted;
    if (onSubmitted == null) return false;
    onSubmitted(_effectiveController.text);
    return true;
  }

  bool _isEnterKey(LogicalKeyboardKey key) {
    return key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.numpadEnter;
  }

  void _insertNewline() {
    final controller = _effectiveController;
    final value = controller.value;
    final text = value.text;
    final selection = value.selection;

    if (!selection.isValid) {
      final nextText = '$text\n';
      controller.value = value.copyWith(
        text: nextText,
        selection: TextSelection.collapsed(offset: nextText.length),
        composing: TextRange.empty,
      );
      widget.onChanged?.call(nextText);
      return;
    }

    final nextText =
        '${selection.textBefore(text)}\n${selection.textAfter(text)}';
    final nextOffset = selection.start + 1;
    controller.value = value.copyWith(
      text: nextText,
      selection: TextSelection.collapsed(offset: nextOffset),
      composing: TextRange.empty,
    );
    widget.onChanged?.call(nextText);
  }

  void _handleAction(ComposerAction action) {
    if (action.panel == null) {
      if (_openActionId != null) setState(() => _openActionId = null);
      action.onPressed?.call();
      return;
    }

    setState(() {
      _openActionId = _openActionId == action.id ? null : action.id;
    });
    action.onPressed?.call();
  }

  // Routes the input's paste through [_ComposerPasteAction] so a copied file
  // becomes an attachment instead of having its filename typed into the field.
  // Without an [onPasteFiles] handler the input keeps Flutter's default paste.
  Widget _wrapPaste(Widget input) {
    final onPasteFiles = widget.onPasteFiles;
    if (onPasteFiles == null) return input;
    return Actions(
      actions: <Type, Action<Intent>>{
        PasteTextIntent: _ComposerPasteAction(onPasteFiles),
      },
      child: input,
    );
  }

  Widget _wrapSuggestionShortcuts(Widget input) {
    return Shortcuts(
      shortcuts: widget.suggestionShortcutsEnabled
          ? const <ShortcutActivator, Intent>{
              SingleActivator(LogicalKeyboardKey.tab):
                  _ComposerSuggestionConfirmIntent(),
              SingleActivator(
                LogicalKeyboardKey.arrowUp,
              ): _ComposerSuggestionNavigateIntent(
                ComposerSuggestionNavigation.previous,
              ),
              SingleActivator(
                LogicalKeyboardKey.arrowDown,
              ): _ComposerSuggestionNavigateIntent(
                ComposerSuggestionNavigation.next,
              ),
            }
          : const <ShortcutActivator, Intent>{},
      child: Actions(
        actions: <Type, Action<Intent>>{
          _ComposerSuggestionConfirmIntent:
              CallbackAction<_ComposerSuggestionConfirmIntent>(
                onInvoke: (_) {
                  widget.onSuggestionActionPressed?.call(
                    ComposerSuggestionAction.confirm,
                  );
                  _inputFocusNode.requestFocus();
                  return null;
                },
              ),
          _ComposerSuggestionNavigateIntent:
              CallbackAction<_ComposerSuggestionNavigateIntent>(
                onInvoke: (intent) {
                  widget.onSuggestionNavigationPressed?.call(intent.navigation);
                  _inputFocusNode.requestFocus();
                  return null;
                },
              ),
        },
        child: input,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final openAction = _openAction;
    final openPanel = openAction?.panel;
    final leading = [
      for (final action in widget.actions)
        if (action.alignment == ComposerActionAlignment.leading) action,
    ];
    final trailing = [
      for (final action in widget.actions)
        if (action.alignment == ComposerActionAlignment.trailing) action,
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final input = _wrapSuggestionShortcuts(
          _wrapPaste(
            Input(
              controller: _effectiveController,
              focusNode: _inputFocusNode,
              hintText: widget.hintText,
              minLines: widget.minLines,
              maxLines: widget.maxLines,
              inputFormatters: widget.inputFormatters,
              canPasteNonText: widget.onCanPasteFiles,
              onSubmitted: widget.onSubmitted,
              onChanged: widget.onChanged,
              bare: true,
            ),
          ),
        );
        final composer = Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AnimatedSize(
              duration: UiMotion.standard,
              curve: Curves.easeOutCubic,
              alignment: Alignment.bottomCenter,
              child: openPanel == null
                  ? const SizedBox(width: double.infinity)
                  : Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _ComposerPanelFrame(
                        key: ValueKey(openAction!.id),
                        panel: openPanel,
                      ),
                    ),
            ),
            ListenableBuilder(
              listenable: _inputFocusNode,
              builder: (context, child) => AnimatedContainer(
                duration: UiMotion.fast,
                curve: UiMotion.curve,
                decoration: BoxDecoration(
                  color: UiColors.surface,
                  borderRadius: BorderRadius.circular(UiRadii.lg + 4),
                  border: Border.all(
                    color: _inputFocusNode.hasFocus
                        ? UiColors.accentBorder
                        : UiColors.border,
                  ),
                  boxShadow: UiShadows.raised,
                ),
                child: child,
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(UiRadii.lg + 3),
                child: _ComposerContent(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (widget.header != null)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(10, 10, 10, 0),
                          child: widget.header,
                        ),
                      if (widget.attachments != null)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(10, 10, 10, 0),
                          child: widget.attachments,
                        ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(6, 6, 6, 6),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            for (final action in leading)
                              _ComposerActionButton(
                                action: action,
                                selected:
                                    action.selected ||
                                    action.id == _openActionId,
                                onAction: _handleAction,
                              ),
                            if (leading.isNotEmpty)
                              Container(
                                width: 1,
                                height: 20,
                                margin: const EdgeInsets.only(
                                  left: 4,
                                  right: 2,
                                  bottom: 10,
                                ),
                                color: UiColors.borderStrong,
                              ),
                            Expanded(child: input),
                            for (final action in trailing)
                              _ComposerActionButton(
                                action: action,
                                selected:
                                    action.selected ||
                                    action.id == _openActionId,
                                onAction: _handleAction,
                                prominent: true,
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        );

        if (!constraints.hasBoundedWidth) return composer;
        return SizedBox(width: constraints.maxWidth, child: composer);
      },
    );
  }
}

class _ComposerSuggestionConfirmIntent extends Intent {
  const _ComposerSuggestionConfirmIntent();
}

class _ComposerSuggestionNavigateIntent extends Intent {
  const _ComposerSuggestionNavigateIntent(this.navigation);

  final ComposerSuggestionNavigation navigation;
}

class _ComposerActionButton extends StatelessWidget {
  const _ComposerActionButton({
    required this.action,
    required this.selected,
    required this.onAction,
    this.prominent = false,
  });

  final ComposerAction action;
  final bool selected;
  final ValueChanged<ComposerAction> onAction;

  /// Trailing actions (send) render as a filled round button.
  final bool prominent;

  @override
  Widget build(BuildContext context) {
    if (prominent) {
      final primary = action.tone == ButtonTone.primary;
      return Padding(
        padding: const EdgeInsets.only(left: 4),
        child: PressableSurface(
          width: 40,
          height: 40,
          tooltip: action.tooltip ?? action.label,
          selected: selected,
          onPressed: () => onAction(action),
          backgroundColor: primary
              ? UiColors.accentStrong
              : UiColors.surfaceRaised,
          selectedBackgroundColor: primary
              ? UiColors.accentStrong
              : UiColors.selected,
          borderColor: Colors.transparent,
          selectedBorderColor: Colors.transparent,
          borderRadius: UiRadii.pill,
          child: Center(
            child: Icon(
              action.icon,
              size: 19,
              color: primary ? UiColors.onAccent : UiColors.textSecondary,
            ),
          ),
        ),
      );
    }
    return ButtonIcon(
      tooltip: action.tooltip ?? action.label,
      icon: Icon(action.icon),
      tone: action.tone,
      selected: selected,
      ghost: true,
      size: 40,
      onPressed: () => onAction(action),
    );
  }
}

class _ComposerPanelFrame extends StatelessWidget {
  const _ComposerPanelFrame({super.key, required this.panel});

  final ComposerPanel panel;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surfaceRaised,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(color: UiColors.borderStrong),
        boxShadow: UiShadows.popover,
      ),
      child: SizedBox(
        width: double.infinity,
        child: switch (panel.type) {
          ComposerPanelType.list => ConstrainedBox(
            constraints: BoxConstraints(maxHeight: panel.height),
            child: Padding(
              padding: panel.padding,
              child: _ComposerWrapList(panel: panel),
            ),
          ),
          ComposerPanelType.static => SizedBox(
            height: panel.height,
            child: Padding(padding: panel.padding, child: panel.child!),
          ),
        },
      ),
    );
  }
}

class _ComposerWrapList extends StatelessWidget {
  const _ComposerWrapList({required this.panel});

  final ComposerPanel panel;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Wrap(
        spacing: 10,
        runSpacing: 10,
        children: [
          for (var index = 0; index < panel.itemCount; index++)
            panel.itemBuilder!(context, index),
        ],
      ),
    );
  }
}

class _ComposerContent extends StatelessWidget {
  const _ComposerContent({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollNotification>(
      onNotification: (_) => true,
      child: child,
    );
  }
}

/// Overrides the input's [PasteTextIntent] so a paste first tries to stage
/// clipboard files/image as attachments; only when nothing was staged does it
/// fall back to the field's default text paste (via [callingAction]). This is
/// the single paste path for both Cmd/Ctrl+V and the context-menu "Paste".
class _ComposerPasteAction extends Action<PasteTextIntent> {
  _ComposerPasteAction(this.onPasteFiles);

  final Future<bool> Function() onPasteFiles;

  @override
  bool get isActionEnabled => true;

  @override
  bool consumesKey(PasteTextIntent intent) =>
      callingAction?.consumesKey(intent) ?? true;

  @override
  Object? invoke(PasteTextIntent intent) {
    // Capture the default action now; callingAction is only valid for the
    // synchronous span of invoke, and the file check below is async.
    final fallback = callingAction;
    onPasteFiles().then((staged) {
      if (!staged) fallback?.invoke(intent);
    });
    return null;
  }
}
