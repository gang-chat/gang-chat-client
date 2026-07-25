import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Android-only operating-system capabilities used by the shell adapters.
///
/// The Dart-facing contract deliberately contains no Android SDK types. Other
/// platforms return harmless defaults and keep using their existing adapters.
class AndroidSystemService {
  const AndroidSystemService();

  static const MethodChannel _channel = MethodChannel(
    'gang_chat/android_system',
  );
  static final StreamController<String> _selectedRooms =
      StreamController<String>.broadcast();
  static final StreamController<AndroidPushRegistration>
  _pushRegistrationChanges =
      StreamController<AndroidPushRegistration>.broadcast();
  static bool _methodHandlerInstalled = false;

  bool get isSupported => !kIsWeb && Platform.isAndroid;

  Stream<String> get selectedNotificationRoomIds {
    _ensureMethodHandler();
    return _selectedRooms.stream;
  }

  Stream<AndroidPushRegistration> get pushRegistrationChanges {
    _ensureMethodHandler();
    return _pushRegistrationChanges.stream;
  }

  Future<void> openUri(Uri uri) {
    return _invokeVoid('openUri', {'uri': uri.toString()});
  }

  Future<void> openMailto(Uri uri) {
    return _invokeVoid('openMailto', {'uri': uri.toString()});
  }

  Future<String?> installedAt() async {
    if (!isSupported) return null;
    return _channel.invokeMethod<String>('getInstalledAt');
  }

  Future<AndroidDocumentLocation?> createDocument({
    required String suggestedName,
    String? mimeType,
  }) async {
    if (!isSupported) return null;
    final result = await _channel.invokeMapMethod<String, Object?>(
      'createDocument',
      {'suggestedName': suggestedName, 'mimeType': mimeType},
    );
    if (result == null) return null;
    final uri = result['uri']?.toString().trim() ?? '';
    final path = result['path']?.toString().trim() ?? '';
    if (uri.isEmpty || path.isEmpty) {
      throw const FormatException('Android 保存位置响应不完整');
    }
    return AndroidDocumentLocation(uri: uri, stagingPath: path);
  }

  Future<void> writeDocumentBytes({
    required String uri,
    required Uint8List bytes,
  }) {
    return _invokeVoid('writeDocumentBytes', {'uri': uri, 'bytes': bytes});
  }

  Future<void> commitDocumentFile({
    required String uri,
    required String stagingPath,
  }) {
    return _invokeVoid('commitDocumentFile', {'uri': uri, 'path': stagingPath});
  }

  Future<void> discardDocumentFile(String stagingPath) {
    return _invokeVoid('discardDocumentFile', {'path': stagingPath});
  }

  Future<String?> saveToDownloads({
    required String filename,
    required Uint8List bytes,
    String? mimeType,
  }) async {
    if (!isSupported) return null;
    return _channel.invokeMethod<String>('saveToDownloads', {
      'filename': filename,
      'mimeType': mimeType,
      'bytes': bytes,
    });
  }

  Future<AndroidClipboardImage?> readClipboardImage() async {
    if (!isSupported) return null;
    final result = await _channel.invokeMapMethod<String, Object?>(
      'readClipboardImage',
    );
    if (result == null) return null;
    final bytes = result['bytes'];
    if (bytes is! Uint8List || bytes.isEmpty) return null;
    return AndroidClipboardImage(
      bytes: bytes,
      filename: result['filename']?.toString() ?? 'clipboard-image.png',
      mimeType: result['mimeType']?.toString() ?? 'image/png',
    );
  }

  Future<bool> writeClipboardImage({
    required Uint8List bytes,
    required String mimeType,
  }) async {
    if (!isSupported || bytes.isEmpty) return false;
    return await _channel.invokeMethod<bool>('writeClipboardImage', {
          'bytes': bytes,
          'mimeType': mimeType,
        }) ??
        false;
  }

  Future<void> enterBackground() => _invokeVoid('enterBackground');

  Future<void> enterForeground() => _invokeVoid('enterForeground');

  Future<void> showRoomMessage({
    required String roomId,
    required String roomName,
    required String sender,
    required String body,
    required int unreadCount,
    String? messageId,
  }) {
    return _invokeVoid('showRoomMessage', {
      'roomId': roomId,
      'roomName': roomName,
      'sender': sender,
      'body': body,
      'unreadCount': unreadCount,
      'messageId': messageId,
    });
  }

  Future<void> cancelRoomNotification(String roomId) {
    return _invokeVoid('cancelRoomNotification', {'roomId': roomId});
  }

  Future<void> syncBadge(int unreadCount) {
    return _invokeVoid('syncBadge', {
      'unreadCount': unreadCount.clamp(0, 9999),
    });
  }

  Future<void> clearMessageNotifications() {
    return _invokeVoid('clearMessageNotifications');
  }

  Future<bool> notificationsEnabled() async {
    if (!isSupported) return false;
    return await _channel.invokeMethod<bool>('notificationsEnabled') ?? false;
  }

  Future<bool> notificationPreferenceEnabled() async {
    if (!isSupported) return false;
    return await _channel.invokeMethod<bool>('notificationPreferenceEnabled') ??
        true;
  }

  Future<bool> setNotificationPreferenceEnabled(bool enabled) async {
    if (!isSupported) return false;
    return await _channel.invokeMethod<bool>(
          'setNotificationPreferenceEnabled',
          {'enabled': enabled},
        ) ??
        false;
  }

  Future<bool> requestNotificationPermission() async {
    if (!isSupported) return false;
    return await _channel.invokeMethod<bool>('requestNotificationPermission') ??
        false;
  }

  Future<AndroidPushRegistration?> pushRegistration() async {
    if (!isSupported) return null;
    _ensureMethodHandler();
    final result = await _channel.invokeMapMethod<String, Object?>(
      'getPushRegistration',
    );
    return result == null ? null : AndroidPushRegistration.fromMap(result);
  }

  Future<bool> requestBluetoothConnectPermission() async {
    if (!isSupported) return false;
    return await _channel.invokeMethod<bool>(
          'requestBluetoothConnectPermission',
        ) ??
        false;
  }

  Future<void> openNotificationSettings() {
    return _invokeVoid('openNotificationSettings');
  }

  Future<String?> takeInitialNotificationRoomId() async {
    if (!isSupported) return null;
    _ensureMethodHandler();
    final value = await _channel.invokeMethod<String>(
      'getInitialNotificationRoomId',
    );
    final roomId = value?.trim() ?? '';
    return roomId.isEmpty ? null : roomId;
  }

  Future<void> speak({
    required List<String> segments,
    required double volume,
    required Duration pause,
  }) {
    return _invokeVoid('speak', {
      'segments': segments,
      'volume': volume.clamp(0.0, 1.0),
      'pauseMs': pause.inMilliseconds,
    });
  }

  Future<void> disposeSpeech() => _invokeVoid('disposeSpeech');

  Future<void> _invokeVoid(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    if (!isSupported) return;
    await _channel.invokeMethod<void>(method, arguments);
  }

  static void _ensureMethodHandler() {
    if (_methodHandlerInstalled) return;
    _methodHandlerInstalled = true;
    _channel.setMethodCallHandler((call) async {
      final arguments = call.arguments;
      if (arguments is! Map) return;
      if (call.method == 'notificationSelected') {
        final roomId = arguments['roomId']?.toString().trim() ?? '';
        if (roomId.isNotEmpty) _selectedRooms.add(roomId);
        return;
      }
      if (call.method == 'pushTokenChanged') {
        final registration = AndroidPushRegistration.tryFromMap(arguments);
        if (registration != null) _pushRegistrationChanges.add(registration);
      }
    });
  }
}

class AndroidPushRegistration {
  const AndroidPushRegistration({
    required this.provider,
    required this.installationId,
    required this.token,
    required this.enabled,
  });

  final String provider;
  final String installationId;
  final String token;
  final bool enabled;

  factory AndroidPushRegistration.fromMap(Map<Object?, Object?> value) {
    final registration = tryFromMap(value);
    if (registration == null) {
      throw const FormatException('Android 推送注册信息不完整');
    }
    return registration;
  }

  static AndroidPushRegistration? tryFromMap(Map<Object?, Object?> value) {
    final provider = value['provider']?.toString().trim() ?? '';
    final installationId =
        value['installationId']?.toString().trim() ?? '';
    final token = value['token']?.toString().trim() ?? '';
    if (provider.isEmpty || installationId.isEmpty || token.isEmpty) {
      return null;
    }
    return AndroidPushRegistration(
      provider: provider,
      installationId: installationId,
      token: token,
      enabled: value['enabled'] as bool? ?? true,
    );
  }
}

class AndroidDocumentLocation {
  const AndroidDocumentLocation({required this.uri, required this.stagingPath});

  final String uri;
  final String stagingPath;
}

class AndroidClipboardImage {
  const AndroidClipboardImage({
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  final Uint8List bytes;
  final String filename;
  final String mimeType;
}
