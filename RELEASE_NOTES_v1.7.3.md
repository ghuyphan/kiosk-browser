# Release v1.7.3: Disconnect Cursor Removal & Polished Refresh Transitions

This release adds clean remote disconnect handling (automatic mouse cursor removal) and heavily polishes the refresh animations across both the kiosk browser and remote controller interface.

### Key Changes

* **Disconnect Mouse Cursor Removal**:
  * Added a `disconnect` action to the whitelist of allowed remote commands.
  * The host browser now automatically hides the remote cursor and displays a notification when a remote controller disconnects.
  * The HTML and Native remote controllers now automatically send the `disconnect` command when closing, leaving the page, or when remote control is toggled off or credentials reset.
* **Polished Refresh Animations & Separation**:
  * Separated the action bar refresh button animation from the pull-to-refresh action, ensuring they animate independently.
  * Replaced standard Android animations with modern `ObjectAnimator` decelerating stop transitions to let the rotation finish gracefully when stopping.
  * Enhanced the pull-to-refresh indicator with haptic feedback, a subtle arming pop animation, a 0.6x to 1.0x growing scale transition, and a glowing border color states.
