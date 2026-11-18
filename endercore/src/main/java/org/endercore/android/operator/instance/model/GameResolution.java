package org.endercore.android.operator.instance.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// Share of the screen resolution an instance renders at. The game also gets the screen size and DPI
// reduced by it, so it picks the interface scale of a screen that size.
public final class GameResolution {
    public static final String SETTINGS_KEY = "resolutionScale";
    // The share Huawei APS gives the original game. At full resolution 1.26 picks so large an interface
    // scale on 1080p phones that its first screen doesn't fit.
    public static final float DEFAULT_SCALE = 2f / 3f;
    public static final float[] SCALES = {1f, 0.75f, DEFAULT_SCALE, 0.5f};

    private GameResolution() {
    }

    public static float getScale(GameInstance instance) {
        JsonObject settings = instance.getSettings();
        JsonElement value = settings != null ? settings.get(SETTINGS_KEY) : null;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return DEFAULT_SCALE;
        }
        float scale = value.getAsFloat();
        return scale > 0f && scale <= 1f ? scale : DEFAULT_SCALE;
    }

    public static void setScale(GameInstance instance, float scale) {
        if (instance.getSettings() == null) {
            instance.setSettings(new JsonObject());
        }
        instance.getSettings().addProperty(SETTINGS_KEY, scale);
    }

    public static int toPercent(float scale) {
        return Math.round(scale * 100f);
    }
}
