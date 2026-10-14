/// Pure helpers for configurable screen-share output quality.
///
/// Capture constraints are only hints in upstream flutter_webrtc. The vendored
/// Windows and macOS capture paths therefore adapt desktop frames before they
/// enter the encoder, while Android retains its MediaProjection session and
/// applies publisher caps. A desktop live switch rebuilds only the capture
/// track for the already-selected source, so it does not reopen the picker.
library;

/// Selectable target heights, in pixels.
const List<int> screenShareHeightOptions = <int>[480, 720, 1080];

/// The default when nothing is stored.
const int defaultScreenShareMaxHeight = 720;

/// Selectable publisher frame-rate caps.
const List<int> screenShareFrameRateOptions = <int>[15, 30, 60];

/// The balanced default on every supported platform.
const int defaultScreenShareFrameRate = 30;

/// Maximum capture/output rate supported by the Android screen capturer.
const int androidScreenShareFrameRateCap = 30;

/// Maximum capture/output rate requested on Windows and macOS.
const int desktopScreenShareFrameRateCap = 60;

/// Coerce an arbitrary stored/in-flight value to one of the supported options,
/// falling back to [defaultScreenShareMaxHeight] for anything unrecognised.
int normalizedScreenShareMaxHeight(int? height) {
  if (height == null) return defaultScreenShareMaxHeight;
  return screenShareHeightOptions.contains(height)
      ? height
      : defaultScreenShareMaxHeight;
}

/// Normalize a stored/selected frame rate and respect a platform's ceiling.
int normalizedScreenShareFrameRate(
  int? frameRate, {
  int maxFrameRate = desktopScreenShareFrameRateCap,
}) {
  final fallback = defaultScreenShareFrameRate > maxFrameRate
      ? maxFrameRate
      : defaultScreenShareFrameRate;
  final selected = screenShareFrameRateOptions.contains(frameRate)
      ? frameRate!
      : fallback;
  return selected > maxFrameRate ? maxFrameRate : selected;
}

/// A plain 16:9 target resolution with an even width.
class ScreenShareResolution {
  const ScreenShareResolution(this.width, this.height);

  final int width;
  final int height;

  @override
  bool operator ==(Object other) =>
      other is ScreenShareResolution &&
      other.width == width &&
      other.height == height;

  @override
  int get hashCode => Object.hash(width, height);

  @override
  String toString() => 'ScreenShareResolution($width x $height)';
}

/// One screen-share output profile.
///
/// [maxBitrate] is a ceiling rather than a reservation. Static screen content
/// normally uses much less bandwidth, while motion can use the full budget.
class ScreenShareQuality {
  const ScreenShareQuality({
    required this.maxHeight,
    required this.maxFrameRate,
    required this.maxBitrate,
  });

  final int maxHeight;
  final int maxFrameRate;
  final int maxBitrate;

  ScreenShareResolution get resolution =>
      screenShareResolutionForHeight(maxHeight);

  @override
  bool operator ==(Object other) =>
      other is ScreenShareQuality &&
      other.maxHeight == maxHeight &&
      other.maxFrameRate == maxFrameRate &&
      other.maxBitrate == maxBitrate;

  @override
  int get hashCode => Object.hash(maxHeight, maxFrameRate, maxBitrate);
}

/// Result of checking a quality update against live outbound statistics.
///
/// WebRTC can intentionally stop producing outbound frames while a local
/// screen-share track has no subscribers. That is not an apply failure: the
/// sender parameters are ready and verification must resume when a viewer
/// subscribes.
enum ScreenShareQualityCheckResult { verified, awaitingOutboundFrames, retry }

ScreenShareQualityCheckResult screenShareQualityCheckResult({
  required bool parametersReady,
  required bool hasOutboundFrames,
  required bool resolutionVerified,
  required bool frameRateCapVerified,
}) {
  if (!hasOutboundFrames) {
    return parametersReady
        ? ScreenShareQualityCheckResult.awaitingOutboundFrames
        : ScreenShareQualityCheckResult.retry;
  }
  return parametersReady && resolutionVerified && frameRateCapVerified
      ? ScreenShareQualityCheckResult.verified
      : ScreenShareQualityCheckResult.retry;
}

/// Resolves which sender scale most likely produced an outbound sample.
///
/// Native Windows stats can omit both RID and SSRC. In that case, prefer the
/// smallest reconstructed source that can still satisfy the selected target.
/// This correctly identifies a 540p low simulcast layer from a 1080p source
/// while avoiding the inverse mistake of treating a 1080p high layer as a 4K
/// source. A known RID/SSRC always wins over this fallback.
double screenShareSourceScaleForSample({
  required int encodedHeight,
  required int targetHeight,
  required Iterable<double> encodingScales,
  double? matchedScale,
  int? knownSourceHeight,
}) {
  if (matchedScale != null && matchedScale >= 1) return matchedScale;
  final scales =
      encodingScales.map((scale) => scale < 1 ? 1.0 : scale).toSet().toList()
        ..sort();
  if (scales.isEmpty) return 1.0;
  if (knownSourceHeight != null && knownSourceHeight > 0) {
    return scales.reduce((best, candidate) {
      final bestDelta = (knownSourceHeight / best - encodedHeight).abs();
      final candidateDelta = (knownSourceHeight / candidate - encodedHeight)
          .abs();
      return candidateDelta < bestDelta ? candidate : best;
    });
  }
  for (final scale in scales) {
    if ((encodedHeight * scale).round() >= targetHeight) return scale;
  }
  return scales.last;
}

/// The actual output size for a selected height while preserving the source
/// aspect ratio and never scaling a smaller source up.
ScreenShareResolution screenShareCappedOutputResolution({
  required int? sourceWidth,
  required int? sourceHeight,
  required int targetHeight,
}) {
  final target = normalizedScreenShareMaxHeight(targetHeight);
  if (sourceWidth == null ||
      sourceWidth <= 0 ||
      sourceHeight == null ||
      sourceHeight <= 0) {
    return screenShareResolutionForHeight(target);
  }
  final outputHeight = sourceHeight < target ? sourceHeight : target;
  var outputWidth = (sourceWidth * outputHeight / sourceHeight).round();
  if (outputWidth < 2) outputWidth = 2;
  if (outputWidth.isOdd) outputWidth += 1;
  return ScreenShareResolution(outputWidth, outputHeight);
}

const Map<int, int> _screenShareBitrateAt30Fps = <int, int>{
  480: 2000 * 1000,
  720: 3000 * 1000,
  1080: 4000 * 1000,
};

/// Resolve the selected output profile and apply the platform frame-rate cap.
ScreenShareQuality screenShareQualityForHeight(
  int height, {
  int frameRate = defaultScreenShareFrameRate,
  int maxFrameRate = desktopScreenShareFrameRateCap,
}) {
  final normalized = normalizedScreenShareMaxHeight(height);
  final normalizedFrameRate = normalizedScreenShareFrameRate(
    frameRate,
    maxFrameRate: maxFrameRate,
  );
  return ScreenShareQuality(
    maxHeight: normalized,
    maxFrameRate: normalizedFrameRate,
    // Scale the ceiling with frame rate so each resolution keeps a stable
    // bits-per-frame budget across the 15/30/60 FPS choices.
    maxBitrate:
        (_screenShareBitrateAt30Fps[normalized]! * normalizedFrameRate / 30)
            .round(),
  );
}

/// The 16:9 target resolution for a given target [height].
ScreenShareResolution screenShareResolutionForHeight(int height) {
  final normalized = normalizedScreenShareMaxHeight(height);
  var width = (normalized * 16 / 9).round();
  if (width.isOdd) width += 1;
  return ScreenShareResolution(width, normalized);
}

/// The publisher scaling factor needed to cap a source at [targetHeight].
///
/// WebRTC only scales down, so this always returns at least 1.0.
double screenShareScaleDownBy({
  required int sourceHeight,
  required int targetHeight,
}) {
  final target = normalizedScreenShareMaxHeight(targetHeight);
  if (sourceHeight <= 0 || sourceHeight <= target) return 1.0;
  return sourceHeight / target;
}

String screenShareResolutionLabel(int height) =>
    '${normalizedScreenShareMaxHeight(height)}p';

String screenShareFrameRateLabel(int frameRate) =>
    '${normalizedScreenShareFrameRate(frameRate)} FPS';
