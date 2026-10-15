# Web 版可行性 Spike

目标：判断 Gang Chat 客户端编译成 Web 的难度和会出多少问题。本分支只做了让它
能编译的最小改动，并在 Chrome 里跑到了登录页；登录后的功能没有测试账号，靠
静态审计判断。

## 结论

- **编译**：容易。`flutter build web` 原本只有 6 个错误，本分支已修复，现在可以
  构建并在浏览器里显示登录 / 注册页。
- **运行**：问题集中在"本地文件"和"自有原生通道"两类代码上。文字聊天、实时
  事件、语音频道大概率能用；**头像 / 图片 / 表情包、文件下载、语音消息录制、
  试听、外部链接会坏**，需要 Web 专用实现。
- **国内访问**：CanvasKit 可以自托管，但中文回落字体默认仍从
  `fonts.gstatic.com` 下载，国内会显示方块，必须处理。
- **工作量**：做到"聊天 + 语音可用"约 2 周；接近桌面版完整功能约 3–4 周
  （含跨浏览器测试）。

## 本分支的改动

| 改动 | 说明 |
| --- | --- |
| `lib/src/app/fnv_hash.dart` | 64 位 FNV-1a 改用 `BigInt` 实现。原来的 `0xcbf29ce484222325` 等字面量超出 JS 数字精度，Web 编译失败。新实现与原生 `int` 版本输出逐字节一致（含媒体缓存键的负号），磁盘上已有的缓存不会失效；`test/unit/app/fnv_hash_test.dart` 在 VM 和 Chrome 下都通过。 |
| `music_track_preview.dart`、`media_cache_controller.dart` | 改用上面的共享实现。 |
| `third_party/flutter_webrtc/lib/src/web/factory_impl.dart` | 补上 fork 只在原生端加的 `createScreenAudioTrack` / `createScreenAudioPeerConnection`，Web 上直接抛 `UnsupportedError`。`live_session.dart` 启动屏幕音频时本来就 `catchError`，只会记日志。 |

## 实测

构建命令（必须带 `--no-web-resources-cdn`，否则 CanvasKit 从 gstatic 加载）：

```bash
flutter build web --release --no-web-resources-cdn
```

| 项 | 结果 |
| --- | --- |
| 构建 | 成功。wasm 试编译提示 `flutter_webrtc` 的 Web 代码有 JS interop 兼容问题，所以先用默认的 JS 编译，不开 `--wasm`。 |
| 首屏下载量 | `main.dart.js` 4.6MB（gzip 1.3MB）+ CanvasKit：Chrome 用 chromium 变体 5.2MB（gzip 2.0MB），其他浏览器 6.9MB（gzip 2.8MB）+ 拉丁字体约 0.75MB。服务器必须开 gzip / brotli。 |
| 登录 / 注册页 | 正常渲染，切换标签无异常，控制台无报错。 |
| 布局 | 登录卡片贴在页面顶部：桌面端会把窗口缩到卡片大小，卡片本身是顶部对齐的。Web 上需要改成居中。 |
| 中文字体 | 能显示，但 Noto Sans SC 是从 `fonts.gstatic.com` 按需下载的（已在网络请求里确认）；`--no-web-resources-cdn` 只把 Roboto 打包到本地。 |
| API 跨域 | 从 `http://localhost:4174` 带 `Authorization` 头请求生产 API，预检通过并返回 401，说明服务端 CORS 已放行。资源域名 / 对象存储的 CORS 未验证（CanvasKit 加载图片需要）。 |

## 按功能的状态

"已验证"指本次在浏览器里实际跑过；其余为静态审计结论。

| 功能 | 状态 | 依据 |
| --- | --- | --- |
| 登录 / 注册界面 | ✅ 已验证 | 见上 |
| API 调用、登录态 | 🟡 大概率可用 | `http` 1.6.0 的 `BrowserClient` 基于 fetch；`flutter_secure_storage` 支持 Web（需要 HTTPS 安全上下文） |
| 实时事件流（SSE） | 🟡 大概率可用 | `BrowserClient` 用 `ReadableStream` 流式读取，`live_stream_client.dart` 的 `client.send` 可以逐块收到事件 |
| 房间列表、文字消息 | 🟡 大概率可用 | 纯 Dart UI + API |
| 附件上传 | 🟡 大概率可用 | `file_selector` 的 `XFile.readAsBytes` + `MultipartFile.fromBytes` |
| 语音频道、摄像头 | 🟡 大概率可用 | LiveKit 的主平台就是浏览器；`live_session.dart` 已有 16 处 `kIsWeb` 判断 |
| 音乐盒收听 | 🟡 大概率可用 | 是 LiveKit 音频轨；Safari 自动播放限制可能需要用户点一下 |
| 头像、聊天图片 | ❌ 会坏 | `cached_asset_image.dart` → `MediaCacheController` 用 `path_provider` + `File` + `Image.file`，Web 都不支持 |
| 表情包 | ❌ 会坏 | `sticker_pack_store.dart` 用 `File` / `Directory` |
| 文件下载、图片另存 | ❌ 会坏 | `file_downloads_controller.dart`、`home_shell_image_preview.dart` 写本地文件；需要改成浏览器下载 |
| 语音消息录制 | ❌ 会坏 | `voice_recorder_controller.dart` 用 `path_provider` + `File`；`record` 在 Web 上产出的是 blob |
| 试听 | ❌ 会坏 | `music_track_preview_service.dart` 有 5 处未加 `kIsWeb` 的 `Platform.is*`，并写临时文件 |
| 打开外部链接、反馈邮件 | ❌ 会坏 | `external_uri_launcher.dart`、`feedback_mail_service.dart` 用 `Platform.is*` + `Process.run`；需要 `url_launcher` 或 `window.open` |
| 提示音、上线播报 | ⚠️ 部分处理 | 写临时文件播放；各有 1 处 `kIsWeb` 判断，需逐条确认 |
| 屏幕共享画面 | ⚠️ 需改 | 桌面端用 `desktopCapturer.getSources`（Web 上 `UnimplementedError`）；浏览器应走 `getDisplayMedia` |
| 屏幕共享声音 | ⚠️ 需改 | 原生隔离工厂不存在；浏览器通过 `getDisplayMedia` 的音频选项提供 |
| 音频设备选择 | ⚠️ 需改 | `system_audio_devices.dart` 是原生通道；Web 用 `enumerateDevices` / `setSinkId`（Safari 支持有限） |
| 剪贴板（粘贴歌曲 / 歌单组件） | ⚠️ 部分处理 | `clipboard_service.dart` 已有 5 处 `kIsWeb`；浏览器剪贴板需要权限 |
| 文件拖放 | ⚠️ 已在 Web 关闭 | `file_drop_service.dart` 有 `kIsWeb` 判断；需要 Web 拖放区才能用 |
| 窗口标题栏、窗口尺寸 | ➖ 不适用 | 大多已按 `kIsWeb` 跳过；登录页布局见上 |
| 自动更新、安装信息 | ➖ 必须关闭 | `release_update_service.dart`、`app_update_gate.dart`、`install_info_service.dart` |
| 推送（FCM） | ➖ 不适用 | 仅 Android；Web 需另做 Notification API / Web Push |

## 要做的事（估算）

| # | 事项 | 估算 |
| --- | --- | --- |
| 1 | 中文字体：自托管 Noto Sans SC 分片并设置 `fontFallbackBaseUrl`，或打包一个中文字体 | 0.5–1 天 |
| 2 | 媒体管线：Web 上头像 / 图片 / 表情包改走网络 + 内存缓存（`Image.network` 或 `Image.memory`），确认资源域名 CORS | 1–2 天 |
| 3 | 下载与另存：Blob + `<a download>` | 0.5–1 天 |
| 4 | 外部链接、反馈邮件：`url_launcher` | 0.5 天 |
| 5 | 语音消息录制与播放 | 1 天 |
| 6 | 屏幕共享：Web 走 `getDisplayMedia`（含声音） | 1 天 |
| 7 | 试听：Web 用 Blob URL 播放 | 0.5–1 天 |
| 8 | 提示音、上线播报：改为资源 / 内存播放 | 0.5 天 |
| 9 | 关闭自动更新与安装信息；登录页居中；隐藏窗口控件 | 0.5 天 |
| 10 | 部署：HTTPS、gzip / brotli、缓存策略、服务端 CORS 白名单加入 Web 域名 | 0.5–1 天 |
| 11 | 跨浏览器测试：Chrome / Edge / Safari / Firefox，中文输入法、右键菜单与快捷键冲突、自动播放 | 2–3 天 |

1–4 和 9–10 做完就能得到一个"聊天可用"的 Web 版；全部完成约 2–3 周，再加缓冲约 4 周。

## 部署

Web 版放在 `https://ky-z.com/gang-chat/app/`，与 API（`/gang-chat/api/v1`）同源，
所以不需要跨域配置，并直接使用站点已有的 HTTPS（麦克风、摄像头、屏幕共享都要求
HTTPS）。

1. **nginx**：把 `deploy/web/nginx-gang-chat-app.conf` 里的两个 `location` 块放进
   ky-z.com 已有的 `server { ... }`，确认 `mime.types` 里有 `wasm`，然后
   `sudo nginx -t && sudo systemctl reload nginx`。
2. **服务器目录**：`/var/www/gang-chat-app`，部署用户可写、nginx 可读。
3. **构建并部署**：

   ```bash
   GANG_WEB_DEPLOY_SSH=<user>@<host> tool/deploy_web.sh
   ```

   脚本先调用 `tool/build_web.sh`（`--no-web-resources-cdn --base-href /gang-chat/app/`），
   再用 rsync 上传到 `releases/<UTC 时间戳>/`，最后原子切换 `current` 软链接，
   默认保留最近 5 个版本以便回滚。加 `GANG_WEB_DEPLOY_DRY_RUN=1` 只打印命令。

在上面"要做的事"完成之前，不要把这个地址公开给用户；可以先作为内部测试地址，
登录后实际检查各功能。

## 本次没有验证的

- nginx 配置没有实际运行过（本机没有 nginx）；上线前先 `nginx -t`。
- 部署脚本没有对真实服务器运行过；远端的切换和清理逻辑在本地模拟验证过。

- 登录后的所有功能（没有测试账号；不能往生产服务器提交真实密码）。
- 资源域名和对象存储的 CORS。
- Safari / Firefox。
- 国内网络下的实际加载。

## 复现

```bash
flutter build web --release --no-web-resources-cdn
python3 -m http.server 4174 --directory build/web
```

打开 `http://localhost:4174`。注意 `flutter build` / `flutter test` 会改写
`analysis_options.yaml` 并可能升级 `pubspec.lock`，提交前要还原。
