# 模块化开发指南

本文档说明客户端两套代码的目录职责、依赖方向和后续开发流程。两套代码都从 Flutter 客户端一比一移植而来，沿用 Flutter 版解耦后的分层；目标是让新功能复用已有的登录、房间、消息、设置、Live 和文件能力，而不把业务逻辑重新写进界面组件。

> Flutter 客户端已从仓库移除。本文提到的 Dart 文件（`lib/src/...`）以提交 `fcf2fdd` 为准，可用 `git show fcf2fdd:lib/src/<路径>` 查看。逐文件翻译的硬性规则见 [TypeScript 移植约定](js-port-conventions.md) 和 [Android 移植约定](android-port-conventions.md)；本文只讲分层原则，与移植约定冲突时以移植约定为准。

## 两套代码

| | 桌面端（Windows / macOS） | Android |
| --- | --- | --- |
| 技术栈 | TypeScript + React DOM，Electron 宿主 | Kotlin + Jetpack Compose |
| 逻辑层 | `packages/core`（`@gang-chat/core`），不依赖界面 | `android/core`（Gradle 模块 `:core`），纯 Kotlin/JVM，不依赖 Android SDK |
| 界面层 | `packages/ui`（`@gang-chat/ui`） | `android/app`（Gradle 模块 `:app`） |
| 平台实现 | `apps/desktop`（Electron 主进程、preload、渲染进程入口） | `android/app` 的 `platform/` |
| 移植约定 / 状态 | [js-port-conventions](js-port-conventions.md) / [js-port-status](js-port-status.md) | [android-port-conventions](android-port-conventions.md) / [android-port-status](android-port-status.md) |

两套代码不共用源码，只共用仓库根目录的 `assets/`（服务器配置、品牌图、字体）和同一个服务端协议。**每个新功能和每处行为修复都要在两端分别实现**，见 [新功能开发流程](#新功能开发流程)。

下文 Kotlin 路径省略 `src/main/kotlin/com/gangchat/client/` 前缀：`android/core` 的 `app/` 指 `android/core/src/main/kotlin/com/gangchat/client/app/`。Kotlin 的包名只看原 Dart 目录、不看模块，所以 `:core` 和 `:app` 里都可能有 `com.gangchat.client.app` 包。

## 核心原则

1. 界面只负责展示、输入和页面编排。
2. 业务规则、状态 patch、展示文案和 action gate 优先放进 app 层（`packages/core/src/app`、`android/core` 的 `app/`）。
3. 平台能力和 SDK 调用放在适配层：桌面端由 `apps/desktop` 实现 `Platform` 接口，`packages/core/src/shell`、`live` 包装；Android 由 `android/app` 的 `platform/` 实现，`shell/`、`live/` 包装。
4. 可复用视觉组件放进 `ui/`（`packages/ui/src/ui`、`android/app` 的 `ui/`），不要依赖具体业务 controller。
5. 新页面先调用已有 app controller/service，不复制其他页面里的业务逻辑。
6. 两端保持对应：同一功能使用对应的文件名、相同的类名、方法名、字段名和中文文案，便于逐文件对照；无法一致的地方写 `PORT:` 注释并记入状态文档。
7. 每个可复用逻辑模块都要有聚焦测试；测试按模块目录存放，跨模块改动再跑两端的全量检查。

## 目录职责

### 运行时对等层

Dart 运行时和 Flutter 框架的对等物，让移植后的代码保持 Dart 的写法和语义。

| 桌面端 | Android |
| --- | --- |
| `packages/core/src/lib`：ChangeNotifier、StreamController、Completer/Timer、JSON 读取、zip | `android/core` 的 `foundation/`：async、ChangeNotifier、Stream、JSON、DateTime、Uri；`foundation/http/`：`package:http` 的对等物（OkHttp） |
| `packages/core/src/platform`：`Platform` 接口（宿主实现的全部原生能力）与 `HttpClient` | `android/core` 的 `plugins/`：PathProvider、SharedPreferences、FlutterSecureStorage、rootBundle 门面，由 `GangChatApplication` 在启动时安装真实实现 |
| `packages/ui/src/lib`：`ViewState`、`useListenable`、Flutter 风格布局组件、文本编辑 | `android/app` 的 `widgets/`：`ViewState`、`BuildContext`、`Navigator`、`Overlay`、`Dialog`、文本、`MediaQuery` |

规则：

- 这一层只提供语言和框架层的能力，不放业务逻辑。
- 业务代码缺少某个 Dart/Flutter 能力时，先补到这一层，再按 Dart 的写法使用，不要在业务文件里临时拼一份。

### app 层（原 `lib/src/app`）

应用逻辑层。这里放纯业务规则、状态 reducer/patch、展示映射、controller 和登录后的会话上下文。

位置：

- 桌面端：`packages/core/src/app/`。
- Android：`android/core` 的 `app/`。依赖 LiveKit、录音或 Compose 图标的少数文件放在 `android/app` 的 `app/`：`AndroidPushRegistrationController`、`AuthenticatedAppServices`、`ComposerAttachmentDisplay`、`LiveSessionController`、`ManagedUserAudioSettings`、`VoiceRecorderController`。

适合放这里的内容（括号内为桌面端 / Android 文件名）：

- 表单校验和 submit draft，例如 `account_forms` / `AccountForms`、`room_forms` / `RoomForms`。
- 展示文案和界面无关的 display helper，例如 `room_display` / `RoomDisplay`、`file_display` / `FileDisplay`、`live_display` / `LiveDisplay`。
- 可测试的状态变更，例如 `settings_shell_state` / `SettingsShellState`、`audio_device_state` / `AudioDeviceState`、`chat_composer_state` / `ChatComposerState`。
- controller，例如 `messages_controller` / `MessagesController`、`rooms_controller` / `RoomsController`、`settings_controller` / `SettingsController`。
- app 可见的协议/抽象，例如 `AudioDeviceStore`。

不要放这里：

- 桌面端：React、`@gang-chat/ui`、Electron、DOM 专属 API（`document`、`HTMLAudioElement`、`AudioContext` 等）。
- Android：Android SDK、Compose、LiveKit。`:core` 是纯 JVM 模块，引入这些依赖会直接编译失败；确实需要的文件按上面的例外放进 `:app`。
- 页面弹窗、导航、toast 展示、布局代码。

桌面端可以用这个命令审计：

```sh
rg -n "from '(react|electron|livekit-client|@gang-chat/ui)" packages/core/src/app
rg -n "from '(react|electron|@gang-chat/ui)" packages/core/src
```

正常结果都应该为空。

### shell 层（原 `lib/src/shell`）

应用壳和平台适配层：应用入口、登录壳、窗口控制、剪贴板、文件选择、安全存储、更新这类平台边界。

桌面端：

- 非界面服务：`packages/core/src/shell/`，例如 `clipboard_service`、`file_selection_service`、`release_update_service`、`sparkle_updater`、`desktop_window_controller`、`local_*_store`。它们只通过 `platform()` 调用原生能力。
- 界面：`packages/ui/src/shell/`，例如 `gang_app`（应用根与认证门）、`login_page`、`app_update_gate`、`email_verification_flow`、`password_reset_flow`、`window_controls`。
- 原生实现：`apps/desktop/src/main/`（主进程）、`src/preload/index.ts`（只放行 `src/shared/ipc.ts` 中登记的 IPC 通道）、`src/renderer/src/platform/desktop_platform.ts`（`Platform` 实现）。

Android：

- `android/core` 的 `shell/`：不依赖 Android 的部分，例如本地偏好 store（`LocalAudioDeviceStore`、`LocalLanguagePreferenceStore` 等）、`LocalPreferencesMigration`、`ClientSystemInfo`、`AndroidFormFactor`、`SynthesizedWav`。
- `android/app` 的 `shell/`：服务（`ClipboardService`、`FileSelectionService`、`ReleaseUpdateService`、`AndroidSystemService` 等）和界面（`GangApp`、`LoginPage`、`AppUpdateGate`、`EmailVerificationFlow`、`PasswordResetFlow`）。
- 原生实现：`android/app` 的 `platform/`，即 Flutter 版 MethodChannel 背后的原生代码（通知、前台服务、FCM、更新安装、兼容 Flutter 格式的存储、剪贴板、文件选择）。

规则：

- shell 服务可以调用平台实现，但要把平台类型藏在服务内，返回 app 层能理解的类型（例如 `SelectedFile`）。
- shell 提供可注入的服务，功能界面通过构造参数 / props 接收，测试时可以替换。
- 不要把业务状态判断写进 shell 服务；shell 服务只包平台能力。
- 桌面端新增原生能力：扩展 `Platform` 接口，在 `apps/desktop` 中实现（需要 IPC 时在 `src/shared/ipc.ts` 登记通道），再用 shell 服务包装。不要在 core 或 ui 里绕过 `platform()`。
- Android 新增原生能力：在 `platform/` 中实现，再用 `shell/` 服务包装；`:core` 里的代码需要的能力通过 `plugins/` 门面提供。

### ui 层（原 `lib/src/ui`）

可复用组件库和展示 adapter：button、input、chat composer、navigation、sidebar、avatar、file icon、gender mark、tooltip、context menu 等。

位置：`packages/ui/src/ui/`；`android/app` 的 `ui/`。

规则：

- 组件必须尽量业务无关，通过参数传入数据和 callback。
- 可以使用界面框架，但不要直接调用 API 或 controller。
- 组件需要把平台/SDK 类型转成 app 逻辑输入时，做成薄 adapter，例如 `sticker_upload_adapter` / `StickerUploadAdapter`。
- 颜色、圆角、间距、字体、阴影、动效只从 token 文件取：`packages/ui/src/ui/tokens.ts`、`android/app` 的 `ui/Tokens.kt`，见 [UI 设计规范](ui-design-system.md)。

### home 层（原 `lib/src/home`）

主界面。位置：`packages/ui/src/home/`；`android/app` 的 `home/`。两端拆分相同：

- `home_page` / `HomePage`：页面总编排、服务注入、生命周期、实时事件连接。
- `home_shell` / `HomeShell` 及其拆分文件 `home_shell_*` / `HomeShell*`：实时事件、消息、房间操作、Live、音乐盒、搜索、通知等。
- `home_sidebar` / `HomeSidebar`：侧栏。
- `chat_*` / `Chat*`：聊天区域。
- `live_channel_*` / `LiveChannel*`：语音频道、成员、媒体和音乐盒面板。
- `room_*` / `Room*`：房间资料、设置、管理和成员弹窗。

规则：

- 可以保留弹窗、导航、布局和组件状态。
- 业务判断应调用 app 层 helper/controller。
- 文件选择、剪贴板等平台能力必须通过 `ClipboardService` / `FileSelectionService` 注入。
- 不要在这里直接使用 LiveKit SDK 或平台实现：桌面端不 import `livekit-client`，Android 不 import `io.livekit`、`org.webrtc`，也不直接调用 `platform/` 中的原生实现。
- 拆分文件的写法（TypeScript 方法表 + `Object.assign`、Kotlin 扩展函数）见两份移植约定。

### settings 层（原 `lib/src/settings`）

设置页。位置：`packages/ui/src/settings/`；`android/app` 的 `settings/`。拆分为 `settings_page` / `SettingsPage`（页面编排和 controller 调用）、`settings_components`（通用局部组件）、`settings_profile_widgets`（账号/资料）、`settings_audio_widgets`（音频）、`settings_music_playlists*`（个人歌单）。表情包管理界面在组件库的 `sticker_manager` / `StickerManager`。

规则：

- 账号、资料、表情包和音频的状态 patch、校验、展示文案放在 app 层。
- LiveKit 音频设备类型不要出现在 settings 界面；使用 `AudioDeviceInfo`。
- 文件、保存、剪贴板必须走 shell 服务。

### live 层（原 `lib/src/live`）

LiveKit、WebRTC 和实时连接的适配层。这里可以直接接触 SDK，但对 home/settings 暴露稳定的小接口。

桌面端：

- `packages/core/src/live/`：`live_session`（LiveKit 会话，`livekit-client`）、`audio_device_service`、`audio_device_restorer`、`audio_test_service`、`screen_audio_publisher`、`system_audio_devices`、`live_stream_client`（SSE）等。只能用浏览器 API 实现的部分放 `*.web.ts`（如 `live_audio_platform.web.ts`），并配同签名的 `*.ts`。
- `packages/ui/src/live/live_video_track_view.tsx`：视频渲染。

Android：

- `android/core` 的 `live/`：不依赖 SDK 的纯逻辑，例如 `LiveStreamClient`（SSE）、`LivekitUrl`、`ScreenShareQuality`、`AudioOutputRebinder`、`CameraDeviceReconciler`。
- `android/app` 的 `live/`：`LiveSession`、`LiveKitAndroidRoom`、`AudioDeviceService`、`AudioDeviceRestorer`、`AudioTestService`、`ScreenAudioPublisher`、`LiveVideoTrackView` 等，基于 LiveKit Android SDK。
- `android/app` 的 `platform/webrtc/`：Flutter 版 `flutter_webrtc` 分叉在 Android 上的修复（AudioSwitch 音频路由、屏幕采集、播放捕获）。

规则：

- SDK 具体类型优先留在 live 适配层内。
- 界面渲染视频时使用 `LiveVideoTrackView`，不要直接使用 SDK 的渲染组件。
- 可纯化的筛选、状态和文案继续放到 app 层的 `live_display` / `LiveDisplay`。
- 两端 SDK 不同，事件顺序和能力也有差异；适配层负责把它们转成与 Dart 一致的行为，差异记入状态文档。

### protocol / auth / config（原 `lib/src/protocol`、`auth`、`config`）

网络协议、鉴权和配置。位置：`packages/core/src/protocol`、`auth`、`config`；`android/core` 的 `protocol/`、`auth/`、`config/`。API client（`api_client` / `ApiClient`）、模型（`models` / `Models`）、错误映射都在这里。

规则：

- app controller 可以调用 protocol client。
- 界面不应该直接拼请求 body；通过 app controller 或已有 API 方法进入。
- 新接口先补 protocol 方法和模型，再由 app controller 封装成界面友好的 action。服务端协议只有一份，两端的模型字段、缺省值和 JSON 读取规则要保持一致。
- 本地联调依赖 [模拟服务端](../tool/js/mock_server/README.md) 时，新接口也要在 `tool/js/mock_server/routes/` 中补上。

### 关于 v2

旧版指南中的 `lib/src/v2` 入口和 v2 迁移计划已经废弃：Flutter 版在移除前（`fcf2fdd`）已经没有 `v2/` 目录，两套新客户端也都没有对应代码，主界面就是移植后的 `home/`。新界面直接在现有分层上开发，不要再新建平行的 v2 目录或重复组件。

## 依赖方向

两套代码内部采用相同的分层，上层可以依赖下层，反之不行：

```text
界面：功能界面（home / settings / shell 界面）→ 可复用 ui
  ↓
适配层：shell 服务、live
  ↓
app
  ↓
protocol / auth / config
  ↓
运行时对等层（TypeScript：lib/、platform/；Kotlin：foundation/、plugins/、widgets/）
```

模块之间：

```text
桌面端：apps/desktop → @gang-chat/ui → @gang-chat/core
Android：:app → :core
```

实际开发时按下面约束判断：

- `app` 不依赖界面和平台实现。需要 LiveKit 会话的 controller（`LiveSessionController`）在桌面端依赖 `packages/core/src/live`，在 Android 放在 `:app`。
- `shell` 和 `live` 可以依赖平台实现和 SDK，但要把平台类型藏在 service/adapter 内。
- `home` / `settings` / shell 界面可以依赖界面框架，但业务逻辑要调用 `app`。
- `ui` 只做可复用组件和展示 adapter，不调用业务 API。
- `@gang-chat/core` 不依赖 React、DOM 专属 API 或 Electron；`@gang-chat/ui` 只依赖 `@gang-chat/core`；`:core` 不依赖 Android SDK、Compose 或 LiveKit。

## 新功能开发流程

### 0. 两端都要实现

- 每个新功能、每处行为修复都要在桌面端和 Android 中分别实现并补测试。确实只适用于一端的（例如桌面窗口、托盘、Android 推送和横竖屏），在代码里用明确的平台边界隔开，并在对应状态文档中说明。
- 两端使用对应的文件名（`room_forms.ts` ↔ `RoomForms.kt`）和相同的类名、方法名、字段名、中文文案，便于逐文件对照和评审。
- 移植约定同样适用于新代码：类型映射、异步模型、界面组件写法、`PORT:` 注释。新增代码没有 Dart 原文可以对照，两端之间就以彼此为准，保持行为一致。
- 建议先在一端完成并通过测试，再按约定翻译到另一端，并尽量在同一次改动中提交两端。

### 1. 先定义业务能力

先问清楚这个功能是否已有 controller/helper（两端同名）：

- 房间：`RoomsController`、`room_forms` / `RoomForms`、`room_display` / `RoomDisplay`。
- 消息：`MessagesController`、`message_display` / `MessageDisplay`。
- 文件：`FileDownloadsController`、`file_display` / `FileDisplay`。
- Live：`LiveController`、`LiveSessionController`、`live_display` / `LiveDisplay`。
- 设置：`SettingsController`、`settings_shell_state` / `SettingsShellState`。
- 表情包：`StickerPacksController`、`sticker_management` / `StickerManagement`、`sticker_uploads` / `StickerUploads`。
- 音乐盒与歌单：`MusicBoxController`、`music_box_display` / `MusicBoxDisplay`、`personal_music_playlists` / `PersonalMusicPlaylists`。

如果没有，先在 app 层加最小可测试逻辑，两端各一份。

### 2. 再接平台或 SDK 能力

| 能力 | 桌面端 | Android |
| --- | --- | --- |
| 文件选择/保存 | 扩展 `FileSelectionService`（`packages/core/src/shell`），原生部分在 `apps/desktop/src/main/files.ts` | 扩展 `FileSelectionService`（`android/app` 的 `shell/`），原生部分在 `platform/FileSelector.kt` |
| 剪贴板 | `ClipboardService` + `apps/desktop/src/main/clipboard.ts` | `ClipboardService` + `platform/Clipboard.kt` |
| 安全存储、偏好 | `platform().secureStorage` / `platform().preferences`，主进程 `storage.ts` | `plugins/` 的 `FlutterSecureStorage` / `SharedPreferences` 门面，实现为 `platform/FlutterSecureStorageStore.kt`、`FlutterSharedPreferencesStore.kt` |
| LiveKit / WebRTC | `packages/core/src/live` | `android/app` 的 `live/`、`platform/webrtc/` |
| 窗口控制 | `DesktopWindowController` + `apps/desktop/src/main/window.ts` | 不适用 |
| 通知、后台、推送 | `platform().notifications`，主进程 `system.ts` | `platform/GangChatNotifications.kt`、`GangChatBackgroundService.kt`、FCM 服务 |

不要在页面里直接调用平台 API 或宿主能力。

### 3. 最后写界面

界面只做：

- 读取当前 state。
- 调用 controller/service。
- 调用 app helper 生成展示文案、按钮状态、列表状态。
- 展示 loading/error/empty/result。
- 管理必要的文本控制器、焦点和弹窗生命周期。

复杂组件拆分优先级：

1. 可跨页面复用的组件放 `ui/`。
2. 只属于一个功能的大块界面放对应功能的拆分文件。
3. 可测试的状态逻辑抽到 app 层。

界面写法遵循移植约定：桌面端用 `ViewState`（`packages/ui/src/lib/view_state.ts`）或 hooks，样式取 tokens；Android 用 `ViewState`（`android/app` 的 `widgets/ViewState.kt`）和 Compose，注意 Flutter 与 Compose 的布局默认值差异。

### 4. 联调与核对

- 用模拟服务端联调：桌面端 `npm run dev`，Android 跑 `GangAppEndToEndTest` 或在真机上安装 debug 包（命令见 README 的“测试与质量检查”）。
- 界面改动在 Android 上用 Roborazzi 截图自查，并与桌面端截图 `docs/js-port-screenshots/` 对照。
- 涉及 LiveKit、音频路由、屏幕共享、通知、推送、安全存储或安装更新时，在真实设备上验证。
- 两端无法一致的地方写进 `docs/js-port-status.md` 或 `docs/android-port-status.md`。

## 测试策略

### 测试目录与分类

客户端测试按“所属代码 + 被测模块”分类，目录与源码目录一一对应。

桌面端（vitest，见根目录 `vitest.config.ts`，匹配 `packages/*/test/**/*.test.ts(x)` 和 `apps/*/test/**/*.test.ts`）：

```text
packages/core/test/
  app/          # 业务规则、状态、controller、display/form helper
  protocol/     # API client、鉴权客户端、协议模型、错误映射
  live/         # LiveKit 适配层、SSE 客户端、音频设备逻辑
  shell/        # 剪贴板、文件、更新、本地偏好等平台边界
  support/      # 共享替身：fake_platform、fake_http_client、fake_audio_player
packages/ui/test/
  lib/          # 文本编辑等框架对等物
  ui/           # 组件库的纯函数
```

Android（JUnit；`:app` 运行在 Robolectric 上，两个模块都固定 UTC 时区）：

```text
android/core/src/test/kotlin/com/gangchat/client/
  app/ auth/ foundation/ live/ protocol/ shell/     # 纯 JVM 测试
android/core/src/testFixtures/kotlin/com/gangchat/client/testing/
  GangTestRule.kt、MockClient.kt                     # 两个模块共用的测试工具
android/app/src/test/kotlin/com/gangchat/client/
  app/ live/ platform/ shell/                       # 服务、适配层和应用壳测试
  ui/ home/ settings/ widgets/                      # Compose UI 测试与 Roborazzi 截图测试
  MainActivityTest.kt                               # 启动流程、启动画面与 Activity 回调
  GangAppEndToEndTest.kt                            # 对模拟服务端的端到端测试，默认跳过
```

分类规则：

- 桌面端测试放 `packages/<包>/test/<目录>/<文件名>.test.ts`，含 JSX 时用 `.test.tsx`，文件名与被测文件对应。
- Android 中不需要 Android 运行时的测试放 `:core` 的 `src/test`；需要 Android API、LiveKit 或 Compose 的放 `:app` 的 `src/test`。包名与被测文件相同，文件名为 `<Name>Test.kt`，截图测试为 `*ScreenshotTest.kt`。
- 从 Dart 移植的用例保留原用例名（Kotlin 用反引号函数名），便于和 `fcf2fdd` 的 `test/` 对照。
- 同一能力同时有纯逻辑和界面行为时分别建测试，不要为了少一个文件把界面测试混进逻辑测试。
- 通用 fake、fixture 或 matcher 达到两个以上使用方时，再提取到共享位置：桌面端 `packages/core/test/support/`，Android `android/core/src/testFixtures` 或相邻的 `*TestSupport.kt`（例如 `home/HomeShellTestSupport.kt`、`shell/ShellTestSupport.kt`）。单个测试专用的数据留在原文件，避免形成难以追踪的全局测试工具箱。
- 只有真实 Windows/macOS/Android、音频设备、摄像头、系统权限或 SDK 原生路径才能验证的场景（原 Flutter 的 `integration_test/` 探针），不进自动测试，必须在目标设备上手动验证并记录运行环境。

### 单元测试

每个新增 app helper/controller 都应有测试，两端各一份：

- 表单校验：输入、trim、无变更、错误文案。
- reducer/patch：busy、error、notice、selection、stale result。
- display helper：fallback、label、排序、过滤。
- controller：API 调用顺序、pending state、失败恢复。

其他单元分类的关注点：

- protocol：请求结构、响应兼容、缺失字段、未知枚举、URL 编码、超时和错误映射。
- live：加入/离开、设备切换、重复事件、并发事件、资源释放和断线恢复。
- shell：平台能力成功/取消/异常、路径与编码差异、持久化损坏和版本边界。

### 界面测试

Android 的界面测试是 Robolectric 上的 Compose UI 测试，适合验证：

- 组件库布局和交互。
- home/settings 关键 affordance 是否存在。
- 输入框、composer、sidebar 这类容易回归的布局行为。
- 指针、长按、软键盘、返回键和横竖屏。

截图测试用 Roborazzi（`@GraphicsMode(NATIVE)`、`@Config(qualifiers = ...)` 指定设备尺寸），由 `./gradlew :app:recordRoborazziDebug` 输出到 `android/app/build/outputs/roborazzi/`，仓库不提交截图基线，用于人工对照。

桌面端 `packages/ui/test/` 目前只覆盖少量纯函数；界面行为通过模拟服务端冒烟和 `docs/js-port-screenshots/` 截图对照验证。新增复杂交互时，优先把可测的状态和计算抽到 app 层或组件库的纯函数中再测试。

界面测试优先验证用户可观察行为，不依赖私有组件层级或过细的像素实现。涉及菜单、焦点、选择、悬停和键盘操作时，要同时覆盖鼠标与键盘入口（桌面端）或点按与长按（Android），以及组件销毁后的异步回调安全。

### 边界与回归测试

新增或修复功能时，至少检查与当前能力相关的边界：

- 文本：空值、空白、trim 前后、最短/最长/超长、简体中文、emoji、组合字符和大小写。
- 集合：零个、一个、多个、重复项、稳定排序、分页边界、最后一页和空结果。
- 时间：起止相同、跨月/年、闰日、时区、服务器时钟偏差、冷却倒计时和过期瞬间。
- 状态：首次加载、重复提交、并发请求、旧响应晚到、取消、重试、断线重连、退出后回调。
- 权限与身份：未登录、会话失效、普通成员、管理员、创建者、对象已删除或已离开房间。
- 数据兼容：缺失字段、旧缓存、未知类型、资源过期、记录存在但关联对象已删除。
- 组合筛选：关键词、日期、成员和分类单独使用及叠加使用，重置后应恢复默认全集。
- 批量操作：全选/全不选、部分选择、单条退化行为、选择项被实时删除、失败后的局部恢复。

修复缺陷时，回归测试应先复现缺陷的最小输入，再覆盖相邻边界；不要只断言“没有抛异常”，还要断言状态、可见结果和副作用。缺陷在两端都存在时，两端都要加回归测试。

### 服务端测试分类与数据库隔离

服务端位于相邻的 `gang-chat-server` 工程。Go 测试继续与被测 package 同目录，保证可以测试包内边界并符合 `go test ./...` 的发现规则；不要把所有 `*_test.go` 移到独立顶层测试目录。

同一 package 内按业务领域拆分测试文件。当前 chat API 测试使用 `api_auth_test.go`、`api_rooms_members_test.go`、`api_messages_test.go`、`api_notifications_test.go`、`api_search_test.go`、`api_stickers_uploads_test.go` 和 `api_live_test.go`；共享 HTTP harness 放 `api_harness_test.go`，数据库准备逻辑放 `mysql_test_support_test.go`，SQL fixture 放该 package 的 `testdata/`。过大的综合测试文件应按领域逐步拆分，但不得改变测试行为或为了拆分导出生产符号。

数据库集成测试必须遵守：

- 只连接名称以 `_test` 结尾或包含 `_test_` 的专用数据库，绝不连接开发、预发布或生产库。
- chat、idgen、musicbox 等会建表、清表或删表的 package 使用彼此独立的数据库和 DSN，不能共享 schema。当前分别使用 `GANG_TEST_MYSQL_DSN`、`GANG_TEST_IDGEN_MYSQL_DSN` 和 `GANG_TEST_MUSICBOX_MYSQL_DSN`。
- 测试开始前从受版本控制的 fixture 建立干净 schema，不能依赖上一次运行残留数据。
- 测试可重复运行，单独运行、整包运行和 `go test ./...` 的结果应一致。
- 对数据库时间、自动递增 ID 和行顺序不作隐式假设；需要顺序时在查询和断言中明确表达。
- 测试日志、fixture 和失败信息不得包含真实邮箱凭据、访问令牌或生产数据。

### 关联主页验证

`gang-chat-homepage` 是独立静态站点。无浏览器依赖的 JSON、版本号、资源链接和纯函数验证使用 Node 内置测试能力放在其 `tests/`，通过 `npm test` 运行；真实导航、下载和响应式布局再使用浏览器级测试。主页测试不放进客户端仓库，避免客户端与网站工具链互相污染。

### 推荐命令

小改动先跑聚焦验证，例如：

```sh
npx vitest run packages/core/test/app/room_forms.test.ts
cd android && ./gradlew :core:test --tests 'com.gangchat.client.app.RoomFormsTest'
```

按分类运行桌面端测试：

```sh
npx vitest run packages/core/test/app
npx vitest run packages/core/test/protocol
npx vitest run packages/core/test/live
npx vitest run packages/core/test/shell
npx vitest run packages/ui/test
```

按包运行 Android 测试（在 `android/` 目录下）：

```sh
./gradlew :core:test --tests 'com.gangchat.client.app.*'
./gradlew :core:test --tests 'com.gangchat.client.protocol.*'
./gradlew :app:testDebugUnitTest --tests 'com.gangchat.client.ui.*'
./gradlew :app:testDebugUnitTest --tests 'com.gangchat.client.home.*'
./gradlew :app:testDebugUnitTest --tests 'com.gangchat.client.settings.*'
./gradlew :app:testDebugUnitTest --tests 'com.gangchat.client.shell.*'
```

截图只在需要时生成，例如：

```sh
./gradlew :app:recordRoborazziDebug --tests 'com.gangchat.client.home.*'
```

对模拟服务端的端到端测试：

```sh
node tool/js/mock_server/server.mjs --port 21116
cd android && GANG_E2E_API_BASE_URL=http://127.0.0.1:21116/api/v1 \
  ./gradlew :app:recordRoborazziDebug --tests 'com.gangchat.client.GangAppEndToEndTest'
```

跨层或提交前跑全量：

```sh
python3 tool/check_utf8.py
npm run typecheck
npm test
cd android && ./gradlew :core:test :app:testDebugUnitTest
```

服务端按范围运行：

```sh
go test ./internal/chat -count=1
go test ./... -count=1
```

`-count=1` 用于避免 Go 测试缓存掩盖数据库隔离、时间或顺序问题。需要数据库的测试应在对应 DSN 已配置且指向独立测试库时运行；未配置时只能将其视为“集成测试未执行”，不能据此宣称服务端全量通过。

## 提交前检查清单

- 两端都实现了改动，并各自有测试；只适用于一端的改动有明确的平台边界和说明。
- 两端的文件名、标识符和中文文案保持对应；无法一致的地方有 `PORT:` 注释，并记入 `docs/js-port-status.md` / `docs/android-port-status.md`。
- `packages/core` 没有 React、Electron、`@gang-chat/ui` 依赖，app 层也没有 `livekit-client`（上面的 `rg` 审计为空）；`android/core` 仍能独立编译。
- 界面没有直接使用平台实现或 SDK 渲染组件：`rg -l "from 'livekit-client'" packages/ui/src` 只有 `live/live_video_track_view.tsx`；`rg -l "^import (io\.livekit|livekit\.|org\.webrtc)" android/app/src/main/kotlin` 只在 `live/` 和 `platform/webrtc/` 下。
- 新业务逻辑有 app 层测试。
- 新平台能力通过 shell/live 适配层注入；桌面端经 `Platform` 接口。
- 测试放在正确的模块目录。
- 缺陷修复包含最小复现和相关边界回归测试。
- `npm run typecheck` 和 `npm test` 通过。
- `./gradlew :core:test :app:testDebugUnitTest` 通过；改了 Android 界面时用 Roborazzi 截图自查过。
- `python3 tool/check_utf8.py` 通过。
- 涉及设备路径时已在真机验证，或明确记录未验证的部分。
- 涉及服务端时，相关 package 测试与 `go test ./... -count=1` 通过，或明确记录未执行的数据库集成测试。

## 常见反模式

- 在界面组件里拼 API request body。
- 在界面组件里维护一套可复用的搜索、排序、权限判断。
- 只在一端实现功能或修复，让两端行为悄悄分叉。
- 两端给同一个概念起不同的名字，或各自改写同一句中文文案。
- 在 `packages/core` 中 import React、Electron 或 DOM 专属 API，或绕过 `platform()` 直接调用宿主能力。
- 为了用一个 Android API 把本可纯化的逻辑整个搬进 `:app`；应先把纯逻辑留在 `:core`。
- 为一个业务页面写只能在该页面使用的通用按钮/input。
- 让 shell 服务返回平台或第三方 SDK 类型，导致界面层继续被平台绑定。
- 新建平行的 v2 目录或重复组件。

## 推荐新增文件命名

| 类别 | 桌面端（snake_case，含 JSX 用 `.tsx`） | Android（PascalCase） |
| --- | --- | --- |
| app 纯逻辑 | `<domain>_forms.ts`、`<domain>_display.ts`、`<domain>_state.ts` | `<Domain>Forms.kt`、`<Domain>Display.kt`、`<Domain>State.kt` |
| app controller | `<domain>_controller.ts` 或复用已有 controller | `<Domain>Controller.kt` 或复用已有 controller |
| shell 服务 | `<capability>_service.ts` | `<Capability>Service.kt` |
| live 适配 | `audio_*.ts`、`live_*.ts` | `Audio*.kt`、`Live*.kt` |
| 功能界面拆分 | `home_<section>.tsx`、`settings_<section>.tsx` | `Home<Section>.kt`、`Settings<Section>.kt` |
| ui 组件 | 通用名，例如 `button.tsx`、`input.tsx`、`navigation.tsx` | `Button.kt`、`Input.kt`、`Navigation.kt` |
| 测试 | `<name>.test.ts` | `<Name>Test.kt`，截图 `<Name>ScreenshotTest.kt` |

## 当前稳定边界

当前可以依赖的边界（两端同名）：

| 边界 | 桌面端 | Android |
| --- | --- | --- |
| `AuthenticatedAppContext`：登录后的 API、token、config、表情包 store 上下文 | `packages/core/src/app` | `android/core` 的 `app/` |
| `AuthenticatedAppServices`：登录后的 controller 集合 | `packages/core/src/app` | `android/app` 的 `app/` |
| `AuthSessionController`：真实登录、缓存和登出状态 | `packages/core/src/app` | `android/core` 的 `app/` |
| `ClipboardService`、`FileSelectionService`：平台能力 adapter | `packages/core/src/shell` | `android/app` 的 `shell/` |
| `LocalAudioDeviceStore`：`AudioDeviceStore` 的本地实现 | `packages/core/src/shell` | `android/core` 的 `shell/` |
| `LiveSessionController`、`LiveAudioDeviceService`、`AudioTestService`：Live/audio adapter | `packages/core/src/app`、`live/` | `android/app` 的 `app/`、`live/` |
| `LiveVideoTrackView`：视频渲染 | `packages/ui/src/live` | `android/app` 的 `live/` |
| `Platform` 接口（仅桌面端） | `packages/core/src/platform/platform.ts` | 不适用 |
| `plugins/` 门面（仅 Android） | 不适用 | `android/core` 的 `plugins/` |
| 组件库：所有页面都应优先复用 | `packages/ui/src/ui` | `android/app` 的 `ui/` |
