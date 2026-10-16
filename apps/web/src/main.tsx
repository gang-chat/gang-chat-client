import '@gang-chat/ui/ui/global.css';
import '@gang-chat/ui/ui/ui.css';

import { createRoot } from 'react-dom/client';
import { gangChatClientVersion } from '@gang-chat/core/app/settings_about';
import { setAudioRecorderFactory } from '@gang-chat/core/app/voice_recorder_controller';
import { setPlatform } from '@gang-chat/core/platform/platform';
import { MediaRecorderAudioRecorder } from '@gang-chat/ui/platform/media_recorder_audio_recorder';
import { installBrowserPasteListener } from '@gang-chat/ui/shell/browser_paste_listener';

import { createWebPlatform } from './platform/web_platform';
import { WebRoot } from './web_root';

// The platform bridge must be installed before any shared module touches
// storage or HTTP.
setPlatform(createWebPlatform({ version: gangChatClientVersion }));
setAudioRecorderFactory(() => new MediaRecorderAudioRecorder());
installBrowserPasteListener();

// No <StrictMode>: its development-only unmount/remount of every effect
// would dispose each ViewState (Flutter State semantics: initState and
// dispose run exactly once), leaving the remounted view inert.
createRoot(document.getElementById('root')!).render(<WebRoot />);
