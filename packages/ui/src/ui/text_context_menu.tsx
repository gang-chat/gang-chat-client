import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ClipboardEvent as ReactClipboardEvent,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';

import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { ValueNotifier } from '@gang-chat/core/lib/change_notifier';
import { hasPlatform, platform } from '@gang-chat/core/platform/platform';

import {
  TextEditingValue,
  TextSelection,
  type FocusNode,
  type TextEditingController,
  type UndoHistoryController,
} from '../lib/text_editing';
import { useMountedRef } from '../lib/use_mounted';
import { insertOverlay, type OverlayEntryHandle } from './overlay';
import { UiColors, UiCurves, UiRadii, UiShadows, UiTypography, boxShadow, withAlpha } from './tokens';

/**
 * Port of `lib/src/ui/text_context_menu.dart`: the app's own text selection
 * menu (剪切 / 复制 / 粘贴 / 全选 / 撤销 / 重做) for editable and read-only text.
 *
 * Flutter hands `contextMenuBuilder` to `TextField`, which calls it with its
 * `EditableTextState` on a secondary click. A DOM `<input>` / `<textarea>`
 * has no such hook, so in this port [TextFieldEditingShortcuts] — the wrapper
 * every text field already sits in — owns it: it handles `contextmenu` on its
 * subtree, builds an [EditableTextState] from the field under the pointer
 * (or from the DOM selection when the subtree is selectable text rather than
 * a field), and shows the menu returned by its `contextMenuBuilder` prop
 * (default: [buildTextFieldContextMenu] with the field's read-only / password
 * state) in an overlay entry:
 *
 * ```tsx
 * <TextFieldEditingShortcuts
 *   controller={controller}
 *   focusNode={focusNode}
 *   undoController={undoController}
 *   contextMenuBuilder={(state) => buildTextFieldContextMenu(state, { undoController, canPasteNonText })}
 * >
 *   <textarea … />
 * </TextFieldEditingShortcuts>
 * ```
 */

const _contextMenuScreenPadding = 8;
const _contextMenuWidth = 196;
const _contextMenuMinItemHeight = 32;
const _contextMenuHorizontalPadding = 10;

// ---------------------------------------------------------------------------
// Flutter framework types the menu reads (`EditableTextState`,
// `ContextMenuButtonItem`, `ClipboardStatus`, …). The controllers and
// `TextSelection` / `TextEditingValue` come from `lib/text_editing`.

/** A point in window coordinates (Flutter global `Offset`). */
export interface TextMenuOffset {
  x: number;
  y: number;
}

export enum ClipboardStatus {
  pasteable = 'pasteable',
  unknown = 'unknown',
  notPasteable = 'notPasteable',
}

export enum ContextMenuButtonType {
  cut = 'cut',
  copy = 'copy',
  paste = 'paste',
  selectAll = 'selectAll',
  delete = 'delete',
  custom = 'custom',
}

export interface ContextMenuButtonItem {
  readonly type: ContextMenuButtonType;
  readonly onPressed: (() => void) | null;
}

export enum SelectionChangedCause {
  tap = 'tap',
  longPress = 'longPress',
  keyboard = 'keyboard',
  toolbar = 'toolbar',
}

export interface TextSelectionToolbarAnchors {
  readonly primaryAnchor: TextMenuOffset;
  readonly secondaryAnchor: TextMenuOffset | null;
}

/**
 * The editable-text state a context menu is built from (Flutter
 * `EditableTextState`), implemented over the DOM field or selection.
 */
export interface EditableTextState {
  readonly textEditingValue: TextEditingValue;
  readonly clipboardStatus: ValueNotifier<ClipboardStatus>;
  readonly contextMenuAnchors: TextSelectionToolbarAnchors;
  readonly contextMenuButtonItems: readonly ContextMenuButtonItem[];
  /** Whether the field is read-only (`EditableText.readOnly`). */
  readonly readOnly: boolean;
  /** Whether the field hides its text (`EditableText.obscureText`). */
  readonly obscureText: boolean;
  selectAll(cause: SelectionChangedCause): void;
  copySelection(cause: SelectionChangedCause): void;
  hideToolbar(): void;
  /**
   * `Actions.maybeInvoke(editableTextState.context, PasteTextIntent(toolbar))`:
   * pastes through the nearest [PasteTextIntentActions] override, falling back
   * to the field's plain text paste.
   */
  invokePasteTextIntent(): void;
}

/** The parts of a `PointerDownEvent` the selection protection reports. */
export interface TextPointerDownEvent {
  /** Window position (`PointerDownEvent.position`). */
  readonly position: TextMenuOffset;
  /** Pressed-button bit mask (primary = 1, secondary = 2). */
  readonly buttons: number;
}

// ---------------------------------------------------------------------------
// Paste override (`Actions(actions: {PasteTextIntent: …})`).

/**
 * Port of an `Action<PasteTextIntent>` override: [invoke] receives the
 * field's default text paste (Flutter's `callingAction`) so it can fall back
 * to it.
 */
export interface PasteTextIntentAction {
  invoke(defaultPaste: () => void): void;
}

interface _PasteTextIntentScopeValue {
  action: PasteTextIntentAction;
  /**
   * PORT: lets a field whose owner did not pass `canPasteNonText` to
   * [buildTextFieldContextMenu] still keep 粘贴 visible for non-text
   * clipboards (used by [TextFieldEditingShortcuts]' default builder).
   */
  canPasteNonText: (() => Promise<boolean>) | null;
}

const _PasteTextIntentScope = createContext<_PasteTextIntentScopeValue | null>(null);

export interface PasteTextIntentActionsProps {
  /** The `PasteTextIntent` override; null keeps the default paste. */
  action: PasteTextIntentAction | null;
  /** Optional non-text clipboard probe for the default context menu. */
  canPasteNonText?: (() => Promise<boolean>) | null;
  children?: ReactNode;
}

/**
 * Port of `Actions(actions: {PasteTextIntent: action}, child: …)`.
 *
 * Both the keyboard paste (Cmd/Ctrl+V — the DOM `paste` event) and the
 * context-menu 粘贴 of any field below run through [action]. The wrapper is
 * `display: contents`, so it does not take part in layout.
 */
export function PasteTextIntentActions({ action, canPasteNonText = null, children }: PasteTextIntentActionsProps) {
  const value = useMemo(() => (action == null ? null : { action, canPasteNonText }), [action, canPasteNonText]);
  const onPaste = (event: ReactClipboardEvent<HTMLDivElement>) => {
    if (action == null || event.defaultPrevented) return;
    const field = _editableTarget(event.target, event.currentTarget);
    if (field == null || field.readOnly || field.disabled) return;
    // The override decides asynchronously whether it consumed the paste, so
    // take the browser's text now and insert it ourselves if it did not.
    event.preventDefault();
    const text = event.clipboardData.getData('text/plain');
    const selection = _fieldSelection(field);
    action.invoke(() => _insertText(field, text, selection));
  };
  return (
    <_PasteTextIntentScope.Provider value={value}>
      <div style={_contentsStyle} onPaste={onPaste}>
        {children}
      </div>
    </_PasteTextIntentScope.Provider>
  );
}

// ---------------------------------------------------------------------------

let _contextMenuPanel: HTMLElement | null = null;

/** Whether [globalPosition] (window coordinates) hits the open text menu. */
export function isTextContextMenuPanelHit(globalPosition: TextMenuOffset): boolean {
  const panel = _contextMenuPanel;
  if (panel == null || !panel.isConnected) return false;
  const rect = panel.getBoundingClientRect();
  return (
    globalPosition.x >= rect.left && globalPosition.y >= rect.top && globalPosition.x < rect.right && globalPosition.y < rect.bottom
  );
}

export interface TextFieldEditingShortcutsProps {
  controller?: TextEditingController | null;
  focusNode?: FocusNode | null;
  /** Selection to keep while a secondary click opens the menu. */
  secondaryClickSelection?: (() => TextSelection | null) | null;
  onGlobalPrimaryPointerDownDuringSecondaryClickProtection?: ((event: TextPointerDownEvent) => void) | null;
  undoController: UndoHistoryController | null;
  /**
   * PORT: Flutter's `TextField.contextMenuBuilder`, hosted here because this
   * wrapper owns the context-menu hookup. Defaults to
   * [buildTextFieldContextMenu] with the field's own read-only / password
   * state and [undoController]. Return null for no menu.
   */
  contextMenuBuilder?: ((editableTextState: EditableTextState) => ReactNode) | null;
  children?: ReactNode;
}

/**
 * Port of `TextFieldEditingShortcuts`: Ctrl+Y redo, the secondary-click
 * selection protection and (in this port) the context menu for the text
 * field(s) it wraps. The wrapper is `display: contents`.
 *
 * Flutter collapsed the selection and dropped focus on a right click before
 * the menu read them, so it restored both. A DOM field keeps its selection
 * and focus through `contextmenu`, so only the protection's other effect is
 * kept: while it is active (after a secondary pointer-down), the next primary
 * pointer-down anywhere reports
 * [onGlobalPrimaryPointerDownDuringSecondaryClickProtection].
 */
export function TextFieldEditingShortcuts(props: TextFieldEditingShortcutsProps) {
  const paste = useContext(_PasteTextIntentScope);
  const propsRef = useRef(props);
  propsRef.current = props;
  const pasteRef = useRef(paste);
  pasteRef.current = paste;
  const protection = useRef<(() => void) | null>(null);

  const clearSecondaryClickProtection = () => {
    protection.current?.();
    protection.current = null;
  };
  useEffect(() => clearSecondaryClickProtection, []);

  // The controller's text changed: Flutter drops the protection.
  const controller = props.controller ?? null;
  useEffect(() => {
    if (controller == null) return;
    let lastText = controller.text;
    const listener = () => {
      if (controller.text === lastText) return;
      lastText = controller.text;
      clearSecondaryClickProtection();
    };
    controller.addListener(listener);
    return () => controller.removeListener(listener);
  }, [controller]);

  const build = (state: EditableTextState): ReactNode => {
    const { contextMenuBuilder = null, undoController } = propsRef.current;
    if (contextMenuBuilder != null) return contextMenuBuilder(state);
    return buildTextFieldContextMenu(state, {
      undoController,
      readOnly: state.readOnly,
      allowCut: !state.obscureText,
      allowCopy: !state.obscureText,
      canPasteNonText: pasteRef.current?.canPasteNonText ?? null,
    });
  };

  const onContextMenu = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (event.defaultPrevented) return;
    event.preventDefault();
    const anchor = { x: event.clientX, y: event.clientY };
    const field = _editableTarget(event.target, event.currentTarget);
    const state =
      field != null
        ? _fieldState(field, anchor, () => pasteRef.current?.action ?? null)
        : _domSelectionState(event.currentTarget, anchor);
    _showMenu(build, state);
  };

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.defaultPrevented) return;
    // SingleActivator(LogicalKeyboardKey.keyY, control: true) → redo.
    if (event.key.toLowerCase() !== 'y' || !event.ctrlKey || event.shiftKey || event.altKey || event.metaKey) return;
    const undoController = propsRef.current.undoController;
    if (undoController == null) return;
    event.preventDefault();
    undoController.redo();
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if ((event.buttons & 2) === 0) {
      clearSecondaryClickProtection();
      return;
    }
    clearSecondaryClickProtection();
    const onGlobalPointerDown = (global: PointerEvent) => {
      if ((global.buttons & 1) === 0) return;
      propsRef.current.onGlobalPrimaryPointerDownDuringSecondaryClickProtection?.({
        position: { x: global.clientX, y: global.clientY },
        buttons: global.buttons,
      });
      clearSecondaryClickProtection();
    };
    document.addEventListener('pointerdown', onGlobalPointerDown, true);
    protection.current = () => document.removeEventListener('pointerdown', onGlobalPointerDown, true);
  };

  return (
    <div style={_contentsStyle} onContextMenu={onContextMenu} onKeyDown={onKeyDown} onPointerDown={onPointerDown}>
      {props.children}
    </div>
  );
}

export interface BuildTextFieldContextMenuOptions {
  undoController?: UndoHistoryController | null;
  /** Kept for call-site parity; taps outside always close the menu. */
  tapRegionGroupId?: unknown;
  onOpenChanged?: ((open: boolean) => void) | null;
  onActionPressed?: (() => void) | null;
  canPasteNonText?: (() => Promise<boolean>) | null;
  readOnly?: boolean;
  showReadOnlySelectAll?: boolean;
  allowCut?: boolean;
  allowCopy?: boolean;
}

/** Port of `buildTextFieldContextMenu` (the `BuildContext` argument is dropped). */
export function buildTextFieldContextMenu(editableTextState: EditableTextState, opts: BuildTextFieldContextMenuOptions = {}): ReactNode {
  const {
    undoController = null,
    onOpenChanged = null,
    onActionPressed = null,
    canPasteNonText = null,
    readOnly = false,
    showReadOnlySelectAll = true,
    allowCut = true,
    allowCopy = true,
  } = opts;
  return (
    <_TextFieldContextMenu
      editableTextState={editableTextState}
      undoController={undoController}
      onOpenChanged={onOpenChanged}
      onActionPressed={onActionPressed}
      canPasteNonText={canPasteNonText}
      readOnly={readOnly}
      showReadOnlySelectAll={showReadOnlySelectAll}
      allowCut={allowCut}
      allowCopy={allowCopy}
    />
  );
}

interface _TextFieldContextMenuProps {
  editableTextState: EditableTextState;
  undoController: UndoHistoryController | null;
  onOpenChanged: ((open: boolean) => void) | null;
  onActionPressed: (() => void) | null;
  canPasteNonText: (() => Promise<boolean>) | null;
  readOnly: boolean;
  showReadOnlySelectAll: boolean;
  allowCut: boolean;
  allowCopy: boolean;
}

enum _ShortcutKind {
  cut = 'cut',
  copy = 'copy',
  paste = 'paste',
  selectAll = 'selectAll',
  undo = 'undo',
  redo = 'redo',
}

interface _ContextMenuItemData {
  label: string;
  shortcut: string;
  onPressed: (() => void) | null;
}

type _ContextMenuSection = _ContextMenuItemData[];

function _TextFieldContextMenu(props: _TextFieldContextMenuProps) {
  const { editableTextState, undoController, canPasteNonText } = props;
  const mounted = useMountedRef();
  const [, setVersion] = useState(0);
  const [canPasteNonTextValue, setCanPasteNonText] = useState(false);
  const canPasteNonTextRef = useRef(false);
  canPasteNonTextRef.current = canPasteNonTextValue;
  const generation = useRef(0);
  const propsRef = useRef(props);
  propsRef.current = props;

  const refreshCanPasteNonText = () => {
    const checker = propsRef.current.canPasteNonText;
    const current = ++generation.current;
    if (checker == null) {
      if (canPasteNonTextRef.current && mounted.current) setCanPasteNonText(false);
      return;
    }
    checker()
      .then((value) => {
        if (!mounted.current || current !== generation.current) return;
        if (canPasteNonTextRef.current === value) return;
        setCanPasteNonText(value);
      })
      .catch(() => {
        if (!mounted.current || current !== generation.current) return;
        if (!canPasteNonTextRef.current) return;
        setCanPasteNonText(false);
      });
  };

  // Rebuild when the clipboard status or the undo history changes.
  useEffect(() => {
    const handleMenuStateChanged = () => {
      if (mounted.current) setVersion((v) => v + 1);
      refreshCanPasteNonText();
    };
    const clipboardStatus = editableTextState.clipboardStatus;
    clipboardStatus.addListener(handleMenuStateChanged);
    undoController?.addListener(handleMenuStateChanged);
    return () => {
      clipboardStatus.removeListener(handleMenuStateChanged);
      undoController?.removeListener(handleMenuStateChanged);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editableTextState, undoController]);

  useEffect(() => {
    refreshCanPasteNonText();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canPasteNonText]);

  // onOpenChanged(true) after the first frame, onOpenChanged(false) after dispose.
  useEffect(() => {
    let openNotified = false;
    const frame = requestAnimationFrame(() => {
      if (!mounted.current) return;
      openNotified = true;
      propsRef.current.onOpenChanged?.(true);
    });
    return () => {
      cancelAnimationFrame(frame);
      generation.current += 1;
      if (openNotified) {
        const onOpenChanged = propsRef.current.onOpenChanged;
        if (onOpenChanged != null) requestAnimationFrame(() => onOpenChanged(false));
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sections = _sectionsFor(props, canPasteNonTextValue);
  if (sections.length === 0) return null;
  return (
    <_ContextMenuToolbar anchors={editableTextState.contextMenuAnchors}>
      <_ContextMenuPanel sections={sections} />
    </_ContextMenuToolbar>
  );
}

function _hasSelection(state: EditableTextState): boolean {
  const selection = state.textEditingValue.selection;
  return selection.isValid && !selection.isCollapsed;
}

function _itemOfType(state: EditableTextState, type: ContextMenuButtonType): ContextMenuButtonItem | null {
  for (const item of state.contextMenuButtonItems) {
    if (item.type === type) return item;
  }
  return null;
}

function _selectAllFallback(state: EditableTextState): (() => void) | null {
  const value = state.textEditingValue;
  if (value.text.length === 0 || !value.selection.isValid) return null;
  const start = value.selection.start;
  const end = value.selection.end;
  if (start === 0 && end === value.text.length) return null;
  return () => state.selectAll(SelectionChangedCause.toolbar);
}

function _copySelectionFallback(state: EditableTextState): (() => void) | null {
  const value = state.textEditingValue;
  if (!value.selection.isValid || value.selection.isCollapsed) return null;
  return () => state.copySelection(SelectionChangedCause.toolbar);
}

function _entry(
  props: _TextFieldContextMenuProps,
  opts: { label: string; shortcut: _ShortcutKind; action: (() => void) | null },
): _ContextMenuItemData {
  const { label, shortcut, action } = opts;
  const keepAndroidSelectionMenu = HostPlatform.isAndroid && shortcut === _ShortcutKind.selectAll;
  return {
    label,
    shortcut: _shortcutLabel(shortcut),
    onPressed:
      action == null
        ? null
        : () => {
            props.onActionPressed?.();
            action();
            if (!keepAndroidSelectionMenu) props.editableTextState.hideToolbar();
          },
  };
}

function _sectionsFor(props: _TextFieldContextMenuProps, canPasteNonTextValue: boolean): _ContextMenuSection[] {
  const state = props.editableTextState;
  if (props.readOnly) return _readOnlySectionsFor(props);

  const editItems: _ContextMenuItemData[] = [];
  const selected = _hasSelection(state);
  const pasteItem = _itemOfType(state, ContextMenuButtonType.paste);
  const canPasteText = pasteItem?.onPressed != null && state.clipboardStatus.value === ClipboardStatus.pasteable;
  const canPasteNonText = canPasteNonTextValue && props.canPasteNonText != null;
  const canPaste = canPasteText || canPasteNonText;
  // Always route editable composer paste through PasteTextIntent when it
  // provides a non-text checker. On Android the system menu can be tapped
  // before the asynchronous clipboard probe finishes; invoking the native
  // paste button directly in that window inserts the readable fallback text
  // and bypasses structured song/playlist restoration. The composer action
  // falls back to the normal text paste when nothing was consumed.
  const pasteAction = props.canPasteNonText != null ? () => state.invokePasteTextIntent() : (pasteItem?.onPressed ?? null);
  const selectAllItem = _itemOfType(state, ContextMenuButtonType.selectAll);
  const selectAllAction = selectAllItem?.onPressed ?? _selectAllFallback(state);

  if (selected) {
    if (props.allowCut) {
      editItems.push(
        _entry(props, { label: '剪切', shortcut: _ShortcutKind.cut, action: _itemOfType(state, ContextMenuButtonType.cut)?.onPressed ?? null }),
      );
    }
    if (props.allowCopy) {
      editItems.push(
        _entry(props, { label: '复制', shortcut: _ShortcutKind.copy, action: _itemOfType(state, ContextMenuButtonType.copy)?.onPressed ?? null }),
      );
    }
    if (canPaste) editItems.push(_entry(props, { label: '粘贴', shortcut: _ShortcutKind.paste, action: pasteAction }));
    if (selectAllAction != null) {
      editItems.push(_entry(props, { label: '全选', shortcut: _ShortcutKind.selectAll, action: selectAllAction }));
    }
  } else {
    if (canPaste) editItems.push(_entry(props, { label: '粘贴', shortcut: _ShortcutKind.paste, action: pasteAction }));
    if (selectAllAction != null) {
      editItems.push(_entry(props, { label: '全选', shortcut: _ShortcutKind.selectAll, action: selectAllAction }));
    }
  }

  const historyItems: _ContextMenuItemData[] = [];
  const undoController = props.undoController;
  if (undoController != null) {
    if (undoController.canUndo) {
      historyItems.push(_entry(props, { label: '撤销', shortcut: _ShortcutKind.undo, action: () => undoController.undo() }));
    }
    if (undoController.canRedo) {
      historyItems.push(_entry(props, { label: '重做', shortcut: _ShortcutKind.redo, action: () => undoController.redo() }));
    }
  }

  const sections: _ContextMenuSection[] = [];
  if (editItems.length > 0) sections.push(editItems);
  if (historyItems.length > 0) sections.push(historyItems);
  return sections;
}

function _readOnlySectionsFor(props: _TextFieldContextMenuProps): _ContextMenuSection[] {
  const state = props.editableTextState;
  const editItems: _ContextMenuItemData[] = [];
  if (_hasSelection(state)) {
    editItems.push(
      _entry(props, {
        label: '复制',
        shortcut: _ShortcutKind.copy,
        action: _itemOfType(state, ContextMenuButtonType.copy)?.onPressed ?? _copySelectionFallback(state),
      }),
    );
  }
  if (props.showReadOnlySelectAll) {
    const selectAllItem = _itemOfType(state, ContextMenuButtonType.selectAll);
    const selectAllAction = selectAllItem?.onPressed ?? _selectAllFallback(state);
    if (selectAllAction != null) {
      editItems.push(_entry(props, { label: '全选', shortcut: _ShortcutKind.selectAll, action: selectAllAction }));
    }
  }
  return editItems.length > 0 ? [editItems] : [];
}

/**
 * Port of `_ContextMenuToolbar`: lays the panel out like Flutter's
 * `DesktopTextSelectionToolbarLayoutDelegate` — its top-left at the anchor,
 * shifted back inside the padded window when it would overhang. Android uses
 * the above/below placement of `TextSelectionToolbarLayoutDelegate`.
 */
function _ContextMenuToolbar({ anchors, children }: { anchors: TextSelectionToolbarAnchors; children: ReactNode }) {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const [position, setPosition] = useState<{ left: number; top: number } | null>(null);

  // Measure before paint, then place (CustomSingleChildLayout).
  useLayoutEffect(() => {
    const panel = panelRef.current;
    if (panel == null) return;
    _contextMenuPanel = panel;
    const width = panel.offsetWidth;
    const height = panel.offsetHeight;
    const paddingAbove = _contextMenuScreenPadding;
    const areaWidth = window.innerWidth - _contextMenuScreenPadding * 2;
    const areaHeight = window.innerHeight - paddingAbove - _contextMenuScreenPadding;
    let left: number;
    let top: number;
    if (HostPlatform.isAndroid) {
      // TextSelectionToolbarLayoutDelegate: above the selection when it fits,
      // otherwise below it.
      const anchorAbove = { x: anchors.primaryAnchor.x - _contextMenuScreenPadding, y: anchors.primaryAnchor.y - 8 - paddingAbove };
      const below = anchors.secondaryAnchor ?? anchors.primaryAnchor;
      const anchorBelow = { x: below.x - _contextMenuScreenPadding, y: below.y + 18 - paddingAbove };
      const fitsAbove = anchorAbove.y >= height;
      const anchor = fitsAbove ? anchorAbove : anchorBelow;
      left = Math.min(anchor.x - width / 2, areaWidth - width);
      top = fitsAbove ? anchor.y - height : anchor.y;
    } else {
      const anchor = { x: anchors.primaryAnchor.x - _contextMenuScreenPadding, y: anchors.primaryAnchor.y - paddingAbove };
      const overhangX = anchor.x + width - areaWidth;
      const overhangY = anchor.y + height - areaHeight;
      left = overhangX > 0 ? anchor.x - overhangX : anchor.x;
      top = overhangY > 0 ? anchor.y - overhangY : anchor.y;
    }
    const next = { left: _contextMenuScreenPadding + Math.max(0, left), top: paddingAbove + Math.max(0, top) };
    setPosition((current) => (current != null && current.left === next.left && current.top === next.top ? current : next));
  });
  useEffect(
    () => () => {
      if (_contextMenuPanel === panelRef.current) _contextMenuPanel = null;
    },
    [],
  );

  return (
    <div
      ref={panelRef}
      data-testid="text-context-menu-panel"
      // Pressing the menu must not move focus out of the field it edits.
      onMouseDown={(event) => event.preventDefault()}
      onContextMenu={(event) => event.preventDefault()}
      style={{
        position: 'fixed',
        left: position?.left ?? 0,
        top: position?.top ?? 0,
        visibility: position == null ? 'hidden' : 'visible',
        zIndex: 1,
      }}
    >
      {children}
    </div>
  );
}

function _ContextMenuPanel({ sections }: { sections: _ContextMenuSection[] }) {
  const children: ReactNode[] = [];
  sections.forEach((section, sectionIndex) => {
    if (sectionIndex > 0) children.push(<_ContextMenuDivider key={`divider-${sectionIndex}`} />);
    section.forEach((item, itemIndex) => children.push(<_ContextMenuItem key={`${sectionIndex}-${itemIndex}`} data={item} />));
  });
  return (
    <div role="menu" style={_panelStyle}>
      {children}
    </div>
  );
}

function _ContextMenuItem({ data }: { data: _ContextMenuItemData }) {
  const [hovered, setHovered] = useState(false);
  const enabled = data.onPressed != null;
  const foreground = enabled ? UiColors.text : UiColors.textMuted;
  const shortcutColor = enabled ? UiColors.textSecondary : withAlpha(UiColors.textMuted, 0.68);
  return (
    <div
      role="menuitem"
      aria-disabled={!enabled}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={data.onPressed ?? undefined}
      style={{
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        boxSizing: 'border-box',
        minHeight: _contextMenuMinItemHeight,
        padding: `6px ${_contextMenuHorizontalPadding}px`,
        borderRadius: UiRadii.sm,
        backgroundColor: enabled && hovered ? UiColors.surfaceHover : UiColors.transparent,
        transition: `background-color 70ms ${UiCurves.easeOutCubic}`,
        cursor: enabled ? 'pointer' : 'default',
        userSelect: 'none',
      }}
    >
      <span style={{ ...UiTypography.body, ..._ellipsis, flex: '1 1 0', minWidth: 0, color: foreground, fontSize: 13, lineHeight: 1.2 }}>
        {data.label}
      </span>
      <span style={{ width: 16, flexShrink: 0 }} />
      <span style={{ ...UiTypography.label, whiteSpace: 'nowrap', color: shortcutColor, fontSize: 12, lineHeight: 1.2 }}>
        {data.shortcut}
      </span>
    </div>
  );
}

function _ContextMenuDivider() {
  return (
    <div data-testid="text-context-menu-divider" style={{ padding: '4px 6px' }}>
      <div style={{ height: 1, backgroundColor: UiColors.borderStrong }} />
    </div>
  );
}

function _shortcutLabel(shortcut: _ShortcutKind): string {
  const mac = HostPlatform.isMacOS;
  switch (shortcut) {
    case _ShortcutKind.cut:
      return mac ? '⌘X' : 'Ctrl+X';
    case _ShortcutKind.copy:
      return mac ? '⌘C' : 'Ctrl+C';
    case _ShortcutKind.paste:
      return mac ? '⌘V' : 'Ctrl+V';
    case _ShortcutKind.selectAll:
      return mac ? '⌘A' : 'Ctrl+A';
    case _ShortcutKind.undo:
      return mac ? '⌘Z' : 'Ctrl+Z';
    case _ShortcutKind.redo:
      return mac ? '⇧⌘Z' : 'Ctrl+Y';
  }
}

const _contentsStyle: CSSProperties = { display: 'contents' };

const _ellipsis: CSSProperties = { whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' };

const _panelStyle: CSSProperties = {
  width: _contextMenuWidth,
  boxSizing: 'border-box',
  backgroundColor: UiColors.surfaceRaised,
  borderRadius: UiRadii.lg - 2,
  border: `1px solid ${UiColors.borderStrong}`,
  boxShadow: boxShadow(UiShadows.popover),
  overflow: 'hidden',
  // Flutter draws the DecoratedBox border over the 5px padding, so the
  // content inset from the outer edge stays 5.
  padding: 4,
  display: 'flex',
  flexDirection: 'column',
};

// ---------------------------------------------------------------------------
// DOM plumbing: the field / selection under the pointer, clipboard, insertion.

type _EditableField = HTMLInputElement | HTMLTextAreaElement;

function _editableTarget(target: EventTarget | null, root: Element): _EditableField | null {
  if (!(target instanceof Element)) return null;
  const field = target.closest('input, textarea');
  if (field == null || !root.contains(field)) return null;
  if (field instanceof HTMLTextAreaElement || field instanceof HTMLInputElement) return field;
  return null;
}

interface _FieldSelection {
  start: number;
  end: number;
  backward: boolean;
}

function _fieldSelection(field: _EditableField): _FieldSelection | null {
  try {
    const start = field.selectionStart;
    const end = field.selectionEnd;
    if (start == null || end == null) return null;
    return { start, end, backward: field.selectionDirection === 'backward' };
  } catch {
    // Some input types (e.g. email) do not expose a selection.
    return null;
  }
}

async function _readClipboardText(): Promise<string> {
  try {
    if (hasPlatform()) return (await platform().clipboard.readText()) ?? '';
    return (await navigator.clipboard?.readText()) ?? '';
  } catch {
    return '';
  }
}

async function _writeClipboardText(text: string): Promise<void> {
  try {
    if (hasPlatform()) {
      await platform().clipboard.writeText(text);
      return;
    }
    await navigator.clipboard?.writeText(text);
  } catch {
    // Copy failures are silent, like Flutter's Clipboard.setData.
  }
}

/**
 * Inserts [text] over [selection] the way typing would, so the browser's
 * undo stack and the field's `onChange` (and thus its controller) follow.
 */
function _insertText(field: _EditableField, text: string, selection: _FieldSelection | null): void {
  field.focus();
  if (selection != null) {
    try {
      field.setSelectionRange(selection.start, selection.end, selection.backward ? 'backward' : 'forward');
    } catch {
      // Selection not supported for this input type.
    }
  }
  if (text.length === 0) return;
  let inserted = false;
  try {
    inserted = document.execCommand('insertText', false, text);
  } catch {
    inserted = false;
  }
  if (inserted) return;
  const current = _fieldSelection(field) ?? { start: field.value.length, end: field.value.length, backward: false };
  field.setRangeText(text, current.start, current.end, 'end');
  field.dispatchEvent(new Event('input', { bubbles: true }));
}

function _deleteSelection(field: _EditableField, selection: _FieldSelection): void {
  field.focus();
  try {
    field.setSelectionRange(selection.start, selection.end);
  } catch {
    return;
  }
  let deleted = false;
  try {
    deleted = document.execCommand('delete');
  } catch {
    deleted = false;
  }
  if (deleted) return;
  field.setRangeText('', selection.start, selection.end, 'end');
  field.dispatchEvent(new Event('input', { bubbles: true }));
}

function _fieldState(
  field: _EditableField,
  anchor: TextMenuOffset,
  pasteIntentAction: () => PasteTextIntentAction | null,
): EditableTextState {
  const text = field.value;
  const domSelection = _fieldSelection(field);
  const selection =
    domSelection == null
      ? TextSelection.collapsed({ offset: -1 })
      : new TextSelection({
          baseOffset: domSelection.backward ? domSelection.end : domSelection.start,
          extentOffset: domSelection.backward ? domSelection.start : domSelection.end,
        });
  const readOnly = field.readOnly || field.disabled;
  const obscureText = field instanceof HTMLInputElement && field.type === 'password';
  const hasSelection = selection.isValid && !selection.isCollapsed;
  const clipboardStatus = new ValueNotifier<ClipboardStatus>(ClipboardStatus.unknown);
  if (!readOnly) {
    void _readClipboardText().then((clip) => {
      clipboardStatus.value = clip.length > 0 ? ClipboardStatus.pasteable : ClipboardStatus.notPasteable;
    });
  }

  const selectedText = () => (domSelection == null ? '' : text.substring(domSelection.start, domSelection.end));
  const copy = () => void _writeClipboardText(selectedText());
  const cut = () => {
    if (domSelection == null) return;
    void _writeClipboardText(selectedText());
    _deleteSelection(field, domSelection);
  };
  const paste = () => {
    void _readClipboardText().then((clip) => _insertText(field, clip, domSelection));
  };
  const selectAll = () => {
    field.focus();
    field.select();
  };
  const allSelected = domSelection != null && domSelection.start === 0 && domSelection.end === text.length;

  const items: ContextMenuButtonItem[] = [];
  if (hasSelection && !readOnly && !obscureText) items.push({ type: ContextMenuButtonType.cut, onPressed: cut });
  if (hasSelection && !obscureText) items.push({ type: ContextMenuButtonType.copy, onPressed: copy });
  if (!readOnly) items.push({ type: ContextMenuButtonType.paste, onPressed: paste });
  if (text.length > 0 && !allSelected) items.push({ type: ContextMenuButtonType.selectAll, onPressed: selectAll });

  return {
    textEditingValue: new TextEditingValue({ text, selection }),
    clipboardStatus,
    contextMenuAnchors: { primaryAnchor: anchor, secondaryAnchor: null },
    contextMenuButtonItems: items,
    readOnly,
    obscureText,
    selectAll: () => selectAll(),
    copySelection: () => copy(),
    hideToolbar: _hideMenu,
    invokePasteTextIntent: () => {
      const action = pasteIntentAction();
      if (action == null) {
        paste();
        return;
      }
      action.invoke(paste);
    },
  };
}

/** Read-only state over the DOM selection inside [root] (selectable text). */
function _domSelectionState(root: Element, anchor: TextMenuOffset): EditableTextState {
  const text = root.textContent ?? '';
  let selection = TextSelection.collapsed({ offset: -1 });
  let selectedText = '';
  const domSelection = window.getSelection();
  if (domSelection != null && domSelection.rangeCount > 0) {
    const range = domSelection.getRangeAt(0);
    if (root.contains(range.commonAncestorContainer)) {
      const before = document.createRange();
      before.selectNodeContents(root);
      before.setEnd(range.startContainer, range.startOffset);
      const start = before.toString().length;
      selectedText = range.toString();
      selection = new TextSelection({ baseOffset: start, extentOffset: start + selectedText.length });
    }
  }
  const copy = () => void _writeClipboardText(selectedText);
  const selectAll = () => {
    const range = document.createRange();
    range.selectNodeContents(root);
    const current = window.getSelection();
    current?.removeAllRanges();
    current?.addRange(range);
  };
  const items: ContextMenuButtonItem[] = [];
  if (selectedText.length > 0) items.push({ type: ContextMenuButtonType.copy, onPressed: copy });
  return {
    textEditingValue: new TextEditingValue({ text, selection }),
    clipboardStatus: new ValueNotifier<ClipboardStatus>(ClipboardStatus.notPasteable),
    contextMenuAnchors: { primaryAnchor: anchor, secondaryAnchor: null },
    contextMenuButtonItems: items,
    readOnly: true,
    obscureText: false,
    selectAll: () => selectAll(),
    copySelection: () => copy(),
    hideToolbar: _hideMenu,
    invokePasteTextIntent: () => {},
  };
}

// ---------------------------------------------------------------------------
// The single open menu (Flutter shows at most one selection toolbar).

let _open: { entry: OverlayEntryHandle; detach: () => void } | null = null;

function _hideMenu(): void {
  const open = _open;
  _open = null;
  if (open == null) return;
  open.detach();
  open.entry.remove();
}

function _showMenu(build: (state: EditableTextState) => ReactNode, state: EditableTextState): void {
  _hideMenu();
  const entry = insertOverlay(() => build(state));
  const inPanel = (event: MouseEvent) =>
    (_contextMenuPanel != null && event.target instanceof Node && _contextMenuPanel.contains(event.target)) ||
    isTextContextMenuPanelHit({ x: event.clientX, y: event.clientY });

  // Taps outside close the menu without swallowing the tap (TapRegion's
  // onTapOutside).
  const onPointerDown = (event: PointerEvent) => {
    if (inPanel(event)) return;
    _hideMenu();
  };
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Escape') return;
    event.preventDefault();
    event.stopPropagation();
    _hideMenu();
  };
  const onWindowChange = () => _hideMenu();
  document.addEventListener('pointerdown', onPointerDown, true);
  document.addEventListener('keydown', onKeyDown, true);
  window.addEventListener('blur', onWindowChange);
  window.addEventListener('resize', onWindowChange);
  _open = {
    entry,
    detach: () => {
      document.removeEventListener('pointerdown', onPointerDown, true);
      document.removeEventListener('keydown', onKeyDown, true);
      window.removeEventListener('blur', onWindowChange);
      window.removeEventListener('resize', onWindowChange);
    },
  };
}
