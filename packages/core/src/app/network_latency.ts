export enum NetworkLatencyQuality {
  unavailable = 'unavailable',
  poor = 'poor',
  fair = 'fair',
  good = 'good',
}

/** Milliseconds. */
export const lowNetworkLatencyThreshold = 150;
/** Milliseconds. */
export const fairNetworkLatencyThreshold = 350;

/** [latency] is in milliseconds. */
export function networkLatencyQuality(latency: number | null): NetworkLatencyQuality {
  if (latency == null) return NetworkLatencyQuality.unavailable;
  if (latency <= lowNetworkLatencyThreshold) return NetworkLatencyQuality.good;
  if (latency <= fairNetworkLatencyThreshold) return NetworkLatencyQuality.fair;
  return NetworkLatencyQuality.poor;
}

export function networkLatencySignalBars(latency: number | null): number {
  switch (networkLatencyQuality(latency)) {
    case NetworkLatencyQuality.good:
      return 3;
    case NetworkLatencyQuality.fair:
      return 2;
    case NetworkLatencyQuality.poor:
      return 1;
    case NetworkLatencyQuality.unavailable:
      return 0;
  }
}

export function networkLatencyTooltip(latency: number | null): string {
  if (latency == null) return '延迟检测中';
  return `${Math.trunc(latency)} ms`;
}
