# Web 版

Gang Chat 客户端的 Web 版：从可行性 spike 开始，现已补齐桌面端的主要功能。
"已验证"指在浏览器里实际跑过；"已实现"指代码与自动化测试已完成，但还需要在
真实浏览器里、登录后手动确认。

## 结论

- **编译与运行**：`flutter build web` 可以构建，登录、房间列表、头像在 Chrome
  里已验证可用。
- **功能**：桌面端用到本地文件、原生通道的功能都已换成浏览器实现（见下表）；
  仍不支持的只剩浏览器本身做不到的：复制文件路径、系统推送。
- **国内访问**：CanvasKit 和中文 / emoji 回落字体都随应用一起托管，加载时不再
  访问任何 Google 域名（已在网络请求里确认）。
- **还要做**：在 Chrome / Edge / Safari / Firefox 里登录后逐项手动检查，见
  "本次没有验证的"。

## 实现方式

- **平台判断**：所有 `dart:io` 的 `Platform` 调用都经过
  `lib/src/config/host_platform.dart`，Web 上不会抛错。
- **浏览器 API**：`lib/src/shell/browser/browser_bridge.dart` 按平台条件导入，
  Web 用 `package:web` 实现（打开链接、语音合成、剪贴板图片、文件拖放、粘贴），
  原生端是空实现；调用方都先判断 `kIsWeb`。
- **媒体**：`MediaCacheController.getOrDownloadBytes` 在 Web 上下载到有上限
  （64MB）的内存缓存，原生端照旧走磁盘缓存；图片用 `MemoryImage` 显示。
- **fork 的 LiveKit**：远端音轨音量、输出设备在 Web 上作用于 `<audio>` 元素。
- **字体**：`tool/mirror_web_fonts.dart` 在构建后把中文、emoji、符号回落字体
  下载到 `build/web/fonts/gstatic/`，并设置 `fontFallbackBaseUrl`；任何下载失败
  都不会切换过去，继续用 gstatic。

## 构建体积

| 项 | 大小 |
| --- | --- |
| `main.dart.js` | 4.6MB（gzip 1.3MB） |
| CanvasKit | Chrome 用 chromium 变体 5.2MB（gzip 2.0MB），其他浏览器 6.9MB（gzip 2.8MB） |
| 拉丁字体 | 约 0.75MB |
| 中文 / emoji 回落字体 | 约 4.8MB 放在服务器上，按需加载用到的分片 |

服务器必须开 gzip / brotli（`deploy/web/nginx-gang-chat-app.conf` 已配置）。
wasm 试编译提示 `flutter_webrtc` 的 Web 代码有 JS interop 兼容问题，所以用默认的
JS 编译，不开 `--wasm`。

## 按功能的状态

| 功能 | 状态 | Web 上的做法 |
| --- | --- | --- |
| 登录 / 注册 | ✅ 已验证 | 卡片在页面中居中（桌面端仍是窗口缩到卡片大小） |
| API、登录态、实时事件（SSE） | ✅ 已验证 | `http` 的 `BrowserClient` 基于 fetch 流式读取；服务端 CORS 已放行 |
| 房间列表、头像 | ✅ 已验证 | 内存媒体缓存 + `MemoryImage`；对象存储返回 `Access-Control-Allow-Origin: *` |
| 中文 / emoji 字体 | ✅ 已验证 | 随应用托管，无 Google 请求 |
| 聊天图片、表情包、图片预览 | 🟢 已实现 | 同头像的媒体管线 |
| 附件上传 | 🟢 已实现 | `XFile.readAsBytes` + `MultipartFile.fromBytes` |
| 文件下载、图片下载 / 另存为 | 🟢 已实现 | 先下载到内存（带进度、暂停、取消），再交给浏览器下载 |
| 语音频道、摄像头 | 🟢 已实现 | LiveKit 浏览器实现 |
| 音量（音乐盒、语音输出、屏幕共享） | 🟢 已实现 | 设置 `<audio>` 元素音量 |
| 屏幕共享（含声音） | 🟢 已实现 | 仅电脑浏览器；浏览器自己的 `getDisplayMedia` 选择框，声音随同一流发布 |
| 输入 / 输出设备 | 🟢 已实现 | 输入随麦克风重新采集；输出用 `setSinkId`（Safari 不支持，保持系统输出） |
| 音乐盒收听 | 🟢 已实现 | LiveKit 音频轨；Safari 可能需要点一下才出声 |
| 试听 | 🟢 已实现 | 内存缓存（64MB）+ `BytesSource` |
| 语音消息播放 | 🟢 已实现 | `UrlSource` 直接播放 |
| 语音消息录制 | 🟢 已实现 | 支持 AAC 的浏览器录 m4a，否则录 16kHz 单声道 WAV（所有客户端都能播，服务端已接受） |
| 提示音、上线提示音 | 🟢 已实现 | `BytesSource` |
| AI 上线播报 | 🟢 已实现 | 浏览器 `speechSynthesis`（需要系统有中文语音） |
| 打开链接、反馈邮件 | 🟢 已实现 | `window.open` / `mailto:` |
| 复制图片 | 🟢 已实现 | 异步剪贴板，非 PNG 先转成 PNG |
| 粘贴图片 / 文件 / 歌曲歌单组件 | 🟢 已实现 | 监听页面 `paste` 事件（Web 的 Ctrl+V 不经过 Flutter 的粘贴动作），仅在输入框有焦点时接管 |
| 拖放文件到输入框 | 🟢 已实现 | 监听页面 `dragover` / `drop`，同时阻止浏览器打开被拖入的文件 |
| 复制文件路径到剪贴板 | ➖ 不支持 | 浏览器拿不到本地路径 |
| 自动更新、安装信息 | ➖ 不适用 | 操作系统为 `web`，更新检查自动关闭 |
| 窗口标题栏、托盘 | ➖ 不适用 | 已按 `kIsWeb` 跳过 |
| 系统推送（FCM） | ➖ 不支持 | 仅 Android；Web 推送需要另做 Web Push |

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

### 随发版自动部署

`.github/workflows/release.yml` 的 `deploy-web` 任务在每次发版（推送 `v*` 标签
或手动运行 release）、GitHub Release 发布成功之后运行：写入版本号，调用
`tool/build_web.sh` 构建，再用 `tool/deploy_web.sh --no-build` 部署；同一时间
只允许一个部署。与桌面签名一样：三个部署 Secret 都没配置时跳过并在任务摘要
里提示；只配了一部分时任务失败。

首次使用前：

1. **生成专用部署密钥**（不要复用个人密钥）：

   ```bash
   ssh-keygen -t ed25519 -N "" -C gang-chat-web-deploy -f gang_chat_web_deploy
   ```

   把 `gang_chat_web_deploy.pub` 的内容追加到服务器上部署用户的
   `~/.ssh/authorized_keys`。
2. **取得服务器主机公钥**，在自己电脑上运行（端口不是 22 时加 `-p <端口>`）：

   ```bash
   ssh-keyscan -t ed25519,rsa ky-z.com
   ```

   核对输出与服务器 `/etc/ssh/ssh_host_*.pub` 一致后再使用。
3. **在仓库 Settings → Secrets and variables → Actions 中添加**：

   | 名称 | 类型 | 内容 |
   | --- | --- | --- |
   | `WEB_DEPLOY_SSH` | Secret | 部署目标，例如 `deploy@ky-z.com` |
   | `WEB_DEPLOY_SSH_KEY` | Secret | `gang_chat_web_deploy` 私钥全文 |
   | `WEB_DEPLOY_KNOWN_HOSTS` | Secret | 第 2 步 `ssh-keyscan` 的输出 |
   | `WEB_DEPLOY_SSH_PORT` | Variable，可选 | SSH 端口，默认 22 |
   | `WEB_DEPLOY_DIR` | Variable，可选 | 远端目录，默认 `/var/www/gang-chat-app` |

   主机公钥只来自 `WEB_DEPLOY_KNOWN_HOSTS`，服务器主机密钥变化时部署会失败
   而不是自动信任。
4. 删除本地的私钥文件。

在浏览器里登录后把"本次没有验证的"逐项确认之前，先把这个地址作为内部测试地址，
不要公开给用户。

## 本次没有验证的

- 登录后需要手动检查的：上表"已实现"的各项（浏览器面板不允许麦克风和屏幕
  采集，下载 / 剪贴板也应在用户自己的浏览器里确认）。
- Safari / Firefox：`setSinkId`、AAC 录音、异步剪贴板读取支持程度不同，代码
  已按不支持时退回处理，但未实测。
- 国内网络下的实际加载。
- nginx 配置没有实际运行过（本机没有 nginx）；上线前先 `nginx -t`。
- 部署脚本没有对真实服务器运行过；远端的切换和清理逻辑在本地模拟验证过。
- `release.yml` 的 `deploy-web` 任务没有实际运行过。

## 复现

```bash
tool/build_web.sh
```

把 `build/web` 放在 `/gang-chat/app/` 路径下访问，例如：

```bash
mkdir -p /tmp/gang-web/gang-chat && ln -sfn "$PWD/build/web" /tmp/gang-web/gang-chat/app && python3 -m http.server 4176 --directory /tmp/gang-web
```

然后打开 `http://localhost:4176/gang-chat/app/`。浏览器专用的测试：

```bash
flutter test --platform chrome test/unit/shell/browser_bridge_web_test.dart test/unit/app/media_cache_web_test.dart test/unit/live/web_screen_share_support_test.dart test/unit/app/fnv_hash_test.dart
```

注意 `flutter build` / `flutter test` 会改写 `analysis_options.yaml`，`tool/pub.sh`
会把 `pubspec.lock` 的源地址改成镜像，提交前要还原。
