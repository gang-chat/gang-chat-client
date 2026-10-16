# Android 原生移植状态

Android 客户端正在从 Flutter 一比一移植为原生 Kotlin + Jetpack Compose，代码在 `android/`。
移植约定见 `docs/android-port-conventions.md`。本文记录各模块进度，以及与 Flutter 版的差异。

## 进度

| 范围 | 模块 | 状态 |
| --- | --- | --- |
| Dart 运行时对等物（async、Stream、JSON、DateTime、Uri、http、插件门面） | `:core` foundation / plugins | 完成 |
| Flutter 框架层对等物（ViewState、BuildContext、Navigator、Overlay、Dialog、文本、MediaQuery） | `:app` widgets | 完成 |
| 协议、鉴权、配置 | `:core` protocol / auth / config | 完成（118 个移植用例通过） |
| 业务控制器 | `:core` app | 进行中（无依赖的底层文件已完成） |
| 平台服务（通知、前台服务、FCM、更新安装、存储） | `:app` platform | 完成 |
| LiveKit 语音、摄像头、屏幕共享 | `:app` live | 未开始 |
| 界面组件库 | `:app` ui | 进行中 |
| 主界面、设置、登录 | `:app` home / settings / shell | 未开始 |

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

- MethodChannel 层已删除，原来经通道调用的原生代码改为直接调用。通知渠道 id、通知 id、请求码、
  intent extra、偏好文件名、FileProvider authority 与 Flutter 版保持一致；启动 Activity 仍是
  `com.gangchat.client.MainActivity`。前台服务与 FCM 服务移到 `platform` 包（没有持久化数据引用它们的类名）。
- 存储兼容 Flutter 版的磁盘格式（已对照插件源码并用 Robolectric 构造的数据验证）：
  `shared_preferences_android` 2.4.26（`FlutterSharedPreferences`、`flutter.` 前缀、double/列表编码、
  旧版 Base64 列表、BigInteger）；`flutter_secure_storage` 10.3.1（AES-GCM 数据、RSA-OAEP 包装的密钥、
  旧 PKCS1+CBC 迁移）。Keystore 部分无法在本环境验证；密钥不可用时（例如备份恢复到新设备）
  与插件一样清空存储，用户重新登录一次。未移植应用从未启用过的 EncryptedSharedPreferences 导入和生物识别加密。
- 消息提示音：Flutter 版在 Android 上 audioplayers 的低延迟模式不接受字节源，`play` 一直静默失败；
  原生版保持静音行为（改成可播放只需一行，但会每条消息都抢占音频焦点，暂停其他应用的音乐）。
- 文件路径类、仅 Web 的剪贴板/文件 API 在 Android 上是空操作；`saveBytesAsBrowserDownload` 删除。
- 从通知启动时的房间 id 只能通过 `takeInitialNotificationRoomId()` 取一次，之后的点击走
  `selectedNotificationRoomIds`。
- IO 与 ContentResolver 操作移到 `Dispatchers.IO`（Flutter 宿主里在主线程）。
- 安全存储两次读取都失败时返回 `null`（插件返回字符串 "Data has been reset"）。
- 平板判断读当前窗口尺寸（Flutter 在 `main()` 里读视图尺寸，可能还是 0）。

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
