# Gang Chat

![Gang Chat](assets/branding/gang_chat.svg)

Gang Chat 是一个房间聊天客户端，面向 Windows、macOS、Android 和 Web（浏览器）。它把文字聊天、语音频道、摄像头、屏幕共享、房间管理、通知、表情包、文件传输和房间音乐盒放在同一个自适应界面中，并通过独立的 Gang Chat 服务端完成账号、消息和业务状态同步，通过 LiveKit/WebRTC 承载实时音视频。

客户端由两套代码组成，都从此前的 Flutter 客户端一比一移植而来，界面、文案和行为以 Flutter 版为准，有意的差异记录在各自的移植状态文档中：

- **Windows / macOS**：Electron + React 桌面客户端，代码在 `apps/desktop`、`packages/core`、`packages/ui`，说明见 [`apps/desktop/README.md`](apps/desktop/README.md)。
- **Web**：同一套 React 界面和逻辑层跑在浏览器里，页面壳在 `apps/web`，部署在 [https://ky-z.com/gang-chat/app/](https://ky-z.com/gang-chat/app/)，说明见 [`docs/web-spike.md`](docs/web-spike.md)。
- **Android**：原生 Kotlin + Jetpack Compose 客户端，代码在 `android/`（Gradle 工程）。

桌面端和 Web 版共用 `packages/core`、`packages/ui`；Android 与它们不共用源码，只共用仓库根目录的 `assets/`（服务器配置、品牌图、字体）和同一个服务端。Flutter 工程已从仓库移除。需要对照原 Dart 实现时，以提交 `fcf2fdd` 为准，例如 `git show fcf2fdd:lib/src/app/rooms_controller.dart`。

> 本 README 以当前 `master` 分支的实际实现为准。

## 目录

- [相关入口](#相关入口)
- [平台支持](#平台支持)
- [功能总览](#功能总览)
- [详细功能](#详细功能)
- [系统架构](#系统架构)
- [项目结构](#项目结构)
- [开发环境](#开发环境)
- [配置客户端](#配置客户端)
- [运行与调试](#运行与调试)
- [构建](#构建)
- [测试与质量检查](#测试与质量检查)
- [发布流程](#发布流程)
- [数据、权限与本地存储](#数据权限与本地存储)
- [常见问题](#常见问题)
- [开发约束](#开发约束)
- [进一步阅读](#进一步阅读)

## 相关入口

- 官网与客户端下载：[https://ky-z.com/gang-chat/home/](https://ky-z.com/gang-chat/home/)
- 客户端仓库：[gang-chat/gang-chat-client](https://github.com/gang-chat/gang-chat-client)
- 服务端仓库：[gang-chat/gang-chat-server](https://github.com/gang-chat/gang-chat-server)
- 主页仓库：[gang-chat/gang-chat-homepage](https://github.com/gang-chat/gang-chat-homepage)
- GitHub Releases：[客户端 Releases](https://github.com/gang-chat/gang-chat-client/releases)

官网会从对象存储的 `releases/` 目录读取可用版本，并分别提供 Windows 安装程序、macOS 磁盘映像和 Android APK。当前约定的正式版文件名为：

```text
GangChat_v<version>.exe
GangChat_v<version>.dmg
GangChat_v<version>.apk
GangChat-<version>-windows.zip
```

## 平台支持

| 能力 | Windows | macOS | Android |
| --- | --- | --- | --- |
| 客户端实现 | Electron + React | Electron + React | Kotlin + Jetpack Compose |
| 正式发布产物 | `.exe`、便携 `.zip` | `.dmg` | `.apk` |
| 自适应宽屏/窄屏布局 | 支持 | 支持 | 支持 |
| 原生窗口控制区 | 自定义最小化/最大化/关闭 | 系统原生交通灯 | 不显示桌面窗口按钮 |
| 后台形态 | 系统托盘 | 桌面窗口生命周期 | 前台服务、系统通知、可选 FCM |
| 文字、文件、图片、语音、表情 | 支持 | 支持 | 支持 |
| 语音频道和摄像头 | 支持 | 支持 | 支持 |
| 屏幕共享 | 屏幕/窗口选择 | 屏幕/窗口选择 | MediaProjection 系统授权 |
| 屏幕共享声音 | 独立屏幕音轨 | 独立屏幕音轨（macOS 14.2 及以上） | Android 播放捕获，尽力而为 |
| 图片/摄像头/屏幕共享全屏 | 支持 | 支持 | 支持横竖屏切换 |
| 应用内更新 | electron-updater 下载安装程序并安装 | Sparkle 下载、校验、安装并重启 | 后台下载并打开系统安装器 |
| 进程退出后的离线推送 | 不适用 | 不适用 | 需要发布包配置 FCM 和设备具备 Google Play 服务 |

Android 手机在普通界面默认使用竖屏，只有全屏图片、全屏摄像头或全屏屏幕共享等媒体场景允许横屏。Android 平板使用大屏判定并允许系统正常旋转，同时继续隐藏桌面端窗口控制按钮。

Linux 和 iOS 不是目标平台：仓库只构建和发布 Windows、macOS、Android 安装包，并部署 Web 版。Web 版与桌面端共用界面，但没有窗口控制、托盘和应用内更新（网页总是最新部署的版本），屏幕共享、通知等能力以浏览器支持为准，逐项状态见 [Web 版说明](docs/web-spike.md)。

## 功能总览

- 邮箱验证注册、登录、自动刷新会话、记住账号、找回密码和多设备会话管理。
- 公共/私密房间、开放/审批/关闭加入方式、邀请、申请、成员、角色和黑名单管理。
- 文字、链接、图片、文件、语音消息、表情、歌曲和歌单组件消息。
- 多条引用、`@` 提及、撤回、删除、未读数、跨设备已读同步和房间草稿。
- 房间通知中心、日期/类型/关键词筛选、Android 系统通知和桌面提醒。
- LiveKit 语音频道、麦克风/耳机状态、成员音量、管理操作、摄像头和屏幕共享。
- Windows、macOS、Android 屏幕共享及独立屏幕音频发布。
- 个人表情包、房间表情包、上传、保存、排序、筛选、下载和批量管理。
- 点歌队列、网易云/哔哩哔哩搜索、个人歌单、房间歌单、试听、分享、克隆、合并和批量添加。
- 自适应窄屏布局、嵌套用户名片/房间名片/歌曲名片/歌单名片和跨平台右键/长按交互。
- Windows、macOS、Android 自动检查更新、下载和安装衔接。

## 详细功能

### 1. 账号、登录与安全

#### 注册与登录

- 使用邮箱、登录用户名和密码注册账号。
- 注册前完成邮箱验证码校验，并检查邮箱和登录用户名是否可用。
- 支持简体中文、繁体中文和 English 登录文案与语言偏好。
- 登录页可以记住历史账号、头像和密码；敏感会话凭据通过安全存储保存。
- Access Token 失效时可使用 Refresh Token 刷新；认证请求对部分短暂 TLS 握手失败带有恢复逻辑。
- 支持退出当前账号，并在退出时清理实时连接、语音状态和 Android 推送注册关联。

#### 密码与邮箱

- 登录状态下修改密码。
- 忘记密码时发起邮箱验证码流程、检查冷却时间、重发验证码并设置新密码。
- 支持验证或更换邮箱绑定。
- 支持手机号绑定信息以及邮箱/手机号公开状态设置；具体可用性取决于服务端配置。

#### 账号资料

- 显示个人 UID 和登录用户名。
- 修改默认显示名、个人签名和性别。
- 选择内置头像，或上传图片、裁剪并设置自定义头像。
- 用户名、头像、身份标签在消息、通知、成员列表、搜索和名片中使用统一解析规则。
- 已注销用户、已删除房间或历史对象缺失时保留可理解的占位信息，避免历史消息失去上下文。

#### 登录会话与账号活动

- 查看近期登录会话。
- 标记当前会话，并显示设备系统、IP/位置和活动时间等服务端可用信息。
- 撤销其他会话。
- 查看账号创建时间和账号状态。
- 普通账号可在确认后注销；超级用户账号受额外保护。

#### 超级用户管理

超级用户可以通过顶部搜索进入用户设置，执行受权限保护的管理操作，包括：

- 查看和修改目标账号资料及公开信息。
- 查看目标账号的会话和服务端保存的语音音量设置。
- 重置目标账号密码。
- 封禁或解除封禁账号；封禁会撤销已有登录会话。
- 强制注销账号。
- 在房间权限允许时管理创建者、管理员和成员角色。

### 2. 主界面、自适应布局与搜索

- 宽屏下使用房间列表与当前房间并列的桌面布局。
- 窄屏和 Android 手机将房间列表与房间内容拆成独立页面，并接管系统返回键。
- Windows/macOS 窗口收窄后复用移动端友好的响应式排列，而不是简单裁切桌面内容。
- Android 平板按逻辑尺寸进入大屏布局，但不显示桌面端最小化、最大化和关闭按钮。
- 顶部搜索会根据可用宽度自动调整位置；搜索结果支持分页和分类。
- 普通用户可以搜索我的房间、公开房间、聊天记录和聊天文件。
- 超级用户可以在搜索中进入用户管理结果。
- 房间列表显示置顶状态、未读状态、最新消息、实时语音人数和房间时间。
- 网络信号角标可显示客户端到服务端的延迟；桌面支持悬停，触屏支持点击固定现有提示卡。
- 顶部语音快捷模块常驻显示当前语音状态；空间不足时会优先压缩房间名，同时保留麦克风、耳机等关键操作。

### 3. 房间与成员管理

#### 创建和编辑房间

- 设置房间名称、简介和房间图标。
- 选择内置房间图标，或上传并裁剪自定义图标。
- 设置可见性：公开或私密。
- 设置加入方式：开放、需审批或关闭。
- 显示房间 RID、创建时间、成员数、在线数和语音频道人数。
- 房间资料、用户名片和公共搜索结果可以互相嵌套打开，不丢失当前上下文。

#### 个人房间偏好

每个成员可以为每个房间单独设置：

- 房间备注名。
- 房间内专用用户名。
- 房间消息策略：全部、接收但不提醒、屏蔽。
- 是否置顶房间。
- 是否启用入场/离场 AI 语音播报。

房间备注名和房间内用户名只改变该用户或该房间中的展示，不会错误替换账号默认头像或账号级资料。

#### 加入、邀请和申请

- 直接加入开放房间。
- 向需审批房间提交申请理由。
- 撤回尚未处理的申请。
- 房间管理者查看并审批新成员申请。
- 邀请用户加入房间，被邀请者可接受或拒绝。
- 房间加入策略改变时，客户端通过实时事件更新入口和通知状态。

#### 成员与权限

- 成员页分为房间成员、新成员和黑名单。
- 按关键词、在线状态、语音状态和身份筛选成员。
- 显示成员、管理员、创建者和超级用户标签。
- 修改成员的房间内用户名和角色。
- 将成员移出房间、拉黑或从黑名单恢复。
- 转让房间创建者。
- 房间管理员及以上权限可以管理房间表情包、房间歌单、消息记录和部分语音操作。
- 所有危险操作使用确认窗口，窄屏时成员操作收进统一设置菜单，避免按钮溢出。

### 4. 文字聊天与消息组件

#### 可发送内容

- 普通文本、Unicode/emoji 和可点击链接。
- 一张或多张图片/文件附件。
- 语音消息。
- 个人表情和房间表情。
- 歌曲组件和歌单组件。
- 多条消息引用和 `@用户` 提及。

文件和组件可以先暂存在输入区；每个暂存项都有独立删除入口。歌曲/歌单组件与普通文字同时发送时，会先发送组件，再紧接着发送普通消息，保持组件的结构化数据。

#### 输入与草稿

- 每个房间独立保存未发送文字、引用和待发送附件。
- 切换房间后恢复对应草稿。
- 支持从系统剪贴板粘贴文本、图片、文件路径以及 Gang Chat 的歌曲/歌单组件数据。
- Windows/macOS 支持文件拖入；Android 使用系统文件选择器和触屏文本选择。
- 桌面端右键和 Android 长按使用同一套上下文菜单能力；Android 文本选择仍保留系统选择手柄和放大镜交互。

#### 消息展示与操作

- 连续消息按发送者、时间和系统事件规则分组。
- 服务器时间用于日期分隔和消息时间，降低本地时钟偏差造成的错序。
- 支持复制、引用、撤回、删除和跳转到引用消息。
- 支持多条引用，并在系统消息中隐藏不必要的发送者信息。
- 管理权限允许时可处理撤回申请或强制删除消息。
- 已撤回、被删除或关联用户已注销的消息显示稳定的历史占位状态。
- 当前房间自动同步已读状态；同一账号的其他设备可收到已读更新。
- `@我` 消息有独立高亮和未读导航。
- 长消息列表支持回到最新消息，并记住房间滚动位置。

#### 房间消息记录

房间设置中的消息记录支持：

- 关键词搜索。
- 开始/结束日期筛选。
- 按成员筛选。
- 按全部、链接、语音、表情、图片、文件、音乐和系统消息分类。
- 复制历史记录。
- 在权限允许时隐藏/删除符合条件的记录。

### 5. 图片、文件与语音消息

#### 图片

- 聊天图片可以打开全屏预览。
- 支持缩放、拖动、复制、下载/保存和添加到表情包。
- 控制按钮会在无操作后自动隐藏，鼠标移动或触屏操作后重新出现。
- Android 全屏预览允许横竖屏切换；退出后恢复手机普通界面的竖屏策略。
- 图片背景保留进入预览前的界面快照，避免横屏时把整个聊天 UI 重新排成横屏。

#### 文件

- 上传普通文件并显示文件名、类型和大小。
- 显示下载进度、成功状态和失败重试。
- Windows 可选择保存位置；其他平台通过平台文件能力保存或打开。
- 全局搜索可单独查找聊天文件。

#### 语音消息

- 在输入区录制、取消和发送语音消息。
- 发送期间先显示待发送气泡，避免长上传阻塞界面。
- 消息气泡显示时长、波形和播放进度。
- 语音播放使用独立的播放服务，不与语音频道麦克风开关混为一体。

### 6. 通知、未读与后台行为

#### 应用内通知中心

- 汇总房间邀请、加入申请、申请结果、角色变化、成员事件和其他房间事件。
- 支持全部、邀请、申请、房间分类。
- 支持关键词、日期范围和组合筛选。
- 支持复制、删除、批量选择和标记已读。
- 通知内容中的每个用户、房间、头像、图标和身份标签保持原位置，并可按权限打开对应名片。
- 窄屏下通知会自动换行，时间最多分两行，操作按钮保持垂直居中。

#### 桌面提醒

- 新消息可以播放应用内提示音。
- Windows 在窗口失焦时可以请求任务栏注意状态，并配合托盘图标提醒。
- 窗口重新聚焦后清除注意状态。
- 关闭窗口的行为可设置为每次询问、最小化到托盘或退出程序。

#### Android 通知

- 应用退到后台时以前台服务保持实时连接，后台状态仍视作在线。
- 从最近任务中划掉应用视作明确退出，客户端会尽力发送离开语音和清理实时状态。
- 当全局系统通知已开启，且房间消息策略为“全部”时，后台房间消息可显示消息内容。
- 系统通知支持按房间聚合、点击进入对应房间和应用图标数字角标。
- 更新 APK 可以在后台继续下载，完成后显示安装通知；没有通知权限时，重新打开 Gang Chat 会继续引导安装。
- 发布包配置 FCM 后，即使应用进程已结束，只要用户没有退出账号，服务端仍可向该设备推送离线通知。
- FCM 依赖 Google Play 服务。没有 Google 服务的设备仍可使用应用和后台实时连接，但进程结束后的推送需要额外接入对应厂商推送渠道。

### 7. 用户名片、房间名片与嵌套交互

- 用户头像或名字可以打开用户名片，显示资料、状态、角色、共同房间和 UID。
- 房间图标或名字可以打开房间名片，显示成员数、加入状态、可见性、加入策略、创建者和 RID。
- 名片中的用户、房间、创建者、共同房间可以继续打开下一层名片。
- 桌面使用悬停和点击固定，Android 使用点击/长按等触屏入口。
- 名片文字支持选择和复制；拖动 Android 选择手柄时不会误关闭名片。
- 窄卡片会根据真实可用宽度缩小头像或换行，优先保持“头像/图标 + 名称”为一个可理解的整体。

### 8. 语音频道

#### 基础状态

- 加入和离开房间语音频道。
- 加入前即可设置麦克风和耳机状态。
- 麦克风静音与耳机关闭是两个独立状态；关闭耳机会同步禁止本地收听，但不会错误地把远端所见麦克风状态保持为开启。
- 离开语音频道时记住本地麦克风/耳机偏好，下次加入时恢复；退出账号后恢复默认开启状态。
- 顶部快捷模块即使未加入语音频道也常驻，便于预设状态和快速返回当前语音房间。

#### 音频设备与音量

- 在设置中选择输入设备和输出设备。
- 可跟随系统默认设备，并在设备断开、Bluetooth 配置切换或自动重连后恢复可用设备。
- 调整输入音量、输出音量和屏幕共享声音音量。
- 音量设置保存在非敏感本地偏好中，不随普通软件升级重置。
- 支持麦克风输入测试、输出测试和实时输入电平。
- 对其他成员设置独立收听音量；滑块图标和静音状态随实际音量更新。
- Android 音乐盒使用媒体音量通道；语音频道继续使用实时通信所需的音频路由。

#### 成员和管理操作

- 显示实时在线成员、说话状态、麦克风、耳机、摄像头和屏幕共享状态。
- LiveKit 参与者列表和服务端房间计数会在重连后重新拉取并协调，避免外部人数与内部列表长期不一致。
- 播放加入/离开提示音，可选 AI 语音播报成员进出。
- 管理员可以静音成员麦克风、限制语音权限、恢复语音权限或踢出语音频道。
- 服务端权限和 LiveKit 轨道状态共同决定最终结果，客户端不只依赖本地乐观状态。

#### 自动重连

- SSE 实时流断线后使用指数退避和抖动重连。
- 重连成功会重新拉取房间、消息和实时状态快照，修复离线期间错过的事件。
- LiveKit 重连后恢复麦克风发布、耳机状态、摄像头/屏幕轨道订阅和成员列表。
- 被管理员踢出或撤销语音权限时不会把该断开误当成普通网络断线无限重连。

### 9. 摄像头与屏幕共享

#### 摄像头

- 发布或停止本地摄像头。
- 点击本人或其他成员的摄像头缩略图进行预览/全屏观看。
- 本人预览支持切换前后摄像头和镜像。
- 观看他人摄像头时可以仅对本地观看画面镜像，不影响发布者和其他观看者。
- 设备列表尚未授权时先请求权限；确定没有可切换摄像头时隐藏翻转操作。
- 开启屏幕共享时再开启摄像头会按与桌面端一致的轨道互斥/切换流程处理，避免同时启动导致崩溃。

#### 屏幕共享

- Windows/macOS 提供桌面屏幕和窗口来源选择器及缩略图。
- Android 使用系统 MediaProjection 授权共享整个设备画面。
- 共享前可设置 480p、720p 或 1080p，以及 15、30 或 60 FPS；实际输出仍受来源分辨率、设备能力和网络影响。
- 屏幕共享画质可以在共享过程中更新，桌面端尽量不重复弹出来源选择器。
- 点击远端屏幕共享缩略图会在需要时先加入语音频道，再开始观看；已在频道内则直接观看。
- 离开语音频道会清除观看目标，再次加入不会自动恢复上一次观看。
- 显示“谁正在看”列表，并把隐藏的屏幕音频辅助参与者排除在普通成员列表之外。
- 全屏观看时标题、观看者、关闭、全屏和音量控件会在无鼠标/触屏活动约 3 秒后隐藏；活动后立即恢复并重新计时。
- Windows 全屏观看时鼠标光标与控制栏一起显示或隐藏。
- Android 全屏会避开状态栏和系统导航区域，横屏时使用完整安全可视区。

#### 屏幕共享声音

- 屏幕声音作为独立的 `screenShareAudio` 轨道发布，不与麦克风轨道共用状态。
- Windows 使用系统回环采集，并请求排除 Gang Chat 自身声音，降低回声风险。
- macOS 通过 Chromium 的 ScreenCaptureKit 回环采集共享声音，需要 macOS 14.2 及以上；更早的系统共享时没有声音。
- Android 使用 MediaProjection 的播放捕获能力；系统版本、内容保护策略和应用是否允许被捕获会影响结果。
- 屏幕视频优先：声音辅助连接失败时不会阻止画面继续共享。
- 本地用户不会订阅自己的屏幕共享声音，远端屏幕声音使用独立音量控制。

### 10. 音乐盒

音乐盒是房间语音频道中的服务端权威协同播放功能。播放状态、队列、当前歌曲、进度和活动来源由服务端同步，客户端负责操作、展示和缓存。

#### 面板布局

面板固定宽 340（窄于此时贴合正文宽度），自上而下为：

- 标题行：`音乐盒` 与右侧的当前来源标签（点歌队列 / 房间歌单名 / 个人歌单名）。有播放时标签带波形图标；点击标签把列表滚动到拥有该来源的区块：点歌队列滚到队列标题，已保存歌单展开所属分组并原地展开该歌单。右侧为关闭按钮。
- 正在播放：唱片、往返滚动的歌曲名、歌手；来源为点歌队列且有点歌人时显示点歌人标签。下方是进度条和播放控制行（上一首、播放/暂停、下一首、播放模式）。没有控制权限的成员看到的是一行“播放由房间管理员控制”，不渲染任何控制按钮。播放按钮仅在当前来源队列非空时可用。
- 音量。
- 正文：一个整体滚动的扁平列表，按顺序包含 `点歌队列`、`房间歌单`、`我的歌单` 三个区块。点歌队列始终展开；两个歌单分组默认折叠，点击分组标题展开或收起。哪些分组、哪个歌单处于展开状态只由用户决定，不会被服务端推送改写。

#### 点歌队列

点歌队列是每个房间的临时播放来源，位于列表顶部并始终展开：

- 标题行：`点歌队列 · N 首 · 正在播放/已暂停/未播放`，有播放时带波形图标。右侧依次为搜索开关和清空按钮（有权限且队列非空时出现，需确认）。没有“整队列播放”按钮：播放的单位始终是一首歌。
- 搜索开关：点击在标题下方展开一行搜索面板，包含输入框“搜索歌曲点歌”和来源分段选择（网易云 / 哔哩哔哩，QQ 音乐入口暂时隐藏）；展开时输入框自动聚焦，再次点击收起并清空搜索框。
- 输入非空时，点歌队列的歌曲行被搜索结果替换，下方两个歌单分组同时隐藏；清空或收起搜索即回到点歌队列。
- 搜索结果显示完整歌曲标题和作者/歌手；哔哩哔哩来源使用“作者”字段并显示 BV 号等详情。
- 搜索结果行只有一个 `+` 按钮加入点歌队列；相同具体链接已在队列中时按钮变为对勾并禁用，提示“已在点歌队列”。行内没有菜单；添加到歌单在歌曲名片里完成。
- 搜索失败在结果区内显示原因，不弹浮层。
- 队列为空时显示“点歌队列为空”，并提示点击右上角的搜索按钮点歌。

#### 播放控制与队列行

- 支持播放、暂停、上一首、下一首和播放模式切换，以及顺序、单曲循环、列表循环、随机等服务端模式。
- 队列行前缀为序号；下载/准备中显示加载状态，正在播放显示波形，失败显示错误图标；行内显示点歌人头像。
- 播放的单位是歌曲：点歌队列为当前来源时，就绪且非当前的歌曲显示行内“优先播放”，立即播放所选点歌并把它放到刚才播放歌曲之后；正在播放已保存歌单时，每首点歌显示“从这首歌开始播放点歌队列”，点击后把房间切回点歌队列并从这首歌起播（需要切换权限）。
- 有删除权限时行尾显示行内危险色 `×`“从点歌队列删除”。队列行不提供菜单按钮或右键菜单；添加到歌单在歌曲名片里完成。
- 打开面板时自动把正在播放的歌曲滚动到可见位置。
- 长歌曲名使用横向往返展示，确保完整内容可以被看到。

#### 房间歌单与我的歌单

两个分组只负责播放已保存歌单，歌单的编辑在设置页和房间设置页完成：

- 分组标题显示 `房间歌单` / `我的歌单`、展开箭头和加载后的歌单数量；当前来源属于该分组且有播放时标题带波形图标。分组在第一次展开时加载歌单列表，之后歌单被编辑时自动重新加载；加载中显示转圈，失败提供重试。
- 正在播放但不在列表中的来源（例如他人的个人歌单）会置顶显示。
- 歌单行本身没有播放按钮，也不能整单播放；播放总是从歌单里某一首歌开始，点击那首歌会把房间的当前来源切换到该歌单并从它起播。歌单图标悬停显示歌单名片，可查看（原地展开）或编辑歌单。
- 点击歌单行在原地展开其歌曲，同一时间只展开一个歌单；再次点击收起。
- 歌单不是当前来源时，展开的是歌单模板，歌曲行只有一个“从这首歌开始播放歌单”按钮，没有加入点歌队列或添加到歌单的行内动作；歌曲名片只展示信息，保留名片自身的“添加到歌单”，不提供“点歌队列”。
- 该歌单正在播放时，展开的是服务端激活快照（与点歌队列行相同的序号和优先播放，不提供删除），而非可编辑的歌单模板。
- 个人歌单或房间歌单为空时，在有权限的情况下显示“新建第一个歌单”并跳转到对应设置页；面板内不会创建新路由。
- 面板内不再提供本地试听；试听保留在设置页。

#### 歌曲名片

歌曲名片可以显示：

- 歌曲名称和音乐图标。
- 时长。
- 歌手；哔哩哔哩来源显示作者。
- 来源。
- 点歌人，或“房间歌单 / 用户头像 + 用户名的歌单 · 歌单名”。
- 哔哩哔哩 BV 号等来源详情。
- 正在播放、优先播放、已在队列中、点歌队列和添加到歌单等上下文操作；展开的歌单里预设歌曲的名片只展示信息并保留“添加到歌单”，不提供“点歌队列”。设置页和只读歌单窗口中的歌曲名片另提供试听/取消试听。

点歌人头像嵌套现有用户名片；歌单图标和歌单名称嵌套歌单名片。试听先下载到本地预览缓存，关闭名片或点击取消试听后停止播放；语音频道耳机开启时会按当前冲突策略限制或隔离试听。

#### 个人歌单

- 每个账号最多保存 50 个个人歌单。
- 每个歌单最多 500 首歌曲。
- 新建、重命名、删除、置顶、移动和筛选歌单。
- 搜索歌曲并添加到歌单。
- 对歌单内歌曲试听、分享、删除、重排、筛选和批量管理。
- 批量“添加到歌单”按所选顺序复制歌曲，不删除原歌曲。
- 以 `来源 + 外部歌曲 ID` 对具体链接去重，不只比较显示名称。
- “合并歌单”按选择顺序合并、去重并创建新歌单；已完整处理的来源歌单会删除，超过 500 首时保留未处理部分并明确提示。
- 即使已有 50 个歌单，只要合并会释放来源歌单名额，服务端仍可在事务中完成合并。
- 将歌单分享到房间文字频道，发送不可变歌单快照。

#### 房间歌单

- 每个房间最多保存 50 个房间歌单，每个歌单最多 500 首。
- 仅房间管理员及以上权限可以新建和管理。
- 新建房间歌单时可以导入并克隆一个个人歌单。
- 支持重命名、排序、置顶、合并和批量添加歌曲。
- 单个房间歌单可以克隆到我的歌单，名称使用“房间备注名/房间名 · 歌单名”。
- 克隆、合并、批量添加和上限裁剪都由服务端事务处理，避免留下空歌单或部分写入的不一致状态。

#### 歌单名片与分享快照

歌单名片显示：

- 歌单名称和歌曲数量。
- 创建人或所属房间，并支持嵌套用户名片/房间名片。
- 创建日期；临时点歌队列不显示创建日期。
- 正在播放状态。
- 清空队列、查看歌单或编辑歌单等权限相关操作。

分享至聊天的歌单和歌曲以结构化组件展示：

- 右键复制后，在 Gang Chat 输入框粘贴可恢复组件，而不是只留下普通文字。
- 输入区组件带有独立的 `×`，可在发送前移除。
- 点击歌单组件打开歌单名片，点击箭头打开只读歌曲窗口或有权限时进入编辑页。
- 只读歌单窗口中的歌曲仍支持歌曲名片、试听和添加到歌单。
- 他人歌单快照可以克隆到我的歌单；源歌单之后重命名或删除不影响已发送的历史快照。

#### 一致性与刷新

- 个人/房间歌单在设置页编辑成功后发出失效修订号。
- 已打开的语音频道音乐盒会重新加载最新歌单摘要和歌曲，丢弃迟到的旧异步结果。
- 同 ID 的已保存歌单发生编辑时，只更新浏览模板；已经激活的播放快照保持不变，不会在播放途中被设置页改写。
- 服务端进度心跳只更新进度相关字段，不覆盖客户端已经加载的新功能状态。

更完整的状态机、错误契约、容量和测试规则见 [新音乐盒完整实现方案](docs/music-box-next-generation-design.md) 与 [音乐盒客户端 API 契约](docs/music-box-api.md)。

### 11. 表情包

- 个人表情包和房间表情包分开展示。
- 空个人表情包显示“上传第一个表情”；空房间表情包仅在当前用户有管理权限时提供上传入口。
- 上传图片或 ZIP，并在需要时自动创建默认表情包。
- 批量上传、批量选择、删除、重命名、筛选、排序和置顶。
- 下载选中表情或表情包。
- 将聊天图片保存到个人表情包；管理员可以保存到房间表情包。
- 发送后的表情可预览、复制、下载或再次保存。
- 上传、保存和删除操作具有重复请求保护，网络重试不会轻易生成重复表情。

### 12. 设置

设置页包含以下部分：

#### 用户资料

- UID、登录用户名、显示名、性别、头像和签名。
- 自定义头像上传、裁剪和内置头像选择。

#### 偏好设置

- Windows/macOS 关闭窗口行为。
- Android 系统通知总开关和系统通知设置入口。
- 简体中文、繁体中文和 English 语言选择。

#### 隐私和安全

- 邮箱/手机号绑定与公开状态。
- 修改或找回密码。
- 登录会话、设备系统、IP/位置和撤销会话。
- 账号状态和注销账号。

#### 语音和视频

- 输入/输出设备。
- 输入、输出和屏幕共享音量。
- 麦克风和输出测试。
- 屏幕共享分辨率及帧率。
- 设备偏好恢复和本地持久化。

#### 我的表情包

- 上传、管理、排序、筛选、批量操作和下载。

#### 我的歌单

- 完整的个人歌单和歌曲管理能力。

#### 关于 Gang Chat

- 当前版本、发行时间和上次更新时间。
- 自动提示更新开关。
- 手动检查新版本、忽略指定版本和下载安装。
- 意见反馈入口。

### 13. 自动更新

- 客户端从配置的 S3 兼容对象存储的 `releases/` 目录发现新版本；Android（以及 Windows 的回退路径）列出该目录中的安装包，按语义版本选择当前平台的最新版本。
- Windows 使用 electron-updater 读取 `releases/latest.yml`，按 blockmap 差分下载安装程序，退出当前应用后安装；对象存储中没有 `latest.yml` 时，退回列出 `releases/` 中的安装包并整包下载。
- macOS 使用 Sparkle 读取 `releases/appcast.xml`，由 Sparkle 自己的窗口完成下载、EdDSA 签名校验、安装和重启；开发版没有 Sparkle，退回在浏览器中下载 DMG。
- Android 校验 APK 长度、ZIP/APK 结构和文件扩展名，再通过 `FileProvider` 打开系统安装界面。
- Android 允许后台下载；下载进度显示在前台服务通知中。
- Android 安装完成前应保持账号和任务状态，不需要一直停留在前台，但不应主动退出 Gang Chat。
- Android 系统必须允许“安装未知应用”；应用无法绕过系统确认步骤。
- 本地的音频设备、输入/输出音量、屏幕共享音量、关闭行为和语言等偏好通过稳定键名和迁移逻辑保存，不应因覆盖安装普通新版本而重置。

## 系统架构

```mermaid
flowchart LR
    subgraph DESKTOP["Windows / macOS / Web：React"]
        DUI["React 界面<br/>packages/ui"]
        DCORE["逻辑层<br/>packages/core"]
        DHOST["Electron 主进程 / preload<br/>apps/desktop"]
        WHOST["浏览器页面壳<br/>apps/web"]
    end
    subgraph MOBILE["Android：Kotlin + Jetpack Compose"]
        AUI["Compose 界面<br/>android/app ui / home / settings / shell"]
        AHOST["平台服务 / LiveKit<br/>android/app platform / live / shell"]
        ACORE["逻辑层<br/>android/core"]
    end
    API["REST API<br/>账号 / 房间 / 消息 / 歌单"]
    SSE["SSE /me/stream<br/>实时业务事件"]
    LIVE["LiveKit + WebRTC<br/>语音 / 摄像头 / 屏幕共享"]
    OBJ["S3 兼容对象存储<br/>资源 / 安装包 / 更新源"]
    PUSH["Android FCM<br/>进程退出后的推送"]

    DUI --> DCORE
    DCORE -->|Platform 接口| DHOST
    DCORE -->|Platform 接口| WHOST
    DHOST --> API
    DHOST --> SSE
    DHOST --> OBJ
    WHOST --> API
    WHOST --> SSE
    WHOST --> OBJ
    DCORE --> LIVE
    AUI --> AHOST
    AUI --> ACORE
    AHOST --> ACORE
    ACORE --> API
    ACORE --> SSE
    ACORE --> OBJ
    AHOST --> LIVE
    AHOST --> PUSH
```

### 两套客户端的分层

两套代码都按 Flutter 版的目录一比一移植，分层相同。下表中 Kotlin 路径省略了 `src/main/kotlin/com/gangchat/client/` 前缀，例如 `android/core` 的 `app/` 指 `android/core/src/main/kotlin/com/gangchat/client/app/`。

| 层 | 桌面端与 Web（TypeScript） | Android（Kotlin） |
| --- | --- | --- |
| 运行时对等层 | `packages/core/src/lib`、`packages/core/src/platform`（`Platform` 接口、`HttpClient`）、`packages/ui/src/lib` | `android/core` 的 `foundation/`、`plugins/`；`android/app` 的 `widgets/` |
| 配置、协议、鉴权 | `packages/core/src/config`、`protocol`、`auth` | `android/core` 的 `config/`、`protocol/`、`auth/` |
| 业务逻辑 | `packages/core/src/app` | `android/core` 的 `app/`；依赖 LiveKit、录音或 Compose 的少数文件在 `android/app` 的 `app/` |
| 实时与语音 | `packages/core/src/live`、视频渲染 `packages/ui/src/live` | `android/core` 的 `live/`（SSE 等纯逻辑）；`android/app` 的 `live/` 和 `platform/webrtc/` |
| 平台服务 | `packages/core/src/shell`；宿主实现在 `apps/desktop/src`（Electron）和 `apps/web/src/platform`（浏览器） | `android/core` 的 `shell/`（本地偏好等）、`android/app` 的 `shell/`；原生实现在 `android/app` 的 `platform/` |
| 界面 | `packages/ui/src/ui`、`home`、`settings`、`shell` | `android/app` 的 `ui/`、`home/`、`settings/`、`shell/` |

文件名和标识符都保持 Dart 原名（TypeScript 文件用 snake_case，Kotlin 文件用 PascalCase），两端可以逐文件对照。具体规则见 [TypeScript 移植约定](docs/js-port-conventions.md) 和 [Android 移植约定](docs/android-port-conventions.md)，各层职责和依赖方向见 [模块化开发指南](docs/modular-development-guide.md)。

### REST API

账号、房间、消息、通知、文件元数据、表情包、歌单和音乐盒控制通过带 Bearer Token 的 REST API 完成。界面组件不直接拼请求体，而是通过 protocol 层的 API client（`packages/core/src/protocol/api_client.ts`、`android/core` 的 `protocol/ApiClient.kt`）和 app 层的 controller 进入。

桌面端的 HTTP 请求（含上传、下载和 SSE）由 Electron 主进程用 Chromium 网络栈发出（`apps/desktop/src/main/net_bridge.ts`），没有浏览器跨域限制；Web 版用浏览器 `fetch`（`apps/web/src/platform/fetch_http_client.ts`），页面与 API 同源部署，不需要跨域配置；Android 使用 OkHttp（`android/core` 的 `foundation/http/`）。

### SSE 实时事件

客户端维护 `GET /api/v1/me/stream` 长连接（`LiveStreamClient`，两端都在 `live/` 目录）：

- 解析服务端事件并更新房间、消息、通知、已读、角色和实时状态。
- 35 秒无任何字节时由 watchdog 判定连接失活。
- 连接失败后使用指数退避与随机抖动重连。
- 每次成功连接后重新拉取权威快照，修复断线期间漏掉的事件。

### LiveKit 与 WebRTC

- LiveKit 负责语音、摄像头和屏幕视频轨道。
- 服务端签发房间 Token，并把管理权限同步到 LiveKit。
- 桌面端使用官方 JS SDK `livekit-client`，在渲染进程中运行（`packages/core/src/live/live_session.ts`）；屏幕源选择和摄像头/麦克风系统权限由主进程处理（`apps/desktop/src/main/screen_capture.ts`、`media_access.ts`）。
- Android 使用 LiveKit Android SDK（`io.livekit:livekit-android`，版本见 `android/gradle/libs.versions.toml`），取代 Flutter 版分叉的 `flutter_webrtc` / `livekit_client`。分叉里 Android 端的修复（进程级 AudioSwitch 音频路由、麦克风增益、MediaProjection 屏幕采集、播放捕获）移植到 `android/app` 的 `platform/webrtc/`。
- 屏幕共享声音在两端都由隐藏的 `<用户 ID>--screen-audio` 辅助参与者在第二个 LiveKit 房间里单独发布，避免和麦克风的音频模块相互干扰；声音发布失败时画面继续共享。
- 升级 `livekit-client` 或 LiveKit Android SDK 前必须做完整的音视频、重连和屏幕共享回归。Android 的断线续连检测依赖反射 SDK 内部回调（见 [Android 移植状态](docs/android-port-status.md)），`protobuf-javalite` 固定为 SDK 自带的版本（见 `android/gradle/libs.versions.toml` 中的注释），需要一起核对。

### 对象存储与资源缓存

- 头像、房间图标、附件和表情通过服务端资源地址加载。
- 客户端统一解析历史绝对 URL、相对资源路径和当前资源域名（`AppConfig.resolveAssetUrl`）。
- 桌面端的远端图片经 `gang-media://` 协议走主进程磁盘缓存（512 MB LRU，`apps/desktop/src/main/media_protocol.ts`）；Android 由 `MediaCacheController` 缓存到应用缓存目录。
- 下载文件、音乐试听和实时提示音使用各自的缓存/临时目录。
- 更新检查直接读取公开对象存储 `releases/` 下的发布文件，见 [自动更新](#13-自动更新)。

### 桌面宿主（Electron）

`apps/desktop` 实现 `@gang-chat/core` 的 `Platform` 接口并组装界面：

- 主进程 `src/main/`：窗口、托盘和关闭行为（`window.ts`），偏好和安全存储（`storage.ts`），网络桥（`net_bridge.ts`），媒体缓存协议（`media_protocol.ts`），文件对话框与读写（`files.ts`），剪贴板（`clipboard.ts`），系统通知、外链和子进程（`system.ts`），屏幕采集与媒体权限（`screen_capture.ts`、`media_access.ts`），Windows 更新（`updater.ts`）和 macOS 更新（`sparkle.ts`）。
- preload `src/preload/index.ts`：渲染进程与主进程之间唯一的桥，只放行 `src/shared/ipc.ts` 中登记的通道。
- 渲染进程 `src/renderer/src/`：`desktop_root.tsx` 启动应用，`platform/desktop_platform.ts` 在 preload 之上实现 `Platform`。

### Web 宿主

`apps/web` 是 Vite 页面，`src/platform/` 用浏览器 API 实现 `Platform`：`fetch`（流式响应、SSE）、`localStorage`、文件选择和浏览器下载、异步剪贴板、Web Notifications 和 `speechSynthesis`。`os` 为 `web`，共享代码里从 Dart 移植来的 `kIsWeb` 分支照旧生效：没有窗口、托盘和标题栏按钮，没有应用内更新。存储格式与 Flutter Web 版相同，同一域名下从 Flutter 版切换过来不用重新登录；`public/flutter_service_worker.js` 负责注销 Flutter 版注册过的 Service Worker。功能状态和部署步骤见 [Web 版说明](docs/web-spike.md)。

### Android 原生能力

`android/app` 的 `platform/` 是 Flutter 版 MethodChannel 背后原生代码的直接移植，由 `shell/` 中的服务包装后供界面使用：

- 前台服务保持后台在线，以及任务划除处理（`GangChatBackgroundService`、`shell/AndroidSystemService`）。
- 通知渠道、房间通知和数字角标（`GangChatNotifications`）；渠道 id、通知 id 和 intent extra 与 Flutter 版一致。
- FCM Token 获取、离线消息和通知点击路由（`GangChatFirebaseMessagingService`、`GangChatPushRegistration`）。
- APK 后台下载、校验、FileProvider 和系统安装器（`shell/ReleaseUpdateService`、`AndroidUpdateInstaller`）。
- 屏幕旋转、全屏系统 UI 和其他系统能力调用（`SystemChrome`、`AndroidDisplay`、`AndroidSystemBridge`）。
- 兼容 Flutter 版存储格式的安全存储和偏好（`FlutterSecureStorageStore`、`FlutterSharedPreferencesStore`）。

进程启动时 `GangChatApplication` 为 `android/core` 的门面安装真实实现；`MainActivity` 每次创建时运行 `GangChatStartup`，再显示 `GangApp`。

## 项目结构

```text
gang-chat-client/
├─ .github/workflows/
│  ├─ android.yml              Android：UTF-8 检查、单元测试、debug APK
│  ├─ desktop-electron.yml     桌面端：类型检查、单元测试、模拟服务端自检、未签名测试包
│  └─ release.yml              正式发布：Windows、macOS、Android
├─ android/                    Android 原生客户端（Gradle 工程）
│  ├─ core/                    纯 Kotlin（JVM）模块：运行时对等层、协议、鉴权、业务逻辑、本地偏好
│  │  └─ src/
│  │     ├─ main/kotlin/com/gangchat/client/
│  │     │                     foundation/ plugins/ config/ protocol/ auth/ app/ lifecycle/ live/ shell/
│  │     ├─ test/              JUnit 测试
│  │     └─ testFixtures/      GangTestRule、MockClient（两个模块共用）
│  ├─ app/                     Android 应用模块：平台服务、LiveKit、Compose 界面
│  │  └─ src/
│  │     ├─ main/kotlin/com/gangchat/client/
│  │     │                     widgets/ platform/ app/ live/ shell/ ui/ home/ settings/
│  │     ├─ main/res/          图标、启动主题、FileProvider 路径
│  │     └─ test/              Robolectric、Compose UI 和 Roborazzi 截图测试
│  ├─ gradle/libs.versions.toml 依赖版本目录
│  ├─ version.properties       versionName 与发行时间
│  └─ settings.gradle.kts、build.gradle.kts、gradlew
├─ apps/desktop/               Electron 桌面客户端
│  ├─ src/main/                主进程：窗口、存储、网络、更新、屏幕采集
│  ├─ src/preload/             渲染进程与主进程之间的 IPC 桥
│  ├─ src/renderer/            渲染进程入口与 Platform 实现
│  ├─ src/shared/              IPC 通道定义
│  ├─ native/sparkle/          macOS Sparkle 的 Node-API 模块
│  ├─ scripts/                 打包、Sparkle 准备、ad-hoc 签名
│  ├─ build/                   图标、NSIS 脚本、macOS entitlements
│  └─ electron-builder.yml
├─ apps/web/                   Web 版（Vite 页面，与桌面端同一套界面）
│  ├─ src/platform/            Platform 的浏览器实现
│  ├─ public/                  图标、manifest、清理旧 Service Worker 的脚本
│  └─ vite.config.ts           生产构建输出到仓库根目录的 build/web
├─ deploy/web/                 Web 版的 nginx 配置
├─ packages/
│  ├─ core/                    @gang-chat/core：协议、鉴权、业务逻辑、LiveKit 会话、平台服务（不依赖界面）
│  │  ├─ src/                  lib/ platform/ config/ protocol/ auth/ app/ lifecycle/ live/ shell/
│  │  └─ test/                 vitest 单元测试
│  └─ ui/                      @gang-chat/ui：React DOM 界面
│     ├─ src/                  lib/ ui/ home/ settings/ shell/ live/
│     └─ test/
├─ assets/                     两端共用的资源（Android 直接作为 APK assets）
│  ├─ branding/               品牌图
│  ├─ config/app_config.json  默认 API、资源和发布地址
│  └─ fonts/                  字体
├─ docs/                       移植约定与状态、模块化指南、音乐盒、发布和设计文档
├─ tool/
│  ├─ check_utf8.py           UTF-8、BOM 和乱码检查
│  ├─ build_web.sh            构建 Web 版到 build/web
│  ├─ deploy_web.sh           经 SSH 部署 Web 版
│  ├─ js/mock_server/         本地模拟服务端
│  ├─ js/generate_icons.mjs   生成 packages/ui/src/ui/icons.ts
│  └─ verify_sparkle_signature.swift  发布时校验 Sparkle 签名
├─ package.json                npm workspaces 与根脚本
├─ package-lock.json
├─ tsconfig.base.json
└─ vitest.config.ts
```

更严格的层级职责和依赖方向见 [模块化开发指南](docs/modular-development-guide.md)。

## 开发环境

### 通用要求

- Git。
- Python 3，用于 `tool/check_utf8.py`。
- 可访问 Gang Chat 服务端、LiveKit 和资源存储的网络环境；没有服务端时可以使用 [模拟服务端](tool/js/mock_server/README.md)（需要 Node.js 22）。

### 桌面端（Windows / macOS）

- Node.js 22（CI 使用 22）和 npm。
- Windows：Windows 10/11 64 位。生成 NSIS 安装程序和便携 ZIP 由 electron-builder 完成，不需要另装 NSIS。
- macOS：打包需要在 macOS 上进行，并安装 Xcode 命令行工具：`apps/desktop/scripts/prepare_sparkle.mjs` 会下载固定版本、校验过的 Sparkle，并编译 `apps/desktop/native/sparkle/sparkle.mm`。应用最低支持 macOS 12（`apps/desktop/electron-builder.yml` 的 `minimumSystemVersion`）。

Web 版使用同一套 npm workspaces，不需要额外依赖。在仓库根目录安装所有 workspace 依赖（CI 使用 `npm ci`，严格按 `package-lock.json` 安装）：

```bash
npm install
```

### Android

- JDK 21（CI 使用 Temurin 21；Kotlin/Java 代码以 17 为目标版本编译）。
- Android SDK：SDK Platform `android-37.0`（`compileSdk = 37`）、Build Tools 和 Platform Tools。应用 `minSdk` 为 24，`targetSdk` 为 36。
- 设置环境变量 `ANDROID_HOME` 指向 SDK，或在 `android/local.properties` 中写 `sdk.dir=<SDK 路径>`（该文件不提交）。
- Gradle 由 wrapper 下载（`android/gradle/wrapper/gradle-wrapper.properties`），不需要单独安装；也可以用 Android Studio 直接打开 `android/` 目录。
- 依赖版本集中在 `android/gradle/libs.versions.toml`（AGP、Kotlin、Compose BOM、LiveKit、Firebase、Robolectric、Roborazzi 等）。
- 真机调试需要开启开发者选项和 USB 调试；无需安装 OPPO 互联等厂商互联软件。

Windows 上连接真机：

1. 手机进入“设置 → 关于本机/版本信息”，连续点击版本号开启开发者选项。
2. 在“系统设置/其他设置 → 开发者选项”中开启 USB 调试。
3. USB 用途选择“传输文件 / Android Auto”更便于驱动识别；ADB 授权完成后具体文件模式通常不影响调试。
4. 在手机上接受这台电脑的 RSA 调试授权。
5. 执行：

```powershell
adb devices
```

如果列表显示 `unauthorized`，解锁手机并重新确认 USB 调试授权；如果完全没有设备，安装手机厂商 USB 驱动或更新 Android Platform Tools。

## 配置客户端

默认配置位于 `assets/config/app_config.json`，两端共用：

```json
{
  "api_base_url": "https://ky-z.com/gang-chat/api/v1",
  "asset_base_url": "https://ky-z.com/gang-chat",
  "release_bucket_url": "https://ky-z.com/gang-chat/assets"
}
```

配置优先级从高到低为：

1. 构建时环境变量（取代 Flutter 的 `--dart-define`）：桌面端在 `npm run dev` 或构建时由 `apps/desktop/electron.vite.config.ts` 读取；Android 在 Gradle 构建时写进 `BuildConfig`（`android/app/build.gradle.kts`）。
2. `assets/config/app_config.json`：桌面安装包把它复制为资源目录中的 `app_config.json`，Android 把整个 `assets/` 打进 APK。
3. 代码中的开发回退值（`packages/core/src/config/app_config.ts`、`android/core/src/main/kotlin/com/gangchat/client/config/AppConfig.kt`），指向 `http://127.0.0.1:21116`。

支持的构建变量：

| 变量 | 用途 |
| --- | --- |
| `GANG_API_BASE_URL` | REST API 根地址，必须包含 `/api/v1` |
| `GANG_ASSET_BASE_URL` | 头像、附件、表情等资源根地址 |
| `GANG_RELEASE_BUCKET_URL` | 自动更新的根地址：`<它>/releases/` 下是安装包和更新源（`appcast.xml`、`latest.yml`），由服务器提供 |

变量在构建时写入，修改后需要重新运行或重新构建。

桌面端连接本地服务端：

```bash
GANG_API_BASE_URL=http://127.0.0.1:21116/api/v1 \
GANG_ASSET_BASE_URL=http://127.0.0.1:21116 \
npm run dev
```

Windows PowerShell：

```powershell
$env:GANG_API_BASE_URL = "http://127.0.0.1:21116/api/v1"
$env:GANG_ASSET_BASE_URL = "http://127.0.0.1:21116"
npm run dev
```

Android（在 `android/` 目录下）：

```bash
GANG_API_BASE_URL=http://192.168.1.10:21116/api/v1 \
GANG_ASSET_BASE_URL=http://192.168.1.10:21116 \
./gradlew :app:installDebug
```

Android 真机不能使用电脑的 `127.0.0.1`。请使用手机可访问的局域网或 HTTPS 地址；用 USB 连接时也可以先执行 `adb reverse tcp:21116 tcp:21116`，把手机的 21116 端口转发到电脑，再继续使用 `127.0.0.1`。清单开启了 `usesCleartextTraffic`，`http://` 开发地址可以直接使用。模拟服务端默认只监听 `127.0.0.1`，局域网访问时需要加 `--host 0.0.0.0 --public-url http://<电脑 IP>:21116`；用 `adb reverse` 时不需要。

## 运行与调试

### 桌面端（Windows / macOS）

在仓库根目录：

```bash
npm run dev
```

启动 Electron，渲染进程（React 界面）支持热更新；修改主进程或 preload 代码后需要重新运行。开发版不启用 electron-updater 和 Sparkle：检查更新时 Windows 退回列出对象存储中的安装包并整包下载，macOS 退回在浏览器中下载 DMG。

macOS 第一次使用摄像头、麦克风或屏幕共享时，在“系统设置 → 隐私与安全性”授予对应权限。权限改变后可能需要完全重启 Gang Chat。

没有服务端时，先启动模拟服务端，再按 [配置客户端](#配置客户端) 中的环境变量运行：

```bash
node tool/js/mock_server/server.mjs --port 21116
```

账号 `demo` / `demo1234`，验证码固定为 `123456`。模拟服务端本身没有 LiveKit；需要连通语音时另开 `livekit-server --dev`，并给模拟服务端加上 `--livekit-api-key devkey --livekit-api-secret secret`（见 [模拟服务端](tool/js/mock_server/README.md)）。

### Web

```bash
npm run dev:web                # http://localhost:4176/gang-chat/app/，热更新
```

连本地服务端时同样用 `GANG_API_BASE_URL` / `GANG_ASSET_BASE_URL` 环境变量。麦克风、摄像头和屏幕共享要求页面在 HTTPS 或 `localhost` 下运行。

### Android

在 `android/` 目录下构建、安装并启动 debug 包（Windows PowerShell 中写作 `.\gradlew`）：

```bash
./gradlew :app:installDebug
adb shell am start -n com.gangchat.client/.MainActivity
```

也可以在 Android Studio 中打开 `android/` 后运行 `app`。debug 包使用调试签名，但 applicationId 与正式版相同（`com.gangchat.client`），设备上已装正式版时需要先卸载才能安装 debug 包，卸载会清除本地登录和设置。

排查日志：

```bash
adb logcat -s GangChat
```

应用日志（`debugPrint`）的 tag 是 `GangChat`；LiveKit 和 WebRTC 使用各自的 tag，需要时去掉 `-s` 过滤查看完整 logcat。

屏幕共享、后台服务、安装 APK、系统通知和 FCM 必须在 Android 真机上验证；Robolectric 测试无法替代这些平台路径。

## 构建

### Windows

```bash
npm run dist:win -w @gang-chat/desktop
```

输出在 `apps/desktop/dist/`：

```text
GangChat_v<version>.exe             NSIS 安装程序
GangChat_v<version>.exe.blockmap    差分更新数据
latest.yml                          electron-updater 更新源
GangChat-<version>-windows.zip      便携 ZIP
win-unpacked/GangChat.exe           未打包的程序目录
```

版本号取 `apps/desktop/package.json` 的 `version`，本地为 `1.0.0`。设置 `CSC_LINK` / `CSC_KEY_PASSWORD` 时 electron-builder 用该证书签名，否则生成未签名的包。只编译不打包用根目录的 `npm run build`（输出 `apps/desktop/out/`）；`npm run dist:dir -w @gang-chat/desktop` 只生成当前平台未打包的程序目录。

### macOS

```bash
npm run dist:mac -w @gang-chat/desktop
```

输出 universal 的 `apps/desktop/dist/GangChat_v<version>.dmg`，应用本体在 `apps/desktop/dist/mac-universal/GangChat.app`。`CFBundleVersion` 由 `apps/desktop/scripts/package.mjs` 按 `major * 1,000,000 + minor * 1,000 + patch` 设置，与 Android 的 versionCode 相同。没有 Developer ID 时，`apps/desktop/scripts/after_pack.cjs` 做 ad-hoc 签名，因为 Sparkle 不接受代码签名无效的更新；Developer ID 签名和公证在发布工作流中配置。

### Web

```bash
tool/build_web.sh                                       # 输出 build/web，base href 为 /gang-chat/app/
npm run preview -w @gang-chat/web                       # 本地预览生产构建
GANG_WEB_DEPLOY_SSH=deploy@ky-z.com tool/deploy_web.sh  # 构建并经 SSH 部署
```

`GANG_WEB_BASE_HREF` 可以改部署路径，`GANG_CHAT_VERSION` / `GANG_CHAT_RELEASED_AT` 设置设置页“关于”里显示的版本和发布时间。`tool/deploy_web.sh` 每次上传到新的 `releases/<时间戳>/` 目录再原子切换 `current`，保留最近几次用于回滚；nginx 配置见 `deploy/web/nginx-gang-chat-app.conf`。

### Android Debug APK

```bash
cd android
./gradlew :app:assembleDebug
```

输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

`versionName` 取 `android/version.properties`（仓库中为 `1.0.0`），`versionCode = major * 1,000,000 + minor * 1,000 + patch` 由 `android/app/build.gradle.kts` 计算，所以本地构建显示 `1.0.0`。

### Android Release APK

```bash
cd android
./gradlew :app:assembleRelease
```

输出 `android/app/build/outputs/apk/release/app-release.apk`。

发布签名环境变量：

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

四个变量都设置时使用正式签名；都不设置时本地 release 构建回退到调试签名；只设置一部分会直接失败。GitHub Release 工作流始终提供正式 keystore，缺失或无效时失败。

`android/app/google-services.json` 存在时构建才启用 Firebase（FCM）；没有该文件时 APK 照常构建，只是无法接收进程退出后的推送。

不要提交 keystore、`key.properties`、`google-services.json` 或任何私钥。

## 测试与质量检查

### 快速检查

```bash
python3 tool/check_utf8.py
npm run typecheck
npm test
cd android && ./gradlew :core:test :app:testDebugUnitTest
```

### 桌面端与 Web

- `npm run typecheck`：对 `packages/core`、`packages/ui`、`apps/desktop` 和 `apps/web` 执行 `tsc --noEmit`（strict）。
- `npm test`：vitest，运行 `packages/*/test/**/*.test.ts(x)` 和 `apps/*/test/**/*.test.ts`（见 `vitest.config.ts`）。
- 模拟服务端自检：`node tool/js/mock_server/check.mjs`。

聚焦运行：

```bash
npx vitest run packages/core/test/app/room_forms.test.ts
npx vitest run packages/core/test/live
```

### Android

在 `android/` 目录下：

```bash
./gradlew :core:test                    # 纯 JVM 的 JUnit 测试（android/core/src/test）
./gradlew :app:testDebugUnitTest        # Robolectric 上的 Android、Compose UI 测试（android/app/src/test）
./gradlew :core:test --tests 'com.gangchat.client.app.RoomFormsTest'
./gradlew :app:testDebugUnitTest --tests 'com.gangchat.client.ui.SegmentedControlTest'
```

测试固定在 UTC 时区运行。失败报告在 `android/core/build/reports/tests/` 和 `android/app/build/reports/tests/`。

### 截图测试（Roborazzi）

```bash
cd android
./gradlew :app:recordRoborazziDebug
./gradlew :app:recordRoborazziDebug --tests 'com.gangchat.client.ui.UiGalleryScreenshotTest'
```

截图测试用 Roborazzi 在 Robolectric 上渲染界面，PNG 输出到 `android/app/build/outputs/roborazzi/`。仓库不提交截图基线；截图用于对照 Dart 原尺寸和桌面端截图 `docs/js-port-screenshots/`（Android 宽屏布局与桌面端同源）。

### 端到端测试（模拟服务端）

Android 有一个默认跳过的端到端测试 `GangAppEndToEndTest`，只在设置 `GANG_E2E_API_BASE_URL` 时运行：

```bash
node tool/js/mock_server/server.mjs --port 21116
# 另开一个终端，在 android/ 目录下：
GANG_E2E_API_BASE_URL=http://127.0.0.1:21116/api/v1 \
  ./gradlew :app:recordRoborazziDebug --tests 'com.gangchat.client.GangAppEndToEndTest'
```

它启动真实的 `MainActivity`、OkHttp 和 SSE 实时流，走完登录、房间列表、打开房间、发送消息、他人消息经 SSE 到达、通知、设置和退出登录，手机与平板各输出 8 张截图（`e2e_phone_*`、`e2e_tablet_*`）。测试会往模拟服务端写入消息，重新运行前重启服务端以恢复种子数据；`GANG_E2E_ASSET_BASE_URL` 默认取 API 地址去掉 `/api/v1`。

桌面端用同一个模拟服务端做冒烟测试：按 [运行与调试](#运行与调试) 启动后手动走一遍主要流程，并与 `docs/js-port-screenshots/` 对照。

### 真机验证

Robolectric、截图测试和模拟服务端覆盖不到以下路径，涉及时必须在真实设备上确认：

- Android：LiveKit 入会、发布/订阅音视频和自动重连；AudioSwitch 的听筒、扬声器、蓝牙路由；MediaProjection 屏幕共享和播放捕获的共享声音；Android Keystore 加密的凭据存储，以及从 Flutter 版覆盖安装后的数据读取；FCM 推送、通知点击跳转、数字角标、前台服务和任务划除；APK 后台下载与系统安装器。清单见 [Android 移植状态](docs/android-port-status.md) 的“需要真机确认”。
- 桌面端：真实音频设备枚举与切换、屏幕共享声音（Windows 回环、macOS ScreenCaptureKit）、electron-updater / Sparkle 更新和安装包签名，需要在真实的 Windows、macOS 上验证。

验证时记录系统版本、设备型号和网络环境。

### 测试目录规则

- 桌面端：测试放 `packages/<包>/test/<目录>/<文件名>.test.ts`，目录与 `src/` 对应；共享替身（`fake_platform`、`fake_http_client`、`fake_audio_player`）放 `packages/core/test/support/`。
- Android：纯逻辑测试放 `android/core/src/test/kotlin/com/gangchat/client/<目录>/<名字>Test.kt`；需要 Android、LiveKit 或 Compose 的测试放 `android/app/src/test/kotlin/com/gangchat/client/<目录>/`；共享的 `GangTestRule`、`MockClient` 在 `android/core/src/testFixtures`。
- 从 Dart 移植的用例保持原用例名，便于对照。
- 缺陷修复应先添加最小复现，再覆盖相邻边界和异步销毁安全。
- 行为改动要在两端都补测试；跨层改动除聚焦测试外，还应执行全量 `npm run typecheck`、`npm test` 和 `./gradlew :core:test :app:testDebugUnitTest`。

## 发布流程

`.github/workflows/release.yml` 在推送 `v*` Tag 或手动输入版本时执行：

1. 校验 `major.minor.patch` 版本格式，计算 `versionCode = major * 1,000,000 + minor * 1,000 + patch`，记录发行时间。
2. 并行构建三端：
   - macOS 和 Windows：`npm ci` 后把版本写进 `apps/desktop/package.json`，用 electron-builder 打包；macOS 还会核对 `CFBundleVersion`、校验代码签名，并用 Sparkle 私钥签名 DMG、生成 `appcast.xml`。
   - Android：执行 `python3 tool/check_utf8.py`，把版本和发行时间写进 `android/version.properties`，执行 `./gradlew :app:assembleRelease`，再用 `apksigner` 校验签名、用 `aapt2 dump badging` 核对 versionCode 和 versionName。
3. 汇总签名状态，发布 GitHub Release 和全部安装资产：`GangChat_v<version>.exe`、`.exe.blockmap`、`latest.yml`、`GangChat-<version>-windows.zip`、`GangChat_v<version>.dmg`、`appcast.xml` 和 `GangChat_v<version>.apk`。
4. GitHub Release 发布成功后，`deploy-web` 用 `tool/build_web.sh` 构建 Web 版，并经 SSH 部署到 `https://ky-z.com/gang-chat/app/`。

发布后再把这些文件同步到对象存储的 `releases/` 目录；`appcast.xml` 指向 DMG，必须最后上传。相关路径有推送或 Pull Request 时，`.github/workflows/desktop-electron.yml` 和 `.github/workflows/android.yml` 分别检查桌面端（含 Web 构建）和 Android，并把未签名的桌面测试包和 debug APK 上传为工作流产物。

### 签名策略

- Windows：配置 `WINDOWS_CERTIFICATE_BASE64` 和 `WINDOWS_CERTIFICATE_PASSWORD` 时，electron-builder 对 `GangChat.exe`、安装程序和卸载程序做 Authenticode 签名并校验；完全未配置时允许明确标记的未签名构建；只配置一部分则失败。
- macOS：配置完整 Developer ID 和 App Store Connect API Key 时执行签名和公证；完全未配置时做 ad-hoc 签名、不公证，并明确标记；部分配置则失败。
- macOS 更新：`SPARKLE_ED_PRIVATE_KEY` 必须配置，并与 `apps/desktop/electron-builder.yml` 中的 `SUPublicEDKey` 对应；发布前用 `tool/verify_sparkle_signature.swift` 校验签名，缺失或不匹配时失败。
- Android：正式发布必须提供固定的 release keystore（`ANDROID_KEYSTORE_BASE64` 等四个 Secret），缺失或无效时失败，防止发布无法覆盖升级的临时签名 APK。
- Android FCM：`FIREBASE_GOOGLE_SERVICES_JSON_BASE64` 可选；缺失时 APK 仍可使用，但进程退出后的 FCM 推送不可用。
- Web 部署：`WEB_DEPLOY_SSH`、`WEB_DEPLOY_SSH_KEY`、`WEB_DEPLOY_KNOWN_HOSTS` 三个 Secret 都未配置时跳过部署并给出警告，只配置一部分时失败；配置方法见 [Web 版说明](docs/web-spike.md) 的“部署”。

完整 Secret 名称、签名命令和验证方法见 [Release workflow](docs/release-workflow.md)。

## 数据、权限与本地存储

### 服务端数据

账号、房间、成员、消息、通知、表情包元数据、个人歌单、房间歌单和音乐盒权威状态由 `gang-chat-server` 保存。客户端缓存不能替代服务端权威状态。

### 敏感本地数据

- 桌面端：Refresh Token 等敏感凭据用 Electron `safeStorage`（macOS 钥匙串 / Windows DPAPI）加密后，保存在用户数据目录的 `secure-storage.json`（`apps/desktop/src/main/storage.ts`）。Flutter 桌面版把凭据存在系统钥匙串/凭据管理器中，因此切换到 Electron 版后需要重新登录一次。
- Web：刷新令牌按 `flutter_secure_storage_web` 的格式加密后存在 `localStorage`（`apps/web/src/platform/web_storage.ts`），从 Flutter Web 版切换过来保留登录状态。
- Android：按 Flutter 版 `flutter_secure_storage` 的格式保存（Android Keystore 包装的密钥 + AES-GCM 数据，`FlutterSecureStorageStore`），从 Flutter 版 APK 覆盖安装后保留登录状态。Keystore 密钥不可用时（例如备份恢复到新设备）会清空安全存储，需要重新登录一次。
- 不要在日志、测试 fixture、截图、Issue 或提交中暴露真实 Token、密码、证书和生产数据库内容。

### 非敏感本地偏好

以下内容保存在非敏感偏好中：桌面端是用户数据目录中的 `preferences.json`（键名与 Flutter 版相同），Web 版是 `localStorage` 中 `flutter.<键>` 形式的 JSON（与 Flutter Web 版相同），Android 是 Flutter 版 `shared_preferences` 使用的 `FlutterSharedPreferences` 文件（`flutter.` 键前缀和值编码按原插件复现，`FlutterSharedPreferencesStore`）。

- API/界面相关的本地选择。
- 输入/输出设备签名。
- 输入、输出和屏幕共享音量。
- 屏幕共享画质与帧率。
- Windows/macOS 关闭方式。
- 自动更新提示和忽略版本。
- 语言和登录账号历史等非敏感展示信息。

本地偏好包含迁移逻辑，覆盖安装新版本时不应因存储键变化而静默重置。

### Android 权限

`android/app/src/main/AndroidManifest.xml` 声明的主要权限包括：

- 网络访问和网络状态。
- 摄像头。
- 麦克风和音频设置。
- Bluetooth/附近设备音频连接。
- 通知。
- 前台数据同步、远程消息、麦克风和屏幕录制（MediaProjection）服务。
- 请求安装 APK。

摄像头不是强制硬件；没有摄像头的设备仍可以使用文字、语音收听和其他功能。权限只应在功能实际需要时请求。

### 不应作出的安全假设

本仓库没有把“端到端加密”声明为产品能力。不要将 HTTPS、LiveKit 传输安全或安全存储描述为端到端加密；服务端仍参与账号、消息和媒体授权流程。

## 常见问题

### Android 收不到进程退出后的消息通知

依次检查：

1. 设置 → 偏好设置 → 系统通知已开启。
2. Android 系统设置已允许 Gang Chat 通知。
3. 对应房间的消息策略是“全部”，不是“接收但不提醒”或“屏蔽”。
4. 安装包构建时包含与 `com.gangchat.client` 匹配的 Firebase 配置（`android/app/google-services.json`，正式发布由 `FIREBASE_GOOGLE_SERVICES_JSON_BASE64` 写入）。
5. 服务端配置了同一 Firebase 项目的服务账号。
6. 手机具备可工作的 Google Play 服务且没有被厂商后台策略完全限制。

OPPO 等无 Google 服务的国内机型无法仅靠 FCM 在进程结束后接收通知，需要以后接入 OPPO Push 等厂商通道。应用仍可在前台或后台服务存活时通过 SSE 接收消息。

### Android 自动更新下载完成但没有立即出现安装界面

- 检查是否允许 Gang Chat“安装未知应用”。
- 检查系统通知权限；后台完成时通常通过通知提示点击安装。
- 没有通知权限时重新打开 Gang Chat，客户端会检查已完成下载并再次启动安装流程。
- 某些系统的安装器会在“正在准备”停留一段时间；APK 已经过基本完整性检查，但系统仍会执行自己的包解析和安全扫描。

### 从 Flutter 版升级后需要重新登录吗

- Android：不需要。applicationId、签名和 versionCode 规则不变，安全存储和偏好按 Flutter 插件的格式读写，覆盖安装后保留登录状态和设置；只有 Keystore 密钥不可用时才需要重新登录。
- Windows / macOS：需要重新登录一次，因为 Electron 版改用 `safeStorage` 保存凭据。
- Web：不需要。新版部署在同一地址，`localStorage` 中的数据按 Flutter Web 版的格式读写。

### Windows/macOS 提示安装包不受信任

发布流程允许在没有桌面签名 Secret 时生成明确标记的产物：Windows 为未签名，macOS 为 ad-hoc 签名且未公证。请优先使用已签名的正式版本，并按 [发布文档](docs/release-workflow.md) 验证签名。不要在不确认来源的情况下绕过系统安全提示。

macOS 的 ad-hoc 签名在每次发布时都会变化，所以每次更新后系统会再次询问麦克风、摄像头、屏幕录制和钥匙串权限；配置 Developer ID 签名可以避免这一问题。

### 语音频道没有声音或不能说话

- 确认麦克风和耳机按钮状态。
- 在设置 → 语音和视频中检查输入/输出设备与音量。
- 检查系统麦克风权限和应用音量混音器。
- Bluetooth 设备切换通话/媒体配置后，等待客户端重新绑定系统默认设备，必要时重新选择设备。
- 若刚从断网或休眠恢复，等待实时连接和 LiveKit 完成重连；仍异常时离开语音频道后重新加入，并保留日志用于排查。

### 屏幕共享没有声音

- 部分 DRM/受保护内容禁止系统捕获音频。
- Android 只有允许 Playback Capture 的应用声音可以被捕获。
- macOS 需要 14.2 或更高版本，并授予屏幕录制权限；Windows 使用系统回环采集。
- 屏幕音频使用独立辅助连接，网络或 LiveKit 发布失败时可能只有画面。日志中搜索 `screen-audio` 可查看原因：桌面端在开发者工具控制台，Android 在 `adb logcat -s GangChat`。

### Android 真机不在 `adb devices` 中，或 Gradle 安装失败

```powershell
adb kill-server
adb start-server
adb devices
```

然后重新插拔 USB、确认 USB 调试授权和驱动。仅安装 OPPO 互联等文件互传应用不会自动开启 ADB 调试。

`./gradlew :app:installDebug` 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 时，说明设备上已装了不同签名（通常是正式版）的 Gang Chat。执行 `adb uninstall com.gangchat.client` 后再安装；卸载会清除本地登录和设置。

### 修改了歌单，但语音频道音乐盒仍显示旧内容

当前实现会在歌单成功编辑后递增客户端失效修订号，并在音乐盒中重新加载。如果仍显示旧数据：

- 确认编辑请求实际成功并出现成功提示。
- 重新打开音乐盒，或收起再展开 `房间歌单` / `我的歌单` 分组触发重新加载。
- 区分“歌单模板”和“已经激活的播放快照”：编辑保存歌单不会修改正在播放中的不可变快照，这是预期行为。
- 正在播放的已保存歌单在面板内展开时，列出的是激活快照（服务端队列），而不是可编辑的歌单模板；要看模板请进入设置页编辑，或等待该歌单不再是当前来源。

## 开发约束

### 模块依赖

两套代码内部采用相同的分层，上层可以依赖下层，反之不行：

```text
界面：功能界面（home / settings / shell 界面）、可复用 ui
  ↓
适配层：shell 服务、live（LiveKit）
  ↓
app：业务规则、状态、controller、display helper
  ↓
protocol / auth / config
  ↓
运行时对等层：TypeScript 的 lib/、platform/；Kotlin 的 foundation/、plugins/
```

关键要求：

- `packages/core` 不依赖 React、DOM 专属 API 或 Electron；所有原生能力经 `platform()`（`packages/core/src/platform/platform.ts`），接口不够用时扩展接口并在 `apps/desktop` 中实现。
- `packages/ui` 只依赖 `@gang-chat/core`；`apps/desktop` 实现 `Platform` 并组装界面。
- `android/core` 是纯 Kotlin（JVM）模块，不能依赖 Android SDK、Compose 或 LiveKit，编译期即保证；需要它们的文件放进 `android/app`，包名仍按原 Dart 目录。
- 界面不直接拼 API 请求，不直接操作安全存储、文件选择器或 LiveKit 原生类型；LiveKit 类型只出现在桌面端的 `packages/core/src/live`、`packages/ui/src/live`，以及 Android 的 `live/`、`platform/webrtc/`。
- 平台能力通过 shell 或 live 的小接口注入。
- 可复用控件进入 `ui/`，只属于单个功能的大块 UI 留在对应功能文件。
- 不在页面组件中复制已有 controller 的搜索、权限、排序和状态同步规则。
- 两套代码不共用源码：新功能和缺陷修复要在两端分别实现，文件名、标识符和中文文案保持对应。

### 依赖和锁文件

- npm：根 `package.json` 用 workspaces 管理 `packages/core`、`packages/ui`、`apps/desktop`。依赖使用精确版本（`peerDependencies` 除外），`package-lock.json` 提交到仓库，CI 用 `npm ci` 安装。
- 有意升级依赖时才同时修改 `package.json` 和 `package-lock.json`；普通功能修改不要无原因重写锁文件。
- `.npmrc` 中的 `legacy-peer-deps=true` 是为了让 npm 10 能安装 vitest 4，不要删除。
- Gradle：依赖和插件版本集中在 `android/gradle/libs.versions.toml`，构建脚本通过 `libs.*` 引用，不在 `build.gradle.kts` 中写版本号；Gradle 版本由 wrapper 固定。
- `protobuf-javalite` 必须与 LiveKit Android SDK 自带的版本一致，升级 LiveKit 时一起核对。
- 升级 `livekit-client`、LiveKit Android SDK、Electron 或 Compose BOM 前，必须审计变化并做完整的音视频、重连、屏幕共享和界面回归。

### 跨平台和响应式要求

- 新功能必须考虑 Windows、macOS、Android 三端，并在桌面端和 Android 两套代码中分别实现。
- 桌面端里只属于 Windows 或 macOS 的行为用 `HostPlatform` 明确区分，不能改变另一端。
- 窄屏不能依赖固定像素凑布局；使用真实约束决定换行、缩放、滚动或收起。
- 所有异步回调、定时器、动画和指针监听在组件销毁后不得再更新状态（检查 `mounted` / `context.mounted`）。
- 鼠标、键盘、触屏、长按和文本选择要分别覆盖，不能把“长按”简单模拟成永久悬停。
- 修改语音、摄像头、屏幕共享或本地试听时，必须检查资源释放、重连、应用失焦/聚焦和任务退出。

### 提交前建议

```bash
python3 tool/check_utf8.py
npm run typecheck
npm test
(cd android && ./gradlew :core:test :app:testDebugUnitTest)
git diff --check
```

涉及服务端协议时还应在相邻的 `gang-chat-server` 仓库运行相应 Go 测试；涉及官网发布链接时在 `gang-chat-homepage` 运行：

```bash
npm test
```

## 进一步阅读

- [桌面端（Electron）说明](apps/desktop/README.md)
- [Web 版说明](docs/web-spike.md)
- [Flutter → TypeScript 移植约定](docs/js-port-conventions.md)
- [TypeScript / Electron 移植状态](docs/js-port-status.md)
- [Flutter → Android 原生移植约定](docs/android-port-conventions.md)
- [Android 原生移植状态](docs/android-port-status.md)
- [模块化开发指南](docs/modular-development-guide.md)
- [UI 设计规范](docs/ui-design-system.md)
- [新音乐盒完整实现方案](docs/music-box-next-generation-design.md)
- [音乐盒客户端 API 契约](docs/music-box-api.md)
- [Release workflow](docs/release-workflow.md)
- [模拟服务端](tool/js/mock_server/README.md)
