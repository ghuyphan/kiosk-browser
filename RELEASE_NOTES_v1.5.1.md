# Release v1.5.1: Native Play Services Code Scanner & Smart Address Bar Search 🔍

Welcome to **v1.5.1** of **Kiosk Browser**! This release integrates a native Google-powered code scanner UI and resolves single-word search queries in the address bar.

---

## 🌟 What's New

### 📷 Native Play Services Code Scanner Overlay
* **Permission-less native scanner overlay**: Integrated Google's official `play-services-code-scanner` client.
* **No permissions required**: Scans barcodes and QR codes securely via a system sheet without requiring application camera permissions.
* **Intent-based fallbacks**: If Play Services is not installed or available on the device, it automatically falls back to standard external intents (like ZXing).

### 🔍 Smart Address Bar Search Heuristics
* **Chromium-style address input**: Resolved the issue where typing single-word queries (e.g. "weather" or "hello") would incorrectly navigate to `https://weather` or `https://hello`.
* **Intelligent differentiation**: Differentiates between URLs, IP addresses, `localhost` hosts, and generic search queries. Single-word queries now default to a Google search automatically.

---

## 🛠️ Technical Details
* **Tag**: `v1.5.1`
* **Target Branch**: `main`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
