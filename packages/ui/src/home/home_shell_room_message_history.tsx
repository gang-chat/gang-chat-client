import { useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import type { FileTransferState } from '@gang-chat/core/app/file_transfer_state';
import * as live_display from '@gang-chat/core/app/live_display';
import * as message_display from '@gang-chat/core/app/message_display';
import type { MessagesController } from '@gang-chat/core/app/messages_controller';
import * as room_display from '@gang-chat/core/app/room_display';
import * as member_filter from '@gang-chat/core/app/room_members_filter';
import * as room_message_history from '@gang-chat/core/app/room_message_history';
import * as room_notifications from '@gang-chat/core/app/room_notifications';
import type { RoomsController } from '@gang-chat/core/app/rooms_controller';
import type { AppConfig } from '@gang-chat/core/config/app_config';
import { Timer, unawaited } from '@gang-chat/core/lib/async';
import type { MessagePage } from '@gang-chat/core/protocol/api_client';
import type { CurrentUser, Message, PublicRoom, RoomDetail, RoomMember, UserSummary } from '@gang-chat/core/protocol/models';
import type { ClipboardService } from '@gang-chat/core/shell/clipboard_service';

import { EdgeInsets } from '../lib/edge_insets';
import { Center, Column, Expanded, Positioned, Row, SizedBox, Stack, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { TextEditingController } from '../lib/text_editing';
import { useListenable } from '../lib/use_listenable';
import { ViewState, useViewState } from '../lib/view_state';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { UiCheckbox } from '../ui/checkbox';
import { UiContextMenuItem, UiContextMenuSection, UiContextMenuTriggerRegion, showUiContextMenu, type WindowPoint } from '../ui/context_menu';
import {
  DialogFrame,
  ResponsiveDialogAction,
  ResponsiveDialogActionBar,
  showFloatingErrorNotice,
  showFloatingSuccessNotice,
} from '../ui/feedback';
import { AdaptiveHighlightedText } from '../ui/highlighted_text';
import { Icon, Icons, type IconData } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog, useRoute } from '../ui/overlay';
import { RoleBadge } from '../ui/role_badge';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { UiColors, UiCurves, UiRadii, UiTypography } from '../ui/tokens';
import { HomeAdaptiveLayout } from './adaptive_layout';
import type { ChatImagePreviewActions } from './chat_image_preview';
import {
  Alignment,
  ChatMessageContent,
  ChatRemovedMessageContent,
  ChatSystemMessageContent,
  WrapAlignment,
  chatRoomUsernameColor,
  type ChatFileDownloadActions,
  type ChatMessageActions,
  type ChatVoicePlaybackActions,
} from './chat_messages';
import { CompactActivityLayout, CompactActivityTimestamp } from './compact_activity_layout';
import { showDateRangeFilterDialog } from './home_notifications';
import { UserHoverCard, type RoomProfileResolver, type UserProfileActionBuilder, type UserProfileResolver } from './room_profile_card';

type _RoomUserProfileResolver = (roomId: string, user: UserSummary) => Promise<UserSummary>;

// ---------------------------------------------------------------------------
// _RoomMessageHistoryPane

export interface _RoomMessageHistoryPaneProps {
  room: RoomDetail;
  currentUser: CurrentUser;
  roomsController: RoomsController;
  messagesController: MessagesController;
  clipboardService: ClipboardService;
  fileDownloads: Map<string, FileTransferState>;
  downloadActions: ChatFileDownloadActions;
  voicePlaybackActions: ChatVoicePlaybackActions;
  imagePreviewActions: ChatImagePreviewActions;
  messageActions: ChatMessageActions;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: _RoomUserProfileResolver | null;
  onOpenRoom: ((room: PublicRoom) => void) | null;
  profileActionBuilder: UserProfileActionBuilder | null;
  onJumpToMessage: (messageId: string) => void;
}

export class _RoomMessageHistoryPaneState extends ViewState<_RoomMessageHistoryPaneProps> {
  static readonly _pageSize = 50;

  constructor(initialProps: _RoomMessageHistoryPaneProps) {
    super();
    // PORT: Dart assigned the `late` date ranges in initState; ViewState runs
    // initState after the first render, which already reads them.
    this._defaultDateRange = _RoomMessageHistoryPaneState._makeDefaultDateRange(initialProps.currentUser);
    this._dateRange = this._defaultDateRange;
  }

  readonly _searchController = new TextEditingController();
  _searchDebounce: Timer | null = null;
  _defaultDateRange: room_notifications.RoomNotificationDateRange;
  _dateRange: room_notifications.RoomNotificationDateRange;
  _category: room_message_history.RoomMessageHistoryCategory = room_message_history.RoomMessageHistoryCategory.all;
  _members: RoomMember[] = [];
  _selectedMember: RoomMember | null = null;
  _messages: Message[] = [];
  _selectedMessageIds: Set<string> = new Set();
  _selectionMode = false;
  _loading = true;
  _loadingMore = false;
  _hasMore = false;
  _membersLoading = true;
  _nextBefore: string | null = null;
  _error: string | null = null;
  _loadSerial = 0;

  /** `AppConfigScope.of(context)`, published by the component each render. */
  _appConfig: AppConfig | null = null;

  override initState(): void {
    unawaited(this._loadMembers());
    unawaited(this._reload());
  }

  override dispose(): void {
    this._searchDebounce?.cancel();
    this._searchController.dispose();
  }

  static _makeDefaultDateRange(currentUser: CurrentUser): room_notifications.RoomNotificationDateRange {
    return room_notifications.RoomNotificationDateRange.defaultFor({
      accountCreatedAt: currentUser.createdAt,
      today: new Date(),
    });
  }

  async _loadMembers(): Promise<void> {
    try {
      const members = await this.widget.roomsController.loadAllRoomMembers(this.widget.room.id);
      if (!this.mounted) return;
      this.setState(() => {
        this._members = members;
        this._membersLoading = false;
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._membersLoading = false;
      });
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '加载房间成员失败' }));
    }
  }

  /** ScrollController listener: page in more once within 240px of the end. */
  _handleScroll = (node: HTMLElement): void => {
    const extentAfter = node.scrollHeight - node.clientHeight - node.scrollTop;
    if (extentAfter > 240) return;
    unawaited(this._loadMore());
  };

  _handleSearchChanged = (_value: string): void => {
    this._searchDebounce?.cancel();
    this._searchDebounce = new Timer(280, () => {
      if (!this.mounted) return;
      this._clearSelection();
      unawaited(this._reload());
    });
  };

  async _reload(): Promise<void> {
    const serial = ++this._loadSerial;
    this.setState(() => {
      this._loading = true;
      this._loadingMore = false;
      this._error = null;
      this._messages = [];
      this._hasMore = false;
      this._nextBefore = null;
    });
    try {
      const page = await this._requestPage();
      if (!this.mounted || serial !== this._loadSerial) return;
      this.setState(() => {
        this._messages = page.messages;
        this._hasMore = page.hasMore;
        this._nextBefore = page.nextBefore;
        this._loading = false;
      });
    } catch (error) {
      if (!this.mounted || serial !== this._loadSerial) return;
      this.setState(() => {
        this._loading = false;
        this._error = userFacingErrorMessage(error, { fallback: '加载消息记录失败' });
      });
    }
  }

  async _loadMore(): Promise<void> {
    if (this._loading || this._loadingMore || !this._hasMore || this._nextBefore == null) return;
    const serial = this._loadSerial;
    this.setState(() => {
      this._loadingMore = true;
    });
    try {
      const page = await this._requestPage({ before: this._nextBefore });
      if (!this.mounted || serial !== this._loadSerial) return;
      const knownIds = new Set(this._messages.map((message) => message.id));
      this.setState(() => {
        const appended: Message[] = [];
        for (const message of page.messages) {
          if (knownIds.has(message.id)) continue;
          knownIds.add(message.id);
          appended.push(message);
        }
        this._messages = [...this._messages, ...appended];
        this._hasMore = page.hasMore;
        this._nextBefore = page.nextBefore;
        this._loadingMore = false;
      });
    } catch (error) {
      if (!this.mounted || serial !== this._loadSerial) return;
      this.setState(() => {
        this._loadingMore = false;
      });
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '加载更多消息记录失败' }));
    }
  }

  _requestPage(opts: { before?: string | null } = {}): Promise<MessagePage> {
    return this.widget.messagesController.loadMessageHistory({
      roomId: this.widget.room.id,
      query: this._searchController.text,
      category: room_message_history.roomMessageHistoryCategoryApiValue(this._category),
      senderUserId: this._selectedMember?.user.id ?? null,
      startAt: room_message_history.roomMessageHistoryDayStart(this._dateRange.startDate),
      endAt: room_message_history.roomMessageHistoryDayEndExclusive(this._dateRange.endDate),
      limit: _RoomMessageHistoryPaneState._pageSize,
      before: opts.before ?? null,
    });
  }

  _showDateFilter = async (): Promise<void> => {
    const result = await showDateRangeFilterDialog({
      initialRange: this._dateRange,
      defaultRange: this._defaultDateRange,
      title: '筛选消息日期',
      description: '选择包含首尾日期的消息时间区间。',
    });
    if (!this.mounted || result == null || result.equals(this._dateRange)) return;
    this.setState(() => {
      this._dateRange = result;
    });
    this._clearSelection();
    await this._reload();
  };

  _showMemberFilter = async (): Promise<void> => {
    if (this._membersLoading) return;
    const widget = this.widget;
    const result = await showDialog<_HistoryMemberFilterResult>(() => (
      <_HistoryMemberFilterDialog
        roomId={widget.room.id}
        currentUser={widget.currentUser}
        members={this._members}
        selectedMember={this._selectedMember}
        onResolveRoomProfile={widget.onResolveRoomProfile}
        onResolveRoomUserProfile={widget.onResolveRoomUserProfile}
        onOpenRoom={widget.onOpenRoom}
      />
    ));
    if (!this.mounted || result == null) return;
    if ((result.member?.user.id ?? null) === (this._selectedMember?.user.id ?? null)) return;
    this.setState(() => {
      this._selectedMember = result.member;
    });
    this._clearSelection();
    await this._reload();
  };

  _changeCategory = (category: room_message_history.RoomMessageHistoryCategory): void => {
    if (this._category === category) return;
    this.setState(() => {
      this._category = category;
    });
    this._clearSelection();
    unawaited(this._reload());
  };

  _toggleSelectionMode = (): void => {
    this.setState(() => {
      this._selectionMode = !this._selectionMode;
      this._selectedMessageIds = new Set();
    });
  };

  _clearSelection(): void {
    if (this._selectedMessageIds.size === 0) return;
    this.setState(() => {
      this._selectedMessageIds = new Set();
    });
  }

  _toggleMessage(messageId: string): void {
    this.setState(() => {
      const ids = new Set(this._selectedMessageIds);
      if (ids.has(messageId)) {
        ids.delete(messageId);
      } else {
        ids.add(messageId);
      }
      this._selectedMessageIds = ids;
    });
  }

  _toggleAllMessages = (): void => {
    if (this._messages.length === 0) return;
    const visibleIds = this._messages.map((message) => message.id);
    this.setState(() => {
      if (visibleIds.every((id) => this._selectedMessageIds.has(id))) {
        const next = new Set(this._selectedMessageIds);
        for (const id of visibleIds) next.delete(id);
        this._selectedMessageIds = next;
      } else {
        this._selectedMessageIds = new Set([...this._selectedMessageIds, ...visibleIds]);
      }
    });
  };

  get _selectedMessages(): Message[] {
    return this._messages.filter((message) => this._selectedMessageIds.has(message.id));
  }

  get _currentUserMentionIdentity(): UserSummary {
    const widget = this.widget;
    const roomName = widget.room.personalProfile.displayName?.trim();
    return widget.currentUser.toSummary().copyWith({
      roomDisplayName: roomName != null && roomName.length > 0 ? roomName : widget.currentUser.displayName,
      roomRole: widget.room.myMembership.role,
    });
  }

  _resolveSenderProfile(): ((user: UserSummary) => Promise<UserSummary>) | null {
    const resolver = this.widget.onResolveRoomUserProfile;
    const roomId = this.widget.room.id;
    return resolver == null ? null : (user) => resolver(roomId, user);
  }

  _buildMessageContent(message: Message): { content: ReactNode; inline: boolean } {
    const widget = this.widget;
    const systemEvent = message_display.systemMessageEvent(message);
    const contentKey = `room-message-history-content-${message.id}`;
    if (systemEvent != null) {
      return {
        content: (
          <ChatSystemMessageContent
            data-testid={contentKey}
            message={message}
            event={systemEvent}
            currentUser={widget.currentUser}
            ownerUserId={widget.room.createdBy?.id ?? null}
            live={widget.room.live}
            enableContextMenu={false}
            showSurface={false}
            alignment={Alignment.centerLeft}
            wrapAlignment={WrapAlignment.start}
            textStyle={UiTypography.body}
            avatarSize={CompactActivityLayout.avatarSize}
            onResolveSenderProfile={this._resolveSenderProfile()}
            onResolveRoomProfile={widget.onResolveRoomProfile}
            onEnterProfileRoom={widget.onOpenRoom}
            profileActionBuilder={widget.profileActionBuilder}
          />
        ),
        inline: true,
      };
    }
    if (message.isRemoved) {
      return {
        content: (
          <ChatRemovedMessageContent
            data-testid={contentKey}
            message={message}
            currentUser={widget.currentUser}
            ownerUserId={widget.room.createdBy?.id ?? null}
            live={widget.room.live}
            messageActions={widget.messageActions}
            enableContextMenu={false}
            showSurface={false}
            alignment={Alignment.centerLeft}
            wrapAlignment={WrapAlignment.start}
            onResolveSenderProfile={this._resolveSenderProfile()}
            onResolveRoomProfile={widget.onResolveRoomProfile}
            onEnterProfileRoom={widget.onOpenRoom}
            profileActionBuilder={widget.profileActionBuilder}
          />
        ),
        inline: true,
      };
    }
    return {
      content: (
        <ChatMessageContent
          data-testid={contentKey}
          message={message}
          outgoing={message.sender.id === widget.currentUser.id}
          fileDownloads={widget.fileDownloads}
          downloadActions={widget.downloadActions}
          voicePlaybackActions={widget.voicePlaybackActions}
          imagePreviewActions={widget.imagePreviewActions}
          currentUser={widget.currentUser}
          currentUserMentionIdentity={this._currentUserMentionIdentity}
          ownerUserId={widget.room.createdBy?.id ?? null}
          mentionMembers={this._members}
          onResolveSenderProfile={this._resolveSenderProfile()}
          onResolveRoomProfile={widget.onResolveRoomProfile}
          onEnterProfileRoom={widget.onOpenRoom}
          profileActionBuilder={widget.profileActionBuilder}
          isUserInLive={(userId) => live_display.liveParticipantByUserId(widget.room.live, userId) != null}
          onOpenQuote={widget.messageActions.onOpenQuote}
          onViewSharedPlaylist={widget.messageActions.onViewSharedPlaylist}
          sharedTrackPreviewController={widget.messageActions.sharedTrackPreviewController}
          loadSharedTrackPlaylists={widget.messageActions.loadSharedTrackPlaylists}
          onAddSharedTrackToPlaylist={widget.messageActions.onAddSharedTrackToPlaylist}
        />
      ),
      inline: false,
    };
  }

  _copyMessage = async (message: Message): Promise<void> => {
    await this.widget.clipboardService.writeText(room_message_history.roomMessageHistoryCopyText(message));
    if (this.mounted) showFloatingSuccessNotice('已复制');
  };

  _deleteMessages = async (messages: readonly Message[]): Promise<void> => {
    if (messages.length === 0) return;
    const confirmed = await showDialog<boolean>((close) => (
      <_HistoryDeleteConfirmDialog messageCount={messages.length} onClose={close} />
    ));
    if (confirmed !== true || !this.mounted) return;
    try {
      await this.widget.messagesController.hideMessageHistory({
        roomId: this.widget.room.id,
        messageIds: messages.map((message) => message.id),
      });
      if (!this.mounted) return;
      const deletedIds = new Set(messages.map((message) => message.id));
      this.setState(() => {
        this._messages = this._messages.filter((message) => !deletedIds.has(message.id));
        const next = new Set(this._selectedMessageIds);
        for (const id of deletedIds) next.delete(id);
        this._selectedMessageIds = next;
      });
      showFloatingSuccessNotice(messages.length === 1 ? '已删除消息记录' : `已删除 ${messages.length} 条消息记录`);
    } catch (error) {
      if (this.mounted) {
        showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '删除消息记录失败' }));
      }
    }
  };

  build(): ReactNode {
    const allSelected =
      this._messages.length > 0 && this._messages.every((message) => this._selectedMessageIds.has(message.id));
    const selectedMember = this._selectedMember;
    return (
      <div
        style={{
          padding: '0 22px 22px 22px',
          boxSizing: 'border-box',
          flex: '1 1 0',
          minWidth: 0,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Row style={{ flexShrink: 0 }}>
          <Expanded data-testid="room-message-history-search">
            <Input
              controller={this._searchController}
              hintText="搜索消息记录"
              prefixIcon={Icons.search}
              showClearButton
              onChanged={this._handleSearchChanged}
            />
          </Expanded>
          <SizedBox width={10} />
          <ButtonIcon
            data-testid="room-message-history-date-filter"
            tooltip="筛选消息日期"
            icon={<Icon icon={Icons.calendar_month_outlined} />}
            selected={!this._dateRange.equals(this._defaultDateRange)}
            onPressed={() => unawaited(this._showDateFilter())}
            size={Input.defaultHeight}
          />
          <SizedBox width={8} />
          <ButtonIcon
            data-testid="room-message-history-member-filter"
            tooltip={
              selectedMember == null ? '筛选成员（所有人）' : `筛选成员：${member_filter.roomMemberDisplayName(selectedMember)}`
            }
            icon={<Icon icon={Icons.person_search_outlined} />}
            selected={selectedMember != null}
            loading={this._membersLoading}
            onPressed={this._membersLoading ? null : () => unawaited(this._showMemberFilter())}
            size={Input.defaultHeight}
          />
          <SizedBox width={8} />
          <ButtonIcon
            data-testid="room-message-history-batch-manage"
            tooltip={this._selectionMode ? '退出批量管理' : '批量管理'}
            icon={<Icon icon={Icons.checklist_outlined} />}
            selected={this._selectionMode}
            onPressed={this._toggleSelectionMode}
            size={Input.defaultHeight}
          />
        </Row>
        <SizedBox height={12} />
        <Row style={{ flexShrink: 0 }}>
          {this._selectionMode ? (
            <>
              <UiCheckbox
                value={allSelected}
                onChanged={this._messages.length === 0 ? null : () => this._toggleAllMessages()}
                tooltip={allSelected ? '取消全选当前消息记录' : '全选当前消息记录'}
                semanticLabel="全选当前消息记录"
              />
              <SizedBox width={8} />
            </>
          ) : null}
          <Expanded>
            <SegmentedControl<room_message_history.RoomMessageHistoryCategory>
              expanded
              value={this._category}
              onChanged={this._changeCategory}
              segments={_historyCategorySegments}
            />
          </Expanded>
        </Row>
        <SizedBox height={12} />
        <Expanded>{this._buildBody()}</Expanded>
      </div>
    );
  }

  _buildBody(): ReactNode {
    if (this._loading) {
      return (
        <Center style={{ flex: '1 1 0', minHeight: 0 }}>
          <CircularProgressIndicator />
        </Center>
      );
    }
    const error = this._error;
    if (error != null) {
      return (
        <Center style={{ flex: '1 1 0', minHeight: 0 }}>
          <Column mainAxisSize="min">
            <Text textAlign="center" style={{ ...UiTypography.body, color: UiColors.danger }}>
              {error}
            </Text>
            <SizedBox height={12} />
            <Button onPressed={() => unawaited(this._reload())} icon={<Icon icon={Icons.refresh} />}>
              重试
            </Button>
          </Column>
        </Center>
      );
    }
    if (this._messages.length === 0) {
      return (
        <Center style={{ flex: '1 1 0', minHeight: 0 }}>
          <Text style={{ ...UiTypography.body, color: UiColors.textMuted }}>没有符合条件的消息记录</Text>
        </Center>
      );
    }
    const widget = this.widget;
    const selectedMessages = this._selectedMessages;
    return (
      <div
        onScroll={(event) => this._handleScroll(event.currentTarget)}
        style={{
          flex: '1 1 0',
          minHeight: 0,
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
        }}
      >
        {this._messages.map((message) => {
          const renderedContent = this._buildMessageContent(message);
          return (
            <_HistoryMessageRow
              key={message.id}
              message={message}
              content={renderedContent.content}
              inlineContent={renderedContent.inline}
              senderAvatar={renderedContent.inline ? null : this._buildSenderAvatar(message)}
              senderColor={chatRoomUsernameColor({
                user: message.sender,
                currentUser: widget.currentUser,
                ownerUserId: widget.room.createdBy?.id ?? null,
              })}
              selectionMode={this._selectionMode}
              selected={this._selectedMessageIds.has(message.id)}
              selectedMessages={selectedMessages}
              onToggleSelection={() => this._toggleMessage(message.id)}
              onCopy={this._copyMessage}
              onDelete={this._deleteMessages}
              onJump={() => widget.onJumpToMessage(message.id)}
            />
          );
        })}
        {this._loadingMore ? (
          <div style={{ padding: 12, boxSizing: 'border-box', flexShrink: 0 }}>
            <Center>
              <CircularProgressIndicator />
            </Center>
          </div>
        ) : null}
      </div>
    );
  }

  _buildSenderAvatar(message: Message): ReactNode {
    const widget = this.widget;
    return (
      <UserHoverCard
        user={message.sender}
        currentUser={widget.currentUser}
        onResolveProfile={this._resolveSenderProfile()}
        onResolveRoomProfile={widget.onResolveRoomProfile}
        onEnterCommonRoom={widget.onOpenRoom}
        profileActionBuilder={widget.profileActionBuilder}
        inLive={live_display.liveParticipantByUserId(widget.room.live, message.sender.id) != null}
        showRoomRole
      >
        <div data-testid={`room-message-history-avatar-${message.id}`} style={{ display: 'flex', flexShrink: 0 }}>
          <Avatar
            label={room_display.userAvatarLabel(message.sender)}
            imageUrl={this._appConfig?.resolveAssetUrl(message.sender.avatarUrl) ?? null}
            defaultAvatarKey={message.sender.defaultAvatarKey}
            size={38}
          />
        </div>
      </UserHoverCard>
    );
  }
}

const _historyCategorySegments: readonly Segment<room_message_history.RoomMessageHistoryCategory>[] = [
  new Segment({ value: room_message_history.RoomMessageHistoryCategory.all, label: '全部', icon: Icons.inbox_outlined }),
  new Segment({ value: room_message_history.RoomMessageHistoryCategory.links, label: '链接', icon: Icons.link }),
  new Segment({ value: room_message_history.RoomMessageHistoryCategory.voice, label: '语音', icon: Icons.mic_none_outlined }),
  new Segment({
    value: room_message_history.RoomMessageHistoryCategory.stickers,
    label: '表情',
    icon: Icons.emoji_emotions_outlined,
  }),
  new Segment({ value: room_message_history.RoomMessageHistoryCategory.images, label: '图片', icon: Icons.image_outlined }),
  new Segment({ value: room_message_history.RoomMessageHistoryCategory.files, label: '文件', icon: Icons.attach_file }),
  new Segment({
    value: room_message_history.RoomMessageHistoryCategory.music,
    label: '音乐',
    icon: Icons.library_music_outlined,
  }),
  new Segment({ value: room_message_history.RoomMessageHistoryCategory.system, label: '系统', icon: Icons.info_outline }),
];

export function _RoomMessageHistoryPane(props: _RoomMessageHistoryPaneProps) {
  const state = useViewState(() => new _RoomMessageHistoryPaneState(props), props);
  state._appConfig = useAppConfig();
  return <>{state.build()}</>;
}

// ---------------------------------------------------------------------------
// _HistoryMessageRow

export interface _HistoryMessageRowProps {
  message: Message;
  content: ReactNode;
  inlineContent: boolean;
  senderAvatar: ReactNode | null;
  senderColor: string;
  selectionMode: boolean;
  selected: boolean;
  selectedMessages: readonly Message[];
  onToggleSelection: () => void;
  onCopy: (message: Message) => Promise<void>;
  onDelete: (messages: readonly Message[]) => Promise<void>;
  onJump: () => void;
}

export function _HistoryMessageRow(props: _HistoryMessageRowProps) {
  const { message, content, inlineContent, senderAvatar, senderColor, selectionMode, selected, onToggleSelection, onJump } = props;
  const [contextMenuActive, setContextMenuActive] = useState(false);
  const mountedRef = useRef(false);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);
  const propsRef = useRef(props);
  propsRef.current = props;

  const senderName = _historySenderName(message.sender);
  const highlighted = selected || contextMenuActive;
  const useCompactLayout = HomeAdaptiveLayout.usesCompactLayout();
  const timestampColumnWidth = useCompactLayout
    ? CompactActivityLayout.compactTimestampColumnWidth
    : CompactActivityLayout.timestampColumnWidth;
  const timestampContentGap = useCompactLayout ? CompactActivityLayout.compactTimestampContentGap : 8;
  const timestampStyle = { ...UiTypography.label, color: UiColors.textMuted };
  const timestamp = room_notifications.roomInviteTimestampLabel(message.createdAt);
  const interactionsKey = `room-message-history-content-interactions-${message.id}`;

  const showContextMenu = (position: WindowPoint) => {
    const current = propsRef.current;
    if (current.selectionMode && !current.selected) return;
    const items = current.selectionMode ? current.selectedMessages : [current.message];
    if (items.length === 0) return;
    setContextMenuActive(true);
    unawaited(
      showUiContextMenu({
        position,
        sections: [
          ...(items.length === 1
            ? [
                new UiContextMenuSection([
                  new UiContextMenuItem({
                    label: '复制',
                    shortcut: 'Ctrl+C',
                    onPressed: () => unawaited(current.onCopy(items[0]!)),
                  }),
                ]),
              ]
            : []),
          new UiContextMenuSection([
            new UiContextMenuItem({
              label: '删除',
              onPressed: () => unawaited(current.onDelete(items)),
            }),
          ]),
        ],
      }).finally(() => {
        if (mountedRef.current) setContextMenuActive(false);
      }),
    );
  };

  const transition = `background-color 90ms ${UiCurves.easeOutCubic}, border-color 90ms ${UiCurves.easeOutCubic}`;
  const surface = (
    <div
      data-testid={`room-message-history-row-${message.id}`}
      style={{
        minHeight: 64,
        padding: '10px 12px',
        boxSizing: 'border-box',
        backgroundColor: highlighted ? UiColors.selected : UiColors.surface,
        borderRadius: UiRadii.lg,
        border: `1px solid ${highlighted ? UiColors.selectedBorder : UiColors.border}`,
        transition,
      }}
    >
      <Stack>
        <Row crossAxisAlignment="start">
          <SizedBox width={timestampColumnWidth} />
          <SizedBox width={timestampContentGap} />
          {!inlineContent ? (
            <>
              {senderAvatar}
              <SizedBox width={10} />
            </>
          ) : null}
          <Expanded>
            {inlineContent ? (
              <div style={{ minHeight: 44, display: 'flex', alignItems: 'center', justifyContent: 'flex-start', minWidth: 0 }}>
                <div
                  data-testid={interactionsKey}
                  style={{ pointerEvents: selectionMode ? 'none' : undefined, minWidth: 0, maxWidth: '100%' }}
                >
                  {content}
                </div>
              </div>
            ) : (
              <Column crossAxisAlignment="stretch" mainAxisSize="min">
                <Text maxLines={1} style={{ ...UiTypography.label, color: senderColor, fontWeight: 700 }}>
                  {senderName}
                </Text>
                <SizedBox height={4} />
                <div data-testid={interactionsKey} style={{ pointerEvents: selectionMode ? 'none' : undefined, minWidth: 0 }}>
                  {content}
                </div>
              </Column>
            )}
          </Expanded>
          <SizedBox width={10} />
          <SizedBox width={34} height={34} />
        </Row>
        <Positioned left={0} top={0} bottom={0} width={timestampColumnWidth}>
          {/* The key sits on the Center so the compact timestamp's FittedBox
              measures the full column width as its available width. */}
          <Center data-testid={`room-message-history-time-${message.id}`} style={{ width: '100%', height: '100%' }}>
            {useCompactLayout ? (
              <CompactActivityTimestamp timestamp={timestamp} style={timestampStyle} />
            ) : (
              <Text textAlign="center" style={timestampStyle}>
                {timestamp}
              </Text>
            )}
          </Center>
        </Positioned>
        <Positioned right={0} top={0} bottom={0} width={34}>
          <Center style={{ width: '100%', height: '100%' }}>
            <_HistoryInteractiveTarget>
              <ButtonIcon
                data-testid={`room-message-history-jump-${message.id}`}
                tooltip="跳转到消息"
                icon={<Icon icon={Icons.location_on_rounded} />}
                tone={ButtonTone.primary}
                onPressed={onJump}
                size={34}
              />
            </_HistoryInteractiveTarget>
          </Center>
        </Positioned>
      </Stack>
    </div>
  );

  return (
    <Row style={{ flexShrink: 0 }}>
      {selectionMode ? (
        <>
          <div data-testid={`room-message-history-select-${message.id}`} style={{ display: 'flex', flexShrink: 0 }}>
            <UiCheckbox value={selected} onChanged={() => onToggleSelection()} semanticLabel="选择这条消息记录" />
          </div>
          <SizedBox width={8} />
        </>
      ) : null}
      <Expanded>
        <UiContextMenuTriggerRegion onTap={selectionMode ? onToggleSelection : null} onTriggered={showContextMenu}>
          {surface}
        </UiContextMenuTriggerRegion>
      </Expanded>
    </Row>
  );
}

/**
 * Claims taps on the jump button so they never reach the row's selection
 * toggle (Dart: a translucent GestureDetector with an empty `onTap`).
 */
export function _HistoryInteractiveTarget({ children }: { children?: ReactNode }) {
  return (
    <div onClick={(event) => event.stopPropagation()} style={{ display: 'flex' }}>
      {children}
    </div>
  );
}

export function _historySenderName(user: UserSummary): string {
  const roomName = user.roomDisplayName?.trim();
  if (roomName != null && roomName.length > 0) return roomName;
  const displayName = user.displayName.trim();
  if (displayName.length > 0) return displayName;
  return user.username;
}

// ---------------------------------------------------------------------------
// Member filter dialog.

export class _HistoryMemberFilterResult {
  constructor(readonly member: RoomMember | null) {}
}

function _subscribeWindowSize(onChange: () => void): () => void {
  if (typeof window === 'undefined') return () => {};
  window.addEventListener('resize', onChange);
  return () => window.removeEventListener('resize', onChange);
}

function _windowHeight(): number {
  return typeof window === 'undefined' ? 0 : window.innerHeight;
}

/** `MediaQuery.sizeOf(context).height` as a hook. */
function _useWindowHeight(): number {
  return useSyncExternalStore(_subscribeWindowSize, _windowHeight, _windowHeight);
}

export interface _HistoryMemberFilterDialogProps {
  roomId: string;
  currentUser: CurrentUser;
  members: readonly RoomMember[];
  selectedMember: RoomMember | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: _RoomUserProfileResolver | null;
  onOpenRoom: ((room: PublicRoom) => void) | null;
}

export function _HistoryMemberFilterDialog(props: _HistoryMemberFilterDialogProps) {
  const { roomId, currentUser, members, onResolveRoomProfile, onResolveRoomUserProfile, onOpenRoom } = props;
  const route = useRoute<_HistoryMemberFilterResult>();
  const [controller] = useState(() => new TextEditingController());
  useEffect(() => () => controller.dispose(), [controller]);
  // `_controller.addListener(_handleSearchChanged)` → setState.
  useListenable(controller);
  const [selectedMember, setSelectedMember] = useState<RoomMember | null>(props.selectedMember);
  const windowHeight = _useWindowHeight();

  const query = controller.text.trim();
  const visibleMembers = members.filter((member) => member_filter.roomMemberSearchRank(member, query) < 99);
  visibleMembers.sort((a, b) => {
    const rank = member_filter.roomMemberSearchRank(a, query) - member_filter.roomMemberSearchRank(b, query);
    if (rank !== 0) return rank;
    const left = member_filter.roomMemberDisplayName(a).toLowerCase();
    const right = member_filter.roomMemberDisplayName(b).toLowerCase();
    return left < right ? -1 : left > right ? 1 : 0;
  });
  const viewportHeight = Math.min(Math.max(windowHeight * 0.58, 240), 560);
  const resolveProfile: UserProfileResolver | null =
    onResolveRoomUserProfile == null ? null : (user) => onResolveRoomUserProfile(roomId, user);

  return (
    <DialogFrame
      title="筛选消息成员"
      icon={Icons.person_search_outlined}
      maxWidth={620}
      actionBar={
        <ResponsiveDialogActionBar
          leadingActionCount={1}
          actions={[
            new ResponsiveDialogAction({
              buttonKey: 'message-history-member-reset',
              label: '重置',
              icon: Icons.restart_alt,
              onPressed: () => route.close(new _HistoryMemberFilterResult(null)),
            }),
            new ResponsiveDialogAction({
              label: '取消',
              onPressed: () => route.close(),
            }),
            new ResponsiveDialogAction({
              buttonKey: 'message-history-member-apply',
              label: '应用',
              icon: Icons.check,
              tone: ButtonTone.primary,
              onPressed: () => route.close(new _HistoryMemberFilterResult(selectedMember)),
            }),
          ]}
        />
      }
    >
      <div style={{ height: viewportHeight, flexShrink: 0, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <div data-testid="message-history-member-search" style={{ flexShrink: 0 }}>
          <Input controller={controller} hintText="搜索成员" prefixIcon={Icons.search} showClearButton />
        </div>
        <SizedBox height={12} />
        {/* PORT: RawScrollbar(thickness: 7, radius: 999, thumb textMuted 82%)
            → the document scrollbar style; inline styles cannot theme it. */}
        <div
          data-testid="message-history-member-scrollbar"
          style={{
            flex: '1 1 0',
            minHeight: 0,
            overflowY: 'auto',
            paddingRight: 10,
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <_HistoryMemberOption
            label="所有人"
            selected={selectedMember == null}
            icon={Icons.groups_outlined}
            onPressed={() => setSelectedMember(null)}
          />
          {visibleMembers.map((member) => (
            <_HistoryMemberOption
              key={member.user.id}
              label={member_filter.roomMemberDisplayName(member)}
              meta={`@${member.user.username}`}
              roleLabel={room_display.roomRoleLabel(member.user)}
              selected={selectedMember?.user.id === member.user.id}
              user={member.user}
              currentUser={currentUser}
              onResolveProfile={resolveProfile}
              onResolveRoomProfile={onResolveRoomProfile}
              onOpenRoom={onOpenRoom}
              onPressed={() => setSelectedMember(member)}
            />
          ))}
        </div>
      </div>
    </DialogFrame>
  );
}

export interface _HistoryMemberOptionProps {
  label: string;
  selected: boolean;
  onPressed: () => void;
  meta?: string | null;
  roleLabel?: string | null;
  user?: UserSummary | null;
  icon?: IconData | null;
  currentUser?: CurrentUser | null;
  onResolveProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onOpenRoom?: ((room: PublicRoom) => void) | null;
}

export function _HistoryMemberOption({
  label,
  selected,
  onPressed,
  meta = null,
  roleLabel = null,
  user = null,
  icon = null,
  currentUser = null,
  onResolveProfile = null,
  onResolveRoomProfile = null,
  onOpenRoom = null,
}: _HistoryMemberOptionProps) {
  const [hovered, setHovered] = useState(false);
  const config = useAppConfig();
  const highlighted = selected || hovered;
  const suffix = user == null ? 'all' : user.id;
  const transition = `background-color 90ms ${UiCurves.easeOutCubic}, border-color 90ms ${UiCurves.easeOutCubic}`;
  return (
    <div
      data-testid={user == null ? 'message-history-member-hover-all' : `message-history-member-hover-${user.id}`}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={(event) => {
        if (event.button !== 0) return;
        onPressed();
      }}
      style={{ flexShrink: 0, cursor: SystemMouseCursors.basic }}
    >
      <div
        data-testid={user == null ? 'message-history-member-all' : `message-history-member-${user.id}`}
        style={{
          minHeight: 64,
          padding: 14,
          boxSizing: 'border-box',
          backgroundColor: highlighted ? UiColors.selected : UiColors.surfaceLow,
          borderRadius: UiRadii.md,
          border: `1px solid ${highlighted ? UiColors.selectedBorder : UiColors.border}`,
          transition,
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
        }}
      >
        {user != null ? (
          <UserHoverCard
            user={user}
            currentUser={currentUser}
            onResolveProfile={onResolveProfile}
            onResolveRoomProfile={onResolveRoomProfile}
            onEnterCommonRoom={onOpenRoom}
            showRoomRole
          >
            <div data-testid={`message-history-member-avatar-${user.id}`} style={{ display: 'flex', flexShrink: 0 }}>
              <Avatar
                label={room_display.userAvatarLabel(user)}
                imageUrl={config.resolveAssetUrl(user.avatarUrl)}
                defaultAvatarKey={user.defaultAvatarKey}
                size={36}
              />
            </div>
          </UserHoverCard>
        ) : (
          <SizedBox width={36} height={36}>
            <Center style={{ width: '100%', height: '100%' }}>
              {icon != null ? <Icon icon={icon} color={UiColors.textSecondary} size={20} /> : null}
            </Center>
          </SizedBox>
        )}
        <SizedBox width={10} />
        <Expanded data-testid={user == null ? 'message-history-member-all-option' : `message-history-member-option-${user.id}`}>
          <div style={{ padding: '4px 0', boxSizing: 'border-box' }}>
            <Row>
              <Expanded>
                <Column crossAxisAlignment="start" mainAxisSize="min" style={{ alignSelf: 'stretch' }}>
                  <AdaptiveHighlightedText
                    text={label}
                    query=""
                    comfortableLines={1}
                    style={{ ...UiTypography.body, color: UiColors.text, fontWeight: 700 }}
                  />
                  {meta != null ? (
                    <>
                      <SizedBox height={2} />
                      <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textMuted, alignSelf: 'stretch' }}>
                        {meta}
                      </Text>
                    </>
                  ) : null}
                </Column>
              </Expanded>
              {roleLabel != null ? (
                <>
                  <SizedBox width={8} />
                  <div data-testid={`message-history-member-role-${user?.id}`} style={{ display: 'flex', flexShrink: 0 }}>
                    <RoleBadge label={roleLabel} padding={EdgeInsets.symmetric({ horizontal: 8, vertical: 5 })} />
                  </div>
                </>
              ) : null}
              <SizedBox width={12} />
              <div data-testid={`message-history-member-radio-${suffix}`} style={{ display: 'flex', flexShrink: 0 }}>
                <Icon
                  icon={selected ? Icons.radio_button_checked : Icons.radio_button_off}
                  color={selected ? UiColors.accent : UiColors.textMuted}
                  size={20}
                />
              </div>
            </Row>
          </div>
        </Expanded>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Delete confirmation.

export function _HistoryDeleteConfirmDialog({
  messageCount,
  onClose,
}: {
  messageCount: number;
  /** Navigator.pop for the dialog route. */
  onClose: (result?: boolean) => void;
}) {
  return (
    <DialogFrame
      title={messageCount === 1 ? '删除消息记录' : '删除选中的消息记录'}
      icon={Icons.warning_amber_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => onClose(false) }),
        new ResponsiveDialogAction({ label: '删除', tone: ButtonTone.danger, onPressed: () => onClose(true) }),
      ]}
    >
      <Text style={UiTypography.body}>
        {messageCount === 1
          ? '删除后仅会从当前账号看到的消息记录中移除，不会撤回或删除房间消息'
          : `确定删除所有选中的 ${messageCount} 条消息记录吗？删除只对当前账号生效`}
      </Text>
    </DialogFrame>
  );
}
