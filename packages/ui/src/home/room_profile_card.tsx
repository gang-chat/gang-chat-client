import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

import * as room_display from '@gang-chat/core/app/room_display';
import { PublicRoom, type CurrentUser, type UserCommonRoom, type UserSummary } from '@gang-chat/core/protocol/models';

import { resolveEdgeInsets, EdgeInsets } from '../lib/edge_insets';
import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { measureTextWidth } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { useViewState, ViewState } from '../lib/view_state';
import { AnchoredPanel } from '../ui/anchored_panel';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar, StatusBadge } from '../ui/avatar';
import { Button, ButtonTone } from '../ui/button';
import { genderMark } from '../ui/gender_mark';
import { Icon, Icons, type IconData } from '../ui/icon';
import { PresencePill } from '../ui/member_presence_icons';
import { pushRoute } from '../ui/overlay';
import type { WindowPoint } from '../ui/platform_gestures';
import { RoleBadge } from '../ui/role_badge';
import { UiColors, UiCurves, UiSpacing, UiTypography } from '../ui/tokens';
import { ChatImagePreviewActions, ChatImagePreviewActionsScope, showChatImagePreview } from './chat_image_preview';
import {
  HoverCardAnchor,
  HoverCardSelectableText,
  HoverCardTapRegionScope,
  hoverCardDefaultWidth,
  hoverCardOverlaySafeInsets,
} from './hover_card_anchor';

export type RoomProfileResolver = (room: PublicRoom) => Promise<PublicRoom>;
export type UserProfileResolver = (user: UserSummary) => Promise<UserSummary>;
export type UserProfileActionBuilder = (user: UserSummary) => UserProfileAction | null;

export class UserProfileAction {
  constructor(opts: { label: string; icon: IconData; onPressed: () => void }) {
    this.label = opts.label;
    this.icon = opts.icon;
    this.onPressed = opts.onPressed;
  }

  readonly label: string;
  readonly icon: IconData;
  readonly onPressed: () => void;
}

// ---------------------------------------------------------------------------
// RoomHoverCard

export interface RoomHoverCardProps {
  room: PublicRoom;
  currentUser: CurrentUser;
  children?: ReactNode;
  onResolveRoom?: RoomProfileResolver | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onEnterRoom?: ((room: PublicRoom) => void) | null;
}

class _RoomHoverCardState extends ViewState<RoomHoverCardProps> {
  _resolved: PublicRoom | null = null;
  _resolveFuture: Promise<void> | null = null;

  get _displayRoom(): PublicRoom {
    return this._resolved ?? this.widget.room;
  }

  override didUpdateProps(oldWidget: RoomHoverCardProps): void {
    const roomChanged = oldWidget.room.id !== this.widget.room.id;
    const deletionChanged = oldWidget.room.isDeleted !== this.widget.room.isDeleted;
    const resolverPresenceChanged = (oldWidget.onResolveRoom == null) !== (this.widget.onResolveRoom == null);
    if (!roomChanged && !deletionChanged && !resolverPresenceChanged) return;
    // PORT: didUpdateProps runs after the render that received the new
    // props, so the reset re-renders (Flutter reset before build).
    this.setState(() => {
      this._resolved = null;
      this._resolveFuture = null;
    });
  }

  _resolveRoom = (): Promise<void> => {
    if (this.widget.room.isDeleted) return Promise.resolve();
    const resolver = this.widget.onResolveRoom;
    if (resolver == null) return Promise.resolve();
    const existing = this._resolveFuture;
    if (existing != null) return existing;

    const requestedRoom = this.widget.room;
    const future = (async () => {
      try {
        const room = await resolver(requestedRoom);
        if (!this.mounted || this.widget.room.id !== requestedRoom.id) return;
        this.setState(() => {
          this._resolved = room;
        });
      } catch {
        if (!this.mounted || this.widget.room.id !== requestedRoom.id) return;
        // Fall back to the current lightweight summary instead of keeping a
        // previously resolved card that may now be stale.
        this.setState(() => {
          this._resolved = null;
        });
      } finally {
        if (this.mounted && this.widget.room.id === requestedRoom.id) {
          this._resolveFuture = null;
        }
      }
    })();
    this._resolveFuture = future;
    return future;
  };

  build(): ReactNode {
    const widget = this.widget;
    return (
      <HoverCardAnchor
        resetKey={JSON.stringify([
          widget.room.id,
          widget.room.isDeleted,
          widget.onResolveRoom != null,
          widget.onResolveUserProfile != null,
        ])}
        onBeforeOpen={widget.room.isDeleted || widget.onResolveRoom == null ? null : this._resolveRoom}
        cardBuilder={() => (
          <_RoomProfileCard
            room={this._displayRoom}
            currentUser={widget.currentUser}
            onResolveRoomProfile={widget.onResolveRoom}
            onResolveUserProfile={widget.onResolveUserProfile}
            onEnterRoom={widget.onEnterRoom}
          />
        )}
      >
        {widget.children}
      </HoverCardAnchor>
    );
  }
}

export function RoomHoverCard(props: RoomHoverCardProps) {
  const state = useViewState(() => new _RoomHoverCardState(), props);
  return <>{state.build()}</>;
}

// ---------------------------------------------------------------------------
// UserHoverCard

export interface UserHoverCardProps {
  user: UserSummary;
  children?: ReactNode;
  currentUser?: CurrentUser | null;
  inLive?: boolean;
  showRoomRole?: boolean;

  /**
   * Fetches a richer, up-to-date profile (gender, common rooms) before the
   * card opens. When null the card shows only the supplied summary.
   */
  onResolveProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
}

/**
 * Shows the user profile card as a popup at [position] (window coordinates).
 * Port of `showUserProfileCardAtPosition(context, …)`; the `BuildContext`
 * argument is dropped.
 */
export function showUserProfileCardAtPosition(opts: {
  position: WindowPoint;
  user: UserSummary;
  currentUser?: CurrentUser | null;
  onResolveProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  profileActionBuilder?: UserProfileActionBuilder | null;
  inLive?: boolean;
  showRoomRole?: boolean;
  resolveOnOpen?: boolean;
}): Promise<void> {
  const { inLive = false, showRoomRole = false, resolveOnOpen = true } = opts;
  // Port of `_UserProfileCardPopupRoute`: a transparent, dismissible barrier.
  const route = pushRoute<void>(
    () => (
      <_UserProfileCardPopup
        position={opts.position}
        user={opts.user}
        currentUser={opts.currentUser ?? null}
        onResolveProfile={opts.onResolveProfile ?? null}
        onResolveRoomProfile={opts.onResolveRoomProfile ?? null}
        onEnterCommonRoom={opts.onEnterCommonRoom ?? null}
        profileActionBuilder={opts.profileActionBuilder ?? null}
        inLive={inLive}
        showRoomRole={showRoomRole}
        resolveOnOpen={resolveOnOpen}
      />
    ),
    { barrierColor: UiColors.transparent, barrierDismissible: true },
  );
  return route.result.then(() => undefined);
}

class _UserHoverCardState extends ViewState<UserHoverCardProps> {
  _resolved: UserSummary | null = null;
  _resolveFuture: Promise<void> | null = null;

  get _displayUser(): UserSummary {
    return this._resolved ?? this.widget.user;
  }

  override didUpdateProps(oldWidget: UserHoverCardProps): void {
    const widget = this.widget;
    const userChanged = oldWidget.user.id !== widget.user.id;
    const deletionChanged = oldWidget.user.isDeleted !== widget.user.isDeleted;
    const suspensionChanged = oldWidget.user.isSuspended !== widget.user.isSuspended;
    const currentUserChanged = oldWidget.currentUser?.id !== widget.currentUser?.id;
    const resolverPresenceChanged = (oldWidget.onResolveProfile == null) !== (widget.onResolveProfile == null);
    const liveChanged = (oldWidget.inLive ?? false) !== (widget.inLive ?? false);
    const roleVisibilityChanged = (oldWidget.showRoomRole ?? false) !== (widget.showRoomRole ?? false);
    const actionPresenceChanged = (oldWidget.profileActionBuilder == null) !== (widget.profileActionBuilder == null);
    if (
      !userChanged &&
      !deletionChanged &&
      !suspensionChanged &&
      !currentUserChanged &&
      !resolverPresenceChanged &&
      !liveChanged &&
      !roleVisibilityChanged &&
      !actionPresenceChanged
    ) {
      return;
    }
    // PORT: see _RoomHoverCardState.didUpdateProps.
    this.setState(() => {
      this._resolved = null;
      this._resolveFuture = null;
    });
  }

  _resolveProfile = (): Promise<void> => {
    if (this.widget.user.isDeleted) return Promise.resolve();
    const resolver = this.widget.onResolveProfile;
    if (resolver == null) return Promise.resolve();
    const existing = this._resolveFuture;
    if (existing != null) return existing;

    const requestedUser = this.widget.user;
    const future = (async () => {
      try {
        const profile = await resolver(requestedUser);
        if (!this.mounted || this.widget.user.id !== requestedUser.id) return;
        this.setState(() => {
          this._resolved = profile;
        });
      } catch {
        if (!this.mounted || this.widget.user.id !== requestedUser.id) return;
        // Keep showing the current lightweight summary on failure.
        this.setState(() => {
          this._resolved = null;
        });
      } finally {
        if (this.mounted && this.widget.user.id === requestedUser.id) {
          this._resolveFuture = null;
        }
      }
    })();
    this._resolveFuture = future;
    return future;
  };

  build(): ReactNode {
    const widget = this.widget;
    const inLive = widget.inLive ?? false;
    const showRoomRole = widget.showRoomRole ?? false;
    return (
      <HoverCardAnchor
        resetKey={JSON.stringify([
          widget.user.id,
          widget.user.isDeleted,
          widget.user.isSuspended,
          widget.onResolveProfile != null,
          widget.onResolveRoomProfile != null,
          widget.currentUser?.id ?? null,
          inLive,
          showRoomRole,
          widget.profileActionBuilder != null,
        ])}
        onBeforeOpen={widget.user.isDeleted || widget.onResolveProfile == null ? null : this._resolveProfile}
        cardBuilder={() => {
          const displayUser = this._displayUser;
          return (
            <_UserProfileCard
              user={displayUser}
              currentUser={widget.currentUser ?? null}
              onResolveUserProfile={widget.onResolveProfile}
              onResolveRoomProfile={widget.onResolveRoomProfile}
              onEnterCommonRoom={widget.onEnterCommonRoom}
              inLive={inLive}
              showRoomRole={showRoomRole}
              action={displayUser.isDeleted ? null : (widget.profileActionBuilder?.(displayUser) ?? null)}
            />
          );
        }}
      >
        {widget.children}
      </HoverCardAnchor>
    );
  }
}

export function UserHoverCard(props: UserHoverCardProps) {
  const state = useViewState(() => new _UserHoverCardState(), props);
  return <>{state.build()}</>;
}

// ---------------------------------------------------------------------------
// Popup route (showUserProfileCardAtPosition)

const _popupScreenPadding = 8;
const _popupGap = 10;
const _popupTransitionDuration = 110;

/** Port of `_UserProfileCardPopupLayoutDelegate.getPositionForChild`. */
export function _userProfileCardPopupPosition(
  anchor: WindowPoint,
  childSize: { width: number; height: number },
  size: { width: number; height: number },
  safeInsets: { left: number; top: number; right: number; bottom: number },
): WindowPoint {
  const minX = safeInsets.left + _popupScreenPadding;
  const maxX = Math.max(minX, size.width - safeInsets.right - childSize.width - _popupScreenPadding);
  const x = Math.min(Math.max(anchor.x, minX), maxX);
  let y = anchor.y + _popupGap;
  const safeBottom = size.height - safeInsets.bottom - _popupScreenPadding;
  if (y + childSize.height > safeBottom) {
    y = anchor.y - childSize.height - _popupGap;
  }
  const minY = safeInsets.top + _popupScreenPadding;
  const maxY = Math.max(minY, safeBottom - childSize.height);
  y = Math.min(Math.max(y, minY), maxY);
  return { x, y };
}

interface _UserProfileCardPopupProps {
  position: WindowPoint;
  user: UserSummary;
  currentUser: CurrentUser | null;
  onResolveProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  profileActionBuilder: UserProfileActionBuilder | null;
  inLive: boolean;
  showRoomRole: boolean;
  resolveOnOpen: boolean;
}

function _UserProfileCardPopup(props: _UserProfileCardPopupProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [placed, setPlaced] = useState<WindowPoint | null>(null);
  const safeInsets = resolveEdgeInsets(hoverCardOverlaySafeInsets());
  const windowWidth = window.innerWidth;
  const windowHeight = window.innerHeight;
  // getConstraintsForChild: the card stays inside the safe window area.
  const maxWidth = Math.max(0, windowWidth - safeInsets.left - safeInsets.right - _popupScreenPadding * 2);
  const maxHeight = Math.max(0, windowHeight - safeInsets.top - safeInsets.bottom - _popupScreenPadding * 2);

  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const next = _userProfileCardPopupPosition(
      props.position,
      { width: node.offsetWidth, height: node.offsetHeight },
      { width: window.innerWidth, height: window.innerHeight },
      safeInsets,
    );
    if (placed == null || placed.x !== next.x || placed.y !== next.y) setPlaced(next);
  });

  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null || placed == null || typeof node.animate !== 'function') return;
    // FadeTransition(CurvedAnimation(easeOutCubic)) over the 110ms route.
    // PORT: the reverse fade on dismissal is not replayed; the overlay
    // removes the route immediately.
    node.animate([{ opacity: 0 }, { opacity: 1 }], { duration: _popupTransitionDuration, easing: UiCurves.easeOutCubic });
    // Only on first placement.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [placed == null]);

  return (
    <div
      ref={ref}
      aria-label="用户名片"
      style={{
        position: 'fixed',
        left: placed?.x ?? 0,
        top: placed?.y ?? 0,
        visibility: placed == null ? 'hidden' : 'visible',
        pointerEvents: 'auto',
        maxWidth,
        maxHeight,
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <AnchoredPanel width={hoverCardDefaultWidth} style={{ maxWidth: '100%', maxHeight, overflowY: 'auto' }}>
        <_ResolvingUserProfileCard
          user={props.user}
          currentUser={props.currentUser}
          onResolveProfile={props.onResolveProfile}
          onResolveRoomProfile={props.onResolveRoomProfile}
          onEnterCommonRoom={props.onEnterCommonRoom}
          profileActionBuilder={props.profileActionBuilder}
          inLive={props.inLive}
          showRoomRole={props.showRoomRole}
          resolveOnOpen={props.resolveOnOpen}
        />
      </AnchoredPanel>
    </div>
  );
}

interface _ResolvingUserProfileCardProps {
  user: UserSummary;
  currentUser: CurrentUser | null;
  onResolveProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  profileActionBuilder: UserProfileActionBuilder | null;
  inLive: boolean;
  showRoomRole: boolean;
  resolveOnOpen: boolean;
}

class _ResolvingUserProfileCardState extends ViewState<_ResolvingUserProfileCardProps> {
  _resolved: UserSummary | null = null;

  get _displayUser(): UserSummary {
    return this._resolved ?? this.widget.user;
  }

  override initState(): void {
    if (this.widget.resolveOnOpen && !this.widget.user.isDeleted) void this._resolve();
  }

  async _resolve(): Promise<void> {
    if (this.widget.user.isDeleted) return;
    const resolver = this.widget.onResolveProfile;
    if (resolver == null) return;
    const requestedUser = this.widget.user;
    try {
      const profile = await resolver(requestedUser);
      if (!this.mounted || this.widget.user.id !== requestedUser.id) return;
      this.setState(() => {
        this._resolved = profile;
      });
    } catch {
      if (!this.mounted || this.widget.user.id !== requestedUser.id) return;
      this.setState(() => {
        this._resolved = null;
      });
    }
  }

  build(): ReactNode {
    const widget = this.widget;
    const user = this._displayUser;
    return (
      <_UserProfileCard
        user={user}
        currentUser={widget.currentUser}
        onResolveUserProfile={widget.onResolveProfile}
        onResolveRoomProfile={widget.onResolveRoomProfile}
        onEnterCommonRoom={widget.onEnterCommonRoom}
        inLive={widget.inLive}
        showRoomRole={widget.showRoomRole}
        action={user.isDeleted ? null : (widget.profileActionBuilder?.(user) ?? null)}
      />
    );
  }
}

function _ResolvingUserProfileCard(props: _ResolvingUserProfileCardProps) {
  const state = useViewState(() => new _ResolvingUserProfileCardState(), props);
  return <>{state.build()}</>;
}

// ---------------------------------------------------------------------------
// User card

interface _UserProfileCardProps {
  user: UserSummary;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  inLive?: boolean;
  showRoomRole?: boolean;
  action?: UserProfileAction | null;
}

// Flutter's TextField (behind HoverCardSelectableText) always fills the
// width its parent offers, so columns holding one stretch their children;
// plain Text left-aligned in the stretched box looks the same as Flutter's
// CrossAxisAlignment.start.
const _cardColumnStyle: CSSProperties = { padding: UiSpacing.lg, boxSizing: 'border-box' };

export function _UserProfileCard({
  user,
  currentUser = null,
  onResolveUserProfile = null,
  onResolveRoomProfile = null,
  onEnterCommonRoom = null,
  inLive = false,
  showRoomRole = false,
  action = null,
}: _UserProfileCardProps) {
  const config = useAppConfig();
  if (user.isDeleted) return <_DeletedUserProfileCard />;
  const name = room_display.userPrimaryName(user);
  const gender = genderMark(user.gender);
  const role = showRoomRole ? room_display.roomRoleLabel(user) : null;
  const isCurrentUser = currentUser?.id === user.id;
  const online = !user.isSuspended && (inLive || isCurrentUser || (user.isOnline ?? false));
  const presencePill = user.isSuspended
    ? PresencePill.suspended()
    : inLive
      ? PresencePill.voice()
      : online
        ? PresencePill.online()
        : PresencePill.offline();
  const bio = user.bio?.trim() ?? null;
  const uid = user.uid?.trim() ?? null;
  const commonRooms = user.commonRooms.filter((r) => r.isUsable);
  const avatarUrl = config.resolveAssetUrl(user.avatarUrl);
  const previewAvatarUrl = _nonEmpty(user.avatarUrl) == null ? null : _nonEmpty(avatarUrl);

  return (
    <Column mainAxisSize="min" crossAxisAlignment="stretch" style={_cardColumnStyle}>
      <Row crossAxisAlignment="start">
        <_ProfileImagePreview
          testId="user-profile-card-avatar-preview"
          imageUrl={previewAvatarUrl}
          suggestedName={`${room_display.userPrimaryName(user)}-avatar.png`}
        >
          <Avatar
            label={room_display.userAvatarLabel(user)}
            imageUrl={avatarUrl}
            defaultAvatarKey={user.defaultAvatarKey}
            size={48}
            active={online}
            activeBorderWidth={2}
            activeBorderColor={inLive ? UiColors.presenceVoice : null}
            paintBorderOnForeground
          />
        </_ProfileImagePreview>
        <SizedBox width={UiSpacing.md} />
        <Expanded style={{ alignItems: 'stretch' }}>
          <Column crossAxisAlignment="stretch" mainAxisSize="min">
            <Row crossAxisAlignment="start">
              {/* PORT: a Flexible TextField takes all the room it is offered,
                  so this is an Expanded in CSS terms. */}
              <Expanded>
                <HoverCardSelectableText value={name} style={{ ...UiTypography.title, fontSize: 16 }} maxLines={12} />
              </Expanded>
              {gender != null ? (
                <>
                  <SizedBox width={5} />
                  <Text maxLines={1} style={{ ...UiTypography.title, color: gender.color, fontSize: 17, fontWeight: 900 }}>
                    {gender.symbol}
                  </Text>
                </>
              ) : null}
            </Row>
            <SizedBox height={2} />
            <HoverCardSelectableText
              value={`@${user.username}`}
              copyStartOffset={1}
              maxLines={12}
              style={{ ...UiTypography.label, color: UiColors.textMuted }}
            />
          </Column>
        </Expanded>
      </Row>
      <SizedBox height={UiSpacing.md} />
      <_Wrap spacing={6} runSpacing={6}>
        {presencePill}
        {role != null ? <RoleBadge label={role} /> : null}
      </_Wrap>
      {bio != null && bio.length > 0 ? (
        <>
          <SizedBox height={UiSpacing.md} />
          <HoverCardSelectableText value={bio} maxLines={4} style={{ ...UiTypography.body, color: UiColors.textSecondary }} />
        </>
      ) : null}
      {commonRooms.length > 0 ? (
        <>
          <SizedBox height={UiSpacing.md} />
          <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{`${commonRooms.length} 个共同房间`}</Text>
          <SizedBox height={UiSpacing.xs} />
          <_UserCommonRoomList
            rooms={commonRooms}
            currentUser={currentUser}
            onResolveUserProfile={onResolveUserProfile}
            onResolveRoomProfile={onResolveRoomProfile}
            onEnterRoom={onEnterCommonRoom}
          />
        </>
      ) : null}
      {uid != null && uid.length > 0 ? (
        <>
          <SizedBox height={UiSpacing.sm} />
          <HoverCardSelectableText
            value={`UID: ${uid}`}
            copyStartOffset={'UID: '.length}
            style={{ ...UiTypography.label, color: UiColors.textMuted }}
          />
        </>
      ) : null}
      {action != null ? (
        <>
          <SizedBox height={UiSpacing.md} />
          {/* Center(child: Button): the Button fills the loose width Center
              hands it, like Flutter's bounded Button row. */}
          <div>
            <Button
              icon={<Icon icon={action.icon} />}
              tone={ButtonTone.primary}
              height={34}
              padding={EdgeInsets.symmetric({ horizontal: 14 })}
              onPressed={action.onPressed}
            >
              {action.label}
            </Button>
          </div>
        </>
      ) : null}
    </Column>
  );
}

function _DeletedUserProfileCard() {
  return (
    <div style={{ padding: UiSpacing.lg, boxSizing: 'border-box' }}>
      <Row data-testid="deleted-user-profile-card" mainAxisSize="min">
        <Avatar label="" defaultAvatarKey="graphite-2" size={42} showFallbackText={false} />
        <SizedBox width={UiSpacing.md} />
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>用户已注销</Text>
      </Row>
    </div>
  );
}

function _ProfileImagePreview({
  testId,
  imageUrl,
  children,
  suggestedName,
}: {
  testId: string;
  imageUrl: string | null;
  children?: ReactNode;
  suggestedName: string;
}) {
  const hoverScope = HoverCardTapRegionScope.maybeOf();
  const previewActions = ChatImagePreviewActionsScope.maybeOf();

  const openPreview = async (url: string): Promise<void> => {
    hoverScope?.onOverlayActivityChanged?.(true);
    try {
      await showChatImagePreview({
        imageUrl: url,
        suggestedName,
        actions: previewActions ?? ChatImagePreviewActions.disabled(),
        showActionBar: previewActions != null,
        forceSquare: true,
      });
    } finally {
      hoverScope?.onOverlayActivityChanged?.(false);
    }
  };

  const url = _nonEmpty(imageUrl);
  if (url == null) {
    return (
      <div data-testid={testId} style={{ flexShrink: 0 }}>
        {children}
      </div>
    );
  }

  return (
    <div
      data-testid={testId}
      onClick={(event) => {
        if (event.button !== 0) return;
        void openPreview(url);
      }}
      style={{ cursor: SystemMouseCursors.click, flexShrink: 0 }}
    >
      {children}
    </div>
  );
}

const _commonRoomListMaxHeight = 116;

function _UserCommonRoomList({
  rooms,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterRoom,
}: {
  rooms: readonly UserCommonRoom[];
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterRoom: ((room: PublicRoom) => void) | null;
}) {
  const list = (
    <Column mainAxisSize="min" crossAxisAlignment="stretch">
      {rooms.map((room) => (
        <div key={room.id} style={{ paddingTop: 4 }}>
          <_UserCommonRoomRow
            room={room}
            currentUser={currentUser}
            onResolveUserProfile={onResolveUserProfile}
            onResolveRoomProfile={onResolveRoomProfile}
            onEnterRoom={onEnterRoom}
          />
        </div>
      ))}
    </Column>
  );

  if (rooms.length <= 4) return list;

  // Scrollbar(thumbVisibility: true) + SingleChildScrollView.
  return (
    <div
      style={{
        maxHeight: _commonRoomListMaxHeight,
        overflowY: 'auto',
        paddingRight: UiSpacing.sm,
        boxSizing: 'border-box',
      }}
    >
      {list}
    </div>
  );
}

function _UserCommonRoomRow({
  room,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterRoom,
}: {
  room: UserCommonRoom;
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterRoom: ((room: PublicRoom) => void) | null;
}) {
  const config = useAppConfig();
  const publicRoom = _publicRoomFromCommonRoom(room);
  return (
    <_CompactProfileIdentity
      name={room_display.commonRoomDisplayName(room)}
      style={{ ...UiTypography.body, color: UiColors.textSecondary, fontSize: 13 }}
      avatarBuilder={(size) => {
        const avatar = (
          <Avatar
            label={room_display.commonRoomAvatarLabel(room)}
            imageUrl={config.resolveAssetUrl(room.avatarUrl)}
            defaultAvatarKey={room.defaultAvatarKey}
            size={size}
            activeBorderWidth={1}
          />
        );
        return currentUser == null ? (
          avatar
        ) : (
          <RoomHoverCard
            room={publicRoom}
            currentUser={currentUser}
            onResolveRoom={onResolveRoomProfile}
            onResolveUserProfile={onResolveUserProfile}
            onEnterRoom={onEnterRoom}
          >
            {avatar}
          </RoomHoverCard>
        );
      }}
    />
  );
}

const _normalAvatarSize = 20;
const _compactAvatarSize = 16;
const _textLayoutSlack = 8;

/**
 * Avatar + one-line name that shrinks the avatar and scales the name down
 * when the natural width does not fit.
 *
 * [maxWidth] is the width the parent offers (Flutter's LayoutBuilder
 * constraint). When it is omitted the identity measures a full-width box
 * itself; parents that lay the identity out inline (a Wrap) pass it.
 */
function _CompactProfileIdentity({
  name,
  style,
  avatarBuilder,
  maxWidth,
}: {
  name: string;
  style: CSSProperties;
  avatarBuilder: (size: number) => ReactNode;
  maxWidth?: number | null;
}) {
  const measureRef = useRef<HTMLDivElement | null>(null);
  const measured = useElementSize(measureRef);
  // This is the row's real width after the owning card has applied its
  // width clamp and padding. Never derive the compact breakpoint from
  // the screen or the default hover-card width.
  const constraintWidth = maxWidth ?? measured?.width ?? Infinity;
  const textWidth = measureTextWidth(name, style);
  const normalWidth = _normalAvatarSize + UiSpacing.sm + textWidth + _textLayoutSlack;
  const compact = normalWidth > constraintWidth;
  const avatarSize = compact ? _compactAvatarSize : _normalAvatarSize;
  const naturalIdentityWidth = avatarSize + UiSpacing.sm + textWidth + _textLayoutSlack;
  const minIdentityWidth = avatarSize + UiSpacing.sm + 1;
  const identityWidth = Math.min(Math.max(naturalIdentityWidth, minIdentityWidth), Math.max(constraintWidth, minIdentityWidth));
  const text = (
    <Text maxLines={1} softWrap={false} overflow="visible" style={style}>
      {name}
    </Text>
  );
  const identity = (
    <SizedBox width={Number.isFinite(identityWidth) ? identityWidth : undefined}>
      <Row>
        {avatarBuilder(avatarSize)}
        <SizedBox width={UiSpacing.sm} />
        <Expanded>{compact ? <_FittedBoxScaleDown alignment="centerLeft">{text}</_FittedBoxScaleDown> : text}</Expanded>
      </Row>
    </SizedBox>
  );
  if (maxWidth !== undefined) return identity;
  return (
    <div ref={measureRef} style={{ width: '100%', minWidth: 0 }}>
      {identity}
    </div>
  );
}

/**
 * Port of `FittedBox(fit: BoxFit.scaleDown)`: lays [children] out at their
 * natural width and scales them down (never up) to the available width.
 */
export function _FittedBoxScaleDown({
  children,
  alignment = 'centerLeft',
  style,
}: {
  children?: ReactNode;
  alignment?: 'centerLeft' | 'center' | 'centerRight';
  style?: CSSProperties;
}) {
  const outerRef = useRef<HTMLDivElement | null>(null);
  const innerRef = useRef<HTMLDivElement | null>(null);
  const [fit, setFit] = useState<{ scale: number; height: number | null }>({ scale: 1, height: null });

  useLayoutEffect(() => {
    const outer = outerRef.current;
    const inner = innerRef.current;
    if (outer == null || inner == null) return;
    const update = () => {
      const available = outer.clientWidth;
      const natural = inner.offsetWidth;
      const scale = natural > 0 && available > 0 && natural > available ? available / natural : 1;
      const height = inner.offsetHeight;
      setFit((previous) =>
        Math.abs(previous.scale - scale) < 0.001 && previous.height === height ? previous : { scale, height },
      );
    };
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(outer);
    observer.observe(inner);
    return () => observer.disconnect();
  }, []);

  const justify = alignment === 'centerRight' ? 'flex-end' : alignment === 'center' ? 'center' : 'flex-start';
  const origin = alignment === 'centerRight' ? 'right center' : alignment === 'center' ? 'center center' : 'left center';
  return (
    <div
      ref={outerRef}
      style={{
        width: '100%',
        minWidth: 0,
        height: fit.height == null ? undefined : fit.height * fit.scale,
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: justify,
        overflow: 'hidden',
        ...style,
      }}
    >
      <div
        ref={innerRef}
        style={{
          width: 'max-content',
          flexShrink: 0,
          transform: fit.scale < 1 ? `scale(${fit.scale})` : undefined,
          transformOrigin: origin,
        }}
      >
        {children}
      </div>
    </div>
  );
}

/** Port of `Wrap(spacing:, runSpacing:, crossAxisAlignment:)`. */
export function _Wrap({
  spacing = 0,
  runSpacing = 0,
  crossAxisAlignment = 'start',
  children,
  style,
  testId,
}: {
  spacing?: number;
  runSpacing?: number;
  crossAxisAlignment?: 'start' | 'center' | 'end';
  children?: ReactNode;
  style?: CSSProperties;
  testId?: string;
}) {
  return (
    <div
      data-testid={testId}
      style={{
        display: 'flex',
        flexDirection: 'row',
        flexWrap: 'wrap',
        columnGap: spacing,
        rowGap: runSpacing,
        alignItems: crossAxisAlignment === 'center' ? 'center' : crossAxisAlignment === 'end' ? 'flex-end' : 'flex-start',
        minWidth: 0,
        boxSizing: 'border-box',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Room card

interface _RoomProfileCardProps {
  room: PublicRoom;
  currentUser: CurrentUser;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onEnterRoom?: ((room: PublicRoom) => void) | null;
}

export function _RoomProfileCard({
  room,
  currentUser,
  onResolveRoomProfile = null,
  onResolveUserProfile = null,
  onEnterRoom = null,
}: _RoomProfileCardProps) {
  const config = useAppConfig();
  if (room.isDeleted) return <_DeletedRoomProfileCard />;
  const description = _nonEmpty(room.description);
  const rid = _nonEmpty(room.rid) ?? room.id;
  const creator = room.createdBy;
  const joined = room.joined;
  const myName = _myRoomDisplayName(room, currentUser);
  const myAvatarUrl = _myRoomAvatarUrl(room, currentUser);
  const myDefaultAvatarKey = _myRoomDefaultAvatarKey(room, currentUser);
  const myRole = _myRoomRoleLabel(room, currentUser);
  const roomAvatarUrl = config.resolveAssetUrl(room.avatarUrl);
  const previewRoomAvatarUrl = _nonEmpty(room.avatarUrl) == null ? null : _nonEmpty(roomAvatarUrl);

  return (
    <Column mainAxisSize="min" crossAxisAlignment="stretch" style={_cardColumnStyle}>
      <Row crossAxisAlignment="start">
        <_ProfileImagePreview
          testId="room-profile-card-icon-preview"
          imageUrl={previewRoomAvatarUrl}
          suggestedName={`${room.name}-icon.png`}
        >
          <Avatar
            label={room_display.publicRoomAvatarLabel(room)}
            imageUrl={roomAvatarUrl}
            defaultAvatarKey={room.defaultAvatarKey}
            size={48}
            activeBorderWidth={2}
          />
        </_ProfileImagePreview>
        <SizedBox width={UiSpacing.md} />
        <Expanded style={{ alignItems: 'stretch' }}>
          <Column crossAxisAlignment="stretch" mainAxisSize="min">
            <HoverCardSelectableText value={room.name} style={{ ...UiTypography.title, fontSize: 16 }} maxLines={12} />
            <SizedBox height={2} />
            <Text maxLines={2} style={{ ...UiTypography.label, color: UiColors.textMuted }}>
              {`${room.memberCount} 名成员`}
            </Text>
          </Column>
        </Expanded>
      </Row>
      <SizedBox height={UiSpacing.md} />
      <_Wrap spacing={6} runSpacing={6}>
        <StatusBadge
          label={room.joined ? '已加入' : '未加入'}
          icon={room.joined ? Icons.check_circle : Icons.circle_outlined}
          active={room.joined}
        />
        <StatusBadge label={room_display.visibilityLabel(room.visibility)} />
        <StatusBadge label={room_display.roomJoinPolicyLabel(room.joinPolicy)} />
      </_Wrap>
      {description != null ? (
        <>
          <SizedBox height={UiSpacing.md} />
          <HoverCardSelectableText
            value={description}
            maxLines={4}
            style={{ ...UiTypography.body, color: UiColors.textSecondary }}
          />
        </>
      ) : null}
      {creator != null ? (
        <>
          <SizedBox height={UiSpacing.md} />
          <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>创建者</Text>
          <SizedBox height={UiSpacing.xs} />
          <_RoomCardPersonRow
            avatarLabel={room_display.userAvatarLabel(creator)}
            avatarUrl={creator.avatarUrl}
            defaultAvatarKey={creator.defaultAvatarKey}
            name={room_display.userPrimaryName(creator)}
            profileUser={creator}
            currentUser={currentUser}
            onResolveUserProfile={onResolveUserProfile}
            onResolveRoomProfile={onResolveRoomProfile}
            onEnterCommonRoom={onEnterRoom}
          />
        </>
      ) : null}
      {joined ? (
        <>
          <SizedBox height={UiSpacing.md} />
          <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>我的房间内信息</Text>
          <SizedBox height={UiSpacing.xs} />
          <_RoomCardPersonRow
            avatarLabel={_currentUserAvatarLabel(currentUser)}
            avatarUrl={myAvatarUrl}
            defaultAvatarKey={myDefaultAvatarKey}
            name={myName}
            trailing={<RoleBadge label={myRole} />}
          />
        </>
      ) : null}
      <SizedBox height={UiSpacing.sm} />
      <HoverCardSelectableText
        value={`RID: ${rid}`}
        copyStartOffset={'RID: '.length}
        style={{ ...UiTypography.label, color: UiColors.textMuted }}
      />
      {joined ? (
        <>
          <SizedBox height={UiSpacing.md} />
          {/* Center(child: Button): the Button fills the loose width Center
              hands it, like Flutter's bounded Button row. */}
          <div>
            <Button
              icon={<Icon icon={Icons.login_rounded} />}
              tone={ButtonTone.primary}
              height={34}
              padding={EdgeInsets.symmetric({ horizontal: 14 })}
              onPressed={onEnterRoom == null ? null : () => onEnterRoom(room)}
            >
              进入房间
            </Button>
          </div>
        </>
      ) : null}
    </Column>
  );
}

function _DeletedRoomProfileCard() {
  return (
    <div style={{ padding: UiSpacing.lg, boxSizing: 'border-box' }}>
      <Row data-testid="deleted-room-profile-card" mainAxisSize="min">
        <Avatar label="" defaultAvatarKey="graphite-2" size={42} showFallbackText={false} />
        <SizedBox width={UiSpacing.md} />
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>房间已删除</Text>
      </Row>
    </div>
  );
}

function _RoomCardPersonRow({
  avatarLabel,
  avatarUrl,
  defaultAvatarKey,
  name,
  profileUser = null,
  currentUser = null,
  onResolveUserProfile = null,
  onResolveRoomProfile = null,
  onEnterCommonRoom = null,
  trailing = null,
}: {
  avatarLabel: string;
  avatarUrl: string | null;
  defaultAvatarKey: string;
  name: string;
  profileUser?: UserSummary | null;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  trailing?: ReactNode;
}) {
  const config = useAppConfig();
  const ref = useRef<HTMLDivElement | null>(null);
  const constraints = useElementSize(ref);
  const nameStyle: CSSProperties = { ...UiTypography.body, color: UiColors.textSecondary, fontSize: 13 };
  const identity = (
    <_CompactProfileIdentity
      name={name}
      style={nameStyle}
      maxWidth={constraints?.width ?? null}
      avatarBuilder={(size) => {
        const avatar = (
          <Avatar
            label={avatarLabel}
            imageUrl={config.resolveAssetUrl(avatarUrl)}
            defaultAvatarKey={defaultAvatarKey}
            size={size}
            activeBorderWidth={1}
          />
        );
        return profileUser == null || currentUser == null ? (
          avatar
        ) : (
          <UserHoverCard
            user={profileUser}
            currentUser={currentUser}
            onResolveProfile={onResolveUserProfile}
            onResolveRoomProfile={onResolveRoomProfile}
            onEnterCommonRoom={onEnterCommonRoom}
          >
            {avatar}
          </UserHoverCard>
        );
      }}
    />
  );
  return (
    <div ref={ref} style={{ width: '100%', minWidth: 0 }}>
      {trailing == null ? (
        // Align(alignment: Alignment.centerLeft)
        <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-start' }}>{identity}</div>
      ) : (
        <_Wrap spacing={UiSpacing.sm} runSpacing={UiSpacing.xs} crossAxisAlignment="center">
          {identity}
          {trailing}
        </_Wrap>
      )}
    </div>
  );
}

function _myRoomDisplayName(room: PublicRoom, currentUser: CurrentUser): string {
  return _nonEmpty(room.personalProfile.displayName) ?? currentUser.displayName;
}

function _myRoomAvatarUrl(_room: PublicRoom, currentUser: CurrentUser): string | null {
  return currentUser.avatarUrl;
}

function _myRoomDefaultAvatarKey(_room: PublicRoom, currentUser: CurrentUser): string {
  return currentUser.defaultAvatarKey;
}

function _currentUserAvatarLabel(currentUser: CurrentUser): string {
  return room_display.userAvatarLabel(currentUser.toSummary());
}

function _myRoomRoleLabel(room: PublicRoom, currentUser: CurrentUser): string {
  const role = room_display.roomRoleLabelFromValue(room.myMembership?.role ?? null);
  if (role != null) return role;
  return currentUser.isSuperuser ? '超级用户' : '成员';
}

function _nonEmpty(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed == null || trimmed.length === 0 ? null : trimmed;
}

function _publicRoomFromCommonRoom(room: UserCommonRoom): PublicRoom {
  return new PublicRoom({
    id: room.id,
    rid: room.rid,
    name: room_display.commonRoomDisplayName(room),
    avatarLabel: room_display.commonRoomAvatarLabel(room),
    avatarUrl: room.avatarUrl,
    defaultAvatarKey: room.defaultAvatarKey,
    visibility: room.visibility,
    joinPolicy: 'closed',
    memberCount: 0,
    onlineMemberCount: 0,
    liveParticipantCount: 0,
    joined: true,
    joinState: 'joined',
  });
}

/** @visibleForTesting */
export function RoomHoverCardForTest({
  room,
  currentUser,
  onResolveRoom,
  onResolveUserProfile,
  onEnterRoom,
}: {
  room: PublicRoom;
  currentUser: CurrentUser;
  onResolveRoom?: RoomProfileResolver | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onEnterRoom?: ((room: PublicRoom) => void) | null;
}) {
  return (
    <RoomHoverCard
      room={room}
      currentUser={currentUser}
      onResolveRoom={onResolveRoom}
      onResolveUserProfile={onResolveUserProfile}
      onEnterRoom={onEnterRoom}
    >
      <Avatar label={room_display.publicRoomAvatarLabel(room)} size={34} />
    </RoomHoverCard>
  );
}
