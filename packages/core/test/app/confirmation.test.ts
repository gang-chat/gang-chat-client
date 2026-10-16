import { expect, test } from 'vitest';

import { matchesConfirmationText } from '../../src/app/confirmation';

test('matchesConfirmationText trims input but keeps exact target matching', () => {
  expect(matchesConfirmationText(' room-name ', 'room-name')).toBe(true);
  expect(matchesConfirmationText('Room-Name', 'room-name')).toBe(false);
  expect(matchesConfirmationText('room-name-extra', 'room-name')).toBe(false);
});
