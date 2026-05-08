package com.mojang.minecraftpe;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.ArrayList;

public class AgentMainActivity extends com.mojang.minecraftpe.MainActivity {
    // fullsreen code from 0.11, ...Platform19#setSystemUiVisibility(5894)
    private static final int IMMERSIVE_SYSTEM_UI_FLAGS =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private AssetManager patchAssetManager = null;
    private Resources patchResources = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        prepareGameWindow();

        ArrayList<String> patchAssetsPath = getIntent().getStringArrayListExtra("ENDERCORE-PATCH-ASSETS");

        if(patchAssetsPath == null)
        {
            Log.e("EnderCore-AgentMain","Value ENDERCORE-PATCH-ASSETS in Intent defines to be null.");
            Log.e("EnderCore-AgentMain","Force close AgentMainActivity.");
            finish();
        }
        else {
            Log.i("EnderCore-AgentMain", "Start patching assets.");
            try {
                patchAssetManager = AssetManager.class.newInstance();
            } catch (IllegalAccessException e) {
                Log.e("EnderCore-AgentMain", "Failed to create new instance of AssetManager.");
                e.printStackTrace();
                Log.e("EnderCore-AgentMain", "Force close AgentMainActivity.");
                finish();
                return;
            } catch (InstantiationException e) {
                Log.e("EnderCore-AgentMain", "Failed to create new instance of AssetManager.");
                e.printStackTrace();
                Log.e("EnderCore-AgentMain", "Force close AgentMainActivity.");
                finish();
                return;
            }

            try {
                int arrayListSize = patchAssetsPath.size();
                Method method = AssetManager.class.getMethod("addAssetPath", String.class);
                for (int i = 0; i < arrayListSize; ++i) {
                    String path = patchAssetsPath.get(i);
                    Log.i("EnderCore-AgentMain", "Patched [" + path + "].");
                    method.invoke(patchAssetManager, path);
                }
            } catch (Throwable t) {
                Log.e("EnderCore", "Failed to patch assets.");
                t.printStackTrace();
                Log.e("EnderCore-AgentMain", "Force close AgentMainActivity.");
                finish();
                return;
            }
            Log.i("EnderCore-AgentMain", "Assets patched successfully.");
            Log.i("EnderCore-AgentMain", "Starting to patch Resources.");
            Resources resourcesOriginal = super.getResources();
            patchResources = new Resources(patchAssetManager, resourcesOriginal.getDisplayMetrics(), resourcesOriginal.getConfiguration());
            Log.i("EnderCore-AgentMain", "Resources patching succeed.");
            Log.i("EnderCore-AgentMain", "Patching finished.Activity creating.");
            super.onCreate(savedInstanceState);
            applyImmersiveMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            applyImmersiveMode();
    }

    @Override
    public AssetManager getAssets() {
        if(patchAssetManager != null)
            return patchAssetManager;
        return super.getAssets();
    }

    @Override
    public Resources getResources() {
        if(patchResources != null)
            return patchResources;
        return super.getResources();
    }

    @Override
    public Context getApplicationContext() {
        if(patchAssetManager != null)
            return this;
        return super.getApplicationContext();
    }

    private void prepareGameWindow() {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        View decorView = window.getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> applyImmersiveMode());
        applyImmersiveMode();
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_SYSTEM_UI_FLAGS);
    }
}
