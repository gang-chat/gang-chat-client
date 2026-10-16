import * as composer_attachment from '@gang-chat/core/app/composer_attachment_display';
import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import * as file_display from '@gang-chat/core/app/file_display';
import { MediaCacheRequest } from '@gang-chat/core/app/media_cache_controller';
import * as message_display from '@gang-chat/core/app/message_display';
import * as message_mentions from '@gang-chat/core/app/message_mentions';
import {
  composerMessageSendPlan,
  sendComposerMessagePartsSequentially,
  canSendComposedMessage,
  type FileMessageStatePatch,
} from '@gang-chat/core/app/messages_controller';
import * as room_display from '@gang-chat/core/app/room_display';
import { SettingsSection } from '@gang-chat/core/app/settings_shell_state';
import * as sticker_display from '@gang-chat/core/app/sticker_display';
import * as voice_display from '@gang-chat/core/app/voice_message_display';
import { AppConfig } from '@gang-chat/core/config/app_config';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Timer, delay, unawaited } from '@gang-chat/core/lib/async';
import { HttpClientHelpers } from '@gang-chat/core/platform/http';
import { platform } from '@gang-chat/core/platform/platform';
import {
  ApiException,
  UploadCancelledException,
  type MusicTrackPreviewApi,
  type PersonalMusicPlaylistApi,
  type RoomMusicPlaylistApi,
  type SharedMusicPlaylistCloneApi,
} from '@gang-chat/core/protocol/api_client';
import {
  Message,
  MessageAttachment,
  MessageQuote,
  MusicBoxSearchResult,
  RoomDetail,
  SharedMusicPlaylist,
  SharedMusicTrack,
  Sticker,
  StickerPack,
  UploadedAsset,
  UserSummary,
} from '@gang-chat/core/protocol/models';
import type { FileDropEvent } from '@gang-chat/core/shell/file_drop_service';
import { SelectedFile } from '@gang-chat/core/shell/file_selection_service';

import { Text } from '../lib/layout';
import { TextEditingValue, TextRange, TextSelection } from '../lib/text_editing';
import { ButtonTone } from '../ui/button';
import { ComposerSuggestionAction, ComposerSuggestionNavigation } from '../ui/chat_composer';
import { DialogFrame, FloatingNoticeTone, ResponsiveDialogAction, showFloatingErrorNotice, showFloatingNotice } from '../ui/feedback';
import { Icons } from '../ui/icon';
import { rectInWindow, showDialog } from '../ui/overlay';
import { UiTypography } from '../ui/tokens';
import { ChatMessageActions } from './chat_messages';
import type { HomeShellState } from './home_shell';
import { _ContentMode, _StagedAttachment } from './home_shell';
import { MusicPlaylistSnapshotDialog } from './music_playlist_snapshot_dialog';
import { MusicTrackCardData, MusicTrackPlaylistTarget } from './music_track_profile_card';

// ---------------------------------------------------------------------------
// Dart error types this extension throws and inspects.

/** Dart `Exception(message)`; its `toString()` reads `Exception: <message>`. */
export class _DartException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'Exception';
  }

  override toString(): string {
    return `Exception: ${this.message}`;
  }
}

/** Dart `StateError(message)`; [_stagedAttachmentErrorMessage] shows its bare message. */
export class _StateError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'StateError';
  }

  override toString(): string {
    return `Bad state: ${this.message}`;
  }
}

// Dart checks optional server extensions with `api is XxxApi`. TS interfaces
// have no runtime identity, so the port probes for the extension's method
// (same approach as `app/personal_music_playlists.ts`).
export function _isPersonalMusicPlaylistApi<T extends object>(api: T): api is T & PersonalMusicPlaylistApi {
  return 'listPersonalMusicPlaylists' in api && typeof api.listPersonalMusicPlaylists === 'function';
}

export function _isRoomMusicPlaylistApi<T extends object>(api: T): api is T & RoomMusicPlaylistApi {
  return 'listRoomMusicPlaylists' in api && typeof api.listRoomMusicPlaylists === 'function';
}

export function _isMusicTrackPreviewApi<T extends object>(api: T): api is T & MusicTrackPreviewApi {
  return 'downloadMusicTrackPreview' in api && typeof api.downloadMusicTrackPreview === 'function';
}

export function _isSharedMusicPlaylistCloneApi<T extends object>(api: T): api is T & SharedMusicPlaylistCloneApi {
  return 'cloneSharedMusicPlaylistToPersonal' in api && typeof api.cloneSharedMusicPlaylistToPersonal === 'function';
}

/**
 * PORT: Dart read `AppConfigScope.of(context)` from the message bubble that
 * invoked the action. A ViewState has no BuildContext, so the shell's render
 * publishes the scope's config as `_appConfig` (HomeShell reads
 * `useAppConfig()`); the defaults apply when it has not.
 */
export function _homeAppConfig(state: HomeShellState): AppConfig {
  const scoped = (state as unknown as { _appConfig?: AppConfig | null })._appConfig;
  return scoped ?? AppConfig.defaults();
}

function _replaceRange(text: string, start: number, end: number, replacement: string): string {
  return `${text.slice(0, start)}${replacement}${text.slice(end)}`;
}

function _compareStrings(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * Port of `extension _HomeShellMessages on _HomeShellState`.
 *
 * PORT notes for the whole table:
 *  - The Dart getters (`_selectedComposerQuotes`, `_selectedComposerComponent`,
 *    `_sidebarRoomDrafts`, `_chatMessageActions`, `_stagedAttachmentViews`)
 *    are methods: the table is merged with `Object.assign`, which would
 *    evaluate a getter against the table instead of copying it.
 *  - The message actions dropped their `BuildContext` argument (like the
 *    kit's `showFloatingNotice`); `context.mounted` checks of the bubble
 *    become checks of the shell's [mounted].
 */
export const HomeShellMessages = {
  _latestServerMessage(this: HomeShellState, messages: readonly Message[]): Message | null {
    for (let index = messages.length - 1; index >= 0; index--) {
      const message = messages[index]!;
      if (!message.pending) return message;
    }
    return null;
  },

  _latestServerMessageId(this: HomeShellState, messages: readonly Message[]): string | null {
    return this._latestServerMessage(messages)?.id ?? null;
  },

  _clearRoomUnreadCount(this: HomeShellState, roomId: string): void {
    this._servers = this._roomsController.patchRoomUnreadCleared({ rooms: this._servers, roomId }).rooms;
  },

  async _markRoomReadFromMessages(this: HomeShellState, roomId: string, messages: readonly Message[]): Promise<void> {
    if (this._selectedServerId !== roomId) return;
    const lastReadMessage = this._latestServerMessage(messages);
    if (lastReadMessage == null) return;
    const lastReadMessageId = lastReadMessage.id;

    if (this._services.roomReads.isSynced({ roomId, messageId: lastReadMessageId })) {
      if (this.mounted) this._setHomeState(() => this._clearRoomUnreadCount(roomId));
      return;
    }
    if (this.mounted) this._setHomeState(() => this._clearRoomUnreadCount(roomId));

    await this._services.roomReads.markRead({
      roomId,
      lastReadMessageId,
      messageCreatedAt: lastReadMessage.createdAt,
    });
  },

  _clearSelectedRoomNewMessagePrompt(this: HomeShellState): void {
    if (this._selectedRoomNewMessageCount === 0) return;
    const roomId = this._selectedServerId;
    const messages = this._messages;
    this._setHomeState(() => {
      this._selectedRoomNewMessageCount = 0;
    });
    if (roomId != null) {
      unawaited(this._markRoomReadFromMessages(roomId, messages));
    }
  },

  _handleComposerDraftChanged(this: HomeShellState): void {
    if (this._updatingComposerFromDraft) return;
    if (this._tryCompleteSingleComposerMentionAfterSpace()) return;
    this._composerController.pruneInvalidConfirmedMentions({ notify: false });
    this._saveComposerDraftValue(this._composerController.text);
    this._updateComposerMentionState();
  },

  _storeSelectedComposerDraft(this: HomeShellState): void {
    this._setHomeState(() => this._saveDraftInState());
  },

  _restoreComposerDraftForRoom(this: HomeShellState, roomId: string): void {
    this._stagedAttachments.length = 0;
    this._stagedAttachments.push(...(this._stagedAttachmentDrafts.get(roomId) ?? []));
    this._setComposerText(this._messageDrafts.get(roomId) ?? '', { saveDraft: false });
    this._clearComposerMentionState();
  },

  _selectedComposerQuotes(this: HomeShellState): MessageQuote[] {
    const roomId = this._selectedServerId;
    return roomId == null ? [] : [...(this._messageQuoteDrafts.get(roomId) ?? [])];
  },

  _selectedComposerComponent(this: HomeShellState): Message | null {
    const roomId = this._selectedServerId;
    return roomId == null ? null : (this._messageComponentDrafts.get(roomId) ?? null);
  },

  _removeSelectedComposerComponent(this: HomeShellState): void {
    const roomId = this._selectedServerId;
    if (roomId == null || !this._messageComponentDrafts.has(roomId)) return;
    this._setHomeState(() => {
      const next = new Map(this._messageComponentDrafts);
      next.delete(roomId);
      this._messageComponentDrafts = next;
      this._saveDraftInState();
    });
  },

  _quoteChatMessage(this: HomeShellState, message: Message): void {
    if (!this._canQuoteChatMessage(message)) return;
    const current = this._messageQuoteDrafts.get(message.roomId) ?? [];
    if (current.some((quote) => quote.messageId === message.id)) {
      this._composerPanelController.closePanel();
      this._composerPanelController.requestInputFocus();
      return;
    }
    this._setHomeState(() => {
      const next = new Map(this._messageQuoteDrafts);
      next.set(message.roomId, [...current, message_display.messageQuoteSnapshot(message)]);
      this._messageQuoteDrafts = next;
      this._saveDraftInState();
      this._sendError = null;
    });
    this._composerPanelController.closePanel();
    this._composerPanelController.requestInputFocus();
  },

  _removeSelectedComposerQuote(this: HomeShellState, messageId: string): void {
    const roomId = this._selectedServerId;
    if (roomId == null || !this._messageQuoteDrafts.has(roomId)) return;
    this._setHomeState(() => {
      const remaining = (this._messageQuoteDrafts.get(roomId) ?? []).filter((quote) => quote.messageId !== messageId);
      const next = new Map(this._messageQuoteDrafts);
      if (remaining.length === 0) {
        next.delete(roomId);
      } else {
        next.set(roomId, remaining);
      }
      this._messageQuoteDrafts = next;
      this._saveDraftInState();
    });
  },

  _setComposerText(this: HomeShellState, text: string, opts: { saveDraft: boolean }): void {
    const { saveDraft } = opts;
    const nextValue = new TextEditingValue({
      text,
      selection: TextSelection.collapsed({ offset: text.length }),
    });
    if (this._composerController.value.equals(nextValue)) {
      if (saveDraft) this._saveComposerDraftValue(text);
      return;
    }

    this._composerController.clearConfirmedMentions();
    const previousUpdating = this._updatingComposerFromDraft;
    this._updatingComposerFromDraft = true;
    this._composerController.value = nextValue;
    this._updatingComposerFromDraft = previousUpdating;
    if (saveDraft) this._saveComposerDraftValue(text);
  },

  _saveComposerDraftValue(this: HomeShellState, text: string): void {
    this._setHomeState(() => this._saveDraftInState({ text }));
  },

  _buildComposerMentionOptions(
    this: HomeShellState,
    query: message_mentions.MessageMentionQuery | null,
  ): message_mentions.MessageMentionOption[] {
    const roomId = this._selectedServerId;
    if (query == null || roomId == null || this._composerMentionMembersRoomId !== roomId) {
      return [];
    }
    return message_mentions.messageMentionOptions({
      members: this._composerMentionMembers,
      query: query.query,
      ownerUserId: this._selectedRoom?.createdBy?.id ?? null,
      excludedUserId: this._currentUser.id,
      limit: 50,
      includeGangBot: this._selectedRoom?.gangBotActive ?? false,
    });
  },

  _setComposerMentionQuery(this: HomeShellState, query: message_mentions.MessageMentionQuery | null): void {
    this._composerMentionQuery = query;
    this._composerMentionOptions = this._buildComposerMentionOptions(query);
    this._composerMentionSelectedIndex = 0;
  },

  _refreshComposerMentionOptions(this: HomeShellState): void {
    this._composerMentionOptions = this._buildComposerMentionOptions(this._composerMentionQuery);
    this._composerMentionSelectedIndex = this._clampComposerMentionSelectedIndex(this._composerMentionSelectedIndex);
  },

  _clampComposerMentionSelectedIndex(this: HomeShellState, index: number): number {
    if (this._composerMentionOptions.length === 0) return 0;
    return Math.min(Math.max(index, 0), this._composerMentionOptions.length - 1);
  },

  _highlightComposerMentionSelection(this: HomeShellState, index: number): void {
    if (this._composerMentionOptions.length === 0) return;
    const nextIndex = this._clampComposerMentionSelectedIndex(index);
    if (this._composerMentionSelectedIndex === nextIndex) return;
    this._setHomeState(() => {
      this._composerMentionSelectedIndex = nextIndex;
    });
  },

  _navigateComposerMentionSelection(this: HomeShellState, navigation: ComposerSuggestionNavigation): boolean {
    if (this._composerMentionQuery == null || this._composerMentionOptions.length === 0) {
      return false;
    }
    const current = this._clampComposerMentionSelectedIndex(this._composerMentionSelectedIndex);
    const delta = navigation === ComposerSuggestionNavigation.previous ? -1 : 1;
    const count = this._composerMentionOptions.length;
    const nextIndex = (current + delta + count) % count;
    this._setHomeState(() => {
      this._composerMentionSelectedIndex = nextIndex;
    });
    this._composerPanelController.requestInputFocus();
    return true;
  },

  _confirmComposerMentionSelection(this: HomeShellState, action: ComposerSuggestionAction): boolean {
    if (action !== ComposerSuggestionAction.confirm) return false;
    return this._completeComposerMentionSelection({
      includeTerminatingSpace: true,
      synthesizeTerminatingSpace: true,
    });
  },

  _clearComposerMentionState(this: HomeShellState): void {
    if (
      this._composerMentionQuery == null &&
      this._composerMentionOptions.length === 0 &&
      !this._loadingComposerMentionMembers
    ) {
      return;
    }
    this._setHomeState(() => {
      this._setComposerMentionQuery(null);
      this._loadingComposerMentionMembers = false;
      this._loadingComposerMentionMembersRoomId = null;
    });
  },

  _updateComposerMentionState(this: HomeShellState): void {
    const roomId = this._selectedServerId;
    if (roomId == null) {
      if (this._composerMentionQuery != null) {
        this._setHomeState(() => this._setComposerMentionQuery(null));
      }
      return;
    }
    const selection = this._composerController.selection;
    const query =
      !selection.isValid || !selection.isCollapsed
        ? null
        : message_mentions.activeMessageMentionQuery({
            text: this._composerController.text,
            cursorOffset: selection.extentOffset,
          });
    if (query == null || this._composerController.hasConfirmedMention({ start: query.start, end: query.end })) {
      if (this._composerMentionQuery != null) {
        this._setHomeState(() => this._setComposerMentionQuery(null));
      }
      return;
    }

    const previous = this._composerMentionQuery;
    if (previous == null || previous.start !== query.start || previous.end !== query.end || previous.query !== query.query) {
      this._setHomeState(() => this._setComposerMentionQuery(query));
    }
    unawaited(this._ensureComposerMentionMembers(roomId));
  },

  async _ensureComposerMentionMembers(this: HomeShellState, roomId: string): Promise<void> {
    if (
      this._composerMentionMembersRoomId === roomId ||
      (this._loadingComposerMentionMembers && this._loadingComposerMentionMembersRoomId === roomId)
    ) {
      return;
    }
    const serial = ++this._composerMentionMembersSerial;
    this._setHomeState(() => {
      this._loadingComposerMentionMembers = true;
      this._loadingComposerMentionMembersRoomId = roomId;
    });
    try {
      const members = await this._roomsController.loadAllRoomMembers(roomId);
      if (!this.mounted || serial !== this._composerMentionMembersSerial || this._selectedServerId !== roomId) {
        return;
      }
      this._setHomeState(() => {
        this._composerMentionMembers = [...members];
        this._composerMentionMembersRoomId = roomId;
        this._loadingComposerMentionMembers = false;
        this._loadingComposerMentionMembersRoomId = null;
        this._refreshComposerMentionOptions();
      });
    } catch {
      if (!this.mounted || serial !== this._composerMentionMembersSerial || this._selectedServerId !== roomId) {
        return;
      }
      this._setHomeState(() => {
        this._composerMentionMembers = [];
        this._composerMentionMembersRoomId = roomId;
        this._loadingComposerMentionMembers = false;
        this._loadingComposerMentionMembersRoomId = null;
        this._refreshComposerMentionOptions();
      });
    }
  },

  _selectComposerMention(this: HomeShellState, option: message_mentions.MessageMentionOption): void {
    const value = this._composerController.value;
    const selection = value.selection;
    const query =
      selection.isValid && selection.isCollapsed
        ? message_mentions.activeMessageMentionQuery({
            text: value.text,
            cursorOffset: selection.extentOffset,
          })
        : this._composerMentionQuery;
    if (query == null) return;

    const replacement = message_mentions.messageMentionInsertText(option);
    const nextText = _replaceRange(value.text, query.start, query.end, replacement);
    const nextOffset = query.start + replacement.length;
    const nextValue = value.copyWith({
      text: nextText,
      selection: TextSelection.collapsed({ offset: nextOffset }),
      composing: TextRange.empty,
    });
    this._composerController.value = nextValue;
    this._composerController.addConfirmedMention({ start: query.start, label: option.label });
    this._saveComposerDraftValue(nextText);
    if (this._composerMentionQuery != null) {
      this._setHomeState(() => this._setComposerMentionQuery(null));
    }
    this._composerPanelController.requestInputFocus();
  },

  _tryCompleteSingleComposerMentionAfterSpace(this: HomeShellState): boolean {
    const value = this._composerController.value;
    const selection = value.selection;
    if (!selection.isValid || !selection.isCollapsed) return false;
    const query = this._composerMentionQuery;
    if (query == null) return false;
    const cursor = selection.extentOffset;
    if (cursor !== query.end + 1) return false;
    if (query.start < 0 || query.end >= value.text.length) return false;
    if (value.text.charCodeAt(query.end) !== 0x20) return false;
    return this._completeComposerMentionSelection({ includeTerminatingSpace: true });
  },

  _completeComposerMentionSelection(
    this: HomeShellState,
    opts: { includeTerminatingSpace: boolean; synthesizeTerminatingSpace?: boolean },
  ): boolean {
    const { includeTerminatingSpace, synthesizeTerminatingSpace = false } = opts;
    const query = this._composerMentionQuery;
    if (query == null) return false;
    const value = this._composerController.value;
    const selection = value.selection;
    if (!selection.isValid || !selection.isCollapsed) return false;
    let text = value.text;
    if (synthesizeTerminatingSpace) {
      if (!includeTerminatingSpace || selection.extentOffset !== query.end) {
        return false;
      }
      text = _replaceRange(text, query.end, query.end, ' ');
    }
    const replaceEnd = includeTerminatingSpace ? query.end + 1 : query.end;
    if (query.start < 0 || query.end > text.length || replaceEnd > text.length) {
      return false;
    }
    if (includeTerminatingSpace && text.charCodeAt(query.end) !== 0x20) {
      return false;
    }
    if (text.substring(query.start, query.end) !== `@${query.query}`) {
      return false;
    }
    if (this._composerController.hasConfirmedMention({ start: query.start, end: query.end })) {
      this._setHomeState(() => this._setComposerMentionQuery(null));
      return false;
    }

    const options = this._composerMentionOptions;
    if (options.length === 0) return false;
    const option = options[this._clampComposerMentionSelectedIndex(this._composerMentionSelectedIndex)]!;
    const replacement = message_mentions.messageMentionInsertText(option);
    const nextText = _replaceRange(text, query.start, replaceEnd, replacement);
    const nextOffset = query.start + replacement.length;
    const nextValue = value.copyWith({
      text: nextText,
      selection: TextSelection.collapsed({ offset: nextOffset }),
      composing: TextRange.empty,
    });

    const previousUpdating = this._updatingComposerFromDraft;
    this._updatingComposerFromDraft = true;
    this._composerController.value = nextValue;
    this._updatingComposerFromDraft = previousUpdating;
    this._composerController.addConfirmedMention({ start: query.start, label: option.label });
    this._saveComposerDraftValue(nextText);
    this._setHomeState(() => this._setComposerMentionQuery(null));
    this._composerPanelController.requestInputFocus();
    return true;
  },

  _mentionUserFromAvatar(this: HomeShellState, user: UserSummary): void {
    const label = this._mentionLabelForUser(user);
    if (label.length === 0) return;
    const value = this._composerController.value;
    const needsLeadingSpace = value.text.length > 0 && !this._endsWithMentionBoundary(value.text);
    const prefix = needsLeadingSpace ? ' ' : '';
    const insertion = `${prefix}@${label} `;
    const selection = value.selection;
    const insertAt = selection.isValid ? selection.end : value.text.length;
    const nextText = _replaceRange(value.text, insertAt, insertAt, insertion);
    const mentionStart = insertAt + prefix.length;
    const nextOffset = insertAt + insertion.length;
    this._composerController.value = value.copyWith({
      text: nextText,
      selection: TextSelection.collapsed({ offset: nextOffset }),
      composing: TextRange.empty,
    });
    this._composerController.addConfirmedMention({ start: mentionStart, label });
    this._saveComposerDraftValue(nextText);
    this._composerPanelController.requestInputFocus();
  },

  _mentionLabelForUser(this: HomeShellState, user: UserSummary): string {
    const roomName = user.roomDisplayName?.trim();
    if (roomName != null && roomName.length > 0) return roomName;
    const displayName = user.displayName.trim();
    if (displayName.length > 0) return displayName;
    return user.username.trim();
  },

  _endsWithMentionBoundary(this: HomeShellState, text: string): boolean {
    if (text.length === 0) return true;
    const codeUnit = text.charCodeAt(text.length - 1);
    return codeUnit <= 0x20 || codeUnit === 0x3000;
  },

  _saveDraftInState(this: HomeShellState, opts: { text?: string } = {}): void {
    const roomId = this._selectedServerId;
    if (roomId == null) return;
    const draftText = opts.text ?? this._composerController.text;
    const hasAttachments = this._stagedAttachments.length > 0;
    const hasQuote = (this._messageQuoteDrafts.get(roomId) ?? []).length > 0;
    const hasComponent = this._messageComponentDrafts.has(roomId);
    const hasVisibleDraft = draftText.trim().length > 0 || hasAttachments || hasQuote || hasComponent;
    const current = this._messageDrafts.get(roomId);
    const currentAttachments = this._stagedAttachmentDrafts.get(roomId) ?? [];
    const sameText = current === draftText;
    const sameAttachments = this._sameStagedAttachments(currentAttachments, this._stagedAttachments);
    if (!hasVisibleDraft && current == null && currentAttachments.length === 0) {
      return;
    }
    if (hasVisibleDraft && sameText && sameAttachments) return;

    const nextText = new Map(this._messageDrafts);
    const nextAttachments = new Map(this._stagedAttachmentDrafts);
    if (hasVisibleDraft) {
      nextText.set(roomId, draftText);
      nextAttachments.set(roomId, [...this._stagedAttachments]);
    } else {
      nextText.delete(roomId);
      nextAttachments.delete(roomId);
    }
    this._messageDrafts = nextText;
    this._stagedAttachmentDrafts = nextAttachments;
  },

  _sameStagedAttachments(
    this: HomeShellState,
    a: readonly _StagedAttachment[],
    b: readonly _StagedAttachment[],
  ): boolean {
    if (a.length !== b.length) return false;
    for (let index = 0; index < a.length; index++) {
      if (a[index] !== b[index]) return false;
    }
    return true;
  },

  _discardRoomDraftInState(this: HomeShellState, roomId: string, opts: { cancelUploads?: boolean } = {}): void {
    const { cancelUploads = true } = opts;
    const attachments = this._stagedAttachmentDrafts.get(roomId) ?? [];
    if (cancelUploads) {
      for (const entry of attachments) {
        entry.uploadController.cancel();
      }
    }
    if (this._messageDrafts.has(roomId)) {
      const next = new Map(this._messageDrafts);
      next.delete(roomId);
      this._messageDrafts = next;
    }
    if (this._stagedAttachmentDrafts.has(roomId)) {
      const next = new Map(this._stagedAttachmentDrafts);
      next.delete(roomId);
      this._stagedAttachmentDrafts = next;
    }
    if (this._messageQuoteDrafts.has(roomId)) {
      const next = new Map(this._messageQuoteDrafts);
      next.delete(roomId);
      this._messageQuoteDrafts = next;
    }
    if (this._messageComponentDrafts.has(roomId)) {
      const next = new Map(this._messageComponentDrafts);
      next.delete(roomId);
      this._messageComponentDrafts = next;
    }
  },

  _cancelAllDraftAttachments(this: HomeShellState): void {
    const seen = new Set<_StagedAttachment>();
    for (const entry of this._stagedAttachments) {
      if (!seen.has(entry)) {
        seen.add(entry);
        entry.uploadController.cancel();
      }
    }
    for (const attachments of this._stagedAttachmentDrafts.values()) {
      for (const entry of attachments) {
        if (!seen.has(entry)) {
          seen.add(entry);
          entry.uploadController.cancel();
        }
      }
    }
  },

  _isStagedAttachmentActive(this: HomeShellState, entry: _StagedAttachment): boolean {
    if (this._stagedAttachments.includes(entry)) return true;
    for (const attachments of this._stagedAttachmentDrafts.values()) {
      if (attachments.includes(entry)) return true;
    }
    return false;
  },

  _sidebarRoomDrafts(this: HomeShellState): Map<string, string> {
    const roomIds = new Set<string>([
      ...this._messageDrafts.keys(),
      ...this._stagedAttachmentDrafts.keys(),
      ...this._messageQuoteDrafts.keys(),
      ...this._messageComponentDrafts.keys(),
    ]);
    const previews = new Map<string, string>();
    for (const roomId of roomIds) {
      const preview = this._roomDraftPreviewFor(roomId);
      if (preview != null) previews.set(roomId, preview);
    }
    return previews;
  },

  _roomDraftPreviewFor(this: HomeShellState, roomId: string): string | null {
    const attachments = this._stagedAttachmentDrafts.get(roomId) ?? [];
    const attachment = attachments.length === 0 ? null : attachments[0]!;
    const component = this._messageComponentDrafts.get(roomId);
    if (component != null) {
      return component.playlistAttachment != null
        ? `[歌单] ${component.playlistAttachment.playlist!.name}`
        : `[歌曲] ${component.musicTrackAttachment!.track!.title}`;
    }
    return room_display.roomDraftPreviewText({
      text: this._messageDrafts.get(roomId) ?? null,
      attachmentFilename: attachment?.file.name ?? null,
      attachmentMimeType: attachment?.file.mimeType ?? null,
      hasQuote: (this._messageQuoteDrafts.get(roomId) ?? []).length > 0,
    });
  },

  _chatMessageActions(this: HomeShellState): ChatMessageActions {
    return new ChatMessageActions({
      onCopy: (message: Message) => this._copyChatMessage(message),
      onQuote: (message: Message) => this._quoteChatMessage(message),
      onOpenQuote: (quote: MessageQuote) => this._openQuotedMessage(quote),
      onDeleteForMe: (message: Message) => this._deleteMessageForMe(message),
      onRecall: (message: Message) => this._recallChatMessage(message),
      canRecall: (message: Message) => this._canRecallChatMessage(message),
      canQuote: (message: Message) => this._canQuoteChatMessage(message),
      onReeditRecalledText: (message: Message) => this._reeditRecalledTextMessage(message),
      canReeditRecalledText: (message: Message) => this._canReeditRecalledTextMessage(message),
      canInspectRecalledText: (message: Message) => this._canInspectRecalledTextMessage(message),
      onViewSharedPlaylist: (message: Message, playlist: SharedMusicPlaylist) =>
        this._viewSharedPlaylistMessage(message, playlist),
      onEditOwnedSharedPlaylist: (message: Message, playlist: SharedMusicPlaylist) =>
        this._editOwnedSharedPlaylistMessage(message, playlist),
      sharedTrackPreviewController: this._sharedMessageTrackPreviewController,
      loadSharedTrackPlaylists: (roomId: string) => this._loadSharedTrackPlaylists(roomId),
      onAddSharedTrackToPlaylist: (track: SharedMusicTrack, target: MusicTrackPlaylistTarget) =>
        this._addSharedTrackToPlaylist(track, target),
    });
  },

  async _loadSharedTrackPlaylists(this: HomeShellState, roomId: string): Promise<MusicTrackPlaylistTarget[]> {
    const api = this._services.api;
    const targets: MusicTrackPlaylistTarget[] = [];
    if (_isPersonalMusicPlaylistApi(api)) {
      const page = await api.listPersonalMusicPlaylists({ pageSize: 50 });
      targets.push(...page.playlists.map((playlist) => MusicTrackPlaylistTarget.personal(playlist)));
    }
    if (_isRoomMusicPlaylistApi(api)) {
      const page = await api.listRoomMusicPlaylists({ roomId, pageSize: 50 });
      targets.push(...page.playlists.map((playlist) => MusicTrackPlaylistTarget.room({ playlist, roomId })));
    }
    return targets;
  },

  async _addSharedTrackToPlaylist(
    this: HomeShellState,
    track: SharedMusicTrack,
    target: MusicTrackPlaylistTarget,
  ): Promise<void> {
    const api = this._services.api;
    const searchResult = new MusicBoxSearchResult({
      trackId: track.trackId,
      name: track.title,
      artists: [...track.artists],
      source: track.source,
    });
    if (target.roomScoped) {
      const roomId = target.roomId;
      if (!_isRoomMusicPlaylistApi(api) || roomId == null || roomId.length === 0) {
        throw new _StateError('当前版本不支持添加到房间歌单');
      }
      await api.addRoomMusicPlaylistItem({
        roomId,
        playlistId: target.playlist.id,
        track: searchResult,
        durationMs: track.durationMs > 0 ? track.durationMs : null,
      });
      return;
    }
    if (!_isPersonalMusicPlaylistApi(api)) {
      throw new _StateError('当前版本不支持添加到个人歌单');
    }
    await api.addPersonalMusicPlaylistItem({
      playlistId: target.playlist.id,
      track: searchResult,
      durationMs: track.durationMs > 0 ? track.durationMs : null,
    });
  },

  async _viewSharedPlaylistMessage(this: HomeShellState, message: Message, playlist: SharedMusicPlaylist): Promise<void> {
    const api = this._services.api;
    if (!_isPersonalMusicPlaylistApi(api)) {
      if (this.mounted) {
        showFloatingErrorNotice('当前版本暂不支持查看歌单');
      }
      return;
    }
    const personalApi = api;
    const tracks = playlist.items.map(
      (item) =>
        new MusicTrackCardData({
          id: item.id,
          source: item.source,
          trackId: item.trackId,
          title: item.title,
          artists: item.artists,
          durationMs: item.durationMs,
        }),
    );
    await showDialog<void>(() => (
      <MusicPlaylistSnapshotDialog
        title={playlist.name}
        tracks={tracks}
        previewApi={_isMusicTrackPreviewApi(api) ? api : null}
        previewPlatformFactory={this.widget.musicTrackPreviewPlatformFactory}
        loadPersonalPlaylists={() => personalApi.listPersonalMusicPlaylists({ pageSize: 50 })}
        onAddToPlaylist={async (track, target) => {
          await personalApi.addPersonalMusicPlaylistItem({
            playlistId: target.id,
            track: new MusicBoxSearchResult({
              trackId: track.trackId,
              name: track.title,
              artists: [...track.artists],
              source: track.source,
            }),
            durationMs: track.durationMs > 0 ? track.durationMs : null,
          });
        }}
        onClone={
          _isSharedMusicPlaylistCloneApi(api)
            ? () =>
                api.cloneSharedMusicPlaylistToPersonal({
                  roomId: message.roomId,
                  messageId: message.id,
                })
            : null
        }
        contentKey={`shared-music-playlist-view-${playlist.id}`}
        trackListKey={`shared-music-playlist-tracks-${playlist.id}`}
        cloneButtonKey={`shared-music-playlist-clone-${playlist.id}`}
        doneButtonKey={`shared-music-playlist-done-${playlist.id}`}
        cloneErrorMessage={(error) => {
          if (!(error instanceof ApiException)) return `克隆歌单失败：${String(error)}`;
          switch (error.code) {
            case 'playlist_limit_reached':
              return '克隆失败：我的歌单已达 50 个上限';
            case 'not_found':
              return '克隆失败：该歌单消息已不可用';
            default:
              return `克隆歌单失败：${error.toString()}`;
          }
        }}
      />
    ));
  },

  async _editOwnedSharedPlaylistMessage(
    this: HomeShellState,
    message: Message,
    playlist: SharedMusicPlaylist,
  ): Promise<void> {
    if (playlist.creator.id !== this._currentUser.id) {
      await this._viewSharedPlaylistMessage(message, playlist);
      return;
    }
    this._openSettingsSection(SettingsSection.playlists, {
      openContent: true,
      initialMusicPlaylistId: playlist.id,
    });
  },

  _canQuoteChatMessage(this: HomeShellState, message: Message): boolean {
    return !message.pending && !message.failed && !message.isRemoved;
  },

  async _openQuotedMessage(this: HomeShellState, quote: MessageQuote): Promise<void> {
    const roomId = this._selectedServerId;
    if (roomId == null || this._locallyDeletedMessageKeys.has(quote.messageId)) {
      if (this.mounted) {
        showFloatingErrorNotice('原消息已被删除或撤回');
      }
      return;
    }

    let target: Message | null = this._messageById(this._messages, quote.messageId);
    if (target == null) {
      try {
        const loaded = await this._messagesController.loadMessagesUntil({
          roomId,
          messageId: quote.messageId,
        });
        if (!this.mounted || this._selectedServerId !== roomId) return;
        target = this._messageById(loaded, quote.messageId);
        if (target != null) {
          this._setHomeState(() => {
            this._messages = this._mergeMessages(this._messages, loaded);
          });
        }
      } catch (error) {
        if (this.mounted) {
          showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '无法跳转到原消息' }));
        }
        return;
      }
    }

    if (target == null || target.isRemoved || this._isMessageDeletedForMe(target)) {
      if (this.mounted) {
        showFloatingErrorNotice('原消息已被删除或撤回');
      }
      return;
    }
    this._setHomeState(() => {
      this._settingsOpen = false;
      this._contentMode = _ContentMode.chat;
      this._focusedMessageId = quote.messageId;
    });
  },

  _messageById(this: HomeShellState, messages: readonly Message[], messageId: string): Message | null {
    for (const message of messages) {
      if (message.id === messageId) return message;
    }
    return null;
  },

  _mergeMessages(this: HomeShellState, current: readonly Message[], loaded: readonly Message[]): Message[] {
    const byId = new Map<string, Message>();
    for (const message of loaded) {
      byId.set(message.id, message);
    }
    for (const message of current) {
      byId.set(message.id, message);
    }
    return [...byId.values()].sort((a, b) => {
      const byTime = a.createdAt.getTime() - b.createdAt.getTime();
      return byTime !== 0 ? byTime : _compareStrings(a.id, b.id);
    });
  },

  _visibleMessagesForMe(this: HomeShellState, messages: Message[]): Message[] {
    if (this._locallyDeletedMessageKeys.size === 0) return messages;
    return messages.filter((message) => !this._isMessageDeletedForMe(message));
  },

  _visibleNewMessageCount(this: HomeShellState, visibleMessages: readonly Message[]): number {
    const count = this._selectedRoomNewMessageCount;
    if (count <= 0) return 0;
    const firstUnreadIndex = this._messages.length - count;
    if (firstUnreadIndex <= 0) return visibleMessages.length;
    let visibleUnread = 0;
    for (let i = firstUnreadIndex; i < this._messages.length; i++) {
      if (!this._isMessageDeletedForMe(this._messages[i]!)) visibleUnread++;
    }
    return visibleUnread;
  },

  _isMessageDeletedForMe(this: HomeShellState, message: Message): boolean {
    return this._locallyDeletedMessageKeys.has(message.id) || this._locallyDeletedMessageKeys.has(message.clientMessageId);
  },

  async _copyChatMessage(this: HomeShellState, message: Message): Promise<void> {
    try {
      await this._copyChatMessageContents(message);
      if (!this.mounted) return;
      this._showChatMessageActionNotice('已复制', { tone: FloatingNoticeTone.success });
    } catch (error) {
      if (!this.mounted) return;
      this._showChatMessageActionNotice(userFacingErrorMessage(error, { fallback: '复制消息失败' }), {
        tone: FloatingNoticeTone.error,
      });
    }
  },

  async _copyChatMessageContents(this: HomeShellState, message: Message): Promise<void> {
    const isMusicComponent = message.playlistAttachment != null || message.musicTrackAttachment != null;
    if (isMusicComponent) {
      const text = message_display.messageComponentClipboardText(message);
      if (text.length === 0) {
        throw new _DartException('这条消息没有可复制的内容');
      }
      // Pending messages only carry a local client id and cannot be resolved
      // to the immutable server snapshot used by structured paste.
      this._copiedMessageComponent = message.pending ? null : message;
      try {
        await this._clipboardService.writeText(text);
      } catch (error) {
        if (this._copiedMessageComponent === message) {
          this._copiedMessageComponent = null;
        }
        throw error;
      }
      return;
    }
    this._copiedMessageComponent = null;
    const attachments = this._copyableMessageAttachments(message);
    if (attachments.length > 0) {
      await this._copyMessageAttachments(message, attachments);
      return;
    }
    const text = message_display.messageClipboardText(message);
    if (text.length === 0) {
      throw new _DartException('这条消息没有可复制的内容');
    }
    await this._clipboardService.writeText(text);
  },

  _copyableMessageAttachments(this: HomeShellState, message: Message): MessageAttachment[] {
    return message.attachments.filter(
      (attachment) =>
        attachment.asset != null &&
        (attachment.type === 'file' || attachment.type === 'sticker' || attachment.type === 'audio'),
    );
  },

  async _copyMessageAttachments(
    this: HomeShellState,
    message: Message,
    attachments: readonly MessageAttachment[],
  ): Promise<void> {
    const config = _homeAppConfig(this);
    if (attachments.length === 1) {
      const attachment = attachments[0]!;
      const asset = attachment.asset;
      const resolvedUrl = config.resolveAssetUrl(asset?.url);
      if (resolvedUrl != null && resolvedUrl.length > 0 && file_display.isImageMimeType(asset?.mimeType)) {
        try {
          await this._imagePreviewActions.onCopyToClipboard(resolvedUrl);
          return;
        } catch {
          // Fall through to file clipboard so platforms without image clipboard
          // support can still paste the sticker/image as a file.
        }
      }
    }

    const paths: string[] = [];
    const fallbackLines: string[] = [];
    const body = message.body.trimEnd();
    if (body.length > 0) fallbackLines.push(body);
    for (const attachment of attachments) {
      const asset = attachment.asset;
      const resolvedUrl = config.resolveAssetUrl(asset?.url);
      if (resolvedUrl == null || resolvedUrl.length === 0 || asset == null) {
        continue;
      }
      fallbackLines.push(resolvedUrl);
      const path = await this._downloadAttachmentToClipboardFile({
        url: resolvedUrl,
        title: this._attachmentClipboardFilename(attachment),
        asset,
      });
      if (path != null) paths.push(path);
    }

    if (paths.length > 0 && (await this._clipboardService.writeFilePaths(paths))) {
      return;
    }
    if (fallbackLines.length > 0) {
      await this._clipboardService.writeText(fallbackLines.join('\n'));
      return;
    }
    throw new _DartException('这条消息没有可复制的内容');
  },

  async _downloadAttachmentToClipboardFile(
    this: HomeShellState,
    opts: { url: string; title: string; asset: UploadedAsset },
  ): Promise<string | null> {
    const { url, title, asset } = opts;
    const files = platform().files;
    const baseDirectory = (await files.directories()).temporary;
    const directory = `${baseDirectory}${HostPlatform.pathSeparator}gang-chat-clipboard`;
    await files.ensureDirectory(directory);
    const filename = this._safeClipboardFilename(title);
    // PORT: Dart's `_uniqueDestinationPath` probed the disk synchronously;
    // the port's file bridge is async, so the result may be a promise.
    const path = await this._uniqueDestinationPath({ directory, filename });
    const request = MediaCacheRequest.tryFromUrl({
      url,
      filename,
      mimeType: asset.mimeType,
      expectedBytes: asset.sizeBytes,
      namespace: 'clipboard',
    });
    if (request != null) {
      const cached = await this._mediaCacheController.getOrDownload({ request });
      await cached.copy(path);
      return path;
    }

    // Uri.tryParse.
    try {
      new URL(url);
    } catch {
      return null;
    }
    // PORT: Dart opened (and closed) its own `http.Client`; the port uses the
    // shared host transport, which has nothing to close.
    const response = await new HttpClientHelpers(platform().http).get(url);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      return null;
    }
    await files.writeFile(path, response.bodyBytes);
    return path;
  },

  _attachmentClipboardFilename(this: HomeShellState, attachment: MessageAttachment): string {
    if (attachment.type === 'sticker') {
      return message_display.stickerPreviewFilename(attachment);
    }
    return file_display.fileAttachmentTitle(attachment);
  },

  _safeClipboardFilename(this: HomeShellState, value: string): string {
    const basename = file_display.basename(value);
    // eslint-disable-next-line no-control-regex
    const safe = basename.replace(/[\\/:*?"<>|\x00-\x1F]+/g, '_').replace(/_+/g, '_').trim();
    return safe.length === 0 || safe === '_' ? 'file' : safe;
  },

  async _deleteMessageForMe(this: HomeShellState, message: Message): Promise<void> {
    const confirmed = await showDialog<boolean>((close) => (
      <DialogFrame
        title="删除消息"
        icon={Icons.delete_outline}
        adaptiveActions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: () => close(false) }),
          new ResponsiveDialogAction({ label: '删除', tone: ButtonTone.danger, onPressed: () => close(true) }),
        ]}
      >
        <Text style={UiTypography.body}>这只会从你的聊天记录中删除，其他人仍然可以看到这条消息。</Text>
      </DialogFrame>
    ));
    if (confirmed !== true || !this.mounted) return;
    this._setHomeState(() => {
      this._locallyDeletedMessageKeys.add(message.id);
      this._locallyDeletedMessageKeys.add(message.clientMessageId);
    });
  },

  async _recallChatMessage(this: HomeShellState, message: Message): Promise<void> {
    if (!this._canRecallChatMessage(message)) return;
    const recallingOther = message.sender.id !== this._currentUser.id;
    if (recallingOther) {
      const confirmed = await showDialog<boolean>((close) => (
        <DialogFrame
          title="撤回消息"
          icon={Icons.undo_rounded}
          adaptiveActions={[
            new ResponsiveDialogAction({ label: '取消', onPressed: () => close(false) }),
            new ResponsiveDialogAction({ label: '撤回', tone: ButtonTone.danger, onPressed: () => close(true) }),
          ]}
        >
          <Text style={UiTypography.body}>{`确认撤回 ${room_display.userPrimaryName(message.sender)} 的这条消息？`}</Text>
        </DialogFrame>
      ));
      if (confirmed !== true) return;
    }

    try {
      const result = await this._messagesController.recallMessage({
        roomId: message.roomId,
        messageId: message.id,
      });
      if (!this.mounted) return;
      const recalled = result.message;
      if (recalled != null) {
        this._setHomeState(() => {
          this._messages = this._messagesController.replaceByMessageId(this._messages, recalled);
        });
        unawaited(this._loadServers());
        if (this.mounted) {
          this._showChatMessageActionNotice('已撤回', { tone: FloatingNoticeTone.success });
        }
        return;
      }
      if (result.isPending && this.mounted) {
        this._showChatMessageActionNotice('已提交撤回申请', { tone: FloatingNoticeTone.success });
      }
    } catch (error) {
      if (!this.mounted) return;
      this._showChatMessageActionNotice(userFacingErrorMessage(error, { fallback: '撤回消息失败' }), {
        tone: FloatingNoticeTone.error,
      });
    }
  },

  _canRecallChatMessage(this: HomeShellState, message: Message): boolean {
    if (message.pending || message.failed || message.isRemoved || message.type === message_display.kSystemMessageType) {
      return false;
    }
    if (message.sender.id === this._currentUser.id) return true;
    const room = this._selectedRoom;
    if (room == null || (!room.isAdmin && !this._currentUser.isSuperuser)) {
      return false;
    }
    return this._currentUserRoomRank(room) > this._messageSenderRoomRank(message.sender);
  },

  _reeditRecalledTextMessage(this: HomeShellState, message: Message): void {
    if (!this._canReeditRecalledTextMessage(message)) return;
    this._setComposerText(message.body, { saveDraft: true });
    this._composerPanelController.closePanel();
    if (this._contentMode !== _ContentMode.chat) {
      this._setHomeState(() => {
        this._contentMode = _ContentMode.chat;
      });
    }
  },

  _canReeditRecalledTextMessage(this: HomeShellState, message: Message): boolean {
    if (!message.isRecalled || message.isForceDeleted || message.type !== 'text' || message.body.length === 0) {
      return false;
    }
    if (message.sender.id !== this._currentUser.id) return false;
    const actor = message.recalledBy ?? message.sender;
    return actor.id === this._currentUser.id;
  },

  _canInspectRecalledTextMessage(this: HomeShellState, message: Message): boolean {
    if (!message.isRecalled || message.isForceDeleted || message.type !== 'text' || message.body.length === 0) {
      return false;
    }
    const actor = message.recalledBy ?? message.sender;
    if (message.sender.id === this._currentUser.id) {
      return actor.id !== this._currentUser.id;
    }
    const room = this._selectedRoom;
    if (room == null) return false;
    return this._currentUserRoomRank(room) > this._messageSenderRoomRank(message.sender);
  },

  _currentUserRoomRank(this: HomeShellState, room: RoomDetail): number {
    if (this._currentUser.isSuperuser) return 4;
    return this._roomRoleRank(room.myMembership.role);
  },

  _messageSenderRoomRank(this: HomeShellState, sender: UserSummary): number {
    if (sender.isSuperuser) return 4;
    return this._roomRoleRank(sender.roomRole);
  },

  _roomRoleRank(this: HomeShellState, role: string | null): number {
    switch ((role ?? '').toLowerCase()) {
      case 'owner':
      case 'creator':
        return 3;
      case 'admin':
      case 'administrator':
        return 2;
      case 'member':
        return 1;
      default:
        return 0;
    }
  },

  _showChatMessageActionNotice(
    this: HomeShellState,
    message: string,
    opts: { tone?: FloatingNoticeTone } = {},
  ): void {
    const { tone = FloatingNoticeTone.info } = opts;
    showFloatingNotice(message, { tone });
  },

  async _sendText(this: HomeShellState, value: string): Promise<void> {
    const body = value.trimEnd();
    const component = this._selectedComposerComponent();
    if (component != null) {
      const roomId = this._selectedServerId;
      const plan = composerMessageSendPlan({ body, component });
      await sendComposerMessagePartsSequentially({
        parts: plan,
        send: async (part, { hasFollowingPart }) => {
          const sent = await this._sendComposed({
            body: part.body,
            type: part.type,
            attachments: part.attachments,
            clearComposer: !hasFollowingPart,
            clearComponent: hasFollowingPart,
            includeQuotes: !hasFollowingPart,
            includeMentions: !hasFollowingPart,
          });
          return sent && this.mounted && this._selectedServerId === roomId;
        },
      });
      return;
    }
    // When files are staged, the message goes out as a file message carrying
    // them as attachments (the body rides along). Otherwise it's plain text.
    if (this._stagedAttachments.length > 0) {
      await this._sendStagedAttachments(body);
      return;
    }
    await this._sendComposed({
      body,
      type: 'text',
      attachments: [],
      clearComposer: true,
    });
  },

  async _sendSticker(this: HomeShellState, sticker: Sticker): Promise<void> {
    const draft = message_display.stickerMessageDraft(sticker);
    await this._sendComposed({
      body: draft.body,
      type: draft.type,
      attachments: draft.attachments,
      clearComposer: false,
    });
  },

  async _sendComposed(
    this: HomeShellState,
    opts: {
      body: string;
      type: string;
      attachments: MessageAttachment[];
      clearComposer: boolean;
      clearComponent?: boolean;
      includeQuotes?: boolean;
      includeMentions?: boolean;
    },
  ): Promise<boolean> {
    const { body, type, attachments, clearComposer, clearComponent = false, includeQuotes = true, includeMentions = true } = opts;
    const room = this._selectedRoom;
    if (room == null || this._sending) return false;
    if (!canSendComposedMessage({ body, type, attachments })) {
      return false;
    }

    const quotes: MessageQuote[] = includeQuotes ? [...(this._messageQuoteDrafts.get(room.id) ?? [])] : [];
    let clientMessageId: string | null = null;
    this._setHomeState(() => {
      this._sending = true;
      this._sendError = null;
    });

    try {
      const confirmedMentionLabels = this._composerController.confirmedMentionLabels();
      const sent = await this._messagesController.sendComposedMessage({
        roomId: room.id,
        sender: this._currentUser.toSummary().copyWith({
          roomDisplayName: this._selectedRoom?.personalProfile.displayName,
        }),
        body,
        type,
        attachments,
        mentions:
          includeMentions && this._composerMentionMembersRoomId === room.id && confirmedMentionLabels.length > 0
            ? message_mentions.messageMentionDescriptors({
                text: body,
                members: this._composerMentionMembers,
                confirmedLabels: confirmedMentionLabels,
              })
            : [],
        quotes,
        onPending: (pending) => {
          clientMessageId = pending.clientMessageId;
          if (!this.mounted) return;
          this._setHomeState(() => {
            this._messages = this._messagesController.patchPendingMessage({
              messages: this._messages,
              pending,
            });
            if (quotes.length > 0) {
              const next = new Map(this._messageQuoteDrafts);
              next.delete(room.id);
              this._messageQuoteDrafts = next;
            }
          });
          if (clearComposer || clearComponent) {
            this._setHomeState(() => {
              const next = new Map(this._messageComponentDrafts);
              next.delete(room.id);
              this._messageComponentDrafts = next;
            });
          }
          if (clearComposer) {
            this._setComposerText('', { saveDraft: true });
          } else if (clearComponent || quotes.length > 0) {
            this._saveComposerDraftValue(this._composerController.text);
          }
        },
      });
      if (!this.mounted || this._selectedServerId !== room.id) return true;
      this._setHomeState(() => {
        this._messages = this._messagesController.patchSentMessage({
          messages: this._messages,
          sent,
        });
      });
      unawaited(this._loadServers());
      return true;
    } catch (error) {
      if (this.mounted) {
        this._setHomeState(() => {
          this._sendError = userFacingErrorMessage(error);
          const failedClientMessageId = clientMessageId;
          if (failedClientMessageId != null) {
            this._messages = this._messagesController.patchFailedMessage({
              messages: this._messages,
              clientMessageId: failedClientMessageId,
            });
          }
        });
      }
      return false;
    } finally {
      if (this.mounted) {
        this._setHomeState(() => {
          this._sending = false;
        });
      }
    }
  },

  /**
   * Load the personal + room sticker packs that back the composer panel.
   * Personal packs are read from the on-disk cache first for an instant
   * render, then refreshed from the server alongside the room's packs.
   */
  async _loadStickerPacks(this: HomeShellState, opts: { forceReload?: boolean } = {}): Promise<void> {
    const { forceReload = false } = opts;
    const roomId = this._selectedServerId;
    if (roomId == null) return;
    if (!sticker_display.shouldLoadStickerPanel({ state: this._stickerPanelState, forceReload })) {
      return;
    }

    this._setHomeState(() => {
      this._stickerPanelState = sticker_display.stickerPanelLoadStarted(this._stickerPanelState);
    });

    try {
      const cachedPersonal = forceReload
        ? null
        : await this._stickerPacksController.readCachedPersonalPacks({ userId: this._currentUser.id });
      if (!this.mounted || this._selectedServerId !== roomId) return;
      if (cachedPersonal != null) {
        this._setHomeState(() => {
          this._stickerPanelState = sticker_display.stickerPanelCachedPersonalApplied({
            state: this._stickerPanelState,
            packs: cachedPersonal,
          });
        });
      }
      const shouldFetchPersonal = forceReload || cachedPersonal == null;
      const packs: StickerPack[][] = await Promise.all([
        shouldFetchPersonal
          ? this._stickerPacksController.loadPersonalPacks({ userId: this._currentUser.id, forceReload: true })
          : Promise.resolve(cachedPersonal!),
        this._stickerPacksController.loadRoomPacks(roomId),
      ]);
      if (!this.mounted || this._selectedServerId !== roomId) return;
      this._setHomeState(() => {
        this._stickerPanelState = sticker_display.stickerPanelLoadSucceeded({
          state: this._stickerPanelState,
          personalPacks: packs[0]!,
          roomPacks: packs[1]!,
        });
      });
    } catch (error) {
      if (!this.mounted || this._selectedServerId !== roomId) return;
      this._setHomeState(() => {
        this._stickerPanelState = sticker_display.stickerPanelLoadFailed({
          state: this._stickerPanelState,
          failure: error,
        });
      });
    } finally {
      if (this.mounted && this._selectedServerId === roomId) {
        this._setHomeState(() => {
          this._stickerPanelState = sticker_display.stickerPanelLoadFinished(this._stickerPanelState);
        });
      }
    }
  },

  _changeStickerSource(this: HomeShellState, source: sticker_display.StickerPanelSource): void {
    this._setHomeState(() => {
      this._stickerPanelState = sticker_display.stickerPanelSourceChanged(this._stickerPanelState, source);
    });
  },

  // --- Voice messages ---------------------------------------------------

  /**
   * Begin a click-to-record voice clip. Starts the recorder, then drives a
   * 1s ticker that updates the displayed duration and stops automatically at
   * the max length.
   */
  async _startVoiceRecording(this: HomeShellState): Promise<void> {
    if (!this._voiceState.isIdle || this._selectedRoom == null) return;
    try {
      await this._voiceRecorder.start();
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._voiceState = new voice_display.VoiceRecorderState().copyWith({
          error: userFacingErrorMessage(error),
        });
      });
      return;
    }
    if (!this.mounted) {
      unawaited(this._voiceRecorder.cancel());
      return;
    }
    this._voiceStartedAt = new Date();
    this._setHomeState(() => {
      this._voiceState = voice_display.voiceRecordingStarted();
    });
    this._voiceTicker?.cancel();
    this._voiceTicker = Timer.periodic(1000, () => {
      this._onVoiceTick();
    });
  },

  _onVoiceTick(this: HomeShellState): void {
    if (!this.mounted || !this._voiceState.isRecording) return;
    const startedAt = this._voiceStartedAt;
    if (startedAt == null) return;
    const elapsed = Date.now() - startedAt.getTime();
    this._setHomeState(() => {
      this._voiceState = voice_display.voiceRecordingTicked(this._voiceState, elapsed);
    });
    if (voice_display.voiceRecordingReachedLimit(elapsed)) {
      unawaited(this._stopVoiceRecording());
    }
  },

  /**
   * Stop recording and move to the review state where the user sends or
   * discards the clip.
   */
  async _stopVoiceRecording(this: HomeShellState): Promise<void> {
    if (!this._voiceState.isRecording) return;
    this._voiceTicker?.cancel();
    this._voiceTicker = null;
    const startedAt = this._voiceStartedAt;
    const elapsed = startedAt == null ? this._voiceState.elapsed : Date.now() - startedAt.getTime();
    this._voiceStartedAt = null;
    let path: string | null = null;
    try {
      path = await this._voiceRecorder.stop();
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._voiceState = new voice_display.VoiceRecorderState().copyWith({
          error: userFacingErrorMessage(error),
        });
      });
      return;
    }
    if (!this.mounted) return;
    this._setHomeState(() => {
      this._voiceState = voice_display.voiceRecordingStopped({
        state: this._voiceState,
        path,
        elapsed,
      });
    });
  },

  /** Discard the current recording or review clip and reset to idle. */
  async _cancelVoiceRecording(this: HomeShellState): Promise<void> {
    if (this._voiceState.isIdle) return;
    this._voiceTicker?.cancel();
    this._voiceTicker = null;
    this._voiceStartedAt = null;
    const wasRecording = this._voiceState.isRecording;
    const path = this._voiceState.recordingPath;
    this._setHomeState(() => {
      this._voiceState = voice_display.voiceRecordingCancelled();
    });
    try {
      if (wasRecording) {
        await this._voiceRecorder.cancel();
      } else {
        // Reviewed-but-discarded clip: drop the temp file.
        await this._voiceRecorder.discardClip(path);
      }
    } catch {
      // Cleanup failures are non-fatal; the state is already reset.
    }
  },

  /**
   * Upload and send the reviewed voice clip as a playable audio message,
   * reusing the shared file transfer pipeline (pending bubble + transfer).
   *
   * Once the clip's bytes are read and the pending bubble is created we reset
   * the recorder and retract the voice panel, so the in-flight clip lives in
   * the message list (with its own pending spinner) instead of trapping the
   * composer on the send screen. Delivery success/failure is then reflected on
   * the bubble, mirroring how text and file messages behave.
   */
  async _sendVoiceMessage(this: HomeShellState): Promise<void> {
    const room = this._selectedRoom;
    const path = this._voiceState.recordingPath;
    if (room == null || path == null || !this._voiceState.canSend) return;

    const duration = this._voiceState.elapsed;
    const quotes: MessageQuote[] = [...(this._messageQuoteDrafts.get(room.id) ?? [])];

    this._setHomeState(() => {
      this._voiceState = voice_display.voiceSendStarted(this._voiceState);
    });

    let bytes: Uint8Array;
    try {
      bytes = await this._voiceRecorder.readClip(path);
    } catch (error) {
      if (!this.mounted) return;
      // Read failed before any bubble existed — keep the review panel open so
      // the user can retry sending the same clip.
      this._setHomeState(() => {
        this._voiceState = voice_display.voiceSendFailed({ state: this._voiceState, failure: error });
      });
      return;
    }

    // Bytes are buffered in memory now; the clip file is no longer needed.
    unawaited(this._voiceRecorder.discardClip(path));

    const filename = voice_display.voiceMessageFilename(new Date(), {
      extension: this._voiceRecorder.clipExtension,
    });
    let clientMessageId: string | null = null;
    try {
      const sent = await this._messagesController.sendVoiceMessage({
        roomId: room.id,
        sender: this._currentUser.toSummary().copyWith({
          roomDisplayName: this._selectedRoom?.personalProfile.displayName,
        }),
        filename,
        sizeBytes: bytes.length,
        mimeType: this._voiceRecorder.clipMimeType,
        duration,
        quotes,
        readBytes: async () => bytes,
        onPending: (pending) => {
          clientMessageId = pending.clientMessageId;
          if (!this.mounted) return;
          this._setHomeState(() => {
            this._applyFileMessageStatePatch(
              this._messagesController.patchPendingFileMessage({
                messages: this._messages,
                fileTransfers: this._fileTransfers,
                pending,
              }),
            );
            // The clip now lives as a pending bubble; retract the recorder.
            this._voiceState = voice_display.voiceSendSucceeded();
            if (quotes.length > 0) {
              const next = new Map(this._messageQuoteDrafts);
              next.delete(room.id);
              this._messageQuoteDrafts = next;
              this._saveDraftInState();
            }
          });
          this._composerPanelController.closePanel();
        },
        onProgress: (pending, { sentBytes, totalBytes }) => {
          if (!this.mounted) return;
          const patch = this._messagesController.patchFileTransferProgress({
            messages: this._messages,
            fileTransfers: this._fileTransfers,
            pending,
            sentBytes,
            totalBytes,
          });
          if (patch == null) return;
          this._setHomeState(() => this._applyFileMessageStatePatch(patch));
        },
        onUploaded: (pending, attachment) => {
          if (!this.mounted) return;
          this._setHomeState(() =>
            this._applyFileMessageStatePatch(
              this._messagesController.patchUploadedFileMessage({
                messages: this._messages,
                fileTransfers: this._fileTransfers,
                pending,
                attachment,
              }),
            ),
          );
        },
      });
      if (!this.mounted || this._selectedServerId !== room.id) return;
      const activeClientMessageId = clientMessageId;
      if (activeClientMessageId != null) {
        this._setHomeState(() =>
          this._applyFileMessageStatePatch(
            this._messagesController.patchSentFileMessage({
              messages: this._messages,
              fileTransfers: this._fileTransfers,
              clientMessageId: activeClientMessageId,
              sent,
            }),
          ),
        );
      }
      unawaited(this._loadServers());
    } catch (error) {
      if (!this.mounted) return;
      const activeClientMessageId = clientMessageId;
      if (activeClientMessageId != null) {
        // The pending bubble owns the failure now — surface "发送失败" on it.
        this._setHomeState(() =>
          this._applyFileMessageStatePatch(
            this._messagesController.patchFailedFileMessage({
              messages: this._messages,
              fileTransfers: this._fileTransfers,
              clientMessageId: activeClientMessageId,
              failure: error,
            }),
          ),
        );
      } else {
        // Failed before the bubble existed — fall back to the review panel.
        this._setHomeState(() => {
          this._voiceState = voice_display.voiceSendFailed({ state: this._voiceState, failure: error });
        });
      }
    }
  },

  /**
   * Stop the in-progress recording and immediately send it. Mirrors the
   * "发送" control shown while recording.
   */
  async _finishAndSendVoice(this: HomeShellState): Promise<void> {
    if (this._voiceState.isRecording) {
      await this._stopVoiceRecording();
      if (!this.mounted) return;
    }
    if (this._voiceState.canSend) {
      await this._sendVoiceMessage();
    }
  },

  /** Apply a [FileMessageStatePatch] to the local message + transfer maps. */
  _applyFileMessageStatePatch(this: HomeShellState, patch: FileMessageStatePatch): void {
    this._messages = patch.messages;
    this._fileTransfers = patch.fileTransfers;
  },

  // --- Composer attachments ---------------------------------------------
  //
  // Files picked here upload immediately (eagerly), so by send time the assets
  // are usually already in hand and the message goes out in one round trip. The
  // message itself still rides the shared composed-send path as a `file` message
  // carrying the assets as attachments — a workaround until the backend offers a
  // single multipart "send message with files" call. Eager upload can leave an
  // orphan asset behind if the user removes a chip or never sends; that cleanup
  // is the backend's responsibility (asset TTL / unreferenced sweep).

  /** View models for the chips shown above the composer input. */
  _stagedAttachmentViews(this: HomeShellState): composer_attachment.ComposerAttachmentView[] {
    return this._stagedAttachments.map(
      (entry) =>
        new composer_attachment.ComposerAttachmentView({
          id: entry.id,
          filename: entry.file.name,
          status: entry.status,
          sizeBytes: entry.sizeBytes,
          mimeType: entry.file.mimeType,
          progress: entry.progress,
          errorMessage: entry.errorMessage,
        }),
    );
  },

  /**
   * Open the system file picker (multi-select), stage the chosen files on the
   * composer, and start uploading each one right away. Does not send anything;
   * the finished assets go out with the next message.
   */
  async _pickAttachments(this: HomeShellState): Promise<void> {
    if (this._selectedRoom == null || this._pickingAttachments) return;
    let files: SelectedFile[];
    this._setHomeState(() => {
      this._pickingAttachments = true;
      this._sendError = null;
    });
    try {
      files = await this._fileSelectionService.openFiles();
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._sendError = userFacingErrorMessage(error);
      });
      return;
    } finally {
      if (this.mounted) {
        this._setHomeState(() => {
          this._pickingAttachments = false;
        });
      }
    }
    if (!this.mounted || files.length === 0) return;

    this._stageAttachmentFiles(files);
  },

  /**
   * Stage any files/image on the clipboard as attachments. Returns true when
   * something was staged, so the paste handler can suppress the default text
   * paste — on macOS a copied file also exposes its name as plain text, which
   * would otherwise land in the composer.
   */
  async _canPasteAttachments(this: HomeShellState): Promise<boolean> {
    if (this._selectedRoom == null) return false;
    try {
      if ((await this._clipboardMessageComponent()) != null) return true;
      const paths = await this._clipboardService.readFilePaths();
      if (file_display.normalizedFilePaths(paths).length > 0) return true;
      const image = await this._clipboardService.readImageFile();
      return image != null;
    } catch {
      return false;
    }
  },

  async _pasteAttachments(this: HomeShellState): Promise<boolean> {
    if (this._selectedRoom == null) return false;
    try {
      const clipboardComponent = await this._clipboardMessageComponent();
      if (clipboardComponent != null) {
        if (!this.mounted) return false;
        return this._stageClipboardComponent(clipboardComponent);
      }
      const paths = await this._clipboardService.readFilePaths();
      if (!this.mounted) return false;
      const normalized = file_display.normalizedFilePaths(paths);
      if (normalized.length > 0) {
        this._stageAttachmentPaths(normalized);
        return true;
      }
      const image = await this._clipboardService.readImageFile();
      if (!this.mounted || image == null) return false;
      const filename = file_display.clipboardImageUploadFilename({
        timestamp: new Date(),
        sequence: ++this._clipboardImagePasteSerial,
        mimeType: image.mimeType,
      });
      this._stageAttachmentFiles([
        SelectedFile.fromBytes({
          name: filename,
          mimeType: image.mimeType,
          bytes: image.bytes,
        }),
      ]);
      return true;
    } catch (error) {
      if (!this.mounted) return false;
      this._setHomeState(() => {
        this._sendError = file_display.clipboardFilesReadFailureMessage(error);
      });
      return false;
    }
  },

  /**
   * Puts a pasted song or playlist into the composer. Returns false when no
   * room is selected; otherwise true, even when it only explains why the
   * component can't be added yet.
   */
  _stageClipboardComponent(this: HomeShellState, component: Message): boolean {
    if (this._composerController.text.trim().length > 0) {
      this._setHomeState(() => {
        this._sendError = '请先清空输入内容，再粘贴歌单或歌曲组件';
      });
      return true;
    }
    if (this._stagedAttachments.length > 0) {
      this._setHomeState(() => {
        this._sendError = '请先移除已选文件，再粘贴歌单或歌曲';
      });
      return true;
    }
    const roomId = this._selectedServerId;
    if (roomId == null) return false;
    this._setHomeState(() => {
      const next = new Map(this._messageComponentDrafts);
      next.set(roomId, component);
      this._messageComponentDrafts = next;
      this._sendError = null;
      this._saveDraftInState();
    });
    this._composerPanelController.closePanel();
    this._composerPanelController.requestInputFocus();
    return true;
  },

  async _clipboardMessageComponent(this: HomeShellState): Promise<Message | null> {
    return this._messageComponentFromClipboardText(await this._clipboardService.readText());
  },

  _messageComponentFromClipboardText(this: HomeShellState, clipboardText: string | null): Message | null {
    const embedded = message_display.messageComponentFromClipboardText(clipboardText);
    if (embedded != null) {
      this._copiedMessageComponent = embedded;
      return embedded;
    }
    const copied = this._copiedMessageComponent;
    if (copied != null && !copied.isRemoved && message_display.messageClipboardTextMatches(copied, clipboardText)) {
      return copied;
    }
    // Compatibility for text copied with an older Gang Chat build: if the
    // immutable source message is still loaded, recover the component from
    // its complete human-readable clipboard representation.
    for (let index = this._messages.length - 1; index >= 0; index--) {
      const message = this._messages[index]!;
      if (message.pending || message.isRemoved) continue;
      if (message.playlistAttachment == null && message.musicTrackAttachment == null) {
        continue;
      }
      if (message_display.messageClipboardTextMatches(message, clipboardText)) {
        this._copiedMessageComponent = message;
        return message;
      }
    }
    return null;
  },

  _handleDroppedFiles(this: HomeShellState, event: FileDropEvent): void {
    if (!this._composerContainsDropPoint(event.x, event.y)) return;
    if (event.files.length > 0) {
      this._stageAttachmentFiles(event.files);
      return;
    }
    this._stageAttachmentPaths(event.paths);
  },

  /**
   * Web paste: browsers insert pasted content straight into the focused text
   * field, so the composer's own paste action never runs. While the composer
   * has focus, pasted files become attachments and a copied Gang Chat song or
   * playlist becomes a component, exactly as a desktop paste would.
   */
  _handleWebPaste(this: HomeShellState, files: SelectedFile[], text: string | null): boolean {
    if (
      this._selectedRoom == null ||
      this._settingsOpen ||
      this._contentMode !== _ContentMode.chat ||
      !this._composerHasFocus()
    ) {
      return false;
    }
    if (files.length > 0) {
      this._stageAttachmentFiles(
        files.map((file) => {
          const mimeType = file.mimeType ?? '';
          // Pasted screenshots arrive as "image.png"; name them like a
          // desktop clipboard image so uploads stay distinguishable.
          if (!mimeType.startsWith('image/')) return file;
          return SelectedFile.deferred({
            name: file_display.clipboardImageUploadFilename({
              timestamp: new Date(),
              sequence: ++this._clipboardImagePasteSerial,
              mimeType,
            }),
            mimeType,
            length: 0,
            readAsBytes: () => file.readAsBytes(),
          });
        }),
      );
      return true;
    }
    const component = this._messageComponentFromClipboardText(text);
    if (component == null) return false;
    this._stageClipboardComponent(component);
    return true;
  },

  _composerHasFocus(this: HomeShellState): boolean {
    // PORT: Flutter walked the focused element's ancestors up to the
    // composer's GlobalKey context; the DOM asks whether the composer box
    // contains the active element.
    const composer = this._composerDropKey.current;
    const focused = typeof document === 'undefined' ? null : document.activeElement;
    if (composer == null || focused == null) return false;
    return composer === focused || composer.contains(focused);
  },

  _composerContainsDropPoint(this: HomeShellState, x: number, y: number): boolean {
    if (this._selectedRoom == null || this._settingsOpen || this._contentMode !== _ContentMode.chat) {
      return false;
    }
    const rect = rectInWindow(this._composerDropKey.current);
    if (rect == null) return false;
    return x >= rect.x && x < rect.x + rect.width && y >= rect.y && y < rect.y + rect.height;
  },

  _stageAttachmentPaths(this: HomeShellState, paths: Iterable<string>): void {
    const normalized = file_display.normalizedFilePaths(paths);
    if (normalized.length === 0) return;
    this._stageAttachmentFiles(this._fileSelectionService.filesFromPaths(normalized));
  },

  _stageAttachmentFiles(this: HomeShellState, files: readonly SelectedFile[]): void {
    if (this._selectedRoom == null || files.length === 0) return;
    const fresh: _StagedAttachment[] = [];
    this._setHomeState(() => {
      const roomId = this._selectedServerId;
      if (roomId != null && this._messageComponentDrafts.has(roomId)) {
        const next = new Map(this._messageComponentDrafts);
        next.delete(roomId);
        this._messageComponentDrafts = next;
      }
      for (const file of files) {
        const entry = new _StagedAttachment({
          id: this._messagesController.mintClientId('att'),
          file,
        });
        this._stagedAttachments.push(entry);
        fresh.push(entry);
      }
      this._saveDraftInState();
      this._sendError = null;
    });

    for (const entry of fresh) {
      unawaited(this._uploadStagedAttachment(entry));
    }
  },

  /**
   * Upload (or re-upload) a single staged file, streaming progress into its
   * chip. Safe to call again after a failure to retry.
   */
  async _uploadStagedAttachment(this: HomeShellState, entry: _StagedAttachment): Promise<void> {
    if (!this._isStagedAttachmentActive(entry)) return;
    this._setHomeState(() => {
      entry.status = composer_attachment.ComposerAttachmentStatus.uploading;
      entry.progress = null;
      entry.error = null;
      entry.errorMessage = null;
    });

    try {
      const bytes = await entry.file.readAsBytes();
      // Drop out if the chip was removed (or the room switched) while reading.
      if (!this._isStagedAttachmentActive(entry)) return;
      if (bytes.length === 0) throw new _StateError(file_display.fileEmptyMessage());
      const asset = await this._messagesController.uploadFileAsset({
        bytes,
        filename: entry.file.name,
        controller: entry.uploadController,
        onProgress: ({ sentBytes, totalBytes }) => {
          if (!this.mounted || !this._isStagedAttachmentActive(entry)) return;
          this._setHomeState(() => {
            entry.sizeBytes = totalBytes;
            entry.progress = totalBytes > 0 ? sentBytes / totalBytes : null;
          });
        },
      });
      if (!this._isStagedAttachmentActive(entry)) return;
      this._setHomeState(() => {
        entry.asset = asset;
        entry.sizeBytes = asset.sizeBytes;
        entry.progress = 1;
        entry.status = composer_attachment.ComposerAttachmentStatus.uploaded;
      });
    } catch (error) {
      // Cancellation means the chip was removed; nothing left to update.
      if (error instanceof UploadCancelledException) return;
      if (!this.mounted || !this._isStagedAttachmentActive(entry)) return;
      this._setHomeState(() => {
        entry.error = error;
        entry.errorMessage = this._stagedAttachmentErrorMessage(error);
        entry.status = composer_attachment.ComposerAttachmentStatus.failed;
      });
    }
  },

  /** Retry a staged upload that previously failed. */
  _retryAttachment(this: HomeShellState, id: string): void {
    const entry = this._stagedAttachmentById(id);
    if (entry == null || entry.isUploaded) return;
    unawaited(this._uploadStagedAttachment(entry));
  },

  /** Drop a single staged file, cancelling its upload if still in flight. */
  _removeAttachment(this: HomeShellState, id: string): void {
    const index = this._stagedAttachments.findIndex((entry) => entry.id === id);
    if (index < 0) return;
    const entry = this._stagedAttachments[index]!;
    entry.uploadController.cancel();
    this._setHomeState(() => {
      this._stagedAttachments.splice(index, 1);
      this._saveDraftInState();
    });
  },

  _stagedAttachmentById(this: HomeShellState, id: string): _StagedAttachment | null {
    for (const entry of this._stagedAttachments) {
      if (entry.id === id) return entry;
    }
    return null;
  },

  /**
   * Send the staged files as one composed message alongside any typed [body].
   * Uploads were kicked off at pick time, so this waits for any still in
   * flight, refuses to send while uploads are failed, then collects the
   * finished assets.
   */
  async _sendStagedAttachments(this: HomeShellState, body: string): Promise<void> {
    const room = this._selectedRoom;
    if (room == null || this._stagedAttachments.length === 0 || this._sending) return;

    // Wait for any uploads still in flight before deciding what to send.
    const pending = this._stagedAttachments.filter(
      (entry) => entry.status === composer_attachment.ComposerAttachmentStatus.uploading,
    );
    if (pending.length > 0) {
      this._setHomeState(() => {
        this._sending = true;
        this._sendError = null;
      });
      await Promise.all(pending.map((entry) => this._awaitUpload(entry)));
      if (!this.mounted || this._selectedServerId !== room.id) {
        if (this.mounted) {
          this._setHomeState(() => {
            this._sending = false;
          });
        }
        return;
      }
      this._setHomeState(() => {
        this._sending = false;
      });
    }

    // Refuse to send a partial batch; surface the failures for retry/removal.
    if (this._stagedAttachments.some((entry) => entry.status === composer_attachment.ComposerAttachmentStatus.failed)) {
      this._setHomeState(() => {
        this._sendError = '部分文件上传失败，请重试或移除后再发送';
      });
      return;
    }

    const attachments: MessageAttachment[] = [];
    for (const entry of this._stagedAttachments) {
      if (entry.asset != null) {
        attachments.push(this._messagesController.fileAttachment({ name: entry.file.name, asset: entry.asset }));
      }
    }
    if (attachments.length === 0) return;

    // Hand off to the shared composed send (which owns the pending/sent/failed
    // bubble and the _sending flag).
    this._setHomeState(() => {
      this._stagedAttachments.length = 0;
    });
    await this._sendComposed({
      body,
      type: 'file',
      attachments,
      clearComposer: true,
    });
  },

  /** Block until [entry]'s in-flight upload settles (success or failure). */
  async _awaitUpload(this: HomeShellState, entry: _StagedAttachment): Promise<void> {
    while (
      this.mounted &&
      this._stagedAttachments.includes(entry) &&
      entry.status === composer_attachment.ComposerAttachmentStatus.uploading
    ) {
      await delay(50);
    }
  },

  _stagedAttachmentErrorMessage(this: HomeShellState, error: unknown): string {
    if (error instanceof _StateError) return error.message;
    return userFacingErrorMessage(error, { fallback: '上传失败，请重试' });
  },
};
