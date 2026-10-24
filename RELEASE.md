## Pulsix v0.5 — first public release

Clean, ad-free companion app for the **Daewoo DW26** smartwatch (IDO platform, the one behind *VeryFit*). No account, no tracking.

### Highlights
- Pairing and auto-reconnect, kept alive by a foreground service.
- Daily health: steps vs. goal, heart rate, calories, distance, sleep, stress and SpO₂, with 7-day charts.
- Notifications, incoming/missed calls, music control and weather ([Open-Meteo](https://open-meteo.com)) on the watch.
- Alarms (up to 10, synced both ways) and daily step goal.
- Watch faces: switch, remove, install from the free catalog (250+) or the app's own **Pip-Boy** face.
- Find my watch (vibrates for 15 s).
- Themes: automatic, light, dark and pure black (AMOLED).

### No proprietary code inside
This APK does **not** include IDO's SDK, its native libraries or VeryFit's key. At startup Pulsix loads them from the **VeryFit app installed on your own phone**, so:

1. Install **VeryFit** (`com.sport.fitplus1`) from Google Play — tested with 3.4.0.
2. You can disable it afterwards so it doesn't steal the watch: `adb shell pm disable-user com.sport.fitplus1` (Pulsix still finds it).
3. Without VeryFit, Pulsix only shows a dialog pointing to the Play Store.

### Install
Download `Pulsix-v0.5.apk` below, open it on the phone and allow installs from unknown sources. **Android 9+ on a 64-bit (arm64) phone.**

Then tap **Buscar reloj** with the watch's screen on, pick the DW26 and accept the pairing on the watch. In **Reloj → Funciones**, turn on notifications, music, calls and weather.

> Signed with a debug key: uninstall older builds signed with a different key first.

**SHA-256:** `140bc06cb5bacf792fea3d2c8fffe727d59ebc2bc5982236dd9364a6b26e2fc7`

### Known issues
- Pairing from Pulsix unbinds VeryFit (the watch accepts a single bound app).
- Notifications, call alerts and find-my-phone are not yet confirmed on the watch.
- Only one free face slot (~143 KB): installing a new face means replacing one.
