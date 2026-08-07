import 'dart:async';

import 'package:client/src/app/composer_attachment_display.dart'
    as composer_attachment;
import 'package:client/src/app/message_display.dart' as message_display;
import 'package:client/src/app/sticker_display.dart' as sticker_display;
import 'package:client/src/app/voice_message_display.dart' as voice_display;
import 'package:client/src/config/app_config.dart';
import 'package:client/src/home/chat_pane.dart';
import 'package:client/src/home/room_profile_card.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/app_config_scope.dart';
import 'package:client/src/ui/cached_asset_image.dart';
import 'package:client/src/ui/ui.dart' as ui;
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderEditable, RenderObject;
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('chat sender names use role colors except current user', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [
            _message(
              type: 'text',
              body: 'from self',
              sender: const UserSummary(
                id: _currentUserId,
                username: 'me',
                displayName: 'Self Sender',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
                roomRole: 'owner',
              ),
            ),
            _message(
              type: 'text',
              body: 'from admin',
              clientMessageId: 'client_admin',
              sender: const UserSummary(
                id: 'user_admin',
                username: 'admin',
                displayName: 'Admin Sender',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
                roomRole: 'admin',
              ),
            ),
            _message(
              type: 'text',
              body: 'from creator',
              clientMessageId: 'client_creator',
              sender: const UserSummary(
                id: 'user_creator',
                username: 'creator',
                displayName: 'Creator Sender',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
                roomRole: 'owner',
              ),
            ),
            _message(
              type: 'text',
              body: 'from superuser',
              clientMessageId: 'client_superuser',
              sender: const UserSummary(
                id: 'user_superuser',
                username: 'superuser',
                displayName: 'Superuser Sender',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
                roomRole: 'member',
                isSuperuser: true,
              ),
            ),
          ],
        ),
        height: 620,
      ),
    );

    expect(
      tester.widget<Text>(find.text('Self Sender')).style?.color,
      ui.UiColors.accent,
    );
    expect(
      tester.widget<Text>(find.text('Admin Sender')).style?.color,
      ui.UiColors.roleAdmin,
    );
    expect(
      tester.widget<Text>(find.text('Creator Sender')).style?.color,
      ui.UiColors.roleCreator,
    );
    expect(
      tester.widget<Text>(find.text('Superuser Sender')).style?.color,
      ui.UiColors.roleSuperuser,
    );
  });

  testWidgets('current user message avatar stays borderless while in live', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final live = LiveState(
      roomId: 'room_1',
      participantCount: 1,
      participants: [
        LiveParticipant(
          liveSessionId: 'live_self',
          user: _currentUser.toSummary(),
          joinedAt: DateTime.utc(2026, 6, 11, 9),
          micMuted: false,
          headphonesMuted: false,
          voiceBlocked: false,
          cameraOn: false,
          screenSharing: false,
          connectionState: 'connected',
        ),
      ],
      updatedAt: DateTime.utc(2026, 6, 11, 9),
    );

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          live: live,
          messages: [
            _message(
              type: 'text',
              body: 'from live self',
              sender: _currentUser.toSummary(),
            ),
          ],
        ),
      ),
    );

    final avatar = tester.widget<ui.Avatar>(
      find.byWidgetPredicate(
        (widget) =>
            widget is ui.Avatar &&
            widget.label == _currentUser.displayName &&
            widget.size == 32,
      ),
    );
    expect(avatar.active, isFalse);
    expect(avatar.showBorder, isFalse);
  });

  testWidgets('message timestamps toggle between brief and detailed labels', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final now = DateTime.now();
    final firstAt = now.subtract(const Duration(minutes: 5));
    final secondAt = now.subtract(const Duration(minutes: 3));
    final firstBrief = message_display.formatChatTimestamp(firstAt, now: now);
    final secondBrief = message_display.formatChatTimestamp(secondAt, now: now);
    final firstDetailed = message_display.formatDetailedChatTimestamp(firstAt);
    final secondDetailed = message_display.formatDetailedChatTimestamp(
      secondAt,
    );

    await tester.pumpWidget(
      _host(
        ChatPane(
          currentUser: _currentUser,
          timestampNow: now,
          roomCard: _roomCard,
          room: null,
          live: null,
          messages: [
            _message(type: 'text', body: 'hello', createdAt: firstAt),
            _message(
              type: 'text',
              body: 'world',
              createdAt: secondAt,
              clientMessageId: 'client_2',
            ),
          ],
          newMessageCount: 0,
          fileTransfers: const {},
          fileDownloads: const {},
          downloadActions: _downloadActions(),
          voicePlaybackActions: const ChatVoicePlaybackActions.disabled(),
          imagePreviewActions: _imagePreviewActions(),
          loading: false,
          error: null,
          sending: false,
          sendError: null,
          composerController: controller,
          composerPanelController: ui.ChatComposerController(),
          stickerPanel: const sticker_display.StickerPanelLoadState(),
          voiceState: const voice_display.VoiceRecorderState(),
          composerAttachments:
              const <composer_attachment.ComposerAttachmentView>[],
          fileActionHighlighted: false,
          onSubmit: (_) {},
          onSendSticker: (_) {},
          onLoadStickers: () {},
          onRefreshStickers: () {},
          onStickerSourceChanged: (_) {},
          onStartVoice: () {},
          onSendVoice: () {},
          onCancelVoice: () {},
          onPickFile: () {},
          onPasteFiles: () async => false,
          onRemoveAttachment: (_) {},
          onRetryAttachment: (_) {},
          onRetry: () {},
          onOpenLiveChannel: () {},
          onOpenRoomMembers: () {},
          onOpenRoomSettings: () {},
          onViewedNewMessages: () {},
        ),
      ),
    );

    expect(find.text(firstBrief), findsOneWidget);
    expect(find.text(secondBrief), findsOneWidget);
    expect(find.text(firstDetailed), findsNothing);
    expect(find.text(secondDetailed), findsNothing);

    await tester.tap(find.text(firstBrief));
    await tester.pump();

    expect(find.text(firstDetailed), findsOneWidget);
    expect(find.text(secondDetailed), findsOneWidget);

    await tester.tap(find.text(firstDetailed));
    await tester.pump();

    expect(find.text(firstBrief), findsOneWidget);
    expect(find.text(secondBrief), findsOneWidget);
    expect(find.text(firstDetailed), findsNothing);
    expect(find.text(secondDetailed), findsNothing);
  });

  testWidgets('quote timestamps follow the message timestamp format', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final now = DateTime(2026, 7, 14, 18);
    final messageAt = DateTime(2026, 7, 14, 17);
    final quoteAt = DateTime(2026, 7, 10, 16, 12);
    final messageBrief = message_display.formatChatTimestamp(
      messageAt,
      now: now,
    );
    final quoteBrief = message_display.formatChatTimestamp(quoteAt, now: now);
    final quoteDetailed = message_display.formatDetailedChatTimestamp(quoteAt);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          timestampNow: now,
          messages: [
            _message(
              type: 'text',
              body: 'Reply body',
              createdAt: messageAt,
              quote: MessageQuote(
                messageId: 'quoted_message',
                senderDisplayName: 'Quoted user',
                body: 'Quoted body',
                createdAt: quoteAt,
              ),
            ),
          ],
        ),
      ),
    );

    expect(find.text('Quoted user  $quoteBrief'), findsOneWidget);
    expect(find.text('Quoted user  $quoteDetailed'), findsNothing);

    await tester.tap(find.text(messageBrief));
    await tester.pump();

    expect(find.text('Quoted user  $quoteBrief'), findsNothing);
    expect(find.text('Quoted user  $quoteDetailed'), findsOneWidget);
  });

  testWidgets('chat pane hides the composer until the room is ready', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _chatPane(controller: controller, messages: const [], loading: true),
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.byType(ui.ChatComposer), findsNothing);
  });

  testWidgets('chat pane opens directly at the latest message', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final messages = _textMessages(60);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: messages,
        ),
        height: 420,
      ),
    );

    final composerRect = tester.getRect(find.byType(ui.ChatComposer));
    final latestRect = tester.getRect(find.text('Message 59'));

    expect(latestRect.bottom, lessThanOrEqualTo(composerRect.top));
    expect(find.byKey(const ValueKey('chat-jump-to-latest')), findsNothing);
  });

  testWidgets('incoming unread messages preserve the current viewport', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    var messages = _textMessages(40);
    var newMessageCount = 0;
    var viewed = 0;
    late StateSetter updateState;

    await tester.pumpWidget(
      _host(
        StatefulBuilder(
          builder: (context, setState) {
            updateState = setState;
            return _chatPane(
              controller: controller,
              room: _roomDetailFor('room_preserve_unread'),
              messages: messages,
              newMessageCount: newMessageCount,
              onViewedNewMessages: () {
                viewed += 1;
                setState(() => newMessageCount = 0);
              },
            );
          },
        ),
        height: 360,
      ),
    );
    await tester.pump();

    final scrollable = tester.state<ScrollableState>(
      find
          .descendant(
            of: find.byKey(const ValueKey('chat-message-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    final previousLatestTop = tester.getTopLeft(find.text('Message 39')).dy;
    final previousPixels = scrollable.position.pixels;

    updateState(() {
      messages = [
        ...messages,
        _message(
          type: 'text',
          body: 'Message 40',
          clientMessageId: 'client_40',
          createdAt: DateTime.utc(2026, 6, 11, 9, 40),
        ),
      ];
      newMessageCount = 1;
    });
    await tester.pump();
    await tester.pump();
    await tester.pump();
    await tester.pump();

    expect(
      tester.getTopLeft(find.text('Message 39')).dy,
      closeTo(previousLatestTop, 1),
    );
    expect(scrollable.position.pixels, greaterThan(previousPixels));
    expect(viewed, 0);

    scrollable.position.jumpTo(scrollable.position.minScrollExtent);
    await tester.pump();
    await tester.pump();

    expect(viewed, 1);
  });

  testWidgets('chat pane can jump to the first new message', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final messages = _textMessages(80);
    var viewed = 0;
    var newMessageCount = 50;

    await tester.pumpWidget(
      _host(
        StatefulBuilder(
          builder: (context, setState) {
            return _chatPane(
              controller: controller,
              room: _roomDetailFor('room_jump_to_unread'),
              messages: messages,
              newMessageCount: newMessageCount,
              onViewedNewMessages: () {
                viewed += 1;
                setState(() => newMessageCount = 0);
              },
            );
          },
        ),
        height: 360,
      ),
    );
    await tester.pump();

    expect(find.byTooltip('查看 50 条未读消息'), findsOneWidget);
    expect(find.text('查看 50 条未读消息'), findsNothing);
    expect(find.byIcon(Icons.keyboard_arrow_up_rounded), findsOneWidget);
    expect(
      tester.getSize(find.byKey(const ValueKey('chat-jump-to-first-new'))),
      const Size(34, 34),
    );

    await tester.tap(find.byKey(const ValueKey('chat-jump-to-first-new')));
    await tester.pumpAndSettle();

    expect(viewed, 1);
    expect(find.byTooltip('查看 50 条未读消息'), findsNothing);
    expect(find.text('未读消息'), findsOneWidget);
  });

  testWidgets(
    'chat pane shows unread divider without jump when unread messages are visible',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      final messages = _textMessages(5);

      await tester.pumpWidget(
        _host(
          _chatPane(
            controller: controller,
            room: _roomDetailFor('room_visible_unread'),
            messages: messages,
            newMessageCount: 2,
          ),
          height: 620,
        ),
      );
      await tester.pump();

      expect(find.text('未读消息'), findsOneWidget);
      expect(find.byTooltip('查看 2 条未读消息'), findsNothing);
      expect(
        find.byKey(const ValueKey('chat-jump-to-first-new')),
        findsNothing,
      );
    },
  );

  testWidgets(
    'chat pane marks unread viewed when divider is scrolled into view',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      final messages = _textMessages(80);
      var viewed = 0;
      var newMessageCount = 50;

      await tester.pumpWidget(
        _host(
          StatefulBuilder(
            builder: (context, setState) {
              return _chatPane(
                controller: controller,
                room: _roomDetailFor('room_scroll_to_unread'),
                messages: messages,
                newMessageCount: newMessageCount,
                onViewedNewMessages: () {
                  viewed += 1;
                  setState(() => newMessageCount = 0);
                },
              );
            },
          ),
          height: 360,
        ),
      );
      await tester.pump();

      expect(find.byTooltip('查看 50 条未读消息'), findsOneWidget);

      final scrollable = tester.state<ScrollableState>(
        find
            .descendant(
              of: find.byKey(const ValueKey('chat-message-list')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      var dividerVisible = false;
      for (final fraction in [0.45, 0.55, 0.62, 0.7]) {
        scrollable.position.jumpTo(
          scrollable.position.maxScrollExtent * fraction,
        );
        await tester.pump();
        await tester.pump();
        dividerVisible = find.text('未读消息').evaluate().isNotEmpty;
        if (dividerVisible) break;
      }

      expect(dividerVisible, isTrue);
      expect(viewed, 1);
      expect(find.byTooltip('查看 50 条未读消息'), findsNothing);
    },
  );

  testWidgets('short chats start at the top of the message area', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final messages = _textMessages(2);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: messages,
        ),
        height: 620,
      ),
    );
    await tester.pump();

    final listRect = tester.getRect(
      find.byKey(const ValueKey('chat-message-list')),
    );
    final firstMessageRect = tester.getRect(find.text('Message 0'));

    expect(firstMessageRect.top, lessThan(listRect.top + 110));
  });

  testWidgets('latest message button appears away from bottom and jumps back', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final messages = _textMessages(80);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: messages,
        ),
        height: 420,
      ),
    );

    final scrollable = tester.state<ScrollableState>(
      find
          .descendant(
            of: find.byKey(const ValueKey('chat-message-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    scrollable.position.jumpTo(scrollable.position.maxScrollExtent);
    await tester.pump();

    final latestButton = find.byKey(const ValueKey('chat-jump-to-latest'));
    expect(latestButton, findsOneWidget);
    expect(find.text('最新消息'), findsNothing);
    expect(tester.getSize(latestButton), const Size(34, 34));

    await tester.tap(latestButton);
    await tester.pumpAndSettle();

    expect(
      scrollable.position.pixels,
      closeTo(scrollable.position.minScrollExtent, 0.01),
    );
    expect(find.byKey(const ValueKey('chat-jump-to-latest')), findsNothing);
  });

  testWidgets('latest message button reappears after chat pane remounts', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final messages = _textMessages(80);
    final room = _roomDetailFor('room_restore_latest_jump');

    await tester.pumpWidget(
      _host(
        _chatPane(controller: controller, room: room, messages: messages),
        height: 420,
      ),
    );

    final scrollable = tester.state<ScrollableState>(
      find
          .descendant(
            of: find.byKey(const ValueKey('chat-message-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    scrollable.position.jumpTo(scrollable.position.maxScrollExtent);
    await tester.pump();

    expect(find.byKey(const ValueKey('chat-jump-to-latest')), findsOneWidget);

    await tester.pumpWidget(_host(const SizedBox.shrink(), height: 420));
    await tester.pump();
    await tester.pumpWidget(
      _host(
        _chatPane(controller: controller, room: room, messages: messages),
        height: 420,
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.byKey(const ValueKey('chat-jump-to-latest')), findsOneWidget);
  });

  testWidgets('unread jump button reappears after chat pane remounts', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final messages = _textMessages(80);
    messages[30] = _message(
      type: 'text',
      body: 'Message 30 @Me',
      clientMessageId: 'client_30',
      createdAt: DateTime.utc(2026, 6, 11, 9, 30),
      mentions: const [
        {'type': 'user', 'user_id': _currentUserId, 'label': 'Me'},
      ],
    );
    final room = _roomDetailFor('room_restore_unread_jump');

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: room,
          messages: messages,
          newMessageCount: 50,
        ),
        height: 360,
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(
      find.byKey(const ValueKey('chat-jump-to-first-new')),
      findsOneWidget,
    );

    await tester.pumpWidget(_host(const SizedBox.shrink(), height: 360));
    await tester.pump();
    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: room,
          messages: messages,
          newMessageCount: 50,
        ),
        height: 360,
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(
      find.byKey(const ValueKey('chat-jump-to-first-new')),
      findsOneWidget,
    );
  });

  testWidgets('text message body is selectable for copy', (tester) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'copy this'),
          downloadActions: _downloadActions(),
        ),
      ),
    );

    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.controller?.text, 'copy this');
    expect(field.readOnly, isTrue);
    expect(field.enableInteractiveSelection, isTrue);
    expect(field.showCursor, isFalse);
  });

  testWidgets('text message bubble removes trailing blank width', (
    tester,
  ) async {
    const body = '123';
    const rawBody = '$body   \n ';
    const cases = [
      (TargetPlatform.windows, 1.0, 1.0),
      (TargetPlatform.macOS, 2.0, 1.0),
      (TargetPlatform.android, 3.0, 1.3),
    ];

    for (final (platform, devicePixelRatio, textScale) in cases) {
      await tester.pumpWidget(
        _host(
          MediaQuery(
            data: MediaQueryData(
              size: const Size(600, 620),
              devicePixelRatio: devicePixelRatio,
              textScaler: TextScaler.linear(textScale),
            ),
            child: MessageBubbleForTest(
              message: _message(type: 'text', body: rawBody),
              downloadActions: _downloadActions(),
            ),
          ),
          height: 620,
          platform: platform,
        ),
      );
      await tester.pumpAndSettle();

      final surface = find.byKey(
        const ValueKey('message-bubble-surface-message_1'),
      );
      final fieldFinder = find.descendant(
        of: surface,
        matching: find.byType(TextField),
      );
      final field = tester.widget<TextField>(fieldFinder);
      final textLayout = find.byKey(
        const ValueKey('message-text-layout-message_1'),
      );
      final content = find.byKey(
        const ValueKey('message-bubble-content-message_1'),
      );

      expect(field.cursorWidth, 2);
      expect(field.style, ui.UiTypography.body);
      expect(field.controller?.text, body);
      RenderEditable? editable;

      void findEditable(RenderObject object) {
        if (object is RenderEditable) {
          editable = object;
          return;
        }
        object.visitChildren(findEditable);
      }

      findEditable(tester.renderObject(textLayout));
      expect(editable, isNotNull);
      final renderEditable = editable!;
      final textPainter = TextPainter(
        text: renderEditable.text,
        textAlign: renderEditable.textAlign,
        textDirection: renderEditable.textDirection,
        textScaler: renderEditable.textScaler,
        maxLines: renderEditable.maxLines,
        locale: renderEditable.locale,
        strutStyle: renderEditable.strutStyle,
        textWidthBasis: renderEditable.textWidthBasis,
        textHeightBehavior: renderEditable.textHeightBehavior,
      )..layout();
      expect(
        tester.getSize(textLayout).width,
        closeTo(textPainter.width, 0.01),
        reason:
            'Text width must follow the active platform font and text scale.',
      );
      textPainter.dispose();

      final surfaceRect = tester.getRect(surface);
      final contentRect = tester.getRect(content);
      expect(
        contentRect.left - surfaceRect.left,
        13,
        reason: '$platform at DPR $devicePixelRatio and scale $textScale',
      );
      expect(
        surfaceRect.right - contentRect.right,
        13,
        reason: '$platform at DPR $devicePixelRatio and scale $textScale',
      );
    }
  });

  testWidgets('text message bubble wraps at compact widths on every platform', (
    tester,
  ) async {
    const body =
        'A long message that must wrap without reserving editor space.';
    const cases = [
      (TargetPlatform.windows, 220.0, 1.0),
      (TargetPlatform.macOS, 180.0, 1.15),
      (TargetPlatform.android, 140.0, 1.3),
    ];

    for (final (platform, width, textScale) in cases) {
      await tester.pumpWidget(
        _host(
          MediaQuery(
            data: MediaQueryData(
              size: Size(width, 620),
              textScaler: TextScaler.linear(textScale),
            ),
            child: Align(
              alignment: Alignment.topLeft,
              child: SizedBox(
                width: width,
                child: MessageBubbleForTest(
                  message: _message(type: 'text', body: body),
                  downloadActions: _downloadActions(),
                ),
              ),
            ),
          ),
          height: 620,
          platform: platform,
        ),
      );
      await tester.pumpAndSettle();

      final surface = find.byKey(
        const ValueKey('message-bubble-surface-message_1'),
      );
      final textLayout = find.byKey(
        const ValueKey('message-text-layout-message_1'),
      );
      expect(tester.getSize(surface).width, width);
      expect(tester.getSize(textLayout).height, greaterThan(19 * textScale));
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('text message body link is recognized as clickable', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(
            type: 'text',
            body: 'open https://example.com/docs now',
          ),
          downloadActions: _downloadActions(),
        ),
      ),
    );

    final fieldFinder = find.byType(TextField);
    final field = tester.widget<TextField>(fieldFinder);
    final span = field.controller!.buildTextSpan(
      context: tester.element(fieldFinder),
      style: field.style,
      withComposing: false,
    );
    final linkSpan = _findTextSpan(span, 'https://example.com/docs');

    expect(linkSpan, isNotNull);
    expect(linkSpan!.recognizer, isA<TapGestureRecognizer>());
    expect(linkSpan.mouseCursor, SystemMouseCursors.click);
    expect(linkSpan.style?.color, ui.UiColors.accent);
  });

  testWidgets('mention text click opens the user profile card', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    const mentionedUser = UserSummary(
      id: 'mentioned_user',
      username: 'target_user',
      displayName: 'Target User',
      avatarUrl: null,
      defaultAvatarKey: 'green-2',
      roomDisplayName: 'Target',
      roomRole: 'member',
    );
    final mentionedMember = RoomMember(
      user: mentionedUser,
      role: 'member',
      joinedAt: DateTime.utc(2026, 6, 11),
      roomDisplayName: 'Target',
    );
    const resolvedProfile = UserSummary(
      id: 'mentioned_user',
      username: 'resolved_target',
      displayName: 'Resolved Target',
      avatarUrl: null,
      defaultAvatarKey: 'green-2',
      roomDisplayName: 'Resolved Target',
      roomRole: 'member',
      uid: '2000001',
      bio: 'Loaded mention profile',
    );
    final resolveCompleter = Completer<UserSummary>();
    final resolvedIds = <String>[];

    Future<UserSummary> resolveProfile(UserSummary sender) {
      resolvedIds.add(sender.id);
      return resolveCompleter.future;
    }

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [
            _message(
              type: 'text',
              body: 'hello @Target',
              mentions: const [
                {
                  'type': 'user',
                  'user_id': 'mentioned_user',
                  'label': 'Target',
                },
              ],
            ),
          ],
          mentionMembers: [mentionedMember],
          onResolveSenderProfile: resolveProfile,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final fieldFinder = find.byType(TextField).first;
    final field = tester.widget<TextField>(fieldFinder);
    final span = field.controller!.buildTextSpan(
      context: tester.element(fieldFinder),
      style: field.style,
      withComposing: false,
    );
    final mentionSpan = _findTextSpan(span, '@Target');

    expect(mentionSpan, isNotNull);
    expect(mentionSpan!.recognizer, isA<TapGestureRecognizer>());
    expect(field.enableInteractiveSelection, isTrue);

    (mentionSpan.recognizer! as TapGestureRecognizer).onTap!();
    await tester.pump();

    expect(resolvedIds, ['mentioned_user']);
    expect(
      find.byWidgetPredicate(
        (widget) => widget is ui.AnchoredPanel && widget.width < 300,
      ),
      findsNothing,
    );
    expect(find.text('@target_user'), findsNothing);

    resolveCompleter.complete(resolvedProfile);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    final profilePanelFinder = find.byWidgetPredicate(
      (widget) => widget is ui.AnchoredPanel && widget.width < 300,
    );
    expect(profilePanelFinder, findsOneWidget);
    final profilePanelRect = tester.getRect(profilePanelFinder);
    final hostRect = tester.getRect(find.byType(Scaffold));
    expect(profilePanelRect.width, lessThan(hostRect.width / 2));
    expect(profilePanelRect.height, lessThan(hostRect.height));
    expect(resolvedIds, ['mentioned_user']);
    expect(find.text('@resolved_target'), findsOneWidget);
    expect(find.text('Loaded mention profile'), findsOneWidget);
  });

  testWidgets('@me message highlights bubble background only', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [
            _message(
              type: 'text',
              body: 'hello @Me',
              mentions: const [
                {'type': 'user', 'user_id': _currentUserId, 'label': 'Me'},
              ],
            ),
          ],
        ),
        height: 620,
      ),
    );
    await tester.pumpAndSettle();

    final decoration = _messageBubbleDecoration(tester);
    final contentDecoration = _messageBubbleContentDecoration(tester);
    expect(decoration.color, isNot(ui.UiColors.surface));
    expect(decoration.color, isNot(ui.UiColors.selected));
    expect(contentDecoration.color, decoration.color);
    expect((decoration.border as Border).top.color, isNot(ui.UiColors.border));

    final fieldFinder = find.byType(TextField).first;
    final field = tester.widget<TextField>(fieldFinder);
    final span = field.controller!.buildTextSpan(
      context: tester.element(fieldFinder),
      style: field.style,
      withComposing: false,
    );
    final mentionSpan = _findTextSpan(span, '@Me');

    expect(mentionSpan, isNotNull);
    expect(mentionSpan!.style?.color, ui.UiColors.controlAccent);

    await _secondaryClickAt(tester, tester.getCenter(fieldFinder));
    await tester.pumpAndSettle();
    final contextDecoration = _messageBubbleDecoration(tester);
    expect(contextDecoration.color, isNot(ui.UiColors.selected));
    expect(
      (contextDecoration.border as Border).top.color,
      isNot(ui.UiColors.selectedBorder),
    );
  });

  testWidgets('mentioned messages wait for mention render context', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    final mentionedMessage = _message(
      type: 'text',
      body: 'hello @Me',
      mentions: const [
        {'type': 'user', 'user_id': _currentUserId, 'label': 'Me'},
      ],
    );

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [mentionedMessage],
          mentionMembersReady: false,
        ),
        height: 620,
      ),
    );
    await tester.pump();

    expect(find.text('hello @Me'), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [mentionedMessage],
          mentionMembersReady: true,
        ),
        height: 620,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('hello @Me'), findsOneWidget);
    final decoration = _messageBubbleDecoration(tester);
    expect(decoration.color, isNot(ui.UiColors.surface));
  });

  testWidgets('focused message is highlighted on first frame', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [_message(type: 'text', body: 'jump target')],
          focusMessageId: 'message_1',
        ),
        height: 620,
      ),
    );

    final decoration = _messageBubbleDecoration(tester);
    expect(decoration.color, isNot(ui.UiColors.surface));
    expect(decoration.color, ui.UiColors.selected.withValues(alpha: 0.86));
  });

  testWidgets('focused system message uses the same timed highlight', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final message = _message(
      type: 'system',
      body: 'Logan joined the room',
      attachments: const [
        MessageAttachment(
          type: 'system',
          event: message_display.kSystemEventRoomMemberJoined,
          target: _systemTarget,
        ),
      ],
    );

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [message],
          focusMessageId: message.id,
        ),
        height: 620,
      ),
    );

    final systemContent = find.byType(ChatSystemMessageContent);
    final systemSurface = find.descendant(
      of: systemContent,
      matching: find.byType(AnimatedContainer),
    );
    var decoration =
        tester.widget<AnimatedContainer>(systemSurface).decoration
            as BoxDecoration;
    expect(decoration.color, ui.UiColors.selected.withValues(alpha: 0.86));
    expect((decoration.border as Border).top.color, ui.UiColors.selectedBorder);

    await tester.pump(const Duration(milliseconds: 2700));

    decoration =
        tester.widget<AnimatedContainer>(systemSurface).decoration
            as BoxDecoration;
    expect(
      decoration.color,
      ui.UiColors.surfacePressed.withValues(alpha: 0.82),
    );
    expect((decoration.border as Border).top.color, ui.UiColors.border);
  });

  testWidgets('focused @me message temporarily strengthens mention color', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [
            _message(
              type: 'text',
              body: 'jump to @Me',
              mentions: const [
                {'type': 'user', 'user_id': _currentUserId, 'label': 'Me'},
              ],
            ),
          ],
          focusMessageId: 'message_1',
        ),
        height: 620,
      ),
    );

    final normalColor = Color.alphaBlend(
      ui.UiColors.amber.withValues(alpha: 0.13),
      ui.UiColors.surface,
    );
    final highlightedColor = Color.alphaBlend(
      ui.UiColors.amber.withValues(alpha: 0.22),
      ui.UiColors.surface,
    );
    final normalBorder = ui.UiColors.amber.withValues(alpha: 0.58);
    final highlightedBorder = ui.UiColors.amber.withValues(alpha: 0.72);
    var decoration = _messageBubbleDecoration(tester);
    expect(decoration.color, highlightedColor);
    expect(decoration.color, isNot(normalColor));
    expect((decoration.border as Border).top.color, highlightedBorder);

    await tester.pump(const Duration(milliseconds: 2700));

    decoration = _messageBubbleDecoration(tester);
    expect(decoration.color, normalColor);
    expect((decoration.border as Border).top.color, normalBorder);
  });

  testWidgets('handled focused message does not replay after remount', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    String? focusMessageId = 'message_70';
    final room = _roomDetailFor('room_focus_restore_position');
    final messages = [
      for (var index = 0; index < 80; index++)
        _message(
          id: 'message_$index',
          type: 'text',
          body: 'Message $index',
          clientMessageId: 'client_$index',
          createdAt: DateTime.utc(2026, 6, 11, 9).add(Duration(minutes: index)),
        ),
    ];

    Widget chat() {
      return StatefulBuilder(
        builder: (context, setState) {
          return _chatPane(
            controller: controller,
            room: room,
            messages: messages,
            focusMessageId: focusMessageId,
            onFocusMessageHandled: (messageId) {
              setState(() {
                if (focusMessageId == messageId) focusMessageId = null;
              });
            },
          );
        },
      );
    }

    await tester.pumpWidget(_host(chat(), height: 420));
    await tester.pumpAndSettle();
    expect(focusMessageId, isNull);

    var scrollable = tester.state<ScrollableState>(
      find
          .descendant(
            of: find.byKey(const ValueKey('chat-message-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    scrollable.position.jumpTo(scrollable.position.maxScrollExtent);
    await tester.pump();
    final previousPixels = scrollable.position.pixels;

    await tester.pumpWidget(_host(const SizedBox.shrink(), height: 420));
    await tester.pump();
    await tester.pumpWidget(_host(chat(), height: 420));
    await tester.pump();
    await tester.pump();

    scrollable = tester.state<ScrollableState>(
      find
          .descendant(
            of: find.byKey(const ValueKey('chat-message-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(scrollable.position.pixels, closeTo(previousPixels, 1));
  });

  testWidgets('the same focused message can be requested again', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    String? focusMessageId = 'message_1';
    VoidCallback? requestAgain;
    var handledCount = 0;

    await tester.pumpWidget(
      _host(
        StatefulBuilder(
          builder: (context, setState) {
            requestAgain = () => setState(() => focusMessageId = 'message_1');
            return _chatPane(
              controller: controller,
              messages: [_message(type: 'text', body: 'jump target')],
              focusMessageId: focusMessageId,
              onFocusMessageHandled: (messageId) {
                handledCount++;
                setState(() {
                  if (focusMessageId == messageId) focusMessageId = null;
                });
              },
            );
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(handledCount, 1);
    expect(focusMessageId, isNull);

    requestAgain!();
    await tester.pumpAndSettle();

    expect(handledCount, 2);
    expect(focusMessageId, isNull);
  });

  testWidgets('selected text message context menu only copies selection', (
    tester,
  ) async {
    final clipboardWrites = <String>[];
    _mockClipboard(clipboardWrites);

    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'copy this'),
          downloadActions: _downloadActions(),
        ),
      ),
    );

    await _showMessageTextContextMenu(
      tester,
      selection: const TextSelection(baseOffset: 0, extentOffset: 4),
    );

    expect(find.text('复制'), findsOneWidget);
    expect(find.text('Ctrl+C'), findsOneWidget);
    expect(find.text('全选'), findsNothing);
    expect(find.text('Select all'), findsNothing);
    expect(find.text('删除'), findsNothing);

    await tester.tap(find.text('复制'));
    await tester.pump();

    expect(clipboardWrites, ['copy']);
  });

  testWidgets('unselected text message right click keeps message menu', (
    tester,
  ) async {
    var copied = false;
    var deleted = false;
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'copy this'),
          downloadActions: _downloadActions(),
          messageActions: ChatMessageActions(
            onCopy: (_, _) async => copied = true,
            onDeleteForMe: (_, _) async => deleted = true,
            onRecall: (_, _) async {},
            canRecall: (_) => false,
          ),
        ),
        platform: TargetPlatform.windows,
      ),
    );

    await _secondaryClickTextMessage(tester);

    var decoration = _messageBubbleDecoration(tester);
    expect(decoration.color, ui.UiColors.selected);
    expect((decoration.border as Border).top.color, ui.UiColors.selectedBorder);
    var contentDecoration = _messageBubbleContentDecoration(tester);
    expect(contentDecoration.color, decoration.color);
    expect(find.text('复制'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
    expect(find.text('全选'), findsNothing);

    await tester.tap(find.text('复制'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    decoration = _messageBubbleDecoration(tester);
    expect(decoration.color, ui.UiColors.surface);
    contentDecoration = _messageBubbleContentDecoration(tester);
    expect(contentDecoration.color, decoration.color);
    expect(copied, isTrue);
    expect(deleted, isFalse);
  });

  testWidgets('Android text message hold opens the desktop message menu', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'copy this'),
          downloadActions: _downloadActions(),
          messageActions: ChatMessageActions(
            onCopy: (_, _) async {},
            onDeleteForMe: (_, _) async {},
            onRecall: (_, _) async {},
            canRecall: (_) => false,
          ),
        ),
        platform: TargetPlatform.android,
      ),
    );

    await tester.longPress(find.byType(TextField));
    await tester.pumpAndSettle();

    final editableTextState = tester.state<EditableTextState>(
      find.byType(EditableText),
    );
    expect(find.text('复制'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
    expect(find.text('全选'), findsNothing);
    expect(editableTextState.textEditingValue.selection.isCollapsed, isTrue);
    expect(_selectionHandleFades(), findsNothing);
  });

  testWidgets('Android message double tap keeps its text selection', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'select this message'),
          downloadActions: _downloadActions(),
        ),
        platform: TargetPlatform.android,
      ),
    );

    final field = find.byType(TextField);
    final rect = tester.getRect(field);
    final wordPosition = Offset(rect.left + 24, rect.center.dy);
    await tester.tapAt(wordPosition);
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tapAt(wordPosition);
    await tester.pump();

    final editableTextState = tester.state<EditableTextState>(
      find.byType(EditableText),
    );
    expect(editableTextState.textEditingValue.selection.isCollapsed, isFalse);

    await tester.pump(const Duration(milliseconds: 600));
    expect(editableTextState.textEditingValue.selection.isCollapsed, isFalse);
    expect(_selectionHandleFades(), findsNWidgets(2));
    expect(find.text('删除'), findsNothing);

    editableTextState.hideToolbar(false);
    await tester.pump();
    expect(editableTextState.textEditingValue.selection.isCollapsed, isFalse);
    expect(_selectionHandleFades(), findsNWidgets(2));

    expect(editableTextState.showToolbar(), isTrue);
    await tester.pump();
    expect(
      find.byKey(const ValueKey('text-context-menu-panel')),
      findsOneWidget,
    );
  });

  testWidgets('message quote shows room name and time and opens source', (
    tester,
  ) async {
    MessageQuote? opened;
    final quote = MessageQuote(
      messageId: 'source_1',
      senderDisplayName: '房内用户名',
      body: '[文件] report.pdf',
      createdAt: DateTime(2026, 7, 14, 16, 12),
    );
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: '本次回复', quote: quote),
          timestampNow: DateTime(2026, 7, 14, 18),
          downloadActions: _downloadActions(),
          messageActions: ChatMessageActions(
            onCopy: (_, _) async {},
            onDeleteForMe: (_, _) async {},
            onRecall: (_, _) async {},
            canRecall: (_) => false,
            onOpenQuote: (_, value) async => opened = value,
          ),
        ),
      ),
    );

    expect(find.text('房内用户名  16:12'), findsOneWidget);
    expect(find.text('[文件] report.pdf'), findsOneWidget);
    expect(find.text('本次回复'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('message-quote-source_1')));
    await tester.pump();
    expect(opened, same(quote));
  });

  testWidgets('system message quote header shows only its timestamp', (
    tester,
  ) async {
    final quote = MessageQuote(
      messageId: 'system_source',
      senderDisplayName: '',
      body: 'A member joined the room',
      createdAt: DateTime(2026, 7, 14, 16, 12),
    );
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'Reply', quote: quote),
          timestampNow: DateTime(2026, 7, 14, 18),
          downloadActions: _downloadActions(),
        ),
      ),
    );

    expect(find.text('16:12'), findsOneWidget);
    expect(find.text('A member joined the room'), findsOneWidget);
    expect(find.textContaining('用户  16:12'), findsNothing);
  });

  testWidgets('composer quote row shows snapshot and can be closed', (
    tester,
  ) async {
    final controller = TextEditingController(text: '本次回复');
    addTearDown(controller.dispose);
    String? removed;
    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          timestampNow: DateTime(2026, 7, 14, 18),
          room: _roomDetail,
          messages: const [],
          composerQuotes: [
            MessageQuote(
              messageId: 'composer_source',
              senderDisplayName: '房内用户名',
              body: '原消息',
              createdAt: DateTime(2026, 7, 14, 16, 12),
            ),
            MessageQuote(
              messageId: 'composer_source_2',
              senderDisplayName: '第二位房内用户',
              body: '[文件] second.pdf',
              createdAt: DateTime(2026, 7, 14, 16, 13),
            ),
          ],
          onRemoveComposerQuote: (messageId) => removed = messageId,
        ),
      ),
    );

    expect(find.text('房内用户名  16:12'), findsOneWidget);
    expect(find.text('原消息'), findsOneWidget);
    expect(find.text('第二位房内用户  16:13'), findsOneWidget);
    expect(find.text('[文件] second.pdf'), findsOneWidget);
    final firstQuoteCard = find.byKey(
      const ValueKey('message-quote-composer_source'),
    );
    final firstCloseButton = find.byKey(
      const ValueKey('composer-quote-close-composer_source'),
    );
    expect(
      tester.getCenter(firstCloseButton).dy,
      closeTo(tester.getCenter(firstQuoteCard).dy, 0.01),
    );
    await tester.tap(firstCloseButton);
    await tester.pump();
    expect(removed, 'composer_source');
  });

  testWidgets('image quote shows only a clickable thumbnail', (tester) async {
    MessageQuote? opened;
    final quote = MessageQuote(
      messageId: 'image_source',
      senderDisplayName: '房内用户名',
      body: '[图片] 照片.png',
      createdAt: DateTime(2026, 7, 14, 16, 12),
      previewAttachment: const MessageAttachment(
        type: 'file',
        name: '照片.png',
        asset: UploadedAsset(
          id: 'image_asset',
          url: '/images/photo.png',
          thumbnailUrl: '/images/photo-thumb.png',
          mimeType: 'image/png',
          filename: '照片.png',
        ),
      ),
    );
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: '本次回复', quote: quote),
          timestampNow: DateTime(2026, 7, 14, 18),
          downloadActions: _downloadActions(),
          imagePreviewActions: _imagePreviewActions(),
          messageActions: ChatMessageActions(
            onCopy: (_, _) async {},
            onDeleteForMe: (_, _) async {},
            onRecall: (_, _) async {},
            canRecall: (_) => false,
            onOpenQuote: (_, value) async => opened = value,
          ),
        ),
      ),
    );

    expect(find.text('[图片] 照片.png'), findsNothing);
    final thumbnail = find.byKey(
      const ValueKey('message-quote-thumbnail-image_source'),
    );
    expect(thumbnail, findsOneWidget);
    final indicator = find.byKey(
      const ValueKey('message-quote-indicator-image_source'),
    );
    expect(indicator, findsOneWidget);
    expect(
      tester.getBottomRight(indicator).dy,
      closeTo(tester.getBottomRight(thumbnail).dy, 0.01),
    );
    tester.widget<GestureDetector>(thumbnail).onTap!();
    await tester.pump();
    expect(opened, isNull);
    await tester.pump(const Duration(milliseconds: 200));
    expect(
      find.byKey(const ValueKey('chat-image-preview-url-image')),
      findsOneWidget,
    );
  });

  testWidgets('message context menu can select a quote source', (tester) async {
    Message? quoted;
    final message = _message(type: 'text', body: '引用我');
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: message,
          downloadActions: _downloadActions(),
          messageActions: ChatMessageActions(
            onCopy: (_, _) async {},
            onDeleteForMe: (_, _) async {},
            onRecall: (_, _) async {},
            canRecall: (_) => false,
            onQuote: (value) => quoted = value,
            canQuote: (_) => true,
          ),
        ),
      ),
    );

    await _secondaryClickTextMessage(tester);
    expect(find.text('引用'), findsOneWidget);
    await tester.tap(find.text('引用'));
    await tester.pump();
    expect(quoted, same(message));
  });

  testWidgets('text message bubble padding opens message menu', (tester) async {
    var deleted = false;
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'copy this'),
          downloadActions: _downloadActions(),
          messageActions: ChatMessageActions(
            onCopy: (_, _) async {},
            onDeleteForMe: (_, _) async => deleted = true,
            onRecall: (_, _) async {},
            canRecall: (_) => false,
          ),
        ),
      ),
    );

    final textRect = tester.getRect(find.byType(TextField));
    await _secondaryClickAt(
      tester,
      Offset(textRect.left - 6, textRect.center.dy),
    );

    expect(find.text('复制'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
    expect(find.text('全选'), findsNothing);

    await tester.tap(find.text('删除'));
    await tester.pump();
    expect(deleted, isTrue);
  });

  testWidgets('left click cancellation clears remembered text selection', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'copy this'),
          downloadActions: _downloadActions(),
          messageActions: ChatMessageActions(
            onCopy: (_, _) async {},
            onDeleteForMe: (_, _) async {},
            onRecall: (_, _) async {},
            canRecall: (_) => false,
          ),
        ),
        platform: TargetPlatform.windows,
      ),
    );

    final editableTextState = await _selectMessageText(
      tester,
      const TextSelection(baseOffset: 0, extentOffset: 4),
    );
    await _secondaryClickTextMessage(tester);
    expect(find.text('删除'), findsNothing);

    await tester.tapAt(const Offset(4, 4));
    await tester.pump(const Duration(milliseconds: 100));
    expect(editableTextState.textEditingValue.selection.isCollapsed, isTrue);

    await _secondaryClickTextMessage(tester);

    expect(find.text('复制'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
    expect(find.text('全选'), findsNothing);
  });

  testWidgets('pending message shows a spinner beside the 发送中 label', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(type: 'text', body: 'sending soon', pending: true),
          outgoing: true,
          downloadActions: _downloadActions(),
        ),
      ),
    );

    expect(find.text('发送中'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('system role message renders below a centered timestamp', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final createdAt = DateTime.now().subtract(const Duration(minutes: 2));
    final brief = message_display.formatChatTimestamp(
      createdAt,
      now: DateTime.now(),
    );
    final detailed = message_display.formatDetailedChatTimestamp(createdAt);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          messages: [
            _message(
              type: 'system',
              body: '降职为管理员',
              createdAt: createdAt,
              attachments: const [
                MessageAttachment(
                  type: 'system',
                  event: message_display.kSystemEventRoomRoleChanged,
                  target: _systemTarget,
                  actor: _systemActor,
                  fromRole: 'owner',
                  toRole: 'admin',
                ),
              ],
            ),
          ],
        ),
      ),
    );

    expect(find.text(brief), findsOneWidget);
    expect(find.text('Logan'), findsOneWidget);
    final loganText = tester.widget<Text>(find.text('Logan').first);
    expect(loganText.style?.color, ui.roleBadgeForegroundColorForLabel('管理员'));
    expect(find.text('Owner'), findsNothing);
    expect(find.text('降职为'), findsOneWidget);
    expect(find.text('管理员'), findsOneWidget);

    await tester.tap(find.text(brief));
    await tester.pump();

    expect(find.text(detailed), findsOneWidget);
  });

  testWidgets(
    'system room profile change messages show original values from info buttons',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _host(
          _chatPane(
            controller: controller,
            messages: [
              _message(
                type: 'system',
                body: '房间名称被Owner修改为New Room',
                clientMessageId: 'client_room_name_changed',
                attachments: const [
                  MessageAttachment(
                    type: 'system',
                    event: message_display.kSystemEventRoomNameChanged,
                    user: _systemActor,
                    actor: _systemActor,
                    oldValue: 'Old Room',
                    newValue: 'New Room',
                  ),
                ],
              ),
              _message(
                type: 'system',
                body: '房间简介被Owner修改为\nNew intro\nline 2',
                clientMessageId: 'client_room_description_changed',
                attachments: const [
                  MessageAttachment(
                    type: 'system',
                    event: message_display.kSystemEventRoomDescriptionChanged,
                    user: _systemActor,
                    actor: _systemActor,
                    oldValue: 'Old intro',
                    newValue: 'New intro\nline 2',
                  ),
                ],
              ),
              _message(
                type: 'system',
                body: '房间可见性被Owner修改为私密',
                clientMessageId: 'client_room_visibility_changed',
                attachments: const [
                  MessageAttachment(
                    type: 'system',
                    event: message_display.kSystemEventRoomVisibilityChanged,
                    user: _systemActor,
                    actor: _systemActor,
                    oldValue: 'public',
                    newValue: 'private',
                  ),
                ],
              ),
              _message(
                type: 'system',
                body: '房间加入方式被Owner修改为关闭',
                clientMessageId: 'client_room_join_policy_changed',
                attachments: const [
                  MessageAttachment(
                    type: 'system',
                    event: message_display.kSystemEventRoomJoinPolicyChanged,
                    user: _systemActor,
                    actor: _systemActor,
                    oldValue: 'approval_required',
                    newValue: 'closed',
                  ),
                ],
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('房间名称'), findsOneWidget);
      final nameInfo = find.byKey(
        const ValueKey(
          'system-info-client_room_name_changed-room_name_changed',
        ),
      );
      expect(nameInfo, findsOneWidget);
      expect(find.text('New Room'), findsOneWidget);
      expect(
        find.ancestor(
          of: find.text('New Room'),
          matching: find.byType(Tooltip),
        ),
        findsNothing,
      );

      expect(find.text('房间简介'), findsOneWidget);
      final descriptionInfo = find.byKey(
        const ValueKey(
          'system-info-client_room_description_changed-room_description_changed',
        ),
      );
      expect(descriptionInfo, findsOneWidget);
      expect(find.text('New intro\nline 2'), findsOneWidget);
      expect(
        find.ancestor(
          of: find.text('New intro\nline 2'),
          matching: find.byType(Tooltip),
        ),
        findsNothing,
      );

      expect(find.text('房间可见性'), findsOneWidget);
      final visibilityInfo = find.byKey(
        const ValueKey(
          'system-info-client_room_visibility_changed-room_visibility_changed',
        ),
      );
      expect(visibilityInfo, findsOneWidget);
      expect(find.text('私密'), findsOneWidget);

      expect(find.text('房间加入方式'), findsOneWidget);
      final joinPolicyInfo = find.byKey(
        const ValueKey(
          'system-info-client_room_join_policy_changed-room_join_policy_changed',
        ),
      );
      expect(joinPolicyInfo, findsOneWidget);
      expect(find.text('关闭'), findsOneWidget);

      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);

      await gesture.moveTo(tester.getCenter(nameInfo));
      await tester.pumpAndSettle();
      expect(find.text('原房间名称：Old Room'), findsOneWidget);

      await gesture.moveTo(Offset.zero);
      await tester.pump(const Duration(milliseconds: 180));
      await tester.tap(descriptionInfo);
      await tester.pumpAndSettle();
      expect(find.text('原房间简介：Old intro'), findsOneWidget);

      await tester.tapAt(Offset.zero);
      await tester.pump(const Duration(milliseconds: 180));
      await tester.tap(visibilityInfo);
      await tester.pumpAndSettle();
      expect(find.text('原可见性：公开'), findsOneWidget);

      await tester.tapAt(Offset.zero);
      await tester.pump(const Duration(milliseconds: 180));
      await tester.tap(joinPolicyInfo);
      await tester.pumpAndSettle();
      expect(find.text('原加入方式：需审批'), findsOneWidget);

      expect(
        find.byWidgetPredicate(
          (widget) => widget is ui.Avatar && widget.label == 'Owner',
        ),
        findsWidgets,
      );
    },
  );

  testWidgets(
    'unread divider stays above timestamp for batched system events',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      final now = DateTime.now();
      final createdAt = now.subtract(const Duration(minutes: 2));
      final timestamp = message_display.formatChatTimestamp(
        createdAt,
        now: now,
      );

      await tester.pumpWidget(
        _host(
          _chatPane(
            controller: controller,
            messages: [
              _message(
                type: 'system',
                body: '房间名称修改为123456',
                clientMessageId: 'client_room_name_changed',
                createdAt: createdAt,
                attachments: const [
                  MessageAttachment(
                    type: 'system',
                    event: message_display.kSystemEventRoomNameChanged,
                    user: _systemActor,
                    actor: _systemActor,
                    oldValue: 'old',
                    newValue: '123456',
                  ),
                ],
              ),
              _message(
                type: 'system',
                body: '房间简介修改为\nintro',
                clientMessageId: 'client_room_description_changed',
                createdAt: createdAt.add(const Duration(milliseconds: 1)),
                attachments: const [
                  MessageAttachment(
                    type: 'system',
                    event: message_display.kSystemEventRoomDescriptionChanged,
                    user: _systemActor,
                    actor: _systemActor,
                    oldValue: 'old intro',
                    newValue: 'intro',
                  ),
                ],
              ),
            ],
            newMessageCount: 2,
          ),
          height: 620,
        ),
      );
      await tester.pumpAndSettle();

      final unreadRect = tester.getRect(find.text('未读消息'));
      final timestampRect = tester.getRect(find.text(timestamp));
      expect(unreadRect.bottom, lessThan(timestampRect.top));
    },
  );

  testWidgets('system message user avatar opens the resolved profile card', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final resolvedIds = <String>[];

    Future<UserSummary> resolveProfile(UserSummary sender) async {
      resolvedIds.add(sender.id);
      return UserSummary(
        id: sender.id,
        username: 'fresh_${sender.username}',
        displayName: 'Fresh ${sender.displayName}',
        avatarUrl: sender.avatarUrl,
        defaultAvatarKey: sender.defaultAvatarKey,
        uid: '20001',
        bio: 'Fresh system profile',
        isOnline: true,
      );
    }

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          messages: [
            _message(
              type: 'system',
              body: '进入了语音频道',
              attachments: const [
                MessageAttachment(
                  type: 'system',
                  event: message_display.kSystemEventLiveJoined,
                  target: _systemTarget,
                ),
              ],
            ),
          ],
          onResolveSenderProfile: resolveProfile,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    final systemAvatar = find.byWidgetPredicate(
      (widget) => widget is ui.Avatar && widget.label == 'Logan',
    );
    expect(systemAvatar, findsOneWidget);

    await gesture.moveTo(tester.getCenter(systemAvatar));
    await tester.pumpAndSettle();

    expect(resolvedIds, ['user_target']);
    expect(find.text('@fresh_logan'), findsOneWidget);
    expect(find.text('Fresh system profile'), findsOneWidget);
  });

  testWidgets('system message user avatar can expose a profile action', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    UserSummary? managedUser;

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          messages: [
            _message(
              type: 'system',
              body: '进入了语音频道',
              attachments: const [
                MessageAttachment(
                  type: 'system',
                  event: message_display.kSystemEventLiveJoined,
                  target: _systemTarget,
                ),
              ],
            ),
          ],
          senderProfileActionBuilder: (user) => UserProfileAction(
            label: '管理成员',
            icon: Icons.manage_accounts_outlined,
            onPressed: () => managedUser = user,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    final systemAvatar = find.byWidgetPredicate(
      (widget) => widget is ui.Avatar && widget.label == 'Logan',
    );
    await gesture.moveTo(tester.getCenter(systemAvatar));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ui.Button, '管理成员'));
    await tester.pumpAndSettle();

    expect(managedUser?.id, 'user_target');
  });

  testWidgets('system and ordinary messages can share a timestamp divider', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final now = DateTime.now();
    final createdAt = DateTime(
      now.year,
      now.month,
      now.day,
      now.hour,
      now.minute,
      5,
    );
    final brief = message_display.formatChatTimestamp(
      createdAt,
      now: DateTime.now(),
    );

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          messages: [
            _message(
              type: 'system',
              body: '进入了语音频道',
              createdAt: createdAt,
              attachments: const [
                MessageAttachment(
                  type: 'system',
                  event: message_display.kSystemEventLiveJoined,
                  target: _systemTarget,
                ),
              ],
            ),
            _message(
              type: 'text',
              body: 'hello after live join',
              createdAt: createdAt.add(const Duration(seconds: 10)),
              clientMessageId: 'client_after_system',
            ),
          ],
        ),
      ),
    );

    expect(find.text(brief), findsOneWidget);
    expect(find.text('Logan'), findsWidgets);
    expect(find.text('进入了语音频道'), findsOneWidget);
    expect(find.text('hello after live join'), findsOneWidget);
  });

  testWidgets('own recalled text message can be re-edited', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    Message? reeditedMessage;

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [
            _message(
              type: 'text',
              body: 'bring this back',
              sender: _currentUser.toSummary(),
              isRecalled: true,
              recalledBy: _currentUser.toSummary(),
            ),
          ],
          messageActions: _messageActions(
            onReeditRecalledText: (message) => reeditedMessage = message,
            canReeditRecalledText: (_) => true,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final button = find.byKey(const ValueKey('message-reedit-message_1'));
    expect(button, findsOneWidget);

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(button));
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(find.text('重新编辑'), findsOneWidget);

    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(reeditedMessage?.body, 'bring this back');
  });

  testWidgets('permitted recalled text message exposes content info card', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [
            _message(
              type: 'text',
              body: 'moderated secret',
              sender: const UserSummary(
                id: 'user_member',
                username: 'member',
                displayName: 'Member',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
                roomRole: 'member',
              ),
              isRecalled: true,
              recalledBy: const UserSummary(
                id: 'user_admin',
                username: 'admin',
                displayName: 'Admin',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
                roomRole: 'admin',
              ),
            ),
          ],
          messageActions: _messageActions(canInspectRecalledText: (_) => true),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final button = find.byKey(const ValueKey('message-info-message_1'));
    expect(button, findsOneWidget);

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(button));
    await tester.pumpAndSettle();
    expect(find.text('moderated secret'), findsOneWidget);

    await gesture.moveTo(Offset.zero);
    await tester.pump(const Duration(milliseconds: 180));
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(find.text('moderated secret'), findsOneWidget);
  });

  testWidgets('own text message recalled by another user exposes info card', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [
            _message(
              type: 'text',
              body: 'my moderated text',
              sender: _currentUser.toSummary(),
              isRecalled: true,
              recalledBy: const UserSummary(
                id: 'user_admin',
                username: 'admin',
                displayName: 'Admin',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
                roomRole: 'admin',
              ),
            ),
          ],
          messageActions: _messageActions(canInspectRecalledText: (_) => true),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final button = find.byKey(const ValueKey('message-info-message_1'));
    expect(button, findsOneWidget);
    expect(
      find.byKey(const ValueKey('message-reedit-message_1')),
      findsNothing,
    );
  });

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} shared playlist message exposes view-only card and direct arrow',
      (tester) async {
        final controller = TextEditingController();
        addTearDown(controller.dispose);
        var viewed = 0;
        final playlist = SharedMusicPlaylist(
          id: 'shared_playlist_1',
          name: '朋友的夜晚歌单',
          description: '',
          itemCount: 1,
          createdAt: DateTime.utc(2026, 8, 7, 9, 30),
          creator: const UserSummary(
            id: 'friend_1',
            username: 'friend',
            displayName: '朋友',
            roomDisplayName: '房间里的朋友',
            avatarUrl: null,
            defaultAvatarKey: 'blue-2',
          ),
          items: const [
            PersonalMusicPlaylistItem(
              id: 'shared_item_1',
              playlistId: 'shared_playlist_1',
              trackId: 'track_1',
              source: 'netease',
              title: '第一首歌',
              artists: ['歌手'],
              durationMs: 180000,
              sortOrder: 10,
              createdAt: null,
            ),
          ],
        );
        final message = _message(
          type: 'playlist',
          body: '[歌单] 朋友的夜晚歌单',
          attachments: [
            MessageAttachment(
              type: 'playlist',
              playlistId: playlist.id,
              playlist: playlist,
            ),
          ],
        );
        final actions = ChatMessageActions(
          onCopy: (_, _) async {},
          onDeleteForMe: (_, _) async {},
          onRecall: (_, _) async {},
          canRecall: (_) => false,
          onViewSharedPlaylist: (context, viewedMessage, viewedPlaylist) async {
            expect(viewedMessage.id, message.id);
            expect(viewedPlaylist.id, playlist.id);
            viewed += 1;
            await showDialog<void>(
              context: context,
              builder: (context) => ui.DialogFrame(
                title: viewedPlaylist.name,
                adaptiveActions: [
                  ui.ResponsiveDialogAction(
                    label: '完成',
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
                child: SizedBox(
                  key: ValueKey<String>(
                    'shared-music-playlist-view-${viewedPlaylist.id}',
                  ),
                  height: 180,
                  child: Center(child: Text(viewedPlaylist.items.first.title)),
                ),
              ),
            );
          },
        );

        await tester.pumpWidget(
          _host(
            _chatPane(
              controller: controller,
              messages: [message],
              messageActions: actions,
            ),
            height: 520,
            platform: platform,
          ),
        );
        await tester.pumpAndSettle();

        final embed = find.byKey(
          const ValueKey<String>(
            'shared-music-playlist-message-shared_playlist_1',
          ),
        );
        expect(embed, findsOneWidget);
        await tester.tap(embed);
        await tester.pumpAndSettle();

        final viewAction = find.byKey(
          const ValueKey<String>('music-playlist-card-view'),
        );
        expect(find.text('克隆'), findsNothing);
        expect(find.text('查看歌单'), findsOneWidget);
        expect(
          find.byKey(const ValueKey<String>('music-playlist-card-play-all')),
          findsNothing,
        );
        expect(viewAction, findsOneWidget);

        await tester.tap(viewAction);
        // The card deliberately keeps its action in the loading state while the
        // read-only playlist dialog is open, so there is no fully settled frame.
        await tester.pump(const Duration(milliseconds: 300));
        expect(
          find.byKey(
            const ValueKey<String>(
              'shared-music-playlist-view-shared_playlist_1',
            ),
          ),
          findsOneWidget,
        );
        expect(find.text('第一首歌'), findsOneWidget);
        expect(viewed, 1);
        await tester.tap(find.text('完成'));
        await tester.pumpAndSettle();

        await tester.tap(
          find.byKey(
            const ValueKey<String>(
              'shared-music-playlist-open-shared_playlist_1',
            ),
          ),
        );
        await tester.pump(const Duration(milliseconds: 300));
        expect(viewed, 2);
        expect(
          find.byKey(
            const ValueKey<String>(
              'shared-music-playlist-view-shared_playlist_1',
            ),
          ),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('sticker bubble exposes the sticker name only as a tooltip', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(
            type: 'sticker',
            body: '[表情] wave',
            attachments: const [
              MessageAttachment(
                type: 'sticker',
                name: 'wave',
                asset: UploadedAsset(
                  id: 'asset_sticker',
                  url: '/stickers/wave.webp',
                  thumbnailUrl: '/stickers/wave-thumb.webp',
                  mimeType: 'image/webp',
                ),
              ),
            ],
          ),
          downloadActions: _downloadActions(),
        ),
      ),
    );

    final image = tester.widget<CachedAssetImage>(
      find.byType(CachedAssetImage),
    );
    expect(image.url, 'https://assets.test/stickers/wave-thumb.webp');
    expect(image.width, 132);
    expect(image.height, 132);
    expect(image.fit, BoxFit.contain);
    expect(find.text('wave'), findsNothing);
    expect(tester.widget<Tooltip>(find.byType(Tooltip)).message, 'wave');
  });

  testWidgets('sticker context menu offers personal and room add actions', (
    tester,
  ) async {
    var savedPersonal = false;
    var savedRoom = false;
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _stickerMessage(),
          downloadActions: _downloadActions(),
          imagePreviewActions: ChatImagePreviewActions(
            onDownload: (_, _) async {},
            onSaveAs: (_, _) async {},
            onCopyToClipboard: (_) async {},
            onSaveSticker: (message, attachment) async {
              savedPersonal =
                  message.id == 'message_1' &&
                  attachment.stickerId == 'sticker_1';
            },
            onSaveRoomSticker: (message, attachment) async {
              savedRoom =
                  message.id == 'message_1' &&
                  attachment.stickerId == 'sticker_1';
            },
          ),
        ),
      ),
    );

    await _secondaryClickSticker(tester);

    expect(find.text('添加到我的表情包'), findsOneWidget);
    expect(find.text('添加到房间表情包'), findsOneWidget);
    final personalLabel = tester.widget<Text>(find.text('添加到我的表情包'));
    expect(personalLabel.style?.fontWeight, FontWeight.w400);
    expect(personalLabel.style?.decoration, TextDecoration.none);
    expect(personalLabel.style?.inherit, isFalse);

    await tester.tap(find.text('添加到房间表情包'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(savedPersonal, isFalse);
    expect(savedRoom, isTrue);
    expect(find.text('已添加到房间表情包'), findsOneWidget);
  });

  testWidgets('Android sticker hold opens the desktop sticker menu', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _stickerMessage(),
          downloadActions: _downloadActions(),
          imagePreviewActions: ChatImagePreviewActions(
            onDownload: (_, _) async {},
            onSaveAs: (_, _) async {},
            onCopyToClipboard: (_) async {},
            onSaveSticker: (_, _) async {},
            onSaveRoomSticker: (_, _) async {},
          ),
        ),
        platform: TargetPlatform.android,
      ),
    );

    await tester.longPress(find.byType(CachedAssetImage));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('添加到我的表情包'), findsOneWidget);
    expect(find.text('添加到房间表情包'), findsOneWidget);
  });

  testWidgets('sticker context menu hides room action when unavailable', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _stickerMessage(),
          downloadActions: _downloadActions(),
          imagePreviewActions: ChatImagePreviewActions(
            onDownload: (_, _) async {},
            onSaveAs: (_, _) async {},
            onCopyToClipboard: (_) async {},
            onSaveSticker: (_, _) async {},
          ),
        ),
      ),
    );

    await _secondaryClickSticker(tester);

    expect(find.text('添加到我的表情包'), findsOneWidget);
    expect(find.text('添加到房间表情包'), findsNothing);
  });

  testWidgets('sticker bubble padding opens sticker menu', (tester) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _stickerMessage(),
          downloadActions: _downloadActions(),
          imagePreviewActions: ChatImagePreviewActions(
            onDownload: (_, _) async {},
            onSaveAs: (_, _) async {},
            onCopyToClipboard: (_) async {},
            onSaveSticker: (_, _) async {},
          ),
        ),
      ),
    );

    final imageRect = tester.getRect(find.byType(CachedAssetImage));
    await _secondaryClickAt(
      tester,
      Offset(imageRect.left - 6, imageRect.center.dy),
    );

    expect(find.text('复制'), findsOneWidget);
    expect(find.text('添加到我的表情包'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
  });

  testWidgets('image file attachment renders a resolved preview image', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(
            type: 'file',
            body: 'photo.png',
            attachments: const [
              MessageAttachment(
                type: 'file',
                name: 'photo.png',
                asset: UploadedAsset(
                  id: 'asset_photo',
                  url: '/uploads/photo.png',
                  thumbnailUrl: '/uploads/photo-thumb.png',
                  mimeType: 'image/png',
                  filename: 'photo.png',
                  sizeBytes: 2048,
                  width: 1600,
                  height: 900,
                ),
              ),
            ],
          ),
          downloadActions: _downloadActions(),
        ),
      ),
    );

    final image = tester.widget<CachedAssetImage>(
      find.byType(CachedAssetImage),
    );
    expect(image.fit, BoxFit.contain);
    expect(image.url, 'https://assets.test/uploads/photo-thumb.png');
    expect(tester.getSize(find.byType(CachedAssetImage)), const Size(320, 180));
  });

  testWidgets('non-image file attachment keeps the file tile without preview', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: _message(
            type: 'file',
            body: 'report.pdf',
            attachments: const [
              MessageAttachment(
                type: 'file',
                name: 'report.pdf',
                asset: UploadedAsset(
                  id: 'asset_report',
                  url: '/uploads/report.pdf',
                  thumbnailUrl: null,
                  mimeType: 'application/pdf',
                  filename: 'report.pdf',
                  sizeBytes: 2048,
                ),
              ),
            ],
          ),
          downloadActions: _downloadActions(),
        ),
      ),
    );

    expect(find.byType(CachedAssetImage), findsNothing);
    expect(find.byIcon(Icons.picture_as_pdf_outlined), findsOneWidget);
    expect(find.byTooltip('report.pdf'), findsOneWidget);
  });

  testWidgets('voice attachment renders a playable waveform bubble', (
    tester,
  ) async {
    final toggles = <String>[];
    final message = _message(
      type: 'audio',
      body: 'voice_1.m4a',
      attachments: const [
        MessageAttachment(
          type: 'audio',
          name: 'voice_1.m4a',
          durationMs: 15000,
          asset: UploadedAsset(
            id: 'asset_voice',
            url: '/uploads/voice_1.m4a',
            thumbnailUrl: null,
            mimeType: 'audio/mp4',
            filename: 'voice_1.m4a',
            sizeBytes: 4096,
          ),
        ),
      ],
    );

    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: message,
          downloadActions: _downloadActions(),
          voicePlaybackActions: ChatVoicePlaybackActions(
            activeMessageId: null,
            onToggle: (messageId, resolvedUrl) {
              toggles.add('$messageId|$resolvedUrl');
            },
          ),
        ),
      ),
    );

    expect(find.text('15"'), findsOneWidget);
    expect(
      tester.getSize(find.byKey(const ValueKey('voice-waveform'))).width,
      closeTo(
        voice_display.voiceWaveformWidth(const Duration(seconds: 15)),
        0.01,
      ),
    );
    expect(find.byIcon(Icons.play_arrow_rounded), findsOneWidget);
    expect(find.byIcon(Icons.audio_file_outlined), findsNothing);
    expect(find.byTooltip('voice_1.m4a'), findsNothing);
    expect(
      tester.widget<Icon>(find.byIcon(Icons.play_arrow_rounded)).color,
      Colors.white,
    );
    expect(tester.widget<Text>(find.text('15"')).style?.color, Colors.white);
    expect(
      find.byWidgetPredicate((widget) {
        final decoration = widget is DecoratedBox ? widget.decoration : null;
        return decoration is BoxDecoration && decoration.color == Colors.white;
      }),
      findsWidgets,
    );

    await tester.tap(find.byIcon(Icons.play_arrow_rounded));
    expect(toggles, ['client_1|https://assets.test/uploads/voice_1.m4a']);

    await tester.pumpWidget(
      _host(
        MessageBubbleForTest(
          message: message,
          downloadActions: _downloadActions(),
          voicePlaybackActions: ChatVoicePlaybackActions(
            activeMessageId: 'client_1',
            onToggle: (messageId, resolvedUrl) {
              toggles.add('$messageId|$resolvedUrl');
            },
          ),
        ),
      ),
    );

    expect(find.byIcon(Icons.stop_rounded), findsOneWidget);
  });

  testWidgets('voice waveform width follows attachment duration', (
    tester,
  ) async {
    Future<double> renderWidth(Duration duration) async {
      await tester.pumpWidget(
        _host(
          MessageBubbleForTest(
            message: _voiceMessage(duration: duration),
            downloadActions: _downloadActions(),
          ),
        ),
      );
      return tester.getSize(find.byKey(const ValueKey('voice-waveform'))).width;
    }

    final shortWidth = await renderWidth(const Duration(seconds: 3));
    final mediumWidth = await renderWidth(const Duration(seconds: 30));
    final longWidth = await renderWidth(const Duration(minutes: 5));

    expect(mediumWidth, greaterThan(shortWidth));
    expect(longWidth, greaterThan(mediumWidth));
    expect(longWidth, closeTo(voice_display.kVoiceWaveformMaxWidth, 0.01));
  });

  testWidgets('voice duration labels keep a consistent trailing gap', (
    tester,
  ) async {
    Future<double> renderTrailingGap(Duration duration, String label) async {
      await tester.pumpWidget(
        _host(
          MessageBubbleForTest(
            message: _voiceMessage(duration: duration),
            downloadActions: _downloadActions(),
          ),
        ),
      );
      final bodyRight = tester
          .getRect(find.byKey(const ValueKey('voice-body')))
          .right;
      final labelRight = tester.getRect(find.text(label)).right;
      return bodyRight - labelRight;
    }

    final secondsGap = await renderTrailingGap(
      const Duration(seconds: 2),
      '2"',
    );
    final minutesGap = await renderTrailingGap(
      const Duration(seconds: 65),
      '1\'05"',
    );

    expect(secondsGap, closeTo(minutesGap, 0.01));
  });

  testWidgets('message sender profile can enter a common room', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    PublicRoom? openedRoom;
    const sender = UserSummary(
      id: 'user_1',
      username: 'logan',
      displayName: 'Logan',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      commonRooms: [
        UserCommonRoom(id: 'room_common', rid: 'R200', name: 'Common room'),
      ],
    );

    await tester.pumpWidget(
      _host(
        _chatPane(
          controller: controller,
          room: _roomDetail,
          messages: [_message(type: 'text', body: 'hello', sender: sender)],
          onEnterProfileRoom: (room) => openedRoom = room,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    final senderAvatar = find.byWidgetPredicate(
      (widget) => widget is ui.Avatar && widget.label == 'Logan',
    );
    await gesture.moveTo(tester.getCenter(senderAvatar.first));
    await tester.pumpAndSettle();

    final commonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is ui.Avatar && widget.label == 'Common room',
    );
    await gesture.moveTo(tester.getCenter(commonRoomAvatar));
    await tester.pumpAndSettle();

    await tester.tap(find.text('进入房间'));
    await tester.pumpAndSettle();

    expect(openedRoom?.id, 'room_common');
  });

  testWidgets('attachment bubbles keep sender and time outside the bubble', (
    tester,
  ) async {
    final messages = [
      _message(
        type: 'file',
        body: 'report.pdf',
        attachments: const [
          MessageAttachment(
            type: 'file',
            name: 'report.pdf',
            asset: UploadedAsset(
              id: 'asset_report',
              url: '/uploads/report.pdf',
              thumbnailUrl: null,
              mimeType: 'application/pdf',
              filename: 'report.pdf',
              sizeBytes: 2048,
            ),
          ),
        ],
      ),
      _message(
        type: 'audio',
        body: 'voice_1.m4a',
        attachments: const [
          MessageAttachment(
            type: 'audio',
            name: 'voice_1.m4a',
            durationMs: 15000,
            asset: UploadedAsset(
              id: 'asset_voice',
              url: '/uploads/voice_1.m4a',
              thumbnailUrl: null,
              mimeType: 'audio/mp4',
              filename: 'voice_1.m4a',
              sizeBytes: 4096,
            ),
          ),
        ],
      ),
      _message(
        type: 'sticker',
        body: '[表情] wave',
        attachments: const [
          MessageAttachment(
            type: 'sticker',
            name: 'wave',
            asset: UploadedAsset(
              id: 'asset_sticker',
              url: '/stickers/wave.webp',
              thumbnailUrl: '/stickers/wave-thumb.webp',
              mimeType: 'image/webp',
            ),
          ),
        ],
      ),
    ];

    for (final message in messages) {
      await tester.pumpWidget(
        _host(
          MessageBubbleForTest(
            message: message,
            downloadActions: _downloadActions(),
          ),
        ),
      );

      expect(find.text('Logan'), findsNothing);
      expect(
        find.text(message_display.formatMessageTime(message.createdAt)),
        findsNothing,
      );
    }
  });
}

Finder _selectionHandleFades() {
  return find.descendant(
    of: find.byWidgetPredicate(
      (widget) => '${widget.runtimeType}' == '_SelectionHandleOverlay',
    ),
    matching: find.byType(FadeTransition),
  );
}

Widget _host(Widget child, {double? height, TargetPlatform? platform}) {
  return MaterialApp(
    theme: ui.uiTheme().copyWith(platform: platform),
    home: AppConfigScope(
      config: const AppConfig(
        apiBaseUrl: 'https://api.test/api/v1',
        assetBaseUrl: 'https://assets.test',
        releaseBucketUrl: 'https://releases.test/gang-chat',
      ),
      child: Scaffold(
        body: Center(
          child: SizedBox(width: 600, height: height, child: child),
        ),
      ),
    ),
  );
}

Widget _chatPane({
  required TextEditingController controller,
  required List<Message> messages,
  RoomDetail? room,
  LiveState? live,
  bool loading = false,
  int newMessageCount = 0,
  String? focusMessageId,
  ValueChanged<String>? onFocusMessageHandled,
  Future<UserSummary> Function(UserSummary sender)? onResolveSenderProfile,
  ValueChanged<PublicRoom>? onEnterProfileRoom,
  UserProfileActionBuilder? senderProfileActionBuilder,
  ChatMessageActions messageActions = const ChatMessageActions.disabled(),
  List<RoomMember> mentionMembers = const [],
  bool mentionMembersReady = true,
  DateTime? timestampNow,
  VoidCallback? onViewedNewMessages,
  List<MessageQuote> composerQuotes = const [],
  ValueChanged<String>? onRemoveComposerQuote,
}) {
  return ChatPane(
    currentUser: _currentUser,
    timestampNow: timestampNow ?? DateTime.now(),
    roomCard: _roomCard,
    room: room,
    live: live,
    messages: messages,
    newMessageCount: newMessageCount,
    focusMessageId: focusMessageId,
    onFocusMessageHandled: onFocusMessageHandled,
    fileTransfers: const {},
    fileDownloads: const {},
    downloadActions: _downloadActions(),
    voicePlaybackActions: const ChatVoicePlaybackActions.disabled(),
    imagePreviewActions: _imagePreviewActions(),
    messageActions: messageActions,
    loading: loading,
    error: null,
    sending: false,
    sendError: null,
    composerController: controller,
    composerPanelController: ui.ChatComposerController(),
    stickerPanel: const sticker_display.StickerPanelLoadState(),
    voiceState: const voice_display.VoiceRecorderState(),
    composerAttachments: const <composer_attachment.ComposerAttachmentView>[],
    composerQuotes: composerQuotes,
    onRemoveComposerQuote: onRemoveComposerQuote,
    fileActionHighlighted: false,
    mentionMembers: mentionMembers,
    mentionMembersReady: mentionMembersReady,
    onSubmit: (_) {},
    onSendSticker: (_) {},
    onLoadStickers: () {},
    onRefreshStickers: () {},
    onStickerSourceChanged: (_) {},
    onStartVoice: () {},
    onSendVoice: () {},
    onCancelVoice: () {},
    onPickFile: () {},
    onPasteFiles: () async => false,
    onRemoveAttachment: (_) {},
    onRetryAttachment: (_) {},
    onRetry: () {},
    onOpenLiveChannel: () {},
    onOpenRoomMembers: () {},
    onOpenRoomSettings: () {},
    onViewedNewMessages: onViewedNewMessages ?? () {},
    onResolveSenderProfile: onResolveSenderProfile,
    onEnterProfileRoom: onEnterProfileRoom,
    senderProfileActionBuilder: senderProfileActionBuilder,
  );
}

List<Message> _textMessages(int count) {
  return [
    for (var index = 0; index < count; index++)
      _message(
        type: 'text',
        body: 'Message $index',
        clientMessageId: 'client_$index',
        createdAt: DateTime.utc(2026, 6, 11, 9).add(Duration(minutes: index)),
      ),
  ];
}

Message _message({
  String id = 'message_1',
  required String type,
  String body = '',
  List<MessageAttachment> attachments = const [],
  DateTime? createdAt,
  String clientMessageId = 'client_1',
  bool pending = false,
  bool isRecalled = false,
  UserSummary? recalledBy,
  bool isForceDeleted = false,
  UserSummary? forceDeletedBy,
  List<Map<String, Object?>> mentions = const [],
  MessageQuote? quote,
  List<MessageQuote> quotes = const [],
  UserSummary sender = const UserSummary(
    id: 'user_1',
    username: 'logan',
    displayName: 'Logan',
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  ),
}) {
  return Message(
    id: id,
    roomId: 'room_1',
    sender: sender,
    clientMessageId: clientMessageId,
    type: type,
    body: body,
    createdAt: createdAt ?? DateTime.utc(2026, 6, 11),
    attachments: attachments,
    mentions: mentions,
    quote: quote,
    quotes: quotes,
    isRecalled: isRecalled,
    recalledBy: recalledBy,
    isForceDeleted: isForceDeleted,
    forceDeletedBy: forceDeletedBy,
    pending: pending,
  );
}

Message _stickerMessage() {
  return _message(
    type: 'sticker',
    body: '[表情] wave',
    attachments: const [
      MessageAttachment(
        type: 'sticker',
        stickerId: 'sticker_1',
        name: 'wave',
        asset: UploadedAsset(
          id: 'asset_sticker',
          url: '/stickers/wave.webp',
          thumbnailUrl: '/stickers/wave-thumb.webp',
          mimeType: 'image/webp',
        ),
      ),
    ],
  );
}

Message _voiceMessage({required Duration duration}) {
  return _message(
    type: 'audio',
    body: 'voice_1.m4a',
    attachments: [
      MessageAttachment(
        type: 'audio',
        name: 'voice_1.m4a',
        durationMs: duration.inMilliseconds,
        asset: const UploadedAsset(
          id: 'asset_voice',
          url: '/uploads/voice_1.m4a',
          thumbnailUrl: null,
          mimeType: 'audio/mp4',
          filename: 'voice_1.m4a',
          sizeBytes: 4096,
        ),
      ),
    ],
  );
}

Future<void> _showMessageTextContextMenu(
  WidgetTester tester, {
  required TextSelection selection,
}) async {
  final editableTextState = await _selectMessageText(tester, selection);
  expect(editableTextState.showToolbar(), isTrue);
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

Future<EditableTextState> _selectMessageText(
  WidgetTester tester,
  TextSelection selection,
) async {
  await tester.tap(find.byType(TextField));
  await tester.pump();
  final editableTextState = tester.state<EditableTextState>(
    find.byType(EditableText),
  );
  editableTextState.userUpdateTextEditingValue(
    editableTextState.textEditingValue.copyWith(selection: selection),
    SelectionChangedCause.toolbar,
  );
  await tester.pump();
  return editableTextState;
}

Future<void> _secondaryClickAt(WidgetTester tester, Offset location) async {
  final gesture = await tester.createGesture(
    kind: PointerDeviceKind.mouse,
    buttons: kSecondaryMouseButton,
  );
  await gesture.addPointer(location: location);
  await tester.pump();
  await gesture.down(location);
  await tester.pump();
  await gesture.up();
  await gesture.removePointer();
  await tester.pump(const Duration(milliseconds: 100));
}

Future<void> _secondaryClickTextMessage(WidgetTester tester) async {
  await _secondaryClickAt(tester, tester.getCenter(find.byType(TextField)));
}

Future<void> _secondaryClickSticker(WidgetTester tester) async {
  await _secondaryClickAt(
    tester,
    tester.getCenter(find.byType(CachedAssetImage)),
  );
}

void _mockClipboard(List<String> writes) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') {
          writes.add(
            (call.arguments as Map<Object?, Object?>)['text']! as String,
          );
          return null;
        }
        if (call.method == 'Clipboard.hasStrings') {
          return const <String, dynamic>{'value': false};
        }
        return null;
      });
  addTearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });
}

TextSpan? _findTextSpan(InlineSpan span, String text) {
  if (span is! TextSpan) return null;
  if (span.text == text) return span;
  final children = span.children;
  if (children == null) return null;
  for (final child in children) {
    final result = _findTextSpan(child, text);
    if (result != null) return result;
  }
  return null;
}

BoxDecoration _messageBubbleDecoration(
  WidgetTester tester, [
  String id = 'message_1',
]) {
  final surface = tester.widget<AnimatedContainer>(
    find.byKey(ValueKey('message-bubble-surface-$id')),
  );
  return surface.decoration! as BoxDecoration;
}

BoxDecoration _messageBubbleContentDecoration(
  WidgetTester tester, [
  String id = 'message_1',
]) {
  final content = tester.widget<DecoratedBox>(
    find.byKey(ValueKey('message-bubble-content-$id')),
  );
  return content.decoration as BoxDecoration;
}

const _currentUser = CurrentUser(
  id: _currentUserId,
  uid: '1000000',
  username: 'me',
  displayName: 'Me',
  bio: '',
  gender: 'secret',
  email: null,
  emailPublic: false,
  phoneNumber: null,
  phoneNumberPublic: false,
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
  isSuperuser: false,
  createdAt: null,
);

const _currentUserId = 'current_user';

const _systemTarget = UserSummary(
  id: 'user_target',
  username: 'logan',
  displayName: 'Logan',
  avatarUrl: null,
  defaultAvatarKey: 'green-2',
);

const _systemActor = UserSummary(
  id: 'user_actor',
  username: 'owner',
  displayName: 'Owner',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
);

final _roomCard = RoomCard(
  id: 'room_1',
  name: 'Test room',
  avatarUrl: null,
  defaultAvatarKey: 'room-1',
  memberCount: 2,
  liveParticipantCount: 0,
  liveAvatarPreview: const [],
  lastMessage: null,
  unreadCount: 0,
  updatedAt: DateTime(2026, 6, 12),
);

final _roomDetail = RoomDetail(
  id: 'room_1',
  name: 'Test room',
  avatarUrl: null,
  defaultAvatarKey: 'room-1',
  memberCount: 2,
  myMembership: RoomMembership(
    joinedAt: DateTime.utc(2026, 6, 4),
    role: 'member',
  ),
  live: LiveState(
    roomId: 'room_1',
    participantCount: 0,
    participants: const [],
    updatedAt: DateTime.utc(2026, 6, 4),
  ),
  createdAt: DateTime.utc(2026, 6, 4),
  updatedAt: DateTime.utc(2026, 6, 4),
);

RoomDetail _roomDetailFor(String id) {
  return RoomDetail(
    id: id,
    name: 'Test room',
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 2,
    myMembership: RoomMembership(
      joinedAt: DateTime.utc(2026, 6, 4),
      role: 'member',
    ),
    live: LiveState(
      roomId: id,
      participantCount: 0,
      participants: const [],
      updatedAt: DateTime.utc(2026, 6, 4),
    ),
    createdAt: DateTime.utc(2026, 6, 4),
    updatedAt: DateTime.utc(2026, 6, 4),
  );
}

ChatMessageActions _messageActions({
  void Function(Message message)? onReeditRecalledText,
  bool Function(Message message)? canReeditRecalledText,
  bool Function(Message message)? canInspectRecalledText,
}) {
  return ChatMessageActions(
    onCopy: (_, _) async {},
    onDeleteForMe: (_, _) async {},
    onRecall: (_, _) async {},
    canRecall: (_) => false,
    onReeditRecalledText: onReeditRecalledText ?? ((_) {}),
    canReeditRecalledText: canReeditRecalledText ?? ((_) => false),
    canInspectRecalledText: canInspectRecalledText ?? ((_) => false),
  );
}

ChatFileDownloadActions _downloadActions() {
  return ChatFileDownloadActions(
    onDownload: (_, _, _, _) {},
    onPause: (_) {},
    onResume: (_) {},
    onCancel: (_) {},
    onDismiss: (_) {},
  );
}

ChatImagePreviewActions _imagePreviewActions() {
  return ChatImagePreviewActions.disabled();
}
