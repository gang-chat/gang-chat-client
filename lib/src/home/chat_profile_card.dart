part of 'chat_pane.dart';

/// Wraps a message avatar so hovering it reveals a floating profile card.
class _AvatarHoverCard extends StatelessWidget {
  const _AvatarHoverCard({
    required this.user,
    required this.currentUser,
    required this.child,
    this.onResolveProfile,
    this.onResolveRoomProfile,
    this.onEnterCommonRoom,
    this.profileActionBuilder,
    this.inLive = false,
    this.showRoomRole = true,
  });

  final UserSummary user;
  final CurrentUser currentUser;
  final Widget child;

  /// Lazily fetches a richer profile (gender, common rooms) the first time the
  /// card opens. When null the card shows only the message summary.
  final Future<UserSummary> Function(UserSummary sender)? onResolveProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final bool inLive;
  final bool showRoomRole;

  @override
  Widget build(BuildContext context) {
    // gang-bot is not an account: there is no profile to show.
    if (user.isBot) {
      return Tooltip(message: kGangBotDescription, child: child);
    }
    return UserHoverCard(
      user: user,
      currentUser: currentUser,
      onResolveProfile: onResolveProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterCommonRoom,
      profileActionBuilder: profileActionBuilder,
      inLive: inLive,
      showRoomRole: showRoomRole,
      child: child,
    );
  }
}

/// Test-only entry point for the otherwise-private avatar hover card, so widget
/// tests can pump it directly without standing up the whole message list.
@visibleForTesting
class AvatarHoverCardForTest extends StatelessWidget {
  const AvatarHoverCardForTest({
    super.key,
    required this.user,
    this.currentUser = _avatarHoverCardTestCurrentUser,
    this.onResolveProfile,
    this.onResolveRoomProfile,
    this.onEnterCommonRoom,
    this.profileActionBuilder,
    this.inLive = false,
    this.showRoomRole = true,
  });

  final UserSummary user;
  final CurrentUser currentUser;
  final Future<UserSummary> Function(UserSummary sender)? onResolveProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final bool inLive;
  final bool showRoomRole;

  @override
  Widget build(BuildContext context) {
    return _AvatarHoverCard(
      user: user,
      currentUser: currentUser,
      onResolveProfile: onResolveProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterCommonRoom,
      profileActionBuilder: profileActionBuilder,
      inLive: inLive,
      showRoomRole: showRoomRole,
      child: Avatar(
        label: room_display.userAvatarLabel(user),
        size: 32,
        showBorder: false,
      ),
    );
  }
}

const _avatarHoverCardTestCurrentUser = CurrentUser(
  id: 'avatar-hover-card-test-user',
  uid: '',
  username: 'viewer',
  displayName: 'Viewer',
  bio: '',
  gender: 'secret',
  email: null,
  emailPublic: false,
  phoneNumber: null,
  phoneNumberPublic: false,
  avatarUrl: null,
  defaultAvatarKey: 'green-2',
  isSuperuser: false,
  createdAt: null,
);

const kGangBotDescription =
    'gang-bot 是群里的 AI，不是真人账号。@gang-bot 就能和它聊，房间管理员可以在房间设置里关掉它。';

/// gang-bot's avatar: a robot mark instead of initials.
class _GangBotAvatar extends StatelessWidget {
  const _GangBotAvatar({required this.size});

  final double size;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.selected,
        shape: BoxShape.circle,
        border: Border.all(color: UiColors.selectedBorder),
      ),
      child: SizedBox.square(
        dimension: size,
        child: Icon(
          Icons.smart_toy_outlined,
          color: UiColors.accent,
          size: size * 0.56,
        ),
      ),
    );
  }
}

/// The "AI" tag next to gang-bot's name, so it never passes for a person.
class _GangBotBadge extends StatelessWidget {
  const _GangBotBadge();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.selected,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: UiColors.selectedBorder),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Text(
          'AI',
          style: UiTypography.label.copyWith(
            color: UiColors.accent,
            fontSize: 9,
            fontWeight: FontWeight.w700,
            height: 1.1,
          ),
        ),
      ),
    );
  }
}
