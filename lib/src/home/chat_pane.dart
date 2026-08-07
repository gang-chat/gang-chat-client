import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/gestures.dart'
    show
        GestureBinding,
        PointerCancelEvent,
        PointerDownEvent,
        PointerEvent,
        PointerUpEvent,
        TapGestureRecognizer,
        kPrimaryMouseButton,
        kSecondaryMouseButton,
        kTouchSlop;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart'
    show
        RenderAbstractViewport,
        RenderEditable,
        RenderObject,
        RenderProxyBox,
        ScrollCacheExtent;
import 'package:flutter/services.dart' show TextInputFormatter;

import '../app/file_display.dart' as file_display;
import '../app/error_display.dart';
import '../app/file_transfer_state.dart';
import '../app/media_cache_controller.dart';
import '../app/composer_attachment_display.dart' as composer_attachment;
import '../app/live_display.dart' as live_display;
import '../app/message_display.dart' as message_display;
import '../app/message_mentions.dart' as message_mentions;
import '../app/music_track_preview.dart';
import '../app/room_display.dart' as room_display;
import '../app/sticker_display.dart' as sticker_display;
import '../app/voice_message_display.dart' as voice_display;
import '../protocol/models.dart';
import '../shell/external_uri_launcher.dart';
import '../ui/ui.dart';
import 'adaptive_layout.dart';
import 'chat_image_preview.dart';
import 'hover_card_anchor.dart';
import 'music_playlist_profile_card.dart';
import 'music_track_profile_card.dart';
import 'room_profile_card.dart';

export 'chat_image_preview.dart';

part 'chat_header.dart';
part 'chat_messages.dart';
part 'chat_composer_dock.dart';
part 'chat_profile_card.dart';

const _chatHeaderHeight = 111.0;
const _chatHorizontalPadding = 18.0;
const _chatFloatingEdgeInset = 14.0;
const _chatHeaderHorizontalInset = _chatFloatingEdgeInset;
const _chatHeaderVisualTopInset = _chatHeaderHorizontalInset;
const _chatHeaderBottomInset = 16.0;
const _headerSurfaceHoverLift = 3.0;
const _headerSurfaceBaseDepth = 5.0;
const _liveHeaderCardHeight = 70.0;
const _headerActionButtonSize = 31.0;
const _headerActionGap = 0.0;
const _messageMaxWidth = 560.0;
// Breathing room below the last message before the composer row begins.
const _messageListBottomInset = 14.0;
const _composerHorizontalInset = _chatFloatingEdgeInset;
const _composerBottomInset = _chatFloatingEdgeInset;
const _outgoingBubble = Color(0xFF1F352B);
const _incomingBubble = UiColors.surface;

class ChatPane extends StatelessWidget {
  const ChatPane({
    super.key,
    required this.currentUser,
    required this.timestampNow,
    required this.roomCard,
    required this.room,
    required this.live,
    required this.messages,
    required this.newMessageCount,
    this.focusMessageId,
    this.onFocusMessageHandled,
    required this.fileTransfers,
    required this.fileDownloads,
    required this.downloadActions,
    required this.voicePlaybackActions,
    required this.imagePreviewActions,
    this.messageActions = const ChatMessageActions.disabled(),
    required this.loading,
    required this.error,
    required this.sending,
    required this.sendError,
    this.hasPendingJoinRequests = false,
    required this.composerController,
    required this.composerPanelController,
    required this.stickerPanel,
    required this.voiceState,
    required this.composerAttachments,
    this.composerQuotes = const [],
    this.onRemoveComposerQuote,
    this.composerComponent,
    this.onRemoveComposerComponent,
    required this.fileActionHighlighted,
    this.mentionOptions = const [],
    this.mentionMembers = const [],
    this.mentionMembersReady = true,
    this.mentionLoading = false,
    this.mentionSelectedIndex = 0,
    this.onSelectMention,
    this.composerInputFormatters,
    this.onNavigateMentionSelection,
    this.onConfirmMentionSelection,
    this.onHighlightMentionSelection,
    required this.onSubmit,
    required this.onSendSticker,
    required this.onLoadStickers,
    required this.onRefreshStickers,
    required this.onStickerSourceChanged,
    this.onUploadFirstPersonalSticker,
    this.onUploadFirstRoomSticker,
    required this.onStartVoice,
    required this.onSendVoice,
    required this.onCancelVoice,
    required this.onPickFile,
    required this.onPasteFiles,
    this.onCanPasteFiles,
    required this.onRemoveAttachment,
    required this.onRetryAttachment,
    required this.onRetry,
    this.onNavigateBack,
    required this.onOpenLiveChannel,
    required this.onOpenRoomMembers,
    required this.onOpenRoomSettings,
    required this.onViewedNewMessages,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.senderProfileActionBuilder,
    this.onMentionUser,
    this.composerDropKey,
  });

  final CurrentUser currentUser;
  final DateTime timestampNow;
  final RoomCard? roomCard;
  final RoomDetail? room;
  final LiveState? live;
  final List<Message> messages;
  final int newMessageCount;
  final String? focusMessageId;
  final ValueChanged<String>? onFocusMessageHandled;
  final Map<String, FileTransferState> fileTransfers;
  final Map<String, FileTransferState> fileDownloads;
  final ChatFileDownloadActions downloadActions;
  final ChatVoicePlaybackActions voicePlaybackActions;
  final ChatImagePreviewActions imagePreviewActions;
  final ChatMessageActions messageActions;
  final bool loading;
  final String? error;
  final bool sending;
  final String? sendError;
  final bool hasPendingJoinRequests;
  final TextEditingController composerController;
  final ChatComposerController composerPanelController;
  final sticker_display.StickerPanelLoadState stickerPanel;
  final voice_display.VoiceRecorderState voiceState;
  final List<composer_attachment.ComposerAttachmentView> composerAttachments;
  final List<MessageQuote> composerQuotes;
  final ValueChanged<String>? onRemoveComposerQuote;
  final Message? composerComponent;
  final VoidCallback? onRemoveComposerComponent;
  final bool fileActionHighlighted;
  final List<message_mentions.MessageMentionOption> mentionOptions;
  final List<RoomMember> mentionMembers;
  final bool mentionMembersReady;
  final bool mentionLoading;
  final int mentionSelectedIndex;
  final ValueChanged<message_mentions.MessageMentionOption>? onSelectMention;
  final List<TextInputFormatter>? composerInputFormatters;
  final bool Function(ComposerSuggestionNavigation navigation)?
  onNavigateMentionSelection;
  final bool Function(ComposerSuggestionAction action)?
  onConfirmMentionSelection;
  final ValueChanged<int>? onHighlightMentionSelection;
  final ValueChanged<String> onSubmit;
  final ValueChanged<Sticker> onSendSticker;
  final VoidCallback onLoadStickers;
  final VoidCallback onRefreshStickers;
  final ValueChanged<sticker_display.StickerPanelSource> onStickerSourceChanged;
  final VoidCallback? onUploadFirstPersonalSticker;
  final VoidCallback? onUploadFirstRoomSticker;
  final VoidCallback onStartVoice;
  final VoidCallback onSendVoice;
  final VoidCallback onCancelVoice;
  final VoidCallback onPickFile;
  final Future<bool> Function() onPasteFiles;
  final Future<bool> Function()? onCanPasteFiles;
  final ValueChanged<String> onRemoveAttachment;
  final ValueChanged<String> onRetryAttachment;
  final VoidCallback onRetry;
  final VoidCallback? onNavigateBack;
  final VoidCallback onOpenLiveChannel;
  final VoidCallback onOpenRoomMembers;
  final VoidCallback onOpenRoomSettings;
  final VoidCallback onViewedNewMessages;

  /// Resolves a richer profile for a message sender on demand (gender, common
  /// rooms). The hover card calls this lazily; when null it shows just the
  /// lightweight summary carried by the message.
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? senderProfileActionBuilder;
  final ValueChanged<UserSummary>? onMentionUser;
  final Key? composerDropKey;

  @override
  Widget build(BuildContext context) {
    final title = _roomTitle(room, roomCard);
    final avatarLabel = _roomAvatarLabel(room, roomCard);
    final avatarUrl = room?.avatarUrl ?? roomCard?.avatarUrl;
    final defaultAvatarKey =
        room?.defaultAvatarKey ??
        roomCard?.defaultAvatarKey ??
        kDefaultAvatarPresetKey;
    final liveParticipantCount =
        live?.participantCount ??
        room?.live.participantCount ??
        roomCard?.liveParticipantCount;
    final liveAvatarPreview =
        live?.participants
            .map((participant) => participant.user)
            .take(5)
            .toList() ??
        room?.live.participants
            .map((participant) => participant.user)
            .take(5)
            .toList() ??
        roomCard?.liveAvatarPreview ??
        const <UserSummary>[];
    final roomReady = room != null;
    final stageKey = ValueKey(
      'message-stage-${room?.id ?? roomCard?.id ?? 'none'}',
    );

    return ColoredBox(
      color: UiColors.background,
      child: Column(
        children: [
          _RoomHeader(
            onNavigateBack: onNavigateBack,
            title: title,
            avatarLabel: avatarLabel,
            avatarUrl: avatarUrl,
            defaultAvatarKey: defaultAvatarKey,
            memberCount: room?.memberCount ?? roomCard?.memberCount,
            onlineMemberCount:
                room?.onlineMemberCount ?? roomCard?.onlineMemberCount,
            liveParticipantCount: liveParticipantCount,
            liveAvatarPreview: liveAvatarPreview,
            hasPendingJoinRequests: hasPendingJoinRequests,
            onLivePressed: onOpenLiveChannel,
            onMembersPressed: onOpenRoomMembers,
            onSettingsPressed: onOpenRoomSettings,
          ),
          Expanded(
            child: _MessageStage(
              key: stageKey,
              roomId: room?.id ?? roomCard?.id,
              currentUser: currentUser,
              currentUserRoomDisplayName: _currentUserRoomDisplayName(
                currentUser,
                room,
              ),
              currentUserRoomRole: room?.myMembership.role,
              ownerUserId: room?.createdBy?.id,
              roomReady: roomReady,
              loading: loading,
              error: error,
              timestampNow: timestampNow,
              messages: messages,
              newMessageCount: newMessageCount,
              focusMessageId: focusMessageId,
              onFocusMessageHandled: onFocusMessageHandled,
              mentionMembers: mentionMembers,
              mentionMembersReady: mentionMembersReady,
              fileTransfers: fileTransfers,
              fileDownloads: fileDownloads,
              live: live,
              downloadActions: downloadActions,
              voicePlaybackActions: voicePlaybackActions,
              imagePreviewActions: imagePreviewActions,
              messageActions: messageActions,
              onRetry: onRetry,
              onViewedNewMessages: onViewedNewMessages,
              bottomInset: _messageListBottomInset,
              onResolveSenderProfile: onResolveSenderProfile,
              onResolveRoomProfile: onResolveRoomProfile,
              onEnterProfileRoom: onEnterProfileRoom,
              senderProfileActionBuilder: senderProfileActionBuilder,
              onMentionUser: onMentionUser,
            ),
          ),
          if (roomReady)
            SelectionContainer.disabled(
              // The composer's text field drives its own selection and its
              // panels (sticker grid, voice) are scrollable but not meant to
              // be selectable. Detach it from the app-wide SelectionArea so
              // showing/hiding a panel mid-selection can't trip the
              // scrollable-selection assertion.
              child: _ComposerDock(
                controller: composerController,
                composerController: composerPanelController,
                timestampNow: timestampNow,
                sending: sending,
                sendError: sendError,
                stickerPanel: stickerPanel,
                voiceState: voiceState,
                attachments: composerAttachments,
                quotes: composerQuotes,
                onRemoveQuote: onRemoveComposerQuote,
                component: composerComponent,
                onRemoveComponent: onRemoveComposerComponent,
                imagePreviewActions: imagePreviewActions,
                fileActionHighlighted: fileActionHighlighted,
                mentionOptions: mentionOptions,
                mentionLoading: mentionLoading,
                mentionSelectedIndex: mentionSelectedIndex,
                onSelectMention: onSelectMention,
                inputFormatters: composerInputFormatters,
                onNavigateMentionSelection: onNavigateMentionSelection,
                onConfirmMentionSelection: onConfirmMentionSelection,
                onHighlightMentionSelection: onHighlightMentionSelection,
                onSubmit: onSubmit,
                onSendSticker: onSendSticker,
                onOpenStickers: onLoadStickers,
                onRefreshStickers: onRefreshStickers,
                onStickerSourceChanged: onStickerSourceChanged,
                onUploadFirstPersonalSticker: onUploadFirstPersonalSticker,
                onUploadFirstRoomSticker: onUploadFirstRoomSticker,
                onStartVoice: onStartVoice,
                onSendVoice: onSendVoice,
                onCancelVoice: onCancelVoice,
                onPickFile: onPickFile,
                onPasteFiles: onPasteFiles,
                onCanPasteFiles: onCanPasteFiles,
                onRemoveAttachment: onRemoveAttachment,
                onRetryAttachment: onRetryAttachment,
                dropKey: composerDropKey,
              ),
            ),
        ],
      ),
    );
  }
}

String _currentUserRoomDisplayName(CurrentUser currentUser, RoomDetail? room) {
  final roomName = room?.personalProfile.displayName?.trim();
  if (roomName != null && roomName.isNotEmpty) return roomName;
  final displayName = currentUser.displayName.trim();
  if (displayName.isNotEmpty) return displayName;
  return currentUser.username;
}

String _roomTitle(RoomDetail? room, RoomCard? card) {
  final detailRemark = room?.remarkName?.trim();
  if (detailRemark != null && detailRemark.isNotEmpty) return detailRemark;
  final detailTitle = room?.name.trim();
  if (detailTitle != null && detailTitle.isNotEmpty) return detailTitle;
  final cardTitle = card?.displayName.trim();
  if (cardTitle != null && cardTitle.isNotEmpty) return cardTitle;
  return '聊天';
}

String _roomAvatarLabel(RoomDetail? room, RoomCard? card) {
  if (room != null) return room_display.roomAvatarLabel(room);
  if (card != null) return room_display.roomCardAvatarLabel(card);
  return '聊天';
}
