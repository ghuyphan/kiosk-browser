# Release v1.5.6: Security Hardening

This release closes the security and kiosk-boundary issues found in the repository-wide audit.

## Security fixes

* Remote-control commands now use HMAC-SHA256 authentication, short validity windows, and replay protection.
* Remote-control topics and secrets are longer, controller credentials stay in the URL fragment, and QR codes are generated locally.
* Every app-initiated navigation path now passes through the same HTTPS and kiosk-host policy.
* Camera, microphone, and location access now require an origin-specific user confirmation.
* Cleartext traffic, mixed content, third-party cookies, user-installed certificate authorities, and app-data backups are disabled.
* Remote-control connections now use finite read timeouts and actively disconnect during shutdown.

## Compatibility notes

* Startup and navigation URLs must use HTTPS.
* Existing remote-controller links are rotated once after upgrading and must be scanned or copied again.

## Technical details

* **Tag**: `v1.5.6`
* **Target branch**: `main`
* **Supported SDKs**: Min SDK `23` (Android 6.0), Target SDK `36` (Android 16)
