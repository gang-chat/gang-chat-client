import type { GangApi } from '../protocol/api_client';
import {
  GlobalSearchCounts,
  GlobalSearchCursors,
  GlobalSearchResults,
  UserSearchPage,
} from '../protocol/models';
import * as search_display from './search_display';

export class GlobalSearchController {
  constructor(opts: { api: GangApi }) {
    this.api = opts.api;
  }

  readonly api: GangApi;

  async search(opts: {
    query: string;
    limit?: number;
    categories?: Iterable<search_display.GlobalSearchCategory> | null;
    myRoomsCursor?: string | null;
    publicRoomsCursor?: string | null;
    userSettingsCursor?: string | null;
    messagesCursor?: string | null;
    filesCursor?: string | null;
  }): Promise<GlobalSearchResults> {
    const { query, limit = 8 } = opts;
    const selected = opts.categories == null ? null : [...opts.categories];
    const includeUserSettings = selected?.includes(search_display.GlobalSearchCategory.userSettings) ?? false;
    const coreCategories =
      selected?.filter((category) => category !== search_display.GlobalSearchCategory.userSettings) ?? null;
    const coreFuture: Promise<GlobalSearchResults> =
      coreCategories != null && coreCategories.length === 0
        ? Promise.resolve(
            new GlobalSearchResults({
              myRooms: [],
              publicRooms: [],
              messages: [],
              files: [],
            }),
          )
        : this.api.search({
            query,
            limit,
            categories: coreCategories?.map(search_display.globalSearchCategoryKey),
            myRoomsCursor: opts.myRoomsCursor,
            publicRoomsCursor: opts.publicRoomsCursor,
            messagesCursor: opts.messagesCursor,
            filesCursor: opts.filesCursor,
          });
    const userFuture: Promise<UserSearchPage> = includeUserSettings
      ? this.api.searchUsersPage({
          query,
          limit,
          cursor: opts.userSettingsCursor,
          includeSuspended: true,
        })
      : Promise.resolve(new UserSearchPage({ users: [] }));
    // Both requests are in flight before either is awaited, like the Dart
    // futures; attach a no-op handler so a failing user search does not raise
    // an unhandled rejection while the core search is still pending.
    userFuture.catch(() => {});
    const core = await coreFuture;
    const users = await userFuture;
    return new GlobalSearchResults({
      myRooms: core.myRooms,
      publicRooms: core.publicRooms,
      userSettings: users.users,
      messages: core.messages,
      files: core.files,
      nextCursors: new GlobalSearchCursors({
        myRooms: core.nextCursors.myRooms,
        publicRooms: core.nextCursors.publicRooms,
        userSettings: users.nextCursor,
        messages: core.nextCursors.messages,
        files: core.nextCursors.files,
      }),
      totalCounts: new GlobalSearchCounts({
        myRooms: core.totalCounts.myRooms,
        publicRooms: core.totalCounts.publicRooms,
        userSettings: users.totalCount,
        messages: core.totalCounts.messages,
        files: core.totalCounts.files,
      }),
    });
  }
}
