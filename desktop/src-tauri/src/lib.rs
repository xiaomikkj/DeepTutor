// DeepTutor Windows Desktop Shell
//
// A thin Tauri 2 shell that connects to a remote DeepTutor server.
// - Loads a local connection page (ui/index.html) for configuring the
//   server address.
// - Persists the server URL in the app config directory.
// - Navigates the main webview to the remote server.
// - Provides a system tray (show/hide, connection settings, quit) and
//   hides to tray on window close.

use std::fs;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, Url, WebviewUrl, Window};

const CONFIG_FILE: &str = "client.json";

#[derive(Serialize, Deserialize, Default, Clone)]
#[serde(rename_all = "camelCase")]
pub struct AppConfig {
    /// Remote DeepTutor server base URL, e.g. https://tutor.example.com
    #[serde(default)]
    pub server_url: String,
    /// Last known reachable URL (used to prefill + quick-connect).
    #[serde(default)]
    pub last_url: String,
}

fn config_path(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("cannot resolve config dir: {e}"))?;
    Ok(dir.join(CONFIG_FILE))
}

fn load_config(app: &AppHandle) -> Result<AppConfig, String> {
    let path = config_path(app)?;
    if !path.exists() {
        return Ok(AppConfig::default());
    }
    let raw = fs::read_to_string(&path).map_err(|e| format!("cannot read config: {e}"))?;
    serde_json::from_str(&raw).map_err(|e| format!("cannot parse config: {e}"))
}

fn save_config(app: &AppHandle, cfg: &AppConfig) -> Result<(), String> {
    let path = config_path(app)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("cannot create config dir: {e}"))?;
    }
    let raw = serde_json::to_string_pretty(cfg).map_err(|e| format!("cannot serialize config: {e}"))?;
    fs::write(&path, raw).map_err(|e| format!("cannot write config: {e}"))
}

/// Normalize a user-provided server address into a navigable base URL.
/// Accepts "host:port", "https://host", trailing slashes are trimmed.
fn normalize_server_url(input: &str) -> Result<String, String> {
    let trimmed = input.trim();
    if trimmed.is_empty() {
        return Err("Please enter a server address.".into());
    }
    let with_scheme = if trimmed.contains("://") {
        trimmed.to_string()
    } else {
        format!("https://{trimmed}")
    };
    let url = url::Url::parse(&with_scheme).map_err(|e| format!("Invalid server address: {e}"))?;
    if url.scheme() != "http" && url.scheme() != "https" {
        return Err("Server address must use http or https.".into());
    }
    let mut base = url.to_string();
    while base.ends_with('/') {
        base.pop();
    }
    Ok(base)
}

#[tauri::command]
fn load_config_command(app: AppHandle) -> Result<AppConfig, String> {
    load_config(&app)
}

#[tauri::command]
fn save_config_command(app: AppHandle, config: AppConfig) -> Result<(), String> {
    save_config(&app, &config)
}

/// Save the server URL and navigate the main window to it.
#[tauri::command]
fn connect(app: AppHandle, server_url: String) -> Result<String, String> {
    let base = normalize_server_url(&server_url)?;
    let mut cfg = load_config(&app).unwrap_or_default();
    cfg.server_url = base.clone();
    cfg.last_url = base.clone();
    save_config(&app, &cfg)?;
    navigate_main(&app, WebviewUrl::External(base.parse().expect("validated url")))?;
    Ok(base)
}

/// Navigate back to the local connection page.
#[tauri::command]
fn open_connect_page(app: AppHandle) -> Result<(), String> {
    navigate_main(&app, WebviewUrl::App("index.html".into()))
}

fn navigate_main(app: &AppHandle, url: WebviewUrl) -> Result<(), String> {
    let win = app
        .get_webview_window("main")
        .ok_or_else(|| "main window not found".to_string())?;
    let url = match url {
        WebviewUrl::External(u) | WebviewUrl::CustomProtocol(u) => u,
        WebviewUrl::App(path) => {
            // Serve the embedded connect page over the tauri protocol.
            // Windows resolves the app origin as http://tauri.localhost.
            let scheme = if cfg!(windows) { "http" } else { "tauri" };
            let rel = if path.to_str() == Some("index.html") {
                String::new()
            } else {
                path.display().to_string()
            };
            Url::parse(&format!("{scheme}://tauri.localhost/{rel}")).map_err(|e| e.to_string())?
        }
        _ => return Err("unsupported webview url".into()),
    };
    win.navigate(url).map_err(|e| e.to_string())
}

fn show_main_window(app: &AppHandle) -> Result<(), String> {
    let win = app
        .get_webview_window("main")
        .ok_or_else(|| "main window not found".to_string())?;
    win.show().map_err(|e| e.to_string())?;
    win.unminimize().map_err(|e| e.to_string())?;
    win.set_focus().map_err(|e| e.to_string())
}

fn setup_tray(app: &tauri::App) -> tauri::Result<()> {
    let show_i = MenuItem::with_id(app, "show", "Show / Hide", true, None::<&str>)?;
    let settings_i = MenuItem::with_id(app, "settings", "Connection Settings", true, None::<&str>)?;
    let quit_i = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&show_i, &settings_i, &quit_i])?;

    let _tray = TrayIconBuilder::with_id("deeptutor-tray")
        .icon(app.default_window_icon().unwrap().clone())
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| {
            let app = app.app_handle();
            let _ = match event.id.as_ref() {
                "show" => show_main_window(app),
                "settings" => {
                    let _ = open_connect_page(app.clone());
                    show_main_window(app)
                }
                "quit" => {
                    app.exit(0);
                    Ok(())
                }
                _ => Ok(()),
            };
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                let _ = show_main_window(tray.app_handle());
            }
        })
        .build(app)?;
    Ok(())
}

/// On close, hide to tray instead of quitting (full app exit via tray "Quit").
fn on_window_event(window: &Window, event: &tauri::WindowEvent) {
    if let tauri::WindowEvent::CloseRequested { api, .. } = event {
        // Preserve the tray session.
        api.prevent_close();
        let _ = window.hide();
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            load_config_command,
            save_config_command,
            connect,
            open_connect_page
        ])
        .on_window_event(on_window_event)
        .setup(|app| {
            setup_tray(app)?;
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running DeepTutor desktop client");
}
