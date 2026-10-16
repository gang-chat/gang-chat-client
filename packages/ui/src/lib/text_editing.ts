import { ChangeNotifier, ValueNotifier } from '@gang-chat/core/lib/change_notifier';

/**
 * Ports of the Flutter text-editing framework types the UI kit is built on:
 * `TextSelection`, `TextEditingValue`, `TextEditingController`, `FocusNode`,
 * `UndoHistoryController`, `TextInputFormatter` and the `TextInputAction` /
 * `TextInputType` / `AutofillHints` constants.
 *
 * `Input`, `TextInput` and the read-only text boxes bind a React Native
 * `TextInput` to these objects, so feature code keeps Dart's controller
 * pattern (`controller.text`, `controller.clear()`, `focusNode.requestFocus()`).
 */

export enum TextAffinity {
  upstream = 'upstream',
  downstream = 'downstream',
}

export class TextSelection {
  constructor(opts: { baseOffset: number; extentOffset: number; affinity?: TextAffinity; isDirectional?: boolean }) {
    this.baseOffset = opts.baseOffset;
    this.extentOffset = opts.extentOffset;
    this.affinity = opts.affinity ?? TextAffinity.downstream;
    this.isDirectional = opts.isDirectional ?? false;
  }

  readonly baseOffset: number;
  readonly extentOffset: number;
  readonly affinity: TextAffinity;
  readonly isDirectional: boolean;

  static collapsed(opts: { offset: number; affinity?: TextAffinity }): TextSelection {
    return new TextSelection({ baseOffset: opts.offset, extentOffset: opts.offset, affinity: opts.affinity });
  }

  get start(): number {
    return Math.min(this.baseOffset, this.extentOffset);
  }

  get end(): number {
    return Math.max(this.baseOffset, this.extentOffset);
  }

  get isValid(): boolean {
    return this.start >= 0 && this.end >= 0;
  }

  get isCollapsed(): boolean {
    return this.baseOffset === this.extentOffset;
  }

  textInside(text: string): string {
    return text.substring(this.start, this.end);
  }

  textBefore(text: string): string {
    return text.substring(0, this.start);
  }

  textAfter(text: string): string {
    return text.substring(this.end);
  }

  copyWith(opts: { baseOffset?: number; extentOffset?: number; affinity?: TextAffinity; isDirectional?: boolean }): TextSelection {
    return new TextSelection({
      baseOffset: opts.baseOffset ?? this.baseOffset,
      extentOffset: opts.extentOffset ?? this.extentOffset,
      affinity: opts.affinity ?? this.affinity,
      isDirectional: opts.isDirectional ?? this.isDirectional,
    });
  }

  equals(other: TextSelection | null | undefined): boolean {
    return (
      other != null &&
      other.baseOffset === this.baseOffset &&
      other.extentOffset === this.extentOffset &&
      other.affinity === this.affinity &&
      other.isDirectional === this.isDirectional
    );
  }
}

/** Port of `TextRange` (used for the IME composing region). */
export class TextRange {
  constructor(
    readonly start: number,
    readonly end: number,
  ) {}

  static readonly empty = new TextRange(-1, -1);

  get isValid(): boolean {
    return this.start >= 0 && this.end >= 0;
  }
}

export class TextEditingValue {
  constructor(opts: { text?: string; selection?: TextSelection; composing?: TextRange } = {}) {
    this.text = opts.text ?? '';
    this.selection = opts.selection ?? TextSelection.collapsed({ offset: -1 });
    this.composing = opts.composing ?? TextRange.empty;
  }

  readonly text: string;
  readonly selection: TextSelection;
  readonly composing: TextRange;

  static readonly empty = new TextEditingValue();

  copyWith(opts: { text?: string; selection?: TextSelection; composing?: TextRange }): TextEditingValue {
    return new TextEditingValue({
      text: opts.text ?? this.text,
      selection: opts.selection ?? this.selection,
      composing: opts.composing ?? this.composing,
    });
  }

  equals(other: TextEditingValue | null | undefined): boolean {
    return other != null && other.text === this.text && this.selection.equals(other.selection);
  }
}

/**
 * Port of `TextEditingController`. Listeners are notified on every text or
 * selection change, exactly like the Dart `ValueNotifier<TextEditingValue>`.
 */
export class TextEditingController extends ValueNotifier<TextEditingValue> {
  constructor(opts: { text?: string } = {}) {
    super(
      opts.text == null
        ? TextEditingValue.empty
        : new TextEditingValue({ text: opts.text, selection: TextSelection.collapsed({ offset: -1 }) }),
    );
  }

  static fromValue(value: TextEditingValue | null | undefined): TextEditingController {
    const controller = new TextEditingController();
    if (value != null) controller.value = value;
    return controller;
  }

  override get value(): TextEditingValue {
    return super.value;
  }

  override set value(next: TextEditingValue) {
    if (super.value.equals(next) && super.value.composing === next.composing) return;
    super.value = next;
  }

  get text(): string {
    return this.value.text;
  }

  /** Setting the text resets the selection, like Flutter. */
  set text(next: string) {
    this.value = this.value.copyWith({
      text: next,
      selection: TextSelection.collapsed({ offset: -1 }),
      composing: TextRange.empty,
    });
  }

  get selection(): TextSelection {
    return this.value.selection;
  }

  set selection(next: TextSelection) {
    this.value = this.value.copyWith({ selection: next, composing: TextRange.empty });
  }

  clear(): void {
    this.value = new TextEditingValue({ selection: TextSelection.collapsed({ offset: 0 }) });
  }

  clearComposing(): void {
    this.value = this.value.copyWith({ composing: TextRange.empty });
  }
}

/** The imperative half of a mounted text field that a [FocusNode] drives. */
export interface FocusTarget {
  focus(): void;
  blur(): void;
}

/**
 * Port of `FocusNode` for text fields. A field attaches itself while mounted;
 * `requestFocus` / `unfocus` then move the platform focus, and [hasFocus]
 * follows the field's focus / blur events.
 */
export class FocusNode extends ChangeNotifier {
  constructor(opts: { debugLabel?: string; canRequestFocus?: boolean; skipTraversal?: boolean } = {}) {
    super();
    this.debugLabel = opts.debugLabel ?? null;
    this.canRequestFocus = opts.canRequestFocus ?? true;
    this.skipTraversal = opts.skipTraversal ?? false;
  }

  readonly debugLabel: string | null;
  canRequestFocus: boolean;
  skipTraversal: boolean;

  private _hasFocus = false;
  private _target: FocusTarget | null = null;
  private _pendingFocus = false;

  get hasFocus(): boolean {
    return this._hasFocus;
  }

  /** Same as [hasFocus] for leaf text fields. */
  get hasPrimaryFocus(): boolean {
    return this._hasFocus;
  }

  requestFocus(): void {
    if (!this.canRequestFocus) return;
    if (this._target == null) {
      // Focus once the field mounts (Flutter queues the request too).
      this._pendingFocus = true;
      return;
    }
    this._target.focus();
  }

  unfocus(): void {
    this._pendingFocus = false;
    this._target?.blur();
  }

  /** @internal Called by the text field that owns this node. */
  _attach(target: FocusTarget | null): void {
    this._target = target;
    if (target != null && this._pendingFocus) {
      this._pendingFocus = false;
      target.focus();
    }
    if (target == null && this._hasFocus) this._setHasFocus(false);
  }

  /** @internal Called from the field's onFocus / onBlur. */
  _setHasFocus(focused: boolean): void {
    if (this._hasFocus === focused) return;
    this._hasFocus = focused;
    this.notifyListeners();
  }
}

/**
 * Port of `UndoHistoryController`. The browser keeps its own undo stack for
 * native inputs, so this records committed values for fields that want
 * explicit undo / redo (the text context menu's 撤销 / 重做).
 */
export class UndoHistoryController extends ChangeNotifier {
  private _undoStack: TextEditingValue[] = [];
  private _redoStack: TextEditingValue[] = [];
  private _controller: TextEditingController | null = null;
  private _applying = false;
  private _last: TextEditingValue | null = null;

  private readonly _onControllerChanged = () => {
    const controller = this._controller;
    if (controller == null || this._applying) return;
    const value = controller.value;
    if (this._last != null && this._last.text === value.text) {
      this._last = value;
      return;
    }
    if (this._last != null) {
      this._undoStack.push(this._last);
      if (this._undoStack.length > 100) this._undoStack.shift();
      this._redoStack = [];
    }
    this._last = value;
    this.notifyListeners();
  };

  get canUndo(): boolean {
    return this._undoStack.length > 0;
  }

  get canRedo(): boolean {
    return this._redoStack.length > 0;
  }

  /** @internal Binds the history to the field's controller. */
  _attach(controller: TextEditingController | null): void {
    if (this._controller === controller) return;
    this._controller?.removeListener(this._onControllerChanged);
    this._controller = controller;
    this._undoStack = [];
    this._redoStack = [];
    this._last = controller?.value ?? null;
    controller?.addListener(this._onControllerChanged);
  }

  undo(): void {
    const controller = this._controller;
    const previous = this._undoStack.pop();
    if (controller == null || previous == null) return;
    this._redoStack.push(controller.value);
    this._apply(controller, previous);
  }

  redo(): void {
    const controller = this._controller;
    const next = this._redoStack.pop();
    if (controller == null || next == null) return;
    this._undoStack.push(controller.value);
    this._apply(controller, next);
  }

  private _apply(controller: TextEditingController, value: TextEditingValue): void {
    this._applying = true;
    try {
      controller.value = value;
      this._last = value;
    } finally {
      this._applying = false;
    }
    this.notifyListeners();
  }

  override dispose(): void {
    this._controller?.removeListener(this._onControllerChanged);
    this._controller = null;
    super.dispose();
  }
}

/** Port of `TextInputFormatter`. */
export interface TextInputFormatter {
  formatEditUpdate(oldValue: TextEditingValue, newValue: TextEditingValue): TextEditingValue;
}

export class FilteringTextInputFormatter implements TextInputFormatter {
  constructor(
    readonly filterPattern: RegExp,
    readonly opts: { allow: boolean; replacementString?: string },
  ) {}

  static allow(pattern: RegExp, opts: { replacementString?: string } = {}): FilteringTextInputFormatter {
    return new FilteringTextInputFormatter(pattern, { allow: true, replacementString: opts.replacementString });
  }

  static deny(pattern: RegExp, opts: { replacementString?: string } = {}): FilteringTextInputFormatter {
    return new FilteringTextInputFormatter(pattern, { allow: false, replacementString: opts.replacementString });
  }

  static readonly digitsOnly = FilteringTextInputFormatter.allow(/[0-9]/);
  static readonly singleLineFormatter = FilteringTextInputFormatter.deny(/\n/);

  formatEditUpdate(_oldValue: TextEditingValue, newValue: TextEditingValue): TextEditingValue {
    const replacement = this.opts.replacementString ?? '';
    const flags = this.filterPattern.flags.includes('g') ? this.filterPattern.flags : `${this.filterPattern.flags}g`;
    const pattern = new RegExp(this.filterPattern.source, flags);
    let text: string;
    if (this.opts.allow) {
      const matches = newValue.text.match(pattern) ?? [];
      // Characters between matches collapse to the replacement string.
      text = '';
      let cursor = 0;
      for (const match of newValue.text.matchAll(pattern)) {
        if ((match.index ?? 0) > cursor && replacement.length > 0) text += replacement;
        text += match[0];
        cursor = (match.index ?? 0) + match[0].length;
      }
      if (matches.length === 0 && newValue.text.length > 0 && replacement.length > 0) text = replacement;
    } else {
      text = newValue.text.replace(pattern, replacement);
    }
    if (text === newValue.text) return newValue;
    const offset = Math.min(text.length, Math.max(0, newValue.selection.extentOffset - (newValue.text.length - text.length)));
    return new TextEditingValue({ text, selection: TextSelection.collapsed({ offset }) });
  }
}

export class LengthLimitingTextInputFormatter implements TextInputFormatter {
  constructor(readonly maxLength: number | null) {}

  formatEditUpdate(oldValue: TextEditingValue, newValue: TextEditingValue): TextEditingValue {
    const max = this.maxLength;
    if (max == null || max < 0) return newValue;
    const chars = Array.from(newValue.text);
    if (chars.length <= max) return newValue;
    if (Array.from(oldValue.text).length === max) return oldValue;
    const text = chars.slice(0, max).join('');
    return new TextEditingValue({ text, selection: TextSelection.collapsed({ offset: text.length }) });
  }
}

/** Port of `TextInputAction` (maps onto React Native `returnKeyType` / `enterKeyHint`). */
export enum TextInputAction {
  none = 'none',
  unspecified = 'unspecified',
  done = 'done',
  go = 'go',
  search = 'search',
  send = 'send',
  next = 'next',
  previous = 'previous',
  newline = 'newline',
}

/** Port of `TextInputType` (maps onto React Native `keyboardType` / `inputMode`). */
export enum TextInputType {
  text = 'text',
  multiline = 'multiline',
  number = 'number',
  phone = 'phone',
  datetime = 'datetime',
  emailAddress = 'emailAddress',
  url = 'url',
  visiblePassword = 'visiblePassword',
  name = 'name',
  streetAddress = 'streetAddress',
  none = 'none',
}

/** Port of `AutofillHints` (values are the HTML `autocomplete` tokens). */
export const AutofillHints = {
  email: 'email',
  username: 'username',
  password: 'current-password',
  newPassword: 'new-password',
  newUsername: 'username',
  oneTimeCode: 'one-time-code',
  name: 'name',
  nickname: 'nickname',
  telephoneNumber: 'tel',
  url: 'url',
} as const;

export type AutofillHint = (typeof AutofillHints)[keyof typeof AutofillHints] | string;
