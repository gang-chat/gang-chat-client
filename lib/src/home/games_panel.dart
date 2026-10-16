import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../app/error_display.dart';
import '../config/host_platform.dart';
import '../protocol/api_client.dart' show ApiException, GamesTicketApi;
import '../ui/ui.dart';

/// flutter_inappwebview has no Linux implementation.
bool get gamesPanelSupported => !HostPlatform.isLinux;

/// The lobby page of gang-games-server for one signed-in visit. The ticket
/// rides in the fragment so it never reaches a server log; the page reads it
/// and clears it.
Uri gamesShellUrl(String baseUrl, String ticket) {
  final base = baseUrl.endsWith('/') ? baseUrl : '$baseUrl/';
  return Uri.parse(base).resolve('shell').replace(fragment: 'ticket=$ticket');
}

/// What outlives the games panel while the user flips back to chat: the
/// lobby page itself, so a match in progress is still there when they
/// return. Browsers cannot keep a WebView alive, so on the web the page is
/// loaded afresh each time.
class GamesPanelSession {
  GamesPanelSession(this.roomId);

  final String roomId;
  InAppWebViewKeepAlive? _keepAlive;
  Uri? _url;

  /// The page to show again, when the old one is still alive.
  Uri? get liveUrl => _keepAlive == null ? null : _url;

  /// Remembers the page about to go on screen.
  void _show(Uri url) {
    _url = url;
    if (!kIsWeb) _keepAlive ??= InAppWebViewKeepAlive();
  }

  /// Drops the page so the next visit loads it from scratch.
  void clear() {
    final keepAlive = _keepAlive;
    _keepAlive = null;
    _url = null;
    if (keepAlive == null) return;
    // Let the frame that takes the WebView widget away go by first; disposing
    // the native view while it is still on screen pulls it out from under
    // Flutter.
    SchedulerBinding.instance
      ..addPostFrameCallback((_) {
        unawaited(InAppWebViewController.disposeKeepAlive(keepAlive));
      })
      ..scheduleFrame();
  }
}

class GamesPanel extends StatefulWidget {
  const GamesPanel({
    super.key,
    required this.api,
    required this.baseUrl,
    required this.session,
    required this.onClose,
  });

  final GamesTicketApi api;
  final String baseUrl;
  final GamesPanelSession session;
  final VoidCallback onClose;

  @override
  State<GamesPanel> createState() => _GamesPanelState();
}

class _GamesPanelState extends State<GamesPanel> {
  Uri? _url;
  String? _error;
  bool _loading = false;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _url = widget.session.liveUrl;
    if (_url == null) unawaited(_load());
  }

  @override
  void didUpdateWidget(GamesPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.session != widget.session ||
        oldWidget.baseUrl != widget.baseUrl) {
      _url = widget.session.liveUrl;
      if (_url == null) unawaited(_load());
    }
  }

  Future<void> _load() async {
    final generation = ++_generation;
    final session = widget.session;
    session.clear();
    setState(() {
      _url = null;
      _error = null;
      _loading = true;
    });
    try {
      final ticket = await widget.api.createGamesTicket(session.roomId);
      if (!mounted || generation != _generation) return;
      final url = gamesShellUrl(widget.baseUrl, ticket);
      session._show(url);
      setState(() {
        _url = url;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() {
        _error = error is ApiException && error.code == 'games_unavailable'
            ? '小游戏还没开放'
            : userFacingErrorMessage(error, fallback: '小游戏加载失败');
        _loading = false;
      });
    }
  }

  void _handleLoadError(WebResourceRequest request, WebResourceError error) {
    if (request.isForMainFrame == false) return;
    if (!mounted) return;
    widget.session.clear();
    setState(() {
      _url = null;
      _error = '小游戏加载失败，请检查网络后重试';
    });
  }

  @override
  Widget build(BuildContext context) {
    return SettingsScaffold(
      icon: Icons.sports_esports_outlined,
      title: '游戏',
      onBack: widget.onClose,
      headerAction: ButtonIcon(
        key: const ValueKey('games-panel-reload-button'),
        tooltip: '重新载入',
        icon: const Icon(Icons.refresh),
        onPressed: _loading ? null : () => unawaited(_load()),
        loading: _loading && _url == null && _error == null,
        size: 38,
      ),
      // Edge to edge: the lobby page brings its own bar and margins.
      body: _buildContent(),
    );
  }

  Widget _buildContent() {
    final url = _url;
    if (url != null) {
      return InAppWebView(
        key: ValueKey(url),
        keepAlive: widget.session._keepAlive,
        initialUrlRequest: URLRequest(url: WebUri.uri(url)),
        initialSettings: InAppWebViewSettings(
          // On the web the page is an iframe; keep it from navigating or
          // popping up over the chat app. The games inside it run in a
          // narrower sandbox of their own, which these flags must cover.
          iframeSandbox: {
            Sandbox.ALLOW_SCRIPTS,
            Sandbox.ALLOW_SAME_ORIGIN,
            Sandbox.ALLOW_MODALS,
            Sandbox.ALLOW_POINTER_LOCK,
          },
          transparentBackground: true,
          supportZoom: false,
          mediaPlaybackRequiresUserGesture: false,
          allowsInlineMediaPlayback: true,
          isInspectable: kDebugMode,
        ),
        onReceivedError: (controller, request, error) =>
            _handleLoadError(request, error),
      );
    }
    final error = _error;
    if (error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(22),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.sports_esports_outlined,
                color: UiColors.textMuted,
                size: 30,
              ),
              const SizedBox(height: 8),
              Text(
                error,
                textAlign: TextAlign.center,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.body.copyWith(
                  color: UiColors.textMuted,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 14),
              Button(
                key: const ValueKey('games-panel-retry-button'),
                icon: const Icon(Icons.refresh),
                onPressed: () => unawaited(_load()),
                child: const Text('重试'),
              ),
            ],
          ),
        ),
      );
    }
    return const Center(
      child: SizedBox.square(
        dimension: 24,
        child: CircularProgressIndicator(
          strokeWidth: 2.2,
          color: UiColors.accent,
        ),
      ),
    );
  }
}
