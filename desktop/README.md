# 纸鸢下载 · 桌面客户端

基于 **Tauri 2** 的多平台「网页壳」应用：窗口内全屏 WebView 加载线上站点，无地址栏。

- 启动地址：https://mybt.kiteyuan.info
- 默认窗口：1200×800，可调整大小
- 标识符：`info.kiteyuan.mybt`

## 命名约定

路径 / 二进制用英文，面向用户的显示名用中文：

| 用途 | 值 |
|------|-----|
| 可执行文件（`mainBinaryName`） | `zhiyuan-download` → `zhiyuan-download.exe` |
| macOS `CFBundleName`（`bundle.macOS.bundleName`） | `zhiyuan-download` |
| Cargo / npm 包名 | `zhiyuan-download` |
| Bundle ID | `info.kiteyuan.mybt` |
| 窗口标题 / 开始菜单与快捷方式显示名（`productName`） | `纸鸢下载` |

> Tauri 2 里 `productName` 同时作为安装器品牌与快捷方式显示名；`mainBinaryName` 只控制主程序文件名。安装包文件名仍会带 `productName`（中文），Windows MSI 因此需要 `wix.language: zh-CN`。

## 环境要求

- [Node.js](https://nodejs.org/) LTS
- [Rust](https://www.rust-lang.org/tools/install) stable
- 平台依赖见 [Tauri 前置条件](https://v2.tauri.app/start/prerequisites/)
  - Windows：WebView2（一般已预装）
  - macOS：Xcode Command Line Tools
  - Linux：`webkit2gtk` 等（与 CI 中 `ubuntu-22.04` 依赖一致）

## 本地开发

```bash
cd desktop
npm install
npm run dev
```

开发模式下会直接打开远程站点（`tauri.conf.json` 中的 `devUrl`）。

## 本地打包

```bash
cd desktop
npm run build
```

产物目录：`desktop/src-tauri/target/release/bundle/`（若设置了 `CARGO_TARGET_DIR` 则以该目录为准）

| 平台 | 常见产物 |
|------|----------|
| Windows | `msi/`（需 `bundle.windows.wix.language: zh-CN`）、`nsis/` |
| macOS | `dmg/`、`macos/` |
| Linux | `deb/`、`appimage/` |
| Android | CI 产出 `.apk`（见下方发版说明） |

仅打 NSIS：`npx tauri build --bundles nsis`

## 用 GitHub Actions 发版

工作流：`.github/workflows/release.yml`（`tauri-apps/tauri-action`）

### 触发方式

1. **打 tag（推荐）**

   ```bash
   # 先把 desktop/src-tauri/tauri.conf.json 与 package.json 的 version 改成一致，例如 0.1.0
   git tag v0.1.0
   git push origin v0.1.0
   ```

2. **手动运行**：GitHub → Actions → Release → Run workflow

### CI 覆盖平台

| Runner | 产物架构 |
|--------|----------|
| `windows-latest` | Windows x64 |
| `ubuntu-22.04` | Linux x64（deb / AppImage 等） |
| `macos-latest` + `aarch64-apple-darwin` | Apple Silicon |
| `macos-latest` + `x86_64-apple-darwin` | Intel Mac |
| `ubuntu-22.04` + `mobile: android` | Android APK（aarch64） |

构建成功后会直接创建 **正式 Release**（非草稿，名称形如「纸鸢下载 v0.1.0」），各平台安装包作为 assets 上传。

> 仓库需允许 Actions 写权限：Settings → Actions → General → Workflow permissions → **Read and write permissions**。

### Android 说明

- CI 中执行 `tauri android init`，不提交 `gen/android`。
- 默认打 `aarch64` APK，便于主流手机侧载。
- **正式签名（推荐）**：在仓库 Secrets 配置：
  - `ANDROID_KEY_BASE64`：keystore 的 base64（`base64 -i upload-keystore.jks`）
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- 未配置 Secrets 时，CI 会用临时 keystore 签名（可安装，但不适合上架 / 长期升级链）。

本地若要开发 Android，需先安装 Android SDK/NDK，并设置 `ANDROID_HOME`、`NDK_HOME`，再执行：

```bash
cd desktop
npm run tauri android init
npm run tauri android dev
```

### 签名 / 公证（可选，未默认启用）

工作流里已用注释标出 Apple 公证与 Tauri updater 签名相关环境变量。需要减少「未知开发者」提示时，再配置对应 Secrets 并取消注释。

## 项目结构

```
desktop/
  package.json              # npm 脚本与 @tauri-apps/cli
  app-icon.png              # 图标源（由仓库根目录 favicon.ico 生成）
  src/index.html            # 本地占位页（正式运行不使用）
  src-tauri/
    tauri.conf.json         # 窗口、远程 URL、打包配置
    capabilities/           # 远程域名与权限
    icons/                  # 各平台图标
    src/                    # Rust 入口
.github/workflows/release.yml
```

远程 URL / CSP / capabilities 要点：

- `build.frontendDist` / `build.devUrl` = `https://mybt.kiteyuan.info`
- `app.security.csp` = `null`（避免干扰线上站点）
- `capabilities/default.json` 的 `remote.urls` 允许 `*.kiteyuan.info`

## 明确不做

- 不是 Electron
- 不把整站离线打进安装包（仅在线加载）
- 不在首版做应用商店上架与复杂公证（Android / macOS 均可后续补签名）
