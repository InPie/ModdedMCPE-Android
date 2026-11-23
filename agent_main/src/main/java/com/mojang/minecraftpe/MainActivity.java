package com.mojang.minecraftpe;

import android.app.NativeActivity;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.core.graphics.Insets;

public abstract class MainActivity extends NativeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        throw new RuntimeException("Stub!");
    }

    public AssetManager getAssets() {
        throw new RuntimeException("Stub!");
    }

    public Resources getResources() {
        throw new RuntimeException("Stub!");
    }

    public byte[] getFileDataBytes(String filename) {
        throw new RuntimeException("Stub!");
    }

    public int[] getImageData(String filename) {
        throw new RuntimeException("Stub!");
    }

    public int[] getImageData(String filename, boolean forced) {
        throw new RuntimeException("Stub!");
    }

    public void initializeXboxLive(long xalInitArgs, long xblInitArgs) {
        throw new RuntimeException("Stub!");
    }

    public int getScreenWidth() {
        throw new RuntimeException("Stub!");
    }

    public int getScreenHeight() {
        throw new RuntimeException("Stub!");
    }

    public float getKeyboardHeight() {
        throw new RuntimeException("Stub!");
    }

    public float getPixelsPerMillimeter() {
        throw new RuntimeException("Stub!");
    }

    // -- for 1.17+ begin --
    public int getDisplayWidth() {
        throw new RuntimeException("Stub!");
    }

    public int getDisplayHeight() {
        throw new RuntimeException("Stub!");
    }
    // -- for 1.17+ end --

    // -- for 1.26.62+ begin --
    public int getPlatformDpi() {
        throw new RuntimeException("Stub!");
    }

    // Declared by com.google.androidgamesdk.GameActivity
    public Insets getWindowInsets(int type) {
        throw new RuntimeException("Stub!");
    }

    public Insets getWaterfallInsets() {
        throw new RuntimeException("Stub!");
    }
    // -- for 1.26.62+ end --

    native void nativeResize(int width, int height); // for 1.16+

    native void nativeOnPickImageCanceled(long callback);

    native void nativeOnPickImageSuccess(long callback, String path);
}
