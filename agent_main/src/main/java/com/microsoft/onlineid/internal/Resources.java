package com.microsoft.onlineid.internal;

import android.content.Context;
import com.mojang.minecraftpe.GameContextBridge;

public class Resources {
    private final Context appContext;

    public Resources(Context appContext) {
        this.appContext = appContext;
    }

    public String getString(String name) {
        return GameContextBridge.getString(appContext, name);
    }

    public int getDimensionPixelSize(String name) {
        return GameContextBridge.getDimensionPixelSize(appContext, name);
    }

    public int getLayout(String name) {
        return GameContextBridge.getIdentifier(appContext, name, "layout");
    }

    public int getId(String name) {
        return GameContextBridge.getIdentifier(appContext, name, "id");
    }

    public int getMenu(String name) {
        return GameContextBridge.getIdentifier(appContext, name, "menu");
    }

    public static String getString(Context appContext, String name) {
        return new Resources(appContext).getString(name);
    }

    public String getSdkVersion() {
        return getString("sdk_version_name");
    }

    public static String getSdkVersion(Context appContext) {
        return new Resources(appContext).getSdkVersion();
    }
}
