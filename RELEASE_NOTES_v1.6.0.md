# Release v1.6.0: Practical Remote Website Control

This release turns the remote controller into a two-way control surface for a
kiosk screen that is visible to the operator.

## Remote control

* Added a signed handshake, command acknowledgements, connection state, and page status.
* The Android pairing dialog closes automatically after a controller connects.
* Added a visible remote cursor with touchpad movement, tap, double-tap, and long-press.
* Added direct-touch coordinate mapping and batched two-finger scrolling.
* Added reliable text insertion plus Enter, Tab, Escape, deletion, and arrow keys.
* Added page title, URL, loading state, and Back/Forward availability to the controller.
* Added controller credential rotation through **Disconnect Controller & Reset Link**.

## Safety and reliability

* Remote commands now use an explicit action allowlist and an 8 KiB payload limit.
* High-frequency pointer and scroll updates avoid acknowledgement traffic.
* Password fields reject remote text insertion.
