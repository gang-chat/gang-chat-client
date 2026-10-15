/// Browser APIs for the web build: opening links, speech, clipboard images and
/// file drops. Native builds get stubs; callers check `kIsWeb` first.
library;

export 'browser_types.dart';
export 'browser_bridge_stub.dart'
    if (dart.library.js_interop) 'browser_bridge_web.dart';
