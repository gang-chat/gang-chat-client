import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:client/src/shell/local_preferences_migration.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{
      'gang.audioInputVolume': 0.8,
    });
  });

  test(
    'migrates every supported gang preference without overwriting',
    () async {
      final preferences = await SharedPreferences.getInstance();

      final count = await LocalPreferencesMigration.migrateLegacyPreferences(
        preferences: preferences,
        legacyValues: <String, Object?>{
          'flutter.gang.audioInputVolume': 0.25,
          'flutter.gang.audioOutputVolume': 0.65,
          'flutter.gang.closeBehavior': 'minimize_to_tray',
          'flutter.gang.autoUpdatePrompt': false,
          'flutter.gang.futureStringList': <String>['one', 'two'],
          'flutter.gang.refreshToken': 'must-not-migrate',
          'flutter.gang.loginAccountPassword.alice': 'must-not-migrate',
          'flutter.unrelated.preference': 'ignored',
          'flutter.gang.unsupported': <String, Object?>{'value': 1},
        },
      );

      expect(count, 4);
      expect(preferences.getDouble('gang.audioInputVolume'), 0.8);
      expect(preferences.getDouble('gang.audioOutputVolume'), 0.65);
      expect(preferences.getString('gang.closeBehavior'), 'minimize_to_tray');
      expect(preferences.getBool('gang.autoUpdatePrompt'), isFalse);
      expect(preferences.getStringList('gang.futureStringList'), [
        'one',
        'two',
      ]);
      expect(preferences.containsKey('unrelated.preference'), isFalse);
      expect(preferences.containsKey('gang.unsupported'), isFalse);
      expect(preferences.containsKey('gang.refreshToken'), isFalse);
      expect(
        preferences.containsKey('gang.loginAccountPassword.alice'),
        isFalse,
      );
    },
  );

  test(
    'recovers defaulted volumes but preserves later custom values',
    () async {
      SharedPreferences.setMockInitialValues(<String, Object>{
        'gang.audioInputVolume': 0.5,
        'gang.audioOutputVolume': 0.35,
        'gang.musicBoxVolume': 0.5,
      });
      final preferences = await SharedPreferences.getInstance();

      final count = await LocalPreferencesMigration.migrateLegacyPreferences(
        preferences: preferences,
        legacyValues: const <String, Object?>{
          'flutter.gang.audioInputVolume': 0.9,
          'flutter.gang.audioOutputVolume': 0.8,
          'flutter.gang.musicBoxVolume': 0.65,
        },
      );

      expect(count, 2);
      expect(preferences.getDouble('gang.audioInputVolume'), 0.9);
      expect(preferences.getDouble('gang.audioOutputVolume'), 0.35);
      expect(preferences.getDouble('gang.musicBoxVolume'), 0.65);
    },
  );
}
