function _clamp(value: number, lower: number, upper: number): number {
  return Math.min(Math.max(value, lower), upper);
}

export function normalizedAudioVolume(volume: number): number {
  return _clamp(volume, 0.0, 1.0);
}

export function audioVolumePercentText(volume: number): string {
  return `${Math.round(normalizedAudioVolume(volume) * 100)}%`;
}

export const defaultParticipantVoiceVolume = 1.0;
export const maxParticipantVoiceVolume = 2.0;

export function normalizedParticipantVoiceVolume(volume: number): number {
  return _clamp(volume, 0.0, maxParticipantVoiceVolume);
}

export function participantVoiceVolumePercentText(volume: number): string {
  return `${Math.round(normalizedParticipantVoiceVolume(volume) * 100)}%`;
}

export function audioLevelSegmentCount(
  availableWidth: number,
  opts: { segmentWidth?: number; minSegments?: number; maxSegments?: number } = {},
): number {
  const { segmentWidth = 12, minSegments = 24, maxSegments = 56 } = opts;
  return _clamp(Math.floor(availableWidth / segmentWidth), minSegments, maxSegments);
}

export function activeAudioLevelSegmentCount(opts: { level: number; active: boolean; segmentCount: number }): number {
  const { level, active, segmentCount } = opts;
  if (!active) return 0;
  return _clamp(Math.ceil(normalizedAudioVolume(level) * segmentCount), 0, segmentCount);
}

export function audioLevelFromVisualizerBands(bands: Iterable<unknown>): number {
  const noiseFloor = 0.04;
  const displayGain = 1.45;
  let peak = 0.0;
  let squareSum = 0.0;
  let count = 0;

  for (const value of bands) {
    if (typeof value !== 'number') continue;
    const raw = value;
    if (!Number.isFinite(raw)) continue;
    const clamped = _clamp(raw, 0.0, 1.0);
    const sample = clamped <= noiseFloor ? 0.0 : (clamped - noiseFloor) / (1 - noiseFloor);
    if (sample > peak) peak = sample;
    squareSum += sample * sample;
    count++;
  }

  if (count === 0 || peak <= 0) return 0;
  const rms = Math.sqrt(squareSum / count);
  const energy = rms * 0.82 + peak * 0.18;
  return _clamp(energy * displayGain, 0.0, 1.0);
}
