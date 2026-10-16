# Gang Chat 桌面端（Electron）

Windows / macOS 桌面客户端，从 Flutter 版一比一移植而来。Android 暂时仍用仓库根目录的
Flutter 工程。

| 目录 | 内容 |
| --- | --- |
| `packages/core` | 协议、鉴权、业务控制器、LiveKit 会话（`livekit-client`）、平台服务；不依赖界面，未来 React Native 移动端直接复用 |
| `packages/ui` | React（DOM）界面，按 Flutter Widget 一比一移植 |
| `apps/desktop` | Electron 主进程、preload、渲染进程入口和 `Platform` 接口的桌面实现 |
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
npm run dist:win -w @gang-chat/desktop    # GangChat_v<version>.exe + GangChat-<version>-windows.zip
npm run dist:mac -w @gang-chat/desktop    # GangChat_v<version>.dmg + GangChat-<version>-mac.zip（更新用）
```

版本号取 `apps/desktop/package.json` 的 `version`。CI 工作流 `.github/workflows/desktop-electron.yml`
在每次推送时做类型检查、单元测试，并打出未签名的 Windows / macOS 测试包（作为 workflow artifact
下载）。

签名：Windows 用 electron-builder 的 `CSC_LINK` / `CSC_KEY_PASSWORD`（与 Flutter 版同一张 PFX）；
macOS 需要 Developer ID 签名和公证，签名后构建时设置 `GANG_MAC_UPDATES_SIGNED=1` 才会启用应用内
自动更新（Squirrel.Mac 只接受已签名应用的更新）。

## 应用内更新

使用 `electron-updater`，读取存储桶 `releases/` 下的 `latest.yml` / `latest-mac.yml`。发布时需要把
安装包、`latest*.yml`、`*.blockmap` 和 macOS 的 zip 一起上传到 `releases/`。正式发布流程
（`.github/workflows/release.yml`）目前仍发布 Flutter 桌面版，切换到 Electron 是后续工作。
