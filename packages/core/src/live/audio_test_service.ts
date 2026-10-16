/**
 * Port of `lib/src/live/audio_test_service.dart`: the Settings microphone
 * level meter and speaker test tone.
 */
import { createLocalAudioTrack, type LocalAudioTrack } from 'livekit-client';
import { PeriodicTimer, Timer, unawaited } from '../lib/async';
import { asNumber, isJsonMap } from '../lib/json';
import { normalizedAudioVolume } from '../app/audio_levels';
import { createLiveTonePlayer, type LiveTonePlayer } from './live_audio_platform';
import { LiveHardware } from './live_hardware';

export class AudioTestService {
  /** PORT: [hardware] supplies the selected output for the speaker test (Flutter used the global ADM route). */
  constructor(opts: { hardware?: LiveHardware; tonePlayerFactory?: () => LiveTonePlayer } = {}) {
    this._hardware = opts.hardware ?? LiveHardware.instance;
    this._tonePlayerFactory = opts.tonePlayerFactory ?? createLiveTonePlayer;
  }

  private readonly _hardware: LiveHardware;
  private readonly _tonePlayerFactory: () => LiveTonePlayer;

  async startInputTest(opts: {
    inputDeviceId: string | null;
    volume: number;
    onLevel: (level: number) => void;
  }): Promise<AudioTestHandle> {
    let track: LocalAudioTrack | null = null;
    let levelSampler: _StartedInputLevelSampler | null = null;
    try {
      track = await this._createTestAudioTrack(opts.inputDeviceId);
      levelSampler = await _startInputLevelSampler(track, opts.onLevel);
      return new AudioTestHandle({ track, inputLevelSampler: levelSampler });
    } catch (error) {
      await levelSampler?.dispose();
      await _disposeTestTrack(track);
      throw error;
    }
  }

  async startOutputTest(opts: { volume: number; onLevel: (level: number) => void }): Promise<AudioTestHandle> {
    let player: LiveTonePlayer | null = null;
    let levelTimer: PeriodicTimer | null = null;
    try {
      player = this._tonePlayerFactory();
      await player.play(_outputTestToneWav(), {
        mimeType: 'audio/wav',
        volume: normalizedAudioVolume(opts.volume),
        loop: true,
        sinkId: this._hardware.selectedAudioOutput?.deviceId ?? null,
      });
      opts.onLevel(1.0);
      levelTimer = Timer.periodic(80, () => opts.onLevel(1.0));
      return new AudioTestHandle({ outputPlayer: player, levelTimer });
    } catch (error) {
      levelTimer?.cancel();
      await _disposeOutputPlayer(player);
      throw error;
    }
  }

  private async _createTestAudioTrack(inputDeviceId: string | null): Promise<LocalAudioTrack> {
    return createLocalAudioTrack(inputDeviceId == null ? {} : { deviceId: inputDeviceId });
  }
}

export class AudioTestHandle {
  /** @internal Created by [AudioTestService] only (Dart `AudioTestHandle._`). */
  constructor(
    opts: {
      track?: LocalAudioTrack | null;
      inputLevelSampler?: _StartedInputLevelSampler | null;
      outputPlayer?: LiveTonePlayer | null;
      levelTimer?: PeriodicTimer | null;
    } = {},
  ) {
    this._track = opts.track ?? null;
    this._inputLevelSampler = opts.inputLevelSampler ?? null;
    this._outputPlayer = opts.outputPlayer ?? null;
    this._levelTimer = opts.levelTimer ?? null;
  }

  private _track: LocalAudioTrack | null;
  private _inputLevelSampler: _StartedInputLevelSampler | null;
  private _outputPlayer: LiveTonePlayer | null;
  private _levelTimer: PeriodicTimer | null;

  async setCaptureVolume(_volume: number): Promise<void> {
    // Input-test levels are scaled in app state. Do not write the local
    // capture track volume here: on desktop that can leak into system mic gain.
  }

  async setPlaybackVolume(volume: number): Promise<void> {
    const player = this._outputPlayer;
    if (player == null) return;
    await player.setVolume(normalizedAudioVolume(volume));
  }

  async dispose(): Promise<void> {
    const inputLevelSampler = this._inputLevelSampler;
    const player = this._outputPlayer;
    const levelTimer = this._levelTimer;
    const track = this._track;
    this._inputLevelSampler = null;
    this._outputPlayer = null;
    this._levelTimer = null;
    this._track = null;
    levelTimer?.cancel();
    await inputLevelSampler?.dispose();
    await _disposeOutputPlayer(player);
    await _disposeTestTrack(track);
  }
}

/**
 * Reads the microphone level from WebRTC sender statistics of a local
 * loopback PeerConnection pair. This keeps the meter on WebRTC globals only,
 * so it works unchanged under react-native-webrtc (no Web Audio needed).
 */
export class _StartedInputLevelSampler {
  constructor(opts: {
    senderPeerConnection: RTCPeerConnection;
    receiverPeerConnection: RTCPeerConnection;
    sender: RTCRtpSender;
    onLevel: (level: number) => void;
  }) {
    this.senderPeerConnection = opts.senderPeerConnection;
    this.receiverPeerConnection = opts.receiverPeerConnection;
    this.sender = opts.sender;
    this.onLevel = opts.onLevel;
  }

  readonly senderPeerConnection: RTCPeerConnection;
  readonly receiverPeerConnection: RTCPeerConnection;
  readonly sender: RTCRtpSender;
  readonly onLevel: (level: number) => void;
  timer: PeriodicTimer | null = null;

  private _previousEnergy: number | null = null;
  private _previousDuration: number | null = null;
  private _sampling = false;

  async sample(): Promise<void> {
    if (this._sampling) return;
    this._sampling = true;
    try {
      const reports = await this.sender.getStats();
      const level = this._audioLevelFromStats(reports);
      if (level != null) {
        this.onLevel(level);
      }
    } catch {
      // Keep the mic test alive even if a stats poll fails once.
    } finally {
      this._sampling = false;
    }
  }

  async dispose(): Promise<void> {
    this.timer?.cancel();
    try {
      this.senderPeerConnection.removeTrack(this.sender);
    } catch {
      // Already closed.
    }
    _disposePeerConnection(this.senderPeerConnection);
    _disposePeerConnection(this.receiverPeerConnection);
  }

  _audioLevelFromStats(reports: RTCStatsReport): number | null {
    let directLevel: number | null = null;
    let totalEnergy: number | null = null;
    let totalDuration: number | null = null;

    reports.forEach((value: unknown) => {
      if (!isJsonMap(value)) return;
      const rawLevel = asNumber(value['audioLevel']);
      if (rawLevel != null) {
        directLevel = Math.max(directLevel ?? 0, rawLevel);
      }
      const rawEnergy = asNumber(value['totalAudioEnergy']);
      const rawDuration = asNumber(value['totalSamplesDuration']);
      if (rawEnergy != null && rawDuration != null) {
        totalEnergy = rawEnergy;
        totalDuration = rawDuration;
      }
    });

    return this._levelFrom(directLevel, totalEnergy, totalDuration);
  }

  private _levelFrom(directLevel: number | null, totalEnergy: number | null, totalDuration: number | null): number | null {
    let energyLevel: number | null = null;
    const previousEnergy = this._previousEnergy;
    const previousDuration = this._previousDuration;
    if (totalEnergy != null && totalDuration != null) {
      this._previousEnergy = totalEnergy;
      this._previousDuration = totalDuration;

      if (previousEnergy != null && previousDuration != null) {
        const energyDelta = totalEnergy - previousEnergy;
        const durationDelta = totalDuration - previousDuration;
        if (!Number.isFinite(energyDelta) || !Number.isFinite(durationDelta) || energyDelta <= 0 || durationDelta <= 0) {
          energyLevel = 0;
        } else {
          energyLevel = Math.sqrt(energyDelta / durationDelta);
        }
      }
    }

    if (directLevel == null && energyLevel == null) return null;
    return _displayAudioLevel(Math.max(directLevel ?? 0, energyLevel ?? 0));
  }
}

async function _startInputLevelSampler(
  track: LocalAudioTrack,
  onLevel: (level: number) => void,
): Promise<_StartedInputLevelSampler> {
  let senderPeerConnection: RTCPeerConnection | null = null;
  let receiverPeerConnection: RTCPeerConnection | null = null;
  let sender: RTCRtpSender | null = null;
  let timer: PeriodicTimer | null = null;
  try {
    senderPeerConnection = new RTCPeerConnection(_inputLevelPeerConnectionConfiguration);
    receiverPeerConnection = new RTCPeerConnection(_inputLevelPeerConnectionConfiguration);

    const loopbackIce = new _InputLevelLoopbackIce(senderPeerConnection, receiverPeerConnection);

    const mediaStreamTrack = track.mediaStreamTrack;
    sender = senderPeerConnection.addTrack(mediaStreamTrack, track.mediaStream ?? new MediaStream([mediaStreamTrack]));
    await _negotiateInputLevelLoopback(senderPeerConnection, receiverPeerConnection, loopbackIce);

    const sampler = new _StartedInputLevelSampler({
      senderPeerConnection,
      receiverPeerConnection,
      sender,
      onLevel,
    });
    timer = Timer.periodic(120, () => unawaited(sampler.sample()));
    sampler.timer = timer;
    onLevel(0);
    unawaited(sampler.sample());
    return sampler;
  } catch (error) {
    timer?.cancel();
    if (sender != null && senderPeerConnection != null) {
      try {
        senderPeerConnection.removeTrack(sender);
      } catch {
        // Already closed.
      }
    }
    _disposePeerConnection(senderPeerConnection);
    _disposePeerConnection(receiverPeerConnection);
    throw error;
  }
}

const _inputLevelPeerConnectionConfiguration: RTCConfiguration = { iceServers: [] };

class _InputLevelLoopbackIce {
  constructor(
    readonly senderPeerConnection: RTCPeerConnection,
    readonly receiverPeerConnection: RTCPeerConnection,
  ) {
    senderPeerConnection.onicecandidate = (event) => {
      const candidate = event.candidate;
      if (candidate == null) return;
      if (this._receiverCanAcceptCandidates) {
        unawaited(_addIceCandidate(receiverPeerConnection, candidate));
      } else {
        this._pendingSenderCandidates.push(candidate);
      }
    };
    receiverPeerConnection.onicecandidate = (event) => {
      const candidate = event.candidate;
      if (candidate == null) return;
      if (this._senderCanAcceptCandidates) {
        unawaited(_addIceCandidate(senderPeerConnection, candidate));
      } else {
        this._pendingReceiverCandidates.push(candidate);
      }
    };
    // The loopback is only a stats source: never render the received copy.
    receiverPeerConnection.ontrack = (event) => {
      event.track.enabled = false;
    };
  }

  private readonly _pendingSenderCandidates: RTCIceCandidate[] = [];
  private readonly _pendingReceiverCandidates: RTCIceCandidate[] = [];
  private _receiverCanAcceptCandidates = false;
  private _senderCanAcceptCandidates = false;

  markReceiverCanAcceptCandidates(): void {
    this._receiverCanAcceptCandidates = true;
    unawaited(_flushIceCandidates(this.receiverPeerConnection, this._pendingSenderCandidates));
  }

  markSenderCanAcceptCandidates(): void {
    this._senderCanAcceptCandidates = true;
    unawaited(_flushIceCandidates(this.senderPeerConnection, this._pendingReceiverCandidates));
  }
}

async function _negotiateInputLevelLoopback(
  senderPeerConnection: RTCPeerConnection,
  receiverPeerConnection: RTCPeerConnection,
  loopbackIce: _InputLevelLoopbackIce,
): Promise<void> {
  const offer = await senderPeerConnection.createOffer();
  await senderPeerConnection.setLocalDescription(offer);
  await receiverPeerConnection.setRemoteDescription(offer);
  loopbackIce.markReceiverCanAcceptCandidates();

  const answer = await receiverPeerConnection.createAnswer();
  await receiverPeerConnection.setLocalDescription(answer);
  await senderPeerConnection.setRemoteDescription(answer);
  loopbackIce.markSenderCanAcceptCandidates();
}

async function _flushIceCandidates(peerConnection: RTCPeerConnection, candidates: RTCIceCandidate[]): Promise<void> {
  const pending = [...candidates];
  candidates.length = 0;
  for (const candidate of pending) {
    await _addIceCandidate(peerConnection, candidate);
  }
}

async function _addIceCandidate(peerConnection: RTCPeerConnection, candidate: RTCIceCandidate): Promise<void> {
  try {
    await peerConnection.addIceCandidate(candidate);
  } catch {
    // A late candidate after close is harmless.
  }
}

function _disposePeerConnection(peerConnection: RTCPeerConnection | null): void {
  if (peerConnection == null) return;
  peerConnection.onicecandidate = null;
  peerConnection.ontrack = null;
  try {
    peerConnection.close();
  } catch {
    // Already closed.
  }
}

export function _displayAudioLevel(rawLevel: number): number {
  const noiseFloor = 0.015;
  const displayGain = 2.2;
  if (!Number.isFinite(rawLevel) || rawLevel <= noiseFloor) return 0;
  const normalized = (rawLevel - noiseFloor) / (1 - noiseFloor);
  return Math.min(1, Math.max(0, normalized * displayGain));
}

async function _disposeTestTrack(track: LocalAudioTrack | null): Promise<void> {
  if (track == null) return;
  try {
    track.stop();
  } catch {
    // Already stopped.
  }
}

async function _disposeOutputPlayer(player: LiveTonePlayer | null): Promise<void> {
  if (player == null) return;
  try {
    await player.dispose();
  } catch {
    // Best-effort teardown.
  }
}

let _outputTestToneWavCache: Uint8Array | null = null;

function _outputTestToneWav(): Uint8Array {
  return (_outputTestToneWavCache ??= _buildOutputTestToneWav());
}

/** A 500 ms, 1 kHz, mono 16-bit 48 kHz sine tone (looped by the player). */
export function _buildOutputTestToneWav(): Uint8Array {
  const sampleRate = 48000;
  const durationMs = 500;
  const frequency = 1000.0;
  const amplitude = 0.22;
  const samples = Math.trunc((sampleRate * durationMs) / 1000);
  const dataByteCount = samples * 2;
  const bytes = new Uint8Array(44 + dataByteCount);
  const data = new DataView(bytes.buffer);

  const writeAscii = (offset: number, value: string): void => {
    for (let i = 0; i < value.length; i += 1) {
      data.setUint8(offset + i, value.charCodeAt(i));
    }
  };

  writeAscii(0, 'RIFF');
  data.setUint32(4, 36 + dataByteCount, true);
  writeAscii(8, 'WAVE');
  writeAscii(12, 'fmt ');
  data.setUint32(16, 16, true);
  data.setUint16(20, 1, true);
  data.setUint16(22, 1, true);
  data.setUint32(24, sampleRate, true);
  data.setUint32(28, sampleRate * 2, true);
  data.setUint16(32, 2, true);
  data.setUint16(34, 16, true);
  writeAscii(36, 'data');
  data.setUint32(40, dataByteCount, true);

  for (let i = 0; i < samples; i += 1) {
    const sample = Math.sin((2 * Math.PI * frequency * i) / sampleRate) * amplitude;
    // Dart `round()` rounds halves away from zero.
    const scaled = sample * 32767;
    data.setInt16(44 + i * 2, scaled < 0 ? -Math.round(-scaled) : Math.round(scaled), true);
  }

  return bytes;
}
