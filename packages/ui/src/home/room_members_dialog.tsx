import type { ReactNode } from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import { removeRoomBlacklistEntry, upsertRoomBlacklistEntry } from '@gang-chat/core/app/room_blacklist';
import {
  publicRoomFromRoomDetail,
  resolvedRoomMemberProfileUser,
  roomAccessState,
  roomManagementPermissionState,
  userWithLocalCommonRoomNames,
} from '@gang-chat/core/app/room_display';
import { roomInvitesEnabled } from '@gang-chat/core/app/room_invites';
import {
  RoomMemberPresenceFilter,
  RoomMemberRoleFilter,
  filteredRoomMembers,
  orderRoomMembersByUserIds,
  removeRoomMemberConfirmBody,
  removeRoomMemberConfirmLabel,
  removeRoomMemberConfirmTitle,
  replaceRoomMember,
  roomMemberFilterCounts,
  roomMemberPermissionState,
  roomMemberRemoveFailed,
  roomMemberRemovedSucceeded,
  roomMemberRoleUpdateConfirmBody,
  roomMemberRoleUpdateConfirmLabel,
  roomMemberRoleUpdateConfirmTitle,
  roomMemberRoleUpdateNotice,
  roomMemberRoomDisplayNameUpdatedNotice,
  roomMemberWithRole,
  roomMembersWithCurrentUserPresence,
  transferCreatorConfirmBody,
  transferCreatorConfirmLabel,
  transferCreatorDialogTitle,
  transferCreatorSuccessNotice,
  visibleRoomMembers,
  type RoomMemberFilterCounts,
  type RoomMemberPermissionState,
} from '@gang-chat/core/app/room_members_filter';
import type { RoomsController } from '@gang-chat/core/app/rooms_controller';
import { Timer, unawaited } from '@gang-chat/core/lib/async';
import type {
  CurrentUser,
  JoinRequest,
  LiveState,
  PublicRoom,
  RoomBlacklistEntry,
  RoomDetail,
  RoomMember,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { Center, Column, SizedBox } from '../lib/layout';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { TextEditingController, TextEditingValue, TextSelection } from '../lib/text_editing';
import { ViewState, useViewState } from '../lib/view_state';
import { ButtonIcon } from '../ui/button';
import { FloatingNotice, FloatingNoticeEmitter, FloatingNoticeTone } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { showDialog, useRoute } from '../ui/overlay';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { SettingsList } from '../ui/settings_scaffold';
import { UiColors } from '../ui/tokens';
import { HomeAdaptiveLayout } from './adaptive_layout';
import { _dialogMaxHeight, _dialogMaxWidth } from './room_management';
import { _ConfirmDialog, _EmptyState, _LayoutBuilder, _RoomDialogShell } from './room_management_components';
import {
  _BlacklistSection,
  _BlockUserSection,
  _EditRoomDisplayNameDialog,
  _InviteSection,
  _JoinRequestDetailsDialog,
  _JoinRequestsSection,
  _MemberFilters,
  _MemberRow,
  _memberActionCount,
  _memberRowNeedsStackedActions,
  _memberRowNeedsTwoLineName,
} from './room_members_components';

// Port of `room_members_dialog.dart` (part of `room_management.dart`).

export enum _RoomMembersSection {
  roomMembers = 'roomMembers',
  newMembers = 'newMembers',
  blacklist = 'blacklist',
}

export interface RoomMembersDialogProps {
  controller: RoomsController;
  room: RoomDetail;
  currentUser: CurrentUser;
  initialLive: LiveState;
  initialSearchQuery?: string;
  hasPendingJoinRequests?: boolean;

  /**
   * Incremented by the host when a realtime event (join requests updated, role
   * changed) means this panel's data is stale. A change triggers a reload.
   */
  reloadToken?: number;

  embedded?: boolean;
  onClose?: (() => void) | null;
  onChanged?: (() => void) | null;
  onPendingJoinRequestsChanged?: ((value: boolean) => void) | null;
  onOpenRoom?: ((room: PublicRoom) => void) | null;
}

const _inviteSearchDebounceDuration = 260;

export class _RoomMembersDialogState extends ViewState<RoomMembersDialogProps> {
  readonly _memberSearchController = new TextEditingController();
  readonly _inviteSearchController = new TextEditingController();
  readonly _blockSearchController = new TextEditingController();
  readonly _busyRequestIds = new Set<string>();
  readonly _busyMemberIds = new Set<string>();
  readonly _busyInviteUserIds = new Set<string>();
  readonly _busyBlockUserIds = new Set<string>();
  readonly _pendingInviteUserIds = new Set<string>();

  _inviteSearchDebounce: Timer | null = null;
  _blockSearchDebounce: Timer | null = null;
  _inviteSearchSeq = 0;
  _blockSearchSeq = 0;

  _room: RoomDetail;
  _live: LiveState;
  _members: RoomMember[] = [];
  _memberDisplayOrder: string[] = [];
  _requests: JoinRequest[] = [];
  _inviteResults: UserSummary[] = [];
  _blockResults: UserSummary[] = [];
  _blacklist: RoomBlacklistEntry[] = [];

  _loading = true;
  _searchingInvites = false;
  _searchingBlocks = false;
  _loadingBlacklist = false;
  _blacklistLoaded = false;
  _changed = false;
  _memberQuery = '';
  _inviteQuery = '';
  _blockQuery = '';
  _lastMemberSearchText = '';
  _lastInviteSearchText = '';
  _lastBlockSearchText = '';
  _error: string | null = null;
  _requestError: string | null = null;
  _inviteError: string | null = null;
  _blockError: string | null = null;
  _blacklistError: string | null = null;
  _notice: string | null = null;
  _floatingNoticeSerial = 0;
  readonly _floatingNoticeEventKeys = new Map<string, number>();
  _activeJoinRequestDetailId: string | null = null;
  _presenceFilter: RoomMemberPresenceFilter = RoomMemberPresenceFilter.all;
  _roleFilter: RoomMemberRoleFilter = RoomMemberRoleFilter.all;
  _section: _RoomMembersSection = _RoomMembersSection.roomMembers;
  _syncingMemberSearch = false;

  /** `HomeAdaptiveLayout.usesCompactLayout(context)`; refreshed on every build. */
  _compactLayout = false;
  /** `Navigator.of(context).pop` of the enclosing dialog route; set on every build. */
  _popRoute: (result?: boolean) => void = () => {};

  constructor(props: RoomMembersDialogProps) {
    super();
    // The first build runs before initState, so the state the Dart initState
    // prepared before its first build is set up front.
    this._room = props.room;
    this._live = props.initialLive;
    this._applyMemberSearchQuery(props.initialSearchQuery ?? '');
  }

  get _canReviewRequests(): boolean {
    return roomAccessState({ room: this._room, currentUser: this.widget.currentUser }).canReviewJoinRequests;
  }

  get _canEditCreatorOnly(): boolean {
    return roomManagementPermissionState({ room: this._room, currentUser: this.widget.currentUser }).canEditCreatorOnly;
  }

  get _canInviteMembers(): boolean {
    return this._canViewNewMembers;
  }

  get _canViewNewMembers(): boolean {
    return roomInvitesEnabled(this._room.joinPolicy);
  }

  get _canViewJoinRequests(): boolean {
    return this._canViewNewMembers && this._canReviewRequests;
  }

  get _canManageMembers(): boolean {
    return roomAccessState({ room: this._room, currentUser: this.widget.currentUser }).canManageRoom;
  }

  get _canViewBlacklist(): boolean {
    return this._canManageMembers;
  }

  override initState(): void {
    this._memberSearchController.addListener(this._onMemberSearchChanged);
    this._inviteSearchController.addListener(this._onInviteSearchChanged);
    this._blockSearchController.addListener(this._onBlockSearchChanged);
    unawaited(this._load());
  }

  override dispose(): void {
    this._inviteSearchDebounce?.cancel();
    this._blockSearchDebounce?.cancel();
    this._memberSearchController.dispose();
    this._inviteSearchController.dispose();
    this._blockSearchController.dispose();
  }

  override didUpdateProps(oldWidget: RoomMembersDialogProps): void {
    const widget = this.widget;
    const liveChanged = widget.initialLive !== oldWidget.initialLive;
    const liveBelongsToRoom = widget.initialLive.roomId === widget.room.id;
    if ((widget.initialSearchQuery ?? '') !== (oldWidget.initialSearchQuery ?? '')) {
      this.setState(() => {
        this._applyMemberSearchQuery(widget.initialSearchQuery ?? '');
        this._refreshMemberDisplayOrder();
        this._section = _RoomMembersSection.roomMembers;
      });
    }
    // The host bumps reloadToken when a realtime event invalidates this panel
    // (join requests changed, or the current user's role changed). Re-pull the
    // member/request lists, and adopt the freshest room so permission checks
    // (canReviewRequests, canManageMembers) reflect the new role.
    if ((widget.reloadToken ?? 0) !== (oldWidget.reloadToken ?? 0) || widget.room !== oldWidget.room) {
      const roomChanged = widget.room.id !== oldWidget.room.id;
      this._room = widget.room;
      if (roomChanged) {
        this._resetBlacklistState();
        this._memberDisplayOrder = [];
      }
      if (liveChanged && liveBelongsToRoom) {
        this._live = widget.initialLive;
      }
      unawaited(this._load({ refreshDisplayOrder: roomChanged }));
      if (this._section === _RoomMembersSection.blacklist) {
        unawaited(this._loadBlacklist());
      }
    } else if (liveChanged && liveBelongsToRoom) {
      this.setState(() => {
        this._live = widget.initialLive;
      });
    }
  }

  _close(): void {
    if (this.widget.embedded ?? false) {
      if (this._changed) this.widget.onChanged?.();
      this.widget.onClose?.();
      return;
    }
    this._popRoute(this._changed);
  }

  _selectSection(section: _RoomMembersSection): void {
    if (section === _RoomMembersSection.newMembers && !this._canViewNewMembers) {
      return;
    }
    if (section === _RoomMembersSection.blacklist && !this._canViewBlacklist) {
      return;
    }
    this.setState(() => {
      this._section = section;
    });
    if (section === _RoomMembersSection.blacklist && !this._blacklistLoaded) {
      unawaited(this._loadBlacklist());
    }
  }

  _resetBlacklistState(): void {
    this._blockSearchDebounce?.cancel();
    this._blockSearchSeq += 1;
    this._blockResults = [];
    this._blacklist = [];
    this._searchingBlocks = false;
    this._loadingBlacklist = false;
    this._blacklistLoaded = false;
    this._blockError = null;
    this._blacklistError = null;
  }

  readonly _onMemberSearchChanged = (): void => {
    if (this._syncingMemberSearch) return;
    const text = this._memberSearchController.text;
    if (text === this._lastMemberSearchText) return;
    this._lastMemberSearchText = text;
    this.setState(() => {
      this._memberQuery = text;
      this._refreshMemberDisplayOrder();
    });
  };

  _applyMemberSearchQuery(query: string): void {
    const value = query.trim();
    this._memberQuery = value;
    this._lastMemberSearchText = value;
    if (this._memberSearchController.text === value) return;
    this._syncingMemberSearch = true;
    this._memberSearchController.value = new TextEditingValue({
      text: value,
      selection: TextSelection.collapsed({ offset: value.length }),
    });
    this._syncingMemberSearch = false;
  }

  readonly _onInviteSearchChanged = (): void => {
    const text = this._inviteSearchController.text;
    if (text === this._lastInviteSearchText) return;
    this._lastInviteSearchText = text;
    if (!this._canInviteMembers) return;
    const query = text.trim();
    this.setState(() => {
      this._inviteQuery = query;
      this._inviteError = null;
      if (query.length === 0) {
        this._inviteResults = [];
        this._searchingInvites = false;
      }
    });
    this._inviteSearchDebounce?.cancel();
    if (query.length < 2) {
      this._inviteSearchSeq += 1;
      return;
    }
    const seq = ++this._inviteSearchSeq;
    this._inviteSearchDebounce = new Timer(_inviteSearchDebounceDuration, () => {
      unawaited(this._searchInviteCandidates(query, seq));
    });
  };

  readonly _onBlockSearchChanged = (): void => {
    const text = this._blockSearchController.text;
    if (text === this._lastBlockSearchText) return;
    this._lastBlockSearchText = text;
    if (!this._canManageMembers) return;
    const query = text.trim();
    this.setState(() => {
      this._blockQuery = query;
      this._blockError = null;
      if (query.length === 0) {
        this._blockResults = [];
        this._searchingBlocks = false;
      }
    });
    this._blockSearchDebounce?.cancel();
    if (query.length < 2) {
      this._blockSearchSeq += 1;
      return;
    }
    const seq = ++this._blockSearchSeq;
    this._blockSearchDebounce = new Timer(_inviteSearchDebounceDuration, () => {
      unawaited(this._searchBlockCandidates(query, seq));
    });
  };

  async _load(opts: { refreshDisplayOrder?: boolean } = {}): Promise<void> {
    const { refreshDisplayOrder = true } = opts;
    this.setState(() => {
      this._loading = true;
      this._error = null;
      this._requestError = null;
    });
    try {
      const snapshot = await this.widget.controller.loadRoomMembersSnapshot({
        roomId: this._room.id,
        fallbackLive: this._live,
        includeJoinRequests: this._canViewJoinRequests,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._members = snapshot.members;
        this._live = snapshot.live;
        if (refreshDisplayOrder || this._memberDisplayOrder.length === 0) {
          this._refreshMemberDisplayOrder();
        }
        this._requests = snapshot.joinRequests;
        this._requestError = snapshot.joinRequestsError;
        this._loading = false;
      });
      this._notifyPendingJoinRequestsChanged(snapshot.joinRequests, snapshot.joinRequestsError);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._loading = false;
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _loadBlacklist(): Promise<void> {
    if (!this._canManageMembers) return;
    if (this._loadingBlacklist) return;
    this.setState(() => {
      this._loadingBlacklist = true;
      this._blacklistError = null;
    });
    try {
      const entries = await this.widget.controller.listRoomBlacklist(this._room.id);
      if (!this.mounted) return;
      this.setState(() => {
        this._blacklist = entries;
        this._blacklistLoaded = true;
        this._loadingBlacklist = false;
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._loadingBlacklist = false;
        this._blacklistError = userFacingErrorMessage(error);
      });
    }
  }

  async _searchInviteCandidates(query: string, seq: number): Promise<void> {
    if (!this._canInviteMembers) return;
    this.setState(() => {
      this._searchingInvites = true;
      this._inviteError = null;
    });
    try {
      const results = await this.widget.controller.searchUsers({ query, limit: 20 });
      if (!this.mounted || seq !== this._inviteSearchSeq) return;
      this.setState(() => {
        this._inviteResults = results;
        this._searchingInvites = false;
      });
    } catch (error) {
      if (!this.mounted || seq !== this._inviteSearchSeq) return;
      this.setState(() => {
        this._searchingInvites = false;
        this._inviteError = userFacingErrorMessage(error);
      });
    }
  }

  async _searchBlockCandidates(query: string, seq: number): Promise<void> {
    if (!this._canManageMembers) return;
    this.setState(() => {
      this._searchingBlocks = true;
      this._blockError = null;
    });
    try {
      const results = await this.widget.controller.searchUsers({ query, limit: 20 });
      if (!this.mounted || seq !== this._blockSearchSeq) return;
      this.setState(() => {
        this._blockResults = results;
        this._searchingBlocks = false;
      });
    } catch (error) {
      if (!this.mounted || seq !== this._blockSearchSeq) return;
      this.setState(() => {
        this._searchingBlocks = false;
        this._blockError = userFacingErrorMessage(error);
      });
    }
  }

  async _invite(user: UserSummary): Promise<void> {
    if (!this._canInviteMembers) return;
    if (this._busyInviteUserIds.has(user.id)) return;
    if (this._pendingInviteUserIds.has(user.id)) return;
    if (this._members.some((member) => member.user.id === user.id)) return;
    this.setState(() => {
      this._busyInviteUserIds.add(user.id);
      this._inviteError = null;
      this._notice = null;
    });
    try {
      await this.widget.controller.inviteMember({ roomId: this._room.id, userId: user.id });
      if (!this.mounted) return;
      this.setState(() => {
        this._busyInviteUserIds.delete(user.id);
        this._pendingInviteUserIds.add(user.id);
        this._changed = true;
        this._notice = '邀请已发送';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._busyInviteUserIds.delete(user.id);
        this._inviteError = userFacingErrorMessage(error);
      });
    }
  }

  async _blockUser(user: UserSummary): Promise<void> {
    if (!this._canManageMembers) return;
    if (this._busyBlockUserIds.has(user.id)) return;
    if (this._blacklist.some((entry) => entry.user.id === user.id)) return;
    if (this._members.some((member) => member.user.id === user.id)) return;
    if (user.isSuperuser) return;
    this.setState(() => {
      this._busyBlockUserIds.add(user.id);
      this._blockError = null;
      this._blacklistError = null;
      this._notice = null;
    });
    try {
      const entry = await this.widget.controller.blockRoomUser({ roomId: this._room.id, userId: user.id });
      if (!this.mounted) return;
      this.setState(() => {
        this._busyBlockUserIds.delete(user.id);
        this._blacklist = upsertRoomBlacklistEntry(this._blacklist, entry);
        this._blacklistLoaded = true;
        this._changed = true;
        this._notice = '已加入黑名单';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._busyBlockUserIds.delete(user.id);
        this._blockError = userFacingErrorMessage(error);
      });
    }
  }

  async _unblockUser(user: UserSummary): Promise<void> {
    if (!this._canManageMembers) return;
    if (this._busyBlockUserIds.has(user.id)) return;
    this.setState(() => {
      this._busyBlockUserIds.add(user.id);
      this._blockError = null;
      this._blacklistError = null;
      this._notice = null;
    });
    try {
      await this.widget.controller.unblockRoomUser({ roomId: this._room.id, userId: user.id });
      if (!this.mounted) return;
      this.setState(() => {
        this._busyBlockUserIds.delete(user.id);
        this._blacklist = removeRoomBlacklistEntry(this._blacklist, user.id);
        this._blacklistLoaded = true;
        this._changed = true;
        this._notice = '已取消拉黑';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._busyBlockUserIds.delete(user.id);
        this._blacklistError = userFacingErrorMessage(error);
      });
    }
  }

  async _reviewRequest(request: JoinRequest, approve: boolean): Promise<void> {
    if (this._busyRequestIds.has(request.id)) return;
    this.setState(() => {
      this._busyRequestIds.add(request.id);
      this._requestError = null;
      this._notice = null;
    });
    try {
      await this.widget.controller.reviewJoinRequest({
        roomId: this._room.id,
        requestId: request.id,
        approve,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._busyRequestIds.delete(request.id);
        this._requests = this._requests.filter((item) => item.id !== request.id);
        this._changed = true;
        this._notice = approve ? '申请已通过' : '申请已拒绝';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
      this._notifyPendingJoinRequestsChanged(this._requests, this._requestError);
      if (approve) unawaited(this._load({ refreshDisplayOrder: false }));
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._busyRequestIds.delete(request.id);
        this._requestError = userFacingErrorMessage(error);
      });
    }
  }

  _notifyPendingJoinRequestsChanged(requests: readonly JoinRequest[], requestError: string | null): void {
    if (!this._canViewJoinRequests || requestError != null) return;
    this.widget.onPendingJoinRequestsChanged?.(requests.length > 0);
  }

  _markFloatingNoticeEvent(channel: string, message: string | null): void {
    if (message == null || message.trim().length === 0) return;
    this._floatingNoticeEventKeys.set(channel, ++this._floatingNoticeSerial);
  }

  _floatingNoticeEventKey(channel: string): unknown {
    return this._floatingNoticeEventKeys.get(channel) ?? null;
  }

  _floatingNotices(): FloatingNotice[] {
    const notices: FloatingNotice[] = [];
    if (this._notice != null) {
      notices.push(
        new FloatingNotice({
          message: this._notice,
          tone: FloatingNoticeTone.success,
          eventKey: this._floatingNoticeEventKey('notice'),
        }),
      );
    }
    if (this._error != null) {
      notices.push(
        new FloatingNotice({
          message: this._error,
          tone: FloatingNoticeTone.error,
          duration: null,
          eventKey: this._floatingNoticeEventKey('error'),
        }),
      );
    }
    return notices;
  }

  async _showJoinRequestDetails(request: JoinRequest): Promise<void> {
    this.setState(() => {
      this._activeJoinRequestDetailId = request.id;
    });
    try {
      await showDialog<void>(() => (
        <_JoinRequestDetailsDialog
          request={request}
          currentUser={this.widget.currentUser}
          onResolveProfile={this._resolveMemberProfile}
          onResolveRoomProfile={this._resolveRoomProfile}
          onOpenRoom={this.widget.onOpenRoom}
        />
      ));
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._activeJoinRequestDetailId = null;
        });
      }
    }
  }

  async _setMemberRole(member: RoomMember, role: string): Promise<void> {
    if (this._busyMemberIds.has(member.user.id)) return;
    const confirmed = await showDialog<boolean>(() => (
      <_ConfirmDialog
        title={roomMemberRoleUpdateConfirmTitle(role)}
        message={roomMemberRoleUpdateConfirmBody(member, role)}
        confirmLabel={roomMemberRoleUpdateConfirmLabel(role)}
        danger={role !== 'admin'}
      />
    ));
    if (confirmed !== true || !this.mounted) return;

    this.setState(() => {
      this._busyMemberIds.add(member.user.id);
      this._error = null;
      this._notice = null;
    });
    try {
      const updated = await this.widget.controller.updateRoomMemberRole({
        roomId: this._room.id,
        userId: member.user.id,
        role,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._busyMemberIds.delete(member.user.id);
        this._members = replaceRoomMember(this._members, updated);
        this._changed = true;
        this._notice = roomMemberRoleUpdateNotice(role);
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._busyMemberIds.delete(member.user.id);
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _editRoomDisplayName(member: RoomMember): Promise<void> {
    if (this._busyMemberIds.has(member.user.id)) return;
    const roomDisplayName = await showDialog<string>(() => <_EditRoomDisplayNameDialog member={member} />);
    if (roomDisplayName == null || !this.mounted) return;

    this.setState(() => {
      this._busyMemberIds.add(member.user.id);
      this._error = null;
      this._notice = null;
    });
    try {
      const updated = await this.widget.controller.updateRoomMemberRoomDisplayName({
        roomId: this._room.id,
        userId: member.user.id,
        roomDisplayName,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._busyMemberIds.delete(member.user.id);
        this._members = replaceRoomMember(this._members, updated);
        this._changed = true;
        this._notice = roomMemberRoomDisplayNameUpdatedNotice(updated);
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._busyMemberIds.delete(member.user.id);
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _transferCreator(member: RoomMember): Promise<void> {
    if (this._busyMemberIds.has(member.user.id)) return;
    const confirmed = await showDialog<boolean>(() => (
      <_ConfirmDialog
        title={transferCreatorDialogTitle()}
        message={transferCreatorConfirmBody(member, { currentUserIsCreator: this._room.isCreator })}
        confirmLabel={transferCreatorConfirmLabel()}
        danger
      />
    ));
    if (confirmed !== true || !this.mounted) return;

    this.setState(() => {
      this._busyMemberIds.add(member.user.id);
      this._error = null;
      this._notice = null;
    });
    try {
      const previousOwnerId = this._room.createdBy?.id ?? null;
      const updated = await this.widget.controller.transferRoomCreator({
        roomId: this._room.id,
        userId: member.user.id,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._busyMemberIds.delete(member.user.id);
        this._room = updated;
        this._members = this._membersAfterCreatorTransfer({
          previousOwnerId,
          newOwnerId: member.user.id,
        });
        this._changed = true;
        this._notice = transferCreatorSuccessNotice();
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._busyMemberIds.delete(member.user.id);
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _removeMember(member: RoomMember): Promise<void> {
    if (this._busyMemberIds.has(member.user.id)) return;
    const confirmed = await showDialog<boolean>(() => (
      <_ConfirmDialog
        title={removeRoomMemberConfirmTitle()}
        message={removeRoomMemberConfirmBody(member)}
        confirmLabel={removeRoomMemberConfirmLabel()}
        danger
      />
    ));
    if (confirmed !== true || !this.mounted) return;

    this.setState(() => {
      this._busyMemberIds.add(member.user.id);
      this._error = null;
      this._notice = null;
    });
    try {
      await this.widget.controller.removeRoomMember({ roomId: this._room.id, userId: member.user.id });
      if (!this.mounted) return;
      const patch = roomMemberRemovedSucceeded({
        room: this._room,
        members: this._members,
        removed: member,
        busyMemberIds: this._busyMemberIds,
      });
      this.setState(() => {
        this._busyMemberIds.clear();
        for (const id of patch.busyMemberIds) this._busyMemberIds.add(id);
        this._members = patch.members;
        this._changed = patch.changed;
        this._error = patch.error;
        this._notice = patch.notice;
        this._markFloatingNoticeEvent('error', this._error);
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      const patch = roomMemberRemoveFailed({
        room: this._room,
        members: this._members,
        changed: this._changed,
        userId: member.user.id,
        busyMemberIds: this._busyMemberIds,
        failure: error,
      });
      this.setState(() => {
        this._busyMemberIds.clear();
        for (const id of patch.busyMemberIds) this._busyMemberIds.add(id);
        this._error = patch.error;
        this._notice = patch.notice;
        this._markFloatingNoticeEvent('error', this._error);
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    }
  }

  readonly _resolveMemberProfile = async (user: UserSummary): Promise<UserSummary> => {
    try {
      const profile = await this.widget.controller.getRoomMemberProfile({
        roomId: this._room.id,
        userId: user.id,
      });
      return this._userWithCurrentRoomCommonRoomName(resolvedRoomMemberProfileUser({ profile, fallback: user }));
    } catch {
      return this._resolveUserProfile(user);
    }
  };

  readonly _resolveUserProfile = async (user: UserSummary): Promise<UserSummary> => {
    try {
      const profile = await this.widget.controller.getUserProfile(user.id);
      return this._userWithCurrentRoomCommonRoomName(profile.mergeMissing(user));
    } catch {
      return this._userWithCurrentRoomCommonRoomName(user);
    }
  };

  _userWithCurrentRoomCommonRoomName(user: UserSummary): UserSummary {
    return userWithLocalCommonRoomNames({ user, rooms: [this._room.toCard()] });
  }

  readonly _resolveRoomProfile = async (room: PublicRoom): Promise<PublicRoom> => {
    const detail = await this.widget.controller.getRoom(room.id);
    return publicRoomFromRoomDetail(detail);
  };

  _visibleMembers(): RoomMember[] {
    const visible = filteredRoomMembers({
      members: this._effectiveMembers,
      live: this._live,
      presenceFilter: this._presenceFilter,
      roleFilter: this._roleFilter,
      query: this._memberQuery,
      ownerUserId: this._room.createdBy?.id ?? null,
    });
    return orderRoomMembersByUserIds({ members: visible, orderedUserIds: this._memberDisplayOrder });
  }

  get _effectiveMembers(): RoomMember[] {
    return roomMembersWithCurrentUserPresence(this._members, { currentUserId: this.widget.currentUser.id });
  }

  _refreshMemberDisplayOrder(): void {
    this._memberDisplayOrder = visibleRoomMembers({
      members: this._effectiveMembers,
      live: this._live,
      presenceFilter: this._presenceFilter,
      roleFilter: this._roleFilter,
      query: this._memberQuery,
      ownerUserId: this._room.createdBy?.id ?? null,
    }).map((member) => member.user.id);
  }

  _membersAfterCreatorTransfer(opts: { previousOwnerId: string | null; newOwnerId: string }): RoomMember[] {
    const { previousOwnerId, newOwnerId } = opts;
    return this._members.map((member) => {
      if (member.user.id === newOwnerId) return roomMemberWithRole(member, 'owner');
      if (previousOwnerId != null && previousOwnerId !== newOwnerId && member.user.id === previousOwnerId) {
        return roomMemberWithRole(member, 'admin');
      }
      return member;
    });
  }

  build(): ReactNode {
    const effectiveMembers = this._effectiveMembers;
    const filterCounts = roomMemberFilterCounts({
      members: effectiveMembers,
      live: this._live,
      ownerUserId: this._room.createdBy?.id ?? null,
    });
    let activeSection = this._section;
    if (activeSection === _RoomMembersSection.newMembers && !this._canViewNewMembers) {
      activeSection = _RoomMembersSection.roomMembers;
    }
    if (activeSection === _RoomMembersSection.blacklist && !this._canViewBlacklist) {
      activeSection = _RoomMembersSection.roomMembers;
    }
    const showJoinRequestsBadge =
      this._canViewJoinRequests && ((this.widget.hasPendingJoinRequests ?? false) || this._requests.length > 0);
    const sections: Segment<_RoomMembersSection>[] = [
      new Segment({ value: _RoomMembersSection.roomMembers, label: '房间成员', icon: Icons.groups_outlined }),
    ];
    if (this._canViewNewMembers) {
      sections.push(
        new Segment({
          value: _RoomMembersSection.newMembers,
          label: '新成员',
          icon: Icons.person_add_alt_1,
          showBadge: showJoinRequestsBadge,
          badgeKey: 'new-members-tab-badge',
        }),
      );
    }
    if (this._canViewBlacklist) {
      sections.push(new Segment({ value: _RoomMembersSection.blacklist, label: '黑名单', icon: Icons.block }));
    }

    let body: ReactNode;
    switch (activeSection) {
      case _RoomMembersSection.roomMembers:
        body = this._buildRoomMembersBody(filterCounts);
        break;
      case _RoomMembersSection.newMembers:
        body = this._buildNewMembersBody(effectiveMembers);
        break;
      case _RoomMembersSection.blacklist:
        body = this._buildBlacklistBody(effectiveMembers);
        break;
    }

    return (
      <FloatingNoticeEmitter notices={this._floatingNotices()}>
        <_RoomDialogShell
          title="成员"
          icon={Icons.group_outlined}
          maxWidth={_dialogMaxWidth}
          maxHeight={_dialogMaxHeight}
          embedded={this.widget.embedded ?? false}
          onClose={() => this._close()}
          pinned={
            sections.length > 1 ? (
              <SegmentedControl<_RoomMembersSection>
                expanded
                value={activeSection}
                onChanged={(section) => this._selectSection(section)}
                segments={sections}
              />
            ) : null
          }
          headerAction={
            this._canReviewRequests || (activeSection === _RoomMembersSection.blacklist && this._canManageMembers) ? (
              <ButtonIcon
                tooltip="刷新"
                icon={<Icon icon={Icons.refresh} />}
                onPressed={
                  activeSection === _RoomMembersSection.blacklist
                    ? () => unawaited(this._loadBlacklist())
                    : () => unawaited(this._load())
                }
                size={38}
              />
            ) : null
          }
        >
          {body}
        </_RoomDialogShell>
      </FloatingNoticeEmitter>
    );
  }

  _buildRoomMembersBody(filterCounts: RoomMemberFilterCounts): ReactNode {
    return (
      <Column crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0, padding: '0 22px 22px' }}>
        <_MemberFilters
          controller={this._memberSearchController}
          filterCounts={filterCounts}
          presenceFilter={this._presenceFilter}
          roleFilter={this._roleFilter}
          onPresenceChanged={(value) => {
            this.setState(() => {
              this._presenceFilter = value;
              this._refreshMemberDisplayOrder();
            });
          }}
          onRoleChanged={(value) => {
            this.setState(() => {
              this._roleFilter = value;
              this._refreshMemberDisplayOrder();
            });
          }}
        />
        <SizedBox height={14} />
        {/* Expanded(SizedBox.expand(...)). */}
        <div data-testid="room-members-list" style={{ flex: '1 1 0', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          {this._buildMemberList()}
        </div>
      </Column>
    );
  }

  _buildNewMembersBody(effectiveMembers: RoomMember[]): ReactNode {
    const children: ReactNode[] = [
      <_InviteSection
        key="invite"
        controller={this._inviteSearchController}
        currentUser={this.widget.currentUser}
        query={this._inviteQuery}
        searching={this._searchingInvites}
        results={this._inviteResults}
        members={effectiveMembers}
        pendingInviteUserIds={this._pendingInviteUserIds}
        busyUserIds={this._busyInviteUserIds}
        error={this._inviteError}
        enabled={this._canInviteMembers}
        onResolveProfile={this._resolveUserProfile}
        onResolveRoomProfile={this._resolveRoomProfile}
        onOpenRoom={this.widget.onOpenRoom}
        onInvite={(user) => unawaited(this._invite(user))}
      />,
    ];
    if (this._canViewJoinRequests) {
      children.push(
        <_JoinRequestsSection
          key="requests"
          requests={this._requests}
          currentUser={this.widget.currentUser}
          busyRequestIds={this._busyRequestIds}
          activeDetailRequestId={this._activeJoinRequestDetailId}
          error={this._requestError}
          onResolveProfile={this._resolveMemberProfile}
          onResolveRoomProfile={this._resolveRoomProfile}
          onOpenRoom={this.widget.onOpenRoom}
          onDetail={(request) => unawaited(this._showJoinRequestDetails(request))}
          onApprove={(request) => unawaited(this._reviewRequest(request, true))}
          onReject={(request) => unawaited(this._reviewRequest(request, false))}
        />,
      );
    }
    return <SettingsList>{children}</SettingsList>;
  }

  _buildBlacklistBody(effectiveMembers: RoomMember[]): ReactNode {
    return (
      <SettingsList>
        {[
          <_BlockUserSection
            key="block"
            controller={this._blockSearchController}
            currentUser={this.widget.currentUser}
            query={this._blockQuery}
            searching={this._searchingBlocks}
            results={this._blockResults}
            members={effectiveMembers}
            blacklist={this._blacklist}
            busyUserIds={this._busyBlockUserIds}
            error={this._blockError}
            enabled={this._canManageMembers}
            onResolveProfile={this._resolveUserProfile}
            onResolveRoomProfile={this._resolveRoomProfile}
            onOpenRoom={this.widget.onOpenRoom}
            onBlock={(user) => unawaited(this._blockUser(user))}
            onUnblock={(user) => unawaited(this._unblockUser(user))}
          />,
          <_BlacklistSection
            key="blacklist"
            entries={this._blacklist}
            currentUser={this.widget.currentUser}
            busyUserIds={this._busyBlockUserIds}
            loading={this._loadingBlacklist}
            error={this._blacklistError}
            onResolveProfile={this._resolveUserProfile}
            onResolveRoomProfile={this._resolveRoomProfile}
            onOpenRoom={this.widget.onOpenRoom}
            onUnblock={(user) => unawaited(this._unblockUser(user))}
          />,
        ]}
      </SettingsList>
    );
  }

  _buildMemberList(): ReactNode {
    if (this._loading) {
      return (
        <Center style={{ flex: '1 1 0', minHeight: 0 }}>
          <SizedBox width={24} height={24}>
            <CircularProgressIndicator size={24} strokeWidth={2.2} color={UiColors.accent} />
          </SizedBox>
        </Center>
      );
    }
    const members = this._visibleMembers();
    if (members.length === 0) {
      return <_EmptyState icon={Icons.person_search_outlined} title="没有匹配的成员" />;
    }

    const ownerUserId = this._room.createdBy?.id ?? null;
    const permissionFor = (member: RoomMember): RoomMemberPermissionState =>
      roomMemberPermissionState({
        member,
        currentUser: this.widget.currentUser,
        canEditCreatorOnly: this._canEditCreatorOnly,
        canManageMembers: this._canManageMembers,
        ownerUserId,
      });

    // The LayoutBuilder box is the ListView itself, so the measured width is
    // the width the rows get (a classic DOM scrollbar takes room; Flutter's
    // desktop scrollbar overlays).
    return (
      <_LayoutBuilder
        style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto' }}
        builder={(maxWidth) => {
          const rowSurfaceHorizontalPadding = 20;
          const rowContentWidth = Number.isFinite(maxWidth) ? Math.max(maxWidth - rowSurfaceHorizontalPadding, 0) : Infinity;
          const stackMemberActions =
            this._compactLayout ||
            members.some((member) =>
              _memberRowNeedsStackedActions({
                availableWidth: rowContentWidth,
                member,
                live: this._live,
                permission: permissionFor(member),
                ownerUserId,
              }),
            );
          return members.map((member, index) => {
            const permission = permissionFor(member);
            const busy = this._busyMemberIds.has(member.user.id);
            const canMoveActions = _memberActionCount(permission) > 0;
            return (
              <div key={member.user.id} style={{ marginTop: index > 0 ? 8 : 0, display: 'flex', flexDirection: 'column' }}>
                <_MemberRow
                  member={member}
                  currentUser={this.widget.currentUser}
                  live={this._live}
                  permission={permission}
                  stackActions={stackMemberActions}
                  nameMaxLines={
                    (stackMemberActions || !canMoveActions) &&
                    _memberRowNeedsTwoLineName({
                      availableWidth: rowContentWidth,
                      member,
                      live: this._live,
                      ownerUserId,
                      busy,
                    })
                      ? 2
                      : 1
                  }
                  ownerUserId={ownerUserId}
                  query={this._memberQuery}
                  busy={busy}
                  onResolveProfile={this._resolveMemberProfile}
                  onResolveRoomProfile={this._resolveRoomProfile}
                  onOpenRoom={this.widget.onOpenRoom}
                  onEditRoomDisplayName={() => unawaited(this._editRoomDisplayName(member))}
                  onSetAdmin={() => unawaited(this._setMemberRole(member, 'admin'))}
                  onUnsetAdmin={() => unawaited(this._setMemberRole(member, 'member'))}
                  onRemoveMember={() => unawaited(this._removeMember(member))}
                  onTransferCreator={() => unawaited(this._transferCreator(member))}
                />
              </div>
            );
          });
        }}
      />
    );
  }
}

/** 房间成员面板：房间成员 / 新成员（邀请与加入申请）/ 黑名单。 */
export function RoomMembersDialog(props: RoomMembersDialogProps) {
  const state = useViewState(() => new _RoomMembersDialogState(props), props);
  state._compactLayout = HomeAdaptiveLayout.usesCompactLayout();
  state._popRoute = useRoute<boolean>().close;
  return <>{state.build()}</>;
}
