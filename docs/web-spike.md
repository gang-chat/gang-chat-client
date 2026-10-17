# Web 版

Gang Chat 的 Web 版放在 `https://ky-z.com/gang-chat/app/`。它和 Electron 桌面端用同一套
React 界面（`packages/ui`）和逻辑层（`packages/core`），页面壳在 `apps/web`；原来的 Flutter
Web 构建已被替换，Flutter 工程也已从仓库移除。

"已验证"指在 Chromium 里对本地模拟服务端（`tool/js/mock_server`）和本地 LiveKit 服务器
实际跑过；"已实现"指代码已完成，但还需要在真实浏览器 / 真实服务器上手动确认。运行截图在
`docs/js-port-screenshots/web_*.png`。

## 实现方式

- **平台接口**：`apps/web/src/platform/` 用浏览器 API 实现 `Platform`，`os` 为 `web`。共享代码
  里从 Dart 移植来的 `kIsWeb` 分支照旧生效：没有窗口、托盘和标题栏按钮，没有应用内更新，
  屏幕共享用浏览器自己的选择框，下载交给浏览器。
- **存储**：与 Flutter Web 版同格式（`flutter.<键>` 的 JSON 偏好设置，`flutter_secure_storage_web`
  格式加密的刷新令牌）。同一域名下从 Flutter 版切换过来，用户不用重新登录，设置也保留。
- **HTTP**：`fetch`。API 与页面同源，不需要 CORS；SSE 和下载流式读取。请求体先收集完整再
  发送（与 Flutter 的 `BrowserClient` 相同），所以上传进度会先到 100% 再等服务器响应。
- **粘贴 / 拖放**：页面级 `paste` 监听直接从粘贴事件里取文件和截图（不读异步剪贴板，不会弹
  权限框），文字粘贴交给浏览器；拖放用页面级 `dragover` / `drop`，并阻止浏览器打开被拖入的文件。
- **字体**：Doto / JetBrains Mono / Space Grotesk 和 Material 图标字体随页面打包；中文和 emoji
  用系统字体，不再需要从 Google 加载回落字体（原来的 `tool/mirror_web_fonts.dart` 已删除）。
- **旧的 Service Worker**：Flutter 版可能在用户浏览器里注册过 `flutter_service_worker.js`。新构建
  在同一路径放了一个只做清理的版本：浏览器更新到它时删除 Flutter 的缓存、注销自己并刷新页面。

## 构建体积

| 项 | React 版 | Flutter 版 |
| --- | --- | --- |
| JS | 2.3MB（gzip 0.62MB） | `main.dart.js` 4.6MB（gzip 1.3MB） |
| 渲染引擎 | 无（浏览器 DOM） | CanvasKit 5.2–6.9MB（gzip 2.0–2.8MB） |
| 字体 | 拉丁字体约 0.6MB，图标字体按需加载 | 拉丁字体约 0.75MB，中文 / emoji 回落字体约 4.8MB |

## 按功能的状态

| 功能 | 状态 | Web 上的做法 |
| --- | --- | --- |
| 登录 / 注册、记住账号 | ✅ 已验证 | 卡片在页面中居中 |
| 刷新页面保持登录 | ✅ 已验证 | 刷新令牌按 Flutter 格式加密存在 `localStorage` |
| 读取 Flutter 版留下的登录态和设置 | ✅ 已验证（单元测试） | `apps/web/test/web_storage.test.ts` 用独立的 AES-GCM 实现生成 Flutter 格式的数据 |
| API、实时事件（SSE） | ✅ 已验证 | `fetch` 流式读取 |
| 房间列表、聊天、发送消息 | ✅ 已验证 | |
| 粘贴截图 / 文件为附件 | ✅ 已验证 | 页面级 `paste` 监听 |
| 粘贴文字 | ✅ 已验证 | 浏览器默认粘贴，不弹剪贴板权限 |
| 拖放文件到输入框 | ✅ 已验证 | |
| 语音频道（麦克风收发） | ✅ 已验证 | 两个浏览器用户经本地 LiveKit 互相订阅到对方的麦克风 |
| 摄像头 | ✅ 已验证 | 对方点开后收到 640×360 画面；与 Flutter 一样，别人的摄像头只在观看时订阅 |
| 屏幕共享（含声音） | ✅ 已验证 | 浏览器 `getDisplayMedia` 选择框，画面和声音随同一流发布，对方收到画面和声音轨 |
| 语音消息录制并发送 | ✅ 已验证 | 不支持 AAC 录音的浏览器录 16kHz 单声道 WAV（Chromium 无专有编解码器时即走此路径） |
| 语音和视频设置（设备列表、测试） | ✅ 已验证 | 输出设备用 `setSinkId`（Safari 不支持，保持系统输出） |
| 手机浏览器布局 | ✅ 已验证 | 页面窄于 720 时用紧凑布局（单栏 + 返回）；只有 Web 版这样做，桌面端窗口最小 960×600 |
| 清理 Flutter 版的 Service Worker | ✅ 已验证 | 模拟一个缓存了页面的旧 worker，部署后一次访问即清除并切到新版本 |
| 图片预览、表情包、文件下载、图片另存为 | 🟢 已实现 | 下载到内存后交给浏览器下载 |
| 复制图片 | 🟢 已实现 | 异步剪贴板，非 PNG 先在 canvas 上转成 PNG |
| 右键菜单"粘贴"图片 | 🟢 已实现 | `navigator.clipboard.read()`，浏览器可能询问一次权限 |
| 音乐盒收听、试听、提示音 | 🟢 已实现 | `<audio>` / Web Audio；Safari 可能需要先点一下页面才出声 |
| AI 上线播报 | 🟢 已实现 | `speechSynthesis`（需要系统有中文语音） |
| 打开链接、反馈邮件 | 🟢 已实现 | 新标签页 / `mailto:` |
| 复制文件路径到剪贴板 | ➖ 不支持 | 浏览器拿不到本地路径 |
| 自动更新、安装信息 | ➖ 不适用 | 网页总是最新部署的版本；"关于"页与 Flutter Web 版一样仍显示检查更新按钮，点了提示当前平台不支持 |
| 窗口标题栏、托盘 | ➖ 不适用 | |
| 系统推送 | ➖ 不支持 | Web 推送需要另做 Web Push |

## 部署

Web 版放在 `https://ky-z.com/gang-chat/app/`，与 API（`/gang-chat/api/v1`）同源，
所以不需要跨域配置，并直接使用站点已有的 HTTPS（麦克风、摄像头、屏幕共享都要求
HTTPS）。

1. **nginx**：把 `deploy/web/nginx-gang-chat-app.conf` 里的 `location` 块放进
   ky-z.com 已有的 `server { ... }`，然后 `sudo nginx -t && sudo systemctl reload nginx`。
   之前为 Flutter 版配置的两个块继续可用；新增的 `/gang-chat/app/assets/` 块让带哈希的
   文件长期缓存。
2. **服务器目录**：`/var/www/gang-chat-app`，部署用户可写、nginx 可读。
3. **构建并部署**：

   ```bash
   GANG_WEB_DEPLOY_SSH=<user>@<host> tool/deploy_web.sh
   ```

   脚本先调用 `tool/build_web.sh`（`vite build`，base `/gang-chat/app/`，输出 `build/web`），
   再用 rsync 上传到 `releases/<UTC 时间戳>/`，最后原子切换 `current` 软链接，
   默认保留最近 5 个版本以便回滚。加 `GANG_WEB_DEPLOY_DRY_RUN=1` 只打印命令。

### 随发版自动部署

`.github/workflows/release.yml` 的 `deploy-web` 任务在每次发版（推送 `v*` 标签
或手动运行 release）、GitHub Release 发布成功之后运行：`npm ci`，带上版本号和发布时间
（`GANG_CHAT_VERSION`、`GANG_CHAT_RELEASED_AT`）调用 `tool/build_web.sh` 构建，再用
`tool/deploy_web.sh --no-build` 部署；同一时间
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

- 真实服务器：所有验证都对着本地模拟服务端和本地 `livekit-server --dev`。上线前在
  `https://ky-z.com/gang-chat/app/` 的测试部署上登录走一遍。
- Safari / Firefox：只在 Chromium 里跑过。`setSinkId`、AAC 录音、异步剪贴板在这两个浏览器上
  支持程度不同，代码已按不支持时退回处理。
- 从 Flutter 版切换：存储格式由单元测试保证，但还没有在同一浏览器里先登录 Flutter 版、再部署
  React 版实际确认。
- 国内网络下的实际加载、nginx 新配置（本机没有 nginx，上线前先 `nginx -t`）、`release.yml`
  的 `deploy-web` 任务（需要发版时才运行）。

## 本地运行

```bash
npm install
npm run dev:web                # http://localhost:4176/gang-chat/app/，热更新
```

对着模拟服务端（账号 `demo` / `demo1234`）：

```bash
node tool/js/mock_server/server.mjs --port 21116
GANG_API_BASE_URL=http://127.0.0.1:21116/api/v1 GANG_ASSET_BASE_URL=http://127.0.0.1:21116 npm run dev:web
```

要连通语音，另开一个 `livekit-server --dev`，并让模拟服务端用开发密钥签发令牌：
`--livekit-api-key devkey --livekit-api-secret secret`（见 `tool/js/mock_server/README.md`）。

生产构建：

```bash
tool/build_web.sh              # 输出 build/web，base /gang-chat/app/
npm run preview -w @gang-chat/web
```
