/// Pure helpers for configurable screen-share output quality.
///
/// Capture constraints are only hints: the vendored desktop, macOS and Android
/// capturers can all return frames at the source's native size. The selected
/// profile therefore controls the publisher encoding as the authoritative
/// output cap. Capture still runs at the platform's maximum supported frame
/// rate so a live switch from a lower profile to a higher profile never needs
/// to restart screen capture or ask for permission again.
library;

/// Selectable target heights, in pixels.
const List<int> screenShareHeightOptions = <int>[480, 720, 1080];

/// The default when nothing is stored.
const int defaultScreenShareMaxHeight = 1080;

/// Selectable publisher frame-rate caps.
const List<int> screenShareFrameRateOptions = <int>[15, 30, 60];

/// The default on a platform that can publish the full desktop profile.
const int defaultScreenShareFrameRate = 60;

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
