/**
 * Port of `lib/src/home/live_channel_members.dart` (part of `live_channel_pane`).
 */
import { useRef, useState, type CSSProperties, type ReactNode } from 'react';

import { maxParticipantVoiceVolume, normalizedParticipantVoiceVolume, participantVoiceVolumePercentText } from '@gang-chat/core/app/audio_levels';
import * as live_display from '@gang-chat/core/app/live_display';
import * as room_display from '@gang-chat/core/app/room_display';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import type { LiveVideoTrack } from '@gang-chat/core/live/live_session';
import type { CurrentUser, LiveParticipant, PublicRoom, UserSummary } from '@gang-chat/core/protocol/models';

import { Center, Row, SizedBox, Spacer, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useElementSize } from '../lib/use_element_size';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Icon, Icons, type IconData } from '../ui/icon';
import { roleBadgeForegroundColorForLabel } from '../ui/role_badge';
import { PressableSurface } from '../ui/surface';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiRadii, UiTypography, withAlpha } from '../ui/tokens';
import { _HoverInfo, _HoverVolumeButton, _hoverVolumePanelHeight, _volumeLevelIcon } from './live_channel_controls';
import { _LiveMediaVideo, _memberPreviewTrack, _selectableTrack } from './live_channel_media';
import {
  _controlButtonSize,
  _memberCardMinTwoColumnWidth,
  _memberCardSpacing,
  _memberCardSurfaceHeight,
  _memberCardWidth,
  _memberIdleBackground,
  _memberSpeakingBackground,
} from './live_channel_pane';
import { UserHoverCard, type RoomProfileResolver, type UserProfileActionBuilder, type UserProfileResolver } from './room_profile_card';

function _clampDouble(value: number, lower: number, upper: number): number {
  return Math.min(Math.max(value, lower), upper);
}

const _fill: CSSProperties = { position: 'absolute', inset: 0 };

export function _LiveRoomHeader({ title }: { title: string }) {
  return (
    <Row>
      <Icon icon={Icons.graphic_eq} color={UiColors.textSecondary} size={20} />
      <SizedBox width={8} />
      <div style={{ flex: '1 1 0', minWidth: 0 }}>
        <Text maxLines={1} overflow="ellipsis" style={{ ...UiTypography.title, fontSize: 16 }}>
          {title}
        </Text>
      </div>
    </Row>
  );
}

export interface _LiveMemberStageProps {
  loading: boolean;
  participants: readonly LiveParticipant[];
  currentUser: CurrentUser;
  localMicMuted: boolean;
  localHeadphonesMuted: boolean;
  speakingUserIds: ReadonlySet<string>;
  liveKitMicMutedByParticipantId: ReadonlyMap<string, boolean>;
  videoTracks: readonly LiveVideoTrack[];
  stageTrack: LiveVideoTrack | null;
  onSelectStage: (track: LiveVideoTrack) => void;
  onSelectScreenShareStage: (identity: string) => void;
  onSelectCameraStage: (identity: string) => void;
  onToggleMic: (() => void) | null;
  onToggleHeadphones: () => void;
  participantVoiceVolume: (userId: string) => number;
  onParticipantVoiceVolumeChanged: (userId: string, volume: number) => void;
  onParticipantVoiceMuteToggled: (userId: string) => void;
  canModerateParticipant: (participant: LiveParticipant) => boolean;
  onToggleParticipantMicModeration: (participant: LiveParticipant) => void;
  onToggleParticipantHeadphonesModeration: (participant: LiveParticipant) => void;
  canRemoveParticipant: (participant: LiveParticipant) => boolean;
  onRemoveParticipant: (participant: LiveParticipant) => void;
  onResolveParticipantProfile?: UserProfileResolver | null;
  onResolveParticipantRoomProfile?: RoomProfileResolver | null;
  onEnterParticipantProfileRoom?: ((room: PublicRoom) => void) | null;
  participantProfileActionBuilder?: UserProfileActionBuilder | null;
}

export function _LiveMemberStage(props: _LiveMemberStageProps) {
  const { loading, participants, currentUser, localMicMuted, localHeadphonesMuted, speakingUserIds, stageTrack } = props;
  // LayoutBuilder over the stage area.
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const constraints = useElementSize(layoutRef);

  let content: ReactNode = null;
  if (loading) {
    content = (
      <Center style={_fill}>
        <CircularProgressIndicator color={UiColors.accent} />
      </Center>
    );
  } else if (participants.length === 0) {
    content = (
      <Center style={_fill}>
        <Text style={{ ...UiTypography.body, color: UiColors.textMuted }}>语音频道里还没有人</Text>
      </Center>
    );
  } else if (constraints != null) {
    const twoColumnCardWidth = (constraints.width - _memberCardSpacing) / 2;
    const cardDimension =
      twoColumnCardWidth >= _memberCardMinTwoColumnWidth
        ? _clampDouble(twoColumnCardWidth, _memberCardMinTwoColumnWidth, _memberCardWidth)
        : _clampDouble(constraints.width, 0.0, _memberCardWidth);

    content = (
      <div style={{ ..._fill, overflowY: 'auto', overflowX: 'hidden' }}>
        <div
          style={{
            minHeight: '100%',
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'row',
            flexWrap: 'wrap',
            justifyContent: 'flex-start',
            alignItems: 'flex-start',
            alignContent: 'flex-start',
            gap: _memberCardSpacing,
          }}
        >
          {participants.map((participant) => {
            const local = participant.user.id === currentUser.id;
            return (
              <_LiveMemberCard
                key={participant.user.id}
                dimension={cardDimension}
                participant={
                  local
                    ? participant.copyWith({
                        micMuted: localMicMuted,
                        headphonesMuted: localHeadphonesMuted,
                        headphonesListening: !localHeadphonesMuted && !participant.headphonesBlocked,
                      })
                    : participant
                }
                currentUser={currentUser}
                local={local}
                speaking={speakingUserIds.has(participant.user.id)}
                liveKitMicMuted={local ? localMicMuted : (props.liveKitMicMutedByParticipantId.get(participant.user.id) ?? null)}
                screenShareFocused={stageTrack?.identity === participant.user.id && stageTrack?.isScreenShare === true}
                cameraFocused={stageTrack?.identity === participant.user.id && stageTrack?.isScreenShare === false}
                previewTrack={_memberPreviewTrack({
                  tracks: props.videoTracks,
                  userId: participant.user.id,
                  stageTrack,
                })}
                selectableTrack={_selectableTrack({
                  tracks: props.videoTracks,
                  userId: participant.user.id,
                  stageTrack,
                })}
                onSelectPreview={props.onSelectStage}
                onSelectScreenShare={props.onSelectScreenShareStage}
                onSelectCamera={props.onSelectCameraStage}
                onToggleMic={props.onToggleMic}
                onToggleHeadphones={props.onToggleHeadphones}
                participantVoiceVolume={props.participantVoiceVolume}
                onParticipantVoiceVolumeChanged={props.onParticipantVoiceVolumeChanged}
                onParticipantVoiceMuteToggled={props.onParticipantVoiceMuteToggled}
                canModerateParticipant={props.canModerateParticipant}
                onToggleParticipantMicModeration={props.onToggleParticipantMicModeration}
                onToggleParticipantHeadphonesModeration={props.onToggleParticipantHeadphonesModeration}
                canRemoveParticipant={props.canRemoveParticipant}
                onRemoveParticipant={props.onRemoveParticipant}
                onResolveParticipantProfile={props.onResolveParticipantProfile}
                onResolveParticipantRoomProfile={props.onResolveParticipantRoomProfile}
                onEnterParticipantProfileRoom={props.onEnterParticipantProfileRoom}
                participantProfileActionBuilder={props.participantProfileActionBuilder}
              />
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <div ref={layoutRef} style={{ position: 'relative', flex: '1 1 0', minHeight: 0, minWidth: 0 }}>
      {content}
    </div>
  );
}

interface _LiveMemberCardProps {
  dimension: number;
  participant: LiveParticipant;
  currentUser: CurrentUser;
  local: boolean;
  speaking: boolean;
  liveKitMicMuted: boolean | null;
  screenShareFocused: boolean;
  cameraFocused: boolean;
  previewTrack?: LiveVideoTrack | null;
  selectableTrack?: LiveVideoTrack | null;
  onSelectPreview: (track: LiveVideoTrack) => void;
  onSelectScreenShare: (identity: string) => void;
  onSelectCamera: (identity: string) => void;
  onToggleMic: (() => void) | null;
  onToggleHeadphones: () => void;
  participantVoiceVolume: (userId: string) => number;
  onParticipantVoiceVolumeChanged: (userId: string, volume: number) => void;
  onParticipantVoiceMuteToggled: (userId: string) => void;
  canModerateParticipant: (participant: LiveParticipant) => boolean;
  onToggleParticipantMicModeration: (participant: LiveParticipant) => void;
  onToggleParticipantHeadphonesModeration: (participant: LiveParticipant) => void;
  canRemoveParticipant: (participant: LiveParticipant) => boolean;
  onRemoveParticipant: (participant: LiveParticipant) => void;
  onResolveParticipantProfile?: UserProfileResolver | null;
  onResolveParticipantRoomProfile?: RoomProfileResolver | null;
  onEnterParticipantProfileRoom?: ((room: PublicRoom) => void) | null;
  participantProfileActionBuilder?: UserProfileActionBuilder | null;
}

function _LiveMemberCard(props: _LiveMemberCardProps) {
  const {
    dimension,
    participant,
    currentUser,
    local,
    speaking,
    liveKitMicMuted,
    screenShareFocused,
    cameraFocused,
    onSelectPreview,
    onSelectScreenShare,
    onSelectCamera,
    canModerateParticipant,
    canRemoveParticipant,
  } = props;
  const config = useAppConfig();
  const state = live_display.liveParticipantTileState(participant, { speaking, liveKitMicMuted });
  const name = live_display.liveUserDisplayName(participant.user);
  const nameColor = _liveMemberNameColor(participant.user, { local });
  const previewTrack = props.previewTrack ?? null;
  const selectableTrack = props.selectableTrack ?? null;
  const androidLocalCameraTap =
    HostPlatform.isAndroid && local && previewTrack != null && previewTrack.isLocal && !previewTrack.isScreenShare
      ? () => onSelectPreview(previewTrack)
      : null;
  const borderColor = state.highlighted ? UiColors.borderStrong : UiColors.border;
  const activityIcon = _participantMetaIcon(participant, { speaking });
  const canModerate = !local && canModerateParticipant(participant);
  const activityTag =
    activityIcon == null ? null : (
      <_LiveMemberActivityTag
        data-testid={`live-member-activity:${participant.user.id}`}
        label={_participantMeta(participant, { speaking })}
        icon={activityIcon}
        color={_participantMetaColor(participant, { speaking })}
      />
    );
  const statusRow = (
    <_LiveMemberStatusRow
      participant={participant}
      participantName={name}
      micMutedForDisplay={state.micMutedForDisplay}
      moderationControls={canModerate}
      onToggleMic={
        local ? props.onToggleMic : canModerate ? () => props.onToggleParticipantMicModeration(participant) : null
      }
      onToggleHeadphones={
        local
          ? props.onToggleHeadphones
          : canModerate
            ? () => props.onToggleParticipantHeadphonesModeration(participant)
            : null
      }
      voiceVolume={local ? null : props.participantVoiceVolume(participant.user.id)}
      onVoiceVolumeChanged={
        local ? null : (volume: number) => props.onParticipantVoiceVolumeChanged(participant.user.id, volume)
      }
      onVoiceVolumeToggle={local ? null : () => props.onParticipantVoiceMuteToggled(participant.user.id)}
      onRemoveMember={!local && canRemoveParticipant(participant) ? () => props.onRemoveParticipant(participant) : null}
    />
  );
  const showScreenSharePreview =
    !screenShareFocused &&
    (previewTrack?.isScreenShare === true || (participant.screenSharing && previewTrack == null));
  const showCameraPreview =
    !cameraFocused &&
    !showScreenSharePreview &&
    (previewTrack?.isScreenShare === false || (participant.cameraOn && previewTrack == null));
  const showMediaCard =
    showScreenSharePreview || showCameraPreview || (previewTrack != null && !screenShareFocused && !cameraFocused);

  if (showMediaCard) {
    const preview = showScreenSharePreview ? (
      <_StoppedLiveMediaThumbnail kind={_StoppedLiveMediaKind.screenShare} />
    ) : showCameraPreview ? (
      previewTrack != null && previewTrack.isScreenShare === false ? (
        <_LiveMemberVideo
          track={previewTrack}
          mirrored={participant.cameraMirrored}
          androidLocalCameraTap={androidLocalCameraTap}
        />
      ) : (
        <_StoppedLiveMediaThumbnail kind={_StoppedLiveMediaKind.camera} />
      )
    ) : (
      <_LiveMemberVideo track={previewTrack!} mirrored={participant.cameraMirrored} />
    );
    return _scaledSurface(
      dimension,
      <PressableSurface
        height={_memberCardWidth}
        borderRadius={UiRadii.lg}
        backgroundColor={state.highlighted ? _memberSpeakingBackground : _memberIdleBackground}
        selectedBackgroundColor={_memberSpeakingBackground}
        borderColor={borderColor}
        selectedBorderColor={UiColors.borderStrong}
        selected={state.highlighted}
        padding={0}
        interactive
        pressEffect={false}
        mouseCursor={SystemMouseCursors.basic}
      >
        <div style={{ position: 'relative', flex: '1 1 0', minHeight: 0 }}>
          <div
            role="button"
            onClick={() => {
              if (showScreenSharePreview) {
                onSelectScreenShare(participant.user.id);
                return;
              }
              if (showCameraPreview) {
                if (previewTrack != null && previewTrack.isScreenShare === false) {
                  onSelectPreview(previewTrack);
                  return;
                }
                onSelectCamera(participant.user.id);
                return;
              }
              if (previewTrack != null) {
                onSelectPreview(previewTrack);
                return;
              }
            }}
            style={{ position: 'absolute', left: 8, right: 8, top: 30, bottom: 46, cursor: SystemMouseCursors.click }}
          >
            <div
              style={{
                position: 'relative',
                width: '100%',
                height: '100%',
                borderRadius: UiRadii.md,
                overflow: 'hidden',
                backgroundColor: UiColors.surfacePressed,
              }}
            >
              {preview}
            </div>
          </div>
          <div style={{ position: 'absolute', left: 9, right: 9, top: 8 }}>
            <Row crossAxisAlignment="start">
              <div style={{ flex: '1 1 0', minWidth: 0 }}>
                <Text maxLines={1} overflow="ellipsis" style={{ ...UiTypography.label, color: nameColor, fontWeight: 600 }}>
                  {name}
                </Text>
              </div>
              {activityTag != null ? (
                <>
                  <SizedBox width={6} />
                  {activityTag}
                </>
              ) : null}
            </Row>
          </div>
          <div style={{ position: 'absolute', left: 12, right: 12, bottom: 8 }}>{statusRow}</div>
          <div
            aria-hidden="true"
            style={{
              ..._fill,
              pointerEvents: 'none',
              boxSizing: 'border-box',
              borderRadius: UiRadii.lg,
              border: `1px solid ${borderColor}`,
            }}
          />
        </div>
      </PressableSurface>,
    );
  }
  return _scaledSurface(
    dimension,
    <PressableSurface
      height={_memberCardWidth}
      borderRadius={UiRadii.lg}
      backgroundColor={state.highlighted ? _memberSpeakingBackground : _memberIdleBackground}
      selectedBackgroundColor={_memberSpeakingBackground}
      borderColor={borderColor}
      selectedBorderColor={UiColors.borderStrong}
      selected={state.highlighted}
      padding={0}
      onPressed={selectableTrack == null ? () => {} : () => onSelectPreview(selectableTrack)}
    >
      <div style={{ position: 'relative', flex: '1 1 0', minHeight: 0 }}>
        <div
          style={{
            ..._fill,
            boxSizing: 'border-box',
            padding: '33px 12px 8px 12px',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'stretch',
          }}
        >
          <Center>
            <UserHoverCard
              user={participant.user}
              currentUser={currentUser}
              onResolveProfile={props.onResolveParticipantProfile ?? null}
              onResolveRoomProfile={props.onResolveParticipantRoomProfile ?? null}
              onEnterCommonRoom={props.onEnterParticipantProfileRoom ?? null}
              profileActionBuilder={props.participantProfileActionBuilder ?? null}
              inLive
              showRoomRole
            >
              <Avatar
                label={room_display.userAvatarLabel(participant.user)}
                imageUrl={config.resolveAssetUrl(participant.user.avatarUrl)}
                defaultAvatarKey={participant.user.defaultAvatarKey}
                size={42}
                showBorder={false}
              />
            </UserHoverCard>
          </Center>
          <SizedBox height={5} />
          <Text
            maxLines={1}
            overflow="ellipsis"
            textAlign="center"
            style={{ ...UiTypography.body, color: nameColor, fontWeight: 600 }}
          >
            {name}
          </Text>
          <Spacer />
          {statusRow}
        </div>
        {/* Painted before the column in Dart; the column never overlaps it,
            so drawing it last only keeps it reachable for hover. */}
        {activityTag != null ? <div style={{ position: 'absolute', top: 8, right: 9 }}>{activityTag}</div> : null}
      </div>
    </PressableSurface>,
  );
}

/**
 * Draws the 154px card scaled to [dimension] (Dart `FittedBox(fit: fill)`
 * over a fixed-size `SizedBox`).
 */
function _scaledSurface(dimension: number, child: ReactNode): ReactNode {
  const scale = dimension / _memberCardWidth;
  return (
    <div style={{ position: 'relative', width: dimension, height: _memberCardSurfaceHeight * scale, flexShrink: 0 }}>
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          width: _memberCardWidth,
          height: _memberCardSurfaceHeight,
          transform: `scale(${scale})`,
          transformOrigin: 'top left',
        }}
      >
        {child}
      </div>
    </div>
  );
}

function _LiveMemberActivityTag({
  label,
  icon,
  color,
  'data-testid': testId,
}: {
  label: string;
  icon: IconData;
  color: string;
  'data-testid'?: string;
}) {
  return (
    <UiTooltip message={label}>
      <div
        data-testid={testId}
        aria-label={label}
        style={{ width: 24, height: 24, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <Icon icon={icon} color={color} size={15} />
      </div>
    </UiTooltip>
  );
}

interface _LiveMemberStatusRowProps {
  participant: LiveParticipant;
  participantName: string;
  micMutedForDisplay: boolean;
  moderationControls: boolean;
  onToggleMic?: (() => void) | null;
  onToggleHeadphones?: (() => void) | null;
  voiceVolume?: number | null;
  onVoiceVolumeChanged?: ((volume: number) => void) | null;
  onVoiceVolumeToggle?: (() => void) | null;
  onRemoveMember?: (() => void) | null;
}

function _LiveMemberStatusRow(props: _LiveMemberStatusRowProps) {
  const { participant, participantName, micMutedForDisplay, moderationControls, onToggleMic, onToggleHeadphones } = props;
  const listening = participant.headphonesListening && !participant.headphonesMuted && !participant.headphonesBlocked;
  const micModerated = participant.micBlocked || participant.voiceBlocked;
  const headphonesModerated = participant.headphonesBlocked;
  const voiceVolume = props.voiceVolume ?? null;
  const onVoiceVolumeChanged = props.onVoiceVolumeChanged ?? null;
  const onRemoveMember = props.onRemoveMember ?? null;
  const buttons: Array<{ key: string; node: ReactNode }> = [
    {
      key: `live-member-status:mic:${participant.user.id}`,
      node: (
        <_LiveMemberStatusButton
          data-testid={`live-member-status:mic:${participant.user.id}`}
          icon={micMutedForDisplay ? Icons.mic_off : Icons.mic}
          active={!micMutedForDisplay}
          danger={micModerated}
          onPressed={onToggleMic ?? null}
          tooltip={_liveMemberMicTooltip({
            micMutedForDisplay,
            micModerated,
            headphonesModerated,
            moderationControls,
          })}
        />
      ),
    },
    {
      key: `live-member-status:headphones:${participant.user.id}`,
      node: (
        <_LiveMemberStatusButton
          data-testid={`live-member-status:headphones:${participant.user.id}`}
          icon={listening ? Icons.headphones : Icons.headset_off}
          active={listening}
          danger={headphonesModerated}
          onPressed={onToggleHeadphones ?? null}
          tooltip={_liveMemberHeadphonesTooltip({
            listening,
            headphonesModerated,
            moderationControls,
          })}
        />
      ),
    },
  ];
  if (voiceVolume != null && onVoiceVolumeChanged != null) {
    const toggleLabel = _memberVoiceVolumeToggleLabel({ participantName, voiceVolume });
    buttons.push({
      key: `live-member-status:voice-volume:${participant.user.id}`,
      node: (
        <_HoverVolumeButton
          data-testid={`live-member-status:voice-volume:${participant.user.id}`}
          value={voiceVolume}
          semanticLabel={`${participantName}语音音量`}
          infoMessage={toggleLabel}
          onChanged={onVoiceVolumeChanged}
          maxValue={maxParticipantVoiceVolume}
          valueFormatter={participantVoiceVolumePercentText}
          panelWidth={_memberStatusButtonDimension()}
          panelHeight={_memberVoiceVolumePanelHeight(_memberStatusButtonDimension())}
          style={{ width: '100%', height: '100%' }}
        >
          <_LiveMemberStatusButton
            icon={_memberVoiceVolumeIcon(voiceVolume)}
            active={voiceVolume > 0}
            tooltip={toggleLabel}
            onPressed={props.onVoiceVolumeToggle ?? null}
            showHoverInfo={false}
          />
        </_HoverVolumeButton>
      ),
    });
  }
  if (onRemoveMember != null) {
    buttons.push({
      key: `live-member-status:kick:${participant.user.id}`,
      node: (
        <_LiveMemberStatusButton
          data-testid={`live-member-status:kick:${participant.user.id}`}
          icon={Icons.exit_to_app}
          active={false}
          danger
          tooltip="踢出语音频道"
          onPressed={onRemoveMember}
        />
      ),
    });
  }

  // LayoutBuilder: four status squares span the row's width.
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const constraints = useElementSize(layoutRef);
  const dimension = (constraints?.width ?? 0) / 4;
  const rowWidth = dimension * buttons.length;
  return (
    <div ref={layoutRef} style={{ width: '100%', display: 'flex', justifyContent: 'center' }}>
      {constraints != null ? (
        <div style={{ width: rowWidth, height: dimension, flexShrink: 0, display: 'flex', flexDirection: 'row', justifyContent: 'center' }}>
          {buttons.map((button) => (
            <div key={button.key} style={{ width: dimension, height: dimension, flexShrink: 0, display: 'flex' }}>
              {button.node}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function _LiveMemberStatusButton({
  icon,
  active,
  tooltip,
  onPressed = null,
  danger = false,
  showHoverInfo = true,
  'data-testid': testId,
}: {
  icon: IconData;
  active: boolean;
  tooltip: string;
  onPressed?: (() => void) | null;
  danger?: boolean;
  showHoverInfo?: boolean;
  'data-testid'?: string;
}) {
  const foreground = danger ? UiColors.danger : active ? UiColors.accent : UiColors.textMuted;
  const enabled = onPressed != null;
  const button = (
    <div
      role="button"
      aria-disabled={!enabled}
      aria-pressed={active}
      aria-label={tooltip}
      data-testid={testId}
      onClick={() => onPressed?.()}
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      }}
    >
      <Icon icon={icon} color={foreground} size={13} />
    </div>
  );
  if (!showHoverInfo) return button;
  return <_HoverInfo message={tooltip}>{button}</_HoverInfo>;
}

function _memberVoiceVolumeIcon(volume: number): IconData {
  return _volumeLevelIcon(normalizedParticipantVoiceVolume(volume));
}

function _memberVoiceVolumeToggleLabel(opts: { participantName: string; voiceVolume: number }): string {
  const { participantName, voiceVolume } = opts;
  return voiceVolume <= 0 ? `取消静音${participantName}` : `静音${participantName}`;
}

// PORT: Dart `const _memberStatusButtonDimension = (_memberCardWidth - 24.0) / 4;`
// is read lazily — the part modules import the library root in a cycle, so a
// module-level read of its constant could run before it is initialised.
function _memberStatusButtonDimension(): number {
  return (_memberCardWidth - 24.0) / 4;
}

function _memberVoiceVolumePanelHeight(buttonDimension: number): number {
  return (_hoverVolumePanelHeight * buttonDimension) / _controlButtonSize;
}

function _liveMemberMicTooltip(opts: {
  micMutedForDisplay: boolean;
  micModerated: boolean;
  headphonesModerated: boolean;
  moderationControls: boolean;
}): string {
  const { micMutedForDisplay, micModerated, moderationControls } = opts;
  if (moderationControls) {
    return micModerated ? '取消麦克风静音' : '麦克风静音';
  }
  if (micModerated) return '已被麦克风静音';
  return micMutedForDisplay ? '麦克风关闭' : '麦克风开启';
}

function _liveMemberHeadphonesTooltip(opts: {
  listening: boolean;
  headphonesModerated: boolean;
  moderationControls: boolean;
}): string {
  const { listening, headphonesModerated, moderationControls } = opts;
  if (moderationControls) {
    return headphonesModerated ? '取消耳机静音' : '耳机静音';
  }
  if (headphonesModerated) return '已被耳机静音';
  return listening ? '正在收听' : '已关闭收听';
}

function _liveMemberNameColor(user: UserSummary, opts: { local: boolean }): string {
  if (opts.local) return UiColors.accent;
  return roleBadgeForegroundColorForLabel(room_display.roomRoleLabel(user));
}

function _participantMetaColor(participant: LiveParticipant, opts: { speaking: boolean }): string {
  if (participant.screenSharing || participant.cameraOn || opts.speaking) {
    return UiColors.accent;
  }
  if (participant.micMuted) return UiColors.textMuted;
  return UiColors.textSecondary;
}

function _participantMetaIcon(participant: LiveParticipant, opts: { speaking: boolean }): IconData | null {
  if (participant.screenSharing) return Icons.screen_share_outlined;
  if (participant.cameraOn) return Icons.videocam;
  if (opts.speaking) return Icons.mic;
  return null;
}

function _LiveMemberVideo({
  track,
  mirrored,
  androidLocalCameraTap = null,
}: {
  track: LiveVideoTrack;
  mirrored: boolean;
  androidLocalCameraTap?: (() => void) | null;
}) {
  const [hovered, setHovered] = useState(false);
  const video = <_LiveMediaVideo track={track} mirrored={mirrored} />;
  const content = track.isScreenShare ? (
    <div style={_fill}>{video}</div>
  ) : (
    <div style={{ ..._fill, borderRadius: UiRadii.lg, overflow: 'hidden' }}>{video}</div>
  );
  return (
    <div
      data-testid={`live-member:video-thumbnail:${track.identity}:${track.isScreenShare}`}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{ ..._fill, cursor: SystemMouseCursors.click }}
    >
      {content}
      <div
        data-testid={`live-member:video-hover:${track.identity}:${track.isScreenShare}`}
        style={{
          ..._fill,
          pointerEvents: 'none',
          opacity: hovered ? 1 : 0,
          transition: 'opacity 120ms linear',
          backgroundColor: withAlpha(UiColors.black, 0.32),
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon icon={Icons.search} color={withAlpha(UiColors.white, 0.92)} size={32} />
      </div>
      {androidLocalCameraTap != null ? (
        <div
          role="button"
          aria-label="预览自己的摄像头"
          data-testid="live-member:android-local-camera-tap"
          onClick={(event) => {
            // The tap is handled here instead of by the surrounding preview area.
            event.stopPropagation();
            androidLocalCameraTap();
          }}
          style={_fill}
        />
      ) : null}
    </div>
  );
}

enum _StoppedLiveMediaKind {
  camera = 'camera',
  screenShare = 'screenShare',
}

function _StoppedLiveMediaThumbnail({ kind }: { kind: _StoppedLiveMediaKind }) {
  const [hovered, setHovered] = useState(false);
  const isCamera = kind === _StoppedLiveMediaKind.camera;
  return (
    <div
      data-testid={isCamera ? 'live-member:camera-thumbnail' : 'live-member:screen-share-thumbnail'}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{ ..._fill, cursor: SystemMouseCursors.click }}
    >
      <div
        style={{
          ..._fill,
          backgroundColor: UiColors.surfacePressed,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon icon={isCamera ? Icons.videocam_outlined : Icons.screen_share_outlined} color={UiColors.textMuted} size={30} />
      </div>
      <div style={{ ..._fill, backgroundColor: withAlpha(UiColors.black, 0.32) }} />
      <div
        style={{
          ..._fill,
          opacity: hovered ? 1 : 0,
          transition: 'opacity 120ms linear',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon icon={Icons.search} color={withAlpha(UiColors.white, 0.92)} size={32} />
      </div>
    </div>
  );
}

function _participantMeta(participant: LiveParticipant, opts: { speaking: boolean }): string {
  if (participant.screenSharing) return '正在共享屏幕';
  if (participant.cameraOn) return '摄像头已开启';
  if (participant.micMuted) return '已静音';
  if (opts.speaking) return '正在说话';
  return '正在收听';
}
