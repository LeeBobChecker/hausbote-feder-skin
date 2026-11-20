# Hausbote Extrablatt

A Voice Satellite skin for [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) in the style of the "Der Hausbote" Home Assistant dashboard: an old letterpress broadsheet with Fraktur masthead, posters, stamps and tickets.

- **Masthead:** date line with issue number, place and time, "Der Hausbote" in Fraktur with a gold sheen, turning stars, a faint castle with flickering windows, sparkles and rising embers. The band below says what is happening ("Der Hausbote hört zu", "Die Setzerei arbeitet", "Extrablatt").
- **Bekanntmachung:** a red poster. While listening a large gold seal swells with your voice; while thinking it turns. Your question stamps in as the headline, the answer appears below word by word, and the seal stamps into the corner.
- **Wertmarken:** three stamps, "Zuhören", "Setzen", "Drucken", show the stage of the turn; the current one moves with the voice.
- **Billett:** the action the assistant took, shown as a switched-on ticket.
- **Docked:** a small Bekanntmachung at the bottom of the screen.

Status lines are in German.

## Settings

The skin follows Kiosk Satellite's **Voice Satellite > Appearance** settings:

| Setting | What the skin does |
| --- | --- |
| Theme | Ignored: the Hausbote is a light design |
| Background | Opacity of the full screen parchment (skin default 97 %) |
| Text size | Scales every line |
| Reactive activity bar | Off: the ink and quill no longer move with your voice |
| Overlay mode | Full screen or Docked |

The plugin has one setting of its own: **Ort in der Kopfzeile**, the place shown in the masthead (default Ostfildern). It makes no network calls and sends no data anywhere; it only reads the voice state, voice level and the turn's text from Kiosk Satellite to draw them.

## Installation

### Manual

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/LeeBobChecker/hausbote-feder-skin` and choose **Preview**.
3. Review the plugin and choose **Trust and install**, then turn it on.
4. In **Voice Satellite > Appearance**, set **Assistant 1 skin** to **Hausbote Extrablatt** and press **Preview**.

Needs Kiosk Satellite 2026.10.45 or newer.

## Building

`python3 tools/test.py` and `python3 tools/build.py` need JDK 17+ and an Android SDK with a platform and build-tools. Releases are built and published by GitHub Actions: bump `version` in `kiosk-satellite-plugin.json` and push to `main`.

## Credits

- Plugin SDK and build tools in `sdk/` and `tools/`: [kiosk-satellite-plugins](https://github.com/jxlarrea/kiosk-satellite-plugins), Apache-2.0.
- Fonts, all SIL Open Font License 1.1 (licences in `assets/fonts/`): IM Fell English and IM Fell English SC by Igino Marini, UnifrakturMaguntia by Peter Wiegel and j. 'mach' wust, Abril Fatface by TypeTogether, Oswald by the Oswald Project Authors.
- Not affiliated with Kiosk Satellite or Home Assistant.
