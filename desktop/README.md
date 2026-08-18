# DeepTutor Windows Desktop Client

A thin Tauri 2 shell that connects to a remote DeepTutor server and loads the
full DeepTutor web experience inside a native Windows window.

- Local connection page for configuring the server address
- Server URL persisted in the user profile config dir
- System tray: show/hide, connection settings, quit
- Hides to tray on window close (use tray → **Quit** to exit)
- Windows WebView2 runtime (shipped with Windows 11 / most Windows 10 installs)

## Architecture

```
desktop/
├── ui/                 # Local connection page (static HTML/CSS/JS)
├── scripts/
│   └── gen-icons.mjs   # Zero-dependency icon generator
├── src-tauri/
│   ├── src/lib.rs      # Tauri commands, tray, navigation
│   ├── capabilities/   # Window permission capabilities
│   ├── tauri.conf.json # App + bundler config
│   └── icons/          # Generated ico/png icon set
└── package.json        # Tauri CLI + scripts
```

The shell loads `ui/index.html`, where the user enters a server address.
On submit, the URL is validated/normalized, persisted via the `connect`
command, and the webview navigates to the remote server. The remote page
runs the regular DeepTutor Web app; the shell itself never embeds or
modifies it.

## Prerequisites (Windows)

1. [Rust](https://rustup.rs/) — stable toolchain, MSVC (`x86_64-pc-windows-msvc`)
2. [Node.js 20+](https://nodejs.org/) and npm
3. WebView2 Runtime (auto-installed on Windows 11; for Windows 10 install
   [Evergreen WebView2 Runtime](https://developer.microsoft.com/en-us/microsoft-edge/webview2/))
4. Microsoft C++ Build Tools (VS 2022 Build Tools with "Desktop development
   with C++" workload) — required by `windows-rs` / link.exe

## Build & run (development)

```bash
cd desktop
npm install

# Generate icons (committed, but regenerate after editing gen-icons.mjs)
npm run icons

# Launch the app in dev mode
npm run tauri dev
```

## Build & run (production installers)

```bash
cd desktop
npm install
npm run icons
npm run tauri build
```

Installers are written to `src-tauri/target/release/bundle/`:

- `nsis/DeepTutor_0.1.0_x64-setup.exe` — user-mode installer (recommended)
- `msi/DeepTutor_0.1.0_x64.msi` — MSI package

## Usage

1. Start the app; the connection page appears.
2. Enter your DeepTutor server address, e.g. `https://tutor.example.com`
   (bare hostnames default to `https://`).
3. Click **Connect**. The window loads the remote DeepTutor UI.
4. Close the window to hide to tray; reopen from the tray icon, or use
   tray → **Connection Settings** to switch servers, tray → **Quit** to exit.

## Configuration file

The saved server URL lives in:

```
%APPDATA%\info.deeptutor.desktop\client.json
```

Delete this file to reset the connection.

## Security notes

- The shell grants **no IPC capability to remote pages**: the loaded remote
  URL cannot call any Tauri command, so the shell surface stays isolated from
  the server content.
- Only `http`/`https` server addresses are accepted.
- Credentials are handled by the remote server and its own session cookies
  inside WebView2 storage.
