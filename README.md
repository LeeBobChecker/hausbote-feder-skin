# Feder und Tinte

A Voice Satellite skin for [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite), drawn in the style of an old letterpress broadsheet. It was made for the "Hausbote" Home Assistant dashboard, but works with any dashboard.

- **Listening:** a pool of ink breathes and swells with your voice; the quill dips in and splashes droplets when you speak up. Small golden sparkles twinkle around it.
- **Thinking:** the quill writes looping flourishes that slowly fade from the paper.
- **Answer:** your words appear in italics in quotation marks, the answer is written word by word like fresh ink. The last action the assistant took is shown in red at the bottom.
- **Full screen:** a parchment page with a printer's frame. **Docked:** a small parchment card at the bottom that leaves the dashboard usable.

The status lines are in German ("Ich höre zu …", "Die Feder überlegt …").

## Settings

The skin follows Kiosk Satellite's **Voice Satellite > Appearance** settings:

| Setting | What the skin does |
| --- | --- |
| Theme | Light: parchment and brown-black ink. Dark: night-blue paper and cream ink with golden accents. Auto follows Kiosk Satellite |
| Background | Opacity of the full screen parchment (skin default 97 %) |
| Text size | Scales every line |
| Reactive activity bar | Off: the ink and quill no longer move with your voice |
| Overlay mode | Full screen or Docked |

The plugin has no settings of its own. It makes no network calls and sends no data anywhere; it only reads the voice state, voice level and the turn's text from Kiosk Satellite to draw them.

## Installation

### Manual

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/LeeBobChecker/hausbote-feder-skin` and choose **Preview**.
3. Review the plugin and choose **Trust and install**, then turn it on.
4. In **Voice Satellite > Appearance**, set **Assistant 1 skin** to **Feder und Tinte** and press **Preview**.

Needs Kiosk Satellite 2026.10.45 or newer.

## Building

`python3 tools/test.py` and `python3 tools/build.py` need JDK 17+ and an Android SDK with a platform and build-tools. Releases are built and published by GitHub Actions: bump `version` in `kiosk-satellite-plugin.json` and push to `main`.

## Credits

- Plugin SDK and build tools in `sdk/` and `tools/`: [kiosk-satellite-plugins](https://github.com/jxlarrea/kiosk-satellite-plugins), Apache-2.0.
- Font: IM Fell English by Igino Marini, SIL Open Font License 1.1 (`assets/fonts/OFL.txt`).
- Not affiliated with Kiosk Satellite or Home Assistant.
