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
| 平台服务（通知、前台服务、FCM、更新安装、存储） | `:app` platform | 进行中 |
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
