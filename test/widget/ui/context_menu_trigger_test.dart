import 'package:client/src/ui/context_menu.dart';
import 'package:client/src/ui/read_only_text_box.dart';
import 'package:client/src/ui/tokens.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Android touch hold triggers the application context action', (
    tester,
  ) async {
    final positions = <Offset>[];
    var tapCount = 0;
    await tester.pumpWidget(
      _host(
        platform: TargetPlatform.android,
        child: UiContextMenuTriggerRegion(
          onTriggered: positions.add,
          onTap: () => tapCount++,
          child: const SizedBox(
            key: ValueKey('target'),
            width: 120,
            height: 56,
          ),
        ),
      ),
    );

    final center = tester.getCenter(find.byKey(const ValueKey('target')));
    await tester.longPressAt(center);
    await tester.pump();

    expect(positions, hasLength(1));
    expect(positions.single.dx, closeTo(center.dx, 0.01));
    expect(positions.single.dy, closeTo(center.dy, 0.01));
    expect(tapCount, 0);
  });

  testWidgets('Windows touch hold does not replace desktop secondary click', (
    tester,
  ) async {
    final positions = <Offset>[];
    await tester.pumpWidget(
      _host(
        platform: TargetPlatform.windows,
        child: UiContextMenuTriggerRegion(
          onTriggered: positions.add,
          child: const SizedBox(
            key: ValueKey('target'),
            width: 120,
            height: 56,
          ),
        ),
      ),
    );

    final center = tester.getCenter(find.byKey(const ValueKey('target')));
    await tester.longPressAt(center);
    await tester.pump();
    expect(positions, isEmpty);

    final gesture = await tester.createGesture(
      kind: PointerDeviceKind.mouse,
      buttons: kSecondaryMouseButton,
    );
    await gesture.addPointer(location: center);
    await gesture.down(center);
    await tester.pump();
    await gesture.up();
    await tester.pump();

    expect(positions, hasLength(1));
    expect(positions.single.dx, closeTo(center.dx, 0.01));
    expect(positions.single.dy, closeTo(center.dy, 0.01));
  });

  testWidgets('Android context target keeps nested text double-tap selection', (
    tester,
  ) async {
    final positions = <Offset>[];
    await tester.pumpWidget(
      _host(
        platform: TargetPlatform.android,
        child: SizedBox(
          width: 240,
          child: UiContextMenuTriggerRegion(
            onTriggered: positions.add,
            child: ReadOnlySelectableText(
              value: 'hello world',
              style: uiTheme().textTheme.bodyMedium!,
            ),
          ),
        ),
      ),
    );

    final field = find.byType(TextField);
    final rect = tester.getRect(field);
    final wordPosition = Offset(rect.left + 28, rect.center.dy);
    await tester.tapAt(wordPosition);
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tapAt(wordPosition);
    await tester.pump();

    final selection = tester
        .state<EditableTextState>(find.byType(EditableText))
        .textEditingValue
        .selection;
    expect(selection.isCollapsed, isFalse);
    expect(positions, isEmpty);
  });
}

Widget _host({required TargetPlatform platform, required Widget child}) {
  return MaterialApp(
    theme: uiTheme().copyWith(platform: platform),
    home: Scaffold(body: Center(child: child)),
  );
}
