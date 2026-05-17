package com.mojang.minecraftpe;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

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
    private String instanceDataPath = null;

    // logging for debug
    private String gameApkPath = null;
    private int getAssetsLogCount = 0;
    private int getApplicationContextLogCount = 0;
    private int getFileDataLogCount = 0;
    private int getImageDataLogCount = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        prepareGameWindow();

        ArrayList<String> patchAssetsPath = getIntent().getStringArrayListExtra("ENDERCORE-PATCH-ASSETS");
        instanceDataPath = getIntent().getStringExtra("ENDERCORE-PATCH-DATA");
        
        if (instanceDataPath != null) {
            new File(instanceDataPath).mkdirs();
        }

        if(patchAssetsPath == null)
        {
            Log.e("EnderCore-AgentMain","Value ENDERCORE-PATCH-ASSETS in Intent defines to be null.");
            Log.e("EnderCore-AgentMain","Force close AgentMainActivity.");
            finish();
        }
        else {
            if (patchAssetsPath.size() > 1) {
                gameApkPath = patchAssetsPath.get(1);
            }

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
                    int cookie = (Integer) method.invoke(patchAssetManager, path);
                    File file = new File(path);
                    Log.i("EnderCore-AgentMain", "Patched [" + path + "], cookie=" + cookie
                            + ", exists=" + file.exists() + ", canRead=" + file.canRead()
                            + ", length=" + file.length() + ".");
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
            Log.i("EnderCore-AgentMain", "Paths: code=" + getPackageCodePath() + ", resource=" + getPackageResourcePath());
            // logAssetState();
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
        if(patchAssetManager != null) {
            // if (getAssetsLogCount < 5) {
            //     Log.i("EnderCore-AgentMain", "getAssets()");
            //     getAssetsLogCount++;
            // }
            return patchAssetManager;
        }
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
        if(patchAssetManager != null) {
            // if (getApplicationContextLogCount < 5) {
            //     Log.i("EnderCore-AgentMain", "getApplicationContext()");
            //     getApplicationContextLogCount++;
            // }
            return this;
        }
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

    /*
     * Called by Native code (endercore.cpp) to get our patched AssetManager
     */
    public AssetManager getPatchAssetManager() {
        return patchAssetManager != null ? patchAssetManager : getAssets();
    }

    /*
     * C.Native code to get the instance internal data path.
     */
    public String getPatchInternalDataPath() {
        return instanceDataPath != null ? instanceDataPath : getFilesDir().getAbsolutePath();
    }

    /*
     * C.Native code to get the instance external data path.
     */
    public String getExternalStoragePath() {
        return getPatchExternalDataPath();
    }

    // --- Legacy path overrides: for 0.14 and below ---
    @Override
    public File getFilesDir() {
        if (instanceDataPath != null) {
            File f = new File(instanceDataPath);
            if (!f.exists()) f.mkdirs();
            return f;
        }
        return super.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        if (instanceDataPath != null) {
            File cache = new File(instanceDataPath, "cache");
            if (!cache.exists()) cache.mkdirs();
            return cache;
        }
        return super.getCacheDir();
    }
    // --- END: legacy path overrides ---



    /////////////////////////////////////////////////
    // ------------ LOGGING FOR DEBUG ------------ //
    /////////////////////////////////////////////////

    private void logAssetState() {
        logAssetList("images/font");
        logAssetProbe("images/font/default8.png");
        logAssetProbe("images/gui/title.png");
        logAssetList("resourcepacks/vanilla");
        logAssetList("resourcepacks/vanilla/models");
        logAssetProbe("assets/images/font/default8.png");
        logAssetProbe("images/mob/skins/Base/Vanilla.json");
        logAssetProbe("skins/skins.json");
        logAssetProbe("skins/Base/base.json");
    }

    private void logAssetProbe(String path) {
        try {
            InputStream input = patchAssetManager.open(path);
            int available = input.available();
            input.close();
            Log.i("EnderCore-AgentMain", "Asset probe OK: " + path + ", available=" + available);
        } catch (Throwable throwable) {
            Log.e("EnderCore-AgentMain", "Asset probe failed: " + path + " -> " + throwable);
        }
    }

    private void logAssetList(String path) {
        try {
            String[] items = patchAssetManager.list(path);
            Log.i("EnderCore-AgentMain", "Asset list " + path + " count=" + (items == null ? -1 : items.length));
            if (items != null) {
                for (int i = 0; i < items.length && i < 8; i++) {
                    Log.i("EnderCore-AgentMain", "Asset list item: " + path + "/" + items[i]);
                }
            }
        } catch (Throwable throwable) {
            Log.e("EnderCore-AgentMain", "Asset list failed: " + path + " -> " + throwable);
        }
    }

    @Override
    public String getPackageCodePath() {
        if (gameApkPath != null) return gameApkPath;
        return super.getPackageCodePath();
    }

    @Override
    public String getPackageResourcePath() {
        if (gameApkPath != null) return gameApkPath;
        return super.getPackageResourcePath();
    }

    @Override
    public android.content.pm.ApplicationInfo getApplicationInfo() {
        android.content.pm.ApplicationInfo info = super.getApplicationInfo();
        if (gameApkPath != null) {
            android.content.pm.ApplicationInfo newInfo = new android.content.pm.ApplicationInfo(info);
            newInfo.sourceDir = gameApkPath;
            newInfo.publicSourceDir = gameApkPath;
            return newInfo;
        }
        return info;
    }

    @Override
    public byte[] getFileDataBytes(String filename) {
        if (filename == null || filename.length() == 0) {
            return null;
        }

        byte[] data = readAllBytesFromAssets(filename);
        if (data != null) {
            logFileDataResult("asset", filename, data.length, null);
            return data;
        }

        data = readAllBytesFromFile(filename);
        logFileDataResult(data == null ? "missing" : "file", filename, data == null ? 0 : data.length, null);
        return data;
    }

    @Override
    public int[] getImageData(String filename) {
        Bitmap bitmap = BitmapFactory.decodeFile(filename);
        if (bitmap == null && patchAssetManager != null) {
            try {
                InputStream inputStream = patchAssetManager.open(filename);
                bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
            } catch (Throwable throwable) {
                logImageDataResult(filename, false);
                return null;
            }
        }

        if (bitmap == null) {
            logImageDataResult(filename, false);
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[(width * height) + 2];
        pixels[0] = width;
        pixels[1] = height;
        bitmap.getPixels(pixels, 2, width, 0, 0, width, height);
        logImageDataResult(filename, true);
        return pixels;
    }

    private byte[] readAllBytesFromAssets(String filename) {
        if (patchAssetManager == null) {
            return null;
        }

        try {
            return readAllBytes(patchAssetManager.open(filename));
        } catch (Throwable throwable) {
            return null;
        }
    }

    private byte[] readAllBytesFromFile(String filename) {
        try {
            return readAllBytes(new FileInputStream(filename));
        } catch (Throwable throwable) {
            return null;
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws java.io.IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            inputStream.close();
        }
    }

    private void logFileDataResult(String source, String filename, int length, Throwable throwable) {
        if (getFileDataLogCount >= 40 && !isNeededAsset(filename)) {
            return;
        }
        if (getFileDataLogCount < 80) {
            String suffix = throwable == null ? "" : ", error=" + throwable;
            Log.i("EnderCore-AgentMain", "getFileDataBytes(" + filename + ") -> " + source
                    + ", length=" + length + suffix);
            getFileDataLogCount++;
        }
    }

    private void logImageDataResult(String filename, boolean success) {
        if (getImageDataLogCount < 40 || isNeededAsset(filename)) {
            Log.i("EnderCore-AgentMain", "getImageData(" + filename + ") -> " + success);
            getImageDataLogCount++;
        }
    }

    private boolean isNeededAsset(String filename) {
        return filename.indexOf("models") >= 0
                || filename.indexOf("resourcepacks") >= 0;
    }

    // END: logging for debug
}
