import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../config/host_platform.dart';

const int _windowsKeyCodeMenu = 0x12;
const int _windowsKeyCodeLeftMenu = 0xA4;
const int _windowsKeyCodeRightMenu = 0xA5;

const int _windowsModifierAlt = 1 << 6;
const int _windowsModifierLeftAlt = 1 << 7;
const int _windowsModifierRightAlt = 1 << 8;

bool _installed = false;

/// Works around a Flutter Windows raw-key edge case where a bare Alt key-down
/// can arrive with `modifiers: 0`, causing RawKeyboard's debug assertion to
/// clear the just-pressed key and crash the debug runner.
///
/// Keep this in the shell layer because it patches a platform channel boundary,
/// not business or widget behavior. The wrapper delegates every event back to
/// Flutter's normal key event manager after normalizing only malformed Windows
/// Alt key-down messages.
void installWindowsAltKeyEventGuard() {
  if (_installed || kIsWeb || !HostPlatform.isWindows) return;
  _installed = true;
  SystemChannels.keyEvent.setMessageHandler((message) {
    // The crash happens before HardwareKeyboard handlers run, inside the
    // legacy raw-key channel. Delegate to Flutter's original raw-key handler
    // after patching the malformed message.
    // ignore: deprecated_member_use
    return ServicesBinding.instance.keyEventManager.handleRawKeyMessage(
      _normalizeWindowsAltKeyMessage(message),
    );
  });
}

@visibleForTesting
Object? normalizeWindowsAltKeyMessageForTest(Object? message) {
  return _normalizeWindowsAltKeyMessage(message);
}

Object? _normalizeWindowsAltKeyMessage(Object? message) {
  if (message is! Map<Object?, Object?> ||
      message['keymap'] != 'windows' ||
      message['type'] != 'keydown') {
    return message;
  }

  final sideModifier = _altSideModifierForKeyCode(message['keyCode']);
  if (sideModifier == null) return message;

  final modifiers = message['modifiers'];
  final currentModifiers = modifiers is int ? modifiers : 0;
  final requiredModifiers = _windowsModifierAlt | sideModifier;
  if ((currentModifiers & requiredModifiers) == requiredModifiers) {
    return message;
  }

  if (message.keys.any((key) => key is! String)) return message;
  return Map<String, dynamic>.from(message)
    ..['modifiers'] = currentModifiers | requiredModifiers;
}

int? _altSideModifierForKeyCode(Object? keyCode) {
  return switch (keyCode) {
    _windowsKeyCodeLeftMenu => _windowsModifierLeftAlt,
    _windowsKeyCodeRightMenu => _windowsModifierRightAlt,
    _windowsKeyCodeMenu => 0,
    _ => null,
  };
}
