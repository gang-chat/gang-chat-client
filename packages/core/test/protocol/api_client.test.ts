import { describe, expect, it } from 'vitest';

import { type JsonMap } from '../../src/lib/json';
import { ClientException } from '../../src/platform/http';
import {
  ApiException,
  GangApiClient,
  newClientId,
  newUuid,
  UploadCancelledException,
  UploadTransferController,
  type AccessTokenProvider,
  type RequestLatencyCallback,
  type ServerTimeCallback,
} from '../../src/protocol/api_client';
import {
  MessageAttachment,
  MusicBoxActiveSourceType,
  MusicBoxPlaybackState,
  MusicBoxSearchResult,
  UploadedAsset,
} from '../../src/protocol/models';
import { gangServerTimeHeader } from '../../src/protocol/server_time_header';
import {
  jsonResponse,
  MockClient,
  mockBytesResponse,
  mockResponse,
  type MockHandler,
  type MockRequest,
} from '../support/fake_http_client';

const tokenProvider: AccessTokenProvider = async () => 'token';

function makeApi(
  handler: MockHandler,
  opts: {
    accessTokenProvider?: AccessTokenProvider;
    onServerTime?: ServerTimeCallback;
    onRequestLatency?: RequestLatencyCallback;
  } = {},
): GangApiClient {
  return new GangApiClient({
    baseUrl: 'http://example.test/api/v1',
    accessTokenProvider: opts.accessTokenProvider ?? tokenProvider,
    httpClient: new MockClient(handler),
    onServerTime: opts.onServerTime,
    onRequestLatency: opts.onRequestLatency,
  });
}

function jsonBody(request: MockRequest): unknown {
  return JSON.parse(request.body);
}

function fail(message: string): never {
  throw new Error(message);
}

function delayMs(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const senderJson = { id: 'user_1', username: 'alice', display_name: 'Alice' };

describe('GangApiClient', () => {
  it('getAppVersion fetches current server version metadata', async () => {
    const serverTimes: Array<string | null> = [];
    const api = makeApi(
      (request) => {
        expect(request.method).toBe('GET');
        expect(request.uri.pathname).toBe('/api/v1/app/version');
        expect(request.headers['authorization']).toBe('Bearer token');
        return jsonResponse(
          {
            latest_version: '1.2.3',
            minimum_supported_version: '1.0.0',
            release_notes: 'ok',
            download_url: 'https://example.test/download',
          },
          200,
          { [gangServerTimeHeader]: '2026-07-08T09:00:00Z' },
        );
      },
      { onServerTime: (value) => serverTimes.push(value) },
    );

    const info = await api.getAppVersion();

    expect(info.latestVersion).toBe('1.2.3');
    expect(info.minimumSupportedVersion).toBe('1.0.0');
    expect(info.releaseNotes).toBe('ok');
    expect(info.downloadUrl).toBe('https://example.test/download');
    expect(serverTimes).toEqual(['2026-07-08T09:00:00Z']);
    api.close();
  });

  it('reports request round-trip latency after a successful response', async () => {
    const latencies: number[] = [];
    const api = makeApi(
      async () => {
        await delayMs(2);
        return jsonResponse({
          latest_version: '1.2.3',
          minimum_supported_version: '1.0.0',
          release_notes: 'ok',
          download_url: 'https://example.test/download',
        });
      },
      { onRequestLatency: (value) => latencies.push(value) },
    );

    await api.getAppVersion();

    expect(latencies).toHaveLength(1);
    expect(latencies[0]!).toBeGreaterThan(0);
    api.close();
  });

  it('refreshes the access token once after a 401 and reports a null server time', async () => {
    const tokens: string[] = [];
    const forceRefreshCalls: boolean[] = [];
    const serverTimes: Array<string | null> = [];
    const api = makeApi(
      (request) => {
        tokens.push(request.headers['authorization']!);
        if (tokens.length === 1) return jsonResponse({ error: { code: 'unauthorized', message: 'expired' } }, 401);
        return jsonResponse({ latest_version: '2.0.0' });
      },
      {
        accessTokenProvider: async (opts) => {
          forceRefreshCalls.push(opts?.forceRefresh ?? false);
          return opts?.forceRefresh ? 'fresh' : 'stale';
        },
        onServerTime: (value) => serverTimes.push(value),
      },
    );

    const info = await api.getAppVersion();

    expect(info.latestVersion).toBe('2.0.0');
    expect(tokens).toEqual(['Bearer stale', 'Bearer fresh']);
    expect(forceRefreshCalls).toEqual([false, true]);
    expect(serverTimes).toEqual([null]);
    api.close();
  });

  it('listMessages retries once after a transient closed connection', async () => {
    let requests = 0;
    const api = makeApi((request) => {
      requests += 1;
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages');
      expect(request.queryParameters['limit']).toBe('50');
      expect(request.headers['authorization']).toBe('Bearer token');

      if (requests === 1) {
        throw new ClientException('Connection closed before full header was received', request.url);
      }

      return jsonResponse({
        messages: [
          {
            id: 'msg_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_1',
            body: 'hello',
            created_at: '2026-05-31T14:00:00Z',
          },
        ],
        has_more: false,
        next_before: null,
      });
    });

    const page = await api.listMessages({ roomId: 'room_1' });

    expect(requests).toBe(2);
    expect(page.messages[0]!.body).toBe('hello');
    api.close();
  });

  it('does not retry a transient failure on a non-retrying endpoint', async () => {
    let requests = 0;
    const api = makeApi((request) => {
      requests += 1;
      throw new ClientException('Connection reset by peer', request.url);
    });

    await expect(api.markRoomNotificationsRead()).rejects.toBeInstanceOf(ClientException);
    expect(requests).toBe(1);
    api.close();
  });

  it('retries a 502 once and then surfaces the ApiException', async () => {
    let requests = 0;
    const api = makeApi(() => {
      requests += 1;
      return mockResponse('bad gateway', 502, { 'x-request-id': 'req_9' });
    });

    const error = await api.getRoom('room_1').catch((e: unknown) => e);

    expect(requests).toBe(2);
    expect(error).toBeInstanceOf(ApiException);
    const apiError = error as ApiException;
    expect(apiError.statusCode).toBe(502);
    expect(apiError.code).toBe('request_failed');
    expect(apiError.requestId).toBe('req_9');
    expect(apiError.message).toBe('请求失败 (502)');
    expect(apiError.toString()).toBe('请求失败 (502)（请求编号：req_9）');
    api.close();
  });

  it('ApiException parses the server error envelope', async () => {
    const api = makeApi(() =>
      jsonResponse(
        { error: { code: 'forbidden', message: '没有权限', request_id: 'req_body' } },
        403,
        { 'x-request-id': 'req_header' },
      ),
    );

    const error = (await api.me().catch((e: unknown) => e)) as ApiException;

    expect(error).toBeInstanceOf(ApiException);
    expect(error.code).toBe('forbidden');
    expect(error.statusCode).toBe(403);
    expect(error.requestId).toBe('req_body');
    expect(error.message).toBe('没有权限');
    expect(error.isUnauthorized).toBe(false);
    expect(error.toString()).toBe('没有权限（请求编号：req_body）');
    api.close();
  });

  it('sendMessage retries a dropped connection with one idempotency key', async () => {
    let requests = 0;
    const idempotencyKeys: string[] = [];
    const api = makeApi((request) => {
      requests += 1;
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages');
      expect(jsonBody(request)).toEqual({ client_message_id: 'cmsg_1', body: 'hello' });
      idempotencyKeys.push(request.headers['idempotency-key']!);

      if (requests === 1) {
        throw new ClientException('Connection reset by peer', request.url);
      }

      return jsonResponse(
        {
          message: {
            id: 'msg_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_1',
            body: 'hello',
            created_at: '2026-05-31T14:00:00Z',
          },
        },
        201,
      );
    });

    const message = await api.sendMessage({ roomId: 'room_1', clientMessageId: 'cmsg_1', body: 'hello' });

    expect(requests).toBe(2);
    expect(idempotencyKeys).toHaveLength(2);
    expect(idempotencyKeys[0]).not.toBe('');
    expect(idempotencyKeys[0]).toBe(idempotencyKeys[1]);
    expect(message.body).toBe('hello');
    api.close();
  });

  it('listMessageHistory sends all filters and parses the page', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/message-history');
      expect(request.queryParameters).toEqual({
        limit: '25',
        category: 'links',
        query: 'needle',
        sender_user_id: 'user_2',
        start_at: '2026-07-01T00:00:00.000Z',
        end_at: '2026-07-14T00:00:00.000Z',
        before: 'msg_older',
      });
      return jsonResponse({
        messages: [
          {
            id: 'msg_1',
            room_id: 'room_1',
            sender: { id: 'user_2', username: 'alice', display_name: 'Alice' },
            client_message_id: 'cmsg_1',
            body: 'needle',
            created_at: '2026-07-10T12:00:00Z',
          },
        ],
        has_more: true,
        next_before: 'msg_1',
      });
    });

    const page = await api.listMessageHistory({
      roomId: 'room_1',
      query: '  needle  ',
      category: 'links',
      senderUserId: 'user_2',
      startAt: new Date(Date.UTC(2026, 6, 1)),
      endAt: new Date(Date.UTC(2026, 6, 14)),
      limit: 25,
      before: 'msg_older',
    });

    expect(page.messages[0]!.id).toBe('msg_1');
    expect(page.hasMore).toBe(true);
    expect(page.nextBefore).toBe('msg_1');
    api.close();
  });

  it('hideMessageHistory confirms current-account record deletion', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/message-history/hide');
      expect(jsonBody(request)).toEqual({ message_ids: ['msg_1', 'msg_2'], confirm: true });
      return jsonResponse({ ok: true, deleted_count: 2 });
    });

    const deleted = await api.hideMessageHistory({ roomId: 'room_1', messageIds: ['msg_1', 'msg_2'] });

    expect(deleted).toBe(2);
    api.close();
  });

  it('sendMessage keeps Chinese JSON UTF-8 without response charset', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages');
      expect(request.headers['accept']).toBe('application/json');
      expect(request.headers['content-type']).toBe('application/json; charset=utf-8');
      expect(jsonBody(request)).toEqual({ client_message_id: 'cmsg_1', body: '你好，世界' });

      return mockBytesResponse(
        new TextEncoder().encode(
          JSON.stringify({
            message: {
              id: 'msg_1',
              room_id: 'room_1',
              sender: senderJson,
              client_message_id: 'cmsg_1',
              body: '服务端中文',
              created_at: '2026-05-31T14:00:00Z',
            },
          }),
        ),
        201,
        { 'content-type': 'application/json' },
      );
    });

    const message = await api.sendMessage({ roomId: 'room_1', clientMessageId: 'cmsg_1', body: '你好，世界' });

    expect(message.body).toBe('服务端中文');
    api.close();
  });

  it('sendMessage sends quotes and mentions with the first quote mirrored', async () => {
    const api = makeApi((request) => {
      expect(jsonBody(request)).toEqual({
        client_message_id: 'cmsg_1',
        body: 'hi',
        mentions: [{ user_id: 'user_2' }],
        quote_message_id: 'msg_a',
        quote_message_ids: ['msg_a', 'msg_b'],
      });
      expect(request.headers['idempotency-key']).toBe('fixed-key');
      return jsonResponse(
        {
          message: {
            id: 'msg_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_1',
            body: 'hi',
            created_at: '2026-05-31T14:00:00Z',
          },
        },
        201,
      );
    });

    await api.sendMessage({
      roomId: 'room_1',
      clientMessageId: 'cmsg_1',
      body: 'hi',
      mentions: [{ user_id: 'user_2' }],
      quoteMessageId: 'msg_ignored',
      quoteMessageIds: ['msg_a', 'msg_b'],
      idempotencyKey: 'fixed-key',
    });
    api.close();
  });

  it('sendMessage can send and parse a sticker attachment', async () => {
    const stickerAsset = new UploadedAsset({
      id: 'asset_1',
      url: '/assets/asset_1/ok.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    });
    const stickerAttachment = new MessageAttachment({
      type: 'sticker',
      stickerId: 'sticker_1',
      name: 'ok',
      asset: stickerAsset,
    });
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages');
      expect(jsonBody(request)).toEqual({
        client_message_id: 'cmsg_1',
        body: '[ok]',
        type: 'sticker',
        attachments: [JSON.parse(JSON.stringify(stickerAttachment.toJson()))],
      });

      return jsonResponse(
        {
          message: {
            id: 'msg_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_1',
            type: 'sticker',
            body: '[ok]',
            attachments: [stickerAttachment.toJson()],
            created_at: '2026-05-31T14:00:00Z',
          },
        },
        201,
        { 'content-type': 'application/json' },
      );
    });

    const message = await api.sendMessage({
      roomId: 'room_1',
      clientMessageId: 'cmsg_1',
      body: '[ok]',
      type: 'sticker',
      attachments: [stickerAttachment],
    });

    expect(message.type).toBe('sticker');
    expect(message.stickerAttachment?.asset?.url).toBe('/assets/asset_1/ok.webp');
    api.close();
  });

  it('playlist messages send an id and parse the server snapshot', async () => {
    const requestedAttachment = new MessageAttachment({ type: 'playlist', playlistId: 'playlist_1' });
    const snapshot = {
      id: 'playlist_1',
      name: '夜晚',
      description: '',
      item_count: 1,
      created_at: '2026-08-07T09:30:00Z',
      creator: {
        id: 'user_1',
        username: 'alice',
        display_name: 'Alice',
        room_display_name: '房间里的 Alice',
        avatar_url: null,
        default_avatar_key: 'blue-1',
      },
      items: [
        {
          id: 'item_1',
          playlist_id: 'playlist_1',
          track_id: 'track_1',
          source: 'netease',
          title: '第一首歌',
          artists: ['歌手'],
          duration_ms: 180000,
          sort_order: 10,
          created_at: '2026-08-07T09:31:00Z',
        },
      ],
    };
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages');
      expect(jsonBody(request)).toEqual({
        client_message_id: 'cmsg_playlist_1',
        body: '',
        type: 'playlist',
        attachments: [JSON.parse(JSON.stringify(requestedAttachment.toJson()))],
      });
      return jsonResponse(
        {
          message: {
            id: 'msg_playlist_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_playlist_1',
            type: 'playlist',
            body: '[歌单] 夜晚',
            attachments: [{ type: 'playlist', playlist: snapshot }],
            created_at: '2026-08-07T09:32:00Z',
          },
        },
        201,
        { 'content-type': 'application/json' },
      );
    });

    const message = await api.sendMessage({
      roomId: 'room_1',
      clientMessageId: 'cmsg_playlist_1',
      body: '',
      type: 'playlist',
      attachments: [requestedAttachment],
    });

    expect(message.type).toBe('playlist');
    expect(message.playlistAttachment?.playlist?.name).toBe('夜晚');
    expect(message.playlistAttachment?.playlist?.creator.roomDisplayName).toBe('房间里的 Alice');
    expect(message.playlistAttachment?.playlist?.items[0]!.trackId).toBe('track_1');
    api.close();
  });

  it('music track share serializes source and parses snapshot', async () => {
    const requestedAttachment = new MessageAttachment({
      type: 'music_track',
      playlistId: 'playlist_1',
      playlistScope: 'room',
      sourceRoomId: 'source_room',
      itemId: 'item_1',
    });
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages');
      expect(jsonBody(request)).toEqual({
        client_message_id: 'cmsg_track_1',
        body: '',
        type: 'music_track',
        attachments: [JSON.parse(JSON.stringify(requestedAttachment.toJson()))],
      });
      return jsonResponse(
        {
          message: {
            id: 'msg_track_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_track_1',
            type: 'music_track',
            body: '[歌曲] 晴天',
            attachments: [
              {
                type: 'music_track',
                track: {
                  id: 'item_1',
                  playlist_id: 'playlist_1',
                  track_id: 'track_1',
                  source: 'netease',
                  title: '晴天',
                  artists: ['周杰伦'],
                  duration_ms: 269000,
                },
              },
            ],
            created_at: '2026-08-07T09:32:00Z',
          },
        },
        201,
        { 'content-type': 'application/json' },
      );
    });

    const message = await api.sendMessage({
      roomId: 'room_1',
      clientMessageId: 'cmsg_track_1',
      body: '',
      type: 'music_track',
      attachments: [requestedAttachment],
    });

    expect(message.musicTrackAttachment?.track?.title).toBe('晴天');
    expect(message.musicTrackAttachment?.track?.trackId).toBe('track_1');
    api.close();
  });

  it('shared playlist clone uses the message-scoped endpoint', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages/msg_1/playlist/clone-to-me');
      return jsonResponse(
        {
          playlist: {
            id: 'clone_1',
            name: '朋友的歌单 · 夜晚',
            description: '',
            revision: 1,
            item_count: 1,
            created_at: '2026-08-07T09:35:00Z',
            updated_at: '2026-08-07T09:35:00Z',
          },
        },
        201,
        { 'content-type': 'application/json' },
      );
    });

    const playlist = await api.cloneSharedMusicPlaylistToPersonal({ roomId: 'room_1', messageId: 'msg_1' });

    expect(playlist.id).toBe('clone_1');
    expect(playlist.name).toBe('朋友的歌单 · 夜晚');
    api.close();
  });

  it('listRoomMembers requests room members with pagination', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/members');
      expect(request.queryParameters).toEqual({ limit: '100', cursor: 'cursor_1' });
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({
        members: [
          {
            user: { id: 'user_1', uid: '1000001', username: 'alice', display_name: 'Alice' },
            role: 'owner',
            joined_at: '2026-05-31T14:00:00Z',
          },
        ],
        next_cursor: null,
      });
    });

    const page = await api.listRoomMembers('room_1', { limit: 100, cursor: 'cursor_1' });

    expect(page.members[0]!.role).toBe('owner');
    expect(page.members[0]!.user.username).toBe('alice');
    api.close();
  });

  it('getRoomMemberProfile parses signature and profile rooms', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/members/user_2/profile');
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({
        profile: {
          user: {
            id: 'user_2',
            uid: '1000002',
            username: 'bob',
            display_name: 'Bob',
            avatar_url: '/global.png',
            default_avatar_key: 'blue-2',
            bio: 'Ship quietly',
            gender: 'female',
            is_online: true,
            common_rooms: [
              {
                id: 'room_2',
                rid: '900002',
                name: 'Ops',
                remark_name: 'Night Ops',
                avatar_url: '/room.png',
                default_avatar_key: 'room-2',
                visibility: 'private',
                room_role: 'admin',
              },
            ],
          },
          room_display_name: 'Room Bob',
          room_avatar_url: '/room-bob.png',
          room_default_avatar_key: 'green-2',
          role: 'admin',
          joined_at: '2026-05-31T14:00:00Z',
        },
      });
    });

    const profile = await api.getRoomMemberProfile({ roomId: 'room_1', userId: 'user_2' });

    expect(profile.user.bio).toBe('Ship quietly');
    expect(profile.user.gender).toBe('female');
    expect(profile.user.roomDisplayName).toBe('Room Bob');
    expect(profile.user.roomRole).toBe('admin');
    expect(profile.user.isOnline).toBe(true);
    expect(profile.user.avatarUrl).toBe('/global.png');
    expect(profile.user.commonRooms[0]!.remarkName).toBe('Night Ops');
    expect(profile.user.commonRooms[0]!.avatarUrl).toBe('/room.png');
    expect(profile.user.commonRooms[0]!.defaultAvatarKey).toBe('room-2');
    api.close();
  });

  it('getUserProfile calls the global profile endpoint', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/users/user_2/profile');
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({
        profile: {
          user: {
            id: 'user_2',
            uid: '1000002',
            username: 'bob',
            display_name: 'Bob',
            avatar_url: '/global.png',
            default_avatar_key: 'blue-2',
            bio: 'Global profile',
            gender: 'male',
            is_online: true,
            common_rooms: [
              {
                id: 'room_2',
                rid: '900002',
                name: 'Ops',
                default_avatar_key: 'room-2',
                visibility: 'private',
                room_role: 'admin',
              },
            ],
          },
        },
      });
    });

    const profile = await api.getUserProfile('user_2');

    expect(profile.username).toBe('bob');
    expect(profile.bio).toBe('Global profile');
    expect(profile.gender).toBe('male');
    expect(profile.isOnline).toBe(true);
    expect(profile.commonRooms[0]!.name).toBe('Ops');
    expect(profile.commonRooms[0]!.roomRole).toBe('admin');
    api.close();
  });

  it('searchUsers calls the user search endpoint and parses summaries', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/users/search');
      expect(request.queryParameters).toEqual({ q: '1000001', limit: '20' });
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({
        users: [
          {
            id: 'user_1',
            uid: '1000001',
            username: 'alice',
            display_name: 'Alice',
            avatar_url: '/assets/asset_1/avatar.png',
            default_avatar_key: 'rose-2',
            is_superuser: true,
          },
        ],
        next_cursor: null,
      });
    });

    const users = await api.searchUsers({ query: '1000001' });

    expect(users[0]!.id).toBe('user_1');
    expect(users[0]!.uid).toBe('1000001');
    expect(users[0]!.avatarUrl).toBe('/assets/asset_1/avatar.png');
    expect(users[0]!.isSuperuser).toBe(true);
    api.close();
  });

  it('searchUsersPage forwards cursor and parses pagination metadata', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/users/search');
      expect(request.queryParameters).toEqual({
        q: 'alice',
        limit: '8',
        cursor: '8',
        include_suspended: 'true',
      });
      return jsonResponse({
        users: [
          {
            id: 'user_1',
            uid: '1000001',
            username: 'alice',
            display_name: 'Alice',
            default_avatar_key: 'blue-3',
            is_suspended: true,
          },
        ],
        next_cursor: '16',
        total_count: 21,
      });
    });

    const page = await api.searchUsersPage({ query: 'alice', limit: 8, cursor: '8', includeSuspended: true });

    expect(page.users[0]!.username).toBe('alice');
    expect(page.users[0]!.isSuspended).toBe(true);
    expect(page.nextCursor).toBe('16');
    expect(page.totalCount).toBe(21);
    api.close();
  });

  it('superuser user settings APIs use target-only endpoints', async () => {
    const requests: MockRequest[] = [];
    const api = makeApi((request) => {
      requests.push(request);
      const userJson = {
        id: 'user_2',
        uid: '1000002',
        username: 'bob',
        display_name: 'Bob',
        bio: '',
        gender: 'secret',
        email: 'bob@example.com',
        email_verified: true,
        email_public: false,
        phone_number_public: false,
        default_avatar_key: 'blue-3',
        language: 'zh-Hans',
        is_superuser: false,
        status: 'active',
      };
      if (request.uri.pathname.endsWith('/audio-settings')) {
        return jsonResponse({
          audio_settings: {
            default_audio_input_volume: 90,
            default_audio_output_volume: 80,
            live_mic_input_volume: 70,
            live_voice_output_volume: 60,
            live_screen_share_output_volume: 50,
            live_music_output_volume: 40,
          },
        });
      }
      if (request.uri.pathname.endsWith('/password')) {
        return jsonResponse({ ok: true });
      }
      return jsonResponse({ user: userJson });
    });

    const user = await api.getForcedUserSettings('user_2');
    const updated = await api.updateForcedUserSettings({ userId: 'user_2', displayName: 'Bob', emailVerified: true });
    const audio = await api.getForcedUserAudioSettings('user_2');
    await api.updateForcedUserAudioSettings({ userId: 'user_2', settings: audio });
    await api.forceResetUserPassword({ userId: 'user_2', newPassword: 'new password' });

    expect(user.id).toBe('user_2');
    expect(updated.emailVerified).toBe(true);
    expect(audio.liveMusicOutputVolume).toBe(40);
    expect(requests.map((request) => request.method)).toEqual(['GET', 'PATCH', 'GET', 'PATCH', 'POST']);
    expect(jsonBody(requests[1]!)).toEqual({ email_verified: true, display_name: 'Bob' });
    expect(jsonBody(requests[3]!)).toEqual({
      default_audio_input_volume: 90,
      default_audio_output_volume: 80,
      live_mic_input_volume: 70,
      live_voice_output_volume: 60,
      live_screen_share_output_volume: 50,
      live_music_output_volume: 40,
    });
    const passwordBody = jsonBody(requests[requests.length - 1]!) as JsonMap;
    expect(passwordBody).toEqual({ new_password: 'new password' });
    expect(passwordBody).not.toHaveProperty('current_password');
    api.close();
  });

  it('search calls global search endpoint and parses categories', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/search');
      expect(request.queryParameters).toEqual({ q: 'alpha', limit: '8' });
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({
        my_rooms: [_roomCardSearchJson()],
        public_rooms: [_publicRoomSearchJson()],
        messages: [{ room: _searchRoomContextJson(), message: _messageSearchJson({ body: 'alpha roadmap' }) }],
        files: [
          {
            room: _searchRoomContextJson(),
            message: _messageSearchJson({
              type: 'file',
              body: 'alpha.pdf',
              attachments: [
                {
                  type: 'file',
                  name: 'alpha.pdf',
                  asset: {
                    id: 'asset_1',
                    url: '/assets/alpha.pdf',
                    thumbnail_url: null,
                    mime_type: 'application/pdf',
                    filename: 'alpha.pdf',
                    size_bytes: 2048,
                  },
                },
              ],
            }),
          },
        ],
      });
    });

    const results = await api.search({ query: 'alpha' });

    expect(results.myRooms[0]!.name).toBe('Alpha Room');
    expect(results.publicRooms[0]!.joined).toBe(false);
    expect(results.messages[0]!.room.name).toBe('Alpha Room');
    expect(results.messages[0]!.message.body).toBe('alpha roadmap');
    expect(results.files[0]!.message.fileAttachments[0]!.asset?.filename).toBe('alpha.pdf');
    api.close();
  });

  it('search encodes non-empty categories and pagination cursors', async () => {
    const api = makeApi((request) => {
      expect(request.queryParameters).toEqual({
        q: 'alpha',
        limit: '8',
        categories: 'messages,files',
        my_rooms_cursor: 'c1',
        public_rooms_cursor: 'c2',
        messages_cursor: 'c3',
        files_cursor: 'c4',
      });
      return jsonResponse({});
    });

    await api.search({
      query: 'alpha',
      categories: new Set(['messages', '', 'files']),
      myRoomsCursor: 'c1',
      publicRoomsCursor: 'c2',
      messagesCursor: 'c3',
      filesCursor: 'c4',
    });
    api.close();
  });

  it('inviteMember posts the target user id and parses the invite', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/invites');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(request.headers['idempotency-key']).toBeTruthy();
      expect(jsonBody(request)).toEqual({ user_id: 'user_2' });

      return jsonResponse({ invite: _roomInviteJson() }, 201);
    });

    const invite = await api.inviteMember({ roomId: 'room_1', userId: 'user_2' });

    expect(invite.id).toBe('rinv_1');
    expect(invite.status).toBe('pending');
    expect(invite.room.name).toBe('Invite Room');
    expect(invite.inviter.username).toBe('alice');
    expect(invite.inviter.roomRole).toBe('owner');
    api.close();
  });

  it('listRoomBlacklist requests and parses blocked users', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/blacklist');
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({ blacklist: [_roomBlacklistEntryJson()], next_cursor: null });
    });

    const entries = await api.listRoomBlacklist('room_1');

    expect(entries[0]!.user.username).toBe('blocked_user');
    expect(entries[0]!.blockedBy?.username).toBe('alice');
    api.close();
  });

  it('listRoomBlacklist falls back to the items key', async () => {
    const api = makeApi(() => jsonResponse({ items: [_roomBlacklistEntryJson()] }));

    const entries = await api.listRoomBlacklist('room_1');

    expect(entries).toHaveLength(1);
    api.close();
  });

  it('blockRoomUser posts target id and parses entry', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/blacklist');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(request.headers['idempotency-key']).toBeTruthy();
      expect(jsonBody(request)).toEqual({ user_id: 'user_2' });

      return jsonResponse({ entry: _roomBlacklistEntryJson() }, 201);
    });

    const entry = await api.blockRoomUser({ roomId: 'room_1', userId: 'user_2' });

    expect(entry.user.id).toBe('user_2');
    api.close();
  });

  it('unblockRoomUser deletes the blocked user entry', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('DELETE');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/blacklist/user_2');
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({ ok: true });
    });

    await api.unblockRoomUser({ roomId: 'room_1', userId: 'user_2' });

    api.close();
  });

  it('joinRoom posts an optional application reason', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/join');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(request.headers['idempotency-key']).toBeTruthy();
      expect(jsonBody(request)).toEqual({ reason: 'Let me in' });

      return jsonResponse(
        {
          join_request: {
            id: 'jrq_1',
            room_id: 'room_1',
            status: 'pending',
            reason: 'Let me in',
            created_at: '2026-01-01T00:00:00Z',
          },
        },
        202,
      );
    });

    const result = await api.joinRoom('room_1', { reason: '  Let me in  ' });

    expect(result.pending).toBe(true);
    api.close();
  });

  it('joinRoom without a reason sends no body and parses the joined room', async () => {
    const api = makeApi((request) => {
      expect(request.body).toBe('');
      return jsonResponse({ room: _roomDetailJson() });
    });

    const result = await api.joinRoom('room_1');

    expect(result.joined).toBe(true);
    expect(result.room?.name).toBe('Invite Room');
    api.close();
  });

  it('listRoomInvites requests pending invites for the current user', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/room-invites');
      expect(request.queryParameters).toEqual({ status: 'pending' });
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({ invites: [_roomInviteJson()], next_cursor: null });
    });

    const invites = await api.listRoomInvites();

    expect(invites[0]!.id).toBe('rinv_1');
    expect(invites[0]!.room.rid).toBe('900001');
    api.close();
  });

  it('listRoomInvites can request all invites for notifications', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/room-invites');
      expect(request.queryParameters).toEqual({ status: 'all' });
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({
        invites: [
          _roomInviteJson({
            status: 'pending',
            roomExists: false,
            inviterExists: false,
            inviterRoomRole: 'left',
            invalidReason: 'room_missing',
          }),
        ],
        next_cursor: null,
      });
    });

    const invites = await api.listRoomInvites({ status: 'all' });

    expect(invites[0]!.status).toBe('pending');
    expect(invites[0]!.roomExists).toBe(false);
    expect(invites[0]!.inviterExists).toBe(false);
    expect(invites[0]!.room.isDeleted).toBe(true);
    expect(invites[0]!.inviter.isDeleted).toBe(true);
    expect(invites[0]!.invalidReason).toBe('room_missing');
    expect(invites[0]!.inviter.roomRole).toBe('left');
    api.close();
  });

  it('room invite notification parses room profile card fields', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/room-invites');

      return jsonResponse({
        invites: [_roomInviteJson({ roomJoined: true, includeViewerRoomInfo: true })],
        next_cursor: null,
      });
    });

    const invites = await api.listRoomInvites({ status: 'all' });
    const room = invites[0]!.room;

    expect(room.description).toBe('Invite room bio');
    expect(room.createdBy?.id).toBe('user_1');
    expect(room.personalProfile.displayName).toBe('Alice in Room');
    expect(room.myMembership?.role).toBe('member');
    api.close();
  });

  it('reviewRoomInvite accepts an invite and parses the joined room', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/room-invites/rinv_1');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(jsonBody(request)).toEqual({ decision: 'accept' });

      return jsonResponse({ ok: true, invite: _roomInviteJson({ status: 'accepted' }), room: _roomDetailJson() });
    });

    const result = await api.reviewRoomInvite({ inviteId: 'rinv_1', accept: true });

    expect(result.joined).toBe(true);
    expect(result.room?.name).toBe('Invite Room');
    api.close();
  });

  it('reviewRoomInvite parses a pending join request after accepting', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/room-invites/rinv_1');
      expect(jsonBody(request)).toEqual({ decision: 'accept', reason: 'Let me in' });

      return jsonResponse(
        {
          ok: true,
          invite: _roomInviteJson({ status: 'accepted' }),
          join_request: { id: 'jrq_1', room_id: 'room_1', status: 'pending', created_at: '2026-01-01T00:00:00Z' },
        },
        202,
      );
    });

    const result = await api.reviewRoomInvite({ inviteId: 'rinv_1', accept: true, reason: '  Let me in  ' });

    expect(result.joined).toBe(false);
    expect(result.pending).toBe(true);
    api.close();
  });

  it('reviewRoomInvite rejection resolves to neither joined nor pending', async () => {
    const api = makeApi((request) => {
      expect(jsonBody(request)).toEqual({ decision: 'reject' });
      return jsonResponse({ ok: true });
    });

    const result = await api.reviewRoomInvite({ inviteId: 'rinv_1', accept: false, reason: '   ' });

    expect(result.joined).toBe(false);
    expect(result.pending).toBe(false);
    api.close();
  });

  it('listRoomApplications requests all applications for notifications', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/room-applications');
      expect(request.queryParameters).toEqual({ status: 'all' });
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({
        applications: [_roomApplicationJson({ status: 'approved', reason: 'Hi team' })],
        next_cursor: null,
      });
    });

    const applications = await api.listRoomApplications({ status: 'all' });

    expect(applications[0]!.id).toBe('jrq_1');
    expect(applications[0]!.status).toBe('approved');
    expect(applications[0]!.room.name).toBe('Invite Room');
    expect(applications[0]!.reviewer?.roomRole).toBe('owner');
    expect(applications[0]!.reviewedAt).not.toBeNull();
    expect(applications[0]!.reason).toBe('Hi team');
    expect(applications[0]!.reviewerExists).toBe(true);
    api.close();
  });

  it('room application notification parses missing reviewer state', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/room-applications');

      return jsonResponse({
        applications: [_roomApplicationJson({ status: 'approved', reviewerExists: false })],
        next_cursor: null,
      });
    });

    const applications = await api.listRoomApplications({ status: 'all' });

    expect(applications[0]!.reviewerExists).toBe(false);
    expect(applications[0]!.reviewer?.isDeleted).toBe(true);
    expect(applications[0]!.reviewer?.displayName).toBe('Alice');
    api.close();
  });

  it('listRoomNotifications requests and parses room event notifications', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/room-notifications');
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({ notifications: [_roomEventNotificationJson()], next_cursor: null });
    });

    const notifications = await api.listRoomNotifications();
    const notification = notifications[0]!;

    expect(notification.id).toBe('rnot_1');
    expect(notification.type).toBe('role_promoted');
    expect(notification.toRole).toBe('admin');
    expect(notification.messageId).toBe('msg_mention_1');
    expect(notification.messagePreview).toBe('@Alice hello');
    expect(notification.room.name).toBe('Invite Room');
    expect(notification.room.description).toBe('Invite room bio');
    expect(notification.actor?.roomDisplayName).toBe('Alice in Invite Room');
    expect(notification.actor?.roomRole).toBe('owner');
    expect(notification.isUnread).toBe(true);
    api.close();
  });

  it('markRoomNotificationsRead posts the read receipt endpoint', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/room-notifications/read');
      expect(request.headers['authorization']).toBe('Bearer token');

      return jsonResponse({ ok: true });
    });

    await api.markRoomNotificationsRead();
    api.close();
  });

  it('withdrawRoomApplication sends withdraw decision and parses result', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/room-applications/jrq_1');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(jsonBody(request)).toEqual({ decision: 'withdraw' });

      return jsonResponse({ ok: true, application: _roomApplicationJson({ status: 'withdrawn' }) });
    });

    const application = await api.withdrawRoomApplication({ requestId: 'jrq_1' });

    expect(application.status).toBe('withdrawn');
    expect(application.reviewer).toBeNull();
    api.close();
  });

  it('sendMessage can send and parse a file attachment', async () => {
    const fileAsset = new UploadedAsset({
      id: 'asset_1',
      url: '/assets/asset_1/report.pdf',
      thumbnailUrl: null,
      mimeType: 'application/pdf',
      filename: 'report.pdf',
      sizeBytes: 4096,
    });
    const fileAttachment = new MessageAttachment({ type: 'file', name: 'report.pdf', asset: fileAsset });
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/messages');
      expect(jsonBody(request)).toEqual({
        client_message_id: 'cmsg_1',
        body: 'report.pdf',
        type: 'file',
        attachments: [JSON.parse(JSON.stringify(fileAttachment.toJson()))],
      });

      return jsonResponse(
        {
          message: {
            id: 'msg_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_1',
            type: 'file',
            body: 'report.pdf',
            attachments: [fileAttachment.toJson()],
            created_at: '2026-05-31T14:00:00Z',
          },
        },
        201,
      );
    });

    const message = await api.sendMessage({
      roomId: 'room_1',
      clientMessageId: 'cmsg_1',
      body: 'report.pdf',
      type: 'file',
      attachments: [fileAttachment],
    });

    expect(message.type).toBe('file');
    expect(message.fileAttachments[0]!.asset?.filename).toBe('report.pdf');
    expect(message.fileAttachments[0]!.asset?.sizeBytes).toBe(4096);
    api.close();
  });

  it('joinLive retries a transient socket write abort with one idempotency key', async () => {
    let requests = 0;
    const idempotencyKeys: string[] = [];
    const api = makeApi((request) => {
      requests += 1;
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/live/join');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(jsonBody(request)).toMatchObject({ mic_muted: true, headphones_muted: true });
      idempotencyKeys.push(request.headers['idempotency-key']!);

      if (requests === 1) {
        throw new ClientException(
          'SocketException: Write failed (OS Error: ' + '你的主机中的软件中止了一个已建立的连接。, errno = 10053)',
          request.url,
        );
      }

      return jsonResponse(_liveJoinJson());
    });

    const result = await api.joinLive({
      roomId: 'room_1',
      clientLiveSessionId: 'clive_1',
      source: 'room_card_speaker',
      micMuted: true,
      headphonesMuted: true,
    });

    expect(requests).toBe(2);
    expect(idempotencyKeys).toHaveLength(2);
    expect(idempotencyKeys[0]).not.toBe('');
    expect(idempotencyKeys[0]).toBe(idempotencyKeys[1]);
    expect(result.liveKit.serverUrl).toBe('wss://voice.example.com');
    expect(result.live.participantCount).toBe(1);
    api.close();
  });

  it('listSessions parses recent account activity array', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/auth/sessions');
      expect(request.headers['authorization']).toBe('Bearer token');
      return jsonResponse([
        {
          id: 'session_1',
          user_agent: 'Flutter test',
          ip_address: '127.0.0.1',
          location: 'Local',
          created_at: 1780300800,
          last_used_at: 1780300860,
          expires_at: 1782892800,
          is_current: true,
        },
      ]);
    });

    const sessions = await api.listSessions();

    expect(sessions).toHaveLength(1);
    expect(sessions[0]!.location).toBe('Local');
    expect(sessions[0]!.isCurrent).toBe(true);
    api.close();
  });

  it('listSessions also accepts a wrapped sessions list', async () => {
    const api = makeApi(() =>
      jsonResponse({
        sessions: [
          {
            id: 'session_1',
            location: 'Local',
            created_at: 1780300800,
            last_used_at: 1780300860,
            expires_at: 1782892800,
            is_current: false,
          },
        ],
      }),
    );

    const sessions = await api.listSessions();

    expect(sessions.map((session) => session.id)).toEqual(['session_1']);
    api.close();
  });

  it('listForcedUserSessions reads the managed account activity', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/users/user_2/sessions');
      expect(request.headers['authorization']).toBe('Bearer token');
      return jsonResponse([
        {
          id: 'target_session',
          user_agent: 'Windows target',
          ip_address: '203.0.113.8',
          location: 'Shanghai',
          created_at: 1780300800,
          last_used_at: 1780300860,
          expires_at: 1782892800,
          is_current: false,
        },
      ]);
    });

    const sessions = await api.listForcedUserSessions('user_2');

    expect(sessions[0]!.id).toBe('target_session');
    expect(sessions[0]!.ipAddress).toBe('203.0.113.8');
    expect(sessions[0]!.isCurrent).toBe(false);
    api.close();
  });

  it('updateAccount sends language preference and parses it', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/users/me/account');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(jsonBody(request)).toEqual({ language: 'en' });
      return jsonResponse({
        user: {
          id: 'user_1',
          uid: '1000001',
          username: 'alice',
          display_name: 'Alice',
          bio: '',
          gender: 'secret',
          email: 'alice@example.test',
          email_public: false,
          phone_number_public: false,
          avatar_url: null,
          default_avatar_key: 'blue-3',
          language: 'en',
        },
      });
    });

    const user = await api.updateAccount({ language: 'en' });

    expect(user.language).toBe('en');
    api.close();
  });

  it('updateAccount sends the email verification token', async () => {
    const api = makeApi((request) => {
      expect(jsonBody(request)).toEqual({
        email: 'new@example.test',
        email_verification_token: 'verification-token',
      });
      return jsonResponse({
        user: {
          id: 'user_1',
          uid: '1000001',
          username: 'alice',
          display_name: 'Alice',
          bio: '',
          gender: 'secret',
          email: 'new@example.test',
          email_verified: true,
          email_public: false,
          phone_number_public: false,
          avatar_url: null,
          default_avatar_key: 'blue-3',
        },
      });
    });

    const user = await api.updateAccount({ email: 'new@example.test', emailVerificationToken: 'verification-token' });

    expect(user.email).toBe('new@example.test');
    expect(user.emailVerified).toBe(true);
    api.close();
  });

  it('push device endpoints encode the provider and installation path', async () => {
    const seen: string[] = [];
    const api = makeApi((request) => {
      seen.push(`${request.method} ${request.uri.pathname}`);
      if (request.method === 'PUT') {
        expect(jsonBody(request)).toEqual({
          provider: 'fcm',
          installation_id: 'install/1',
          token: 'push-token',
          platform: 'android',
          enabled: true,
        });
      }
      if (request.method === 'PATCH') expect(jsonBody(request)).toEqual({ enabled: false });
      return jsonResponse({ ok: true });
    });

    await api.upsertPushDevice({ provider: 'fcm', installationId: 'install/1', token: 'push-token', enabled: true });
    await api.updatePushDeviceEnabled({ provider: 'fcm', installationId: 'install/1', enabled: false });
    await api.deletePushDevice({ provider: 'fcm', installationId: 'install/1' });

    expect(seen).toEqual([
      'PUT /api/v1/me/push-devices/fcm/install%2F1',
      'PATCH /api/v1/me/push-devices/fcm/install%2F1',
      'DELETE /api/v1/me/push-devices/fcm/install%2F1',
    ]);
    api.close();
  });

  it('uploadImageAsset posts multipart image data', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/uploads/images');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(request.headers['content-type']).toMatch(/^multipart\/form-data/);
      expect(request.bodyBytes.length).toBeGreaterThan(0);
      expect(request.contentLength).toBe(request.bodyBytes.length);
      expect(request.body).toContain('name="purpose"');
      expect(request.body).toContain('avatar');
      expect(request.body).toContain('filename="avatar.png"');
      return jsonResponse(
        {
          asset: {
            id: 'asset_1',
            url: '/assets/asset_1/avatar.png',
            thumbnail_url: '/assets/asset_1/avatar.png',
            mime_type: 'image/png',
          },
        },
        201,
      );
    });

    const asset = await api.uploadImageAsset({
      bytes: Uint8Array.from([1, 2, 3]),
      filename: 'avatar.png',
      purpose: 'avatar',
    });

    expect(asset.id).toBe('asset_1');
    expect(asset.mimeType).toBe('image/png');
    api.close();
  });

  it('uploadFileAsset posts multipart file data', async () => {
    const progress: number[] = [];
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/uploads/files');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(request.headers['content-type']).toMatch(/^multipart\/form-data/);
      expect(request.contentLength).toBe(request.bodyBytes.length);
      const body = request.body;
      expect(body).toContain('name="purpose"');
      expect(body).toContain('message_file');
      expect(body).toContain('report.pdf');
      return jsonResponse(
        {
          asset: {
            id: 'asset_1',
            filename: 'report.pdf',
            size_bytes: 4096,
            url: '/assets/asset_1/report.pdf',
            thumbnail_url: null,
            mime_type: 'application/pdf',
          },
        },
        201,
      );
    });

    const asset = await api.uploadFileAsset({
      bytes: Uint8Array.from([37, 80, 68, 70]),
      filename: 'report.pdf',
      onProgress: ({ sentBytes, totalBytes }) => {
        expect(totalBytes).toBe(4);
        progress.push(sentBytes);
      },
    });

    expect(asset.id).toBe('asset_1');
    expect(asset.filename).toBe('report.pdf');
    expect(asset.sizeBytes).toBe(4096);
    expect(asset.mimeType).toBe('application/pdf');
    expect(progress[0]).toBe(0);
    expect(progress[progress.length - 1]).toBe(4);
    api.close();
  });

  it('uploadFileAsset streams 64 KiB chunks and waits while paused', async () => {
    const controller = new UploadTransferController();
    const progress: number[] = [];
    const total = 64 * 1024 * 2 + 10;
    let received = 0;
    const api = makeApi((request) => {
      received = request.bodyBytes.length;
      return jsonResponse({ asset: { id: 'asset_big', url: '/assets/big', mime_type: 'application/zip' } }, 201);
    });

    controller.pause();
    expect(controller.isPaused).toBe(true);
    const upload = api.uploadFileAsset({
      bytes: new Uint8Array(total),
      filename: 'big.zip',
      controller,
      onProgress: ({ sentBytes }) => progress.push(sentBytes),
    });
    await delayMs(10);
    expect(progress).toEqual([0]);
    controller.resume();
    const asset = await upload;

    expect(asset.id).toBe('asset_big');
    expect(progress).toEqual([0, 65536, 131072, total]);
    expect(received).toBeGreaterThan(total);
    api.close();
  });

  it('uploadFileAsset rejects with UploadCancelledException when cancelled', async () => {
    const controller = new UploadTransferController();
    let requests = 0;
    const api = makeApi(() => {
      requests += 1;
      return jsonResponse({ asset: { id: 'asset_1', url: '/a', mime_type: 'text/plain' } }, 201);
    });

    controller.pause();
    const upload = api.uploadFileAsset({ bytes: new Uint8Array(10), filename: 'a.txt', controller });
    await delayMs(5);
    controller.cancel();

    const error = await upload.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(UploadCancelledException);
    expect(String(error)).toBe('上传已取消');
    expect(controller.isCancelled).toBe(true);
    expect(controller.isPaused).toBe(false);
    expect(requests).toBe(0);
    api.close();
  });

  it('uploadImageAsset retries a transient socket write abort', async () => {
    let requests = 0;
    const api = makeApi((request) => {
      requests += 1;
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/uploads/images');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(request.headers['content-type']).toMatch(/^multipart\/form-data/);
      expect(request.bodyBytes.length).toBeGreaterThan(0);

      if (requests === 1) {
        throw new ClientException(
          'SocketException: Write failed (OS Error: ' + '你的主机中的软件中止了一个已建立的连接。, errno = 10053)',
          request.url,
        );
      }

      return jsonResponse(
        {
          asset: {
            id: 'asset_1',
            url: '/assets/asset_1/sticker.png',
            thumbnail_url: '/assets/asset_1/sticker.png',
            mime_type: 'image/png',
            width: 128,
            height: 128,
          },
        },
        201,
      );
    });

    const asset = await api.uploadImageAsset({
      bytes: Uint8Array.from([1, 2, 3]),
      filename: 'sticker.png',
      purpose: 'sticker',
    });

    expect(requests).toBe(2);
    expect(asset.id).toBe('asset_1');
    expect(asset.width).toBe(128);
    api.close();
  });

  it('uploadImageAsset retries a transient 503 response', async () => {
    let requests = 0;
    const api = makeApi((request) => {
      requests += 1;
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/uploads/images');
      expect(request.headers['authorization']).toBe('Bearer token');

      if (requests === 1) {
        return mockResponse('service unavailable', 503);
      }

      return jsonResponse(
        {
          asset: {
            id: 'asset_1',
            url: '/assets/asset_1/sticker.png',
            thumbnail_url: '/assets/asset_1/sticker.png',
            mime_type: 'image/png',
          },
        },
        201,
      );
    });

    const asset = await api.uploadImageAsset({
      bytes: Uint8Array.from([1, 2, 3]),
      filename: 'sticker.png',
      purpose: 'sticker',
    });

    expect(requests).toBe(2);
    expect(asset.id).toBe('asset_1');
    api.close();
  });

  it('listStickerPacks parses personal sticker assets', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
      expect(request.queryParameters['scope']).toBe('personal');
      expect(request.headers['authorization']).toBe('Bearer token');
      return jsonResponse({
        packs: [
          {
            id: 'stkp_1',
            scope: 'personal',
            room_id: null,
            name: 'My Stickers',
            sort_order: 10,
            updated_at: '2026-06-02T08:00:00Z',
            stickers: [
              {
                id: 'stk_1',
                name: 'ok',
                sort_order: 20,
                asset: {
                  id: 'asset_1',
                  url: '/assets/asset_1/ok.webp',
                  thumbnail_url: '/assets/asset_1/ok.webp',
                  mime_type: 'image/webp',
                  width: 128,
                  height: 128,
                  created_at: '2026-06-02T07:59:00Z',
                },
              },
            ],
          },
        ],
      });
    });

    const packs = await api.listStickerPacks();

    expect(packs[0]!.name).toBe('My Stickers');
    expect(packs[0]!.stickers[0]!.asset.mimeType).toBe('image/webp');
    expect(packs[0]!.stickers[0]!.asset.width).toBe(128);
    api.close();
  });

  it('saveSticker posts target scope and parses returned pack', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/stickers/save');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(jsonBody(request)).toEqual({
        sticker_id: 'stk_1',
        source_message_id: 'msg_1',
        target_scope: 'room',
        name: 'ok',
      });
      return jsonResponse(_stickerPackJson({ stickers: [_stickerJson({ id: 'stk_saved' })] }), 201);
    });

    const pack = await api.saveSticker({
      roomId: 'room_1',
      stickerId: 'stk_1',
      sourceMessageId: 'msg_1',
      targetScope: 'room',
      name: 'ok',
    });

    expect(pack.stickers[0]!.id).toBe('stk_saved');
    api.close();
  });

  it('personal sticker APIs carry the managed owner user id', async () => {
    let requestIndex = 0;
    const api = makeApi((request) => {
      requestIndex += 1;
      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          expect(request.queryParameters).toEqual({ scope: 'personal', owner_user_id: 'user_2' });
          return jsonResponse({ packs: [] });
        case 2:
          expect(request.method).toBe('POST');
          expect(jsonBody(request)).toEqual({ scope: 'personal', name: 'Target Pack', owner_user_id: 'user_2' });
          return jsonResponse(_stickerPackJson(), 201);
      }
      fail(`unexpected request ${request.method} ${request.url}`);
    });

    await api.listStickerPacks({ scope: 'personal', ownerUserId: 'user_2' });
    await api.createStickerPack({ name: 'Target Pack', ownerUserId: 'user_2' });

    expect(requestIndex).toBe(2);
    api.close();
  });

  it('sticker pack management uses server sticker routes', async () => {
    let requestIndex = 0;
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          expect(jsonBody(request)).toEqual({ scope: 'personal', name: 'My Stickers', sort_order: 10 });
          return jsonResponse(_stickerPackJson(), 201);
        case 2:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers');
          expect(request.headers['idempotency-key']).toBeTruthy();
          expect(jsonBody(request)).toEqual({ asset_id: 'asset_2', name: 'hi', sort_order: 20 });
          return jsonResponse({ sticker: { id: 'stk_2', asset_id: 'asset_2', name: 'hi', sort_order: 20 } }, 201);
        case 3:
          expect(request.method).toBe('PATCH');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1');
          expect(jsonBody(request)).toEqual({ name: 'Saved', sort_order: 30 });
          return jsonResponse(_stickerPackJson({ name: 'Saved' }));
        case 4:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers/stk_2');
          return jsonResponse({ ok: true });
        case 5:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1');
          return jsonResponse({ ok: true });
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    const pack = await api.createStickerPack({ name: 'My Stickers', sortOrder: 10 });
    await api.addSticker({ packId: pack.id, assetId: 'asset_2', name: 'hi', sortOrder: 20 });
    const updated = await api.updateStickerPack({ packId: pack.id, name: 'Saved', sortOrder: 30 });
    await api.deleteSticker({ packId: pack.id, stickerId: 'stk_2' });
    await api.deleteStickerPack(pack.id);

    expect(updated.name).toBe('Saved');
    expect(requestIndex).toBe(5);
    api.close();
  });

  it('sticker item management uses rename reorder and download routes', async () => {
    let requestIndex = 0;
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('PATCH');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers/stk_1');
          expect(jsonBody(request)).toEqual({ name: 'ok' });
          return jsonResponse({ sticker: _stickerJson({ name: 'ok (2)' }) });
        case 2:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers/reorder');
          expect(jsonBody(request)).toEqual({ sticker_ids: ['stk_2', 'stk_1'] });
          return jsonResponse(
            _stickerPackJson({
              stickers: [_stickerJson({ id: 'stk_2', sortOrder: 10 }), _stickerJson({ id: 'stk_1', sortOrder: 20 })],
            }),
          );
        case 3:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/stickers/download');
          expect(request.queryParameters['ids']).toBe('stk_1,stk_2');
          // Downloads only carry the bearer token, not the JSON headers.
          expect(request.headers['accept']).toBeUndefined();
          return mockBytesResponse([80, 75, 3, 4], 200, {
            'content-type': 'application/zip',
            'content-disposition': 'attachment; filename="stickers.zip"',
          });
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    const renamed = await api.updateSticker({ packId: 'stkp_1', stickerId: 'stk_1', name: 'ok' });
    const reordered = await api.reorderStickers({ packId: 'stkp_1', stickerIds: ['stk_2', 'stk_1'] });
    const downloaded = await api.downloadStickers({ stickerIds: ['stk_1', 'stk_2'] });

    expect(renamed.name).toBe('ok (2)');
    expect(reordered.stickers[0]!.id).toBe('stk_2');
    expect(downloaded.filename).toBe('stickers.zip');
    expect(downloaded.mimeType).toBe('application/zip');
    expect(Array.from(downloaded.bytes)).toEqual([80, 75, 3, 4]);
    expect(requestIndex).toBe(3);
    api.close();
  });

  it('downloadStickers prefers UTF-8 filename star header', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/stickers/download');
      return mockBytesResponse([80, 75, 3, 4], 200, {
        'content-type': 'application/zip',
        'content-disposition': 'attachment; filename="stickers.zip"; ' + "filename*=UTF-8''%E8%A1%A8%E6%83%85.zip",
      });
    });

    const downloaded = await api.downloadStickers({ stickerIds: ['stk_1'] });

    expect(downloaded.filename).toBe('表情.zip');
    api.close();
  });

  it('downloadStickers falls back to unquoted and mime-based filenames', async () => {
    const responses = [
      mockBytesResponse([1], 200, { 'content-disposition': 'attachment; filename= plain.webp ; size=1' }),
      mockBytesResponse([1], 200, { 'content-type': 'application/zip' }),
      mockBytesResponse([1], 200, {}),
    ];
    const api = makeApi(() => responses.shift()!);

    expect((await api.downloadStickers({ stickerIds: ['a'] })).filename).toBe('plain.webp');
    const zip = await api.downloadStickers({ stickerIds: ['a'] });
    expect(zip.filename).toBe('stickers.zip');
    const bare = await api.downloadStickers({ stickerIds: ['a'] });
    expect(bare.filename).toBe('sticker');
    expect(bare.mimeType).toBe('application/octet-stream');
    api.close();
  });

  it('downloadMusicTrackPreview posts identity and returns M4A bytes', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/me/music-box/preview');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(jsonBody(request)).toEqual({ source: 'netease', track_id: 'track-1' });
      return mockBytesResponse([0, 0, 0, 24, 102, 116, 121, 112], 200, {
        'content-type': 'audio/mp4',
        'content-disposition': 'inline; filename="music-preview.m4a"',
      });
    });

    const downloaded = await api.downloadMusicTrackPreview({ source: 'netease', trackId: 'track-1' });

    expect(Array.from(downloaded.bytes)).toEqual([0, 0, 0, 24, 102, 116, 121, 112]);
    expect(downloaded.mimeType).toBe('audio/mp4');
    expect(downloaded.filename).toBe('music-preview.m4a');
    api.close();
  });

  it('deleteSticker treats transient close as success when sticker is gone', async () => {
    let requestIndex = 0;
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers/stk_2');
          throw new ClientException('Connection closed before full header was received', request.url);
        case 2:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          expect(request.queryParameters['scope']).toBe('personal');
          return jsonResponse(_personalStickerPacksJson());
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    await api.deleteSticker({ packId: 'stkp_1', stickerId: 'stk_2' });

    expect(requestIndex).toBe(2);
    api.close();
  });

  it('deleteSticker retries a transient close when sticker remains', async () => {
    let requestIndex = 0;
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers/stk_2');
          throw new ClientException('Connection closed before full header was received', request.url);
        case 2:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          expect(request.queryParameters['scope']).toBe('personal');
          return jsonResponse(_personalStickerPacksJson({ linkedAssetId: 'asset_2' }));
        case 3:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers/stk_2');
          return jsonResponse({ ok: true });
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    await api.deleteSticker({ packId: 'stkp_1', stickerId: 'stk_2' });

    expect(requestIndex).toBe(3);
    api.close();
  });

  it('deleteSticker treats transient 503 as success when sticker is gone', async () => {
    let requestIndex = 0;
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers/stk_2');
          return mockResponse('service unavailable', 503);
        case 2:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          expect(request.queryParameters['scope']).toBe('personal');
          return jsonResponse(_personalStickerPacksJson());
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    await api.deleteSticker({ packId: 'stkp_1', stickerId: 'stk_2' });

    expect(requestIndex).toBe(2);
    api.close();
  });

  it('deleteSticker treats a 404 as already deleted', async () => {
    let requests = 0;
    const api = makeApi(() => {
      requests += 1;
      return jsonResponse({ error: { code: 'not_found', message: 'sticker not found' } }, 404);
    });

    await api.deleteSticker({ packId: 'stkp_1', stickerId: 'stk_2' });

    expect(requests).toBe(1);
    api.close();
  });

  it('addSticker treats transient write failure as success when asset exists', async () => {
    let requestIndex = 0;
    const idempotencyKeys: string[] = [];
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers');
          idempotencyKeys.push(request.headers['idempotency-key']!);
          throw new ClientException(
            'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
            request.url,
          );
        case 2:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          expect(request.queryParameters['scope']).toBe('personal');
          return jsonResponse(_personalStickerPacksJson({ linkedAssetId: 'asset_2' }));
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    await api.addSticker({ packId: 'stkp_1', assetId: 'asset_2', name: 'hi' });

    expect(requestIndex).toBe(2);
    expect(idempotencyKeys).toHaveLength(1);
    expect(idempotencyKeys[0]).toBeTruthy();
    api.close();
  });

  it('addSticker retries once after transient write failure when not linked', async () => {
    let requestIndex = 0;
    const idempotencyKeys: string[] = [];
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers');
          idempotencyKeys.push(request.headers['idempotency-key']!);
          throw new ClientException(
            'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
            request.url,
          );
        case 2:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          expect(request.queryParameters['scope']).toBe('personal');
          return jsonResponse(_personalStickerPacksJson());
        case 3:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers');
          idempotencyKeys.push(request.headers['idempotency-key']!);
          expect(jsonBody(request)).toEqual({ asset_id: 'asset_2', name: 'hi' });
          return jsonResponse({ sticker: { id: 'stk_2', asset_id: 'asset_2', name: 'hi', sort_order: 10 } }, 201);
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    await api.addSticker({ packId: 'stkp_1', assetId: 'asset_2', name: 'hi' });

    expect(requestIndex).toBe(3);
    expect(idempotencyKeys).toHaveLength(2);
    expect(idempotencyKeys[0]).toBeTruthy();
    expect(idempotencyKeys[0]).toBe(idempotencyKeys[1]);
    api.close();
  });

  it('addSticker recovers after repeated transient write failures once linked', async () => {
    let requestIndex = 0;
    const idempotencyKeys: string[] = [];
    const api = makeApi((request) => {
      requestIndex += 1;
      expect(request.headers['authorization']).toBe('Bearer token');

      switch (requestIndex) {
        case 1:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers');
          idempotencyKeys.push(request.headers['idempotency-key']!);
          throw new ClientException(
            'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
            request.url,
          );
        case 2:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          return jsonResponse(_personalStickerPacksJson());
        case 3:
          expect(request.method).toBe('POST');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs/stkp_1/stickers');
          idempotencyKeys.push(request.headers['idempotency-key']!);
          throw new ClientException(
            'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
            request.url,
          );
        case 4:
          expect(request.method).toBe('GET');
          expect(request.uri.pathname).toBe('/api/v1/sticker-packs');
          return jsonResponse(_personalStickerPacksJson({ linkedAssetId: 'asset_2' }));
      }

      fail(`unexpected request ${requestIndex} ${request.method} ${request.url}`);
    });

    await api.addSticker({ packId: 'stkp_1', assetId: 'asset_2', name: 'hi' });

    expect(requestIndex).toBe(4);
    expect(idempotencyKeys).toHaveLength(2);
    expect(idempotencyKeys[0]).toBe(idempotencyKeys[1]);
    api.close();
  });

  it('addSticker rethrows non-retryable failures without verification', async () => {
    let requests = 0;
    const api = makeApi(() => {
      requests += 1;
      return jsonResponse({ error: { code: 'forbidden', message: 'nope' } }, 403);
    });

    await expect(api.addSticker({ packId: 'stkp_1', assetId: 'asset_2', name: 'hi' })).rejects.toBeInstanceOf(
      ApiException,
    );
    expect(requests).toBe(1);
    api.close();
  });

  it('room management endpoints use server room routes', async () => {
    let requests = 0;
    const api = makeApi((request) => {
      requests += 1;
      expect(request.headers['authorization']).toBe('Bearer token');
      switch (requests) {
        case 1:
          expect(request.method).toBe('PATCH');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1');
          expect(jsonBody(request)).toEqual({
            name: 'New Room',
            description: 'Updated',
            visibility: 'private',
            join_policy: 'closed',
            ai_voice_announcements_enabled: false,
            avatar_asset_id: 'asset_1',
            default_avatar_key: 'teal-2',
          });
          return jsonResponse({
            room: {
              ..._roomDetailJson(),
              name: 'New Room',
              description: 'Updated',
              visibility: 'private',
              join_policy: 'closed',
              ai_voice_announcements_enabled: false,
              avatar_url: '/assets/asset_1/room.png',
              default_avatar_key: 'teal-2',
            },
          });
        case 2:
          expect(request.method).toBe('PATCH');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/me');
          expect(jsonBody(request)).toEqual({
            remark_name: 'Ops',
            notification_policy: 'silent',
            room_display_name: 'Alice Ops',
            is_pinned: true,
            ai_voice_announcements_enabled: false,
          });
          return jsonResponse({
            room: {
              ..._roomDetailJson(),
              remark_name: 'Ops',
              notification_policy: 'silent',
              is_pinned: true,
              personal_profile: {
                display_name: 'Alice Ops',
                avatar_url: '/assets/asset_2/avatar.png',
                default_avatar_key: 'mint-2',
              },
            },
          });
        case 3:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/members/me');
          expect(jsonBody(request)).toEqual({ confirm_delete_if_empty: true });
          return mockResponse('{}', 200);
        case 4:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1');
          expect(jsonBody(request)).toEqual({ confirm_name: 'New Room' });
          return mockResponse('{}', 200);
        case 5:
          expect(request.method).toBe('PATCH');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/members/user_2');
          expect(jsonBody(request)).toEqual({ role: 'admin' });
          return jsonResponse({
            member: {
              user: { id: 'user_2', username: 'bob', display_name: 'Bob' },
              role: 'admin',
              joined_at: '2026-05-31T14:00:00Z',
            },
          });
        case 6:
          expect(request.method).toBe('PATCH');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/members/user_2');
          expect(jsonBody(request)).toEqual({ room_display_name: 'Room Bob' });
          return jsonResponse({
            member: {
              user: { id: 'user_2', username: 'bob', display_name: 'Bob', room_display_name: 'Room Bob' },
              room_display_name: 'Room Bob',
              role: 'admin',
              joined_at: '2026-05-31T14:00:00Z',
            },
          });
        case 7:
          expect(request.method).toBe('DELETE');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/members/user_2');
          return mockResponse('{}', 200);
        case 8:
          expect(request.method).toBe('PATCH');
          expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/creator');
          expect(jsonBody(request)).toEqual({ user_id: 'user_2' });
          return jsonResponse({
            room: {
              ..._roomDetailJson(),
              created_by: { id: 'user_2', username: 'bob', display_name: 'Bob' },
            },
          });
        default:
          fail(`unexpected request ${request.method} ${request.url}`);
      }
    });

    const updatedRoom = await api.updateRoom({
      roomId: 'room_1',
      name: 'New Room',
      description: 'Updated',
      visibility: 'private',
      joinPolicy: 'closed',
      aiVoiceAnnouncementsEnabled: false,
      avatarAssetId: 'asset_1',
      defaultAvatarKey: 'teal-2',
    });
    const myRoom = await api.updateMyRoomSettings({
      roomId: 'room_1',
      remarkName: 'Ops',
      notificationPolicy: 'silent',
      roomDisplayName: 'Alice Ops',
      isPinned: true,
      aiVoiceAnnouncementsEnabled: false,
    });
    await api.leaveRoom({ roomId: 'room_1', confirmDeleteIfEmpty: true });
    await api.deleteRoom({ roomId: 'room_1', confirmName: 'New Room' });
    const member = await api.updateRoomMemberRole({ roomId: 'room_1', userId: 'user_2', role: 'admin' });
    const renamedMember = await api.updateRoomMemberRoomDisplayName({
      roomId: 'room_1',
      userId: 'user_2',
      roomDisplayName: 'Room Bob',
    });
    await api.removeRoomMember({ roomId: 'room_1', userId: 'user_2' });
    const transferred = await api.transferRoomCreator({ roomId: 'room_1', userId: 'user_2' });

    expect(updatedRoom.name).toBe('New Room');
    expect(myRoom.remarkName).toBe('Ops');
    expect(myRoom.isPinned).toBe(true);
    expect(member.role).toBe('admin');
    expect(renamedMember.roomDisplayName).toBe('Room Bob');
    expect(transferred.createdBy?.id).toBe('user_2');
    expect(requests).toBe(8);
    api.close();
  });

  it('createRoom and leaveRoom send their default bodies', async () => {
    const requests: MockRequest[] = [];
    const api = makeApi((request) => {
      requests.push(request);
      if (request.method === 'POST') return jsonResponse({ room: _roomDetailJson() }, 201);
      return mockResponse('{}', 200);
    });

    await api.createRoom({ name: 'Ops', visibility: 'public', idempotencyKey: 'room-key' });
    await api.leaveRoom({ roomId: 'room_1' });

    expect(requests[0]!.uri.pathname).toBe('/api/v1/rooms');
    expect(requests[0]!.headers['idempotency-key']).toBe('room-key');
    expect(jsonBody(requests[0]!)).toEqual({ name: 'Ops', visibility: 'public' });
    expect(jsonBody(requests[1]!)).toEqual({});
    api.close();
  });

  it('updateProfile retries a transient socket write abort', async () => {
    let requests = 0;
    const api = makeApi((request) => {
      requests += 1;
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/users/me/profile');
      expect(request.headers['authorization']).toBe('Bearer token');
      expect(jsonBody(request)).toEqual({ avatar_asset_id: 'asset_1' });

      if (requests === 1) {
        throw new ClientException(
          'SocketException: Write failed (OS Error: ' + '你的主机中的软件中止了一个已建立的连接, errno = 10053)',
          request.url,
        );
      }

      return jsonResponse({
        user: {
          id: 'user_1',
          uid: '1000001',
          username: 'alice',
          display_name: 'Alice',
          avatar_url: '/assets/asset_1/avatar.png',
          default_avatar_key: 'blue-3',
        },
      });
    });

    const user = await api.updateProfile({ avatarAssetId: 'asset_1' });

    expect(requests).toBe(2);
    expect(user.avatarUrl).toBe('/assets/asset_1/avatar.png');
    api.close();
  });

  it('message moderation bodies omit a null reason', async () => {
    const bodies: unknown[] = [];
    const api = makeApi((request) => {
      bodies.push(jsonBody(request));
      if (request.uri.pathname.endsWith('/force-delete')) {
        return jsonResponse({
          message: {
            id: 'msg_1',
            room_id: 'room_1',
            sender: senderJson,
            client_message_id: 'cmsg_1',
            body: '',
            created_at: '2026-05-31T14:00:00Z',
          },
        });
      }
      return jsonResponse({ ok: true });
    });

    await api.recallMessage({ roomId: 'room_1', messageId: 'msg_1' });
    await api.recallMessage({ roomId: 'room_1', messageId: 'msg_1', reason: 'oops' });
    await api.reviewMessageRecallRequest({ roomId: 'room_1', requestId: 'req_1', approve: false });
    await api.forceDeleteMessage({ roomId: 'room_1', messageId: 'msg_1' });
    await api.moderateLiveParticipant({ roomId: 'room_1', userId: 'user_2', action: 'kick', reason: 'spam' });
    await api.updateMyLiveScreenView({ roomId: 'room_1' });

    expect(bodies).toEqual([
      {},
      { reason: 'oops' },
      { decision: 'reject' },
      { confirm: true },
      { action: 'kick', reason: 'spam' },
      { broadcaster_user_id: '' },
    ]);
    api.close();
  });

  it('getMusicBoxState parses the room snapshot', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/music-box/state');
      expect(request.headers['authorization']).toBe('Bearer token');
      return jsonResponse({
        enabled: true,
        playback: {
          state: 'playing',
          current_item_id: 'item_1',
          position_ms: 4200,
          volume: 70,
          updated_at: '2026-06-01T10:00:00Z',
        },
        queue: [
          {
            id: 'item_1',
            source: 'netease',
            track_id: 't1',
            title: 'Song A',
            artist: 'Artist A',
            duration_ms: 200000,
            status: 'ready',
            added_by_user_id: 'user_2',
          },
        ],
        usage: { used_bytes: 1024, limit_bytes: 10240 },
      });
    });

    const state = await api.getMusicBoxState('room_1');

    expect(state.enabled).toBe(true);
    expect(state.playback.state).toBe(MusicBoxPlaybackState.playing);
    expect(state.currentItem?.title).toBe('Song A');
    expect(state.usage.limitBytes).toBe(10240);
    api.close();
  });

  it('searchMusicBox sends keyword and parses results', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('GET');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/music-box/search');
      expect(request.queryParameters['keyword']).toBe('晴天');
      return jsonResponse(
        { results: [{ source: 'netease', track_id: 't9', name: '晴天', artists: ['周杰伦'] }] },
        200,
        { 'content-type': 'application/json; charset=utf-8' },
      );
    });

    const results = await api.searchMusicBox({ roomId: 'room_1', keyword: '晴天' });

    expect(results).toHaveLength(1);
    expect(results[0]!.name).toBe('晴天');
    expect(results[0]!.artists).toEqual(['周杰伦']);
    api.close();
  });

  it('queueMusicBoxTrack posts the track body and returns the snapshot', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/music-box/queue');
      expect(request.headers['idempotency-key']).toBeTruthy();
      expect(jsonBody(request)).toEqual({ source: 'netease', track_id: 't9', title: '晴天', artist: '周杰伦' });
      return jsonResponse({ enabled: true, queue: [], usage: {} });
    });

    const state = await api.queueMusicBoxTrack({
      roomId: 'room_1',
      trackId: 't9',
      title: '晴天',
      source: 'netease',
      artist: '周杰伦',
    });

    expect(state.enabled).toBe(true);
    api.close();
  });

  it('controlMusicBox posts the action and optional queue item', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/music-box/control');
      expect(jsonBody(request)).toEqual({ action: 'play_now', item_id: 'queue_1' });
      return jsonResponse({ enabled: true, queue: [], usage: {} });
    });

    const state = await api.controlMusicBox({ roomId: 'room_1', action: 'play_now', itemId: 'queue_1' });

    expect(state.enabled).toBe(true);
    api.close();
  });

  it('activateMusicBoxPlaylist posts saved source and playback intent', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/music-box/activate');
      expect(jsonBody(request)).toEqual({
        source_type: 'room_playlist',
        start_play: true,
        playlist_id: 'playlist_1',
        start_item_id: 'playlist_item_2',
      });
      return jsonResponse({
        enabled: true,
        revision: 2,
        active_source: {
          type: 'room_playlist',
          playlist_id: 'playlist_1',
          snapshot_id: 'snapshot_1',
          name: 'Room list',
          created_at: '2026-08-06T03:04:05Z',
        },
        queue: [],
        temporary_queue: [],
        usage: {},
      });
    });

    const state = await api.activateMusicBoxPlaylist({
      roomId: 'room_1',
      sourceType: MusicBoxActiveSourceType.roomPlaylist,
      playlistId: 'playlist_1',
      startItemId: 'playlist_item_2',
    });

    expect(state.activeSource.type).toBe(MusicBoxActiveSourceType.roomPlaylist);
    expect(state.activeSource.snapshotId).toBe('snapshot_1');
    expect(state.activeSource.createdAt?.getTime()).toBe(Date.UTC(2026, 7, 6, 3, 4, 5));
    expect(state.revision).toBe(2);
    api.close();
  });

  it('cloneActiveMusicBoxPlaylist pins the immutable snapshot', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/music-box/active-playlist/clone');
      expect(jsonBody(request)).toEqual({ playlist_id: 'playlist_1', snapshot_id: 'snapshot_1' });
      return jsonResponse(
        { playlist: { id: 'playlist_clone', name: '朋友的歌单 · 夜晚', description: '', revision: 1, item_count: 2 } },
        201,
        { 'content-type': 'application/json; charset=utf-8' },
      );
    });

    const playlist = await api.cloneActiveMusicBoxPlaylist({
      roomId: 'room_1',
      playlistId: 'playlist_1',
      snapshotId: 'snapshot_1',
    });

    expect(playlist.id).toBe('playlist_clone');
    expect(playlist.name).toBe('朋友的歌单 · 夜晚');
    expect(playlist.itemCount).toBe(2);
    api.close();
  });

  it('removeMusicBoxItem deletes the queue item', async () => {
    const api = makeApi((request) => {
      expect(request.method).toBe('DELETE');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/music-box/queue/item_1');
      return jsonResponse({ enabled: true, queue: [], usage: {} });
    });

    const state = await api.removeMusicBoxItem({ roomId: 'room_1', itemId: 'item_1' });

    expect(state.enabled).toBe(true);
    api.close();
  });

  it('personal playlist endpoints parse paginated metadata and items', async () => {
    let requests = 0;
    const api = makeApi((request) => {
      requests += 1;
      expect(request.method).toBe('GET');
      expect(request.headers['authorization']).toBe('Bearer token');
      if (requests === 1) {
        expect(request.uri.pathname).toBe('/api/v1/me/music-box/playlists');
        expect(request.queryParameters['page']).toBe('1');
        expect(request.queryParameters['page_size']).toBe('50');
        return jsonResponse(
          {
            playlists: [
              {
                id: 'mbp_1',
                name: '夜晚',
                description: '',
                revision: 3,
                item_count: 1,
                created_at: '2026-07-31T01:00:00Z',
                updated_at: '2026-07-31T01:05:00Z',
              },
            ],
            pagination: { page: 1, page_size: 50, total: 1, has_more: false },
            limits: { max_playlists: 50, max_playlist_items: 500 },
          },
          200,
          { 'content-type': 'application/json; charset=utf-8' },
        );
      }
      expect(request.uri.pathname).toBe('/api/v1/me/music-box/playlists/mbp_1');
      expect(request.queryParameters['keyword']).toBe('晴');
      expect(request.queryParameters['source']).toBe('netease');
      return jsonResponse(
        {
          playlist: { id: 'mbp_1', name: '夜晚', description: '', revision: 3, item_count: 1 },
          items: [
            {
              id: 'mbpi_1',
              playlist_id: 'mbp_1',
              track_id: 'track_1',
              source: 'netease',
              title: '晴天',
              artists: ['周杰伦'],
              duration_ms: 269000,
              sort_order: 10,
            },
          ],
          pagination: { page: 1, page_size: 50, total: 1, has_more: false },
        },
        200,
        { 'content-type': 'application/json; charset=utf-8' },
      );
    });

    const playlists = await api.listPersonalMusicPlaylists();
    const items = await api.getPersonalMusicPlaylist({ playlistId: 'mbp_1', keyword: '晴', source: 'netease' });

    expect(playlists.playlists[0]!.name).toBe('夜晚');
    expect(playlists.maxPlaylistItems).toBe(500);
    expect(items.items[0]!.title).toBe('晴天');
    expect(items.items[0]!.artists).toEqual(['周杰伦']);
    api.close();
  });

  it('personal playlist mutations use scoped routes and request bodies', async () => {
    const seen: string[] = [];
    const api = makeApi((request) => {
      seen.push(`${request.method} ${request.uri.pathname}`);
      const route = `${request.method} ${request.uri.pathname}`;
      switch (route) {
        case 'POST /api/v1/me/music-box/playlists':
          expect(jsonBody(request)).toEqual({ name: '夜晚' });
          return jsonResponse(
            { playlist: { id: 'mbp_1', name: '夜晚', description: '', revision: 1, item_count: 0 } },
            201,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'POST /api/v1/me/music-box/playlists/merge':
          expect(jsonBody(request)).toEqual({ name: '合并结果', playlist_ids: ['mbp_2', 'mbp_1'] });
          return jsonResponse(
            {
              playlist: { id: 'mbp_merged', name: '合并结果', description: '', revision: 1, item_count: 2 },
              merge: {
                source_item_count: 3,
                unique_item_count: 2,
                duplicate_count: 1,
                item_count: 2,
                omitted_count: 0,
                deleted_playlist_count: 2,
                retained_playlist_count: 0,
                consumed_source_item_count: 3,
                truncated: false,
              },
            },
            201,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'POST /api/v1/me/music-box/playlists/mbp_1/items':
          expect(jsonBody(request)).toEqual({
            track_id: 'track_1',
            source: 'netease',
            title: '晴天',
            artists: ['周杰伦'],
          });
          return jsonResponse(
            {
              item: {
                id: 'mbpi_1',
                playlist_id: 'mbp_1',
                track_id: 'track_1',
                source: 'netease',
                title: '晴天',
                artists: ['周杰伦'],
                sort_order: 10,
              },
            },
            201,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'PATCH /api/v1/me/music-box/playlists/mbp_1/items/order':
          expect(jsonBody(request)).toEqual({ item_id: 'mbpi_1', direction: 'down' });
          return jsonResponse({ ok: true });
        case 'PATCH /api/v1/me/music-box/playlists/order': {
          const body = jsonBody(request) as JsonMap;
          if ('playlist_ids' in body) {
            expect(body).toEqual({ playlist_ids: ['mbp_1', 'mbp_2'] });
          } else {
            expect(body).toEqual({ playlist_id: 'mbp_1', direction: 'up' });
          }
          return jsonResponse({ ok: true });
        }
        case 'PATCH /api/v1/me/music-box/playlists/mbp_1':
          expect(jsonBody(request)).toEqual({ name: '夜间精选' });
          return jsonResponse(
            { playlist: { id: 'mbp_1', name: '夜间精选', description: '', revision: 2, item_count: 1 } },
            200,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'DELETE /api/v1/me/music-box/playlists/mbp_1/items':
          expect(jsonBody(request)).toEqual({ item_ids: ['mbpi_1'] });
          return jsonResponse({ ok: true, deleted: 1 });
        case 'DELETE /api/v1/me/music-box/playlists/mbp_1':
          return jsonResponse({ ok: true });
        default:
          fail(`unexpected request: ${request.method} ${request.url}`);
      }
    });

    const playlist = await api.createPersonalMusicPlaylist({ name: '夜晚' });
    const merged = await api.mergePersonalMusicPlaylists({ name: '合并结果', playlistIds: ['mbp_2', 'mbp_1'] });
    const item = await api.addPersonalMusicPlaylistItem({
      playlistId: playlist.id,
      track: new MusicBoxSearchResult({ trackId: 'track_1', name: '晴天', artists: ['周杰伦'], source: 'netease' }),
    });
    await api.movePersonalMusicPlaylistItem({ playlistId: playlist.id, itemId: item.id, direction: 'down' });
    await api.pinPersonalMusicPlaylists({ playlistIds: [playlist.id, 'mbp_2'] });
    await api.movePersonalMusicPlaylist({ playlistId: playlist.id, direction: 'up' });
    const renamed = await api.renamePersonalMusicPlaylist({ playlistId: playlist.id, name: '夜间精选' });
    await api.deletePersonalMusicPlaylistItems({ playlistId: playlist.id, itemIds: [item.id] });
    await api.deletePersonalMusicPlaylist(playlist.id);

    expect(renamed.name).toBe('夜间精选');
    expect(merged.playlist.name).toBe('合并结果');
    expect(merged.duplicateCount).toBe(1);
    expect(merged.truncated).toBe(false);
    expect(seen).toHaveLength(9);
    api.close();
  });

  it('room playlist APIs use room-scoped routes and bodies', async () => {
    const seen: string[] = [];
    const api = makeApi((request) => {
      seen.push(`${request.method} ${request.uri.pathname}`);
      expect(request.headers['authorization']).toBe('Bearer token');
      const route = `${request.method} ${request.uri.pathname}`;
      switch (route) {
        case 'GET /api/v1/rooms/room_1/music-box/playlists':
          expect(request.queryParameters).toEqual({ page: '1', page_size: '50' });
          return jsonResponse(
            {
              playlists: [{ id: 'mbp_room_1', name: '房间精选', description: '', revision: 1, item_count: 0 }],
              pagination: { page: 1, page_size: 50, total: 1, has_more: false },
              limits: { max_playlists: 50, max_playlist_items: 500 },
            },
            200,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'POST /api/v1/rooms/room_1/music-box/playlists': {
          const body = jsonBody(request) as JsonMap;
          const imported = 'import_playlist_id' in body;
          expect(body).toEqual(
            imported ? { name: '导入精选', import_playlist_id: 'mbp_personal_1' } : { name: '新歌单' },
          );
          return jsonResponse(
            {
              playlist: {
                id: imported ? 'mbp_room_import' : 'mbp_room_2',
                name: imported ? '导入精选' : '新歌单',
                description: '',
                revision: 1,
                item_count: imported ? 1 : 0,
              },
            },
            201,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        }
        case 'POST /api/v1/rooms/room_1/music-box/playlists/merge':
          expect(jsonBody(request)).toEqual({ name: '房间合并', playlist_ids: ['mbp_room_2', 'mbp_room_1'] });
          return jsonResponse(
            {
              playlist: { id: 'mbp_room_merged', name: '房间合并', description: '', revision: 1, item_count: 1 },
              merge: {
                source_item_count: 1,
                unique_item_count: 1,
                duplicate_count: 0,
                item_count: 1,
                omitted_count: 0,
                deleted_playlist_count: 2,
                retained_playlist_count: 0,
                consumed_source_item_count: 1,
                truncated: false,
              },
            },
            201,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'POST /api/v1/rooms/room_1/music-box/playlists/mbp_room_2/clone-to-me':
          expect(request.body).toBe('');
          return jsonResponse(
            {
              playlist: {
                id: 'mbp_personal_clone_1',
                name: '房间备注名 · 新歌单',
                description: '',
                revision: 1,
                item_count: 0,
              },
            },
            201,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'POST /api/v1/rooms/room_1/music-box/playlists/mbp_room_2/items':
          expect(jsonBody(request)).toEqual({
            track_id: 'track_1',
            source: 'netease',
            title: '晴天',
            artists: ['周杰伦'],
          });
          return jsonResponse(
            {
              item: {
                id: 'mbpi_room_1',
                playlist_id: 'mbp_room_2',
                track_id: 'track_1',
                source: 'netease',
                title: '晴天',
                artists: ['周杰伦'],
                sort_order: 10,
              },
            },
            201,
            { 'content-type': 'application/json; charset=utf-8' },
          );
        case 'PATCH /api/v1/rooms/room_1/music-box/playlists/order':
          expect(jsonBody(request)).toEqual({ playlist_ids: ['mbp_room_2', 'mbp_room_1'] });
          return jsonResponse({ ok: true });
        case 'DELETE /api/v1/rooms/room_1/music-box/playlists/mbp_room_2/items':
          expect(jsonBody(request)).toEqual({ item_ids: ['mbpi_room_1'] });
          return jsonResponse({ ok: true, deleted: 1 });
        case 'DELETE /api/v1/rooms/room_1/music-box/playlists/mbp_room_2':
          return jsonResponse({ ok: true });
        default:
          fail(`unexpected request: ${request.method} ${request.url}`);
      }
    });

    const listed = await api.listRoomMusicPlaylists({ roomId: 'room_1' });
    const created = await api.createRoomMusicPlaylist({ roomId: 'room_1', name: '新歌单' });
    const imported = await api.createRoomMusicPlaylistFromPersonal({
      roomId: 'room_1',
      name: '导入精选',
      importPlaylistId: 'mbp_personal_1',
    });
    const merged = await api.mergeRoomMusicPlaylists({
      roomId: 'room_1',
      name: '房间合并',
      playlistIds: ['mbp_room_2', 'mbp_room_1'],
    });
    const item = await api.addRoomMusicPlaylistItem({
      roomId: 'room_1',
      playlistId: created.id,
      track: new MusicBoxSearchResult({ trackId: 'track_1', name: '晴天', artists: ['周杰伦'], source: 'netease' }),
    });
    const cloned = await api.cloneRoomMusicPlaylistToPersonal({ roomId: 'room_1', playlistId: created.id });
    await api.pinRoomMusicPlaylists({ roomId: 'room_1', playlistIds: [created.id, listed.playlists[0]!.id] });
    await api.deleteRoomMusicPlaylistItems({ roomId: 'room_1', playlistId: created.id, itemIds: [item.id] });
    await api.deleteRoomMusicPlaylist({ roomId: 'room_1', playlistId: created.id });

    expect(imported.itemCount).toBe(1);
    expect(cloned.name).toBe('房间备注名 · 新歌单');
    expect(merged.playlist.name).toBe('房间合并');
    expect(seen).toHaveLength(9);
    api.close();
  });

  it('playlist batch add APIs preserve selected item order', async () => {
    const seen: string[] = [];
    const api = makeApi((request) => {
      seen.push(`${request.method} ${request.uri.pathname}`);
      expect(request.headers['authorization']).toBe('Bearer token');
      const inRoom = request.uri.pathname.includes('/rooms/');
      expect(jsonBody(request)).toEqual({
        source_playlist_id: inRoom ? 'room_source' : 'personal_source',
        item_ids: ['second', 'first'],
      });
      return jsonResponse(
        {
          playlist: {
            id: inRoom ? 'room_target' : 'personal_target',
            name: '目标歌单',
            description: '',
            revision: 3,
            item_count: 500,
          },
          batch_add: {
            selected_item_count: 2,
            unique_item_count: 2,
            duplicate_count: 0,
            already_present_count: 0,
            added_item_count: 1,
            omitted_count: 1,
            truncated: true,
          },
        },
        200,
        { 'content-type': 'application/json; charset=utf-8' },
      );
    });

    const personal = await api.batchAddPersonalMusicPlaylistItems({
      sourcePlaylistId: 'personal_source',
      targetPlaylistId: 'personal_target',
      itemIds: ['second', 'first'],
    });
    const room = await api.batchAddRoomMusicPlaylistItems({
      roomId: 'room_1',
      sourcePlaylistId: 'room_source',
      targetPlaylistId: 'room_target',
      itemIds: ['second', 'first'],
    });

    expect(seen).toEqual([
      'POST /api/v1/me/music-box/playlists/personal_target/items/batch-add',
      'POST /api/v1/rooms/room_1/music-box/playlists/room_target/items/batch-add',
    ]);
    expect(personal.addedItemCount).toBe(1);
    expect(personal.omittedCount).toBe(1);
    expect(personal.truncated).toBe(true);
    expect(room.playlist.id).toBe('room_target');
    api.close();
  });

  it('close closes the injected HTTP client', () => {
    const client = new MockClient(() => jsonResponse({}));
    const api = new GangApiClient({ baseUrl: 'http://example.test/api/v1/', accessTokenProvider: tokenProvider, httpClient: client });

    api.close();

    expect(client.closed).toBe(true);
  });
});

describe('newUuid / newClientId', () => {
  it('produces RFC 4122 version-4 identifiers', () => {
    const value = newUuid();
    expect(value).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(newUuid()).not.toBe(value);
  });

  it('prefixes client ids', () => {
    expect(newClientId('cmsg')).toMatch(/^cmsg_[0-9a-f-]{36}$/);
  });
});

function _roomInviteJson(
  opts: {
    status?: string;
    roomExists?: boolean;
    invalidReason?: string | null;
    inviterRoomRole?: string;
    inviterExists?: boolean;
    roomJoined?: boolean;
    includeViewerRoomInfo?: boolean;
  } = {},
): JsonMap {
  const {
    status = 'pending',
    roomExists = true,
    invalidReason = null,
    inviterRoomRole = 'owner',
    inviterExists = true,
    roomJoined = false,
    includeViewerRoomInfo = false,
  } = opts;
  return {
    id: 'rinv_1',
    status: status,
    created_at: '2026-05-31T13:00:00Z',
    updated_at: '2026-05-31T13:00:00Z',
    room_exists: roomExists,
    inviter_exists: inviterExists,
    invalid_reason: invalidReason,
    room: {
      id: 'room_1',
      rid: '900001',
      name: 'Invite Room',
      description: 'Invite room bio',
      avatar_url: null,
      default_avatar_key: 'room-1',
      visibility: 'private',
      join_policy: 'closed',
      member_count: 1,
      online_member_count: 1,
      live_participant_count: 0,
      joined: roomJoined,
      join_state: roomJoined ? 'joined' : 'none',
      created_by: {
        id: 'user_1',
        uid: '1000001',
        username: 'alice',
        display_name: 'Alice',
        avatar_url: null,
        default_avatar_key: 'blue-3',
      },
      ...(includeViewerRoomInfo
        ? {
            personal_profile: {
              display_name: 'Alice in Room',
              avatar_url: '/room-alice.png',
              default_avatar_key: 'green-2',
            },
            my_membership: { joined_at: '2026-05-31T14:00:00Z', role: 'member' },
          }
        : {}),
    },
    inviter: {
      id: 'user_1',
      uid: '1000001',
      username: 'alice',
      display_name: 'Alice',
      avatar_url: null,
      default_avatar_key: 'blue-3',
      room_display_name: 'Alice in Invite Room',
      room_role: inviterRoomRole,
    },
  };
}

function _roomBlacklistEntryJson(): JsonMap {
  return {
    user: {
      id: 'user_2',
      uid: '1000002',
      username: 'blocked_user',
      display_name: 'Blocked User',
      avatar_url: null,
      default_avatar_key: 'red-2',
    },
    blocked_by: {
      id: 'user_1',
      uid: '1000001',
      username: 'alice',
      display_name: 'Alice',
      avatar_url: null,
      default_avatar_key: 'blue-3',
    },
    created_at: '2026-05-31T13:00:00Z',
  };
}

function _roomApplicationJson(opts: { status?: string; reason?: string; reviewerExists?: boolean } = {}): JsonMap {
  const { status = 'pending', reason = '', reviewerExists = true } = opts;
  const reviewed = status === 'approved' || status === 'rejected';
  return {
    id: 'jrq_1',
    status: status,
    reason: reason,
    created_at: '2026-05-30T13:00:00Z',
    updated_at: '2026-05-31T13:00:00Z',
    reviewed_at: reviewed ? '2026-05-31T13:00:00Z' : null,
    room: _roomInviteJson()['room'],
    reviewer_exists: reviewerExists,
    reviewer: reviewed
      ? {
          id: 'user_1',
          uid: '1000001',
          username: 'alice',
          display_name: 'Alice',
          avatar_url: null,
          default_avatar_key: 'blue-3',
          room_display_name: 'Alice in Invite Room',
          room_role: 'owner',
        }
      : null,
  };
}

function _roomEventNotificationJson(opts: { readAt?: string | null } = {}): JsonMap {
  return {
    id: 'rnot_1',
    type: 'role_promoted',
    created_at: '2026-06-01T13:00:00Z',
    read_at: opts.readAt ?? null,
    room_exists: true,
    actor_exists: true,
    from_role: 'member',
    to_role: 'admin',
    message_id: 'msg_mention_1',
    message_preview: '@Alice hello',
    room: _roomInviteJson({ roomJoined: true })['room'],
    actor: {
      id: 'user_1',
      uid: '1000001',
      username: 'alice',
      display_name: 'Alice',
      avatar_url: null,
      default_avatar_key: 'blue-3',
      room_display_name: 'Alice in Invite Room',
      room_role: 'owner',
    },
  };
}

function _roomDetailJson(): JsonMap {
  return {
    id: 'room_1',
    rid: '900001',
    name: 'Invite Room',
    avatar_url: null,
    default_avatar_key: 'room-1',
    member_count: 2,
    online_member_count: 1,
    created_by: { id: 'user_1', uid: '1000001', username: 'alice', display_name: 'Alice' },
    my_membership: { joined_at: '2026-05-31T14:00:00Z', role: 'member' },
    live: { room_id: 'room_1', participant_count: 0, participants: [], updated_at: '2026-05-31T14:00:00Z' },
    created_at: '2026-05-31T13:00:00Z',
    updated_at: '2026-05-31T14:00:00Z',
  };
}

function _roomCardSearchJson(): JsonMap {
  return {
    id: 'room_1',
    rid: '900001',
    name: 'Alpha Room',
    avatar_url: null,
    default_avatar_key: 'room-1',
    visibility: 'public',
    join_policy: 'open',
    description: 'Searchable room',
    notification_policy: 'all',
    member_count: 3,
    online_member_count: 1,
    live_participant_count: 0,
    live_avatar_preview: [],
    last_message: null,
    unread_count: 0,
    updated_at: '2026-05-31T14:00:00Z',
  };
}

function _publicRoomSearchJson(): JsonMap {
  return {
    id: 'room_2',
    rid: '900002',
    name: 'Alpha Public',
    avatar_url: null,
    default_avatar_key: 'room-2',
    visibility: 'public',
    join_policy: 'open',
    member_count: 2,
    online_member_count: 0,
    live_participant_count: 0,
    joined: false,
    join_state: 'none',
  };
}

function _searchRoomContextJson(): JsonMap {
  return { id: 'room_1', rid: '900001', name: 'Alpha Room', avatar_url: null, default_avatar_key: 'room-1' };
}

function _messageSearchJson(opts: { type?: string; body?: string; attachments?: JsonMap[] } = {}): JsonMap {
  const { type = 'text', body = 'hello', attachments = [] } = opts;
  return {
    id: 'msg_1',
    room_id: 'room_1',
    sender: { id: 'user_1', username: 'alice', display_name: 'Alice', default_avatar_key: 'blue-3' },
    client_message_id: 'cmsg_1',
    type: type,
    body: body,
    attachments: attachments,
    created_at: '2026-05-31T14:00:00Z',
  };
}

function _liveJoinJson(): JsonMap {
  const participant = {
    live_session_id: 'live_1',
    user: { id: 'user_1', username: 'alice', display_name: 'Alice' },
    joined_at: '2026-05-31T14:00:00Z',
    mic_muted: true,
    headphones_muted: false,
    voice_blocked: false,
    camera_on: false,
    screen_sharing: false,
    connection_state: 'joining',
  };

  return {
    livekit: {
      server_url: 'wss://voice.example.com',
      token: 'livekit-token',
      token_expires_at: '2026-05-31T14:10:00Z',
      room_name: 'room_1',
    },
    participant: participant,
    live: { room_id: 'room_1', participant_count: 1, participants: [participant], updated_at: '2026-05-31T14:00:00Z' },
  };
}

function _stickerPackJson(opts: { name?: string; stickers?: JsonMap[] } = {}): JsonMap {
  const { name = 'My Stickers', stickers = [] } = opts;
  return {
    pack: {
      id: 'stkp_1',
      scope: 'personal',
      room_id: null,
      name: name,
      sort_order: 10,
      updated_at: '2026-06-02T08:00:00Z',
      stickers: stickers,
    },
  };
}

function _stickerJson(opts: { id?: string; name?: string; sortOrder?: number } = {}): JsonMap {
  const { id = 'stk_1', name = 'ok', sortOrder = 10 } = opts;
  const assetId = `asset_${id}`;
  return {
    id: id,
    name: name,
    sort_order: sortOrder,
    asset: {
      id: assetId,
      url: `/assets/${assetId}/ok.webp`,
      thumbnail_url: `/assets/${assetId}/ok.webp`,
      mime_type: 'image/webp',
      width: 128,
      height: 128,
      created_at: '2026-06-02T07:59:00Z',
    },
  };
}

function _personalStickerPacksJson(opts: { linkedAssetId?: string | null } = {}): JsonMap {
  const { linkedAssetId = null } = opts;
  return {
    packs: [
      {
        id: 'stkp_1',
        scope: 'personal',
        room_id: null,
        name: 'My Stickers',
        sort_order: 10,
        updated_at: '2026-06-02T08:00:00Z',
        stickers:
          linkedAssetId != null
            ? [
                {
                  id: 'stk_2',
                  name: 'hi',
                  sort_order: 10,
                  asset: {
                    id: linkedAssetId,
                    url: `/assets/${linkedAssetId}/hi.webp`,
                    thumbnail_url: `/assets/${linkedAssetId}/hi.webp`,
                    mime_type: 'image/webp',
                  },
                },
              ]
            : [],
      },
    ],
  };
}
