package org.endercore.android.operator;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.endercore.android.interf.IOptionsData;

import java.io.IOException;
import java.util.ArrayList;

public final class OptionsManager {
    private static final String TAG = "EnderCore-Options";

    private OptionsJsonBean optionsJsonBean;
    private final IOptionsData optionsData;

    public OptionsManager(IOptionsData optionsData) {
        this.optionsData = optionsData;
        this.optionsJsonBean = loadOptions();
    }

    // Runs on start of every process, so it must not fail or write
    private OptionsJsonBean loadOptions() {
        OptionsJsonBean loaded = null;
        try {
            loaded = new Gson().fromJson(optionsData.getJSONAsString(new Gson().toJson(new OptionsJsonBean())), OptionsJsonBean.class);
        } catch (IOException | JsonParseException e) {
            Log.e(TAG, "Failed to read options, using defaults.", e);
        }
        if (loaded == null)
            loaded = new OptionsJsonBean();

        ArrayList<NModOptionsElement> validNMods = new ArrayList<>();
        if (loaded.installed_nmods != null) {
            for (NModOptionsElement element : loaded.installed_nmods) {
                if (element != null && element.uuid != null)
                    validNMods.add(element);
            }
        }
        loaded.installed_nmods = validNMods.toArray(new NModOptionsElement[0]);
        return loaded;
    }

    public void setUseNMods(boolean val) {
        optionsJsonBean.use_nmods = val;
    }

    public boolean getUseNMods() {
        return optionsJsonBean.use_nmods;
    }

    public void setAutoLicense(boolean val) {
        optionsJsonBean.auto_license = val;
    }

    public boolean getAutoLicense() {
        return optionsJsonBean.auto_license;
    }

    public void setRedirectGameDir(boolean val) {
        optionsJsonBean.redirect_game_dir = true; //val;
    }

    public boolean getRedirectGameDir() {
        return true; //optionsJsonBean.redirect_game_dir;
    }

    public void setUnlockMjscript(boolean val) {
        optionsJsonBean.unlock_mjscript = false; //val;
    }

    public boolean getUnlockMjscript() {
        return false; //optionsJsonBean.unlock_mjscript;
    }

    public NModOptionsElement[] getInstalledNMods() {
        return optionsJsonBean.installed_nmods;
    }

    public void removeNModElement(String uuid) {
        ArrayList<NModOptionsElement> newArrayList = new ArrayList<>();
        for (int i = 0; i < optionsJsonBean.installed_nmods.length; ++i) {
            NModOptionsElement element = optionsJsonBean.installed_nmods[i];
            if (!element.uuid.equals(uuid))
                newArrayList.add(element);
        }
        NModOptionsElement[] newArray = new NModOptionsElement[newArrayList.size()];
        int j = 0;
        for (NModOptionsElement element : newArrayList) {
            newArray[j++] = element;
        }
        optionsJsonBean.installed_nmods = newArray;
    }

    public void setNModElementEnabled(String uuid, boolean enabled) {
        for (int i = 0; i < optionsJsonBean.installed_nmods.length; ++i)
            if (optionsJsonBean.installed_nmods[i].uuid.equals(uuid))
                optionsJsonBean.installed_nmods[i].enabled = enabled;
    }

    public boolean isNModElementEnabled(String uuid) {
        for (int i = 0; i < optionsJsonBean.installed_nmods.length; ++i)
            if (optionsJsonBean.installed_nmods[i].uuid.equals(uuid))
                return optionsJsonBean.installed_nmods[i].enabled;
        return false;
    }

    public boolean isNModElementExists(String uuid) {
        for (int i = 0; i < optionsJsonBean.installed_nmods.length; ++i)
            if (optionsJsonBean.installed_nmods[i].uuid.equals(uuid))
                return true;
        return false;
    }

    public void addNewNModElement(String uuid, boolean enabled) {
        NModOptionsElement[] newArray = new NModOptionsElement[optionsJsonBean.installed_nmods.length + 1];
        System.arraycopy(optionsJsonBean.installed_nmods, 0, newArray, 0, optionsJsonBean.installed_nmods.length);
        newArray[optionsJsonBean.installed_nmods.length] = new NModOptionsElement();
        newArray[optionsJsonBean.installed_nmods.length].uuid = uuid;
        newArray[optionsJsonBean.installed_nmods.length].enabled = true;
        optionsJsonBean.installed_nmods = newArray;
    }

    public void saveDataToFile() throws IOException {
        optionsData.setJSONAsString(new Gson().toJson(optionsJsonBean));
    }

    public void reset() {
        optionsJsonBean = new OptionsJsonBean();
    }

    public final static class NModOptionsElement {
        public String uuid = null;
        public boolean enabled = true;
    }

    private final static class OptionsJsonBean {
        private boolean use_nmods = false;
        private boolean auto_license = true;
        private boolean redirect_game_dir = true;
        private boolean unlock_mjscript = false;
        private NModOptionsElement[] installed_nmods = new NModOptionsElement[0];
    }
}
