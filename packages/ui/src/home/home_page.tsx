import { useRef } from 'react';

import type { AvailableAppUpdate } from '@gang-chat/core/app/app_update';
import type { AudioDeviceStore } from '@gang-chat/core/app/audio_device_store';
import type { AuthenticatedAppContext } from '@gang-chat/core/app/authenticated_app_context';
import type { CloseBehaviorStore } from '@gang-chat/core/app/close_behavior';
import type { LanguagePreferenceStore } from '@gang-chat/core/app/language_preference';
import type { LivePresenceSpeechPlayer } from '@gang-chat/core/app/live_presence_announcement';
import type { LiveSessionController } from '@gang-chat/core/app/live_session_controller';
import type { MusicTrackPreviewPlatformFactory } from '@gang-chat/core/app/music_track_preview';
import type { RealtimeService } from '@gang-chat/core/app/realtime_controller';
import type { LivePresenceSoundPlayer } from '@gang-chat/core/live/live_presence_sound_service';
import type { AndroidSystemService } from '@gang-chat/core/shell/android_system_service';
import { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { LocalAudioDeviceStore } from '@gang-chat/core/shell/local_audio_device_store';
import { LocalCloseBehaviorStore } from '@gang-chat/core/shell/local_close_behavior_store';
import { LocalLanguagePreferenceStore } from '@gang-chat/core/shell/local_language_preference_store';
import type { MessageNotificationSoundPlayer } from '@gang-chat/core/shell/message_notification_sound_service';

import { HomeShell } from './home_shell';

export interface HomePageProps {
  app: AuthenticatedAppContext;
  audioDeviceStore?: AudioDeviceStore;
  liveSessionController?: LiveSessionController | null;
  livePresenceSoundPlayer?: LivePresenceSoundPlayer | null;
  livePresenceSpeechPlayer?: LivePresenceSpeechPlayer | null;
  messageNotificationSoundPlayer?: MessageNotificationSoundPlayer | null;
  realtime?: RealtimeService | null;
  androidSystemService?: AndroidSystemService;
  closeBehaviorStore?: CloseBehaviorStore;
  languageStore?: LanguagePreferenceStore;
  detectedAppUpdate?: AvailableAppUpdate | null;
  onDetectedAppUpdateShown?: (() => void) | null;
  windowController?: DesktopWindowController | null;
  musicTrackPreviewPlatformFactory?: MusicTrackPreviewPlatformFactory;
}

// The Dart defaults are `const` instances, i.e. one shared object; these lazy
// singletons keep the same identity across rebuilds (HomeShell compares them
// in didUpdateProps).
let _defaultAudioDeviceStore: LocalAudioDeviceStore | null = null;
let _defaultCloseBehaviorStore: LocalCloseBehaviorStore | null = null;
let _defaultLanguageStore: LocalLanguagePreferenceStore | null = null;

export function HomePage(props: HomePageProps) {
  // PORT: Dart built `windowController ?? DesktopWindowController()` in the
  // constructor, i.e. a fresh controller on every rebuild of the parent. The
  // fallback is kept for the lifetime of this page instead, so the shell's
  // close/tray handlers are not re-registered on every render.
  const fallbackWindowController = useRef<DesktopWindowController | null>(null);
  const windowController = props.windowController ?? (fallbackWindowController.current ??= new DesktopWindowController());
  return (
    <HomeShell
      app={props.app}
      audioDeviceStore={props.audioDeviceStore ?? (_defaultAudioDeviceStore ??= new LocalAudioDeviceStore())}
      liveSessionController={props.liveSessionController}
      livePresenceSoundPlayer={props.livePresenceSoundPlayer}
      livePresenceSpeechPlayer={props.livePresenceSpeechPlayer}
      messageNotificationSoundPlayer={props.messageNotificationSoundPlayer}
      realtime={props.realtime}
      androidSystemService={props.androidSystemService}
      closeBehaviorStore={props.closeBehaviorStore ?? (_defaultCloseBehaviorStore ??= new LocalCloseBehaviorStore())}
      languageStore={props.languageStore ?? (_defaultLanguageStore ??= new LocalLanguagePreferenceStore())}
      windowController={windowController}
      detectedAppUpdate={props.detectedAppUpdate}
      onDetectedAppUpdateShown={props.onDetectedAppUpdateShown}
      musicTrackPreviewPlatformFactory={props.musicTrackPreviewPlatformFactory}
    />
  );
}
