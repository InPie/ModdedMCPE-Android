package com.google.android.gms.common.internal;

import android.content.Context;

import com.mojang.minecraftpe.GameContextBridge;

public class StringResourceValueReader {
    private final Context context;

    public StringResourceValueReader(Context context) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.context = context;
    }

    public String getString(String name) {
        return GameContextBridge.getString(context, name);
    }
}
