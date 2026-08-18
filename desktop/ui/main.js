// DeepTutor desktop shell — local connection page logic.
// Runs inside the Tauri webview, so window.__TAURI__ is available.

const invoke = window.__TAURI__?.core?.invoke;

const form = document.getElementById("connect-form");
const input = document.getElementById("server-url");
const errorEl = document.getElementById("error");
const connectBtn = document.getElementById("connect-btn");

function showError(message) {
  errorEl.textContent = message;
  errorEl.classList.remove("hidden");
}

function clearError() {
  errorEl.classList.add("hidden");
  errorEl.textContent = "";
}

async function prefill() {
  try {
    const cfg = await invoke("load_config_command");
    if (cfg?.server_url) {
      input.value = cfg.server_url;
    } else if (cfg?.last_url) {
      input.value = cfg.last_url;
    }
  } catch (err) {
    showError(`Failed to load settings: ${err}`);
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearError();

  const url = input.value.trim();
  if (!url) {
    showError("Please enter a server address.");
    return;
  }

  connectBtn.disabled = true;
  connectBtn.textContent = "Connecting…";
  try {
    // The `connect` command validates, persists and navigates the window.
    await invoke("connect", { serverUrl: url });
  } catch (err) {
    showError(String(err));
    connectBtn.disabled = false;
    connectBtn.textContent = "Connect";
  }
});

prefill();
