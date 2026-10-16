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

### 应用层：房间列表、语音频道、音乐盒、表情包（`packages/core/src/app`）

- 表情包 zip 解包：`package:archive` 改为 `lib/zip.ts` 的纯 TypeScript 实现（stored / deflate、
  ZIP64），不依赖 `DecompressionStream` 或 Node `zlib`，React Native 可直接用；加密或其它压缩方式的
  条目读作 `null` 并跳过（与 Dart 的 `content == null` 分支一致）。
- 歌单的可选服务端扩展（Dart 的 `api is RoomMusicPlaylistCloneApi` 等）改为检查对应方法是否存在，
  因为 TS 接口没有运行时类型。
- `PersonalMusicPlaylistsController.room(...)` 的 `api` 标识在 Dart 里是按值比较的 record；
  TS 版返回按 `(roomApi, personalApi, roomId, canManage)` 驻留的冻结元组，相同参数保持 `===`。
- 音乐盒命令 id 用 `Date.now() * 1000`（JS 没有微秒时钟），唯一性仍靠自增序号。
- `copyWith` 的"不变"哨兵（`StickerPanelLoadState`、`StickerPreviewState`、
  `LiveScreenSourcePickerState`）：不传或传 `undefined` 保持原值，传 `null` 清空。
- `ArgumentError.value(delta, ...)` 改为 `Error`，文案沿用 Dart 的 `Invalid argument (delta): ...`。

### 语音（`packages/core/src/live`、`packages/ui/src/live`）

- LiveKit 换成官方 JS SDK `livekit-client`（`@livekit/react-native` 包装的也是它）。房间事件先转成
  `LiveRoomEvent`（`debugHandleRoomEvent` 的入参）再交给与 Dart 一致的处理函数。完整重连时
  `livekit-client` 先拆掉所有远端参与者、再发 `Reconnecting`，与 Flutter SDK 的顺序相反；事件按微任务
  批量处理，`_coalesceLiveRoomEventBatch` 把 `reconnecting` 提前，并丢弃这次拆除产生的远端
  取消发布事件，避免误报离开、误清正在观看的共享/摄像头。
- 闭麦仍是"Option A"：采集和已发布的轨道保持不动，只把输出增益置 0。增益是挂在麦克风轨道上的
  Web Audio `TrackProcessor`（`live_audio_platform.web.ts`）；React Native 版只能用
  `MediaStreamTrack.enabled` 做开/关。新麦克风先采集、装好增益再发布，所以闭麦状态下不会漏出原声。
- 远端音频需要显式播放：订阅到的音频轨道挂到隐藏的 `<audio>` 上；房间开启 `webAudioMix`，让单人
  音量能超过 100%。输出设备用 `room.switchActiveDevice('audiooutput')`。Flutter 的
  `lk.Hardware` 全局选择改为 `live_hardware.ts` 记录，会话跟随输出选择。
- 换麦克风或热插拔恢复改为对现有轨道 `restartTrack`（先 exact，失败退回 ideal），不再关掉再开麦克风；
  断线续连后的麦克风重发会重新发布同一条轨道（新建 sender 并重新协商），不重新打开设备。
- 屏幕共享：`platform().screenCapture.selectSource(id, { audio })` 后调用一次 `getDisplayMedia`，
  视频约束是高度上限 + 帧率上限（宽度按源比例），`contentHint` 在 60 FPS 时是 `motion`，
  其余是 `detail`。主房间只发布视频（单层、`priority: 'high'`）；系统声音由
  `<userId>--screen-audio` 辅助参与者在第二个 `Room` 里发布（发布的是 loopback 轨道的副本）。
  宿主没有返回音频轨道时只共享画面。调整画质时对正在采集的轨道 `applyConstraints`，不重新弹选择器。
  `livekit-client` 没有 Flutter 分支版的 `updatePublishedVideoTrackDimensions`，不再向 SFU 重报尺寸。
- 设备枚举（`system_audio_devices.ts`）改用 `navigator.mediaDevices`：去掉 Chromium 的
  `default` / `communications` 伪设备，并把它们解析成真实设备 id 作为"系统默认"；原生通道的
  默认设备变更、拓扑变更、摄像头变更通知，改为在每次 `devicechange` 时对比前后两次设备快照得出；
  不再有监听重试。
- 麦克风测试仍用本地回环 `RTCPeerConnection` 的统计取电平（不依赖 Web Audio）；扬声器测试音
  通过所选输出设备（`setSinkId`）播放。
- 无对应能力、改为空操作的：macOS 通话结束后恢复蓝牙 A2DP 采样率、macOS 原生屏幕缩略图、Android
  音频路由与 WebRTC 初始化（`android_audio_initialization.ts` 只保留参数）。LiveKit 信令"直连不走代理"
  由主进程统一处理。
- `ScreenSource.thumbnail` 由字节改为 PNG data URL 字符串（另有别名 `thumbnailDataUrl`）；
  `LiveVideoTrack.track` 是 `livekit-client` 的 `VideoTrack`。`supportsDesktopScreenShare` 等 Dart
  顶层 getter 改为函数。`LiveVideoTrackView` 是 `<video>` 元素（`track.attach` / `detach`、
  `object-fit`、镜像用 `scaleX(-1)`）。

### 界面层：文本菜单、输入栏、表情包管理、头像裁剪、设置外壳（`packages/ui/src/ui`）

- 文本右键菜单（`text_context_menu.tsx`）：Flutter 由 `TextField.contextMenuBuilder` 触发；移植后
  由 `TextFieldEditingShortcuts` 接管 DOM `contextmenu`，按指针下的 `<input>` / `<textarea>`
  （或可选文本的 DOM 选区）构造 `EditableTextState`，菜单画在 overlay 里，`contextMenuBuilder`
  改为 `TextFieldEditingShortcuts` 的属性。剪切/复制/粘贴走 `platform().clipboard`，插入文本用
  `execCommand('insertText')`，保留浏览器撤销栈并触发输入框自己的 `onChange`。
  `buildTextFieldContextMenu` 去掉了 `BuildContext` 参数，`tapRegionGroupId` 保留但不再使用（点
  外部总会关闭菜单）。DOM 输入框右键不会丢失选区和焦点，所以右键保护只保留两点：右键时没有选区则
  套用 `secondaryClickSelection`；保护期间下一次（输入框以外的）主键按下回调
  `onGlobalPrimaryPointerDownDuringSecondaryClickProtection`。Esc 先关菜单，不会顺带关掉下面的对话框。
- `PasteTextIntent` 覆盖改为 `PasteTextIntentActions`：快捷键粘贴是 DOM `paste` 事件，先同步取出
  剪贴板文本并 `preventDefault()`，再异步等 `onPasteFiles` 判断是否已作为附件消费，未消费才插入
  文本。与 `ClipboardService.dispatchWebPaste` 的页面级监听并存时，以先 `preventDefault()` 的一方为准。
- 输入栏（`chat_composer.tsx`）：`HardwareKeyboard` 全局处理器改为输入框子树的 capture 阶段
  `keydown`（只在输入框有焦点时生效）；输入法组字中的 Enter 交给输入法；Shift/Ctrl+Enter 的换行用
  `execCommand` 插入，失败时才回退到 Dart 的改控制器写法。`AnimatedSize` 用高度过渡实现，只在动画期间裁剪。
- 头像裁剪（`avatar_crop_dialog.tsx`）：`<img>`（object URL）+ `<canvas>` 生成 512×512 PNG；缩放滑杆
  是按 Material `Slider` 主题手写还原的；遮罩用 SVG even-odd 路径。
- 表情包管理（`sticker_manager.tsx`）：`Button.withoutIcon()` 改为 `cloneElement(button, { showIcon: false })`；
  预览图与尺寸行通过加载 `cachedMediaImageProvider` 返回的 URL 读取图片原始尺寸。
- 设置外壳（`settings_scaffold.tsx`）：`SettingsList.physics` 只支持 `'clamping'`
  （`overscroll-behavior: contain`）；`LayoutBuilder` 用 `useElementSize` 测宽。

### 界面层：基础组件（`packages/ui/src/ui`、`packages/ui/src/lib`）

- 新增的框架级辅助：`lib/text_editing.ts`（`TextEditingController` / `TextSelection` /
  `TextEditingValue` / `FocusNode` / `UndoHistoryController` / `TextInputFormatter` 及
  `TextInputAction`、`TextInputType`、`AutofillHints`）、`lib/edge_insets.ts`（`EdgeInsets.*` +
  `paddingStyle`）、`lib/mouse_cursors.ts`（`SystemMouseCursors`、`kTouchSlop`）、`lib/text_measure.ts`
  （canvas 同步测宽，代替 `TextPainter`）、`lib/use_element_size.ts`（`LayoutBuilder`）、
  `lib/tap_region.ts`（`TapRegion.onTapOutside`）、`lib/progress_indicator.tsx`
  （`CircularProgressIndicator`，关键帧在 `ui/ui.css`）。`Icon` 支持 `IconTheme`。
- 所有带 `BuildContext` 的顶层函数去掉该参数：`showUiContextMenu({ position, sections })`、
  `showFloatingNotice(message, { tone, duration })`、`showUiDialog({ title, child, icon })`、
  `Button.minimumWidthForLabel({ label, hasIcon, padding })`；`AppNotificationHost.maybeOf()` /
  `of()` 返回最近挂载的宿主（组件里用 `useAppNotificationController()`）；`AppConfigScope.of` /
  `MediaCacheScope.of` 是 hook（`useAppConfig` / `useMediaCache`）。`Duration` 一律毫秒，
  `null` 表示常驻。位置用窗口坐标 `{ x, y }`。
- `PressableSurface`：只有主键触发按下/点击（Dart 的 `Listener` 对右键也生效，DOM 右键交给菜单）；
  按下后在 window 上跟踪指针，嵌套的可按压面与 Flutter 一样各自收到完整手势。内容层是纵向 flex 并
  垂直居中（Flutter 给子组件紧约束高度）。`cornerCut` 用 CSS `clip-path`。
- `Button`：Flutter 里按钮在有界父级中撑满、在 `Row` 里收缩；移植用 `align-self: stretch` 得到同样
  的分界（`Center`/`Wrap` 里因此会收缩，`DialogFrame.actions` 改为纵向排列以保持整行按钮）。
  `withoutIcon()` 改为 `showIcon={false}`。按钮文字强制单行省略（`ui.css` 的 `.gang-button-label`）。
- `UiTooltip`：按 Material 桌面行为实现（悬停 380ms、其它提示打开时立即显示、离开 100ms、任意按下
  关闭、`positionDependentBox` 定位）；`triggerMode` 只影响触屏，桌面忽略。提示气泡不可悬停停留。
- `Input`：桌面 Flutter 版把文字/图标下移 4–5px 来补偿自身排版，浏览器本身垂直居中，故按 Android
  分支对称留白。`onTapOutside` 有值时浏览器仍会让输入框失焦；`tapRegionGroupId` 仅保留参数。
  Enter 提交时按 Flutter 默认行为失焦（`next`/`previous` 除外，且不移动焦点）。
- `SegmentedControl`：溢出布局用横向滚动容器；滚轮转横向滚动、鼠标拖动滚动（1px 判定，拖动后吞掉点击）；
  `animateTo` 用 rAF 缓动实现。`FittedBox(scaleDown)` 未实现（宽度计算已保证标签放得下）。
- `Sidebar`：不再区分有界/无界高度，统一用"中间可滚动"布局（页脚间距 16）。
- `CachedAssetImage` / `cachedMediaImageProvider`：图片直接用 `platform().cachedMediaUrl(url)`，
  `cache` 参数只为保持签名；仍用 `MediaCacheRequest.tryFromUrl` 校验地址（无效时报"图片地址无效"）。
- `UiContextMenuTriggerRegion`：DOM `contextmenu`（最内层处理并阻止冒泡）；`onTap` 是普通点击，
  不再与子组件的点击竞争。菜单的淡出动画（60ms）未实现。
- `GangScrollBehavior`：DOM 用经典滚动条（占位），不需要额外的右侧留白。
- `WindowChromeInsets.of()`：移动端 `viewPadding.top` 分支为 0；`DotGridBackground` 用 canvas 按设备像素绘制。
- `AnchoredPanelAnchor`：浮层内容渲染在 `OverlayHost` 中，位于两者之间的 React context 不会传入面板。
