part of '../gang_app_shell_test.dart';

void registerShellUiShowcaseWidgetTests() {
  testWidgets('button lays out with automatic width in a row', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Row(
            children: [ui.Button(onPressed: () {}, child: const Text('Send'))],
          ),
        ),
      ),
    );

    expect(find.text('Send'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('full-width button uses finite parent width', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 240,
              child: ui.Button(
                width: double.infinity,
                onPressed: () {},
                child: const Text('Create'),
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.getSize(find.byType(ui.Button)).width, 240);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui button truncates inside narrow bounds', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: Column(
              children: [
                SizedBox(
                  width: 98,
                  child: ui.Button(
                    loading: true,
                    onPressed: () {},
                    child: const Text('Loading'),
                  ),
                ),
                SizedBox(
                  width: 98,
                  child: ui.Button(
                    icon: const Icon(Icons.extension_outlined),
                    onPressed: () {},
                    child: const Text('Command'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('ui button lays out with automatic width in a row', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Row(
            children: [ui.Button(onPressed: () {}, child: const Text('Send'))],
          ),
        ),
      ),
    );

    final size = tester.getSize(find.byType(ui.Button));

    expect(find.text('Send'), findsOneWidget);
    expect(size.width.isFinite, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui pressable surface lays out a single flat layer', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 120,
              child: ui.PressableSurface(
                height: 40,
                onPressed: () {},
                child: const Text('Surface'),
              ),
            ),
          ),
        ),
      ),
    );

    final surfaceFinder = find.byType(ui.PressableSurface);
    // No stacked cap/base layers any more: one decorated layer fills the
    // whole surface.
    expect(
      find.descendant(of: surfaceFinder, matching: find.byType(Positioned)),
      findsNothing,
    );
    final layerFinder = find.descendant(
      of: surfaceFinder,
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is DecoratedBox && widget.decoration is BoxDecoration,
      ),
    );
    expect(layerFinder, findsOneWidget);
    expect(tester.getSize(layerFinder), const Size(120, 40));
    expect(tester.getSize(surfaceFinder), const Size(120, 40));
    final decoration =
        tester.widget<DecoratedBox>(layerFinder).decoration as BoxDecoration;
    expect(decoration.color, ui.UiColors.surface);
    expect((decoration.border as Border).top.color, ui.UiColors.border);
    expect((decoration.border as Border).top.color.a, greaterThan(0));
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui kit segmented control does not use material ripple', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: ui.SegmentedControl<String>(
              value: 'chat',
              onChanged: (_) {},
              segments: const [
                ui.Segment(value: 'chat', label: 'Chat'),
                ui.Segment(value: 'forms', label: 'Forms'),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.byType(InkWell), findsNothing);
    expect(find.byType(ui.PressableSurface), findsNothing);
    expect(
      find.descendant(
        of: find.byType(ui.SegmentedControl<String>),
        matching: find.byType(GestureDetector),
      ),
      findsNWidgets(2),
    );
    // The selected segment sits on the inverse (white) thumb.
    expect(
      tester.widget<Text>(find.text('Chat')).style?.color,
      ui.UiColors.onInverse,
    );
    expect(
      tester.widget<Text>(find.text('Forms')).style?.color,
      ui.UiColors.textSecondary,
    );
    expect(
      tester.widget<Text>(find.text('Chat')).style?.fontWeight,
      FontWeight.w600,
    );
    expect(
      tester.widget<Text>(find.text('Forms')).style?.fontWeight,
      FontWeight.w500,
    );
    expect(
      tester.getSize(find.byType(ui.SegmentedControl<String>)).width,
      lessThan(192),
    );
    expect(
      tester.getSize(find.byType(ui.SegmentedControl<String>)).height,
      closeTo(36, 0.01),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui navigation tabs expose reusable segmented navigation', (
    WidgetTester tester,
  ) async {
    var selected = 'chat';

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) {
              return ui.NavigationTabs<String>(
                value: selected,
                onChanged: (value) => setState(() => selected = value),
                items: const [
                  ui.NavigationItem(
                    value: 'chat',
                    label: 'Chat',
                    icon: Icons.chat_bubble_outline,
                  ),
                  ui.NavigationItem(
                    value: 'forms',
                    label: 'Forms',
                    icon: Icons.tune,
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );

    expect(find.byType(ui.SegmentedControl<String>), findsOneWidget);
    expect(find.text('Chat'), findsOneWidget);
    expect(find.text('Forms'), findsOneWidget);

    await tester.tap(find.text('Forms'));
    await tester.pumpAndSettle();

    expect(selected, 'forms');
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui sidebar renders groups and changes selection', (
    WidgetTester tester,
  ) async {
    var selected = 'overview';

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) {
              return SizedBox(
                height: 320,
                child: ui.Sidebar(
                  width: 220,
                  selectedId: selected,
                  onItemSelected: (value) => setState(() => selected = value),
                  header: const Text(
                    'Navigation',
                    style: ui.UiTypography.title,
                  ),
                  footer: const Text('Footer', style: ui.UiTypography.label),
                  groups: const [
                    ui.SidebarGroup(
                      label: 'Workspace',
                      items: [
                        ui.SidebarItem(
                          id: 'overview',
                          label: 'Overview',
                          icon: Icons.space_dashboard_outlined,
                          badge: '4',
                        ),
                        ui.SidebarItem(
                          id: 'threads',
                          label: 'Threads',
                          icon: Icons.forum_outlined,
                        ),
                        ui.SidebarItem(
                          id: 'archive',
                          label: 'Archive',
                          icon: Icons.inventory_2_outlined,
                          enabled: false,
                        ),
                      ],
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );

    expect(tester.getSize(find.byType(ui.Sidebar)).width, 220);
    expect(find.text('Workspace'), findsOneWidget);
    expect(find.text('4'), findsOneWidget);
    expect(find.text('Footer'), findsOneWidget);
    expect(
      tester
          .widget<ui.PressableSurface>(
            find.ancestor(
              of: find.text('Overview'),
              matching: find.byType(ui.PressableSurface),
            ),
          )
          .selected,
      isTrue,
    );

    await tester.tap(find.text('Threads'));
    await tester.pump();

    expect(selected, 'threads');
    expect(
      tester
          .widget<ui.PressableSurface>(
            find.ancestor(
              of: find.text('Threads'),
              matching: find.byType(ui.PressableSurface),
            ),
          )
          .selected,
      isTrue,
    );

    await tester.tap(find.text('Archive'));
    await tester.pump();

    expect(selected, 'threads');
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui showcase narrow layout enters content from sidebar', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Center(
          child: SizedBox(
            width: 420,
            height: 760,
            child: showcase.UiShowcasePage(),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(ui.Sidebar), findsOneWidget);
    expect(find.text('Sections'), findsOneWidget);
    expect(find.text('Buttons'), findsNothing);
    expect(find.byTooltip('Show sections'), findsNothing);

    await tester.tap(find.text('Forms'));
    await tester.pump();

    expect(find.byType(ui.Sidebar), findsNothing);
    expect(find.byTooltip('Show sections'), findsOneWidget);
    expect(find.text('Fields'), findsOneWidget);

    await tester.tap(find.byTooltip('Show sections'));
    await tester.pump();

    expect(find.byType(ui.Sidebar), findsOneWidget);
    expect(find.text('Fields'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui input focuses from padding and grows upward', (
    WidgetTester tester,
  ) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 280,
              height: 120,
              child: Align(
                alignment: Alignment.bottomCenter,
                child: SizedBox(
                  width: 280,
                  child: ui.Input(
                    controller: controller,
                    focusNode: focusNode,
                    hintText: 'Type here',
                    prefixIcon: Icons.person_outline,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    final inputFinder = find.byType(ui.Input);
    final textFieldFinder = find.byType(TextField);
    expect(find.byType(ui.Input), findsOneWidget);
    expect(textFieldFinder, findsOneWidget);
    expect(
      find.descendant(
        of: inputFinder,
        matching: find.byType(ui.PressableSurface),
      ),
      findsNothing,
    );
    expect(
      tester.widget<TextField>(textFieldFinder).mouseCursor,
      SystemMouseCursors.text,
    );
    final textFieldPadding =
        tester.widget<TextField>(textFieldFinder).decoration?.contentPadding
            as EdgeInsets;
    expect(textFieldPadding.top, greaterThan(textFieldPadding.bottom));
    expect(textFieldPadding.top - textFieldPadding.bottom, closeTo(8, 0.01));
    expect(
      tester
          .widgetList<Transform>(
            find.ancestor(
              of: find.byIcon(Icons.person_outline),
              matching: find.byType(Transform),
            ),
          )
          .map((transform) => transform.transform.storage[13]),
      contains(closeTo(5, 0.01)),
    );

    final initialRect = tester.getRect(inputFinder);
    await tester.tapAt(initialRect.topLeft + const Offset(4, 6));
    await tester.pump();

    expect(focusNode.hasFocus, isTrue);

    focusNode.unfocus();
    await tester.pump();
    await tester.tap(find.byIcon(Icons.person_outline));
    await tester.pump();

    expect(focusNode.hasFocus, isTrue);

    final paddingPoints = [
      Offset(initialRect.center.dx, initialRect.top + 6),
      Offset(initialRect.center.dx, initialRect.bottom - 14),
      Offset(initialRect.center.dx, initialRect.top + 6),
      Offset(initialRect.right - 4, initialRect.top + 6),
      Offset(initialRect.right - 4, initialRect.center.dy),
      Offset(initialRect.right - 4, initialRect.bottom - 14),
    ];
    final mouse = await tester.createGesture(
      kind: PointerDeviceKind.mouse,
      pointer: 99,
    );
    addTearDown(mouse.removePointer);
    await mouse.addPointer(location: Offset.zero);
    await tester.pump();
    for (final point in paddingPoints) {
      await mouse.moveTo(point);
      await tester.pump();
      await mouse.down(point);
      await tester.pump();

      expect(focusNode.hasFocus, isTrue);

      await mouse.up();
      await tester.pump();
      await mouse.down(point);
      await tester.pump();

      expect(focusNode.hasFocus, isTrue);

      await mouse.up();
      await tester.pump();
    }

    await tester.enterText(find.byType(TextField), 'First line\nSecond line');
    await tester.pumpAndSettle();

    final multilineRect = tester.getRect(inputFinder);
    expect(multilineRect.height, greaterThan(initialRect.height));
    expect(multilineRect.bottom, closeTo(initialRect.bottom, 0.01));
    expect(multilineRect.top, lessThan(initialRect.top));
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui input hover and focus only restyle its flat frame', (
    WidgetTester tester,
  ) async {
    final focusNode = FocusNode();
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 240,
              child: ui.Input(focusNode: focusNode, hintText: 'Type here'),
            ),
          ),
        ),
      ),
    );

    final inputFinder = find.byType(ui.Input);
    // The flat input has no lifting cap; one animated frame carries the
    // hover and focus styles.
    expect(
      find.descendant(of: inputFinder, matching: find.byType(AnimatedPadding)),
      findsNothing,
    );
    final frameFinder = find.descendant(
      of: inputFinder,
      matching: find.byType(AnimatedContainer),
    );
    expect(frameFinder, findsOneWidget);
    final frame = tester.widget<AnimatedContainer>(frameFinder);
    expect(frame.duration, ui.UiMotion.fast);
    expect(frame.curve, ui.UiMotion.curve);
    final restingSize = tester.getSize(inputFinder);

    BoxDecoration frameDecoration() {
      final decorations = _inputLayerDecorations(tester, inputFinder);
      expect(decorations, hasLength(1));
      return decorations.single;
    }

    var decoration = frameDecoration();
    expect(_topBorderColor(decoration), ui.UiColors.fieldBorder);
    expect(decoration.color, ui.UiColors.fieldFill);
    expect(decoration.boxShadow, isNull);

    var inputRect = tester.getRect(inputFinder);
    await tester.tapAt(inputRect.center);
    await tester.pumpAndSettle();

    expect(focusNode.hasFocus, isTrue);
    decoration = frameDecoration();
    expect(_topBorderColor(decoration), ui.UiColors.accentStrong);
    expect(decoration.color, ui.UiColors.fieldFill);
    expect(decoration.boxShadow, isNotEmpty);
    expect(tester.getSize(inputFinder), restingSize);

    focusNode.unfocus();
    await tester.pumpAndSettle();

    decoration = frameDecoration();
    expect(_topBorderColor(decoration), ui.UiColors.fieldBorder);
    expect(decoration.color, ui.UiColors.fieldFill);
    expect(decoration.boxShadow, isNull);

    final hoverRegions = tester
        .widgetList<MouseRegion>(
          find.descendant(of: inputFinder, matching: find.byType(MouseRegion)),
        )
        .where((region) => region.onEnter != null);
    expect(hoverRegions, isNotEmpty);
    for (final region in hoverRegions) {
      region.onEnter?.call(const PointerEnterEvent(position: Offset.zero));
    }
    await tester.pumpAndSettle();

    // Hover only brightens the border; fill and size stay unchanged.
    decoration = frameDecoration();
    expect(_topBorderColor(decoration), ui.UiColors.fieldBorderHover);
    expect(decoration.color, ui.UiColors.fieldFill);
    expect(decoration.boxShadow, isNull);
    expect(tester.getSize(inputFinder), restingSize);

    inputRect = tester.getRect(inputFinder);
    await tester.tapAt(inputRect.center);
    await tester.pumpAndSettle();

    expect(focusNode.hasFocus, isTrue);
    decoration = frameDecoration();
    expect(_topBorderColor(decoration), ui.UiColors.accentStrong);
    expect(decoration.color, ui.UiColors.fieldFill);
    expect(decoration.boxShadow, isNotEmpty);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui input grows without an internal line cap', (
    WidgetTester tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 320,
              child: ui.Input(
                controller: controller,
                hintText: 'Description',
                prefixIcon: Icons.notes_outlined,
                maxLines: null,
              ),
            ),
          ),
        ),
      ),
    );

    TextField textField() =>
        tester.widget<TextField>(_textFieldWithHint('Description'));

    expect(textField().minLines, 1);
    expect(textField().maxLines, isNull);
    final initialHeight = tester.getSize(find.byType(ui.Input)).height;

    await tester.enterText(
      _textFieldWithHint('Description'),
      'One two three four five six seven eight nine ten eleven twelve '
      'thirteen fourteen fifteen sixteen seventeen eighteen.',
    );
    await tester.pumpAndSettle();

    expect(textField().maxLines, isNull);
    expect(
      tester.getSize(find.byType(ui.Input)).height,
      greaterThan(initialHeight * 2),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui button supports toggle mode', (WidgetTester tester) async {
    var enabled = false;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: StatefulBuilder(
              builder: (context, setState) {
                return ui.Button(
                  icon: const Icon(Icons.mic),
                  toggleValue: enabled,
                  onToggleChanged: (value) => setState(() => enabled = value),
                  child: const Text('Mic'),
                );
              },
            ),
          ),
        ),
      ),
    );

    expect(
      find.descendant(
        of: find.widgetWithText(ui.Button, 'Mic'),
        matching: find.byType(InkWell),
      ),
      findsNothing,
    );

    await tester.tap(find.text('Mic'));
    await tester.pump();

    expect(enabled, isTrue);
    final toggledButton = tester.widget<ui.PressableSurface>(
      find.descendant(
        of: find.widgetWithText(ui.Button, 'Mic'),
        matching: find.byType(ui.PressableSurface),
      ),
    );
    expect(toggledButton.selected, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui button activates after pointer moves inside bounds', (
    WidgetTester tester,
  ) async {
    var taps = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 160,
              child: ui.Button(
                width: double.infinity,
                onPressed: () => taps++,
                child: const Text('Select'),
              ),
            ),
          ),
        ),
      ),
    );

    final surfaceFinder = find.descendant(
      of: find.widgetWithText(ui.Button, 'Select'),
      matching: find.byType(ui.PressableSurface),
    );
    final rect = tester.getRect(surfaceFinder);
    final gesture = await tester.startGesture(
      rect.center,
      kind: PointerDeviceKind.mouse,
    );

    await tester.pump();
    await gesture.moveTo(rect.center + const Offset(34, 7));
    await tester.pump();
    await gesture.up();
    await tester.pump();

    expect(taps, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui checkbox toggles and keeps custom surface', (
    WidgetTester tester,
  ) async {
    var checked = false;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: StatefulBuilder(
              builder: (context, setState) {
                return ui.UiCheckbox(
                  value: checked,
                  tooltip: '记住密码',
                  onChanged: (value) => setState(() => checked = value),
                );
              },
            ),
          ),
        ),
      ),
    );

    final checkboxFinder = find.byType(ui.UiCheckbox);
    expect(checkboxFinder, findsOneWidget);
    expect(find.byType(Checkbox), findsNothing);
    expect(
      find.descendant(of: checkboxFinder, matching: find.byType(InkWell)),
      findsNothing,
    );
    expect(tester.getSize(checkboxFinder), const Size(18, 18));

    await tester.tap(find.byTooltip('记住密码'));
    await tester.pumpAndSettle();

    expect(checked, isTrue);
    final decorations = tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: checkboxFinder,
            matching: find.byType(DecoratedBox),
          ),
        )
        .where((box) => box.decoration is BoxDecoration)
        .map((box) => box.decoration as BoxDecoration)
        .toList();
    // Checked boxes fill with the lime signal colour.
    expect(
      decorations.any(
        (decoration) => decoration.color == ui.UiColors.accentStrong,
      ),
      isTrue,
    );
    expect(_rememberPasswordCheckIcon(tester).color, ui.UiColors.onAccent);

    await tester.tap(checkboxFinder);
    await tester.pumpAndSettle();

    expect(checked, isFalse);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: const Scaffold(
          body: Center(child: ui.UiCheckbox(value: false, onChanged: null)),
        ),
      ),
    );
    expect(_rememberPasswordCheckIcon(tester).color, Colors.transparent);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: const Scaffold(
          body: Center(child: ui.UiCheckbox(value: true, onChanged: null)),
        ),
      ),
    );
    expect(_rememberPasswordCheckIcon(tester).color, ui.UiColors.textSecondary);

    await tester.tap(checkboxFinder);
    await tester.pump();

    expect(tester.takeException(), isNull);
  });

  testWidgets('ui switch toggles and keeps custom surface', (
    WidgetTester tester,
  ) async {
    var enabled = false;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: StatefulBuilder(
              builder: (context, setState) {
                return ui.UiSwitch(
                  value: enabled,
                  onChanged: (value) => setState(() => enabled = value),
                );
              },
            ),
          ),
        ),
      ),
    );

    final switchFinder = find.byType(ui.UiSwitch);
    expect(switchFinder, findsOneWidget);
    expect(find.byType(Switch), findsNothing);
    expect(
      find.descendant(of: switchFinder, matching: find.byType(InkWell)),
      findsNothing,
    );
    expect(tester.getSize(switchFinder), const Size(42, 24));

    await tester.tap(switchFinder);
    await tester.pumpAndSettle();

    expect(enabled, isTrue);
    final decorations = tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: switchFinder,
            matching: find.byType(DecoratedBox),
          ),
        )
        .where((box) => box.decoration is BoxDecoration)
        .map((box) => box.decoration as BoxDecoration)
        .toList();
    // The track of an enabled switch fills with the lime signal colour.
    expect(
      decorations.any(
        (decoration) => decoration.color == ui.UiColors.accentStrong,
      ),
      isTrue,
    );

    final switchRect = tester.getRect(switchFinder);
    final gesture = await tester.startGesture(
      switchRect.center,
      kind: PointerDeviceKind.mouse,
    );
    await tester.pump();
    await gesture.moveTo(switchRect.center + const Offset(12, 4));
    await tester.pump();
    await gesture.up();
    await tester.pumpAndSettle();

    expect(enabled, isFalse);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: const Scaffold(
          body: Center(child: ui.UiSwitch(value: true, onChanged: null)),
        ),
      ),
    );
    await tester.tap(switchFinder);
    await tester.pump();

    expect(tester.takeException(), isNull);
  });

  testWidgets('ui kit chat composer switches list and static panels', (
    WidgetTester tester,
  ) async {
    var sends = 0;
    final pageScrollController = ScrollController();
    addTearDown(pageScrollController.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: SingleChildScrollView(
            controller: pageScrollController,
            child: Padding(
              padding: const EdgeInsets.only(top: 80, bottom: 900),
              child: Center(
                child: SizedBox(
                  width: 560,
                  child: ui.ChatComposer(
                    hintText: 'Message',
                    actions: [
                      ui.ComposerAction(
                        id: 'stickers',
                        icon: Icons.emoji_emotions_outlined,
                        label: 'Stickers',
                        panel: ui.ComposerPanel.list(
                          itemCount: 24,
                          itemBuilder: (context, index) {
                            return SizedBox(
                              width: 84,
                              height: 76,
                              child: Center(child: Text('Sticker $index')),
                            );
                          },
                        ),
                      ),
                      const ui.ComposerAction(
                        id: 'voice',
                        icon: Icons.mic_none,
                        label: 'Voice',
                        panel: ui.ComposerPanel.static(
                          child: Center(child: Text('Voice panel')),
                        ),
                      ),
                      ui.ComposerAction(
                        id: 'send',
                        icon: Icons.send_rounded,
                        label: 'Send',
                        alignment: ui.ComposerActionAlignment.trailing,
                        onPressed: () => sends++,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    final inputFinder = find.byType(ui.Input);

    expect(find.text('Sticker 0'), findsNothing);
    expect(find.text('Voice panel'), findsNothing);
    expect(inputFinder, findsOneWidget);
    expect(
      find.descendant(
        of: inputFinder,
        matching: find.byType(ui.PressableSurface),
      ),
      findsNothing,
    );

    final sendSurfaceFinder = find.ancestor(
      of: find.byIcon(Icons.send_rounded),
      matching: find.byType(ui.PressableSurface),
    );
    expect(sendSurfaceFinder, findsOneWidget);
    final stickerSurfaceFinder = find.ancestor(
      of: find.byIcon(Icons.emoji_emotions_outlined),
      matching: find.byType(ui.PressableSurface),
    );
    expect(stickerSurfaceFinder, findsOneWidget);

    final composerRect = tester.getRect(find.byType(ui.ChatComposer));
    final initialInputRect = tester.getRect(inputFinder);
    final stickerRect = tester.getRect(stickerSurfaceFinder);
    final voiceRect = tester.getRect(
      find.ancestor(
        of: find.byIcon(Icons.mic_none),
        matching: find.byType(ui.PressableSurface),
      ),
    );
    final sendRect = tester.getRect(sendSurfaceFinder);
    expect(composerRect.width, closeTo(560, 0.01));
    // One pill row: leading actions, the input filling the middle, and the
    // trailing send button.
    expect(stickerRect.left, closeTo(composerRect.left + 7, 0.01));
    expect(voiceRect.left, greaterThanOrEqualTo(stickerRect.right));
    expect(initialInputRect.left, greaterThan(voiceRect.right));
    expect(sendRect.left, greaterThanOrEqualTo(initialInputRect.right));
    expect(sendRect.right, closeTo(composerRect.right - 7, 0.01));
    expect(initialInputRect.width, greaterThan(composerRect.width / 2));
    for (final rect in [stickerRect, voiceRect, sendRect]) {
      expect(rect.bottom, closeTo(initialInputRect.bottom, 0.01));
    }
    expect(
      ui.Input.defaultHeight,
      tester.widget<ui.PressableSurface>(sendSurfaceFinder).height,
    );
    expect(initialInputRect.height, closeTo(ui.Input.defaultHeight, 0.01));
    expect(
      tester.widget<TextField>(find.byType(TextField)).textAlignVertical,
      TextAlignVertical.center,
    );
    expect(
      tester.widget<TextField>(find.byType(TextField)).mouseCursor,
      SystemMouseCursors.text,
    );

    // The composer input is bare: the composer pill itself is the field
    // frame and marks focus with the accent border.
    expect(
      find.descendant(
        of: inputFinder,
        matching: find.byType(AnimatedContainer),
      ),
      findsNothing,
    );
    expect(
      find.descendant(
        of: inputFinder,
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is MouseRegion && widget.cursor == SystemMouseCursors.text,
        ),
      ),
      findsWidgets,
    );
    Color composerBorderColor() {
      final frame = tester.widget<AnimatedContainer>(
        find
            .descendant(
              of: find.byType(ui.ChatComposer),
              matching: find.byType(AnimatedContainer),
            )
            .first,
      );
      return ((frame.decoration! as BoxDecoration).border! as Border).top.color;
    }

    expect(composerBorderColor(), ui.UiColors.border);

    final inputRect = tester.getRect(inputFinder);
    await tester.tapAt(inputRect.topLeft + const Offset(4, 4));
    await tester.pump();

    expect(
      tester.widget<TextField>(find.byType(TextField)).focusNode?.hasFocus,
      isTrue,
    );
    await tester.pump(const Duration(milliseconds: 200));
    expect(composerBorderColor(), ui.UiColors.accentBorder);

    await tester.enterText(find.byType(TextField), 'First line\nSecond line');
    await tester.pumpAndSettle();

    final multilineInputRect = tester.getRect(inputFinder);
    final multilineSendRect = tester.getRect(sendSurfaceFinder);
    // The input grows taller while send stays beside it, pinned to the
    // bottom of the row.
    expect(multilineInputRect.height, greaterThan(multilineSendRect.height));
    expect(
      multilineSendRect.left,
      greaterThanOrEqualTo(multilineInputRect.right),
    );
    expect(multilineSendRect.bottom, closeTo(multilineInputRect.bottom, 0.01));

    await tester.enterText(find.byType(TextField), '');
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Stickers'));
    await tester.pumpAndSettle();

    expect(find.text('Sticker 0'), findsOneWidget);
    expect(find.text('Sticker 8'), findsOneWidget);
    expect(find.text('Voice panel'), findsNothing);
    expect(pageScrollController.offset, 0);
    final stickerScrollerFinder = find.descendant(
      of: find.byType(ui.ChatComposer),
      matching: find.byType(SingleChildScrollView),
    );
    expect(stickerScrollerFinder, findsOneWidget);
    expect(tester.getSize(stickerScrollerFinder).height, closeTo(194, 0.01));
    expect(
      find.descendant(
        of: find.byType(ui.ChatComposer),
        matching: find.byType(Wrap),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byType(ui.ChatComposer),
        matching: find.byType(ListView),
      ),
      findsNothing,
    );

    await tester.tap(find.byTooltip('Voice'));
    await tester.pumpAndSettle();

    expect(find.text('Sticker 0'), findsNothing);
    expect(find.text('Voice panel'), findsOneWidget);

    await tester.tap(find.byTooltip('Send'));
    await tester.pumpAndSettle();

    expect(sends, 1);
    expect(find.text('Voice panel'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui kit chat composer list panel shrinks to one row', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 560,
              child: ui.ChatComposer(
                hintText: 'Message',
                actions: [
                  ui.ComposerAction(
                    id: 'stickers',
                    icon: Icons.emoji_emotions_outlined,
                    label: 'Stickers',
                    panel: ui.ComposerPanel.list(
                      itemCount: 3,
                      itemBuilder: (context, index) {
                        return SizedBox(
                          width: 84,
                          height: 76,
                          child: Center(child: Text('Sticker $index')),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byTooltip('Stickers'));
    await tester.pumpAndSettle();

    final stickerScrollerFinder = find.descendant(
      of: find.byType(ui.ChatComposer),
      matching: find.byType(SingleChildScrollView),
    );
    expect(stickerScrollerFinder, findsOneWidget);
    expect(tester.getSize(stickerScrollerFinder).height, closeTo(76, 0.01));
    expect(find.text('Sticker 2'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui kit anchored panel opens above its anchor', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: ui.AnchoredPanelAnchor(
              width: 240,
              anchor: (context, open, toggle) => ui.Button(
                selected: open,
                onPressed: toggle,
                child: const Text('Open'),
              ),
              panel: const Padding(
                padding: EdgeInsets.all(12),
                child: Text('Mock panel content'),
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.text('Mock panel content'), findsNothing);

    await tester.tap(find.text('Open'));
    await tester.pump();

    expect(find.text('Mock panel content'), findsOneWidget);

    final anchorRect = tester.getRect(find.widgetWithText(ui.Button, 'Open'));
    final panelRect = tester.getRect(find.byType(ui.AnchoredPanel));

    expect(panelRect.center.dx, closeTo(anchorRect.center.dx, 0.01));
    expect(anchorRect.top - panelRect.bottom, closeTo(8, 0.01));

    await tester.tapAt(const Offset(8, 8));
    await tester.pump();

    expect(find.text('Mock panel content'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('ui kit anchored panel stays pinned in a scrolled layout', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: SizedBox(
            height: 420,
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(28, 28, 28, 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 360),
                  Row(
                    children: [
                      const Expanded(child: Text('Scrolled layout anchor')),
                      ui.AnchoredPanelAnchor(
                        width: 240,
                        anchor: (context, open, toggle) => ui.Button(
                          selected: open,
                          onPressed: toggle,
                          child: const Text('Scrolled panel'),
                        ),
                        panel: const Padding(
                          padding: EdgeInsets.all(12),
                          child: Text('Scrolled panel content'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 520),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    await tester.ensureVisible(find.text('Scrolled panel'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Scrolled panel'));
    await tester.pump();

    final anchorRect = tester.getRect(
      find.widgetWithText(ui.Button, 'Scrolled panel'),
    );
    final panelRect = tester.getRect(find.byType(ui.AnchoredPanel));

    expect(panelRect.center.dx, closeTo(anchorRect.center.dx, 0.01));
    expect(panelRect.top - anchorRect.bottom, closeTo(8, 0.01));
    expect(panelRect.top, greaterThanOrEqualTo(0));
    expect(tester.takeException(), isNull);
  });

  testWidgets('loading buttons keep tone colors without tapping', (
    WidgetTester tester,
  ) async {
    var taps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              ui.Button(
                loading: true,
                tone: ui.ButtonTone.primary,
                height: 40,
                onPressed: () => taps++,
                child: const Text('Loading'),
              ),
              ui.ButtonIcon(
                loading: true,
                tone: ui.ButtonTone.danger,
                onPressed: () => taps++,
                tooltip: 'Call',
                icon: const Icon(Icons.call),
              ),
            ],
          ),
        ),
      ),
    );

    final surfaces = tester.widgetList<ui.PressableSurface>(
      find.byType(ui.PressableSurface),
    );

    expect(
      surfaces.map((surface) => surface.backgroundColor),
      containsAllInOrder([ui.UiColors.accentStrong, ui.UiColors.dangerSurface]),
    );
    expect(
      surfaces.map((surface) => surface.borderColor),
      containsAllInOrder([Colors.transparent, ui.UiColors.dangerBorder]),
    );
    expect(surfaces.map((surface) => surface.enabled), everyElement(isTrue));
    expect(surfaces.map((surface) => surface.onPressed), everyElement(isNull));
    expect(find.byIcon(Icons.call), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.tap(find.text('Loading'));
    await tester.tap(find.byTooltip('Call'));

    expect(taps, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('loading button replaces only an existing leading icon', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              ui.Button(
                loading: true,
                icon: const Icon(Icons.call),
                onPressed: () {},
                child: const Text('Join'),
              ),
              ui.Button(
                loading: true,
                onPressed: () {},
                child: const Text('Wait'),
              ),
            ],
          ),
        ),
      ),
    );

    final join = find.widgetWithText(ui.Button, 'Join');
    final wait = find.widgetWithText(ui.Button, 'Wait');
    expect(
      find.descendant(of: join, matching: find.byIcon(Icons.call)),
      findsNothing,
    );
    expect(
      find.descendant(
        of: join,
        matching: find.byType(CircularProgressIndicator),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: wait,
        matching: find.byType(CircularProgressIndicator),
      ),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  Color? surfaceFill(WidgetTester tester, Finder surface) {
    final container = tester.widget<AnimatedContainer>(
      find.descendant(of: surface, matching: find.byType(AnimatedContainer)),
    );
    return (container.decoration as BoxDecoration?)?.color;
  }

  testWidgets('flat surface tints on hover and occupies only its height', (
    WidgetTester tester,
  ) async {
    const base = Color(0xFF334455);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: ui.PressableSurface(
              width: 120,
              height: 40,
              backgroundColor: base,
              onPressed: () {},
              child: const Text('Surface'),
            ),
          ),
        ),
      ),
    );

    final surfaceFinder = find.byType(ui.PressableSurface);
    expect(tester.getSize(surfaceFinder), const Size(120, 40));
    expect(surfaceFill(tester, surfaceFinder), base);

    final mouseRegion = tester.widget<MouseRegion>(
      find.descendant(of: surfaceFinder, matching: find.byType(MouseRegion)),
    );
    mouseRegion.onEnter?.call(const PointerEnterEvent(position: Offset.zero));
    await tester.pump();

    expect(surfaceFill(tester, surfaceFinder), ui.UiStates.hover(base));
    expect(tester.getSize(surfaceFinder), const Size(120, 40));
    expect(tester.takeException(), isNull);
  });

  testWidgets('pressed surface darkens while held', (
    WidgetTester tester,
  ) async {
    const base = Color(0xFF334455);
    var pressed = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: ui.PressableSurface(
              width: 120,
              height: 40,
              backgroundColor: base,
              onPressed: () => pressed += 1,
              child: const Text('Surface'),
            ),
          ),
        ),
      ),
    );

    final surfaceFinder = find.byType(ui.PressableSurface);
    final gesture = await tester.startGesture(tester.getCenter(surfaceFinder));
    await tester.pump();

    expect(surfaceFill(tester, surfaceFinder), ui.UiStates.pressed(base));

    await gesture.up();
    await tester.pump();

    expect(pressed, 1);
    expect(surfaceFill(tester, surfaceFinder), base);
    expect(tester.takeException(), isNull);
  });

  testWidgets('button tones use the signal palette', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              ui.Button(
                tone: ui.ButtonTone.primary,
                onPressed: () {},
                child: const Text('Primary'),
              ),
              ui.Button(
                tone: ui.ButtonTone.danger,
                onPressed: () {},
                child: const Text('Danger'),
              ),
              ui.Button(
                selected: true,
                onPressed: () {},
                child: const Text('Selected'),
              ),
            ],
          ),
        ),
      ),
    );

    Color? fillOf(String label) => surfaceFill(
      tester,
      find.ancestor(
        of: find.text(label),
        matching: find.byType(ui.PressableSurface),
      ),
    );

    expect(fillOf('Primary'), ui.UiColors.accentStrong);
    expect(fillOf('Danger'), ui.UiColors.dangerSurface);
    expect(fillOf('Selected'), ui.UiColors.inverse);
    expect(tester.takeException(), isNull);
  });

  testWidgets('embedded settings page exposes a back button', (
    WidgetTester tester,
  ) async {
    var closeCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(isSubWindow: true, onClose: () => closeCount += 1),
      ),
    );
    await tester.pump();

    expect(find.byTooltip('返回'), findsOneWidget);

    await tester.tap(find.byTooltip('返回'));
    await tester.pump();

    expect(closeCount, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('settings scaffold keeps main header in the old left layout', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(640, 480);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: SettingsPage(isSubWindow: true, onClose: () {}),
      ),
    );
    await tester.pump();

    final titleRect = tester.getRect(find.text('设置'));
    expect(titleRect.left, lessThan(140));
    expect(tester.takeException(), isNull);
  });

  testWidgets('segmented control centers labels inside each segment', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(300, 120);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Center(
          child: SizedBox(
            width: 300,
            child: ui.SegmentedControl<int>(
              expanded: true,
              value: 0,
              onChanged: (_) {},
              segments: const [
                ui.Segment(value: 0, label: 'A'),
                ui.Segment(value: 1, label: 'B'),
                ui.Segment(value: 2, label: 'C'),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    for (final label in ['A', 'B', 'C']) {
      final segment = find.ancestor(
        of: find.text(label),
        matching: find.byType(GestureDetector),
      );
      expect(
        tester.getRect(find.text(label)).center.dx,
        closeTo(tester.getRect(segment).center.dx, 1),
      );
    }
    expect(tester.takeException(), isNull);
  });
}
