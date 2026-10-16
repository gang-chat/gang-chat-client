import type { CSSProperties, PointerEvent as ReactPointerEvent, ReactNode } from 'react';

import * as message_mentions from '@gang-chat/core/app/message_mentions';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import type { Listenable } from '@gang-chat/core/lib/change_notifier';
import { asString, type JsonMap } from '@gang-chat/core/lib/json';
import type { CurrentUser, Message, PublicRoom, RoomMember, UserSummary } from '@gang-chat/core/protocol/models';
import { ExternalUriLauncher } from '@gang-chat/core/shell/external_uri_launcher';

import { SystemMouseCursors, kTouchSlop } from '../lib/mouse_cursors';
import { TextSelection } from '../lib/text_editing';
import { ViewState, useViewState } from '../lib/view_state';
import { FloatingNoticeTone } from '../ui/feedback';
import type { WindowPoint } from '../ui/platform_gestures';
import {
  TextFieldEditingShortcuts,
  buildTextFieldContextMenu,
  isTextContextMenuPanelHit,
  type EditableTextState,
  type TextPointerDownEvent,
} from '../ui/text_context_menu';
import { UiColors, UiTypography } from '../ui/tokens';
import { _showMessageContextNotice } from './chat_messages_actions';
import { showUserProfileCardAtPosition, type RoomProfileResolver, type UserProfileActionBuilder } from './room_profile_card';

/**
 * The selectable text body of a chat message (`_TextBody` and the link /
 * mention span builder from `chat_messages.dart`).
 *
 * PORT: Flutter rendered the body as a read-only `TextField` whose
 * `_MessageTextController.buildTextSpan` produced link and mention spans with
 * tap recognizers, wrapped in `IntrinsicWidth` + `_TightReadOnlyEditableLayout`
 * so the bubble hugged the text. The DOM version is selectable inline text
 * (`user-select: text`) with clickable spans and `width: fit-content`, which
 * gives the same shrink-wrapped layout without the editor's caret allowance;
 * the selection is the document selection, observed through `selectionchange`.
 * A drag can extend that selection across neighbouring bubbles, which a
 * Flutter field could not.
 */

const _bodyLineHeightPx = (UiTypography.body.fontSize as number) * (UiTypography.body.lineHeight as number);

export interface _TextBodyProps {
  message: Message;
  profileCurrentUser: CurrentUser;
  currentUser: UserSummary;
  ownerUserId: string | null;
  mentionMembers: readonly RoomMember[];
  mentionHighlighted: boolean;
  onSelectionActiveChanged: (active: boolean) => void;
  selectionResetListenable?: Listenable | null;
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  isUserInLive?: ((userId: string) => boolean) | null;
}

interface _DomSelectionSnapshot {
  anchorNode: Node | null;
  anchorOffset: number;
  focusNode: Node | null;
  focusOffset: number;
}

class _TextBodyState extends ViewState<_TextBodyProps> {
  readonly _textRef: { current: HTMLDivElement | null } = { current: null };
  _activeTextContextMenuState: EditableTextState | null = null;
  _lastPointerDownPosition: WindowPoint | null = null;
  _textContextMenuOpen = false;
  _textContextMenuActionPressed = false;
  // PORT: whether the body had a selection when the current pointer went
  // down. The bubble's message menu and the text menu both answer the same
  // DOM `contextmenu`; like the Dart bubble, the text menu only takes over
  // when a selection already existed.
  _selectionActiveAtPointerDown = false;
  _reportedSelectionActive = false;
  _androidOutsidePointer: number | null = null;
  _androidOutsidePointerOrigin: WindowPoint | null = null;
  _androidOutsideInitialSelection: _DomSelectionSnapshot | null = null;
  _androidOutsidePointerMoved = false;
  _androidOutsideSelectionChanged = false;
  private _tapOutsideInstalled = false;
  private _resetFrame: number | null = null;

  override initState(): void {
    _addSelectionChangeListener(this._handleControllerChanged);
    this.widget.selectionResetListenable?.addListener(this._handleSelectionReset);
  }

  override didUpdateProps(oldWidget: _TextBodyProps): void {
    if (oldWidget.selectionResetListenable !== this.widget.selectionResetListenable) {
      oldWidget.selectionResetListenable?.removeListener(this._handleSelectionReset);
      this.widget.selectionResetListenable?.addListener(this._handleSelectionReset);
    }
    // PORT: the Dart controller swapped its text while clamping the selection;
    // React re-renders the spans and the document keeps whatever selection
    // still resolves.
  }

  override dispose(): void {
    this._stopAndroidOutsidePointerTracking();
    this._setTapOutsideInstalled(false);
    if (this._resetFrame != null) cancelAnimationFrame(this._resetFrame);
    this.widget.selectionResetListenable?.removeListener(this._handleSelectionReset);
    _removeSelectionChangeListener(this._handleControllerChanged);
    this.widget.onSelectionActiveChanged(false);
  }

  build(): ReactNode {
    const message = this.widget.message;
    const text = _displayMessageBody(message);
    return (
      <TextFieldEditingShortcuts
        controller={null}
        focusNode={null}
        undoController={null}
        onGlobalPrimaryPointerDownDuringSecondaryClickProtection={
          this._handleGlobalPrimaryPointerDownDuringTextSelectionProtection
        }
        contextMenuBuilder={this._buildContextMenu}
      >
        <div
          ref={(node) => {
            this._textRef.current = node;
          }}
          data-testid={`message-text-layout-${message.id}`}
          onPointerDown={this._handlePointerDown}
          style={_textBodyStyle}
        >
          {_buildMessageTextSpans({
            text,
            mentions: message.mentions,
            mentionMembers: this.widget.mentionMembers,
            onOpenMentionUser: this._handleOpenMentionUser,
            onOpenLink: this._handleOpenLink,
            isSelecting: () => this._hasSelection,
          })}
        </div>
      </TextFieldEditingShortcuts>
    );
  }

  _handleControllerChanged = (): void => {
    const initialSelection = this._androidOutsideInitialSelection;
    if (initialSelection != null && !_sameSelection(initialSelection, _domSelectionSnapshot())) {
      this._androidOutsideSelectionChanged = true;
    }
    const active = this._hasSelection;
    this._setTapOutsideInstalled(active);
    if (active === this._reportedSelectionActive) return;
    this._reportedSelectionActive = active;
    this.widget.onSelectionActiveChanged(active);
  };

  _handleSelectionReset = (): void => {
    this._collapseSelection({ hideToolbar: true });
    // The bubble context menu and the platform both resolve the same Android
    // long press. The platform can publish its selection later in this frame,
    // so reconcile once more after both have completed.
    if (this._resetFrame != null) cancelAnimationFrame(this._resetFrame);
    this._resetFrame = requestAnimationFrame(() => {
      this._resetFrame = null;
      if (!this.mounted) return;
      this._collapseSelection({ hideToolbar: true });
    });
  };

  _buildContextMenu = (editableTextState: EditableTextState): ReactNode => {
    // PORT: see [_selectionActiveAtPointerDown]. Flutter's read-only menu
    // without a selection (and without 全选) was empty anyway.
    if (!this._selectionActiveAtPointerDown) return null;
    this._activeTextContextMenuState = editableTextState;
    return buildTextFieldContextMenu(editableTextState, {
      readOnly: true,
      showReadOnlySelectAll: false,
      onOpenChanged: this._handleTextContextMenuOpenChanged,
      onActionPressed: this._handleTextContextMenuActionPressed,
    });
  };

  _handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>): void => {
    this._selectionActiveAtPointerDown = this._hasSelection;
    if ((event.buttons & 1) !== 0) {
      this._lastPointerDownPosition = { x: event.clientX, y: event.clientY };
    }
  };

  _handleTextContextMenuActionPressed = (): void => {
    this._textContextMenuActionPressed = true;
  };

  _handleTextContextMenuOpenChanged = (open: boolean): void => {
    if (!this.mounted) return;
    if (open) {
      this._textContextMenuOpen = true;
      this._textContextMenuActionPressed = false;
      return;
    }

    const closedByOutsideClick = this._textContextMenuOpen && !this._textContextMenuActionPressed;
    this._textContextMenuOpen = false;
    this._textContextMenuActionPressed = false;
    this._activeTextContextMenuState = null;
    if (closedByOutsideClick && !HostPlatform.isAndroid) {
      this._collapseSelection();
    }
  };

  get _hasSelection(): boolean {
    const node = this._textRef.current;
    if (node == null) return false;
    return _selectedTextWithin(node).length > 0;
  }

  // Port of `TapRegion(onTapOutside:)`: a primary press anywhere outside the
  // body (and outside its text menu, which shares the tap-region group)
  // collapses the selection. Only listened to while a selection exists.
  private _setTapOutsideInstalled(installed: boolean): void {
    if (installed === this._tapOutsideInstalled || typeof document === 'undefined') return;
    this._tapOutsideInstalled = installed;
    if (installed) {
      document.addEventListener('pointerdown', this._handleDocumentPointerDown, true);
    } else {
      document.removeEventListener('pointerdown', this._handleDocumentPointerDown, true);
    }
  }

  private _handleDocumentPointerDown = (event: PointerEvent): void => {
    const node = this._textRef.current;
    if (node == null) return;
    if (event.target instanceof Node && node.contains(event.target)) return;
    if (isTextContextMenuPanelHit({ x: event.clientX, y: event.clientY })) return;
    this._handleTapOutside(event);
  };

  _handleTapOutside(event: PointerEvent): void {
    if ((event.buttons & 1) === 0) return;
    if (HostPlatform.isAndroid) {
      this._startAndroidOutsidePointerTracking(event);
      return;
    }
    this._collapseSelection({ hideToolbar: true });
  }

  _startAndroidOutsidePointerTracking(event: PointerEvent): void {
    if (!this._hasSelection) return;
    this._stopAndroidOutsidePointerTracking();
    this._androidOutsidePointer = event.pointerId;
    this._androidOutsidePointerOrigin = { x: event.clientX, y: event.clientY };
    this._androidOutsideInitialSelection = _domSelectionSnapshot();
    this._androidOutsidePointerMoved = false;
    this._androidOutsideSelectionChanged = false;
    document.addEventListener('pointermove', this._handleAndroidOutsidePointerEvent, true);
    document.addEventListener('pointerup', this._handleAndroidOutsidePointerEvent, true);
    document.addEventListener('pointercancel', this._handleAndroidOutsidePointerEvent, true);
  }

  _handleAndroidOutsidePointerEvent = (event: PointerEvent): void => {
    if (event.pointerId !== this._androidOutsidePointer) return;
    const origin = this._androidOutsidePointerOrigin;
    if (origin != null && (event.clientX !== origin.x || event.clientY !== origin.y)) {
      this._androidOutsidePointerMoved =
        this._androidOutsidePointerMoved || Math.hypot(event.clientX - origin.x, event.clientY - origin.y) > kTouchSlop;
    }
    const initialSelection = this._androidOutsideInitialSelection;
    if (initialSelection != null && !_sameSelection(initialSelection, _domSelectionSnapshot())) {
      this._androidOutsideSelectionChanged = true;
    }
    if (event.type !== 'pointerup' && event.type !== 'pointercancel') return;

    const shouldCollapse =
      event.type === 'pointerup' && !this._androidOutsidePointerMoved && !this._androidOutsideSelectionChanged;
    const selectionToCollapse = this._androidOutsideInitialSelection;
    this._stopAndroidOutsidePointerTracking();
    if (!shouldCollapse || selectionToCollapse == null) return;
    requestAnimationFrame(() => {
      if (!this.mounted || !_sameSelection(selectionToCollapse, _domSelectionSnapshot())) return;
      this._collapseSelection({ hideToolbar: true });
    });
  };

  _stopAndroidOutsidePointerTracking(): void {
    if (this._androidOutsidePointer != null && typeof document !== 'undefined') {
      document.removeEventListener('pointermove', this._handleAndroidOutsidePointerEvent, true);
      document.removeEventListener('pointerup', this._handleAndroidOutsidePointerEvent, true);
      document.removeEventListener('pointercancel', this._handleAndroidOutsidePointerEvent, true);
    }
    this._androidOutsidePointer = null;
    this._androidOutsidePointerOrigin = null;
    this._androidOutsideInitialSelection = null;
    this._androidOutsidePointerMoved = false;
    this._androidOutsideSelectionChanged = false;
  }

  _handleGlobalPrimaryPointerDownDuringTextSelectionProtection = (event: TextPointerDownEvent): void => {
    if (isTextContextMenuPanelHit(event.position)) return;
    this._collapseSelection({ hideToolbar: true });
  };

  _collapseSelection(opts: { hideToolbar?: boolean } = {}): void {
    const { hideToolbar = false } = opts;
    if (!this._hasSelection) return;
    const selection = window.getSelection();
    if (selection == null) return;
    const node = this._textRef.current;
    // Collapse onto the extent (focus) end, like `TextSelection.collapsed(
    // offset: selection.extentOffset)`.
    if (selection.focusNode != null && node != null && node.contains(selection.focusNode)) {
      selection.collapse(selection.focusNode, selection.focusOffset);
    } else {
      selection.collapseToEnd();
    }
    this._reportedSelectionActive = false;
    this._setTapOutsideInstalled(false);
    this.widget.onSelectionActiveChanged(false);
    if (hideToolbar) this._activeTextContextMenuState?.hideToolbar();
  }

  _handleOpenLink = (uri: string): void => {
    void this._openLink(uri);
  };

  _handleOpenMentionUser = (user: UserSummary): void => {
    const position = this._lastPointerDownPosition ?? this._fallbackProfilePosition();
    void this._openMentionUserProfile(user, position);
  };

  async _openMentionUserProfile(user: UserSummary, position: WindowPoint): Promise<void> {
    const resolver = this.widget.onResolveSenderProfile ?? null;
    let displayUser = user;
    if (resolver != null) {
      try {
        displayUser = await resolver(user);
      } catch {
        displayUser = user;
      }
    }
    if (!this.mounted) return;
    await showUserProfileCardAtPosition({
      position,
      user: displayUser,
      currentUser: this.widget.profileCurrentUser,
      onResolveProfile: resolver,
      onResolveRoomProfile: this.widget.onResolveRoomProfile ?? null,
      onEnterCommonRoom: this.widget.onEnterProfileRoom ?? null,
      profileActionBuilder: this.widget.profileActionBuilder ?? null,
      inLive: this.widget.isUserInLive?.(displayUser.id) ?? false,
      showRoomRole: true,
      resolveOnOpen: false,
    });
  }

  _fallbackProfilePosition(): WindowPoint {
    const node = this._textRef.current;
    if (node == null || !node.isConnected) return { x: 0, y: 0 };
    const rect = node.getBoundingClientRect();
    return { x: rect.left + rect.width / 2, y: rect.bottom };
  }

  async _openLink(uri: string): Promise<void> {
    try {
      await new ExternalUriLauncher().open(uri);
    } catch {
      if (!this.mounted) return;
      _showMessageContextNotice('无法打开链接', { tone: FloatingNoticeTone.error });
    }
  }
}

export function _TextBody(props: _TextBodyProps) {
  const state = useViewState(() => new _TextBodyState(), props);
  return <>{state.build()}</>;
}

const _textBodyStyle: CSSProperties = {
  ...UiTypography.body,
  // IntrinsicWidth + _TightReadOnlyEditableLayout: hug the text, wrap at the
  // bubble's max width.
  width: 'fit-content',
  maxWidth: '100%',
  minWidth: 0,
  // `minLines: 1`.
  minHeight: _bodyLineHeightPx,
  whiteSpace: 'pre-wrap',
  overflowWrap: 'break-word',
  wordBreak: 'normal',
  userSelect: 'text',
  WebkitUserSelect: 'text',
  cursor: SystemMouseCursors.text,
};

export function _displayMessageBody(message: Message): string {
  return message.body.trimEnd();
}

// ---------------------------------------------------------------------------
// Link and mention spans (`_MessageTextController.buildTextSpan`).

const _linkStyle: CSSProperties = {
  color: UiColors.accent,
  textDecorationLine: 'underline',
  textDecorationColor: UiColors.accent,
  cursor: SystemMouseCursors.click,
};

const _mentionStyle: CSSProperties = {
  color: UiColors.controlAccent,
  fontWeight: 600,
  textDecorationLine: 'none',
};

/**
 * Builds the inline children for [text]: links and @mentions as styled,
 * clickable spans, everything else as plain text.
 *
 * PORT: replaces `_MessageTextController.buildTextSpan` and its
 * `TapGestureRecognizer` bookkeeping. A click that ends a drag-selection
 * ([isSelecting]) is not a tap, as with Flutter's recognizers.
 */
export function _buildMessageTextSpans(opts: {
  text: string;
  mentions: readonly JsonMap[];
  mentionMembers: readonly RoomMember[];
  onOpenMentionUser: (user: UserSummary) => void;
  onOpenLink: (uri: string) => void;
  isSelecting?: () => boolean;
}): ReactNode {
  const { text, mentions, mentionMembers, onOpenMentionUser, onOpenLink, isSelecting = () => false } = opts;
  const linkMatches = _messageLinkMatches(text);
  const mentionMatches = message_mentions
    .messageMentionRanges(text, { labels: _knownMentionLabels(mentions) })
    .filter((range) => !_overlapsAnyLink(range, linkMatches));
  if (linkMatches.length === 0 && mentionMatches.length === 0) return text;

  const segments: _MessageInlineSegment[] = [
    ...linkMatches.map((match) => _MessageInlineSegment.link(match)),
    ...mentionMatches.map((match) => _MessageInlineSegment.mention(match)),
  ].sort((a, b) => a.start - b.start);
  const children: ReactNode[] = [];
  let cursor = 0;
  for (const segment of segments) {
    if (segment.start < cursor) continue;
    if (segment.start > cursor) {
      children.push(text.substring(cursor, segment.start));
    }
    const tapHandler = (handler: () => void) => (event: { button: number; stopPropagation(): void }) => {
      if (event.button !== 0 || isSelecting()) return;
      event.stopPropagation();
      handler();
    };
    if (segment.link != null) {
      const match = segment.link;
      const key = `${match.start}:${match.end}:${match.uri}`;
      children.push(
        <span key={key} style={_linkStyle} onClick={tapHandler(() => onOpenLink(match.uri))}>
          {text.substring(match.start, match.end)}
        </span>,
      );
    } else {
      const range = segment.mention!;
      const label = text.substring(range.start + 1, range.end);
      const mentionMember =
        message_mentions.messageMentionKindForLabel(label) === message_mentions.MessageMentionKind.user
          ? message_mentions.resolveMessageMentionMember({ label, members: mentionMembers })
          : null;
      const key = `${range.start}:${range.end}:${mentionMember?.user.id ?? ''}`;
      children.push(
        <span
          key={key}
          style={mentionMember == null ? _mentionStyle : { ..._mentionStyle, cursor: SystemMouseCursors.click }}
          onClick={mentionMember == null ? undefined : tapHandler(() => onOpenMentionUser(mentionMember.user))}
        >
          {text.substring(range.start, range.end)}
        </span>,
      );
    }
    cursor = segment.end;
  }
  if (cursor < text.length) {
    children.push(text.substring(cursor));
  }
  return children;
}

function _knownMentionLabels(mentions: readonly JsonMap[]): string[] {
  const labels: string[] = [];
  for (const mention of mentions) {
    const label = asString(mention['label']);
    if (label != null && label.trim().length > 0) labels.push(label);
  }
  return labels;
}

export class _MessageInlineSegment {
  private constructor(
    readonly link: _MessageLinkMatch | null,
    readonly mention: message_mentions.MessageMentionRange | null,
  ) {}

  static link(link: _MessageLinkMatch): _MessageInlineSegment {
    return new _MessageInlineSegment(link, null);
  }

  static mention(mention: message_mentions.MessageMentionRange): _MessageInlineSegment {
    return new _MessageInlineSegment(null, mention);
  }

  get start(): number {
    return this.link?.start ?? this.mention!.start;
  }

  get end(): number {
    return this.link?.end ?? this.mention!.end;
  }
}

function _overlapsAnyLink(range: message_mentions.MessageMentionRange, links: readonly _MessageLinkMatch[]): boolean {
  for (const link of links) {
    if (range.start < link.end && range.end > link.start) return true;
  }
  return false;
}

export class _MessageLinkMatch {
  constructor(opts: { start: number; end: number; uri: string }) {
    this.start = opts.start;
    this.end = opts.end;
    this.uri = opts.uri;
  }

  readonly start: number;
  readonly end: number;
  /** The absolute http(s) URL to open (Dart `Uri`). */
  readonly uri: string;
}

const _messageLinkPattern = /(?:(?:https?:\/\/)|(?:www\.))[^\s<>{}[\]"']+/gi;

const _messageLinkTrailingPunctuation = '.,!?;:，。！？；：、)]}）】》';

export function _messageLinkMatches(value: string): _MessageLinkMatch[] {
  const matches: _MessageLinkMatch[] = [];
  for (const match of value.matchAll(_messageLinkPattern)) {
    const start = match.index ?? 0;
    let end = start + match[0].length;
    while (end > start && _messageLinkTrailingPunctuation.includes(value[end - 1]!)) {
      end--;
    }
    if (end <= start) continue;
    const raw = value.substring(start, end);
    const uri = _messageLinkUri(raw);
    if (uri == null) continue;
    matches.push(new _MessageLinkMatch({ start, end, uri }));
  }
  return matches;
}

export function _messageLinkUri(value: string): string | null {
  const lower = value.toLowerCase();
  const normalized = lower.startsWith('http://') || lower.startsWith('https://') ? value : `https://${value}`;
  // PORT: Dart's lenient `Uri.tryParse` vs the WHATWG URL parser; both reject
  // a missing host and the result is opened as the parser's normalized href.
  let uri: URL;
  try {
    uri = new URL(normalized);
  } catch {
    return null;
  }
  if (uri.hostname.length === 0) return null;
  if (uri.protocol !== 'http:' && uri.protocol !== 'https:') return null;
  return uri.href;
}

export function _clampMessageTextSelection(selection: TextSelection, length: number): TextSelection {
  if (!selection.isValid) return TextSelection.collapsed({ offset: length });
  return new TextSelection({
    baseOffset: Math.min(selection.baseOffset, length),
    extentOffset: Math.min(selection.extentOffset, length),
    affinity: selection.affinity,
    isDirectional: selection.isDirectional,
  });
}

// ---------------------------------------------------------------------------
// Document selection helpers.

const _selectionChangeListeners = new Set<() => void>();

function _onDocumentSelectionChange(): void {
  for (const listener of [..._selectionChangeListeners]) listener();
}

function _addSelectionChangeListener(listener: () => void): void {
  if (typeof document === 'undefined') return;
  if (_selectionChangeListeners.size === 0) {
    document.addEventListener('selectionchange', _onDocumentSelectionChange);
  }
  _selectionChangeListeners.add(listener);
}

function _removeSelectionChangeListener(listener: () => void): void {
  if (!_selectionChangeListeners.delete(listener)) return;
  if (_selectionChangeListeners.size === 0 && typeof document !== 'undefined') {
    document.removeEventListener('selectionchange', _onDocumentSelectionChange);
  }
}

/** The part of the document selection that lies inside [node]. */
function _selectedTextWithin(node: Node): string {
  if (typeof window === 'undefined') return '';
  const selection = window.getSelection();
  if (selection == null || selection.rangeCount === 0 || selection.isCollapsed) return '';
  let text = '';
  for (let index = 0; index < selection.rangeCount; index += 1) {
    const range = selection.getRangeAt(index);
    if (!range.intersectsNode(node)) continue;
    const within = document.createRange();
    within.selectNodeContents(node);
    if (range.compareBoundaryPoints(Range.START_TO_START, within) > 0) {
      within.setStart(range.startContainer, range.startOffset);
    }
    if (range.compareBoundaryPoints(Range.END_TO_END, within) < 0) {
      within.setEnd(range.endContainer, range.endOffset);
    }
    if (!within.collapsed) text += within.toString();
  }
  return text;
}

function _domSelectionSnapshot(): _DomSelectionSnapshot | null {
  if (typeof window === 'undefined') return null;
  const selection = window.getSelection();
  if (selection == null) return null;
  return {
    anchorNode: selection.anchorNode,
    anchorOffset: selection.anchorOffset,
    focusNode: selection.focusNode,
    focusOffset: selection.focusOffset,
  };
}

function _sameSelection(a: _DomSelectionSnapshot | null, b: _DomSelectionSnapshot | null): boolean {
  if (a == null || b == null) return a === b;
  return (
    a.anchorNode === b.anchorNode &&
    a.anchorOffset === b.anchorOffset &&
    a.focusNode === b.focusNode &&
    a.focusOffset === b.focusOffset
  );
}
