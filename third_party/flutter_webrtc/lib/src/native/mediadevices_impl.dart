import 'dart:async';

import 'package:flutter/services.dart';

import 'package:webrtc_interface/webrtc_interface.dart';

import 'event_channel.dart';
import 'media_stream_impl.dart';
import 'utils.dart';

String? _constraintDeviceId(dynamic constraints) {
  if (constraints is! Map) return null;
  for (final key in const ['sourceId', 'deviceId']) {
    final value = constraints[key];
    if (value is String && value.isNotEmpty) return value;
    if (value is Map) {
      for (final nestedKey in const ['exact', 'ideal']) {
        final nested = value[nestedKey];
        if (nested is String && nested.isNotEmpty) return nested;
      }
    }
  }
  final optional = constraints['optional'];
  if (optional is Iterable) {
    for (final entry in optional) {
      final id = _constraintDeviceId(entry);
      if (id != null) return id;
    }
  }
  return null;
}

class MediaDeviceNative extends MediaDevices {
  MediaDeviceNative._internal() {
    FlutterWebRTCEventChannel.instance.handleEvents.stream.listen((data) {
      var event = data.keys.first;
      Map<dynamic, dynamic> map = data.values.first;
      handleEvent(event, map);
    });
  }

  static final MediaDeviceNative instance = MediaDeviceNative._internal();

  void handleEvent(String event, final Map<dynamic, dynamic> map) async {
    switch (map['event']) {
      case 'onDeviceChange':
        ondevicechange?.call(null);
        break;
    }
  }

  @override
  Future<MediaStream> getUserMedia(
      Map<String, dynamic> mediaConstraints) async {
    try {
      if (WebRTC.platformIsWindows) {
        final preparations = <Future<void>>[];
        final audioDeviceId = _constraintDeviceId(mediaConstraints['audio']);
        if (audioDeviceId != null) {
          preparations.add(_prepareWindowsAudioInput(audioDeviceId));
        }
        final video = mediaConstraints['video'];
        if (video == true || video is Map) {
          // Windows GetUserVideo consumes the camera snapshot built by the
          // timed native worker, so no camera enumeration runs on the Flutter
          // platform thread.
          preparations.add(getSources().then<void>((_) {}));
        }
        await Future.wait(preparations).timeout(const Duration(seconds: 6));
      }
      final response = await WebRTC.invokeMethod(
        'getUserMedia',
        <String, dynamic>{'constraints': mediaConstraints},
      );
      if (response == null) {
        throw Exception('getUserMedia return null, something wrong');
      }

      String streamId = response['streamId'];
      var stream = MediaStreamNative(streamId, 'local');
      stream.setMediaTracks(
          response['audioTracks'] ?? [], response['videoTracks'] ?? []);
      return stream;
    } on PlatformException catch (e) {
      throw 'Unable to getUserMedia: ${e.message}';
    }
  }

  Future<void> _prepareWindowsAudioInput(String deviceId) async {
    for (var attempt = 0; attempt < 3; attempt += 1) {
      final response =
          await WebRTC.invokeMethod<Map<dynamic, dynamic>, dynamic>(
        'selectAudioInput',
        <String, dynamic>{'deviceId': deviceId},
      );
      if (response?['deferred'] != true) return;
      if (attempt < 2) {
        await Future<void>.delayed(const Duration(milliseconds: 200));
      }
    }
  }

  @override
  Future<MediaStream> getDisplayMedia(
      Map<String, dynamic> mediaConstraints) async {
    try {
      final response = await WebRTC.invokeMethod(
        'getDisplayMedia',
        <String, dynamic>{'constraints': mediaConstraints},
      );
      if (response == null) {
        throw Exception('getDisplayMedia return null, something wrong');
      }
      String streamId = response['streamId'];
      var stream = MediaStreamNative(streamId, 'local');
      stream.setMediaTracks(response['audioTracks'], response['videoTracks']);
      return stream;
    } on PlatformException catch (e) {
      throw 'Unable to getDisplayMedia: ${e.message}';
    }
  }

  @override
  Future<List<dynamic>> getSources() async {
    try {
      final response = await WebRTC.invokeMethod(
        'getSources',
        <String, dynamic>{},
      );

      List<dynamic> sources = response['sources'];

      return sources;
    } on PlatformException catch (e) {
      throw 'Unable to getSources: ${e.message}';
    }
  }

  @override
  Future<List<MediaDeviceInfo>> enumerateDevices() async {
    var source = await getSources();
    return source
        .map(
          (e) => MediaDeviceInfo(
              deviceId: e['deviceId'],
              groupId: e['groupId'],
              kind: e['kind'],
              label: e['label']),
        )
        .toList();
  }

  @override
  Future<MediaDeviceInfo> selectAudioOutput(
      [AudioOutputOptions? options]) async {
    await WebRTC.invokeMethod('selectAudioOutput', {
      'deviceId': options?.deviceId,
    });
    // TODO(cloudwebrtc): return the selected device
    return MediaDeviceInfo(label: 'label', deviceId: options!.deviceId);
  }
}
