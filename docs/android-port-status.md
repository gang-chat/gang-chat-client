# Android 原生移植状态

Android 客户端已从 Flutter 一比一移植为原生 Kotlin + Jetpack Compose，代码在 `android/`；
Flutter 工程随后从仓库移除。移植约定见 `docs/android-port-conventions.md`。本文记录各模块进度、
与 Flutter 版的差异，以及需要真机确认的部分。

> Flutter 客户端已从仓库移除。本文提到的 Dart 源码（`lib/`、`test/` 等）以提交 `fcf2fdd` 为准，
> 可用 `git show fcf2fdd:lib/src/<路径>` 查看。

## 进度

| 范围 | 模块 | 状态 |
| --- | --- | --- |
| Dart 运行时对等物（async、Stream、JSON、DateTime、Uri、http、插件门面） | `:core` foundation / plugins | 完成 |
| Flutter 框架层对等物（ViewState、BuildContext、Navigator、Overlay、Dialog、文本、MediaQuery） | `:app` widgets | 完成 |
| 协议、鉴权、配置 | `:core` protocol / auth / config | 完成（118 个移植用例通过） |
| 业务控制器 | `:core` / `:app` app | 完成（`authenticated_app_services` 随主界面一起移植） |
| 平台服务（通知、前台服务、FCM、更新安装、存储） | `:app` platform | 完成 |
| LiveKit 语音、摄像头、屏幕共享 | `:app` live | 完成（86 个测试；SDK 路径需真机验证） |
| 界面组件库 | `:app` ui | 完成（93 个测试含 12 组截图） |
| 登录、邮箱验证、找回密码、更新提示 | `:app` shell | 完成（41 个测试 + 16 张截图） |
| 聊天、语音频道、通知、房间管理、设置 | `:app` home / settings | 完成 |
| 主框架（home_shell）与应用入口（gang_app） | `:app` home / shell | 完成（应用入口 16 个测试 + 1 个启动画面用例；另有对模拟服务器的端到端测试） |

## 移除 Flutter

- 删除了 `lib/`、`test/`、`integration_test/`、`ios/`、`linux/`、`macos/`、`windows/`、`web/`、
  `third_party/`、`installer/`、`deploy/`、`pubspec.*` 和 Dart 脚本。Windows / macOS 由 Electron 版
  （`apps/desktop`）发布，Web 版改用同一套 React 界面（`apps/web`，见 `docs/web-spike.md`），
  发布流程的 `deploy-web` 部署的是它。
- `tool/check_utf8.dart` 改为 `tool/check_utf8.py`（同样的检查，另加一个乱码特征 `鏂囦欢`）。
- CI：`.github/workflows/android.yml` 跑 UTF-8 检查、`:core:test :app:testDebugUnitTest` 并打 debug APK；
  `release.yml` 的 `build-android` 改用 Gradle：按 tag 写 `android/version.properties`，
  `:app:assembleRelease`，再用 `aapt2 dump badging` 核对 versionCode / versionName。签名、FCM 配置、
  产物文件名与 Flutter 版相同。
- 签名与 ABI：发布包同时带 v1（JAR）和 v2 签名（Flutter 版只有 v2；部分手机的安全安装检测和第三方
  扫描只认 v1 文件，会把只有 v2 的包当成“没有签名”），`release.yml` 校验两者都在。原生库只打包
  Flutter 版的三种 ABI（arm64-v8a、armeabi-v7a、x86_64）：JNA 经 LiveKit 带进来的 armeabi、mips、
  mips64 和缺 LiveKit 库的 x86 不再打包。
- 原地升级：applicationId、签名、versionCode 规则不变，`shared_preferences` 与
  `flutter_secure_storage` 的存储格式按原插件复现，从 Flutter 版覆盖安装后保留登录状态和设置。

## 需要真机确认

本仓库的验证环境没有模拟器，界面和逻辑用 Robolectric + Roborazzi 截图测试覆盖，并对模拟服务端
（`tool/js/mock_server`）跑过端到端测试（手机、平板各一遍：登录、房间列表、聊天收发、实时消息、
通知、设置、退出）。以下路径只能在真机上验证：

- LiveKit 入会、发布/订阅音视频、自动重连；AudioSwitch 的听筒/扬声器/蓝牙路由。
- 屏幕共享（MediaProjection 授权、前台服务类型）和播放捕获的共享声音。
- Android Keystore 加密的凭据存储，以及从 Flutter 版覆盖安装后的数据读取。
- FCM 推送、通知点击跳转、数字角标、前台服务与任务划除通知。
- 启动画面到首帧的衔接、APK 后台下载与系统安装器。

## 修正了的 Flutter 版问题

下面三处 Flutter 版在 Android 上有问题，原生版先一比一保留，确认后已修正（需真机验证听感和路由）：

1. 新消息提示音不响：Flutter 版用 audioplayers 的低延迟模式播放内存里的音效，audioplayers_android
   不支持，`play` 一直静默失败。现在经 `SoundPoolCuePlayer`（`SoundPool`，音效先写进缓存目录）按
   通知事件（`USAGE_NOTIFICATION_EVENT`）播放：跟随通知音量和响铃模式，通话中走通话的设备，
   不申请音频焦点，所以不会暂停其他应用的音乐。
2. 进入/离开语音频道的提示音不响，链在其后的语音播报也因此被跳过：提示音同样改走 `SoundPoolCuePlayer`。
   通话中按语音通话（`USAGE_VOICE_COMMUNICATION`）播放，与通话同一设备（听筒、扬声器或蓝牙耳机）、
   同一音量；通话已结束时（离开后的提示音）按媒体播放。语音播报（TextToSpeech）用同样的规则设置
   音频属性。
3. 录语音消息时把麦克风切到蓝牙耳机，结束后清除通信设备，会打乱正在进行的通话的音频路由（插件
   即使没切换过也会清除）。现在语音通话中录音不动路由；不在通话中时只撤销自己做的切换并恢复原来的
   通信设备，停止录音时也会撤销（插件只在取消和释放时撤销，正常结束后一直占着蓝牙通话链路）。
4. 输入、输出音量默认 50%：Flutter 版（各端都是）把默认的 0.5 直接当增益，发出去的声音和听到的声音
   默认都只有一半。现在两者默认 100%（滑块拉满），并各有一个“增强 +50%”开关，打开后增益为音量 × 1.5；
   开关存为 `gang.audioInputVolumeBoost` / `gang.audioOutputVolumeBoost`。存着旧默认 0.5 的输入、输出
   音量在第一次读取时改成 1.0（只做一次，记在 `gang.audioUnityVolumeDefaults`）；音乐盒和屏幕共享音量仍
   默认 50%。桌面端和 Web 端同样修改。

## 与 Flutter 版的差异

### 协议与鉴权（`:core` protocol / auth / live / shell）

- Dart 的 `HandshakeException`（TLS 握手失败）对应 `SSLException`，或消息以 `HandshakeException:` 开头、
  cause 为 `SSLException` 的 `ClientException`。
- 环境代理（`HttpClient.findProxyFromEnvironment`）按 `http_proxy` / `https_proxy` / `no_proxy` 移植；
  代理认证改由 OkHttp 的 proxy authenticator 在 407 时（CONNECT 隧道则预先）发送；代理配置无效时直连
  （dart:io 会抛错）。
- JSON 里读进 `Int` 字段的整数按 32 位截断（Dart `int` 是 64 位）；字节数、文件大小、上传进度、
  Unix 秒时间戳用 `Long`。
- SSE 事件流里的非法 UTF-8 字节被替换而不是报错（Dart 的解码器是严格的）。
- 表情包缓存的文件读写放在 `Dispatchers.IO`。
- `TokenStore` 去掉了只对 macOS 有意义的钥匙串选项。

### 平台层（`:app` platform / shell）

- 允许明文 HTTP（`android:usesCleartextTraffic="true"`）：Flutter 的 Dart `HttpClient` 不受 Android
  明文流量策略约束，`http://` / `ws://` 地址（自建 LiveKit、本地服务器）都能连；OkHttp 和 LiveKit
  SDK 遵守该策略，不打开就会在 Android 9+ 上拒绝连接。
- MethodChannel 层已删除，原来经通道调用的原生代码改为直接调用。通知渠道 id、通知 id、请求码、
  intent extra、偏好文件名、FileProvider authority 与 Flutter 版保持一致；启动 Activity 仍是
  `com.gangchat.client.MainActivity`。前台服务与 FCM 服务移到 `platform` 包（没有持久化数据引用它们的类名）。
- 存储兼容 Flutter 版的磁盘格式（已对照插件源码并用 Robolectric 构造的数据验证）：
  `shared_preferences_android` 2.4.26（`FlutterSharedPreferences`、`flutter.` 前缀、double/列表编码、
  旧版 Base64 列表、BigInteger）；`flutter_secure_storage` 10.3.1（AES-GCM 数据、RSA-OAEP 包装的密钥、
  旧 PKCS1+CBC 迁移）。Keystore 部分无法在本环境验证；密钥不可用时（例如备份恢复到新设备）
  与插件一样清空存储，用户重新登录一次。未移植应用从未启用过的 EncryptedSharedPreferences 导入和生物识别加密。
- 消息提示音：Flutter 版在 Android 上从未响过，原生版已修正，见“修正了的 Flutter 版问题”第 1 条。
- 文件路径类、仅 Web 的剪贴板/文件 API 在 Android 上是空操作；`saveBytesAsBrowserDownload` 删除。
- 从通知启动时的房间 id 只能通过 `takeInitialNotificationRoomId()` 取一次，之后的点击走
  `selectedNotificationRoomIds`。
- IO 与 ContentResolver 操作移到 `Dispatchers.IO`（Flutter 宿主里在主线程）。
- 安全存储两次读取都失败时返回 `null`（插件返回字符串 "Data has been reset"）。
- 平板判断读当前窗口尺寸（Flutter 在 `main()` 里读视图尺寸，可能还是 0）。

### 录音、更新、试听（`:app` app / shell / platform）

- 语音消息录音替换 `record` 插件（按 record 7.1 / record_android 2.1.1 源码移植）：AAC-LC 64 kbps、
  44.1 kHz 单声道、`MediaMuxer` 写 MPEG-4（`.m4a` / `audio/mp4`），文件名 `voice_<微秒时间戳>.m4a`。
  编码器在录音线程同步运行；权限请求走 activity result registry；Android 11 及以下等待蓝牙耳机连接
  最多 5 秒（插件无限等待）；新录音会等上一个取消的录音结束（插件有竞态）；编码器卡住时有 2 秒保护。
  未移植应用没用到的暂停/恢复、流式、选择输入设备等接口。录音开始时把麦克风切到蓝牙耳机，与插件一致；
  结束后的路由处理已修正，见“修正了的 Flutter 版问题”第 3 条。
- 应用内更新：只保留 Android 路径（下载 APK 交给系统安装器），与 Dart 一样不支持断点续传，失败或
  取消时删除未完成的文件。
- 试听直接调用 `AndroidMusicPreviewPlayer`（原来经 `gang_chat/music_preview` 通道）。

### 语音、视频、屏幕共享（`:app` live / platform/webrtc）

改用 LiveKit Android SDK；Flutter 版依赖的 `flutter_webrtc` / `livekit_client` 分支在 Android 上的行为
移植到 `platform/webrtc`：

- 进程级音频路由：LiveKit 每个 Room 有自己的音频设备模块，Flutter 版是全进程一个 AudioSwitch。
  现在所有 Room 通过自定义 `AudioHandler` 共享一个引用计数的 `AudioSwitchManager`（通话模式、
  VOICE_COMMUNICATION、输出优先级 蓝牙 > 有线耳机 > 扬声器 > 听筒，设备 id 不变）。
- 本地麦克风增益（分支里的 AudioProcessingAdapter）移植为采集后处理器。
- 屏幕共享：MediaProjection 授权走 `activityResultRegistry`；前台服务渠道 `gang_chat_screen_capture`、
  类型 `mediaProjection|microphone`；按真实屏幕尺寸、30 fps 单层发布。共享声音用 AudioPlaybackCapture
  （排除本应用 UID），在独立音频模块、独立的 `<id>--screen-audio` 参与者里发布。
- 差异：
  1. 共享过程中调整画质后，新的分辨率不会重新上报（SDK 没有对应接口）；码率和帧率缩放仍然生效。
  2. 断线续连（resume）检测和每个参与者的离开原因依赖反射 SDK 内部的 RTCEngine 回调；反射失败时
     所有重连按完整重连处理、所有离开按"离开"处理。
  3. 麦克风测试的电平改为对测试音轨采样计算 RMS（每 120 ms），不再走回环统计。
  4. 切换前后摄像头用 `switchCamera`。
  5. 进入/离开语音频道的提示音和其后的语音播报：Flutter 版在 Android 上从未播放，原生版已修正，
     见“修正了的 Flutter 版问题”第 2 条。
  6. LiveKit 自带的通话模式处理被关闭，音频模式由共享的 AudioSwitch 管理。
  7. 摄像头联播时 SDK 把优先级放在全分辨率层而不是 encodings[0]；屏幕共享是单层，不受影响。
- 只编译、未在设备上运行过的部分：真实 Room 连接与反射钩子、AudioSwitch、MediaProjection 与前台服务、
  播放捕获、屏幕声音发布、麦克风/扬声器测试、视频渲染、TTS。

### 主框架（`:app` home）

- 系统返回键：主框架内容包在 `PopScope` 里，按 Dart 的顺序依次处理更新下载锁定、全屏直播、展开的搜索、
  设置、侧边面板、窄屏内容页回到房间列表；都不适用时交给 Activity（退出）。
- 通知点击打开对应房间；房间列表还没加载完时先记下，加载后再打开。启动和每次回到前台时检查
  `takeInitialNotificationRoomId`。
- 生命周期映射与 Flutter 的 Android 嵌入一致（pause → inactive、stop → hidden + paused、
  start → hidden + inactive、resume → resumed），进入后台时启动前台服务，回到前台时停止并同步推送注册。
- 系统划掉任务时：离开直播（断开 900 ms 超时 + 离开接口）与停止实时连接并行，然后退出会话。
- 前台收到"全部提醒"房间的新消息播放提示音（耳机静音时音量为 0）；在后台改为系统通知；
  图标角标同步，已读房间的通知会被取消。
- 去掉了窗口控制器、桌面关闭/托盘路径；`suspendStageVideo` 恒为 false（Dart 里只用于 Windows）。
- 组件库修正：没有 `onTap` 的 `UiContextMenuTriggerRegion` 不再吞掉快速点击，只消费完成的长按
  （对应 Flutter 手势竞争）；之前点消息头像不会打开资料卡片。

### 设置页（`:app` settings）

- 去掉了"关闭方式"分组（桌面托盘/关闭行为；仍然读写对应的存储）；搜索只收录 Android 上的条目，
  "偏好设置"卡片摘要只显示语言。去掉了 Sparkle、DMG 下载、桌面安装程序启动后退出等桌面路径。
- 系统通知分组只在 `isSupported` 时显示；应用回到前台时重新读取通知设置。
- "关于"里的更新与 Dart 设置页一致：`AppUpdatePage` → `ReleaseUpdateService.downloadUpdate`（进度、取消）
  → 系统安装器。
- 移植中修正：歌单卡片的点击检测在每次重建时重启（可能丢点击）；音量滑块拖动时一直用第一次构建时的回调。

### 通知中心与房间管理（`:app` home）

- 日历的手动输入用 `KeyboardType.Phone`（Compose 没有日期键盘）；Android 上手动输入模式隐藏月历，与 Dart 一致。
- 通知行的同步布局用 `SubcomposeLayout` 和固有尺寸测量实现，测量时两种行变体都会短暂组合一次。
- 移植中修正的组件库问题：点击 `DialogFrame` 面板上的文字会穿透到遮罩、把对话框关掉；
  `BadgeDot` 上的 `ignorePointer()` 让小红点变成 48dp 的触摸区域，挡住了带角标的分段（如"新成员"）
  和按钮的点击。两处都加了回归测试。
- 框架层：`ignorePointer()` 是 Flutter `AbsorbPointer` 的语义（吞掉点击）。Flutter `IgnorePointer`
  的两处用法改为让点击穿透：表情包悬停遮罩不再挂指针修饰符；全屏控制条淡出后改为 `unplaced()`，
  点击才能落到下面的画面上把控制条唤出。

### 语音频道页与音乐盒（`:app` home）

- 音乐盒列表改为惰性列表，屏幕外的行会被释放（分组、歌单等状态保存在列表外，不会丢失）；
  "定位到正在播放"会滚动并居中。
- 文字溢出时的渐隐手动绘制；旋转唱片跟随帧时钟（测试中静止）；鼠标指针样式未移植。
- 开始屏幕共享走系统 MediaProjection 授权，没有桌面的屏幕源选择器。
- 移植中修正：音乐盒面板的零尺寸锚点导致面板高度为 0；成员卡片上的透传遮罩吞掉了点击；
  窄屏下两张卡片因像素取整换行；全屏控制条在鼠标下不会自动隐藏；歌曲卡片只在标题文字上能点开。
- 框架层修正（`widgets/ViewState.kt`）：没有自有字段的 State 被 Compose 编译器推断为 stable，
  父级传入新 widget 时 `build()` 被跳过、界面不更新；`widget` 改为快照状态后修复，并加了回归测试。

### 聊天页（`:app` home）

- 消息列表改用以 `clientMessageId` 为键的反向 `LazyColumn`；消息少时用排列方式顶到上方
  （Dart 用测量出的占位）；保存的滚动位置是"条目索引 + 偏移"而不是像素；尚未布局的行先按估算高度
  定位再校正；可见区下方的行变化时为保持位置可能打断正在进行的惯性滚动。
- 长按打开菜单后，结束长按的那次抬起被消费掉（对应 Dart 里长按在手势竞争中胜出），
  否则长按表情包会同时打开图片预览。
- `TextOverflow.fade` 改为裁剪（Compose 没有渐隐）。
- 图标按钮保留 48dp 的 Material 点击区域，歌单/歌曲卡片因此略高；不画 Material 按下遮罩。
- 去掉了桌面拖放用的 `composerDropKey`；桌面悬停/右键的测试改为点按/长按，Windows/macOS 变体未移植。

### 头像、图片、表情包管理、侧边栏与资料卡片（`:app` ui / home）

- 动图：GIF 在所有版本上播放；动态 WebP 在 API 28 以下只显示首帧（Coil 在 28 以下只解码 GIF 动画）。
  图片切换时不保留 Flutter 的 gapless playback。
- 滚动条用新的 `Modifier.rawScrollbar` 近似实现；侧边栏图标阴影只在 API 31+ 有模糊效果。
- 资料卡片在 Android 上靠点按/长按打开（Flutter 在桌面上靠悬停）；可选中文字的右键菜单与
  `copyStartOffset` 未移植，改用长按选择。依赖鼠标悬停或右键的测试用例未移植。
- 图片预览只走 Android 沉浸式路径；`FutureBuilder` 的结果晚一帧送达。
- `TapRegion` 与 Flutter 一致：所在路由被对话框盖住时不响应外部点击（否则在对话框里点击会关掉下面的卡片）。
- 修正了两个移植时发现的问题：嵌套卡片在资料加载完成后不刷新；鼠标移出锚点时悬停卡片不一定关闭。

### 登录与更新提示（`:app` shell）

- 去掉了桌面窗口相关的部分：登录窗口锁定尺寸、窗口控制按钮与拖拽区、Sparkle 分支。
  `authWidgetSize()`（登录 430×368、注册 430×436）是 `desktop_window_controller.dart` 里唯一与 Android
  有关的部分，保留给平板宽屏布局使用。
- 账号历史在 store 不挂起时于 `initState` 同步载入（Dart 在首帧之后才填充）。
- `AppUpdateGate` 检查更新时先让出一次，避免在构建过程中回调父组件。
- 修正组件库 `SegmentedControl` 的一个缺陷：控件重建会重启点击检测，按下分段本身又会触发重建，
  导致真机上点击（包括登录/注册切换）被丢掉；改用 `rememberUpdatedState`，并加了回归测试。

### 应用入口（`:app` shell `GangApp`、`MainActivity`）

- `GangApp`（`shell/GangApp.kt`）按 `MaterialApp` 的层次组装：`AppConfigScope` → `UiTheme`（主题与根
  `DefaultTextStyle`）→ `WindowMediaQuery` → 全应用关闭触摸触发的 tooltip（新增的 `LocalTooltipTriggerMode`，
  对应 Dart 的 `TooltipTheme(triggerMode: manual)`；单个 `UiTooltip` 自己的 `triggerMode` 仍然优先）→
  `AppNotificationHost` → `RootNavigator`，首页是认证门 `_AuthGate`。
- 启动顺序：`GangChatApplication`（进程级安装）→ `MainActivity.onCreate` → `GangChatStartup.main()`
  （偏好迁移、方向、WebRTC 音频、`AppConfig.load`、自有主机直连）→ `prepareGangApp(config, showInitialWindow)`
  预读刷新令牌得到 `startsAuthenticated` → `GangApp`。`migrateLocalPreferences()` 接上了
  `LocalPreferencesMigration().migrate()`（Android 上为空操作）。
- 桌面窗口控制器未移植（在 Android 上本来就是空操作），只保留“显示窗口”：认证门在 Dart 调用
  `showInitialWindow()` 的位置调用 `GangApp(showInitialWindow =)`，`MainActivity` 的启动画面（取消绘制的
  pre-draw 监听）保持到那时。没有保存的会话时，恢复检查结束即显示登录页；有保存的会话时，恢复成功并渲染
  一帧后再等 120 ms 显示主页，恢复失败显示登录页，4 秒内恢复没有结束则先撤掉启动画面、显示加载页（Dart
  桌面版的保险定时器）。Flutter 的 Android 版在第一帧就撤掉启动画面，加载页会立即出现。
- Android 上没有全应用的 `SelectionArea`（与 Dart 相同），`SelectionContainer.disabled` 因此没有作用，未移植。
- 测试接缝：`GangApp` 多了 `authClientFactory`、`homePage` 两个参数（默认是真实的鉴权客户端与 `HomePage`），
  供 `GangAppTest` 接入假鉴权服务器和主框架的假 API；`MainActivity.holdsLaunchScreen` 让在 Compose 测试时钟下
  运行的测试关掉启动画面（Robolectric 会立即重跑被取消的绘制，活动永远启动不完）。
- 未移植的 Dart 断言：登录入口用例里标题的鼠标指针样式（桌面）；冒烟用例里主题光标颜色与“没有
  `SelectionArea`”的检查。两个 `auth_widget_tests` 用例和四个 `auth_smoke_widget_tests` 用例改为在真实
  `GangApp` 上运行（原先在 `LoginPageWidgetTest` 里直接托管 `LoginPage`）。
- 端到端测试 `GangAppEndToEndTest`（设置 `GANG_E2E_API_BASE_URL` 时才运行，对 `tool/js/mock_server`）：真实的
  `MainActivity` 启动、OkHttp 与 SSE 实时流；登录、房间列表、打开房间、发送消息、他人消息经 SSE 到达、通知、
  设置、退出登录，手机与平板各 8 张截图，与 `docs/js-port-screenshots/` 对照一致。测试里图片用 BitmapFactory
  解码（Robolectric 没有 `ImageDecoder`），安全存储用内存实现（没有 Android Keystore）。服务器在本机、几乎
  没有延迟时，打开房间后已读回执的 SSE 回声会早于下一帧到达，“未读消息”分隔线于是不显示（Dart 的执行顺序
  相同）；服务器加 `--latency 40` 时分隔线正常出现。

### 界面组件库（`:app` ui）

- 中文行高：Android 的后备字体行距让中文行比 Flutter 的 `height` 高几个像素（14sp/1.4 在 xxhdpi 下
  63px 对 59px），Compose 没有公开开关。`Input` 按拉丁和中文两种行高中较高的那个留白以保持 40dp；
  其他中文文本可能比 Flutter 高几个像素。
- 按钮：Compose 默认收缩，Flutter 在有界宽度里撑满；`Button(width = Dp.Infinity)` 撑满，
  `DialogFrame` 的按钮行通过 `LocalButtonFillsBoundedWidth` 得到 Flutter 的行为。
- 去掉了只在桌面上有意义的部分：输入框/只读文本的右键选区保护、桌面光学偏移、Windows 间距、
  `SettingsList` 的 physics 参数；`GangScrollBehavior` 在 Android 上是空操作（Dart 在 Android 上也是）。
- Tooltip 用 Compose `Popup`，按 Flutter 的 `positionDependentBox` 定位。
- 输入框的文本菜单用 Compose 1.12 的 `TextContextMenuProvider` 画组件库自己的面板，位置对齐
  Flutter 的 Android 工具栏；"全选"后菜单保持打开。
- 给可点击表面和按钮补了点击/角色/选中的无障碍语义。
- 部分边框动画做了简化；计时器改用跟随 Compose 时钟的计时器，便于测试控制。
- 未移植的桌面专用 widget 测试：输入框右键 7 个、桌面优化偏移、Windows 间距、对话框按钮行的
  Windows 用例、右键菜单的 Windows 用例、滚动条边距 4 个。

### 业务逻辑（`:core` app / shell）

- `settings_about` 的版本号和发布时间原来是编译期常量（`String.fromEnvironment`），现在是属性：
  版本号读 `AppConfig.environment["GANG_CHAT_VERSION"]`（debug 构建仍显示 1.0.0），
  发布时间读 `AppConfig.environment["GANG_CHAT_RELEASE_TIMESTAMP"]`，空值视为未设置。
- `userFacingErrorMessage` 先把 JVM 标准异常的 `toString()`（`java.lang.IllegalStateException: …`）
  换成 Dart 的写法（`Bad state: …` 等），避免界面上出现 Java 类名；后续判断与 Dart 相同。
- 表情包 zip 解包改用 `java.util.zip.Inflater` 加一个按中央目录读取的小解析器，行为对齐 `archive`
  4.x（数据描述符、重名条目、目录/链接判断、非 zip 报错文案）；只支持 stored / deflate，
  加密条目和其他压缩方式（`archive` 还支持 bzip2）跳过。
- `LocalLoginAccountHistoryStore` 去掉了只对 macOS 钥匙串有意义的选项。
- `roomCardWithLive` 与 Dart 一样不复制 `gangBotAvailable` / `gangBotEnabled`（疑似 Dart 端的遗漏，
  目前没有代码从 `RoomCard` 读这两个字段），保持原样并加了 `PORT` 注释。
- `room_display` 里的 `\s+` 用显式字符类实现 JavaScript `\s` 的范围（含全角空格 U+3000、U+00A0），
  Java 默认的 `\s` 不包含它们。
- `LocalPreferencesMigration.migrate()` 在 Android 上是空操作：Dart 版只在 Windows 上迁移旧的
  ProductName 偏好；`migrateLegacyPreferences` 已完整移植并测试。
- `storedAudioVolumeFromStorageValue` 用按 Dart `double.tryParse` 规则实现的解析（不接受 Java 的
  `d`/`f` 后缀和十六进制浮点）。
- `AuthSessionController` 共享的刷新 Future 改为 `Deferred`；定时刷新的错误静默丢弃，与 Dart 的
  `catchError((_) => '')` 一致。
- 为了让测试能像 Dart 一样继承替身，`AuthClient`、`TokenStore` 改为 `open`。
- **刻意修正**：`MessagesController.sendVoiceMessage` 遇到空文件时，Dart 源码里的报错是乱码
  `鏂囦欢涓虹┖`（"文件为空"的 UTF-8 字节被按 GBK 读出），用户会直接看到；原生版改为 `文件为空`。
- `patchRoomDetailApplied`、`patchLiveSnapshot`、`LiveController._removeUserFromRoomLivePreview`
  与 `roomCardWithLive` 一样不复制 gang-bot 字段（同一处疑似 Dart 遗漏，均有 `PORT` 注释）。
- `MediaCacheController` 去掉了只在 Web 上用的内存缓存（64 MB 淘汰）；原生端一直走磁盘缓存，
  Dart 也没有磁盘淘汰，行为不变。
- 下载按块读取响应流（暂停时停止读取，对应 Dart 暂停的 `await for`）；文件读写、重命名、删除在
  `Dispatchers.IO`；协程取消按取消传播，不转成 `DownloadCancelledException`。
- `RoomReadSyncController`：不挂起就完成的同步会立即从进行中表里移除（Dart 晚一个 microtask）。
- `RoomNotificationDateRange` 起止颠倒时抛 `IllegalArgumentException`，消息文字与 Dart 的
  `ArgumentError.value` 略有不同。
