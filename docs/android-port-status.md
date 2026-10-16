# Android 原生移植状态

Android 客户端正在从 Flutter 一比一移植为原生 Kotlin + Jetpack Compose，代码在 `android/`。
移植约定见 `docs/android-port-conventions.md`。本文记录各模块进度，以及与 Flutter 版的差异。

## 进度

| 范围 | 模块 | 状态 |
| --- | --- | --- |
| Dart 运行时对等物（async、Stream、JSON、DateTime、Uri、http、插件门面） | `:core` foundation / plugins | 完成 |
| Flutter 框架层对等物（ViewState、BuildContext、Navigator、Overlay、Dialog、文本、MediaQuery） | `:app` widgets | 完成 |
| 协议、鉴权、配置 | `:core` protocol / auth / config | 进行中 |
| 业务控制器 | `:core` app | 进行中（无依赖的底层文件已完成） |
| 平台服务（通知、前台服务、FCM、更新安装、存储） | `:app` platform | 进行中 |
| LiveKit 语音、摄像头、屏幕共享 | `:app` live | 未开始 |
| 界面组件库 | `:app` ui | 进行中 |
| 主界面、设置、登录 | `:app` home / settings / shell | 未开始 |

## 与 Flutter 版的差异

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
