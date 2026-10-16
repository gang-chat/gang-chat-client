import { expect, test } from 'vitest';

import { fnv1a64, fnv1a64Signed } from '../../src/app/fnv_hash';

// Expected values come from the earlier native `int` implementations, so a
// change here would orphan every cached file on disk.
test('fnv1a64 matches the 64-bit FNV-1a reference values', () => {
  expect(fnv1a64('').toString(16)).toBe('cbf29ce484222325');
  expect(fnv1a64('foobar').toString(16)).toBe('85944171f73967e8');
});

test('signed hashes render like the native int implementation', () => {
  const expected: Record<string, string> = {
    '': '-340d631b7bdddcdb',
    a: '-509c23b379fe1374',
    foobar: '-7a6bbe8e08c69818',
    'https://ky-z.com/gang-chat/uploads/avatars/u1.png': '1760ab0d0fbe3141',
    'https://os.ky-z.com:9000/gang-chat/files/表情.gif?v=2': '-435febba40bcd2c2',
  };
  for (const [key, value] of Object.entries(expected)) {
    expect(fnv1a64Signed(key).toString(16).padStart(16, '0'), key).toBe(value);
  }
});
