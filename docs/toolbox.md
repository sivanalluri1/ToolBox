# Toolbox

Toolbox adds double tap to sleep to the Pixel Launcher home screen and the Pixel lock screen. Double tap an empty area to turn off the display and lock the phone. Home-screen and lock-screen support can be enabled separately.

## Requirements and setup

- Android 14 (API 34) or later. The current implementation is developed and tested on Pixel 9 running Android 17.
- Pixel Launcher for home-screen support.
- Toolbox accessibility service enabled by the user.

Open Toolbox, turn on the desired screen options, and select **Set up accessibility**. Review the disclosure and enable Toolbox in Android's accessibility settings. Both screen options default to off. Android 17 Advanced Protection may prevent this utility from enabling its accessibility service.

## Build and install

Open the project in Android Studio with Android SDK 37 and JDK 17 or newer. Alternatively, run:

```sh
./gradlew assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The application ID is `dev.siva.toolbox`. The current version is 0.3.0. The Gradle wrapper is included. Local SDK paths, build outputs, APKs, IDE files, caches, and signing keys are excluded from Git.

## Tap timing

Use **Time between taps** to set the maximum interval from 150 to 700 milliseconds in 10-millisecond steps. The default is 300 milliseconds. The setting is saved on the device and applies immediately to both home and lock screens. Changing it clears any pending tap pair.

Lower values require faster taps; higher values accommodate slower taps but increase the chance of recognizing unrelated touches. The separate 160-millisecond settling delay remains fixed.

## How it works

`SleepService` listens for accessibility window-state, window-change, click, and scroll events. Screen-on, screen-off, and unlock broadcasts also update detection. There is no polling loop.

On supported screens, the service attaches an invisible 1 × 1 pixel accessibility window with `FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCH_MODAL`, and `FLAG_WATCH_OUTSIDE_TOUCH`. Android delivers `ACTION_OUTSIDE` notifications when touches begin outside that pixel while sending the actual touches to the underlying screen. There is no visible gesture area or touch replay.

Two touch starts separated by at least 40 milliseconds and no more than the configured maximum interval schedule a lock after a 160-millisecond settling period. Accessibility click and scroll events cancel pending detection. The service checks screen eligibility again before calling `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. Device Admin permission is not required.

### Screen detection

Home detection requires Pixel Launcher's visible `workspace` node and excludes visible `apps_view` and `overview_panel` nodes.

Lock-screen detection checks that the display is interactive and keyguard is locked, then locates a System UI window containing the Android 17 Compose `element:lockscreen` scene or a supported legacy keyguard clock/status node. Accessibility configuration includes interactive windows, resource IDs, and structural views so the Compose hierarchy remains available. Known credential-entry, shade, and Quick Settings nodes exclude detection.

The observer is removed when the screen is unsupported, the display is no longer interactive, or the service stops. Pending gesture detection is canceled when the observer is removed.

## Privacy and battery

Window structure is processed locally to identify supported screens. Toolbox has no internet permission, analytics, continuous sensor monitoring, wake locks, or foreground service. Debug diagnostics report detection state, event counts, and lock results without recording screen text or touch coordinates.

Battery overhead is expected to be low because the service reacts to events and does not keep the display awake. Hierarchy inspection during active use is the main source of overhead. Battery consumption has not been measured, so no percentage estimate is claimed.

## Validation and limitations

The debug build and Android lint pass. Pixel 9 testing confirms touch-start delivery and successful sleep actions, including a lock-screen test where a single tap left the display awake and a double tap turned it off.

`ACTION_OUTSIDE` supplies only the initial touch down, without movement or release events. Coordinates are redacted on the tested device. Detection therefore uses timing and accessibility cancellation rather than complete gesture tracking. Rapid swipes, long presses, or interactions that do not emit click/scroll events may cause false detection.

Launcher and System UI identifiers can change between releases. Credential-entry exclusions, notification interactions, app-drawer transitions, and fingerprint/face unlocking require further validation. Android's existing authentication requirements still apply after locking. Sleep may show the always-on display if enabled in system settings.

For diagnostics:

```sh
adb shell dumpsys activity service dev.siva.toolbox/.SleepService
adb logcat -s ToolboxGesture:D
```

For Play Store distribution, the accessibility-service declaration and disclosure must be reviewed against applicable requirements. Toolbox declares `isAccessibilityTool=false`.
