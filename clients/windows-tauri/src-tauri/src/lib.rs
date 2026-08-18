//! DeepTutor Windows client — thin Tauri (WebView2) shell.
//!
//! The DeepTutor stack runs as a pair of local servers started by the
//! `deeptutor start` CLI: backend (FastAPI on :8001) + frontend (Next.js on
//! :3782, which proxies /api and /ws to the backend). This shell simply opens
//! a WebView2 window pointed at the frontend URL, and — when the server is not
//! already running — tries to start it in the background so the app works on
//! first double-click (after `pip install deeptutor`).
//!
//! Microphone: `getUserMedia` in the WebView works out of the box (WebView2
//! shows the per-site permission prompt; on Windows 10/11 also allow the app
//! under Settings → Privacy → Microphone). No extra native code needed.
//!
//! Environment overrides:
//!   DEEPTUTOR_URL           frontend URL (default http://localhost:3782)
//!   DEEPTUTOR_AUTO_LAUNCH   0|false|no disables auto-starting `deeptutor start`
//!   DEEPTUTOR_READY_TIMEOUT  seconds to wait for the server (default 120)

use std::net::{TcpStream, ToSocketAddrs};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::{Manager, RunEvent};

const DEFAULT_URL: &str = "http://localhost:3782";
const DEFAULT_READY_TIMEOUT_SECS: u64 = 120;
const HEALTH_RETRY_MS: u64 = 1_000;
const CONNECT_TIMEOUT_MS: u64 = 800;

static CHILD: Mutex<Option<Child>> = Mutex::new(None);

fn configured_url() -> String {
    std::env::var("DEEPTUTOR_URL")
        .ok()
        .map(|v| v.trim().trim_end_matches('/').to_string())
        .filter(|v| !v.is_empty())
        .unwrap_or_else(|| DEFAULT_URL.to_string())
}

fn auto_launch_enabled() -> bool {
    std::env::var("DEEPTUTOR_AUTO_LAUNCH")
        .ok()
        .map(|v| !matches!(v.trim().to_lowercase().as_str(), "0" | "false" | "no" | "off"))
        .unwrap_or(true)
}

fn ready_timeout() -> Duration {
    std::env::var("DEEPTUTOR_READY_TIMEOUT")
        .ok()
        .and_then(|v| v.trim().parse::<u64>().ok())
        .map(Duration::from_secs)
        .unwrap_or(Duration::from_secs(DEFAULT_READY_TIMEOUT_SECS))
}

/// Extract (host, port) from an http(s) URL. Defaults to port 80.
fn host_port(url: &str) -> Option<(String, u16)> {
    let rest = url
        .strip_prefix("http://")
        .or_else(|| url.strip_prefix("https://"))?;
    let authority = rest.split('/').next()?;
    match authority.rsplit_once(':') {
        Some((host, port)) => {
            let port = port.parse::<u16>().ok()?;
            if !host.is_empty() {
                Some((host.to_string(), port))
            } else {
                None
            }
        }
        None => Some((authority.to_string(), 80)),
    }
}

/// Cheap readiness probe: the server only binds its port once it is ready.
fn port_open(url: &str) -> bool {
    let Some((host, port)) = host_port(url) else {
        return false;
    };
    let Ok(mut addrs) = (host.as_str(), port).to_socket_addrs() else {
        return false;
    };
    let Some(addr) = addrs.next() else {
        return false;
    };
    TcpStream::connect_timeout(&addr, Duration::from_millis(CONNECT_TIMEOUT_MS)).is_ok()
}

/// Try to launch `deeptutor start` in the background (detached, no console).
/// Returns true if the process was spawned.
fn launch_deeptutor() -> bool {
    let mut cmd = Command::new("deeptutor");
    cmd.arg("start");
    cmd.stdout(Stdio::null());
    cmd.stderr(Stdio::null());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    match cmd.spawn() {
        Ok(child) => {
            if let Ok(mut guard) = CHILD.lock() {
                *guard = Some(child);
            }
            true
        }
        Err(_) => false,
    }
}

fn stop_child() {
    if let Ok(mut guard) = CHILD.lock() {
        if let Some(mut child) = guard.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
}

fn navigate_main_window(app: &tauri::AppHandle, url: &str) {
    if let Some(win) = app.get_webview_window("main") {
        if let Ok(parsed) = url.parse::<tauri::Url>() {
            let _ = win.navigate(parsed);
        }
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            let handle = app.handle().clone();
            std::thread::spawn(move || {
                let url = configured_url();
                let auto_launch = auto_launch_enabled();
                let timeout = ready_timeout();

                if !port_open(&url) && auto_launch {
                    let _ = launch_deeptutor();
                }

                let deadline = Instant::now() + timeout;
                while Instant::now() < deadline {
                    if port_open(&url) {
                        navigate_main_window(&handle, &url);
                        return;
                    }
                    std::thread::sleep(Duration::from_millis(HEALTH_RETRY_MS));
                }
                // Give up quietly: the window keeps showing the status page,
                // which tells the user how to start the server manually.
            });
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building DeepTutor desktop shell")
        .run(|_app, event| {
            if matches!(event, RunEvent::Exit) {
                stop_child();
            }
        });
}
