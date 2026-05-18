package com.mojang.minecraftpe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.PackageInfo;
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
import java.util.LinkedHashMap;
import java.util.Map;

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
    private String instanceId = null;

    // legacy mcpe 0.1-0.6
    private int legacyUserInputStatus = -1;
    private String[] legacyUserInputText = null;
    private Boolean legacyUseProfile06 = null;

    // logging for debug
    private String gameApkPath = null;
    private int getAssetsLogCount = 0;
    private int getApplicationContextLogCount = 0;
    private int getFileDataLogCount = 0;
    private int getImageDataLogCount = 0;

    private static final String EXTRA_INSTANCE_ID = "org.endercore.android.extra.INSTANCE_ID";
    // legacy mcpe 0.1-0.6
    private static final String EXTRA_LEGACY_INPUT_VALUES = "me.effently.moddedmcpe.extra.LEGACY_INPUT_VALUES";
    private static final String PREF_PREFIX = "legacy_mcpe_options_";
    private static final int DIALOG_CREATE_NEW_WORLD = 1;
    private static final int DIALOG_MAINMENU_OPTIONS = 3;
    private static final int DIALOG_RENAME_MP_WORLD = 4;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        prepareGameWindow();

        ArrayList<String> patchAssetsPath = getIntent().getStringArrayListExtra("ENDERCORE-PATCH-ASSETS");
        instanceDataPath = getIntent().getStringExtra("ENDERCORE-PATCH-DATA");
        instanceId = getIntent().getStringExtra(EXTRA_INSTANCE_ID);
        
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

    public String getExternalStoragePath() {
        return getPatchExternalDataPath();
    }

    public String getInstanceID() {
        return instanceId;
    }

    private File getInstanceDir() {
        if (instanceDataPath == null) {
            return null;
        }
        File dataDir = new File(instanceDataPath);
        return dataDir.getParentFile();
    }






    /////////////////////////////////////////////////////
    // ------------ PATH OVERRIDES 0.14<= ------------ //
    /////////////////////////////////////////////////////

    // --- Legacy path overrides: for 0.14 and below ---
    // and just in case ...
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

    /*
     * Called by Native code (endercore.cpp):
     */
    //  get our patched AssetManager
    public AssetManager getPatchAssetManager() {
        return patchAssetManager != null ? patchAssetManager : getAssets();
    }
    //  get the instance internal data path
    public String getPatchInternalDataPath() {
        return instanceDataPath != null ? instanceDataPath : getFilesDir().getAbsolutePath();
    }
    //  get the instance external data path
    public String getPatchExternalDataPath() {
        return instanceDataPath != null ? instanceDataPath : getFilesDir().getAbsolutePath();
    }

    // --- END: Legacy path overrides ---



    ///////////////////////////////////////////////
    // ------------ FOR LEGACY MCPE ------------ //
    ///////////////////////////////////////////////

    // --- Legacy methods for MCPE 0.1-0.6 UI ---
    public void initiateUserInput(int id) {
        legacyUserInputText = null;
        legacyUserInputStatus = -1;
    }

    public int getUserInputStatus() {
        return legacyUserInputStatus;
    }

    public String[] getUserInputString() {
        return legacyUserInputText;
    }

    public void displayDialog(int dialogId) {
        if (instanceId == null || instanceId.length() == 0) {
            Log.e("EnderCore-AgentMain", "Legacy dialog requested without instance id!");
            legacyUserInputStatus = 0;
            return;
        }

        Intent intent = new Intent();
        intent.putExtra(EXTRA_INSTANCE_ID, instanceId);

        if (dialogId == DIALOG_CREATE_NEW_WORLD) {
            intent.setClassName(getPackageName(), "me.effently.moddedmcpe.legacy_mcpe.activities.LegacyCreateWorldActivity");
        } else if (dialogId == DIALOG_MAINMENU_OPTIONS) {
            intent.setClassName(getPackageName(), "me.effently.moddedmcpe.legacy_mcpe.activities.LegacyGameOptionsActivity");
        } else if (dialogId == DIALOG_RENAME_MP_WORLD) {
            intent.setClassName(getPackageName(), "me.effently.moddedmcpe.legacy_mcpe.activities.LegacyRenameWorldActivity");
        } else {
            Log.w("EnderCore-AgentMain", "Unsupported legacy dialog id: " + dialogId);
            legacyUserInputStatus = 0;
            return;
        }

        try {
            startActivityForResult(intent, dialogId);
        } catch (Throwable throwable) {
            Log.e("EnderCore-AgentMain", "Failed to start legacy dialog " + dialogId, throwable);
            legacyUserInputStatus = 0;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DIALOG_MAINMENU_OPTIONS) {
            legacyUserInputStatus = 1;
            return;
        }

        if (requestCode == DIALOG_CREATE_NEW_WORLD || requestCode == DIALOG_RENAME_MP_WORLD) {
            if (resultCode == RESULT_OK && data != null) {
                legacyUserInputText = data.getStringArrayExtra(EXTRA_LEGACY_INPUT_VALUES);
                legacyUserInputStatus = 1;
            } else {
                legacyUserInputText = null;
                legacyUserInputStatus = 0;
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public String[] getOptionStrings() { // options
        boolean is06 = isLegacyProfile06();
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        SharedPreferences prefs = getSharedPreferences(getLegacyPrefsName(), MODE_PRIVATE);

        putString(options, prefs, "mp_username", "Steve");
        putBoolean(options, prefs, "mp_server_visible_default", true);
        putBoolean(options, prefs, "gfx_fancygraphics", false);
        putBoolean(options, prefs, "gfx_lowquality", false);
        putString(options, "ctrl_sensitivity", sensitivityToGameValue(prefs.getInt("ctrl_sensitivity", 50)));
        putBoolean(options, prefs, "ctrl_invertmouse", false);
        putBoolean(options, prefs, "ctrl_islefthanded", false);
        putBoolean(options, prefs, "ctrl_usetouchscreen", true);
        if (is06) {
            putBoolean(options, prefs, "ctrl_usetouchjoypad", false);
        }
        putBoolean(options, prefs, "feedback_vibration", true);
        if (is06) {
            boolean peaceful = prefs.getBoolean("game_difficultypeaceful", false);
            putString(options, "game_difficulty", peaceful ? "0" : "2");
        }

        String[] out = new String[options.size() * 2];
        int index = 0;
        for (Map.Entry<String, String> entry : options.entrySet()) {
            out[index++] = entry.getKey();
            out[index++] = entry.getValue();
        }
        return out;
    }

    private void putString(LinkedHashMap<String, String> options, SharedPreferences prefs, String key, String defaultValue) {
        putString(options, key, prefs.getString(key, defaultValue));
    }

    private void putString(LinkedHashMap<String, String> options, String key, String value) {
        options.put(key, value == null ? "" : value);
    }

    private void putBoolean(LinkedHashMap<String, String> options, SharedPreferences prefs, String key, boolean defaultValue) {
        options.put(key, Boolean.toString(prefs.getBoolean(key, defaultValue)));
    }

    private String sensitivityToGameValue(int sensitivity) {
        return Double.toString(0.01d * sensitivity);
    }

    private String getLegacyPrefsName() {
        if (instanceId == null || instanceId.length() == 0) {
            return PREF_PREFIX + "unknown";
        }
        return PREF_PREFIX + instanceId;
    }

    private boolean isLegacyProfile06() {
        if (legacyUseProfile06 != null) {
            return legacyUseProfile06.booleanValue();
        }

        String versionName = getGameVersionName();
        boolean is06 = true;
        if (versionName != null && versionName.startsWith("0.")) {
            int minor = parseMinorVersion(versionName);
            is06 = minor >= 6;
        }
        legacyUseProfile06 = Boolean.valueOf(is06);
        Log.i("EnderCore-AgentMain", "MCPE gui profile: " + (is06 ? "0.6.1" : "0.1.3")
                + ", versionName=" + versionName);
        return is06;
    }

    private int parseMinorVersion(String versionName) {
        int firstDot = versionName.indexOf('.');
        if (firstDot < 0) {
            return 6;
        }
        int secondDot = versionName.indexOf('.', firstDot + 1);
        String minor = secondDot < 0
                ? versionName.substring(firstDot + 1)
                : versionName.substring(firstDot + 1, secondDot);
        try {
            return Integer.parseInt(minor);
        } catch (NumberFormatException exception) {
            return 6;
        }
    }

    private String getGameVersionName() {
        if (gameApkPath == null || gameApkPath.length() == 0) {
            return null;
        }

        PackageInfo packageInfo = getPackageManager().getPackageArchiveInfo(gameApkPath, 0);
        if (packageInfo == null) {
            Log.w("EnderCore-AgentMain", "Failed to read MCPE package information from " + gameApkPath);
            return null;
        }
        
        return packageInfo.versionName;
    }
    // --- END: Legacy methods for MCPE 0.1-0.6 UI ---



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
