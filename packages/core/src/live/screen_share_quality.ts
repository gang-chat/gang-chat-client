/**
 * Pure helpers for configurable screen-share output quality.
 *
 * Port of `lib/src/live/screen_share_quality.dart`. Capture constraints are
 * only hints; on desktop (Electron/Chromium) the `getDisplayMedia` constraints
 * bound the captured frames to the selected profile before they reach the
 * encoder, while Android keeps its MediaProjection session and applies
 * publisher caps. A desktop live switch re-applies constraints to the
 * already-selected capture track, so it does not reopen the picker.
 */

/** Selectable target heights, in pixels. */
export const screenShareHeightOptions: readonly number[] = [480, 720, 1080];

/** The default when nothing is stored. */
export const defaultScreenShareMaxHeight = 720;

/** Selectable publisher frame-rate caps. */
export const screenShareFrameRateOptions: readonly number[] = [15, 30, 60];

/** The balanced default on every supported platform. */
export const defaultScreenShareFrameRate = 30;

/** Maximum capture/output rate supported by the Android screen capturer. */
export const androidScreenShareFrameRateCap = 30;

/** Maximum capture/output rate requested on Windows and macOS. */
export const desktopScreenShareFrameRateCap = 60;

/**
 * Coerce an arbitrary stored/in-flight value to one of the supported options,
 * falling back to [defaultScreenShareMaxHeight] for anything unrecognised.
 */
export function normalizedScreenShareMaxHeight(height: number | null | undefined): number {
  if (height == null) return defaultScreenShareMaxHeight;
  return screenShareHeightOptions.includes(height) ? height : defaultScreenShareMaxHeight;
}

/** Normalize a stored/selected frame rate and respect a platform's ceiling. */
export function normalizedScreenShareFrameRate(
  frameRate: number | null | undefined,
  opts: { maxFrameRate?: number } = {},
): number {
  const { maxFrameRate = desktopScreenShareFrameRateCap } = opts;
  const fallback = defaultScreenShareFrameRate > maxFrameRate ? maxFrameRate : defaultScreenShareFrameRate;
  const selected = frameRate != null && screenShareFrameRateOptions.includes(frameRate) ? frameRate : fallback;
  return selected > maxFrameRate ? maxFrameRate : selected;
}

/** A plain 16:9 target resolution with an even width. */
export class ScreenShareResolution {
  constructor(
    readonly width: number,
    readonly height: number,
  ) {}

  equals(other: unknown): boolean {
    return other instanceof ScreenShareResolution && other.width === this.width && other.height === this.height;
  }

  toString(): string {
    return `ScreenShareResolution(${this.width} x ${this.height})`;
  }
}

/**
 * One screen-share output profile.
 *
 * [maxBitrate] is a ceiling rather than a reservation. Static screen content
 * normally uses much less bandwidth, while motion can use the full budget.
 */
export class ScreenShareQuality {
  constructor(opts: { maxHeight: number; maxFrameRate: number; maxBitrate: number }) {
    this.maxHeight = opts.maxHeight;
    this.maxFrameRate = opts.maxFrameRate;
    this.maxBitrate = opts.maxBitrate;
  }

  readonly maxHeight: number;
  readonly maxFrameRate: number;
  readonly maxBitrate: number;

  get resolution(): ScreenShareResolution {
    return screenShareResolutionForHeight(this.maxHeight);
  }

  equals(other: unknown): boolean {
    return (
      other instanceof ScreenShareQuality &&
      other.maxHeight === this.maxHeight &&
      other.maxFrameRate === this.maxFrameRate &&
      other.maxBitrate === this.maxBitrate
    );
  }
}

/**
 * Result of checking a quality update against live outbound statistics.
 *
 * WebRTC can intentionally stop producing outbound frames while a local
 * screen-share track has no subscribers. That is not an apply failure: the
 * sender parameters are ready and verification must resume when a viewer
 * subscribes.
 */
export enum ScreenShareQualityCheckResult {
  verified = 'verified',
  awaitingOutboundFrames = 'awaitingOutboundFrames',
  retry = 'retry',
}

export function screenShareQualityCheckResult(opts: {
  parametersReady: boolean;
  hasOutboundFrames: boolean;
  resolutionVerified: boolean;
  frameRateCapVerified: boolean;
}): ScreenShareQualityCheckResult {
  const { parametersReady, hasOutboundFrames, resolutionVerified, frameRateCapVerified } = opts;
  if (!hasOutboundFrames) {
    return parametersReady
      ? ScreenShareQualityCheckResult.awaitingOutboundFrames
      : ScreenShareQualityCheckResult.retry;
  }
  return parametersReady && resolutionVerified && frameRateCapVerified
    ? ScreenShareQualityCheckResult.verified
    : ScreenShareQualityCheckResult.retry;
}

/**
 * Resolves which sender scale most likely produced an outbound sample.
 *
 * Stats can omit the RID. In that case, prefer the smallest reconstructed
 * source that can still satisfy the selected target. This correctly
 * identifies a 540p low simulcast layer from a 1080p source while avoiding the
 * inverse mistake of treating a 1080p high layer as a 4K source. A known RID
 * always wins over this fallback.
 */
export function screenShareSourceScaleForSample(opts: {
  encodedHeight: number;
  targetHeight: number;
  encodingScales: Iterable<number>;
  matchedScale?: number | null;
  knownSourceHeight?: number | null;
}): number {
  const { encodedHeight, targetHeight, encodingScales, matchedScale, knownSourceHeight } = opts;
  if (matchedScale != null && matchedScale >= 1) return matchedScale;
  const scales = [...new Set([...encodingScales].map((scale) => (scale < 1 ? 1.0 : scale)))].sort((a, b) => a - b);
  if (scales.length === 0) return 1.0;
  if (knownSourceHeight != null && knownSourceHeight > 0) {
    return scales.reduce((best, candidate) => {
      const bestDelta = Math.abs(knownSourceHeight / best - encodedHeight);
      const candidateDelta = Math.abs(knownSourceHeight / candidate - encodedHeight);
      return candidateDelta < bestDelta ? candidate : best;
    });
  }
  for (const scale of scales) {
    if (Math.round(encodedHeight * scale) >= targetHeight) return scale;
  }
  return scales[scales.length - 1]!;
}

/**
 * The actual output size for a selected height while preserving the source
 * aspect ratio and never scaling a smaller source up.
 */
export function screenShareCappedOutputResolution(opts: {
  sourceWidth: number | null;
  sourceHeight: number | null;
  targetHeight: number;
}): ScreenShareResolution {
  const { sourceWidth, sourceHeight, targetHeight } = opts;
  const target = normalizedScreenShareMaxHeight(targetHeight);
  if (sourceWidth == null || sourceWidth <= 0 || sourceHeight == null || sourceHeight <= 0) {
    return screenShareResolutionForHeight(target);
  }
  const outputHeight = sourceHeight < target ? sourceHeight : target;
  let outputWidth = Math.round((sourceWidth * outputHeight) / sourceHeight);
  if (outputWidth < 2) outputWidth = 2;
  if (outputWidth % 2 !== 0) outputWidth += 1;
  return new ScreenShareResolution(outputWidth, outputHeight);
}

const _screenShareBitrateAt30Fps: ReadonlyMap<number, number> = new Map([
  [480, 2000 * 1000],
  [720, 3000 * 1000],
  [1080, 4000 * 1000],
]);

/** Resolve the selected output profile and apply the platform frame-rate cap. */
export function screenShareQualityForHeight(
  height: number,
  opts: { frameRate?: number; maxFrameRate?: number } = {},
): ScreenShareQuality {
  const { frameRate = defaultScreenShareFrameRate, maxFrameRate = desktopScreenShareFrameRateCap } = opts;
  const normalized = normalizedScreenShareMaxHeight(height);
  const normalizedFrameRate = normalizedScreenShareFrameRate(frameRate, { maxFrameRate });
  return new ScreenShareQuality({
    maxHeight: normalized,
    maxFrameRate: normalizedFrameRate,
    // Scale the ceiling with frame rate so each resolution keeps a stable
    // bits-per-frame budget across the 15/30/60 FPS choices.
    maxBitrate: Math.round((_screenShareBitrateAt30Fps.get(normalized)! * normalizedFrameRate) / 30),
  });
}

/** The 16:9 target resolution for a given target [height]. */
export function screenShareResolutionForHeight(height: number): ScreenShareResolution {
  const normalized = normalizedScreenShareMaxHeight(height);
  let width = Math.round((normalized * 16) / 9);
  if (width % 2 !== 0) width += 1;
  return new ScreenShareResolution(width, normalized);
}

/**
 * The publisher scaling factor needed to cap a source at [targetHeight].
 *
 * WebRTC only scales down, so this always returns at least 1.0.
 */
export function screenShareScaleDownBy(opts: { sourceHeight: number; targetHeight: number }): number {
  const { sourceHeight, targetHeight } = opts;
  const target = normalizedScreenShareMaxHeight(targetHeight);
  if (sourceHeight <= 0 || sourceHeight <= target) return 1.0;
  return sourceHeight / target;
}

export function screenShareResolutionLabel(height: number): string {
  return `${normalizedScreenShareMaxHeight(height)}p`;
}

export function screenShareFrameRateLabel(frameRate: number): string {
  return `${normalizedScreenShareFrameRate(frameRate)} FPS`;
}
