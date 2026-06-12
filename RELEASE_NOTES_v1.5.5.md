# Release v1.5.5: Polished "Hide Controls" Behavior ⚙️

Welcome to **v1.5.5** of **Kiosk Browser**! This release polishes the hide-by-default behavior of controls.

---

## 🌟 What's New

### 👁️ Polished Hide Controls Logic
* **No Auto-Hide Overwrites**: The auto-hide timer no longer saves the "hidden by default" preference (`TOOLBAR_HIDDEN`). The preference is only updated when the user explicitly interacts with the menu option.
* **Persistent Hide Preference**: If the user explicitly selects **Hide controls** from the menu, the URL bar/controls will remain hidden by default on subsequent app launches and configuration reloads.
* **Dynamic Menu Toggling**: Tapping **Hide controls** hides the bar and updates the menu option to **Show controls** (accompanied by an eye icon). Tapping **Show controls** turns off the hide-by-default behavior, restoring default visibility.

---

## 🛠️ Technical Details
* **Tag**: `v1.5.5`
* **Target Branch**: `main`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
