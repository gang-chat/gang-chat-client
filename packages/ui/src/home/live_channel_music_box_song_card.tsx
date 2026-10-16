import { useRef, useState, type CSSProperties } from 'react';

import { unawaited } from '@gang-chat/core/lib/async';
import * as music_box_display from '@gang-chat/core/app/music_box_display';
import { musicBoxControlErrorMessage, type MusicBoxController } from '@gang-chat/core/app/music_box_controller';
import {
  MusicBoxActiveSourceType,
  PersonalMusicPlaylist,
  UserSummary,
  type CurrentUser,
  type MusicBoxActiveSource,
  type MusicBoxQueueItem,
  type MusicBoxRequester,
  type MusicBoxSearchResult,
  type MusicBoxState,
  type PublicRoom,
} from '@gang-chat/core/protocol/models';

import { Column, Expanded, Flexible, Row, SizedBox, Text } from '../lib/layout';
import { measureLineCount, measureTextWidth } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { useMountedRef } from '../lib/use_mounted';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonTone } from '../ui/button';
import { showFloatingErrorNotice, showFloatingSuccessNotice } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { UiColors, UiSpacing, UiTypography } from '../ui/tokens';
import { HoverCardSelectableText } from './hover_card_anchor';
import {
  MusicPlaylistCardData,
  MusicPlaylistCardHostScope,
  MusicPlaylistHoverCard,
  type MusicPlaylistCardResolver,
} from './music_playlist_profile_card';
import { MusicTrackPlaylistTarget, showMusicTrackPlaylistTargetDialog } from './music_track_profile_card';
import { UserHoverCard, type RoomProfileResolver, type UserProfileActionBuilder, type UserProfileResolver } from './room_profile_card';

/*
 * Song profile card shared by every music-box row: queue items, search hits,
 * and saved-playlist tracks. Opened as a hover card on the row's text area;
 * its actions (优先播放 / 添加到歌单) are gated by server capabilities.
 */

export function _musicBoxActiveSourceOwner(source: MusicBoxActiveSource, currentUser: CurrentUser | null | undefined): UserSummary | null {
  const owner = source.owner;
  if (owner != null) {
    return new UserSummary({
      id: owner.userId,
      username: owner.username,
      displayName: owner.avatarLabel,
      roomDisplayName: owner.displayName,
      avatarUrl: owner.avatarUrl,
      defaultAvatarKey: owner.defaultAvatarKey,
    });
  }
  if (currentUser != null && (source.ownerUserId.length === 0 || source.ownerUserId === currentUser.id)) {
    return currentUser.toSummary();
  }
  return null;
}

export function _musicBoxPlaylistById(playlists: readonly PersonalMusicPlaylist[], id: string): PersonalMusicPlaylist | null {
  for (const playlist of playlists) {
    if (playlist.id === id) return playlist;
  }
  return null;
}

/** Arguments of Dart `_MusicBoxSongCard.queue(...)`. */
export interface _MusicBoxSongCardQueueProps {
  item: MusicBoxQueueItem;
  isCurrent: boolean;
  queueSongCount: number;
  controller?: MusicBoxController | null;
  roomId?: string | null;
  activeSource: MusicBoxActiveSource;
  onStateChanged?: ((state: MusicBoxState) => void) | null;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
}

/** Arguments of Dart `_MusicBoxSongCard.search(...)`. */
export interface _MusicBoxSongCardSearchProps {
  result: MusicBoxSearchResult;
  controller?: MusicBoxController | null;
  roomId?: string | null;
  onQueueResult: ((result: MusicBoxSearchResult) => void) | null;
  alreadyInRequestQueue: boolean;
  catalogDurationMs?: number | null;
}

/** All fields of the Dart widget; the two named constructors fill them. */
export interface _MusicBoxSongCardProps {
  item: MusicBoxQueueItem | null;
  result: MusicBoxSearchResult | null;
  catalogDurationMs: number | null;
  isCurrent: boolean;
  queueSongCount: number;
  controller: MusicBoxController | null;
  roomId: string | null;
  activeSource: MusicBoxActiveSource | null;
  onStateChanged: ((state: MusicBoxState) => void) | null;
  onQueueResult: ((result: MusicBoxSearchResult) => void) | null;
  alreadyInRequestQueue: boolean;
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
}

function _MusicBoxSongCardImpl(props: _MusicBoxSongCardProps) {
  const [playingNow, setPlayingNow] = useState(false);
  const playingNowRef = useRef(false);
  const mounted = useMountedRef();

  const _playNow = async (): Promise<void> => {
    const { controller, roomId, item } = props;
    if (controller == null || roomId == null || item == null || playingNowRef.current) {
      return;
    }
    playingNowRef.current = true;
    setPlayingNow(true);
    try {
      const state = await controller.playNow({ roomId, item });
      props.onStateChanged?.(state);
      if (mounted.current) showFloatingSuccessNotice('已优先播放');
    } catch (error) {
      if (mounted.current) {
        showFloatingErrorNotice(musicBoxControlErrorMessage(error, '优先播放失败，请重试'));
      }
    } finally {
      playingNowRef.current = false;
      if (mounted.current) setPlayingNow(false);
    }
  };

  const _openPlaylistPicker = (): Promise<void> => {
    return _musicBoxAddToPlaylist({
      controller: props.controller,
      roomId: props.roomId,
      item: props.item,
      result: props.result,
      durationMs: props.catalogDurationMs,
    });
  };

  const item = props.item;
  const result = props.result;
  const isSearchResult = result != null;
  const title = item?.title ?? result!.name;
  const artist = item?.artist.trim() ?? music_box_display.musicBoxArtistsLabel(result!.artists).trim();
  const source = item?.source ?? result!.source;
  const trackId = item?.trackId ?? result!.trackId;
  const durationMs = item?.durationMs ?? props.catalogDurationMs ?? 0;
  const isBilibili = source.trim().toLowerCase() === 'bilibili';
  const canPlayNow = item != null && item.canPlayNow && item.playable && props.controller != null && props.roomId != null;
  return (
    <div style={{ overflowY: 'auto', maxHeight: '100%', boxSizing: 'border-box' }}>
      <Column
        data-testid={
          isSearchResult ? `music-box-song-card:search:${result.source}:${result.trackId}` : `music-box-song-card:${item!.id}`
        }
        mainAxisSize="min"
        crossAxisAlignment="stretch"
        style={{ padding: UiSpacing.lg }}
      >
        <Row>
          <Icon icon={Icons.music_note} color={UiColors.accent} size={28} />
          <SizedBox width={UiSpacing.sm} />
          <Expanded>
            <_MusicBoxAdaptiveFullText
              data-testid="music-box-song-card-title"
              text={title}
              style={{ ...UiTypography.title, fontSize: 16 }}
            />
          </Expanded>
        </Row>
        <SizedBox height={UiSpacing.md} />
        <_MusicBoxSongDetailRow
          label="时长"
          value={durationMs > 0 ? music_box_display.musicBoxFormatDuration(durationMs) : '未知'}
        />
        <_MusicBoxSongDetailRow
          label={music_box_display.musicBoxArtistFieldLabel(source)}
          value={artist.length === 0 ? (isBilibili ? '未知作者' : '未知艺人') : artist}
        />
        <_MusicBoxSongDetailRow label="来源" value={music_box_display.musicBoxSourceLabel(source)} />
        {!isSearchResult ? (
          <>
            <SizedBox height={UiSpacing.sm} />
            <_MusicBoxSongAttribution
              source={props.activeSource!}
              requester={item!.requestedBy}
              songCount={props.queueSongCount}
              controller={props.controller}
              roomId={props.roomId}
              onStateChanged={props.onStateChanged}
              currentUser={props.currentUser}
              onResolveUserProfile={props.onResolveUserProfile}
              onResolveRoomProfile={props.onResolveRoomProfile}
              onEnterCommonRoom={props.onEnterCommonRoom}
              userProfileActionBuilder={props.userProfileActionBuilder}
            />
          </>
        ) : null}
        {isBilibili ? (
          <>
            <SizedBox height={UiSpacing.sm} />
            <_MusicBoxSongDetailRow label="详情" value={_musicBoxBilibiliBvId(trackId)} />
          </>
        ) : null}
        <SizedBox height={UiSpacing.md} />
        <Row>
          {/* Preset tracks pass no queue callback: they are played from the
              music box, not requested, so the card shows no queue action
              for them. */}
          {isSearchResult && (props.onQueueResult != null || props.alreadyInRequestQueue) ? (
            <>
              <Expanded>
                <Button
                  icon={<Icon icon={props.alreadyInRequestQueue ? Icons.playlist_add_check : Icons.playlist_add} />}
                  tone={props.alreadyInRequestQueue ? ButtonTone.neutral : ButtonTone.primary}
                  height={34}
                  onPressed={props.alreadyInRequestQueue ? null : () => props.onQueueResult?.(result)}
                >
                  {props.alreadyInRequestQueue ? '已在队列中' : '点歌队列'}
                </Button>
              </Expanded>
              <SizedBox width={8} />
            </>
          ) : null}
          {canPlayNow ? (
            <>
              <Expanded>
                <Button
                  icon={<Icon icon={Icons.play_arrow} />}
                  height={34}
                  loading={playingNow}
                  onPressed={props.isCurrent ? null : () => unawaited(_playNow())}
                >
                  {props.isCurrent ? '正在播放' : '优先播放'}
                </Button>
              </Expanded>
              <SizedBox width={8} />
            </>
          ) : null}
          <Expanded>
            <Button
              icon={<Icon icon={Icons.playlist_add} />}
              tone={ButtonTone.primary}
              height={34}
              onPressed={props.controller == null ? null : () => unawaited(_openPlaylistPicker())}
            >
              添加到歌单
            </Button>
          </Expanded>
        </Row>
      </Column>
    </div>
  );
}

/**
 * The song card. Dart's named constructors are exposed as
 * `_MusicBoxSongCard.queue(props)` / `_MusicBoxSongCard.search(props)`: plain
 * (hook-free) functions, so they work both as JSX components
 * (`<_MusicBoxSongCard.queue … />`) and as builders returning an element.
 */
export const _MusicBoxSongCard = Object.assign(_MusicBoxSongCardImpl, {
  queue(props: _MusicBoxSongCardQueueProps) {
    return (
      <_MusicBoxSongCardImpl
        item={props.item}
        result={null}
        catalogDurationMs={null}
        isCurrent={props.isCurrent}
        queueSongCount={props.queueSongCount}
        controller={props.controller ?? null}
        roomId={props.roomId ?? null}
        activeSource={props.activeSource}
        onStateChanged={props.onStateChanged ?? null}
        onQueueResult={null}
        alreadyInRequestQueue={false}
        currentUser={props.currentUser ?? null}
        onResolveUserProfile={props.onResolveUserProfile ?? null}
        onResolveRoomProfile={props.onResolveRoomProfile ?? null}
        onEnterCommonRoom={props.onEnterCommonRoom ?? null}
        userProfileActionBuilder={props.userProfileActionBuilder ?? null}
      />
    );
  },
  search(props: _MusicBoxSongCardSearchProps) {
    return (
      <_MusicBoxSongCardImpl
        item={null}
        result={props.result}
        catalogDurationMs={props.catalogDurationMs ?? null}
        isCurrent={false}
        queueSongCount={0}
        controller={props.controller ?? null}
        roomId={props.roomId ?? null}
        activeSource={null}
        onStateChanged={null}
        onQueueResult={props.onQueueResult}
        alreadyInRequestQueue={props.alreadyInRequestQueue}
        currentUser={null}
        onResolveUserProfile={null}
        onResolveRoomProfile={null}
        onEnterCommonRoom={null}
        userProfileActionBuilder={null}
      />
    );
  },
});

export interface _MusicBoxSongAttributionProps {
  source: MusicBoxActiveSource;
  requester: MusicBoxRequester | null;
  songCount: number;
  controller?: MusicBoxController | null;
  roomId?: string | null;
  onStateChanged?: ((state: MusicBoxState) => void) | null;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
}

export function _MusicBoxSongAttribution(props: _MusicBoxSongAttributionProps) {
  const {
    source,
    requester,
    songCount,
    controller,
    roomId,
    onStateChanged,
    currentUser,
    onResolveUserProfile,
    onResolveRoomProfile,
    onEnterCommonRoom,
    userProfileActionBuilder,
  } = props;
  const mounted = useMountedRef();
  // `MusicPlaylistCardHostScope.maybeOf(context)` is a hook in the port.
  const host = MusicPlaylistCardHostScope.maybeOf();

  const _playlistResolver = (): MusicPlaylistCardResolver | null => {
    const musicController = controller;
    if (musicController == null || source.id.length === 0) return null;
    if (source.type === MusicBoxActiveSourceType.roomPlaylist) {
      const currentRoomId = roomId;
      if (currentRoomId == null) return null;
      return async (current) => {
        const page = await musicController.loadRoomPlaylists({ roomId: currentRoomId });
        const playlist = _musicBoxPlaylistById(page.playlists, source.id);
        if (playlist == null) return current;
        return current.copyWith({
          name: playlist.name,
          songCount: playlist.itemCount,
          createdAt: playlist.createdAt,
        });
      };
    }
    const ownerId = source.ownerUserId.length > 0 ? source.ownerUserId : (source.owner?.userId ?? '');
    if (
      source.type !== MusicBoxActiveSourceType.userPlaylist ||
      currentUser == null ||
      (ownerId.length > 0 && ownerId !== currentUser.id)
    ) {
      return null;
    }
    return async (current) => {
      const page = await musicController.loadMyPlaylists();
      const playlist = _musicBoxPlaylistById(page.playlists, source.id);
      if (playlist == null) return current;
      return current.copyWith({
        name: playlist.name,
        songCount: playlist.itemCount,
        createdAt: playlist.createdAt,
      });
    };
  };

  const _playPlaylist = async (): Promise<void> => {
    const musicController = controller;
    const currentRoomId = roomId;
    if (musicController == null || currentRoomId == null || source.id.length === 0) {
      return;
    }
    try {
      const state = await musicController.activatePlaylist({
        roomId: currentRoomId,
        sourceType: source.type,
        playlistId: source.id,
      });
      onStateChanged?.(state);
    } catch (error) {
      if (mounted.current) {
        showFloatingErrorNotice(musicBoxControlErrorMessage(error, '播放歌单失败，请重试'));
      }
    }
  };

  const isPlaylist = source.type !== MusicBoxActiveSourceType.temporary;
  const playlistName = source.name.trim().length === 0 ? '未命名歌单' : source.name;
  const creator = isPlaylist ? _musicBoxActiveSourceOwner(source, currentUser) : null;
  const ownerId = source.ownerUserId.length > 0 ? source.ownerUserId : (source.owner?.userId ?? '');
  const viewer = currentUser;
  const isOwnedPersonalPlaylist =
    source.type === MusicBoxActiveSourceType.userPlaylist && viewer != null && (ownerId.length === 0 || ownerId === viewer.id);
  const editPlaylist =
    source.type === MusicBoxActiveSourceType.roomPlaylist
      ? host?.onEditRoomPlaylist
      : isOwnedPersonalPlaylist
        ? host?.onEditPersonalPlaylist
        : null;
  const playlistValue = <_MusicBoxPlaylistAttributionValue name={playlistName} />;
  const value =
    isPlaylist && source.id.length > 0 ? (
      <MusicPlaylistHoverCard
        data={
          new MusicPlaylistCardData({
            id: source.id,
            name: playlistName,
            songCount,
            createdAt: source.createdAt,
            creator: source.type === MusicBoxActiveSourceType.userPlaylist ? creator : null,
            room: source.type === MusicBoxActiveSourceType.roomPlaylist ? (host?.room ?? null) : null,
          })
        }
        resolveData={_playlistResolver() ?? undefined}
        currentUser={currentUser ?? undefined}
        onResolveUserProfile={onResolveUserProfile ?? undefined}
        onResolveRoomProfile={onResolveRoomProfile ?? undefined}
        onEnterCommonRoom={onEnterCommonRoom ?? undefined}
        userProfileActionBuilder={userProfileActionBuilder ?? undefined}
        onPlayAll={controller == null || roomId == null ? null : () => _playPlaylist()}
        onViewPlaylist={
          editPlaylist != null
            ? () => editPlaylist(source.id)
            : host == null
              ? null
              : () =>
                  host.onViewPlaylist(
                    new PersonalMusicPlaylist({
                      id: source.id,
                      name: playlistName,
                      description: '',
                      revision: 0,
                      itemCount: songCount,
                      createdAt: source.createdAt,
                      updatedAt: null,
                    }),
                    source.type === MusicBoxActiveSourceType.roomPlaylist,
                  )
        }
        secondaryActionLabel={editPlaylist == null ? '查看歌单' : '编辑歌单'}
        secondaryActionIcon={editPlaylist == null ? Icons.queue_music : Icons.edit_outlined}
      >
        {playlistValue}
      </MusicPlaylistHoverCard>
    ) : isPlaylist ? (
      playlistValue
    ) : (
      <_MusicBoxSongAttributionValue
        value={requester?.displayName ?? '未知用户'}
        person={requester}
        showAvatar={requester != null}
        currentUser={currentUser}
        onResolveUserProfile={onResolveUserProfile}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onEnterCommonRoom}
        userProfileActionBuilder={userProfileActionBuilder}
      />
    );

  return (
    <Row>
      <SizedBox width={38}>
        <HoverCardSelectableText value={isPlaylist ? '歌单' : '点歌人'} style={{ ...UiTypography.label, color: UiColors.textMuted }} />
      </SizedBox>
      <Expanded>{value}</Expanded>
    </Row>
  );
}

export function _MusicBoxPlaylistAttributionValue({ name }: { name: string }) {
  return (
    <Row data-testid="music-box-song-playlist-attribution" mainAxisAlignment="end" crossAxisAlignment="center">
      <span data-testid="music-box-song-playlist-icon" style={{ display: 'flex' }}>
        <Icon icon={Icons.queue_music} size={17} color={UiColors.accent} />
      </span>
      <SizedBox width={5} />
      <Flexible>
        <Text data-testid="music-box-song-attribution-name" textAlign="right" style={{ ...UiTypography.label, color: UiColors.text }}>
          {name}
        </Text>
      </Flexible>
    </Row>
  );
}

export interface _MusicBoxSongAttributionValueProps {
  value: string;
  person: MusicBoxRequester | null;
  showAvatar: boolean;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
}

export function _MusicBoxSongAttributionValue({
  value,
  person,
  showAvatar,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  userProfileActionBuilder,
}: _MusicBoxSongAttributionValueProps) {
  const config = useAppConfig();
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const layoutSize = useElementSize(layoutRef);
  const style: CSSProperties = { ...UiTypography.label, color: UiColors.text };
  const user =
    person == null
      ? null
      : new UserSummary({
          id: person.userId,
          username: person.displayName,
          displayName: person.displayName,
          avatarUrl: person.avatarUrl,
          defaultAvatarKey: person.defaultAvatarKey,
        });
  const avatarSize = 26;
  const avatarGap = 5;
  // Canvas measurement and DOM layout can differ by a few physical pixels
  // after font fallback and device-pixel rounding. Keep enough trailing room
  // that a measured single line never loses its final glyph.
  const textLayoutSlack = 8;
  const maxWidth = layoutSize?.width ?? 0;
  const avatarWidth = showAvatar ? avatarSize + avatarGap : 0;
  const maxTextWidth = Math.max(maxWidth - avatarWidth, 1);
  const measurementWidth = Math.min(Math.max(maxTextWidth - textLayoutSlack, 1), maxTextWidth);
  const measured = value.length === 0 ? ' ' : value;
  const lineCount = measureLineCount(measured, style, measurementWidth);
  const longestLine = lineCount > 1 ? measurementWidth : measureTextWidth(measured, style);
  const textWidth = lineCount > 1 ? maxTextWidth : Math.min(Math.max(longestLine + textLayoutSlack, 1), maxTextWidth);
  return (
    <Row
      ref={layoutRef}
      data-testid="music-box-song-attribution-value-group"
      mainAxisAlignment="end"
      crossAxisAlignment="center"
    >
      {showAvatar ? (
        <>
          <UserHoverCard
            user={user!}
            currentUser={currentUser ?? undefined}
            onResolveProfile={onResolveUserProfile ?? undefined}
            onResolveRoomProfile={onResolveRoomProfile ?? undefined}
            onEnterCommonRoom={onEnterCommonRoom ?? undefined}
            profileActionBuilder={userProfileActionBuilder ?? undefined}
            showRoomRole
          >
            <div data-testid="music-box-song-attribution-avatar" style={{ display: 'flex', flexShrink: 0 }}>
              <Avatar
                label={person!.avatarLabel}
                imageUrl={config.resolveAssetUrl(person!.avatarUrl)}
                defaultAvatarKey={person!.defaultAvatarKey}
                size={avatarSize}
                showBorder={false}
              />
            </div>
          </UserHoverCard>
          <SizedBox width={avatarGap} />
        </>
      ) : null}
      <SizedBox width={textWidth}>
        <Text data-testid="music-box-song-attribution-name" textAlign="right" style={style}>
          {value}
        </Text>
      </SizedBox>
    </Row>
  );
}

export function _MusicBoxSongDetailRow({ label, value }: { label: string; value: string }) {
  return (
    <Row crossAxisAlignment="start" style={{ paddingBottom: 6 }}>
      <SizedBox width={38}>
        <HoverCardSelectableText value={label} style={{ ...UiTypography.label, color: UiColors.textMuted }} />
      </SizedBox>
      <Expanded>
        <HoverCardSelectableText value={value} textAlign="right" style={{ ...UiTypography.label, color: UiColors.text }} maxLines={12} />
      </Expanded>
    </Row>
  );
}

export const _musicBoxBvidPattern = /BV[0-9A-Za-z]+/i;

export function _musicBoxBilibiliBvId(trackId: string): string {
  const raw = trackId.trim();
  const match = raw.match(_musicBoxBvidPattern)?.[0];
  if (match == null || match.length <= 2) return raw.length === 0 ? '未知' : raw;
  return `BV${match.substring(2)}`;
}

export function _MusicBoxAdaptiveFullText({
  text,
  style,
  'data-testid': testId,
}: {
  text: string;
  style: CSSProperties;
  /** Dart `key` (test id). */
  'data-testid'?: string;
}) {
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const layoutSize = useElementSize(layoutRef);
  const baseSize = typeof style.fontSize === 'number' ? style.fontSize : 16;
  const lineCount = measureLineCount(text, style, layoutSize?.width ?? Number.POSITIVE_INFINITY);
  const reduction = lineCount <= 2 ? 0 : Math.min(Math.max(lineCount - 2, 1), 4);
  const fontSize = Math.min(Math.max(baseSize - reduction, 11), baseSize);
  return (
    <div ref={layoutRef} data-testid={testId} style={{ width: '100%', minWidth: 0 }}>
      <HoverCardSelectableText value={text} style={{ ...style, fontSize }} maxLines={lineCount < 1 ? 1 : lineCount} />
    </div>
  );
}

/**
 * Opens the add-to-playlist picker for a queue item or catalog track and
 * submits to the personal or room playlist API according to the chosen
 * target's scope.
 *
 * PORT: the Dart `BuildContext` argument is dropped (dialogs and notices are
 * global in the port).
 */
export async function _musicBoxAddToPlaylist(opts: {
  controller: MusicBoxController | null | undefined;
  roomId: string | null | undefined;
  item?: MusicBoxQueueItem | null;
  result?: MusicBoxSearchResult | null;
  durationMs?: number | null;
}): Promise<void> {
  const { controller, roomId, item, result, durationMs } = opts;
  if (controller == null) return;
  // Dart: assert(item != null || result != null).

  const loadTargets = async (): Promise<MusicTrackPlaylistTarget[]> => {
    const targets: MusicTrackPlaylistTarget[] = [];
    let roomError: unknown = null;
    let personalError: unknown = null;
    if (roomId != null) {
      try {
        const page = await controller.loadRoomPlaylists({ roomId });
        targets.push(...page.playlists.map((playlist) => MusicTrackPlaylistTarget.room({ playlist, roomId })));
      } catch (error) {
        roomError = error;
      }
    }
    try {
      const page = await controller.loadMyPlaylists();
      targets.push(...page.playlists.map((playlist) => MusicTrackPlaylistTarget.personal(playlist)));
    } catch (error) {
      personalError = error;
    }
    if (targets.length === 0 && (roomError != null || personalError != null)) {
      throw roomError ?? personalError;
    }
    return targets;
  };

  const add = async (target: MusicTrackPlaylistTarget): Promise<void> => {
    if (target.roomScoped) {
      const targetRoomId = target.roomId ?? roomId;
      if (targetRoomId == null) return;
      if (item != null) {
        await controller.addQueueItemToRoomPlaylist({
          roomId: targetRoomId,
          playlistId: target.playlist.id,
          item,
        });
      } else {
        await controller.addSearchResultToRoomPlaylist({
          roomId: targetRoomId,
          playlistId: target.playlist.id,
          result: result!,
          durationMs,
        });
      }
      return;
    }
    if (item != null) {
      await controller.addQueueItemToMyPlaylist({
        playlistId: target.playlist.id,
        item,
      });
    } else {
      await controller.addSearchResultToMyPlaylist({
        playlistId: target.playlist.id,
        result: result!,
        durationMs,
      });
    }
  };

  const target = await showMusicTrackPlaylistTargetDialog({ loadTargets, onAdd: add });
  if (target == null) return;
  showFloatingSuccessNotice(`已添加到「${target.playlist.name}」`);
}
