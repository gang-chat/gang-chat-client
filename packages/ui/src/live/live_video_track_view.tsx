/**
 * Port of `lib/src/live/live_video_track_view.dart`.
 *
 * PORT: livekit_client's `VideoTrackRenderer` widget becomes a `<video>`
 * element the livekit-client track attaches to (`track.attach(element)` on
 * mount, `track.detach(element)` on unmount or track change). `fit` maps to
 * CSS `object-fit` and mirroring to a horizontal flip, both owned explicitly
 * by the caller exactly as in Dart.
 */
import { useEffect, useRef, type ReactNode } from 'react';

import type { LiveVideoTrack } from '@gang-chat/core/live/live_session';
import type { VideoTrack } from 'livekit-client';

export enum LiveVideoTrackFit {
  cover = 'cover',
  contain = 'contain',
}

export type LiveVideoTrackRendererBuilder = (
  track: LiveVideoTrack,
  fit: LiveVideoTrackFit,
  shouldMirror: boolean,
) => ReactNode;

let _liveVideoTrackRendererForTest: LiveVideoTrackRendererBuilder | null = null;

/**
 * Dart's `@visibleForTesting liveVideoTrackRendererForTest` variable. ES
 * module bindings are read-only for importers, so tests assign it through
 * this setter.
 */
export function setLiveVideoTrackRendererForTest(builder: LiveVideoTrackRendererBuilder | null): void {
  _liveVideoTrackRendererForTest = builder;
}

export function liveVideoTrackRendererForTest(): LiveVideoTrackRendererBuilder | null {
  return _liveVideoTrackRendererForTest;
}

export function resetLiveVideoTrackRendererForTest(): void {
  _liveVideoTrackRendererForTest = null;
}

export interface LiveVideoTrackViewProps {
  track: LiveVideoTrack;
  fit?: LiveVideoTrackFit;
  mirrored?: boolean;
}

export function LiveVideoTrackView(props: LiveVideoTrackViewProps): ReactNode {
  const { track, fit = LiveVideoTrackFit.contain, mirrored = false } = props;
  // Mirror state is owned explicitly by the app. Screen shares never mirror,
  // and camera rendering does not depend on the SDK's automatic policy.
  const shouldMirror = !track.isScreenShare && mirrored;
  const testRenderer = _liveVideoTrackRendererForTest;
  if (testRenderer != null) {
    return testRenderer(track, fit, shouldMirror);
  }

  return <_VideoTrackElement videoTrack={track.track} fit={fit} mirror={shouldMirror} />;
}

function _VideoTrackElement(props: { videoTrack: VideoTrack; fit: LiveVideoTrackFit; mirror: boolean }): ReactNode {
  const { videoTrack, fit, mirror } = props;
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const element = videoRef.current;
    if (element == null) return;
    videoTrack.attach(element);
    return () => {
      videoTrack.detach(element);
    };
  }, [videoTrack]);

  return (
    <video
      ref={videoRef}
      autoPlay
      playsInline
      muted
      disablePictureInPicture
      style={{
        display: 'block',
        width: '100%',
        height: '100%',
        objectFit: fit === LiveVideoTrackFit.cover ? 'cover' : 'contain',
        transform: mirror ? 'scaleX(-1)' : undefined,
        pointerEvents: 'none',
      }}
    />
  );
}
