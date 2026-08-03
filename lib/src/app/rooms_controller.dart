import 'dart:typed_data';

import '../protocol/api_client.dart';
import '../protocol/models.dart';
import 'error_display.dart';
import 'room_display.dart' as room_display;
import 'room_join_requests.dart' as room_join_requests;
import 'room_live_state.dart' as room_live_state;

class RoomOpenSnapshot {
  const RoomOpenSnapshot({
    required this.detail,
    required this.messages,
    required this.live,
  });

  final RoomDetail detail;
  final List<Message> messages;
  final LiveState live;
}

class RoomListLoadPatch {
  const RoomListLoadPatch({
    required this.rooms,
    required this.loading,
    required this.error,
  });

  final List<RoomCard> rooms;
  final bool loading;
  final String? error;
}

class RoomCardsPatch {
  const RoomCardsPatch({required this.rooms});

  final List<RoomCard> rooms;
}

class RoomUpdatedPatch {
  const RoomUpdatedPatch({
    required this.rooms,
    required this.selectedRoom,
    required this.shouldReloadMembers,
  });

  final List<RoomCard> rooms;
  final RoomDetail? selectedRoom;
  final bool shouldReloadMembers;
}

class RoomOpenStatePatch {
  const RoomOpenStatePatch({
    required this.settingsOpen,
    required this.selectedRoomId,
    required this.selectedRoom,
    required this.loadingRoom,
    required this.error,
    required this.selectedRoomHasPendingJoinRequests,
    required this.messages,
    required this.live,
    required this.livePanelOpen,
  });

  final bool settingsOpen;
  final String? selectedRoomId;
  final RoomDetail? selectedRoom;
  final bool loadingRoom;
  final String? error;
  final bool selectedRoomHasPendingJoinRequests;
  final List<Message> messages;
  final LiveState? live;
  final bool livePanelOpen;
}

class RoomOpenSuccessEffects {
  const RoomOpenSuccessEffects({
    required this.joinRequestBadgeRoom,
    required this.joinLiveSource,
  });

  final RoomDetail joinRequestBadgeRoom;
  final String? joinLiveSource;
}

class RoomLiveSnapshotPatch {
  const RoomLiveSnapshotPatch({
    required this.roomId,
    required this.rooms,
    required this.selectedLive,
  });

  final String roomId;
  final List<RoomCard> rooms;
  final LiveState? selectedLive;
}

class RoomDeletedPatch {
  const RoomDeletedPatch({
    required this.roomId,
    required this.rooms,
    required this.selectedRoomId,
    required this.selectedRoom,
    required this.selectedRoomHasPendingJoinRequests,
    required this.messages,
    required this.live,
    required this.livePanelOpen,
    required this.settingsOpen,
    required this.joinedLiveRoomId,
    required this.wasSelected,
    required this.shouldDisconnectLive,
  });

  final String roomId;
  final List<RoomCard> rooms;
  final String? selectedRoomId;
  final RoomDetail? selectedRoom;
  final bool selectedRoomHasPendingJoinRequests;
  final List<Message> messages;
  final LiveState? live;
  final bool livePanelOpen;
  final bool settingsOpen;
  final String? joinedLiveRoomId;
  final bool wasSelected;
  final bool shouldDisconnectLive;
}

class RoomSelectedDetailPatch {
  const RoomSelectedDetailPatch({
    required this.selectedRoom,
    required this.rooms,
  });

  final RoomDetail selectedRoom;
  final List<RoomCard> rooms;
}

class RoomRoleChangedPatch {
  const RoomRoleChangedPatch({
    required this.roomId,
    required this.selectedRoom,
  });

  final String roomId;
  final RoomDetail selectedRoom;
}

class RoomLiveRefreshPatch {
  const RoomLiveRefreshPatch({required this.live});

  final LiveState live;
}

class RoomMembersSnapshot {
  const RoomMembersSnapshot({
    required this.members,
    required this.live,
    required this.joinRequests,
    this.joinRequestsError,
  });

  final List<RoomMember> members;
  final LiveState live;
  final List<JoinRequest> joinRequests;
  final String? joinRequestsError;
}

class RoomMembersLiveSnapshot {
  const RoomMembersLiveSnapshot({required this.members, required this.live});

  final List<RoomMember> members;
  final LiveState live;
}

class RoomMembersDialogStatePatch {
  const RoomMembersDialogStatePatch({
    required this.members,
    required this.requests,
    required this.live,
    required this.loading,
    required this.changed,
    required this.error,
    required this.requestError,
    required this.busyRequestIds,
  });

  final List<RoomMember> members;
  final List<JoinRequest> requests;
  final LiveState? live;
  final bool loading;
  final bool changed;
  final String? error;
  final String? requestError;
  final Set<String> busyRequestIds;

  bool get hasPendingRequests => requests.isNotEmpty;
  bool get shouldNotifyPendingRequests => requestError == null;
}

bool shouldSkipRoomOpenRequest({
  required bool loadingRoom,
  required String? selectedRoomId,
  required String roomId,
}) {
  return loadingRoom && selectedRoomId == roomId;
}

bool canApplyRoomOpenResult({
  required String requestedRoomId,
  required String? selectedRoomId,
}) {
  return selectedRoomId == requestedRoomId;
}

bool shouldShowOptimisticRoomOpenRefreshFailure({
  required bool hasOptimisticDetail,
  required String requestedRoomId,
  required String? selectedRoomId,
}) {
  return hasOptimisticDetail &&
      canApplyRoomOpenResult(
        requestedRoomId: requestedRoomId,
        selectedRoomId: selectedRoomId,
      );
}

bool shouldFinishRoomOpenLoading({
  required String requestedRoomId,
  required String? selectedRoomId,
}) {
  return canApplyRoomOpenResult(
    requestedRoomId: requestedRoomId,
    selectedRoomId: selectedRoomId,
  );
}

bool canApplyLiveRefresh({
  required LiveState live,
  required String? selectedRoomId,
}) {
  return live.roomId == selectedRoomId;
}

RoomOpenSuccessEffects roomOpenSucceededEffects({
  required RoomOpenSnapshot snapshot,
  required bool joinLive,
}) {
  return RoomOpenSuccessEffects(
    joinRequestBadgeRoom: snapshot.detail,
    joinLiveSource: joinLive ? 'room_card_speaker' : null,
  );
}

class RoomsController {
  RoomsController({required this.api});

  final GangApi api;

  Future<List<RoomCard>> loadRooms() async {
    final page = await api.listRooms();
    return orderedRoomCards(page.rooms);
  }

  RoomListLoadPatch patchRoomListLoadStarted({required List<RoomCard> rooms}) {
    return RoomListLoadPatch(rooms: rooms, loading: true, error: null);
  }

  RoomListLoadPatch patchRoomListLoadSucceeded({
    required List<RoomCard> rooms,
  }) {
    return RoomListLoadPatch(
      rooms: orderedRoomCards(rooms),
      loading: false,
      error: null,
    );
  }

  RoomListLoadPatch patchRoomListLoadFailed({
    required List<RoomCard> rooms,
    required Object failure,
  }) {
    return RoomListLoadPatch(
      rooms: rooms,
      loading: false,
      error: userFacingErrorMessage(failure),
    );
  }

  Future<RoomOpenSnapshot> openRoom(String roomId) async {
    final detail = await api.getRoom(roomId);
    final messagePage = await api.listMessages(roomId: roomId);
    final live = await api.getLiveState(roomId);
    return RoomOpenSnapshot(
      detail: detail,
      messages: messagePage.messages,
      live: live,
    );
  }

  RoomOpenStatePatch patchRoomOpenStarted({
    required String roomId,
    required RoomDetail? currentSelectedRoom,
    required List<Message> currentMessages,
    required LiveState? currentLive,
    required bool currentLivePanelOpen,
    required bool joinLive,
    RoomDetail? optimisticDetail,
  }) {
    return RoomOpenStatePatch(
      settingsOpen: false,
      selectedRoomId: roomId,
      selectedRoom: optimisticDetail ?? currentSelectedRoom,
      loadingRoom: true,
      error: null,
      selectedRoomHasPendingJoinRequests: false,
      messages: optimisticDetail == null ? currentMessages : const [],
      live: optimisticDetail?.live ?? currentLive,
      livePanelOpen: joinLive ? currentLivePanelOpen : false,
    );
  }

  RoomOpenStatePatch patchRoomOpenSucceeded({
    required bool currentSettingsOpen,
    required bool currentLoadingRoom,
    required String? currentError,
    required bool currentSelectedRoomHasPendingJoinRequests,
    required RoomOpenSnapshot snapshot,
    required bool joinLive,
  }) {
    return RoomOpenStatePatch(
      settingsOpen: currentSettingsOpen,
      selectedRoomId: snapshot.detail.id,
      selectedRoom: snapshot.detail,
      loadingRoom: currentLoadingRoom,
      error: currentError,
      selectedRoomHasPendingJoinRequests:
          currentSelectedRoomHasPendingJoinRequests,
      messages: snapshot.messages,
      live: snapshot.live,
      livePanelOpen: joinLive,
    );
  }

  RoomOpenStatePatch patchRoomOpenFailed({
    required bool settingsOpen,
    required String? selectedRoomId,
    required RoomDetail? selectedRoom,
    required bool loadingRoom,
    required bool selectedRoomHasPendingJoinRequests,
    required List<Message> messages,
    required LiveState? live,
    required bool livePanelOpen,
    required Object failure,
  }) {
    return RoomOpenStatePatch(
      settingsOpen: settingsOpen,
      selectedRoomId: selectedRoomId,
      selectedRoom: selectedRoom,
      loadingRoom: loadingRoom,
      error: userFacingErrorMessage(failure),
      selectedRoomHasPendingJoinRequests: selectedRoomHasPendingJoinRequests,
      messages: messages,
      live: live,
      livePanelOpen: livePanelOpen,
    );
  }

  RoomOpenStatePatch patchRoomOpenFinished({
    required bool settingsOpen,
    required String? selectedRoomId,
    required RoomDetail? selectedRoom,
    required String? error,
    required bool selectedRoomHasPendingJoinRequests,
    required List<Message> messages,
    required LiveState? live,
    required bool livePanelOpen,
  }) {
    return RoomOpenStatePatch(
      settingsOpen: settingsOpen,
      selectedRoomId: selectedRoomId,
      selectedRoom: selectedRoom,
      loadingRoom: false,
      error: error,
      selectedRoomHasPendingJoinRequests: selectedRoomHasPendingJoinRequests,
      messages: messages,
      live: live,
      livePanelOpen: livePanelOpen,
    );
  }

  RoomMembersDialogStatePatch patchRoomMembersLoadStarted({
    required List<RoomMember> members,
    required List<JoinRequest> requests,
    required LiveState? live,
    required bool changed,
    required Iterable<String> busyRequestIds,
  }) {
    return RoomMembersDialogStatePatch(
      members: members,
      requests: requests,
      live: live,
      loading: true,
      changed: changed,
      error: null,
      requestError: null,
      busyRequestIds: busyRequestIds.toSet(),
    );
  }

  RoomMembersDialogStatePatch patchRoomMembersLoadSucceeded({
    required RoomMembersSnapshot snapshot,
    required bool changed,
    required Iterable<String> busyRequestIds,
  }) {
    return RoomMembersDialogStatePatch(
      members: snapshot.members,
      requests: snapshot.joinRequests,
      live: snapshot.live,
      loading: false,
      changed: changed,
      error: null,
      requestError: snapshot.joinRequestsError,
      busyRequestIds: busyRequestIds.toSet(),
    );
  }

  RoomMembersDialogStatePatch patchRoomMembersLoadFailed({
    required List<RoomMember> members,
    required List<JoinRequest> requests,
    required LiveState? live,
    required bool changed,
    required Iterable<String> busyRequestIds,
    required Object failure,
  }) {
    return RoomMembersDialogStatePatch(
      members: members,
      requests: requests,
      live: live,
      loading: false,
      changed: changed,
      error: userFacingErrorMessage(failure),
      requestError: null,
      busyRequestIds: busyRequestIds.toSet(),
    );
  }

  RoomMembersDialogStatePatch patchRoomMembersAndLiveReloadSucceeded({
    required RoomMembersLiveSnapshot snapshot,
    required List<JoinRequest> requests,
    required bool loading,
    required bool changed,
    required String? requestError,
    required Iterable<String> busyRequestIds,
  }) {
    return RoomMembersDialogStatePatch(
      members: snapshot.members,
      requests: requests,
      live: snapshot.live,
      loading: loading,
      changed: changed,
      error: null,
      requestError: requestError,
      busyRequestIds: busyRequestIds.toSet(),
    );
  }

  RoomMembersDialogStatePatch patchRoomMembersAndLiveReloadFailed({
    required List<RoomMember> members,
    required List<JoinRequest> requests,
    required LiveState? live,
    required bool loading,
    required bool changed,
    required String? requestError,
    required Iterable<String> busyRequestIds,
    required Object failure,
  }) {
    return RoomMembersDialogStatePatch(
      members: members,
      requests: requests,
      live: live,
      loading: loading,
      changed: changed,
      error: userFacingErrorMessage(failure),
      requestError: requestError,
      busyRequestIds: busyRequestIds.toSet(),
    );
  }

  RoomMembersDialogStatePatch patchRoomJoinRequestsReloadSucceeded({
    required List<RoomMember> members,
    required LiveState? live,
    required List<JoinRequest> requests,
    required bool loading,
    required bool changed,
    required Iterable<String> busyRequestIds,
  }) {
    return RoomMembersDialogStatePatch(
      members: members,
      requests: requests,
      live: live,
      loading: loading,
      changed: changed,
      error: null,
      requestError: null,
      busyRequestIds: busyRequestIds.toSet(),
    );
  }

  RoomMembersDialogStatePatch patchRoomJoinRequestsReloadFailed({
    required List<RoomMember> members,
    required List<JoinRequest> requests,
    required LiveState? live,
    required bool loading,
    required bool changed,
    required Iterable<String> busyRequestIds,
    required Object failure,
  }) {
    return RoomMembersDialogStatePatch(
      members: members,
      requests: requests,
      live: live,
      loading: loading,
      changed: changed,
      error: null,
      requestError: userFacingErrorMessage(failure),
      busyRequestIds: busyRequestIds.toSet(),
    );
  }

  RoomMembersDialogStatePatch patchJoinRequestReviewStarted({
    required List<RoomMember> members,
    required List<JoinRequest> requests,
    required LiveState? live,
    required bool loading,
    required bool changed,
    required String requestId,
    required Iterable<String> busyRequestIds,
  }) {
    final started = room_join_requests.joinRequestReviewStarted(
      requests: requests,
      requestId: requestId,
      busyRequestIds: busyRequestIds,
    );
    return RoomMembersDialogStatePatch(
      members: members,
      requests: started.requests,
      live: live,
      loading: loading,
      changed: changed,
      error: null,
      requestError: null,
      busyRequestIds: started.busyRequestIds,
    );
  }

  RoomMembersDialogStatePatch patchJoinRequestReviewSucceeded({
    required List<RoomMember> members,
    required List<JoinRequest> requests,
    required LiveState? live,
    required bool loading,
    required bool changed,
    required String? error,
    required String? requestError,
    required String requestId,
    required Iterable<String> busyRequestIds,
  }) {
    final succeeded = room_join_requests.joinRequestReviewSucceeded(
      requests: requests,
      requestId: requestId,
      busyRequestIds: busyRequestIds,
    );
    return RoomMembersDialogStatePatch(
      members: members,
      requests: succeeded.requests,
      live: live,
      loading: loading,
      changed: changed || succeeded.changed,
      error: error,
      requestError: requestError,
      busyRequestIds: succeeded.busyRequestIds,
    );
  }

  RoomMembersDialogStatePatch patchJoinRequestReviewFailed({
    required List<RoomMember> members,
    required List<JoinRequest> requests,
    required LiveState? live,
    required bool loading,
    required bool changed,
    required String? error,
    required String requestId,
    required Iterable<String> busyRequestIds,
    required Object failure,
  }) {
    final failed = room_join_requests.joinRequestReviewFailed(
      requests: requests,
      requestId: requestId,
      busyRequestIds: busyRequestIds,
    );
    return RoomMembersDialogStatePatch(
      members: members,
      requests: failed.requests,
      live: live,
      loading: loading,
      changed: changed,
      error: error,
      requestError: userFacingErrorMessage(failure),
      busyRequestIds: failed.busyRequestIds,
    );
  }

  Future<RoomDetail> getRoom(String roomId) {
    return api.getRoom(roomId);
  }

  Future<RoomMemberProfile> getRoomMemberProfile({
    required String roomId,
    required String userId,
  }) {
    return api.getRoomMemberProfile(roomId: roomId, userId: userId);
  }

  Future<UserSummary> getUserProfile(String userId) {
    return api.getUserProfile(userId);
  }

  Future<RoomDetail> createRoom({
    required String name,
    String? description,
    String? visibility,
    String? joinPolicy,
    bool? aiVoiceAnnouncementsEnabled,
    String? avatarAssetId,
    String? defaultAvatarKey,
  }) {
    return api.createRoom(
      name: name,
      description: description,
      visibility: visibility,
      joinPolicy: joinPolicy,
      aiVoiceAnnouncementsEnabled: aiVoiceAnnouncementsEnabled,
      avatarAssetId: avatarAssetId,
      defaultAvatarKey: defaultAvatarKey,
    );
  }

  Future<UploadedAsset> uploadImageAsset({
    required Uint8List bytes,
    required String filename,
    String purpose = 'avatar',
  }) {
    return api.uploadImageAsset(
      bytes: bytes,
      filename: filename,
      purpose: purpose,
    );
  }

  Future<RoomDetail> updateMyRoomSettings({
    required String roomId,
    String? remarkName,
    String? notificationPolicy,
    String? roomDisplayName,
    bool? isPinned,
    bool? aiVoiceAnnouncementsEnabled,
  }) {
    return api.updateMyRoomSettings(
      roomId: roomId,
      remarkName: remarkName,
      notificationPolicy: notificationPolicy,
      roomDisplayName: roomDisplayName,
      isPinned: isPinned,
      aiVoiceAnnouncementsEnabled: aiVoiceAnnouncementsEnabled,
    );
  }

  Future<void> leaveRoom({
    required String roomId,
    bool confirmDeleteIfEmpty = false,
  }) {
    return api.leaveRoom(
      roomId: roomId,
      confirmDeleteIfEmpty: confirmDeleteIfEmpty,
    );
  }

  Future<RoomDetail> updateRoom({
    required String roomId,
    String? name,
    String? description,
    String? visibility,
    String? joinPolicy,
    bool? aiVoiceAnnouncementsEnabled,
    String? avatarAssetId,
    String? defaultAvatarKey,
  }) {
    return api.updateRoom(
      roomId: roomId,
      name: name,
      description: description,
      visibility: visibility,
      joinPolicy: joinPolicy,
      aiVoiceAnnouncementsEnabled: aiVoiceAnnouncementsEnabled,
      avatarAssetId: avatarAssetId,
      defaultAvatarKey: defaultAvatarKey,
    );
  }

  Future<void> deleteRoom({
    required String roomId,
    required String confirmName,
  }) {
    return api.deleteRoom(roomId: roomId, confirmName: confirmName);
  }

  Future<RoomMember> updateRoomMemberRole({
    required String roomId,
    required String userId,
    required String role,
  }) {
    return api.updateRoomMemberRole(roomId: roomId, userId: userId, role: role);
  }

  Future<RoomMember> updateRoomMemberRoomDisplayName({
    required String roomId,
    required String userId,
    required String roomDisplayName,
  }) {
    return api.updateRoomMemberRoomDisplayName(
      roomId: roomId,
      userId: userId,
      roomDisplayName: roomDisplayName,
    );
  }

  Future<void> removeRoomMember({
    required String roomId,
    required String userId,
  }) {
    return api.removeRoomMember(roomId: roomId, userId: userId);
  }

  Future<RoomDetail> transferRoomCreator({
    required String roomId,
    required String userId,
  }) {
    return api.transferRoomCreator(roomId: roomId, userId: userId);
  }

  Future<List<StickerPack>> listRoomStickerPacks(String roomId) {
    return api.listStickerPacks(scope: 'room', roomId: roomId);
  }

  Future<StickerPack> createRoomStickerPack({
    required String roomId,
    required String name,
    int? sortOrder,
  }) {
    return api.createStickerPack(
      name: name,
      scope: 'room',
      roomId: roomId,
      sortOrder: sortOrder,
    );
  }

  Future<void> addRoomSticker({
    required String roomId,
    required String packId,
    required String assetId,
    required String name,
    int? sortOrder,
  }) {
    return api.addSticker(
      packId: packId,
      assetId: assetId,
      name: name,
      sortOrder: sortOrder,
      scope: 'room',
      roomId: roomId,
    );
  }

  Future<void> deleteRoomSticker({
    required String roomId,
    required String packId,
    required String stickerId,
  }) {
    return api.deleteSticker(
      packId: packId,
      stickerId: stickerId,
      scope: 'room',
      roomId: roomId,
    );
  }

  Future<Sticker> updateSticker({
    required String packId,
    required String stickerId,
    String? name,
    int? sortOrder,
  }) {
    return api.updateSticker(
      packId: packId,
      stickerId: stickerId,
      name: name,
      sortOrder: sortOrder,
    );
  }

  Future<StickerPack> reorderStickers({
    required String packId,
    required List<String> stickerIds,
  }) {
    return api.reorderStickers(packId: packId, stickerIds: stickerIds);
  }

  Future<DownloadedFile> downloadStickers({required List<String> stickerIds}) {
    return api.downloadStickers(stickerIds: stickerIds);
  }

  Future<List<PublicRoom>> searchRooms({required String query}) {
    return api.searchRooms(query: query);
  }

  Future<JoinRoomResult> joinRoom(String roomId, {String? reason}) {
    return api.joinRoom(roomId, reason: reason);
  }

  Future<List<RoomInvite>> listRoomInvites({String status = 'pending'}) {
    return api.listRoomInvites(status: status);
  }

  Future<List<RoomApplication>> listRoomApplications({
    String status = 'pending',
  }) {
    return api.listRoomApplications(status: status);
  }

  Future<List<RoomEventNotification>> listRoomNotifications() {
    return api.listRoomNotifications();
  }

  Future<void> deleteRoomNotification({
    required String notificationType,
    required String notificationId,
  }) {
    return api.deleteRoomNotification(
      notificationType: notificationType,
      notificationId: notificationId,
    );
  }

  Future<void> markRoomNotificationsRead() {
    return api.markRoomNotificationsRead();
  }

  Future<RoomMemberPage> listRoomMembers(
    String roomId, {
    int limit = 100,
    String? cursor,
  }) {
    return api.listRoomMembers(roomId, limit: limit, cursor: cursor);
  }

  Future<List<RoomMember>> loadAllRoomMembers(
    String roomId, {
    int pageLimit = 100,
    int maxPages = 50,
  }) async {
    final members = <RoomMember>[];
    String? cursor;
    var pageCount = 0;
    do {
      final page = await api.listRoomMembers(
        roomId,
        limit: pageLimit,
        cursor: cursor,
      );
      members.addAll(page.members);
      cursor = _nonEmpty(page.nextCursor);
      pageCount += 1;
    } while (cursor != null && pageCount < maxPages);
    return members;
  }

  Future<RoomMembersLiveSnapshot> loadRoomMembersAndLive({
    required String roomId,
    required LiveState fallbackLive,
  }) async {
    final members = await loadAllRoomMembers(roomId);
    final live = await getLiveStateOr(roomId, fallbackLive);
    return RoomMembersLiveSnapshot(members: members, live: live);
  }

  Future<RoomMembersSnapshot> loadRoomMembersSnapshot({
    required String roomId,
    required LiveState fallbackLive,
    required bool includeJoinRequests,
  }) async {
    final membersAndLive = await loadRoomMembersAndLive(
      roomId: roomId,
      fallbackLive: fallbackLive,
    );

    List<JoinRequest> joinRequests = const [];
    String? joinRequestsError;
    if (includeJoinRequests) {
      try {
        joinRequests = await listJoinRequests(roomId);
      } catch (e) {
        joinRequestsError = e.toString();
      }
    }

    return RoomMembersSnapshot(
      members: membersAndLive.members,
      live: membersAndLive.live,
      joinRequests: joinRequests,
      joinRequestsError: joinRequestsError,
    );
  }

  Future<RoomInvite> inviteMember({
    required String roomId,
    required String userId,
  }) {
    return api.inviteMember(roomId: roomId, userId: userId);
  }

  Future<List<RoomBlacklistEntry>> listRoomBlacklist(String roomId) {
    return api.listRoomBlacklist(roomId);
  }

  Future<RoomBlacklistEntry> blockRoomUser({
    required String roomId,
    required String userId,
  }) {
    return api.blockRoomUser(roomId: roomId, userId: userId);
  }

  Future<void> unblockRoomUser({
    required String roomId,
    required String userId,
  }) {
    return api.unblockRoomUser(roomId: roomId, userId: userId);
  }

  Future<List<UserSummary>> searchUsers({
    required String query,
    int limit = 20,
  }) {
    return api.searchUsers(query: query, limit: limit);
  }

  Future<List<JoinRequest>> listJoinRequests(
    String roomId, {
    String status = 'pending',
  }) {
    return api.listJoinRequests(roomId, status: status);
  }

  Future<JoinRoomResult> reviewRoomInvite({
    required String inviteId,
    required bool accept,
    String? reason,
  }) {
    return api.reviewRoomInvite(
      inviteId: inviteId,
      accept: accept,
      reason: reason,
    );
  }

  Future<RoomApplication> withdrawRoomApplication({required String requestId}) {
    return api.withdrawRoomApplication(requestId: requestId);
  }

  Future<void> reviewJoinRequest({
    required String roomId,
    required String requestId,
    required bool approve,
  }) {
    return api.reviewJoinRequest(
      roomId: roomId,
      requestId: requestId,
      approve: approve,
    );
  }

  Future<bool> hasPendingRoomInvites() async {
    final invites = await api.listRoomInvites();
    return invites.isNotEmpty;
  }

  Future<bool> hasPendingJoinRequests(
    RoomDetail room, {
    required bool canReviewJoinRequests,
  }) async {
    if (!canReviewJoinRequests) return false;
    final requests = await api.listJoinRequests(room.id);
    return requests.isNotEmpty;
  }

  Future<LiveState> getLiveState(String roomId) {
    return api.getLiveState(roomId);
  }

  Future<LiveState> getLiveStateOr(
    String roomId,
    LiveState fallbackLive,
  ) async {
    try {
      return await api.getLiveState(roomId);
    } catch (_) {
      return fallbackLive;
    }
  }

  RoomCard? roomCardFromSnapshot(Map<String, dynamic> data) {
    if (data['id'] is! String) return null;
    try {
      return RoomCard.fromJson(data);
    } catch (_) {
      return null;
    }
  }

  List<RoomCard> upsertRoomCard(List<RoomCard> rooms, RoomCard room) {
    final idx = rooms.indexWhere((item) => item.id == room.id);
    if (idx < 0) return orderedRoomCards([room, ...rooms]);
    final next = [...rooms];
    next[idx] = room;
    return orderedRoomCards(next);
  }

  List<RoomCard> orderedRoomCards(List<RoomCard> rooms) {
    final indexed = [
      for (var i = 0; i < rooms.length; i++) (index: i, room: rooms[i]),
    ];
    indexed.sort((a, b) {
      final pinned = _roomPinnedRank(a.room).compareTo(_roomPinnedRank(b.room));
      if (pinned != 0) return pinned;

      final unread = _roomUnreadRank(a.room).compareTo(_roomUnreadRank(b.room));
      if (unread != 0) return unread;

      final live = _roomLiveRank(a.room).compareTo(_roomLiveRank(b.room));
      if (live != 0) return live;

      final activity = _roomActivityTime(
        b.room,
      ).compareTo(_roomActivityTime(a.room));
      if (activity != 0) return activity;

      return a.index.compareTo(b.index);
    });
    return [for (final item in indexed) item.room];
  }

  int _roomPinnedRank(RoomCard room) {
    return room.isPinned ? 0 : 1;
  }

  int _roomUnreadRank(RoomCard room) {
    if (room.unreadCount <= 0) return 2;
    final policy = room_display.normalizeRoomNotificationPolicy(
      room.notificationPolicy,
    );
    if (policy == 'silent') return 1;
    if (policy == 'blocked') return 2;
    return 0;
  }

  int _roomLiveRank(RoomCard room) {
    return room.liveParticipantCount > 0 ? 0 : 1;
  }

  DateTime _roomActivityTime(RoomCard room) {
    if (room_display.normalizeRoomNotificationPolicy(room.notificationPolicy) ==
        'blocked') {
      return room.updatedAt;
    }
    return room.lastMessage?.createdAt ?? room.updatedAt;
  }

  RoomCardsPatch patchRoomCardsRefreshed({required List<RoomCard> rooms}) {
    return RoomCardsPatch(rooms: orderedRoomCards(rooms));
  }

  RoomCardsPatch patchRoomCardUpserted({
    required List<RoomCard> rooms,
    required RoomCard room,
  }) {
    return RoomCardsPatch(rooms: upsertRoomCard(rooms, room));
  }

  RoomCardsPatch patchRoomCardUpdated({
    required List<RoomCard> rooms,
    required RoomCard incoming,
    bool clearUnread = false,
  }) {
    return RoomCardsPatch(
      rooms: mergeRoomUpdated(rooms, incoming, clearUnread: clearUnread),
    );
  }

  RoomUpdatedPatch patchRoomUpdated({
    required List<RoomCard> rooms,
    required RoomCard incoming,
    required RoomDetail? selectedRoom,
  }) {
    final nextRooms = patchRoomCardUpdated(
      rooms: rooms,
      incoming: incoming,
    ).rooms;
    final current = selectedRoom;
    if (current == null || current.id != incoming.id) {
      return RoomUpdatedPatch(
        rooms: nextRooms,
        selectedRoom: current,
        shouldReloadMembers: false,
      );
    }
    return RoomUpdatedPatch(
      rooms: nextRooms,
      selectedRoom: _mergeSelectedRoomSnapshot(current, incoming),
      shouldReloadMembers:
          current.memberCount != incoming.memberCount ||
          current.onlineMemberCount != incoming.onlineMemberCount,
    );
  }

  RoomSelectedDetailPatch patchRoomDetailApplied({
    required List<RoomCard> rooms,
    required RoomDetail detail,
  }) {
    final existingIndex = rooms.indexWhere((room) => room.id == detail.id);
    final existing = existingIndex < 0 ? null : rooms[existingIndex];
    final card = detail.toCard();
    return RoomSelectedDetailPatch(
      selectedRoom: detail,
      rooms: upsertRoomCard(
        rooms,
        existing == null
            ? card
            : RoomCard(
                id: card.id,
                name: card.name,
                rid: card.rid,
                visibility: card.visibility,
                remarkName: card.remarkName,
                description: card.description,
                notificationPolicy: card.notificationPolicy,
                isPinned: card.isPinned,
                avatarUrl: card.avatarUrl,
                defaultAvatarKey: card.defaultAvatarKey,
                memberCount: card.memberCount,
                onlineMemberCount: card.onlineMemberCount,
                liveParticipantCount: card.liveParticipantCount,
                liveAvatarPreview: card.liveAvatarPreview,
                lastMessage: existing.lastMessage,
                unreadCount: existing.unreadCount,
                hasUnreadCount: existing.hasUnreadCount,
                unreadMentionCount: existing.unreadMentionCount,
                hasUnreadMentionCount: existing.hasUnreadMentionCount,
                hasPendingJoinRequests: existing.hasPendingJoinRequests,
                aiVoiceAnnouncementsEnabled: card.aiVoiceAnnouncementsEnabled,
                updatedAt: card.updatedAt,
              ),
      ),
    );
  }

  RoomCardsPatch patchRoomUnreadCleared({
    required List<RoomCard> rooms,
    required String roomId,
  }) {
    final idx = rooms.indexWhere((room) => room.id == roomId);
    if (idx < 0 ||
        (rooms[idx].unreadCount == 0 && rooms[idx].unreadMentionCount == 0)) {
      return RoomCardsPatch(rooms: rooms);
    }
    final next = [...rooms];
    next[idx] = next[idx].copyWith(unreadCount: 0, unreadMentionCount: 0);
    return RoomCardsPatch(rooms: orderedRoomCards(next));
  }

  RoomSelectedDetailPatch? patchSelectedRoomDetailRefreshed({
    required List<RoomCard> rooms,
    required String? selectedRoomId,
    required RoomDetail detail,
  }) {
    if (selectedRoomId != detail.id) return null;
    return patchRoomDetailApplied(rooms: rooms, detail: detail);
  }

  RoomLiveRefreshPatch? patchSelectedLiveRefreshed({
    required LiveState live,
    required String? selectedRoomId,
    LiveState? currentLive,
  }) {
    if (!canApplyLiveRefresh(live: live, selectedRoomId: selectedRoomId)) {
      return null;
    }
    if (_isOlderLiveSnapshot(live, currentLive)) return null;
    return RoomLiveRefreshPatch(live: live);
  }

  List<RoomCard> removeRoomCard(List<RoomCard> rooms, String roomId) {
    return rooms.where((room) => room.id != roomId).toList();
  }

  RoomDeletedPatch? patchRoomDeleted({
    required List<RoomCard> rooms,
    required String? selectedRoomId,
    required RoomDetail? selectedRoom,
    required bool selectedRoomHasPendingJoinRequests,
    required List<Message> messages,
    required LiveState? live,
    required bool livePanelOpen,
    required bool settingsOpen,
    required String? joinedLiveRoomId,
    required Map<String, dynamic> data,
  }) {
    final roomId = data['room_id'] as String?;
    if (roomId == null) return null;

    final wasSelected = selectedRoomId == roomId;
    final shouldDisconnectLive = joinedLiveRoomId == roomId;
    return RoomDeletedPatch(
      roomId: roomId,
      rooms: removeRoomCard(rooms, roomId),
      selectedRoomId: wasSelected ? null : selectedRoomId,
      selectedRoom: wasSelected ? null : selectedRoom,
      selectedRoomHasPendingJoinRequests: wasSelected
          ? false
          : selectedRoomHasPendingJoinRequests,
      messages: wasSelected ? const [] : messages,
      live: wasSelected ? null : live,
      livePanelOpen: wasSelected ? false : livePanelOpen,
      settingsOpen: wasSelected ? false : settingsOpen,
      joinedLiveRoomId: shouldDisconnectLive ? null : joinedLiveRoomId,
      wasSelected: wasSelected,
      shouldDisconnectLive: shouldDisconnectLive,
    );
  }

  RoomRoleChangedPatch? patchRoomRoleChanged({
    required RoomDetail? selectedRoom,
    required Map<String, dynamic> data,
  }) {
    final roomId = data['room_id'] as String?;
    final role = data['role'] as String?;
    final current = selectedRoom;
    if (roomId == null || role == null || current == null) return null;
    if (current.id != roomId) return null;
    return RoomRoleChangedPatch(
      roomId: roomId,
      selectedRoom: current.copyWithRole(role),
    );
  }

  List<RoomInvite> removeRoomInvite(List<RoomInvite> invites, String inviteId) {
    return invites.where((invite) => invite.id != inviteId).toList();
  }

  List<JoinRequest> removeJoinRequest(
    List<JoinRequest> requests,
    String requestId,
  ) {
    return requests.where((request) => request.id != requestId).toList();
  }

  List<RoomCard> mergeRoomUpdated(
    List<RoomCard> rooms,
    RoomCard incoming, {
    bool clearUnread = false,
  }) {
    final idx = rooms.indexWhere((room) => room.id == incoming.id);
    if (idx < 0) return upsertRoomCard(rooms, incoming);

    final existing = rooms[idx];
    final next = [...rooms];
    // Public room snapshots do not carry local per-user fields, so keep the
    // local unread count, but bump it when a fresh last_message arrives for a
    // room the user is not currently reading.
    final unreadCount = clearUnread
        ? 0
        : _mergedUnreadCount(existing: existing, incoming: incoming);
    final unreadMentionCount = clearUnread
        ? 0
        : _mergedUnreadMentionCount(existing: existing, incoming: incoming);
    next[idx] = _mergeRoomCard(
      existing,
      incoming,
      unreadCount: unreadCount,
      unreadMentionCount: unreadMentionCount,
    );
    return orderedRoomCards(next);
  }

  int _mergedUnreadCount({
    required RoomCard existing,
    required RoomCard incoming,
  }) {
    if (incoming.hasUnreadCount) return incoming.unreadCount;
    final incomingLastMessage = incoming.lastMessage;
    if (incomingLastMessage == null) return existing.unreadCount;
    if (room_display.normalizeRoomNotificationPolicy(
          existing.notificationPolicy,
        ) ==
        'blocked') {
      return 0;
    }
    if (existing.lastMessage?.id == incomingLastMessage.id) {
      return existing.unreadCount;
    }
    return existing.unreadCount + 1;
  }

  int _mergedUnreadMentionCount({
    required RoomCard existing,
    required RoomCard incoming,
  }) {
    if (incoming.hasUnreadMentionCount) return incoming.unreadMentionCount;
    return existing.unreadMentionCount;
  }

  RoomCard _mergeRoomCard(
    RoomCard existing,
    RoomCard incoming, {
    required int unreadCount,
    required int unreadMentionCount,
  }) {
    return RoomCard(
      id: incoming.id,
      name: incoming.name,
      rid: incoming.rid,
      visibility: incoming.visibility,
      remarkName: existing.remarkName,
      description: incoming.description,
      notificationPolicy: existing.notificationPolicy,
      isPinned: existing.isPinned,
      avatarUrl: incoming.avatarUrl,
      defaultAvatarKey: incoming.defaultAvatarKey,
      memberCount: incoming.memberCount,
      onlineMemberCount: incoming.onlineMemberCount,
      liveParticipantCount: incoming.liveParticipantCount,
      liveAvatarPreview: incoming.liveAvatarPreview,
      lastMessage: incoming.lastMessage,
      unreadCount: unreadCount,
      hasUnreadCount: true,
      unreadMentionCount: unreadMentionCount,
      hasUnreadMentionCount: true,
      hasPendingJoinRequests: incoming.hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled: incoming.aiVoiceAnnouncementsEnabled,
      updatedAt: incoming.updatedAt,
    );
  }

  RoomDetail _mergeSelectedRoomSnapshot(RoomDetail room, RoomCard incoming) {
    return RoomDetail(
      id: room.id,
      name: incoming.name,
      rid: incoming.rid,
      visibility: incoming.visibility,
      description: incoming.description,
      joinPolicy: room.joinPolicy,
      remarkName: room.remarkName,
      notificationPolicy: room.notificationPolicy,
      isPinned: room.isPinned,
      personalProfile: room.personalProfile,
      aiVoiceAnnouncementsEnabled: incoming.aiVoiceAnnouncementsEnabled,
      messageRecallPolicy: room.messageRecallPolicy,
      messageRecallWindowSeconds: room.messageRecallWindowSeconds,
      canDeleteRoom: room.canDeleteRoom,
      avatarUrl: incoming.avatarUrl,
      defaultAvatarKey: incoming.defaultAvatarKey,
      memberCount: incoming.memberCount,
      onlineMemberCount: incoming.onlineMemberCount,
      createdBy: room.createdBy,
      myMembership: room.myMembership,
      live: room.live,
      createdAt: room.createdAt,
      updatedAt: incoming.updatedAt,
    );
  }

  RoomLiveSnapshotPatch? patchLiveSnapshot({
    required List<RoomCard> rooms,
    required String? selectedRoomId,
    required Map<String, dynamic> data,
    String? joinedLiveRoomId,
    String? currentUserId,
    LiveState? previousLive,
    bool localParticipantConnected = false,
  }) {
    final roomId = data['room_id'] as String?;
    if (roomId == null) return null;

    final liveJson = data['live'] as Map<String, dynamic>?;
    final count = data['participant_count'] as int?;
    final previewJson = data['preview'] as List?;
    final preview = previewJson
        ?.cast<Map<String, dynamic>>()
        .map(UserSummary.fromJson)
        .toList();
    final parsedSelectedLive = selectedRoomId == roomId && liveJson != null
        ? LiveState.fromJson(liveJson)
        : null;
    final staleSelectedLive =
        parsedSelectedLive != null &&
        _isOlderLiveSnapshot(parsedSelectedLive, previousLive);

    var nextRooms = rooms;
    final idx = rooms.indexWhere((room) => room.id == roomId);
    if (idx >= 0 && count != null) {
      final existing = rooms[idx];
      final effectiveCount = staleSelectedLive
          ? previousLive!.participantCount
          : count;
      nextRooms = [...rooms];
      nextRooms[idx] = RoomCard(
        id: existing.id,
        name: existing.name,
        rid: existing.rid,
        visibility: existing.visibility,
        remarkName: existing.remarkName,
        description: existing.description,
        notificationPolicy: existing.notificationPolicy,
        isPinned: existing.isPinned,
        avatarUrl: existing.avatarUrl,
        defaultAvatarKey: existing.defaultAvatarKey,
        memberCount: existing.memberCount,
        onlineMemberCount: existing.onlineMemberCount,
        liveParticipantCount: effectiveCount,
        liveAvatarPreview: staleSelectedLive
            ? existing.liveAvatarPreview
            : preview ?? existing.liveAvatarPreview,
        lastMessage: existing.lastMessage,
        unreadCount: existing.unreadCount,
        hasUnreadCount: existing.hasUnreadCount,
        unreadMentionCount: existing.unreadMentionCount,
        hasUnreadMentionCount: existing.hasUnreadMentionCount,
        hasPendingJoinRequests: existing.hasPendingJoinRequests,
        aiVoiceAnnouncementsEnabled: existing.aiVoiceAnnouncementsEnabled,
        updatedAt: existing.updatedAt,
      );
      nextRooms = orderedRoomCards(nextRooms);
    }

    var selectedLive = staleSelectedLive ? null : parsedSelectedLive;
    if (selectedLive != null && previousLive?.roomId == roomId) {
      selectedLive = _mergeLiveParticipantUsers(
        selectedLive,
        previousLive: previousLive!,
      );
    }

    // Self-preservation: a snapshot computed server-side before our own join
    // (SSE delivery and the LiveKit webhook can lag/reorder) can arrive right
    // after we join and drop us from the roster — making us briefly invisible
    // in our own channel. While we're joined to this room, if the snapshot
    // omits us but our prior state had us, re-insert our own participant from
    // that prior state. A later, correct snapshot reconciles.
    if (selectedLive != null &&
        joinedLiveRoomId == roomId &&
        currentUserId != null &&
        localParticipantConnected) {
      final present = selectedLive.participants.any(
        (p) => p.user.id == currentUserId,
      );
      if (!present) {
        LiveParticipant? self;
        if (previousLive?.roomId == roomId) {
          for (final p in previousLive!.participants) {
            if (p.user.id == currentUserId) {
              self = p;
              break;
            }
          }
        }
        if (self != null) {
          selectedLive = LiveState(
            roomId: selectedLive.roomId,
            participantCount: selectedLive.participantCount + 1,
            participants: [...selectedLive.participants, self],
            updatedAt: selectedLive.updatedAt,
          );
        }
      }
    }

    // The selected-room roster may have been reconciled above (most notably
    // by retaining a locally connected participant while an older server
    // snapshot is in flight). Derive the room-card count and preview from that
    // exact same snapshot so the outside and inside views cannot diverge.
    if (selectedLive != null) {
      nextRooms = room_live_state.patchRoomLiveCount(
        rooms: nextRooms,
        roomId: roomId,
        live: selectedLive,
      );
    }

    return RoomLiveSnapshotPatch(
      roomId: roomId,
      rooms: nextRooms,
      selectedLive: selectedLive,
    );
  }

  bool _isOlderLiveSnapshot(LiveState incoming, LiveState? current) {
    return current?.roomId == incoming.roomId &&
        incoming.updatedAt.isBefore(current!.updatedAt);
  }

  LiveState _mergeLiveParticipantUsers(
    LiveState live, {
    required LiveState previousLive,
  }) {
    final previousByUserId = {
      for (final participant in previousLive.participants)
        participant.user.id: participant,
    };
    var changed = false;
    final participants = [
      for (final participant in live.participants)
        if (previousByUserId[participant.user.id] case final previous?)
          _mergeLiveParticipantUser(
            participant,
            previous,
            changed: () {
              changed = true;
            },
          )
        else
          participant,
    ];
    if (!changed) return live;
    return LiveState(
      roomId: live.roomId,
      participantCount: live.participantCount,
      participants: participants,
      updatedAt: live.updatedAt,
    );
  }

  LiveParticipant _mergeLiveParticipantUser(
    LiveParticipant participant,
    LiveParticipant previous, {
    required void Function() changed,
  }) {
    final mergedUser = participant.user.mergeMissing(previous.user);
    if (identical(mergedUser, participant.user)) return participant;
    changed();
    return participant.copyWith(user: mergedUser);
  }

  List<RoomCard> patchRoomLiveCount(
    List<RoomCard> rooms,
    String roomId,
    LiveState live,
  ) {
    return room_live_state.patchRoomLiveCount(
      rooms: rooms,
      roomId: roomId,
      live: live,
    );
  }

  RoomCard withLive(RoomCard room, LiveState live) {
    return room_live_state.roomCardWithLive(room, live);
  }
}

String? _nonEmpty(String? value) {
  final text = value?.trim();
  if (text == null || text.isEmpty) return null;
  return text;
}
