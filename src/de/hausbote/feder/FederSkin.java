// SPDX-License-Identifier: Apache-2.0
package de.hausbote.feder;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Follows the Voice Satellite turn and shows the Hausbote broadsheet while
 * this skin is picked. Events arrive on the plugin worker thread; the view
 * reads the shared {@link Turn} on the main thread.
 */
final class FederSkin {
    static final String KEY = "feder_skin";
    /** Docked card distance from the bottom edge, in dp. */
    static final int DOCK_INSET = 24;
    static final String[] EVENTS = {"voice.overlay", "voice.level", "voice.text", "voice.appearance", "voice.state"};

    /** What the view draws. Written on the worker thread, read on the main thread. */
    static final class Turn {
        volatile String state = "idle";
        volatile float level;
        volatile String command = "";
        volatile String answer = "";
        volatile String tool = "";
        volatile boolean announcement;
        volatile String theme = "auto";
        /** Null means skin default. */
        volatile Float background;
        volatile float textScale = 1f;
        volatile boolean reactive = true;
        volatile Typeface regular;
        volatile Typeface italic;
        volatile Typeface smallCaps;
        volatile Typeface fraktur;
        volatile Typeface fat;
        volatile Typeface oswald;
        /** Place in the masthead's date line. */
        volatile String ort = "Ostfildern";
    }

    private final Turn turn = new Turn();
    private PluginHost host;
    private int generation;
    private boolean selected;
    private boolean skinHeard;
    private boolean appearanceHeard;
    private boolean stateHeard;
    private boolean following;
    private String overlay = "hidden";
    private String drawn;
    /** Showing view, touched on the main thread only. */
    private BoteView view;

    synchronized void start(PluginHost host) {
        this.host = host;
        int current = ++generation;
        loadFonts(host);
        try {
            host.subscribe("voice.skin");
        } catch (IllegalArgumentException | UnsupportedOperationException olderHost) {
            return;
        }
        try {
            host.executeCommand("getVoiceState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
                synchronized (FederSkin.this) {
                    if (current != generation || !ok || !(data instanceof Map)) return;
                    Map<?, ?> state = (Map<?, ?>) data;
                    if (!skinHeard) selected = Boolean.TRUE.equals(state.get("skinSelected"));
                    if ("hidden".equals(overlay) && state.get("overlay") instanceof String) overlay = (String) state.get("overlay");
                    if (!stateHeard && state.get("state") instanceof String) turn.state = (String) state.get("state");
                    follow();
                    update();
                }
            });
        } catch (UnsupportedOperationException olderHost) {
            // The voice.skin event alone still brings the skin up.
        }
    }

    private void loadFonts(PluginHost host) {
        turn.regular = font(host, "fonts/IMFellEnglish-Regular.ttf", Typeface.SERIF);
        turn.italic = font(host, "fonts/IMFellEnglish-Italic.ttf", Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        turn.smallCaps = font(host, "fonts/IMFellEnglishSC.ttf", Typeface.SERIF);
        turn.fraktur = font(host, "fonts/UnifrakturMaguntia.ttf", Typeface.SERIF);
        turn.fat = font(host, "fonts/AbrilFatface.ttf", Typeface.create(Typeface.SERIF, Typeface.BOLD));
        turn.oswald = font(host, "fonts/Oswald-Medium.ttf", Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    }

    /** Plugin settings: the place in the masthead. */
    void settings(Map<String, Object> values) {
        Object ort = values == null ? null : values.get("ort");
        turn.ort = ort instanceof String ? ((String) ort).trim() : "Ostfildern";
    }

    private static Typeface font(PluginHost host, String name, Typeface fallback) {
        try {
            String path = host.assetPath(name);
            if (path != null) return Typeface.createFromFile(path);
        } catch (Throwable missing) {
            // Fall back to the system serif.
        }
        return fallback;
    }

    private void follow() {
        if (host == null || selected == following) return;
        following = selected;
        int current = generation;
        for (String event : EVENTS) {
            try {
                if (following) host.subscribe(event);
                else host.unsubscribe(event);
            } catch (IllegalArgumentException olderHost) {
                // An event this Kiosk Satellite does not have.
            }
        }
        if (!following) return;
        try {
            host.executeCommand("getVoiceAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
                synchronized (FederSkin.this) {
                    if (current == generation && ok && data instanceof Map && !appearanceHeard) appearance((Map<?, ?>) data);
                }
            });
            host.executeCommand("getVoiceText", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
                synchronized (FederSkin.this) {
                    if (current == generation && ok && data instanceof Map && turn.command.isEmpty() && turn.answer.isEmpty()) words((Map<?, ?>) data);
                }
            });
        } catch (UnsupportedOperationException olderHost) {
            // Events alone still bring the skin up.
        }
    }

    synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null || payload == null) return;
        if ("ks.voice.skin".equals(event)) {
            selected = Boolean.TRUE.equals(payload.get("selected"));
            skinHeard = true;
            follow();
        } else if ("ks.voice.overlay".equals(event)) {
            overlay = String.valueOf(payload.get("overlay"));
        } else if ("ks.voice.level".equals(event)) {
            Object value = payload.get("level");
            turn.level = value instanceof Number ? ((Number) value).floatValue() : 0f;
            return;
        } else if ("ks.voice.state".equals(event)) {
            Object value = payload.get("state");
            if (value instanceof String) turn.state = (String) value;
            stateHeard = true;
            return;
        } else if ("ks.voice.text".equals(event)) {
            words(payload);
            return;
        } else if ("ks.voice.appearance".equals(event)) {
            appearanceHeard = true;
            appearance(payload);
            return;
        } else {
            return;
        }
        update();
    }

    private void appearance(Map<?, ?> settings) {
        turn.theme = settings.get("theme") instanceof String ? (String) settings.get("theme") : "auto";
        turn.background = settings.get("background") instanceof Number ? ((Number) settings.get("background")).floatValue() : null;
        turn.textScale = settings.get("textScale") instanceof Number ? ((Number) settings.get("textScale")).floatValue() : 1f;
        turn.reactive = !Boolean.FALSE.equals(settings.get("reactive"));
        poke();
    }

    private void words(Map<?, ?> text) {
        turn.command = text.get("command") instanceof String ? (String) text.get("command") : "";
        turn.answer = text.get("answer") instanceof String ? (String) text.get("answer") : "";
        turn.announcement = Boolean.TRUE.equals(text.get("announcement"));
        Object tools = text.get("tools");
        turn.tool = tools instanceof List && !((List<?>) tools).isEmpty()
            ? String.valueOf(((List<?>) tools).get(((List<?>) tools).size() - 1)) : "";
        poke();
    }

    /** New words or settings may change the docked card's height. */
    private void poke() {
        final BoteView current = view;
        if (current != null) current.post(current::textChanged);
    }

    private void update() {
        String want = selected && ("full".equals(overlay) || "docked".equals(overlay)) ? overlay : null;
        if (want == null ? drawn == null : want.equals(drawn)) return;
        drawn = want;
        if (want == null) {
            host.hideOverlay(KEY);
            turn.level = 0f;
        } else if ("full".equals(want)) {
            host.showOverlay(KEY, animated(OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false), 350), new Factory(false));
        } else {
            host.showOverlay(KEY, animated(OverlaySpec.at(OverlaySpec.BOTTOM).inset(DOCK_INSET).onTop(true).touchable(false).closeOnBack(false), 250), new Factory(true));
        }
    }

    private static OverlaySpec animated(OverlaySpec spec, int ms) {
        try {
            return spec.enter(OverlaySpec.FADE).exit(OverlaySpec.FADE).transitionDuration(ms);
        } catch (LinkageError olderHost) {
            return spec;
        }
    }

    synchronized void stop() {
        if (host != null && drawn != null) {
            try {
                host.hideOverlay(KEY);
            } catch (RuntimeException ignored) {
                // Stopping anyway.
            }
        }
        host = null;
        generation++;
        selected = false;
        skinHeard = false;
        appearanceHeard = false;
        stateHeard = false;
        following = false;
        overlay = "hidden";
        drawn = null;
        turn.level = 0f;
        turn.state = "idle";
        turn.command = turn.answer = turn.tool = "";
        turn.announcement = false;
    }

    private final class Factory implements OverlayFactory {
        private final boolean docked;

        Factory(boolean docked) {
            this.docked = docked;
        }

        @Override public View create(Context context, KsTheme theme) {
            BoteView created = new BoteView(context, theme, turn, docked);
            view = created;
            return created;
        }

        @Override public void onThemeChanged(View changed, KsTheme theme) {
            ((BoteView) changed).setKsTheme(theme);
        }

        @Override public void onDestroy(View destroyed) {
            if (view == destroyed) view = null;
            ((BoteView) destroyed).release();
        }
    }
}
