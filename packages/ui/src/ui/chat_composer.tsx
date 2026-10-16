import { useLayoutEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type ReactNode } from 'react';

import { ChangeNotifier } from '@gang-chat/core/lib/change_notifier';

import { EdgeInsets, paddingStyle, type EdgeInsetsValue } from '../lib/edge_insets';
import { Column, Expanded, Row } from '../lib/layout';
import {
  FocusNode,
  TextEditingController,
  TextRange,
  TextSelection,
  type TextInputFormatter,
} from '../lib/text_editing';
import { useListenable } from '../lib/use_listenable';
import { ViewState, useViewState } from '../lib/view_state';
import { ButtonIcon, ButtonTone } from './button';
import { Icon, type IconData } from './icon';
import { Input } from './input';
import { PressableSurface } from './surface';
import { PasteTextIntentActions, type PasteTextIntentAction } from './text_context_menu';
import { UiColors, UiMotion, UiRadii, UiShadows, boxShadow } from './tokens';

export enum ComposerPanelType {
  list = 'list',
  static = 'static',
}

export enum ComposerSuggestionNavigation {
  previous = 'previous',
  next = 'next',
}

export enum ComposerSuggestionAction {
  confirm = 'confirm',
}

/**
 * Lets an owner drive the composer's open panel imperatively — chiefly to
 * retract a panel (e.g. the voice recorder) once its action completes, so the
 * composer collapses back to the input without the user tapping again.
 */
export class ChatComposerController extends ChangeNotifier {
  private _closeRequested = false;
  private _focusRequested = false;

  get closeRequested(): boolean {
    return this._closeRequested;
  }

  get focusRequested(): boolean {
    return this._focusRequested;
  }

  /** Asks the attached [ChatComposer] to close whatever panel is open. */
  closePanel(): void {
    this._closeRequested = true;
    this.notifyListeners();
  }

  requestInputFocus(): void {
    this._focusRequested = true;
    this.notifyListeners();
  }

  /** @internal (library-private in Dart) */
  _consumeCloseRequest(): void {
    this._closeRequested = false;
  }

  /** @internal (library-private in Dart) */
  _consumeFocusRequest(): void {
    this._focusRequested = false;
  }
}

export enum ComposerActionAlignment {
  leading = 'leading',
  trailing = 'trailing',
}

export class ComposerAction {
  constructor(opts: {
    id: string;
    icon: IconData;
    label: string;
    tooltip?: string | null;
    panel?: ComposerPanel | null;
    onPressed?: (() => void) | null;
    tone?: ButtonTone;
    selected?: boolean;
    alignment?: ComposerActionAlignment;
  }) {
    this.id = opts.id;
    this.icon = opts.icon;
    this.label = opts.label;
    this.tooltip = opts.tooltip ?? null;
    this.panel = opts.panel ?? null;
    this.onPressed = opts.onPressed ?? null;
    this.tone = opts.tone ?? ButtonTone.neutral;
    this.selected = opts.selected ?? false;
    this.alignment = opts.alignment ?? ComposerActionAlignment.leading;
  }

  readonly id: string;
  readonly icon: IconData;
  readonly label: string;
  readonly tooltip: string | null;
  readonly panel: ComposerPanel | null;
  readonly onPressed: (() => void) | null;
  readonly tone: ButtonTone;
  readonly selected: boolean;

  /**
   * Where the action sits on the button row below the input. Trailing actions
   * (typically send) are pinned to the far right.
   */
  readonly alignment: ComposerActionAlignment;

  get opensPanel(): boolean {
    return this.panel != null;
  }
}

export class ComposerPanel {
  private constructor(opts: {
    type: ComposerPanelType;
    itemCount: number;
    itemBuilder: ((index: number) => ReactNode) | null;
    child: ReactNode;
    height: number;
    padding: EdgeInsetsValue;
  }) {
    this.type = opts.type;
    this.itemCount = opts.itemCount;
    this.itemBuilder = opts.itemBuilder;
    this.child = opts.child;
    this.height = opts.height;
    this.padding = opts.padding;
  }

  /** Port of `ComposerPanel.list` (`IndexedWidgetBuilder` without the context). */
  static list(opts: {
    itemCount: number;
    itemBuilder: (index: number) => ReactNode;
    height?: number;
    padding?: EdgeInsetsValue;
  }): ComposerPanel {
    return new ComposerPanel({
      type: ComposerPanelType.list,
      itemCount: opts.itemCount,
      itemBuilder: opts.itemBuilder,
      child: null,
      height: opts.height ?? 220,
      padding: opts.padding ?? EdgeInsets.fromLTRB(12, 12, 12, 14),
    });
  }

  /** Port of `ComposerPanel.static`. */
  static static(opts: { child: ReactNode; height?: number; padding?: EdgeInsetsValue }): ComposerPanel {
    return new ComposerPanel({
      type: ComposerPanelType.static,
      itemCount: 0,
      itemBuilder: null,
      child: opts.child,
      height: opts.height ?? 124,
      padding: opts.padding ?? EdgeInsets.fromLTRB(12, 12, 12, 14),
    });
  }

  readonly type: ComposerPanelType;
  readonly itemCount: number;
  readonly itemBuilder: ((index: number) => ReactNode) | null;
  readonly child: ReactNode;
  readonly height: number;
  readonly padding: EdgeInsetsValue;
}

export interface ChatComposerProps {
  actions: readonly ComposerAction[];
  controller?: TextEditingController | null;

  /** Optional imperative handle for closing the open panel from outside. */
  composerController?: ChatComposerController | null;
  hintText?: string;
  minLines?: number;
  maxLines?: number;
  onSubmitted?: ((value: string) => void) | null;
  onChanged?: ((value: string) => void) | null;
  suggestionShortcutsEnabled?: boolean;
  onSuggestionNavigationPressed?: ((navigation: ComposerSuggestionNavigation) => boolean) | null;
  onSuggestionActionPressed?: ((action: ComposerSuggestionAction) => boolean) | null;
  inputFormatters?: readonly TextInputFormatter[] | null;

  /**
   * Invoked when the user pastes into the composer. Should stage any clipboard
   * files/image as attachments and return true when it consumed the paste, so
   * the composer can skip the default text paste (a copied file also carries
   * its name as plain text on macOS).
   */
  onPasteFiles?: (() => Promise<boolean>) | null;

  /**
   * Checks whether the clipboard currently contains files/images that
   * [onPasteFiles] can consume. Used to keep the context-menu paste action
   * visible for non-text clipboard contents without showing it for an empty
   * clipboard.
   */
  onCanPasteFiles?: (() => Promise<boolean>) | null;

  /**
   * Optional strip rendered above the input, used to show files staged for
   * the next message. Null (or empty) leaves the composer unchanged.
   */
  attachments?: ReactNode;
  header?: ReactNode;
}

class _ChatComposerState extends ViewState<ChatComposerProps> {
  readonly _inputFocusNode = new FocusNode();
  _localController: TextEditingController | null = null;
  _openActionId: string | null = null;
  private _pasteAction: { onPasteFiles: () => Promise<boolean>; action: _ComposerPasteAction } | null = null;

  get _effectiveController(): TextEditingController {
    return this.widget.controller ?? this._localController!;
  }

  get _openAction(): ComposerAction | null {
    const id = this._openActionId;
    if (id == null) return null;
    for (const action of this.widget.actions) {
      if (action.id === id && action.panel != null) return action;
    }
    return null;
  }

  /**
   * The local controller has to exist for the first build, which runs before
   * [initState] (`useViewState` mounts after the first render), so it is
   * created here on demand rather than in initState.
   */
  _ensureLocalController(): void {
    if (this.widget.controller == null && this._localController == null) {
      this._localController = new TextEditingController();
    }
  }

  override initState(): void {
    this._ensureLocalController();
    this.widget.composerController?.addListener(this._handleComposerControllerChanged);
  }

  override didUpdateProps(oldWidget: ChatComposerProps): void {
    if (oldWidget.composerController !== this.widget.composerController) {
      oldWidget.composerController?.removeListener(this._handleComposerControllerChanged);
      this.widget.composerController?.addListener(this._handleComposerControllerChanged);
    }
    if ((oldWidget.controller ?? null) === (this.widget.controller ?? null)) return;

    if (this.widget.controller == null) {
      this._localController = TextEditingController.fromValue(oldWidget.controller?.value ?? null);
      this.setState();
      return;
    }

    this._localController?.dispose();
    this._localController = null;
  }

  override dispose(): void {
    this.widget.composerController?.removeListener(this._handleComposerControllerChanged);
    this._localController?.dispose();
    this._inputFocusNode.dispose();
  }

  _handleComposerControllerChanged = (): void => {
    const controller = this.widget.composerController;
    if (controller == null) return;
    const closeRequested = controller.closeRequested;
    const focusRequested = controller.focusRequested;
    if (!closeRequested && !focusRequested) return;
    if (closeRequested) controller._consumeCloseRequest();
    if (focusRequested) controller._consumeFocusRequest();
    if (closeRequested && this._openActionId != null) {
      this.setState(() => {
        this._openActionId = null;
      });
    }
    if (focusRequested) this._inputFocusNode.requestFocus();
  };

  /**
   * Port of the `HardwareKeyboard` handler plus the suggestion `Shortcuts`.
   * It runs in the capture phase of the input's subtree, i.e. only while the
   * field has focus, and before the field's own key handling.
   */
  _handleKeyboardEvent = (event: ReactKeyboardEvent<HTMLDivElement>): void => {
    if (this._handleKey(event)) {
      event.preventDefault();
      event.stopPropagation();
    }
  };

  _handleKey(event: ReactKeyboardEvent<HTMLDivElement>): boolean {
    const field = _fieldTarget(event.target);
    if (field == null) return false;
    // Keys that commit an IME composition (Chinese / Japanese input) belong to
    // the IME, not to the composer.
    if (event.nativeEvent.isComposing || event.keyCode === 229) return false;

    // Enter and NumpadEnter both report `key === 'Enter'`.
    if (event.key === 'Enter' && !event.altKey && !event.metaKey) {
      if (event.shiftKey || event.ctrlKey) {
        this._insertNewline(field);
        return true;
      }
      const onSubmitted = this.widget.onSubmitted;
      if (onSubmitted == null) return false;
      onSubmitted(this._effectiveController.text);
      return true;
    }

    if (!this.widget.suggestionShortcutsEnabled) return false;
    if (event.altKey || event.metaKey || event.ctrlKey || event.shiftKey) return false;
    switch (event.key) {
      case 'Tab':
        this.widget.onSuggestionActionPressed?.(ComposerSuggestionAction.confirm);
        this._inputFocusNode.requestFocus();
        return true;
      case 'ArrowUp':
        this.widget.onSuggestionNavigationPressed?.(ComposerSuggestionNavigation.previous);
        this._inputFocusNode.requestFocus();
        return true;
      case 'ArrowDown':
        this.widget.onSuggestionNavigationPressed?.(ComposerSuggestionNavigation.next);
        this._inputFocusNode.requestFocus();
        return true;
      default:
        return false;
    }
  }

  _insertNewline(field: HTMLTextAreaElement | HTMLInputElement | null): void {
    // Typing the newline through the browser keeps its undo stack and goes
    // through the field's own input handling (formatters, onChanged).
    if (field != null) {
      try {
        if (document.execCommand('insertText', false, '\n')) return;
      } catch {
        // Fall through to editing the controller.
      }
    }
    const controller = this._effectiveController;
    const value = controller.value;
    const text = value.text;
    const selection = value.selection;

    if (!selection.isValid) {
      const nextText = `${text}\n`;
      controller.value = value.copyWith({
        text: nextText,
        selection: TextSelection.collapsed({ offset: nextText.length }),
        composing: TextRange.empty,
      });
      this.widget.onChanged?.(nextText);
      return;
    }

    const nextText = `${selection.textBefore(text)}\n${selection.textAfter(text)}`;
    const nextOffset = selection.start + 1;
    controller.value = value.copyWith({
      text: nextText,
      selection: TextSelection.collapsed({ offset: nextOffset }),
      composing: TextRange.empty,
    });
    this.widget.onChanged?.(nextText);
  }

  _handleAction = (action: ComposerAction): void => {
    if (action.panel == null) {
      if (this._openActionId != null) {
        this.setState(() => {
          this._openActionId = null;
        });
      }
      action.onPressed?.();
      return;
    }

    this.setState(() => {
      this._openActionId = this._openActionId === action.id ? null : action.id;
    });
    action.onPressed?.();
  };

  /**
   * Routes the input's paste through [_ComposerPasteAction] so a copied file
   * becomes an attachment instead of having its filename typed into the field.
   * Without an [onPasteFiles] handler the input keeps the default paste.
   */
  _wrapPaste(input: ReactNode): ReactNode {
    const onPasteFiles = this.widget.onPasteFiles ?? null;
    if (onPasteFiles == null) return input;
    if (this._pasteAction?.onPasteFiles !== onPasteFiles) {
      this._pasteAction = { onPasteFiles, action: new _ComposerPasteAction(onPasteFiles) };
    }
    return (
      <PasteTextIntentActions action={this._pasteAction.action} canPasteNonText={this.widget.onCanPasteFiles ?? null}>
        {input}
      </PasteTextIntentActions>
    );
  }

  _wrapSuggestionShortcuts(input: ReactNode): ReactNode {
    return (
      <div style={{ display: 'contents' }} onKeyDownCapture={this._handleKeyboardEvent}>
        {input}
      </div>
    );
  }
}

function _fieldTarget(target: EventTarget | null): HTMLTextAreaElement | HTMLInputElement | null {
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLInputElement) return target;
  return null;
}

export function ChatComposer(props: ChatComposerProps) {
  const state = useViewState(() => new _ChatComposerState(), props);
  state._ensureLocalController();
  useListenable(state._inputFocusNode);
  const { actions, hintText = '输入消息', minLines = 1, maxLines = 5, header = null, attachments = null } = props;

  const openAction = state._openAction;
  const openPanel = openAction?.panel ?? null;
  const leading = actions.filter((action) => action.alignment === ComposerActionAlignment.leading);
  const trailing = actions.filter((action) => action.alignment === ComposerActionAlignment.trailing);

  const input = state._wrapSuggestionShortcuts(
    state._wrapPaste(
      <Input
        controller={state._effectiveController}
        focusNode={state._inputFocusNode}
        hintText={hintText}
        minLines={minLines}
        maxLines={maxLines}
        inputFormatters={props.inputFormatters ?? null}
        canPasteNonText={props.onCanPasteFiles ?? null}
        onSubmitted={props.onSubmitted ?? null}
        onChanged={props.onChanged ?? null}
        bare
      />,
    ),
  );

  const focused = state._inputFocusNode.hasFocus;
  return (
    <Column crossAxisAlignment="stretch" mainAxisSize="min" style={{ width: '100%' }}>
      <_AnimatedSize duration={UiMotion.standard}>
        {openPanel == null || openAction == null ? null : (
          <div style={{ paddingBottom: 8 }}>
            <_ComposerPanelFrame key={openAction.id} panel={openPanel} />
          </div>
        )}
      </_AnimatedSize>
      <div
        style={{
          backgroundColor: UiColors.surface,
          borderRadius: UiRadii.lg + 4,
          border: `1px solid ${focused ? UiColors.accentBorder : UiColors.border}`,
          boxShadow: boxShadow(UiShadows.raised),
          boxSizing: 'border-box',
          transition: `border-color ${UiMotion.fast}ms ${UiMotion.curve}`,
        }}
      >
        <div style={{ borderRadius: UiRadii.lg + 3, overflow: 'hidden' }}>
          <_ComposerContent>
            <Column crossAxisAlignment="stretch" mainAxisSize="min">
              {header != null ? <div style={{ padding: '10px 10px 0' }}>{header}</div> : null}
              {attachments != null ? <div style={{ padding: '10px 10px 0' }}>{attachments}</div> : null}
              <Row crossAxisAlignment="end" style={{ padding: 6, boxSizing: 'border-box' }}>
                {leading.map((action) => (
                  <_ComposerActionButton
                    key={action.id}
                    action={action}
                    selected={action.selected || action.id === state._openActionId}
                    onAction={state._handleAction}
                  />
                ))}
                {leading.length > 0 ? (
                  <div
                    style={{
                      width: 1,
                      height: 20,
                      flexShrink: 0,
                      margin: '0 2px 10px 4px',
                      backgroundColor: UiColors.borderStrong,
                    }}
                  />
                ) : null}
                <Expanded>{input}</Expanded>
                {trailing.map((action) => (
                  <_ComposerActionButton
                    key={action.id}
                    action={action}
                    selected={action.selected || action.id === state._openActionId}
                    onAction={state._handleAction}
                    prominent
                  />
                ))}
              </Row>
            </Column>
          </_ComposerContent>
        </div>
      </div>
    </Column>
  );
}

interface _ComposerActionButtonProps {
  action: ComposerAction;
  selected: boolean;
  onAction: (action: ComposerAction) => void;

  /** Trailing actions (send) render as a filled round button. */
  prominent?: boolean;
}

function _ComposerActionButton({ action, selected, onAction, prominent = false }: _ComposerActionButtonProps) {
  if (prominent) {
    const primary = action.tone === ButtonTone.primary;
    return (
      <div style={{ paddingLeft: 4, flexShrink: 0 }}>
        <PressableSurface
          width={40}
          height={40}
          tooltip={action.tooltip ?? action.label}
          selected={selected}
          onPressed={() => onAction(action)}
          backgroundColor={primary ? UiColors.accentStrong : UiColors.surfaceRaised}
          selectedBackgroundColor={primary ? UiColors.accentStrong : UiColors.selected}
          borderColor={UiColors.transparent}
          selectedBorderColor={UiColors.transparent}
          borderRadius={UiRadii.pill}
          aria-label={action.label}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flex: '1 1 0' }}>
            <Icon icon={action.icon} size={19} color={primary ? UiColors.onAccent : UiColors.textSecondary} />
          </div>
        </PressableSurface>
      </div>
    );
  }
  return (
    <ButtonIcon
      tooltip={action.tooltip ?? action.label}
      icon={<Icon icon={action.icon} />}
      tone={action.tone}
      selected={selected}
      ghost
      size={40}
      onPressed={() => onAction(action)}
    />
  );
}

function _ComposerPanelFrame({ panel }: { panel: ComposerPanel }) {
  const decoration = {
    backgroundColor: UiColors.surfaceRaised,
    borderRadius: UiRadii.lg,
    border: `1px solid ${UiColors.borderStrong}`,
    boxShadow: boxShadow(UiShadows.popover),
    // DecoratedBox paints its border over the child, so the panel padding
    // stays measured from the outer edge.
    boxSizing: 'border-box' as const,
  };
  const padding = _insetByBorder(panel.padding);
  switch (panel.type) {
    case ComposerPanelType.list:
      return (
        <div style={{ ...decoration, width: '100%', maxHeight: panel.height, display: 'flex', flexDirection: 'column' }}>
          <div style={{ ...paddingStyle(padding), boxSizing: 'border-box', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <_ComposerWrapList panel={panel} />
          </div>
        </div>
      );
    case ComposerPanelType.static:
      return (
        <div style={{ ...decoration, width: '100%', height: panel.height, display: 'flex', flexDirection: 'column' }}>
          <div
            style={{
              ...paddingStyle(padding),
              boxSizing: 'border-box',
              flex: '1 1 0',
              minHeight: 0,
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            {panel.child}
          </div>
        </div>
      );
  }
}

function _insetByBorder(value: EdgeInsetsValue): EdgeInsetsValue {
  const all = typeof value === 'number' ? { left: value, top: value, right: value, bottom: value } : value;
  const side = (n: number | undefined) => Math.max(0, (n ?? 0) - 1);
  return {
    left: side(all.left ?? all.horizontal),
    top: side(all.top ?? all.vertical),
    right: side(all.right ?? all.horizontal),
    bottom: side(all.bottom ?? all.vertical),
  };
}

function _ComposerWrapList({ panel }: { panel: ComposerPanel }) {
  const items: ReactNode[] = [];
  for (let index = 0; index < panel.itemCount; index++) {
    items.push(<div key={index}>{panel.itemBuilder!(index)}</div>);
  }
  return (
    <div style={{ overflowY: 'auto', minHeight: 0 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>{items}</div>
    </div>
  );
}

/**
 * Port of `_ComposerContent`: the Flutter version swallowed scroll
 * notifications from the input so ancestors (the chat list) did not react to
 * the field's own scrolling. DOM scroll events do not bubble, so this is a
 * plain wrapper.
 */
function _ComposerContent({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

/**
 * Port of `AnimatedSize(duration, curve: easeOutCubic, alignment:
 * bottomCenter)`: the box tweens its height to the child's, with the child
 * pinned to the bottom edge and clipped only while the size animates.
 */
function _AnimatedSize({ duration, children }: { duration: number; children: ReactNode }) {
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [height, setHeight] = useState<number | null>(null);
  const [animating, setAnimating] = useState(false);
  useLayoutEffect(() => {
    const node = contentRef.current;
    if (node == null) return;
    const measure = () => {
      const next = node.offsetHeight;
      setHeight((current) => {
        if (current != null && current !== next) setAnimating(true);
        return next;
      });
    };
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);
  return (
    <div
      onTransitionEnd={(event) => {
        if (event.target === event.currentTarget && event.propertyName === 'height') setAnimating(false);
      }}
      style={{
        position: 'relative',
        height: height ?? 'auto',
        overflow: animating ? 'hidden' : 'visible',
        transition: `height ${duration}ms ${UiMotion.curve}`,
        flexShrink: 0,
      }}
    >
      <div ref={contentRef} style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        {children}
      </div>
    </div>
  );
}

/**
 * Overrides the input's paste (`PasteTextIntent`) so a paste first tries to
 * stage clipboard files/image as attachments; only when nothing was staged
 * does it fall back to the field's default text paste. This is the single
 * paste path for both Cmd/Ctrl+V and the context-menu "粘贴".
 */
class _ComposerPasteAction implements PasteTextIntentAction {
  constructor(readonly onPasteFiles: () => Promise<boolean>) {}

  invoke(defaultPaste: () => void): void {
    // The default paste is captured now (Flutter's callingAction is only
    // valid synchronously) and run once the file check below has settled.
    const fallback = defaultPaste;
    void this.onPasteFiles().then((staged) => {
      if (!staged) fallback();
    });
  }
}
