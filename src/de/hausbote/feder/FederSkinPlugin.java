// SPDX-License-Identifier: Apache-2.0
package de.hausbote.feder;

import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/** Entry class. The plugin only draws a Voice Satellite skin. */
public final class FederSkinPlugin implements KioskPlugin {
    private final FederSkin skin = new FederSkin();

    @Override public void start(PluginHost host, Map<String, Object> settings) {
        skin.settings(settings);
        skin.start(host);
    }

    @Override public void configure(Map<String, Object> settings) {
        skin.settings(settings);
    }

    @Override public void execute(String command, Map<String, Object> arguments) {
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        skin.onEvent(event, payload);
    }

    @Override public void stop() {
        skin.stop();
    }
}
