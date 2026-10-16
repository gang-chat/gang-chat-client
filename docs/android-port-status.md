# Android 原生移植状态

Android 客户端正在从 Flutter 一比一移植为原生 Kotlin + Jetpack Compose，代码在 `android/`。
移植约定见 `docs/android-port-conventions.md`。本文记录各模块进度，以及与 Flutter 版的差异。

## 进度

| 范围 | 模块 | 状态 |
| --- | --- | --- |
| Dart 运行时对等物（async、Stream、JSON、DateTime、Uri、http、插件门面） | `:core` foundation / plugins | 完成 |
| Flutter 框架层对等物（ViewState、BuildContext、Navigator、Overlay、Dialog、文本、MediaQuery） | `:app` widgets | 完成 |
| 协议、鉴权、配置 | `:core` protocol / auth / config | 进行中 |
| 业务控制器 | `:core` app | 未开始 |
| 平台服务（通知、前台服务、FCM、更新安装、存储） | `:app` platform | 进行中 |
| LiveKit 语音、摄像头、屏幕共享 | `:app` live | 未开始 |
| 界面组件库 | `:app` ui | 进行中 |
| 主界面、设置、登录 | `:app` home / settings / shell | 未开始 |

## 与 Flutter 版的差异

（各模块移植时补充。）
