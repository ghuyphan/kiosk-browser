# Kiosk Browser

A small Android browser intended for QMS dashboards, wall displays, tablets, and
other single-site deployments. It uses Android System WebView, whose rendering
engine is Chromium on standard Android devices.

## Download

Download the latest installable APK from the
[GitHub Releases page](https://github.com/ghuyphan/kiosk-browser/releases/latest).

## Features

- Configurable website that opens automatically at launch
- Modern browser toolbar with vector icons and a rounded address field
- Optional hidden navigation/URL toolbar and fullscreen mode
- Tap the small top chevron or swipe down from the top edge to reveal a hidden toolbar
- Back, forward, reload, home, share, find-in-page, and desktop-site controls
- JavaScript, cookies, DOM storage, authenticated downloads, and file uploads
- Camera, microphone, and location permission support for websites
- HTTP support for private-LAN QMS installations
- Android 6.0 (API 23) through current Android versions
- No ads, analytics, accounts, or third-party runtime dependencies

## Build

Install Android Studio or the Android command-line SDK, then run:

```sh
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a production deployment, create a signing key and configure a release
signing block in `app/build.gradle`, then run `./gradlew assembleRelease`.

## First use

1. Launch **Kiosk Browser**.
2. Open the menu and select **Settings**.
3. Enter the QMS website URL.
4. Choose whether to show the toolbar, use fullscreen, request desktop pages,
   or keep the screen awake.
5. Tap **Save and open website**.

When the toolbar is hidden, tap the small chevron at the top of the screen or
swipe downward from the top edge. Use **Hide toolbar** in the menu to return to
the clean display.

## Compatibility and security

The APK supports API 23 and newer. Website compatibility and Chromium security
updates depend on the installed **Android System WebView** (or Chrome WebView
provider), so managed devices should keep that component updated.

Invalid HTTPS certificates are blocked. Cleartext HTTP is enabled because many
private QMS systems still use LAN-only HTTP; prefer HTTPS whenever possible.
