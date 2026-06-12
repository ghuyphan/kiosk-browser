# Release v1.4.1: Premium Settings Redesign & UI Polish 🚀

We are excited to release **v1.4.1** of **Kiosk Browser**, featuring a complete visual redesign of the Settings screen, premium micro-animations, custom vector icons, and layout polish throughout the app.

---

## 🌟 What's New

### ⚙️ Premium Settings Screen Redesign
The settings screen has been rebuilt from the ground up for a modern, fluid, and cohesive user experience:
* **Interactive Setting Rows**: Tapping anywhere on a setting row now triggers a responsive ripple effect and toggles the switch state automatically.
* **Left-Aligned Icon Badges**: Every single setting row now features a dedicated vector icon wrapped inside a soft circular background badge tinted with its section's accent color (15% opacity), improving readability and style.
* **Capsule URL Input Bar**: Re-engineered the startup website text field into a premium capsule-like bar containing a leading web globe icon and a dynamic "Clear Text" button that appears on the right as soon as text is entered.
* **Sticky Glassmorphic Footer**: Action buttons have been moved into a floating bottom panel raised and padded to accommodate system navigation bar insets (gesture lines) smoothly on modern devices.
* **Gradient Save Button**: The "Save settings" action features a glowing neon-blue gradient background, while the "Cancel" action is represented by a clean outlined pill button.
* **Themed Clear Data Dialog**: Replaced the default system alert dialog with a gorgeous, dark-themed custom modal. It coordinates with the card midnight-blue theme and features a warning badge and custom styled buttons.

### 📱 Main Browser UI Polish
* **Midnight System Bars**: Re-colored status and navigation bar backgrounds to match the `#101124` midnight-blue theme.
* **Animated URL Bar Security Icon**: Scaled down the security icon to 16dp and added entrance scale animations and tactile touch-down scale interactions.
* **Isolated Pull-to-Refresh Layout**: Restricted the pull-to-refresh swipe bounds to the WebView context, preventing it from cutting into the top URL bar when pulled.

---

## 🛠️ Technical Details
* **Tag**: `v1.4.1`
* **Target Branch**: `main`
* **Commit**: `fd43caf`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
