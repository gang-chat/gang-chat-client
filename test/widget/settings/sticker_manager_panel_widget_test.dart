import 'dart:typed_data';

import 'package:client/src/app/sticker_management.dart' as sticker_management;
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/shell/file_selection_service.dart';
import 'package:client/src/ui/ui.dart' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderParagraph;
import 'package:flutter_test/flutter_test.dart';

Widget _host(Widget child) {
  return MaterialApp(
    theme: ui.uiTheme(),
    home: Scaffold(
      body: Padding(
        padding: const EdgeInsets.all(18),
        child: SizedBox.expand(child: child),
      ),
    ),
  );
}

void main() {
  testWidgets('narrow sticker actions switch from three columns to two', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(380, 760));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final backend = _FakeStickerBackend(
      capabilities:
          const sticker_management.StickerManagementCapabilities.readOnlyDownloads(),
      packs: [
        _pack('room_pack', ['alpha', 'beta']),
      ],
    );
    await tester.pumpWidget(
      _host(
        ui.StickerManagerPanel(
          backend: backend,
          fileSelectionService: _FakeFileSelectionService(),
          title: '表情包管理',
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('批量管理'));
    await tester.pumpAndSettle();

    final grid = find.byKey(const ValueKey('sticker-action-grid'));
    final buttons = find.descendant(of: grid, matching: find.byType(ui.Button));
    expect(buttons, findsNWidgets(6));
    final rects = [
      for (final button in buttons.evaluate())
        tester.getRect(find.byWidget(button.widget)),
    ];
    expect(rects[0].center.dy, closeTo(rects[1].center.dy, 0.01));
    expect(rects[2].center.dy, closeTo(rects[3].center.dy, 0.01));
    expect(rects[4].center.dy, closeTo(rects[5].center.dy, 0.01));
    expect(rects[0].center.dx, closeTo(rects[2].center.dx, 0.01));
    expect(rects[1].center.dx, closeTo(rects[3].center.dx, 0.01));
    expect(rects[2].center.dx, closeTo(rects[4].center.dx, 0.01));
    expect(rects[3].center.dx, closeTo(rects[5].center.dx, 0.01));
    expect(
      tester
          .renderObjectList<RenderParagraph>(
            find.descendant(of: grid, matching: find.byType(RichText)),
          )
          .every((paragraph) => !paragraph.didExceedMaxLines),
      isTrue,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'narrow sticker preview keeps all labels across three action rows',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(432, 760));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final backend = _FakeStickerBackend(
        capabilities: const sticker_management.StickerManagementCapabilities(),
        packs: [
          _pack('personal_pack', ['preview']),
        ],
        supportsAvatar: true,
      );
      await tester.pumpWidget(
        _host(
          ui.StickerManagerPanel(
            backend: backend,
            fileSelectionService: _FakeFileSelectionService(),
            title: '表情包管理',
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.image_not_supported_outlined).first);
      await tester.pumpAndSettle();

      final dialog = find.byType(Dialog);
      final grid = find.descendant(
        of: dialog,
        matching: find.byKey(const ValueKey('sticker-action-grid')),
      );
      final buttons = find.descendant(
        of: grid,
        matching: find.byType(ui.Button),
      );
      expect(buttons, findsNWidgets(6));
      final rects = [
        for (final button in buttons.evaluate())
          tester.getRect(find.byWidget(button.widget)),
      ];
      expect(rects[0].center.dy, closeTo(rects[1].center.dy, 0.01));
      expect(rects[2].center.dy, closeTo(rects[3].center.dy, 0.01));
      expect(rects[4].center.dy, closeTo(rects[5].center.dy, 0.01));
      expect(rects[0].center.dy, lessThan(rects[2].center.dy));
      expect(rects[2].center.dy, lessThan(rects[4].center.dy));
      expect(
        tester
            .renderObjectList<RenderParagraph>(
              find.descendant(of: grid, matching: find.byType(RichText)),
            )
            .every((paragraph) => !paragraph.didExceedMaxLines),
        isTrue,
      );
      expect(
        tester
            .getSize(find.byKey(const ValueKey('sticker-preview-image')))
            .height,
        lessThan(320),
      );
      expect(find.widgetWithText(ui.Button, '删除'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'action grid hides every icon when two columns still cannot fit labels',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(720, 500));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      const labels = ['下载', '设为头像', '保存名称', '置顶', '上移一位', '下移一位'];
      await tester.pumpWidget(
        _host(
          Center(
            child: SizedBox(
              width: 220,
              child: ui.StickerActionGrid(
                actions: [
                  for (final label in labels)
                    ui.StickerActionGridEntry(
                      label: label,
                      button: ui.Button(
                        onPressed: () {},
                        icon: const Icon(Icons.circle_outlined),
                        width: double.infinity,
                        child: Text(label),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final grid = find.byKey(const ValueKey('sticker-action-grid'));
      final buttons = find.descendant(
        of: grid,
        matching: find.byType(ui.Button),
      );
      final rects = [
        for (final button in buttons.evaluate())
          tester.getRect(find.byWidget(button.widget)),
      ];
      expect(rects, hasLength(6));
      expect(rects[0].center.dy, closeTo(rects[1].center.dy, 0.01));
      expect(rects[2].center.dy, closeTo(rects[3].center.dy, 0.01));
      expect(rects[4].center.dy, closeTo(rects[5].center.dy, 0.01));
      expect(
        find.descendant(of: grid, matching: find.byType(Icon)),
        findsNothing,
      );
      expect(
        tester
            .renderObjectList<RenderParagraph>(
              find.descendant(of: grid, matching: find.byType(RichText)),
            )
            .every((paragraph) => !paragraph.didExceedMaxLines),
        isTrue,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('narrow sticker filter keeps action labels and hides icons', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(330, 620));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: const Scaffold(
          body: ui.StickerFilterDialog(keyword: '', mimeType: ''),
        ),
      ),
    );
    await tester.pumpAndSettle();

    for (final label in ['重置', '取消', '确认']) {
      final button = find.widgetWithText(ui.Button, label);
      expect(button, findsOneWidget);
      expect(
        find.descendant(of: button, matching: find.byType(Icon)),
        findsNothing,
      );
      final paragraph = tester.renderObject<RenderParagraph>(find.text(label));
      expect(paragraph.didExceedMaxLines, isFalse);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('sticker pin disables an unchanged top selection', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(720, 760));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final backend = _FakeStickerBackend(
      capabilities: const sticker_management.StickerManagementCapabilities(),
      packs: [
        _pack('personal_pack', ['alpha', 'beta']),
      ],
    );
    await tester.pumpWidget(
      _host(
        ui.StickerManagerPanel(
          backend: backend,
          fileSelectionService: _FakeFileSelectionService(),
          title: '表情包管理',
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('批量管理'));
    await tester.pumpAndSettle();
    expect(_buttonEnabled(tester, '置顶'), isFalse);

    await tester.tap(find.byTooltip('alpha'));
    await tester.pumpAndSettle();
    expect(_buttonEnabled(tester, '置顶'), isFalse);

    await tester.tap(find.byTooltip('alpha'));
    await tester.tap(find.byTooltip('beta'));
    await tester.pumpAndSettle();
    expect(_buttonEnabled(tester, '置顶'), isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('sticker management keeps controls while stickers scroll', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(720, 600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final backend = _FakeStickerBackend(
      capabilities: const sticker_management.StickerManagementCapabilities(),
      packs: [
        _pack('personal_pack', List.generate(32, (index) => 'sticker_$index')),
      ],
    );

    await tester.pumpWidget(
      _host(
        ui.StickerManagerPanel(
          backend: backend,
          fileSelectionService: _FakeFileSelectionService(),
          title: '表情包管理',
        ),
      ),
    );
    await tester.pumpAndSettle();

    final title = find.text('表情包管理');
    final actions = find.byKey(const ValueKey('sticker-action-grid'));
    final stickers = find.byKey(const ValueKey('sticker-manager-items-scroll'));
    final titleTop = tester.getTopLeft(title).dy;
    final actionsTop = tester.getTopLeft(actions).dy;
    await tester.drag(stickers, const Offset(0, -260));
    await tester.pumpAndSettle();

    expect(tester.getTopLeft(title).dy, titleTop);
    expect(tester.getTopLeft(actions).dy, actionsTop);
    final scrollable = find.descendant(
      of: stickers,
      matching: find.byType(Scrollable),
    );
    expect(
      tester.state<ScrollableState>(scrollable).position.pixels,
      greaterThan(0),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('room sticker panel keeps read-only actions disabled', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final backend = _FakeStickerBackend(
      capabilities:
          const sticker_management.StickerManagementCapabilities.readOnlyDownloads(),
      packs: [
        _pack('room_pack', ['alpha', 'beta']),
      ],
    );
    final files = _FakeFileSelectionService();

    await tester.pumpWidget(
      _host(
        ui.StickerManagerPanel(
          backend: backend,
          fileSelectionService: files,
          title: '房间表情包',
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(_buttonEnabled(tester, '本地上传'), isFalse);
    expect(_buttonEnabled(tester, '批量管理'), isTrue);
    expect(_buttonEnabled(tester, '筛选'), isTrue);

    await tester.tap(find.text('批量管理'));
    await tester.pumpAndSettle();

    expect(_buttonEnabled(tester, '删除'), isFalse);
    expect(_buttonEnabled(tester, '下载'), isFalse);
    expect(_buttonEnabled(tester, '置顶'), isFalse);
    expect(_buttonEnabled(tester, '全选'), isTrue);
    expect(_buttonSelected(tester, '全选'), isFalse);

    await tester.tap(find.text('全选'));
    await tester.pumpAndSettle();

    expect(_buttonEnabled(tester, '全选'), isTrue);
    expect(_buttonSelected(tester, '全选'), isTrue);
    expect(_buttonEnabled(tester, '删除'), isFalse);
    expect(_buttonEnabled(tester, '下载'), isTrue);
    expect(_buttonEnabled(tester, '置顶'), isFalse);

    await tester.tap(find.byTooltip('alpha'));
    await tester.pumpAndSettle();

    expect(_buttonEnabled(tester, '全选'), isTrue);
    expect(_buttonSelected(tester, '全选'), isFalse);

    await tester.tap(find.text('全选'));
    await tester.pumpAndSettle();
    expect(_buttonSelected(tester, '全选'), isTrue);

    await tester.tap(find.text('全选'));
    await tester.pumpAndSettle();

    expect(_buttonEnabled(tester, '全选'), isTrue);
    expect(_buttonSelected(tester, '全选'), isFalse);

    await tester.tap(find.text('取消管理'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.image_not_supported_outlined).first);
    await tester.pumpAndSettle();

    expect(find.text('表情预览'), findsOneWidget);
    expect(
      tester
          .getSize(find.byKey(const ValueKey('sticker-preview-image')))
          .height,
      320,
    );
    expect(_buttonEnabled(tester, '下载'), isTrue);
    expect(_buttonEnabled(tester, '保存名称'), isFalse);
    expect(_buttonEnabled(tester, '置顶'), isFalse);
    expect(_buttonEnabled(tester, '上移一位'), isFalse);
    expect(_buttonEnabled(tester, '下移一位'), isFalse);
    expect(_buttonEnabled(tester, '删除'), isFalse);

    await tester.tap(find.text('下载'));
    await tester.pumpAndSettle();

    expect(backend.downloads, 1);
    expect(files.saved, 1);
    expect(backend.mutations, 0);
    expect(tester.takeException(), isNull);
  });
}

bool _buttonEnabled(WidgetTester tester, String label) {
  final surfaceFinder = find.ancestor(
    of: find.text(label),
    matching: find.byType(ui.PressableSurface),
  );
  expect(surfaceFinder, findsOneWidget, reason: label);
  return tester.widget<ui.PressableSurface>(surfaceFinder).enabled;
}

bool _buttonSelected(WidgetTester tester, String label) {
  final surfaceFinder = find.ancestor(
    of: find.text(label),
    matching: find.byType(ui.PressableSurface),
  );
  expect(surfaceFinder, findsOneWidget, reason: label);
  return tester.widget<ui.PressableSurface>(surfaceFinder).selected;
}

StickerPack _pack(String id, List<String> stickerIds) {
  return StickerPack(
    id: id,
    scope: 'room',
    roomId: 'room_1',
    name: id,
    sortOrder: 10,
    updatedAt: DateTime.utc(2026, 6, 9),
    stickers: [
      for (final entry in stickerIds.asMap().entries)
        Sticker(
          id: entry.value,
          name: entry.value,
          sortOrder: (entry.key + 1) * 10,
          asset: UploadedAsset(
            id: 'asset_${entry.value}',
            url: '/assets/${entry.value}.png',
            thumbnailUrl: '',
            mimeType: 'image/png',
          ),
        ),
    ],
  );
}

class _FakeStickerBackend extends ui.StickerManagerBackend {
  _FakeStickerBackend({
    required this.capabilities,
    required this.packs,
    this.supportsAvatar = false,
  });

  @override
  final sticker_management.StickerManagementCapabilities capabilities;

  final List<StickerPack> packs;
  final bool supportsAvatar;
  int downloads = 0;
  int mutations = 0;

  @override
  Future<void> Function(sticker_management.ManagedSticker item)?
  get onSetAvatar => supportsAvatar ? (_) async {} : null;

  @override
  sticker_management.StickerManagementScope get scope =>
      sticker_management.StickerManagementScope.room;

  @override
  bool get hasApi => true;

  @override
  Future<List<StickerPack>> loadPacks() async => packs;

  @override
  Future<StickerPack> createDefaultPack({int? sortOrder}) async {
    mutations += 1;
    throw StateError('disabled');
  }

  @override
  Future<String> uploadImageAsset({
    required Uint8List bytes,
    required String filename,
    required String purpose,
  }) async {
    mutations += 1;
    throw StateError('disabled');
  }

  @override
  Future<void> addSticker({
    required String packId,
    required String assetId,
    required String name,
    int? sortOrder,
  }) async {
    mutations += 1;
  }

  @override
  Future<void> deleteSticker({
    required String packId,
    required String stickerId,
  }) async {
    mutations += 1;
  }

  @override
  Future<String?> renameSticker({
    required String packId,
    required String stickerId,
    required String name,
  }) async {
    mutations += 1;
    return name;
  }

  @override
  Future<void> reorderStickers({
    required String packId,
    required List<String> stickerIds,
  }) async {
    mutations += 1;
  }

  @override
  Future<DownloadedFile> downloadStickers({
    required List<String> stickerIds,
  }) async {
    downloads += 1;
    return DownloadedFile(
      bytes: Uint8List.fromList([1, 2, 3]),
      filename: 'stickers.zip',
      mimeType: 'application/zip',
    );
  }
}

class _FakeFileSelectionService extends FileSelectionService {
  int saved = 0;

  @override
  Future<SaveFileLocation?> getSaveLocation({
    required String suggestedName,
    List<FileTypeGroup> acceptedTypeGroups = const [],
    String? confirmButtonText,
  }) async {
    return SaveFileLocation(path: suggestedName);
  }

  @override
  Future<void> saveBytesToPath({
    required Uint8List bytes,
    required String path,
    required String filename,
    String? mimeType,
  }) async {
    saved += 1;
  }
}
