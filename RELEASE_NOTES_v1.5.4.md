# Release v1.5.4: Separated Toolbar Reveal and Pull-to-Refresh Gestures 📱

Welcome to **v1.5.4** of **Kiosk Browser**! This release fixes the conflict between swipe-down-to-reveal-toolbar and pull-to-refresh gestures.

---

## 🌟 What's New

### 🔄 Resolved Gesture Conflicts
* **Gesture Separation**: Previously, swiping down when the toolbar was hidden would simultaneously reveal the toolbar and trigger the pull-to-refresh spinner.
* **Smart Gesture Logic**: Swiping down when the toolbar is hidden now **only** reveals the toolbar. Once the toolbar is visible, pulling down at the top of the page will correctly trigger the pull-to-refresh indicator, providing a clean, conflict-free interaction.

---

## 🛠️ Technical Details
* **Tag**: `v1.5.4`
* **Target Branch**: `main`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
