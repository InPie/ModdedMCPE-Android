package org.endercore.android.operator.instance.model;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

public class GameResolutionTest {
    @Test
    public void instanceWithoutSettingsUsesDefault() {
        assertEquals(GameResolution.DEFAULT_SCALE, GameResolution.getScale(new GameInstance()), 0f);
    }

    @Test
    public void savedScaleSurvivesInstanceFile() {
        Gson gson = new Gson();
        for (float scale : GameResolution.SCALES) {
            GameInstance instance = new GameInstance();
            GameResolution.setScale(instance, scale);

            GameInstance loaded = gson.fromJson(gson.toJson(instance), GameInstance.class);

            assertEquals(scale, GameResolution.getScale(loaded), 0f);
        }
    }

    @Test
    public void scaleOutOfRangeUsesDefault() {
        GameInstance instance = new GameInstance();
        GameResolution.setScale(instance, 1.5f);

        assertEquals(GameResolution.DEFAULT_SCALE, GameResolution.getScale(instance), 0f);
    }

    @Test
    public void scaleThatIsNotNumberUsesDefault() {
        GameInstance instance = new GameInstance();
        instance.setSettings(new JsonObject());
        instance.getSettings().addProperty(GameResolution.SETTINGS_KEY, "half");

        assertEquals(GameResolution.DEFAULT_SCALE, GameResolution.getScale(instance), 0f);
    }

    @Test
    public void defaultIsShownAsSixtySevenPercent() {
        assertEquals(67, GameResolution.toPercent(GameResolution.DEFAULT_SCALE));
    }
}
