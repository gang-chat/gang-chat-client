import { expect, test } from 'vitest';

import { delay } from '../../src/lib/async';
import { StreamController } from '../../src/lib/event_stream';
import { AudioOutputRebinder } from '../../src/live/audio_output_rebinder';

// Port of test/unit/live/audio_output_rebinder_test.dart.
test('rebinds output and re-applies routing on a device change', async () => {
  const changes = new StreamController<void>();
  const selected: string[] = [];
  let rebounds = 0;

  const rebinder = new AudioOutputRebinder({
    deviceChanges: changes.stream,
    currentOutputDeviceId: async () => 'speaker_1',
    selectOutput: async (id) => {
      selected.push(id);
    },
    onRebound: async () => {
      rebounds += 1;
    },
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  await delay(30);

  expect(selected).toEqual(['speaker_1']);
  expect(rebounds).toBe(1);
  await rebinder.stop();
});

test('coalesces a burst of changes into a single rebind', async () => {
  const changes = new StreamController<void>();
  let selects = 0;
  let rebounds = 0;

  const rebinder = new AudioOutputRebinder({
    deviceChanges: changes.stream,
    currentOutputDeviceId: async () => 'speaker_1',
    selectOutput: async () => {
      selects += 1;
    },
    onRebound: async () => {
      rebounds += 1;
    },
    debounce: 20,
  });
  rebinder.start();

  // A single profile flip emits several events in quick succession.
  changes.add();
  changes.add();
  changes.add();
  await delay(50);

  expect(selects).toBe(1);
  expect(rebounds).toBe(1);
  await rebinder.stop();
});

test('re-applies routing even without a known output id', async () => {
  const changes = new StreamController<void>();
  let selects = 0;
  let rebounds = 0;

  const rebinder = new AudioOutputRebinder({
    deviceChanges: changes.stream,
    currentOutputDeviceId: async () => null,
    selectOutput: async () => {
      selects += 1;
    },
    onRebound: async () => {
      rebounds += 1;
    },
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  await delay(30);

  // No id to select, but routing is still refreshed so volumes re-bind to
  // whatever endpoint playback ended up on.
  expect(selects).toBe(0);
  expect(rebounds).toBe(1);
  await rebinder.stop();
});

test('a failed rebind is swallowed and does not block later ones', async () => {
  const changes = new StreamController<void>();
  let attempts = 0;

  const rebinder = new AudioOutputRebinder({
    deviceChanges: changes.stream,
    currentOutputDeviceId: async () => {
      attempts += 1;
      if (attempts === 1) throw new Error('native failure');
      return 'speaker_2';
    },
    selectOutput: async () => {},
    onRebound: async () => {},
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  await delay(30);
  changes.add();
  await delay(30);

  expect(attempts).toBe(2);
  await rebinder.stop();
});

test('stop cancels a pending rebind', async () => {
  const changes = new StreamController<void>();
  let rebounds = 0;

  const rebinder = new AudioOutputRebinder({
    deviceChanges: changes.stream,
    currentOutputDeviceId: async () => 'speaker_1',
    selectOutput: async () => {},
    onRebound: async () => {
      rebounds += 1;
    },
    debounce: 30,
  });
  rebinder.start();

  changes.add();
  await rebinder.stop();
  await delay(50);

  expect(rebounds).toBe(0);
});

test('ignores events after stop', async () => {
  const changes = new StreamController<void>();
  let rebounds = 0;

  const rebinder = new AudioOutputRebinder({
    deviceChanges: changes.stream,
    currentOutputDeviceId: async () => 'speaker_1',
    selectOutput: async () => {},
    onRebound: async () => {
      rebounds += 1;
    },
    debounce: 10,
  });
  rebinder.start();
  await rebinder.stop();

  changes.add();
  await delay(30);

  expect(rebounds).toBe(0);
});
