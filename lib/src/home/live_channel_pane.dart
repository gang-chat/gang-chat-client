import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show KeyDownEvent, LogicalKeyboardKey;

import '../app/audio_levels.dart';
import '../app/live_display.dart' as live_display;
import '../app/music_box_display.dart' as music_box_display;
import '../app/room_display.dart' as room_display;
import '../live/live_session.dart';
import '../live/live_video_track_view.dart';
import '../protocol/models.dart';
import '../ui/ui.dart';
import 'room_profile_card.dart';

part 'live_channel_members.dart';
part 'live_channel_media.dart';
part 'live_channel_controls.dart';
part 'live_channel_music_box.dart';

const _paneEdgeInset = 14.0;
const _paneTopInset = _paneEdgeInset;
const _liveRoomRadius = UiRadii.md;
const _liveRoomPadding = 18.0;
// Width of the right-docked music box panel (search + queue) when expanded.
const _musicBoxPanelWidth = 270.0;
const _memberCardWidth = 154.0;
const _memberCardMinTwoColumnWidth = 135.0;
const _memberCardSpacing = 12.0;
const _controlButtonSize = 44.0;
const _controlHoverInfoBelowReserve = 4.0;
const _controlHoverInfoVerticalOffset = 24.0;
const _liveStageControlGap = 16.0;
// The open music box always has its 44px inline player above one 44px
// transport row in the standard layout. Narrow layouts may add more rows, but
// those rows must move the docked panel upward instead of shortening it.
const _musicBoxStandardControlHeight = _controlButtonSize * 2 + 10.0;
const _liveRoomBackground = UiColors.surfaceLow;
const _liveRoomBorder = UiColors.border;
const _memberSpeakingBackground = Color(0xFF252A34);
const _memberIdleBackground = Color(0xFF1D2328);

enum LiveStageSelectionMode { none, track }

class LiveStageSelection {
  const LiveStageSelection.track({
    required String this.identity,
    required bool this.isScreenShare,
  }) : mode = LiveStageSelectionMode.track;

  const LiveStageSelection.none()
    : mode = LiveStageSelectionMode.none,
      identity = null,
      isScreenShare = null;

  factory LiveStageSelection.fromTrack(LiveVideoTrack track) {
    return LiveStageSelection.track(
      identity: track.identity,
      isScreenShare: track.isScreenShare,
    );
  }

  final LiveStageSelectionMode mode;
  final String? identity;
  final bool? isScreenShare;
}

class LiveChannelPane extends StatefulWidget {
  const LiveChannelPane({
    super.key,
    required this.title,
    required this.avatarUrl,
    required this.live,
    required this.currentUser,
    required this.loading,
    required this.joined,
    required this.joining,
    required this.micMuted,
    required this.headphonesMuted,
    required this.voiceBlocked,
    required this.cameraOn,
    required this.screenSharing,
    required this.speakingUserIds,
    required this.connectedParticipantIds,
    required this.liveKitMicMutedByParticipantId,
    required this.videoTracks,
    required this.stageSelection,
    this.suspendStageVideo = false,
    required this.onStageSelectionChanged,
    required this.onEnterFullScreen,
    required this.onBackToChat,
    required this.onJoin,
    required this.onLeave,
    required this.onToggleMic,
    required this.onToggleHeadphones,
    required this.onToggleCamera,
    required this.onFlipCamera,
    required this.onSetLocalCameraMirrored,
    required this.onToggleShare,
    required this.musicBox,
    required this.musicBoxOpen,
    required this.musicBoxSearchController,
    required this.musicBoxSearchResults,
    required this.musicBoxSearching,
    required this.musicBoxSearchError,
    required this.musicBoxSource,
    required this.onToggleMusicBox,
    required this.onMusicBoxTogglePlayback,
    required this.onMusicBoxSkip,
    required this.onMusicBoxQueueResult,
    required this.onMusicBoxRemoveItem,
    required this.onMusicBoxSourceChanged,
    required this.inputVolume,
    required this.outputVolume,
    required this.musicBoxVolume,
    required this.screenShareVolume,
    required this.participantVoiceVolume,
    required this.onInputVolumeChanged,
    required this.onOutputVolumeChanged,
    required this.onMusicBoxVolumeChanged,
    required this.onScreenShareVolumeChanged,
    required this.onScreenShareMuteToggled,
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

  final String title;
  final String? avatarUrl;
  final LiveState? live;
  final CurrentUser currentUser;
  final bool loading;
  final bool joined;
  final bool joining;
  final bool micMuted;
  final bool headphonesMuted;
  final bool voiceBlocked;
  final bool cameraOn;
  final bool screenSharing;
  final Set<String> speakingUserIds;
  final Set<String> connectedParticipantIds;
  final Map<String, bool> liveKitMicMutedByParticipantId;
  final List<LiveVideoTrack> videoTracks;
  final LiveStageSelection? stageSelection;

  /// Keeps the stage chrome mounted while releasing its native video texture.
  ///
  /// Windows uses this while the same track is rendered by the full-screen
  /// overlay. Keeping both renderers alive doubles the per-frame ARGB copy and
  /// leaves two native texture consumers attached across display transitions.
  final bool suspendStageVideo;
  final ValueChanged<LiveStageSelection?> onStageSelectionChanged;
  final ValueChanged<LiveVideoTrack> onEnterFullScreen;
  final VoidCallback onBackToChat;
  final VoidCallback onJoin;
  final VoidCallback onLeave;
  final VoidCallback? onToggleMic;
  final VoidCallback onToggleHeadphones;
  final VoidCallback onToggleCamera;
  final Future<bool> Function()? onFlipCamera;
  final Future<bool> Function(bool mirrored) onSetLocalCameraMirrored;
  final VoidCallback? onToggleShare;
  final MusicBoxState? musicBox;
  final bool musicBoxOpen;
  final TextEditingController musicBoxSearchController;
  final List<MusicBoxSearchResult> musicBoxSearchResults;
  final bool musicBoxSearching;
  final String? musicBoxSearchError;
  final String musicBoxSource;
  final VoidCallback onToggleMusicBox;
  final VoidCallback onMusicBoxTogglePlayback;
  final VoidCallback onMusicBoxSkip;
  final ValueChanged<MusicBoxSearchResult> onMusicBoxQueueResult;
  final ValueChanged<MusicBoxQueueItem> onMusicBoxRemoveItem;
  final ValueChanged<String> onMusicBoxSourceChanged;

  /// Local microphone input preference (0-1); 0 mutes the sent mic without
  /// writing system input gain.
  final double inputVolume;

  /// Local voice listening volume (0-1), applied to ordinary remote speakers.
  final double outputVolume;

  /// Local listening volume for the music box bot (0–1), restored from the
  /// store and persisted by [onMusicBoxVolumeChanged].
  final double musicBoxVolume;

  /// Local listening volume for remote screen-share audio (0-1), separate from
  /// ordinary voice output.
  final double screenShareVolume;

  /// Per-user relative voice volume (0-2) for ordinary remote speaker audio.
  /// Screen-share audio remains controlled by [screenShareVolume].
  final double Function(String userId) participantVoiceVolume;

  final ValueChanged<double> onInputVolumeChanged;
  final ValueChanged<double> onOutputVolumeChanged;
  final ValueChanged<double> onMusicBoxVolumeChanged;
  final ValueChanged<double> onScreenShareVolumeChanged;
  final VoidCallback onScreenShareMuteToggled;
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
  State<LiveChannelPane> createState() => _LiveChannelPaneState();
}

class _LiveChannelPaneState extends State<LiveChannelPane> {
  LiveVideoTrack? _resolveStageTrack() {
    return _resolveLiveStageTrack(
      tracks: widget.videoTracks,
      selection: widget.stageSelection,
    );
  }

  void _selectStage(LiveVideoTrack track) {
    _selectStageAndJoinIfNeeded(LiveStageSelection.fromTrack(track));
  }

  void _selectScreenShareStage(String identity) {
    _selectStageAndJoinIfNeeded(
      LiveStageSelection.track(identity: identity, isScreenShare: true),
    );
  }

  void _selectCameraStage(String identity) {
    _selectStageAndJoinIfNeeded(
      LiveStageSelection.track(identity: identity, isScreenShare: false),
    );
  }

  void _selectStageAndJoinIfNeeded(LiveStageSelection selection) {
    widget.onStageSelectionChanged(selection);
    if (!widget.joined && !widget.joining && !widget.loading) {
      widget.onJoin();
    }
  }

  void _exitStage() {
    widget.onStageSelectionChanged(const LiveStageSelection.none());
  }

  @override
  Widget build(BuildContext context) {
    final participants = live_display
        .visibleLiveParticipantsForStage(
          widget.live?.participants ?? const <LiveParticipant>[],
          currentUserId: widget.currentUser.id,
          localParticipantReady: widget.joined && !widget.joining,
          connectedParticipantIds: widget.connectedParticipantIds,
        )
        .where((p) => p.user.id != musicBoxBotIdentity)
        .toList();
    final stageTrack = _resolveStageTrack();
    final musicBox = widget.musicBox;
    final musicBoxEnabled = musicBox?.enabled ?? false;
    final musicBoxOpen =
        widget.joined && musicBoxEnabled && widget.musicBoxOpen;
    return ColoredBox(
      color: UiColors.background,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          _paneEdgeInset,
          _paneTopInset,
          _paneEdgeInset,
          _paneEdgeInset,
        ),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: _liveRoomBackground,
            borderRadius: BorderRadius.circular(_liveRoomRadius),
            border: Border.all(color: _liveRoomBorder),
            boxShadow: const [
              BoxShadow(
                color: Color(0x66080A0D),
                offset: Offset(0, 10),
                blurRadius: 22,
              ),
            ],
          ),
          child: Padding(
            padding: const EdgeInsets.all(_liveRoomPadding),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _LiveRoomHeader(title: widget.title),
                const SizedBox(height: 16),
                Expanded(
                  child: _LiveChannelBodyLayout(
                    stage: Column(
                      children: [
                        if (stageTrack != null) ...[
                          Expanded(
                            flex: 3,
                            child: _LiveMediaStage(
                              track: stageTrack,
                              renderVideo: !widget.suspendStageVideo,
                              label: liveStageTrackLabel(
                                widget.live,
                                stageTrack,
                              ),
                              screenShareViewers: stageTrack.isScreenShare
                                  ? live_display.liveScreenShareViewers(
                                      widget.live,
                                      stageTrack.identity,
                                    )
                                  : const <UserSummary>[],
                              screenShareVolume: widget.screenShareVolume,
                              cameraMirrored:
                                  live_display
                                      .liveParticipantByUserId(
                                        widget.live,
                                        stageTrack.identity,
                                      )
                                      ?.cameraMirrored ??
                                  false,
                              onFlipCamera: stageTrack.isLocal
                                  ? widget.onFlipCamera
                                  : null,
                              onSetLocalCameraMirrored:
                                  widget.onSetLocalCameraMirrored,
                              onExit: _exitStage,
                              onFullScreen: () =>
                                  widget.onEnterFullScreen(stageTrack),
                              onScreenShareVolumeChanged:
                                  widget.onScreenShareVolumeChanged,
                              onScreenShareMuteToggled:
                                  widget.onScreenShareMuteToggled,
                            ),
                          ),
                          const SizedBox(height: 14),
                        ],
                        Expanded(
                          flex: stageTrack == null ? 1 : 2,
                          child: _LiveMemberStage(
                            participants: participants,
                            currentUser: widget.currentUser,
                            localMicMuted: widget.micMuted,
                            localHeadphonesMuted: widget.headphonesMuted,
                            speakingUserIds: widget.speakingUserIds,
                            liveKitMicMutedByParticipantId:
                                widget.liveKitMicMutedByParticipantId,
                            videoTracks: widget.videoTracks,
                            stageTrack: stageTrack,
                            onSelectStage: _selectStage,
                            onSelectScreenShareStage: _selectScreenShareStage,
                            onSelectCameraStage: _selectCameraStage,
                            onToggleMic: widget.onToggleMic,
                            onToggleHeadphones: widget.onToggleHeadphones,
                            participantVoiceVolume:
                                widget.participantVoiceVolume,
                            onParticipantVoiceVolumeChanged:
                                widget.onParticipantVoiceVolumeChanged,
                            onParticipantVoiceMuteToggled:
                                widget.onParticipantVoiceMuteToggled,
                            canModerateParticipant:
                                widget.canModerateParticipant,
                            onToggleParticipantMicModeration:
                                widget.onToggleParticipantMicModeration,
                            onToggleParticipantHeadphonesModeration:
                                widget.onToggleParticipantHeadphonesModeration,
                            canRemoveParticipant: widget.canRemoveParticipant,
                            onRemoveParticipant: widget.onRemoveParticipant,
                            onResolveParticipantProfile:
                                widget.onResolveParticipantProfile,
                            onResolveParticipantRoomProfile:
                                widget.onResolveParticipantRoomProfile,
                            onEnterParticipantProfileRoom:
                                widget.onEnterParticipantProfileRoom,
                            participantProfileActionBuilder:
                                widget.participantProfileActionBuilder,
                          ),
                        ),
                      ],
                    ),
                    controls: _LiveControlBar(
                      joined: widget.joined,
                      joining: widget.joining || widget.loading,
                      micMuted: widget.micMuted,
                      headphonesMuted: widget.headphonesMuted,
                      voiceBlocked: widget.voiceBlocked,
                      cameraOn: widget.cameraOn,
                      screenSharing: widget.screenSharing,
                      inputVolume: widget.inputVolume,
                      outputVolume: widget.outputVolume,
                      musicBox: musicBox,
                      musicBoxEnabled: musicBoxEnabled,
                      musicBoxOpen: musicBoxOpen,
                      onJoin: widget.onJoin,
                      onLeave: widget.onLeave,
                      onToggleMic: widget.onToggleMic,
                      onToggleHeadphones: widget.onToggleHeadphones,
                      onToggleCamera: widget.onToggleCamera,
                      onToggleShare: widget.onToggleShare,
                      onInputVolumeChanged: widget.onInputVolumeChanged,
                      onOutputVolumeChanged: widget.onOutputVolumeChanged,
                      onToggleMusicBox: widget.onToggleMusicBox,
                      onMusicBoxTogglePlayback: widget.onMusicBoxTogglePlayback,
                      onMusicBoxSkip: widget.onMusicBoxSkip,
                      onCollapse: widget.onBackToChat,
                    ),
                    musicBox: musicBoxOpen && musicBox != null
                        ? LiveMusicBoxPanel(
                            key: const ValueKey<String>('live-music-box-panel'),
                            state: musicBox,
                            searchController: widget.musicBoxSearchController,
                            searchResults: widget.musicBoxSearchResults,
                            searching: widget.musicBoxSearching,
                            searchError: widget.musicBoxSearchError,
                            source: widget.musicBoxSource,
                            onTogglePlayback: widget.onMusicBoxTogglePlayback,
                            onSkip: widget.onMusicBoxSkip,
                            onQueueResult: widget.onMusicBoxQueueResult,
                            onRemoveItem: widget.onMusicBoxRemoveItem,
                            onSourceChanged: widget.onMusicBoxSourceChanged,
                            onClose: widget.onToggleMusicBox,
                            volume: widget.musicBoxVolume,
                            onVolumeChanged: widget.onMusicBoxVolumeChanged,
                          )
                        : null,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

enum _LiveChannelBodySlot { stage, controls, musicBoxAnchor }

/// Lays out the live stage and its controls in one pass so the docked music box
/// can keep a stable height. Extra compact control rows reduce the stage and
/// move the panel upward; they never compress the panel itself.
class _LiveChannelBodyLayout extends StatefulWidget {
  const _LiveChannelBodyLayout({
    required this.stage,
    required this.controls,
    required this.musicBox,
  });

  final Widget stage;
  final Widget controls;
  final Widget? musicBox;

  @override
  State<_LiveChannelBodyLayout> createState() => _LiveChannelBodyLayoutState();
}

class _LiveChannelBodyLayoutState extends State<_LiveChannelBodyLayout> {
  final GlobalKey _bodyKey = GlobalKey();
  final GlobalKey _musicBoxAnchorKey = GlobalKey();
  final OverlayPortalController _musicBoxPortal = OverlayPortalController();

  @override
  void didUpdateWidget(_LiveChannelBodyLayout oldWidget) {
    super.didUpdateWidget(oldWidget);
    _schedulePortalSync();
  }

  @override
  void dispose() {
    if (_musicBoxPortal.isShowing) _musicBoxPortal.hide();
    super.dispose();
  }

  void _schedulePortalSync() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (widget.musicBox != null) {
        if (!_musicBoxPortal.isShowing) _musicBoxPortal.show();
      } else if (_musicBoxPortal.isShowing) {
        _musicBoxPortal.hide();
      }
    });
  }

  Rect? _rectInOverlay(GlobalKey key) {
    final overlay = Overlay.maybeOf(context);
    final anchorBox = key.currentContext?.findRenderObject();
    final overlayBox = overlay?.context.findRenderObject();
    if (anchorBox is! RenderBox ||
        overlayBox is! RenderBox ||
        !anchorBox.hasSize ||
        !overlayBox.hasSize) {
      return null;
    }
    return anchorBox.localToGlobal(Offset.zero, ancestor: overlayBox) &
        anchorBox.size;
  }

  @override
  Widget build(BuildContext context) {
    _schedulePortalSync();
    return OverlayPortal(
      controller: _musicBoxPortal,
      overlayChildBuilder: (context) {
        return Positioned.fill(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final bodyRect = _rectInOverlay(_bodyKey);
              final anchorRect = _rectInOverlay(_musicBoxAnchorKey);
              if (bodyRect == null ||
                  anchorRect == null ||
                  widget.musicBox == null) {
                return const SizedBox.shrink();
              }
              final standardPanelHeight =
                  bodyRect.height -
                  _musicBoxStandardControlHeight -
                  _liveStageControlGap -
                  _controlHoverInfoBelowReserve;
              final desiredPanelHeight =
                  standardPanelHeight < _musicBoxMinComfortableHeight
                  ? _musicBoxMinComfortableHeight
                  : standardPanelHeight;
              final panelHeight = desiredPanelHeight > anchorRect.top
                  ? anchorRect.top
                  : desiredPanelHeight;
              final panelWidth = bodyRect.width < _musicBoxPanelWidth
                  ? bodyRect.width
                  : _musicBoxPanelWidth;
              if (panelHeight <= 0 || panelWidth <= 0) {
                return const SizedBox.shrink();
              }
              return Stack(
                children: [
                  Positioned(
                    left: anchorRect.right - panelWidth,
                    top: anchorRect.top - panelHeight,
                    width: panelWidth,
                    height: panelHeight,
                    child: widget.musicBox!,
                  ),
                ],
              );
            },
          ),
        );
      },
      child: KeyedSubtree(
        key: _bodyKey,
        child: CustomMultiChildLayout(
          delegate: _LiveChannelBodyLayoutDelegate(),
          children: [
            LayoutId(id: _LiveChannelBodySlot.stage, child: widget.stage),
            LayoutId(id: _LiveChannelBodySlot.controls, child: widget.controls),
            LayoutId(
              id: _LiveChannelBodySlot.musicBoxAnchor,
              child: SizedBox(key: _musicBoxAnchorKey),
            ),
          ],
        ),
      ),
    );
  }
}

class _LiveChannelBodyLayoutDelegate extends MultiChildLayoutDelegate {
  _LiveChannelBodyLayoutDelegate();

  @override
  void performLayout(Size size) {
    final controlSize = layoutChild(
      _LiveChannelBodySlot.controls,
      BoxConstraints(minWidth: size.width, maxWidth: size.width),
    );
    final stageHeight =
        (size.height -
                controlSize.height -
                _liveStageControlGap -
                _controlHoverInfoBelowReserve)
            .clamp(0.0, size.height)
            .toDouble();
    layoutChild(
      _LiveChannelBodySlot.stage,
      BoxConstraints.tightFor(width: size.width, height: stageHeight),
    );
    positionChild(_LiveChannelBodySlot.stage, Offset.zero);
    positionChild(
      _LiveChannelBodySlot.controls,
      Offset(0, stageHeight + _liveStageControlGap),
    );
    layoutChild(
      _LiveChannelBodySlot.musicBoxAnchor,
      BoxConstraints.tight(Size.zero),
    );
    positionChild(
      _LiveChannelBodySlot.musicBoxAnchor,
      Offset(size.width, stageHeight),
    );
  }

  @override
  bool shouldRelayout(_LiveChannelBodyLayoutDelegate oldDelegate) {
    return false;
  }
}
