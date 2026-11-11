# Smart Clipboard Pro — Stage 1

Open this folder in Android Studio (Koala+), let Gradle sync, run on a device.
If you build from CLI, run `gradle wrapper` once first.

## Implemented
- Foreground service owning a WindowManager overlay (TYPE_APPLICATION_OVERLAY)
- Clipboard detection (listener + transparent-activity read for Android 10+)
- Modes: Mini Bubble / Smart Window / Editor
- Bubble: drag, magnet snap to left/right edge, Edge Peek, long-press -> close
- Editor: confirm / cancel / copy / undo / redo, char-word-line counters
- RTL Compose UI, Material 3 (dynamic color, dark/light), TalkBack descriptions
- Permission screen + OEM battery guidance

## Honest limitations
- Android 10+ forbids background clipboard reads. This app works around it by briefly
  focusing a transparent activity; some OEMs may still block it.
- Not yet built or tested on a device (no Android SDK in the authoring environment).

## Next stages
2. Room history (30 free) + dedup/pin/search  3. Quick Text  4. Smart tools + detection
5. DataStore settings  6. Hilt + modularization  7. Play Billing  8. Tests
