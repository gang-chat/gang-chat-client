# Flutter → TypeScript 移植约定

桌面端从 Flutter 一比一移植到 Electron。共享代码按 React Native 的方式写，
以后移动端用 React Native 时可以直接复用逻辑层和界面层。Android 目前仍用 Flutter。

本文是移植时的硬性约定：每个 Dart 文件都按同一套规则翻译，不同人（或不同
agent）分别移植的模块才能不用协商就互相引用。

## 1. 仓库结构

```
packages/core/src/      @gang-chat/core：纯 TypeScript，不依赖 React、DOM、Electron
  lib/                  ChangeNotifier、StreamController、Completer/Timer、JSON 读取
  platform/             Platform 接口（宿主应用实现）与 HttpClient
  config/ protocol/ auth/ app/ live/ lifecycle/
  shell/                lib/src/shell 里的非界面服务（剪贴板、文件、窗口、更新……）
packages/ui/src/        @gang-chat/ui：React + React Native 组件
  lib/                  useListenable、ViewState
  ui/ home/ settings/
  shell/                lib/src/shell 里的界面（登录页、更新提示、密码找回流程……）
apps/desktop/           Electron：main / preload / renderer 入口和桌面平台实现
```

## 2. 文件映射

- `lib/src/<dir>/<name>.dart` → 同名 `<name>.ts`；含 JSX 时用 `.tsx`。
  - `protocol/ config/ auth/ app/ live/ lifecycle/` → `packages/core/src/<dir>/`
  - `ui/ home/ settings/` → `packages/ui/src/<dir>/`
  - `shell/`：纯服务 → `packages/core/src/shell/`；含 Widget → `packages/ui/src/shell/`
- 文件名保持 snake_case，与 Dart 一致，便于对照。
- 导入：包内用相对路径（不带扩展名）；跨包用 `@gang-chat/core/<dir>/<file>`。
  不建 barrel 文件（`app.dart` 这种 export 汇总不移植），直接从定义处导入。
- `part` / `part of` 文件拆成独立模块，被其它部分用到的私有成员改为导出
  （名字保留前导下划线以示"包内私有"，例如 `export function _buildHeader`）。
- `extension _Foo on _BarState { ... }` 形式的 part 文件（`home_shell_*.dart`）：State 的
  字段全部留在主文件的类里；每个 part 文件导出一个方法表，主类用声明合并 + `Object.assign`
  挂到原型上，调用处仍写 `this._foo()`：
  ```ts
  // home_shell_realtime.ts
  import type { HomeShellState } from './home_shell';
  export const HomeShellRealtime = {
    _handleRealtimeEvent(this: HomeShellState, event: RealtimeEvent): void { ... },
  };
  // home_shell.tsx
  export class HomeShellState extends ViewState<HomeShellProps> { /* 字段、initState、dispose */ }
  export interface HomeShellState extends HomeShellMethods {}
  type HomeShellMethods = typeof HomeShellRealtime & typeof HomeShellRoomActions & ...;
  Object.assign(HomeShellState.prototype, HomeShellRealtime, HomeShellRoomActions, ...);
  ```
  方法表的对象名 = Dart extension 名去掉前导下划线；方法名保持 Dart 原名（包括下划线）。
  part 文件里的私有 Widget 类导出为组件（保留下划线名，例如 `export function _JoinDialog`）。
- 平台差异：同名文件加后缀 `foo.web.tsx`（Electron / 浏览器）和 `foo.tsx`
  （React Native 原生实现或占位）。导入时写 `./foo`，打包器按平台选择。两者导出
  完全相同的签名。

## 3. 命名与签名

- 公开标识符保持 Dart 原名：类、方法、字段、顶层函数、常量都不改名。
- Dart 私有 `_foo`：类成员 → `private _foo`（或保持 `_foo` 的普通成员，测试要用时）；
  顶层 → 不导出的模块函数/常量，名字保留下划线。
- **具名参数** `foo({required String a, int b = 1})` → `foo(opts: { a: string; b?: number })`，
  默认值在函数体内解构：`const { a, b = 1 } = opts;`。
- 位置参数保持位置参数；混合时：`foo(pos, { named })`。全是可选具名参数时 `opts` 也可省略：`opts: {...} = {}`。
- 构造函数同理：`Foo({required this.a})` → `new Foo({ a })`；位置构造保持位置。
- 命名构造函数 / factory：`Foo.fromJson(json)`、`Foo.defaults()` → `static fromJson` / `static defaults()`。
  `const Foo()` 的无参常量实例可以导出为模块常量或 `static`。
- getter/setter 保持 getter/setter。
- `copyWith({...})` 保持，参数对象里的 `null` 与"未传"要区分时沿用 Dart 的写法
  （Dart 代码里一般用 `bool clearX = false` 之类的旗标，照搬）。

## 4. 类型

| Dart | TypeScript |
| --- | --- |
| `int` / `double` / `num` | `number` |
| `String` / `bool` | `string` / `boolean` |
| `T?`（字段、返回值） | `T \| null`（统一用 `null`，不用 `undefined`） |
| 可选具名参数 | `?:`（可为 `undefined`） |
| `List<T>` / `Set<T>` / `Map<K,V>` | `T[]`（只读语义用 `readonly T[]`）/ `Set<T>` / `Map<K,V>`；JSON 风格的字符串键映射可用 `Record<string, V>` |
| `Map<String, Object?>` JSON | `JsonMap`（`@gang-chat/core/lib/json`） |
| `DateTime` | `Date`（UTC 运算用 `getTime()`；`DateTime.now()` → `new Date()`） |
| `Duration` | `number`（毫秒），常量名不变，例如 `const _watchdogTimeout = 35_000` |
| `Uint8List` | `Uint8Array` |
| `Future<T>` / `async` | `Promise<T>` / `async` |
| `Stream<T>` / `StreamController.broadcast()` | `EventStream<T>` / `StreamController<T>`（`lib/event_stream`） |
| `StreamSubscription` | `StreamSubscription`（`.cancel()`） |
| `Completer` / `Timer` / `Future.delayed` / `.timeout` / `unawaited` | `lib/async` 里的同名工具：`Completer`、`Timer`、`delay`、`withTimeout`、`unawaited` |
| `ChangeNotifier` / `ValueNotifier` / `Listenable` | `lib/change_notifier` |
| `Uri` | `string`（需要解析时用 `URL`） |
| `Exception` 子类 | `class XxxException extends Error`，同名，`toString()` 保持原文案 |
| `Object.hash` / `==` 重写 | 需要比较时写 `equals(other)` 方法；集合去重用 id 字符串 |
| `kIsWeb` / `Platform.isWindows` / `defaultTargetPlatform` | `HostPlatform`（`@gang-chat/core/config/host_platform`） |

### 枚举

- 普通 enum → 字符串枚举：`enum Foo { a, b }` → `export enum Foo { a = 'a', b = 'b' }`。
  `foo.name` → 直接用值本身。`Foo.values` → `Object.values(Foo)`。
- 带字段/方法的 enhanced enum → 类 + 静态实例，保留 `Foo.bar.label` 的写法：
  ```ts
  export class Foo {
    static readonly bar = new Foo('bar', '标签');
    static readonly values = [Foo.bar];
    private constructor(readonly name: string, readonly label: string) {}
  }
  ```
- `switch` 表达式 → `switch` 语句或对象查表，必须穷尽。

### 模型

- 模型类保留字段名（camelCase），`readonly`，`static fromJson(json: JsonMap)`、`toJson()`（若有）。
- 用 `lib/json` 的 `asString/asInt/asBool/asDate/mapList/requireXxx` 读字段，
  行为对齐 Dart 的 `as T` / `as T?`：Dart 用 `!` 或非空 cast 的地方用 `require*`。
- sealed class / 继承层次照搬为 TS 类继承；`is` 判断用 `instanceof`。

## 5. 逻辑层（`@gang-chat/core`）

- 不 import `react`、`react-native`、`electron`，也不直接用 DOM 专属 API（`document`、
  `window`、`HTMLAudioElement`、`AudioContext`）。可以用标准 JS（`TextEncoder`、`URL`、
  `crypto.getRandomValues`），以及 WebRTC 全局对象（`navigator.mediaDevices`、
  `MediaStream`：React Native 端由 `@livekit/react-native` 的 `registerGlobals()` 提供）。
- 确实只能用浏览器 API 实现的部分（音频播放、Web Audio 增益、`speechSynthesis`）放进
  core 的 `*.web.ts` 平台文件，并配一个同签名的 `*.ts` 原生版本（可以是占位）。
- 所有原生能力经 `platform()`（`@gang-chat/core/platform/platform`）：
  安全存储、偏好设置（SharedPreferences）、文件、剪贴板、窗口、通知、屏幕采集源、
  进程、系统朗读、打开外链、媒体缓存 URL。接口不够用时**扩展接口**并在
  `apps/desktop` 里实现，不要在 core 里绕过它。
- HTTP 一律走 `HttpClient`（`platform/http.ts`，Dart `http.Client` 的对等物：
  `send(request) → StreamedResponse`，以及 `HttpClientHelpers` 的 get/post/...）。
  默认用 `platform().http`，构造函数允许注入（测试用）。
- `ChangeNotifier` 子类在状态变化后调用 `notifyListeners()`，与 Dart 完全一致。
- LiveKit：`live/` 用 `livekit-client`（官方 JS SDK）。React Native 端的
  `@livekit/react-native` 也是基于它，所以 `LiveSession` 只在视频渲染、设备枚举等
  少数点上需要平台文件。
- 测试放 `packages/core/test/<dir>/<name>.test.ts`（vitest），从 `test/unit/<dir>/`
  对应的 Dart 测试移植关键用例。

## 6. 界面层（`@gang-chat/ui`）

只用 React Native 基础组件：`View`、`Text`、`Pressable`、`ScrollView`、`FlatList`、
`TextInput`、`Image`、`ActivityIndicator`、`Animated`。Electron 通过
react-native-web 渲染它们。**不要**写 `<div>`、CSS 文件或 className
（`*.web.tsx` 平台文件除外）。

### Widget → 组件

| Flutter | React Native |
| --- | --- |
| `StatelessWidget` | `export function Foo(props: FooProps)` |
| `StatefulWidget` + `State` | 简单状态用 hooks；字段/生命周期多的 State 类用 `ViewState`（`ui/src/lib/view_state.ts`），保持 Dart 结构：`initState`、`didUpdateProps`、`dispose`、`setState`、`mounted` |
| 构造参数 | props，同名；`Key? key` 去掉；`Widget child` → `children: ReactNode`，其它 `Widget` 参数 → `ReactNode` |
| `VoidCallback?` / `ValueChanged<T>?` | `onX?: () => void` / `onX?: (value: T) => void` |
| `ListenableBuilder` / `AnimatedBuilder` | `useListenable(controller)` |
| `ValueListenableBuilder` | `useListenableSelector(notifier, n => n.value)` |
| `Column` / `Row` | `View`（默认纵向）/ `View style={{flexDirection:'row'}}` |
| `Expanded` / `Flexible` | `flex: 1` / `flexShrink: 1`；`minWidth: 0` 防止文本撑破 |
| `SizedBox(width/height)` | `View style={{ width, height }}` |
| `Padding` / `Container` / `DecoratedBox` | `View` + `padding` / `backgroundColor` / `borderRadius` / `borderWidth` |
| `Stack` + `Positioned` | 父 `position:'relative'`，子 `position:'absolute'` |
| `Text` + `TextStyle` | `Text`，样式从 `UiTypography.*` 展开后覆盖；`maxLines`/`overflow: ellipsis` → `numberOfLines` |
| `Icon(Icons.x, size, color)` | `<Icon icon={Icons.x} size color />`（`ui/icon.tsx`，名字与 Flutter 相同） |
| `GestureDetector` / `InkWell` / `MouseRegion` | `Pressable`（`onPress`、`onLongPress`、`onHoverIn/Out`）；可按压视觉统一用移植后的 `PressableSurface` |
| `ClipRRect` | `borderRadius` + `overflow: 'hidden'` |
| `Opacity` / `AnimatedOpacity` | `opacity` / `Animated` |
| `ListView.builder` | `FlatList`（长列表）或 `ScrollView` + `map` |
| `TextField` | `TextInput`（移植后的 `Input` / `UiTextInput` 包装） |
| `Image.network` / `Image.memory` | `Image source={{ uri }}`；远端图片的 URL 先经 `platform().cachedMediaUrl(url)` |
| `LayoutBuilder` / `MediaQuery.sizeOf` | `onLayout` 测量 / `useWindowDimensions()` |
| `Tooltip` | 移植后的 `UiTooltip` |
| `showDialog` / `Navigator.pop(result)` | `showDialog(close => <Dialog … />)`（`ui/overlay.tsx`），返回 `Promise<T \| undefined>`；路由内 `useRoute().close(result)` |
| `OverlayEntry` / `Overlay.of(context).insert` | `insertOverlay(() => node)` → `handle.remove()` |
| `RenderBox.localToGlobal` / 锚点 | `measureInWindow(ref)`（`ui/overlay.tsx`） |
| `InheritedWidget`（`AppConfigScope` 等） | React context + `useXxx()` hook |
| `Theme.of(context).platform == TargetPlatform.android` | `HostPlatform.isAndroid` |

### 样式

- 颜色、圆角、间距、字体、阴影、动效只从 `ui/tokens.ts` 取（`UiColors`、`UiRadii`、
  `UiSpacing`、`UiTypography`、`UiShadows` + `boxShadow()`、`UiMotion`）。
  `Color(0xAARRGGBB)` 写死的颜色用 `argb(0xAARRGGBB)`；`withValues(alpha:)` → `withAlpha()`；
  `Color.lerp` → `lerpColor()`。
- Flutter 的 `TextStyle.height` 是倍数，RN 要绝对 `lineHeight`：tokens 已换算好；
  自定义时写 `lineHeight: fontSize * height`。
- 尺寸数值（宽高、间距、断点）保持 Dart 原值，像素级一致是目标。

### 窗口拖拽区

Flutter 的 `AppWindowDragRegion` / `windowManager.startDragging()` 在 Electron 里是 CSS
`-webkit-app-region`。react-native-web 会丢掉未知样式属性，所以用 `dataSet` 标记：
拖拽区 `<View dataSet={{ gangDrag: 'true' }}>`，其中可点击的控件 `dataSet={{ gangNoDrag: 'true' }}`
（`apps/desktop/src/renderer/src/global.css` 里有对应规则；原生端忽略）。

### 只有 Web 才有的交互

右键菜单、文件拖放、滚轮、键盘快捷键、粘贴事件、`<video>` 渲染等 DOM 能力：
放进 `*.web.tsx`（可直接用 DOM：react-native-web 的 ref 就是 DOM 节点），同时提供
同签名的 `*.tsx` 原生版本（能做就做，做不到就是占位）。业务组件只依赖这个统一签名。
已有的 `ui/platform_gestures.dart` 这类跨平台手势封装就是放这种分支的地方。

## 7. 文案与注释

- 中文文案原样保留（包括标点）。
- 保留 Dart 里解释"为什么"的注释（翻成对应的 TS 位置）；删掉只适用于 Flutter
  插件/引擎细节且在新实现里不成立的注释，必要时改写成新实现的原因。

## 8. 完成标准

- `npm run typecheck` 通过（`tsc --noEmit`，strict）。
- 有对应 Dart 单元测试的逻辑模块，移植主要用例到 vitest。
- 不留 `any`（JSON 边界用 `unknown` + `lib/json` 读取）；确需绕过类型时写明原因。
- 无法一比一实现的地方（例如 Flutter 插件独有能力），在代码里写 `// PORT:` 注释说明差异，
  汇总进 `docs/js-port-status.md`。
