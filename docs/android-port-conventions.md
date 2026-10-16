# Flutter → Android 原生（Kotlin + Compose）移植约定

Android 客户端从 Flutter 一比一移植为原生 Kotlin 应用，界面用 Jetpack Compose。
本文是硬性约定：每个 Dart 文件都按同一套规则翻译，不同人（或不同 agent）分别移植的
模块才能不用协商就互相引用。**行为以 Dart 源码为准**；`packages/core`（TypeScript 版）
是另一份一比一移植，遇到 Dart 写法不好直译时可以参考它的处理方式，但不以它为准。

## 1. 工程结构

```
android/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  version.properties          versionName（CI 按 tag 改写）；versionCode = 主*1000000 + 次*1000 + 补丁
  core/                       纯 Kotlin（JVM）模块，不依赖 Android SDK
    src/main/kotlin/com/gangchat/client/
      foundation/             Dart 运行时的对等物：async、ChangeNotifier、Stream、JSON、DateTime、Uri……
      foundation/http/        package:http 的对等物（OkHttp 实现）
      plugins/                Flutter 插件门面：PathProvider、SharedPreferences、FlutterSecureStorage
      config/ protocol/ auth/ app/ lifecycle/ live/ shell/   ← lib/src 下可放进 core 的文件
    src/test/kotlin/...       JUnit 测试（对应 test/unit）
    src/testFixtures/kotlin/com/gangchat/client/testing/   GangTestRule、MockClient
  app/                        Android 应用模块（Compose、LiveKit、系统服务）
    src/main/kotlin/com/gangchat/client/
      widgets/                Flutter widgets 框架层的对等物：ViewState、BuildContext、Navigator、Overlay……
      platform/               原 MethodChannel 背后的原生实现（通知、前台服务、FCM、更新安装……）
      config/ app/ live/ shell/ ui/ home/ settings/          ← 需要 Android/LiveKit/Compose 的文件
    src/test/kotlin/...       Robolectric 测试、Compose UI 测试、Roborazzi 截图测试
  legacy-flutter-host/        旧 Flutter 宿主的 Kotlin 代码，仅供移植参考，移植完成后删除
```

仓库根目录的 `assets/`（配置、品牌图、字体）整个打进 APK 的 assets，与桌面端共用。

### 1.1 文件放哪个模块

一个 Dart 文件放进 `:core`，除非它（或它依赖的文件）需要 Android API、LiveKit/WebRTC、
音频播放/录音、或 Compose；这些放进 `:app`。**包名只看 Dart 目录，不看模块**：
`lib/src/app/rooms_controller.dart` 在哪个模块都是 `package com.gangchat.client.app`。

`:app` 里的逻辑文件（其余 `app/`、`live/`、`shell/`、`config/` 的文件都在 `:core`）：

```
app/android_push_registration_controller  app/app (barrel，不移植)  app/authenticated_app_services
app/composer_attachment_display  app/live_session_controller  app/managed_user_audio_settings
app/voice_recorder_controller
live/android_audio_initialization  live/audio_device_rebinder  live/audio_device_restorer
live/audio_device_service  live/audio_input_rebinder  live/audio_test_service
live/live_presence_audio_coordinator  live/live_presence_sound_service  live/live_session
live/live_video_track_view  live/screen_audio_publisher  live/system_audio_devices
shell/ 下除 android_form_factor、client_system_info、local_*_store、local_preferences_migration、
synthesized_wav 以外的文件
```

### 1.2 不移植的文件（只在桌面 / Web 上运行）

`shell/desktop_window_controller`、`shell/windows_key_event_guard`、`shell/window_controls`、
`shell/sparkle_updater`、`shell/browser/*`、`shell/file_drop_service`（桌面拖放）、
`live/mac_audio_devices`、`home/live_screen_share_picker`（桌面屏幕源选择器）、
`ui/window_chrome`、`ui/title_bar`、`lib/ui_showcase.dart`、`app/app.dart` 与 `ui/ui.dart`（barrel）。

已经移植好的：`config/app_config.dart` → `:core` `config/AppConfig.kt`（资源经 `rootBundle` 门面读取），
`config/host_platform.dart` → `config/HostPlatform.kt`（`isAndroid` 恒为 true，`kIsWeb` 恒为 false）。

文件内只在 Web / Windows / macOS / Linux 上走的分支同样删掉，只保留 Android 走得到的代码
（`HostPlatform.isAndroid` 恒为 true，`kIsWeb` 恒为 false）。Android 平板走宽屏布局，
所以宽屏布局要保留，只去掉桌面窗口相关的部分（标题栏按钮、拖拽区、托盘、窗口尺寸）。
拿不准的就移植。

## 2. 文件与命名

- `lib/src/<dir>/<name>.dart` → `<模块>/src/main/kotlin/com/gangchat/client/<dir>/<Name>.kt`，
  文件名是 Dart 文件名的 PascalCase（`api_client.dart` → `ApiClient.kt`）。
- `part` 文件拆成同包的独立 `.kt`（`home_shell_messages.dart` → `HomeShellMessages.kt`）。
  `extension _X on _HomeShellState` 形式的 part：方法写成 `_HomeShellState` 的扩展函数，
  需要访问的私有字段改为 `internal`。
- **标识符保持 Dart 原名**：类、方法、字段、顶层函数、常量、枚举值（包括小写枚举值和
  前导下划线）都不改名，便于对照。Dart 私有（`_foo`）→ Kotlin `private`（文件内）；
  需要跨文件（part 拆分）时用 `internal`，名字仍带下划线。
- 不建 barrel 文件，直接 import 定义处。
- 保留 Dart 里解释“为什么”的注释；中文文案（包括标点）原样保留。

## 3. 类型

| Dart | Kotlin |
| --- | --- |
| `int` | `Int`；字节数/大小/偏移、毫秒或微秒时间戳用 `Long` |
| `double` / `num` | `Double` / `Double`（或 `Number`） |
| `String` / `bool` | `String` / `Boolean` |
| `T?` | `T?` |
| `List<T>` / `Set<T>` / `Map<K,V>` | `List`/`MutableList`、`Set`/`MutableSet`（LinkedHashSet）、`Map`/`MutableMap`（LinkedHashMap，保持插入顺序） |
| `Map<String, Object?>`（JSON） | `JsonMap`（`foundation/Json.kt`） |
| `Object?` / `dynamic` | `Any?` |
| `DateTime` | `com.gangchat.client.foundation.DateTime`（语义完全照搬 Dart，见 §4） |
| `Duration` | `kotlin.time.Duration`（`Duration(seconds: 5)` → `5.seconds`；`inMilliseconds` → `inWholeMilliseconds`，返回 `Long`） |
| `Uri` | `com.gangchat.client.foundation.Uri`（Dart 语义） |
| `Uint8List` / `List<int>`（字节） | `ByteArray` |
| `Future<T>` / `async` | `suspend fun ...: T`；存进字段/Map 的 Future 用 `Deferred<T>`（见 §5） |
| `Stream<T>` / `StreamController` | `foundation.Stream<T>` / `foundation.StreamController<T>` |
| `ChangeNotifier` / `ValueNotifier` / `Listenable` | `foundation` 里的同名类 |
| `VoidCallback` / `ValueChanged<T>` | `() -> Unit` / `(T) -> Unit` |
| `Future<void> Function()` | `suspend () -> Unit` |
| record `(A, B)` / `({A a, B b})` | `Pair` / 小的 `data class` |
| `typedef F = R Function(A)` | `typealias F = (A) -> R` |

### 3.1 类与构造

- `const Foo({required this.a, this.b = 1})` → `class Foo(val a: A, val b: Int = 1)`；调用处用具名参数
  `Foo(a = x)`，与 Dart 一致。
- `factory Foo.fromJson(json)`、`Foo.empty()` → `companion object { fun fromJson(json: JsonMap): Foo; fun empty(): Foo }`。
- Dart 重写了 `==`/`hashCode` 的类 → `override fun equals/hashCode`（字段完全对应时可用 `data class`）；
  没重写的保持普通 `class`（引用相等，与 Dart 相同）。
- `copyWith` 照搬：可空参数 `null` 表示“不变”；Dart 用 `clearX` 旗标的地方照搬旗标。
- `late final x` → `lateinit var x`（基本类型用 `by Delegates.notNull()` 或可空 + `!!`）。
- 普通 `enum` → `enum class Foo { a, b }`（保留小写名）。`values` → `entries`，`.index` → `.ordinal`，
  `values.byName(x)` → `valueOf(x)`。注意 Dart 的 `enumValue.toString()` 是 `"Foo.a"`，Kotlin 是 `"a"`；
  代码里拿 `toString()` 当键/文案的地方要改成显式字符串。
- 带字段/方法的 enhanced enum → 带构造参数的 `enum class`。
- `sealed class` → `sealed class`；`switch` 表达式/模式匹配 → `when`，必须穷尽。
- `extension` → 扩展函数/属性，同名。
- mixin（`with`）→ 接口 + 默认方法，需要状态时改成抽象基类或委托。
- `static` → `companion object`；`static const` → `const val`（或顶层 `private const val`）。

### 3.2 容易踩的语义差异

- **整数除法**：Dart `a / b`（int）得到 `double`；Kotlin `Int / Int` 是整除。写成 `a.toDouble() / b`。
  Dart `~/` 才对应 Kotlin `Int / Int`。
- **取模**：Dart `%` 结果总非负；Kotlin `%` 可为负，用 `a.mod(b)`。
- **四舍五入**：Dart `round()` 是“远离零”；Kotlin `roundToInt()` 对负数的 .5 向正无穷。负数场景用
  `kotlin.math.sign(x) * kotlin.math.round(abs(x))` 的写法或 `Math.round` 后核对。
- `toStringAsFixed(n)` → `String.format(Locale.ROOT, "%.${n}f", x)`。
- `num.clamp(a, b)` → `coerceIn(a, b)`。`int.parse` / `tryParse` → `toInt()` / `toIntOrNull()`。
- `String.characters`（grapheme）→ `java.text.BreakIterator.getCharacterInstance()`；`runes` → `codePoints()`。
- 正则：`RegExp(p)` → `Regex(p)`；`caseSensitive: false` → `RegexOption.IGNORE_CASE`；`firstMatch` → `find`、
  `hasMatch` → `containsMatchIn`、`allMatches` → `findAll`、`group(1)` → `groupValues[1]`（未匹配组为 `""`）
  或 `groups[1]?.value`（保留 null）。
- 集合：`where` → `filter`、`firstWhere(test, orElse: () => x)` → `firstOrNull(test) ?: x`、
  `every` → `all`、`expand` → `flatMap`、`whereType<T>()` → `filterIsInstance<T>()`、`join(s)` → `joinToString(s)`、
  `fold` → `fold`、`putIfAbsent` → `getOrPut`、`first`/`last`（空时抛）→ `first()`/`last()`。
  Dart 返回的 `Iterable` 是惰性的；只遍历一次的地方用 `List` 即可。
- `identical(a, b)` → `a === b`；`Object.hash(a, b)` → `java.util.Objects.hash(a, b)`。
- `assert(...)` 只在 debug 生效，移植时删掉；有测试依赖断言抛错的改成 `require`/`check`。
- 异常：自定义 `class FooException implements Exception` → `class FooException(...) : Exception(...)`，
  `toString()` 保持 Dart 原文（界面会显示它）。`StateError` → `IllegalStateException`，`ArgumentError` →
  `IllegalArgumentException`，`RangeError` → `IndexOutOfBoundsException`，`UnsupportedError` →
  `UnsupportedOperationException`，`FormatException` / `TimeoutException` → `foundation` 里的同名类。
  `on X catch (e)` → `catch (e: X)`；`catch (_)` → `catch (_: Exception)`；`rethrow` → `throw e`。
- `print` / `debugPrint` → `foundation.debugPrint`。`kDebugMode` → `foundation.kDebugMode`。

## 4. foundation（`:core`）速查

全部在 `com.gangchat.client.foundation`（`http` 在 `.foundation.http`，插件在 `com.gangchat.client.plugins`）。

- **JSON**：`jsonDecode(s): Any?`、`jsonEncode(v)`。解码结果：`Map<String, Any?>`（有序）、`List<Any?>`、
  `String`、`Boolean`、`null`、整数一律 `Long`、带小数/指数的数字 `Double`。
  **不要写 `json["n"] as Int` / `as Double`**（运行时会 ClassCastException），写
  `(json["n"] as Number?)?.toInt()`，或用 `optInt` / `optLong` / `optDouble` / `optString` / `optBool` /
  `optMap` / `optList`、`asJsonMap()`、`asJsonMapList()`、`jsonInt()`。
  Dart 的 `json['x']! as String` → `json["x"] as String`。
- **DateTime**：Dart 同名 API：`DateTime(2024, 1, 2)`（本地，溢出字段自动进位）、`DateTime.utc(...)`、
  `DateTime.now()`、`parse` / `tryParse`、`fromMillisecondsSinceEpoch(ms, isUtc)`、`toLocal()` / `toUtc()`、
  `add` / `subtract` / `difference`、`isBefore` / `isAfter` / `isAtSameMomentAs`、`year`…`weekday`、
  `toIso8601String()`、`toString()`。`==` 同时比较时刻和 `isUtc`（与 Dart 一致）。
  测试里用 `GangTestRule.setNow(...)` 固定当前时间。
- **Uri**：`Uri.parse` / `tryParse`、`Uri(scheme =, host =, path =, queryParameters =)`、`replace(...)`、
  `resolve`、`queryParameters`、`pathSegments`、`Uri.encodeComponent` / `decodeComponent` /
  `encodeQueryComponent`。规范化与 Dart 相同（小写 scheme/host、去掉默认端口、非法字符百分号编码）。
- **文本**：`utf8Decode` / `utf8Encode`、`base64Encode` / `base64Decode`、`base64UrlEncode` / `Decode`、
  `splitLines`（LineSplitter）、`byteStream.utf8Lines()`（`transform(utf8.decoder).transform(LineSplitter())`）。
- **其他**：`Stopwatch`、`dartDuration(days =, hours =, ...)`、`Duration.toDartString()`、`@VisibleForTesting`。

### 4.1 http

`package:http` 的对等物，名字对应：`http.Client` → `HttpClient`（`HttpClient()` 工厂即
`http.Client()`）、`http.Request` → `HttpRequest`、`http.Response` → `HttpResponse`、
`http.StreamedResponse` → `StreamedResponse`、`http.MultipartRequest` / `MultipartFile`、
`http.ByteStream` → `ByteStream`、`http.ClientException` → `ClientException`。
扩展函数 `client.get/post/put/patch/delete/head/read/readBytes(url, headers =, body =)`，
`HttpResponse.fromStream(streamed)`、`response.body`（按 content-type 的 charset，默认 Latin-1，与 Dart 一致）、
`response.bodyBytes`、`response.headers`（小写键）。

- 上传进度：Dart 用 `async*` 生成器喂 `MultipartFile` 的地方写成 `ByteStream(flow { ...; emit(chunk) })`，
  生产者在事件循环上运行，按 socket 写入速度逐块推进（保留背压）。
- 传输错误统一抛 `ClientException`，消息尽量贴近 Dart（`Connection reset by peer`、
  `Connection closed before full header was received`、`SocketException: ...` 等），
  因为 `api_client` 的重试判断和 `userFacingErrorMessage` 依赖这些词。
- 测试用 `testing.MockClient { request -> HttpResponse(...) }`，对应 Dart 的 `MockClient`。

### 4.2 插件门面

| Flutter 插件 | Kotlin |
| --- | --- |
| `path_provider` | `PathProvider.getApplicationSupportDirectory()` 等（`suspend`，返回 `File`） |
| `shared_preferences` | `SharedPreferences.getInstance()`，同名 get/set（set 是 `suspend`）；`getInt` 返回 `Int?`，64 位值用 `getLong`/`setLong` |
| `flutter_secure_storage` | `FlutterSecureStorage().read/write/delete(key)` |
| `rootBundle`（assets） | `rootBundle.loadString("assets/...")`（测试用 `rootBundle.setMockAssets`） |
| `--dart-define` / `String.fromEnvironment` | `AppConfig.environment`（`:app` 启动时从 `BuildConfig` 填入） |

`:app` 在启动时安装真实实现。**真实实现必须读写 Flutter 插件原有的存储格式**
（SharedPreferences 文件 `FlutterSharedPreferences`、键前缀 `flutter.`、列表/浮点的编码前缀；
flutter_secure_storage 的存储），这样 Flutter 版用户原地升级后不用重新登录、设置不丢。
测试里 `SharedPreferences.setMockInitialValues(mapOf(...))` / `FlutterSecureStorage.setMockInitialValues(...)`
与 Dart 测试相同（`GangTestRule` 每个测试前清空）。

## 5. 异步模型

**所有业务代码都跑在 Android 主线程上**，对应 Dart 的单 isolate：控制器、服务、界面状态都不加锁。
需要阻塞的工作（网络、磁盘）由平台层在 `Dispatchers.IO` 上执行，挂起后回到主线程继续。

| Dart | Kotlin |
| --- | --- |
| `Future<T> foo() async { ... }` | `suspend fun foo(): T { ... }` |
| `unawaited(foo())` / 不 await 的调用 | `unawaited { foo() }` — 立即同步执行到第一个挂起点，与 Dart 相同 |
| 存进字段/Map、被多处 await 的 Future | `val f: Deferred<T> = future { ... }`，`f.await()` |
| `Future.value(x)` / `Future.error(e)` | `completedFuture(x)` / `failedFuture(e)` |
| `Future.wait(list)` | `waitAll(list)`（`Deferred` 列表）或 `coroutineScope { list.map { async { } }.awaitAll() }` |
| `Future.delayed(d)` / `Future<void>.delayed(Duration.zero)` | `delayed(d)`（`Duration.ZERO` 也会让出一次） |
| `future.timeout(d)` / `timeout(d, onTimeout:)` | `timeout(d) { ... }` / `timeout(d, onTimeout = { ... }) { ... }`，超时抛 `foundation.TimeoutException` |
| `Completer<T>()` | `Completer<T>()`（`future`、`complete`、`completeError`、`isCompleted`） |
| `Timer(d, cb)` / `Timer.periodic(d, cb)` / `timer.cancel()` / `isActive` | 同名（`foundation.Timer`），回调总在之后的事件循环里执行 |
| `scheduleMicrotask(cb)` | `scheduleMicrotask(cb)` |
| `StreamController.broadcast()` / `StreamController()` | 同名，默认异步投递（与 Dart 相同），`sync = true` 时同步 |
| `stream.listen(onData, onError:, onDone:, cancelOnError:)` | 同名，返回 `StreamSubscription`（`cancel()`） |
| `await for (x in stream)` / `stream.first` / `firstWhere` | `stream.forEach { }` / `stream.first()` / `stream.firstWhere { }` |
| `stream.where/map/expand` | 同名扩展 |

- 界面事件回调不能是 `suspend`：Dart 里 `onPressed: () async { ... }` → `onPressed = { unawaited { ... } }`。
- 宽泛的 `catch (e: Exception)` 也会捕获协程的 `CancellationException`。在 `timeout { }` 内部或可能被取消的
  代码里，先 `if (e is CancellationException) throw e`。
- `ChangeNotifier` 子类状态变化后调用 `notifyListeners()`，与 Dart 完全一致；监听器按 `==` 比较，
  `addListener(::onChanged)` 可以用 `removeListener(::onChanged)` 移除。

## 6. 界面层（Compose）

界面按 Flutter widget 一比一移植成 Compose，视觉尺寸保持 Dart 原值（逻辑像素 = dp，字号 = sp）。
不用 Material 主题样式：颜色、圆角、间距、字体、阴影、动效全部从移植后的 `ui/Tokens.kt` 取。
`com.gangchat.client.widgets` 是 Flutter 框架层的对等物，下面的 API 都在那里。

### 6.1 Widget → Composable

- `StatelessWidget Foo` → `@Composable fun Foo(<字段作参数>, modifier: Modifier = Modifier)`，
  `modifier` 作用在根节点上。`Widget child` / `Widget? leading` → `@Composable () -> Unit` /
  `(@Composable () -> Unit)?`；`IndexedWidgetBuilder` 等 builder → 对应的 `@Composable` lambda。
- `StatefulWidget Foo` + `_FooState` → `ViewState`（见 `widgets/ViewState.kt` 的示例）：
  ```kotlin
  @Composable
  fun Foo(a: A, onTap: (() -> Unit)? = null, modifier: Modifier = Modifier) =
      StatefulWidget(FooWidget(a, onTap, modifier)) { _FooState() }

  class FooWidget(val a: A, val onTap: (() -> Unit)?, val modifier: Modifier)

  private class _FooState : ViewState<FooWidget>() {
      // 字段、initState、didUpdateWidget(oldWidget)、dispose、setState { } 与 Dart 一一对应
      @Composable override fun build() { ... widget.a ... context ... }
  }
  ```
  State 的字段是普通 Kotlin 属性；只有 `setState` 或监听的 `Listenable` 触发重建，与 Flutter 相同。
  （编译器关闭了 strong skipping：父级重建时子级一定重建，和 Flutter 一致。）
- `Key` / `ValueKey(id)` → 在调用处包 `key(id) { Foo(...) }`；`GlobalKey` 用于取位置时改用 `RenderBoxHandle`。
- 监听：`ListenableBuilder(listenable:, builder:)` → `ListenableBuilder(listenable) { ... }`；
  `ValueListenableBuilder` 同名；在 composable 里直接 `listen(controller)` 也会订阅。
  State 里 `controller.addListener(_onChanged)` + `setState` 的写法照搬即可。
- `BuildContext`：`ViewState.context`，或 `rememberBuildContext()`。在事件回调、`initState`、异步代码里都能用：
  `Navigator.of(context)`、`MediaQuery.sizeOf(context)`、`context.mounted`、`context[LocalXxx]`。
- `InheritedWidget`（`AppConfigScope`、`MediaCacheScope` 等）→ `staticCompositionLocalOf` +
  `CompositionLocalProvider`；`Foo.of(context)` → `context[LocalFoo]`。
- `Theme.of(context).platform == TargetPlatform.android` → 恒为 true，删掉非 Android 分支。

### 6.2 布局对照（注意默认值不同）

| Flutter | Compose |
| --- | --- |
| `Column` / `Row` | `Column` / `Row`。Flutter 交叉轴默认**居中**：写 `horizontalAlignment = Alignment.CenterHorizontally` / `verticalAlignment = Alignment.CenterVertically`。Flutter 主轴默认 `MainAxisSize.max`：用了 `mainAxisAlignment`（center/end/spaceBetween…）或依赖尺寸时加 `fillMaxHeight()` / `fillMaxWidth()`；`MainAxisSize.min` 不加 |
| `CrossAxisAlignment.stretch` | Column 本身 `fillMaxWidth()`（或父级给定宽度），每个子项 `fillMaxWidth()` |
| `Expanded(flex: n)` / `Flexible(fit: loose)` / `Spacer()` | `Modifier.weight(n.toFloat())` / `weight(1f, fill = false)` / `Spacer(Modifier.weight(1f))` |
| `SizedBox(width:, height:)` / `SizedBox.expand` / `SizedBox.shrink` | `Spacer`/`Box` + `Modifier.width/height/size` / `fillMaxSize()` / 省略 |
| `Padding(padding: EdgeInsets...)` | `Modifier.padding(...)`（`widgets.EdgeInsets` 可直接 `Modifier.padding(insets)`） |
| `Container(margin, constraints, decoration, padding, alignment)` | 修饰符顺序：外边距 `padding` → 尺寸约束 → `boxShadow` → `background`/`border`/`clip` → 内边距 `padding`；`alignment` → `Box(contentAlignment =)` 且 Flutter 此时会撑满可用空间（加 `fillMaxSize`） |
| `ConstrainedBox(constraints)` | 子项上 `widthIn/heightIn/sizeIn`；若最小约束要传给子项，用 `Box(Modifier.widthIn(min =), propagateMinConstraints = true)` |
| `Center` / `Align` | `Box(Modifier.fillMaxSize(), contentAlignment = ...)`（在无界方向上去掉 fill） |
| `Stack` / `Positioned` / `Positioned.fill` | `Box` / `Modifier.align(...).offset(...)` 或 `padding` / `matchParentSize()`。**Flutter Stack 默认裁剪**（`Clip.hardEdge`）：除非 Dart 写了 `clipBehavior: Clip.none`，给 Box 加 `clipToBounds()` |
| `Wrap(spacing:, runSpacing:)` | `FlowRow(horizontalArrangement = spacedBy(), verticalArrangement = spacedBy())` |
| `ListView` / `ListView.builder` / `SingleChildScrollView` | `LazyColumn` / `items(count)` / `Column(Modifier.verticalScroll(rememberScrollState()))` |
| `LayoutBuilder` | `BoxWithConstraints`（`maxWidth`/`maxHeight` 是 dp） |
| `ClipRRect(borderRadius)` | `Modifier.clip(RoundedCornerShape(r.dp))` |
| `Opacity` / `AnimatedOpacity` / `AnimatedContainer` | `Modifier.alpha` / `animateFloatAsState` / `animate*AsState`；时长、曲线取 `UiMotion` / `Curves.xxx`（`widgets/Curves.kt`，与 Flutter 控制点相同） |
| `IgnorePointer` / `AbsorbPointer` | `Modifier.ignorePointer()` |
| `Offstage` | `Modifier.offstage()` |
| `SafeArea` / `MediaQuery.removePadding` | `SafeArea { }` / `MediaQueryRemovePadding { }` |
| `GestureDetector(onTap)` / `InkWell` | `Modifier.clickable(interactionSource, indication = null)` / 需要水波纹时用默认 indication；长按/双击用 `combinedClickable` 或 `pointerInput { detectTapGestures }` |
| `MouseRegion` / 悬停 | Android 上只在接鼠标时有意义；保留时用 `hoverable` / `collectIsHoveredAsState` |
| `CustomPaint` | `Canvas` / `Modifier.drawBehind` |
| `Image.memory` / 网络图 | 先经 `MediaCacheController` 拿字节/文件，再用 Coil 3（`coil3.compose.AsyncImage`，GIF/WebP 动图走 `coil-gif`） |
| `Icon(Icons.x_rounded, size:, color:)` | `Icon(Icons.Rounded.X, ...)`（material-icons-extended，名字按 Flutter 去后缀转 PascalCase：`_rounded` → `Icons.Rounded`，`_outlined` → `Icons.Outlined`，无后缀 → `Icons.Filled`）。用移植后的 `ui` 图标组件统一尺寸/颜色 |

Flutter 的约束是“父给子 tight/loose 约束”，Compose 默认只传最大值；移植后要核对尺寸。
有疑问时写个 Roborazzi 截图测试看结果（§7）。

### 6.3 文本

- `Text(data, style:, maxLines:, overflow:, textAlign:, softWrap:)` → `widgets.Text(...)`（同名参数，
  合并 `DefaultTextStyle`，`maxLines: null` 即默认值）。`Text.rich(TextSpan)` → `buildAnnotatedString`。
  `SelectableText` → `Text(..., selectable = true)`。
- `TextStyle(fontSize:, height:, letterSpacing:, fontWeight:, color:)` → `flutterTextStyle(...)`：
  `height` 按 Flutter 语义换算为行高并按比例分配上下留白。**不要直接 `TextStyle(lineHeight = ...)`**。
- `DefaultTextStyle(style:)` / `DefaultTextStyle.merge` → `DefaultTextStyle(style) { }`。
- 不要 import `androidx.compose.material3.Text`。
- `TextField` / `TextEditingController` → `widgets.TextEditingController`（Flutter 语义：controller 是文本的唯一来源，
  程序修改同步通知监听器），绑定 `BasicTextField(value = controller.value, onValueChange = controller::updateFromField)`。
- `FocusNode` → `widgets.FocusNode` + `Modifier.focusNode(node)`；`FocusScope.of(context).unfocus()` →
  `FocusScope.unfocus(context)`。

### 6.4 装饰

- `Color(0xAARRGGBB)` 原样；`withValues(alpha: a)` / `withOpacity(a)` → `.copy(alpha = a)`；`Color.lerp` → `lerp`。
- `BorderRadius.circular(r)` → `RoundedCornerShape(r.dp)`；`Border.all(color:, width:)` → `Modifier.border(width, color, shape)`。
- `BoxShadow(color:, offset:, blurRadius:, spreadRadius:)` → `widgets.BoxShadow` + `Modifier.boxShadow(list, shape)`。

### 6.5 路由、对话框、浮层

只有一个根 `Navigator`（`RootNavigator`），与 MaterialApp 相同。

| Flutter | Kotlin |
| --- | --- |
| `await showDialog<T>(context:, barrierDismissible:, builder: (context) => ...)` | `showDialog<T>(context, barrierDismissible = ...) { dialogContext -> ... }`（`suspend`，返回 pop 的结果） |
| Material `Dialog(insetPadding:, child:)` | `widgets.Dialog(insetPadding = ...) { }` |
| `Navigator.of(context).pop(result)` / `maybePop` / `canPop` | 同名（`NavigatorState`） |
| `Navigator.of(context).push(route)` | `Navigator.of(context).push(route).await()` |
| `PageRouteBuilder` / 自定义 `PopupRoute` | `PageRouteBuilder` / 继承 `PopupRoute` 并实现 `buildPage`、`buildTransitions` |
| `PopScope(canPop:, onPopInvokedWithResult:)` | `PopScope(canPop, onPopInvokedWithResult) { }`；系统返回键经它处理 |
| `OverlayEntry` / `Overlay.of(context).insert(entry)` / `entry.remove()` | 同名（`OverlayState.of(context)`） |
| `context.findRenderObject() as RenderBox` + `localToGlobal` | `RenderBoxHandle` + `Modifier.renderBox(handle)`，`handle.localToGlobal()` / `globalRect`（dp，窗口坐标，与浮层坐标一致） |

### 6.6 MediaQuery

`MediaQuery.sizeOf(context)`（`DpSize`）、`paddingOf`、`viewPaddingOf`、`viewInsetsOf`（软键盘）、
`textScalerOf(context).scale(x)`、`devicePixelRatioOf`、`orientationOf`；composable 里用 `MediaQuery.current`。
Activity 是 edge-to-edge，语义与 Flutter 相同（内容画到系统栏下面，由 `SafeArea`/padding 让开）。

## 7. 测试与验证

每个移植单元都要能编译、测试通过后再交付。环境变量：`ANDROID_HOME=/opt/android-sdk`。

```bash
cd android
./gradlew :core:compileKotlin :core:test            # 纯逻辑
./gradlew :app:compileDebugKotlin                   # Android/Compose
./gradlew :app:testDebugUnitTest --tests '...'      # Robolectric / Compose UI 测试
./gradlew :app:recordRoborazziDebug --tests '...'   # 截图，输出到 app/build/outputs/roborazzi/
```

- `test/unit/<dir>/<name>_test.dart` → `core/src/test/kotlin/com/gangchat/client/<dir>/<Name>Test.kt`
  （依赖 `:app` 的放 `app/src/test/...`）。逐个用例移植，用例名保持 Dart 原文（反引号函数名）。
  `GangTestRule` 对应 flutter_test 的绑定：`gang.runTest { ... }` 在虚拟时间上运行，
  `pumpEventQueue()` / `elapse(d)` / `flushTimers()` 对应 `pumpEventQueue()` / `fakeAsync.elapse` / `flushTimers`。
- `test/widget/**` → `app/src/test/...` 的 Compose UI 测试（Robolectric，`createComposeRule()`）。
  移植断言行为的用例；只针对桌面窗口/像素的用例可以跳过，在移植状态文档里说明。
- 界面移植用 Roborazzi 截图自查（`@GraphicsMode(NATIVE)`、`@Config(qualifiers = "w411dp-h891dp-xxhdpi")`），
  对照 Dart 代码的尺寸与 `docs/js-port-screenshots/`（桌面端截图，Android 宽屏布局同源）。

## 8. 与 Flutter 版的差异记录

不可避免的差异、刻意的简化、未移植的部分，写进 `docs/android-port-status.md` 对应小节，
写清原因。不要静默删除功能。
