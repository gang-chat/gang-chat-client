import 'package:flutter/widgets.dart';
import 'package:livekit_client/livekit_client.dart' as lk;

import 'live_session.dart';

enum LiveVideoTrackFit { cover, contain }

typedef LiveVideoTrackRendererBuilder =
    Widget Function(
      LiveVideoTrack track,
      LiveVideoTrackFit fit,
      bool shouldMirror,
    );

@visibleForTesting
LiveVideoTrackRendererBuilder? liveVideoTrackRendererForTest;

@visibleForTesting
void resetLiveVideoTrackRendererForTest() {
  liveVideoTrackRendererForTest = null;
}

class LiveVideoTrackView extends StatelessWidget {
  const LiveVideoTrackView({
    super.key,
    required this.track,
    this.fit = LiveVideoTrackFit.contain,
    this.mirrored = false,
  });

  final LiveVideoTrack track;
  final LiveVideoTrackFit fit;
  final bool mirrored;

  @override
  Widget build(BuildContext context) {
    // Mirror state is owned explicitly by the app. Screen shares never mirror,
    // and camera rendering does not depend on the SDK's platform-specific
    // automatic policy.
    final shouldMirror = !track.isScreenShare && mirrored;
    final testRenderer = liveVideoTrackRendererForTest;
    if (testRenderer != null) {
      return testRenderer(track, fit, shouldMirror);
    }

    return lk.VideoTrackRenderer(
      track.track,
      fit: switch (fit) {
        LiveVideoTrackFit.cover => lk.VideoViewFit.cover,
        LiveVideoTrackFit.contain => lk.VideoViewFit.contain,
      },
      mirrorMode: shouldMirror
          ? lk.VideoViewMirrorMode.mirror
          : lk.VideoViewMirrorMode.off,
    );
  }
}
