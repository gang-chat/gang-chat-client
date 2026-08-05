import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../app/room_display.dart' as room_display;
import '../protocol/models.dart';
import '../ui/ui.dart';
import 'chat_image_preview.dart';
import 'hover_card_anchor.dart';

typedef RoomProfileResolver = Future<PublicRoom> Function(PublicRoom room);
typedef UserProfileResolver = Future<UserSummary> Function(UserSummary user);
typedef UserProfileActionBuilder =
    UserProfileAction? Function(UserSummary user);

class UserProfileAction {
  const UserProfileAction({
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final VoidCallback onPressed;
}

class RoomHoverCard extends StatefulWidget {
  const RoomHoverCard({
    super.key,
    required this.room,
    required this.currentUser,
    required this.child,
    this.onResolveRoom,
    this.onResolveUserProfile,
    this.onEnterRoom,
  });

  final PublicRoom room;
  final CurrentUser currentUser;
  final Widget child;
  final RoomProfileResolver? onResolveRoom;
  final UserProfileResolver? onResolveUserProfile;
  final ValueChanged<PublicRoom>? onEnterRoom;

  @override
  State<RoomHoverCard> createState() => _RoomHoverCardState();
}

class _RoomHoverCardState extends State<RoomHoverCard> {
  PublicRoom? _resolved;
  Future<void>? _resolveFuture;

  PublicRoom get _displayRoom => _resolved ?? widget.room;

  @override
  void didUpdateWidget(covariant RoomHoverCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    final roomChanged = oldWidget.room.id != widget.room.id;
    final deletionChanged = oldWidget.room.isDeleted != widget.room.isDeleted;
    final resolverPresenceChanged =
        (oldWidget.onResolveRoom == null) != (widget.onResolveRoom == null);
    if (!roomChanged && !deletionChanged && !resolverPresenceChanged) return;
    _resolved = null;
    _resolveFuture = null;
  }

  Future<void> _resolveRoom() {
    if (widget.room.isDeleted) return Future<void>.value();
    final resolver = widget.onResolveRoom;
    if (resolver == null) return Future<void>.value();
    final existing = _resolveFuture;
    if (existing != null) return existing;

    final requestedRoom = widget.room;
    final future = () async {
      try {
        final room = await resolver(requestedRoom);
        if (!mounted || widget.room.id != requestedRoom.id) return;
        setState(() => _resolved = room);
      } catch (_) {
        if (!mounted || widget.room.id != requestedRoom.id) return;
        // Fall back to the current lightweight summary instead of keeping a
        // previously resolved card that may now be stale.
        setState(() => _resolved = null);
      } finally {
        if (mounted && widget.room.id == requestedRoom.id) {
          _resolveFuture = null;
        }
      }
    }();
    _resolveFuture = future;
    return future;
  }

  @override
  Widget build(BuildContext context) {
    return HoverCardAnchor(
      resetKey: Object.hash(
        widget.room.id,
        widget.room.isDeleted,
        widget.onResolveRoom != null,
        widget.onResolveUserProfile != null,
      ),
      onBeforeOpen: widget.room.isDeleted || widget.onResolveRoom == null
          ? null
          : _resolveRoom,
      cardBuilder: (context) => _RoomProfileCard(
        room: _displayRoom,
        currentUser: widget.currentUser,
        onResolveRoomProfile: widget.onResolveRoom,
        onResolveUserProfile: widget.onResolveUserProfile,
        onEnterRoom: widget.onEnterRoom,
      ),
      child: widget.child,
    );
  }
}

class UserHoverCard extends StatefulWidget {
  const UserHoverCard({
    super.key,
    required this.user,
    required this.child,
    this.currentUser,
    this.onResolveProfile,
    this.onResolveRoomProfile,
    this.onEnterCommonRoom,
    this.profileActionBuilder,
    this.inLive = false,
    this.showRoomRole = false,
  });

  final UserSummary user;
  final Widget child;
  final CurrentUser? currentUser;
  final bool inLive;
  final bool showRoomRole;

  /// Fetches a richer, up-to-date profile (gender, common rooms) before the
  /// card opens. When null the card shows only the supplied summary.
  final UserProfileResolver? onResolveProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? profileActionBuilder;

  @override
  State<UserHoverCard> createState() => _UserHoverCardState();
}

Future<void> showUserProfileCardAtPosition(
  BuildContext context, {
  required Offset position,
  required UserSummary user,
  CurrentUser? currentUser,
  UserProfileResolver? onResolveProfile,
  RoomProfileResolver? onResolveRoomProfile,
  ValueChanged<PublicRoom>? onEnterCommonRoom,
  UserProfileActionBuilder? profileActionBuilder,
  bool inLive = false,
  bool showRoomRole = false,
  bool resolveOnOpen = true,
}) {
  return Navigator.of(context, rootNavigator: true).push(
    _UserProfileCardPopupRoute(
      position: position,
      user: user,
      currentUser: currentUser,
      onResolveProfile: onResolveProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterCommonRoom,
      profileActionBuilder: profileActionBuilder,
      inLive: inLive,
      showRoomRole: showRoomRole,
      resolveOnOpen: resolveOnOpen,
    ),
  );
}

class _UserHoverCardState extends State<UserHoverCard> {
  UserSummary? _resolved;
  Future<void>? _resolveFuture;

  UserSummary get _displayUser => _resolved ?? widget.user;

  @override
  void didUpdateWidget(covariant UserHoverCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    final userChanged = oldWidget.user.id != widget.user.id;
    final deletionChanged = oldWidget.user.isDeleted != widget.user.isDeleted;
    final suspensionChanged =
        oldWidget.user.isSuspended != widget.user.isSuspended;
    final currentUserChanged =
        oldWidget.currentUser?.id != widget.currentUser?.id;
    final resolverPresenceChanged =
        (oldWidget.onResolveProfile == null) !=
        (widget.onResolveProfile == null);
    final liveChanged = oldWidget.inLive != widget.inLive;
    final roleVisibilityChanged = oldWidget.showRoomRole != widget.showRoomRole;
    final actionPresenceChanged =
        (oldWidget.profileActionBuilder == null) !=
        (widget.profileActionBuilder == null);
    if (!userChanged &&
        !deletionChanged &&
        !suspensionChanged &&
        !currentUserChanged &&
        !resolverPresenceChanged &&
        !liveChanged &&
        !roleVisibilityChanged &&
        !actionPresenceChanged) {
      return;
    }
    _resolved = null;
    _resolveFuture = null;
  }

  Future<void> _resolveProfile() {
    if (widget.user.isDeleted) return Future<void>.value();
    final resolver = widget.onResolveProfile;
    if (resolver == null) return Future<void>.value();
    final existing = _resolveFuture;
    if (existing != null) return existing;

    final requestedUser = widget.user;
    final future = () async {
      try {
        final profile = await resolver(requestedUser);
        if (!mounted || widget.user.id != requestedUser.id) return;
        setState(() => _resolved = profile);
      } catch (_) {
        if (!mounted || widget.user.id != requestedUser.id) return;
        // Keep showing the current lightweight summary on failure.
        setState(() => _resolved = null);
      } finally {
        if (mounted && widget.user.id == requestedUser.id) {
          _resolveFuture = null;
        }
      }
    }();
    _resolveFuture = future;
    return future;
  }

  @override
  Widget build(BuildContext context) {
    return HoverCardAnchor(
      resetKey: Object.hash(
        widget.user.id,
        widget.user.isDeleted,
        widget.user.isSuspended,
        widget.onResolveProfile != null,
        widget.onResolveRoomProfile != null,
        widget.currentUser?.id,
        widget.inLive,
        widget.showRoomRole,
        widget.profileActionBuilder != null,
      ),
      onBeforeOpen: widget.user.isDeleted || widget.onResolveProfile == null
          ? null
          : _resolveProfile,
      cardBuilder: (context) => _UserProfileCard(
        user: _displayUser,
        currentUser: widget.currentUser,
        onResolveUserProfile: widget.onResolveProfile,
        onResolveRoomProfile: widget.onResolveRoomProfile,
        onEnterCommonRoom: widget.onEnterCommonRoom,
        inLive: widget.inLive,
        showRoomRole: widget.showRoomRole,
        action: _displayUser.isDeleted
            ? null
            : widget.profileActionBuilder?.call(_displayUser),
      ),
      child: widget.child,
    );
  }
}

class _UserProfileCardPopupRoute extends PopupRoute<void> {
  _UserProfileCardPopupRoute({
    required this.position,
    required this.user,
    required this.currentUser,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.profileActionBuilder,
    required this.inLive,
    required this.showRoomRole,
    required this.resolveOnOpen,
  });

  final Offset position;
  final UserSummary user;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final bool inLive;
  final bool showRoomRole;
  final bool resolveOnOpen;

  @override
  Color? get barrierColor => Colors.transparent;

  @override
  bool get barrierDismissible => true;

  @override
  String? get barrierLabel => '关闭用户名片';

  @override
  Duration get transitionDuration => const Duration(milliseconds: 110);

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    final safeInsets = hoverCardOverlaySafeInsets(context);
    return CustomSingleChildLayout(
      delegate: _UserProfileCardPopupLayoutDelegate(
        position,
        safeInsets: safeInsets,
      ),
      child: FadeTransition(
        opacity: CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
        child: AnchoredPanel(
          width: hoverCardDefaultWidth,
          child: Material(
            type: MaterialType.transparency,
            child: _ResolvingUserProfileCard(
              user: user,
              currentUser: currentUser,
              onResolveProfile: onResolveProfile,
              onResolveRoomProfile: onResolveRoomProfile,
              onEnterCommonRoom: onEnterCommonRoom,
              profileActionBuilder: profileActionBuilder,
              inLive: inLive,
              showRoomRole: showRoomRole,
              resolveOnOpen: resolveOnOpen,
            ),
          ),
        ),
      ),
    );
  }
}

class _UserProfileCardPopupLayoutDelegate extends SingleChildLayoutDelegate {
  const _UserProfileCardPopupLayoutDelegate(
    this.anchor, {
    required this.safeInsets,
  });

  static const double _screenPadding = 8;
  static const double _gap = 10;

  final Offset anchor;
  final EdgeInsets safeInsets;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    final maxWidth = constraints.hasBoundedWidth
        ? math.max(
            0.0,
            constraints.maxWidth - safeInsets.horizontal - _screenPadding * 2,
          )
        : double.infinity;
    final maxHeight = constraints.hasBoundedHeight
        ? math.max(
            0.0,
            constraints.maxHeight - safeInsets.vertical - _screenPadding * 2,
          )
        : double.infinity;
    return BoxConstraints(maxWidth: maxWidth, maxHeight: maxHeight);
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final minX = safeInsets.left + _screenPadding;
    final maxX = math.max(
      minX,
      size.width - safeInsets.right - childSize.width - _screenPadding,
    );
    final x = anchor.dx.clamp(minX, maxX).toDouble();
    var y = anchor.dy + _gap;
    final safeBottom = size.height - safeInsets.bottom - _screenPadding;
    if (y + childSize.height > safeBottom) {
      y = anchor.dy - childSize.height - _gap;
    }
    final minY = safeInsets.top + _screenPadding;
    final maxY = math.max(minY, safeBottom - childSize.height);
    y = y.clamp(minY, maxY).toDouble();
    return Offset(x, y);
  }

  @override
  bool shouldRelayout(_UserProfileCardPopupLayoutDelegate oldDelegate) {
    return anchor != oldDelegate.anchor || safeInsets != oldDelegate.safeInsets;
  }
}

class _ResolvingUserProfileCard extends StatefulWidget {
  const _ResolvingUserProfileCard({
    required this.user,
    required this.currentUser,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.profileActionBuilder,
    required this.inLive,
    required this.showRoomRole,
    required this.resolveOnOpen,
  });

  final UserSummary user;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final bool inLive;
  final bool showRoomRole;
  final bool resolveOnOpen;

  @override
  State<_ResolvingUserProfileCard> createState() =>
      _ResolvingUserProfileCardState();
}

class _ResolvingUserProfileCardState extends State<_ResolvingUserProfileCard> {
  UserSummary? _resolved;

  UserSummary get _displayUser => _resolved ?? widget.user;

  @override
  void initState() {
    super.initState();
    if (widget.resolveOnOpen && !widget.user.isDeleted) _resolve();
  }

  Future<void> _resolve() async {
    if (widget.user.isDeleted) return;
    final resolver = widget.onResolveProfile;
    if (resolver == null) return;
    final requestedUser = widget.user;
    try {
      final profile = await resolver(requestedUser);
      if (!mounted || widget.user.id != requestedUser.id) return;
      setState(() => _resolved = profile);
    } catch (_) {
      if (!mounted || widget.user.id != requestedUser.id) return;
      setState(() => _resolved = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = _displayUser;
    return _UserProfileCard(
      user: user,
      currentUser: widget.currentUser,
      onResolveUserProfile: widget.onResolveProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      inLive: widget.inLive,
      showRoomRole: widget.showRoomRole,
      action: user.isDeleted ? null : widget.profileActionBuilder?.call(user),
    );
  }
}

class _UserProfileCard extends StatelessWidget {
  const _UserProfileCard({
    required this.user,
    this.currentUser,
    this.onResolveUserProfile,
    this.onResolveRoomProfile,
    this.onEnterCommonRoom,
    this.inLive = false,
    this.showRoomRole = false,
    this.action,
  });

  final UserSummary user;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final bool inLive;
  final bool showRoomRole;
  final UserProfileAction? action;

  @override
  Widget build(BuildContext context) {
    if (user.isDeleted) return const _DeletedUserProfileCard();
    final name = room_display.userPrimaryName(user);
    final gender = genderMark(user.gender);
    final role = showRoomRole ? room_display.roomRoleLabel(user) : null;
    final isCurrentUser = currentUser?.id == user.id;
    final online =
        !user.isSuspended &&
        (inLive || isCurrentUser || (user.isOnline ?? false));
    final presencePill = user.isSuspended
        ? PresencePill.suspended()
        : inLive
        ? PresencePill.voice()
        : online
        ? PresencePill.online()
        : PresencePill.offline();
    final bio = user.bio?.trim();
    final uid = user.uid?.trim();
    final commonRooms = user.commonRooms.where((r) => r.isUsable).toList();
    final config = AppConfigScope.of(context);
    final avatarUrl = config.resolveAssetUrl(user.avatarUrl);
    final previewAvatarUrl = _nonEmpty(user.avatarUrl) == null
        ? null
        : _nonEmpty(avatarUrl);

    return Padding(
      padding: const EdgeInsets.all(UiSpacing.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _ProfileImagePreview(
                key: const ValueKey('user-profile-card-avatar-preview'),
                imageUrl: previewAvatarUrl,
                suggestedName:
                    '${room_display.userPrimaryName(user)}-avatar.png',
                child: Avatar(
                  label: room_display.userAvatarLabel(user),
                  imageUrl: avatarUrl,
                  defaultAvatarKey: user.defaultAvatarKey,
                  size: 48,
                  active: online,
                  activeBorderWidth: 2,
                  activeBorderColor: inLive ? UiColors.presenceVoice : null,
                  paintBorderOnForeground: true,
                ),
              ),
              const SizedBox(width: UiSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Flexible(
                          child: HoverCardSelectableText(
                            value: name,
                            style: UiTypography.title.copyWith(fontSize: 16),
                            maxLines: 12,
                          ),
                        ),
                        if (gender != null) ...[
                          const SizedBox(width: 5),
                          Text(
                            gender.symbol,
                            maxLines: 1,
                            style: UiTypography.title.copyWith(
                              color: gender.color,
                              fontSize: 17,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 2),
                    HoverCardSelectableText(
                      value: '@${user.username}',
                      copyStartOffset: 1,
                      maxLines: 12,
                      style: UiTypography.label.copyWith(
                        color: UiColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: UiSpacing.md),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              presencePill,
              if (role != null) RoleBadge(label: role),
            ],
          ),
          if (bio != null && bio.isNotEmpty) ...[
            const SizedBox(height: UiSpacing.md),
            HoverCardSelectableText(
              value: bio,
              maxLines: 4,
              style: UiTypography.body.copyWith(color: UiColors.textSecondary),
            ),
          ],
          if (commonRooms.isNotEmpty) ...[
            const SizedBox(height: UiSpacing.md),
            Text(
              '${commonRooms.length} 个共同房间',
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
            const SizedBox(height: UiSpacing.xs),
            _UserCommonRoomList(
              rooms: commonRooms,
              currentUser: currentUser,
              onResolveUserProfile: onResolveUserProfile,
              onResolveRoomProfile: onResolveRoomProfile,
              onEnterRoom: onEnterCommonRoom,
            ),
          ],
          if (uid != null && uid.isNotEmpty) ...[
            const SizedBox(height: UiSpacing.sm),
            HoverCardSelectableText(
              value: 'UID: $uid',
              copyStartOffset: 'UID: '.length,
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
          ],
          if (action != null) ...[
            const SizedBox(height: UiSpacing.md),
            Center(
              child: Button(
                icon: Icon(action!.icon),
                tone: ButtonTone.primary,
                height: 34,
                padding: const EdgeInsets.symmetric(horizontal: 14),
                onPressed: action!.onPressed,
                child: Text(action!.label),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DeletedUserProfileCard extends StatelessWidget {
  const _DeletedUserProfileCard();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(UiSpacing.lg),
      child: Row(
        key: const ValueKey('deleted-user-profile-card'),
        mainAxisSize: MainAxisSize.min,
        children: [
          const Avatar(
            label: '',
            defaultAvatarKey: 'graphite-2',
            size: 42,
            showFallbackText: false,
          ),
          const SizedBox(width: UiSpacing.md),
          Text(
            '用户已注销',
            style: UiTypography.body.copyWith(color: UiColors.textSecondary),
          ),
        ],
      ),
    );
  }
}

class _ProfileImagePreview extends StatelessWidget {
  const _ProfileImagePreview({
    super.key,
    required this.imageUrl,
    required this.child,
    required this.suggestedName,
  });

  final String? imageUrl;
  final Widget child;
  final String suggestedName;

  Future<void> _openPreview(BuildContext context, String url) async {
    final hoverScope = HoverCardTapRegionScope.maybeOf(context);
    final previewActions = ChatImagePreviewActionsScope.maybeOf(context);
    hoverScope?.onOverlayActivityChanged?.call(true);
    try {
      await showChatImagePreview(
        context,
        imageUrl: url,
        suggestedName: suggestedName,
        actions: previewActions ?? ChatImagePreviewActions.disabled(),
        showActionBar: previewActions != null,
        forceSquare: true,
      );
    } finally {
      hoverScope?.onOverlayActivityChanged?.call(false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final url = _nonEmpty(imageUrl);
    if (url == null) return child;

    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => _openPreview(context, url),
        child: child,
      ),
    );
  }
}

class _UserCommonRoomList extends StatefulWidget {
  const _UserCommonRoomList({
    required this.rooms,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterRoom,
  });

  final List<UserCommonRoom> rooms;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterRoom;

  @override
  State<_UserCommonRoomList> createState() => _UserCommonRoomListState();
}

class _UserCommonRoomListState extends State<_UserCommonRoomList> {
  static const double _maxHeight = 116;

  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final list = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final room in widget.rooms)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: _UserCommonRoomRow(
              room: room,
              currentUser: widget.currentUser,
              onResolveUserProfile: widget.onResolveUserProfile,
              onResolveRoomProfile: widget.onResolveRoomProfile,
              onEnterRoom: widget.onEnterRoom,
            ),
          ),
      ],
    );

    if (widget.rooms.length <= 4) return list;

    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: _maxHeight),
      child: Scrollbar(
        controller: _scrollController,
        thumbVisibility: true,
        child: SingleChildScrollView(
          controller: _scrollController,
          padding: const EdgeInsets.only(right: UiSpacing.sm),
          child: list,
        ),
      ),
    );
  }
}

class _UserCommonRoomRow extends StatelessWidget {
  const _UserCommonRoomRow({
    required this.room,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterRoom,
  });

  final UserCommonRoom room;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterRoom;

  @override
  Widget build(BuildContext context) {
    final publicRoom = _publicRoomFromCommonRoom(room);
    return _CompactProfileIdentity(
      name: room_display.commonRoomDisplayName(room),
      style: UiTypography.body.copyWith(
        color: UiColors.textSecondary,
        fontSize: 13,
      ),
      avatarBuilder: (size) {
        final avatar = Avatar(
          label: room_display.commonRoomAvatarLabel(room),
          imageUrl: AppConfigScope.of(context).resolveAssetUrl(room.avatarUrl),
          defaultAvatarKey: room.defaultAvatarKey,
          size: size,
          activeBorderWidth: 1,
        );
        return currentUser == null
            ? avatar
            : RoomHoverCard(
                room: publicRoom,
                currentUser: currentUser!,
                onResolveRoom: onResolveRoomProfile,
                onResolveUserProfile: onResolveUserProfile,
                onEnterRoom: onEnterRoom,
                child: avatar,
              );
      },
    );
  }
}

class _CompactProfileIdentity extends StatelessWidget {
  const _CompactProfileIdentity({
    required this.name,
    required this.style,
    required this.avatarBuilder,
  });

  static const double _normalAvatarSize = 20;
  static const double _compactAvatarSize = 16;
  static const double _textLayoutSlack = 8;

  final String name;
  final TextStyle style;
  final Widget Function(double size) avatarBuilder;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        // This is the row's real width after the owning card has applied its
        // width clamp and padding. Never derive the compact breakpoint from
        // the screen or the default hover-card width.
        final painter = TextPainter(
          text: TextSpan(text: name, style: style),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
          maxLines: 1,
        )..layout();
        final normalWidth =
            _normalAvatarSize + UiSpacing.sm + painter.width + _textLayoutSlack;
        final compact = normalWidth > constraints.maxWidth;
        final avatarSize = compact ? _compactAvatarSize : _normalAvatarSize;
        final identityWidth =
            (avatarSize + UiSpacing.sm + painter.width + _textLayoutSlack)
                .clamp(avatarSize + UiSpacing.sm + 1, constraints.maxWidth)
                .toDouble();
        return SizedBox(
          width: identityWidth,
          child: Row(
            children: [
              avatarBuilder(avatarSize),
              const SizedBox(width: UiSpacing.sm),
              Expanded(
                child: compact
                    ? FittedBox(
                        fit: BoxFit.scaleDown,
                        alignment: Alignment.centerLeft,
                        child: Text(
                          name,
                          maxLines: 1,
                          softWrap: false,
                          overflow: TextOverflow.visible,
                          style: style,
                        ),
                      )
                    : Text(
                        name,
                        maxLines: 1,
                        softWrap: false,
                        overflow: TextOverflow.visible,
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

class _RoomProfileCard extends StatelessWidget {
  const _RoomProfileCard({
    required this.room,
    required this.currentUser,
    this.onResolveRoomProfile,
    this.onResolveUserProfile,
    this.onEnterRoom,
  });

  final PublicRoom room;
  final CurrentUser currentUser;
  final RoomProfileResolver? onResolveRoomProfile;
  final UserProfileResolver? onResolveUserProfile;
  final ValueChanged<PublicRoom>? onEnterRoom;

  @override
  Widget build(BuildContext context) {
    if (room.isDeleted) return const _DeletedRoomProfileCard();
    final config = AppConfigScope.of(context);
    final description = _nonEmpty(room.description);
    final rid = _nonEmpty(room.rid) ?? room.id;
    final creator = room.createdBy;
    final joined = room.joined;
    final myName = _myRoomDisplayName(room, currentUser);
    final myAvatarUrl = _myRoomAvatarUrl(room, currentUser);
    final myDefaultAvatarKey = _myRoomDefaultAvatarKey(room, currentUser);
    final myRole = _myRoomRoleLabel(room, currentUser);
    final roomAvatarUrl = config.resolveAssetUrl(room.avatarUrl);
    final previewRoomAvatarUrl = _nonEmpty(room.avatarUrl) == null
        ? null
        : _nonEmpty(roomAvatarUrl);

    return Padding(
      padding: const EdgeInsets.all(UiSpacing.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _ProfileImagePreview(
                key: const ValueKey('room-profile-card-icon-preview'),
                imageUrl: previewRoomAvatarUrl,
                suggestedName: '${room.name}-icon.png',
                child: Avatar(
                  label: room_display.publicRoomAvatarLabel(room),
                  imageUrl: roomAvatarUrl,
                  defaultAvatarKey: room.defaultAvatarKey,
                  size: 48,
                  activeBorderWidth: 2,
                ),
              ),
              const SizedBox(width: UiSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    HoverCardSelectableText(
                      value: room.name,
                      style: UiTypography.title.copyWith(fontSize: 16),
                      maxLines: 12,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${room.memberCount} 名成员',
                      maxLines: 2,
                      style: UiTypography.label.copyWith(
                        color: UiColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: UiSpacing.md),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              StatusBadge(
                label: room.joined ? '已加入' : '未加入',
                icon: room.joined ? Icons.check_circle : Icons.circle_outlined,
                active: room.joined,
              ),
              StatusBadge(label: room_display.visibilityLabel(room.visibility)),
              StatusBadge(
                label: room_display.roomJoinPolicyLabel(room.joinPolicy),
              ),
            ],
          ),
          if (description != null) ...[
            const SizedBox(height: UiSpacing.md),
            HoverCardSelectableText(
              value: description,
              maxLines: 4,
              style: UiTypography.body.copyWith(color: UiColors.textSecondary),
            ),
          ],
          if (creator != null) ...[
            const SizedBox(height: UiSpacing.md),
            Text(
              '创建者',
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
            const SizedBox(height: UiSpacing.xs),
            _RoomCardPersonRow(
              avatarLabel: room_display.userAvatarLabel(creator),
              avatarUrl: creator.avatarUrl,
              defaultAvatarKey: creator.defaultAvatarKey,
              name: room_display.userPrimaryName(creator),
              profileUser: creator,
              currentUser: currentUser,
              onResolveUserProfile: onResolveUserProfile,
              onResolveRoomProfile: onResolveRoomProfile,
              onEnterCommonRoom: onEnterRoom,
            ),
          ],
          if (joined) ...[
            const SizedBox(height: UiSpacing.md),
            Text(
              '我的房间内信息',
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
            const SizedBox(height: UiSpacing.xs),
            _RoomCardPersonRow(
              avatarLabel: _currentUserAvatarLabel(currentUser),
              avatarUrl: myAvatarUrl,
              defaultAvatarKey: myDefaultAvatarKey,
              name: myName,
              trailing: RoleBadge(label: myRole),
            ),
          ],
          const SizedBox(height: UiSpacing.sm),
          HoverCardSelectableText(
            value: 'RID: $rid',
            copyStartOffset: 'RID: '.length,
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
          if (joined) ...[
            const SizedBox(height: UiSpacing.md),
            Center(
              child: Button(
                icon: const Icon(Icons.login_rounded),
                tone: ButtonTone.primary,
                height: 34,
                padding: const EdgeInsets.symmetric(horizontal: 14),
                onPressed: onEnterRoom == null
                    ? null
                    : () => onEnterRoom!(room),
                child: const Text('进入房间'),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DeletedRoomProfileCard extends StatelessWidget {
  const _DeletedRoomProfileCard();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(UiSpacing.lg),
      child: Row(
        key: const ValueKey('deleted-room-profile-card'),
        mainAxisSize: MainAxisSize.min,
        children: [
          const Avatar(
            label: '',
            defaultAvatarKey: 'graphite-2',
            size: 42,
            showFallbackText: false,
          ),
          const SizedBox(width: UiSpacing.md),
          Text(
            '房间已删除',
            style: UiTypography.body.copyWith(color: UiColors.textSecondary),
          ),
        ],
      ),
    );
  }
}

class _RoomCardPersonRow extends StatelessWidget {
  const _RoomCardPersonRow({
    required this.avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.name,
    this.profileUser,
    this.currentUser,
    this.onResolveUserProfile,
    this.onResolveRoomProfile,
    this.onEnterCommonRoom,
    this.trailing,
  });

  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final String name;
  final UserSummary? profileUser;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final nameStyle = UiTypography.body.copyWith(
      color: UiColors.textSecondary,
      fontSize: 13,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final identity = _CompactProfileIdentity(
          name: name,
          style: nameStyle,
          avatarBuilder: (size) {
            final avatar = Avatar(
              label: avatarLabel,
              imageUrl: AppConfigScope.of(context).resolveAssetUrl(avatarUrl),
              defaultAvatarKey: defaultAvatarKey,
              size: size,
              activeBorderWidth: 1,
            );
            return profileUser == null || currentUser == null
                ? avatar
                : UserHoverCard(
                    user: profileUser!,
                    currentUser: currentUser,
                    onResolveProfile: onResolveUserProfile,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onEnterCommonRoom: onEnterCommonRoom,
                    child: avatar,
                  );
          },
        );
        if (trailing == null) {
          return Align(alignment: Alignment.centerLeft, child: identity);
        }
        return Wrap(
          spacing: UiSpacing.sm,
          runSpacing: UiSpacing.xs,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [identity, trailing!],
        );
      },
    );
  }
}

String _myRoomDisplayName(PublicRoom room, CurrentUser currentUser) {
  return _nonEmpty(room.personalProfile.displayName) ?? currentUser.displayName;
}

String? _myRoomAvatarUrl(PublicRoom room, CurrentUser currentUser) {
  return currentUser.avatarUrl;
}

String _myRoomDefaultAvatarKey(PublicRoom room, CurrentUser currentUser) {
  return currentUser.defaultAvatarKey;
}

String _currentUserAvatarLabel(CurrentUser currentUser) {
  return room_display.userAvatarLabel(currentUser.toSummary());
}

String _myRoomRoleLabel(PublicRoom room, CurrentUser currentUser) {
  final role = room_display.roomRoleLabelFromValue(room.myMembership?.role);
  if (role != null) return role;
  return currentUser.isSuperuser ? '超级用户' : '成员';
}

String? _nonEmpty(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

PublicRoom _publicRoomFromCommonRoom(UserCommonRoom room) {
  return PublicRoom(
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
  );
}

@visibleForTesting
class RoomHoverCardForTest extends StatelessWidget {
  const RoomHoverCardForTest({
    super.key,
    required this.room,
    required this.currentUser,
    this.onResolveRoom,
    this.onResolveUserProfile,
    this.onEnterRoom,
  });

  final PublicRoom room;
  final CurrentUser currentUser;
  final RoomProfileResolver? onResolveRoom;
  final UserProfileResolver? onResolveUserProfile;
  final ValueChanged<PublicRoom>? onEnterRoom;

  @override
  Widget build(BuildContext context) {
    return RoomHoverCard(
      room: room,
      currentUser: currentUser,
      onResolveRoom: onResolveRoom,
      onResolveUserProfile: onResolveUserProfile,
      onEnterRoom: onEnterRoom,
      child: Avatar(label: room_display.publicRoomAvatarLabel(room), size: 34),
    );
  }
}
