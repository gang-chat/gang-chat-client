import 'package:client/src/app/sticker_display.dart' as sticker_display;
import 'package:client/src/home/chat_pane.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart' as ui;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _host(Widget child) {
  return MaterialApp(
    theme: ui.uiTheme(),
    home: Scaffold(
      body: Center(child: SizedBox(width: 560, height: 300, child: child)),
    ),
  );
}

void main() {
  testWidgets('empty sticker sources expose their first-upload actions', (
    tester,
  ) async {
    var personalUploads = 0;
    var roomUploads = 0;

    await tester.pumpWidget(
      _host(
        StickerPanelForTest(
          state: const sticker_display.StickerPanelLoadState(loaded: true),
          onSendSticker: (_) {},
          onRefresh: () {},
          onSourceChanged: (_) {},
          onUploadFirstPersonalSticker: () => personalUploads += 1,
          onUploadFirstRoomSticker: () => roomUploads += 1,
        ),
      ),
    );

    expect(find.text('暂无个人表情'), findsOneWidget);
    final personalAction = find.byKey(
      const ValueKey<String>('sticker-panel-upload-first-personal'),
    );
    expect(personalAction, findsOneWidget);
    expect(find.text('上传第一个表情'), findsOneWidget);
    await tester.tap(personalAction);
    expect(personalUploads, 1);
    expect(roomUploads, 0);

    await tester.pumpWidget(
      _host(
        StickerPanelForTest(
          state: const sticker_display.StickerPanelLoadState(
            source: sticker_display.StickerPanelSource.room,
            loaded: true,
          ),
          onSendSticker: (_) {},
          onRefresh: () {},
          onSourceChanged: (_) {},
          onUploadFirstPersonalSticker: () => personalUploads += 1,
          onUploadFirstRoomSticker: () => roomUploads += 1,
        ),
      ),
    );

    expect(find.text('暂无房间表情'), findsOneWidget);
    final roomAction = find.byKey(
      const ValueKey<String>('sticker-panel-upload-first-room'),
    );
    expect(roomAction, findsOneWidget);
    await tester.tap(roomAction);
    expect(personalUploads, 1);
    expect(roomUploads, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('room first-upload action stays hidden without permission', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        StickerPanelForTest(
          state: const sticker_display.StickerPanelLoadState(
            source: sticker_display.StickerPanelSource.room,
            loaded: true,
          ),
          onSendSticker: (_) {},
          onRefresh: () {},
          onSourceChanged: (_) {},
          onUploadFirstPersonalSticker: () {},
        ),
      ),
    );

    expect(find.text('暂无房间表情'), findsOneWidget);
    expect(find.text('上传第一个表情'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('sticker panel flattens packs without pack headers', (
    tester,
  ) async {
    final sent = <String>[];

    await tester.pumpWidget(
      _host(
        StickerPanelForTest(
          state: sticker_display.StickerPanelLoadState(
            loaded: true,
            personalPacks: [
              _pack('saved', 'Saved Stickers', ['saved_1']),
              _pack('mine', '我的表情包', ['mine_1']),
            ],
          ),
          onSendSticker: (sticker) => sent.add(sticker.id),
          onRefresh: () {},
          onSourceChanged: (_) {},
        ),
      ),
    );

    expect(find.text('Saved Stickers'), findsNothing);
    expect(find.text('我的表情包'), findsOneWidget);
    expect(find.byIcon(Icons.image_not_supported_outlined), findsNWidgets(2));

    await tester.tap(find.byIcon(Icons.image_not_supported_outlined).first);

    expect(sent, ['saved_1']);
    expect(tester.takeException(), isNull);
  });
}

StickerPack _pack(String id, String name, List<String> stickerIds) {
  return StickerPack(
    id: id,
    scope: 'personal',
    roomId: null,
    name: name,
    sortOrder: 10,
    updatedAt: DateTime.utc(2026, 6, 9),
    stickers: [
      for (final entry in stickerIds.asMap().entries)
        Sticker(
          id: entry.value,
          name: entry.value,
          sortOrder: (entry.key + 1) * 10,
          asset: _asset(entry.value),
        ),
    ],
  );
}

UploadedAsset _asset(String id) {
  return UploadedAsset(
    id: 'asset_$id',
    url: '',
    thumbnailUrl: null,
    mimeType: 'image/png',
    filename: '$id.png',
    sizeBytes: 128,
  );
}
