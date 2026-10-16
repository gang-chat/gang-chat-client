import { expect, test } from 'vitest';

import { Completer, delay, withTimeout } from '../../src/lib/async';
import { StreamController } from '../../src/lib/event_stream';
import { CameraDeviceReconciler } from '../../src/live/camera_device_reconciler';
import { waitFor } from './live_test_helpers';

// Port of test/unit/live/camera_device_reconciler_test.dart.
test('camera topology burst is debounced to one refresh', async () => {
  const changes = new StreamController<void>();
  let refreshes = 0;
  const reconciler = new CameraDeviceReconciler({
    deviceChanges: changes.stream,
    refreshDevices: async () => {
      refreshes += 1;
    },
    debounce: 10,
  });
  reconciler.start();

  changes.add();
  changes.add();
  changes.add();
  await waitFor(() => refreshes === 1);
  await delay(30);
  expect(refreshes).toBe(1);
  await reconciler.stop();
  changes.close();
});

test('timed-out camera refresh does not block a later hotplug', async () => {
  const changes = new StreamController<void>();
  const firstStarted = new Completer<void>();
  const neverCompletes = new Completer<void>();
  let attempts = 0;
  let completed = 0;
  const reconciler = new CameraDeviceReconciler({
    deviceChanges: changes.stream,
    refreshDevices: async () => {
      attempts += 1;
      if (attempts === 1) {
        firstStarted.complete();
        await neverCompletes.promise;
        return;
      }
      completed += 1;
    },
    debounce: 0,
    operationTimeout: 20,
  });
  reconciler.start();

  changes.add();
  await firstStarted.promise;
  await delay(30);
  changes.add();
  await waitFor(() => completed === 1);

  expect(attempts).toBe(2);
  await reconciler.stop();
  changes.close();
});

test('stop cancels logical wait for an in-flight camera refresh', async () => {
  const changes = new StreamController<void>();
  const started = new Completer<void>();
  const neverCompletes = new Completer<void>();
  const reconciler = new CameraDeviceReconciler({
    deviceChanges: changes.stream,
    refreshDevices: async () => {
      started.complete();
      await neverCompletes.promise;
    },
    debounce: 0,
    operationTimeout: 30_000,
  });
  reconciler.start();

  changes.add();
  await started.promise;
  await withTimeout(reconciler.stop(), 200);
  changes.close();
});
