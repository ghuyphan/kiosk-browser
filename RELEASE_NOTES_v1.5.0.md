# Release v1.5.0: Zero-Dependency P2P Remote Control & Resilient QR Code Renderer 📡

Welcome to **v1.5.0** of **Kiosk Browser**! This release introduces a powerful peer-to-peer remote control system that lets you control this browser from another device or act as a remote controller, completely dependency-free, alongside a highly resilient multi-provider QR code loading engine.

---

## 🌟 What's New

### 📡 Peer-to-Peer Remote control System
* **ntfy.sh Integration**: Uses the public `ntfy.sh` server as a lightweight, public relay to safely push and receive control commands.
* **Control Actions**: Supports real-time execution of critical commands sent from a remote controller:
  * `back`: Navigate back.
  * `forward`: Navigate forward.
  * `reload`: Reload active page.
  * `home`: Navigate to configured start URL.
  * `url`: Direct load of a specified URL.
  * `type`: Inject keyboard input programmatically via JavaScript.
* **Static Controller Dashboard (`remote.html`)**: A dark-themed responsive controller web app hosted directly alongside the browser, allowing other devices to control the kiosk layout easily.

### 🖼️ Resilient Multi-Provider QR Code Renderer
* **Local offline fallback tree**: Integrated a robust loading mechanism that cycles through multiple free public QR code providers if any network or API endpoint is unreachable:
  1. `api.qrserver.com`
  2. `quickchart.io`
  3. `image-charts.com`
* **Safe Connection Timeouts**: Sets a strict `5000ms` connection and read timeout on each provider attempt to prevent locking threads in slow network conditions.

### 🛡️ Kiosk Security Integration
* **Restriction Bypass Protections**: Scanned remote controller URLs (`remote.html`) are exempted from domain restriction checks (`restrictToStartHost`) to allow devices to serve as controllers without breaching security boundaries.

---

## 🛠️ Technical Details
* **Tag**: `v1.5.0`
* **Target Branch**: `main`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
