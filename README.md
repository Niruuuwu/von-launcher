# von

> a quiet, minimal android launcher that plays nice with your music.

von is a personal fork of [olauncher](https://github.com/tanujnotes/Olauncher). the original premise was solid, but it needed cleaner typography, proper music controls, and zero nag screens. so i reshaped it into something calmer.

no icon clutter, no notification dots, no background telemetry. just clean text, a daylight-responsive clock, and an ambient player when tunes are rolling.

---

### what's inside

#### daylight-curve clock
large, thin, left-aligned numbers set in **outfit**. the weight and opacity quietly adapt to the daylight curve so it never glares at night:
- **05:00 – 11:00 (morning)**: ExtraLight (`200`), full alpha
- **11:00 – 17:00 (day)**: Light (`300`), full alpha
- **17:00 – 22:00 (evening)**: ExtraLight (`200`), 95% alpha
- **22:00 – 05:00 (night)**: Thin (`100`), 80% alpha

the colon stays resting at 40% alpha so it doesn't fight the digits. transitions crossfade over 300ms. updates on system broadcast ticks only; zero background battery drain.

#### now playing widget
when spotify or your media player is playing, a subtle 72dp album art frame slides onto the home screen:
- inline transport controls (previous, play/pause, next).
- an interactive wavy scrub line running across the display tracking track completion—tap or drag anywhere to seek.
- auto-hides cleanly when playback stops.

#### agsl aurora visualizer
an android graphics shading language (agsl) dynamic fluid aurora shader resting behind the home screen:
- samples the palette from whatever album art is currently active.
- drifts gently beneath the app list with tuned dark, moody contrast so it never washes out text or strains your eyes in dark rooms.
- runs purely on the gpu when media is active.

#### stripped clean
- completely gutted the first-run timers, review nags, rating prompts, and affiliate tracking.
- stripped out all external tracking parameters (`utm_source`).
- 100% offline and local. nothing touches a server.

---

### gestures

- **swipe up**: open drawer (fuzzy search, instant keyboard).
- **swipe down**: notification panel.
- **double tap**: lock screen (via local accessibility service).
- **swipe left / right**: launch your primary mapped shortcuts.
- **long press empty space**: settings & hidden apps.

---

### build & install

requires Android SDK 34+ and JDK 17+.

```bash
# clone
git clone https://github.com/Niruuuwu/von-launcher.git
cd von-launcher

# assemble apk
./gradlew assembleDebug

# push to device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### credits & license

- forked from [olauncher](https://github.com/tanujnotes/Olauncher) by tanuj notes.
- licensed under [GNU GPLv3](LICENSE).
- curated by [niranjan shastry](https://www.linkedin.com/in/niranjan-shastry-86aa04290/) ([@Niruuuwu](https://github.com/Niruuuwu)).
