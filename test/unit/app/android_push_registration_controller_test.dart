import 'dart:async';

import 'package:client/src/app/android_push_registration_controller.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/shell/android_system_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('registers initial token and refreshed token in order', () async {
    final android = _FakeAndroidSystemService(
      const AndroidPushRegistration(
        provider: 'fcm',
        installationId: 'install-1',
        token: 'token-1',
        enabled: true,
      ),
    );
    final api = _RecordingGangApi();
    final controller = AndroidPushRegistrationController(
      api: api,
      androidSystemService: android,
    );
    addTearDown(() async {
      await controller.dispose();
      await android.dispose();
    });

    await controller.start();
    android.emit(
      const AndroidPushRegistration(
        provider: 'fcm',
        installationId: 'install-1',
        token: 'token-2',
        enabled: false,
      ),
    );
    await Future<void>.delayed(Duration.zero);
    await controller.synchronize();

    expect(api.upserts, [
      'fcm/install-1/token-1/true',
      'fcm/install-1/token-2/false',
      'fcm/install-1/token-2/false',
    ]);
  });

  test(
    'dispose keeps registration while explicit unregister deletes it',
    () async {
      final android = _FakeAndroidSystemService(
        const AndroidPushRegistration(
          provider: 'fcm',
          installationId: 'install-2',
          token: 'token-2',
          enabled: true,
        ),
      );
      final api = _RecordingGangApi();
      final controller = AndroidPushRegistrationController(
        api: api,
        androidSystemService: android,
      );
      await controller.start();

      await controller.unregister();
      expect(api.deletes, ['fcm/install-2']);

      await controller.dispose();
      expect(api.deletes, hasLength(1));
      await android.dispose();
    },
  );
}

class _FakeAndroidSystemService extends AndroidSystemService {
  _FakeAndroidSystemService(this.registration);

  AndroidPushRegistration? registration;
  final _changes = StreamController<AndroidPushRegistration>.broadcast();

  @override
  bool get isSupported => true;

  @override
  Stream<AndroidPushRegistration> get pushRegistrationChanges =>
      _changes.stream;

  @override
  Future<AndroidPushRegistration?> pushRegistration() async => registration;

  void emit(AndroidPushRegistration value) {
    registration = value;
    _changes.add(value);
  }

  Future<void> dispose() => _changes.close();
}

class _RecordingGangApi implements GangApi {
  final upserts = <String>[];
  final deletes = <String>[];

  @override
  Future<void> upsertPushDevice({
    required String provider,
    required String installationId,
    required String token,
    required bool enabled,
  }) async {
    upserts.add('$provider/$installationId/$token/$enabled');
  }

  @override
  Future<void> deletePushDevice({
    required String provider,
    required String installationId,
  }) async {
    deletes.add('$provider/$installationId');
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
