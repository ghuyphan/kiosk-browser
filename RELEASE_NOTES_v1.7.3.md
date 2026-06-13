# Release v1.7.3: Simplified Remote Control

This release gives remote control a simpler, more familiar mouse-and-keyboard interface, improves disconnect handling, and polishes browser refresh feedback.

### Key Changes

* **Simplified Pairing**:
  * Removed the exposed controller URL and click-to-copy action.
  * Reduced the pairing dialog to a clear enable switch, QR code, scan action, and pairing reset.
  * Shortened labels and removed unnecessary containers for a cleaner native appearance.
* **Cleaner Remote Controller**:
  * Redesigned the native and web controllers around a large trackpad, clear left/right buttons, and simple Mouse and Keyboard modes.
  * Replaced the purple card-heavy styling with a restrained blue-gray utility layout.
* **Disconnect Mouse Cursor Removal**:
  * Added a `disconnect` action to the whitelist of allowed remote commands.
  * The host browser now automatically hides the remote cursor and displays a notification when a remote controller disconnects.
  * The HTML and Native remote controllers now automatically send the `disconnect` command when closing, leaving the page, or when remote control is toggled off or credentials reset.
* **Polished Refresh Feedback**:
  * Separated the action bar refresh button animation from the pull-to-refresh action, ensuring they animate independently.
  * Replaced standard Android animations with modern `ObjectAnimator` decelerating stop transitions to let the rotation finish gracefully when stopping.
  * Enhanced the pull-to-refresh indicator with haptic feedback, a subtle arming pop animation, a 0.6x to 1.0x growing scale transition, and a glowing border color states.
