import 'package:flutter/material.dart';

import '../app/app_update.dart';
import '../app/audio_device_store.dart';
import '../app/authenticated_app_context.dart';
import '../app/close_behavior.dart';
import '../app/language_preference.dart';
import '../app/live_session_controller.dart';
import '../app/live_presence_announcement.dart';
import '../app/music_track_preview.dart';
import '../app/realtime_controller.dart';
import '../live/live_presence_sound_service.dart';
import '../shell/local_close_behavior_store.dart';
import '../shell/android_system_service.dart';
import '../shell/desktop_window_controller.dart';
import '../shell/local_audio_device_store.dart';
import '../shell/local_language_preference_store.dart';
import '../shell/message_notification_sound_service.dart';
import '../shell/music_track_preview_service.dart';
import 'home_shell.dart';

class HomePage extends StatelessWidget {
  HomePage({
    super.key,
    required this.app,
    this.audioDeviceStore = const LocalAudioDeviceStore(),
    this.liveSessionController,
    this.livePresenceSoundPlayer,
    this.livePresenceSpeechPlayer,
    this.messageNotificationSoundPlayer,
    this.realtime,
    this.androidSystemService = const AndroidSystemService(),
    this.closeBehaviorStore = const LocalCloseBehaviorStore(),
    this.languageStore = const LocalLanguagePreferenceStore(),
    this.detectedAppUpdate,
    this.onDetectedAppUpdateShown,
    DesktopWindowController? windowController,
    this.musicTrackPreviewPlatformFactory =
        const DefaultMusicTrackPreviewPlatformFactory(),
  }) : windowController = windowController ?? DesktopWindowController();

  final AuthenticatedAppContext app;
  final AudioDeviceStore audioDeviceStore;
  final LiveSessionController? liveSessionController;
  final LivePresenceSoundPlayer? livePresenceSoundPlayer;
  final LivePresenceSpeechPlayer? livePresenceSpeechPlayer;
  final MessageNotificationSoundPlayer? messageNotificationSoundPlayer;
  final RealtimeService? realtime;
  final AndroidSystemService androidSystemService;
  final CloseBehaviorStore closeBehaviorStore;
  final LanguagePreferenceStore languageStore;
  final AvailableAppUpdate? detectedAppUpdate;
  final VoidCallback? onDetectedAppUpdateShown;
  final DesktopWindowController windowController;
  final MusicTrackPreviewPlatformFactory musicTrackPreviewPlatformFactory;

  @override
  Widget build(BuildContext context) {
    return HomeShell(
      app: app,
      audioDeviceStore: audioDeviceStore,
      liveSessionController: liveSessionController,
      livePresenceSoundPlayer: livePresenceSoundPlayer,
      livePresenceSpeechPlayer: livePresenceSpeechPlayer,
      messageNotificationSoundPlayer: messageNotificationSoundPlayer,
      realtime: realtime,
      androidSystemService: androidSystemService,
      closeBehaviorStore: closeBehaviorStore,
      languageStore: languageStore,
      windowController: windowController,
      detectedAppUpdate: detectedAppUpdate,
      onDetectedAppUpdateShown: onDetectedAppUpdateShown,
      musicTrackPreviewPlatformFactory: musicTrackPreviewPlatformFactory,
    );
  }
}
