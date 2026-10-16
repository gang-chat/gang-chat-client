import { describe, expect, test } from 'vitest';

import {
  NetworkLatencyQuality,
  networkLatencyQuality,
  networkLatencySignalBars,
  networkLatencyTooltip,
} from '../../src/app/network_latency';

describe('network_latency', () => {
  test('classifies latency into unavailable, good, fair, and poor bands', () => {
    expect(networkLatencyQuality(null)).toBe(NetworkLatencyQuality.unavailable);
    expect(networkLatencyQuality(150)).toBe(NetworkLatencyQuality.good);
    expect(networkLatencyQuality(151)).toBe(NetworkLatencyQuality.fair);
    expect(networkLatencyQuality(350)).toBe(NetworkLatencyQuality.fair);
    expect(networkLatencyQuality(351)).toBe(NetworkLatencyQuality.poor);
  });

  test('maps latency bands to signal bar counts and tooltip text', () => {
    expect(networkLatencySignalBars(null)).toBe(0);
    expect(networkLatencySignalBars(80)).toBe(3);
    expect(networkLatencySignalBars(220)).toBe(2);
    expect(networkLatencySignalBars(620)).toBe(1);

    expect(networkLatencyTooltip(null)).toBe('延迟检测中');
    expect(networkLatencyTooltip(228)).toBe('228 ms');
  });
});
