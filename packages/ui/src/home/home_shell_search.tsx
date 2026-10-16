import type { ReactNode } from 'react';

import * as room_display from '@gang-chat/core/app/room_display';
import * as room_join from '@gang-chat/core/app/room_join';
import * as search_display from '@gang-chat/core/app/search_display';
import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import { SettingsSection } from '@gang-chat/core/app/settings_shell_state';
import { Timer, unawaited } from '@gang-chat/core/lib/async';
import {
  GlobalSearchResults,
  MessageSearchResult,
  PublicRoom,
  RoomCard,
  RoomDetail,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { showDialog } from '../ui/overlay';
import type { HomeShellState } from './home_shell';
import { _ContentMode } from './home_shell';
import { _JoinApplicationDialog } from './home_shell_join_dialog';
import { _SearchTapRegion, _TitleSearchField, _TitleSearchResultsPanel } from './home_shell_title_bar';
import { HoverCardTapRegionScope } from './hover_card_anchor';

/**
 * Port of `extension _HomeShellSearch on _HomeShellState`.
 *
 * PORT: the Dart getters (`_hasSearchQuery`, `_filteringSidebarBySearch`,
 * `_sidebarServers`, `_loadingSidebarSearch`, `_visibleSearchCategories`) are
 * methods here: the table is merged onto the prototype with `Object.assign`,
 * which would evaluate a getter once against the table instead of copying it.
 */
export const HomeShellSearch = {
  _hasSearchQuery(this: HomeShellState): boolean {
    return search_display.hasGlobalSearchQuery(this._searchQuery);
  },

  _buildCompactSearchField(this: HomeShellState): ReactNode {
    return (
      <_SearchTapRegion
        groupId={this._searchTapRegionGroup}
        onTapOutside={() => this._collapseSearch()}
        data-testid="home-title-search"
        style={{ width: '100%', height: '100%', minWidth: 0 }}
      >
        <_TitleSearchField
          controller={this._titleSearchController}
          tapRegionGroup={this._searchTapRegionGroup}
          enabled={!this._appUpdateDownloadInProgress}
          onContextMenuOpenChanged={(open) => this._handleTitleSearchContextMenuOpenChanged(open)}
          onActivated={() => this._activateSearch()}
          onClearQuery={() => this._clearSearchQuery()}
        />
      </_SearchTapRegion>
    );
  },

  _buildSearchResultsTapRegion(this: HomeShellState): ReactNode {
    return (
      <_SearchTapRegion key="home-title-search-results" data-testid="home-title-search-results" groupId={this._searchTapRegionGroup}>
        <HoverCardTapRegionScope tapRegionGroup={this._searchTapRegionGroup}>
          <_TitleSearchResultsPanel
            query={this._searchQuery}
            results={this._searchResults}
            loading={this._searching}
            loadingMore={this._searchLoadingMore}
            error={this._searchError}
            timestampNow={this._serverNow}
            currentUser={this.widget.app.currentUser}
            activeCategory={this._activeSearchCategory}
            visibleCategories={this._visibleSearchCategories()}
            busyPublicRoomId={this._busySearchPublicRoomId}
            pendingPublicRoomIds={this._searchPendingPublicRoomIds}
            onCategorySelected={(category) => this._selectSearchCategory(category)}
            onLoadMore={() => unawaited(this._loadMoreSearchResults())}
            onMyRoomSelected={(room) => this._openSearchRoom(room)}
            onProfileRoomSelected={(room) => this._openSearchProfileRoom(room)}
            onResolveRoomProfile={(room) => this._resolveRoomProfile(room)}
            onResolveRoomUserProfile={(roomId, user) => this._resolveRoomUserProfile(roomId, user)}
            onResolveUserProfile={(user) => this._resolveUserProfile(user)}
            onPublicRoomAction={(room) => unawaited(this._handlePublicRoomSearchAction(room))}
            onUserSettingsSelected={(user) => this._openSuperuserUserSettings(user)}
            onMessageSelected={(result) => this._openMessageSearchResult(result)}
            onFileSelected={(result) => this._openMessageSearchResult(result)}
          />
        </HoverCardTapRegionScope>
      </_SearchTapRegion>
    );
  },

  _filteringSidebarBySearch(this: HomeShellState): boolean {
    return this._hasSearchQuery() && this._activeSearchCategory === search_display.GlobalSearchCategory.myRooms;
  },

  _sidebarServers(this: HomeShellState): RoomCard[] {
    return search_display.sidebarRoomsForSearch({
      rooms: this._servers,
      query: this._searchQuery,
      activeCategory: this._activeSearchCategory,
      results: this._searchResults,
    });
  },

  _loadingSidebarSearch(this: HomeShellState): boolean {
    return this._filteringSidebarBySearch() && this._searching && this._searchResults == null;
  },

  _visibleSearchCategories(this: HomeShellState): readonly search_display.GlobalSearchCategory[] {
    const activeCategory = this._activeSearchCategory;
    return activeCategory == null
      ? this._currentUser.isSuperuser
        ? search_display.superuserGlobalSearchCategories
        : search_display.globalSearchCategories
      : [activeCategory];
  },

  _handleTitleSearchChanged(this: HomeShellState): void {
    const query = this._titleSearchController.text;
    if (query === this._lastTitleSearchText) return;
    this._lastTitleSearchText = query;
    this._pendingTitleSearchContextMenuUpdate = null;
    this._searchDebounce?.cancel();
    this._searchRequestSerial++;
    const requestSerial = this._searchRequestSerial;
    const hasQuery = search_display.hasGlobalSearchQuery(query);

    this._setHomeState(() => {
      this._searchQuery = query;
      this._searchExpanded = hasQuery;
      this._searchError = null;
      this._searchLoadingMore = false;
      if (hasQuery) {
        this._searching = true;
        this._searchResults = null;
      } else {
        this._searching = false;
        this._searchResults = null;
      }
    });

    if (!hasQuery) return;
    this._searchDebounce = new Timer(260, () => {
      unawaited(this._runSearch(requestSerial, query));
    });
  },

  async _runSearch(this: HomeShellState, requestSerial: number, rawQuery: string): Promise<void> {
    const query = rawQuery.trim();
    if (!search_display.hasGlobalSearchQuery(query)) return;

    try {
      const results = await this._globalSearchController.search({
        query,
        categories: this._visibleSearchCategories(),
      });
      if (!this.mounted || requestSerial !== this._searchRequestSerial) return;
      if (this._titleSearchController.text.trim() !== query) return;
      this._setTitleSearchResultsState(() => {
        this._searchResults = search_display.globalSearchResultsForView(results, { query });
        this._searching = false;
        this._searchLoadingMore = false;
        this._searchError = null;
      });
    } catch (error) {
      if (!this.mounted || requestSerial !== this._searchRequestSerial) return;
      this._setTitleSearchResultsState(() => {
        this._searching = false;
        this._searchLoadingMore = false;
        this._searchError = userFacingErrorMessage(error, { fallback: '搜索失败' });
      });
    }
  },

  async _loadMoreSearchResults(this: HomeShellState): Promise<void> {
    const current = this._searchResults;
    const query = this._searchQuery.trim();
    if (this._searchLoadingMore || this._searching || current == null) return;
    if (!search_display.hasGlobalSearchQuery(query)) return;

    const categories = this._visibleSearchCategories().filter(
      (category) => search_display.globalSearchCursorForCategory(current.nextCursors, category) != null,
    );
    if (categories.length === 0) return;

    const requestSerial = this._searchRequestSerial;
    const cursors = search_display.globalSearchCursorsForCategories(current.nextCursors, categories);
    this._setTitleSearchResultsState(() => {
      this._searchLoadingMore = true;
    });

    try {
      const page = await this._globalSearchController.search({
        query,
        categories,
        myRoomsCursor: cursors.myRooms,
        publicRoomsCursor: cursors.publicRooms,
        userSettingsCursor: cursors.userSettings,
        messagesCursor: cursors.messages,
        filesCursor: cursors.files,
      });
      if (!this.mounted || requestSerial !== this._searchRequestSerial) return;
      if (this._titleSearchController.text.trim() !== query) return;

      const visiblePage = search_display.globalSearchResultsForView(page, { query });
      this._setTitleSearchResultsState(() => {
        const latest = this._searchResults;
        this._searchLoadingMore = false;
        if (latest == null) return;
        this._searchResults = search_display.globalSearchResultsByAppendingPage({
          current: latest,
          page: visiblePage,
          categories,
        });
      });
    } catch {
      if (!this.mounted || requestSerial !== this._searchRequestSerial) return;
      this._setTitleSearchResultsState(() => {
        this._searchLoadingMore = false;
      });
    }
  },

  _handleTitleSearchContextMenuOpenChanged(this: HomeShellState, open: boolean): void {
    this._titleSearchContextMenuOpen = open;
    if (open) return;
    const pending = this._pendingTitleSearchContextMenuUpdate;
    this._pendingTitleSearchContextMenuUpdate = null;
    if (pending == null || !this.mounted) return;
    this._setHomeState(pending);
  },

  _setTitleSearchResultsState(this: HomeShellState, update: () => void): void {
    if (!this._titleSearchContextMenuOpen) {
      this._setHomeState(update);
      return;
    }
    const pending = this._pendingTitleSearchContextMenuUpdate;
    this._pendingTitleSearchContextMenuUpdate = () => {
      pending?.();
      update();
    };
  },

  _activateSearch(this: HomeShellState): void {
    if (this._searchExpanded) return;
    this._setHomeState(() => {
      this._searchExpanded = true;
    });
  },

  _collapseSearch(this: HomeShellState): void {
    if (!this._searchExpanded) return;
    this._setHomeState(() => {
      this._searchExpanded = false;
    });
  },

  _selectSearchCategory(this: HomeShellState, category: search_display.GlobalSearchCategory): void {
    this._setHomeState(() => {
      this._activeSearchCategory = this._activeSearchCategory === category ? null : category;
    });
  },

  _clearSearchQuery(this: HomeShellState): void {
    this._pendingTitleSearchContextMenuUpdate = null;
    this._titleSearchController.clear();
  },

  _openSearchRoom(this: HomeShellState, room: RoomCard): void {
    this._selectServer(room, { openContent: this._compactLayout });
  },

  _openSearchProfileRoom(this: HomeShellState, room: PublicRoom): void {
    this._openSearchRoom(this._roomCardForPublicRoom(room));
  },

  _openSuperuserUserSettings(this: HomeShellState, user: UserSummary): void {
    if (!this._currentUser.isSuperuser) return;
    if (user.id === this._currentUser.id) {
      this._setHomeState(() => {
        this._superuserSettingsTarget = null;
        this._settingsOpen = true;
        this._settingsInitialSection = SettingsSection.profile;
        this._settingsInitialMusicPlaylistId = null;
        this._settingsAppUpdate = null;
        this._contentMode = _ContentMode.chat;
        this._narrowContentOpen = true;
        this._searchExpanded = false;
      });
      return;
    }
    this._setHomeState(() => {
      this._superuserSettingsTarget = user;
      this._settingsOpen = false;
      this._contentMode = _ContentMode.superuserUserSettings;
      this._narrowContentOpen = true;
      this._searchExpanded = false;
    });
  },

  _closeSuperuserUserSettings(this: HomeShellState): void {
    this._setHomeState(() => {
      this._superuserSettingsTarget = null;
      this._contentMode = _ContentMode.chat;
      if (this._compactLayout && this._selectedServerId == null) {
        this._narrowContentOpen = false;
      }
    });
  },

  async _resolveRoomProfile(this: HomeShellState, room: PublicRoom): Promise<PublicRoom> {
    const detail = await this._roomsController.getRoom(room.id);
    return room_display.publicRoomFromRoomDetail(detail);
  },

  async _resolveRoomUserProfile(this: HomeShellState, roomId: string, user: UserSummary): Promise<UserSummary> {
    try {
      const profile = await this._roomsController.getRoomMemberProfile({ roomId, userId: user.id });
      return _userWithLocalCommonRoomNames(
        room_display.resolvedRoomMemberProfileUser({ profile, fallback: user }),
        this._servers,
      );
    } catch {
      return this._resolveUserProfile(user);
    }
  },

  async _resolveUserProfile(this: HomeShellState, user: UserSummary): Promise<UserSummary> {
    try {
      const profile = await this._roomsController.getUserProfile(user.id);
      return _userWithLocalCommonRoomNames(profile.mergeMissing(user), this._servers);
    } catch {
      return _userWithLocalCommonRoomNames(user, this._servers);
    }
  },

  async _handlePublicRoomSearchAction(this: HomeShellState, room: PublicRoom): Promise<void> {
    const pending = room.joinState === 'pending' || this._searchPendingPublicRoomIds.has(room.id);
    if (!room_display.publicRoomJoinActionable(room, { pending })) {
      return;
    }

    if (room.joined) {
      this._openSearchRoom(this._roomCardForPublicRoom(room));
      return;
    }

    let reason: string | null = null;
    let startedBeforeDialog = false;
    if (room_display.publicRoomJoinRequiresApplication(room)) {
      const started = room_join.roomJoinPublicActionStarted({
        roomId: room.id,
        pendingRoomIds: this._searchPendingPublicRoomIds,
      });
      this._setHomeState(() => {
        this._busySearchPublicRoomId = started.busyRoomId;
        this._searchError = started.error;
        this._searchPendingPublicRoomIds = started.pendingRoomIds;
      });
      startedBeforeDialog = true;
      const rawReason = await this._showJoinApplicationDialog(room);
      if (rawReason == null || !this.mounted) {
        if (this.mounted) {
          this._setHomeState(() => {
            this._busySearchPublicRoomId = null;
          });
        }
        return;
      }
      reason = room_join.joinRequestReasonValue(rawReason);
    }

    if (!startedBeforeDialog) {
      const started = room_join.roomJoinPublicActionStarted({
        roomId: room.id,
        pendingRoomIds: this._searchPendingPublicRoomIds,
      });
      this._setHomeState(() => {
        this._busySearchPublicRoomId = started.busyRoomId;
        this._searchError = started.error;
        this._searchPendingPublicRoomIds = started.pendingRoomIds;
      });
    }

    try {
      const result = await this._roomsController.joinRoom(room.id, { reason });
      if (!this.mounted) return;
      const joinedRoom = result.room;
      if (result.joined && joinedRoom != null) {
        await this._applyJoinedSearchRoom(joinedRoom);
        if (!this.mounted) return;
        this._setHomeState(() => {
          this._busySearchPublicRoomId = null;
          this._searchResults = this._searchResultsWithPublicRoomJoined(joinedRoom);
        });
        unawaited(this._loadServersSilently());
        return;
      }

      const pendingPatch = room_join.roomJoinPublicActionPending({
        busyRoomId: null,
        error: null,
        pendingRoomIds: this._searchPendingPublicRoomIds,
        room,
        result,
      });
      this._setHomeState(() => {
        this._busySearchPublicRoomId = pendingPatch.busyRoomId;
        this._searchError = pendingPatch.error;
        this._searchPendingPublicRoomIds = pendingPatch.pendingRoomIds;
        this._hasPendingRoomInvites = true;
      });
      unawaited(this._refreshPendingRoomInviteBadge());
    } catch (error) {
      if (!this.mounted) return;
      const failed = room_join.roomJoinPublicActionFailed({
        busyRoomId: null,
        pendingRoomIds: this._searchPendingPublicRoomIds,
        failure: error,
      });
      this._setHomeState(() => {
        this._busySearchPublicRoomId = failed.busyRoomId;
        this._searchError = failed.error;
        this._searchPendingPublicRoomIds = failed.pendingRoomIds;
      });
    }
  },

  async _showJoinApplicationDialog(this: HomeShellState, room: PublicRoom): Promise<string | null> {
    if (this._showingJoinApplicationDialog) return null;
    this._showingJoinApplicationDialog = true;
    try {
      const result = await showDialog<string>(() => <_JoinApplicationDialog room={room} />);
      return result ?? null;
    } finally {
      this._showingJoinApplicationDialog = false;
    }
  },

  async _applyJoinedSearchRoom(this: HomeShellState, room: RoomDetail): Promise<void> {
    const patch = this._roomsController.patchRoomDetailApplied({
      rooms: this._servers,
      detail: room,
    });
    this._setHomeState(() => {
      this._servers = patch.rooms;
    });
    await this._openRoom(room.toCard(), { openContent: true });
  },

  _openMessageSearchResult(this: HomeShellState, result: MessageSearchResult): void {
    this._openSearchRoom(this._roomCardForSearchResult(result));
  },

  _roomCardForSearchResult(this: HomeShellState, result: MessageSearchResult): RoomCard {
    const existing = _serverById(this, result.room.id);
    if (existing != null) return existing;
    return search_display.roomCardFromSearchContext(result.room, {
      updatedAt: result.message.createdAt,
    });
  },

  _roomCardForPublicRoom(this: HomeShellState, room: PublicRoom): RoomCard {
    const existing = _serverById(this, room.id);
    if (existing != null) return existing;
    return new RoomCard({
      id: room.id,
      rid: room.rid,
      name: room.name,
      visibility: room.visibility,
      description: room.description,
      avatarUrl: room.avatarUrl,
      defaultAvatarKey: room.defaultAvatarKey,
      memberCount: room.memberCount,
      onlineMemberCount: room.onlineMemberCount,
      liveParticipantCount: room.liveParticipantCount,
      liveAvatarPreview: [],
      lastMessage: null,
      unreadCount: 0,
      updatedAt: new Date(),
    });
  },

  _searchResultsWithPublicRoomJoined(this: HomeShellState, room: RoomDetail): GlobalSearchResults | null {
    const snapshot = this._searchResults;
    if (snapshot == null) return null;
    return new GlobalSearchResults({
      myRooms: snapshot.myRooms,
      publicRooms: snapshot.publicRooms.map((publicRoom) =>
        publicRoom.id === room.id
          ? publicRoom.copyWith({
              joined: true,
              joinState: 'joined',
              memberCount: room.memberCount,
              onlineMemberCount: room.onlineMemberCount,
              liveParticipantCount: room.live.participantCount,
            })
          : publicRoom,
      ),
      userSettings: snapshot.userSettings,
      messages: snapshot.messages,
      files: snapshot.files,
      nextCursors: snapshot.nextCursors,
      totalCounts: snapshot.totalCounts,
    });
  },
};

/**
 * The search extension's `_serverById`, which also looks through the loaded
 * "my rooms" search results.
 *
 * PORT: `_HomeShellRoomActions` declares a different `_serverById` (joined
 * rooms only). Dart resolved each call lexically to its own extension, so this
 * one stays a module function instead of a second `_serverById` entry that
 * `Object.assign` would let one table silently overwrite on the prototype.
 */
function _serverById(self: HomeShellState, roomId: string): RoomCard | null {
  for (const server of self._servers) {
    if (server.id === roomId) return server;
  }
  const results = self._searchResults;
  if (results != null) {
    for (const room of results.myRooms) {
      if (room.id === roomId) return room;
    }
  }
  return null;
}

export function _userWithLocalCommonRoomNames(user: UserSummary, rooms: Iterable<RoomCard>): UserSummary {
  return room_display.userWithLocalCommonRoomNames({ user, rooms });
}
