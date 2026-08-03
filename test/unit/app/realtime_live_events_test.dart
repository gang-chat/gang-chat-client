import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/realtime_live_events.dart';

void main() {
  test('all server live reconnect snapshots use the live state route', () {
    expect(
      [
        'live_participant_reconnecting',
        'live_participant_reconnected',
        'live_participants_reconciled',
        'live_room_reconnecting',
      ].every(isLiveSnapshotRealtimeEventType),
      isTrue,
    );
  });

  test('unrelated realtime events stay on their dedicated routes', () {
    expect(isLiveSnapshotRealtimeEventType('room_updated'), isFalse);
    expect(isLiveSnapshotRealtimeEventType('message_created'), isFalse);
  });
}
