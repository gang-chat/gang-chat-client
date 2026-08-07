import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/room_message_history.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test('history categories expose stable server values', () {
    expect(
      RoomMessageHistoryCategory.values.map((category) => category.apiValue),
      [
        'all',
        'links',
        'voice',
        'stickers',
        'images',
        'files',
        'music',
        'system',
      ],
    );
  });

  test('history copy contains only message content', () {
    final message = Message(
      id: 'msg_1',
      roomId: 'room_1',
      sender: const UserSummary(
        id: 'user_1',
        username: 'alice',
        displayName: 'Alice',
        avatarUrl: null,
        defaultAvatarKey: 'blue-3',
      ),
      clientMessageId: 'client_1',
      body: '  hello world  ',
      createdAt: DateTime.utc(2026, 7, 13, 8, 30),
    );

    expect(roomMessageHistoryCopyText(message), 'hello world');
  });

  test('date filtering uses a local inclusive end day', () {
    final day = DateTime(2026, 7, 13, 20, 30);

    expect(roomMessageHistoryDayStart(day), DateTime(2026, 7, 13));
    expect(roomMessageHistoryDayEndExclusive(day), DateTime(2026, 7, 14));
  });
}
