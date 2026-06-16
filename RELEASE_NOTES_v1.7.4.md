# Release v1.7.4: Secure Storage, Credentials Clearing, and Hardened Configs

This release upgrades credential and pairing storage to modern encrypted standards, adds a dedicated action to clear remembered HTTP Basic Auth credentials, resolves daily session clearing timeouts, and strengthens default browser safety settings.

### Key Changes

* **Encrypted Storage**:
  * Replaced the previous basic auth shared preferences with `CredentialStore`, leveraging a centralized, keystore-backed `SecurePreferences` instance.
  * Migrated remote controller credentials to `RemotePairingStore`, ensuring all pairing secrets and topics are encrypted at rest.
  * Added fallback support to standard private preferences if the Android Keystore system is unavailable or corrupted.
* **Credentials Management**:
  * Added a **Clear saved credentials** option in the **Reset options** category of Settings.
  * This action allows users to selectively remove all stored HTTP Basic Auth credentials separate from other cached web data.
* **Session Clearing Reliability**:
  * Replaced asynchronous callbacks in `SessionClearWorker` with a synchronous, timeout-bounded operation (`clearSessionBlocking`).
  * If a session clear operation does not finish within 30 seconds, the worker registers a warning and retries on the next execution cycle.
* **Hardened Web Configurations**:
  * Disabled zoom controls specifically on SSO/popup windows to match modern desktop styling.
  * Explicitly disabled webview content access (`setAllowContentAccess(false)`) by default to prevent localized file path leaking.
  * Enabled Google Safe Browsing on Android 8.0 (API 26) and newer to block malicious and phishing pages.
* **Autofill Documentation**:
  * Updated `README.md` to reflect Android Autofill support for HTML forms and differentiate it from native Basic Auth storage.
