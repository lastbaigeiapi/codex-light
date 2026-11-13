# Codex Light Android 客户端

轻量 Android 原生外壳（WebView），用于在同一可信局域网内访问桌面端 Codex Light 开启的「局域网访问」地址。应用本身不包含模型运行时，也不复制本机配置；会话、项目、模型目录、终端与审批均复用所连接电脑上的官方 Codex CLI 实例。

## 已实现的功能

- **局域网连接与配对**：支持手动输入 IP / 端口 / 令牌，或从系统剪贴板一键解析桌面端生成的 `http://<IP>:<PORT>/#token=<TOKEN>` 链接；保留最近 8 条连接记录并支持清空。
- **令牌刷新保持**：桌面端局域网模式默认在提取 `#token` 后清除地址栏片段且不写入 Web Storage；Android 外壳仅对当前已校验的同源目标通过受控桥接回传当前会话令牌，页面刷新后无需重新粘贴链接。
- **非安全上下文兼容**：局域网 HTTP 下浏览器默认隐藏 `[SecureContext]` API（如 `crypto.randomUUID` 与 `navigator.clipboard`）；前端入口模块内置符合 CSP（`script-src 'self'`）的 UUIDv4 生成与原生剪贴板兜底。
- **附件与交互适配**：对接 Android 系统文件/图片选择器（`WebChromeClient.onShowFileChooser`），支持向当前回合上传最多 5 个图片或路径引用文件；提供连接断开覆盖层重试、网页回退与双击返回退出保护。
- **离线演示模式**：未连接电脑时可进入内置的静态演示页预览移动端布局，不发起网络或模型请求。

## 安全与使用边界

- 局域网 HTTP 通信不加密，仅适用于受信任的家庭或个人局域网。请勿在公共 Wi-Fi 下开启或暴露端口。
- 外壳禁用应用备份（`android:allowBackup="false"`），`MainActivity` 不向外部应用导出（`android:exported="false"`）。
- WebView 导航严格锁定在当前配置的同源 `http://<host>:<port>`（或演示模式下的 `file:///android_asset/`）；对话内容中的外部网页链接统一交由系统浏览器打开，不会在持有原生桥接的 WebView 内加载第三方站点。
- 遵循服务端局域网权限边界：手机端无法调用主程序关闭、运行引擎升级、DSH 连接注册、应用更新及打开本机资源管理器等仅限本机窗口的接口。

## 连接步骤

1. 在电脑端启动 Codex Light，打开**设置 → 远程控制 → 局域网访问**，选择局域网 IPv4 地址开启访问并复制链接。
2. 确保手机与电脑处于同一局域网，且电脑防火墙放行对应 TCP 端口（或通过 USB 调试转发）。
3. 打开 Android 端 Codex Light，点击**快捷粘贴局域网链接**（或手动填入主机、端口与令牌），点击**连接并进入**。

## 从源码构建

构建脚本直接使用 Android SDK Build-Tools 与 JDK 原生工具链（`aapt2`、`javac`、`d8`、`zipalign`、`apksigner`），不依赖 Gradle Wrapper 下载外部构件。

### 环境要求

- Windows PowerShell 5.1+ 或 PowerShell 7+
- JDK 17+（设置 `JAVA_HOME` 或加入 `PATH`）
- Android SDK Platform 34 与 Build-Tools 34.0.0+（设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`）

### 执行构建

```powershell
pwsh -File android/build.ps1
```

默认产物输出至 `android/dist/codex-light.apk` 及对应的 `codex-light.apk.sha256`（已被 `.gitignore` 忽略，不纳入源码提交）。未通过环境变量 `CODEX_LIGHT_ANDROID_KEYSTORE` 指定正式签名密钥时，脚本会在本地生成不入库的 `local-debug.keystore` 完成签名校验；请勿将任何 `.keystore` 或私钥文件提交至版本库。

## 目录结构

```text
android/
├── app/src/main/
│   ├── AndroidManifest.xml             # 权限、网络安全配置与 Activity 导出边界
│   ├── assets/web/index.html           # 离线演示模式静态页
│   ├── java/com/codexlight/android/
│   │   ├── ServerConnectActivity.java  # 启动入口：链接粘贴解析与连接历史管理
│   │   ├── MainActivity.java           # WebView 容器、同源校验与错误重试弹层
│   │   ├── AppWebViewClient.java       # 同源导航守卫与外部链接系统浏览器分流
│   │   ├── AppWebChromeClient.java     # 加载进度条与系统文件选择器桥接
│   │   ├── CodexLightBridge.java       # 受同源约束的 JavascriptInterface 桥接
│   │   ├── ServerConfig.java           # 局域网 URL / 令牌解析模型
│   │   └── ServerConfigManager.java    # 本机私有 SharedPreferences 存储
│   └── res/                            # 布局、深色主题、图标与明文局域网配置
└── build.ps1                           # 无 Gradle 原生打包与签名校验脚本
```
