import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:window_manager/window_manager.dart';

import 'src/ui/ui.dart';
import 'src/config/host_platform.dart';

const _showcaseWindowSize = Size(1120, 760);
const _showcaseMinWindowSize = Size(390, 560);
const _unboundedWindowSize = Size(100000, 100000);
const _showcaseNarrowBreakpoint = 720.0;

bool get _supportsDesktopWindowManagement =>
    !kIsWeb &&
    (HostPlatform.isMacOS || HostPlatform.isWindows || HostPlatform.isLinux);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (_supportsDesktopWindowManagement) {
    await windowManager.ensureInitialized();
    await windowManager.waitUntilReadyToShow(
      const WindowOptions(
        size: _showcaseWindowSize,
        minimumSize: _showcaseMinWindowSize,
        backgroundColor: UiColors.background,
        center: true,
        title: 'Gang UI Kit',
      ),
      () async {
        await windowManager.setResizable(true);
        await windowManager.setMinimumSize(_showcaseMinWindowSize);
        await windowManager.setMaximumSize(_unboundedWindowSize);
        await windowManager.setSize(_showcaseWindowSize);
        await windowManager.setAlignment(Alignment.center);
        await windowManager.setOpacity(1);
        await windowManager.show();
        await windowManager.focus();
      },
    );
  }
  runApp(const UiShowcaseApp());
}

class UiShowcaseApp extends StatelessWidget {
  const UiShowcaseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Gang UI Kit',
      theme: uiTheme(),
      home: const UiShowcasePage(),
    );
  }
}

class UiShowcasePage extends StatefulWidget {
  const UiShowcasePage({super.key});

  @override
  State<UiShowcasePage> createState() => _UiShowcasePageState();
}

class _UiShowcasePageState extends State<UiShowcasePage> {
  bool _mic = true;
  bool _camera = false;
  bool _share = false;
  bool _desktopNotifications = true;
  bool _compactComposer = false;
  String _largeAction = 'focus';
  String _section = 'chat';
  String _navigationPreview = 'chat';
  bool _narrowContentOpen = false;
  bool _toastVisible = false;
  final TextEditingController _nameController = TextEditingController(
    text: 'Gang Chat',
  );
  final TextEditingController _composerController = TextEditingController();

  @override
  void dispose() {
    _nameController.dispose();
    _composerController.dispose();
    super.dispose();
  }

  void _showToast() {
    setState(() => _toastVisible = true);
    Future<void>.delayed(const Duration(seconds: 2), () {
      if (mounted) setState(() => _toastVisible = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: UiColors.background,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final narrow = constraints.maxWidth < _showcaseNarrowBreakpoint;
          final body = narrow
              ? (_narrowContentOpen
                    ? _buildNarrowContentPage(context)
                    : _buildSidebarPane(
                        width: constraints.maxWidth,
                        openContentOnSelect: true,
                      ))
              : Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildSidebarPane(width: 248, openContentOnSelect: false),
                    Expanded(
                      child: _buildContentScroll(
                        context,
                        padding: const EdgeInsets.fromLTRB(28, 28, 28, 40),
                      ),
                    ),
                  ],
                );

          return Stack(
            children: [
              body,
              if (_toastVisible)
                Positioned(
                  top: 24,
                  left: 0,
                  right: 0,
                  child: Center(
                    child: Toast(
                      icon: Icons.check_circle_outline,
                      message:
                          'UI kit toast is rendering from the showcase entry.',
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildSidebarPane({
    required double width,
    required bool openContentOnSelect,
  }) {
    return SizedBox(
      width: width,
      child: Sidebar(
        width: width,
        padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
        selectedId: _section,
        onItemSelected: (value) {
          setState(() {
            _section = value;
            if (openContentOnSelect) _narrowContentOpen = true;
          });
        },
        header: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Gang UI Kit', style: UiTypography.title),
            SizedBox(height: 18),
            _PaletteStrip(),
          ],
        ),
        groups: const [
          SidebarGroup(
            label: 'Sections',
            items: [
              SidebarItem(
                id: 'chat',
                label: 'Chat',
                icon: Icons.chat_bubble_outline,
              ),
              SidebarItem(id: 'forms', label: 'Forms', icon: Icons.tune),
            ],
          ),
        ],
        footer: const Row(
          children: [
            Avatar(label: 'Kai', active: true),
            SizedBox(width: 10),
            Expanded(
              child: Text(
                'Showcase entry',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.label,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNarrowContentPage(BuildContext context) {
    return Column(
      children: [
        DecoratedBox(
          decoration: const BoxDecoration(
            color: UiColors.surfaceLow,
            border: Border(bottom: BorderSide(color: UiColors.border)),
          ),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 16, 10),
            child: Row(
              children: [
                ButtonIcon(
                  tooltip: 'Show sections',
                  icon: const Icon(Icons.arrow_back),
                  onPressed: () => setState(() => _narrowContentOpen = false),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _sectionTitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.title,
                  ),
                ),
              ],
            ),
          ),
        ),
        Expanded(
          child: _buildContentScroll(
            context,
            padding: const EdgeInsets.fromLTRB(20, 22, 20, 40),
          ),
        ),
      ],
    );
  }

  Widget _buildContentScroll(
    BuildContext context, {
    required EdgeInsetsGeometry padding,
  }) {
    return SingleChildScrollView(
      padding: padding,
      child: _section == 'chat'
          ? _buildChatSection(context)
          : _buildFormsSection(context),
    );
  }

  String get _sectionTitle => _section == 'forms' ? 'Forms' : 'Chat';

  Widget _buildChatSection(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionTitle('Navigation'),
        _buildNavigationShowcase(context),
        const SizedBox(height: 30),
        const _SectionTitle('Buttons'),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            Button(
              tone: ButtonTone.primary,
              icon: const Icon(Icons.send_rounded),
              onPressed: _showToast,
              child: const Text('Send'),
            ),
            Button(
              icon: const Icon(Icons.add),
              onPressed: () {},
              child: const Text('Create room'),
            ),
            Button(
              tone: ButtonTone.danger,
              icon: const Icon(Icons.logout),
              onPressed: () {},
              child: const Text('Leave'),
            ),
            Button(
              loading: true,
              onPressed: () {},
              child: const Text('Loading'),
            ),
          ],
        ),
        const SizedBox(height: 30),
        const _SectionTitle('Button Cards'),
        _buildButtonCardShowcase(context),
        const SizedBox(height: 30),
        const _SectionTitle('Icon Buttons'),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            ButtonIcon(
              tooltip: 'Emoji',
              selected: true,
              onPressed: () {},
              icon: const Icon(Icons.emoji_emotions_outlined),
            ),
            ButtonIcon(
              tooltip: 'Attach',
              onPressed: () {},
              icon: const Icon(Icons.attach_file),
            ),
            ButtonIcon(
              tooltip: 'Delete',
              tone: ButtonTone.danger,
              onPressed: () {},
              icon: const Icon(Icons.delete_outline),
            ),
          ],
        ),
        const SizedBox(height: 30),
        const _SectionTitle('Composer Bar'),
        _buildComposerShowcase(context),
        const SizedBox(height: 30),
        const _SectionTitle('Anchored Panel'),
        Row(
          children: [
            const Expanded(
              child: Text(
                'Anchored panel with a compact surface, edge-aware placement, and outside-tap close.',
                style: UiTypography.body,
              ),
            ),
            const SizedBox(width: 16),
            AnchoredPanelAnchor(
              width: 300,
              anchor: (context, open, toggle) => Button(
                icon: const Icon(Icons.auto_awesome_outlined),
                selected: open,
                onPressed: toggle,
                child: const Text('Open panel'),
              ),
              panel: const _MockAnchoredPanel(),
            ),
          ],
        ),
        const SizedBox(height: 30),
        const _SectionTitle('Live Controls'),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            Button(
              icon: const Icon(Icons.mic),
              toggleValue: _mic,
              onToggleChanged: (value) => setState(() => _mic = value),
              child: const Text('Mic'),
            ),
            Button(
              icon: const Icon(Icons.videocam),
              toggleValue: _camera,
              onToggleChanged: (value) => setState(() => _camera = value),
              child: const Text('Camera'),
            ),
            Button(
              icon: const Icon(Icons.screen_share),
              toggleValue: _share,
              onToggleChanged: (value) => setState(() => _share = value),
              child: const Text('Share'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildNavigationShowcase(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 320),
      child: NavigationTabs<String>(
        value: _navigationPreview,
        onChanged: (value) => setState(() => _navigationPreview = value),
        items: const [
          NavigationItem(
            value: 'chat',
            label: 'Chat',
            icon: Icons.chat_bubble_outline,
          ),
          NavigationItem(value: 'forms', label: 'Forms', icon: Icons.tune),
        ],
      ),
    );
  }

  Widget _buildComposerShowcase(BuildContext context) {
    const stickerIcons = [
      Icons.sentiment_satisfied_alt,
      Icons.waving_hand_outlined,
      Icons.auto_awesome,
      Icons.local_fire_department_outlined,
      Icons.coffee_outlined,
      Icons.celebration_outlined,
      Icons.favorite_border,
      Icons.lightbulb_outline,
      Icons.bolt_outlined,
      Icons.nightlight_round,
      Icons.public_outlined,
      Icons.workspace_premium_outlined,
    ];
    const stickerLabels = [
      'Smile',
      'Wave',
      'Spark',
      'Fire',
      'Coffee',
      'Party',
      'Heart',
      'Idea',
      'Fast',
      'Night',
      'World',
      'Win',
    ];
    const stickerColors = [
      UiColors.accent,
      UiColors.violet,
      UiColors.amber,
      UiColors.danger,
    ];

    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 860),
      child: ChatComposer(
        controller: _composerController,
        hintText: 'Write a message',
        actions: [
          ComposerAction(
            id: 'stickers',
            icon: Icons.emoji_emotions_outlined,
            label: 'Stickers',
            panel: ComposerPanel.list(
              itemCount: stickerIcons.length,
              itemBuilder: (context, index) {
                return _StickerPanelItem(
                  icon: stickerIcons[index],
                  label: stickerLabels[index],
                  color: stickerColors[index % stickerColors.length],
                );
              },
            ),
          ),
          const ComposerAction(
            id: 'voice',
            icon: Icons.mic_none,
            label: 'Voice',
            panel: ComposerPanel.static(child: _VoicePanelPreview()),
          ),
          ComposerAction(
            id: 'send',
            icon: Icons.send_rounded,
            label: 'Send',
            tone: ButtonTone.primary,
            onPressed: _showToast,
          ),
        ],
      ),
    );
  }

  Widget _buildButtonCardShowcase(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 680;
        final cardWidth = wide
            ? (constraints.maxWidth - 12) / 2
            : constraints.maxWidth;
        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            SizedBox(
              width: cardWidth,
              child: _LargeActionButton(
                icon: Icons.headset_mic_outlined,
                title: 'Focus room',
                subtitle: 'Voice, notes, and pinned files',
                badge: 'Live',
                stats: const ['8 members', '3 threads', '24 files'],
                footer: 'Room quality 98%',
                selected: _largeAction == 'focus',
                onPressed: () => setState(() => _largeAction = 'focus'),
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: _LargeActionButton(
                icon: Icons.dashboard_customize_outlined,
                title: 'Project board',
                subtitle: 'Tasks, decisions, and room activity',
                badge: 'Draft',
                stats: const ['12 tasks', '5 owners', '2 blockers'],
                footer: 'Updated 11 min ago',
                selected: _largeAction == 'board',
                onPressed: () => setState(() => _largeAction = 'board'),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildFormsSection(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionTitle('Fields'),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Column(
            children: [
              TextInput(
                controller: _nameController,
                label: 'Room name',
                prefixIcon: Icons.tag,
              ),
              const SizedBox(height: 16),
              const TextInput(
                label: 'Message',
                hint: 'Write a short preview message',
                prefixIcon: Icons.edit_outlined,
                minLines: 3,
                maxLines: 3,
              ),
            ],
          ),
        ),
        const SizedBox(height: 30),
        const _SectionTitle('Switches'),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Column(
            children: [
              _SwitchRow(
                icon: Icons.notifications_active_outlined,
                title: 'Desktop notifications',
                subtitle: 'Surface new mentions while the app is backgrounded',
                value: _desktopNotifications,
                onChanged: (value) =>
                    setState(() => _desktopNotifications = value),
              ),
              const SizedBox(height: 12),
              _SwitchRow(
                icon: Icons.vertical_align_center_outlined,
                title: 'Compact composer',
                subtitle: 'Keep the message bar dense in smaller rooms',
                value: _compactComposer,
                onChanged: (value) => setState(() => _compactComposer = value),
              ),
            ],
          ),
        ),
        const SizedBox(height: 30),
        const _SectionTitle('Badges'),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: const [
            StatusBadge(label: 'Online', icon: Icons.circle, active: true),
            StatusBadge(label: 'Muted', icon: Icons.mic_off),
            StatusBadge(label: 'Blocked', icon: Icons.block, danger: true),
          ],
        ),
        const SizedBox(height: 30),
        const _SectionTitle('Dialog'),
        Button(
          icon: const Icon(Icons.open_in_new),
          onPressed: () {
            showUiDialog(
              context,
              title: 'Dialog frame',
              icon: Icons.info_outline,
              child: const Text(
                'This dialog uses the UI kit frame and can be reused by settings, stickers, and live controls.',
                style: UiTypography.body,
              ),
            );
          },
          child: const Text('Open dialog'),
        ),
      ],
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Text(title, style: UiTypography.title),
    );
  }
}

class _SwitchRow extends StatelessWidget {
  const _SwitchRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final foreground = value ? UiColors.text : UiColors.textSecondary;
    final iconColor = value ? UiColors.accent : UiColors.textMuted;
    return PressableSurface(
      height: 68,
      selected: value,
      backgroundColor: UiColors.surfaceLow,
      selectedBackgroundColor: UiColors.selected,
      borderColor: UiColors.border,
      selectedBorderColor: UiColors.accentBorder,
      borderRadius: UiRadii.md,
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      child: Row(
        children: [
          Icon(icon, color: iconColor, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: foreground,
                    fontSize: 13,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  subtitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: UiColors.textMuted,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 14),
          UiSwitch(value: value, onChanged: onChanged, tooltip: title),
        ],
      ),
    );
  }
}

class _LargeActionButton extends StatelessWidget {
  const _LargeActionButton({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.badge,
    required this.stats,
    required this.footer,
    required this.selected,
    required this.onPressed,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String badge;
  final List<String> stats;
  final String footer;
  final bool selected;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final foreground = selected ? UiColors.text : UiColors.textSecondary;
    final muted = selected ? UiColors.accent : UiColors.textMuted;

    return PressableSurface(
      height: 152,
      onPressed: onPressed,
      selected: selected,
      backgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.selected,
      borderColor: UiColors.border,
      selectedBorderColor: UiColors.accentBorder,
      borderRadius: UiRadii.md,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              DecoratedBox(
                decoration: BoxDecoration(
                  color: selected
                      ? UiColors.accent.withValues(alpha: 0.14)
                      : UiColors.surfacePressed,
                  borderRadius: BorderRadius.circular(UiRadii.md),
                  border: Border.all(
                    color: selected
                        ? UiColors.accentBorder
                        : UiColors.borderStrong,
                  ),
                ),
                child: SizedBox.square(
                  dimension: 36,
                  child: Icon(icon, color: muted, size: 20),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: foreground,
                        fontSize: 15,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: selected
                            ? UiColors.textSecondary
                            : UiColors.textMuted,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              _CardBadge(label: badge, selected: selected),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final stat in stats)
                _MetricPill(label: stat, selected: selected),
            ],
          ),
          const Spacer(),
          Row(
            children: [
              Icon(
                selected ? Icons.check_circle_outline : Icons.circle_outlined,
                color: muted,
                size: 16,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  footer,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: selected ? UiColors.accent : UiColors.textMuted,
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StickerPanelItem extends StatelessWidget {
  const _StickerPanelItem({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 88,
      child: PressableSurface(
        height: 76,
        onPressed: () {},
        padding: const EdgeInsets.all(10),
        backgroundColor: UiColors.surfaceLow,
        borderColor: UiColors.border,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: color, size: 28),
            const SizedBox(height: 8),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: UiColors.textSecondary,
                fontSize: 12,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _VoicePanelPreview extends StatelessWidget {
  const _VoicePanelPreview();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        PressableSurface(
          width: 86,
          height: 76,
          selected: true,
          padding: EdgeInsets.zero,
          child: const Center(
            child: Icon(Icons.mic_none, color: UiColors.accent, size: 30),
          ),
        ),
        const SizedBox(width: 12),
        const Expanded(child: _VoiceMeter()),
        const SizedBox(width: 12),
        Button(
          icon: const Icon(Icons.fiber_manual_record),
          tone: ButtonTone.primary,
          onPressed: () {},
          child: const Text('Record'),
        ),
      ],
    );
  }
}

class _VoiceMeter extends StatelessWidget {
  const _VoiceMeter();

  static const _levels = [0.34, 0.58, 0.82, 0.46, 0.72, 0.38, 0.62, 0.9];

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 76,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surfaceLow,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(color: UiColors.border),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Text(
                'Voice note',
                style: TextStyle(
                  color: UiColors.textSecondary,
                  fontSize: 13,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(width: 18),
              Expanded(
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    for (final level in _levels)
                      FractionallySizedBox(
                        heightFactor: level,
                        child: const SizedBox(
                          width: 7,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: UiColors.accent,
                              borderRadius: BorderRadius.all(
                                Radius.circular(99),
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MockAnchoredPanel extends StatelessWidget {
  const _MockAnchoredPanel();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(14),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: const [
          Text('Room shortcuts', style: UiTypography.title),
          SizedBox(height: 12),
          _AnchoredPanelAction(
            icon: Icons.push_pin_outlined,
            title: 'Pinned notes',
            subtitle: '3 recent decisions',
          ),
          SizedBox(height: 8),
          _AnchoredPanelAction(
            icon: Icons.call_outlined,
            title: 'Start huddle',
            subtitle: 'Mic and camera optional',
          ),
          SizedBox(height: 8),
          _AnchoredPanelAction(
            icon: Icons.folder_open_outlined,
            title: 'Shared files',
            subtitle: '24 assets available',
          ),
        ],
      ),
    );
  }
}

class _AnchoredPanelAction extends StatelessWidget {
  const _AnchoredPanelAction({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surfacePressed,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(color: UiColors.border),
      ),
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          children: [
            Icon(icon, color: UiColors.accent, size: 18),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: UiColors.text,
                      fontSize: 13,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    subtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: UiColors.textMuted,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CardBadge extends StatelessWidget {
  const _CardBadge({required this.label, required this.selected});

  final String label;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: selected
            ? UiColors.accent.withValues(alpha: 0.14)
            : UiColors.surfacePressed,
        borderRadius: BorderRadius.circular(UiRadii.sm),
        border: Border.all(
          color: selected ? UiColors.accentBorder : UiColors.border,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Text(
          label,
          style: TextStyle(
            color: selected ? UiColors.accent : UiColors.textMuted,
            fontSize: 11,
            fontWeight: FontWeight.w900,
          ),
        ),
      ),
    );
  }
}

class _MetricPill extends StatelessWidget {
  const _MetricPill({required this.label, required this.selected});

  final String label;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: selected
            ? UiColors.surfacePressed.withValues(alpha: 0.42)
            : UiColors.surfaceLow,
        borderRadius: BorderRadius.circular(UiRadii.sm),
        border: Border.all(
          color: selected
              ? UiColors.accentBorder.withValues(alpha: 0.7)
              : UiColors.border,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
        child: Text(
          label,
          style: TextStyle(
            color: selected ? UiColors.textSecondary : UiColors.textMuted,
            fontSize: 11,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _PaletteStrip extends StatelessWidget {
  const _PaletteStrip();

  static const _swatches = [
    UiColors.accent,
    UiColors.violet,
    UiColors.amber,
    UiColors.danger,
  ];

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final swatch in _swatches) ...[
          DecoratedBox(
            decoration: BoxDecoration(
              color: swatch,
              borderRadius: BorderRadius.circular(3),
            ),
            child: const SizedBox(width: 30, height: 18),
          ),
          if (swatch != _swatches.last) const SizedBox(width: 8),
        ],
      ],
    );
  }
}
