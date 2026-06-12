# Release v1.4.2: Find-in-Page UI, Password Saving & Hardened Security 🔐

Welcome to **v1.4.2** of **Kiosk Browser**! This release brings major interactive upgrades, administrator configurations for privacy, and critical security patches to harden our kiosk containment.

---

## 🌟 What's New

### 🔍 Inline Find-in-Page UI Bar
Re-engineered the search-in-page experience from a disruptive alert dialog to a modern, custom-designed inline sliding Find Bar:
* **Interactive Live Search**: Highlights matches instantly on the page and updates match counts (e.g. `1/5`) dynamically as you type using `WebView.setFindListener`.
* **Chevron Navigation**: Easily cycle through matching results using prev/next chevron buttons.
* **Smart Dismissal**: Pressing the hardware back key automatically hides the Find Bar first and restores page focus seamlessly.

### 🔑 Autocomplete & Password Saving Options
* **Credentials Preference**: Added a **Save passwords** setting switch under the **Kiosk controls** card.
* **Autofill Integration**: Allows administrators to disable password cache and autofill capabilities to protect user privacy in shared public kiosk configurations.

### 🛡️ Critical Security & Hardening Patches
* **Kiosk Mode Bypass (Fixed)**: Discovered and patched a critical bypass vulnerability in the window creation delegate (`onCreateWindow`). Previously, target="_blank" links would open popup windows that loaded arbitrary domains without validation, allowing users to jump out of the host-restricted boundary. The popup delegate now strictly enforces `isAllowedKioskHost` check before loading targets in the main web context.
* **Local Resource Hardening**: Explicitly disabled file and universal URL access permissions (`setAllowFileAccessFromFileURLs(false)` and `setAllowUniversalAccessFromFileURLs(false)`) to safeguard against cross-site scripting (XSS) attacks accessing local storage assets.

---

## 🛠️ Technical Details
* **Tag**: `v1.4.2`
* **Target Branch**: `main`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
