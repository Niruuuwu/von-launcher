# Von Launcher

A minimal, aesthetic Android launcher focused on simplicity, music integration, and ambient design.

Forked from [Olauncher](https://github.com/tanujnotes/Olauncher) by Tanuj Notes under the GNU GPLv3 license.

---

## Features & What Was Changed

- **Time-of-Day Daylight Clock**:
  - Uses the Outfit typeface (Google Fonts, OFL) with dynamic daylight-curve font weights:
    - `05:00 - 11:00` Morning: ExtraLight (200), alpha 1.0
    - `11:00 - 17:00` Day: Light (300), alpha 1.0
    - `17:00 - 22:00` Evening: ExtraLight (200), alpha 0.95
    - `22:00 - 05:00` Night: Thin (100), alpha 0.80
  - Colon separator rendered at 40% alpha with smooth 300ms crossfade on period transitions.
  - Localized 12h/24h format support with battery-friendly `ACTION_TIME_TICK` updates.

- **Now Playing Music Widget**:
  - Live album art cover preview (72dp) directly on the home screen.
  - Interactive playback transport controls (previous track, play/pause, next track).
  - Real-time interactive wavy progress scrub bar tracking song completion.

- **AGSL Fluid Ambient Visualizer**:
  - Hardware-accelerated Android Graphics Shading Language (AGSL) aurora wave shader on the home screen that softly moves and harmonizes during music playback.

- **Telemetry & Tracking Removed**:
  - Completely stripped out install-time tracking, state-machine telemetry, affiliate links, and review/rating prompts.

- **Developer**:
  - Customized by **Niranjan Shastry**
  - GitHub: [@Niruuuwu](https://github.com/Niruuuwu)
  - LinkedIn: [Niranjan Shastry](https://www.linkedin.com/in/niranjan-shastry-86aa04290/)

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE), preserving the original Olauncher open-source license.
