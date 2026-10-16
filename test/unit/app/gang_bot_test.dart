import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:client/src/app/message_mentions.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';

Map<String, Object?> _roomDetailJson({Map<String, Object?> extra = const {}}) {
  return {
    'id': 'room_1',
    'name': 'Room',
    'avatar_url': null,
    'default_avatar_key': 'blue-3',
    'member_count': 2,
    'my_membership': {'joined_at': '2026-10-15T10:00:00Z', 'role': 'owner'},
    'live': {
      'room_id': 'room_1',
      'participant_count': 0,
      'participants': <Object?>[],
      'updated_at': '2026-10-15T10:00:00Z',
    },
    'created_at': '2026-10-15T09:00:00Z',
    'updated_at': '2026-10-15T10:00:00Z',
    ...extra,
  };
}

void main() {
  group('gang-bot mentions', () {
    test('offers gang-bot first only when the room has it on', () {
      final withBot = messageMentionOptions(
        members: const [],
        query: '',
        ownerUserId: null,
        includeGangBot: true,
      );
      expect(withBot.first.label, kMentionGangBotLabel);
      expect(withBot.first.kind, MessageMentionKind.bot);
      expect(withBot.first.roleLabel, 'AI');

      final withoutBot = messageMentionOptions(
        members: const [],
        query: '',
        ownerUserId: null,
      );
      expect(
        withoutBot.map((option) => option.label),
        isNot(contains(kMentionGangBotLabel)),
      );
    });

    test('matches the bot by name and common aliases', () {
      for (final query in ['gang', 'bot', 'ai', '机器']) {
        final options = messageMentionOptions(
          members: const [],
          query: query,
          ownerUserId: null,
          includeGangBot: true,
        );
        expect(
          options.map((option) => option.kind),
          contains(MessageMentionKind.bot),
          reason: query,
        );
      }
      final unrelated = messageMentionOptions(
        members: const [],
        query: 'zzz',
        ownerUserId: null,
        includeGangBot: true,
      );
      expect(unrelated, isEmpty);
    });

    test('sends a bot mention, never a user one', () {
      final descriptors = messageMentionDescriptors(
        text: '@gang-bot 你怎么看',
        members: const [],
        confirmedLabels: const ['gang-bot'],
      );
      expect(descriptors, [
        {'type': 'bot', 'label': 'gang-bot'},
      ]);
      expect(messageMentionKindForLabel('Gang-Bot'), MessageMentionKind.bot);
      const someone = UserSummary(
        id: 'u1',
        username: 'gang-bot-fan',
        displayName: 'gang-bot',
        avatarUrl: null,
        defaultAvatarKey: 'blue-3',
      );
      expect(
        messageMentionLabelTargetsUser(
          label: 'gang-bot',
          user: someone,
          ownerUserId: null,
        ),
        isFalse,
      );
    });
  });

  group('gang-bot models', () {
    test('marks the bot sender', () {
      final sender = UserSummary.fromJson({
        'id': 'gang-bot',
        'username': 'gang-bot',
        'display_name': 'gang-bot',
        'is_bot': true,
      });
      expect(sender.isBot, isTrue);
      expect(sender.copyWith(displayName: 'x').isBot, isTrue);
      expect(
        UserSummary.fromJson({'id': 'u1', 'username': 'alice'}).isBot,
        isFalse,
      );
    });

    test('reads the room switch, defaulting to unavailable', () {
      final card = RoomCard.fromJson({
        'id': 'r1',
        'name': 'Room',
        'member_count': 2,
        'updated_at': '2026-10-15T10:00:00Z',
      });
      expect(card.gangBotAvailable, isFalse);
      expect(card.gangBotActive, isFalse);

      final detail = RoomDetail.fromJson(
        _roomDetailJson(
          extra: {'gang_bot_available': true, 'gang_bot_enabled': false},
        ),
      );
      expect(detail.gangBotAvailable, isTrue);
      expect(detail.gangBotEnabled, isFalse);
      expect(detail.gangBotActive, isFalse);
      expect(detail.toCard().gangBotAvailable, isTrue);
      expect(detail.copyWithRole('admin').gangBotEnabled, isFalse);
    });
  });

  test('updateRoom sends the gang-bot switch', () async {
    Object? sentBody;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'PATCH');
        expect(request.url.path, '/api/v1/rooms/room_1');
        sentBody = jsonDecode(utf8.decode(request.bodyBytes));
        return http.Response(
          jsonEncode({
            'room': _roomDetailJson(
              extra: {'gang_bot_available': true, 'gang_bot_enabled': false},
            ),
          }),
          200,
        );
      }),
    );
    final room = await api.updateRoom(roomId: 'room_1', gangBotEnabled: false);
    expect(sentBody, {'gang_bot_enabled': false});
    expect(room.gangBotEnabled, isFalse);
  });
}
