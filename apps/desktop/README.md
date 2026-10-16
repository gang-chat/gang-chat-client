# Gang Chat 桌面端（Electron）

Windows / macOS 桌面客户端，从 Flutter 版一比一移植而来。Android 暂时仍用仓库根目录的
Flutter 工程。

| 目录 | 内容 |
| --- | --- |
| `packages/core` | 协议、鉴权、业务控制器、LiveKit 会话（`livekit-client`）、平台服务；不依赖界面，未来 React Native 移动端直接复用 |
| `packages/ui` | React（DOM）界面，按 Flutter Widget 一比一移植 |
| `apps/desktop` | Electron 主进程、preload、渲染进程入口和 `Platform` 接口的桌面实现 |
| `apps/web` | Web 版：同一套界面跑在浏览器里，`Platform` 接口的浏览器实现，见 `docs/web-spike.md` |
| `tool/js/mock_server` | 本地模拟服务端，用于没有真实服务器时联调 |

移植约定见 `docs/js-port-conventions.md`，与 Flutter 版的差异见 `docs/js-port-status.md`，
运行截图在 `docs/js-port-screenshots/`。

## 开发

需要 Node 22。在仓库根目录：

```bash
npm install                 # 安装所有 workspace 依赖
npm run dev                 # 启动 Electron，渲染进程热更新
npm run typecheck           # core + ui + desktop 的 TypeScript 检查
npm test                    # vitest 单元测试
```

服务器地址默认取 `assets/config/app_config.json`（与 Flutter 版共用）。构建时可用环境变量覆盖，
对应 Flutter 的 `--dart-define`：

```bash
GANG_API_BASE_URL=http://127.0.0.1:21116/api/v1 \
GANG_ASSET_BASE_URL=http://127.0.0.1:21116 \
npm run dev
```

### 用模拟服务端联调

```bash
node tool/js/mock_server/server.mjs --port 21116
```

然后按上面的环境变量启动；账号 `demo` / `demo1234`，验证码固定 `123456`。语音频道会按预期
连不上媒体服务器（模拟服务端没有 LiveKit）。

## 打包

```bash
npm run dist:win -w @gang-chat/desktop    # GangChat_v<version>.exe（+ .blockmap、latest.yml）+ GangChat-<version>-windows.zip
npm run dist:mac -w @gang-chat/desktop    # GangChat_v<version>.dmg
```

版本号取 `apps/desktop/package.json` 的 `version`；macOS 的 `CFBundleVersion` 由
`scripts/package.mjs` 算成 `主*1000000 + 次*1000 + 补丁`，和 Flutter macOS 版、Android 一致。
macOS 打包前 `scripts/prepare_sparkle.mjs` 会下载固定版本、校验过的 Sparkle 并编译
`native/sparkle/sparkle.mm`（需要 Xcode 命令行工具）。CI 工作流
`.github/workflows/desktop-electron.yml` 在每次推送时做类型检查、单元测试，并打出测试包；
正式发布由 `.github/workflows/release.yml` 打包桌面版（见 `docs/release-workflow.md`）。

签名：Windows 用 electron-builder 的 `CSC_LINK` / `CSC_KEY_PASSWORD`（与 Flutter 版同一张 PFX）。
macOS 有 Developer ID 时由 electron-builder 签名并公证；没有时 `scripts/after_pack.cjs` 做 ad-hoc
签名——Sparkle 不接受代码签名无效的更新，而未签名的 Electron 包只带一个失效的链接器签名。

## 应用内更新

- Windows：`electron-updater` 读取存储桶 `releases/` 下的 `latest.yml`，按 blockmap 差分下载安装包。
  桶里没有 `latest.yml` 时退回 Flutter 版的做法：列出 `releases/` 里的安装包，下载最新的整包。
- macOS：Sparkle（`src/main/sparkle.ts`），和 Flutter macOS 版读同一个 `releases/appcast.xml`、
  用同一把 EdDSA 公钥，不需要 Developer ID。Sparkle 自己弹窗、下载、校验、安装并重启应用；
  Flutter 用户也经由同一个 appcast 升级到 Electron 版。开发版没有 Sparkle，退回在浏览器里下载 DMG。
