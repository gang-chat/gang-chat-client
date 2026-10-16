import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ChangeEvent,
  type CSSProperties,
  type HTMLAttributes,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from 'react';

import { paddingStyle, resolveEdgeInsets, type EdgeInsetsValue } from '../lib/edge_insets';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { useTapOutside } from '../lib/tap_region';
import {
  FocusNode,
  TextEditingController,
  TextEditingValue,
  TextInputAction,
  TextInputType,
  TextSelection,
  UndoHistoryController,
  type AutofillHint,
  type TextInputFormatter,
} from '../lib/text_editing';
import { useListenable } from '../lib/use_listenable';
import { Icon, Icons, type IconData } from './icon';
import { buildTextFieldContextMenu, TextFieldEditingShortcuts, type EditableTextState } from './text_context_menu';
import { UiTooltip } from './tooltip';
import { UiColors, UiMotion, UiRadii, UiSpacing, UiTypography, withAlpha } from './tokens';

const _inputHorizontalPadding = 12;
const _inputIconSize = 17;
const _inputClearButtonSize = 26;
const _inputClearIconSize = 15;

// ---------------------------------------------------------------------------
// Shared field element.

/** Owns a controller / focus node / undo history locally unless the caller passes one. */
export function _useOwned<T>(provided: T | null | undefined, create: () => T, dispose?: (value: T) => void): T {
  const local = useRef<T | null>(null);
  if (provided == null && local.current == null) local.current = create();
  useEffect(() => {
    if (provided != null && local.current != null) {
      dispose?.(local.current);
      local.current = null;
    }
  }, [provided, dispose]);
  useEffect(
    () => () => {
      if (local.current != null) dispose?.(local.current);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );
  return provided ?? local.current!;
}

function lineHeightPx(style: CSSProperties): number {
  const fontSize = typeof style.fontSize === 'number' ? style.fontSize : 14;
  const lineHeight = style.lineHeight;
  if (typeof lineHeight === 'number') return fontSize * lineHeight;
  if (typeof lineHeight === 'string' && lineHeight.endsWith('px')) return parseFloat(lineHeight);
  // `normal` for the client face is roughly 1.32em.
  return fontSize * 1.32;
}

function inputModeFor(type: TextInputType | null | undefined): HTMLAttributes<HTMLInputElement>['inputMode'] {
  switch (type) {
    case TextInputType.number:
      return 'numeric';
    case TextInputType.phone:
      return 'tel';
    case TextInputType.emailAddress:
      return 'email';
    case TextInputType.url:
      return 'url';
    case TextInputType.none:
      return 'none';
    default:
      return undefined;
  }
}

function enterKeyHintFor(action: TextInputAction | null | undefined): HTMLAttributes<HTMLInputElement>['enterKeyHint'] {
  switch (action) {
    case TextInputAction.done:
      return 'done';
    case TextInputAction.go:
      return 'go';
    case TextInputAction.search:
      return 'search';
    case TextInputAction.send:
      return 'send';
    case TextInputAction.next:
      return 'next';
    case TextInputAction.previous:
      return 'previous';
    case TextInputAction.newline:
      return 'enter';
    default:
      return undefined;
  }
}

export interface _EditableTextFieldProps {
  controller: TextEditingController;
  focusNode: FocusNode;
  undoController: UndoHistoryController;
  enabled?: boolean;
  readOnly?: boolean;
  obscureText?: boolean;
  autofillHints?: readonly AutofillHint[] | null;
  keyboardType?: TextInputType | null;
  textInputAction?: TextInputAction | null;
  minLines?: number;
  /** `null` = grows without limit. */
  maxLines?: number | null;
  onSubmitted?: ((value: string) => void) | null;
  onChanged?: ((value: string) => void) | null;
  inputFormatters?: readonly TextInputFormatter[] | null;
  style: CSSProperties;
  hintText?: string | null;
  hintStyle?: CSSProperties | null;
  textAlign?: CSSProperties['textAlign'];
  enableInteractiveSelection?: boolean;
  showCursor?: boolean;
  /** Content padding inside the field element. */
  padding?: EdgeInsetsValue;
  contextMenuBuilder?: ((state: EditableTextState) => ReactNode) | null;
  secondaryClickSelection?: (() => TextSelection | null) | null;
  /** Extra style on the `<input>` / `<textarea>`. */
  elementStyle?: CSSProperties;
  'aria-label'?: string;
}

/**
 * A DOM `<input>` (one line) or auto-growing `<textarea>` bound to a
 * [TextEditingController] / [FocusNode] / [UndoHistoryController], wrapped in
 * [TextFieldEditingShortcuts] for the app's text context menu. Shared by
 * `Input`, `TextInput` and the read-only text boxes (Flutter's `TextField`).
 */
export function _EditableTextField(props: _EditableTextFieldProps) {
  const {
    controller,
    focusNode,
    undoController,
    enabled = true,
    readOnly = false,
    obscureText = false,
    autofillHints,
    keyboardType,
    textInputAction,
    minLines = 1,
    maxLines = 1,
    onSubmitted,
    onChanged,
    inputFormatters,
    style,
    hintText,
    hintStyle,
    textAlign = 'start',
    enableInteractiveSelection = true,
    showCursor = true,
    padding = 0,
    contextMenuBuilder,
    secondaryClickSelection,
    elementStyle,
  } = props;
  useListenable(controller);
  const elementRef = useRef<HTMLInputElement | HTMLTextAreaElement | null>(null);
  const composing = useRef(false);
  const multiline = maxLines == null || maxLines > 1;
  const value = controller.text;

  useEffect(() => {
    undoController._attach(controller);
    return () => undoController._attach(null);
  }, [undoController, controller]);

  useEffect(() => {
    const node = elementRef.current;
    if (node == null) return;
    focusNode._attach({
      focus: () => node.focus(),
      blur: () => node.blur(),
    });
    if (document.activeElement === node) focusNode._setHasFocus(true);
    return () => focusNode._attach(null);
  }, [focusNode, multiline]);

  // Programmatic selection changes (controller.selection = …) reach the DOM.
  useLayoutEffect(() => {
    const node = elementRef.current;
    if (node == null) return;
    const selection = controller.selection;
    if (!selection.isValid) return;
    const start = Math.min(selection.start, node.value.length);
    const end = Math.min(selection.end, node.value.length);
    if (node.selectionStart === start && node.selectionEnd === end) return;
    try {
      node.setSelectionRange(start, end, selection.baseOffset > selection.extentOffset ? 'backward' : 'forward');
    } catch {
      // Some input types have no selection API.
    }
  });

  // Auto-grow between minLines and maxLines (Flutter's TextField sizing).
  const resolvedPadding = resolveEdgeInsets(padding);
  const lineHeight = lineHeightPx(style);
  useLayoutEffect(() => {
    const node = elementRef.current;
    if (node == null || !multiline) return;
    const vertical = resolvedPadding.top + resolvedPadding.bottom;
    const minHeight = minLines * lineHeight + vertical;
    const maxHeight = maxLines == null ? Infinity : maxLines * lineHeight + vertical;
    node.style.height = '0px';
    const content = node.scrollHeight;
    const height = Math.min(Math.max(content, minHeight), maxHeight);
    node.style.height = `${height}px`;
    node.style.overflowY = content > maxHeight + 0.5 ? 'auto' : 'hidden';
  });

  const syncSelectionFromElement = () => {
    const node = elementRef.current;
    if (node == null) return;
    const start = node.selectionStart ?? node.value.length;
    const end = node.selectionEnd ?? start;
    const backward = node.selectionDirection === 'backward';
    const next = new TextSelection({ baseOffset: backward ? end : start, extentOffset: backward ? start : end });
    if (controller.selection.equals(next) && controller.text === node.value) return;
    if (controller.text !== node.value) return;
    controller.selection = next;
  };

  const handleChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const node = event.currentTarget;
    const start = node.selectionStart ?? node.value.length;
    const end = node.selectionEnd ?? start;
    let next = new TextEditingValue({
      text: node.value,
      selection: new TextSelection({ baseOffset: start, extentOffset: end }),
    });
    const isComposing = composing.current || (event.nativeEvent as InputEvent).isComposing === true;
    if (!isComposing && inputFormatters != null) {
      for (const formatter of inputFormatters) next = formatter.formatEditUpdate(controller.value, next);
    }
    const changed = next.text !== controller.text;
    controller.value = next;
    if (changed) onChanged?.(next.text);
  };

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing || composing.current) return;
    const submits = !multiline || (textInputAction != null && textInputAction !== TextInputAction.newline);
    if (!submits) return;
    event.preventDefault();
    controller.clearComposing();
    const action = textInputAction ?? TextInputAction.done;
    // Flutter's default editing-complete: completion actions unfocus.
    if (action !== TextInputAction.next && action !== TextInputAction.previous) {
      focusNode.unfocus();
    }
    onSubmitted?.(controller.text);
  };

  const sharedStyle: CSSProperties = {
    display: 'block',
    width: '100%',
    minWidth: 0,
    boxSizing: 'border-box',
    margin: 0,
    border: 'none',
    outline: 'none',
    background: 'transparent',
    resize: 'none',
    ...style,
    lineHeight: `${lineHeight}px`,
    textAlign,
    ...paddingStyle(padding),
    caretColor: showCursor ? UiColors.accent : 'transparent',
    cursor: enabled ? SystemMouseCursors.text : SystemMouseCursors.basic,
    userSelect: enableInteractiveSelection ? 'text' : 'none',
    WebkitUserSelect: enableInteractiveSelection ? 'text' : 'none',
    ['--gang-placeholder-color' as string]: (hintStyle?.color as string | undefined) ?? UiColors.textMuted,
    ...elementStyle,
  };

  const common = {
    ref: (node: HTMLInputElement | HTMLTextAreaElement | null) => {
      elementRef.current = node;
    },
    className: 'gang-input',
    value,
    placeholder: hintText ?? undefined,
    disabled: !enabled,
    readOnly,
    spellCheck: false,
    autoComplete: autofillHints != null && autofillHints.length > 0 ? autofillHints[0] : 'off',
    inputMode: inputModeFor(keyboardType),
    enterKeyHint: enterKeyHintFor(textInputAction),
    'aria-label': props['aria-label'],
    onChange: handleChange,
    onKeyDown: handleKeyDown,
    onSelect: syncSelectionFromElement,
    onFocus: () => focusNode._setHasFocus(true),
    onBlur: () => focusNode._setHasFocus(false),
    onCompositionStart: () => {
      composing.current = true;
    },
    onCompositionEnd: () => {
      composing.current = false;
    },
    style: sharedStyle,
  };

  const field = multiline ? (
    <textarea {...common} rows={Math.max(1, minLines)} />
  ) : (
    <input {...common} type={obscureText ? 'password' : 'text'} />
  );

  return (
    <TextFieldEditingShortcuts
      controller={controller}
      focusNode={focusNode}
      undoController={undoController}
      secondaryClickSelection={secondaryClickSelection}
      contextMenuBuilder={enableInteractiveSelection ? contextMenuBuilder : () => null}
    >
      {field}
    </TextFieldEditingShortcuts>
  );
}

// ---------------------------------------------------------------------------
// Input

export interface InputProps {
  controller?: TextEditingController | null;
  focusNode?: FocusNode | null;
  hintText?: string;
  enabled?: boolean;
  obscureText?: boolean;
  autofillHints?: readonly AutofillHint[] | null;
  keyboardType?: TextInputType | null;
  textInputAction?: TextInputAction | null;
  prefixIcon?: IconData | null;
  suffix?: ReactNode;
  showClearButton?: boolean;
  clearTooltip?: string;
  minLines?: number;
  maxLines?: number | null;
  onSubmitted?: ((value: string) => void) | null;
  onChanged?: ((value: string) => void) | null;
  inputFormatters?: readonly TextInputFormatter[] | null;
  style?: CSSProperties;
  hintStyle?: CSSProperties;
  textAlign?: CSSProperties['textAlign'];
  /**
   * Called for a pointer down outside the field while it has focus.
   *
   * PORT: the browser still blurs a focused field when a non-focusable area
   * is pressed; Flutter skipped its default unfocus when this was set.
   */
  onTapOutside?: ((event: PointerEvent) => void) | null;
  /** Kept for call-site parity (Flutter `TapRegion` group). */
  tapRegionGroupId?: unknown;
  /** The collapsed (single-line) height of the field. */
  height?: number;
  undoController?: UndoHistoryController | null;
  canPasteNonText?: (() => Promise<boolean>) | null;
  enableInteractiveSelection?: boolean;
  /**
   * Renders only the text field, without the well background and border, for
   * hosts (like the chat composer) that draw their own container.
   */
  bare?: boolean;
  /** Outer box style (DOM addition). */
  containerStyle?: CSSProperties;
  'aria-label'?: string;
}

const kInputDefaultHeight = 40;

function sameIcon(a: IconData | null | undefined, b: IconData): boolean {
  return a != null && a.codePoint === b.codePoint && a.fontFamily === b.fontFamily;
}

function InputImpl(props: InputProps) {
  const {
    hintText = '输入消息',
    enabled = true,
    obscureText = false,
    autofillHints,
    keyboardType,
    textInputAction,
    prefixIcon,
    suffix,
    showClearButton = false,
    clearTooltip = '清空搜索',
    minLines = 1,
    maxLines = 5,
    onSubmitted,
    onChanged,
    inputFormatters,
    style = UiTypography.body,
    hintStyle = { color: UiColors.textMuted },
    textAlign = 'start',
    onTapOutside,
    height = kInputDefaultHeight,
    canPasteNonText,
    enableInteractiveSelection = true,
    bare = false,
    containerStyle,
  } = props;
  const controller = _useOwned(props.controller, () => new TextEditingController(), disposeNotifier);
  const focusNode = _useOwned(props.focusNode, () => new FocusNode(), disposeNotifier);
  const undoController = _useOwned(props.undoController, () => new UndoHistoryController(), disposeNotifier);
  useListenable(controller, focusNode);
  const [hovered, setHovered] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const isSearchInput =
    sameIcon(prefixIcon, Icons.search) || hintText.trim().includes('搜索') || (showClearButton && clearTooltip.trim().includes('搜索'));
  const effectiveMinLines = obscureText || isSearchInput ? 1 : minLines;
  const effectiveMaxLines = obscureText || isSearchInput ? 1 : maxLines;
  const effectiveTextInputAction = textInputAction ?? (isSearchInput ? TextInputAction.search : null);

  const focused = focusNode.hasFocus;
  useTapOutside([containerRef], (event) => onTapOutside?.(event), focused && onTapOutside != null);

  const clearText = () => {
    if (controller.text.length === 0) return;
    controller.clear();
    onChanged?.('');
    focusNode.requestFocus();
  };

  const overlaysClearButton = suffix == null && showClearButton && enabled && height < kInputDefaultHeight;
  const canClear = overlaysClearButton && controller.text.length > 0;
  const effectiveSuffix = (): ReactNode => {
    const showClear = showClearButton && enabled && controller.text.length > 0;
    if (!showClear) return suffix ?? null;
    return <_InputClearButton tooltip={clearTooltip} onPressed={clearText} />;
  };
  const resolvedSuffix = overlaysClearButton ? null : effectiveSuffix();

  // Vertical padding centres one line in [height]. PORT: the Flutter desktop
  // build shifted text/icons down 4–5px to compensate its own text layout;
  // the browser centres the line box itself, like the Android branch.
  const lh = lineHeightPx(style);
  const verticalPadding = Math.max(0, (height - lh) / 2);
  // Unbounded fields leave a little room under the last line on desktop.
  const unboundedBottomPadding = effectiveMaxLines == null ? 8 : 0;
  const contentPadding = {
    left: prefixIcon != null ? 0 : _inputHorizontalPadding,
    top: verticalPadding,
    right:
      resolvedSuffix != null
        ? 0
        : _inputHorizontalPadding + (overlaysClearButton ? UiSpacing.sm + _inputClearButtonSize : 0),
    bottom: verticalPadding + unboundedBottomPadding,
  };

  const textField = (
    <_EditableTextField
      controller={controller}
      focusNode={focusNode}
      undoController={undoController}
      enabled={enabled}
      obscureText={obscureText}
      autofillHints={autofillHints}
      keyboardType={keyboardType}
      textInputAction={effectiveTextInputAction}
      minLines={effectiveMinLines}
      maxLines={effectiveMaxLines}
      onSubmitted={onSubmitted}
      onChanged={onChanged}
      inputFormatters={inputFormatters}
      style={style}
      hintText={hintText}
      hintStyle={hintStyle}
      textAlign={textAlign}
      enableInteractiveSelection={enableInteractiveSelection}
      padding={contentPadding}
      aria-label={props['aria-label'] ?? hintText}
      contextMenuBuilder={(state) =>
        buildTextFieldContextMenu(state, {
          undoController,
          canPasteNonText,
          tapRegionGroupId: props.tapRegionGroupId,
          allowCut: !obscureText,
          allowCopy: !obscureText,
        })
      }
    />
  );

  const content = (
    <div
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'row',
        alignItems: effectiveMaxLines === 1 ? 'center' : 'flex-start',
        minHeight: height,
        minWidth: 0,
        width: '100%',
        boxSizing: 'border-box',
      }}
    >
      {prefixIcon != null ? (
        <div
          style={{
            paddingLeft: _inputHorizontalPadding,
            paddingRight: UiSpacing.sm,
            height: effectiveMaxLines === 1 ? undefined : height,
            display: 'flex',
            alignItems: 'center',
            flexShrink: 0,
          }}
        >
          <Icon icon={prefixIcon} size={_inputIconSize} color={UiColors.textMuted} />
        </div>
      ) : null}
      <div style={{ flex: '1 1 0', minWidth: 0 }}>{textField}</div>
      {resolvedSuffix != null ? (
        <div
          style={{
            paddingLeft: UiSpacing.sm,
            paddingRight: _inputHorizontalPadding,
            height: effectiveMaxLines === 1 ? undefined : height,
            display: 'flex',
            alignItems: 'center',
            flexShrink: 0,
          }}
        >
          {resolvedSuffix}
        </div>
      ) : null}
      {canClear ? (
        <div style={{ position: 'absolute', top: 0, right: _inputHorizontalPadding, bottom: 0, width: _inputClearButtonSize }}>
          <_InputClearButton tooltip={clearTooltip} onPressed={clearText} fill />
        </div>
      ) : null}
    </div>
  );

  if (bare) {
    return (
      <div
        ref={containerRef}
        style={{ cursor: enabled ? SystemMouseCursors.text : SystemMouseCursors.basic, minWidth: 0, ...containerStyle }}
      >
        {content}
      </div>
    );
  }

  const background = enabled ? UiColors.fieldFill : UiColors.disabledSurface;
  const borderColor = !enabled
    ? UiColors.disabledBorder
    : focused
      ? UiColors.accentStrong
      : hovered
        ? UiColors.fieldBorderHover
        : UiColors.fieldBorder;
  const transition = (property: string) => `${property} ${UiMotion.fast}ms ${UiMotion.curve}`;

  return (
    <div
      ref={containerRef}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        // The 1px border sits outside the field's [height], as in Flutter's
        // decorated AnimatedContainer.
        boxSizing: 'border-box',
        minWidth: 0,
        cursor: enabled ? SystemMouseCursors.text : SystemMouseCursors.basic,
        backgroundColor: background,
        borderRadius: UiRadii.md,
        border: `1px solid ${borderColor}`,
        boxShadow: focused && enabled ? `0 0 0 3px ${withAlpha(UiColors.accentStrong, 0.2)}` : 'none',
        overflow: 'hidden',
        transition: `${transition('background-color')}, ${transition('border-color')}, ${transition('box-shadow')}`,
        ...containerStyle,
      }}
    >
      {content}
    </div>
  );
}

function disposeNotifier(value: { dispose(): void }): void {
  value.dispose();
}

export const Input = Object.assign(InputImpl, { defaultHeight: kInputDefaultHeight });

function _InputClearButton({ tooltip, onPressed, fill = false }: { tooltip: string; onPressed: () => void; fill?: boolean }) {
  return (
    <UiTooltip message={tooltip}>
      <div
        role="button"
        aria-label={tooltip}
        // Keep the field focused while clearing.
        onMouseDown={(event) => event.preventDefault()}
        onClick={(event) => {
          if (event.button !== 0) return;
          onPressed();
        }}
        style={{
          width: _inputClearButtonSize,
          height: fill ? '100%' : _inputClearButtonSize,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: SystemMouseCursors.click,
          flexShrink: 0,
        }}
      >
        <Icon icon={Icons.close} size={_inputClearIconSize} color={UiColors.textMuted} />
      </div>
    </UiTooltip>
  );
}
