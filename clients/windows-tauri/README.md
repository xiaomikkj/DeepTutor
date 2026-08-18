# DeepTutor Windows 客户端（Tauri / WebView2 薄壳）

体积最小的 Windows 客户端方案：Tauri v2 壳 + 系统自带 WebView2（Win10/11 内置），
**不内置浏览器内核**。安装包约 3–8MB（Electron 方案约 80–150MB）。

## 它能做什么

- 打开一个原生窗口，加载 DeepTutor 前端（默认 `http://localhost:3782`），
  前端原有的聊天、语音、知识库、设置等全部功能原样可用。
- 启动时自动探测服务端口；若未运行则后台拉起 `deeptutor start`
  （后端 FastAPI :8001 + 前端 Next.js :3782 会一并启动），实现双击即用。
- 麦克风：WebView2 原生支持 `getUserMedia`，语音输入（`useVoiceRecorder` →
  `/api/v1/voice/stt`）开箱即用，首次使用会弹出站点授权提示。

## 目录结构

```
windows-tauri/
├── package.json              # npm 脚本（tauri dev / build）
├── scripts/gen_icon.py       # 生成图标源文件
├── src/index.html            # 启动等待页（等待服务就绪后自动跳转）
└── src-tauri/
    ├── tauri.conf.json       # 窗口/打包配置（NSIS 安装包）
    ├── capabilities/default.json
    ├── icons/                # 全套图标（已由 tauri icon 生成）
    └── src/
        ├── main.rs
        └── lib.rs            # 壳逻辑：探测端口 → 拉起后端 → 跳转
```

## 前置条件（Windows）

1. 安装后端（提供 Web UI 与 API）：`pip install deeptutor`
2. 安装 Node.js 20+（用于构建 Tauri 壳本身）
3. 安装 Rust（https://rustup.rs）
4. 安装 VS Build Tools 的「使用 C++ 的桌面开发」组件（Tauri 需要 MSVC 链接器）
5. 系统自带 WebView2 运行时（Win10/11 已内置）

## 构建与运行

```bash
cd clients/windows-tauri
npm install          # 安装 @tauri-apps/cli
npm run build        # 生成安装包：src-tauri/target/release/bundle/nsis/*.exe
```

开发调试：

```bash
npm run dev          # 以调试模式启动窗口
```

## 使用方式

- 默认双击安装后直接使用（自动拉起本地 `deeptutor start`，首次会自动构建前端，
  可能需等待 1–2 分钟，有加载提示页）。
- 连接远程/已有服务：设置环境变量后启动应用：

```bat
set DEEPTUTOR_URL=http://192.168.1.100:3782
deeptutor-desktop.exe
```

| 环境变量 | 默认 | 说明 |
| --- | --- | --- |
| `DEEPTUTOR_URL` | `http://localhost:3782` | 前端地址 |
| `DEEPTUTOR_AUTO_LAUNCH` | `1` | 设为 `0` 时不自动启动 `deeptutor start` |
| `DEEPTUTOR_READY_TIMEOUT` | `120` | 等待服务就绪的最大秒数 |

## 麦克风注意事项（Windows）

- 首次在应用内点击语音按钮时，WebView2 会弹出「允许站点使用麦克风吗」，选择允许。
- 若被拒绝，到 Windows 设置 → 隐私和安全性 → 麦克风：
  - 打开「允许桌面应用访问你的麦克风」；
  - 在允许列表中找到并启用本应用。
- 应用关闭时会自动回收它拉起的 `deeptutor start` 子进程（后端+前端一并停止）。
