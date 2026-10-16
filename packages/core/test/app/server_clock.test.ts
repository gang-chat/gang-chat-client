import { describe, expect, test } from 'vitest';

import { ServerClock, parseServerTimeHeader } from '../../src/app/server_clock';

const minute = 60_000;

describe('server_clock', () => {
  test('parses valid server time headers as UTC', () => {
    expect(parseServerTimeHeader('2026-07-08T09:00:00Z')).toEqual(new Date(Date.UTC(2026, 6, 8, 9)));
    expect(parseServerTimeHeader('')).toBeNull();
    expect(parseServerTimeHeader('not-a-time')).toBeNull();
  });

  test('normalizes whitespace and explicit timezone offsets to UTC', () => {
    expect(parseServerTimeHeader(' 2026-07-08T17:00:00+08:00 ')).toEqual(new Date(Date.UTC(2026, 6, 8, 9)));
  });

  test('derives current time from server time and monotonic elapsed time', () => {
    let localNow = new Date(Date.UTC(2026, 6, 8, 1));
    let monotonicNow = 0;
    const clock = new ServerClock({ localNow: () => localNow, monotonicNow: () => monotonicNow });
    let notifications = 0;
    clock.addListener(() => (notifications += 1));

    expect(clock.now()).toEqual(localNow);

    expect(clock.updateFromHeader('2026-07-08T09:00:00Z')).toBe(true);
    expect(notifications).toBe(1);

    localNow = new Date(Date.UTC(2026, 6, 8, 3, 5));
    monotonicNow = 5 * minute;
    expect(clock.now()).toEqual(new Date(Date.UTC(2026, 6, 8, 9, 5)));

    expect(clock.updateFromHeader('not-a-time')).toBe(false);
    expect(notifications).toBe(1);
  });

  test('server-derived time ignores local wall-clock rollback', () => {
    let localNow = new Date(Date.UTC(2026, 6, 8, 1));
    let monotonicNow = 0;
    const clock = new ServerClock({ localNow: () => localNow, monotonicNow: () => monotonicNow });

    expect(clock.updateFromHeader('2026-07-08T09:00:00Z')).toBe(true);
    localNow = new Date(Date.UTC(2020, 0, 1));
    monotonicNow = 3 * minute;

    expect(clock.now()).toEqual(new Date(Date.UTC(2026, 6, 8, 9, 3)));
  });

  test('a newer server header rebases monotonic elapsed time', () => {
    let monotonicNow = 0;
    const clock = new ServerClock({ monotonicNow: () => monotonicNow });

    clock.updateFromHeader('2026-07-08T09:00:00Z');
    monotonicNow = 5 * minute;
    expect(clock.now()).toEqual(new Date(Date.UTC(2026, 6, 8, 9, 5)));

    clock.updateFromHeader('2026-07-08T09:04:00Z');
    monotonicNow = 6 * minute;
    expect(clock.now()).toEqual(new Date(Date.UTC(2026, 6, 8, 9, 5)));
  });

  test('records the latest successful request round-trip latency', () => {
    const clock = new ServerClock();
    let notifications = 0;
    clock.addListener(() => (notifications += 1));

    expect(clock.requestRoundTrip).toBeNull();
    expect(clock.updateRequestRoundTrip(87)).toBe(true);

    expect(clock.requestRoundTrip).toBe(87);
    expect(notifications).toBe(1);
    expect(clock.updateRequestRoundTrip(-1)).toBe(false);
    expect(clock.requestRoundTrip).toBe(87);
    expect(notifications).toBe(1);
  });
});
