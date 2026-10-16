import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react';

import { roomBlacklistActionLabel, roomBlacklistCandidates } from '@gang-chat/core/app/room_blacklist';
import { roomRoleLabel, userAvatarLabel, userPrimaryName, userUsernameLabel } from '@gang-chat/core/app/room_display';
import { roomInviteCandidates } from '@gang-chat/core/app/room_invites';
import { joinRequestDetailReasonText, joinRequestFromInvitation, joinRequestSourceText } from '@gang-chat/core/app/room_join_requests';
import {
  RoomMemberPresence,
  RoomMemberPresenceFilter,
  RoomMemberRoleFilter,
  roomMemberDefaultDisplayName,
  roomMemberDisplayName,
  roomMemberPresence,
  roomMemberPresenceFilterLabel,
  roomMemberPresenceLabel,
  roomMemberRoleFilterLabel,
  roomMemberRoomDisplayNameOriginalLabel,
  roomMemberRoomDisplayNameValue,
  type RoomMemberFilterCounts,
  type RoomMemberPermissionState,
} from '@gang-chat/core/app/room_members_filter';
import type {
  CurrentUser,
  JoinRequest,
  LiveState,
  PublicRoom,
  RoomBlacklistEntry,
  RoomMember,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { TextEditingController, TextInputAction } from '../lib/text_editing';
import { measureTextWidth } from '../lib/text_measure';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction } from '../ui/feedback';
import { AdaptiveHighlightedText, HighlightedText } from '../ui/highlighted_text';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { PresencePill } from '../ui/member_presence_icons';
import { useRoute } from '../ui/overlay';
import { RoleBadge } from '../ui/role_badge';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { SettingsCard } from '../ui/settings_scaffold';
import { UiColors, UiSpacing, UiTypography } from '../ui/tokens';
import { HomeAdaptiveLayout } from './adaptive_layout';
import { _LayoutBuilder, _RowSurface } from './room_management_components';
import { UserHoverCard, type RoomProfileResolver } from './room_profile_card';

// Port of `room_members_components.dart` (part of `room_management.dart`).

type _UserProfileResolver = (user: UserSummary) => Promise<UserSummary>;

export interface _MemberFiltersProps {
  controller: TextEditingController;
  filterCounts: RoomMemberFilterCounts;
  presenceFilter: RoomMemberPresenceFilter;
  roleFilter: RoomMemberRoleFilter;
  onPresenceChanged: (value: RoomMemberPresenceFilter) => void;
  onRoleChanged: (value: RoomMemberRoleFilter) => void;
}

const _presenceFilterValues: readonly RoomMemberPresenceFilter[] = [
  RoomMemberPresenceFilter.all,
  RoomMemberPresenceFilter.live,
  RoomMemberPresenceFilter.online,
  RoomMemberPresenceFilter.offline,
];

const _roleFilterValues: readonly RoomMemberRoleFilter[] = [
  RoomMemberRoleFilter.all,
  RoomMemberRoleFilter.member,
  RoomMemberRoleFilter.admin,
  RoomMemberRoleFilter.creator,
];

export function _MemberFilters({
  controller,
  filterCounts,
  presenceFilter,
  roleFilter,
  onPresenceChanged,
  onRoleChanged,
}: _MemberFiltersProps) {
  const narrow = HomeAdaptiveLayout.usesCompactLayout();
  const presenceSegments = _presenceFilterValues.map(
    (value) => new Segment<RoomMemberPresenceFilter>({ value, label: roomMemberPresenceFilterLabel(value, filterCounts) }),
  );
  const roleSegments = _roleFilterValues.map(
    (value) => new Segment<RoomMemberRoleFilter>({ value, label: roomMemberRoleFilterLabel(value, filterCounts) }),
  );
  const presence = (
    <div data-testid="member-presence-filter">
      <SegmentedControl<RoomMemberPresenceFilter> expanded value={presenceFilter} onChanged={onPresenceChanged} segments={presenceSegments} />
    </div>
  );
  const roles = (
    <div data-testid="member-role-filter">
      <SegmentedControl<RoomMemberRoleFilter> expanded value={roleFilter} onChanged={onRoleChanged} segments={roleSegments} />
    </div>
  );
  return (
    <_LayoutBuilder
      style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', flexShrink: 0 }}
      builder={(maxWidth) => {
        const pairedControlWidth = Number.isFinite(maxWidth) ? Math.max((maxWidth - 10) / 2, 0) : Infinity;
        const stackBeforeOverflow =
          SegmentedControl.minimumWidthFor(presenceSegments) > pairedControlWidth ||
          SegmentedControl.minimumWidthFor(roleSegments) > pairedControlWidth;
        const stackFilters = narrow || stackBeforeOverflow;
        return (
          <>
            <Input controller={controller} hintText="搜索成员" prefixIcon={Icons.search} showClearButton />
            <SizedBox height={10} />
            {stackFilters ? (
              <>
                {presence}
                <SizedBox height={10} />
                {roles}
              </>
            ) : (
              <Row>
                <Expanded>{presence}</Expanded>
                <SizedBox width={10} />
                <Expanded>{roles}</Expanded>
              </Row>
            )}
          </>
        );
      }}
    />
  );
}

export interface _MemberRowProps {
  member: RoomMember;
  currentUser: CurrentUser;
  live: LiveState;
  permission: RoomMemberPermissionState;
  stackActions: boolean;
  nameMaxLines: number;
  ownerUserId: string | null;
  query: string;
  busy: boolean;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onEditRoomDisplayName: () => void;
  onSetAdmin: () => void;
  onUnsetAdmin: () => void;
  onRemoveMember: () => void;
  onTransferCreator: () => void;
}

// Non-flex children of a Flutter Row keep their intrinsic width.
const _rigid: CSSProperties = { flexShrink: 0 };

export function _MemberRow({
  member,
  currentUser,
  live,
  permission,
  stackActions,
  nameMaxLines,
  ownerUserId,
  query,
  busy,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onEditRoomDisplayName,
  onSetAdmin,
  onUnsetAdmin,
  onRemoveMember,
  onTransferCreator,
}: _MemberRowProps) {
  const appConfig = useAppConfig();
  const presence = roomMemberPresence(member, { live });
  const role = roomRoleLabel(member.user, { ownerUserId });
  const avatar = (
    <Avatar
      label={userAvatarLabel(member.user)}
      imageUrl={appConfig.resolveAssetUrl(member.user.avatarUrl)}
      defaultAvatarKey={member.user.defaultAvatarKey}
      size={38}
    />
  );
  const actionCount = _memberActionCount(permission);
  const hasActions = actionCount > 0;
  const actionButtons: ReactNode[] = [];
  if (permission.canEditRoomDisplayName) {
    actionButtons.push(
      <ButtonIcon key="edit" tooltip="修改房间内用户名" icon={<Icon icon={Icons.edit_outlined} />} onPressed={onEditRoomDisplayName} size={34} />,
    );
  }
  if (permission.canRoleEdit) {
    actionButtons.push(
      <ButtonIcon
        key="admin"
        tooltip={permission.isAdmin ? '移除管理员' : '设为管理员'}
        icon={<Icon icon={permission.isAdmin ? Icons.admin_panel_settings_outlined : Icons.admin_panel_settings} />}
        selected={permission.isAdmin}
        onPressed={permission.isAdmin ? onUnsetAdmin : onSetAdmin}
        size={34}
      />,
    );
  }
  if (permission.canRemoveMember) {
    actionButtons.push(
      <ButtonIcon
        key="remove"
        tooltip="踢出此用户"
        icon={<Icon icon={Icons.person_remove_outlined} />}
        tone={ButtonTone.danger}
        onPressed={onRemoveMember}
        size={34}
      />,
    );
  }
  if (permission.canRoleEdit) {
    actionButtons.push(
      <ButtonIcon
        key="transfer"
        tooltip="转让创建者"
        icon={<Icon icon={Icons.swap_horiz} />}
        tone={ButtonTone.danger}
        onPressed={onTransferCreator}
        size={34}
      />,
    );
  }
  // SizedBox(width) + Wrap(spacing: 6, runSpacing: 8, alignment: end).
  const actions = (
    <div
      data-testid={`member-action-wrap-${member.user.id}`}
      style={{
        width: _memberActionsWidth(actionCount),
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'row',
        flexWrap: 'wrap',
        justifyContent: 'flex-end',
        alignItems: 'flex-start',
        columnGap: 6,
        rowGap: 8,
      }}
    >
      {actionButtons}
    </div>
  );

  const information = (
    <Row data-testid={`member-information-${member.user.id}`} style={{ minHeight: 48 }}>
      <div style={_rigid}>
        <UserHoverCard
          user={member.user}
          currentUser={currentUser}
          onResolveProfile={onResolveProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onEnterCommonRoom={onOpenRoom ?? undefined}
          inLive={presence === RoomMemberPresence.live}
          showRoomRole
        >
          {avatar}
        </UserHoverCard>
      </div>
      <SizedBox width={10} />
      <Expanded>
        <Column mainAxisSize="min" mainAxisAlignment="center" crossAxisAlignment="start">
          <AdaptiveHighlightedText
            text={roomMemberDisplayName(member)}
            query={query}
            comfortableLines={nameMaxLines}
            style={{ ...UiTypography.body, fontWeight: 600 }}
          />
          <SizedBox height={2} />
          <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textMuted, fontSize: 12, maxWidth: '100%' }}>
            {`@${member.user.username}`}
          </Text>
        </Column>
      </Expanded>
      <SizedBox width={10} />
      <div data-testid={`member-presence-${member.user.id}`} style={_rigid}>
        {PresencePill.member(presence)}
      </div>
      <SizedBox width={6} />
      <div data-testid={`member-role-${member.user.id}`} style={_rigid}>
        <RoleBadge label={role} padding={{ horizontal: 8, vertical: 5 }} />
      </div>
      {busy ? (
        <>
          <SizedBox width={10} />
          <CircularProgressIndicator size={18} color={UiColors.accent} strokeWidth={2} />
        </>
      ) : null}
    </Row>
  );

  if (stackActions && !busy && hasActions) {
    return (
      <_RowSurface>
        <Column crossAxisAlignment="stretch">
          {information}
          <SizedBox height={8} />
          {/* Align(alignment: Alignment.centerRight). */}
          <Row mainAxisAlignment="end">{actions}</Row>
        </Column>
      </_RowSurface>
    );
  }
  return (
    <_RowSurface>
      <Row>
        <Expanded>{information}</Expanded>
        {!busy && hasActions ? (
          <>
            <SizedBox width={8} />
            {actions}
          </>
        ) : null}
      </Row>
    </_RowSurface>
  );
}

const _displayNameStyle: CSSProperties = { ...UiTypography.body, fontWeight: 600 };
const _usernameStyle: CSSProperties = { ...UiTypography.label, color: UiColors.textMuted, fontSize: 12 };
const _presenceStyle: CSSProperties = { ...UiTypography.label, fontWeight: 600 };
const _roleStyle: CSSProperties = { ...UiTypography.label, fontSize: 12, fontWeight: 600 };

export function _memberRowNeedsStackedActions(opts: {
  availableWidth: number;
  member: RoomMember;
  live: LiveState;
  permission: RoomMemberPermissionState;
  ownerUserId: string | null;
}): boolean {
  const { availableWidth, member, live, permission, ownerUserId } = opts;
  const actionCount = _memberActionCount(permission);
  if (actionCount === 0) return false;

  const presence = roomMemberPresence(member, { live });
  const role = roomRoleLabel(member.user, { ownerUserId });
  const displayNameWidth = _memberRowTextWidth(roomMemberDisplayName(member), _displayNameStyle);
  const usernameWidth = _memberRowTextWidth(`@${member.user.username}`, _usernameStyle);
  const identityWidth = displayNameWidth > usernameWidth ? displayNameWidth : usernameWidth;
  const displayNameGuardWidth = _memberRowTextWidth('…', _displayNameStyle);
  const usernameGuardWidth = _memberRowTextWidth('…', _usernameStyle);
  const truncationGuardWidth = displayNameGuardWidth > usernameGuardWidth ? displayNameGuardWidth : usernameGuardWidth;
  const presenceWidth = 2 + 18 + 10 + 6 + _memberRowTextWidth(roomMemberPresenceLabel(presence), _presenceStyle);
  const roleWidth = 2 + 16 + _memberRowTextWidth(role, _roleStyle);
  const actionsWidth = _memberActionsWidth(actionCount);
  const measurementSafety = 2;
  const requiredWidth =
    38 +
    10 +
    identityWidth +
    10 +
    presenceWidth +
    6 +
    roleWidth +
    8 +
    actionsWidth +
    truncationGuardWidth +
    measurementSafety;
  return requiredWidth > availableWidth;
}

export function _memberRowNeedsTwoLineName(opts: {
  availableWidth: number;
  member: RoomMember;
  live: LiveState;
  ownerUserId: string | null;
  busy: boolean;
}): boolean {
  const { availableWidth, member, live, ownerUserId, busy } = opts;
  const presence = roomMemberPresence(member, { live });
  const role = roomRoleLabel(member.user, { ownerUserId });
  const presenceWidth = 2 + 18 + 10 + 6 + _memberRowTextWidth(roomMemberPresenceLabel(presence), _presenceStyle);
  const roleWidth = 2 + 16 + _memberRowTextWidth(role, _roleStyle);
  const requiredWidth =
    38 +
    10 +
    _memberRowTextWidth(roomMemberDisplayName(member), _displayNameStyle) +
    _memberRowTextWidth('…', _displayNameStyle) +
    10 +
    presenceWidth +
    6 +
    roleWidth +
    (busy ? 10 + 18 : 0) +
    2;
  return requiredWidth > availableWidth;
}

export function _memberActionCount(permission: RoomMemberPermissionState): number {
  return (permission.canEditRoomDisplayName ? 1 : 0) + (permission.canRoleEdit ? 2 : 0) + (permission.canRemoveMember ? 1 : 0);
}

export function _memberActionsWidth(actionCount: number): number {
  if (actionCount <= 0) return 0;
  return actionCount * 34 + (actionCount - 1) * 6;
}

/** `TextPainter(maxLines: 1)..layout()` width. PORT: the `BuildContext` (text direction / scaler) is dropped. */
export function _memberRowTextWidth(text: string, style: CSSProperties): number {
  return measureTextWidth(text, style);
}

export interface _EditRoomDisplayNameDialogProps {
  member: RoomMember;
}

/** Shown with `showDialog<string>`; closes with the entered name, or `undefined` when cancelled. */
export function _EditRoomDisplayNameDialog({ member }: _EditRoomDisplayNameDialogProps) {
  const route = useRoute<string>();
  const controllerRef = useRef<TextEditingController | null>(null);
  controllerRef.current ??= new TextEditingController({ text: roomMemberRoomDisplayNameValue(member) });
  const controller = controllerRef.current;
  useEffect(() => () => controller.dispose(), [controller]);

  const defaultName = roomMemberDefaultDisplayName(member);
  const originalName = roomMemberRoomDisplayNameOriginalLabel(member);
  return (
    <DialogFrame
      title={`修改${defaultName}的房间内用户名`}
      icon={Icons.edit_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() }),
        new ResponsiveDialogAction({ label: '确认修改', tone: ButtonTone.primary, onPressed: () => route.close(controller.text) }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="start">
        {/* A TextField fills the column's width even under `start` alignment. */}
        <Input controller={controller} hintText={defaultName} containerStyle={{ alignSelf: 'stretch' }} />
        <SizedBox height={UiSpacing.xs} />
        <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{`原名称：${originalName}`}</Text>
      </Column>
    </DialogFrame>
  );
}

/**
 * Port of Material's indeterminate `LinearProgressIndicator(minHeight:,
 * color:, backgroundColor:)`, drawn with the two-line Material timing. The
 * keyframes (`gang-linear-progress-*`) live in `ui/ui.css`.
 *
 * PORT: the kit has no linear indicator yet (only the circular one in
 * `lib/progress_indicator.tsx`), so this panel carries its own.
 */
export function _LinearProgressIndicator({
  minHeight = 4,
  color = UiColors.accent,
  backgroundColor = UiColors.surfaceRaised,
}: {
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
      style={{ position: 'relative', height: minHeight, width: '100%', flexShrink: 0, overflow: 'hidden', backgroundColor }}
    >
      <div style={line('gang-linear-progress-line1-head 1800ms linear infinite, gang-linear-progress-line1-tail 1800ms linear infinite')} />
      <div style={line('gang-linear-progress-line2-head 1800ms linear infinite, gang-linear-progress-line2-tail 1800ms linear infinite')} />
    </div>
  );
}

function _SearchProgress() {
  return <_LinearProgressIndicator minHeight={2} color={UiColors.accent} backgroundColor={UiColors.surfacePressed} />;
}

const _candidateEmptyStyle: CSSProperties = { ...UiTypography.label, color: UiColors.textMuted };

export interface _InviteSectionProps {
  controller: TextEditingController;
  currentUser: CurrentUser;
  query: string;
  searching: boolean;
  results: readonly UserSummary[];
  members: readonly RoomMember[];
  pendingInviteUserIds: ReadonlySet<string>;
  busyUserIds: ReadonlySet<string>;
  error: string | null;
  enabled: boolean;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onInvite: (user: UserSummary) => void;
}

export function _InviteSection({
  controller,
  currentUser,
  query,
  searching,
  results,
  members,
  pendingInviteUserIds,
  busyUserIds,
  enabled,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onInvite,
}: _InviteSectionProps) {
  const candidates = roomInviteCandidates({
    searchResults: results,
    members,
    query,
    pendingInviteUserIds,
    busyUserIds,
    invitesEnabled: enabled,
  });
  let below: ReactNode = null;
  if (searching) {
    below = (
      <>
        <SizedBox height={10} />
        <_SearchProgress />
      </>
    );
  } else if (enabled && query.length >= 2) {
    below = (
      <>
        <SizedBox height={8} />
        {candidates.length === 0 ? (
          <Text style={_candidateEmptyStyle}>未找到用户</Text>
        ) : (
          candidates.map((candidate, index) => (
            <Column key={candidate.user.id} crossAxisAlignment="stretch">
              <_InviteUserRow
                user={candidate.user}
                currentUser={currentUser}
                query={query}
                alreadyMember={candidate.existing}
                pending={candidate.pending}
                busy={candidate.busy}
                enabled={enabled}
                onResolveProfile={onResolveProfile}
                onResolveRoomProfile={onResolveRoomProfile}
                onOpenRoom={onOpenRoom}
                onInvite={() => onInvite(candidate.user)}
              />
              {index < candidates.length - 1 ? <SizedBox height={6} /> : null}
            </Column>
          ))
        )}
      </>
    );
  }
  return (
    <SettingsCard title="邀请成员">
      {[
        <Column key="invite" crossAxisAlignment="stretch">
          <Input
            controller={controller}
            hintText="按用户名、昵称或 UID 搜索"
            prefixIcon={Icons.person_add_alt_1}
            enabled={enabled}
            showClearButton
          />
          {below}
        </Column>,
      ]}
    </SettingsCard>
  );
}

const _rowPrimaryNameStyle: CSSProperties = { ...UiTypography.label, color: UiColors.text, fontWeight: 600, maxWidth: '100%' };
const _rowUsernameStyle: CSSProperties = { ...UiTypography.label, color: UiColors.textMuted, maxWidth: '100%' };

function _UserRowAvatar({
  user,
  currentUser,
  size,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
}: {
  user: UserSummary;
  currentUser: CurrentUser;
  size: number;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
}) {
  const appConfig = useAppConfig();
  return (
    <div style={_rigid}>
      <UserHoverCard
        user={user}
        currentUser={currentUser}
        onResolveProfile={onResolveProfile}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onOpenRoom ?? undefined}
      >
        <Avatar
          label={userAvatarLabel(user)}
          imageUrl={appConfig.resolveAssetUrl(user.avatarUrl)}
          defaultAvatarKey={user.defaultAvatarKey}
          size={size}
        />
      </UserHoverCard>
    </div>
  );
}

export interface _InviteUserRowProps {
  user: UserSummary;
  currentUser: CurrentUser;
  query: string;
  alreadyMember: boolean;
  pending: boolean;
  busy: boolean;
  enabled: boolean;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onInvite: () => void;
}

export function _InviteUserRow({
  user,
  currentUser,
  query,
  alreadyMember,
  pending,
  busy,
  enabled,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onInvite,
}: _InviteUserRowProps) {
  const actionLabel = alreadyMember ? '在房间内' : pending ? '已邀请' : '邀请';
  return (
    <_RowSurface compact>
      <Row>
        <_UserRowAvatar
          user={user}
          currentUser={currentUser}
          size={32}
          onResolveProfile={onResolveProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onOpenRoom={onOpenRoom}
        />
        <SizedBox width={9} />
        <Expanded>
          <Column mainAxisSize="min" crossAxisAlignment="start">
            <HighlightedText text={userPrimaryName(user)} query={query} maxLines={1} overflow="ellipsis" style={_rowPrimaryNameStyle} />
            <SizedBox height={2} />
            <HighlightedText text={userUsernameLabel(user)} query={query} maxLines={1} overflow="ellipsis" style={_rowUsernameStyle} />
          </Column>
        </Expanded>
        <Button height={34} loading={busy} onPressed={!enabled || alreadyMember || pending ? null : onInvite}>
          {actionLabel}
        </Button>
      </Row>
    </_RowSurface>
  );
}

export interface _BlockUserSectionProps {
  controller: TextEditingController;
  currentUser: CurrentUser;
  query: string;
  searching: boolean;
  results: readonly UserSummary[];
  members: readonly RoomMember[];
  blacklist: readonly RoomBlacklistEntry[];
  busyUserIds: ReadonlySet<string>;
  error: string | null;
  enabled: boolean;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onBlock: (user: UserSummary) => void;
  onUnblock: (user: UserSummary) => void;
}

export function _BlockUserSection({
  controller,
  currentUser,
  query,
  searching,
  results,
  members,
  blacklist,
  busyUserIds,
  enabled,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onBlock,
  onUnblock,
}: _BlockUserSectionProps) {
  const candidates = roomBlacklistCandidates({
    searchResults: results,
    blacklist,
    members,
    query,
    busyUserIds,
  });
  let below: ReactNode = null;
  if (searching) {
    below = (
      <>
        <SizedBox height={10} />
        <_SearchProgress />
      </>
    );
  } else if (enabled && query.length >= 2) {
    below = (
      <>
        <SizedBox height={8} />
        {candidates.length === 0 ? (
          <Text style={_candidateEmptyStyle}>未找到用户</Text>
        ) : (
          candidates.map((candidate, index) => (
            <Column key={candidate.user.id} crossAxisAlignment="stretch">
              <_BlockUserRow
                user={candidate.user}
                currentUser={currentUser}
                query={query}
                member={candidate.member}
                blocked={candidate.blocked}
                superuser={candidate.superuser}
                busy={candidate.busy}
                enabled={enabled}
                onResolveProfile={onResolveProfile}
                onResolveRoomProfile={onResolveRoomProfile}
                onOpenRoom={onOpenRoom}
                onBlock={() => onBlock(candidate.user)}
                onUnblock={() => onUnblock(candidate.user)}
              />
              {index < candidates.length - 1 ? <SizedBox height={6} /> : null}
            </Column>
          ))
        )}
      </>
    );
  }
  return (
    <SettingsCard title="拉黑用户">
      {[
        <Column key="block" crossAxisAlignment="stretch">
          <Input
            controller={controller}
            hintText="按用户名、昵称或 UID 搜索"
            prefixIcon={Icons.block}
            enabled={enabled}
            showClearButton
            textInputAction={TextInputAction.search}
          />
          {below}
        </Column>,
      ]}
    </SettingsCard>
  );
}

export interface _BlockUserRowProps {
  user: UserSummary;
  currentUser: CurrentUser;
  query: string;
  member: boolean;
  blocked: boolean;
  superuser: boolean;
  busy: boolean;
  enabled: boolean;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onBlock: () => void;
  onUnblock: () => void;
}

export function _BlockUserRow({
  user,
  currentUser,
  query,
  member,
  blocked,
  superuser,
  busy,
  enabled,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onBlock,
  onUnblock,
}: _BlockUserRowProps) {
  const actionLabel = roomBlacklistActionLabel({ member, superuser, blocked });
  const onPressed = !enabled || member || superuser ? null : blocked ? onUnblock : onBlock;
  return (
    <_RowSurface compact>
      <Row>
        <_UserRowAvatar
          user={user}
          currentUser={currentUser}
          size={32}
          onResolveProfile={onResolveProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onOpenRoom={onOpenRoom}
        />
        <SizedBox width={9} />
        <Expanded>
          <Column mainAxisSize="min" crossAxisAlignment="start">
            <HighlightedText text={userPrimaryName(user)} query={query} maxLines={1} overflow="ellipsis" style={_rowPrimaryNameStyle} />
            <SizedBox height={2} />
            <HighlightedText text={userUsernameLabel(user)} query={query} maxLines={1} overflow="ellipsis" style={_rowUsernameStyle} />
          </Column>
        </Expanded>
        <Button height={34} loading={busy} tone={blocked ? ButtonTone.neutral : ButtonTone.danger} onPressed={onPressed}>
          {actionLabel}
        </Button>
      </Row>
    </_RowSurface>
  );
}

const _sectionEmptyStyle: CSSProperties = { ...UiTypography.label, color: UiColors.textMuted };

export interface _BlacklistSectionProps {
  entries: readonly RoomBlacklistEntry[];
  currentUser: CurrentUser;
  busyUserIds: ReadonlySet<string>;
  loading: boolean;
  error: string | null;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onUnblock: (user: UserSummary) => void;
}

export function _BlacklistSection({
  entries,
  currentUser,
  busyUserIds,
  loading,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onUnblock,
}: _BlacklistSectionProps) {
  let content: ReactNode;
  if (loading) {
    content = (
      <>
        <SizedBox height={8} />
        <_SearchProgress />
      </>
    );
  } else if (entries.length === 0) {
    content = (
      <div style={{ paddingTop: 12, paddingBottom: 6 }}>
        <Text style={_sectionEmptyStyle}>暂无黑名单用户</Text>
      </div>
    );
  } else {
    content = entries.map((entry, index) => (
      <Column key={entry.user.id} crossAxisAlignment="stretch">
        <_BlacklistUserRow
          user={entry.user}
          currentUser={currentUser}
          busy={busyUserIds.has(entry.user.id)}
          onResolveProfile={onResolveProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onOpenRoom={onOpenRoom}
          onUnblock={() => onUnblock(entry.user)}
        />
        {index < entries.length - 1 ? <SizedBox height={6} /> : null}
      </Column>
    ));
  }
  return (
    <SettingsCard title="黑名单">
      {[
        <Column key="blacklist" crossAxisAlignment="stretch" style={{ minHeight: 64 }}>
          {content}
        </Column>,
      ]}
    </SettingsCard>
  );
}

export interface _BlacklistUserRowProps {
  user: UserSummary;
  currentUser: CurrentUser;
  busy: boolean;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onUnblock: () => void;
}

export function _BlacklistUserRow({
  user,
  currentUser,
  busy,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onUnblock,
}: _BlacklistUserRowProps) {
  return (
    <_RowSurface compact>
      <Row>
        <_UserRowAvatar
          user={user}
          currentUser={currentUser}
          size={32}
          onResolveProfile={onResolveProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onOpenRoom={onOpenRoom}
        />
        <SizedBox width={9} />
        <Expanded>
          <Column mainAxisSize="min" crossAxisAlignment="start">
            <Text maxLines={1} style={{ ...UiTypography.label, fontWeight: 600, maxWidth: '100%' }}>
              {userPrimaryName(user)}
            </Text>
            <SizedBox height={2} />
            <Text maxLines={1} style={_rowUsernameStyle}>
              {userUsernameLabel(user)}
            </Text>
          </Column>
        </Expanded>
        <Button height={34} loading={busy} onPressed={onUnblock}>
          取消拉黑
        </Button>
      </Row>
    </_RowSurface>
  );
}

export interface _JoinRequestsSectionProps {
  requests: readonly JoinRequest[];
  currentUser: CurrentUser;
  busyRequestIds: ReadonlySet<string>;
  activeDetailRequestId: string | null;
  error: string | null;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onDetail: (request: JoinRequest) => void;
  onApprove: (request: JoinRequest) => void;
  onReject: (request: JoinRequest) => void;
}

export function _JoinRequestsSection({
  requests,
  currentUser,
  busyRequestIds,
  activeDetailRequestId,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onDetail,
  onApprove,
  onReject,
}: _JoinRequestsSectionProps) {
  return (
    <SettingsCard title="加入申请">
      {[
        <Column key="requests" crossAxisAlignment="stretch" style={{ minHeight: 64 }}>
          {requests.length === 0 ? (
            <div style={{ paddingTop: 12, paddingBottom: 6 }}>
              <Text style={_sectionEmptyStyle}>暂无待处理申请</Text>
            </div>
          ) : (
            requests.map((request, index) => (
              <Column key={request.id} crossAxisAlignment="stretch">
                <_JoinRequestRow
                  request={request}
                  currentUser={currentUser}
                  busy={busyRequestIds.has(request.id)}
                  detailActive={activeDetailRequestId === request.id}
                  onResolveProfile={onResolveProfile}
                  onResolveRoomProfile={onResolveRoomProfile}
                  onOpenRoom={onOpenRoom}
                  onDetail={() => onDetail(request)}
                  onApprove={() => onApprove(request)}
                  onReject={() => onReject(request)}
                />
                {index < requests.length - 1 ? <SizedBox height={6} /> : null}
              </Column>
            ))
          )}
        </Column>,
      ]}
    </SettingsCard>
  );
}

export interface _JoinRequestRowProps {
  request: JoinRequest;
  currentUser: CurrentUser;
  busy: boolean;
  detailActive: boolean;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
  onDetail: () => void;
  onApprove: () => void;
  onReject: () => void;
}

export function _JoinRequestRow({
  request,
  currentUser,
  busy,
  detailActive,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
  onDetail,
  onApprove,
  onReject,
}: _JoinRequestRowProps) {
  return (
    <_RowSurface compact>
      <Row>
        <_UserRowAvatar
          user={request.user}
          currentUser={currentUser}
          size={32}
          onResolveProfile={onResolveProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onOpenRoom={onOpenRoom}
        />
        <SizedBox width={9} />
        <Expanded>
          <Text maxLines={1} style={{ ...UiTypography.label, fontWeight: 600 }}>
            {userPrimaryName(request.user)}
          </Text>
        </Expanded>
        {busy ? (
          <CircularProgressIndicator size={18} color={UiColors.accent} strokeWidth={2} />
        ) : (
          <>
            <ButtonIcon tooltip="详情" icon={<Icon icon={Icons.info_outline} />} selected={detailActive} onPressed={onDetail} size={32} />
            <SizedBox width={6} />
            <ButtonIcon tooltip="拒绝" icon={<Icon icon={Icons.close} />} tone={ButtonTone.danger} onPressed={onReject} size={32} />
            <SizedBox width={6} />
            <ButtonIcon tooltip="通过" icon={<Icon icon={Icons.check} />} tone={ButtonTone.primary} onPressed={onApprove} size={32} />
          </>
        )}
      </Row>
    </_RowSurface>
  );
}

export interface _JoinRequestDetailsDialogProps {
  request: JoinRequest;
  currentUser: CurrentUser;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
}

export function _JoinRequestDetailsDialog({
  request,
  currentUser,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
}: _JoinRequestDetailsDialogProps) {
  const route = useRoute<void>();
  return (
    <DialogFrame
      title="申请详情"
      icon={Icons.fact_check_outlined}
      actions={[
        <Button key="close" onPressed={() => route.close()}>
          关闭
        </Button>,
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <_JoinRequestDetailBlock label="来源">
          <_JoinRequestSourceContent
            request={request}
            currentUser={currentUser}
            onResolveProfile={onResolveProfile}
            onResolveRoomProfile={onResolveRoomProfile}
            onOpenRoom={onOpenRoom}
          />
        </_JoinRequestDetailBlock>
        <SizedBox height={14} />
        <_JoinRequestDetailBlock label="申请理由">
          <Text style={UiTypography.body}>{joinRequestDetailReasonText(request)}</Text>
        </_JoinRequestDetailBlock>
      </Column>
    </DialogFrame>
  );
}

export function _JoinRequestDetailBlock({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Column crossAxisAlignment="stretch">
      <Text style={{ ...UiTypography.label, color: UiColors.textMuted, fontWeight: 600 }}>{label}</Text>
      <SizedBox height={6} />
      {children}
    </Column>
  );
}

export interface _JoinRequestSourceContentProps {
  request: JoinRequest;
  currentUser: CurrentUser;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
}

export function _JoinRequestSourceContent({
  request,
  currentUser,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
}: _JoinRequestSourceContentProps) {
  if (!joinRequestFromInvitation(request) || request.inviters.length === 0) {
    return <Text style={{ ...UiTypography.body, fontWeight: 600 }}>{joinRequestSourceText(request)}</Text>;
  }

  return (
    <Column crossAxisAlignment="stretch">
      {request.inviters.map((inviter, index) => (
        <Column key={`${inviter.id}-${index}`} crossAxisAlignment="stretch">
          <_JoinRequestInviterSourceLine
            user={inviter}
            currentUser={currentUser}
            onResolveProfile={onResolveProfile}
            onResolveRoomProfile={onResolveRoomProfile}
            onOpenRoom={onOpenRoom}
          />
          {index < request.inviters.length - 1 ? <SizedBox height={6} /> : null}
        </Column>
      ))}
    </Column>
  );
}

export interface _JoinRequestInviterSourceLineProps {
  user: UserSummary;
  currentUser: CurrentUser;
  onResolveProfile: _UserProfileResolver;
  onResolveRoomProfile: RoomProfileResolver;
  onOpenRoom: ((room: PublicRoom) => void) | null | undefined;
}

export function _JoinRequestInviterSourceLine({
  user,
  currentUser,
  onResolveProfile,
  onResolveRoomProfile,
  onOpenRoom,
}: _JoinRequestInviterSourceLineProps) {
  return (
    <Row>
      <_UserRowAvatar
        user={user}
        currentUser={currentUser}
        size={28}
        onResolveProfile={onResolveProfile}
        onResolveRoomProfile={onResolveRoomProfile}
        onOpenRoom={onOpenRoom}
      />
      <SizedBox width={8} />
      <Expanded>
        <Text maxLines={1} style={{ ...UiTypography.body, fontWeight: 600 }}>
          {`${userPrimaryName(user)} 的邀请`}
        </Text>
      </Expanded>
    </Row>
  );
}
