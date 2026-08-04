part of 'live_channel_pane.dart';

class _LiveRoomHeader extends StatelessWidget {
  const _LiveRoomHeader({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Icon(Icons.graphic_eq, color: UiColors.textSecondary, size: 20),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.title.copyWith(fontSize: 16),
          ),
        ),
      ],
    );
  }
}

class _LiveMemberStage extends StatelessWidget {
  const _LiveMemberStage({
    required this.loading,
    required this.participants,
    required this.currentUser,
    required this.localMicMuted,
    required this.localHeadphonesMuted,
    required this.speakingUserIds,
    required this.liveKitMicMutedByParticipantId,
    required this.videoTracks,
    required this.stageTrack,
    required this.onSelectStage,
    required this.onSelectScreenShareStage,
    required this.onSelectCameraStage,
    required this.onToggleMic,
    required this.onToggleHeadphones,
    required this.participantVoiceVolume,
    required this.onParticipantVoiceVolumeChanged,
    required this.onParticipantVoiceMuteToggled,
    required this.canModerateParticipant,
    required this.onToggleParticipantMicModeration,
    required this.onToggleParticipantHeadphonesModeration,
    required this.canRemoveParticipant,
    required this.onRemoveParticipant,
    this.onResolveParticipantProfile,
    this.onResolveParticipantRoomProfile,
    this.onEnterParticipantProfileRoom,
    this.participantProfileActionBuilder,
  });

  final bool loading;
  final List<LiveParticipant> participants;
  final CurrentUser currentUser;
  final bool localMicMuted;
  final bool localHeadphonesMuted;
  final Set<String> speakingUserIds;
  final Map<String, bool> liveKitMicMutedByParticipantId;
  final List<LiveVideoTrack> videoTracks;
  final LiveVideoTrack? stageTrack;
  final ValueChanged<LiveVideoTrack> onSelectStage;
  final ValueChanged<String> onSelectScreenShareStage;
  final ValueChanged<String> onSelectCameraStage;
  final VoidCallback? onToggleMic;
  final VoidCallback onToggleHeadphones;
  final double Function(String userId) participantVoiceVolume;
  final void Function(String userId, double volume)
  onParticipantVoiceVolumeChanged;
  final ValueChanged<String> onParticipantVoiceMuteToggled;
  final bool Function(LiveParticipant participant) canModerateParticipant;
  final ValueChanged<LiveParticipant> onToggleParticipantMicModeration;
  final ValueChanged<LiveParticipant> onToggleParticipantHeadphonesModeration;
  final bool Function(LiveParticipant participant) canRemoveParticipant;
  final ValueChanged<LiveParticipant> onRemoveParticipant;
  final UserProfileResolver? onResolveParticipantProfile;
  final RoomProfileResolver? onResolveParticipantRoomProfile;
  final ValueChanged<PublicRoom>? onEnterParticipantProfileRoom;
  final UserProfileActionBuilder? participantProfileActionBuilder;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (loading) {
          return const Center(
            child: CircularProgressIndicator(color: UiColors.accent),
          );
        }
        if (participants.isEmpty) {
          return Center(
            child: Text(
              '语音频道里还没有人',
              style: UiTypography.body.copyWith(color: UiColors.textMuted),
            ),
          );
        }

        final twoColumnCardWidth =
            (constraints.maxWidth - _memberCardSpacing) / 2;
        final cardDimension = twoColumnCardWidth >= _memberCardMinTwoColumnWidth
            ? twoColumnCardWidth.clamp(
                _memberCardMinTwoColumnWidth,
                _memberCardWidth,
              )
            : constraints.maxWidth.clamp(0.0, _memberCardWidth);

        return SingleChildScrollView(
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: Align(
              alignment: Alignment.topLeft,
              child: Wrap(
                spacing: _memberCardSpacing,
                runSpacing: _memberCardSpacing,
                children: [
                  for (final participant in participants)
                    _LiveMemberCard(
                      dimension: cardDimension,
                      participant: participant.user.id == currentUser.id
                          ? participant.copyWith(
                              micMuted: localMicMuted,
                              headphonesMuted: localHeadphonesMuted,
                              headphonesListening:
                                  !localHeadphonesMuted &&
                                  !participant.headphonesBlocked,
                            )
                          : participant,
                      currentUser: currentUser,
                      local: participant.user.id == currentUser.id,
                      speaking: speakingUserIds.contains(participant.user.id),
                      liveKitMicMuted: participant.user.id == currentUser.id
                          ? localMicMuted
                          : liveKitMicMutedByParticipantId[participant.user.id],
                      screenShareFocused:
                          stageTrack?.identity == participant.user.id &&
                          stageTrack?.isScreenShare == true,
                      cameraFocused:
                          stageTrack?.identity == participant.user.id &&
                          stageTrack?.isScreenShare == false,
                      previewTrack: _memberPreviewTrack(
                        tracks: videoTracks,
                        userId: participant.user.id,
                        stageTrack: stageTrack,
                      ),
                      selectableTrack: _selectableTrack(
                        tracks: videoTracks,
                        userId: participant.user.id,
                        stageTrack: stageTrack,
                      ),
                      onSelectPreview: onSelectStage,
                      onSelectScreenShare: onSelectScreenShareStage,
                      onSelectCamera: onSelectCameraStage,
                      onToggleMic: onToggleMic,
                      onToggleHeadphones: onToggleHeadphones,
                      participantVoiceVolume: participantVoiceVolume,
                      onParticipantVoiceVolumeChanged:
                          onParticipantVoiceVolumeChanged,
                      onParticipantVoiceMuteToggled:
                          onParticipantVoiceMuteToggled,
                      canModerateParticipant: canModerateParticipant,
                      onToggleParticipantMicModeration:
                          onToggleParticipantMicModeration,
                      onToggleParticipantHeadphonesModeration:
                          onToggleParticipantHeadphonesModeration,
                      canRemoveParticipant: canRemoveParticipant,
                      onRemoveParticipant: onRemoveParticipant,
                      onResolveParticipantProfile: onResolveParticipantProfile,
                      onResolveParticipantRoomProfile:
                          onResolveParticipantRoomProfile,
                      onEnterParticipantProfileRoom:
                          onEnterParticipantProfileRoom,
                      participantProfileActionBuilder:
                          participantProfileActionBuilder,
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _LiveMemberCard extends StatelessWidget {
  const _LiveMemberCard({
    required this.dimension,
    required this.participant,
    required this.currentUser,
    required this.local,
    required this.speaking,
    required this.liveKitMicMuted,
    required this.screenShareFocused,
    required this.cameraFocused,
    required this.onSelectPreview,
    required this.onSelectScreenShare,
    required this.onSelectCamera,
    required this.onToggleMic,
    required this.onToggleHeadphones,
    required this.participantVoiceVolume,
    required this.onParticipantVoiceVolumeChanged,
    required this.onParticipantVoiceMuteToggled,
    required this.canModerateParticipant,
    required this.onToggleParticipantMicModeration,
    required this.onToggleParticipantHeadphonesModeration,
    required this.canRemoveParticipant,
    required this.onRemoveParticipant,
    this.onResolveParticipantProfile,
    this.onResolveParticipantRoomProfile,
    this.onEnterParticipantProfileRoom,
    this.participantProfileActionBuilder,
    this.selectableTrack,
    this.previewTrack,
  });

  final double dimension;
  final LiveParticipant participant;
  final CurrentUser currentUser;
  final bool local;
  final bool speaking;
  final bool? liveKitMicMuted;
  final bool screenShareFocused;
  final bool cameraFocused;
  final LiveVideoTrack? previewTrack;
  final LiveVideoTrack? selectableTrack;
  final ValueChanged<LiveVideoTrack> onSelectPreview;
  final ValueChanged<String> onSelectScreenShare;
  final ValueChanged<String> onSelectCamera;
  final VoidCallback? onToggleMic;
  final VoidCallback onToggleHeadphones;
  final double Function(String userId) participantVoiceVolume;
  final void Function(String userId, double volume)
  onParticipantVoiceVolumeChanged;
  final ValueChanged<String> onParticipantVoiceMuteToggled;
  final bool Function(LiveParticipant participant) canModerateParticipant;
  final ValueChanged<LiveParticipant> onToggleParticipantMicModeration;
  final ValueChanged<LiveParticipant> onToggleParticipantHeadphonesModeration;
  final bool Function(LiveParticipant participant) canRemoveParticipant;
  final ValueChanged<LiveParticipant> onRemoveParticipant;
  final UserProfileResolver? onResolveParticipantProfile;
  final RoomProfileResolver? onResolveParticipantRoomProfile;
  final ValueChanged<PublicRoom>? onEnterParticipantProfileRoom;
  final UserProfileActionBuilder? participantProfileActionBuilder;

  @override
  Widget build(BuildContext context) {
    final state = live_display.liveParticipantTileState(
      participant,
      speaking: speaking,
      liveKitMicMuted: liveKitMicMuted,
    );
    final name = live_display.liveUserDisplayName(participant.user);
    final nameColor = _liveMemberNameColor(participant.user, local: local);
    final previewTrack = this.previewTrack;
    final androidLocalCameraTap =
        Theme.of(context).platform == TargetPlatform.android &&
            local &&
            previewTrack != null &&
            previewTrack.isLocal &&
            !previewTrack.isScreenShare
        ? () => onSelectPreview(previewTrack)
        : null;
    final borderColor = state.highlighted
        ? UiColors.borderStrong
        : UiColors.border;
    final activityIcon = _participantMetaIcon(participant, speaking: speaking);
    final canModerate = !local && canModerateParticipant(participant);
    final activityTag = activityIcon == null
        ? null
        : _LiveMemberActivityTag(
            key: ValueKey<String>(
              'live-member-activity:${participant.user.id}',
            ),
            label: _participantMeta(participant, speaking: speaking),
            icon: activityIcon,
            color: _participantMetaColor(participant, speaking: speaking),
          );
    final statusRow = _LiveMemberStatusRow(
      participant: participant,
      participantName: name,
      micMutedForDisplay: state.micMutedForDisplay,
      moderationControls: canModerate,
      onToggleMic: local
          ? onToggleMic
          : canModerate
          ? () => onToggleParticipantMicModeration(participant)
          : null,
      onToggleHeadphones: local
          ? onToggleHeadphones
          : canModerate
          ? () => onToggleParticipantHeadphonesModeration(participant)
          : null,
      voiceVolume: local ? null : participantVoiceVolume(participant.user.id),
      onVoiceVolumeChanged: local
          ? null
          : (volume) =>
                onParticipantVoiceVolumeChanged(participant.user.id, volume),
      onVoiceVolumeToggle: local
          ? null
          : () => onParticipantVoiceMuteToggled(participant.user.id),
      onRemoveMember: !local && canRemoveParticipant(participant)
          ? () => onRemoveParticipant(participant)
          : null,
    );
    final showScreenSharePreview =
        !screenShareFocused &&
        (previewTrack?.isScreenShare == true ||
            (participant.screenSharing && previewTrack == null));
    final showCameraPreview =
        !cameraFocused &&
        !showScreenSharePreview &&
        (previewTrack?.isScreenShare == false ||
            (participant.cameraOn && previewTrack == null));
    final showMediaCard =
        showScreenSharePreview ||
        showCameraPreview ||
        (previewTrack != null && !screenShareFocused && !cameraFocused);
    if (showMediaCard) {
      final preview = showScreenSharePreview
          ? const _StoppedLiveMediaThumbnail(
              kind: _StoppedLiveMediaKind.screenShare,
            )
          : showCameraPreview
          ? previewTrack != null && previewTrack.isScreenShare == false
                ? _LiveMemberVideo(
                    track: previewTrack,
                    mirrored: participant.cameraMirrored,
                    androidLocalCameraTap: androidLocalCameraTap,
                  )
                : const _StoppedLiveMediaThumbnail(
                    kind: _StoppedLiveMediaKind.camera,
                  )
          : _LiveMemberVideo(
              track: previewTrack!,
              mirrored: participant.cameraMirrored,
            );
      return _scaledSurface(
        child: PressableSurface(
          height: _memberCardWidth,
          hoverLift: _memberCardHoverLift,
          baseDepth: _memberCardBaseDepth,
          borderRadius: UiRadii.lg,
          backgroundColor: state.highlighted
              ? _memberSpeakingBackground
              : _memberIdleBackground,
          selectedBackgroundColor: _memberSpeakingBackground,
          borderColor: borderColor,
          selectedBorderColor: UiColors.borderStrong,
          selected: state.highlighted,
          padding: EdgeInsets.zero,
          interactive: true,
          pressEffect: false,
          mouseCursor: SystemMouseCursors.basic,
          child: Stack(
            fit: StackFit.expand,
            children: [
              Positioned(
                left: 8,
                right: 8,
                top: 30,
                bottom: 46,
                child: MouseRegion(
                  cursor: SystemMouseCursors.click,
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: () {
                      if (showScreenSharePreview) {
                        onSelectScreenShare(participant.user.id);
                        return;
                      }
                      if (showCameraPreview) {
                        if (previewTrack != null &&
                            previewTrack.isScreenShare == false) {
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
                    },
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(UiRadii.md),
                      child: ColoredBox(
                        color: UiColors.surfacePressed,
                        child: preview,
                      ),
                    ),
                  ),
                ),
              ),
              Positioned(
                left: 9,
                right: 9,
                top: 8,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(
                        name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.label.copyWith(
                          color: nameColor,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    if (activityTag != null) ...[
                      const SizedBox(width: 6),
                      activityTag,
                    ],
                  ],
                ),
              ),
              Positioned(left: 12, right: 12, bottom: 8, child: statusRow),
              Positioned.fill(
                child: IgnorePointer(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(UiRadii.lg),
                      border: Border.all(color: borderColor),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }
    return _scaledSurface(
      child: PressableSurface(
        height: _memberCardWidth,
        hoverLift: _memberCardHoverLift,
        baseDepth: _memberCardBaseDepth,
        borderRadius: UiRadii.lg,
        backgroundColor: state.highlighted
            ? _memberSpeakingBackground
            : _memberIdleBackground,
        selectedBackgroundColor: _memberSpeakingBackground,
        borderColor: borderColor,
        selectedBorderColor: UiColors.borderStrong,
        selected: state.highlighted,
        padding: EdgeInsets.zero,
        onPressed: selectableTrack == null
            ? () {}
            : () => onSelectPreview(selectableTrack!),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (activityTag != null)
              Positioned(top: 8, right: 9, child: activityTag),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 33, 12, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Center(
                    child: UserHoverCard(
                      user: participant.user,
                      currentUser: currentUser,
                      onResolveProfile: onResolveParticipantProfile,
                      onResolveRoomProfile: onResolveParticipantRoomProfile,
                      onEnterCommonRoom: onEnterParticipantProfileRoom,
                      profileActionBuilder: participantProfileActionBuilder,
                      inLive: true,
                      showRoomRole: true,
                      child: Avatar(
                        label: room_display.userAvatarLabel(participant.user),
                        imageUrl: AppConfigScope.of(
                          context,
                        ).resolveAssetUrl(participant.user.avatarUrl),
                        defaultAvatarKey: participant.user.defaultAvatarKey,
                        size: 42,
                        showBorder: false,
                      ),
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: UiTypography.body.copyWith(
                      color: nameColor,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const Spacer(),
                  statusRow,
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _scaledSurface({required Widget child}) {
    final scale = dimension / _memberCardWidth;
    return SizedBox(
      width: dimension,
      height: _memberCardSurfaceHeight * scale,
      child: FittedBox(
        alignment: Alignment.topLeft,
        fit: BoxFit.fill,
        child: SizedBox(
          width: _memberCardWidth,
          height: _memberCardSurfaceHeight,
          child: child,
        ),
      ),
    );
  }
}

class _LiveMemberActivityTag extends StatelessWidget {
  const _LiveMemberActivityTag({
    super.key,
    required this.label,
    required this.icon,
    required this.color,
  });

  final String label;
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: label,
      child: Semantics(
        label: label,
        child: SizedBox.square(
          dimension: 24,
          child: Center(child: Icon(icon, color: color, size: 15)),
        ),
      ),
    );
  }
}

class _LiveMemberStatusRow extends StatelessWidget {
  const _LiveMemberStatusRow({
    required this.participant,
    required this.participantName,
    required this.micMutedForDisplay,
    required this.moderationControls,
    this.onToggleMic,
    this.onToggleHeadphones,
    this.voiceVolume,
    this.onVoiceVolumeChanged,
    this.onVoiceVolumeToggle,
    this.onRemoveMember,
  });

  final LiveParticipant participant;
  final String participantName;
  final bool micMutedForDisplay;
  final bool moderationControls;
  final VoidCallback? onToggleMic;
  final VoidCallback? onToggleHeadphones;
  final double? voiceVolume;
  final ValueChanged<double>? onVoiceVolumeChanged;
  final VoidCallback? onVoiceVolumeToggle;
  final VoidCallback? onRemoveMember;

  @override
  Widget build(BuildContext context) {
    final listening =
        participant.headphonesListening &&
        !participant.headphonesMuted &&
        !participant.headphonesBlocked;
    final micModerated = participant.micBlocked || participant.voiceBlocked;
    final headphonesModerated = participant.headphonesBlocked;
    final voiceVolume = this.voiceVolume;
    final onVoiceVolumeChanged = this.onVoiceVolumeChanged;
    final buttons = <Widget>[
      _LiveMemberStatusButton(
        key: ValueKey<String>('live-member-status:mic:${participant.user.id}'),
        icon: micMutedForDisplay ? Icons.mic_off : Icons.mic,
        active: !micMutedForDisplay,
        danger: micModerated,
        onPressed: onToggleMic,
        tooltip: _liveMemberMicTooltip(
          micMutedForDisplay: micMutedForDisplay,
          micModerated: micModerated,
          headphonesModerated: headphonesModerated,
          moderationControls: moderationControls,
        ),
      ),
      _LiveMemberStatusButton(
        key: ValueKey<String>(
          'live-member-status:headphones:${participant.user.id}',
        ),
        icon: listening ? Icons.headphones : Icons.headset_off,
        active: listening,
        danger: headphonesModerated,
        onPressed: onToggleHeadphones,
        tooltip: _liveMemberHeadphonesTooltip(
          listening: listening,
          headphonesModerated: headphonesModerated,
          moderationControls: moderationControls,
        ),
      ),
      if (voiceVolume != null && onVoiceVolumeChanged != null)
        _HoverVolumeButton(
          key: ValueKey<String>(
            'live-member-status:voice-volume:${participant.user.id}',
          ),
          value: voiceVolume,
          semanticLabel: '$participantName语音音量',
          infoMessage: _memberVoiceVolumeToggleLabel(
            participantName: participantName,
            voiceVolume: voiceVolume,
          ),
          onChanged: onVoiceVolumeChanged,
          maxValue: maxParticipantVoiceVolume,
          valueFormatter: participantVoiceVolumePercentText,
          panelWidth: _memberStatusButtonDimension,
          panelHeight: _memberVoiceVolumePanelHeight(
            _memberStatusButtonDimension,
          ),
          child: _LiveMemberStatusButton(
            icon: _memberVoiceVolumeIcon(voiceVolume),
            active: voiceVolume > 0,
            tooltip: _memberVoiceVolumeToggleLabel(
              participantName: participantName,
              voiceVolume: voiceVolume,
            ),
            onPressed: onVoiceVolumeToggle,
            showHoverInfo: false,
          ),
        ),
      if (onRemoveMember != null)
        _LiveMemberStatusButton(
          key: ValueKey<String>(
            'live-member-status:kick:${participant.user.id}',
          ),
          icon: Icons.exit_to_app,
          active: false,
          danger: true,
          tooltip: '踢出语音频道',
          onPressed: onRemoveMember,
        ),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final dimension = constraints.maxWidth / 4;
        final rowWidth = dimension * buttons.length;
        return Center(
          child: SizedBox(
            width: rowWidth,
            height: dimension,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                for (final button in buttons)
                  SizedBox.square(dimension: dimension, child: button),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _LiveMemberStatusButton extends StatelessWidget {
  const _LiveMemberStatusButton({
    super.key,
    required this.icon,
    required this.active,
    required this.tooltip,
    this.onPressed,
    this.danger = false,
    this.showHoverInfo = true,
  });

  final IconData icon;
  final bool active;
  final bool danger;
  final String tooltip;
  final VoidCallback? onPressed;
  final bool showHoverInfo;

  @override
  Widget build(BuildContext context) {
    final foreground = danger
        ? UiColors.danger
        : active
        ? UiColors.accent
        : UiColors.textMuted;
    final enabled = onPressed != null;
    final button = MouseRegion(
      cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onPressed,
        child: Semantics(
          button: true,
          enabled: enabled,
          selected: active,
          label: tooltip,
          child: Center(child: Icon(icon, color: foreground, size: 13)),
        ),
      ),
    );
    if (!showHoverInfo) return button;
    return _HoverInfo(message: tooltip, child: button);
  }
}

IconData _memberVoiceVolumeIcon(double volume) {
  return _volumeLevelIcon(normalizedParticipantVoiceVolume(volume));
}

String _memberVoiceVolumeToggleLabel({
  required String participantName,
  required double voiceVolume,
}) {
  return voiceVolume <= 0 ? '取消静音$participantName' : '静音$participantName';
}

const _memberStatusButtonDimension = (_memberCardWidth - 24.0) / 4;

double _memberVoiceVolumePanelHeight(double buttonDimension) {
  return _hoverVolumePanelHeight * buttonDimension / _controlButtonSize;
}

String _liveMemberMicTooltip({
  required bool micMutedForDisplay,
  required bool micModerated,
  required bool headphonesModerated,
  required bool moderationControls,
}) {
  if (moderationControls) {
    return micModerated ? '取消麦克风静音' : '麦克风静音';
  }
  if (micModerated) return '已被麦克风静音';
  return micMutedForDisplay ? '麦克风关闭' : '麦克风开启';
}

String _liveMemberHeadphonesTooltip({
  required bool listening,
  required bool headphonesModerated,
  required bool moderationControls,
}) {
  if (moderationControls) {
    return headphonesModerated ? '取消耳机静音' : '耳机静音';
  }
  if (headphonesModerated) return '已被耳机静音';
  return listening ? '正在收听' : '已关闭收听';
}

Color _liveMemberNameColor(UserSummary user, {required bool local}) {
  if (local) return UiColors.accent;
  return roleBadgeForegroundColorForLabel(room_display.roomRoleLabel(user));
}

Color _participantMetaColor(
  LiveParticipant participant, {
  required bool speaking,
}) {
  if (participant.screenSharing || participant.cameraOn || speaking) {
    return UiColors.accent;
  }
  if (participant.micMuted) return UiColors.textMuted;
  return UiColors.textSecondary;
}

IconData? _participantMetaIcon(
  LiveParticipant participant, {
  required bool speaking,
}) {
  if (participant.screenSharing) return Icons.screen_share_outlined;
  if (participant.cameraOn) return Icons.videocam;
  if (speaking) return Icons.mic;
  return null;
}

class _LiveMemberVideo extends StatefulWidget {
  const _LiveMemberVideo({
    required this.track,
    required this.mirrored,
    this.androidLocalCameraTap,
  });

  final LiveVideoTrack track;
  final bool mirrored;
  final VoidCallback? androidLocalCameraTap;

  @override
  State<_LiveMemberVideo> createState() => _LiveMemberVideoState();
}

class _LiveMemberVideoState extends State<_LiveMemberVideo> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final track = widget.track;
    final video = _LiveMediaVideo(track: track, mirrored: widget.mirrored);
    final content = track.isScreenShare
        ? video
        : ClipRRect(
            borderRadius: BorderRadius.circular(UiRadii.lg),
            child: video,
          );
    return MouseRegion(
      key: ValueKey<String>(
        'live-member:video-thumbnail:${track.identity}:${track.isScreenShare}',
      ),
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: Stack(
        fit: StackFit.expand,
        children: [
          content,
          IgnorePointer(
            child: AnimatedOpacity(
              key: ValueKey<String>(
                'live-member:video-hover:${track.identity}:${track.isScreenShare}',
              ),
              opacity: _hovered ? 1 : 0,
              duration: const Duration(milliseconds: 120),
              child: ColoredBox(
                color: Colors.black.withValues(alpha: 0.32),
                child: Center(
                  child: Icon(
                    Icons.search,
                    color: Colors.white.withValues(alpha: 0.92),
                    size: 32,
                  ),
                ),
              ),
            ),
          ),
          if (widget.androidLocalCameraTap != null)
            Positioned.fill(
              child: Semantics(
                button: true,
                label: '预览自己的摄像头',
                child: GestureDetector(
                  key: const ValueKey<String>(
                    'live-member:android-local-camera-tap',
                  ),
                  behavior: HitTestBehavior.opaque,
                  onTap: widget.androidLocalCameraTap,
                  child: const SizedBox.expand(),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

enum _StoppedLiveMediaKind { camera, screenShare }

class _StoppedLiveMediaThumbnail extends StatefulWidget {
  const _StoppedLiveMediaThumbnail({required this.kind});

  final _StoppedLiveMediaKind kind;

  @override
  State<_StoppedLiveMediaThumbnail> createState() =>
      _StoppedLiveMediaThumbnailState();
}

class _StoppedLiveMediaThumbnailState
    extends State<_StoppedLiveMediaThumbnail> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final isCamera = widget.kind == _StoppedLiveMediaKind.camera;
    return MouseRegion(
      key: ValueKey<String>(
        isCamera
            ? 'live-member:camera-thumbnail'
            : 'live-member:screen-share-thumbnail',
      ),
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: Stack(
        fit: StackFit.expand,
        children: [
          DecoratedBox(
            decoration: const BoxDecoration(color: UiColors.surfacePressed),
            child: Center(
              child: Icon(
                isCamera
                    ? Icons.videocam_outlined
                    : Icons.screen_share_outlined,
                color: UiColors.textMuted,
                size: 30,
              ),
            ),
          ),
          ColoredBox(color: Colors.black.withValues(alpha: 0.32)),
          AnimatedOpacity(
            opacity: _hovered ? 1 : 0,
            duration: const Duration(milliseconds: 120),
            child: Center(
              child: Icon(
                Icons.search,
                color: Colors.white.withValues(alpha: 0.92),
                size: 32,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _participantMeta(LiveParticipant participant, {required bool speaking}) {
  if (participant.screenSharing) return '正在共享屏幕';
  if (participant.cameraOn) return '摄像头已开启';
  if (participant.micMuted) return '已静音';
  if (speaking) return '正在说话';
  return '正在收听';
}
