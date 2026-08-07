import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/protocol/server_time_header.dart';

void main() {
  test('getAppVersion fetches current server version metadata', () async {
    final serverTimes = <String?>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      onServerTime: serverTimes.add,
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/app/version');
        expect(request.headers['authorization'], 'Bearer token');
        return http.Response(
          jsonEncode({
            'latest_version': '1.2.3',
            'minimum_supported_version': '1.0.0',
            'release_notes': 'ok',
            'download_url': 'https://example.test/download',
          }),
          200,
          headers: {gangServerTimeHeader: '2026-07-08T09:00:00Z'},
        );
      }),
    );

    final info = await api.getAppVersion();

    expect(info.latestVersion, '1.2.3');
    expect(info.minimumSupportedVersion, '1.0.0');
    expect(info.releaseNotes, 'ok');
    expect(info.downloadUrl, 'https://example.test/download');
    expect(serverTimes, ['2026-07-08T09:00:00Z']);
    api.close();
  });

  test(
    'reports request round-trip latency after a successful response',
    () async {
      final latencies = <Duration>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        onRequestLatency: latencies.add,
        httpClient: MockClient((request) async {
          await Future<void>.delayed(const Duration(milliseconds: 2));
          return http.Response(
            jsonEncode({
              'latest_version': '1.2.3',
              'minimum_supported_version': '1.0.0',
              'release_notes': 'ok',
              'download_url': 'https://example.test/download',
            }),
            200,
          );
        }),
      );

      await api.getAppVersion();

      expect(latencies, hasLength(1));
      expect(latencies.single.inMicroseconds, greaterThan(0));
      api.close();
    },
  );

  test(
    'listMessages retries once after a transient closed connection',
    () async {
      var requests = 0;
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requests += 1;
          expect(request.method, 'GET');
          expect(request.url.path, '/api/v1/rooms/room_1/messages');
          expect(request.url.queryParameters['limit'], '50');
          expect(request.headers['authorization'], 'Bearer token');

          if (requests == 1) {
            throw http.ClientException(
              'Connection closed before full header was received',
              request.url,
            );
          }

          return http.Response(
            jsonEncode({
              'messages': [
                {
                  'id': 'msg_1',
                  'room_id': 'room_1',
                  'sender': {
                    'id': 'user_1',
                    'username': 'alice',
                    'display_name': 'Alice',
                  },
                  'client_message_id': 'cmsg_1',
                  'body': 'hello',
                  'created_at': '2026-05-31T14:00:00Z',
                },
              ],
              'has_more': false,
              'next_before': null,
            }),
            200,
          );
        }),
      );

      final page = await api.listMessages(roomId: 'room_1');

      expect(requests, 2);
      expect(page.messages.single.body, 'hello');
      api.close();
    },
  );

  test(
    'sendMessage retries a dropped connection with one idempotency key',
    () async {
      var requests = 0;
      final idempotencyKeys = <String>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requests += 1;
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/rooms/room_1/messages');
          expect(
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
            {'client_message_id': 'cmsg_1', 'body': 'hello'},
          );
          idempotencyKeys.add(request.headers['idempotency-key']!);

          if (requests == 1) {
            throw http.ClientException('Connection reset by peer', request.url);
          }

          return http.Response(
            jsonEncode({
              'message': {
                'id': 'msg_1',
                'room_id': 'room_1',
                'sender': {
                  'id': 'user_1',
                  'username': 'alice',
                  'display_name': 'Alice',
                },
                'client_message_id': 'cmsg_1',
                'body': 'hello',
                'created_at': '2026-05-31T14:00:00Z',
              },
            }),
            201,
          );
        }),
      );

      final message = await api.sendMessage(
        roomId: 'room_1',
        clientMessageId: 'cmsg_1',
        body: 'hello',
      );

      expect(requests, 2);
      expect(idempotencyKeys, hasLength(2));
      expect(idempotencyKeys.first, isNotEmpty);
      expect(idempotencyKeys.first, idempotencyKeys.last);
      expect(message.body, 'hello');
      api.close();
    },
  );

  test('listMessageHistory sends all filters and parses the page', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/rooms/room_1/message-history');
        expect(request.url.queryParameters, {
          'limit': '25',
          'category': 'links',
          'query': 'needle',
          'sender_user_id': 'user_2',
          'start_at': '2026-07-01T00:00:00.000Z',
          'end_at': '2026-07-14T00:00:00.000Z',
          'before': 'msg_older',
        });
        return http.Response(
          jsonEncode({
            'messages': [
              {
                'id': 'msg_1',
                'room_id': 'room_1',
                'sender': {
                  'id': 'user_2',
                  'username': 'alice',
                  'display_name': 'Alice',
                },
                'client_message_id': 'cmsg_1',
                'body': 'needle',
                'created_at': '2026-07-10T12:00:00Z',
              },
            ],
            'has_more': true,
            'next_before': 'msg_1',
          }),
          200,
        );
      }),
    );
    addTearDown(api.close);

    final page = await api.listMessageHistory(
      roomId: 'room_1',
      query: '  needle  ',
      category: 'links',
      senderUserId: 'user_2',
      startAt: DateTime.utc(2026, 7, 1),
      endAt: DateTime.utc(2026, 7, 14),
      limit: 25,
      before: 'msg_older',
    );

    expect(page.messages.single.id, 'msg_1');
    expect(page.hasMore, isTrue);
    expect(page.nextBefore, 'msg_1');
  });

  test('hideMessageHistory confirms current-account record deletion', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/message-history/hide');
        expect(jsonDecode(utf8.decode(request.bodyBytes)), {
          'message_ids': ['msg_1', 'msg_2'],
          'confirm': true,
        });
        return http.Response(jsonEncode({'ok': true, 'deleted_count': 2}), 200);
      }),
    );
    addTearDown(api.close);

    final deleted = await api.hideMessageHistory(
      roomId: 'room_1',
      messageIds: const ['msg_1', 'msg_2'],
    );

    expect(deleted, 2);
  });

  test(
    'sendMessage keeps Chinese JSON UTF-8 without response charset',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/rooms/room_1/messages');
          expect(request.headers['accept'], 'application/json');
          expect(
            request.headers['content-type'],
            'application/json; charset=utf-8',
          );
          expect(
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
            {'client_message_id': 'cmsg_1', 'body': '你好，世界'},
          );

          return http.Response.bytes(
            utf8.encode(
              jsonEncode({
                'message': {
                  'id': 'msg_1',
                  'room_id': 'room_1',
                  'sender': {
                    'id': 'user_1',
                    'username': 'alice',
                    'display_name': 'Alice',
                  },
                  'client_message_id': 'cmsg_1',
                  'body': '服务端中文',
                  'created_at': '2026-05-31T14:00:00Z',
                },
              }),
            ),
            201,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final message = await api.sendMessage(
        roomId: 'room_1',
        clientMessageId: 'cmsg_1',
        body: '你好，世界',
      );

      expect(message.body, '服务端中文');
      api.close();
    },
  );

  test('sendMessage can send and parse a sticker attachment', () async {
    final stickerAsset = UploadedAsset(
      id: 'asset_1',
      url: '/assets/asset_1/ok.webp',
      thumbnailUrl: null,
      mimeType: 'image/webp',
    );
    final stickerAttachment = MessageAttachment(
      type: 'sticker',
      stickerId: 'sticker_1',
      name: 'ok',
      asset: stickerAsset,
    );
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/messages');
        expect(
          jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          {
            'client_message_id': 'cmsg_1',
            'body': '[ok]',
            'type': 'sticker',
            'attachments': [stickerAttachment.toJson()],
          },
        );

        return http.Response.bytes(
          utf8.encode(
            jsonEncode({
              'message': {
                'id': 'msg_1',
                'room_id': 'room_1',
                'sender': {
                  'id': 'user_1',
                  'username': 'alice',
                  'display_name': 'Alice',
                },
                'client_message_id': 'cmsg_1',
                'type': 'sticker',
                'body': '[ok]',
                'attachments': [stickerAttachment.toJson()],
                'created_at': '2026-05-31T14:00:00Z',
              },
            }),
          ),
          201,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final message = await api.sendMessage(
      roomId: 'room_1',
      clientMessageId: 'cmsg_1',
      body: '[ok]',
      type: 'sticker',
      attachments: [stickerAttachment],
    );

    expect(message.type, 'sticker');
    expect(message.stickerAttachment?.asset?.url, '/assets/asset_1/ok.webp');
    api.close();
  });

  test('playlist messages send an id and parse the server snapshot', () async {
    const requestedAttachment = MessageAttachment(
      type: 'playlist',
      playlistId: 'playlist_1',
    );
    final snapshot = {
      'id': 'playlist_1',
      'name': '夜晚',
      'description': '',
      'item_count': 1,
      'created_at': '2026-08-07T09:30:00Z',
      'creator': {
        'id': 'user_1',
        'username': 'alice',
        'display_name': 'Alice',
        'room_display_name': '房间里的 Alice',
        'avatar_url': null,
        'default_avatar_key': 'blue-1',
      },
      'items': [
        {
          'id': 'item_1',
          'playlist_id': 'playlist_1',
          'track_id': 'track_1',
          'source': 'netease',
          'title': '第一首歌',
          'artists': ['歌手'],
          'duration_ms': 180000,
          'sort_order': 10,
          'created_at': '2026-08-07T09:31:00Z',
        },
      ],
    };
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/messages');
        expect(
          jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          {
            'client_message_id': 'cmsg_playlist_1',
            'body': '',
            'type': 'playlist',
            'attachments': [requestedAttachment.toJson()],
          },
        );
        return http.Response.bytes(
          utf8.encode(
            jsonEncode({
              'message': {
                'id': 'msg_playlist_1',
                'room_id': 'room_1',
                'sender': {
                  'id': 'user_1',
                  'username': 'alice',
                  'display_name': 'Alice',
                },
                'client_message_id': 'cmsg_playlist_1',
                'type': 'playlist',
                'body': '[歌单] 夜晚',
                'attachments': [
                  {'type': 'playlist', 'playlist': snapshot},
                ],
                'created_at': '2026-08-07T09:32:00Z',
              },
            }),
          ),
          201,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final message = await api.sendMessage(
      roomId: 'room_1',
      clientMessageId: 'cmsg_playlist_1',
      body: '',
      type: 'playlist',
      attachments: const [requestedAttachment],
    );

    expect(message.type, 'playlist');
    expect(message.playlistAttachment?.playlist?.name, '夜晚');
    expect(
      message.playlistAttachment?.playlist?.creator.roomDisplayName,
      '房间里的 Alice',
    );
    expect(
      message.playlistAttachment?.playlist?.items.single.trackId,
      'track_1',
    );
    api.close();
  });

  test('shared playlist clone uses the message-scoped endpoint', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/api/v1/rooms/room_1/messages/msg_1/playlist/clone-to-me',
        );
        return http.Response.bytes(
          utf8.encode(
            jsonEncode({
              'playlist': {
                'id': 'clone_1',
                'name': '朋友的歌单 · 夜晚',
                'description': '',
                'revision': 1,
                'item_count': 1,
                'created_at': '2026-08-07T09:35:00Z',
                'updated_at': '2026-08-07T09:35:00Z',
              },
            }),
          ),
          201,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final playlist = await api.cloneSharedMusicPlaylistToPersonal(
      roomId: 'room_1',
      messageId: 'msg_1',
    );

    expect(playlist.id, 'clone_1');
    expect(playlist.name, '朋友的歌单 · 夜晚');
    api.close();
  });

  test('Message parses rich sender fields for profile popups', () {
    final message = Message.fromJson({
      'id': 'msg_1',
      'room_id': 'room_1',
      'sender': {
        'id': 'user_1',
        'uid': '1000001',
        'username': 'alice',
        'display_name': 'Alice',
        'bio': 'Building quietly.',
        'gender': 'female',
        'email': 'alice@example.test',
        'email_public': true,
        'phone_number': '+8613800000000',
        'phone_number_public': true,
        'avatar_url': '/assets/asset_1/avatar.png',
        'default_avatar_key': 'rose-2',
        'is_superuser': true,
        'common_rooms': [
          {
            'room_id': 'room_2',
            'rid': 'R10002',
            'room_name': 'Side Room',
            'visibility': 'private',
            'room_username': 'Alice Side',
            'membership_role': 'member',
          },
        ],
      },
      'sender_room_username': 'A. Chen',
      'sender_room_role': 'admin',
      'sender_common_rooms': [
        {
          'room_id': 'room_3',
          'rid': 'R10003',
          'room_name': 'Admin Room',
          'visibility': 'public',
          'room_username': 'Alice Admin',
          'membership_role': 'admin',
        },
      ],
      'client_message_id': 'cmsg_1',
      'body': 'hello',
      'created_at': '2026-05-31T14:00:00Z',
    });

    final sender = message.sender;
    expect(sender.uid, '1000001');
    expect(sender.bio, 'Building quietly.');
    expect(sender.gender, 'female');
    expect(sender.email, 'alice@example.test');
    expect(sender.emailPublic, isTrue);
    expect(sender.phoneNumber, '+8613800000000');
    expect(sender.phoneNumberPublic, isTrue);
    expect(sender.roomDisplayName, 'A. Chen');
    expect(sender.roomRole, 'admin');
    expect(sender.isSuperuser, isTrue);
    expect(sender.commonRooms, hasLength(1));
    expect(sender.commonRooms.single.id, 'room_3');
    expect(sender.commonRooms.single.rid, 'R10003');
    expect(sender.commonRooms.single.name, 'Admin Room');
    expect(sender.commonRooms.single.visibility, 'public');
    expect(sender.commonRooms.single.roomDisplayName, 'Alice Admin');
    expect(sender.commonRooms.single.roomRole, 'admin');
  });

  test('RoomMember parses room names, remarks, presence, and role fields', () {
    final page = RoomMemberPage.fromJson({
      'next_cursor': 'cursor_2',
      'members': [
        {
          'user': {
            'id': 'user_1',
            'uid': '1000001',
            'username': 'alice',
            'display_name': 'Alice',
            'avatar_url': '/assets/asset_1/avatar.png',
            'default_avatar_key': 'rose-2',
            'is_superuser': true,
          },
          'role': 'admin',
          'room_display_name': 'Alice In Room',
          'remark_name': 'Ops lead',
          'text_muted_until': 'permanent',
          'presence': {'status': 'connected'},
          'joined_at': '2026-05-31T14:00:00Z',
        },
      ],
    });

    final member = page.members.single;
    expect(page.nextCursor, 'cursor_2');
    expect(member.user.uid, '1000001');
    expect(member.user.roomDisplayName, 'Alice In Room');
    expect(member.user.roomRole, 'admin');
    expect(member.user.isSuperuser, isTrue);
    expect(member.roomDisplayName, 'Alice In Room');
    expect(member.remarkName, 'Ops lead');
    expect(member.textMutedUntil, 'permanent');
    expect(member.isOnline, isTrue);
    expect(
      member.joinedAt.toUtc().toIso8601String(),
      '2026-05-31T14:00:00.000Z',
    );
  });

  test('RoomMember parses online state from nested user payload', () {
    final member = RoomMember.fromJson({
      'user': {
        'id': 'user_1',
        'uid': '1000001',
        'username': 'alice',
        'display_name': 'Alice',
        'is_online': true,
      },
      'role': 'member',
      'joined_at': '2026-05-31T14:00:00Z',
    });

    expect(member.isOnline, isTrue);
    expect(member.user.isOnline, isTrue);
  });

  test('listRoomMembers requests room members with pagination', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/rooms/room_1/members');
        expect(request.url.queryParameters, {
          'limit': '100',
          'cursor': 'cursor_1',
        });
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(
          jsonEncode({
            'members': [
              {
                'user': {
                  'id': 'user_1',
                  'uid': '1000001',
                  'username': 'alice',
                  'display_name': 'Alice',
                },
                'role': 'owner',
                'joined_at': '2026-05-31T14:00:00Z',
              },
            ],
            'next_cursor': null,
          }),
          200,
        );
      }),
    );

    final page = await api.listRoomMembers(
      'room_1',
      limit: 100,
      cursor: 'cursor_1',
    );

    expect(page.members.single.role, 'owner');
    expect(page.members.single.user.username, 'alice');
    api.close();
  });

  test('getRoomMemberProfile parses signature and profile rooms', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/rooms/room_1/members/user_2/profile');
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(
          jsonEncode({
            'profile': {
              'user': {
                'id': 'user_2',
                'uid': '1000002',
                'username': 'bob',
                'display_name': 'Bob',
                'avatar_url': '/global.png',
                'default_avatar_key': 'blue-2',
                'bio': 'Ship quietly',
                'gender': 'female',
                'is_online': true,
                'common_rooms': [
                  {
                    'id': 'room_2',
                    'rid': '900002',
                    'name': 'Ops',
                    'remark_name': 'Night Ops',
                    'avatar_url': '/room.png',
                    'default_avatar_key': 'room-2',
                    'visibility': 'private',
                    'room_role': 'admin',
                  },
                ],
              },
              'room_display_name': 'Room Bob',
              'room_avatar_url': '/room-bob.png',
              'room_default_avatar_key': 'green-2',
              'role': 'admin',
              'joined_at': '2026-05-31T14:00:00Z',
            },
          }),
          200,
        );
      }),
    );

    final profile = await api.getRoomMemberProfile(
      roomId: 'room_1',
      userId: 'user_2',
    );

    expect(profile.user.bio, 'Ship quietly');
    expect(profile.user.gender, 'female');
    expect(profile.user.roomDisplayName, 'Room Bob');
    expect(profile.user.roomRole, 'admin');
    expect(profile.user.isOnline, isTrue);
    expect(profile.user.avatarUrl, '/global.png');
    expect(profile.user.commonRooms.single.remarkName, 'Night Ops');
    expect(profile.user.commonRooms.single.avatarUrl, '/room.png');
    expect(profile.user.commonRooms.single.defaultAvatarKey, 'room-2');
    api.close();
  });

  test('getUserProfile calls the global profile endpoint', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/users/user_2/profile');
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(
          jsonEncode({
            'profile': {
              'user': {
                'id': 'user_2',
                'uid': '1000002',
                'username': 'bob',
                'display_name': 'Bob',
                'avatar_url': '/global.png',
                'default_avatar_key': 'blue-2',
                'bio': 'Global profile',
                'gender': 'male',
                'is_online': true,
                'common_rooms': [
                  {
                    'id': 'room_2',
                    'rid': '900002',
                    'name': 'Ops',
                    'default_avatar_key': 'room-2',
                    'visibility': 'private',
                    'room_role': 'admin',
                  },
                ],
              },
            },
          }),
          200,
        );
      }),
    );

    final profile = await api.getUserProfile('user_2');

    expect(profile.username, 'bob');
    expect(profile.bio, 'Global profile');
    expect(profile.gender, 'male');
    expect(profile.isOnline, isTrue);
    expect(profile.commonRooms.single.name, 'Ops');
    expect(profile.commonRooms.single.roomRole, 'admin');
    api.close();
  });

  test(
    'searchUsers calls the user search endpoint and parses summaries',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/api/v1/users/search');
          expect(request.url.queryParameters, {'q': '1000001', 'limit': '20'});
          expect(request.headers['authorization'], 'Bearer token');

          return http.Response(
            jsonEncode({
              'users': [
                {
                  'id': 'user_1',
                  'uid': '1000001',
                  'username': 'alice',
                  'display_name': 'Alice',
                  'avatar_url': '/assets/asset_1/avatar.png',
                  'default_avatar_key': 'rose-2',
                  'is_superuser': true,
                },
              ],
              'next_cursor': null,
            }),
            200,
          );
        }),
      );

      final users = await api.searchUsers(query: '1000001');

      expect(users.single.id, 'user_1');
      expect(users.single.uid, '1000001');
      expect(users.single.avatarUrl, '/assets/asset_1/avatar.png');
      expect(users.single.isSuperuser, isTrue);
      api.close();
    },
  );

  test(
    'searchUsersPage forwards cursor and parses pagination metadata',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/api/v1/users/search');
          expect(request.url.queryParameters, {
            'q': 'alice',
            'limit': '8',
            'cursor': '8',
            'include_suspended': 'true',
          });
          return http.Response(
            jsonEncode({
              'users': [
                {
                  'id': 'user_1',
                  'uid': '1000001',
                  'username': 'alice',
                  'display_name': 'Alice',
                  'default_avatar_key': 'blue-3',
                  'is_suspended': true,
                },
              ],
              'next_cursor': '16',
              'total_count': 21,
            }),
            200,
          );
        }),
      );

      final page = await api.searchUsersPage(
        query: 'alice',
        limit: 8,
        cursor: '8',
        includeSuspended: true,
      );

      expect(page.users.single.username, 'alice');
      expect(page.users.single.isSuspended, isTrue);
      expect(page.nextCursor, '16');
      expect(page.totalCount, 21);
      api.close();
    },
  );

  test('superuser user settings APIs use target-only endpoints', () async {
    final requests = <http.Request>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests.add(request);
        final userJson = {
          'id': 'user_2',
          'uid': '1000002',
          'username': 'bob',
          'display_name': 'Bob',
          'bio': '',
          'gender': 'secret',
          'email': 'bob@example.com',
          'email_verified': true,
          'email_public': false,
          'phone_number_public': false,
          'default_avatar_key': 'blue-3',
          'language': 'zh-Hans',
          'is_superuser': false,
          'status': 'active',
        };
        if (request.url.path.endsWith('/audio-settings')) {
          return http.Response(
            jsonEncode({
              'audio_settings': {
                'default_audio_input_volume': 90,
                'default_audio_output_volume': 80,
                'live_mic_input_volume': 70,
                'live_voice_output_volume': 60,
                'live_screen_share_output_volume': 50,
                'live_music_output_volume': 40,
              },
            }),
            200,
          );
        }
        if (request.url.path.endsWith('/password')) {
          return http.Response(jsonEncode({'ok': true}), 200);
        }
        return http.Response(jsonEncode({'user': userJson}), 200);
      }),
    );

    final user = await api.getForcedUserSettings('user_2');
    final updated = await api.updateForcedUserSettings(
      userId: 'user_2',
      displayName: 'Bob',
      emailVerified: true,
    );
    final audio = await api.getForcedUserAudioSettings('user_2');
    await api.updateForcedUserAudioSettings(userId: 'user_2', settings: audio);
    await api.forceResetUserPassword(
      userId: 'user_2',
      newPassword: 'new password',
    );

    expect(user.id, 'user_2');
    expect(updated.emailVerified, isTrue);
    expect(audio.liveMusicOutputVolume, 40);
    expect(requests.map((request) => request.method), [
      'GET',
      'PATCH',
      'GET',
      'PATCH',
      'POST',
    ]);
    final passwordBody = jsonDecode(requests.last.body) as Map<String, Object?>;
    expect(passwordBody, {'new_password': 'new password'});
    expect(passwordBody, isNot(contains('current_password')));
    api.close();
  });

  test('search calls global search endpoint and parses categories', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/search');
        expect(request.url.queryParameters, {'q': 'alpha', 'limit': '8'});
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(
          jsonEncode({
            'my_rooms': [_roomCardSearchJson()],
            'public_rooms': [_publicRoomSearchJson()],
            'messages': [
              {
                'room': _searchRoomContextJson(),
                'message': _messageSearchJson(body: 'alpha roadmap'),
              },
            ],
            'files': [
              {
                'room': _searchRoomContextJson(),
                'message': _messageSearchJson(
                  type: 'file',
                  body: 'alpha.pdf',
                  attachments: [
                    {
                      'type': 'file',
                      'name': 'alpha.pdf',
                      'asset': {
                        'id': 'asset_1',
                        'url': '/assets/alpha.pdf',
                        'thumbnail_url': null,
                        'mime_type': 'application/pdf',
                        'filename': 'alpha.pdf',
                        'size_bytes': 2048,
                      },
                    },
                  ],
                ),
              },
            ],
          }),
          200,
        );
      }),
    );

    final results = await api.search(query: 'alpha');

    expect(results.myRooms.single.name, 'Alpha Room');
    expect(results.publicRooms.single.joined, isFalse);
    expect(results.messages.single.room.name, 'Alpha Room');
    expect(results.messages.single.message.body, 'alpha roadmap');
    expect(
      results.files.single.message.fileAttachments.single.asset?.filename,
      'alpha.pdf',
    );
    api.close();
  });

  test('inviteMember posts the target user id and parses the invite', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/invites');
        expect(request.headers['authorization'], 'Bearer token');
        expect(request.headers['idempotency-key'], isNotEmpty);
        expect(
          jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          {'user_id': 'user_2'},
        );

        return http.Response(jsonEncode({'invite': _roomInviteJson()}), 201);
      }),
    );

    final invite = await api.inviteMember(roomId: 'room_1', userId: 'user_2');

    expect(invite.id, 'rinv_1');
    expect(invite.status, 'pending');
    expect(invite.room.name, 'Invite Room');
    expect(invite.inviter.username, 'alice');
    expect(invite.inviter.roomRole, 'owner');
    api.close();
  });

  test('listRoomBlacklist requests and parses blocked users', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/rooms/room_1/blacklist');
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(
          jsonEncode({
            'blacklist': [_roomBlacklistEntryJson()],
            'next_cursor': null,
          }),
          200,
        );
      }),
    );

    final entries = await api.listRoomBlacklist('room_1');

    expect(entries.single.user.username, 'blocked_user');
    expect(entries.single.blockedBy?.username, 'alice');
    api.close();
  });

  test('blockRoomUser posts target id and parses entry', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/blacklist');
        expect(request.headers['authorization'], 'Bearer token');
        expect(request.headers['idempotency-key'], isNotEmpty);
        expect(
          jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          {'user_id': 'user_2'},
        );

        return http.Response(
          jsonEncode({'entry': _roomBlacklistEntryJson()}),
          201,
        );
      }),
    );

    final entry = await api.blockRoomUser(roomId: 'room_1', userId: 'user_2');

    expect(entry.user.id, 'user_2');
    api.close();
  });

  test('unblockRoomUser deletes the blocked user entry', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(request.url.path, '/api/v1/rooms/room_1/blacklist/user_2');
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(jsonEncode({'ok': true}), 200);
      }),
    );

    await api.unblockRoomUser(roomId: 'room_1', userId: 'user_2');

    api.close();
  });

  test('joinRoom posts an optional application reason', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/join');
        expect(request.headers['authorization'], 'Bearer token');
        expect(request.headers['idempotency-key'], isNotEmpty);
        expect(
          jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          {'reason': 'Let me in'},
        );

        return http.Response(
          jsonEncode({
            'join_request': {
              'id': 'jrq_1',
              'room_id': 'room_1',
              'status': 'pending',
              'reason': 'Let me in',
              'created_at': '2026-01-01T00:00:00Z',
            },
          }),
          202,
        );
      }),
    );

    final result = await api.joinRoom('room_1', reason: '  Let me in  ');

    expect(result.pending, isTrue);
    api.close();
  });

  test(
    'listRoomInvites requests pending invites for the current user',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/api/v1/room-invites');
          expect(request.url.queryParameters, {'status': 'pending'});
          expect(request.headers['authorization'], 'Bearer token');

          return http.Response(
            jsonEncode({
              'invites': [_roomInviteJson()],
              'next_cursor': null,
            }),
            200,
          );
        }),
      );

      final invites = await api.listRoomInvites();

      expect(invites.single.id, 'rinv_1');
      expect(invites.single.room.rid, '900001');
      api.close();
    },
  );

  test('listRoomInvites can request all invites for notifications', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/room-invites');
        expect(request.url.queryParameters, {'status': 'all'});
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(
          jsonEncode({
            'invites': [
              _roomInviteJson(
                status: 'pending',
                roomExists: false,
                inviterExists: false,
                inviterRoomRole: 'left',
                invalidReason: 'room_missing',
              ),
            ],
            'next_cursor': null,
          }),
          200,
        );
      }),
    );

    final invites = await api.listRoomInvites(status: 'all');

    expect(invites.single.status, 'pending');
    expect(invites.single.roomExists, isFalse);
    expect(invites.single.inviterExists, isFalse);
    expect(invites.single.room.isDeleted, isTrue);
    expect(invites.single.inviter.isDeleted, isTrue);
    expect(invites.single.invalidReason, 'room_missing');
    expect(invites.single.inviter.roomRole, 'left');
    api.close();
  });

  test('room invite notification parses room profile card fields', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/room-invites');

        return http.Response(
          jsonEncode({
            'invites': [
              _roomInviteJson(roomJoined: true, includeViewerRoomInfo: true),
            ],
            'next_cursor': null,
          }),
          200,
        );
      }),
    );

    final invites = await api.listRoomInvites(status: 'all');
    final room = invites.single.room;

    expect(room.description, 'Invite room bio');
    expect(room.createdBy?.id, 'user_1');
    expect(room.personalProfile.displayName, 'Alice in Room');
    expect(room.myMembership?.role, 'member');
    api.close();
  });

  test(
    'reviewRoomInvite accepts an invite and parses the joined room',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'PATCH');
          expect(request.url.path, '/api/v1/room-invites/rinv_1');
          expect(request.headers['authorization'], 'Bearer token');
          expect(
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
            {'decision': 'accept'},
          );

          return http.Response(
            jsonEncode({
              'ok': true,
              'invite': _roomInviteJson(status: 'accepted'),
              'room': _roomDetailJson(),
            }),
            200,
          );
        }),
      );

      final result = await api.reviewRoomInvite(
        inviteId: 'rinv_1',
        accept: true,
      );

      expect(result.joined, isTrue);
      expect(result.room?.name, 'Invite Room');
      api.close();
    },
  );

  test(
    'reviewRoomInvite parses a pending join request after accepting',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'PATCH');
          expect(request.url.path, '/api/v1/room-invites/rinv_1');
          expect(
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
            {'decision': 'accept', 'reason': 'Let me in'},
          );

          return http.Response(
            jsonEncode({
              'ok': true,
              'invite': _roomInviteJson(status: 'accepted'),
              'join_request': {
                'id': 'jrq_1',
                'room_id': 'room_1',
                'status': 'pending',
                'created_at': '2026-01-01T00:00:00Z',
              },
            }),
            202,
          );
        }),
      );

      final result = await api.reviewRoomInvite(
        inviteId: 'rinv_1',
        accept: true,
        reason: '  Let me in  ',
      );

      expect(result.joined, isFalse);
      expect(result.pending, isTrue);
      api.close();
    },
  );

  test(
    'listRoomApplications requests all applications for notifications',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/api/v1/room-applications');
          expect(request.url.queryParameters, {'status': 'all'});
          expect(request.headers['authorization'], 'Bearer token');

          return http.Response(
            jsonEncode({
              'applications': [
                _roomApplicationJson(status: 'approved', reason: 'Hi team'),
              ],
              'next_cursor': null,
            }),
            200,
          );
        }),
      );

      final applications = await api.listRoomApplications(status: 'all');

      expect(applications.single.id, 'jrq_1');
      expect(applications.single.status, 'approved');
      expect(applications.single.room.name, 'Invite Room');
      expect(applications.single.reviewer?.roomRole, 'owner');
      expect(applications.single.reviewedAt, isNotNull);
      expect(applications.single.reason, 'Hi team');
      expect(applications.single.reviewerExists, isTrue);
      api.close();
    },
  );

  test('room application notification parses missing reviewer state', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/room-applications');

        return http.Response(
          jsonEncode({
            'applications': [
              _roomApplicationJson(status: 'approved', reviewerExists: false),
            ],
            'next_cursor': null,
          }),
          200,
        );
      }),
    );

    final applications = await api.listRoomApplications(status: 'all');

    expect(applications.single.reviewerExists, isFalse);
    expect(applications.single.reviewer?.isDeleted, isTrue);
    expect(applications.single.reviewer?.displayName, 'Alice');
    api.close();
  });

  test(
    'listRoomNotifications requests and parses room event notifications',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/api/v1/room-notifications');
          expect(request.headers['authorization'], 'Bearer token');

          return http.Response(
            jsonEncode({
              'notifications': [_roomEventNotificationJson()],
              'next_cursor': null,
            }),
            200,
          );
        }),
      );

      final notifications = await api.listRoomNotifications();
      final notification = notifications.single;

      expect(notification.id, 'rnot_1');
      expect(notification.type, 'role_promoted');
      expect(notification.toRole, 'admin');
      expect(notification.messageId, 'msg_mention_1');
      expect(notification.messagePreview, '@Alice hello');
      expect(notification.room.name, 'Invite Room');
      expect(notification.room.description, 'Invite room bio');
      expect(notification.actor?.roomDisplayName, 'Alice in Invite Room');
      expect(notification.actor?.roomRole, 'owner');
      expect(notification.isUnread, isTrue);
      api.close();
    },
  );

  test('markRoomNotificationsRead posts the read receipt endpoint', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/room-notifications/read');
        expect(request.headers['authorization'], 'Bearer token');

        return http.Response(jsonEncode({'ok': true}), 200);
      }),
    );

    await api.markRoomNotificationsRead();
    api.close();
  });

  test(
    'withdrawRoomApplication sends withdraw decision and parses result',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'PATCH');
          expect(request.url.path, '/api/v1/room-applications/jrq_1');
          expect(request.headers['authorization'], 'Bearer token');
          expect(
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
            {'decision': 'withdraw'},
          );

          return http.Response(
            jsonEncode({
              'ok': true,
              'application': _roomApplicationJson(status: 'withdrawn'),
            }),
            200,
          );
        }),
      );

      final application = await api.withdrawRoomApplication(requestId: 'jrq_1');

      expect(application.status, 'withdrawn');
      expect(application.reviewer, isNull);
      api.close();
    },
  );

  test('sendMessage can send and parse a file attachment', () async {
    final fileAsset = UploadedAsset(
      id: 'asset_1',
      url: '/assets/asset_1/report.pdf',
      thumbnailUrl: null,
      mimeType: 'application/pdf',
      filename: 'report.pdf',
      sizeBytes: 4096,
    );
    final fileAttachment = MessageAttachment(
      type: 'file',
      name: 'report.pdf',
      asset: fileAsset,
    );
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/messages');
        expect(
          jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          {
            'client_message_id': 'cmsg_1',
            'body': 'report.pdf',
            'type': 'file',
            'attachments': [fileAttachment.toJson()],
          },
        );

        return http.Response(
          jsonEncode({
            'message': {
              'id': 'msg_1',
              'room_id': 'room_1',
              'sender': {
                'id': 'user_1',
                'username': 'alice',
                'display_name': 'Alice',
              },
              'client_message_id': 'cmsg_1',
              'type': 'file',
              'body': 'report.pdf',
              'attachments': [fileAttachment.toJson()],
              'created_at': '2026-05-31T14:00:00Z',
            },
          }),
          201,
        );
      }),
    );

    final message = await api.sendMessage(
      roomId: 'room_1',
      clientMessageId: 'cmsg_1',
      body: 'report.pdf',
      type: 'file',
      attachments: [fileAttachment],
    );

    expect(message.type, 'file');
    expect(message.fileAttachments.single.asset?.filename, 'report.pdf');
    expect(message.fileAttachments.single.asset?.sizeBytes, 4096);
    api.close();
  });

  test(
    'joinLive retries a transient socket write abort with one idempotency key',
    () async {
      var requests = 0;
      final idempotencyKeys = <String>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requests += 1;
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/rooms/room_1/live/join');
          expect(request.headers['authorization'], 'Bearer token');
          expect(jsonDecode(request.body), containsPair('mic_muted', true));
          expect(
            jsonDecode(request.body),
            containsPair('headphones_muted', true),
          );
          idempotencyKeys.add(request.headers['idempotency-key']!);

          if (requests == 1) {
            throw http.ClientException(
              'SocketException: Write failed (OS Error: '
              '你的主机中的软件中止了一个已建立的连接。, errno = 10053)',
              request.url,
            );
          }

          return http.Response(jsonEncode(_liveJoinJson()), 200);
        }),
      );

      final result = await api.joinLive(
        roomId: 'room_1',
        clientLiveSessionId: 'clive_1',
        source: 'room_card_speaker',
        micMuted: true,
        headphonesMuted: true,
      );

      expect(requests, 2);
      expect(idempotencyKeys, hasLength(2));
      expect(idempotencyKeys.first, isNotEmpty);
      expect(idempotencyKeys.first, idempotencyKeys.last);
      expect(result.liveKit.serverUrl, 'wss://voice.example.com');
      expect(result.live.participantCount, 1);
      api.close();
    },
  );

  test('listSessions parses recent account activity array', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/auth/sessions');
        expect(request.headers['authorization'], 'Bearer token');
        return http.Response(
          jsonEncode([
            {
              'id': 'session_1',
              'user_agent': 'Flutter test',
              'ip_address': '127.0.0.1',
              'location': 'Local',
              'created_at': 1780300800,
              'last_used_at': 1780300860,
              'expires_at': 1782892800,
              'is_current': true,
            },
          ]),
          200,
        );
      }),
    );

    final sessions = await api.listSessions();

    expect(sessions, hasLength(1));
    expect(sessions.single.location, 'Local');
    expect(sessions.single.isCurrent, isTrue);
    api.close();
  });

  test('listForcedUserSessions reads the managed account activity', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/users/user_2/sessions');
        expect(request.headers['authorization'], 'Bearer token');
        return http.Response(
          jsonEncode([
            {
              'id': 'target_session',
              'user_agent': 'Windows target',
              'ip_address': '203.0.113.8',
              'location': 'Shanghai',
              'created_at': 1780300800,
              'last_used_at': 1780300860,
              'expires_at': 1782892800,
              'is_current': false,
            },
          ]),
          200,
        );
      }),
    );

    final sessions = await api.listForcedUserSessions('user_2');

    expect(sessions.single.id, 'target_session');
    expect(sessions.single.ipAddress, '203.0.113.8');
    expect(sessions.single.isCurrent, isFalse);
    api.close();
  });

  test('updateAccount sends language preference and parses it', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'PATCH');
        expect(request.url.path, '/api/v1/users/me/account');
        expect(request.headers['authorization'], 'Bearer token');
        expect(jsonDecode(request.body) as Map<String, Object?>, {
          'language': 'en',
        });
        return http.Response(
          jsonEncode({
            'user': {
              'id': 'user_1',
              'uid': '1000001',
              'username': 'alice',
              'display_name': 'Alice',
              'bio': '',
              'gender': 'secret',
              'email': 'alice@example.test',
              'email_public': false,
              'phone_number_public': false,
              'avatar_url': null,
              'default_avatar_key': 'blue-3',
              'language': 'en',
            },
          }),
          200,
        );
      }),
    );

    final user = await api.updateAccount(language: 'en');

    expect(user.language, 'en');
    api.close();
  });

  test('updateAccount sends the email verification token', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(jsonDecode(request.body) as Map<String, Object?>, {
          'email': 'new@example.test',
          'email_verification_token': 'verification-token',
        });
        return http.Response(
          jsonEncode({
            'user': {
              'id': 'user_1',
              'uid': '1000001',
              'username': 'alice',
              'display_name': 'Alice',
              'bio': '',
              'gender': 'secret',
              'email': 'new@example.test',
              'email_verified': true,
              'email_public': false,
              'phone_number_public': false,
              'avatar_url': null,
              'default_avatar_key': 'blue-3',
            },
          }),
          200,
        );
      }),
    );

    final user = await api.updateAccount(
      email: 'new@example.test',
      emailVerificationToken: 'verification-token',
    );

    expect(user.email, 'new@example.test');
    expect(user.emailVerified, isTrue);
    api.close();
  });

  test('uploadImageAsset posts multipart image data', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/uploads/images');
        expect(request.headers['authorization'], 'Bearer token');
        expect(
          request.headers['content-type'],
          startsWith('multipart/form-data'),
        );
        expect(request.bodyBytes, isNotEmpty);
        return http.Response(
          jsonEncode({
            'asset': {
              'id': 'asset_1',
              'url': '/assets/asset_1/avatar.png',
              'thumbnail_url': '/assets/asset_1/avatar.png',
              'mime_type': 'image/png',
            },
          }),
          201,
        );
      }),
    );

    final asset = await api.uploadImageAsset(
      bytes: Uint8List.fromList([1, 2, 3]),
      filename: 'avatar.png',
      purpose: 'avatar',
    );

    expect(asset.id, 'asset_1');
    expect(asset.mimeType, 'image/png');
    api.close();
  });

  test('uploadFileAsset posts multipart file data', () async {
    final progress = <int>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/uploads/files');
        expect(request.headers['authorization'], 'Bearer token');
        expect(
          request.headers['content-type'],
          startsWith('multipart/form-data'),
        );
        final body = utf8.decode(request.bodyBytes, allowMalformed: true);
        expect(body, contains('name="purpose"'));
        expect(body, contains('message_file'));
        expect(body, contains('report.pdf'));
        return http.Response(
          jsonEncode({
            'asset': {
              'id': 'asset_1',
              'filename': 'report.pdf',
              'size_bytes': 4096,
              'url': '/assets/asset_1/report.pdf',
              'thumbnail_url': null,
              'mime_type': 'application/pdf',
            },
          }),
          201,
        );
      }),
    );

    final asset = await api.uploadFileAsset(
      bytes: Uint8List.fromList([37, 80, 68, 70]),
      filename: 'report.pdf',
      onProgress: ({required sentBytes, required totalBytes}) {
        expect(totalBytes, 4);
        progress.add(sentBytes);
      },
    );

    expect(asset.id, 'asset_1');
    expect(asset.filename, 'report.pdf');
    expect(asset.sizeBytes, 4096);
    expect(asset.mimeType, 'application/pdf');
    expect(progress.first, 0);
    expect(progress.last, 4);
    api.close();
  });

  test('uploadImageAsset retries a transient socket write abort', () async {
    var requests = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests += 1;
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/uploads/images');
        expect(request.headers['authorization'], 'Bearer token');
        expect(
          request.headers['content-type'],
          startsWith('multipart/form-data'),
        );
        expect(request.bodyBytes, isNotEmpty);

        if (requests == 1) {
          throw http.ClientException(
            'SocketException: Write failed (OS Error: '
            '你的主机中的软件中止了一个已建立的连接。, errno = 10053)',
            request.url,
          );
        }

        return http.Response(
          jsonEncode({
            'asset': {
              'id': 'asset_1',
              'url': '/assets/asset_1/sticker.png',
              'thumbnail_url': '/assets/asset_1/sticker.png',
              'mime_type': 'image/png',
              'width': 128,
              'height': 128,
            },
          }),
          201,
        );
      }),
    );

    final asset = await api.uploadImageAsset(
      bytes: Uint8List.fromList([1, 2, 3]),
      filename: 'sticker.png',
      purpose: 'sticker',
    );

    expect(requests, 2);
    expect(asset.id, 'asset_1');
    expect(asset.width, 128);
    api.close();
  });

  test('uploadImageAsset retries a transient 503 response', () async {
    var requests = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests += 1;
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/uploads/images');
        expect(request.headers['authorization'], 'Bearer token');

        if (requests == 1) {
          return http.Response('service unavailable', 503);
        }

        return http.Response(
          jsonEncode({
            'asset': {
              'id': 'asset_1',
              'url': '/assets/asset_1/sticker.png',
              'thumbnail_url': '/assets/asset_1/sticker.png',
              'mime_type': 'image/png',
            },
          }),
          201,
        );
      }),
    );

    final asset = await api.uploadImageAsset(
      bytes: Uint8List.fromList([1, 2, 3]),
      filename: 'sticker.png',
      purpose: 'sticker',
    );

    expect(requests, 2);
    expect(asset.id, 'asset_1');
    api.close();
  });

  test('listStickerPacks parses personal sticker assets', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/sticker-packs');
        expect(request.url.queryParameters['scope'], 'personal');
        expect(request.headers['authorization'], 'Bearer token');
        return http.Response(
          jsonEncode({
            'packs': [
              {
                'id': 'stkp_1',
                'scope': 'personal',
                'room_id': null,
                'name': 'My Stickers',
                'sort_order': 10,
                'updated_at': '2026-06-02T08:00:00Z',
                'stickers': [
                  {
                    'id': 'stk_1',
                    'name': 'ok',
                    'sort_order': 20,
                    'asset': {
                      'id': 'asset_1',
                      'url': '/assets/asset_1/ok.webp',
                      'thumbnail_url': '/assets/asset_1/ok.webp',
                      'mime_type': 'image/webp',
                      'width': 128,
                      'height': 128,
                      'created_at': '2026-06-02T07:59:00Z',
                    },
                  },
                ],
              },
            ],
          }),
          200,
        );
      }),
    );

    final packs = await api.listStickerPacks();

    expect(packs.single.name, 'My Stickers');
    expect(packs.single.stickers.single.asset.mimeType, 'image/webp');
    expect(packs.single.stickers.single.asset.width, 128);
    api.close();
  });

  test('saveSticker posts target scope and parses returned pack', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/stickers/save');
        expect(request.headers['authorization'], 'Bearer token');
        expect(jsonDecode(request.body) as Map<String, Object?>, {
          'sticker_id': 'stk_1',
          'source_message_id': 'msg_1',
          'target_scope': 'room',
          'name': 'ok',
        });
        return http.Response(
          jsonEncode(
            _stickerPackJson(stickers: [_stickerJson(id: 'stk_saved')]),
          ),
          201,
        );
      }),
    );

    final pack = await api.saveSticker(
      roomId: 'room_1',
      stickerId: 'stk_1',
      sourceMessageId: 'msg_1',
      targetScope: 'room',
      name: 'ok',
    );

    expect(pack.stickers.single.id, 'stk_saved');
    api.close();
  });

  test('personal sticker APIs carry the managed owner user id', () async {
    var requestIndex = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requestIndex += 1;
        switch (requestIndex) {
          case 1:
            expect(request.method, 'GET');
            expect(request.url.path, '/api/v1/sticker-packs');
            expect(request.url.queryParameters, {
              'scope': 'personal',
              'owner_user_id': 'user_2',
            });
            return http.Response(jsonEncode({'packs': []}), 200);
          case 2:
            expect(request.method, 'POST');
            expect(jsonDecode(request.body), {
              'scope': 'personal',
              'name': 'Target Pack',
              'owner_user_id': 'user_2',
            });
            return http.Response(jsonEncode(_stickerPackJson()), 201);
        }
        fail('unexpected request ${request.method} ${request.url}');
      }),
    );

    await api.listStickerPacks(scope: 'personal', ownerUserId: 'user_2');
    await api.createStickerPack(name: 'Target Pack', ownerUserId: 'user_2');

    expect(requestIndex, 2);
    api.close();
  });

  test('sticker pack management uses server sticker routes', () async {
    var requestIndex = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requestIndex += 1;
        expect(request.headers['authorization'], 'Bearer token');

        switch (requestIndex) {
          case 1:
            expect(request.method, 'POST');
            expect(request.url.path, '/api/v1/sticker-packs');
            expect(jsonDecode(request.body) as Map<String, Object?>, {
              'scope': 'personal',
              'name': 'My Stickers',
              'sort_order': 10,
            });
            return http.Response(jsonEncode(_stickerPackJson()), 201);
          case 2:
            expect(request.method, 'POST');
            expect(request.url.path, '/api/v1/sticker-packs/stkp_1/stickers');
            expect(request.headers['idempotency-key'], isNotEmpty);
            expect(jsonDecode(request.body) as Map<String, Object?>, {
              'asset_id': 'asset_2',
              'name': 'hi',
              'sort_order': 20,
            });
            return http.Response(
              jsonEncode({
                'sticker': {
                  'id': 'stk_2',
                  'asset_id': 'asset_2',
                  'name': 'hi',
                  'sort_order': 20,
                },
              }),
              201,
            );
          case 3:
            expect(request.method, 'PATCH');
            expect(request.url.path, '/api/v1/sticker-packs/stkp_1');
            expect(jsonDecode(request.body) as Map<String, Object?>, {
              'name': 'Saved',
              'sort_order': 30,
            });
            return http.Response(
              jsonEncode(_stickerPackJson(name: 'Saved')),
              200,
            );
          case 4:
            expect(request.method, 'DELETE');
            expect(
              request.url.path,
              '/api/v1/sticker-packs/stkp_1/stickers/stk_2',
            );
            return http.Response(jsonEncode({'ok': true}), 200);
          case 5:
            expect(request.method, 'DELETE');
            expect(request.url.path, '/api/v1/sticker-packs/stkp_1');
            return http.Response(jsonEncode({'ok': true}), 200);
        }

        fail(
          'unexpected request $requestIndex ${request.method} ${request.url}',
        );
      }),
    );

    final pack = await api.createStickerPack(
      name: 'My Stickers',
      sortOrder: 10,
    );
    await api.addSticker(
      packId: pack.id,
      assetId: 'asset_2',
      name: 'hi',
      sortOrder: 20,
    );
    final updated = await api.updateStickerPack(
      packId: pack.id,
      name: 'Saved',
      sortOrder: 30,
    );
    await api.deleteSticker(packId: pack.id, stickerId: 'stk_2');
    await api.deleteStickerPack(pack.id);

    expect(updated.name, 'Saved');
    expect(requestIndex, 5);
    api.close();
  });

  test(
    'sticker item management uses rename reorder and download routes',
    () async {
      var requestIndex = 0;
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestIndex += 1;
          expect(request.headers['authorization'], 'Bearer token');

          switch (requestIndex) {
            case 1:
              expect(request.method, 'PATCH');
              expect(
                request.url.path,
                '/api/v1/sticker-packs/stkp_1/stickers/stk_1',
              );
              expect(jsonDecode(request.body) as Map<String, Object?>, {
                'name': 'ok',
              });
              return http.Response(
                jsonEncode({'sticker': _stickerJson(name: 'ok (2)')}),
                200,
              );
            case 2:
              expect(request.method, 'POST');
              expect(
                request.url.path,
                '/api/v1/sticker-packs/stkp_1/stickers/reorder',
              );
              expect(jsonDecode(request.body) as Map<String, Object?>, {
                'sticker_ids': ['stk_2', 'stk_1'],
              });
              return http.Response(
                jsonEncode(
                  _stickerPackJson(
                    stickers: [
                      _stickerJson(id: 'stk_2', sortOrder: 10),
                      _stickerJson(id: 'stk_1', sortOrder: 20),
                    ],
                  ),
                ),
                200,
              );
            case 3:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/stickers/download');
              expect(request.url.queryParameters['ids'], 'stk_1,stk_2');
              return http.Response.bytes(
                Uint8List.fromList([80, 75, 3, 4]),
                200,
                headers: {
                  'content-type': 'application/zip',
                  'content-disposition': 'attachment; filename="stickers.zip"',
                },
              );
          }

          fail(
            'unexpected request $requestIndex ${request.method} ${request.url}',
          );
        }),
      );

      final renamed = await api.updateSticker(
        packId: 'stkp_1',
        stickerId: 'stk_1',
        name: 'ok',
      );
      final reordered = await api.reorderStickers(
        packId: 'stkp_1',
        stickerIds: ['stk_2', 'stk_1'],
      );
      final downloaded = await api.downloadStickers(
        stickerIds: ['stk_1', 'stk_2'],
      );

      expect(renamed.name, 'ok (2)');
      expect(reordered.stickers.first.id, 'stk_2');
      expect(downloaded.filename, 'stickers.zip');
      expect(downloaded.mimeType, 'application/zip');
      expect(downloaded.bytes, [80, 75, 3, 4]);
      expect(requestIndex, 3);
      api.close();
    },
  );

  test('downloadStickers prefers UTF-8 filename star header', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/stickers/download');
        return http.Response.bytes(
          Uint8List.fromList([80, 75, 3, 4]),
          200,
          headers: {
            'content-type': 'application/zip',
            'content-disposition':
                'attachment; filename="stickers.zip"; '
                "filename*=UTF-8''%E8%A1%A8%E6%83%85.zip",
          },
        );
      }),
    );

    final downloaded = await api.downloadStickers(stickerIds: ['stk_1']);

    expect(downloaded.filename, '表情.zip');
    api.close();
  });

  test(
    'downloadMusicTrackPreview posts identity and returns M4A bytes',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/me/music-box/preview');
          expect(request.headers['authorization'], 'Bearer token');
          expect(jsonDecode(request.body), {
            'source': 'netease',
            'track_id': 'track-1',
          });
          return http.Response.bytes(
            Uint8List.fromList([0, 0, 0, 24, 102, 116, 121, 112]),
            200,
            headers: {
              'content-type': 'audio/mp4',
              'content-disposition': 'inline; filename="music-preview.m4a"',
            },
          );
        }),
      );

      final downloaded = await api.downloadMusicTrackPreview(
        source: 'netease',
        trackId: 'track-1',
      );

      expect(downloaded.bytes, [0, 0, 0, 24, 102, 116, 121, 112]);
      expect(downloaded.mimeType, 'audio/mp4');
      expect(downloaded.filename, 'music-preview.m4a');
      api.close();
    },
  );

  test(
    'deleteSticker treats transient close as success when sticker is gone',
    () async {
      var requestIndex = 0;
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestIndex += 1;
          expect(request.headers['authorization'], 'Bearer token');

          switch (requestIndex) {
            case 1:
              expect(request.method, 'DELETE');
              expect(
                request.url.path,
                '/api/v1/sticker-packs/stkp_1/stickers/stk_2',
              );
              throw http.ClientException(
                'Connection closed before full header was received',
                request.url,
              );
            case 2:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/sticker-packs');
              expect(request.url.queryParameters['scope'], 'personal');
              return http.Response(
                jsonEncode(_personalStickerPacksJson()),
                200,
              );
          }

          fail(
            'unexpected request $requestIndex ${request.method} ${request.url}',
          );
        }),
      );

      await api.deleteSticker(packId: 'stkp_1', stickerId: 'stk_2');

      expect(requestIndex, 2);
      api.close();
    },
  );

  test(
    'deleteSticker retries a transient close when sticker remains',
    () async {
      var requestIndex = 0;
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestIndex += 1;
          expect(request.headers['authorization'], 'Bearer token');

          switch (requestIndex) {
            case 1:
              expect(request.method, 'DELETE');
              expect(
                request.url.path,
                '/api/v1/sticker-packs/stkp_1/stickers/stk_2',
              );
              throw http.ClientException(
                'Connection closed before full header was received',
                request.url,
              );
            case 2:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/sticker-packs');
              expect(request.url.queryParameters['scope'], 'personal');
              return http.Response(
                jsonEncode(_personalStickerPacksJson(linkedAssetId: 'asset_2')),
                200,
              );
            case 3:
              expect(request.method, 'DELETE');
              expect(
                request.url.path,
                '/api/v1/sticker-packs/stkp_1/stickers/stk_2',
              );
              return http.Response(jsonEncode({'ok': true}), 200);
          }

          fail(
            'unexpected request $requestIndex ${request.method} ${request.url}',
          );
        }),
      );

      await api.deleteSticker(packId: 'stkp_1', stickerId: 'stk_2');

      expect(requestIndex, 3);
      api.close();
    },
  );

  test(
    'deleteSticker treats transient 503 as success when sticker is gone',
    () async {
      var requestIndex = 0;
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestIndex += 1;
          expect(request.headers['authorization'], 'Bearer token');

          switch (requestIndex) {
            case 1:
              expect(request.method, 'DELETE');
              expect(
                request.url.path,
                '/api/v1/sticker-packs/stkp_1/stickers/stk_2',
              );
              return http.Response('service unavailable', 503);
            case 2:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/sticker-packs');
              expect(request.url.queryParameters['scope'], 'personal');
              return http.Response(
                jsonEncode(_personalStickerPacksJson()),
                200,
              );
          }

          fail(
            'unexpected request $requestIndex ${request.method} ${request.url}',
          );
        }),
      );

      await api.deleteSticker(packId: 'stkp_1', stickerId: 'stk_2');

      expect(requestIndex, 2);
      api.close();
    },
  );

  test(
    'addSticker treats transient write failure as success when asset exists',
    () async {
      var requestIndex = 0;
      final idempotencyKeys = <String>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestIndex += 1;
          expect(request.headers['authorization'], 'Bearer token');

          switch (requestIndex) {
            case 1:
              expect(request.method, 'POST');
              expect(request.url.path, '/api/v1/sticker-packs/stkp_1/stickers');
              idempotencyKeys.add(request.headers['idempotency-key']!);
              throw http.ClientException(
                'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
                request.url,
              );
            case 2:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/sticker-packs');
              expect(request.url.queryParameters['scope'], 'personal');
              return http.Response(
                jsonEncode(_personalStickerPacksJson(linkedAssetId: 'asset_2')),
                200,
              );
          }

          fail(
            'unexpected request $requestIndex ${request.method} ${request.url}',
          );
        }),
      );

      await api.addSticker(packId: 'stkp_1', assetId: 'asset_2', name: 'hi');

      expect(requestIndex, 2);
      expect(idempotencyKeys.single, isNotEmpty);
      api.close();
    },
  );

  test(
    'addSticker retries once after transient write failure when not linked',
    () async {
      var requestIndex = 0;
      final idempotencyKeys = <String>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestIndex += 1;
          expect(request.headers['authorization'], 'Bearer token');

          switch (requestIndex) {
            case 1:
              expect(request.method, 'POST');
              expect(request.url.path, '/api/v1/sticker-packs/stkp_1/stickers');
              idempotencyKeys.add(request.headers['idempotency-key']!);
              throw http.ClientException(
                'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
                request.url,
              );
            case 2:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/sticker-packs');
              expect(request.url.queryParameters['scope'], 'personal');
              return http.Response(
                jsonEncode(_personalStickerPacksJson()),
                200,
              );
            case 3:
              expect(request.method, 'POST');
              expect(request.url.path, '/api/v1/sticker-packs/stkp_1/stickers');
              idempotencyKeys.add(request.headers['idempotency-key']!);
              expect(jsonDecode(request.body) as Map<String, Object?>, {
                'asset_id': 'asset_2',
                'name': 'hi',
              });
              return http.Response(
                jsonEncode({
                  'sticker': {
                    'id': 'stk_2',
                    'asset_id': 'asset_2',
                    'name': 'hi',
                    'sort_order': 10,
                  },
                }),
                201,
              );
          }

          fail(
            'unexpected request $requestIndex ${request.method} ${request.url}',
          );
        }),
      );

      await api.addSticker(packId: 'stkp_1', assetId: 'asset_2', name: 'hi');

      expect(requestIndex, 3);
      expect(idempotencyKeys, hasLength(2));
      expect(idempotencyKeys.first, isNotEmpty);
      expect(idempotencyKeys.first, idempotencyKeys.last);
      api.close();
    },
  );

  test(
    'addSticker recovers after repeated transient write failures once linked',
    () async {
      var requestIndex = 0;
      final idempotencyKeys = <String>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestIndex += 1;
          expect(request.headers['authorization'], 'Bearer token');

          switch (requestIndex) {
            case 1:
              expect(request.method, 'POST');
              expect(request.url.path, '/api/v1/sticker-packs/stkp_1/stickers');
              idempotencyKeys.add(request.headers['idempotency-key']!);
              throw http.ClientException(
                'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
                request.url,
              );
            case 2:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/sticker-packs');
              return http.Response(
                jsonEncode(_personalStickerPacksJson()),
                200,
              );
            case 3:
              expect(request.method, 'POST');
              expect(request.url.path, '/api/v1/sticker-packs/stkp_1/stickers');
              idempotencyKeys.add(request.headers['idempotency-key']!);
              throw http.ClientException(
                'SocketException: Write failed (OS Error: connection aborted, errno = 10053)',
                request.url,
              );
            case 4:
              expect(request.method, 'GET');
              expect(request.url.path, '/api/v1/sticker-packs');
              return http.Response(
                jsonEncode(_personalStickerPacksJson(linkedAssetId: 'asset_2')),
                200,
              );
          }

          fail(
            'unexpected request $requestIndex ${request.method} ${request.url}',
          );
        }),
      );

      await api.addSticker(packId: 'stkp_1', assetId: 'asset_2', name: 'hi');

      expect(requestIndex, 4);
      expect(idempotencyKeys, hasLength(2));
      expect(idempotencyKeys.first, idempotencyKeys.last);
      api.close();
    },
  );

  test('RoomCard defaults the personal AI voice preference to disabled', () {
    final room = RoomCard.fromJson({
      'id': 'room_1',
      'name': 'Alpha',
      'avatar_url': null,
      'default_avatar_key': 'blue-1',
      'member_count': 2,
      'live_participant_count': 1,
      'live_avatar_preview': const [],
      'last_message': null,
      'unread_count': 0,
      'updated_at': '2026-07-11T10:00:00Z',
    });

    expect(room.aiVoiceAnnouncementsEnabled, isFalse);
  });

  test('RoomDetail parses room settings and nullable creator', () {
    final room = RoomDetail.fromJson({
      ..._roomDetailJson(),
      'created_by': null,
      'description': 'A room for launch planning',
      'visibility': 'public',
      'join_policy': 'open',
      'remark_name': 'Launch',
      'notification_policy': 'mentions',
      'is_pinned': true,
      'personal_profile': {
        'display_name': 'Alice Ops',
        'avatar_url': '/assets/asset_2/room-avatar.png',
        'default_avatar_key': 'mint-2',
      },
      'ai_voice_announcements_enabled': false,
      'can_delete_room': true,
      'my_membership': {
        'joined_at': '2026-05-31T14:00:00Z',
        'role': 'superuser',
      },
    });

    expect(room.createdBy, isNull);
    expect(room.description, 'A room for launch planning');
    expect(room.visibility, 'public');
    expect(room.joinPolicy, 'open');
    expect(room.remarkName, 'Launch');
    expect(room.notificationPolicy, 'mentions');
    expect(room.isPinned, isTrue);
    expect(room.personalProfile.displayName, 'Alice Ops');
    expect(room.onlineMemberCount, 1);
    expect(room.toCard().onlineMemberCount, 1);
    expect(room.aiVoiceAnnouncementsEnabled, isFalse);
    expect(room.myMembership.aiVoiceAnnouncementsEnabled, isFalse);
    expect(room.isAdmin, isTrue);
    expect(room.isSuperuser, isTrue);
    expect(room.canDelete, isTrue);
    expect(room.toCard().displayName, 'Launch');
    expect(room.toCard().isPinned, isTrue);
  });

  test('room management endpoints use server room routes', () async {
    var requests = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests += 1;
        expect(request.headers['authorization'], 'Bearer token');
        switch (requests) {
          case 1:
            expect(request.method, 'PATCH');
            expect(request.url.path, '/api/v1/rooms/room_1');
            expect(jsonDecode(utf8.decode(request.bodyBytes)), {
              'name': 'New Room',
              'description': 'Updated',
              'visibility': 'private',
              'join_policy': 'closed',
              'ai_voice_announcements_enabled': false,
              'avatar_asset_id': 'asset_1',
              'default_avatar_key': 'teal-2',
            });
            return http.Response(
              jsonEncode({
                'room': {
                  ..._roomDetailJson(),
                  'name': 'New Room',
                  'description': 'Updated',
                  'visibility': 'private',
                  'join_policy': 'closed',
                  'ai_voice_announcements_enabled': false,
                  'avatar_url': '/assets/asset_1/room.png',
                  'default_avatar_key': 'teal-2',
                },
              }),
              200,
            );
          case 2:
            expect(request.method, 'PATCH');
            expect(request.url.path, '/api/v1/rooms/room_1/me');
            expect(jsonDecode(utf8.decode(request.bodyBytes)), {
              'remark_name': 'Ops',
              'notification_policy': 'silent',
              'room_display_name': 'Alice Ops',
              'is_pinned': true,
              'ai_voice_announcements_enabled': false,
            });
            return http.Response(
              jsonEncode({
                'room': {
                  ..._roomDetailJson(),
                  'remark_name': 'Ops',
                  'notification_policy': 'silent',
                  'is_pinned': true,
                  'personal_profile': {
                    'display_name': 'Alice Ops',
                    'avatar_url': '/assets/asset_2/avatar.png',
                    'default_avatar_key': 'mint-2',
                  },
                },
              }),
              200,
            );
          case 3:
            expect(request.method, 'DELETE');
            expect(request.url.path, '/api/v1/rooms/room_1/members/me');
            expect(jsonDecode(utf8.decode(request.bodyBytes)), {
              'confirm_delete_if_empty': true,
            });
            return http.Response('{}', 200);
          case 4:
            expect(request.method, 'DELETE');
            expect(request.url.path, '/api/v1/rooms/room_1');
            expect(jsonDecode(utf8.decode(request.bodyBytes)), {
              'confirm_name': 'New Room',
            });
            return http.Response('{}', 200);
          case 5:
            expect(request.method, 'PATCH');
            expect(request.url.path, '/api/v1/rooms/room_1/members/user_2');
            expect(jsonDecode(utf8.decode(request.bodyBytes)), {
              'role': 'admin',
            });
            return http.Response(
              jsonEncode({
                'member': {
                  'user': {
                    'id': 'user_2',
                    'username': 'bob',
                    'display_name': 'Bob',
                  },
                  'role': 'admin',
                  'joined_at': '2026-05-31T14:00:00Z',
                },
              }),
              200,
            );
          case 6:
            expect(request.method, 'PATCH');
            expect(request.url.path, '/api/v1/rooms/room_1/members/user_2');
            expect(jsonDecode(utf8.decode(request.bodyBytes)), {
              'room_display_name': 'Room Bob',
            });
            return http.Response(
              jsonEncode({
                'member': {
                  'user': {
                    'id': 'user_2',
                    'username': 'bob',
                    'display_name': 'Bob',
                    'room_display_name': 'Room Bob',
                  },
                  'room_display_name': 'Room Bob',
                  'role': 'admin',
                  'joined_at': '2026-05-31T14:00:00Z',
                },
              }),
              200,
            );
          case 7:
            expect(request.method, 'DELETE');
            expect(request.url.path, '/api/v1/rooms/room_1/members/user_2');
            return http.Response('{}', 200);
          case 8:
            expect(request.method, 'PATCH');
            expect(request.url.path, '/api/v1/rooms/room_1/creator');
            expect(jsonDecode(utf8.decode(request.bodyBytes)), {
              'user_id': 'user_2',
            });
            return http.Response(
              jsonEncode({
                'room': {
                  ..._roomDetailJson(),
                  'created_by': {
                    'id': 'user_2',
                    'username': 'bob',
                    'display_name': 'Bob',
                  },
                },
              }),
              200,
            );
          default:
            fail('unexpected request ${request.method} ${request.url}');
        }
      }),
    );

    final updatedRoom = await api.updateRoom(
      roomId: 'room_1',
      name: 'New Room',
      description: 'Updated',
      visibility: 'private',
      joinPolicy: 'closed',
      aiVoiceAnnouncementsEnabled: false,
      avatarAssetId: 'asset_1',
      defaultAvatarKey: 'teal-2',
    );
    final myRoom = await api.updateMyRoomSettings(
      roomId: 'room_1',
      remarkName: 'Ops',
      notificationPolicy: 'silent',
      roomDisplayName: 'Alice Ops',
      isPinned: true,
      aiVoiceAnnouncementsEnabled: false,
    );
    await api.leaveRoom(roomId: 'room_1', confirmDeleteIfEmpty: true);
    await api.deleteRoom(roomId: 'room_1', confirmName: 'New Room');
    final member = await api.updateRoomMemberRole(
      roomId: 'room_1',
      userId: 'user_2',
      role: 'admin',
    );
    final renamedMember = await api.updateRoomMemberRoomDisplayName(
      roomId: 'room_1',
      userId: 'user_2',
      roomDisplayName: 'Room Bob',
    );
    await api.removeRoomMember(roomId: 'room_1', userId: 'user_2');
    final transferred = await api.transferRoomCreator(
      roomId: 'room_1',
      userId: 'user_2',
    );

    expect(updatedRoom.name, 'New Room');
    expect(myRoom.remarkName, 'Ops');
    expect(myRoom.isPinned, isTrue);
    expect(member.role, 'admin');
    expect(renamedMember.roomDisplayName, 'Room Bob');
    expect(transferred.createdBy?.id, 'user_2');
    expect(requests, 8);
    api.close();
  });

  test('updateProfile retries a transient socket write abort', () async {
    var requests = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requests += 1;
        expect(request.method, 'PATCH');
        expect(request.url.path, '/api/v1/users/me/profile');
        expect(request.headers['authorization'], 'Bearer token');
        expect(jsonDecode(request.body) as Map<String, Object?>, {
          'avatar_asset_id': 'asset_1',
        });

        if (requests == 1) {
          throw http.ClientException(
            'SocketException: Write failed (OS Error: '
            '你的主机中的软件中止了一个已建立的连接, errno = 10053)',
            request.url,
          );
        }

        return http.Response(
          jsonEncode({
            'user': {
              'id': 'user_1',
              'uid': '1000001',
              'username': 'alice',
              'display_name': 'Alice',
              'avatar_url': '/assets/asset_1/avatar.png',
              'default_avatar_key': 'blue-3',
            },
          }),
          200,
        );
      }),
    );

    final user = await api.updateProfile(avatarAssetId: 'asset_1');

    expect(requests, 2);
    expect(user.avatarUrl, '/assets/asset_1/avatar.png');
    api.close();
  });

  test('getMusicBoxState parses the room snapshot', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/rooms/room_1/music-box/state');
        expect(request.headers['authorization'], 'Bearer token');
        return http.Response(
          jsonEncode({
            'enabled': true,
            'playback': {
              'state': 'playing',
              'current_item_id': 'item_1',
              'position_ms': 4200,
              'volume': 70,
              'updated_at': '2026-06-01T10:00:00Z',
            },
            'queue': [
              {
                'id': 'item_1',
                'source': 'netease',
                'track_id': 't1',
                'title': 'Song A',
                'artist': 'Artist A',
                'duration_ms': 200000,
                'status': 'ready',
                'added_by_user_id': 'user_2',
              },
            ],
            'usage': {'used_bytes': 1024, 'limit_bytes': 10240},
          }),
          200,
        );
      }),
    );

    final state = await api.getMusicBoxState('room_1');

    expect(state.enabled, isTrue);
    expect(state.playback.state, MusicBoxPlaybackState.playing);
    expect(state.currentItem?.title, 'Song A');
    expect(state.usage.limitBytes, 10240);
    api.close();
  });

  test('searchMusicBox sends keyword and parses results', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/rooms/room_1/music-box/search');
        expect(request.url.queryParameters['keyword'], '晴天');
        return http.Response(
          jsonEncode({
            'results': [
              {
                'source': 'netease',
                'track_id': 't9',
                'name': '晴天',
                'artists': ['周杰伦'],
              },
            ],
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final results = await api.searchMusicBox(roomId: 'room_1', keyword: '晴天');

    expect(results, hasLength(1));
    expect(results.single.name, '晴天');
    expect(results.single.artists, ['周杰伦']);
    api.close();
  });

  test(
    'queueMusicBoxTrack posts the track body and returns the snapshot',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/rooms/room_1/music-box/queue');
          expect(jsonDecode(request.body) as Map<String, Object?>, {
            'source': 'netease',
            'track_id': 't9',
            'title': '晴天',
            'artist': '周杰伦',
          });
          return http.Response(
            jsonEncode({'enabled': true, 'queue': [], 'usage': {}}),
            200,
          );
        }),
      );

      final state = await api.queueMusicBoxTrack(
        roomId: 'room_1',
        trackId: 't9',
        title: '晴天',
        source: 'netease',
        artist: '周杰伦',
      );

      expect(state.enabled, isTrue);
      api.close();
    },
  );

  test('controlMusicBox posts the action and optional queue item', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/rooms/room_1/music-box/control');
        expect(jsonDecode(request.body) as Map<String, Object?>, {
          'action': 'play_now',
          'item_id': 'queue_1',
        });
        return http.Response(
          jsonEncode({'enabled': true, 'queue': [], 'usage': {}}),
          200,
        );
      }),
    );

    final state = await api.controlMusicBox(
      roomId: 'room_1',
      action: 'play_now',
      itemId: 'queue_1',
    );

    expect(state.enabled, isTrue);
    api.close();
  });

  test(
    'activateMusicBoxPlaylist posts saved source and playback intent',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/rooms/room_1/music-box/activate');
          expect(jsonDecode(request.body) as Map<String, Object?>, {
            'source_type': 'room_playlist',
            'start_play': true,
            'playlist_id': 'playlist_1',
          });
          return http.Response(
            jsonEncode({
              'enabled': true,
              'revision': 2,
              'active_source': {
                'type': 'room_playlist',
                'playlist_id': 'playlist_1',
                'snapshot_id': 'snapshot_1',
                'name': 'Room list',
                'created_at': '2026-08-06T03:04:05Z',
              },
              'queue': [],
              'temporary_queue': [],
              'usage': {},
            }),
            200,
          );
        }),
      );

      final state = await api.activateMusicBoxPlaylist(
        roomId: 'room_1',
        sourceType: MusicBoxActiveSourceType.roomPlaylist,
        playlistId: 'playlist_1',
      );

      expect(state.activeSource.type, MusicBoxActiveSourceType.roomPlaylist);
      expect(state.activeSource.snapshotId, 'snapshot_1');
      expect(state.activeSource.createdAt, DateTime.utc(2026, 8, 6, 3, 4, 5));
      expect(state.revision, 2);
      api.close();
    },
  );

  test('cloneActiveMusicBoxPlaylist pins the immutable snapshot', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/api/v1/rooms/room_1/music-box/active-playlist/clone',
        );
        expect(jsonDecode(request.body) as Map<String, Object?>, {
          'playlist_id': 'playlist_1',
          'snapshot_id': 'snapshot_1',
        });
        return http.Response(
          jsonEncode({
            'playlist': {
              'id': 'playlist_clone',
              'name': '朋友的歌单 · 夜晚',
              'description': '',
              'revision': 1,
              'item_count': 2,
            },
          }),
          201,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final playlist = await api.cloneActiveMusicBoxPlaylist(
      roomId: 'room_1',
      playlistId: 'playlist_1',
      snapshotId: 'snapshot_1',
    );

    expect(playlist.id, 'playlist_clone');
    expect(playlist.name, '朋友的歌单 · 夜晚');
    expect(playlist.itemCount, 2);
    api.close();
  });

  test('removeMusicBoxItem deletes the queue item', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(request.url.path, '/api/v1/rooms/room_1/music-box/queue/item_1');
        return http.Response(
          jsonEncode({'enabled': true, 'queue': [], 'usage': {}}),
          200,
        );
      }),
    );

    final state = await api.removeMusicBoxItem(
      roomId: 'room_1',
      itemId: 'item_1',
    );

    expect(state.enabled, isTrue);
    api.close();
  });

  test(
    'personal playlist endpoints parse paginated metadata and items',
    () async {
      var requests = 0;
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requests += 1;
          expect(request.method, 'GET');
          expect(request.headers['authorization'], 'Bearer token');
          if (requests == 1) {
            expect(request.url.path, '/api/v1/me/music-box/playlists');
            expect(request.url.queryParameters['page'], '1');
            expect(request.url.queryParameters['page_size'], '50');
            return http.Response(
              jsonEncode({
                'playlists': [
                  {
                    'id': 'mbp_1',
                    'name': '夜晚',
                    'description': '',
                    'revision': 3,
                    'item_count': 1,
                    'created_at': '2026-07-31T01:00:00Z',
                    'updated_at': '2026-07-31T01:05:00Z',
                  },
                ],
                'pagination': {
                  'page': 1,
                  'page_size': 50,
                  'total': 1,
                  'has_more': false,
                },
                'limits': {'max_playlists': 50, 'max_playlist_items': 500},
              }),
              200,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          }
          expect(request.url.path, '/api/v1/me/music-box/playlists/mbp_1');
          expect(request.url.queryParameters['keyword'], '晴');
          expect(request.url.queryParameters['source'], 'netease');
          return http.Response(
            jsonEncode({
              'playlist': {
                'id': 'mbp_1',
                'name': '夜晚',
                'description': '',
                'revision': 3,
                'item_count': 1,
              },
              'items': [
                {
                  'id': 'mbpi_1',
                  'playlist_id': 'mbp_1',
                  'track_id': 'track_1',
                  'source': 'netease',
                  'title': '晴天',
                  'artists': ['周杰伦'],
                  'duration_ms': 269000,
                  'sort_order': 10,
                },
              ],
              'pagination': {
                'page': 1,
                'page_size': 50,
                'total': 1,
                'has_more': false,
              },
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final playlists = await api.listPersonalMusicPlaylists();
      final items = await api.getPersonalMusicPlaylist(
        playlistId: 'mbp_1',
        keyword: '晴',
        source: 'netease',
      );

      expect(playlists.playlists.single.name, '夜晚');
      expect(playlists.maxPlaylistItems, 500);
      expect(items.items.single.title, '晴天');
      expect(items.items.single.artists, ['周杰伦']);
      api.close();
    },
  );

  test(
    'personal playlist mutations use scoped routes and request bodies',
    () async {
      final seen = <String>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          seen.add('${request.method} ${request.url.path}');
          switch ((request.method, request.url.path)) {
            case ('POST', '/api/v1/me/music-box/playlists'):
              expect(jsonDecode(request.body), {'name': '夜晚'});
              return http.Response(
                jsonEncode({
                  'playlist': {
                    'id': 'mbp_1',
                    'name': '夜晚',
                    'description': '',
                    'revision': 1,
                    'item_count': 0,
                  },
                }),
                201,
                headers: {'content-type': 'application/json; charset=utf-8'},
              );
            case ('POST', '/api/v1/me/music-box/playlists/merge'):
              expect(jsonDecode(request.body), {
                'name': '合并结果',
                'playlist_ids': ['mbp_2', 'mbp_1'],
              });
              return http.Response(
                jsonEncode({
                  'playlist': {
                    'id': 'mbp_merged',
                    'name': '合并结果',
                    'description': '',
                    'revision': 1,
                    'item_count': 2,
                  },
                  'merge': {
                    'source_item_count': 3,
                    'unique_item_count': 2,
                    'duplicate_count': 1,
                    'item_count': 2,
                    'omitted_count': 0,
                    'deleted_playlist_count': 2,
                    'retained_playlist_count': 0,
                    'consumed_source_item_count': 3,
                    'truncated': false,
                  },
                }),
                201,
                headers: {'content-type': 'application/json; charset=utf-8'},
              );
            case ('POST', '/api/v1/me/music-box/playlists/mbp_1/items'):
              expect(jsonDecode(request.body), {
                'track_id': 'track_1',
                'source': 'netease',
                'title': '晴天',
                'artists': ['周杰伦'],
              });
              return http.Response(
                jsonEncode({
                  'item': {
                    'id': 'mbpi_1',
                    'playlist_id': 'mbp_1',
                    'track_id': 'track_1',
                    'source': 'netease',
                    'title': '晴天',
                    'artists': ['周杰伦'],
                    'sort_order': 10,
                  },
                }),
                201,
                headers: {'content-type': 'application/json; charset=utf-8'},
              );
            case ('PATCH', '/api/v1/me/music-box/playlists/mbp_1/items/order'):
              expect(jsonDecode(request.body), {
                'item_id': 'mbpi_1',
                'direction': 'down',
              });
              return http.Response(jsonEncode({'ok': true}), 200);
            case ('PATCH', '/api/v1/me/music-box/playlists/order'):
              final body = jsonDecode(request.body) as Map<String, dynamic>;
              if (body.containsKey('playlist_ids')) {
                expect(body, {
                  'playlist_ids': ['mbp_1', 'mbp_2'],
                });
              } else {
                expect(body, {'playlist_id': 'mbp_1', 'direction': 'up'});
              }
              return http.Response(jsonEncode({'ok': true}), 200);
            case ('PATCH', '/api/v1/me/music-box/playlists/mbp_1'):
              expect(jsonDecode(request.body), {'name': '夜间精选'});
              return http.Response(
                jsonEncode({
                  'playlist': {
                    'id': 'mbp_1',
                    'name': '夜间精选',
                    'description': '',
                    'revision': 2,
                    'item_count': 1,
                  },
                }),
                200,
                headers: {'content-type': 'application/json; charset=utf-8'},
              );
            case ('DELETE', '/api/v1/me/music-box/playlists/mbp_1/items'):
              expect(jsonDecode(request.body), {
                'item_ids': ['mbpi_1'],
              });
              return http.Response(jsonEncode({'ok': true, 'deleted': 1}), 200);
            case ('DELETE', '/api/v1/me/music-box/playlists/mbp_1'):
              return http.Response(jsonEncode({'ok': true}), 200);
            default:
              fail('unexpected request: ${request.method} ${request.url}');
          }
        }),
      );

      final playlist = await api.createPersonalMusicPlaylist(name: '夜晚');
      final merged = await api.mergePersonalMusicPlaylists(
        name: '合并结果',
        playlistIds: const ['mbp_2', 'mbp_1'],
      );
      final item = await api.addPersonalMusicPlaylistItem(
        playlistId: playlist.id,
        track: const MusicBoxSearchResult(
          trackId: 'track_1',
          name: '晴天',
          artists: ['周杰伦'],
          source: 'netease',
        ),
      );
      await api.movePersonalMusicPlaylistItem(
        playlistId: playlist.id,
        itemId: item.id,
        direction: 'down',
      );
      await api.pinPersonalMusicPlaylists(playlistIds: [playlist.id, 'mbp_2']);
      await api.movePersonalMusicPlaylist(
        playlistId: playlist.id,
        direction: 'up',
      );
      final renamed = await api.renamePersonalMusicPlaylist(
        playlistId: playlist.id,
        name: '夜间精选',
      );
      await api.deletePersonalMusicPlaylistItems(
        playlistId: playlist.id,
        itemIds: [item.id],
      );
      await api.deletePersonalMusicPlaylist(playlist.id);

      expect(renamed.name, '夜间精选');
      expect(merged.playlist.name, '合并结果');
      expect(merged.duplicateCount, 1);
      expect(merged.truncated, isFalse);
      expect(seen, hasLength(9));
      api.close();
    },
  );

  test('room playlist APIs use room-scoped routes and bodies', () async {
    final seen = <String>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        seen.add('${request.method} ${request.url.path}');
        expect(request.headers['authorization'], 'Bearer token');
        switch ((request.method, request.url.path)) {
          case ('GET', '/api/v1/rooms/room_1/music-box/playlists'):
            expect(request.url.queryParameters, {
              'page': '1',
              'page_size': '50',
            });
            return http.Response(
              jsonEncode({
                'playlists': [
                  {
                    'id': 'mbp_room_1',
                    'name': '房间精选',
                    'description': '',
                    'revision': 1,
                    'item_count': 0,
                  },
                ],
                'pagination': {
                  'page': 1,
                  'page_size': 50,
                  'total': 1,
                  'has_more': false,
                },
                'limits': {'max_playlists': 50, 'max_playlist_items': 500},
              }),
              200,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          case ('POST', '/api/v1/rooms/room_1/music-box/playlists'):
            final body = jsonDecode(request.body) as Map<String, dynamic>;
            final imported = body.containsKey('import_playlist_id');
            expect(
              body,
              imported
                  ? {'name': '导入精选', 'import_playlist_id': 'mbp_personal_1'}
                  : {'name': '新歌单'},
            );
            return http.Response(
              jsonEncode({
                'playlist': {
                  'id': imported ? 'mbp_room_import' : 'mbp_room_2',
                  'name': imported ? '导入精选' : '新歌单',
                  'description': '',
                  'revision': 1,
                  'item_count': imported ? 1 : 0,
                },
              }),
              201,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          case ('POST', '/api/v1/rooms/room_1/music-box/playlists/merge'):
            expect(jsonDecode(request.body), {
              'name': '房间合并',
              'playlist_ids': ['mbp_room_2', 'mbp_room_1'],
            });
            return http.Response(
              jsonEncode({
                'playlist': {
                  'id': 'mbp_room_merged',
                  'name': '房间合并',
                  'description': '',
                  'revision': 1,
                  'item_count': 1,
                },
                'merge': {
                  'source_item_count': 1,
                  'unique_item_count': 1,
                  'duplicate_count': 0,
                  'item_count': 1,
                  'omitted_count': 0,
                  'deleted_playlist_count': 2,
                  'retained_playlist_count': 0,
                  'consumed_source_item_count': 1,
                  'truncated': false,
                },
              }),
              201,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          case (
            'POST',
            '/api/v1/rooms/room_1/music-box/playlists/mbp_room_2/clone-to-me',
          ):
            expect(request.body, isEmpty);
            return http.Response(
              jsonEncode({
                'playlist': {
                  'id': 'mbp_personal_clone_1',
                  'name': '房间备注名 · 新歌单',
                  'description': '',
                  'revision': 1,
                  'item_count': 0,
                },
              }),
              201,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          case (
            'POST',
            '/api/v1/rooms/room_1/music-box/playlists/mbp_room_2/items',
          ):
            expect(jsonDecode(request.body), {
              'track_id': 'track_1',
              'source': 'netease',
              'title': '晴天',
              'artists': ['周杰伦'],
            });
            return http.Response(
              jsonEncode({
                'item': {
                  'id': 'mbpi_room_1',
                  'playlist_id': 'mbp_room_2',
                  'track_id': 'track_1',
                  'source': 'netease',
                  'title': '晴天',
                  'artists': ['周杰伦'],
                  'sort_order': 10,
                },
              }),
              201,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          case ('PATCH', '/api/v1/rooms/room_1/music-box/playlists/order'):
            expect(jsonDecode(request.body), {
              'playlist_ids': ['mbp_room_2', 'mbp_room_1'],
            });
            return http.Response(jsonEncode({'ok': true}), 200);
          case (
            'DELETE',
            '/api/v1/rooms/room_1/music-box/playlists/mbp_room_2/items',
          ):
            expect(jsonDecode(request.body), {
              'item_ids': ['mbpi_room_1'],
            });
            return http.Response(jsonEncode({'ok': true, 'deleted': 1}), 200);
          case (
            'DELETE',
            '/api/v1/rooms/room_1/music-box/playlists/mbp_room_2',
          ):
            return http.Response(jsonEncode({'ok': true}), 200);
          default:
            fail('unexpected request: ${request.method} ${request.url}');
        }
      }),
    );

    final listed = await api.listRoomMusicPlaylists(roomId: 'room_1');
    final created = await api.createRoomMusicPlaylist(
      roomId: 'room_1',
      name: '新歌单',
    );
    final imported = await api.createRoomMusicPlaylistFromPersonal(
      roomId: 'room_1',
      name: '导入精选',
      importPlaylistId: 'mbp_personal_1',
    );
    final merged = await api.mergeRoomMusicPlaylists(
      roomId: 'room_1',
      name: '房间合并',
      playlistIds: const ['mbp_room_2', 'mbp_room_1'],
    );
    final item = await api.addRoomMusicPlaylistItem(
      roomId: 'room_1',
      playlistId: created.id,
      track: const MusicBoxSearchResult(
        trackId: 'track_1',
        name: '晴天',
        artists: ['周杰伦'],
        source: 'netease',
      ),
    );
    final cloned = await api.cloneRoomMusicPlaylistToPersonal(
      roomId: 'room_1',
      playlistId: created.id,
    );
    await api.pinRoomMusicPlaylists(
      roomId: 'room_1',
      playlistIds: [created.id, listed.playlists.single.id],
    );
    await api.deleteRoomMusicPlaylistItems(
      roomId: 'room_1',
      playlistId: created.id,
      itemIds: [item.id],
    );
    await api.deleteRoomMusicPlaylist(roomId: 'room_1', playlistId: created.id);

    expect(imported.itemCount, 1);
    expect(cloned.name, '房间备注名 · 新歌单');
    expect(merged.playlist.name, '房间合并');
    expect(seen, hasLength(9));
    api.close();
  });

  test('playlist batch add APIs preserve selected item order', () async {
    final seen = <String>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        seen.add('${request.method} ${request.url.path}');
        expect(request.headers['authorization'], 'Bearer token');
        expect(jsonDecode(request.body), {
          'source_playlist_id': request.url.path.contains('/rooms/')
              ? 'room_source'
              : 'personal_source',
          'item_ids': ['second', 'first'],
        });
        return http.Response(
          jsonEncode({
            'playlist': {
              'id': request.url.path.contains('/rooms/')
                  ? 'room_target'
                  : 'personal_target',
              'name': '目标歌单',
              'description': '',
              'revision': 3,
              'item_count': 500,
            },
            'batch_add': {
              'selected_item_count': 2,
              'unique_item_count': 2,
              'duplicate_count': 0,
              'already_present_count': 0,
              'added_item_count': 1,
              'omitted_count': 1,
              'truncated': true,
            },
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final personal = await api.batchAddPersonalMusicPlaylistItems(
      sourcePlaylistId: 'personal_source',
      targetPlaylistId: 'personal_target',
      itemIds: const ['second', 'first'],
    );
    final room = await api.batchAddRoomMusicPlaylistItems(
      roomId: 'room_1',
      sourcePlaylistId: 'room_source',
      targetPlaylistId: 'room_target',
      itemIds: const ['second', 'first'],
    );

    expect(seen, [
      'POST /api/v1/me/music-box/playlists/personal_target/items/batch-add',
      'POST /api/v1/rooms/room_1/music-box/playlists/room_target/items/batch-add',
    ]);
    expect(personal.addedItemCount, 1);
    expect(personal.omittedCount, 1);
    expect(personal.truncated, isTrue);
    expect(room.playlist.id, 'room_target');
    api.close();
  });
}

Map<String, Object?> _roomInviteJson({
  String status = 'pending',
  bool roomExists = true,
  String? invalidReason,
  String inviterRoomRole = 'owner',
  bool inviterExists = true,
  bool roomJoined = false,
  bool includeViewerRoomInfo = false,
}) {
  return {
    'id': 'rinv_1',
    'status': status,
    'created_at': '2026-05-31T13:00:00Z',
    'updated_at': '2026-05-31T13:00:00Z',
    'room_exists': roomExists,
    'inviter_exists': inviterExists,
    'invalid_reason': invalidReason,
    'room': {
      'id': 'room_1',
      'rid': '900001',
      'name': 'Invite Room',
      'description': 'Invite room bio',
      'avatar_url': null,
      'default_avatar_key': 'room-1',
      'visibility': 'private',
      'join_policy': 'closed',
      'member_count': 1,
      'online_member_count': 1,
      'live_participant_count': 0,
      'joined': roomJoined,
      'join_state': roomJoined ? 'joined' : 'none',
      'created_by': {
        'id': 'user_1',
        'uid': '1000001',
        'username': 'alice',
        'display_name': 'Alice',
        'avatar_url': null,
        'default_avatar_key': 'blue-3',
      },
      if (includeViewerRoomInfo) ...{
        'personal_profile': {
          'display_name': 'Alice in Room',
          'avatar_url': '/room-alice.png',
          'default_avatar_key': 'green-2',
        },
        'my_membership': {
          'joined_at': '2026-05-31T14:00:00Z',
          'role': 'member',
        },
      },
    },
    'inviter': {
      'id': 'user_1',
      'uid': '1000001',
      'username': 'alice',
      'display_name': 'Alice',
      'avatar_url': null,
      'default_avatar_key': 'blue-3',
      'room_display_name': 'Alice in Invite Room',
      'room_role': inviterRoomRole,
    },
  };
}

Map<String, Object?> _roomBlacklistEntryJson() {
  return {
    'user': {
      'id': 'user_2',
      'uid': '1000002',
      'username': 'blocked_user',
      'display_name': 'Blocked User',
      'avatar_url': null,
      'default_avatar_key': 'red-2',
    },
    'blocked_by': {
      'id': 'user_1',
      'uid': '1000001',
      'username': 'alice',
      'display_name': 'Alice',
      'avatar_url': null,
      'default_avatar_key': 'blue-3',
    },
    'created_at': '2026-05-31T13:00:00Z',
  };
}

Map<String, Object?> _roomApplicationJson({
  String status = 'pending',
  String reason = '',
  bool reviewerExists = true,
}) {
  final reviewed = status == 'approved' || status == 'rejected';
  return {
    'id': 'jrq_1',
    'status': status,
    'reason': reason,
    'created_at': '2026-05-30T13:00:00Z',
    'updated_at': '2026-05-31T13:00:00Z',
    'reviewed_at': reviewed ? '2026-05-31T13:00:00Z' : null,
    'room': _roomInviteJson()['room'],
    'reviewer_exists': reviewerExists,
    'reviewer': reviewed
        ? {
            'id': 'user_1',
            'uid': '1000001',
            'username': 'alice',
            'display_name': 'Alice',
            'avatar_url': null,
            'default_avatar_key': 'blue-3',
            'room_display_name': 'Alice in Invite Room',
            'room_role': 'owner',
          }
        : null,
  };
}

Map<String, Object?> _roomEventNotificationJson({String? readAt}) {
  return {
    'id': 'rnot_1',
    'type': 'role_promoted',
    'created_at': '2026-06-01T13:00:00Z',
    'read_at': readAt,
    'room_exists': true,
    'actor_exists': true,
    'from_role': 'member',
    'to_role': 'admin',
    'message_id': 'msg_mention_1',
    'message_preview': '@Alice hello',
    'room': _roomInviteJson(roomJoined: true)['room'],
    'actor': {
      'id': 'user_1',
      'uid': '1000001',
      'username': 'alice',
      'display_name': 'Alice',
      'avatar_url': null,
      'default_avatar_key': 'blue-3',
      'room_display_name': 'Alice in Invite Room',
      'room_role': 'owner',
    },
  };
}

Map<String, Object?> _roomDetailJson() {
  return {
    'id': 'room_1',
    'rid': '900001',
    'name': 'Invite Room',
    'avatar_url': null,
    'default_avatar_key': 'room-1',
    'member_count': 2,
    'online_member_count': 1,
    'created_by': {
      'id': 'user_1',
      'uid': '1000001',
      'username': 'alice',
      'display_name': 'Alice',
    },
    'my_membership': {'joined_at': '2026-05-31T14:00:00Z', 'role': 'member'},
    'live': {
      'room_id': 'room_1',
      'participant_count': 0,
      'participants': <Object?>[],
      'updated_at': '2026-05-31T14:00:00Z',
    },
    'created_at': '2026-05-31T13:00:00Z',
    'updated_at': '2026-05-31T14:00:00Z',
  };
}

Map<String, Object?> _roomCardSearchJson() {
  return {
    'id': 'room_1',
    'rid': '900001',
    'name': 'Alpha Room',
    'avatar_url': null,
    'default_avatar_key': 'room-1',
    'visibility': 'public',
    'join_policy': 'open',
    'description': 'Searchable room',
    'notification_policy': 'all',
    'member_count': 3,
    'online_member_count': 1,
    'live_participant_count': 0,
    'live_avatar_preview': <Object?>[],
    'last_message': null,
    'unread_count': 0,
    'updated_at': '2026-05-31T14:00:00Z',
  };
}

Map<String, Object?> _publicRoomSearchJson() {
  return {
    'id': 'room_2',
    'rid': '900002',
    'name': 'Alpha Public',
    'avatar_url': null,
    'default_avatar_key': 'room-2',
    'visibility': 'public',
    'join_policy': 'open',
    'member_count': 2,
    'online_member_count': 0,
    'live_participant_count': 0,
    'joined': false,
    'join_state': 'none',
  };
}

Map<String, Object?> _searchRoomContextJson() {
  return {
    'id': 'room_1',
    'rid': '900001',
    'name': 'Alpha Room',
    'avatar_url': null,
    'default_avatar_key': 'room-1',
  };
}

Map<String, Object?> _messageSearchJson({
  String type = 'text',
  String body = 'hello',
  List<Map<String, Object?>> attachments = const [],
}) {
  return {
    'id': 'msg_1',
    'room_id': 'room_1',
    'sender': {
      'id': 'user_1',
      'username': 'alice',
      'display_name': 'Alice',
      'default_avatar_key': 'blue-3',
    },
    'client_message_id': 'cmsg_1',
    'type': type,
    'body': body,
    'attachments': attachments,
    'created_at': '2026-05-31T14:00:00Z',
  };
}

Map<String, Object?> _liveJoinJson() {
  final participant = {
    'live_session_id': 'live_1',
    'user': {'id': 'user_1', 'username': 'alice', 'display_name': 'Alice'},
    'joined_at': '2026-05-31T14:00:00Z',
    'mic_muted': true,
    'headphones_muted': false,
    'voice_blocked': false,
    'camera_on': false,
    'screen_sharing': false,
    'connection_state': 'joining',
  };

  return {
    'livekit': {
      'server_url': 'wss://voice.example.com',
      'token': 'livekit-token',
      'token_expires_at': '2026-05-31T14:10:00Z',
      'room_name': 'room_1',
    },
    'participant': participant,
    'live': {
      'room_id': 'room_1',
      'participant_count': 1,
      'participants': [participant],
      'updated_at': '2026-05-31T14:00:00Z',
    },
  };
}

Map<String, Object?> _stickerPackJson({
  String name = 'My Stickers',
  List<Map<String, Object?>> stickers = const [],
}) {
  return {
    'pack': {
      'id': 'stkp_1',
      'scope': 'personal',
      'room_id': null,
      'name': name,
      'sort_order': 10,
      'updated_at': '2026-06-02T08:00:00Z',
      'stickers': stickers,
    },
  };
}

Map<String, Object?> _stickerJson({
  String id = 'stk_1',
  String name = 'ok',
  int sortOrder = 10,
}) {
  final assetId = 'asset_$id';
  return {
    'id': id,
    'name': name,
    'sort_order': sortOrder,
    'asset': {
      'id': assetId,
      'url': '/assets/$assetId/ok.webp',
      'thumbnail_url': '/assets/$assetId/ok.webp',
      'mime_type': 'image/webp',
      'width': 128,
      'height': 128,
      'created_at': '2026-06-02T07:59:00Z',
    },
  };
}

Map<String, Object?> _personalStickerPacksJson({String? linkedAssetId}) {
  return {
    'packs': [
      {
        'id': 'stkp_1',
        'scope': 'personal',
        'room_id': null,
        'name': 'My Stickers',
        'sort_order': 10,
        'updated_at': '2026-06-02T08:00:00Z',
        'stickers': [
          if (linkedAssetId != null)
            {
              'id': 'stk_2',
              'name': 'hi',
              'sort_order': 10,
              'asset': {
                'id': linkedAssetId,
                'url': '/assets/$linkedAssetId/hi.webp',
                'thumbnail_url': '/assets/$linkedAssetId/hi.webp',
                'mime_type': 'image/webp',
              },
            },
        ],
      },
    ],
  };
}
