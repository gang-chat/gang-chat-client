import * as room_display from '@gang-chat/core/app/room_display';
import * as voice_display from '@gang-chat/core/app/voice_message_display';
import type { MusicTrackPreviewController } from '@gang-chat/core/app/music_track_preview';
import type {
  CurrentUser,
  Message,
  MessageAttachment,
  MessageQuote,
  SharedMusicPlaylist,
  SharedMusicTrack,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { UiContextMenuItem, UiContextMenuSection, showUiContextMenu, type WindowPoint } from '../ui/context_menu';
import { FloatingNoticeTone, showFloatingNotice } from '../ui/feedback';
import { roleBadgeForegroundColorForLabel } from '../ui/role_badge';
import { UiColors, argb } from '../ui/tokens';
import type { MusicTrackPlaylistTarget } from './music_track_profile_card';

// PORT: the `chat_pane.dart` constants the message widgets read. The part
// files shared them as library privates; here they are mirrored with the same
// values instead of imported from './chat_pane', because chat_pane.tsx
// imports the message modules and importing back would form an evaluation
// cycle (chat_messages could be evaluated before chat_pane's constants exist).
export const _chatHorizontalPadding = 18.0;
export const _chatFloatingEdgeInset = 14.0;
export const _messageMaxWidth = 560.0;
export const _outgoingBubble = argb(0xff1f352b);
export const _incomingBubble = UiColors.surface;

/**
 * Shared pieces of `chat_messages.dart` (the message action bundles, the
 * message context menu and the sender name / colour helpers). Split out of
 * `chat_messages.tsx` so the sibling `chat_messages_*` modules can use them
 * without an import cycle; `chat_messages.tsx` re-exports everything.
 *
 * PORT: the Dart callbacks took a `BuildContext` first argument (for
 * notices and dialogs); it is dropped like everywhere else in the port.
 */

/**
 * Callbacks for file-attachment downloads, bundled so they can travel from
 * [ChatPane] down to each [_FileAttachmentTile] without threading five
 * separate parameters through every intermediate widget.
 */
export class ChatFileDownloadActions {
  constructor(opts: {
    onDownload: (message: Message, attachment: MessageAttachment, index: number, resolvedUrl: string) => void;
    onPause: (downloadKey: string) => void;
    onResume: (downloadKey: string) => void;
    onCancel: (downloadKey: string) => void;
    onDismiss: (downloadKey: string) => void;
  }) {
    this.onDownload = opts.onDownload;
    this.onPause = opts.onPause;
    this.onResume = opts.onResume;
    this.onCancel = opts.onCancel;
    this.onDismiss = opts.onDismiss;
  }

  /**
   * Start downloading [attachment] (the [index]th attachment of [message]).
   * [resolvedUrl] is the absolute asset URL, already resolved by the widget
   * layer against [AppConfig].
   */
  readonly onDownload: (message: Message, attachment: MessageAttachment, index: number, resolvedUrl: string) => void;
  readonly onPause: (downloadKey: string) => void;
  readonly onResume: (downloadKey: string) => void;
  readonly onCancel: (downloadKey: string) => void;
  readonly onDismiss: (downloadKey: string) => void;
}

export class ChatVoicePlaybackActions {
  /** Positions and durations are milliseconds (Dart `Duration`). */
  constructor(opts: {
    activeMessageId: string | null;
    onToggle: ((messageId: string, resolvedUrl: string) => void) | null;
    activePosition?: number;
    activeDuration?: number;
  }) {
    this.activeMessageId = opts.activeMessageId;
    this.onToggle = opts.onToggle;
    this.activePosition = opts.activePosition ?? 0;
    this.activeDuration = opts.activeDuration ?? 0;
  }

  /** Port of `const ChatVoicePlaybackActions.disabled()`. */
  static disabled(): ChatVoicePlaybackActions {
    return new ChatVoicePlaybackActions({ activeMessageId: null, onToggle: null });
  }

  readonly activeMessageId: string | null;
  readonly activePosition: number;
  readonly activeDuration: number;
  readonly onToggle: ((messageId: string, resolvedUrl: string) => void) | null;

  isPlaying(messageId: string): boolean {
    return this.activeMessageId === messageId;
  }

  progressFor(messageId: string, opts: { fallbackDuration?: number | null } = {}): number {
    if (!this.isPlaying(messageId)) return 0;
    const duration = this.activeDuration > 0 ? this.activeDuration : (opts.fallbackDuration ?? null);
    return voice_display.voicePlaybackProgress({ position: this.activePosition, duration });
  }
}

export type ChatSharedPlaylistCallback = (message: Message, playlist: SharedMusicPlaylist) => Promise<void>;

export class ChatMessageActions {
  constructor(opts: {
    onCopy: (message: Message) => Promise<void>;
    onQuote?: (message: Message) => void;
    onOpenQuote?: (quote: MessageQuote) => Promise<void>;
    onDeleteForMe: (message: Message) => Promise<void>;
    onRecall: (message: Message) => Promise<void>;
    canRecall: (message: Message) => boolean;
    canQuote?: (message: Message) => boolean;
    onReeditRecalledText?: (message: Message) => void;
    canReeditRecalledText?: (message: Message) => boolean;
    canInspectRecalledText?: (message: Message) => boolean;
    onViewSharedPlaylist?: ChatSharedPlaylistCallback;
    onEditOwnedSharedPlaylist?: ChatSharedPlaylistCallback | null;
    sharedTrackPreviewController?: MusicTrackPreviewController | null;
    loadSharedTrackPlaylists?: ((roomId: string) => Promise<MusicTrackPlaylistTarget[]>) | null;
    onAddSharedTrackToPlaylist?: ((track: SharedMusicTrack, target: MusicTrackPlaylistTarget) => Promise<void>) | null;
  }) {
    this.onCopy = opts.onCopy;
    this.onQuote = opts.onQuote ?? ChatMessageActions._syncNoop;
    this.onOpenQuote = opts.onOpenQuote ?? ChatMessageActions._noopQuote;
    this.onDeleteForMe = opts.onDeleteForMe;
    this.onRecall = opts.onRecall;
    this.canRecall = opts.canRecall;
    this.canQuote = opts.canQuote ?? ChatMessageActions._neverCanRecall;
    this.onReeditRecalledText = opts.onReeditRecalledText ?? ChatMessageActions._syncNoop;
    this.canReeditRecalledText = opts.canReeditRecalledText ?? ChatMessageActions._neverCanRecall;
    this.canInspectRecalledText = opts.canInspectRecalledText ?? ChatMessageActions._neverCanRecall;
    this.onViewSharedPlaylist = opts.onViewSharedPlaylist ?? ChatMessageActions._noopSharedPlaylist;
    this.onEditOwnedSharedPlaylist = opts.onEditOwnedSharedPlaylist ?? null;
    this.sharedTrackPreviewController = opts.sharedTrackPreviewController ?? null;
    this.loadSharedTrackPlaylists = opts.loadSharedTrackPlaylists ?? null;
    this.onAddSharedTrackToPlaylist = opts.onAddSharedTrackToPlaylist ?? null;
  }

  /** Port of `const ChatMessageActions.disabled()`. */
  static disabled(): ChatMessageActions {
    return new ChatMessageActions({
      onCopy: ChatMessageActions._noop,
      onQuote: ChatMessageActions._syncNoop,
      onOpenQuote: ChatMessageActions._noopQuote,
      onDeleteForMe: ChatMessageActions._noop,
      onRecall: ChatMessageActions._noop,
      canRecall: ChatMessageActions._neverCanRecall,
      canQuote: ChatMessageActions._neverCanRecall,
      onReeditRecalledText: ChatMessageActions._syncNoop,
      canReeditRecalledText: ChatMessageActions._neverCanRecall,
      canInspectRecalledText: ChatMessageActions._neverCanRecall,
      onViewSharedPlaylist: ChatMessageActions._noopSharedPlaylist,
      onEditOwnedSharedPlaylist: null,
      sharedTrackPreviewController: null,
      loadSharedTrackPlaylists: null,
      onAddSharedTrackToPlaylist: null,
    });
  }

  readonly onCopy: (message: Message) => Promise<void>;
  readonly onQuote: (message: Message) => void;
  readonly onOpenQuote: (quote: MessageQuote) => Promise<void>;
  readonly onDeleteForMe: (message: Message) => Promise<void>;
  readonly onRecall: (message: Message) => Promise<void>;
  readonly canRecall: (message: Message) => boolean;
  readonly canQuote: (message: Message) => boolean;
  readonly onReeditRecalledText: (message: Message) => void;
  readonly canReeditRecalledText: (message: Message) => boolean;
  readonly canInspectRecalledText: (message: Message) => boolean;
  readonly onViewSharedPlaylist: ChatSharedPlaylistCallback;
  readonly onEditOwnedSharedPlaylist: ChatSharedPlaylistCallback | null;
  readonly sharedTrackPreviewController: MusicTrackPreviewController | null;
  readonly loadSharedTrackPlaylists: ((roomId: string) => Promise<MusicTrackPlaylistTarget[]>) | null;
  readonly onAddSharedTrackToPlaylist: ((track: SharedMusicTrack, target: MusicTrackPlaylistTarget) => Promise<void>) | null;

  private static async _noop(_message: Message): Promise<void> {}

  private static async _noopQuote(_quote: MessageQuote): Promise<void> {}

  private static _syncNoop(_message: Message): void {}

  private static _neverCanRecall(_message: Message): boolean {
    return false;
  }

  private static async _noopSharedPlaylist(_message: Message, _playlist: SharedMusicPlaylist): Promise<void> {}
}

// ---------------------------------------------------------------------------
// Message context menu.

export function _showChatMessageContextMenu(opts: {
  position: WindowPoint;
  message: Message;
  actions: ChatMessageActions;
}): Promise<void> {
  const { position, message, actions } = opts;
  const sections = _messageContextMenuSections({
    message,
    actions,
    includeCopy: true,
    includeQuote: true,
    includeDelete: true,
    includeRecall: !message.isRemoved,
  });
  return showUiContextMenu({ position, sections });
}

export function _messageContextMenuSections(opts: {
  message: Message;
  actions: ChatMessageActions;
  includeCopy?: boolean;
  includeQuote?: boolean;
  includeDelete?: boolean;
  includeRecall?: boolean;
}): UiContextMenuSection[] {
  const { message, actions, includeCopy = true, includeQuote = true, includeDelete = true, includeRecall = true } = opts;
  const sections: UiContextMenuSection[] = [];
  const canQuote = includeQuote && actions.canQuote(message);
  if (includeCopy || canQuote) {
    const items: UiContextMenuItem[] = [];
    if (includeCopy) {
      items.push(
        new UiContextMenuItem({
          label: '复制',
          shortcut: 'Ctrl+C',
          onPressed: () => void actions.onCopy(message),
        }),
      );
    }
    if (canQuote) {
      items.push(new UiContextMenuItem({ label: '引用', onPressed: () => actions.onQuote(message) }));
    }
    sections.push(new UiContextMenuSection(items));
  }
  if (includeRecall && actions.canRecall(message)) {
    sections.push(
      new UiContextMenuSection([new UiContextMenuItem({ label: '撤回', onPressed: () => void actions.onRecall(message) })]),
    );
  }
  if (includeDelete) {
    sections.push(
      new UiContextMenuSection([
        new UiContextMenuItem({ label: '删除', onPressed: () => void actions.onDeleteForMe(message) }),
      ]),
    );
  }
  return sections;
}

export function _showMessageContextNotice(message: string, opts: { tone?: FloatingNoticeTone } = {}): void {
  showFloatingNotice(message, { tone: opts.tone ?? FloatingNoticeTone.info });
}

export function _ignoreMessageSelectionChange(_active: boolean): void {}

// ---------------------------------------------------------------------------
// Sender names and colours.

export function chatRoomUsernameColor(opts: {
  user: UserSummary;
  currentUser: CurrentUser;
  ownerUserId?: string | null;
}): string {
  const { user, currentUser } = opts;
  if (user.id === currentUser.id) return UiColors.accent;
  return roleBadgeForegroundColorForLabel(room_display.roomRoleLabel(user, { ownerUserId: opts.ownerUserId ?? null }));
}

export function _senderName(user: UserSummary): string {
  const roomName = user.roomDisplayName?.trim();
  if (roomName != null && roomName.length > 0) return roomName;
  return user.displayName;
}
