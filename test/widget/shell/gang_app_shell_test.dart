import 'dart:async';
import 'dart:convert';

import 'package:flutter/gestures.dart'
    show PointerDeviceKind, PointerEnterEvent, kSecondaryMouseButton;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderParagraph;
import 'package:flutter/services.dart'
    show LogicalKeyboardKey, SystemUiOverlayStyle;
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:livekit_client/livekit_client.dart' as lk;
import 'package:shared_preferences/shared_preferences.dart';

import 'package:client/main.dart';
import 'package:client/src/app/app_update.dart';
import 'package:client/src/app/audio_device_info.dart';
import 'package:client/src/app/audio_device_store.dart';
import 'package:client/src/app/authenticated_app_context.dart';
import 'package:client/src/app/auth_session_controller.dart';
import 'package:client/src/app/close_behavior.dart';
import 'package:client/src/app/email_verification_controller.dart';
import 'package:client/src/app/login_account_history.dart';
import 'package:client/src/app/password_reset_controller.dart';
import 'package:client/src/app/live_session_controller.dart';
import 'package:client/src/app/live_presence_announcement.dart';
import 'package:client/src/app/realtime_controller.dart';
import 'package:client/src/app/room_display.dart' as room_display;
import 'package:client/src/app/settings_about.dart';
import 'package:client/src/app/settings_controller.dart';
import 'package:client/src/app/settings_shell_state.dart';
import 'package:client/src/app/server_clock.dart';
import 'package:client/src/auth/auth_client.dart';
import 'package:client/src/auth/token_store.dart';
import 'package:client/src/live/audio_device_service.dart';
import 'package:client/src/live/live_session.dart';
import 'package:client/src/live/live_presence_sound_service.dart';
import 'package:client/src/live/live_video_track_view.dart';
import 'package:client/src/live/system_audio_devices.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/protocol/sticker_pack_store.dart';
import 'package:client/src/settings/settings_page.dart';
import 'package:client/src/shell/desktop_window_controller.dart';
import 'package:client/src/shell/android_system_service.dart';
import 'package:client/src/shell/feedback_mail_service.dart';
import 'package:client/src/shell/install_info_service.dart';
import 'package:client/src/shell/login_page.dart';
import 'package:client/src/shell/message_notification_sound_service.dart';
import 'package:client/src/shell/release_update_service.dart';
import 'package:client/src/ui/ui.dart' as ui;
import 'package:client/src/home/hover_card_anchor.dart';
import 'package:client/src/home/chat_pane.dart';
import 'package:client/src/home/home_page.dart';
import 'package:client/src/home/live_channel_pane.dart' as live_pane;
import 'package:client/src/home/room_profile_card.dart';
import 'package:client/ui_showcase.dart' as showcase;

part 'gang_app_shell_test_parts/live_hover_widget_tests.dart';
part 'gang_app_shell_test_parts/auth_widget_tests.dart';
part 'gang_app_shell_test_parts/home_widget_tests.dart';
part 'gang_app_shell_test_parts/settings_widget_tests.dart';
part 'gang_app_shell_test_parts/room_management_widget_tests.dart';
part 'gang_app_shell_test_parts/realtime_live_widget_tests.dart';
part 'gang_app_shell_test_parts/auth_smoke_widget_tests.dart';
part 'gang_app_shell_test_parts/ui_showcase_widget_tests.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  registerShellLiveHoverWidgetTests();
  registerShellAuthWidgetTests();
  registerShellHomeWidgetTests();
  registerShellSettingsWidgetTests();
  registerShellRoomManagementWidgetTests();
  registerShellRealtimeLiveWidgetTests();
  registerShellAuthSmokeWidgetTests();
  registerShellUiShowcaseWidgetTests();
}

Color _expectedShadowForBackground(Color background) {
  return Color.lerp(background, Colors.black, 0.46)!;
}

List<BoxDecoration> _inputLayerDecorations(
  WidgetTester tester,
  Finder inputFinder,
) {
  return tester
      .widgetList<DecoratedBox>(
        find.descendant(of: inputFinder, matching: find.byType(DecoratedBox)),
      )
      .where((box) {
        final decoration = box.decoration;
        return decoration is BoxDecoration && decoration.border is Border;
      })
      .map((box) => box.decoration as BoxDecoration)
      .toList();
}

Color _topBorderColor(BoxDecoration decoration) {
  return (decoration.border as Border).top.color;
}

Finder _textFieldWithHint(String hintText) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.hintText == hintText,
  );
}

Finder _roomSettingsTextField(String field) {
  return find.descendant(
    of: find.byKey(ValueKey('room-settings-$field-input')),
    matching: find.byType(TextField),
  );
}

Finder _highlightedSearchText(String text) {
  return find.byWidgetPredicate(
    (widget) => widget is ui.HighlightedText && widget.text.contains(text),
  );
}

Finder _buttonIconWithTooltip(String tooltip) {
  return find.byWidgetPredicate(
    (widget) => widget is ui.ButtonIcon && widget.tooltip == tooltip,
  );
}

Finder _liveControl(String id) {
  return find.byKey(ValueKey<String>('live-control:$id'));
}

Future<void> _pumpUntilFound(
  WidgetTester tester,
  Finder finder, {
  int maxPumps = 40,
}) async {
  for (var attempt = 0; attempt < maxPumps; attempt += 1) {
    await tester.pump(const Duration(milliseconds: 25));
    if (finder.evaluate().isNotEmpty) return;
  }
  throw TestFailure('Timed out waiting for the target widget.');
}

Future<void> _openLiveChannelFromHeader(WidgetTester tester) async {
  final header = find.byKey(const ValueKey('chat-header-live-button'));
  expect(header, findsOneWidget);
  await tester.tap(header);
  await tester.pumpAndSettle();
}

void _expectLiveVolumeFill(WidgetTester tester, String label, double volume) {
  final slider = find.byKey(ValueKey<String>('live-volume-slider:$label'));
  final thumb = find.byKey(ValueKey<String>('live-volume-thumb:$label'));
  final fill = find.byKey(ValueKey<String>('live-volume-fill:$label'));
  expect(slider, findsOneWidget);
  expect(thumb, findsOneWidget);
  expect(fill, findsOneWidget);

  final sliderRect = tester.getRect(slider);
  final thumbRect = tester.getRect(thumb);
  final fillRect = tester.getRect(fill);
  final expectedHeight = (sliderRect.height - thumbRect.height) * volume;
  expect(fillRect.height, closeTo(expectedHeight, 1.0));
}

void _expectRectCloseTo(Rect actual, Rect expected) {
  expect(actual.left, closeTo(expected.left, 0.01));
  expect(actual.top, closeTo(expected.top, 0.01));
  expect(actual.right, closeTo(expected.right, 0.01));
  expect(actual.bottom, closeTo(expected.bottom, 0.01));
}

double _submitBottomGap(WidgetTester tester, {required String submitLabel}) {
  final surfaceRect = tester.getRect(
    find.byKey(const ValueKey('auth-surface')),
  );
  final submitRect = tester.getRect(
    find.widgetWithText(ui.Button, submitLabel),
  );
  return surfaceRect.bottom - submitRect.bottom;
}

Icon _rememberPasswordCheckIcon(WidgetTester tester) {
  return tester.widget<Icon>(
    find.descendant(
      of: find.byType(ui.UiCheckbox),
      matching: find.byIcon(Icons.check_rounded),
    ),
  );
}

void _expectSubmitButtonFullWidth(
  WidgetTester tester, {
  required String submitLabel,
}) {
  final inputRect = tester.getRect(find.byType(ui.Input).first);
  final submitRect = tester.getRect(
    find.widgetWithText(ui.Button, submitLabel),
  );
  expect(submitRect.left, closeTo(inputRect.left, 0.01));
  expect(submitRect.right, closeTo(inputRect.right, 0.01));
}

AuthenticatedAppContext _homeTestAppContext({
  Future<void> Function()? onLogout,
  Future<void> Function()? onExitSessionForAppExit,
  List<String>? requestedPaths,
  List<Uri>? requestedUris,
  List<Map<String, Object?>>? accountUpdates,
  List<Map<String, Object?>>? roomCreations,
  List<Map<String, Object?>>? roomSettingsUpdates,
  List<Map<String, Object?>>? myRoomSettingsUpdates,
  List<Map<String, Object?>>? liveJoinRequests,
  List<Map<String, Object?>>? liveStateUpdates,
  List<String?>? liveScreenViewUpdates,
  List<String>? liveOperationLog,
  List<String>? liveModerationActions,
  String currentRoomRole = 'owner',
  String currentRoomJoinPolicy = 'approval_required',
  bool currentUserIsSuperuser = false,
  String secondaryMemberRole = 'member',
  bool includeActionComparisonMember = false,
  bool includeUnreadRoomNotification = false,
  bool includeFreshRoomNotificationOnRefresh = false,
  bool pinAlphaRoom = false,
  String alphaRoomName = 'Alpha Room',
  int alphaRoomUnreadCount = 3,
  bool alphaRoomHasPendingJoinRequests = false,
  bool alphaRoomAiVoiceAnnouncementsEnabled = false,
  Future<void> Function(String roomId)? beforeRoomDetailResponse,
  Future<void> Function(Map<String, Object?> update)? beforeLiveStateResponse,
}) {
  final user = CurrentUser(
    id: 'user-1',
    uid: 'uid-1',
    username: 'kai',
    displayName: 'Kai',
    bio: '',
    gender: 'secret',
    email: 'kai@example.com',
    emailPublic: false,
    phoneNumber: null,
    phoneNumberPublic: false,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    isSuperuser: currentUserIsSuperuser,
    createdAt: DateTime.utc(2026),
  );

  return AuthenticatedAppContext(
    session: AuthSession(
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      accessTokenExpiresAt: DateTime.utc(2026, 1, 1, 1),
      user: user,
    ),
    apiBaseUrl: 'http://localhost:3000',
    accessTokenProvider: ({bool forceRefresh = false}) async => 'access-token',
    serverClock: ServerClock(localNow: () => DateTime.utc(2026, 6, 12, 2)),
    logout: onLogout ?? () async {},
    exitSessionForAppExit: onExitSessionForAppExit ?? () async {},
    api: _roomsApi(
      requestedPaths: requestedPaths,
      requestedUris: requestedUris,
      accountUpdates: accountUpdates,
      roomCreations: roomCreations,
      roomSettingsUpdates: roomSettingsUpdates,
      myRoomSettingsUpdates: myRoomSettingsUpdates,
      liveJoinRequests: liveJoinRequests,
      liveStateUpdates: liveStateUpdates,
      liveScreenViewUpdates: liveScreenViewUpdates,
      liveOperationLog: liveOperationLog,
      liveModerationActions: liveModerationActions,
      currentRoomRole: currentRoomRole,
      currentRoomJoinPolicy: currentRoomJoinPolicy,
      secondaryMemberRole: secondaryMemberRole,
      includeActionComparisonMember: includeActionComparisonMember,
      includeUnreadRoomNotification: includeUnreadRoomNotification,
      includeFreshRoomNotificationOnRefresh:
          includeFreshRoomNotificationOnRefresh,
      pinAlphaRoom: pinAlphaRoom,
      initialAlphaRoomName: alphaRoomName,
      alphaRoomUnreadCount: alphaRoomUnreadCount,
      alphaRoomHasPendingJoinRequests: alphaRoomHasPendingJoinRequests,
      initialAlphaRoomAiVoiceAnnouncementsEnabled:
          alphaRoomAiVoiceAnnouncementsEnabled,
      beforeRoomDetailResponse: beforeRoomDetailResponse,
      beforeLiveStateResponse: beforeLiveStateResponse,
    ),
  );
}

GangApi _roomsApi({
  List<String>? requestedPaths,
  List<Uri>? requestedUris,
  List<Map<String, Object?>>? accountUpdates,
  List<Map<String, Object?>>? roomCreations,
  List<Map<String, Object?>>? roomSettingsUpdates,
  List<Map<String, Object?>>? myRoomSettingsUpdates,
  List<Map<String, Object?>>? liveJoinRequests,
  List<Map<String, Object?>>? liveStateUpdates,
  List<String?>? liveScreenViewUpdates,
  List<String>? liveOperationLog,
  List<String>? liveModerationActions,
  String currentRoomRole = 'owner',
  String currentRoomJoinPolicy = 'approval_required',
  String secondaryMemberRole = 'member',
  bool includeActionComparisonMember = false,
  bool includeUnreadRoomNotification = false,
  bool includeFreshRoomNotificationOnRefresh = false,
  bool pinAlphaRoom = false,
  String initialAlphaRoomName = 'Alpha Room',
  int alphaRoomUnreadCount = 3,
  bool alphaRoomHasPendingJoinRequests = false,
  bool initialAlphaRoomAiVoiceAnnouncementsEnabled = false,
  Future<void> Function(String roomId)? beforeRoomDetailResponse,
  Future<void> Function(Map<String, Object?> update)? beforeLiveStateResponse,
}) {
  var roomNotificationsMarkedRead = false;
  var actionComparisonMemberRole = 'member';
  var alphaRoomName = initialAlphaRoomName;
  var alphaRoomDescription = '';
  var alphaRoomVisibility = 'private';
  var alphaRoomJoinPolicy = currentRoomJoinPolicy;
  var alphaRoomAiVoiceAnnouncementsEnabled =
      initialAlphaRoomAiVoiceAnnouncementsEnabled;
  var alphaRoomNotificationPolicy = 'all';
  var liveMicMuted = false;
  var liveHeadphonesMuted = false;
  var liveCameraOn = false;
  var liveCameraMirrored = false;
  var liveScreenSharing = false;
  return GangApiClient(
    baseUrl: 'http://example.test/api/v1',
    accessTokenProvider: ({bool forceRefresh = false}) async => 'access-token',
    httpClient: MockClient((request) async {
      requestedPaths?.add(request.url.path);
      requestedUris?.add(request.url);
      if (request.url.path == '/api/v1/rooms') {
        if (request.method == 'POST') {
          final body =
              jsonDecode(utf8.decode(request.bodyBytes))
                  as Map<String, Object?>;
          final created = {
            ...body,
            'id': 'server-created-${(roomCreations?.length ?? 0) + 1}',
          };
          roomCreations?.add(created);
          return _jsonResponse({
            'room': _roomDetailJson(
              id: created['id']! as String,
              name: body['name']! as String,
              memberCount: 1,
              onlineMemberCount: 1,
              liveParticipantCount: 0,
              description: body['description'] as String? ?? '',
              visibility: body['visibility'] as String? ?? 'public',
              joinPolicy: body['join_policy'] as String? ?? 'approval_required',
              aiVoiceAnnouncementsEnabled:
                  body['ai_voice_announcements_enabled'] as bool? ?? false,
            ),
          });
        }
        return _jsonResponse({
          'rooms': [
            for (final created in roomCreations ?? const [])
              _roomCardJson(
                id: created['id']! as String,
                name: created['name']! as String,
                memberCount: 1,
              ),
            ..._serverListJson(
              currentRoomJoinPolicy: currentRoomJoinPolicy,
              pinAlphaRoom: pinAlphaRoom,
              alphaRoomName: initialAlphaRoomName,
              alphaRoomUnreadCount: alphaRoomUnreadCount,
              alphaRoomHasPendingJoinRequests: alphaRoomHasPendingJoinRequests,
            ),
          ],
        });
      }
      if (request.url.path == '/api/v1/search') {
        final query = request.url.queryParameters['q']?.toLowerCase() ?? '';
        Map<String, Object?> pagedMessage(int index) {
          return {
            'room': _searchRoomContextJson(
              id: 'server-page',
              name: 'Paged Room',
            ),
            'message': _messageJson(
              id: 'msg-page-$index',
              roomId: 'server-page',
              sender: _userJson(
                id: 'user-2',
                username: 'morgan',
                displayName: 'Morgan',
              ),
              clientMessageId: 'client-msg-page-$index',
              body: 'Page result $index',
            ),
          };
        }

        if (query == 'page') {
          if (request.url.queryParameters['messages_cursor'] ==
              'message-cursor-8') {
            return _jsonResponse({
              'my_rooms': <Object?>[],
              'public_rooms': <Object?>[],
              'messages': [pagedMessage(9)],
              'files': <Object?>[],
              'next_cursors': {
                'my_rooms': null,
                'public_rooms': null,
                'messages': null,
                'files': null,
              },
              'total_counts': {
                'my_rooms': 0,
                'public_rooms': 0,
                'messages': 9,
                'files': 0,
              },
            });
          }
          return _jsonResponse({
            'my_rooms': <Object?>[],
            'public_rooms': <Object?>[],
            'messages': [
              for (var index = 1; index <= 8; index += 1) pagedMessage(index),
            ],
            'files': <Object?>[],
            'next_cursors': {
              'my_rooms': null,
              'public_rooms': null,
              'messages': 'message-cursor-8',
              'files': null,
            },
            'total_counts': {
              'my_rooms': 0,
              'public_rooms': 0,
              'messages': 9,
              'files': 0,
            },
          });
        }
        if (query == '1') {
          return _jsonResponse({
            'my_rooms': [
              {
                ..._roomCardJson(
                  id: 'server-beta',
                  name: 'Beta Room',
                  memberCount: 5,
                ),
                'description': '12345',
              },
            ],
            'public_rooms': <Object?>[],
            'messages': <Object?>[],
            'files': <Object?>[],
            'next_cursors': {
              'my_rooms': null,
              'public_rooms': null,
              'messages': null,
              'files': null,
            },
          });
        }
        return _jsonResponse({
          'my_rooms': [
            _roomCardJson(id: 'server-beta', name: 'Beta Room', memberCount: 5),
          ],
          'public_rooms': [
            {
              ..._roomCardJson(
                id: 'server-public',
                name: 'Beta Public',
                memberCount: 2,
              ),
              'visibility': 'public',
              'join_policy': 'open',
              'joined': false,
              'join_state': 'none',
            },
          ],
          'messages': [
            {
              'room': _searchRoomContextJson(
                id: 'server-beta',
                name: 'Beta Room',
              ),
              'message': _messageJson(
                id: 'msg-beta',
                roomId: 'server-beta',
                sender: _userJson(
                  id: 'user-2',
                  username: 'morgan',
                  displayName: 'Morgan',
                ),
                clientMessageId: 'client-msg-beta',
                body: 'Beta release notes',
              ),
            },
          ],
          'files': [
            {
              'room': _searchRoomContextJson(
                id: 'server-beta',
                name: 'Beta Room',
              ),
              'message': {
                ..._messageJson(
                  id: 'msg-file-beta',
                  roomId: 'server-beta',
                  sender: _currentUserJson,
                  clientMessageId: 'client-msg-file-beta',
                  body: 'Beta brief.pdf',
                ),
                'type': 'file',
                'attachments': [
                  {
                    'type': 'file',
                    'name': 'Beta brief.pdf',
                    'asset': {
                      'id': 'asset-beta',
                      'url': '/assets/beta.pdf',
                      'thumbnail_url': null,
                      'mime_type': 'application/pdf',
                      'filename': 'Beta brief.pdf',
                      'size_bytes': 1024,
                    },
                  },
                ],
              },
            },
          ],
          'next_cursors': {
            'my_rooms': null,
            'public_rooms': null,
            'messages': null,
            'files': null,
          },
        });
      }
      if (request.url.path.startsWith('/api/v1/rooms/') &&
          request.url.path.endsWith('/read')) {
        expect(request.method, 'POST');
        final body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        expect(body['last_read_message_id'], isA<String>());
        return _jsonResponse({'ok': true, 'unread_count': 0});
      }
      if (request.url.path == '/api/v1/rooms/server-beta') {
        await beforeRoomDetailResponse?.call('server-beta');
        return _jsonResponse({
          'room': _roomDetailJson(
            id: 'server-beta',
            name: 'Beta Room',
            memberCount: 5,
            onlineMemberCount: 1,
            liveParticipantCount: 0,
            visibility: 'private',
            joinPolicy: 'closed',
            role: 'member',
            createdBy: _userJson(
              id: 'user-2',
              username: 'morgan',
              displayName: 'Morgan',
              isOnline: true,
            ),
          ),
        });
      }
      if (request.url.path ==
          '/api/v1/rooms/server-beta/members/user-2/profile') {
        return _jsonResponse({
          'profile': {
            'user': {
              ..._userJson(
                id: 'user-2',
                username: 'morgan',
                displayName: 'Morgan',
                isOnline: true,
              ),
              'bio': 'Creator profile',
              'common_rooms': [
                {
                  'id': 'server-alpha',
                  'rid': 'server-alpha',
                  'name': 'Shared Alpha',
                  'visibility': 'private',
                  'default_avatar_key': 'room-1',
                  'room_role': 'member',
                },
              ],
            },
            'room_display_name': 'Morgan Creator',
            'role': 'owner',
            'joined_at': '2026-06-01T00:00:00Z',
          },
        });
      }
      if (request.url.path ==
          '/api/v1/rooms/server-alpha/members/user-2/profile') {
        return _jsonResponse({
          'profile': {
            'user': _userJson(
              id: 'user-2',
              username: 'morgan',
              displayName: 'Morgan Account',
              uid: 'uid-2',
              isOnline: true,
            )..['room_display_name'] = 'Morgan',
            'role': 'member',
            'joined_at': '2026-06-01T00:00:00Z',
          },
        });
      }
      if (request.url.path == '/api/v1/rooms/server-public/join') {
        return _jsonResponse({
          'room': _roomDetailJson(
            id: 'server-public',
            name: 'Beta Public',
            memberCount: 3,
            onlineMemberCount: 1,
            liveParticipantCount: 0,
            visibility: 'public',
            joinPolicy: 'open',
            role: 'member',
          ),
        });
      }
      if (request.url.path == '/api/v1/rooms/server-public') {
        return _jsonResponse({
          'room': _roomDetailJson(
            id: 'server-public',
            name: 'Beta Public',
            memberCount: 3,
            onlineMemberCount: 1,
            liveParticipantCount: 0,
            visibility: 'public',
            joinPolicy: 'open',
            role: 'member',
          ),
        });
      }
      if (request.url.path == '/api/v1/rooms/server-public/messages') {
        return _jsonResponse({
          'messages': [
            _messageJson(
              id: 'msg-public-history',
              roomId: 'server-public',
              sender: _userJson(
                id: 'user-2',
                username: 'morgan',
                displayName: 'Morgan',
              ),
              clientMessageId: 'client-msg-public-history',
              body: 'History visible immediately after join',
            ),
          ],
          'has_more': false,
          'next_before': null,
        });
      }
      if (request.url.path == '/api/v1/rooms/server-public/live') {
        return _jsonResponse({
          'live': _liveStateJson(roomId: 'server-public', participantCount: 0),
        });
      }
      if (request.url.path == '/api/v1/users/me/account') {
        final body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        accountUpdates?.add(body);
        return _jsonResponse({
          'user': {..._currentUserJson, ...body},
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/me') {
        expect(request.method, 'PATCH');
        final body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        alphaRoomNotificationPolicy =
            body['notification_policy'] as String? ??
            alphaRoomNotificationPolicy;
        alphaRoomAiVoiceAnnouncementsEnabled =
            body['ai_voice_announcements_enabled'] as bool? ??
            alphaRoomAiVoiceAnnouncementsEnabled;
        myRoomSettingsUpdates?.add(body);
        return _jsonResponse({
          'room': {
            ..._roomDetailJson(
              id: 'server-alpha',
              name: alphaRoomName,
              memberCount: 2,
              onlineMemberCount: 1,
              liveParticipantCount: 1,
              description: alphaRoomDescription,
              visibility: alphaRoomVisibility,
              joinPolicy: alphaRoomJoinPolicy,
              aiVoiceAnnouncementsEnabled: alphaRoomAiVoiceAnnouncementsEnabled,
              role: currentRoomRole,
            ),
            'notification_policy': alphaRoomNotificationPolicy,
          },
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha') {
        if (request.method == 'PATCH') {
          final body =
              jsonDecode(utf8.decode(request.bodyBytes))
                  as Map<String, Object?>;
          roomSettingsUpdates?.add(body);
          alphaRoomName = body['name'] as String? ?? alphaRoomName;
          alphaRoomDescription =
              body['description'] as String? ?? alphaRoomDescription;
          alphaRoomVisibility =
              body['visibility'] as String? ?? alphaRoomVisibility;
          alphaRoomJoinPolicy =
              body['join_policy'] as String? ?? alphaRoomJoinPolicy;
          alphaRoomAiVoiceAnnouncementsEnabled =
              body['ai_voice_announcements_enabled'] as bool? ??
              alphaRoomAiVoiceAnnouncementsEnabled;
          return _jsonResponse({
            'room': {
              ..._roomDetailJson(
                id: 'server-alpha',
                name: alphaRoomName,
                memberCount: 2,
                onlineMemberCount: 1,
                liveParticipantCount: 1,
                description: alphaRoomDescription,
                visibility: alphaRoomVisibility,
                joinPolicy: alphaRoomJoinPolicy,
                aiVoiceAnnouncementsEnabled:
                    alphaRoomAiVoiceAnnouncementsEnabled,
              ),
              'notification_policy': alphaRoomNotificationPolicy,
            },
          });
        }
        await beforeRoomDetailResponse?.call('server-alpha');
        return _jsonResponse({
          'room': {
            ..._roomDetailJson(
              id: 'server-alpha',
              name: alphaRoomName,
              memberCount: 2,
              onlineMemberCount: 1,
              liveParticipantCount: 1,
              description: alphaRoomDescription,
              visibility: alphaRoomVisibility,
              joinPolicy: alphaRoomJoinPolicy,
              aiVoiceAnnouncementsEnabled: alphaRoomAiVoiceAnnouncementsEnabled,
              role: currentRoomRole,
            ),
            'notification_policy': alphaRoomNotificationPolicy,
          },
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/members') {
        return _jsonResponse({
          'members': [
            _roomMemberJson(user: _currentUserJson, role: currentRoomRole),
            _roomMemberJson(
              user: _userJson(
                id: 'user-2',
                username: 'morgan',
                displayName: 'Morgan Account',
                uid: 'uid-2',
                isOnline: true,
              )..['room_display_name'] = 'Morgan',
              role: secondaryMemberRole,
            ),
            if (includeActionComparisonMember)
              _roomMemberJson(
                user: _userJson(
                  id: 'user-5',
                  username: 'taylor',
                  displayName: 'Taylor',
                  uid: 'uid-5',
                  isOnline: true,
                ),
                role: actionComparisonMemberRole,
              ),
          ],
          'next_cursor': null,
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/members/user-2') {
        if (request.method == 'PATCH') {
          return _jsonResponse({
            'member': _roomMemberJson(
              user: _userJson(
                id: 'user-2',
                username: 'morgan',
                displayName: 'Morgan',
                isOnline: true,
              ),
              role: 'admin',
            ),
          });
        }
        if (request.method == 'DELETE') {
          return _jsonResponse({'ok': true});
        }
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/members/user-5') {
        if (request.method == 'PATCH') {
          actionComparisonMemberRole = 'admin';
          return _jsonResponse({
            'member': _roomMemberJson(
              user: _userJson(
                id: 'user-5',
                username: 'taylor',
                displayName: 'Taylor',
                uid: 'uid-5',
                isOnline: true,
              ),
              role: 'admin',
            ),
          });
        }
      }
      if (request.url.path ==
          '/api/v1/rooms/server-alpha/live/participants/user-2/moderation') {
        expect(request.method, 'POST');
        final body = jsonDecode(request.body) as Map<String, Object?>;
        liveModerationActions?.add(body['action']! as String);
        expect(
          body['action'],
          isIn([
            'kick',
            'mute_mic',
            'block_voice',
            'restore_voice',
            'restore_headphones',
          ]),
        );
        return _jsonResponse({'ok': true});
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/join-requests') {
        return _jsonResponse({
          'requests': [
            _joinRequestJson(
              id: 'join-request-riley',
              reason: 'Please approve my request',
              user: _userJson(
                id: 'user-3',
                username: 'riley',
                displayName: 'Riley',
                uid: '10000001',
                isOnline: true,
              ),
            ),
          ],
        });
      }
      if (request.url.path ==
          '/api/v1/rooms/server-alpha/join-requests/join-request-riley') {
        return _jsonResponse({});
      }
      if (request.url.path == '/api/v1/users/user-3/settings') {
        return _jsonResponse({
          'user': {
            ..._currentUserJson,
            'id': 'user-3',
            'uid': '10000003',
            'username': 'riley',
            'display_name': 'Riley',
            'email': 'riley@example.com',
            'is_superuser': false,
          },
        });
      }
      if (request.url.path == '/api/v1/users/user-3/sessions') {
        return _jsonResponse([]);
      }
      if (request.url.path == '/api/v1/users/user-3/audio-settings') {
        return _jsonResponse({
          'audio_settings': {
            'default_audio_input_volume': 100,
            'default_audio_output_volume': 100,
            'live_mic_input_volume': 100,
            'live_voice_output_volume': 100,
            'live_screen_share_output_volume': 100,
            'live_music_output_volume': 100,
          },
        });
      }
      if (request.url.path == '/api/v1/users/search') {
        return _jsonResponse({
          'users': [
            _userJson(
              id: 'user-3',
              username: 'riley',
              displayName: 'Riley',
              isOnline: true,
            ),
            _userJson(
              id: 'user-5',
              username: 'river',
              displayName: 'River',
              isOnline: true,
            ),
            _userJson(
              id: 'user-6',
              username: 'rina',
              displayName: 'Rina',
              isOnline: true,
            ),
            _userJson(
              id: 'user-7',
              username: 'riko',
              displayName: 'Riko',
              isOnline: true,
            ),
            _userJson(
              id: 'user-8',
              username: 'rita',
              displayName: 'Rita',
              isOnline: true,
            ),
          ],
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/invites') {
        return _jsonResponse({'invite': _roomInviteJson(id: 'invite-riley')});
      }
      if (request.url.path == '/api/v1/room-invites') {
        return _jsonResponse({
          'invites': [
            _roomInviteJson(
              joinPolicy: 'approval_required',
              inviter: _userJson(
                id: 'user-2',
                username: 'morgan',
                displayName: 'Morgan',
              ),
              inviterRoomRole: 'member',
              inviterRoomDisplayName: 'Morgan Member',
            ),
            _roomInviteJson(
              id: 'invite-invalid',
              inviter: _userJson(
                id: 'user-left',
                username: 'lefty',
                displayName: 'Lefty',
              ),
              inviterRoomRole: 'left',
              inviterRoomDisplayName: 'Lefty',
              invalidReason: 'inviter_left',
            ),
          ],
          'next_cursor': null,
        });
      }
      if (request.url.path == '/api/v1/room-applications') {
        return _jsonResponse({
          'applications': [
            _roomApplicationJson(id: 'application-alpha'),
            _roomApplicationJson(
              id: 'application-approved',
              status: 'approved',
              createdAt: '2026-06-04T08:00:00Z',
              updatedAt: '2026-06-07T08:00:00Z',
              reviewedAt: '2026-06-07T08:00:00Z',
              reviewer: {
                ..._userJson(id: 'user-4', username: 'ivy', displayName: 'Ivy'),
                'room_display_name': 'Ivy Owner',
                'room_role': 'owner',
              },
            ),
          ],
          'next_cursor': null,
        });
      }
      if (request.url.path == '/api/v1/room-notifications') {
        return _jsonResponse({
          'notifications': [
            if (includeUnreadRoomNotification)
              _roomEventNotificationJson(
                readAt: roomNotificationsMarkedRead
                    ? '2026-06-07T09:00:00Z'
                    : null,
              ),
            if (includeFreshRoomNotificationOnRefresh &&
                roomNotificationsMarkedRead)
              _roomEventNotificationJson(id: 'room-event-fresh'),
          ],
          'next_cursor': null,
        });
      }
      if (request.url.path == '/api/v1/room-notifications/read') {
        roomNotificationsMarkedRead = true;
        return _jsonResponse({'ok': true});
      }
      if (request.url.path == '/api/v1/room-applications/application-alpha') {
        return _jsonResponse({
          'ok': true,
          'application': _roomApplicationJson(
            id: 'application-alpha',
            status: 'withdrawn',
          ),
        });
      }
      if (request.url.path == '/api/v1/room-invites/invite-alpha') {
        return _jsonResponse({
          'ok': true,
          'invite': _roomInviteJson(status: 'accepted'),
          'join_request': {
            'id': 'join-request-alpha',
            'room_id': 'server-alpha',
            'status': 'pending',
            'reason': 'I was invited',
            'created_at': '2026-06-05T08:00:00Z',
          },
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/messages') {
        if (request.method == 'POST') {
          final body =
              jsonDecode(utf8.decode(request.bodyBytes))
                  as Map<String, Object?>;
          return _jsonResponse({
            'message': _messageJson(
              id: 'msg-sent',
              roomId: 'server-alpha',
              sender: _currentUserJson,
              clientMessageId: body['client_message_id']! as String,
              body: body['body']! as String,
            ),
          });
        }
        return _jsonResponse({
          'messages': [
            _messageJson(
              id: 'msg-1',
              roomId: 'server-alpha',
              sender: _userJson(
                id: 'user-2',
                username: 'morgan',
                displayName: 'Morgan',
                uid: 'uid-2',
                isOnline: true,
              ),
              clientMessageId: 'client-msg-1',
              body: 'Hello from Morgan',
            ),
            _messageJson(
              id: 'msg-2',
              roomId: 'server-alpha',
              sender: _currentUserJson,
              clientMessageId: 'client-msg-2',
              body: 'Reply from Kai',
            ),
          ],
          'has_more': false,
          'next_before': null,
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/message-history') {
        final morgan = _userJson(
          id: 'user-2',
          username: 'morgan',
          displayName: 'Morgan Account',
          uid: 'uid-2',
          isOnline: true,
        )..['room_display_name'] = 'Morgan';
        return _jsonResponse({
          'messages': [
            _messageJson(
              id: 'msg-1',
              roomId: 'server-alpha',
              sender: morgan,
              clientMessageId: 'client-msg-1',
              body: 'Hello from Morgan',
            ),
            _messageJson(
              id: 'msg-2',
              roomId: 'server-alpha',
              sender: _currentUserJson,
              clientMessageId: 'client-msg-2',
              body: 'Reply from Kai',
            ),
            _messageJson(
              id: 'msg-system',
              roomId: 'server-alpha',
              sender: _currentUserJson,
              clientMessageId: 'client-msg-system',
              type: 'system',
              body: 'Morgan 加入了房间',
              attachments: [
                {
                  'type': 'system',
                  'event': 'room_member_joined',
                  'user': morgan,
                },
              ],
            ),
          ],
          'has_more': false,
          'next_before': null,
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/live') {
        return _jsonResponse({
          'live': _liveStateJson(
            roomId: 'server-alpha',
            participantCount: 1,
            participants: [
              _liveParticipantJson(
                user: _userJson(
                  id: 'user-2',
                  username: 'morgan',
                  displayName: 'Morgan',
                ),
                liveSessionId: 'live-session-morgan',
                micMuted: true,
              ),
            ],
          ),
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/live/join') {
        liveOperationLog?.add('join');
        final body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        liveJoinRequests?.add(body);
        if (body['mic_muted'] case final bool value) {
          liveMicMuted = value;
        }
        if (body['headphones_muted'] case final bool value) {
          liveHeadphonesMuted = value;
        }
        final participant = _liveParticipantJson(
          user: _currentUserJson,
          liveSessionId: 'live-session-joined',
          micMuted: liveMicMuted,
          headphonesMuted: liveHeadphonesMuted,
          cameraOn: liveCameraOn,
          cameraMirrored: liveCameraMirrored,
          screenSharing: liveScreenSharing,
        );
        return _jsonResponse({
          'livekit': {
            'server_url': 'ws://live.example.test',
            'token': 'live-token',
            'token_expires_at': '2026-06-05T09:00:00Z',
            'room_name': 'server-alpha',
          },
          'participant': participant,
          'live': _liveStateJson(
            roomId: 'server-alpha',
            participantCount: 2,
            participants: [
              participant,
              _liveParticipantJson(
                user: _userJson(
                  id: 'user-2',
                  username: 'morgan',
                  displayName: 'Morgan',
                ),
                liveSessionId: 'live-session-morgan',
                micMuted: true,
              ),
            ],
          ),
        });
      }
      if (request.url.path == '/api/v1/rooms/server-alpha/live/me') {
        final body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        liveOperationLog?.add(
          'state:${body['connection_state'] ?? 'unchanged'}',
        );
        liveStateUpdates?.add(body);
        await beforeLiveStateResponse?.call(body);
        if (body['mic_muted'] case final bool value) {
          liveMicMuted = value;
        }
        if (body['headphones_muted'] case final bool value) {
          liveHeadphonesMuted = value;
        }
        if (body['camera_on'] case final bool value) {
          liveCameraOn = value;
        }
        if (body['camera_mirrored'] case final bool value) {
          liveCameraMirrored = value;
        }
        if (body['screen_sharing'] case final bool value) {
          liveScreenSharing = value;
        }
        return _jsonResponse({
          'participant': _liveParticipantJson(
            user: _currentUserJson,
            liveSessionId: 'live-session-joined',
            micMuted: liveMicMuted,
            headphonesMuted: liveHeadphonesMuted,
            cameraOn: liveCameraOn,
            cameraMirrored: liveCameraMirrored,
            screenSharing: liveScreenSharing,
          ),
        });
      }
      if (request.url.path ==
          '/api/v1/rooms/server-alpha/live/me/screen-view') {
        expect(request.method, 'PATCH');
        final body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        final broadcasterUserId = body['broadcaster_user_id'] as String?;
        liveOperationLog?.add('screen-view:${broadcasterUserId ?? ''}');
        liveScreenViewUpdates?.add(
          broadcasterUserId == null || broadcasterUserId.isEmpty
              ? null
              : broadcasterUserId,
        );
        return _jsonResponse(const <String, Object?>{});
      }
      if (request.url.path == '/api/v1/me') {
        return _jsonResponse(_currentUserJson);
      }
      return http.Response('unexpected request: ${request.url}', 404);
    }),
  );
}

http.Response _jsonResponse(Object body) {
  return http.Response(
    jsonEncode(body),
    200,
    headers: const {'content-type': 'application/json; charset=utf-8'},
  );
}

List<Map<String, Object?>> _serverListJson({
  String currentRoomJoinPolicy = 'approval_required',
  bool pinAlphaRoom = false,
  String alphaRoomName = 'Alpha Room',
  int alphaRoomUnreadCount = 3,
  bool alphaRoomHasPendingJoinRequests = false,
}) {
  return [
    _roomCardJson(
      id: 'server-alpha',
      name: alphaRoomName,
      memberCount: 2,
      liveParticipantCount: 1,
      unreadCount: alphaRoomUnreadCount,
      joinPolicy: currentRoomJoinPolicy,
      isPinned: pinAlphaRoom,
      hasPendingJoinRequests: alphaRoomHasPendingJoinRequests,
    ),
    _roomCardJson(id: 'server-beta', name: 'Beta Room', memberCount: 5),
  ];
}

final _currentUserJson = {
  'id': 'user-1',
  'uid': 'uid-1',
  'username': 'kai',
  'display_name': 'Kai',
  'bio': '',
  'gender': 'secret',
  'email': 'kai@example.com',
  'email_verified': true,
  'email_public': false,
  'phone_number': null,
  'phone_number_public': false,
  'avatar_url': null,
  'default_avatar_key': 'blue-3',
  'is_superuser': false,
  'created_at': '2026-01-01T00:00:00Z',
  'status': 'active',
  'language': 'zh-Hans',
};

Map<String, Object?> _roomDetailJson({
  required String id,
  required String name,
  required int memberCount,
  required int onlineMemberCount,
  required int liveParticipantCount,
  String description = '',
  String visibility = 'private',
  String joinPolicy = 'approval_required',
  bool aiVoiceAnnouncementsEnabled = false,
  String role = 'owner',
  Map<String, Object?>? createdBy,
}) {
  return {
    ..._roomCardJson(
      id: id,
      name: name,
      memberCount: memberCount,
      liveParticipantCount: liveParticipantCount,
    ),
    'online_member_count': onlineMemberCount,
    'description': description,
    'visibility': visibility,
    'join_policy': joinPolicy,
    'ai_voice_announcements_enabled': aiVoiceAnnouncementsEnabled,
    'my_membership': {'joined_at': '2026-06-01T00:00:00Z', 'role': role},
    'created_by': createdBy ?? _currentUserJson,
    'live': _liveStateJson(roomId: id, participantCount: liveParticipantCount),
    'created_at': '2026-06-01T00:00:00Z',
  };
}

Map<String, Object?> _messageJson({
  required String id,
  required String roomId,
  required Map<String, Object?> sender,
  required String clientMessageId,
  required String body,
  String type = 'text',
  List<Object?> attachments = const [],
}) {
  return {
    'id': id,
    'room_id': roomId,
    'sender': sender,
    'client_message_id': clientMessageId,
    'type': type,
    'body': body,
    'attachments': attachments,
    'created_at': '2026-06-05T08:00:00Z',
  };
}

Map<String, Object?> _liveStateJson({
  required String roomId,
  required int participantCount,
  List<Object?>? participants,
}) {
  final participantList =
      participants ??
      (participantCount <= 0
          ? <Object?>[]
          : [
              _liveParticipantJson(
                user: _currentUserJson,
                liveSessionId: 'live-session-1',
              ),
            ]);
  return {
    'room_id': roomId,
    'participant_count': participantCount,
    'participants': participantList,
    'updated_at': '2026-06-05T08:00:00Z',
  };
}

Map<String, Object?> _liveParticipantJson({
  required Map<String, Object?> user,
  required String liveSessionId,
  bool micMuted = false,
  bool micBlocked = false,
  bool headphonesMuted = false,
  bool headphonesBlocked = false,
  bool voiceBlocked = false,
  bool cameraOn = false,
  bool cameraMirrored = false,
  bool screenSharing = false,
}) {
  return {
    'live_session_id': liveSessionId,
    'user': user,
    'joined_at': '2026-06-05T08:00:00Z',
    'mic_muted': micMuted,
    'mic_blocked': micBlocked,
    'headphones_muted': headphonesMuted,
    'headphones_blocked': headphonesBlocked,
    'headphones_listening': !headphonesMuted && !headphonesBlocked,
    'voice_blocked': voiceBlocked,
    'camera_on': cameraOn,
    'camera_mirrored': cameraMirrored,
    'screen_sharing': screenSharing,
    'connection_state': 'connected',
  };
}

Map<String, Object?> _roomMemberJson({
  required Map<String, Object?> user,
  String role = 'member',
}) {
  return {
    'user': user,
    'role': role,
    'joined_at': '2026-06-01T00:00:00Z',
    'is_online': user['is_online'] as bool? ?? false,
  };
}

Map<String, Object?> _joinRequestJson({
  required String id,
  required Map<String, Object?> user,
  String? reason,
}) {
  final json = <String, Object?>{
    'id': id,
    'status': 'pending',
    'user': user,
    'created_at': '2026-06-05T08:00:00Z',
  };
  if (reason != null) json['reason'] = reason;
  return json;
}

Map<String, Object?> _userJson({
  required String id,
  required String username,
  required String displayName,
  String? uid,
  bool? isOnline,
}) {
  final json = <String, Object?>{
    'id': id,
    'username': username,
    'display_name': displayName,
    'avatar_url': null,
    'default_avatar_key': 'blue-3',
  };
  if (uid != null) json['uid'] = uid;
  if (isOnline != null) json['is_online'] = isOnline;
  return json;
}

Map<String, Object?> _roomCardJson({
  required String id,
  required String name,
  String joinPolicy = 'approval_required',
  int memberCount = 1,
  int liveParticipantCount = 0,
  int unreadCount = 0,
  bool isPinned = false,
  bool hasPendingJoinRequests = false,
}) {
  return {
    'id': id,
    'name': name,
    'rid': id,
    'visibility': 'private',
    'join_policy': joinPolicy,
    'description': '',
    'notification_policy': 'all',
    'avatar_url': null,
    'default_avatar_key': 'room-1',
    'member_count': memberCount,
    'live_participant_count': liveParticipantCount,
    'live_avatar_preview': <Object?>[],
    'last_message': null,
    'unread_count': unreadCount,
    'has_pending_join_requests': hasPendingJoinRequests,
    'is_pinned': isPinned,
    'updated_at': '2026-06-05T00:00:00Z',
  };
}

Map<String, Object?> _searchRoomContextJson({
  required String id,
  required String name,
}) {
  return {
    'id': id,
    'rid': id,
    'name': name,
    'avatar_url': null,
    'default_avatar_key': 'room-1',
  };
}

Map<String, Object?> _roomInviteJson({
  String id = 'invite-alpha',
  String status = 'pending',
  Map<String, Object?>? inviter,
  String inviterRoomRole = 'admin',
  String inviterRoomDisplayName = 'Morgan Admin',
  String joinPolicy = 'closed',
  bool roomExists = true,
  String? invalidReason,
}) {
  return {
    'id': id,
    'status': status,
    'room_exists': roomExists,
    'invalid_reason': invalidReason,
    'room': {
      ..._roomCardJson(
        id: 'server-alpha',
        name: 'Alpha Room',
        memberCount: 2,
        liveParticipantCount: 1,
      ),
      'join_policy': joinPolicy,
      'joined': false,
      'join_state': 'none',
    },
    'inviter': {
      ...(inviter ?? _currentUserJson),
      'room_display_name': inviterRoomDisplayName,
      'room_role': inviterRoomRole,
    },
    'created_at': '2026-06-05T08:00:00Z',
    'updated_at': '2026-06-05T08:00:00Z',
  };
}

Map<String, Object?> _roomApplicationJson({
  String id = 'application-alpha',
  String status = 'pending',
  String createdAt = '2026-06-06T08:00:00Z',
  String updatedAt = '2026-06-06T08:00:00Z',
  String? reviewedAt,
  Map<String, Object?>? reviewer,
}) {
  return {
    'id': id,
    'status': status,
    'room': {
      ..._roomCardJson(
        id: 'server-alpha',
        name: 'Alpha Room',
        memberCount: 2,
        liveParticipantCount: 1,
      ),
      'join_policy': 'approval_required',
      'joined': false,
      'join_state': status == 'pending' ? 'pending' : 'none',
    },
    'created_at': createdAt,
    'updated_at': updatedAt,
    'reviewed_at': reviewedAt,
    'reviewer': reviewer,
  };
}

Map<String, Object?> _roomEventNotificationJson({
  String id = 'room-event-alpha',
  String type = 'role_promoted',
  String? readAt,
}) {
  return {
    'id': id,
    'type': type,
    'created_at': '2026-06-07T08:00:00Z',
    'read_at': readAt,
    'room_exists': true,
    'actor_exists': true,
    'from_role': 'member',
    'to_role': 'admin',
    'message_id': 'msg-room-event-alpha',
    'message_preview': '@Morgan Admin hello',
    'room': {
      ..._roomCardJson(
        id: 'server-alpha',
        name: 'Alpha Room',
        memberCount: 2,
        liveParticipantCount: 1,
      ),
      'join_policy': 'approval_required',
      'joined': true,
      'join_state': 'joined',
    },
    'actor': {
      ..._userJson(id: 'user-2', username: 'morgan', displayName: 'Morgan'),
      'room_display_name': 'Morgan Admin',
      'room_role': 'owner',
    },
  };
}

class _FakeSettingsAudioDeviceService extends LiveAudioDeviceService {
  const _FakeSettingsAudioDeviceService();

  static const _input = AudioDeviceInfo(
    deviceId: 'input-1',
    label: 'Input 1',
    kind: 'audioinput',
  );
  static const _output = AudioDeviceInfo(
    deviceId: 'output-1',
    label: 'Output 1',
    kind: 'audiooutput',
  );

  @override
  Stream<List<AudioDeviceInfo>> get devicesChanged => const Stream.empty();

  @override
  AudioDeviceInfo? get selectedAudioInput => _input;

  @override
  AudioDeviceInfo? get selectedAudioOutput => _output;

  @override
  Future<List<AudioDeviceInfo>> enumerateDevices() async {
    return const [_input, _output];
  }

  @override
  Future<void> selectAudioInput(AudioDeviceInfo device) async {}

  @override
  Future<void> selectAudioOutput(AudioDeviceInfo device) async {}
}

class _FakeFeedbackMailService extends FeedbackMailService {
  const _FakeFeedbackMailService(this.drafts);

  final List<FeedbackMailDraft> drafts;

  @override
  Future<void> openDraft(FeedbackMailDraft draft) async {
    drafts.add(draft);
  }
}

class _FakeAutoUpdatePromptStore extends AutoUpdatePromptStore {
  _FakeAutoUpdatePromptStore(this.writes, {required this.initialValue});

  final List<bool> writes;
  final bool initialValue;
  bool? value;
  String? ignoredVersion;

  @override
  Future<bool> read() async => value ?? initialValue;

  @override
  Future<void> write(bool enabled) async {
    value = enabled;
    writes.add(enabled);
  }

  @override
  Future<String?> readIgnoredVersion() async => ignoredVersion;

  @override
  Future<void> writeIgnoredVersion(String? version) async {
    ignoredVersion = version;
  }
}

class _FakeInstallInfoService extends InstallInfoService {
  const _FakeInstallInfoService(this.installedAt);

  final String? installedAt;

  @override
  Future<String?> readInstalledAt() async => installedAt;
}

class _FakeAudioDeviceStore extends AudioDeviceStore {
  const _FakeAudioDeviceStore({
    this.inputVolume = 0.35,
    this.outputVolume = 0.75,
  });

  final double inputVolume;
  final double outputVolume;

  @override
  Future<StoredAudioDevices> read() async {
    return StoredAudioDevices(
      inputVolume: inputVolume,
      outputVolume: outputVolume,
    );
  }

  @override
  Future<void> writeInputDeviceId(String deviceId) async {}

  @override
  Future<void> writeOutputDeviceId(String deviceId) async {}

  @override
  Future<void> writeInputVolume(double volume) async {}

  @override
  Future<void> writeOutputVolume(double volume) async {}
}

class _RecordingLivePresenceSoundPlayer implements LivePresenceSoundPlayer {
  final sounds = <LivePresenceSound>[];
  final volumes = <double>[];
  Completer<void>? nextPlaybackCompletion;

  @override
  Future<void> play(LivePresenceSound sound, {required double volume}) async {
    sounds.add(sound);
    volumes.add(volume);
    final completion = nextPlaybackCompletion;
    nextPlaybackCompletion = null;
    if (completion != null) await completion.future;
  }

  @override
  Future<void> dispose() async {}
}

class _RecordingLivePresenceSpeechPlayer implements LivePresenceSpeechPlayer {
  final announcements = <LivePresenceAnnouncement>[];
  final volumes = <double>[];

  @override
  Future<void> speak(
    LivePresenceAnnouncement announcement, {
    required double volume,
  }) async {
    announcements.add(announcement);
    volumes.add(volume);
  }

  @override
  Future<void> dispose() async {}
}

class _RecordingMessageNotificationSoundPlayer
    implements MessageNotificationSoundPlayer {
  final volumes = <double>[];
  bool disposed = false;

  @override
  Future<void> play({required double volume}) async {
    volumes.add(volume);
  }

  @override
  Future<void> dispose() async {
    disposed = true;
  }
}

class _FixedCloseBehaviorStore extends CloseBehaviorStore {
  const _FixedCloseBehaviorStore(this.behavior);

  final CloseBehavior behavior;

  @override
  Future<CloseBehavior> read() async => behavior;

  @override
  Future<void> write(CloseBehavior behavior) async {}
}

class _RecordingWindowController extends DesktopWindowController {
  _RecordingWindowController(this.events);

  final List<String> events;
  AppCloseRequestHandler? closeRequestHandler;
  AppTrayExitHandler? trayExitHandler;

  @override
  void setCloseRequestHandler(AppCloseRequestHandler? handler) {
    closeRequestHandler = handler;
  }

  @override
  void setTrayExitHandler(AppTrayExitHandler? handler) {
    trayExitHandler = handler;
  }

  @override
  Future<void> hideAppWindowForExit() async {
    events.add('hide');
  }

  @override
  Future<void> terminateApplication() async {
    events.add('terminate');
  }

  @override
  Future<void> requestMessageAttention() async {
    events.add('message-attention');
  }
}

class _NoopRealtimeService implements RealtimeService {
  final _events = const Stream<RealtimeEvent>.empty();
  final _statusChanges = const Stream<RealtimeConnectionStatus>.empty();

  @override
  void Function()? onReconnect;

  @override
  Stream<RealtimeEvent> get events => _events;

  @override
  RealtimeConnectionStatus get status => RealtimeConnectionStatus.offline;

  @override
  Stream<RealtimeConnectionStatus> get statusChanges => _statusChanges;

  @override
  Future<void> start() async {}

  @override
  Future<void> stop() async {}

  @override
  void dispose() {}
}

class _RecordingRealtimeService implements RealtimeService {
  _RecordingRealtimeService(this.lifecycleEvents);

  final List<String> lifecycleEvents;
  final _events = const Stream<RealtimeEvent>.empty();
  final _statusChanges = const Stream<RealtimeConnectionStatus>.empty();

  @override
  void Function()? onReconnect;

  @override
  Stream<RealtimeEvent> get events => _events;

  @override
  RealtimeConnectionStatus get status => RealtimeConnectionStatus.offline;

  @override
  Stream<RealtimeConnectionStatus> get statusChanges => _statusChanges;

  @override
  Future<void> start() async {}

  @override
  Future<void> stop() async {
    lifecycleEvents.add('realtime-stop');
  }

  @override
  void dispose() {}
}

class _FakeRealtimeService implements RealtimeService {
  final _controller = StreamController<RealtimeEvent>.broadcast();
  final _statusController =
      StreamController<RealtimeConnectionStatus>.broadcast();
  RealtimeConnectionStatus _status = RealtimeConnectionStatus.connected;

  @override
  void Function()? onReconnect;

  @override
  Stream<RealtimeEvent> get events => _controller.stream;

  @override
  RealtimeConnectionStatus get status => _status;

  @override
  Stream<RealtimeConnectionStatus> get statusChanges =>
      _statusController.stream;

  @override
  Future<void> start() async {}

  @override
  Future<void> stop() async {}

  void add(RealtimeEvent event) => _controller.add(event);

  void setStatus(RealtimeConnectionStatus status) {
    _status = status;
    _statusController.add(status);
  }

  void emitReconnect() => onReconnect?.call();

  @override
  void dispose() {
    _controller.close();
    _statusController.close();
  }
}

class _FakeLiveSessionController extends LiveSessionController {
  _FakeLiveSessionController({
    required LiveSession session,
    super.audioDeviceStore = const _FakeAudioDeviceStore(),
  }) : super(
         apiBaseUrl: 'http://localhost:3000',
         session: session,
         audioDeviceRestorer: (_) async => null,
       );

  @override
  Future<List<ScreenSource>> listScreenSources() async {
    return const [
      ScreenSource(
        id: 'screen-primary',
        name: 'Primary Display',
        thumbnail: null,
        isWindow: false,
      ),
    ];
  }

  @override
  Future<void> refreshScreenSourceThumbnails() async {}
}

class _FakeLiveSession extends LiveSession {
  _FakeLiveSession({this.failMicUnmute = false});

  final bool failMicUnmute;
  int connectAttempts = 0;
  int disconnects = 0;
  bool _connected = false;
  String? _roomName;
  bool _localMicMuted = true;
  final inputVolumes = <double>[];
  final outputVolumes = <double>[];
  final participantVoiceVolumeWrites = <String>[];
  final screenShareVolumes = <double>[];
  final micMutes = <bool>[];
  final outputMutes = <bool>[];
  final cameraEnables = <bool>[];
  final screenShareEnables = <bool>[];
  final screenShareSourceIds = <String?>[];
  List<LiveVideoTrack> testVideoTracks = const <LiveVideoTrack>[];

  @override
  List<LiveVideoTrack> get videoTracks => testVideoTracks;

  void emitParticipantJoined() => onParticipantJoined?.call('user-2');

  void emitParticipantLeft({bool removed = false}) => onParticipantLeft?.call(
    'user-2',
    removed
        ? LiveParticipantDepartureKind.removed
        : LiveParticipantDepartureKind.left,
  );

  void emitUnexpectedDisconnect() {
    _connected = false;
    onUnexpectedlyDisconnected?.call();
  }

  @override
  Future<void> connect({
    required String url,
    required String token,
    required String roomName,
    required bool micMuted,
  }) async {
    connectAttempts += 1;
    _connected = true;
    _roomName = roomName;
    _localMicMuted = micMuted;
  }

  @override
  Future<void> disconnect() async {
    disconnects += 1;
    _connected = false;
    _roomName = null;
  }

  @override
  bool get isConnected => _connected;

  @override
  String? get roomName => _roomName;

  @override
  bool isAttachedToRoom(String roomName) => _connected && _roomName == roomName;

  @override
  bool get localMicMuted => _localMicMuted;

  @override
  Future<void> setMicMuted(bool muted) async {
    micMutes.add(muted);
    _localMicMuted = !muted && failMicUnmute ? true : muted;
  }

  @override
  Future<void> setOutputMuted(bool muted) async {
    outputMutes.add(muted);
  }

  @override
  Future<void> setInputVolume(double volume) async {
    inputVolumes.add(volume);
    await super.setInputVolume(volume);
  }

  @override
  Future<void> setOutputVolume(double volume) async {
    outputVolumes.add(volume);
    await super.setOutputVolume(volume);
  }

  @override
  Future<void> setParticipantVoiceVolume(String userId, double volume) async {
    participantVoiceVolumeWrites.add('$userId:${volume.toStringAsFixed(2)}');
    await super.setParticipantVoiceVolume(userId, volume);
  }

  @override
  Future<void> setScreenShareVolume(double volume) async {
    screenShareVolumes.add(volume);
    await super.setScreenShareVolume(volume);
  }

  @override
  Future<bool> setCameraEnabled(bool enabled) async {
    cameraEnables.add(enabled);
    return enabled;
  }

  @override
  Future<bool> setScreenShareEnabled(bool enabled, {String? sourceId}) async {
    screenShareEnables.add(enabled);
    screenShareSourceIds.add(sourceId);
    return enabled;
  }
}

LiveVideoTrack _liveVideoTrack({
  required String identity,
  required bool isScreenShare,
  required bool isLocal,
}) {
  return LiveVideoTrack(
    identity: identity,
    track: _FakeVideoTrack(),
    isScreenShare: isScreenShare,
    isLocal: isLocal,
  );
}

class _FakeVideoTrack implements lk.VideoTrack {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeEmailVerificationController extends EmailVerificationController {
  _FakeEmailVerificationController({this.onInspect, this.onStart})
    : super(apiBaseUrl: 'https://unused.test');

  final Future<EmailVerificationInspection> Function(String email)? onInspect;
  final Future<EmailVerificationChallenge> Function(String email)? onStart;
  final List<String> calls = [];

  @override
  Future<bool> isEmailAvailable(String email) async {
    calls.add('available:$email');
    return true;
  }

  @override
  Future<EmailVerificationInspection> inspect(String email) async {
    calls.add('inspect:$email');
    final inspect = onInspect;
    if (inspect != null) return inspect(email);
    return const EmailVerificationInspection(
      canSend: true,
      retryAfterSeconds: 0,
    );
  }

  @override
  Future<EmailVerificationChallenge> start(String email) async {
    calls.add('start:$email');
    final start = onStart;
    if (start != null) return start(email);
    return const EmailVerificationChallenge(
      id: 'email-challenge',
      retryAfterSeconds: 60,
    );
  }

  @override
  Future<EmailVerificationChallenge> resend(String challengeId) async {
    calls.add('resend:$challengeId');
    return EmailVerificationChallenge(id: challengeId, retryAfterSeconds: 60);
  }

  @override
  Future<String> verify({
    required String challengeId,
    required String code,
  }) async {
    calls.add('verify:$challengeId:$code');
    return 'email-verification-token';
  }
}

class _MemoryTokenStore extends TokenStore {
  String? _refreshToken;
  String? _apiBaseUrl;

  @override
  Future<String?> readRefreshToken() async => _refreshToken;

  @override
  Future<void> writeRefreshToken(String refreshToken) async {
    _refreshToken = refreshToken;
  }

  @override
  Future<void> clearRefreshToken() async {
    _refreshToken = null;
  }

  @override
  Future<String?> readApiBaseUrl() async => _apiBaseUrl;

  @override
  Future<void> writeApiBaseUrl(String baseUrl) async {
    _apiBaseUrl = baseUrl;
  }
}

class _MemoryLoginAccountHistoryStore extends LoginAccountHistoryStore {
  _MemoryLoginAccountHistoryStore([List<LoginAccountRecord> records = const []])
    : records = List<LoginAccountRecord>.unmodifiable(records);

  List<LoginAccountRecord> records;

  @override
  Future<List<LoginAccountRecord>> read() async => records;

  @override
  Future<void> write(List<LoginAccountRecord> records) async {
    this.records = List<LoginAccountRecord>.unmodifiable(records);
  }
}
