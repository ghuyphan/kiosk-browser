# Release v1.7.0: Modernization, Recovery & Advanced Session Policies

This major update brings the Kiosk Browser up to modern Android standards by removing deprecated APIs, introducing robust self-recovery/reload capabilities, and adding fine-grained session security policies, alongside the polished Remote Control interface.

### Key Enhancements

* **Modular Architecture**: Restructured the app by extracting responsibilities into specialized controllers:
  * `BrowserSessionManager` for cookie/storage coordination.
  * `BrowserRecoveryController` for connection monitoring and exponential backoff reload retries.
  * `AuthenticationController` for HTTPS Basic Auth.
  * `WebViewConfigurator`, `NavigationPolicy`, `DownloadController`, and `PermissionController`.
* **Robust Crash & Network Recovery**:
  * Automatically detects network connectivity loss and schedules bounded exponential backoff retries to reload when connection returns.
  * Handles WebView renderer crashes (`onRenderProcessGone()`) by cleanly recreating the WebView and restoring the last successful URL.
* **Modernized Credentials & Autofill**:
  * Removed deprecated WebView password saving APIs.
  * Configured standard Android Autofill support (managed via Autofill settings toggle).
* **Flexible Session Clearing Policies**:
  * Clears cookies, DOM storage, database cache, history, SSL state, and basic auth credentials.
  * Supported scheduling options: Never, On App Start, After Inactivity (touch-monitored timer), and Daily clearing (coordinated with background `SessionClearWorker` via WorkManager).
* **Secure HTTPS Basic Auth**:
  * Custom prompt UI for Basic Auth credentials over HTTPS.
  * Keystore-backed `EncryptedSharedPreferences` for secure credential persistence.
* **SSO Redirect & Custom Schemes Compatibility**:
  * Full-screen dialog to handle popup windows (`onCreateWindow` / `onCloseWindow`) during OIDC/SAML login flows.
  * Allowed custom schemes (e.g., `intent://`, `msauth://`) in navigation rules.
  * New settings for configuring **Identity Provider (IDP) Allowlist** domains and toggling **Third-Party Cookies**.
* **Remote Control Polish**:
  * Fixed kiosk pairing dialog handshake lifecycle.
  * Integrated native Android controller screen for scanned QR kiosk links.
  * Avoided gesture conflicts between remote pointer and browser refresh.
  * Optimized movement transmission to prevent rate limits with cooldown.
