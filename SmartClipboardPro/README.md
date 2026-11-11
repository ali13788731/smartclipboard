# Smart Clipboard Pro — v0.2

Open in Android Studio (Koala+) or build with the included GitHub workflow (`.github/workflows/build.yml`).

## What changed in 0.2
- Activation button is never disabled; it asks for the missing permission, shows live state (active / inactive),
  and reports a clear error if the service did not start.
- "Test window" button to verify the overlay works independently of clipboard detection.
- Clipboard reading on Android 10+ now uses a 1px focusable overlay window (gets input focus -> can read clipboard),
  with the transparent activity kept only as a last resort. The app shows which method worked / why it failed.
- New UI: animated hero card, staged entrance, animated preview card (slide/scale, countdown bar, swipe to dismiss),
  bottom-sheet editor with Persian helpers (extra spaces, ی/ک, digit conversion), undo/redo, custom colors,
  adaptive icon, letter-spacing fixed for Persian.
- Notification has a "disable" action.

## Honest limitations
- Not compiled/tested on a device in the authoring environment (no Android SDK there).
- Some OEMs (MIUI/HyperOS, ColorOS...) block background pop-ups or clipboard access; see the compatibility tips in the app.
