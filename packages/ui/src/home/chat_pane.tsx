// Port of `chat_pane.dart`, the library root of the chat pane. Its Dart parts
// are separate modules: `chat_header`, `chat_messages`, `chat_composer_dock`
// and `chat_profile_card` (the part-private names they share are exported
// with their leading underscore).
//
// The parts import the constants below from this module. The import is
// circular, so parts must only read them inside functions (render time),
// never in their own top-level initializers.
import type { ComponentProps, RefObject } from 'react';

import type { ComposerAttachmentView } from '@gang-chat/core/app/composer_attachment_display';
import type { MessageMentionOption } from '@gang-chat/core/app/message_mentions';
import * as room_display from '@gang-chat/core/app/room_display';
import type { StickerPanelLoadState, StickerPanelSource } from '@gang-chat/core/app/sticker_display';
import type { VoiceRecorderState } from '@gang-chat/core/app/voice_message_display';
import type {
  CurrentUser,
  LiveState,
  Message,
  MessageQuote,
  PublicRoom,
  RoomCard,
  RoomDetail,
  Sticker,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { Column, Expanded } from '../lib/layout';
import type { TextEditingController, TextInputFormatter } from '../lib/text_editing';
import { kDefaultAvatarPresetKey } from '../ui/avatar';
import type { ChatComposerController, ComposerSuggestionAction, ComposerSuggestionNavigation } from '../ui/chat_composer';
import { UiColors, argb } from '../ui/tokens';
import { _ComposerDock } from './chat_composer_dock';
import { _RoomHeader } from './chat_header';
import type { ChatImagePreviewActions } from './chat_image_preview';
import { ChatMessageActions, _MessageStage, type ChatFileDownloadActions, type ChatVoicePlaybackActions } from './chat_messages';
import type { RoomProfileResolver, UserProfileActionBuilder } from './room_profile_card';

export * from './chat_image_preview';
export * from './chat_header';
export * from './chat_messages';
export * from './chat_composer_dock';
export * from './chat_profile_card';

export const _chatHeaderHeight = 111.0;
export const _chatHorizontalPadding = 18.0;
export const _chatFloatingEdgeInset = 14.0;
export const _chatHeaderHorizontalInset = _chatFloatingEdgeInset;
export const _chatHeaderVisualTopInset = _chatHeaderHorizontalInset;
export const _chatHeaderBottomInset = 16.0;
export const _liveHeaderCardHeight = 70.0;
export const _headerActionButtonSize = 31.0;
export const _headerActionGap = 8.0;
export const _messageMaxWidth = 560.0;
// Breathing room below the last message before the composer row begins.
export const _messageListBottomInset = 14.0;
export const _composerHorizontalInset = _chatFloatingEdgeInset;
export const _composerBottomInset = _chatFloatingEdgeInset;
export const _outgoingBubble = argb(0xff1f352b);
export const _incomingBubble = UiColors.surface;

/**
 * The props `_MessageStage` (chat_messages) declares. Collections the pane
 * only passes through take its declared types, so `Map` vs `ReadonlyMap`
 * (or `readonly` arrays) cannot drift between the two modules.
 */
type _StageProps = ComponentProps<typeof _MessageStage>;

export interface ChatPaneProps {
  currentUser: CurrentUser;
  timestampNow: Date;
  roomCard: RoomCard | null;
  room: RoomDetail | null;
  live: LiveState | null;
  messages: _StageProps['messages'];
  newMessageCount: number;
  focusMessageId?: string | null;
  onFocusMessageHandled?: ((messageId: string) => void) | null;
  fileTransfers: _StageProps['fileTransfers'];
  fileDownloads: _StageProps['fileDownloads'];
  downloadActions: ChatFileDownloadActions;
  voicePlaybackActions: ChatVoicePlaybackActions;
  imagePreviewActions: ChatImagePreviewActions;
  /** Defaults to `ChatMessageActions.disabled()`. */
  messageActions?: ChatMessageActions;
  loading: boolean;
  error: string | null;
  sending: boolean;
  sendError: string | null;
  hasPendingJoinRequests?: boolean;
  composerController: TextEditingController;
  composerPanelController: ChatComposerController;
  stickerPanel: StickerPanelLoadState;
  voiceState: VoiceRecorderState;
  composerAttachments: readonly ComposerAttachmentView[];
  composerQuotes?: readonly MessageQuote[];
  onRemoveComposerQuote?: ((messageId: string) => void) | null;
  composerComponent?: Message | null;
  onRemoveComposerComponent?: (() => void) | null;
  fileActionHighlighted: boolean;
  mentionOptions?: readonly MessageMentionOption[];
  mentionMembers?: _StageProps['mentionMembers'];
  mentionMembersReady?: boolean;
  mentionLoading?: boolean;
  mentionSelectedIndex?: number;
  onSelectMention?: ((option: MessageMentionOption) => void) | null;
  composerInputFormatters?: readonly TextInputFormatter[] | null;
  onNavigateMentionSelection?: ((navigation: ComposerSuggestionNavigation) => boolean) | null;
  onConfirmMentionSelection?: ((action: ComposerSuggestionAction) => boolean) | null;
  onHighlightMentionSelection?: ((index: number) => void) | null;
  onSubmit: (text: string) => void;
  onSendSticker: (sticker: Sticker) => void;
  onLoadStickers: () => void;
  onRefreshStickers: () => void;
  onStickerSourceChanged: (source: StickerPanelSource) => void;
  onUploadFirstPersonalSticker?: (() => void) | null;
  onUploadFirstRoomSticker?: (() => void) | null;
  onStartVoice: () => void;
  onSendVoice: () => void;
  onCancelVoice: () => void;
  onPickFile: () => void;
  onPasteFiles: () => Promise<boolean>;
  onCanPasteFiles?: (() => Promise<boolean>) | null;
  onRemoveAttachment: (id: string) => void;
  onRetryAttachment: (id: string) => void;
  onRetry: () => void;
  onNavigateBack?: (() => void) | null;
  onOpenLiveChannel: () => void;
  onOpenRoomMembers: () => void;
  onOpenRoomSettings: () => void;
  onViewedNewMessages: () => void;

  /**
   * Resolves a richer profile for a message sender on demand (gender, common
   * rooms). The hover card calls this lazily; when null it shows just the
   * lightweight summary carried by the message.
   */
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  senderProfileActionBuilder?: UserProfileActionBuilder | null;
  onMentionUser?: ((user: UserSummary) => void) | null;

  /**
   * Dart `composerDropKey` (a `GlobalKey`): the home shell measures the
   * composer through it to decide whether a file drop landed on it.
   */
  composerDropKey?: RefObject<HTMLDivElement | null> | null;
}

let _disabledMessageActions: ChatMessageActions | null = null;

function _defaultMessageActions(): ChatMessageActions {
  _disabledMessageActions ??= ChatMessageActions.disabled();
  return _disabledMessageActions;
}

export function ChatPane(props: ChatPaneProps) {
  const {
    currentUser,
    timestampNow,
    roomCard,
    room,
    live,
    hasPendingJoinRequests = false,
    composerQuotes = [],
    mentionOptions = [],
    mentionMembers = [],
    mentionMembersReady = true,
    mentionLoading = false,
    mentionSelectedIndex = 0,
  } = props;
  const title = _roomTitle(room, roomCard);
  const avatarLabel = _roomAvatarLabel(room, roomCard);
  const avatarUrl = room?.avatarUrl ?? roomCard?.avatarUrl ?? null;
  const defaultAvatarKey = room?.defaultAvatarKey ?? roomCard?.defaultAvatarKey ?? kDefaultAvatarPresetKey;
  const liveParticipantCount = live?.participantCount ?? room?.live.participantCount ?? roomCard?.liveParticipantCount ?? null;
  const liveAvatarPreview: readonly UserSummary[] =
    live?.participants.map((participant) => participant.user).slice(0, 5) ??
    room?.live.participants.map((participant) => participant.user).slice(0, 5) ??
    roomCard?.liveAvatarPreview ??
    [];
  const roomReady = room != null;
  const stageKey = `message-stage-${room?.id ?? roomCard?.id ?? 'none'}`;

  return (
    <Column crossAxisAlignment="stretch" style={{ height: '100%', width: '100%', backgroundColor: UiColors.background }}>
      <_RoomHeader
        onNavigateBack={props.onNavigateBack}
        title={title}
        avatarLabel={avatarLabel}
        avatarUrl={avatarUrl}
        defaultAvatarKey={defaultAvatarKey}
        memberCount={room?.memberCount ?? roomCard?.memberCount ?? null}
        onlineMemberCount={room?.onlineMemberCount ?? roomCard?.onlineMemberCount ?? null}
        liveParticipantCount={liveParticipantCount}
        liveAvatarPreview={liveAvatarPreview}
        hasPendingJoinRequests={hasPendingJoinRequests}
        onLivePressed={props.onOpenLiveChannel}
        onMembersPressed={props.onOpenRoomMembers}
        onSettingsPressed={props.onOpenRoomSettings}
      />
      <Expanded>
        <_MessageStage
          key={stageKey}
          roomId={room?.id ?? roomCard?.id ?? null}
          currentUser={currentUser}
          currentUserRoomDisplayName={_currentUserRoomDisplayName(currentUser, room)}
          currentUserRoomRole={room?.myMembership.role ?? null}
          ownerUserId={room?.createdBy?.id ?? null}
          roomReady={roomReady}
          loading={props.loading}
          error={props.error}
          timestampNow={timestampNow}
          messages={props.messages}
          newMessageCount={props.newMessageCount}
          focusMessageId={props.focusMessageId ?? null}
          onFocusMessageHandled={props.onFocusMessageHandled ?? undefined}
          mentionMembers={mentionMembers}
          mentionMembersReady={mentionMembersReady}
          fileTransfers={props.fileTransfers}
          fileDownloads={props.fileDownloads}
          live={live}
          downloadActions={props.downloadActions}
          voicePlaybackActions={props.voicePlaybackActions}
          imagePreviewActions={props.imagePreviewActions}
          messageActions={props.messageActions ?? _defaultMessageActions()}
          onRetry={props.onRetry}
          onViewedNewMessages={props.onViewedNewMessages}
          bottomInset={_messageListBottomInset}
          onResolveSenderProfile={props.onResolveSenderProfile ?? undefined}
          onResolveRoomProfile={props.onResolveRoomProfile ?? undefined}
          onEnterProfileRoom={props.onEnterProfileRoom ?? undefined}
          senderProfileActionBuilder={props.senderProfileActionBuilder ?? undefined}
          onMentionUser={props.onMentionUser ?? undefined}
        />
      </Expanded>
      {roomReady ? (
        // Dart wrapped the dock in `SelectionContainer.disabled`: the
        // composer's text field drives its own selection and its panels
        // (sticker grid, voice) are scrollable but not meant to be
        // selectable. DOM text outside the field is unselectable by default.
        <_ComposerDock
          controller={props.composerController}
          composerController={props.composerPanelController}
          timestampNow={timestampNow}
          sending={props.sending}
          sendError={props.sendError}
          stickerPanel={props.stickerPanel}
          voiceState={props.voiceState}
          attachments={props.composerAttachments}
          quotes={composerQuotes}
          onRemoveQuote={props.onRemoveComposerQuote ?? null}
          component={props.composerComponent ?? null}
          onRemoveComponent={props.onRemoveComposerComponent ?? null}
          imagePreviewActions={props.imagePreviewActions}
          fileActionHighlighted={props.fileActionHighlighted}
          mentionOptions={mentionOptions}
          mentionLoading={mentionLoading}
          mentionSelectedIndex={mentionSelectedIndex}
          onSelectMention={props.onSelectMention ?? null}
          inputFormatters={props.composerInputFormatters ?? null}
          onNavigateMentionSelection={props.onNavigateMentionSelection ?? null}
          onConfirmMentionSelection={props.onConfirmMentionSelection ?? null}
          onHighlightMentionSelection={props.onHighlightMentionSelection ?? null}
          onSubmit={props.onSubmit}
          onSendSticker={props.onSendSticker}
          onOpenStickers={props.onLoadStickers}
          onRefreshStickers={props.onRefreshStickers}
          onStickerSourceChanged={props.onStickerSourceChanged}
          onUploadFirstPersonalSticker={props.onUploadFirstPersonalSticker ?? null}
          onUploadFirstRoomSticker={props.onUploadFirstRoomSticker ?? null}
          onStartVoice={props.onStartVoice}
          onSendVoice={props.onSendVoice}
          onCancelVoice={props.onCancelVoice}
          onPickFile={props.onPickFile}
          onPasteFiles={props.onPasteFiles}
          onCanPasteFiles={props.onCanPasteFiles ?? null}
          onRemoveAttachment={props.onRemoveAttachment}
          onRetryAttachment={props.onRetryAttachment}
          dropKey={props.composerDropKey ?? null}
        />
      ) : null}
    </Column>
  );
}

export function _currentUserRoomDisplayName(currentUser: CurrentUser, room: RoomDetail | null): string {
  const roomName = room?.personalProfile.displayName?.trim();
  if (roomName != null && roomName.length > 0) return roomName;
  const displayName = currentUser.displayName.trim();
  if (displayName.length > 0) return displayName;
  return currentUser.username;
}

export function _roomTitle(room: RoomDetail | null, card: RoomCard | null): string {
  const detailRemark = room?.remarkName?.trim();
  if (detailRemark != null && detailRemark.length > 0) return detailRemark;
  const detailTitle = room?.name.trim();
  if (detailTitle != null && detailTitle.length > 0) return detailTitle;
  const cardTitle = card?.displayName.trim();
  if (cardTitle != null && cardTitle.length > 0) return cardTitle;
  return '聊天';
}

export function _roomAvatarLabel(room: RoomDetail | null, card: RoomCard | null): string {
  if (room != null) return room_display.roomAvatarLabel(room);
  if (card != null) return room_display.roomCardAvatarLabel(card);
  return '聊天';
}
