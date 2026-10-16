import { beforeEach, expect, test } from 'vitest';
import { LocalAutoUpdatePromptStore } from '../../src/shell/local_auto_update_prompt_store';
import { installFakePlatform } from '../support/fake_platform';

beforeEach(() => {
  installFakePlatform({ preferences: {} });
});

test('auto update prompt defaults to enabled', async () => {
  const store = new LocalAutoUpdatePromptStore();

  expect(await store.read()).toBe(true);
});

test('auto update prompt persists as a local app-wide preference', async () => {
  const store = new LocalAutoUpdatePromptStore();

  await store.write(true);
  expect(await store.read()).toBe(true);

  await store.write(false);
  expect(await store.read()).toBe(false);
});

test('ignored update version persists and can be cleared', async () => {
  const store = new LocalAutoUpdatePromptStore();

  expect(await store.readIgnoredVersion()).toBeNull();

  await store.writeIgnoredVersion(' 0.5.1 ');
  expect(await store.readIgnoredVersion()).toBe('0.5.1');

  await store.writeIgnoredVersion(null);
  expect(await store.readIgnoredVersion()).toBeNull();
});
