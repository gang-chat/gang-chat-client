// Copyright 2024 LiveKit, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'dart:async';

import 'package:collection/collection.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;

import '../logger.dart';
import '../support/native.dart';
import '../support/native_audio.dart';
import '../support/platform.dart';
import '../track/audio_management.dart';

class MediaDevice {
  const MediaDevice(this.deviceId, this.label, this.kind, this.groupId);

  final String deviceId;
  final String label;
  final String kind;
  final String? groupId;

  @override
  bool operator ==(covariant MediaDevice other) {
    if (identical(this, other)) return true;

    return other.deviceId == deviceId && other.kind == kind && other.label == label && other.groupId == groupId;
  }

  @override
  int get hashCode {
    return deviceId.hashCode ^ kind.hashCode ^ label.hashCode;
  }

  @override
  String toString() {
    return 'MediaDevice{deviceId: $deviceId, label: $label, kind: $kind, groupId: $groupId}';
  }
}

class Hardware {
  Hardware._internal() {
    rtc.navigator.mediaDevices.ondevicechange = _onDeviceChange;
    unawaited(
      _refreshSelectedDevices().catchError((Object error, StackTrace stack) {
        logger.warning('Initial media-device enumeration failed: $error');
        return <MediaDevice>[];
      }),
    );
  }

  static final Hardware instance = Hardware._internal();

  final StreamController<List<MediaDevice>> onDeviceChange = StreamController.broadcast();

  MediaDevice? selectedAudioInput;

  MediaDevice? selectedAudioOutput;

  MediaDevice? selectedVideoInput;

  bool? get speakerOn => _preferSpeakerOutput;

  bool _preferSpeakerOutput = true;

  bool get preferSpeakerOutput => _preferSpeakerOutput;

  bool _forceSpeakerOutput = false;

  /// if true, will force speaker output even if headphones or bluetooth is connected
  /// only supported on iOS for now
  bool get forceSpeakerOutput => _forceSpeakerOutput && _preferSpeakerOutput;

  // This flag is used to determine if automatic native configuration
  // of audio is enabled. If set to false Natvive.configureAudio
  // will not be called, and the user is responsible for configuring
  // the native audio configuration manually.
  bool _isAutomaticConfigurationEnabled = true;
  bool get isAutomaticConfigurationEnabled => _isAutomaticConfigurationEnabled;

  void setAutomaticConfigurationEnabled({required bool enable}) {
    _isAutomaticConfigurationEnabled = enable;
  }

  Future<List<MediaDevice>> enumerateDevices({String? type}) async {
    final infos = await rtc.navigator.mediaDevices.enumerateDevices();
    var devices = infos.map((e) => MediaDevice(e.deviceId, e.label, e.kind!, e.groupId)).toList();
    if (type != null && type.isNotEmpty) {
      devices = devices.where((d) => d.kind == type).toList();
    }
    return devices;
  }

  Future<List<MediaDevice>> audioInputs() async {
    return enumerateDevices(type: 'audioinput');
  }

  Future<List<MediaDevice>> audioOutputs() async {
    return enumerateDevices(type: 'audiooutput');
  }

  Future<List<MediaDevice>> videoInputs() async {
    return enumerateDevices(type: 'videoinput');
  }

  Future<void> selectAudioOutput(MediaDevice device) async {
    if (!lkPlatformIsDesktop() && !lkPlatformIs(PlatformType.android)) {
      logger.warning(
        'selectAudioOutput is only supported on Desktop and Android',
      );
      return;
    }
    selectedAudioOutput = device;
    await rtc.Helper.selectAudioOutput(device.deviceId);
  }

  Future<void> selectAudioInput(MediaDevice device) async {
    if (lkPlatformIs(PlatformType.web) || lkPlatformIs(PlatformType.iOS)) {
      logger.warning(
        'selectAudioInput is not supported on Web or iOS',
      );
      return;
    }
    selectedAudioInput = device;
    await rtc.Helper.selectAudioInput(device.deviceId);
  }

  @Deprecated('use setSpeakerphoneOn')
  Future<void> setPreferSpeakerOutput(bool enable) => setSpeakerphoneOn(enable);

  bool get canSwitchSpeakerphone => lkPlatformIsMobile();

  /// [enable] set speakerphone on or off, by default wired/bluetooth headsets will still
  /// be prioritized even if set to true.
  /// [forceSpeakerOutput] if true, will force speaker output even if headphones
  /// or bluetooth is connected, only supported on iOS for now
  Future<void> setSpeakerphoneOn(bool enable, {bool forceSpeakerOutput = false}) async {
    if (canSwitchSpeakerphone) {
      _preferSpeakerOutput = enable;
      _forceSpeakerOutput = forceSpeakerOutput;
      if (lkPlatformIs(PlatformType.iOS)) {
        NativeAudioConfiguration? config;
        if (lkPlatformIs(PlatformType.iOS)) {
          // Only iOS for now...
          config = await onConfigureNativeAudio.call(audioTrackState);
          if (_preferSpeakerOutput && _forceSpeakerOutput) {
            config = config.copyWith(
              appleAudioCategoryOptions: {
                ...?config.appleAudioCategoryOptions,
                AppleAudioCategoryOption.defaultToSpeaker,
              },
            );
          }
          logger.fine('configuring for ${audioTrackState} using ${config}...');
          try {
            if (_isAutomaticConfigurationEnabled) {
              await Native.configureAudio(config);
            }
          } catch (error) {
            logger.warning('failed to configure ${error}');
          }
        }
      } else {
        await rtc.Helper.setSpeakerphoneOn(enable);
      }
    } else {
      logger.warning('setSpeakerphoneOn only support on iOS/Android');
    }
  }

  Future<rtc.MediaStream> openCamera({MediaDevice? device, bool? facingMode}) async {
    final constraints = <String, dynamic>{
      if (facingMode != null) 'facingMode': facingMode ? 'user' : 'environment',
    };
    if (device != null) {
      if (lkPlatformIs(PlatformType.web)) {
        constraints['deviceId'] = device.deviceId;
      } else {
        constraints['optional'] = [
          {'sourceId': device.deviceId}
        ];
      }
    }
    selectedVideoInput = device;
    return rtc.navigator.mediaDevices.getUserMedia(<String, dynamic>{
      'audio': false,
      'video': device != null ? constraints : true,
    });
  }

  MediaDevice? _reconciledSelection(
    MediaDevice? selected,
    List<MediaDevice> devices,
    String kind,
  ) {
    final matching = devices.where((device) => device.kind == kind).toList();
    if (selected != null) {
      final refreshed = matching.firstWhereOrNull(
        (device) => device.deviceId == selected.deviceId,
      );
      if (refreshed != null) return refreshed;
    }
    return matching.firstOrNull;
  }

  Future<List<MediaDevice>> _refreshSelectedDevices() async {
    final devices = await enumerateDevices();
    selectedAudioInput = _reconciledSelection(
      selectedAudioInput,
      devices,
      'audioinput',
    );
    selectedAudioOutput = _reconciledSelection(
      selectedAudioOutput,
      devices,
      'audiooutput',
    );
    selectedVideoInput = _reconciledSelection(
      selectedVideoInput,
      devices,
      'videoinput',
    );
    return devices;
  }

  /// Re-enumerates device topology and emits the reconciled selections.
  ///
  /// The Windows runner calls this indirectly for camera-only device-tree
  /// changes, which libwebrtc's audio-only desktop callback cannot observe.
  Future<List<MediaDevice>> refreshDevices() async {
    final devices = await _refreshSelectedDevices();
    onDeviceChange.add(devices);
    return devices;
  }

  dynamic _onDeviceChange(dynamic _) async {
    try {
      await refreshDevices();
    } catch (error) {
      logger.warning('Media-device refresh failed: $error');
      // Still emit a topology signal so platform-specific recovery can run.
      onDeviceChange.add(const []);
    }
  }
}
