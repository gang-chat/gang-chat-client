# TypeScript / Electron 移植状态

桌面端（Windows、macOS）从 Flutter 一比一移植到 Electron + React；Android 暂时保持
Flutter。约定见 `docs/js-port-conventions.md`。本文记录各模块的移植状态，以及与 Flutter
实现有意不同的地方（代码里以 `// PORT:` 注释标出）。

## 架构

| 层 | 位置 | 说明 |
| --- | --- | --- |
| 逻辑层 | `packages/core` | 协议、鉴权、业务控制器、LiveKit 会话（`livekit-client`）、平台服务。平台无关，未来 React Native 移动端直接复用 |
| 界面层 | `packages/ui` | React DOM 组件，按 Flutter Widget 一比一移植 |
| 桌面壳 | `apps/desktop` | Electron main / preload / renderer；实现 `Platform` 接口 |
| 联调用模拟服务端 | `tool/js/mock_server` | 本地模拟 `/api/v1`，用于无法连接真实服务器时的冒烟测试 |

## 与 Flutter 实现的差异

### 协议与鉴权

- 模型解析：JSON 列表里出现非对象元素时跳过，而 Dart 会抛异常（`models.ts` `_listOfMaps`）。
- `AuthClient` / `GangApiClient` 默认共用 `platform().http`，不会在 `close()` 时关闭共享连接；
  测试注入的客户端照常关闭。
- 认证在连续 TLS 握手失败后的"改走环境代理重试"：桌面端所有请求都由主进程发出，
  代理策略在主进程统一设置（与 Dart 一致：配置的 Gang Chat 主机直连，其它主机用
  `http(s)_proxy` 环境变量），因此重试仍走同一个 `HttpClient`。
- 上传进度在传输层拉取下一块数据时回调（异步生成器语义），而不是在写出后回调。

### 桌面壳

- HTTP（含 SSE、上传、下载）在 Electron 主进程用 Chromium 网络栈发出，没有 CORS 限制。
- 刷新令牌用 Electron `safeStorage`（macOS 钥匙串 / Windows DPAPI）加密保存在用户目录；
  Flutter 版存在系统钥匙串/凭据管理器里，切换后需要重新登录一次。
- 偏好设置（SharedPreferences）保存在用户目录的 `preferences.json`，键名不变。
- 远端图片经 `gang-media://` 协议走主进程磁盘缓存（512MB LRU）。
- 屏幕共享系统声音：Windows 用 Chromium 的 WASAPI loopback；macOS 依赖 Chromium 的
  ScreenCaptureKit loopback（macOS 14.2+），更早的系统共享时没有声音。Flutter 版在
  macOS 上用自己的 ScreenCaptureKit 采集。
- 系统朗读用 Chromium `speechSynthesis`（同样调用系统语音），不再调用 PowerShell / `say`。

### 平台服务（`packages/core/src/shell`）

- 音频播放（消息提示音、语音消息、试听）统一走 `shell/audio_player`：桌面端是
  `HTMLAudioElement`（`audio_player.web.ts`），原生端是占位实现。Windows 的临时 WAV 文件
  绕行方案和 macOS 音频会话设置不再需要。
- 系统朗读经 `platform().speech`，提示语言 `zh-CN`。
- 粘贴：输入框的 DOM `paste` 事件交给 `ClipboardService.dispatchWebPaste`；非 PNG 图片的
  重新编码由宿主完成。文件拖放由界面层把 DOM 事件交给 `FileDropService.dispatch()`。
- 窗口：`startDragging` 改为 CSS 拖拽区；Flutter 的首帧等待是空操作；标题栏、阴影、
  可最大化等设置合并进 `setLayout`。只有 Windows 有托盘（与 Flutter 一致），其它平台
  `minimizeToTray()` 退回最小化。
- 应用内更新改用 `electron-updater`（基于 Electron 自带的 `autoUpdater`），读取存储桶
  `releases/` 下的 `latest.yml` / `latest-mac.yml`：Windows 按 blockmap 差分下载 NSIS 安装包，
  退出后安装；macOS 走 Squirrel.Mac（需要 Developer ID 签名，构建时设置
  `GANG_MAC_UPDATES_SIGNED=1`）。更新提示、进度和取消界面沿用 Flutter 版。开发版或未签名的
  macOS 版自动退回 Flutter 版的流程（Windows 自行下载安装包；macOS 在浏览器中下载 DMG）。
  Sparkle 不再使用。发布流程需要把 `latest*.yml`、`*.blockmap` 和 macOS 的
  `GangChat-<version>-mac.zip` 一起上传到 `releases/`。
- 偏好迁移：保留 Flutter 版的迁移逻辑；音量类键一律按 double 写入（JSON 里 1.0 和 1 无法区分）。
- Android 专用服务（推送、屏幕旋转、系统 UI）在桌面端是空实现，Android 仍用 Flutter 版。

### 应用层：账号、设置、文件传输、语音录制（`packages/core/src/app`）

- 时长一律用毫秒数（`ServerClock.requestRoundTrip`、语音录制时长、网络延迟等）。
- `gangChatClientVersion` 读取打包时注入的 `__GANG_CHAT_VERSION__`（对应
  `--dart-define=GANG_CHAT_VERSION`；桌面端取 `apps/desktop/package.json` 的 `version`），
  未注入时为 `1.0.0`。
- `DateTime.tryParse` 由 `settings_about.ts` 的 `_tryParseDateTime` 按 Dart 的 ISO-8601 规则实现
  （不接受 `Date.parse` 的宽松格式，无时区按本地时间）。
- 文件下载与媒体缓存：磁盘读写经 `platform().files`；新增可选的
  `FileSystemBridge.openRead`（流式读取，缺省时退回 `readFile`）。默认 HTTP 客户端是
  `platform().http` 的单次下载包装（`_AbortableHttpClient`），`close()` 只中止本次下载，
  不关闭共享连接。`MediaCacheController` 返回 `MediaCacheFile`（代替 `dart:io` 的 `File`）。
- 语音录制：`VoiceRecorderController` 依赖 `AudioRecorder` 接口（`record` 插件的子集），由宿主经
  `setAudioRecorderFactory()` 安装，首次使用时才创建（Dart 版在构造时创建）。桌面端实现是
  `MediaRecorderAudioRecorder`：Windows / macOS 用系统 AAC 编码器录 m4a；没有 AAC 编码器时
  退回 16 kHz 单声道 WAV（与 Flutter Web 版相同）。
- `StateError` 改为 `Error`，错误文案前缀是 "Error:" 而不是 "Bad state:"。
- `CloseBehaviorStorage.storageValue` 扩展 getter 改为函数 `closeBehaviorStorageValue(behavior)`。

### 应用层：消息、房间展示、搜索与实时事件（`packages/core/src/app`）

- `composerAttachmentGlyph` 返回 `Icons.*` 的键名（如 `'image_outlined'`），因为 core 不依赖
  UI 的图标表；界面层用 `Icons[composerAttachmentGlyph(...)]` 渲染。
- `RoomMessageHistoryCategory.apiValue` 扩展 getter 改为
  `RoomMessageHistoryCategoryValue.apiValue(category)` / `roomMessageHistoryCategoryApiValue(category)`。
- `fnv1a64` / `fnv1a64Signed` 返回 `bigint`，十六进制输出与 Dart 版一致（缓存键不变）。
- 本地时间：Dart 的 `toLocal()` 直接用 `Date` 的本地 getter；`DateTime(y, m, d)` 是本地午夜；
  "昨天 / 前天"等天数差按 `Duration.inDays`（毫秒差截断）计算，跨夏令时与 Dart 一致。
- `RoomNotificationDateRange` 的 `==` 改为 `equals()`；起止日期颠倒时抛 `RangeError`
  （Dart 是 `ArgumentError`）。
- 音乐组件剪贴板信封的 base64url 编解码手写实现，不依赖 `btoa` / `Buffer`（React Native 可用）。
- `MessagesController` 的语音时长参数是毫秒；`fileTransfers` 是 `Map<string, FileTransferState>`。

