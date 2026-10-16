import 'package:client/src/home/games_panel.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('gamesShellUrl puts the ticket in the fragment of the lobby page', () {
    for (final base in const [
      'https://ky-z.com/gang-chat/games-api',
      'https://ky-z.com/gang-chat/games-api/',
    ]) {
      final url = gamesShellUrl(base, 'aaa.bbb.ccc');
      expect(
        url.toString(),
        'https://ky-z.com/gang-chat/games-api/shell#ticket=aaa.bbb.ccc',
      );
      expect(url.query, isEmpty);
    }
  });

  testWidgets('a server without games says so and can be asked again', (
    tester,
  ) async {
    final api = _FakeGamesTicketApi();
    var closed = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: uiTheme(),
        home: Scaffold(
          body: GamesPanel(
            api: api,
            baseUrl: 'https://ky-z.com/gang-chat/games-api',
            session: GamesPanelSession('room-1'),
            onClose: () => closed++,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(api.requests, ['room-1']);
    expect(find.text('小游戏还没开放'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('games-panel-retry-button')));
    await tester.pumpAndSettle();
    expect(api.requests, ['room-1', 'room-1']);

    await tester.tap(find.byTooltip('返回'));
    expect(closed, 1);
  });
}

class _FakeGamesTicketApi implements GamesTicketApi {
  final requests = <String>[];

  @override
  Future<String> createGamesTicket(String roomId) async {
    requests.add(roomId);
    throw ApiException(
      '小游戏还没开放',
      statusCode: 503,
      code: 'games_unavailable',
      requestId: null,
    );
  }
}
