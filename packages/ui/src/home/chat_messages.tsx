import {
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';

import type { FileTransferState } from '@gang-chat/core/app/file_transfer_state';
import * as live_display from '@gang-chat/core/app/live_display';
import * as message_display from '@gang-chat/core/app/message_display';
import * as message_mentions from '@gang-chat/core/app/message_mentions';
import * as room_display from '@gang-chat/core/app/room_display';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Timer } from '@gang-chat/core/lib/async';
import { ValueNotifier } from '@gang-chat/core/lib/change_notifier';
import type { CurrentUser, LiveState, Message, PublicRoom, RoomMember, UserSummary } from '@gang-chat/core/protocol/models';

import { EdgeInsets, paddingStyle } from '../lib/edge_insets';
import { Center, Column, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { ViewState, useViewState } from '../lib/view_state';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button } from '../ui/button';
import { UiContextMenuItem, UiContextMenuSection, UiContextMenuTriggerRegion, showUiContextMenu } from '../ui/context_menu';
import { FloatingNotice, FloatingNoticeEmitter, FloatingNoticeTone } from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { UiAndroidLongPressTracker, type WindowPoint } from '../ui/platform_gestures';
import { PressableSurface } from '../ui/surface';
import { UiColors, UiCurves, UiRadii, UiTypography, alphaBlend, argb, withAlpha } from '../ui/tokens';
import { ChatImagePreviewActions } from './chat_image_preview';
import {
  ChatMessageActions,
  ChatVoicePlaybackActions,
  _senderName,
  _showChatMessageContextMenu,
  chatRoomUsernameColor,
  type ChatFileDownloadActions,
} from './chat_messages_actions';
import { ChatMessageContent, _showStickerContextMenu } from './chat_messages_content';
import { ChatRemovedMessageContent, ChatSystemMessageContent } from './chat_messages_system';
// Circular (chat_pane imports this module): these are read only at render time.
import { _chatFloatingEdgeInset, _chatHorizontalPadding, _incomingBubble, _messageMaxWidth, _outgoingBubble } from './chat_pane';
import { _AvatarHoverCard, _GangBotAvatar, _GangBotBadge, _avatarHoverCardTestCurrentUser } from './chat_profile_card';
import type { RoomProfileResolver, UserProfileActionBuilder } from './room_profile_card';

/**
 * Port of `chat_messages.dart` (a part of `chat_pane.dart`): the room's
 * message list ([_MessageStage]), message rows and bubbles, and the message
 * contents that other surfaces (message history) reuse.
 *
 * The Dart part is split across sibling modules; this module re-exports all
 * of them under their Dart names:
 *  - `chat_messages_actions.ts`: action bundles, the message context menu,
 *    sender name / colour helpers;
 *  - `chat_messages_system.tsx`: system and recalled-message rows;
 *  - `chat_messages_content.tsx`: [ChatMessageContent] and its bodies;
 *  - `chat_messages_text.tsx`: the selectable text body.
 */

export * from './chat_messages_actions';
export * from './chat_messages_content';
export * from './chat_messages_system';
export * from './chat_messages_text';

/**
 * How close (in logical pixels) to the newest message counts as "at bottom"
 * for saved scroll anchors and the jump-to-latest affordance.
 */
const _autoScrollFollowThreshold = 120;
const _chatMessageListTopPadding = 18;
// PORT: `_messageListCacheExtent` (1200px) sized the sliver cache of
// Flutter's lazily built ListView. The DOM list renders every row of the
// loaded page (one server page plus live messages), so there is no cache
// extent; every lookup below that Flutter could miss for unbuilt rows simply
// finds the row.
const _messageBubblePadding = EdgeInsets.symmetric({ horizontal: 12, vertical: 10 });

/**
 * A remembered scroll position for one room's message list. The list widget is
 * torn down and rebuilt whenever the pane swaps (e.g. opening the live
 * channel and coming back), so we stash the last position here keyed by room
 * id and restore it on the next mount instead of snapping to the bottom.
 */
export class _ScrollAnchor {
  constructor(opts: { offset: number; atBottom: boolean }) {
    this.offset = opts.offset;
    this.atBottom = opts.atBottom;
  }

  readonly offset: number;
  readonly atBottom: boolean;
}

export class _ViewportAnchor {
  constructor(opts: { clientMessageId: string; top: number }) {
    this.clientMessageId = opts.clientMessageId;
    this.top = opts.top;
  }

  readonly clientMessageId: string;
  readonly top: number;
}

/**
 * Stands in for the `ScrollController` of Flutter's reversed `ListView`.
 *
 * PORT: the list is a `flex-direction: column-reverse` scroll box. Its scroll
 * origin is the bottom edge: Chromium reports `scrollTop` 0 at the newest
 * message and negative values above it. [pixels] is the Flutter offset (the
 * distance from the newest message, `minScrollExtent` 0), so — exactly like
 * the reversed viewport — rows inserted above or below keep the pixel offset,
 * and an offset of 0 follows new messages. `overflow-anchor: none` turns off
 * Chromium's own scroll anchoring so nothing else moves the offset.
 */
export class _MessageListScrollController {
  constructor(opts: { initialScrollOffset: number }) {
    this.initialScrollOffset = opts.initialScrollOffset;
  }

  readonly initialScrollOffset: number;
  element: HTMLDivElement | null = null;
  private _initialOffsetApplied = false;
  private _animation: { cancel: () => void } | null = null;

  /** Ref callback for the scroll box (`ScrollController.attach`). */
  attach = (element: HTMLDivElement | null): void => {
    this.element = element;
    if (element == null || this._initialOffsetApplied) return;
    this._initialOffsetApplied = true;
    // The first layout lands at the initial offset (before the first paint).
    if (this.initialScrollOffset > 0) this._setPixels(this.initialScrollOffset);
  };

  get hasClients(): boolean {
    return this.element != null && this.element.isConnected;
  }

  get pixels(): number {
    const element = this.element;
    if (element == null) return 0;
    return Math.max(0, -element.scrollTop);
  }

  get minScrollExtent(): number {
    return 0;
  }

  get maxScrollExtent(): number {
    const element = this.element;
    if (element == null) return 0;
    return Math.max(0, element.scrollHeight - element.clientHeight);
  }

  get viewportDimension(): number {
    return this.element?.clientHeight ?? 0;
  }

  /** `jumpTo`: stops any running animation, then moves (clamped, ClampingScrollPhysics). */
  jumpTo(value: number): void {
    this.cancelAnimation();
    this._setPixels(value);
  }

  /** `animateTo(..).whenComplete`: resolves when the animation ends or is interrupted. */
  animateTo(value: number, opts: { duration: number; curve?: (t: number) => number }): Promise<void> {
    this.cancelAnimation();
    const element = this.element;
    if (element == null) return Promise.resolve();
    const { duration, curve = _easeOutCubic } = opts;
    const start = this.pixels;
    const target = Math.min(Math.max(value, this.minScrollExtent), this.maxScrollExtent);
    return new Promise<void>((resolve) => {
      let frame = 0;
      let done = false;
      const startTime = performance.now();
      const finish = () => {
        if (done) return;
        done = true;
        cancelAnimationFrame(frame);
        if (this._animation === handle) this._animation = null;
        resolve();
      };
      const handle = { cancel: finish };
      this._animation = handle;
      const step = (now: number) => {
        if (done) return;
        const t = duration <= 0 ? 1 : Math.min(1, (now - startTime) / duration);
        this._setPixels(start + (target - start) * curve(t));
        if (t >= 1) {
          finish();
          return;
        }
        frame = requestAnimationFrame(step);
      };
      frame = requestAnimationFrame(step);
    });
  }

  /** A user scroll (wheel, drag) interrupts a programmatic animation, as in Flutter. */
  cancelAnimation(): void {
    this._animation?.cancel();
  }

  dispose(): void {
    this.cancelAnimation();
  }

  private _setPixels(value: number): void {
    const element = this.element;
    if (element == null) return;
    const clamped = Math.min(Math.max(value, this.minScrollExtent), this.maxScrollExtent);
    element.scrollTop = -clamped;
  }
}

/** `Curves.easeOutCubic`. */
function _easeOutCubic(t: number): number {
  const u = 1 - t;
  return 1 - u * u * u;
}

export interface _MessageStageProps {
  roomId: string | null;
  currentUser: CurrentUser;
  currentUserRoomDisplayName: string;
  currentUserRoomRole: string | null;
  ownerUserId: string | null;
  roomReady: boolean;
  loading: boolean;
  error: string | null;
  timestampNow: Date;
  messages: readonly Message[];
  newMessageCount: number;
  focusMessageId: string | null;
  onFocusMessageHandled?: ((messageId: string) => void) | null;
  mentionMembers: readonly RoomMember[];
  mentionMembersReady: boolean;
  fileTransfers: Map<string, FileTransferState>;
  fileDownloads: Map<string, FileTransferState>;
  live: LiveState | null;
  downloadActions: ChatFileDownloadActions;
  voicePlaybackActions: ChatVoicePlaybackActions;
  imagePreviewActions: ChatImagePreviewActions;
  messageActions: ChatMessageActions;
  onRetry: () => void;
  onViewedNewMessages: () => void;
  // Breathing room at the bottom of the list, between the last message and the
  // composer row that now sits below it.
  bottomInset: number;
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  senderProfileActionBuilder?: UserProfileActionBuilder | null;
  onMentionUser?: ((user: UserSummary) => void) | null;
}

export class _MessageStageState extends ViewState<_MessageStageProps> {
  // Last known scroll position per room, surviving widget teardown when the
  // pane swaps (live channel, settings, etc.). Static so it outlives the State.
  static readonly _scrollAnchors = new Map<string, _ScrollAnchor>();

  _showDetailedTimestamps = false;
  _showLatestButton = false;
  _showNewMessageJumpButton = false;
  _newMessageJumpDismissed = false;
  _retainedNewMessageClientId: string | null = null;
  _retainedNewMessageLabelCount = 0;
  _scrollController!: _MessageListScrollController;
  // `_messageListKey` / `_firstNewMessageKey` / `_messageRowKeys`: the DOM
  // elements of the list, the first-new divider and each mounted row.
  _messageListElement: HTMLDivElement | null = null;
  _firstNewMessageElement: HTMLDivElement | null = null;
  readonly _messageRowKeys = new Map<string, HTMLElement>();
  private readonly _messageRowRefCallbacks = new Map<string, (node: HTMLDivElement | null) => void>();
  _messageIndexByClientId: Map<string, number> = new Map();
  readonly _viewedUnreadMentionClientIds = new Set<string>();
  _highlightedMentionClientId: string | null = null;
  _highlightedMessageId: string | null = null;
  _handledFocusMessageId: string | null = null;
  _highlightedMentionTimer: Timer | null = null;
  _highlightedMessageTimer: Timer | null = null;
  _underflowBottomSpacer = 0;
  _underflowAlignmentScheduled = false;
  _latestButtonVisibilityScheduled = false;
  _newMessageJumpVisibilityScheduled = false;
  _messageListReady = false;
  _incomingUnreadWaitingForUserScroll = false;
  _restoringIncomingViewport = false;
  // Guards queued scroll requests so overlapping requests don't stack up; the newest
  // request wins by bumping this token.
  _scrollToBottomToken = 0;
  // PORT: DOM scroll events arrive after the jump that caused them, so the
  // incoming-viewport restore remembers the scrollTop it set and the scroll
  // handler recognises that event (Flutter's listener ran inside jumpTo).
  private _incomingViewportRestoreScrollTop: number | null = null;
  private _postFrameCallbacks: Array<() => void> = [];
  private _postFrameRequest: number | null = null;

  /**
   * The part of Dart's `initState` that the first build depends on. It runs
   * when the state is created, before the first render (`useViewState`
   * mounts — [initState] — only after the first commit).
   */
  _initFields(): void {
    this._captureNewMessageAnchorFromWidget();
    // Seed the controller with the remembered offset so the very first layout
    // lands in place — restoring via a post-frame jump would render one frame
    // pinned to the latest message first, which reads as a flash.
    const anchor = this._anchorForRoom(this.widget.roomId);
    const restoring = anchor != null && !anchor.atBottom;
    this._scrollController = new _MessageListScrollController({ initialScrollOffset: restoring ? anchor.offset : 0 });
    // If we're not restoring a browsed-history position, land at the newest
    // message when the room first opens.
    if (!restoring) {
      this._scrollToBottom({ animated: false });
    }
    this._scheduleLatestButtonVisibilitySync();
    this._prepareFocusedMessageHighlight(this.widget.focusMessageId);
    this._scheduleFocusedMessageJump(this.widget.focusMessageId);
  }

  /**
   * Port of `didUpdateWidget`. Called by [_MessageStage] during render, before
   * the new props are committed, because it measures the old layout (the
   * viewport anchor) the way Flutter's didUpdateWidget ran before the next
   * build. It only mutates fields and queues post-frame work.
   */
  didUpdateWidget(oldWidget: _MessageStageProps): void {
    const widget = this.widget;
    if (oldWidget.focusMessageId !== widget.focusMessageId && widget.focusMessageId == null) {
      this._handledFocusMessageId = null;
    }
    if (widget.focusMessageId != null && oldWidget.messages.length > 0 && widget.messages.length === 0) {
      this._highlightedMessageTimer?.cancel();
      this._highlightedMessageTimer = null;
      this._highlightedMessageId = null;
      this._handledFocusMessageId = null;
    }
    if (oldWidget.roomId !== widget.roomId) {
      this._showDetailedTimestamps = false;
      this._showLatestButton = false;
      this._showNewMessageJumpButton = false;
      this._newMessageJumpDismissed = false;
      this._viewedUnreadMentionClientIds.clear();
      this._highlightedMentionClientId = null;
      this._highlightedMessageId = null;
      this._handledFocusMessageId = null;
      this._highlightedMentionTimer?.cancel();
      this._highlightedMentionTimer = null;
      this._highlightedMessageTimer?.cancel();
      this._highlightedMessageTimer = null;
      this._incomingUnreadWaitingForUserScroll = false;
      this._restoringIncomingViewport = false;
      this._clearRetainedNewMessageAnchor();
      this._captureNewMessageAnchorFromWidget();
      // New row keys: drop the cached ref callbacks too, so the next commit
      // re-registers every row.
      this._messageRowKeys.clear();
      this._messageRowRefCallbacks.clear();
      this._underflowBottomSpacer = 0;
      this._messageListReady = false;
      // New room: restore its remembered position, or snap to latest.
      const anchor = this._anchorForRoom(widget.roomId);
      if (anchor != null && !anchor.atBottom) {
        this._restoreScroll(anchor.offset);
      } else {
        this._scrollToBottom({ animated: false });
      }
      this._prepareFocusedMessageHighlight(widget.focusMessageId);
      this._scheduleFocusedMessageJump(widget.focusMessageId);
      return;
    }
    if (oldWidget.focusMessageId !== widget.focusMessageId || oldWidget.messages.length !== widget.messages.length) {
      this._prepareFocusedMessageHighlight(widget.focusMessageId);
      this._scheduleFocusedMessageJump(widget.focusMessageId);
    }
    const addedMessages = this._addedMessagesSince(oldWidget);
    const preserveIncomingViewport = this._shouldPreserveViewportForIncoming(addedMessages);
    const viewportAnchor = preserveIncomingViewport ? this._captureViewportAnchorFrom(oldWidget.messages) : null;

    if (oldWidget.newMessageCount !== widget.newMessageCount) {
      this._showNewMessageJumpButton = false;
      if (widget.newMessageCount > 0) {
        this._newMessageJumpDismissed = false;
        const current = this._currentUnreadMentionClientIds;
        for (const id of [...this._viewedUnreadMentionClientIds]) {
          if (!current.has(id)) this._viewedUnreadMentionClientIds.delete(id);
        }
        this._captureNewMessageAnchorFromWidget();
      } else {
        this._incomingUnreadWaitingForUserScroll = false;
        this._viewedUnreadMentionClientIds.clear();
      }
    }
    if (
      oldWidget.messages.length !== widget.messages.length ||
      (oldWidget.messages.length > 0 &&
        widget.messages.length > 0 &&
        oldWidget.messages[oldWidget.messages.length - 1]!.clientMessageId !==
          widget.messages[widget.messages.length - 1]!.clientMessageId)
    ) {
      this._underflowBottomSpacer = 0;
      this._showNewMessageJumpButton = false;
      if (widget.newMessageCount > 0) {
        this._newMessageJumpDismissed = false;
        this._captureNewMessageAnchorFromWidget();
      }
    }
    if (this._shouldFollowToBottom(oldWidget, addedMessages)) {
      this._scrollToBottom({ animated: true });
    } else if (viewportAnchor != null) {
      this._incomingUnreadWaitingForUserScroll =
        this._scrollController.hasClients && this._scrollController.maxScrollExtent > 0;
      this._restoreViewportAnchorAfterIncoming(viewportAnchor);
    }
  }

  override dispose(): void {
    this._rememberScrollPosition();
    this._highlightedMentionTimer?.cancel();
    this._highlightedMessageTimer?.cancel();
    this._scrollController.dispose();
    if (this._postFrameRequest != null) cancelAnimationFrame(this._postFrameRequest);
    this._postFrameRequest = null;
    this._postFrameCallbacks = [];
  }

  // ---------------------------------------------------------------------------
  // `WidgetsBinding.instance.addPostFrameCallback`.
  //
  // PORT: callbacks queued while building run right after the next commit
  // ([_flushPostFrameCallbacks] from a layout effect, before paint — the DOM
  // has laid out synchronously by then); callbacks queued anywhere else, or
  // from inside a callback, run on the next animation frame.

  _addPostFrameCallback(callback: () => void): void {
    this._postFrameCallbacks.push(callback);
    if (this._postFrameRequest != null || typeof requestAnimationFrame === 'undefined') return;
    this._postFrameRequest = requestAnimationFrame(() => {
      this._postFrameRequest = null;
      this._flushPostFrameCallbacks();
    });
  }

  _flushPostFrameCallbacks(): void {
    if (this._postFrameCallbacks.length === 0) return;
    const callbacks = this._postFrameCallbacks;
    this._postFrameCallbacks = [];
    for (const callback of callbacks) callback();
  }

  // ---------------------------------------------------------------------------

  _anchorForRoom(roomId: string | null): _ScrollAnchor | null {
    if (roomId == null) return null;
    return _MessageStageState._scrollAnchors.get(roomId) ?? null;
  }

  // Records the live scroll offset (and whether it's pinned to the latest
  // message) so a later remount can restore it. The list is reversed, so the
  // newest message sits at the minimum scroll extent — "at bottom" means the
  // offset is within the follow threshold of it. Called on every scroll and on
  // dispose.
  _rememberScrollPosition(): void {
    const roomId = this.widget.roomId;
    if (roomId == null || !this._scrollController.hasClients) return;
    _MessageStageState._scrollAnchors.set(
      roomId,
      new _ScrollAnchor({
        offset: this._scrollController.pixels,
        atBottom: this._distanceFromBottom() <= _autoScrollFollowThreshold,
      }),
    );
  }

  // Restores a remembered offset once the list has laid out, clamped to the
  // current scroll extent in case the content shrank while we were away.
  _restoreScroll(offset: number): void {
    const token = ++this._scrollToBottomToken;
    this._addPostFrameCallback(() => {
      if (!this.mounted || token !== this._scrollToBottomToken || !this._scrollController.hasClients) {
        return;
      }
      this._scrollController.jumpTo(Math.min(Math.max(offset, 0), this._scrollController.maxScrollExtent));
      this._rememberScrollPosition();
      this._syncLatestButtonVisibility();
    });
  }

  _addedMessagesSince(oldWidget: _MessageStageProps): Message[] {
    const messages = this.widget.messages;
    if (messages.length === 0) return [];
    const grew = messages.length > oldWidget.messages.length;
    const lastChanged =
      oldWidget.messages.length > 0 &&
      messages.length > 0 &&
      oldWidget.messages[oldWidget.messages.length - 1]!.clientMessageId !== messages[messages.length - 1]!.clientMessageId;
    if (!grew && !lastChanged) return [];
    const oldClientMessageIds = new Set(oldWidget.messages.map((message) => message.clientMessageId));
    return messages.filter((message) => !oldClientMessageIds.has(message.clientMessageId));
  }

  _shouldPreserveViewportForIncoming(addedMessages: readonly Message[]): boolean {
    if (this.widget.newMessageCount <= 0 || addedMessages.length === 0) return false;
    return addedMessages.some((message) => message.sender.id !== this.widget.currentUser.id);
  }

  // Decides whether an update introduced a new message worth following. We
  // always chase our own outgoing messages; messages from others remain unread
  // until the divider actually enters the viewport.
  _shouldFollowToBottom(oldWidget: _MessageStageProps, addedMessages: readonly Message[]): boolean {
    const messages = this.widget.messages;
    if (messages.length === 0) return false;
    if (addedMessages.length > 0) {
      return addedMessages[addedMessages.length - 1]!.sender.id === this.widget.currentUser.id;
    }
    const last = messages[messages.length - 1]!;
    const lastChanged =
      oldWidget.messages.length > 0 && last.clientMessageId !== oldWidget.messages[oldWidget.messages.length - 1]!.clientMessageId;
    if (!lastChanged) return false;
    if (last.sender.id === this.widget.currentUser.id) return true;
    return false;
  }

  _isNearBottom(): boolean {
    if (!this._scrollController.hasClients) return true;
    return this._distanceFromBottom() <= _autoScrollFollowThreshold;
  }

  _distanceFromBottom(): number {
    return Math.max(0, this._scrollController.pixels - this._scrollController.minScrollExtent);
  }

  _userInLive = (userId: string): boolean => {
    return live_display.liveParticipantByUserId(this.widget.live, userId) != null;
  };

  get _currentUserMentionIdentity(): UserSummary {
    return this.widget.currentUser.toSummary().copyWith({
      roomDisplayName: this.widget.currentUserRoomDisplayName,
      roomRole: this.widget.currentUserRoomRole,
    });
  }

  /** DOM `scroll` event of the list (the ScrollController listener). */
  _handleScrollEvent = (): void => {
    const element = this._scrollController.element;
    const expected = this._incomingViewportRestoreScrollTop;
    this._incomingViewportRestoreScrollTop = null;
    this._restoringIncomingViewport = element != null && expected != null && Math.abs(element.scrollTop - expected) < 1;
    try {
      this._handleScrollChanged();
    } finally {
      this._restoringIncomingViewport = false;
    }
  };

  _handleScrollChanged(): void {
    if (this._incomingUnreadWaitingForUserScroll && !this._restoringIncomingViewport) {
      this._incomingUnreadWaitingForUserScroll = false;
    }
    this._rememberScrollPosition();
    this._syncLatestButtonVisibility();
    this._scheduleNewMessageJumpVisibilitySync();
  }

  _syncLatestButtonVisibility(): void {
    if (!this._scrollController.hasClients) return;
    const next = !this._isNearBottom();
    if (next === this._showLatestButton) return;
    this.setState(() => {
      this._showLatestButton = next;
    });
  }

  _scheduleLatestButtonVisibilitySync(): void {
    if (this._latestButtonVisibilityScheduled) return;
    this._latestButtonVisibilityScheduled = true;
    this._addPostFrameCallback(() => {
      this._latestButtonVisibilityScheduled = false;
      if (!this.mounted) return;
      this._rememberScrollPosition();
      this._syncLatestButtonVisibility();
    });
  }

  _captureNewMessageAnchorFromWidget(): void {
    const count = this._effectiveWidgetNewMessageCount;
    if (count <= 0) return;
    const messages = this.widget.messages;
    const index = messages.length - count;
    if (index < 0 || index >= messages.length) return;
    this._retainedNewMessageClientId = messages[index]!.clientMessageId;
    this._retainedNewMessageLabelCount = this._newMessageLabelCountFromWidget;
  }

  _clearRetainedNewMessageAnchor(): void {
    this._retainedNewMessageClientId = null;
    this._retainedNewMessageLabelCount = 0;
  }

  // PORT: Flutter's reversed list kept a short history pinned to the bottom,
  // so this measured the oldest row after layout and grew a bottom spacer
  // (`_underflowBottomSpacer`) until that row sat at the top padding. The DOM
  // list keeps its rows in a `min-height: 100%` column, so a short history
  // already starts at the top padding and the spacer stays 0; what remains is
  // revealing the list once it has laid out (`_messageListReady`).
  _scheduleUnderflowAlignment(): void {
    if (this._underflowAlignmentScheduled) return;
    this._underflowAlignmentScheduled = true;
    this._addPostFrameCallback(() => {
      this._underflowAlignmentScheduled = false;
      if (!this.mounted) return;
      if (this._messageListElement == null) return;
      if (this._underflowBottomSpacer === 0 && this._messageListReady) return;
      this.setState(() => {
        this._underflowBottomSpacer = 0;
        this._messageListReady = true;
      });
    });
  }

  _scheduleNewMessageJumpVisibilitySync(): void {
    if (this._newMessageJumpVisibilityScheduled) return;
    this._newMessageJumpVisibilityScheduled = true;
    this._addPostFrameCallback(() => {
      this._newMessageJumpVisibilityScheduled = false;
      if (!this.mounted) return;
      if (this._firstNewMessageIsVisible() && !this._newMessageJumpDismissed) {
        if (!this._incomingUnreadWaitingForUserScroll) {
          this._markNewMessagesViewed();
        }
        return;
      }
      const next = this._shouldShowNewMessageJumpButton();
      if (next === this._showNewMessageJumpButton) return;
      this.setState(() => {
        this._showNewMessageJumpButton = next;
      });
    });
  }

  _shouldShowNewMessageJumpButton(): boolean {
    if (this._firstNewMessageIndex == null || this._newMessageJumpDismissed) {
      return false;
    }
    if (!this._messageListReady || !this._scrollController.hasClients) return false;
    const listBox = this._messageListElement;
    if (listBox == null || !listBox.isConnected) return false;
    const anchorBox = this._firstNewMessageElement;
    if (anchorBox == null || !anchorBox.isConnected) {
      // If the first-new anchor is not laid out, it is above the visible
      // window and needs the one-shot jump affordance.
      return true;
    }
    const listTop = listBox.getBoundingClientRect().top;
    const anchorTop = anchorBox.getBoundingClientRect().top;
    return anchorTop < listTop + 1;
  }

  _firstNewMessageAnchorIsVisible(): boolean {
    if (this._firstNewMessageIndex == null) return false;
    return this._renderBoxIntersectsMessageList(this._firstNewMessageElement);
  }

  _firstNewMessageIsVisible(): boolean {
    if (this._firstNewMessageAnchorIsVisible()) return true;
    const firstIndex = this._firstNewMessageIndex;
    const messages = this.widget.messages;
    if (firstIndex == null || firstIndex < 0 || firstIndex >= messages.length) {
      return false;
    }
    const message = messages[firstIndex]!;
    return this._renderBoxIntersectsMessageList(this._messageRowKeys.get(message.clientMessageId) ?? null);
  }

  _renderBoxIntersectsMessageList(box: HTMLElement | null): boolean {
    const listBox = this._messageListElement;
    if (listBox == null || box == null || !listBox.isConnected || !box.isConnected) {
      return false;
    }
    const listRect = listBox.getBoundingClientRect();
    const boxRect = box.getBoundingClientRect();
    return boxRect.bottom >= listRect.top && boxRect.top <= listRect.bottom;
  }

  // The list is reversed, so the latest message lives at the minimum scroll
  // extent. New rooms can render directly at the final bottom position instead
  // of painting history first and jumping on the next frame.
  _scrollToBottom(opts: { animated: boolean }): void {
    const token = ++this._scrollToBottomToken;
    this._addPostFrameCallback(() => {
      if (!this.mounted || token !== this._scrollToBottomToken || !this._scrollController.hasClients) {
        return;
      }
      const target = this._scrollController.minScrollExtent;
      if (Math.abs(this._scrollController.pixels - target) < 1) {
        this._syncLatestButtonVisibility();
        return;
      }
      if (opts.animated) {
        void this._scrollController.animateTo(target, { duration: 220 }).finally(() => {
          if (this.mounted) this._syncLatestButtonVisibility();
        });
        return;
      }
      this._scrollController.jumpTo(target);
      this._syncLatestButtonVisibility();
    });
  }

  _messageRowKey(clientMessageId: string): (node: HTMLDivElement | null) => void {
    let callback = this._messageRowRefCallbacks.get(clientMessageId);
    if (callback == null) {
      callback = (node) => {
        if (node == null) {
          this._messageRowKeys.delete(clientMessageId);
        } else {
          this._messageRowKeys.set(clientMessageId, node);
        }
      };
      this._messageRowRefCallbacks.set(clientMessageId, callback);
    }
    return callback;
  }

  _syncMessageRowKeys(): void {
    const messages = this.widget.messages;
    const indexByClientId = new Map<string, number>();
    for (let index = 0; index < messages.length; index++) {
      indexByClientId.set(messages[index]!.clientMessageId, index);
    }
    this._messageIndexByClientId = indexByClientId;
    for (const [id, node] of [...this._messageRowKeys]) {
      if (!indexByClientId.has(id) || !node.isConnected) this._messageRowKeys.delete(id);
    }
    for (const id of [...this._messageRowRefCallbacks.keys()]) {
      if (!indexByClientId.has(id)) this._messageRowRefCallbacks.delete(id);
    }
  }

  // PORT: `_findMessageItemIndex` mapped GlobalKey rows to their new index so
  // inserting a message moved rows instead of re-parenting them. React's keyed
  // reconciliation (rows keyed by clientMessageId) moves the DOM rows the same
  // way, so open hover cards inside a row survive an insertion.

  _captureViewportAnchorFrom(messages: readonly Message[]): _ViewportAnchor | null {
    const listBox = this._messageListElement;
    if (listBox == null || !listBox.isConnected) return null;
    const listRect = listBox.getBoundingClientRect();
    const listTop = listRect.top;
    const listBottom = listTop + listRect.height;
    for (let index = messages.length - 1; index >= 0; index--) {
      const message = messages[index]!;
      const box = this._messageRowKeys.get(message.clientMessageId);
      if (box == null || !box.isConnected) continue;
      const rect = box.getBoundingClientRect();
      const top = rect.top;
      const bottom = top + rect.height;
      if (bottom >= listTop && top <= listBottom) {
        return new _ViewportAnchor({ clientMessageId: message.clientMessageId, top });
      }
    }
    return null;
  }

  _restoreViewportAnchorAfterIncoming(anchor: _ViewportAnchor): void {
    const token = ++this._scrollToBottomToken;
    this._addPostFrameCallback(() => {
      if (!this.mounted || token !== this._scrollToBottomToken || !this._scrollController.hasClients) {
        return;
      }
      const position = this._scrollController;
      const anchorBox = this._messageRowKeys.get(anchor.clientMessageId) ?? null;
      const listBox = this._messageListElement;
      if (anchorBox != null && anchorBox.isConnected && listBox != null && listBox.isConnected) {
        // PORT: Flutter estimated the offset with `getOffsetToReveal` and let
        // [_correctViewportAnchorAfterIncoming] fix the residue a frame later.
        // The DOM has laid out synchronously, so the exact offset that puts
        // the anchor row back at its old top — what those passes converge to
        // — is applied in one step.
        const top = anchorBox.getBoundingClientRect().top;
        const target = Math.min(
          Math.max(position.pixels - (top - anchor.top), position.minScrollExtent),
          position.maxScrollExtent,
        );
        if (Math.abs(position.pixels - target) > 0.5) {
          this._jumpToDuringIncomingViewportRestore(target);
        }
        this._correctViewportAnchorAfterIncoming(anchor, token, { remainingPasses: 2 });
        return;
      }
      this._finishViewportAnchorRestore();
    });
  }

  _correctViewportAnchorAfterIncoming(anchor: _ViewportAnchor, token: number, opts: { remainingPasses: number }): void {
    this._addPostFrameCallback(() => {
      if (!this.mounted || token !== this._scrollToBottomToken || !this._scrollController.hasClients) {
        return;
      }
      const anchorBox = this._messageRowKeys.get(anchor.clientMessageId) ?? null;
      if (anchorBox == null || !anchorBox.isConnected) {
        this._finishViewportAnchorRestore();
        return;
      }
      const top = anchorBox.getBoundingClientRect().top;
      const delta = top - anchor.top;
      if (Math.abs(delta) > 0.5) {
        const position = this._scrollController;
        const target = Math.min(Math.max(position.pixels - delta, position.minScrollExtent), position.maxScrollExtent);
        if (Math.abs(position.pixels - target) > 0.5) {
          this._jumpToDuringIncomingViewportRestore(target);
        }
        if (opts.remainingPasses > 0) {
          this._correctViewportAnchorAfterIncoming(anchor, token, { remainingPasses: opts.remainingPasses - 1 });
          return;
        }
      }
      this._finishViewportAnchorRestore();
    });
  }

  _finishViewportAnchorRestore(): void {
    this._rememberScrollPosition();
    this._syncLatestButtonVisibility();
    this._scheduleNewMessageJumpVisibilitySync();
  }

  _jumpToDuringIncomingViewportRestore(target: number): void {
    this._scrollController.jumpTo(target);
    this._incomingViewportRestoreScrollTop = this._scrollController.element?.scrollTop ?? null;
  }

  get _effectiveWidgetNewMessageCount(): number {
    const widget = this.widget;
    if (widget.messages.length === 0 || widget.newMessageCount <= 0) return 0;
    return Math.min(Math.max(widget.newMessageCount, 0), widget.messages.length);
  }

  get _newMessageLabelCountFromWidget(): number {
    return this.widget.newMessageCount < 0 ? 0 : this.widget.newMessageCount;
  }

  get _activeNewMessageCount(): number {
    const widgetCount = this._effectiveWidgetNewMessageCount;
    if (widgetCount > 0) return widgetCount;
    if (this._retainedNewMessageIndex == null) return 0;
    return Math.min(Math.max(this._retainedNewMessageLabelCount, 0), this.widget.messages.length);
  }

  get _newMessageLabelCount(): number {
    const widgetCount = this._effectiveWidgetNewMessageCount;
    const raw = widgetCount > 0 ? this._newMessageLabelCountFromWidget : this._retainedNewMessageLabelCount;
    return Math.max(0, raw - this._viewedUnreadMentionClientIds.size);
  }

  get _retainedNewMessageIndex(): number | null {
    const retainedId = this._retainedNewMessageClientId;
    if (retainedId == null) return null;
    const index = this.widget.messages.findIndex((message) => message.clientMessageId === retainedId);
    return index === -1 ? null : index;
  }

  get _firstNewMessageIndex(): number | null {
    const widgetCount = this._effectiveWidgetNewMessageCount;
    if (widgetCount > 0) return this.widget.messages.length - widgetCount;
    return this._retainedNewMessageIndex;
  }

  get _unreadMentionIndexes(): number[] {
    const firstIndex = this._firstNewMessageIndex;
    if (firstIndex == null) return [];
    const indexes: number[] = [];
    const currentUser = this._currentUserMentionIdentity;
    const messages = this.widget.messages;
    for (let index = firstIndex; index < messages.length; index++) {
      const message = messages[index]!;
      if (this._viewedUnreadMentionClientIds.has(message.clientMessageId)) {
        continue;
      }
      if (message.pending || message.isRemoved) continue;
      if (message.sender.id === this.widget.currentUser.id) continue;
      if (
        message_mentions.messageMentionsUser({
          text: message.body,
          mentions: message.mentions,
          user: currentUser,
          ownerUserId: this.widget.ownerUserId,
        })
      ) {
        indexes.push(index);
      }
    }
    return indexes;
  }

  get _currentUnreadMentionClientIds(): Set<string> {
    return new Set(this._unreadMentionIndexes.map((index) => this.widget.messages[index]!.clientMessageId));
  }

  get _nextUnreadMentionIndex(): number | null {
    const indexes = this._unreadMentionIndexes;
    if (indexes.length === 0) return null;
    return indexes[indexes.length - 1]!;
  }

  _markNewMessagesViewed(): void {
    if (this._newMessageJumpDismissed) return;
    this.setState(() => {
      this._newMessageJumpDismissed = true;
      this._showNewMessageJumpButton = false;
      this._incomingUnreadWaitingForUserScroll = false;
      this._viewedUnreadMentionClientIds.clear();
    });
    this.widget.onViewedNewMessages();
  }

  _markUnreadMentionViewed(message: Message): void {
    this._viewedUnreadMentionClientIds.add(message.clientMessageId);
  }

  _scrollToNextUnreadMention = (): void => {
    const index = this._nextUnreadMentionIndex;
    const messages = this.widget.messages;
    if (index == null || index < 0 || index >= messages.length) {
      this._scrollToFirstNewMessage();
      return;
    }
    const message = messages[index]!;
    this.setState(() => {
      this._highlightedMentionClientId = message.clientMessageId;
      this._markUnreadMentionViewed(message);
      this._showNewMessageJumpButton = this._shouldShowNewMessageJumpButton();
    });
    this._highlightedMentionTimer?.cancel();
    this._highlightedMentionTimer = new Timer(2600, () => {
      if (!this.mounted || this._highlightedMentionClientId !== message.clientMessageId) {
        return;
      }
      this.setState(() => {
        this._highlightedMentionClientId = null;
      });
    });
    this._ensureMessageVisible(message);
  };

  _scrollToFirstNewMessage = (): void => {
    this._markNewMessagesViewed();
    if (this._ensureFirstNewMessageVisible()) {
      return;
    }
    if (!this._scrollController.hasClients) return;
    const count = this._activeNewMessageCount;
    if (count <= 0) return;
    const position = this._scrollController;
    const messages = this.widget.messages;
    const averageExtent = messages.length <= 1 ? position.maxScrollExtent : position.maxScrollExtent / (messages.length - 1);
    const firstIndex = this._firstNewMessageIndex;
    const distanceFromBottom = firstIndex == null ? count - 1 : messages.length - 1 - firstIndex;
    const target = Math.min(
      Math.max(position.minScrollExtent + averageExtent * distanceFromBottom, position.minScrollExtent),
      position.maxScrollExtent,
    );
    void this._scrollController.animateTo(target, { duration: 240 }).finally(() => {
      if (this.mounted) this._ensureFirstNewMessageVisible();
    });
  };

  _ensureFirstNewMessageVisible(): boolean {
    const element = this._firstNewMessageElement;
    if (element == null || !element.isConnected) return false;
    void this._ensureVisible(element, { alignment: 0.12 });
    return true;
  }

  _ensureMessageVisible(message: Message): void {
    const element = this._messageRowKeys.get(message.clientMessageId);
    if (element == null || !element.isConnected) return;
    void this._ensureVisible(element, { alignment: 0.18 });
  }

  /**
   * Port of `Scrollable.ensureVisible(context, duration: 180ms, curve:
   * easeOutCubic, alignment:)` for the reversed list: as in Flutter's
   * `getOffsetToReveal` for `AxisDirection.up`, [alignment] is measured from
   * the viewport's bottom (leading) edge — the target's bottom lands
   * `alignment * (viewport - target)` above the bottom.
   */
  _ensureVisible(target: HTMLElement, opts: { alignment: number }): Promise<void> {
    const listBox = this._messageListElement;
    if (listBox == null || !this._scrollController.hasClients) return Promise.resolve();
    const listRect = listBox.getBoundingClientRect();
    const rect = target.getBoundingClientRect();
    const viewport = this._scrollController.viewportDimension;
    const currentFromBottom = listRect.top + viewport - rect.bottom;
    const desiredFromBottom = (viewport - rect.height) * opts.alignment;
    const position = this._scrollController;
    const offset = Math.min(
      Math.max(position.pixels + currentFromBottom - desiredFromBottom, position.minScrollExtent),
      position.maxScrollExtent,
    );
    if (Math.abs(offset - position.pixels) < 0.5) return Promise.resolve();
    return position.animateTo(offset, { duration: 180 });
  }

  _scheduleFocusedMessageJump(messageId: string | null): void {
    if (messageId == null || messageId.length === 0) return;
    if (this._handledFocusMessageId === messageId) return;
    this._addPostFrameCallback(() => {
      if (!this.mounted) return;
      const message = this._messageById(messageId);
      if (message == null) return;
      const focusedMessage = message;
      const focusedMessageId = focusedMessage.id;
      this._handledFocusMessageId = focusedMessageId;
      this.setState(() => {
        this._highlightedMessageId = focusedMessageId;
      });
      this._ensureMessageVisible(focusedMessage);
      this._highlightedMessageTimer?.cancel();
      this._highlightedMessageTimer = new Timer(2600, () => {
        if (!this.mounted || this._highlightedMessageId !== focusedMessageId) return;
        this.setState(() => {
          this._highlightedMessageId = null;
        });
      });
      this.widget.onFocusMessageHandled?.(focusedMessageId);
    });
  }

  _prepareFocusedMessageHighlight(messageId: string | null): void {
    if (messageId == null || messageId.length === 0) return;
    if (this._handledFocusMessageId === messageId && this._highlightedMessageId == null) {
      return;
    }
    const message = this._messageById(messageId);
    if (message == null) return;
    this._highlightedMessageId = message.id;
  }

  _messageById(messageId: string): Message | null {
    for (const item of this.widget.messages) {
      if (item.id === messageId) return item;
    }
    return null;
  }

  _messageTargetsCurrentUser(message: Message): boolean {
    if (message.pending || message.isRemoved) return false;
    if (message.sender.id === this.widget.currentUser.id) return false;
    return message_mentions.messageMentionsUser({
      text: message.body,
      mentions: message.mentions,
      user: this._currentUserMentionIdentity,
      ownerUserId: this.widget.ownerUserId,
    });
  }

  _messageIsHighlighted(message: Message): boolean {
    if (message.clientMessageId === this._highlightedMentionClientId) return true;
    if (message.id === this._highlightedMessageId) return true;
    const focusMessageId = this.widget.focusMessageId;
    return (
      focusMessageId != null &&
      focusMessageId.length > 0 &&
      this._handledFocusMessageId !== focusMessageId &&
      message.id === focusMessageId
    );
  }

  get _waitingForMentionRenderContext(): boolean {
    if (this.widget.mentionMembersReady) return false;
    return this.widget.messages.some((message) => this._messageNeedsMentionRenderContext(message));
  }

  _messageNeedsMentionRenderContext(message: Message): boolean {
    if (message.pending || message.isRemoved || message.type !== 'text') {
      return false;
    }
    if (message.mentions.length > 0) return true;
    return message_mentions.messageMentionRanges(message.body).length > 0;
  }

  _toggleDetailedTimestamps = (): void => {
    this.setState(() => {
      this._showDetailedTimestamps = !this._showDetailedTimestamps;
      this._underflowBottomSpacer = 0;
    });
  };

  private _attachMessageList = (node: HTMLDivElement | null): void => {
    this._messageListElement = node;
    this._scrollController.attach(node);
  };

  private _attachFirstNewMessage = (node: HTMLDivElement | null): void => {
    this._firstNewMessageElement = node;
  };

  // A wheel tick, a touch hold or a scrollbar drag interrupts a programmatic
  // scroll animation (Flutter `pointerScroll` → goIdle / drag `hold`). Mouse
  // presses on the content do not: desktop Flutter does not drag with a mouse.
  private _handleWheel = (): void => {
    this._scrollController.cancelAnimation();
  };

  private _handleListPointerDown = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (event.pointerType !== 'mouse' || event.target === event.currentTarget) {
      this._scrollController.cancelAnimation();
    }
  };

  build(): ReactNode {
    this._syncMessageRowKeys();
    const widget = this.widget;
    if (widget.error != null && !widget.roomReady) {
      return (
        <FloatingNoticeEmitter
          notices={[new FloatingNotice({ message: widget.error, tone: FloatingNoticeTone.error, duration: null })]}
        >
          <_CenteredState
            icon={Icons.error_outline}
            title="无法加载聊天"
            detail="请稍后重试"
            action={
              <Button icon={<Icon icon={Icons.refresh} />} onPressed={widget.onRetry}>
                重试
              </Button>
            }
          />
        </FloatingNoticeEmitter>
      );
    }

    if ((widget.loading && widget.messages.length === 0) || this._waitingForMentionRenderContext) {
      return (
        <Center style={_fillStyle}>
          <SizedBox width={24} height={24}>
            <CircularProgressIndicator size={24} strokeWidth={2.2} color={UiColors.accent} />
          </SizedBox>
        </Center>
      );
    }

    if (widget.messages.length === 0) {
      return <_CenteredState icon={Icons.forum_outlined} title="还没有消息" detail="在下方开始对话吧" />;
    }

    this._scheduleUnderflowAlignment();
    this._scheduleLatestButtonVisibilitySync();

    const now = widget.timestampNow;
    const firstNewMessageIndex = this._firstNewMessageIndex;
    const showNewMessageJump = firstNewMessageIndex != null && !this._newMessageJumpDismissed && this._showNewMessageJumpButton;
    const nextUnreadMentionIndex = this._nextUnreadMentionIndex;
    const showMentionJump = showNewMessageJump && nextUnreadMentionIndex != null;
    const newMessageJumpTooltip = showMentionJump
      ? `${widget.currentUserRoomDisplayName}@我`
      : `查看 ${this._newMessageLabelCount} 条未读消息`;
    this._scheduleNewMessageJumpVisibilitySync();
    const currentUserMentionIdentity = this._currentUserMentionIdentity;

    // Rows in chronological order: the list box is `column-reverse`, but its
    // single content column reads top to bottom (see _MessageListScrollController).
    const rows = widget.messages.map((message, chronologicalIndex) => {
      const systemEvent = message_display.systemMessageEvent(message);
      const previous = chronologicalIndex === 0 ? null : widget.messages[chronologicalIndex - 1]!;
      const showTimestamp = message_display.shouldShowChatTimestamp({
        current: message.createdAt,
        previous: previous?.createdAt ?? null,
        now,
      });
      const timestamp = this._showDetailedTimestamps
        ? message_display.formatDetailedChatTimestamp(message.createdAt)
        : message_display.formatChatTimestamp(message.createdAt, { now });
      const showNewMessageDivider = chronologicalIndex === firstNewMessageIndex;
      let content: ReactNode;
      if (systemEvent != null) {
        content = (
          <ChatSystemMessageContent
            message={message}
            event={systemEvent}
            highlighted={this._messageIsHighlighted(message)}
            currentUser={widget.currentUser}
            ownerUserId={widget.ownerUserId}
            live={widget.live}
            messageActions={widget.messageActions}
            onResolveSenderProfile={widget.onResolveSenderProfile}
            onResolveRoomProfile={widget.onResolveRoomProfile}
            onEnterProfileRoom={widget.onEnterProfileRoom}
            profileActionBuilder={widget.senderProfileActionBuilder}
          />
        );
      } else if (message.isRemoved) {
        content = (
          <ChatRemovedMessageContent
            message={message}
            highlighted={this._messageIsHighlighted(message)}
            currentUser={widget.currentUser}
            ownerUserId={widget.ownerUserId}
            live={widget.live}
            messageActions={widget.messageActions}
            onResolveSenderProfile={widget.onResolveSenderProfile}
            onResolveRoomProfile={widget.onResolveRoomProfile}
            onEnterProfileRoom={widget.onEnterProfileRoom}
            profileActionBuilder={widget.senderProfileActionBuilder}
          />
        );
      } else {
        content = (
          <_MessageRow
            message={message}
            outgoing={message.sender.id === widget.currentUser.id}
            timestampNow={now}
            showDetailedTimestamps={this._showDetailedTimestamps}
            currentUser={widget.currentUser}
            currentUserMentionIdentity={currentUserMentionIdentity}
            ownerUserId={widget.ownerUserId}
            mentionMembers={widget.mentionMembers}
            transfer={widget.fileTransfers.get(message.clientMessageId) ?? null}
            fileDownloads={widget.fileDownloads}
            downloadActions={widget.downloadActions}
            voicePlaybackActions={widget.voicePlaybackActions}
            imagePreviewActions={widget.imagePreviewActions}
            messageActions={widget.messageActions}
            mentionHighlighted={this._messageIsHighlighted(message)}
            mentionTargetsCurrentUser={this._messageTargetsCurrentUser(message)}
            inLive={this._userInLive(message.sender.id)}
            onResolveSenderProfile={widget.onResolveSenderProfile}
            onResolveRoomProfile={widget.onResolveRoomProfile}
            onEnterProfileRoom={widget.onEnterProfileRoom}
            profileActionBuilder={widget.senderProfileActionBuilder}
            onMentionUser={widget.onMentionUser}
            isUserInLive={this._userInLive}
          />
        );
      }
      return (
        <div
          key={message.clientMessageId}
          ref={this._messageRowKey(message.clientMessageId)}
          style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', flexShrink: 0 }}
        >
          {showNewMessageDivider ? (
            <>
              <div ref={this._attachFirstNewMessage}>
                <_NewMessagesDivider />
              </div>
              <SizedBox height={10} />
            </>
          ) : null}
          {showTimestamp ? (
            <>
              <_MessageTimeDivider label={timestamp} onTap={this._toggleDetailedTimestamps} />
              <SizedBox height={10} />
            </>
          ) : null}
          {content}
        </div>
      );
    });

    const list = (
      <div
        ref={this._attachMessageList}
        data-testid="chat-message-list"
        onScroll={this._handleScrollEvent}
        onWheel={this._handleWheel}
        onPointerDown={this._handleListPointerDown}
        style={_messageListStyle}
      >
        <div
          style={{
            ..._messageListContentStyle,
            ...paddingStyle(EdgeInsets.fromLTRB(_chatHorizontalPadding, _chatMessageListTopPadding, _chatHorizontalPadding, widget.bottomInset)),
          }}
        >
          {rows}
        </div>
      </div>
    );

    return (
      <div style={_stageStyle}>
        <div style={{ position: 'absolute', inset: 0, opacity: this._messageListReady ? 1 : 0 }}>{list}</div>
        <div style={{ position: 'absolute', right: _chatFloatingEdgeInset, bottom: 12 }}>
          <_AnimatedSwitcherFade duration={120}>
            {this._showLatestButton ? (
              <_ChatJumpButton
                testId="chat-jump-to-latest"
                tooltip="跳到最新消息"
                icon={Icons.keyboard_arrow_down_rounded}
                onPressed={() => this._scrollToBottom({ animated: true })}
              />
            ) : null}
          </_AnimatedSwitcherFade>
        </div>
        <div style={{ position: 'absolute', top: 12, right: _chatFloatingEdgeInset }}>
          <_AnimatedSwitcherFade duration={120}>
            {showNewMessageJump ? (
              <_ChatJumpButton
                testId="chat-jump-to-first-new"
                tooltip={newMessageJumpTooltip}
                icon={Icons.keyboard_arrow_up_rounded}
                onPressed={showMentionJump ? this._scrollToNextUnreadMention : this._scrollToFirstNewMessage}
              />
            ) : null}
          </_AnimatedSwitcherFade>
        </div>
      </div>
    );
  }
}

export function _MessageStage(props: _MessageStageProps) {
  const state = useViewState(() => {
    const created = new _MessageStageState();
    created.props = props;
    created._initFields();
    return created;
  }, props);
  // didUpdateWidget before build (see _MessageStageState.didUpdateWidget).
  const renderedProps = useRef(props);
  if (renderedProps.current !== props) {
    const oldWidget = renderedProps.current;
    renderedProps.current = props;
    state.didUpdateWidget(oldWidget);
  }
  const content = state.build();
  // Post-frame callbacks queued by this build (and initState) run once the
  // DOM reflects it, before the browser paints.
  useLayoutEffect(() => {
    state._flushPostFrameCallbacks();
  });
  return <>{content}</>;
}

const _fillStyle: CSSProperties = { flex: '1 1 0', minWidth: 0, minHeight: 0, width: '100%', height: '100%' };

const _stageStyle: CSSProperties = {
  position: 'relative',
  flex: '1 1 0',
  minWidth: 0,
  minHeight: 0,
  width: '100%',
  height: '100%',
  overflow: 'hidden',
};

const _messageListStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
  overflowX: 'hidden',
  overflowY: 'auto',
  display: 'flex',
  flexDirection: 'column-reverse',
  // Flutter reversed-viewport semantics only (see _MessageListScrollController).
  overflowAnchor: 'none',
  // ClampingScrollPhysics: no overscroll bounce or scroll chaining.
  overscrollBehavior: 'none',
};

const _messageListContentStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'stretch',
  // ListView.separated: SizedBox(height: 12) between rows.
  gap: 12,
  boxSizing: 'border-box',
  minHeight: '100%',
  flexShrink: 0,
};

/** The round jump buttons over the list (`chat-jump-to-latest` / `-first-new`). */
function _ChatJumpButton({
  testId,
  tooltip,
  icon,
  onPressed,
}: {
  testId: string;
  tooltip: string;
  icon: IconData;
  onPressed: () => void;
}) {
  return (
    <PressableSurface
      data-testid={testId}
      width={34}
      height={34}
      backgroundColor={UiColors.selected}
      selectedBackgroundColor={UiColors.selected}
      pressedBackgroundColor={argb(0xff14211b)}
      borderColor={UiColors.selectedBorder}
      selectedBorderColor={UiColors.selectedBorder}
      tooltip={tooltip}
      onPressed={onPressed}
      contentStyle={{ alignItems: 'center' }}
    >
      <Icon icon={icon} color={UiColors.accent} size={18} />
    </PressableSurface>
  );
}

/**
 * Port of `AnimatedSwitcher(duration:, switchInCurve: easeOutCubic,
 * switchOutCurve: easeInCubic)` between a child and an empty box: the child
 * fades in, and fades out before it is removed. The initial child is not
 * animated, like Flutter.
 */
function _AnimatedSwitcherFade({ children, duration }: { children: ReactNode | null; duration: number }) {
  const visible = children != null;
  const lastChild = useRef<ReactNode>(children);
  if (visible) lastChild.current = children;
  const [present, setPresent] = useState(visible);
  const [shown, setShown] = useState(visible);

  useLayoutEffect(() => {
    if (visible) {
      setPresent(true);
      // Start from transparent, then transition in on the next frame.
      const frame = requestAnimationFrame(() => setShown(true));
      return () => cancelAnimationFrame(frame);
    }
    setShown(false);
    const timer = setTimeout(() => setPresent(false), duration);
    return () => clearTimeout(timer);
  }, [visible, duration]);

  if (!visible && !present) return null;
  return (
    <div
      style={{
        opacity: visible && shown ? 1 : 0,
        transition: `opacity ${duration}ms ${visible ? UiCurves.easeOutCubic : UiCurves.easeInCubic}`,
        pointerEvents: visible ? undefined : 'none',
      }}
    >
      {visible ? children : lastChild.current}
    </div>
  );
}

export function _NewMessagesDivider() {
  const lineColor = withAlpha(UiColors.accent, 0.58);
  const line = <div style={{ flex: '1 1 0', minWidth: 0, height: 1, backgroundColor: lineColor }} />;
  return (
    <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center' }}>
      {line}
      <div style={{ padding: '0 10px', flexShrink: 0 }}>
        <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.accent, fontSize: 12, fontWeight: 700 }}>
          未读消息
        </Text>
      </div>
      {line}
    </div>
  );
}

export function _MessageTimeDivider({ label, onTap }: { label: string; onTap: () => void }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'row', justifyContent: 'center', minWidth: 0 }}>
      <div
        onClick={(event) => {
          if (event.button !== 0) return;
          onTap();
        }}
        style={{
          boxSizing: 'border-box',
          maxWidth: '100%',
          minWidth: 0,
          padding: '4px 9px',
          backgroundColor: UiColors.surfacePressed,
          borderRadius: 999,
          // DecoratedBox border: painted inside, no layout space.
          boxShadow: `inset 0 0 0 1px ${UiColors.border}`,
          cursor: SystemMouseCursors.click,
        }}
      >
        <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textMuted, fontSize: 11 }}>
          {label}
        </Text>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Message rows and bubbles.

export interface _MessageRowProps {
  message: Message;
  outgoing: boolean;
  timestampNow: Date;
  showDetailedTimestamps: boolean;
  currentUser: CurrentUser;
  currentUserMentionIdentity: UserSummary;
  ownerUserId: string | null;
  mentionMembers: readonly RoomMember[];
  transfer: FileTransferState | null;
  fileDownloads: Map<string, FileTransferState>;
  downloadActions: ChatFileDownloadActions;
  voicePlaybackActions: ChatVoicePlaybackActions;
  imagePreviewActions: ChatImagePreviewActions;
  messageActions: ChatMessageActions;
  mentionHighlighted: boolean;
  mentionTargetsCurrentUser: boolean;
  inLive: boolean;
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  onMentionUser?: ((user: UserSummary) => void) | null;
  isUserInLive?: ((userId: string) => boolean) | null;
}

export function _MessageRow(props: _MessageRowProps) {
  const { message, outgoing, currentUser, ownerUserId } = props;
  const config = useAppConfig();
  const sender = _senderName(message.sender);
  const senderColor = chatRoomUsernameColor({ user: message.sender, currentUser, ownerUserId });
  const bubble = (
    <div
      style={{
        flex: '0 1 auto',
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: outgoing ? 'flex-end' : 'flex-start',
      }}
    >
      <div
        style={{
          boxSizing: 'border-box',
          maxWidth: _messageMaxWidth,
          minWidth: 0,
          padding: '0 2px',
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          width: 'fit-content',
        }}
      >
        <Text
          maxLines={1}
          textAlign={outgoing ? 'right' : 'left'}
          style={{ ...UiTypography.label, color: senderColor, fontSize: 11, fontWeight: 600, flex: '0 1 auto' }}
        >
          {sender}
        </Text>
        {message.sender.isBot ? (
          <>
            <SizedBox width={4} />
            <_GangBotBadge />
          </>
        ) : null}
      </div>
      <SizedBox height={4} />
      <_MessageBubble
        message={message}
        outgoing={outgoing}
        timestampNow={props.timestampNow}
        showDetailedTimestamps={props.showDetailedTimestamps}
        transfer={props.transfer}
        fileDownloads={props.fileDownloads}
        downloadActions={props.downloadActions}
        voicePlaybackActions={props.voicePlaybackActions}
        imagePreviewActions={props.imagePreviewActions}
        messageActions={props.messageActions}
        mentionHighlighted={props.mentionHighlighted}
        mentionTargetsCurrentUser={props.mentionTargetsCurrentUser}
        currentUser={currentUser}
        currentUserMentionIdentity={props.currentUserMentionIdentity}
        ownerUserId={ownerUserId}
        mentionMembers={props.mentionMembers}
        onResolveSenderProfile={props.onResolveSenderProfile}
        onResolveRoomProfile={props.onResolveRoomProfile}
        onEnterProfileRoom={props.onEnterProfileRoom}
        profileActionBuilder={props.profileActionBuilder}
        isUserInLive={props.isUserInLive}
      />
    </div>
  );

  const avatar = message.sender.isBot ? (
    <_GangBotAvatar size={32} />
  ) : (
    <Avatar
      label={room_display.userAvatarLabel(message.sender)}
      imageUrl={config.resolveAssetUrl(message.sender.avatarUrl)}
      defaultAvatarKey={message.sender.defaultAvatarKey}
      size={32}
      showBorder={false}
    />
  );

  const avatarWithContextMenu = (
    <_MessageAvatarMentionMenu user={message.sender} onMentionUser={message.sender.id === currentUser.id ? null : props.onMentionUser}>
      {avatar}
    </_MessageAvatarMentionMenu>
  );
  const avatarHoverCard = (
    <_AvatarHoverCard
      user={message.sender}
      currentUser={currentUser}
      onResolveProfile={props.onResolveSenderProfile}
      onResolveRoomProfile={props.onResolveRoomProfile}
      onEnterCommonRoom={props.onEnterProfileRoom}
      profileActionBuilder={props.profileActionBuilder}
      inLive={props.inLive}
    >
      {avatarWithContextMenu}
    </_AvatarHoverCard>
  );

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'row',
        justifyContent: outgoing ? 'flex-end' : 'flex-start',
        alignItems: 'flex-start',
        minWidth: 0,
      }}
    >
      {!outgoing ? (
        <>
          {avatarHoverCard}
          <SizedBox width={10} />
        </>
      ) : null}
      {bubble}
      {outgoing ? (
        <>
          <SizedBox width={10} />
          {avatarHoverCard}
        </>
      ) : null}
    </div>
  );
}

export function _MessageAvatarMentionMenu({
  user,
  children,
  onMentionUser = null,
}: {
  user: UserSummary;
  children?: ReactNode;
  onMentionUser?: ((user: UserSummary) => void) | null;
}) {
  const onMention = onMentionUser;
  if (onMention == null) return <>{children}</>;
  const label = _senderName(user);
  return (
    <UiContextMenuTriggerRegion
      onTriggered={(position) => {
        void showUiContextMenu({
          position,
          sections: [new UiContextMenuSection([new UiContextMenuItem({ label: `@${label}`, onPressed: () => onMention(user) })])],
        });
      }}
    >
      {children}
    </UiContextMenuTriggerRegion>
  );
}

export interface _MessageBubbleProps {
  message: Message;
  outgoing: boolean;
  timestampNow: Date;
  showDetailedTimestamps: boolean;
  transfer: FileTransferState | null;
  fileDownloads: Map<string, FileTransferState>;
  downloadActions: ChatFileDownloadActions;
  voicePlaybackActions: ChatVoicePlaybackActions;
  imagePreviewActions: ChatImagePreviewActions;
  messageActions: ChatMessageActions;
  mentionHighlighted: boolean;
  mentionTargetsCurrentUser: boolean;
  currentUser: CurrentUser;
  currentUserMentionIdentity: UserSummary;
  ownerUserId: string | null;
  mentionMembers: readonly RoomMember[];
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  isUserInLive?: ((userId: string) => boolean) | null;
}

class _MessageBubbleState extends ViewState<_MessageBubbleProps> {
  readonly _androidLongPressTracker = new UiAndroidLongPressTracker({
    onLongPress: (position) => this._handleAndroidBubbleLongPress(position),
  });
  readonly _textSelectionReset = new ValueNotifier<number>(0);
  _textSelectionActive = false;
  _contextMenuActive = false;
  _androidLongPressEnabled = false;
  _textSelectionActiveAtPointerDown = false;
  _androidLongPressHandled = false;

  override dispose(): void {
    this._androidLongPressTracker.cancel();
    this._textSelectionReset.dispose();
  }

  build(): ReactNode {
    const widget = this.widget;
    this._androidLongPressEnabled = HostPlatform.isAndroid;
    const contentKind = message_display.messageContentKind(widget.message);
    const contextMenuActive = this._contextMenuActive;
    const backgroundColor = widget.outgoing ? _outgoingBubble : _incomingBubble;
    const mentionBackgroundColor = widget.outgoing ? withAlpha(UiColors.amber, 0.18) : withAlpha(UiColors.amber, 0.13);
    const contextMentionBackgroundColor = widget.outgoing ? withAlpha(UiColors.amber, 0.28) : withAlpha(UiColors.amber, 0.22);
    const targetsCurrentUser = widget.mentionTargetsCurrentUser;
    const mentionHighlightColor = alphaBlend(mentionBackgroundColor, backgroundColor);
    const contextMentionHighlightColor = alphaBlend(contextMentionBackgroundColor, backgroundColor);
    const mentionEmphasized = targetsCurrentUser && (contextMenuActive || widget.mentionHighlighted);
    const highlightColor =
      contextMenuActive && !targetsCurrentUser
        ? UiColors.selected
        : targetsCurrentUser
          ? mentionEmphasized
            ? contextMentionHighlightColor
            : mentionHighlightColor
          : widget.mentionHighlighted
            ? withAlpha(UiColors.selected, 0.86)
            : backgroundColor;
    const borderColor =
      contextMenuActive && !targetsCurrentUser
        ? UiColors.selectedBorder
        : targetsCurrentUser
          ? mentionEmphasized
            ? withAlpha(UiColors.amber, 0.72)
            : withAlpha(UiColors.amber, 0.58)
          : widget.mentionHighlighted
            ? UiColors.selectedBorder
            : widget.outgoing
              ? UiColors.accentBorder
              : UiColors.border;
    const duration = widget.mentionTargetsCurrentUser || widget.mentionHighlighted ? 0 : 90;

    return (
      <div
        data-testid={`message-bubble-surface-${widget.message.id}`}
        onPointerDown={(event) => {
          this._textSelectionActiveAtPointerDown = this._textSelectionActive;
          this._androidLongPressHandled = false;
          this._androidLongPressTracker.handlePointerDown(event, { enabled: this._androidLongPressEnabled });
        }}
        onPointerMove={(event) => this._androidLongPressTracker.handlePointerMove(event)}
        onPointerUp={(event) => {
          this._androidLongPressTracker.handlePointerUp(event);
          this._androidLongPressHandled = false;
        }}
        onPointerCancel={(event) => {
          this._androidLongPressTracker.handlePointerCancel(event);
          this._androidLongPressHandled = false;
        }}
        onContextMenu={(event) => this._handleBubbleContextMenu(event, contentKind)}
        style={{
          boxSizing: 'border-box',
          width: 'fit-content',
          maxWidth: _messageMaxWidth,
          minWidth: 0,
          backgroundColor: highlightColor,
          borderRadius: UiRadii.lg,
          border: `1px solid ${borderColor}`,
          ...paddingStyle(_messageBubblePadding),
          transition:
            duration === 0
              ? 'none'
              : `background-color ${duration}ms ${UiCurves.easeOutCubic}, border-color ${duration}ms ${UiCurves.easeOutCubic}`,
        }}
      >
        <div
          data-testid={`message-bubble-content-${widget.message.id}`}
          style={{ backgroundColor: highlightColor, borderRadius: Math.max(0, UiRadii.lg - 5), minWidth: 0 }}
        >
          <ChatMessageContent
            message={widget.message}
            outgoing={widget.outgoing}
            timestampNow={widget.timestampNow}
            showDetailedTimestamps={widget.showDetailedTimestamps}
            transfer={widget.transfer}
            fileDownloads={widget.fileDownloads}
            downloadActions={widget.downloadActions}
            voicePlaybackActions={widget.voicePlaybackActions}
            imagePreviewActions={widget.imagePreviewActions}
            currentUser={widget.currentUser}
            currentUserMentionIdentity={widget.currentUserMentionIdentity}
            ownerUserId={widget.ownerUserId}
            mentionMembers={widget.mentionMembers}
            mentionHighlighted={widget.mentionHighlighted}
            onResolveSenderProfile={widget.onResolveSenderProfile}
            onResolveRoomProfile={widget.onResolveRoomProfile}
            onEnterProfileRoom={widget.onEnterProfileRoom}
            profileActionBuilder={widget.profileActionBuilder}
            isUserInLive={widget.isUserInLive}
            onSelectionActiveChanged={this._handleTextSelectionActiveChanged}
            textSelectionResetListenable={this._textSelectionReset}
            onOpenQuote={widget.messageActions.onOpenQuote}
            onViewSharedPlaylist={widget.messageActions.onViewSharedPlaylist}
            onEditOwnedSharedPlaylist={widget.messageActions.onEditOwnedSharedPlaylist}
            sharedTrackPreviewController={widget.messageActions.sharedTrackPreviewController}
            loadSharedTrackPlaylists={widget.messageActions.loadSharedTrackPlaylists}
            onAddSharedTrackToPlaylist={widget.messageActions.onAddSharedTrackToPlaylist}
          />
        </div>
      </div>
    );
  }

  _handleAndroidBubbleLongPress(position: WindowPoint): void {
    if (!this._androidLongPressEnabled || this._androidLongPressHandled) return;
    this._androidLongPressHandled = true;
    const contentKind = message_display.messageContentKind(this.widget.message);
    if (contentKind === message_display.MessageContentKind.text && this._textSelectionActiveAtPointerDown) {
      return;
    }
    if (contentKind === message_display.MessageContentKind.text) {
      this._textSelectionReset.value++;
    }
    // PORT: `ContextMenuController.removeAny()`; text menus close on the next
    // outside pointer down in this port.
    this._showBubbleContextMenuAt(position, contentKind, { textSelectionActive: this._textSelectionActiveAtPointerDown });
  }

  /**
   * PORT: Dart opened the menu from the bubble's `Listener.onPointerDown`
   * with the secondary button. A DOM menu opened on pointer down would be
   * dismissed by the `contextmenu` event that follows, so the bubble answers
   * `contextmenu` instead, with the selection state from that pointer down.
   * It runs after the text body's own handler (which shows the text menu
   * only when a selection existed), so the two never both open.
   */
  _handleBubbleContextMenu(event: ReactMouseEvent<HTMLDivElement>, contentKind: message_display.MessageContentKind): void {
    event.preventDefault();
    event.stopPropagation();
    const pointerType = (event.nativeEvent as PointerEvent).pointerType;
    // Touch holds go through the Android long-press tracker.
    if (pointerType === 'touch' || pointerType === 'pen') return;
    this._showBubbleContextMenuAt({ x: event.clientX, y: event.clientY }, contentKind, {
      textSelectionActive: this._textSelectionActiveAtPointerDown,
    });
  }

  _showBubbleContextMenuAt(
    position: WindowPoint,
    contentKind: message_display.MessageContentKind,
    opts: { textSelectionActive: boolean },
  ): void {
    const widget = this.widget;
    if (contentKind === message_display.MessageContentKind.text && opts.textSelectionActive) {
      return;
    }
    if (contentKind === message_display.MessageContentKind.sticker) {
      this._showContextMenuWithHighlight(() =>
        _showStickerContextMenu({
          position,
          message: widget.message,
          attachment: widget.message.stickerAttachment!,
          imagePreviewActions: widget.imagePreviewActions,
          messageActions: widget.messageActions,
        }),
      );
      return;
    }
    this._showContextMenuWithHighlight(() =>
      _showChatMessageContextMenu({ position, message: widget.message, actions: widget.messageActions }),
    );
  }

  _handleTextSelectionActiveChanged = (active: boolean): void => {
    if (this._textSelectionActive === active) return;
    this.setState(() => {
      this._textSelectionActive = active;
    });
  };

  _showContextMenuWithHighlight(showMenu: () => Promise<void>): void {
    this._setContextMenuActive(true);
    void showMenu().finally(() => {
      if (this.mounted) this._setContextMenuActive(false);
    });
  }

  _setContextMenuActive(active: boolean): void {
    if (!this.mounted || this._contextMenuActive === active) return;
    this.setState(() => {
      this._contextMenuActive = active;
    });
  }
}

export function _MessageBubble(props: _MessageBubbleProps) {
  const state = useViewState(() => new _MessageBubbleState(), props);
  return <>{state.build()}</>;
}

export interface MessageBubbleForTestProps {
  message: Message;
  downloadActions: ChatFileDownloadActions;
  imagePreviewActions?: ChatImagePreviewActions | null;
  outgoing?: boolean;
  transfer?: FileTransferState | null;
  fileDownloads?: Map<string, FileTransferState>;
  voicePlaybackActions?: ChatVoicePlaybackActions;
  messageActions?: ChatMessageActions;
  timestampNow?: Date | null;
  showDetailedTimestamps?: boolean;
}

/** Test-only entry point for a single private message bubble. */
export function MessageBubbleForTest({
  message,
  downloadActions,
  imagePreviewActions = null,
  outgoing = false,
  transfer = null,
  fileDownloads,
  voicePlaybackActions,
  messageActions,
  timestampNow = null,
  showDetailedTimestamps = false,
}: MessageBubbleForTestProps) {
  // Dart const defaults, created once per mount.
  const defaults = useRef<{
    fileDownloads: Map<string, FileTransferState>;
    voicePlaybackActions: ChatVoicePlaybackActions;
    messageActions: ChatMessageActions;
    imagePreviewActions: ChatImagePreviewActions;
    currentUserMentionIdentity: UserSummary;
  } | null>(null);
  defaults.current ??= {
    fileDownloads: new Map(),
    voicePlaybackActions: ChatVoicePlaybackActions.disabled(),
    messageActions: ChatMessageActions.disabled(),
    imagePreviewActions: ChatImagePreviewActions.disabled(),
    currentUserMentionIdentity: _avatarHoverCardTestCurrentUser.toSummary(),
  };
  const fallback = defaults.current;
  return (
    <div style={{ maxWidth: _messageMaxWidth, minWidth: 0 }}>
      <_MessageBubble
        message={message}
        outgoing={outgoing}
        timestampNow={timestampNow ?? new Date()}
        showDetailedTimestamps={showDetailedTimestamps}
        transfer={transfer}
        fileDownloads={fileDownloads ?? fallback.fileDownloads}
        downloadActions={downloadActions}
        voicePlaybackActions={voicePlaybackActions ?? fallback.voicePlaybackActions}
        imagePreviewActions={imagePreviewActions ?? fallback.imagePreviewActions}
        messageActions={messageActions ?? fallback.messageActions}
        mentionHighlighted={false}
        mentionTargetsCurrentUser={false}
        currentUser={_avatarHoverCardTestCurrentUser}
        currentUserMentionIdentity={fallback.currentUserMentionIdentity}
        ownerUserId={null}
        mentionMembers={_noMentionMembers}
      />
    </div>
  );
}

const _noMentionMembers: readonly RoomMember[] = [];

export function _CenteredState({
  icon,
  title,
  detail,
  action = null,
}: {
  icon: IconData;
  title: string;
  detail: string;
  action?: ReactNode | null;
}) {
  return (
    <Center style={_fillStyle}>
      {/*
        ConstrainedBox(maxWidth: 320) under Center. The box takes the full
        320 (less on narrow panes) so the Button below fills it like Flutter's
        bounded Button; the centred texts look the same either way.
      */}
      <div style={{ width: '100%', maxWidth: 320, minWidth: 0, boxSizing: 'border-box', padding: 24 }}>
        <Column mainAxisSize="min">
          <Icon icon={icon} color={UiColors.textMuted} size={30} />
          <SizedBox height={12} />
          <Text textAlign="center" style={UiTypography.title}>
            {title}
          </Text>
          <SizedBox height={7} />
          <Text textAlign="center" style={{ ...UiTypography.body, color: UiColors.textMuted }}>
            {detail}
          </Text>
          {action != null ? (
            <>
              <SizedBox height={16} />
              {action}
            </>
          ) : null}
        </Column>
      </div>
    </Center>
  );
}
