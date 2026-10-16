import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import * as file_display from '@gang-chat/core/app/file_display';
import type { FileTransferState } from '@gang-chat/core/app/file_transfer_state';
import type { MediaCacheController } from '@gang-chat/core/app/media_cache_controller';
import * as message_display from '@gang-chat/core/app/message_display';
import type { MusicTrackPreviewController } from '@gang-chat/core/app/music_track_preview';
import * as voice_display from '@gang-chat/core/app/voice_message_display';
import type { Listenable } from '@gang-chat/core/lib/change_notifier';
import type {
  CurrentUser,
  Message,
  MessageAttachment,
  MessageQuote,
  PublicRoom,
  RoomMember,
  SharedMusicPlaylist,
  SharedMusicTrack,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { Expanded, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useElementSize } from '../lib/use_element_size';
import { useAppConfig } from '../ui/app_config_scope';
import { ButtonIcon } from '../ui/button';
import { BoxFit, CachedAssetImage } from '../ui/cached_asset_image';
import { UiContextMenuItem, UiContextMenuSection, showUiContextMenu, type WindowPoint } from '../ui/context_menu';
import { FloatingNoticeTone } from '../ui/feedback';
import { fileIconForMime } from '../ui/file_icons';
import { Icon, Icons, type IconData } from '../ui/icon';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiCurves, UiRadii, UiTypography, withAlpha } from '../ui/tokens';
import {
  _ignoreMessageSelectionChange,
  _messageContextMenuSections,
  _showMessageContextNotice,
  type ChatFileDownloadActions,
  type ChatMessageActions,
  type ChatSharedPlaylistCallback,
  type ChatVoicePlaybackActions,
} from './chat_messages_actions';
import { _TextBody } from './chat_messages_text';
import { showChatImagePreview, type ChatImagePreviewActions } from './chat_image_preview';
import { MusicPlaylistCardData, MusicPlaylistHoverCard } from './music_playlist_profile_card';
import { MusicTrackCardData, MusicTrackHoverCard, type MusicTrackPlaylistTarget } from './music_track_profile_card';
import type { RoomProfileResolver, UserProfileActionBuilder, UserProfileResolver } from './room_profile_card';

/**
 * Message bodies from `chat_messages.dart`: [ChatMessageContent] and the
 * quote, sticker, voice, file, playlist and song renderers it switches over.
 */

/**
 * Flutter `width: double.infinity` under loose constraints: fill the parent's
 * max width. The huge preferred width also makes shrink-wrapping ancestors
 * (the message bubble) grow to their max width, as Flutter's layout did,
 * because a percentage max-width does not cap intrinsic size contributions.
 */
const _fillMaxWidth: CSSProperties = { width: 99999, maxWidth: '100%' };

export interface ChatMessageContentProps {
  message: Message;
  outgoing: boolean;
  fileDownloads: Map<string, FileTransferState>;
  downloadActions: ChatFileDownloadActions;
  voicePlaybackActions: ChatVoicePlaybackActions;
  imagePreviewActions: ChatImagePreviewActions;
  currentUser: CurrentUser;
  currentUserMentionIdentity: UserSummary;
  ownerUserId: string | null;
  mentionMembers: readonly RoomMember[];
  transfer?: FileTransferState | null;
  mentionHighlighted?: boolean;
  onResolveSenderProfile?: ((sender: UserSummary) => Promise<UserSummary>) | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterProfileRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  isUserInLive?: ((userId: string) => boolean) | null;
  onSelectionActiveChanged?: ((active: boolean) => void) | null;
  textSelectionResetListenable?: Listenable | null;
  onOpenQuote?: ((quote: MessageQuote) => Promise<void>) | null;
  timestampNow?: Date | null;
  showDetailedTimestamps?: boolean;
  onViewSharedPlaylist?: ChatSharedPlaylistCallback | null;
  onEditOwnedSharedPlaylist?: ChatSharedPlaylistCallback | null;
  sharedTrackPreviewController?: MusicTrackPreviewController | null;
  loadSharedTrackPlaylists?: ((roomId: string) => Promise<MusicTrackPlaylistTarget[]>) | null;
  onAddSharedTrackToPlaylist?: ((track: SharedMusicTrack, target: MusicTrackPlaylistTarget) => Promise<void>) | null;
  /** DOM addition: outer box style (Dart `Key`s became `data-testid`). */
  'data-testid'?: string;
}

/**
 * Renders the exact contents used inside a normal chat message bubble without
 * adding the bubble background, border, padding, or message context menu.
 *
 * Message-history and other read-only surfaces use this widget so links,
 * mentions, stickers, voice messages, files, and image previews keep the same
 * behavior as the main chat instead of maintaining a second renderer.
 */
export function ChatMessageContent(props: ChatMessageContentProps) {
  const {
    message,
    outgoing,
    fileDownloads,
    downloadActions,
    voicePlaybackActions,
    imagePreviewActions,
    currentUser,
    currentUserMentionIdentity,
    ownerUserId,
    mentionMembers,
    transfer = null,
    mentionHighlighted = false,
    onResolveSenderProfile,
    onResolveRoomProfile,
    onEnterProfileRoom,
    profileActionBuilder,
    isUserInLive,
    onSelectionActiveChanged,
    textSelectionResetListenable,
    onOpenQuote,
    timestampNow = null,
    showDetailedTimestamps = false,
    onViewSharedPlaylist = null,
    onEditOwnedSharedPlaylist = null,
    sharedTrackPreviewController = null,
    loadSharedTrackPlaylists = null,
    onAddSharedTrackToPlaylist = null,
  } = props;
  const contentKind = message_display.messageContentKind(message);
  const status = message_display.messageDeliveryStatusText(message);
  const quotes = message.effectiveQuotes;

  let body: ReactNode;
  switch (contentKind) {
    case message_display.MessageContentKind.sticker:
      body = <_StickerBody message={message} attachment={message.stickerAttachment!} imagePreviewActions={imagePreviewActions} />;
      break;
    case message_display.MessageContentKind.voice:
      body = (
        <_VoiceBody
          message={message}
          attachment={voice_display.voiceMessageAttachment(message)!}
          playbackActions={voicePlaybackActions}
        />
      );
      break;
    case message_display.MessageContentKind.files:
      body = (
        <_FileBody
          message={message}
          outgoing={outgoing}
          transfer={transfer}
          fileDownloads={fileDownloads}
          downloadActions={downloadActions}
          imagePreviewActions={imagePreviewActions}
        />
      );
      break;
    case message_display.MessageContentKind.playlist:
      body = (
        <_PlaylistShareBody
          message={message}
          playlist={message.playlistAttachment!.playlist!}
          onView={onViewSharedPlaylist}
          onEditOwned={onEditOwnedSharedPlaylist}
          currentUser={currentUser}
          onResolveUserProfile={onResolveSenderProfile ?? null}
          onResolveRoomProfile={onResolveRoomProfile ?? null}
          onEnterCommonRoom={onEnterProfileRoom ?? null}
          userProfileActionBuilder={profileActionBuilder ?? null}
        />
      );
      break;
    case message_display.MessageContentKind.musicTrack:
      body = (
        <_MusicTrackShareBody
          roomId={message.roomId}
          track={message.musicTrackAttachment!.track!}
          previewController={sharedTrackPreviewController}
          loadPlaylists={loadSharedTrackPlaylists}
          onAddToPlaylist={onAddSharedTrackToPlaylist}
        />
      );
      break;
    case message_display.MessageContentKind.text:
      body = (
        <_TextBody
          message={message}
          profileCurrentUser={currentUser}
          currentUser={currentUserMentionIdentity}
          ownerUserId={ownerUserId}
          mentionMembers={mentionMembers}
          mentionHighlighted={mentionHighlighted}
          onResolveSenderProfile={onResolveSenderProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onEnterProfileRoom={onEnterProfileRoom}
          profileActionBuilder={profileActionBuilder}
          isUserInLive={isUserInLive}
          onSelectionActiveChanged={onSelectionActiveChanged ?? _ignoreMessageSelectionChange}
          selectionResetListenable={textSelectionResetListenable}
        />
      );
      break;
  }

  return (
    <div data-testid={props['data-testid']} style={_contentColumnStyle}>
      {quotes.map((quote, index) => (
        <div key={`${quote.messageId}:${index}`} style={_quoteSlotStyle}>
          <_MessageQuoteCard
            quote={quote}
            timestampNow={timestampNow}
            showDetailedTimestamps={showDetailedTimestamps}
            imagePreviewActions={imagePreviewActions}
            onTap={onOpenQuote == null ? null : () => void onOpenQuote(quote)}
          />
          <SizedBox height={index === quotes.length - 1 ? 8 : 6} />
        </div>
      ))}
      {body}
      {status != null ? (
        <>
          <SizedBox height={7} />
          <div style={_statusRowStyle}>
            {message.pending && !message.failed ? (
              <>
                <CircularProgressIndicator size={11} strokeWidth={1.6} color={UiColors.textMuted} />
                <SizedBox width={6} />
              </>
            ) : null}
            <Text style={{ ...UiTypography.label, color: message.failed ? UiColors.danger : UiColors.textMuted }}>{status}</Text>
          </div>
        </>
      ) : null}
    </div>
  );
}

const _contentColumnStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
  minWidth: 0,
  maxWidth: '100%',
};

const _quoteSlotStyle: CSSProperties = { ..._fillMaxWidth, display: 'flex', flexDirection: 'column' };

const _statusRowStyle: CSSProperties = { display: 'flex', flexDirection: 'row', alignItems: 'center', width: 'fit-content' };

// ---------------------------------------------------------------------------
// Shared music cards.

const _sharePanelStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'row',
  alignItems: 'center',
  boxSizing: 'border-box',
  // BoxConstraints(minWidth: 210, maxWidth: 360) around a full-width Row.
  width: 360,
  minWidth: 210,
  maxWidth: '100%',
  padding: 12,
  backgroundColor: UiColors.surfaceRaised,
  border: `1px solid ${UiColors.border}`,
  borderRadius: 8,
};

const _shareTextColumnStyle: CSSProperties = { alignItems: 'flex-start' };

export interface _MusicTrackShareBodyProps {
  roomId: string;
  track: SharedMusicTrack;
  previewController: MusicTrackPreviewController | null;
  loadPlaylists: ((roomId: string) => Promise<MusicTrackPlaylistTarget[]>) | null;
  onAddToPlaylist: ((track: SharedMusicTrack, target: MusicTrackPlaylistTarget) => Promise<void>) | null;
}

export function _MusicTrackShareBody({ roomId, track, previewController, loadPlaylists, onAddToPlaylist }: _MusicTrackShareBodyProps) {
  const data = new MusicTrackCardData({
    id: track.id,
    source: track.source,
    trackId: track.trackId,
    title: track.title,
    artists: track.artists,
    durationMs: track.durationMs,
  });
  const artists = track.artists
    .map((value) => value.trim())
    .filter((value) => value.length > 0)
    .join('、');
  const panel = (
    <div data-testid={`shared-music-track-message-${track.id}`} style={_sharePanelStyle}>
      <Icon icon={Icons.music_note} color={UiColors.accent} size={26} />
      <SizedBox width={10} />
      <Expanded style={_shareTextColumnStyle}>
        <_FadeOverflowText maxLines={4} style={{ ...UiTypography.body, color: UiColors.text, fontWeight: 700 }}>
          {track.title}
        </_FadeOverflowText>
        {artists.length > 0 ? (
          <>
            <SizedBox height={3} />
            <_FadeOverflowText maxLines={2} style={{ ...UiTypography.label, color: UiColors.textMuted }}>
              {artists}
            </_FadeOverflowText>
          </>
        ) : null}
      </Expanded>
    </div>
  );
  const preview = previewController;
  if (preview == null) return panel;
  return (
    <MusicTrackHoverCard
      data={data}
      previewController={preview}
      loadPlaylistTargets={loadPlaylists == null ? null : () => loadPlaylists(roomId)}
      playlistTargetsLoadKey={roomId}
      onAddToPlaylistTarget={onAddToPlaylist == null ? null : (target) => onAddToPlaylist(track, target)}
    >
      {panel}
    </MusicTrackHoverCard>
  );
}

export interface _PlaylistShareBodyProps {
  message: Message;
  playlist: SharedMusicPlaylist;
  onView: ChatSharedPlaylistCallback | null;
  onEditOwned: ChatSharedPlaylistCallback | null;
  currentUser: CurrentUser;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
}

export function _PlaylistShareBody({
  message,
  playlist,
  onView,
  onEditOwned,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  userProfileActionBuilder,
}: _PlaylistShareBodyProps) {
  const open = (): Promise<void> => {
    const edit = playlist.creator.id === currentUser.id ? onEditOwned : null;
    if (edit != null) return edit(message, playlist);
    const view = onView;
    return view == null ? Promise.resolve() : view(message, playlist);
  };

  const canEdit = playlist.creator.id === currentUser.id && onEditOwned != null;
  const cardData = new MusicPlaylistCardData({
    id: playlist.id,
    name: playlist.name,
    songCount: playlist.itemCount,
    createdAt: playlist.createdAt,
    creator: playlist.creator,
  });
  return (
    <MusicPlaylistHoverCard
      data={cardData}
      currentUser={currentUser}
      onResolveUserProfile={onResolveUserProfile}
      onResolveRoomProfile={onResolveRoomProfile}
      onEnterCommonRoom={onEnterCommonRoom}
      userProfileActionBuilder={userProfileActionBuilder}
      onPlayAll={null}
      showPrimaryActionWhenDisabled={false}
      onViewPlaylist={() => open()}
      secondaryActionLabel={canEdit ? '编辑歌单' : '查看歌单'}
      secondaryActionIcon={canEdit ? Icons.edit : Icons.queue_music}
    >
      <div data-testid={`shared-music-playlist-message-${playlist.id}`} style={_sharePanelStyle}>
        <Icon icon={Icons.queue_music} color={UiColors.accent} size={26} />
        <SizedBox width={10} />
        <Expanded style={_shareTextColumnStyle}>
          <Text style={{ ...UiTypography.body, color: UiColors.text, fontWeight: 700 }}>{playlist.name}</Text>
          <SizedBox height={3} />
          <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{`${playlist.itemCount} 首歌曲`}</Text>
        </Expanded>
        <SizedBox width={8} />
        <_MaterialIconButton
          testId={`shared-music-playlist-open-${playlist.id}`}
          tooltip={canEdit ? '编辑歌单' : '查看歌单'}
          onPressed={onView == null && !canEdit ? null : () => void open()}
          width={36}
          height={40}
          icon={Icons.chevron_right}
          iconSize={22}
          color={UiColors.textMuted}
        />
      </div>
    </MusicPlaylistHoverCard>
  );
}

// ---------------------------------------------------------------------------
// Quotes.

export interface _MessageQuoteCardProps {
  quote: MessageQuote;
  onTap?: (() => void) | null;
  onClose?: (() => void) | null;
  compact?: boolean;
  imagePreviewActions?: ChatImagePreviewActions | null;
  timestampNow?: Date | null;
  showDetailedTimestamps?: boolean;
}

export function _MessageQuoteCard({
  quote,
  onTap = null,
  onClose = null,
  compact = false,
  imagePreviewActions = null,
  timestampNow = null,
  showDetailedTimestamps = false,
}: _MessageQuoteCardProps) {
  const trimmedBody = quote.body.trim();
  const body = trimmedBody.length === 0 ? '[消息]' : trimmedBody;
  const hasThumbnail = quote.previewAttachment?.asset != null;
  const timestamp = showDetailedTimestamps
    ? message_display.formatDetailedChatTimestamp(quote.createdAt)
    : message_display.formatChatTimestamp(quote.createdAt, { now: timestampNow });
  const senderDisplayName = quote.senderDisplayName.trim();
  const header = senderDisplayName.length === 0 ? timestamp : `${senderDisplayName}  ${timestamp}`;
  const verticalPadding = compact ? 7 : 8;
  return (
    <div
      data-testid={`message-quote-${quote.messageId}`}
      onClick={
        onTap == null
          ? undefined
          : (event) => {
              if (event.button !== 0) return;
              onTap();
            }
      }
      style={{
        ..._fillMaxWidth,
        position: 'relative',
        boxSizing: 'border-box',
        padding: `${verticalPadding}px 6px ${verticalPadding}px 10px`,
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.md,
        border: `1px solid ${UiColors.border}`,
        cursor: onTap == null ? undefined : SystemMouseCursors.click,
      }}
    >
      <div style={{ position: 'relative' }}>
        <div
          data-testid={`message-quote-indicator-${quote.messageId}`}
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            bottom: 0,
            width: 3,
            backgroundColor: UiColors.textMuted,
            borderRadius: 999,
          }}
        />
        <div style={{ paddingLeft: 12, display: 'flex', flexDirection: 'row', alignItems: 'center' }}>
          <Expanded style={{ alignItems: 'flex-start' }}>
            <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textSecondary, fontWeight: 600, alignSelf: 'stretch' }}>
              {header}
            </Text>
            <SizedBox height={3} />
            {hasThumbnail ? (
              <_MessageQuoteThumbnail quote={quote} actions={imagePreviewActions} compact={compact} fallbackText={body} />
            ) : (
              <Text
                maxLines={compact ? 1 : 4}
                style={{ ...UiTypography.body, color: UiColors.text, fontSize: 12, lineHeight: 1.35, alignSelf: 'stretch' }}
              >
                {body}
              </Text>
            )}
          </Expanded>
          {onClose != null ? (
            <>
              <SizedBox width={6} />
              <_MaterialIconButton
                testId={`composer-quote-close-${quote.messageId}`}
                onPressed={onClose}
                icon={Icons.close}
                iconSize={16}
                color={UiColors.textMuted}
                tooltip="取消引用"
                width={28}
                height={28}
              />
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}

export interface _MessageQuoteThumbnailProps {
  quote: MessageQuote;
  actions: ChatImagePreviewActions | null;
  compact: boolean;
  fallbackText: string;
}

export function _MessageQuoteThumbnail({ quote, actions, compact, fallbackText }: _MessageQuoteThumbnailProps) {
  const config = useAppConfig();
  const attachment = quote.previewAttachment;
  const asset = attachment?.asset ?? null;
  if (attachment == null || asset == null) return null;
  const thumbnailUrl = config.resolveAssetUrl(asset.thumbnailUrl ?? asset.url);
  const fullUrl = config.resolveAssetUrl(asset.url);
  if (thumbnailUrl == null || thumbnailUrl.length === 0) {
    return (
      <Text maxLines={2} style={{ ...UiTypography.body, color: UiColors.text, fontSize: 12 }}>
        {fallbackText}
      </Text>
    );
  }
  const isSticker = attachment.type === 'sticker';
  const title = isSticker ? message_display.stickerPreviewFilename(attachment) : file_display.fileAttachmentTitle(attachment);
  const dimension = compact ? 44.0 : 64.0;
  const image = (
    <div style={{ borderRadius: UiRadii.sm, overflow: 'hidden', width: 'fit-content', flexShrink: 0 }}>
      <CachedAssetImage
        url={thumbnailUrl}
        filename={asset.filename ?? title}
        mimeType={asset.mimeType}
        expectedBytes={asset.sizeBytes}
        cache={actions?.mediaCache ?? null}
        width={dimension}
        height={dimension}
        fit={isSticker ? BoxFit.contain : BoxFit.cover}
        errorBuilder={() => (
          <SizedBox width={compact ? 120 : 180}>
            <Text maxLines={2} style={{ ...UiTypography.body, color: UiColors.text, fontSize: 12 }}>
              {fallbackText}
            </Text>
          </SizedBox>
        )}
      />
    </div>
  );
  const previewActions = actions;
  const previewUrl = fullUrl == null || fullUrl.length === 0 ? thumbnailUrl : fullUrl;
  if (previewActions == null) return image;
  return (
    <UiTooltip message="查看预览">
      <div
        data-testid={`message-quote-thumbnail-${quote.messageId}`}
        onClick={(event) => {
          if (event.button !== 0) return;
          // The thumbnail's tap wins over the quote card's (gesture arena).
          event.stopPropagation();
          void showChatImagePreview({ imageUrl: previewUrl, suggestedName: title, actions: previewActions });
        }}
        style={{ cursor: SystemMouseCursors.click, width: 'fit-content' }}
      >
        {image}
      </div>
    </UiTooltip>
  );
}

// ---------------------------------------------------------------------------
// Stickers.

export interface _StickerBodyProps {
  message: Message;
  attachment: MessageAttachment;
  imagePreviewActions: ChatImagePreviewActions;
}

export function _StickerBody({ message, attachment, imagePreviewActions }: _StickerBodyProps) {
  const config = useAppConfig();
  const asset = attachment.asset;
  const imageUrl = asset == null ? null : config.resolveAssetUrl(asset.thumbnailUrl ?? asset.url);
  // The preview shows the full-resolution sticker, not the thumbnail.
  const previewUrl = asset == null ? null : config.resolveAssetUrl(asset.url);
  const name = message_display.stickerAttachmentTitle(attachment);
  const image =
    imageUrl != null && imageUrl.length > 0 ? (
      <div style={{ borderRadius: UiRadii.md, overflow: 'hidden', width: 'fit-content' }}>
        <CachedAssetImage
          url={imageUrl}
          filename={asset?.filename ?? name}
          mimeType={asset?.mimeType}
          expectedBytes={asset?.sizeBytes}
          cache={imagePreviewActions.mediaCache}
          width={132}
          height={132}
          fit={BoxFit.contain}
          errorBuilder={() => <_StickerFallback name={name} />}
        />
      </div>
    ) : (
      <_StickerFallback name={name} />
    );

  const tappablePreviewUrl =
    previewUrl != null && previewUrl.length > 0 ? previewUrl : imageUrl != null && imageUrl.length > 0 ? imageUrl : null;

  const preview =
    tappablePreviewUrl == null ? (
      image
    ) : (
      <div
        onClick={(event) => {
          if (event.button !== 0) return;
          void showChatImagePreview({
            imageUrl: tappablePreviewUrl,
            suggestedName: message_display.stickerPreviewFilename(attachment),
            actions: imagePreviewActions,
            stickerSource: attachment.stickerId != null ? { message, attachment } : null,
          });
        }}
        style={{ cursor: SystemMouseCursors.click, width: 'fit-content' }}
      >
        {image}
      </div>
    );

  return (
    <UiTooltip message={name} waitDuration={350}>
      {preview}
    </UiTooltip>
  );
}

export function _showStickerContextMenu(opts: {
  position: WindowPoint;
  message: Message;
  attachment: MessageAttachment;
  imagePreviewActions: ChatImagePreviewActions;
  messageActions: ChatMessageActions;
}): Promise<void> {
  const { position, message, attachment, imagePreviewActions, messageActions } = opts;
  const stickerId = attachment.stickerId;

  const stickerItems: UiContextMenuItem[] = [];
  const onSaveSticker = imagePreviewActions.onSaveSticker;
  if (onSaveSticker != null) {
    stickerItems.push(
      new UiContextMenuItem({
        label: '添加到我的表情包',
        onPressed: () =>
          void _runStickerContextAction(message, attachment, onSaveSticker, { successMessage: '已添加到我的表情包' }),
      }),
    );
  }
  const onSaveRoomSticker = imagePreviewActions.onSaveRoomSticker;
  if (onSaveRoomSticker != null) {
    stickerItems.push(
      new UiContextMenuItem({
        label: '添加到房间表情包',
        onPressed: () =>
          void _runStickerContextAction(message, attachment, onSaveRoomSticker, { successMessage: '已添加到房间表情包' }),
      }),
    );
  }
  const sections: UiContextMenuSection[] = [
    ..._messageContextMenuSections({
      message,
      actions: messageActions,
      includeCopy: true,
      includeQuote: true,
      includeDelete: false,
      includeRecall: false,
    }),
    ...(stickerId != null && stickerId.length > 0 && stickerItems.length > 0 ? [new UiContextMenuSection(stickerItems)] : []),
    ..._messageContextMenuSections({
      message,
      actions: messageActions,
      includeCopy: false,
      includeQuote: false,
      includeDelete: false,
      includeRecall: true,
    }),
    ..._messageContextMenuSections({
      message,
      actions: messageActions,
      includeCopy: false,
      includeQuote: false,
      includeDelete: true,
      includeRecall: false,
    }),
  ];

  return showUiContextMenu({ position, sections });
}

export async function _runStickerContextAction(
  message: Message,
  attachment: MessageAttachment,
  action: (message: Message, attachment: MessageAttachment) => Promise<void>,
  opts: { successMessage: string },
): Promise<void> {
  // PORT: Dart checked `context.mounted` before each notice; notices are
  // app-global here.
  try {
    await action(message, attachment);
    _showMessageContextNotice(opts.successMessage, { tone: FloatingNoticeTone.success });
  } catch (error) {
    _showMessageContextNotice(userFacingErrorMessage(error, { fallback: '表情操作失败' }), { tone: FloatingNoticeTone.error });
  }
}

export function _StickerFallback({ name }: { name: string }) {
  return (
    <div
      style={{
        width: 132,
        height: 96,
        flexShrink: 0,
        boxSizing: 'border-box',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.md,
        // DecoratedBox border: painted inside, no layout space.
        boxShadow: `inset 0 0 0 1px ${UiColors.border}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Text maxLines={2} textAlign="center" style={UiTypography.label}>
        {name}
      </Text>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Voice messages.

const _voiceAccent = UiColors.white;

export interface _VoiceBodyProps {
  message: Message;
  attachment: MessageAttachment;
  playbackActions: ChatVoicePlaybackActions;
}

export function _VoiceBody({ message, attachment, playbackActions }: _VoiceBodyProps) {
  const resolvedUrl = useAppConfig().resolveAssetUrl(attachment.asset?.url);
  const playbackKey = message.clientMessageId;
  const playing = playbackActions.isPlaying(playbackKey);
  const canPlay = resolvedUrl != null && resolvedUrl.length > 0 && playbackActions.onToggle != null;
  const duration = voice_display.voiceAttachmentDuration(attachment);
  const durationText = voice_display.formatVoiceBubbleDuration(duration);
  const waveformWidth = voice_display.voiceWaveformWidth(duration);
  const playbackProgress = playbackActions.progressFor(playbackKey, { fallbackDuration: duration });

  const togglePlayback = () => {
    const url = resolvedUrl;
    const onToggle = playbackActions.onToggle;
    if (url == null || url.length === 0 || onToggle == null) return;
    onToggle(playbackKey, url);
  };

  return (
    <div
      data-testid="voice-body"
      role={canPlay ? 'button' : undefined}
      aria-label={playing ? '停止播放录音' : '播放录音'}
      onClick={
        canPlay
          ? (event) => {
              if (event.button !== 0) return;
              togglePlayback();
            }
          : undefined
      }
      style={{
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        width: 'fit-content',
        minWidth: 150,
        maxWidth: 304,
        cursor: canPlay ? SystemMouseCursors.click : undefined,
      }}
    >
      <SizedBox width={22} height={22}>
        <Icon
          icon={playing ? Icons.stop_rounded : Icons.play_arrow_rounded}
          color={canPlay ? _voiceAccent : UiColors.textMuted}
          size={22}
        />
      </SizedBox>
      <SizedBox width={8} />
      <_VoiceWaveform width={waveformWidth} progress={playbackProgress} />
      {durationText.length > 0 ? (
        <>
          <SizedBox width={12} />
          <Text maxLines={1} style={{ ...UiTypography.body, color: _voiceAccent, fontWeight: 500, flexShrink: 0 }}>
            {durationText}
          </Text>
        </>
      ) : null}
    </div>
  );
}

const _voiceWaveformHeights: readonly number[] = [8, 16, 22, 13, 18, 10, 24, 15, 9, 19, 12, 21, 7];

export function _VoiceWaveform({ width, progress }: { width: number; progress: number }) {
  const clamped = Math.min(Math.max(progress, 0), 1);
  return (
    <div data-testid="voice-waveform" style={{ position: 'relative', width, height: 28, flexShrink: 0 }}>
      <_VoiceWaveformBars color={withAlpha(_voiceAccent, 0.28)} width={width} />
      {/* _VoiceProgressClipper: reveal the bright bars up to the progress. */}
      <div style={{ position: 'absolute', inset: 0, clipPath: `inset(0 ${(1 - clamped) * 100}% 0 0)` }}>
        <_VoiceWaveformBars color={_voiceAccent} width={width} />
      </div>
    </div>
  );
}

export function _VoiceWaveformBars({ color, width }: { color: string; width: number }) {
  return (
    <div
      style={{
        width,
        height: 28,
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}
    >
      {_waveformHeightsForWidth(width).map((height, index) => (
        <div key={index} style={{ width: 3, height, flexShrink: 0, backgroundColor: color, borderRadius: 999 }} />
      ))}
    </div>
  );
}

export function _waveformHeightsForWidth(width: number): number[] {
  const count = Math.min(Math.max(Math.round(width / 6), 12), 28);
  const heights: number[] = [];
  for (let i = 0; i < count; i++) {
    heights.push(_voiceWaveformHeights[i % _voiceWaveformHeights.length]!);
  }
  return heights;
}

// ---------------------------------------------------------------------------
// File attachments.

export interface _FileBodyProps {
  message: Message;
  outgoing: boolean;
  transfer: FileTransferState | null;
  fileDownloads: Map<string, FileTransferState>;
  downloadActions: ChatFileDownloadActions;
  imagePreviewActions: ChatImagePreviewActions;
}

export function _FileBody({ message, outgoing, transfer, fileDownloads, downloadActions, imagePreviewActions }: _FileBodyProps) {
  const attachments = message.fileAttachments;
  const showBody = message_display.shouldShowFileAttachmentBody({ body: message.body, attachments });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', minWidth: 0, maxWidth: '100%' }}>
      {attachments.map((attachment, index) => (
        <div key={index} style={{ display: 'contents' }}>
          {index > 0 ? <SizedBox height={8} /> : null}
          <_FileAttachmentTile
            message={message}
            outgoing={outgoing}
            attachment={attachment}
            index={index}
            // The outgoing upload transfer only ever applies to the first
            // attachment; the slot helper resolves upload vs. download for us.
            slot={file_display.fileAttachmentTransferSlot({
              message,
              attachment,
              index,
              uploadTransfer: transfer,
              downloads: fileDownloads,
            })}
            downloadActions={downloadActions}
            imagePreviewActions={imagePreviewActions}
          />
        </div>
      ))}
      {showBody ? (
        <>
          <SizedBox height={8} />
          <Text style={UiTypography.body}>{message.body}</Text>
        </>
      ) : null}
    </div>
  );
}

export interface _FileAttachmentTileProps {
  message: Message;
  outgoing: boolean;
  attachment: MessageAttachment;
  index: number;
  slot: file_display.FileAttachmentTransferSlot;
  downloadActions: ChatFileDownloadActions;
  imagePreviewActions: ChatImagePreviewActions;
}

export function _FileAttachmentTile({
  message,
  outgoing,
  attachment,
  index,
  slot,
  downloadActions,
  imagePreviewActions,
}: _FileAttachmentTileProps) {
  // Resolve the asset URL here, at the widget layer, so the download
  // orchestration stays free of [AppConfig].
  const config = useAppConfig();
  const asset = attachment.asset;
  const transfer = slot.transfer;
  const title = file_display.fileAttachmentTitle(attachment);
  const meta = transfer == null ? file_display.fileAttachmentMeta(asset) : file_display.fileTransferProgressState(transfer).label;
  const progress = transfer == null ? null : file_display.fileTransferProgressState(transfer);
  const previewPath = file_display.fileAttachmentPreviewPath(asset);

  const resolvedUrl = config.resolveAssetUrl(asset?.url);
  const previewUrl = config.resolveAssetUrl(previewPath);
  const previewSize = file_display.fileAttachmentPreviewSize(asset);
  const interaction = file_display.fileAttachmentInteractionState({ title, url: resolvedUrl, transfer });
  const trailing = file_display.fileAttachmentTrailingState({ transfer, canDownload: interaction.canDownload });

  const startDownload = () => {
    const url = resolvedUrl;
    if (url == null) return;
    downloadActions.onDownload(message, attachment, index, url);
  };

  const tile = (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-start',
        boxSizing: 'border-box',
        width: 'fit-content',
        maxWidth: '100%',
        padding: 10,
        borderRadius: UiRadii.md,
        // No fill; the outline matches the surrounding bubble so it reads on
        // both the green outgoing bubble and the darker incoming one. A
        // DecoratedBox border takes no layout space.
        boxShadow: `inset 0 0 0 1px ${outgoing ? UiColors.accentBorder : UiColors.borderStrong}`,
      }}
    >
      {previewUrl != null && previewUrl.length > 0 ? (
        <>
          <_FileImagePreview
            url={previewUrl}
            title={title}
            width={previewSize.width}
            height={previewSize.height}
            mediaCache={imagePreviewActions.mediaCache}
            onTap={() => {
              const fullUrl = resolvedUrl != null && resolvedUrl.length > 0 ? resolvedUrl : previewUrl;
              void showChatImagePreview({ imageUrl: fullUrl, suggestedName: title, actions: imagePreviewActions });
            }}
          />
          <SizedBox height={10} />
        </>
      ) : null}
      <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', width: 'fit-content', maxWidth: '100%', minWidth: 0 }}>
        <Icon icon={fileIconForMime(asset?.mimeType)} color={UiColors.textSecondary} size={22} />
        <SizedBox width={10} />
        <div style={{ flex: '0 1 auto', minWidth: 0, display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
          <Text maxLines={1} style={{ ...UiTypography.body, fontWeight: 600, maxWidth: '100%' }}>
            {title}
          </Text>
          {meta.length > 0 ? (
            <>
              <SizedBox height={3} />
              <Text
                maxLines={1}
                style={{ ...UiTypography.label, color: progress?.failed === true ? UiColors.danger : UiColors.textMuted, maxWidth: '100%' }}
              >
                {meta}
              </Text>
            </>
          ) : null}
          {progress != null ? (
            <>
              <SizedBox height={8} />
              <SizedBox width={180}>
                <_LinearProgressIndicator
                  value={progress.value}
                  minHeight={3}
                  color={progress.failed ? UiColors.danger : UiColors.accent}
                  backgroundColor={UiColors.border}
                />
              </SizedBox>
            </>
          ) : null}
        </div>
        <SizedBox width={10} />
        <_FileAttachmentTrailing state={trailing} downloadKey={slot.downloadKey} actions={downloadActions} />
      </div>
    </div>
  );

  // Tapping an idle, downloadable tile starts the download. While a transfer
  // is in flight the trailing controls own the interaction instead.
  if (trailing.kind !== file_display.FileAttachmentTrailingKind.download) {
    return tile;
  }
  return (
    <UiTooltip message={title}>
      <div
        onClick={(event) => {
          if (event.button !== 0) return;
          startDownload();
        }}
        style={{ cursor: SystemMouseCursors.click, width: 'fit-content', maxWidth: '100%' }}
      >
        {tile}
      </div>
    </UiTooltip>
  );
}

export interface _FileImagePreviewProps {
  url: string;
  title: string;
  width: number;
  height: number;
  mediaCache?: MediaCacheController | null;
  onTap?: (() => void) | null;
}

export function _FileImagePreview({ url, title, width, height, mediaCache = null, onTap = null }: _FileImagePreviewProps) {
  const preview = (
    <div
      style={{
        position: 'relative',
        width,
        height,
        flexShrink: 0,
        boxSizing: 'border-box',
        borderRadius: UiRadii.sm,
        overflow: 'hidden',
        backgroundColor: UiColors.surfaceLow,
        // The DecoratedBox border paints behind the image, like an inset
        // shadow under in-flow content.
        boxShadow: `inset 0 0 0 1px ${UiColors.border}`,
      }}
    >
      <CachedAssetImage
        url={url}
        filename={title}
        cache={mediaCache}
        fit={BoxFit.contain}
        errorBuilder={() => (
          <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ padding: 12, minWidth: 0 }}>
              <Text maxLines={2} textAlign="center" style={{ ...UiTypography.label, color: UiColors.textMuted }}>
                {title}
              </Text>
            </div>
          </div>
        )}
      />
    </div>
  );

  if (onTap == null) return preview;
  return (
    <div
      onClick={(event) => {
        if (event.button !== 0) return;
        // The preview's tap wins over the tile's download tap (gesture arena).
        event.stopPropagation();
        onTap();
      }}
      style={{ cursor: SystemMouseCursors.click, width: 'fit-content' }}
    >
      {preview}
    </div>
  );
}

/**
 * Trailing controls for a file tile. Renders purely from
 * [file_display.FileAttachmentTrailingState]; the decision of which controls
 * to show lives in `file_display.ts`, so this widget only paints them.
 */
export function _FileAttachmentTrailing({
  state,
  downloadKey,
  actions,
}: {
  state: file_display.FileAttachmentTrailingState;
  downloadKey: string;
  actions: ChatFileDownloadActions;
}) {
  switch (state.kind) {
    // Idle/downloadable tiles carry no trailing control: tapping the tile
    // itself starts the download.
    case file_display.FileAttachmentTrailingKind.download:
      return null;
    case file_display.FileAttachmentTrailingKind.activeTransfer:
      return (
        <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', flexShrink: 0 }}>
          <ButtonIcon
            icon={state.pauseResumeIsResume ? Icons.play_arrow_rounded : Icons.pause_rounded}
            tooltip={state.pauseResumeTooltip ?? ''}
            size={32}
            onPressed={() => (state.pauseResumeIsResume ? actions.onResume(downloadKey) : actions.onPause(downloadKey))}
          />
          <SizedBox width={4} />
          <ButtonIcon
            icon={Icons.close_rounded}
            tooltip={state.cancelTooltip ?? ''}
            size={32}
            onPressed={() => actions.onCancel(downloadKey)}
          />
        </div>
      );
    case file_display.FileAttachmentTrailingKind.failed:
      return (
        <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', flexShrink: 0 }}>
          {state.showDismiss ? (
            <ButtonIcon icon={Icons.close_rounded} tooltip="清除" size={32} onPressed={() => actions.onDismiss(downloadKey)} />
          ) : null}
        </div>
      );
    case file_display.FileAttachmentTrailingKind.sending:
      return (
        <SizedBox width={18} height={18}>
          <CircularProgressIndicator size={18} strokeWidth={2} color={UiColors.accent} />
        </SizedBox>
      );
    case file_display.FileAttachmentTrailingKind.placeholder:
      return null;
  }
}

// ---------------------------------------------------------------------------
// Local stand-ins for Material widgets the kit does not port.

/**
 * Port of Material 3 `IconButton(padding: zero, constraints: …)` as these
 * cards use it: a fixed-size, stadium-shaped button with the M3 hover /
 * pressed state layer (`onSurfaceVariant` at 8% / 10%; the theme's
 * transparent `hoverColor` does not reach the M3 button style).
 *
 * PORT: a native `<button>` so an enclosing hover card's tap-to-pin skips
 * clicks on it, as Flutter's gesture arena let the inner button win.
 */
export function _MaterialIconButton({
  icon,
  iconSize,
  color,
  tooltip,
  onPressed,
  width,
  height,
  testId,
}: {
  icon: IconData;
  iconSize: number;
  color: string;
  tooltip?: string | null;
  onPressed: (() => void) | null;
  width: number;
  height: number;
  testId?: string;
}) {
  const [hovered, setHovered] = useState(false);
  const [pressed, setPressed] = useState(false);
  const enabled = onPressed != null;
  const overlay = !enabled
    ? UiColors.transparent
    : pressed
      ? withAlpha(UiColors.text, 0.1)
      : hovered
        ? withAlpha(UiColors.text, 0.08)
        : UiColors.transparent;
  const button = (
    <button
      type="button"
      data-testid={testId}
      aria-label={tooltip ?? undefined}
      disabled={!enabled}
      onPointerEnter={(event) => {
        if (event.pointerType === 'mouse') setHovered(true);
      }}
      onPointerLeave={() => {
        setHovered(false);
        setPressed(false);
      }}
      onPointerDown={(event) => {
        if (event.button === 0) setPressed(true);
      }}
      onPointerUp={() => setPressed(false)}
      onClick={(event) => {
        event.stopPropagation();
        if (event.button !== 0) return;
        onPressed?.();
      }}
      style={{
        width,
        height,
        flexShrink: 0,
        margin: 0,
        padding: 0,
        border: 'none',
        outline: 'none',
        borderRadius: Math.min(width, height) / 2,
        backgroundColor: overlay,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        transition: `background-color 100ms ${UiCurves.easeOut}`,
      }}
    >
      <Icon icon={icon} size={iconSize} color={color} />
    </button>
  );
  if (tooltip == null || tooltip.length === 0) return button;
  return <UiTooltip message={tooltip}>{button}</UiTooltip>;
}

/**
 * Port of Material's `LinearProgressIndicator(value:, minHeight:, color:,
 * backgroundColor:)`: a determinate bar, or the indeterminate two-line
 * animation when [value] is null (keyframes `gang-linear-progress-*` in
 * `ui/ui.css`).
 */
export function _LinearProgressIndicator({
  value,
  minHeight = 4,
  color = UiColors.accent,
  backgroundColor = UiColors.surfaceRaised,
}: {
  value: number | null;
  minHeight?: number;
  color?: string;
  backgroundColor?: string;
}) {
  const line = (animation: string): CSSProperties => ({
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
    right: '100%',
    backgroundColor: color,
    animation,
  });
  return (
    <div
      role="progressbar"
      aria-valuenow={value == null ? undefined : Math.round(value * 100)}
      style={{ position: 'relative', height: minHeight, width: '100%', flexShrink: 0, overflow: 'hidden', backgroundColor }}
    >
      {value != null ? (
        <div
          style={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            left: 0,
            width: `${Math.min(Math.max(value, 0), 1) * 100}%`,
            backgroundColor: color,
          }}
        />
      ) : (
        <>
          <div style={line('gang-linear-progress-line1-head 1800ms linear infinite, gang-linear-progress-line1-tail 1800ms linear infinite')} />
          <div style={line('gang-linear-progress-line2-head 1800ms linear infinite, gang-linear-progress-line2-tail 1800ms linear infinite')} />
        </>
      )}
    </div>
  );
}

/**
 * `Text(maxLines: n, overflow: TextOverflow.fade)`: clamps to [maxLines] and,
 * only when the text is cut, fades the last line out towards the bottom like
 * Flutter's vertical overflow shader.
 */
export function _FadeOverflowText({ children, maxLines, style }: { children: ReactNode; maxLines: number; style: CSSProperties }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const [overflowing, setOverflowing] = useState(false);
  const fontSize = typeof style.fontSize === 'number' ? style.fontSize : 14;
  const lineHeight = typeof style.lineHeight === 'number' ? style.lineHeight : 1.2;
  const lineHeightPx = fontSize * lineHeight;
  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const next = node.scrollHeight > node.clientHeight + 0.5;
    if (next !== overflowing) setOverflowing(next);
  });
  void size;
  const mask = overflowing ? `linear-gradient(to bottom, #000 calc(100% - ${lineHeightPx}px), transparent)` : undefined;
  return (
    <div
      ref={ref}
      style={{
        ...style,
        display: 'block',
        minWidth: 0,
        maxWidth: '100%',
        whiteSpace: 'pre-wrap',
        overflowWrap: 'break-word',
        overflow: 'hidden',
        maxHeight: lineHeightPx * maxLines,
        maskImage: mask,
        WebkitMaskImage: mask,
      }}
    >
      {children}
    </div>
  );
}
