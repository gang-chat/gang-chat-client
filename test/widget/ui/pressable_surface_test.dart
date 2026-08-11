import 'package:client/src/ui/surface.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('ignores a pointer release delivered after disposal', (
    tester,
  ) async {
    final groupId = Object();
    var showSurface = true;

    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            return Scaffold(
              body: Column(
                children: [
                  TapRegion(
                    groupId: groupId,
                    onTapOutside: (_) => setState(() => showSurface = false),
                    child: const SizedBox(width: 40, height: 40),
                  ),
                  if (showSurface)
                    PressableSurface(
                      key: const ValueKey<String>('disposable-surface'),
                      width: 120,
                      height: 36,
                      onPressed: () {},
                      child: const Center(child: Text('打开页面')),
                    ),
                ],
              ),
            );
          },
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey<String>('disposable-surface')));
    await tester.pump();

    expect(
      find.byKey(const ValueKey<String>('disposable-surface')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
}
