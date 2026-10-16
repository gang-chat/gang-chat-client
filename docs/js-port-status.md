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
- 应用内更新：Windows 下载安装程序后启动；macOS 没有 Sparkle（`Platform.updater` 为空），
  走"下载并打开 DMG"的流程。
- 偏好迁移：保留 Flutter 版的迁移逻辑；音量类键一律按 double 写入（JSON 里 1.0 和 1 无法区分）。
- Android 专用服务（推送、屏幕旋转、系统 UI）在桌面端是空实现，Android 仍用 Flutter 版。
