part of 'live_channel_pane.dart';

/// Song profile card shared by every music-box row: queue items, search hits,
/// and saved-playlist tracks. Opened as a hover card on the row's text area;
/// its actions (优先播放 / 添加到歌单) are gated by server capabilities.

UserSummary? _musicBoxActiveSourceOwner(
  MusicBoxActiveSource source,
  CurrentUser? currentUser,
) {
  final owner = source.owner;
  if (owner != null) {
    return UserSummary(
      id: owner.userId,
      username: owner.username,
      displayName: owner.avatarLabel,
      roomDisplayName: owner.displayName,
      avatarUrl: owner.avatarUrl,
      defaultAvatarKey: owner.defaultAvatarKey,
    );
  }
  if (currentUser != null &&
      (source.ownerUserId.isEmpty || source.ownerUserId == currentUser.id)) {
    return currentUser.toSummary();
  }
  return null;
}

PersonalMusicPlaylist? _musicBoxPlaylistById(
  List<PersonalMusicPlaylist> playlists,
  String id,
) {
  for (final playlist in playlists) {
    if (playlist.id == id) return playlist;
  }
  return null;
}

class _MusicBoxSongCard extends StatefulWidget {
  const _MusicBoxSongCard.queue({
    required this.item,
    required this.isCurrent,
    required this.queueSongCount,
    required this.controller,
    required this.roomId,
    required this.activeSource,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  }) : result = null,
       catalogDurationMs = null,
       alreadyInRequestQueue = false,
       onQueueResult = null;

  const _MusicBoxSongCard.search({
    required this.result,
    required this.controller,
    required this.roomId,
    required this.onQueueResult,
    required this.alreadyInRequestQueue,
    this.catalogDurationMs,
  }) : item = null,
       isCurrent = false,
       queueSongCount = 0,
       activeSource = null,
       onStateChanged = null,
       currentUser = null,
       onResolveUserProfile = null,
       onResolveRoomProfile = null,
       onEnterCommonRoom = null,
       userProfileActionBuilder = null;

  final MusicBoxQueueItem? item;
  final MusicBoxSearchResult? result;
  final int? catalogDurationMs;
  final bool isCurrent;
  final int queueSongCount;
  final MusicBoxController? controller;
  final String? roomId;
  final MusicBoxActiveSource? activeSource;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final ValueChanged<MusicBoxSearchResult>? onQueueResult;
  final bool alreadyInRequestQueue;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  State<_MusicBoxSongCard> createState() => _MusicBoxSongCardState();
}

class _MusicBoxSongCardState extends State<_MusicBoxSongCard> {
  bool _playingNow = false;

  Future<void> _playNow() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    final item = widget.item;
    if (controller == null || roomId == null || item == null || _playingNow) {
      return;
    }
    setState(() => _playingNow = true);
    try {
      final state = await controller.playNow(roomId: roomId, item: item);
      widget.onStateChanged?.call(state);
      if (mounted) showFloatingSuccessNotice(context, '已优先播放');
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '优先播放失败，请重试'),
        );
      }
    } finally {
      if (mounted) setState(() => _playingNow = false);
    }
  }

  Future<void> _openPlaylistPicker() {
    return _musicBoxAddToPlaylist(
      context,
      controller: widget.controller,
      roomId: widget.roomId,
      item: widget.item,
      result: widget.result,
      durationMs: widget.catalogDurationMs,
    );
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final result = widget.result;
    final isSearchResult = result != null;
    final title = item?.title ?? result!.name;
    final artist =
        item?.artist.trim() ??
        music_box_display.musicBoxArtistsLabel(result!.artists).trim();
    final source = item?.source ?? result!.source;
    final trackId = item?.trackId ?? result!.trackId;
    final durationMs = item?.durationMs ?? widget.catalogDurationMs ?? 0;
    final isBilibili = source.trim().toLowerCase() == 'bilibili';
    final canPlayNow =
        item != null &&
        item.canPlayNow &&
        item.status == MusicBoxQueueItemStatus.ready &&
        widget.controller != null &&
        widget.roomId != null;
    return SingleChildScrollView(
      child: Padding(
        key: ValueKey<String>(
          isSearchResult
              ? 'music-box-song-card:search:${result.source}:${result.trackId}'
              : 'music-box-song-card:${item!.id}',
        ),
        padding: const EdgeInsets.all(UiSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.music_note, color: UiColors.accent, size: 28),
                const SizedBox(width: UiSpacing.sm),
                Expanded(
                  child: _MusicBoxAdaptiveFullText(
                    key: const ValueKey<String>('music-box-song-card-title'),
                    text: title,
                    style: UiTypography.title.copyWith(fontSize: 16),
                  ),
                ),
              ],
            ),
            const SizedBox(height: UiSpacing.md),
            _MusicBoxSongDetailRow(
              label: '时长',
              value: durationMs > 0
                  ? music_box_display.musicBoxFormatDuration(durationMs)
                  : '未知',
            ),
            _MusicBoxSongDetailRow(
              label: music_box_display.musicBoxArtistFieldLabel(source),
              value: artist.isEmpty
                  ? isBilibili
                        ? '未知作者'
                        : '未知艺人'
                  : artist,
            ),
            _MusicBoxSongDetailRow(
              label: '来源',
              value: music_box_display.musicBoxSourceLabel(source),
            ),
            if (!isSearchResult) ...[
              const SizedBox(height: UiSpacing.sm),
              _MusicBoxSongAttribution(
                source: widget.activeSource!,
                requester: item!.requestedBy,
                songCount: widget.queueSongCount,
                controller: widget.controller,
                roomId: widget.roomId,
                onStateChanged: widget.onStateChanged,
                currentUser: widget.currentUser,
                onResolveUserProfile: widget.onResolveUserProfile,
                onResolveRoomProfile: widget.onResolveRoomProfile,
                onEnterCommonRoom: widget.onEnterCommonRoom,
                userProfileActionBuilder: widget.userProfileActionBuilder,
              ),
            ],
            if (isBilibili) ...[
              const SizedBox(height: UiSpacing.sm),
              _MusicBoxSongDetailRow(
                label: '详情',
                value: _musicBoxBilibiliBvId(trackId),
              ),
            ],
            const SizedBox(height: UiSpacing.md),
            Row(
              children: [
                // Preset tracks pass no queue callback: they are played from
                // the music box, not requested, so the card shows no queue
                // action for them.
                if (isSearchResult &&
                    (widget.onQueueResult != null ||
                        widget.alreadyInRequestQueue)) ...[
                  Expanded(
                    child: Button(
                      icon: Icon(
                        widget.alreadyInRequestQueue
                            ? Icons.playlist_add_check
                            : Icons.playlist_add,
                      ),
                      tone: widget.alreadyInRequestQueue
                          ? ButtonTone.neutral
                          : ButtonTone.primary,
                      height: 34,
                      onPressed: widget.alreadyInRequestQueue
                          ? null
                          : () => widget.onQueueResult?.call(result),
                      child: Text(
                        widget.alreadyInRequestQueue ? '已在队列中' : '点歌队列',
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                if (canPlayNow) ...[
                  Expanded(
                    child: Button(
                      icon: const Icon(Icons.play_arrow),
                      height: 34,
                      loading: _playingNow,
                      onPressed: widget.isCurrent
                          ? null
                          : () => unawaited(_playNow()),
                      child: Text(widget.isCurrent ? '正在播放' : '优先播放'),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  child: Button(
                    icon: const Icon(Icons.playlist_add),
                    tone: ButtonTone.primary,
                    height: 34,
                    onPressed: widget.controller == null
                        ? null
                        : () => unawaited(_openPlaylistPicker()),
                    child: const Text('添加到歌单'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _MusicBoxSongAttribution extends StatelessWidget {
  const _MusicBoxSongAttribution({
    required this.source,
    required this.requester,
    required this.songCount,
    required this.controller,
    required this.roomId,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final MusicBoxActiveSource source;
  final MusicBoxRequester? requester;
  final int songCount;
  final MusicBoxController? controller;
  final String? roomId;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  MusicPlaylistCardResolver? _playlistResolver() {
    final musicController = controller;
    if (musicController == null || source.id.isEmpty) return null;
    if (source.type == MusicBoxActiveSourceType.roomPlaylist) {
      final currentRoomId = roomId;
      if (currentRoomId == null) return null;
      return (current) async {
        final page = await musicController.loadRoomPlaylists(
          roomId: currentRoomId,
        );
        final playlist = _musicBoxPlaylistById(page.playlists, source.id);
        if (playlist == null) return current;
        return current.copyWith(
          name: playlist.name,
          songCount: playlist.itemCount,
          createdAt: playlist.createdAt,
        );
      };
    }
    final ownerId = source.ownerUserId.isNotEmpty
        ? source.ownerUserId
        : source.owner?.userId ?? '';
    if (source.type != MusicBoxActiveSourceType.userPlaylist ||
        currentUser == null ||
        (ownerId.isNotEmpty && ownerId != currentUser!.id)) {
      return null;
    }
    return (current) async {
      final page = await musicController.loadMyPlaylists();
      final playlist = _musicBoxPlaylistById(page.playlists, source.id);
      if (playlist == null) return current;
      return current.copyWith(
        name: playlist.name,
        songCount: playlist.itemCount,
        createdAt: playlist.createdAt,
      );
    };
  }

  Future<void> _playPlaylist(BuildContext context) async {
    final musicController = controller;
    final currentRoomId = roomId;
    if (musicController == null || currentRoomId == null || source.id.isEmpty) {
      return;
    }
    try {
      final state = await musicController.activatePlaylist(
        roomId: currentRoomId,
        sourceType: source.type,
        playlistId: source.id,
      );
      onStateChanged?.call(state);
    } catch (error) {
      if (context.mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '播放歌单失败，请重试'),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isPlaylist = source.type != MusicBoxActiveSourceType.temporary;
    final playlistName = source.name.trim().isEmpty ? '未命名歌单' : source.name;
    final host = MusicPlaylistCardHostScope.maybeOf(context);
    final creator = isPlaylist
        ? _musicBoxActiveSourceOwner(source, currentUser)
        : null;
    final ownerId = source.ownerUserId.isNotEmpty
        ? source.ownerUserId
        : source.owner?.userId ?? '';
    final viewer = currentUser;
    final isOwnedPersonalPlaylist =
        source.type == MusicBoxActiveSourceType.userPlaylist &&
        viewer != null &&
        (ownerId.isEmpty || ownerId == viewer.id);
    final editPlaylist = source.type == MusicBoxActiveSourceType.roomPlaylist
        ? host?.onEditRoomPlaylist
        : isOwnedPersonalPlaylist
        ? host?.onEditPersonalPlaylist
        : null;
    final playlistValue = _MusicBoxPlaylistAttributionValue(name: playlistName);
    final value = isPlaylist && source.id.isNotEmpty
        ? MusicPlaylistHoverCard(
            data: MusicPlaylistCardData(
              id: source.id,
              name: playlistName,
              songCount: songCount,
              createdAt: source.createdAt,
              creator: source.type == MusicBoxActiveSourceType.userPlaylist
                  ? creator
                  : null,
              room: source.type == MusicBoxActiveSourceType.roomPlaylist
                  ? host?.room
                  : null,
            ),
            resolveData: _playlistResolver(),
            currentUser: currentUser,
            onResolveUserProfile: onResolveUserProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onEnterCommonRoom,
            userProfileActionBuilder: userProfileActionBuilder,
            onPlayAll: controller == null || roomId == null
                ? null
                : () => _playPlaylist(context),
            onViewPlaylist: editPlaylist != null
                ? () => editPlaylist(source.id)
                : host == null
                ? null
                : () => host.onViewPlaylist(
                    PersonalMusicPlaylist(
                      id: source.id,
                      name: playlistName,
                      description: '',
                      revision: 0,
                      itemCount: songCount,
                      createdAt: source.createdAt,
                      updatedAt: null,
                    ),
                    source.type == MusicBoxActiveSourceType.roomPlaylist,
                  ),
            secondaryActionLabel: editPlaylist == null ? '查看歌单' : '编辑歌单',
            secondaryActionIcon: editPlaylist == null
                ? Icons.queue_music
                : Icons.edit_outlined,
            child: playlistValue,
          )
        : isPlaylist
        ? playlistValue
        : _MusicBoxSongAttributionValue(
            value: requester?.displayName ?? '未知用户',
            person: requester,
            showAvatar: requester != null,
            currentUser: currentUser,
            onResolveUserProfile: onResolveUserProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onEnterCommonRoom,
            userProfileActionBuilder: userProfileActionBuilder,
          );

    return Row(
      children: [
        SizedBox(
          width: 38,
          child: HoverCardSelectableText(
            value: isPlaylist ? '歌单' : '点歌人',
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
        ),
        Expanded(child: value),
      ],
    );
  }
}

class _MusicBoxPlaylistAttributionValue extends StatelessWidget {
  const _MusicBoxPlaylistAttributionValue({required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    return Row(
      key: const ValueKey<String>('music-box-song-playlist-attribution'),
      mainAxisAlignment: MainAxisAlignment.end,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        const Icon(
          Icons.queue_music,
          key: ValueKey<String>('music-box-song-playlist-icon'),
          size: 17,
          color: UiColors.accent,
        ),
        const SizedBox(width: 5),
        Flexible(
          child: Text(
            name,
            key: const ValueKey<String>('music-box-song-attribution-name'),
            textAlign: TextAlign.right,
            softWrap: true,
            style: UiTypography.label.copyWith(color: UiColors.text),
          ),
        ),
      ],
    );
  }
}

class _MusicBoxSongAttributionValue extends StatelessWidget {
  const _MusicBoxSongAttributionValue({
    required this.value,
    required this.person,
    required this.showAvatar,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final String value;
  final MusicBoxRequester? person;
  final bool showAvatar;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  Widget build(BuildContext context) {
    final style = UiTypography.label.copyWith(color: UiColors.text);
    final user = person == null
        ? null
        : UserSummary(
            id: person!.userId,
            username: person!.displayName,
            displayName: person!.displayName,
            avatarUrl: person!.avatarUrl,
            defaultAvatarKey: person!.defaultAvatarKey,
          );
    return LayoutBuilder(
      builder: (context, constraints) {
        const avatarSize = 26.0;
        const avatarGap = 5.0;
        // TextPainter and RenderParagraph can differ by a few physical pixels
        // after font fallback and device-pixel rounding. Keep enough trailing
        // room that a measured single line never loses its final glyph.
        const textLayoutSlack = 8.0;
        final avatarWidth = showAvatar ? avatarSize + avatarGap : 0.0;
        final maxTextWidth = (constraints.maxWidth - avatarWidth).clamp(
          1.0,
          double.infinity,
        );
        final measurementWidth = (maxTextWidth - textLayoutSlack).clamp(
          1.0,
          maxTextWidth,
        );
        final painter = TextPainter(
          text: TextSpan(text: value.isEmpty ? ' ' : value, style: style),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: measurementWidth);
        final lines = painter.computeLineMetrics();
        var longestLine = 0.0;
        for (final line in lines) {
          if (line.width > longestLine) longestLine = line.width;
        }
        final textWidth = lines.length > 1
            ? maxTextWidth
            : (longestLine + textLayoutSlack).clamp(1.0, maxTextWidth);
        return Row(
          key: const ValueKey<String>('music-box-song-attribution-value-group'),
          mainAxisAlignment: MainAxisAlignment.end,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            if (showAvatar) ...[
              UserHoverCard(
                user: user!,
                currentUser: currentUser,
                onResolveProfile: onResolveUserProfile,
                onResolveRoomProfile: onResolveRoomProfile,
                onEnterCommonRoom: onEnterCommonRoom,
                profileActionBuilder: userProfileActionBuilder,
                showRoomRole: true,
                child: Avatar(
                  key: const ValueKey<String>(
                    'music-box-song-attribution-avatar',
                  ),
                  label: person!.avatarLabel,
                  imageUrl: AppConfigScope.of(
                    context,
                  ).resolveAssetUrl(person!.avatarUrl),
                  defaultAvatarKey: person!.defaultAvatarKey,
                  size: avatarSize,
                  showBorder: false,
                ),
              ),
              const SizedBox(width: avatarGap),
            ],
            SizedBox(
              width: textWidth,
              child: Text(
                value,
                key: const ValueKey<String>('music-box-song-attribution-name'),
                textAlign: TextAlign.right,
                style: style,
                softWrap: true,
              ),
            ),
          ],
        );
      },
    );
  }
}

class _MusicBoxSongDetailRow extends StatelessWidget {
  const _MusicBoxSongDetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 38,
            child: HoverCardSelectableText(
              value: label,
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
          ),
          Expanded(
            child: HoverCardSelectableText(
              value: value,
              textAlign: TextAlign.right,
              style: UiTypography.label.copyWith(color: UiColors.text),
              maxLines: 12,
            ),
          ),
        ],
      ),
    );
  }
}

final RegExp _musicBoxBvidPattern = RegExp(
  r'BV[0-9A-Za-z]+',
  caseSensitive: false,
);

String _musicBoxBilibiliBvId(String trackId) {
  final raw = trackId.trim();
  final match = _musicBoxBvidPattern.firstMatch(raw)?.group(0);
  if (match == null || match.length <= 2) return raw.isEmpty ? '未知' : raw;
  return 'BV${match.substring(2)}';
}

class _MusicBoxAdaptiveFullText extends StatelessWidget {
  const _MusicBoxAdaptiveFullText({
    super.key,
    required this.text,
    required this.style,
  });

  final String text;
  final TextStyle style;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final baseSize = style.fontSize ?? 16;
        final painter = TextPainter(
          text: TextSpan(text: text, style: style),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: constraints.maxWidth);
        final lineCount = painter.computeLineMetrics().length;
        final reduction = lineCount <= 2
            ? 0.0
            : (lineCount - 2).clamp(1, 4).toDouble();
        final fontSize = (baseSize - reduction).clamp(11.0, baseSize);
        return HoverCardSelectableText(
          value: text,
          style: style.copyWith(fontSize: fontSize),
          maxLines: lineCount < 1 ? 1 : lineCount,
        );
      },
    );
  }
}

/// Opens the add-to-playlist picker for a queue item or catalog track and
/// submits to the personal or room playlist API according to the chosen
/// target's scope.
Future<void> _musicBoxAddToPlaylist(
  BuildContext context, {
  required MusicBoxController? controller,
  required String? roomId,
  MusicBoxQueueItem? item,
  MusicBoxSearchResult? result,
  int? durationMs,
}) async {
  if (controller == null) return;
  assert(item != null || result != null);

  Future<List<MusicTrackPlaylistTarget>> loadTargets() async {
    final targets = <MusicTrackPlaylistTarget>[];
    Object? roomError;
    Object? personalError;
    if (roomId != null) {
      try {
        final page = await controller.loadRoomPlaylists(roomId: roomId);
        targets.addAll(
          page.playlists.map(
            (playlist) =>
                MusicTrackPlaylistTarget.room(playlist: playlist, roomId: roomId),
          ),
        );
      } catch (error) {
        roomError = error;
      }
    }
    try {
      final page = await controller.loadMyPlaylists();
      targets.addAll(page.playlists.map(MusicTrackPlaylistTarget.personal));
    } catch (error) {
      personalError = error;
    }
    if (targets.isEmpty && (roomError != null || personalError != null)) {
      throw roomError ?? personalError!;
    }
    return targets;
  }

  Future<void> add(MusicTrackPlaylistTarget target) async {
    if (target.roomScoped) {
      final targetRoomId = target.roomId ?? roomId;
      if (targetRoomId == null) return;
      if (item != null) {
        await controller.addQueueItemToRoomPlaylist(
          roomId: targetRoomId,
          playlistId: target.playlist.id,
          item: item,
        );
      } else {
        await controller.addSearchResultToRoomPlaylist(
          roomId: targetRoomId,
          playlistId: target.playlist.id,
          result: result!,
          durationMs: durationMs,
        );
      }
      return;
    }
    if (item != null) {
      await controller.addQueueItemToMyPlaylist(
        playlistId: target.playlist.id,
        item: item,
      );
    } else {
      await controller.addSearchResultToMyPlaylist(
        playlistId: target.playlist.id,
        result: result!,
        durationMs: durationMs,
      );
    }
  }

  final target = await showMusicTrackPlaylistTargetDialog(
    context,
    loadTargets: loadTargets,
    onAdd: add,
  );
  if (target == null || !context.mounted) return;
  showFloatingSuccessNotice(context, '已添加到「${target.playlist.name}」');
}
