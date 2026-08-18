# DeepTutor Android 客户端（Capacitor / WebView 薄壳）

体积较小的 Android 客户端方案：Capacitor 壳 + 系统 WebView（不用打包浏览器内核）。
APK 约 10–20MB（Flutter/RN 通常 15–40MB）。

## 它能做什么

- App 启动后进入「服务器选择」页，输入电脑上 DeepTutor 的前端地址
  （如 `http://192.168.1.100:3782`），点击进入即以 WebView 打开完整前端，
  聊天、语音、知识库等全部功能可用。
- 手机与电脑连同一 Wi-Fi，即可把电脑当作服务器使用（前端会把 `/api`、`/ws`
  代理到本机后端 8001）。
- 麦克风：已声明 `RECORD_AUDIO` + 启动时申请运行时权限，WebView 的
  `getUserMedia` 会被 Capacitor 自动放行，语音输入可用。
- 在 DeepTutor 界面按返回键可回到服务器选择页换地址；服务器地址保存在本地。

## 目录结构

```
android-capacitor/
├── package.json              # npm 脚本（cap sync / add android / build:android）
├── capacitor.config.ts       # appId=com.deeptutor.mobile, webDir=src
├── src/index.html            # 服务器选择页（本地资源，Capacitor 提供）
└── android/                  # 原生 Android 工程（npx cap add android 生成）
    ├── app/src/main/AndroidManifest.xml   # RECORD_AUDIO + cleartext
    └── app/src/main/java/com/deeptutor/mobile/MainActivity.java  # 运行时申请麦克风
```

## 前置条件

- Node.js 18+
- Android Studio（含 Android SDK / Gradle / JDK 17）
- 电脑端 DeepTutor 后端：`pip install deeptutor && deeptutor start`

## 构建 APK

```bash
cd clients/android-capacitor
npm install
npx cap add android          # 首次生成 android/ 工程（已存在可跳过）
npx cap sync                 # 同步 web 资源与配置到 android/
npx cap open android         # 用 Android Studio 打开后点击 Run / 打包 APK
```

纯命令行构建：

```bash
npm run build:android        # cap sync + ./gradlew assembleDebug
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 手机与电脑连同一 Wi-Fi。
2. 电脑运行 `deeptutor start`，记录提示的前端地址（如 `http://192.168.1.100:3782`）。
3. 安装 APK 后打开 App，输入该地址 → 测试（可选）→ 进入。

> 说明：首次启动会申请麦克风权限（用于语音转文字）。若以后想更换服务器，
> 在 DeepTutor 界面按返回键回到选择页即可；彻底重置可到 设置→应用→DeepTutor→清除存储。

## 麦克风实现要点

- `AndroidManifest.xml`：`<uses-permission android:name="android.permission.RECORD_AUDIO" />`，
  并在 `<application>` 上设置 `android:usesCleartextTraffic="true"`（局域网 http 地址需要）。
- `MainActivity.onCreate` 运行时申请 `RECORD_AUDIO`。
- Capacitor 7 的 `BridgeWebChromeClient.onPermissionRequest` 在权限已授予时
  自动放行 WebView 的音频捕获请求，因此前端 `useVoiceRecorder` 可直接使用。
