// ignore_for_file: implementation_imports

import 'package:flutter_test/flutter_test.dart';
import 'package:livekit_client/src/extensions.dart';
import 'package:livekit_client/src/proto/livekit_models.pbenum.dart'
    as protocol;
import 'package:livekit_client/src/types/other.dart' as sdk;

void main() {
  test('every protocol disconnect reason has a safe SDK mapping', () {
    for (final reason in protocol.DisconnectReason.values) {
      expect(
        () => reason.toSDKType(),
        returnsNormally,
        reason: 'Missing compatibility mapping for ${reason.name}',
      );
    }
  });

  test('mobile process timeout remains a normal participant departure', () {
    expect(
      protocol.DisconnectReason.CONNECTION_TIMEOUT.toSDKType(),
      sdk.DisconnectReason.disconnected,
    );
  });

  test('new room and signaling reasons retain their existing semantics', () {
    expect(
      protocol.DisconnectReason.ROOM_CLOSED.toSDKType(),
      sdk.DisconnectReason.roomDeleted,
    );
    expect(
      protocol.DisconnectReason.SIGNAL_CLOSE.toSDKType(),
      sdk.DisconnectReason.signalingConnectionFailure,
    );
  });
}
