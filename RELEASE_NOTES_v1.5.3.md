# Release v1.5.3: Theme Unification, Icon Resizing, and URL Auto-Hide Restore 🎨

Welcome to **v1.5.3** of **Kiosk Browser**! This release unifies the theme styling, resizes icons for better visibility, and restores the automatic toolbar auto-hiding behavior.

---

## 🌟 What's New

### 🎨 Theme Unification & Accent Styling
* **Deep Indigo/Violet Theme**: Restyled card headers, toggle switches, text selection handles, buttons, and dialogs across the settings and main screens using `#7C3AED` instead of the previous blue color.
* **Refined Dialogs**: The Remote Control and other custom popups now sport a premium Midnight card background with purple accent highlights.

### 📐 Enlarged Address Bar & Settings Icons
* **Main URL Bar Lock/Globe Icon**: Enlarged the `securityIcon` to `20dp` for a cleaner, modern look.
* **Settings Start URL Icon**: Enlarged the left globe icon to `22dp` to improve visibility.

### ⏱️ Restored Toolbar Auto-Hide
* **Auto-Hide Functionality**: Fixed a regression where the URL bar / toolbar stopped auto-hiding after loading page content or losing focus. The timer is now restored and auto-hides controls after `2200ms` of inactivity.

---

## 🛠️ Technical Details
* **Tag**: `v1.5.3`
* **Target Branch**: `main`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
