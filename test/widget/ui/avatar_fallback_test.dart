import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/ui/ui.dart';

void main() {
  test('avatarFallbackColor maps known keys and falls back to blue', () {
    expect(avatarFallbackColor('blue-3'), const Color(0xFF4E68D0));
    expect(normalizeAvatarPresetKey('room-1'), 'blue-3');
    expect(avatarFallbackColor('room-1'), const Color(0xFF4E68D0));
    expect(avatarFallbackColor('graphite-2'), const Color(0xFF50525E));
    expect(avatarFallbackColor('red-2'), const Color(0xFFC9363F));
    expect(avatarFallbackColor('missing'), const Color(0xFF4E68D0));
    expect(avatarPresetLabel('room-1'), '蓝色');
    expect(avatarPresetLabel('purple-2'), '紫色');
    expect(avatarPresetLabel('missing'), '蓝色');
    expect(
      kAvatarPresetKeys,
      containsAll(<String>['red-2', 'gold-2', 'black-2']),
    );
  });

  testWidgets('uploaded avatar keeps its background transparent', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Center(
          child: Avatar(
            label: 'Kai',
            imageUrl: 'uploaded-avatar',
            defaultAvatarKey: 'red-2',
          ),
        ),
      ),
    );

    final container = tester.widget<Container>(
      find.descendant(
        of: find.byType(Avatar),
        matching: find.byType(Container),
      ),
    );
    final decoration = container.decoration! as BoxDecoration;
    expect(decoration.color, Colors.transparent);
  });

  testWidgets('avatar picker exposes Chinese color names', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AvatarPicker(
            label: '头像',
            displayName: '凯',
            imageUrl: null,
            defaultAvatarKey: 'red-2',
            usingPreset: true,
            uploading: false,
            enabled: true,
            presetKeys: const ['red-2'],
            onUpload: () {},
            onPresetSelected: (_) {},
          ),
        ),
      ),
    );

    expect(find.byTooltip('红色'), findsOneWidget);
    expect(find.byTooltip('red-2'), findsNothing);
  });

  testWidgets('Android avatar picker reuses centered preset avatar text', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: uiTheme().copyWith(platform: TargetPlatform.android),
        home: Scaffold(
          body: AvatarPicker(
            label: '头像',
            displayName: 'Test',
            imageUrl: null,
            defaultAvatarKey: 'blue-3',
            usingPreset: true,
            uploading: false,
            enabled: true,
            presetKeys: const ['blue-3'],
            onUpload: () {},
            onPresetSelected: (_) {},
          ),
        ),
      ),
    );

    final preview = find.byKey(const ValueKey('avatar-picker-preview'));
    final avatar = find.descendant(of: preview, matching: find.byType(Avatar));
    final text = find.descendant(of: avatar, matching: find.text('TE'));
    expect(avatar, findsOneWidget);
    expect(tester.getCenter(text).dx, tester.getCenter(avatar).dx);
    expect(tester.getCenter(text).dy, tester.getCenter(avatar).dy);
  });

  testWidgets('active avatar paints the status border above its content', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Center(
          child: Avatar(
            label: 'Kai',
            active: true,
            activeBorderColor: UiColors.presenceVoice,
            paintBorderOnForeground: true,
          ),
        ),
      ),
    );

    final container = tester.widget<Container>(
      find.descendant(
        of: find.byType(Avatar),
        matching: find.byType(Container),
      ),
    );
    final foreground = container.foregroundDecoration as BoxDecoration;
    final border = foreground.border! as Border;
    expect(border.top.color, UiColors.presenceVoice);
    expect(border.top.width, 2);
  });

  testWidgets('avatar keeps status border in the background by default', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Center(child: Avatar(label: 'Kai', active: true)),
      ),
    );

    final container = tester.widget<Container>(
      find.descendant(
        of: find.byType(Avatar),
        matching: find.byType(Container),
      ),
    );
    expect(container.foregroundDecoration, isNull);
  });

  testWidgets(
    'Android preset avatar text stays geometrically centered at fixed scale',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: uiTheme().copyWith(platform: TargetPlatform.android),
          home: const MediaQuery(
            data: MediaQueryData(textScaler: TextScaler.linear(1.8)),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Avatar(
                  key: ValueKey('android-avatar-latin'),
                  label: 'Test',
                  defaultAvatarKey: 'blue-3',
                  size: 18,
                ),
                Avatar(
                  key: ValueKey('android-avatar-digits'),
                  label: '12',
                  defaultAvatarKey: 'green-2',
                  size: 34,
                ),
                Avatar(
                  key: ValueKey('android-avatar-chinese'),
                  label: '暗影',
                  defaultAvatarKey: 'purple-2',
                  size: 40,
                ),
              ],
            ),
          ),
        ),
      );

      for (final (avatarKey, initials, size) in const [
        ('android-avatar-latin', 'TE', 18.0),
        ('android-avatar-digits', '12', 34.0),
        ('android-avatar-chinese', '暗影', 40.0),
      ]) {
        final avatar = find.byKey(ValueKey(avatarKey));
        final textFinder = find.descendant(
          of: avatar,
          matching: find.text(initials),
        );
        final text = tester.widget<Text>(textFinder);
        expect(text.textAlign, TextAlign.center);
        expect(text.textScaler, TextScaler.noScaling);
        expect(text.style?.fontFamily, 'sans-serif');
        expect(text.style?.fontSize, size * 0.36);
        expect(text.style?.height, isNull);
        expect(text.style?.leadingDistribution, isNull);
        final avatarCenter = tester.getCenter(avatar);
        final textCenter = tester.getCenter(textFinder);
        expect(textCenter.dx, avatarCenter.dx);
        expect(textCenter.dy, avatarCenter.dy);
      }
    },
  );

  testWidgets('Windows preset avatar text keeps desktop font behavior', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: uiTheme().copyWith(platform: TargetPlatform.windows),
        home: const Center(
          child: Avatar(
            key: ValueKey('windows-avatar'),
            label: 'Test',
            defaultAvatarKey: 'blue-3',
            size: 18,
          ),
        ),
      ),
    );

    final avatar = find.byKey(const ValueKey('windows-avatar'));
    final textFinder = find.descendant(of: avatar, matching: find.text('TE'));
    final text = tester.widget<Text>(textFinder);
    expect(text.textScaler, isNull);
    expect(text.style?.fontFamily, isNull);
    expect(text.style?.height, isNull);
    expect(text.style?.leadingDistribution, isNull);
    expect(tester.getCenter(textFinder), tester.getCenter(avatar));
  });

  testWidgets('avatar can suppress shared status and base borders', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Center(
          child: Avatar(label: 'Kai', active: true, showBorder: false),
        ),
      ),
    );

    final container = tester.widget<Container>(
      find.descendant(
        of: find.byType(Avatar),
        matching: find.byType(Container),
      ),
    );
    final decoration = container.decoration! as BoxDecoration;
    final label = tester.widget<Text>(find.text('KA'));
    expect(decoration.border, isNull);
    expect(container.foregroundDecoration, isNull);
    // Without the active border the initials keep the neutral fallback color
    // instead of switching to the active accent.
    expect(label.style?.color, Colors.white);
    expect(label.style?.color, isNot(UiColors.accent));
  });

  testWidgets('avatar picker opens preview only for uploaded images', (
    tester,
  ) async {
    var previews = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 460,
              child: AvatarPicker(
                label: 'Avatar',
                displayName: 'Kai',
                imageUrl: 'uploaded-avatar',
                defaultAvatarKey: 'blue-3',
                usingPreset: false,
                uploading: false,
                enabled: true,
                onUpload: () {},
                onPresetSelected: (_) {},
                onImagePreview: () => previews += 1,
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('avatar-picker-preview')));
    await tester.pump();

    expect(previews, 1);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 460,
              child: AvatarPicker(
                label: 'Avatar',
                displayName: 'Kai',
                imageUrl: 'uploaded-avatar',
                defaultAvatarKey: 'blue-3',
                usingPreset: true,
                uploading: false,
                enabled: true,
                onUpload: () {},
                onPresetSelected: (_) {},
                onImagePreview: () => previews += 1,
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('avatar-picker-preview')));
    await tester.pump();

    expect(previews, 1);
  });
}
