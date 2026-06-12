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
- Navigation/URL toolbar that automatically hides without scrolling
- Remembers the hidden toolbar state between app launches
- Smooth expanding address field with a one-tap clear action
- Pull down anywhere to reveal controls; at the top, pull farther to refresh
- Back, forward, reload, home, share, find-in-page, and desktop-site controls
- JavaScript, cookies, DOM storage, authenticated downloads, and file uploads
- Camera, microphone, and location permission support for websites
- HTTP support for private-LAN QMS installations
- Optional Android screen pinning, startup-domain restriction, external-app
  blocking, screenshot protection, fullscreen, and keep-awake controls
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
4. Choose whether to use fullscreen, request desktop pages, or keep the screen awake.
5. Tap **Save settings**.

When the toolbar is hidden, pull down anywhere on the page to reveal it. When
the page is already at the top, pull farther and release to refresh.

## Compatibility and security

The APK supports API 23 and newer. Website compatibility and Chromium security
updates depend on the installed **Android System WebView** (or Chrome WebView
provider), so managed devices should keep that component updated.

Invalid HTTPS certificates are blocked. Cleartext HTTP is enabled because many
private QMS systems still use LAN-only HTTP; prefer HTTPS whenever possible.
