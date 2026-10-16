import { describe, expect, test } from 'vitest';

import {
  activeAudioLevelSegmentCount,
  audioLevelFromVisualizerBands,
  audioLevelSegmentCount,
  audioVolumePercentText,
  normalizedAudioVolume,
  normalizedParticipantVoiceVolume,
  participantVoiceVolumePercentText,
} from '../../src/app/audio_levels';

describe('audio_levels', () => {
  test('normalizedAudioVolume clamps volume to audio range', () => {
    expect(normalizedAudioVolume(-0.2)).toBe(0);
    expect(normalizedAudioVolume(0.45)).toBe(0.45);
    expect(normalizedAudioVolume(1.7)).toBe(1);
  });

  test('audioVolumePercentText formats clamped volume', () => {
    expect(audioVolumePercentText(-0.2)).toBe('0%');
    expect(audioVolumePercentText(0.455)).toBe('46%');
    expect(audioVolumePercentText(1.7)).toBe('100%');
  });

  test('participant voice volume supports 200 percent boosts', () => {
    expect(normalizedParticipantVoiceVolume(-0.2)).toBe(0);
    expect(normalizedParticipantVoiceVolume(1.25)).toBe(1.25);
    expect(normalizedParticipantVoiceVolume(3)).toBe(2);
    expect(participantVoiceVolumePercentText(1.25)).toBe('125%');
    expect(participantVoiceVolumePercentText(3)).toBe('200%');
  });

  test('level meter helpers calculate segment counts and active bars', () => {
    expect(audioLevelSegmentCount(120)).toBe(24);
    expect(audioLevelSegmentCount(420)).toBe(35);
    expect(audioLevelSegmentCount(900)).toBe(56);

    expect(activeAudioLevelSegmentCount({ level: 0.31, active: true, segmentCount: 10 })).toBe(4);
    expect(activeAudioLevelSegmentCount({ level: 0.31, active: false, segmentCount: 10 })).toBe(0);
    expect(activeAudioLevelSegmentCount({ level: 2, active: true, segmentCount: 10 })).toBe(10);
  });

  test('audioLevelFromVisualizerBands uses overall audio energy', () => {
    expect(audioLevelFromVisualizerBands([0.0, 0.03, null, Number.NaN])).toBe(0);

    const singleSpike = audioLevelFromVisualizerBands([1.0, ...new Array<number>(13).fill(0.0)]);
    const broadVoice = audioLevelFromVisualizerBands(new Array<number>(14).fill(0.5));

    expect(singleSpike).toBeGreaterThan(0);
    expect(singleSpike).toBeLessThan(0.7);
    expect(broadVoice).toBeGreaterThan(singleSpike);
    expect(audioLevelFromVisualizerBands(new Array<number>(14).fill(1.0))).toBe(1);
  });
});
