import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
  type LayoutChangeEvent,
  type ViewStyle,
} from 'react-native';

import { ValueNotifier } from '@gang-chat/core/lib/change_notifier';
import { HostPlatform } from '@gang-chat/core/config/host_platform';

import {
  type FocusNode,
  type TextEditingController,
  type TextEditingValue,
  type TextSelection,
  type UndoHistoryController,
} from '../lib/text_editing';
import { useMountedRef } from '../lib/use_mounted';
import { measureInWindow, type WindowRect } from './overlay';
import { UiColors, UiMotion, UiRadii, UiShadows, UiTypography, boxShadow, withAlpha } from './tokens';
import { useTextFieldDomHooks } from './text_context_menu_dom';

/**
 * Port of `lib/src/ui/text_context_menu.dart`: the app's own text selection
 * menu (剪切 / 复制 / 粘贴 / 全选 / 撤销 / 重做) for editable and read-only text.
 *
 * Flutter hands `contextMenuBuilder` to `TextField`, which calls it with its
 * `EditableTextState` on a secondary click. React Native's `TextInput` has no
 * such hook, so in this port [TextFieldEditingShortcuts] — the wrapper every
 * text field already sits in — owns the hookup:
 *
 *  - Web (Electron): `text_context_menu_dom.web.ts` listens for `contextmenu`
 *    on the wrapped DOM subtree, builds an [EditableTextState] from the
 *    focused `<input>` / `<textarea>` (or from the DOM selection for
 *    selectable `<Text>`), and shows the menu returned by the wrapper's
 *    [TextFieldEditingShortcutsProps.contextMenuBuilder] (default:
 *    [buildTextFieldContextMenu] with the field's read-only / password state)
 *    in an overlay entry.
 *  - Native: the platform's own selection menu is used (see
 *    `text_context_menu_dom.ts`).
 *
 * Callers pass the same options Dart passed to `buildTextFieldContextMenu`:
 *
 * ```tsx
 * <TextFieldEditingShortcuts
 *   controller={controller}
 *   focusNode={focusNode}
 *   undoController={undoController}
 *   contextMenuBuilder={(state) => buildTextFieldContextMenu(state, { undoController, canPasteNonText })}
 * >
 *   <TextInput … />
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

/** A point in window coordinates (Flutter `Offset`, global). */
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
 * `EditableTextState`). The web hookup implements it over the DOM field.
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
 * field's default text paste (`callingAction`) so it can fall back to it.
 */
export interface PasteTextIntentAction {
  invoke(defaultPaste: () => void): void;
}

interface PasteTextIntentScopeValue {
  action: PasteTextIntentAction;
  /**
   * PORT: lets a field whose owner did not pass `canPasteNonText` to
   * [buildTextFieldContextMenu] still keep 粘贴 visible for non-text
   * clipboards (see [TextFieldEditingShortcuts]' default builder).
   */
  canPasteNonText: (() => Promise<boolean>) | null;
}

const PasteTextIntentScope = createContext<PasteTextIntentScopeValue | null>(null);

export interface PasteTextIntentActionsProps {
  /** The `PasteTextIntent` override; null keeps the default paste. */
  action: PasteTextIntentAction | null;
  /** Optional non-text clipboard probe for the default context menu. */
  canPasteNonText?: (() => Promise<boolean>) | null;
  children: ReactNode;
}

/**
 * Port of `Actions(actions: {PasteTextIntent: action}, child: …)`.
 *
 * Both the keyboard paste (Cmd/Ctrl+V, a DOM `paste` event on the web) and
 * the context-menu 粘贴 of any field below run through [action].
 */
export function PasteTextIntentActions({ action, canPasteNonText = null, children }: PasteTextIntentActionsProps) {
  const value = useMemo(() => (action == null ? null : { action, canPasteNonText }), [action, canPasteNonText]);
  const ref = useRef<View | null>(null);
  useTextFieldDomHooks(ref, {
    pasteAction: value?.action ?? null,
    // The paste override is the only hook this wrapper installs; the field's
    // own TextFieldEditingShortcuts provides the context menu.
    contextMenu: null,
  });
  return (
    <PasteTextIntentScope.Provider value={value}>
      <View ref={ref} style={styles.contents}>
        {children}
      </View>
    </PasteTextIntentScope.Provider>
  );
}

// ---------------------------------------------------------------------------

let _contextMenuPanelBounds: WindowRect | null = null;

/** Whether [globalPosition] (window coordinates) hits the open text menu. */
export function isTextContextMenuPanelHit(globalPosition: TextMenuOffset): boolean {
  const rect = _contextMenuPanelBounds;
  if (rect == null) return false;
  return (
    globalPosition.x >= rect.x &&
    globalPosition.y >= rect.y &&
    globalPosition.x < rect.x + rect.width &&
    globalPosition.y < rect.y + rect.height
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
   * state and [undoController].
   */
  contextMenuBuilder?: ((editableTextState: EditableTextState) => ReactNode) | null;
  children: ReactNode;
}

/**
 * Port of `TextFieldEditingShortcuts`: Ctrl+Y redo, the secondary-click
 * selection protection and (in this port) the context-menu hookup for the
 * text field(s) it wraps. It does not take part in layout (the wrapper is
 * `display: contents`).
 *
 * Flutter collapsed the selection and dropped focus on a right click before
 * the menu read them, so it restored both. A DOM field keeps its selection
 * and focus through `contextmenu`, so the web hookup only keeps the
 * protection's other effect: while a menu opened by a secondary click is up,
 * the next primary pointer-down anywhere reports
 * [onGlobalPrimaryPointerDownDuringSecondaryClickProtection].
 */
export function TextFieldEditingShortcuts(props: TextFieldEditingShortcutsProps) {
  const {
    controller = null,
    focusNode = null,
    secondaryClickSelection = null,
    onGlobalPrimaryPointerDownDuringSecondaryClickProtection = null,
    undoController,
    contextMenuBuilder = null,
    children,
  } = props;
  const paste = useContext(PasteTextIntentScope);
  const ref = useRef<View | null>(null);
  const builder = (state: EditableTextState): ReactNode =>
    contextMenuBuilder != null
      ? contextMenuBuilder(state)
      : buildTextFieldContextMenu(state, {
          undoController,
          readOnly: state.readOnly,
          allowCut: !state.obscureText,
          allowCopy: !state.obscureText,
          canPasteNonText: paste?.canPasteNonText ?? null,
        });
  useTextFieldDomHooks(ref, {
    pasteAction: null,
    contextMenu: {
      build: builder,
      pasteIntentAction: paste?.action ?? null,
      undoController,
      controller,
      focusNode,
      secondaryClickSelection,
      onGlobalPrimaryPointerDown: onGlobalPrimaryPointerDownDuringSecondaryClickProtection,
    },
  });
  return (
    <View ref={ref} style={styles.contents}>
      {children}
    </View>
  );
}

export interface BuildTextFieldContextMenuOptions {
  undoController?: UndoHistoryController | null;
  /** Unused in this port (taps outside always close the menu); kept for call-site parity. */
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

  // Listen to the clipboard status and undo history (initState/dispose).
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
    const timer = setTimeout(() => {
      if (!mounted.current) return;
      openNotified = true;
      propsRef.current.onOpenChanged?.(true);
    }, 0);
    return () => {
      clearTimeout(timer);
      generation.current += 1;
      if (openNotified) {
        const onOpenChanged = propsRef.current.onOpenChanged;
        if (onOpenChanged != null) setTimeout(() => onOpenChanged(false), 0);
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
  const start = value.selection.start < value.selection.end ? value.selection.start : value.selection.end;
  const end = value.selection.start < value.selection.end ? value.selection.end : value.selection.start;
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
 * shifted back inside the padded screen when it would overhang. Android uses
 * the above/below placement of `TextSelectionToolbarLayoutDelegate`.
 */
function _ContextMenuToolbar({ anchors, children }: { anchors: TextSelectionToolbarAnchors; children: ReactNode }) {
  const window = useWindowDimensions();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const panelRef = useRef<View | null>(null);
  const paddingAbove = _contextMenuScreenPadding;
  const areaWidth = window.width - _contextMenuScreenPadding * 2;
  const areaHeight = window.height - paddingAbove - _contextMenuScreenPadding;

  useEffect(
    () => () => {
      _contextMenuPanelBounds = null;
    },
    [],
  );

  let left = 0;
  let top = 0;
  if (size != null) {
    if (HostPlatform.isAndroid) {
      // TextSelectionToolbarLayoutDelegate: above the selection when it fits,
      // otherwise below it (kToolbarContentDistanceBelow = 20 - 2).
      const anchorAbove = { x: anchors.primaryAnchor.x - _contextMenuScreenPadding, y: anchors.primaryAnchor.y - 8 - paddingAbove };
      const below = anchors.secondaryAnchor ?? anchors.primaryAnchor;
      const anchorBelow = { x: below.x - _contextMenuScreenPadding, y: below.y + 18 - paddingAbove };
      const fitsAbove = anchorAbove.y >= size.height;
      const anchor = fitsAbove ? anchorAbove : anchorBelow;
      left = Math.max(0, Math.min(anchor.x - size.width / 2, areaWidth - size.width));
      top = fitsAbove ? anchor.y - size.height : anchor.y;
    } else {
      const anchor = { x: anchors.primaryAnchor.x - _contextMenuScreenPadding, y: anchors.primaryAnchor.y - paddingAbove };
      const overhangX = anchor.x + size.width - areaWidth;
      const overhangY = anchor.y + size.height - areaHeight;
      left = overhangX > 0 ? anchor.x - overhangX : anchor.x;
      top = overhangY > 0 ? anchor.y - overhangY : anchor.y;
    }
    left = Math.max(0, left);
    top = Math.max(0, top);
  }

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    if (size == null || size.width !== width || size.height !== height) setSize({ width, height });
    void measureInWindow(panelRef).then((rect) => {
      _contextMenuPanelBounds = rect;
    });
  };

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <View
        style={{
          position: 'absolute',
          left: _contextMenuScreenPadding + left,
          top: paddingAbove + top,
          // Hidden until measured so the first frame never flashes at 0,0.
          opacity: size == null ? 0 : 1,
        }}
      >
        <View ref={panelRef} onLayout={onLayout} nativeID="text-context-menu-panel" testID="text-context-menu-panel">
          {children}
        </View>
      </View>
    </View>
  );
}

function _ContextMenuPanel({ sections }: { sections: _ContextMenuSection[] }) {
  const children: ReactNode[] = [];
  sections.forEach((section, sectionIndex) => {
    if (sectionIndex > 0) children.push(<_ContextMenuDivider key={`divider-${sectionIndex}`} />);
    section.forEach((item, itemIndex) => children.push(<_ContextMenuItem key={`${sectionIndex}-${itemIndex}`} data={item} />));
  });
  return <View style={styles.panel}>{children}</View>;
}

const _webItemTransition: ViewStyle | null =
  Platform.OS === 'web'
    ? ({ transitionProperty: 'background-color', transitionDuration: '70ms', transitionTimingFunction: UiMotion.curve } as ViewStyle)
    : null;

function _ContextMenuItem({ data }: { data: _ContextMenuItemData }) {
  const [hovered, setHovered] = useState(false);
  const enabled = data.onPressed != null;
  const foreground = enabled ? UiColors.text : UiColors.textMuted;
  const shortcutColor = enabled ? UiColors.textSecondary : withAlpha(UiColors.textMuted, 0.68);
  return (
    <Pressable
      accessibilityRole="menuitem"
      disabled={!enabled}
      onPress={data.onPressed ?? undefined}
      onHoverIn={() => setHovered(true)}
      onHoverOut={() => setHovered(false)}
      style={[
        styles.item,
        _webItemTransition,
        { backgroundColor: enabled && hovered ? UiColors.surfaceHover : UiColors.transparent },
        Platform.OS === 'web' ? ({ cursor: enabled ? 'pointer' : 'default' } as ViewStyle) : null,
      ]}
    >
      <Text numberOfLines={1} style={[styles.itemLabel, { color: foreground }]}>
        {data.label}
      </Text>
      <View style={{ width: 16 }} />
      <Text numberOfLines={1} style={[styles.itemShortcut, { color: shortcutColor }]}>
        {data.shortcut}
      </Text>
    </Pressable>
  );
}

function _ContextMenuDivider() {
  return (
    <View testID="text-context-menu-divider" style={styles.dividerPadding}>
      <View style={styles.divider} />
    </View>
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

const styles = StyleSheet.create({
  contents: { display: 'contents' },
  panel: {
    width: _contextMenuWidth,
    backgroundColor: UiColors.surfaceRaised,
    borderRadius: UiRadii.lg - 2,
    borderWidth: 1,
    borderColor: UiColors.borderStrong,
    ...boxShadow(UiShadows.popover),
    overflow: 'hidden',
    // Flutter draws the border over the 5px padding (DecoratedBox), so the
    // content inset from the outer edge stays 5.
    padding: 4,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: _contextMenuMinItemHeight,
    paddingHorizontal: _contextMenuHorizontalPadding,
    paddingVertical: 6,
    borderRadius: UiRadii.sm,
  },
  itemLabel: {
    ...UiTypography.body,
    flex: 1,
    minWidth: 0,
    fontSize: 13,
    lineHeight: 13 * 1.2,
  },
  itemShortcut: {
    ...UiTypography.label,
    fontSize: 12,
    lineHeight: 12 * 1.2,
  },
  dividerPadding: { paddingHorizontal: 6, paddingVertical: 4 },
  divider: { alignSelf: 'stretch', height: 1, backgroundColor: UiColors.borderStrong },
});
