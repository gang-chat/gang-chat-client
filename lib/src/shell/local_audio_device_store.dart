import 'package:shared_preferences/shared_preferences.dart';

import '../app/audio_device_store.dart';
import '../app/audio_levels.dart';
import '../live/screen_share_quality.dart';

/// Persists audio device + volume preferences in [SharedPreferences].
///
/// These values (which mic/speaker, what volumes) are not sensitive, so they
/// live in plain local preferences rather than the keychain. On macOS reading
/// the keychain pops an authorization prompt every launch under a debug/unstable
/// code signature; SharedPreferences (a plist on macOS) never does. The refresh
/// token stays in flutter_secure_storage — see TokenStore.
class LocalAudioDeviceStore extends AudioDeviceStore {
  const LocalAudioDeviceStore();

  static const _inputDeviceIdKey = 'gang.audioInputDeviceId';
  static const _inputDeviceLabelKey = 'gang.audioInputDeviceLabel';
  static const _inputDeviceGroupIdKey = 'gang.audioInputDeviceGroupId';
  static const _outputDeviceIdKey = 'gang.audioOutputDeviceId';
  static const _outputDeviceLabelKey = 'gang.audioOutputDeviceLabel';
  static const _outputDeviceGroupIdKey = 'gang.audioOutputDeviceGroupId';
  static const _inputVolumeKey = 'gang.audioInputVolume';
  static const _outputVolumeKey = 'gang.audioOutputVolume';
  static const _musicBoxVolumeKey = 'gang.musicBoxVolume';
  static const _screenShareVolumeKey = 'gang.screenShareVolume';
  static const _screenShareMaxHeightKey = 'gang.screenShareMaxHeight';
  static const _screenShareFrameRateKey = 'gang.screenShareFrameRate';
  static const _participantVoiceVolumePrefix = 'gang.participantVoiceVolume.';

  @override
  Future<StoredAudioDevices> read() async {
    final prefs = await SharedPreferences.getInstance();
    return StoredAudioDevices(
      inputDeviceId: storedAudioDeviceIdFromStorageValue(
        prefs.getString(_inputDeviceIdKey),
      ),
      inputDeviceLabel: storedAudioDeviceSignatureFromStorageValue(
        prefs.getString(_inputDeviceLabelKey),
      ),
      inputDeviceGroupId: storedAudioDeviceSignatureFromStorageValue(
        prefs.getString(_inputDeviceGroupIdKey),
      ),
      outputDeviceId: storedAudioDeviceIdFromStorageValue(
        prefs.getString(_outputDeviceIdKey),
      ),
      outputDeviceLabel: storedAudioDeviceSignatureFromStorageValue(
        prefs.getString(_outputDeviceLabelKey),
      ),
      outputDeviceGroupId: storedAudioDeviceSignatureFromStorageValue(
        prefs.getString(_outputDeviceGroupIdKey),
      ),
      inputVolume: _readVolume(prefs, _inputVolumeKey),
      outputVolume: _readVolume(prefs, _outputVolumeKey),
      musicBoxVolume: _readVolume(prefs, _musicBoxVolumeKey),
      screenShareVolume: _readVolume(prefs, _screenShareVolumeKey),
      screenShareMaxHeight: normalizedScreenShareMaxHeight(
        prefs.getInt(_screenShareMaxHeightKey),
      ),
      screenShareFrameRate: normalizedScreenShareFrameRate(
        prefs.getInt(_screenShareFrameRateKey),
      ),
    );
  }

  @override
  Future<void> writeInputDeviceId(String deviceId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_inputDeviceIdKey, deviceId);
  }

  @override
  Future<void> writeInputDevicePreference({
    required String deviceId,
    String? label,
    String? groupId,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await _writeDevicePreference(
      prefs,
      idKey: _inputDeviceIdKey,
      labelKey: _inputDeviceLabelKey,
      groupIdKey: _inputDeviceGroupIdKey,
      deviceId: deviceId,
      label: label,
      groupId: groupId,
    );
  }

  @override
  Future<void> writeOutputDeviceId(String deviceId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_outputDeviceIdKey, deviceId);
  }

  @override
  Future<void> writeOutputDevicePreference({
    required String deviceId,
    String? label,
    String? groupId,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await _writeDevicePreference(
      prefs,
      idKey: _outputDeviceIdKey,
      labelKey: _outputDeviceLabelKey,
      groupIdKey: _outputDeviceGroupIdKey,
      deviceId: deviceId,
      label: label,
      groupId: groupId,
    );
  }

  @override
  Future<void> writeInputVolume(double volume) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_inputVolumeKey, normalizedAudioVolume(volume));
  }

  @override
  Future<void> writeOutputVolume(double volume) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_outputVolumeKey, normalizedAudioVolume(volume));
  }

  @override
  Future<void> writeMusicBoxVolume(double volume) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_musicBoxVolumeKey, normalizedAudioVolume(volume));
  }

  @override
  Future<void> writeScreenShareVolume(double volume) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_screenShareVolumeKey, normalizedAudioVolume(volume));
  }

  @override
  Future<void> writeScreenShareMaxHeight(int height) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(
      _screenShareMaxHeightKey,
      normalizedScreenShareMaxHeight(height),
    );
  }

  @override
  Future<void> writeScreenShareFrameRate(int frameRate) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(
      _screenShareFrameRateKey,
      normalizedScreenShareFrameRate(frameRate),
    );
  }

  @override
  Future<double> readParticipantVoiceVolume(String userId) async {
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getDouble(_participantVoiceVolumeKey(userId));
    if (value == null) return defaultParticipantVoiceVolume;
    return normalizedParticipantVoiceVolume(value);
  }

  @override
  Future<void> writeParticipantVoiceVolume(String userId, double volume) async {
    final prefs = await SharedPreferences.getInstance();
    final normalized = normalizedParticipantVoiceVolume(volume);
    final key = _participantVoiceVolumeKey(userId);
    if (normalized == defaultParticipantVoiceVolume) {
      await prefs.remove(key);
      return;
    }
    await prefs.setDouble(key, normalized);
  }

  double _readVolume(SharedPreferences prefs, String key) {
    final value = prefs.getDouble(key);
    return value == null ? defaultAudioVolume : normalizedAudioVolume(value);
  }

  Future<void> _writeDevicePreference(
    SharedPreferences prefs, {
    required String idKey,
    required String labelKey,
    required String groupIdKey,
    required String deviceId,
    required String? label,
    required String? groupId,
  }) async {
    await prefs.setString(idKey, deviceId);
    await _writeOptionalString(prefs, labelKey, label);
    await _writeOptionalString(prefs, groupIdKey, groupId);
  }

  Future<void> _writeOptionalString(
    SharedPreferences prefs,
    String key,
    String? value,
  ) async {
    final trimmed = value?.trim();
    if (trimmed == null || trimmed.isEmpty) {
      await prefs.remove(key);
      return;
    }
    await prefs.setString(key, trimmed);
  }

  String _participantVoiceVolumeKey(String userId) {
    return '$_participantVoiceVolumePrefix${Uri.encodeComponent(userId)}';
  }
}
