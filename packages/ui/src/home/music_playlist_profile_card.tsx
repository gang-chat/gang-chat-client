import { createContext, useContext, type ReactNode } from 'react';

import * as account_display from '@gang-chat/core/app/account_display';
import * as room_display from '@gang-chat/core/app/room_display';
import type {
  CurrentUser,
  MusicBoxState,
  PersonalMusicPlaylist,
  PublicRoom,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { useViewState, ViewState } from '../lib/view_state';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { ButtonTone } from '../ui/button';
import { ResponsiveDialogAction, ResponsiveDialogActionBar } from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { UiColors, UiSpacing, UiTypography } from '../ui/tokens';
import { HoverCardAnchor, HoverCardSelectableText } from './hover_card_anchor';
import {
  RoomHoverCard,
  UserHoverCard,
  _FittedBoxScaleDown,
  type RoomProfileResolver,
  type UserProfileActionBuilder,
  type UserProfileResolver,
} from './room_profile_card';

/**
 * Presentation data for a playlist hover card.
 *
 * The music-box snapshot intentionally does not invent metadata that the
 * server does not provide. For example, the temporary queue has no creation
 * timestamp, so [createdAt] remains null and the card renders it as unknown.
 */
export class MusicPlaylistCardData {
  constructor(opts: {
    id: string;
    name: string;
    songCount: number;
    createdAt: Date | null;
    creator?: UserSummary | null;
    room?: PublicRoom | null;
    showPlayingStatus?: boolean;
    showCreatedAt?: boolean;
  }) {
    this.id = opts.id;
    this.name = opts.name;
    this.songCount = opts.songCount;
    this.createdAt = opts.createdAt;
    this.creator = opts.creator ?? null;
    this.room = opts.room ?? null;
    this.showPlayingStatus = opts.showPlayingStatus ?? false;
    this.showCreatedAt = opts.showCreatedAt ?? true;
  }

  readonly id: string;
  readonly name: string;
  readonly songCount: number;
  readonly createdAt: Date | null;
  readonly creator: UserSummary | null;
  readonly room: PublicRoom | null;

  /**
   * True only when the card was opened from the authoritative current-source
   * header. It replaces the play-all action with a disabled playing action;
   * the same playlist listed elsewhere keeps its normal play-all action.
   */
  readonly showPlayingStatus: boolean;

  /**
   * Whether the card should render the creation timestamp row. Temporary
   * queues have no creation lifecycle, so their cards omit the row instead of
   * presenting a misleading "unknown" value.
   */
  readonly showCreatedAt: boolean;

  copyWith(
    opts: { name?: string | null; songCount?: number | null; createdAt?: Date | null; creator?: UserSummary | null } = {},
  ): MusicPlaylistCardData {
    return new MusicPlaylistCardData({
      id: this.id,
      name: opts.name ?? this.name,
      songCount: opts.songCount ?? this.songCount,
      createdAt: opts.createdAt ?? this.createdAt,
      creator: opts.creator ?? this.creator,
      room: this.room,
      showPlayingStatus: this.showPlayingStatus,
      showCreatedAt: this.showCreatedAt,
    });
  }
}

export type MusicPlaylistCardResolver = (current: MusicPlaylistCardData) => Promise<MusicPlaylistCardData>;

export type MusicPlaylistViewCallback = (playlist: PersonalMusicPlaylist, roomScoped: boolean) => Promise<void>;
export type MusicPlaylistEditCallback = (playlistId: string) => Promise<void>;

export interface MusicPlaylistCardHostScopeData {
  readonly currentUser: CurrentUser | null;
  readonly room: PublicRoom | null;
  readonly onResolveUserProfile: UserProfileResolver | null;
  readonly onResolveRoomProfile: RoomProfileResolver | null;
  readonly onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  readonly userProfileActionBuilder: UserProfileActionBuilder | null;
  readonly onStateChanged: ((state: MusicBoxState) => void) | null;
  readonly onViewPlaylist: MusicPlaylistViewCallback;
  readonly onEditPersonalPlaylist: MusicPlaylistEditCallback | null;
  readonly onEditRoomPlaylist: MusicPlaylistEditCallback | null;
}

export interface MusicPlaylistCardHostScopeProps {
  currentUser: CurrentUser | null;
  room: PublicRoom | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
  onStateChanged: ((state: MusicBoxState) => void) | null;
  onViewPlaylist: MusicPlaylistViewCallback;
  onEditPersonalPlaylist?: MusicPlaylistEditCallback | null;
  onEditRoomPlaylist?: MusicPlaylistEditCallback | null;
  children?: ReactNode;
}

const _MusicPlaylistCardHostContext = createContext<MusicPlaylistCardHostScopeData | null>(null);

function MusicPlaylistCardHostScopeImpl({ children, ...props }: MusicPlaylistCardHostScopeProps) {
  const value: MusicPlaylistCardHostScopeData = {
    currentUser: props.currentUser,
    room: props.room,
    onResolveUserProfile: props.onResolveUserProfile,
    onResolveRoomProfile: props.onResolveRoomProfile,
    onEnterCommonRoom: props.onEnterCommonRoom,
    userProfileActionBuilder: props.userProfileActionBuilder,
    onStateChanged: props.onStateChanged,
    onViewPlaylist: props.onViewPlaylist,
    onEditPersonalPlaylist: props.onEditPersonalPlaylist ?? null,
    onEditRoomPlaylist: props.onEditRoomPlaylist ?? null,
  };
  return <_MusicPlaylistCardHostContext.Provider value={value}>{children}</_MusicPlaylistCardHostContext.Provider>;
}

/** Port of `MusicPlaylistCardHostScope.maybeOf(context)` (a hook). */
function useMaybeMusicPlaylistCardHostScope(): MusicPlaylistCardHostScopeData | null {
  return useContext(_MusicPlaylistCardHostContext);
}

/**
 * Supplies playlist identity and navigation actions to cards opened several
 * layers below the music-box body (notably the add-to-playlist picker inside a
 * song card) without duplicating those parameters through every song widget.
 */
export const MusicPlaylistCardHostScope = Object.assign(MusicPlaylistCardHostScopeImpl, {
  maybeOf: useMaybeMusicPlaylistCardHostScope,
});

export interface MusicPlaylistHoverCardProps {
  data: MusicPlaylistCardData;
  children?: ReactNode;
  onPlayAll: (() => Promise<void>) | null;
  onViewPlaylist: (() => Promise<void>) | null;
  resolveData?: MusicPlaylistCardResolver | null;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
  primaryActionLabel?: string;
  primaryActionIcon?: IconData;
  showPrimaryActionWhenDisabled?: boolean;
  secondaryActionLabel?: string;
  secondaryActionIcon?: IconData;
  secondaryActionTone?: ButtonTone;
}

/**
 * Shared playlist hover/tap card used by the current-source header and saved
 * playlist rows on Windows, macOS, and Android.
 */
class _MusicPlaylistHoverCardState extends ViewState<MusicPlaylistHoverCardProps> {
  _resolved: MusicPlaylistCardData | null = null;
  _resolveFuture: Promise<void> | null = null;
  _playing = false;
  _viewing = false;
  _dismissEpoch = 0;

  get _displayData(): MusicPlaylistCardData {
    return this._resolved ?? this.widget.data;
  }

  override didUpdateProps(oldWidget: MusicPlaylistHoverCardProps): void {
    const a = oldWidget.data;
    const b = this.widget.data;
    if (
      a.id === b.id &&
      a.name === b.name &&
      a.songCount === b.songCount &&
      (a.createdAt?.getTime() ?? null) === (b.createdAt?.getTime() ?? null) &&
      a.creator?.id === b.creator?.id &&
      a.creator?.roomDisplayName === b.creator?.roomDisplayName &&
      a.creator?.displayName === b.creator?.displayName &&
      a.creator?.avatarUrl === b.creator?.avatarUrl &&
      a.room?.id === b.room?.id &&
      a.showPlayingStatus === b.showPlayingStatus &&
      a.showCreatedAt === b.showCreatedAt
    ) {
      return;
    }
    // PORT: didUpdateProps runs after the render that received the new props,
    // so the reset re-renders (Flutter reset before build).
    this.setState(() => {
      this._resolved = null;
      this._resolveFuture = null;
    });
  }

  _resolve = (): Promise<void> => {
    const playlistResolver = this.widget.resolveData ?? null;
    const creatorResolver = this.widget.onResolveUserProfile ?? null;
    if (playlistResolver == null && (creatorResolver == null || this.widget.data.creator == null)) {
      return Promise.resolve();
    }
    const existing = this._resolveFuture;
    if (existing != null) return existing;
    const requested = this.widget.data;
    const future = (async () => {
      try {
        let resolved = requested;
        if (playlistResolver != null) {
          try {
            resolved = await playlistResolver(requested);
          } catch {
            // Keep the snapshot metadata visible when richer playlist metadata
            // is unavailable (for example another user's private playlist).
          }
        }
        const creator = resolved.creator;
        if (creatorResolver != null && creator != null) {
          try {
            const profile = await creatorResolver(creator);
            resolved = resolved.copyWith({ creator: profile });
          } catch {
            // The lightweight identity remains usable when its room-member
            // profile cannot be refreshed.
          }
        }
        if (this.mounted && this.widget.data.id === requested.id) {
          this.setState(() => {
            this._resolved = resolved;
          });
        }
      } finally {
        if (this.mounted && this.widget.data.id === requested.id) this._resolveFuture = null;
      }
    })();
    this._resolveFuture = future;
    return future;
  };

  _playAll = async (): Promise<void> => {
    const callback = this.widget.onPlayAll;
    if (callback == null || this._playing) return;
    this.setState(() => {
      this._playing = true;
    });
    try {
      await callback();
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._playing = false;
        });
      }
    }
  };

  _viewPlaylist = async (): Promise<void> => {
    const callback = this.widget.onViewPlaylist;
    if (callback == null || this._viewing) return;
    this.setState(() => {
      this._viewing = true;
    });
    try {
      await callback();
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._viewing = false;
          // Viewing replaces the underlying browser or reveals the already
          // visible current queue. Resetting the anchor closes the pinned card
          // instead of leaving it over the content the user asked to see.
          this._dismissEpoch += 1;
        });
      }
    }
  };

  build(): ReactNode {
    const widget = this.widget;
    const data = this._displayData;
    return (
      <HoverCardAnchor
        cardWidth={304}
        resetKey={JSON.stringify([widget.data.id, widget.data.showPlayingStatus, this._dismissEpoch])}
        onBeforeOpen={
          widget.resolveData == null && (widget.onResolveUserProfile == null || widget.data.creator == null)
            ? null
            : this._resolve
        }
        cardBuilder={() => (
          <_MusicPlaylistProfileCard
            data={data}
            currentUser={widget.currentUser ?? null}
            onResolveUserProfile={widget.onResolveUserProfile ?? null}
            onResolveRoomProfile={widget.onResolveRoomProfile ?? null}
            onEnterCommonRoom={widget.onEnterCommonRoom ?? null}
            userProfileActionBuilder={widget.userProfileActionBuilder ?? null}
            playing={this._playing}
            viewing={this._viewing}
            onPlayAll={widget.onPlayAll == null ? null : () => void this._playAll()}
            onViewPlaylist={widget.onViewPlaylist == null ? null : () => void this._viewPlaylist()}
            primaryActionLabel={widget.primaryActionLabel ?? '播放全部'}
            primaryActionIcon={widget.primaryActionIcon ?? Icons.play_arrow}
            showPrimaryActionWhenDisabled={widget.showPrimaryActionWhenDisabled ?? true}
            secondaryActionLabel={widget.secondaryActionLabel ?? '查看歌单'}
            secondaryActionIcon={widget.secondaryActionIcon ?? Icons.queue_music}
            secondaryActionTone={widget.secondaryActionTone ?? ButtonTone.neutral}
          />
        )}
      >
        {widget.children}
      </HoverCardAnchor>
    );
  }
}

export function MusicPlaylistHoverCard(props: MusicPlaylistHoverCardProps) {
  const state = useViewState(() => new _MusicPlaylistHoverCardState(), props);
  return <>{state.build()}</>;
}

function _MusicPlaylistProfileCard({
  data,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  userProfileActionBuilder,
  playing,
  viewing,
  onPlayAll,
  onViewPlaylist,
  primaryActionLabel,
  primaryActionIcon,
  showPrimaryActionWhenDisabled,
  secondaryActionLabel,
  secondaryActionIcon,
  secondaryActionTone,
}: {
  data: MusicPlaylistCardData;
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
  playing: boolean;
  viewing: boolean;
  onPlayAll: (() => void) | null;
  onViewPlaylist: (() => void) | null;
  primaryActionLabel: string;
  primaryActionIcon: IconData;
  showPrimaryActionWhenDisabled: boolean;
  secondaryActionLabel: string;
  secondaryActionIcon: IconData;
  secondaryActionTone: ButtonTone;
}) {
  const isRoomPlaylist = data.room != null;
  const actions: ResponsiveDialogAction[] = [];
  if (onPlayAll != null || showPrimaryActionWhenDisabled) {
    actions.push(
      new ResponsiveDialogAction({
        buttonKey: 'music-playlist-card-play-all',
        label: data.showPlayingStatus ? '正在播放' : primaryActionLabel,
        icon: data.showPlayingStatus ? Icons.play_arrow : primaryActionIcon,
        tone: ButtonTone.primary,
        loading: !data.showPlayingStatus && playing,
        onPressed: data.showPlayingStatus ? null : onPlayAll,
      }),
    );
  }
  actions.push(
    new ResponsiveDialogAction({
      buttonKey: 'music-playlist-card-view',
      label: secondaryActionLabel,
      icon: secondaryActionIcon,
      tone: secondaryActionTone,
      loading: viewing,
      onPressed: onViewPlaylist,
    }),
  );
  return (
    <Column
      data-testid={`music-playlist-card:${data.id}`}
      mainAxisSize="min"
      crossAxisAlignment="stretch"
      style={{ padding: UiSpacing.lg, boxSizing: 'border-box' }}
    >
      <Row data-testid="music-playlist-card-title" crossAxisAlignment="center">
        <span data-testid="music-playlist-card-title-icon" style={{ display: 'contents' }}>
          <Icon icon={Icons.queue_music} size={22} color={UiColors.accent} />
        </span>
        <SizedBox width={UiSpacing.sm} />
        <Expanded>
          <HoverCardSelectableText value={data.name} maxLines={12} style={{ ...UiTypography.title, fontSize: 16 }} />
        </Expanded>
      </Row>
      <SizedBox height={UiSpacing.md} />
      <_MusicPlaylistDetailRow label="歌曲数量">
        <Text textAlign="right" style={UiTypography.label}>{`${data.songCount} 首`}</Text>
      </_MusicPlaylistDetailRow>
      <SizedBox height={UiSpacing.sm} />
      <_MusicPlaylistDetailRow label={isRoomPlaylist ? '房间' : '创建人'}>
        <_MusicPlaylistIdentity
          creator={data.creator}
          room={data.room}
          currentUser={currentUser}
          onResolveUserProfile={onResolveUserProfile}
          onResolveRoomProfile={onResolveRoomProfile}
          onEnterCommonRoom={onEnterCommonRoom}
          userProfileActionBuilder={userProfileActionBuilder}
        />
      </_MusicPlaylistDetailRow>
      {data.showCreatedAt ? (
        <>
          <SizedBox height={UiSpacing.sm} />
          <_MusicPlaylistDetailRow label="创建日期">
            {/* A TextField fills the width Align offers it. */}
            <div style={{ width: '100%', minWidth: 0 }}>
              <HoverCardSelectableText
                value={account_display.formatDateTime(data.createdAt)}
                textAlign="right"
                maxLines={2}
                style={UiTypography.label}
              />
            </div>
          </_MusicPlaylistDetailRow>
        </>
      ) : null}
      <SizedBox height={UiSpacing.lg} />
      <ResponsiveDialogActionBar expanded actions={actions} />
    </Column>
  );
}

function _MusicPlaylistDetailRow({ label, children }: { label: string; children?: ReactNode }) {
  return (
    <Row crossAxisAlignment="center">
      <SizedBox width={58}>
        <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{label}</Text>
      </SizedBox>
      <Expanded>
        {/* Align(alignment: Alignment.centerRight) */}
        <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', minWidth: 0 }}>
          {children}
        </div>
      </Expanded>
    </Row>
  );
}

function _MusicPlaylistIdentity({
  creator,
  room,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  userProfileActionBuilder,
}: {
  creator: UserSummary | null;
  room: PublicRoom | null;
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
}) {
  const config = useAppConfig();
  const roomValue = room;
  if (roomValue != null) {
    const identity = (
      <_MusicPlaylistIdentityValue
        label={room_display.publicRoomAvatarLabel(roomValue)}
        imageUrl={config.resolveAssetUrl(roomValue.avatarUrl)}
        defaultAvatarKey={roomValue.defaultAvatarKey}
        name={roomValue.name}
      />
    );
    const user = currentUser;
    if (user == null) return identity;
    return (
      <RoomHoverCard
        room={roomValue}
        currentUser={user}
        onResolveRoom={onResolveRoomProfile}
        onResolveUserProfile={onResolveUserProfile}
        onEnterRoom={onEnterCommonRoom}
      >
        {identity}
      </RoomHoverCard>
    );
  }

  const creatorValue = creator;
  if (creatorValue != null) {
    const identity = (
      <_MusicPlaylistIdentityValue
        label={room_display.userAvatarLabel(creatorValue)}
        imageUrl={config.resolveAssetUrl(creatorValue.avatarUrl)}
        defaultAvatarKey={creatorValue.defaultAvatarKey}
        name={room_display.userPrimaryName(creatorValue)}
      />
    );
    return (
      <UserHoverCard
        user={creatorValue}
        currentUser={currentUser}
        onResolveProfile={onResolveUserProfile}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onEnterCommonRoom}
        profileActionBuilder={userProfileActionBuilder}
        showRoomRole
      >
        {identity}
      </UserHoverCard>
    );
  }

  return (
    <Text textAlign="right" style={{ ...UiTypography.label, color: UiColors.textMuted }}>
      未知
    </Text>
  );
}

const _identityAvatarSize = 24;
const _identityGap = 6;

function _MusicPlaylistIdentityValue({
  label,
  imageUrl,
  defaultAvatarKey,
  name,
}: {
  label: string;
  imageUrl: string | null;
  defaultAvatarKey: string;
  name: string;
}) {
  const style = { ...UiTypography.label, color: UiColors.text };
  // SizedBox(width: infinity) > Align(centerRight) > FittedBox(scaleDown).
  return (
    <div style={{ width: '100%', minWidth: 0 }}>
      <_FittedBoxScaleDown alignment="centerRight">
        <Row mainAxisSize="min">
          <Avatar label={label} imageUrl={imageUrl} defaultAvatarKey={defaultAvatarKey} size={_identityAvatarSize} showBorder={false} />
          <SizedBox width={_identityGap} />
          <Text data-testid="music-playlist-identity-name" maxLines={1} softWrap={false} textAlign="right" style={style}>
            {name}
          </Text>
        </Row>
      </_FittedBoxScaleDown>
    </div>
  );
}
