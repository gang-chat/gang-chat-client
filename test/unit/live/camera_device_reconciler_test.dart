import 'dart:async';

import 'package:client/src/live/camera_device_reconciler.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('camera topology burst is debounced to one refresh', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var refreshes = 0;
    final reconciler = CameraDeviceReconciler(
      deviceChanges: changes.stream,
      refreshDevices: () async => refreshes += 1,
      debounce: const Duration(milliseconds: 10),
    );
    addTearDown(reconciler.stop);
    reconciler.start();

    changes
      ..add(null)
      ..add(null)
      ..add(null);
    await _waitFor(() => refreshes == 1);
  });

  test('timed-out camera refresh does not block a later hotplug', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    final firstStarted = Completer<void>();
    final neverCompletes = Completer<void>();
    var attempts = 0;
    var completed = 0;
    final reconciler = CameraDeviceReconciler(
      deviceChanges: changes.stream,
      refreshDevices: () async {
        attempts += 1;
        if (attempts == 1) {
          firstStarted.complete();
          await neverCompletes.future;
          return;
        }
        completed += 1;
      },
      debounce: Duration.zero,
      operationTimeout: const Duration(milliseconds: 20),
    );
    addTearDown(reconciler.stop);
    reconciler.start();

    changes.add(null);
    await firstStarted.future;
    await Future<void>.delayed(const Duration(milliseconds: 30));
    changes.add(null);
    await _waitFor(() => completed == 1);

    expect(attempts, 2);
  });

  test('stop cancels logical wait for an in-flight camera refresh', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    final started = Completer<void>();
    final neverCompletes = Completer<void>();
    final reconciler = CameraDeviceReconciler(
      deviceChanges: changes.stream,
      refreshDevices: () async {
        started.complete();
        await neverCompletes.future;
      },
      debounce: Duration.zero,
      operationTimeout: const Duration(seconds: 30),
    );
    reconciler.start();

    changes.add(null);
    await started.future;
    await reconciler.stop().timeout(const Duration(milliseconds: 200));
  });
}

Future<void> _waitFor(bool Function() condition) async {
  final deadline = DateTime.now().add(const Duration(seconds: 2));
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) {
      fail('Timed out waiting for camera-device reconciliation.');
    }
    await Future<void>.delayed(const Duration(milliseconds: 1));
  }
}
