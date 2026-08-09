import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../app/audio_device_preferences.dart';

/// Migrates device-wide preferences from historical application identities.
///
/// Windows derives its SharedPreferences directory from the executable's
/// CompanyName + ProductName metadata. Gang Chat used `client` as ProductName
/// before the visible product rename, so an in-place update appeared to reset
/// every preference even though the old JSON file was still present. Android's
/// application id and macOS's bundle id have remained stable and need no file
/// migration.
class LocalPreferencesMigration {
  const LocalPreferencesMigration();

  static const _windowsMigrationMarker =
      'gang.preferencesMigration.windowsProductName.v1';
  static const _flutterStorageKeyPrefix = 'flutter.';
  static const _audioVolumeKeys = <String>{
    'gang.audioInputVolume',
    'gang.audioOutputVolume',
    'gang.musicBoxVolume',
    'gang.screenShareVolume',
  };

  Future<void> migrate() async {
    if (kIsWeb || !Platform.isWindows) return;
    final roamingAppData = Platform.environment['APPDATA']?.trim();
    if (roamingAppData == null || roamingAppData.isEmpty) return;

    final preferences = await SharedPreferences.getInstance();
    if (preferences.getBool(_windowsMigrationMarker) == true) return;

    final legacyFile = File(
      '$roamingAppData${Platform.pathSeparator}'
      'com.gangchat${Platform.pathSeparator}'
      'client${Platform.pathSeparator}'
      'shared_preferences.json',
    );
    if (!await legacyFile.exists()) return;

    final values = await _readLegacyPreferences(legacyFile);
    await migrateLegacyPreferences(
      preferences: preferences,
      legacyValues: values,
    );
    await preferences.setBool(_windowsMigrationMarker, true);
  }

  Future<Map<String, Object?>> _readLegacyPreferences(File file) async {
    try {
      final decoded = jsonDecode(await file.readAsString());
      if (decoded is! Map) return const <String, Object?>{};
      return Map<String, Object?>.from(decoded);
    } catch (_) {
      // A corrupt legacy file must never prevent the current app from opening.
      return const <String, Object?>{};
    }
  }

  @visibleForTesting
  static Future<int> migrateLegacyPreferences({
    required SharedPreferences preferences,
    required Map<String, Object?> legacyValues,
  }) async {
    var migrated = 0;
    for (final entry in legacyValues.entries) {
      final storageKey = entry.key.startsWith(_flutterStorageKeyPrefix)
          ? entry.key.substring(_flutterStorageKeyPrefix.length)
          : entry.key;
      if (!_isSafeSharedPreferenceKey(storageKey)) {
        continue;
      }
      final hasCurrentValue = preferences.containsKey(storageKey);
      if (hasCurrentValue &&
          !_shouldRecoverLegacyDefault(
            storageKey,
            preferences.get(storageKey),
          )) {
        continue;
      }
      if (await _writeSupportedValue(preferences, storageKey, entry.value)) {
        migrated += 1;
      }
    }
    return migrated;
  }

  static bool _isSafeSharedPreferenceKey(String key) {
    if (!key.startsWith('gang.')) return false;
    // Authentication secrets and remembered passwords belong to
    // flutter_secure_storage and must never be copied into plaintext JSON.
    return key != 'gang.refreshToken' &&
        !key.startsWith('gang.loginAccountPassword.');
  }

  static bool _shouldRecoverLegacyDefault(String key, Object? currentValue) {
    if (!_audioVolumeKeys.contains(key) || currentValue is! num) return false;
    return (currentValue.toDouble() - defaultAudioVolume).abs() < 0.000001;
  }

  static Future<bool> _writeSupportedValue(
    SharedPreferences preferences,
    String key,
    Object? value,
  ) async {
    if (value is bool) return preferences.setBool(key, value);
    if (value is int) return preferences.setInt(key, value);
    if (value is double) return preferences.setDouble(key, value);
    if (value is String) return preferences.setString(key, value);
    if (value is List && value.every((item) => item is String)) {
      return preferences.setStringList(key, value.cast<String>());
    }
    return false;
  }
}
