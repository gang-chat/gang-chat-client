package com.gangchat.client.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.AuthenticatedAppContext
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.CloseBehaviorStore
import com.gangchat.client.app.LanguagePreferenceStore
import com.gangchat.client.app.LivePresenceSpeechPlayer
import com.gangchat.client.app.LiveSessionController
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.app.RealtimeService
import com.gangchat.client.live.LivePresenceSoundPlayer
import com.gangchat.client.shell.AndroidSystemService
import com.gangchat.client.shell.LocalAudioDeviceStore
import com.gangchat.client.shell.LocalCloseBehaviorStore
import com.gangchat.client.shell.LocalLanguagePreferenceStore
import com.gangchat.client.shell.MessageNotificationSoundPlayer

// Port of lib/src/home/home_page.dart.

/**
 * The defaults Dart wrote as `const` constructor calls (canonical instances,
 * so a rebuilt page keeps passing the same stores to the shell).
 */
private object _HomePageDefaults {
    val audioDeviceStore: AudioDeviceStore by lazy { LocalAudioDeviceStore() }
    val closeBehaviorStore: CloseBehaviorStore by lazy { LocalCloseBehaviorStore() }
    val languageStore: LanguagePreferenceStore by lazy { LocalLanguagePreferenceStore() }
}

/**
 * The authenticated app's home page: hosts [HomeShell] for [app].
 *
 * PORT: Dart's `windowController` (the desktop window) is not a parameter;
 * Android has no window to control.
 */
@Composable
fun HomePage(
    app: AuthenticatedAppContext,
    modifier: Modifier = Modifier,
    audioDeviceStore: AudioDeviceStore = _HomePageDefaults.audioDeviceStore,
    liveSessionController: LiveSessionController? = null,
    livePresenceSoundPlayer: LivePresenceSoundPlayer? = null,
    livePresenceSpeechPlayer: LivePresenceSpeechPlayer? = null,
    messageNotificationSoundPlayer: MessageNotificationSoundPlayer? = null,
    realtime: RealtimeService? = null,
    androidSystemService: AndroidSystemService = _HomeShellDefaults.androidSystemService,
    closeBehaviorStore: CloseBehaviorStore = _HomePageDefaults.closeBehaviorStore,
    languageStore: LanguagePreferenceStore = _HomePageDefaults.languageStore,
    detectedAppUpdate: AvailableAppUpdate? = null,
    onDetectedAppUpdateShown: (() -> Unit)? = null,
    musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory = _HomeShellDefaults.musicTrackPreviewPlatformFactory,
) {
    HomeShell(
        app = app,
        audioDeviceStore = audioDeviceStore,
        liveSessionController = liveSessionController,
        livePresenceSoundPlayer = livePresenceSoundPlayer,
        livePresenceSpeechPlayer = livePresenceSpeechPlayer,
        messageNotificationSoundPlayer = messageNotificationSoundPlayer,
        realtime = realtime,
        androidSystemService = androidSystemService,
        closeBehaviorStore = closeBehaviorStore,
        languageStore = languageStore,
        detectedAppUpdate = detectedAppUpdate,
        onDetectedAppUpdateShown = onDetectedAppUpdateShown,
        musicTrackPreviewPlatformFactory = musicTrackPreviewPlatformFactory,
        modifier = modifier,
    )
}
