import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../app/account_display.dart' as account_display;
import '../app/room_display.dart' as room_display;
import '../protocol/models.dart';
import '../ui/ui.dart';
import 'hover_card_anchor.dart';
import 'room_profile_card.dart';

/// Presentation data for a playlist hover card.
///
/// The music-box snapshot intentionally does not invent metadata that the
/// server does not provide. For example, the temporary queue has no creation
/// timestamp, so [createdAt] remains null and the card renders it as unknown.
class MusicPlaylistCardData {
  const MusicPlaylistCardData({
    required this.id,
    required this.name,
    required this.songCount,
    required this.createdAt,
    this.creator,
    this.room,
    this.showPlayingStatus = false,
  });

  final String id;
  final String name;
  final int songCount;
  final DateTime? createdAt;
  final UserSummary? creator;
  final PublicRoom? room;

  /// True only when the card was opened from the authoritative current-source
  /// header. A playlist being listed elsewhere does not gain this badge.
  final bool showPlayingStatus;

  MusicPlaylistCardData copyWith({
    String? name,
    int? songCount,
    DateTime? createdAt,
  }) {
    return MusicPlaylistCardData(
      id: id,
      name: name ?? this.name,
      songCount: songCount ?? this.songCount,
      createdAt: createdAt ?? this.createdAt,
      creator: creator,
      room: room,
      showPlayingStatus: showPlayingStatus,
    );
  }
}

typedef MusicPlaylistCardResolver =
    Future<MusicPlaylistCardData> Function(MusicPlaylistCardData current);

typedef MusicPlaylistViewCallback =
    Future<void> Function(PersonalMusicPlaylist playlist, bool roomScoped);

/// Supplies playlist identity and navigation actions to cards opened several
/// layers below the music-box body (notably the add-to-playlist picker inside a
/// song card) without duplicating those parameters through every song widget.
class MusicPlaylistCardHostScope extends InheritedWidget {
  const MusicPlaylistCardHostScope({
    super.key,
    required this.currentUser,
    required this.room,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
    required this.onStateChanged,
    required this.onViewPlaylist,
    required super.child,
  });

  final CurrentUser? currentUser;
  final PublicRoom? room;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final MusicPlaylistViewCallback onViewPlaylist;

  static MusicPlaylistCardHostScope? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<MusicPlaylistCardHostScope>();
  }

  @override
  bool updateShouldNotify(MusicPlaylistCardHostScope oldWidget) {
    return currentUser?.id != oldWidget.currentUser?.id ||
        room?.id != oldWidget.room?.id ||
        onResolveUserProfile != oldWidget.onResolveUserProfile ||
        onResolveRoomProfile != oldWidget.onResolveRoomProfile ||
        onEnterCommonRoom != oldWidget.onEnterCommonRoom ||
        userProfileActionBuilder != oldWidget.userProfileActionBuilder ||
        onStateChanged != oldWidget.onStateChanged ||
        onViewPlaylist != oldWidget.onViewPlaylist;
  }
}

/// Shared playlist hover/tap card used by the current-source header and saved
/// playlist rows on Windows, macOS, and Android.
class MusicPlaylistHoverCard extends StatefulWidget {
  const MusicPlaylistHoverCard({
    super.key,
    required this.data,
    required this.child,
    required this.onPlayAll,
    required this.onViewPlaylist,
    this.resolveData,
    this.currentUser,
    this.onResolveUserProfile,
    this.onResolveRoomProfile,
    this.onEnterCommonRoom,
    this.userProfileActionBuilder,
  });

  final MusicPlaylistCardData data;
  final Widget child;
  final Future<void> Function()? onPlayAll;
  final Future<void> Function()? onViewPlaylist;
  final MusicPlaylistCardResolver? resolveData;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  State<MusicPlaylistHoverCard> createState() => _MusicPlaylistHoverCardState();
}

class _MusicPlaylistHoverCardState extends State<MusicPlaylistHoverCard> {
  MusicPlaylistCardData? _resolved;
  Future<void>? _resolveFuture;
  bool _playing = false;
  bool _viewing = false;
  int _dismissEpoch = 0;

  MusicPlaylistCardData get _displayData => _resolved ?? widget.data;

  @override
  void didUpdateWidget(covariant MusicPlaylistHoverCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.data.id == widget.data.id &&
        oldWidget.data.name == widget.data.name &&
        oldWidget.data.songCount == widget.data.songCount &&
        oldWidget.data.createdAt == widget.data.createdAt &&
        oldWidget.data.creator?.id == widget.data.creator?.id &&
        oldWidget.data.room?.id == widget.data.room?.id &&
        oldWidget.data.showPlayingStatus == widget.data.showPlayingStatus) {
      return;
    }
    _resolved = null;
    _resolveFuture = null;
  }

  Future<void> _resolve() {
    final resolver = widget.resolveData;
    if (resolver == null) return Future<void>.value();
    final existing = _resolveFuture;
    if (existing != null) return existing;
    final requested = widget.data;
    final future = () async {
      try {
        final resolved = await resolver(requested);
        if (!mounted || widget.data.id != requested.id) return;
        setState(() => _resolved = resolved);
      } catch (_) {
        if (!mounted || widget.data.id != requested.id) return;
        // Keep the snapshot metadata visible when richer playlist metadata is
        // unavailable (for example another user's active private playlist).
        setState(() => _resolved = null);
      } finally {
        if (mounted && widget.data.id == requested.id) _resolveFuture = null;
      }
    }();
    _resolveFuture = future;
    return future;
  }

  Future<void> _playAll() async {
    final callback = widget.onPlayAll;
    if (callback == null || _playing) return;
    setState(() => _playing = true);
    try {
      await callback();
    } finally {
      if (mounted) setState(() => _playing = false);
    }
  }

  Future<void> _viewPlaylist() async {
    final callback = widget.onViewPlaylist;
    if (callback == null || _viewing) return;
    setState(() => _viewing = true);
    try {
      await callback();
    } finally {
      if (mounted) {
        setState(() {
          _viewing = false;
          // Viewing replaces the underlying browser or reveals the already
          // visible current queue. Resetting the anchor closes the pinned card
          // instead of leaving it over the content the user asked to see.
          _dismissEpoch += 1;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = _displayData;
    return HoverCardAnchor(
      cardWidth: 304,
      resetKey: Object.hash(
        widget.data.id,
        widget.data.showPlayingStatus,
        _dismissEpoch,
      ),
      onBeforeOpen: widget.resolveData == null ? null : _resolve,
      cardBuilder: (context) => _MusicPlaylistProfileCard(
        data: data,
        currentUser: widget.currentUser,
        onResolveUserProfile: widget.onResolveUserProfile,
        onResolveRoomProfile: widget.onResolveRoomProfile,
        onEnterCommonRoom: widget.onEnterCommonRoom,
        userProfileActionBuilder: widget.userProfileActionBuilder,
        playing: _playing,
        viewing: _viewing,
        onPlayAll: widget.onPlayAll == null ? null : _playAll,
        onViewPlaylist: widget.onViewPlaylist == null ? null : _viewPlaylist,
      ),
      child: widget.child,
    );
  }
}

class _MusicPlaylistProfileCard extends StatelessWidget {
  const _MusicPlaylistProfileCard({
    required this.data,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
    required this.playing,
    required this.viewing,
    required this.onPlayAll,
    required this.onViewPlaylist,
  });

  final MusicPlaylistCardData data;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;
  final bool playing;
  final bool viewing;
  final VoidCallback? onPlayAll;
  final VoidCallback? onViewPlaylist;

  @override
  Widget build(BuildContext context) {
    final isRoomPlaylist = data.room != null;
    return Padding(
      key: ValueKey<String>('music-playlist-card:${data.id}'),
      padding: const EdgeInsets.all(UiSpacing.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 2),
                child: Icon(
                  Icons.queue_music,
                  size: 22,
                  color: UiColors.accent,
                ),
              ),
              const SizedBox(width: UiSpacing.sm),
              Expanded(
                child: HoverCardSelectableText(
                  value: data.name,
                  maxLines: 12,
                  style: UiTypography.title.copyWith(fontSize: 16),
                ),
              ),
            ],
          ),
          if (data.showPlayingStatus) ...[
            const SizedBox(height: UiSpacing.sm),
            Align(
              alignment: Alignment.centerLeft,
              child: StatusBadge(
                label: '正在播放',
                icon: Icons.graphic_eq,
                active: true,
              ),
            ),
          ],
          const SizedBox(height: UiSpacing.md),
          _MusicPlaylistDetailRow(
            label: '歌曲数量',
            child: Text(
              '${data.songCount} 首',
              textAlign: TextAlign.right,
              style: UiTypography.body,
            ),
          ),
          const SizedBox(height: UiSpacing.sm),
          _MusicPlaylistDetailRow(
            label: isRoomPlaylist ? '房间' : '创建人',
            child: _MusicPlaylistIdentity(
              creator: data.creator,
              room: data.room,
              currentUser: currentUser,
              onResolveUserProfile: onResolveUserProfile,
              onResolveRoomProfile: onResolveRoomProfile,
              onEnterCommonRoom: onEnterCommonRoom,
              userProfileActionBuilder: userProfileActionBuilder,
            ),
          ),
          const SizedBox(height: UiSpacing.sm),
          _MusicPlaylistDetailRow(
            label: '创建日期',
            child: HoverCardSelectableText(
              value: account_display.formatDateTime(data.createdAt),
              textAlign: TextAlign.right,
              maxLines: 2,
              style: UiTypography.body,
            ),
          ),
          const SizedBox(height: UiSpacing.lg),
          ResponsiveDialogActionBar(
            expanded: true,
            actions: [
              ResponsiveDialogAction(
                buttonKey: const ValueKey<String>(
                  'music-playlist-card-play-all',
                ),
                label: '播放全部',
                icon: Icons.play_arrow,
                tone: ButtonTone.primary,
                loading: playing,
                onPressed: onPlayAll,
              ),
              ResponsiveDialogAction(
                buttonKey: const ValueKey<String>('music-playlist-card-view'),
                label: '查看歌单',
                icon: Icons.queue_music,
                loading: viewing,
                onPressed: onViewPlaylist,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MusicPlaylistDetailRow extends StatelessWidget {
  const _MusicPlaylistDetailRow({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(
          width: 58,
          child: Text(
            label,
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
        ),
        Expanded(
          child: Align(alignment: Alignment.centerRight, child: child),
        ),
      ],
    );
  }
}

class _MusicPlaylistIdentity extends StatelessWidget {
  const _MusicPlaylistIdentity({
    required this.creator,
    required this.room,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final UserSummary? creator;
  final PublicRoom? room;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  Widget build(BuildContext context) {
    final roomValue = room;
    if (roomValue != null) {
      final identity = _MusicPlaylistIdentityValue(
        label: room_display.publicRoomAvatarLabel(roomValue),
        imageUrl: AppConfigScope.of(
          context,
        ).resolveAssetUrl(roomValue.avatarUrl),
        defaultAvatarKey: roomValue.defaultAvatarKey,
        name: roomValue.name,
      );
      final user = currentUser;
      if (user == null) return identity;
      return RoomHoverCard(
        room: roomValue,
        currentUser: user,
        onResolveRoom: onResolveRoomProfile,
        onResolveUserProfile: onResolveUserProfile,
        onEnterRoom: onEnterCommonRoom,
        child: identity,
      );
    }

    final creatorValue = creator;
    if (creatorValue != null) {
      final identity = _MusicPlaylistIdentityValue(
        label: room_display.userAvatarLabel(creatorValue),
        imageUrl: AppConfigScope.of(
          context,
        ).resolveAssetUrl(creatorValue.avatarUrl),
        defaultAvatarKey: creatorValue.defaultAvatarKey,
        name: room_display.userPrimaryName(creatorValue),
      );
      return UserHoverCard(
        user: creatorValue,
        currentUser: currentUser,
        onResolveProfile: onResolveUserProfile,
        onResolveRoomProfile: onResolveRoomProfile,
        onEnterCommonRoom: onEnterCommonRoom,
        profileActionBuilder: userProfileActionBuilder,
        showRoomRole: true,
        child: identity,
      );
    }

    return Text(
      '未知',
      textAlign: TextAlign.right,
      style: UiTypography.body.copyWith(color: UiColors.textMuted),
    );
  }
}

class _MusicPlaylistIdentityValue extends StatelessWidget {
  const _MusicPlaylistIdentityValue({
    required this.label,
    required this.imageUrl,
    required this.defaultAvatarKey,
    required this.name,
  });

  final String label;
  final String? imageUrl;
  final String defaultAvatarKey;
  final String name;

  @override
  Widget build(BuildContext context) {
    const avatarSize = 24.0;
    const gap = 6.0;
    final style = UiTypography.body.copyWith(color: UiColors.text);
    return LayoutBuilder(
      builder: (context, constraints) {
        final painter = TextPainter(
          text: TextSpan(text: name, style: style),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
          maxLines: 1,
        )..layout();
        final available = constraints.hasBoundedWidth
            ? constraints.maxWidth
            : painter.width + avatarSize + gap;
        final width = math.min(available, painter.width + avatarSize + gap + 4);
        return SizedBox(
          width: width,
          child: Row(
            children: [
              Avatar(
                label: label,
                imageUrl: imageUrl,
                defaultAvatarKey: defaultAvatarKey,
                size: avatarSize,
                showBorder: false,
              ),
              const SizedBox(width: gap),
              Expanded(
                child: Text(
                  name,
                  maxLines: 3,
                  softWrap: true,
                  textAlign: TextAlign.right,
                  style: style,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
