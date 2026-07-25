import 'dart:async';

import '../protocol/api_client.dart';
import '../shell/android_system_service.dart';

/// Keeps the Android push token associated with the authenticated API session.
///
/// Registering a device is intentionally independent from the realtime
/// connection: it must not make an offline account appear online. Disposing
/// this controller also keeps the registration intact so process death or
/// removing the app from Recents behaves like a mobile app going offline, not
/// like an explicit logout.
class AndroidPushRegistrationController {
  AndroidPushRegistrationController({
    required this.api,
    required this.androidSystemService,
  });

  final GangApi api;
  final AndroidSystemService androidSystemService;

  StreamSubscription<AndroidPushRegistration>? _changes;
  AndroidPushRegistration? _lastRegistration;
  Future<void> _operationTail = Future<void>.value();
  bool _disposed = false;

  Future<void> start() async {
    if (_disposed || !androidSystemService.isSupported || _changes != null) {
      return;
    }
    _changes = androidSystemService.pushRegistrationChanges.listen((
      registration,
    ) {
      unawaited(
        _enqueue(() => _upsert(registration)).catchError((Object _) {}),
      );
    });
    await synchronize();
  }

  Future<void> synchronize() {
    return _enqueue(() async {
      final registration = await androidSystemService.pushRegistration();
      if (registration == null) return;
      await _upsert(registration);
    });
  }

  Future<void> unregister() {
    return _enqueue(() async {
      final registration =
          _lastRegistration ?? await androidSystemService.pushRegistration();
      if (registration == null) return;
      await api.deletePushDevice(
        provider: registration.provider,
        installationId: registration.installationId,
      );
      _lastRegistration = null;
    });
  }

  Future<void> _upsert(AndroidPushRegistration registration) async {
    if (_disposed) return;
    await api.upsertPushDevice(
      provider: registration.provider,
      installationId: registration.installationId,
      token: registration.token,
      enabled: registration.enabled,
    );
    _lastRegistration = registration;
  }

  Future<void> _enqueue(Future<void> Function() operation) {
    if (_disposed) return Future<void>.value();
    final next = _operationTail.then((_) => operation());
    _operationTail = next.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    return next;
  }

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    final changes = _changes;
    _changes = null;
    if (changes != null) await changes.cancel();
  }
}
