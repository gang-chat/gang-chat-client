import 'dart:async';

import 'package:client/src/ui/ui.dart' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('chat composer action can be highlighted externally', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            actions: [
              ui.ComposerAction(
                id: 'file',
                icon: Icons.attach_file,
                label: 'File',
                selected: true,
                onPressed: () {},
              ),
            ],
          ),
        ),
      ),
    );

    final button = tester.widget<ui.ButtonIcon>(
      find.byWidgetPredicate(
        (widget) => widget is ui.ButtonIcon && widget.tooltip == 'File',
      ),
    );
    expect(button.selected, isTrue);
  });

  testWidgets('chat composer reports paste shortcut from the input', (
    tester,
  ) async {
    var pasteAttempts = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            onPasteFiles: () async {
              pasteAttempts++;
              // Report "consumed" so the default text paste is suppressed,
              // matching a clipboard that holds a file.
              return true;
            },
            actions: const [
              ui.ComposerAction(
                id: 'file',
                icon: Icons.attach_file,
                label: 'File',
              ),
            ],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pump();

    expect(pasteAttempts, 1);
  });

  testWidgets('chat composer context menu shows paste for clipboard files', (
    tester,
  ) async {
    _mockClipboardText(null);
    var pasteAttempts = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            onPasteFiles: () async {
              pasteAttempts++;
              return true;
            },
            onCanPasteFiles: () async => true,
            actions: const [],
          ),
        ),
      ),
    );

    await _showComposerContextMenu(tester);

    expect(find.text('粘贴'), findsOneWidget);
    expect(find.text('Ctrl+V'), findsOneWidget);

    await tester.tap(find.text('粘贴'));
    await tester.pumpAndSettle();

    expect(pasteAttempts, 1);
  });

  testWidgets('chat composer context menu hides paste without text or files', (
    tester,
  ) async {
    _mockClipboardText(null);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            onPasteFiles: () async => false,
            onCanPasteFiles: () async => false,
            actions: const [],
          ),
        ),
      ),
    );

    await _showComposerContextMenu(tester);

    expect(find.text('粘贴'), findsNothing);
    expect(find.text('Ctrl+V'), findsNothing);
  });

  testWidgets(
    'chat composer context-menu paste is routed before async probe completes',
    (tester) async {
      _mockClipboardText('readable music component');
      var pasteAttempts = 0;
      final pendingProbe = Completer<bool>();

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
          home: Scaffold(
            body: ui.ChatComposer(
              onPasteFiles: () async {
                pasteAttempts++;
                return true;
              },
              onCanPasteFiles: () => pendingProbe.future,
              actions: const [],
            ),
          ),
        ),
      );

      await tester.tap(find.byType(TextField));
      await tester.pump();
      final editableTextState = tester.state<EditableTextState>(
        find.byType(EditableText),
      );
      expect(editableTextState.showToolbar(), isTrue);
      await tester.pump();

      expect(find.text('粘贴'), findsOneWidget);
      await tester.tap(find.text('粘贴'));
      await tester.pumpAndSettle();

      expect(pasteAttempts, 1);
      pendingProbe.complete(false);
      await tester.pump();
    },
  );

  testWidgets('chat composer sends focused input on Enter', (tester) async {
    final controller = TextEditingController(text: 'hello');
    addTearDown(controller.dispose);
    final submissions = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            controller: controller,
            onSubmitted: submissions.add,
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    await tester.sendKeyDownEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.enter);
    await tester.pump();

    expect(submissions, ['hello']);
    expect(controller.text, 'hello');
  });

  testWidgets('chat composer inserts newline on Shift Enter', (tester) async {
    final controller = TextEditingController(text: 'hello');
    addTearDown(controller.dispose);
    final submissions = <String>[];
    final changes = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            controller: controller,
            onSubmitted: submissions.add,
            onChanged: changes.add,
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    controller.selection = const TextSelection.collapsed(offset: 5);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await tester.pump();

    expect(submissions, isEmpty);
    expect(changes, ['hello\n']);
    expect(controller.text, 'hello\n');
    expect(controller.selection.baseOffset, 6);
  });

  testWidgets('chat composer inserts newline on Control Enter', (tester) async {
    final controller = TextEditingController(text: 'hello');
    addTearDown(controller.dispose);
    final submissions = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            controller: controller,
            onSubmitted: submissions.add,
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    controller.selection = const TextSelection(baseOffset: 1, extentOffset: 4);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pump();

    expect(submissions, isEmpty);
    expect(controller.text, 'h\no');
    expect(controller.selection.baseOffset, 2);
  });

  testWidgets('chat composer reports suggestion navigation keys', (
    tester,
  ) async {
    final navigation = <ui.ComposerSuggestionNavigation>[];
    final actions = <ui.ComposerSuggestionAction>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            suggestionShortcutsEnabled: true,
            onSuggestionNavigationPressed: (direction) {
              navigation.add(direction);
              return true;
            },
            onSuggestionActionPressed: (action) {
              actions.add(action);
              return true;
            },
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    await tester.sendKeyDownEvent(LogicalKeyboardKey.arrowDown);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.arrowDown);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.arrowUp);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.arrowUp);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.tab);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.tab);
    await tester.pump();

    final editableText = tester.widget<EditableText>(find.byType(EditableText));
    expect(editableText.focusNode.hasFocus, isTrue);
    expect(navigation, [
      ui.ComposerSuggestionNavigation.next,
      ui.ComposerSuggestionNavigation.previous,
    ]);
    expect(actions, [ui.ComposerSuggestionAction.confirm]);
  });

  testWidgets('chat composer leaves horizontal arrows for cursor movement', (
    tester,
  ) async {
    final controller = TextEditingController(text: 'hello');
    addTearDown(controller.dispose);
    final navigation = <ui.ComposerSuggestionNavigation>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            controller: controller,
            suggestionShortcutsEnabled: true,
            onSuggestionNavigationPressed: (direction) {
              navigation.add(direction);
              return true;
            },
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    controller.selection = const TextSelection.collapsed(offset: 3);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.arrowLeft);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.arrowLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.arrowRight);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.arrowRight);
    await tester.pump();

    expect(navigation, isEmpty);
  });

  testWidgets('chat composer keeps input focused when suggestions enable', (
    tester,
  ) async {
    final controller = TextEditingController(text: '@');
    final suggestionsEnabled = ValueNotifier(false);
    addTearDown(controller.dispose);
    addTearDown(suggestionsEnabled.dispose);

    Widget buildHost() {
      return MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ValueListenableBuilder<bool>(
            valueListenable: suggestionsEnabled,
            builder: (context, enabled, child) => ui.ChatComposer(
              controller: controller,
              suggestionShortcutsEnabled: enabled,
              onSuggestionNavigationPressed: (_) => true,
              onSuggestionActionPressed: (_) => true,
              actions: const [],
            ),
          ),
        ),
      );
    }

    await tester.pumpWidget(buildHost());
    await tester.tap(find.byType(TextField));
    await tester.pump();
    final before = tester.widget<EditableText>(find.byType(EditableText));
    expect(before.focusNode.hasFocus, isTrue);

    suggestionsEnabled.value = true;
    await tester.pump();
    final after = tester.widget<EditableText>(find.byType(EditableText));
    expect(after.focusNode.hasFocus, isTrue);

    await tester.enterText(find.byType(TextField), '@a');
    await tester.pump();
    expect(controller.text, '@a');
  });

  testWidgets('chat composer applies input formatters before text editing', (
    tester,
  ) async {
    final controller = TextEditingController(text: 'hello');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            controller: controller,
            inputFormatters: const [_BackspaceReplacementFormatter()],
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    controller.selection = const TextSelection.collapsed(offset: 5);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.backspace);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.backspace);
    await tester.pump();

    expect(controller.text, 'handled');
  });

  testWidgets('paste does not type clipboard text when a file is staged', (
    tester,
  ) async {
    // Simulate macOS putting a copied file's name on the clipboard as text.
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.getData') {
          return <String, dynamic>{'text': '/Users/me/report.pdf'};
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );

    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            controller: controller,
            // Returns true => paste was consumed as an attachment.
            onPasteFiles: () async => true,
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    expect(controller.text, isEmpty);
  });

  testWidgets('paste still inserts text when no file is staged', (
    tester,
  ) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.getData') {
          return <String, dynamic>{'text': 'hello world'};
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );

    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: ui.ChatComposer(
            controller: controller,
            // Returns false => nothing staged, fall back to default text paste.
            onPasteFiles: () async => false,
            actions: const [],
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    expect(controller.text, 'hello world');
  });
}

Future<void> _showComposerContextMenu(WidgetTester tester) async {
  await tester.tap(find.byType(TextField));
  await tester.pump();
  final editableTextState = tester.state<EditableTextState>(
    find.byType(EditableText),
  );
  expect(editableTextState.showToolbar(), isTrue);
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

void _mockClipboardText(String? text) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(SystemChannels.platform, (call) async {
        return switch (call.method) {
          'Clipboard.getData' =>
            text == null ? null : <String, dynamic>{'text': text},
          'Clipboard.hasStrings' => <String, dynamic>{
            'value': text != null && text.isNotEmpty,
          },
          'Clipboard.setData' => null,
          _ => null,
        };
      });
  addTearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });
}

class _BackspaceReplacementFormatter extends TextInputFormatter {
  const _BackspaceReplacementFormatter();

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    if (oldValue.text == 'hello' && newValue.text == 'hell') {
      return const TextEditingValue(
        text: 'handled',
        selection: TextSelection.collapsed(offset: 7),
      );
    }
    return newValue;
  }
}
