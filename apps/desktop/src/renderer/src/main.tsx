import '@gang-chat/ui/ui/global.css';
import '@gang-chat/ui/ui/ui.css';

import { createRoot } from 'react-dom/client';
import { setAudioRecorderFactory } from '@gang-chat/core/app/voice_recorder_controller';
import { setPlatform } from '@gang-chat/core/platform/platform';
import { MediaRecorderAudioRecorder } from '@gang-chat/ui/platform/media_recorder_audio_recorder';

import { desktopBridge } from './platform/desktop_bridge';
import { createDesktopPlatform } from './platform/desktop_platform';
import { DesktopRoot } from './desktop_root';

// The platform bridge must be installed before any shared module touches
// storage, HTTP or the window.
const bridge = desktopBridge();
setPlatform(createDesktopPlatform(bridge));
setAudioRecorderFactory(() => new MediaRecorderAudioRecorder());

// No <StrictMode>: its development-only unmount/remount of every effect
// would dispose each ViewState (Flutter State semantics: initState and
// dispose run exactly once), leaving the remounted view inert.
createRoot(document.getElementById('root')!).render(<DesktopRoot />);
