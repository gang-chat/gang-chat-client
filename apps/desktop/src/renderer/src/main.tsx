import './global.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { setAudioRecorderFactory } from '@gang-chat/core/app/voice_recorder_controller';
import { setPlatform } from '@gang-chat/core/platform/platform';

import { desktopBridge } from './platform/desktop_bridge';
import { createDesktopPlatform } from './platform/desktop_platform';
import { MediaRecorderAudioRecorder } from './platform/media_recorder_audio_recorder';
import { DesktopRoot } from './desktop_root';

// The platform bridge must be installed before any shared module touches
// storage, HTTP or the window.
const bridge = desktopBridge();
setPlatform(createDesktopPlatform(bridge));
setAudioRecorderFactory(() => new MediaRecorderAudioRecorder());

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <DesktopRoot />
  </StrictMode>,
);
