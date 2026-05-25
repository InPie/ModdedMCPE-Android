package com.mojang.minecraftpe;

import android.content.Intent;
import android.content.pm.Signature;
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
import java.util.Properties;

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
    private AssetManager patchResourceAssetManager = null;
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

    private static final String TAG = "EnderCore-AgentMain";
    private static final String EXTRA_INSTANCE_ID = "org.endercore.android.extra.INSTANCE_ID";
    // legacy mcpe 0.1-0.6
    private static final String EXTRA_LEGACY_INPUT_VALUES = "me.effently.moddedmcpe.extra.LEGACY_INPUT_VALUES";
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
            Log.e(TAG,"Value ENDERCORE-PATCH-ASSETS in Intent defines to be null.");
            Log.e(TAG,"Force close AgentMainActivity.");
            finish();
        }
        else {
            if (patchAssetsPath.size() > 1) {
                gameApkPath = patchAssetsPath.get(1);
                GameContextBridge.init(this, gameApkPath);
            }

            Log.i(TAG, "Start patching assets.");
            try {
                patchAssetManager = AssetManager.class.newInstance();
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Failed to create new instance of AssetManager.");
                e.printStackTrace();
                Log.e(TAG, "Force close AgentMainActivity.");
                finish();
                return;
            } catch (InstantiationException e) {
                Log.e(TAG, "Failed to create new instance of AssetManager.");
                e.printStackTrace();
                Log.e(TAG, "Force close AgentMainActivity.");
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
                    Log.i(TAG, "Patched [" + path + "], cookie=" + cookie
                            + ", exists=" + file.exists() + ", canRead=" + file.canRead()
                            + ", length=" + file.length() + ".");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to patch assets.");
                t.printStackTrace();
                Log.e(TAG, "Force close AgentMainActivity.");
                finish();
                return;
            }
            Log.i(TAG, "Assets patched successfully.");
            Log.i(TAG, "Starting to patch Resources.");
            Resources resourcesOriginal = super.getResources();
            patchResourceAssetManager = buildPatchedResourceAssetManager(patchAssetsPath);
            patchResources = new Resources(
                    patchResourceAssetManager != null ? patchResourceAssetManager : patchAssetManager,
                    resourcesOriginal.getDisplayMetrics(),
                    resourcesOriginal.getConfiguration());
            Log.i(TAG, "Resources patching succeed.");
            Log.i(TAG, "Paths: code=" + getPackageCodePath() + ", resource=" + getPackageResourcePath());
            // logAssetState();
            Log.i(TAG, "Patching finished.Activity creating.");
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
            //     Log.i(TAG, "getAssets()");
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

    private String getGameVersionName() {
        return GameContextBridge.getGameVersionName();
    }

    private AssetManager buildPatchedResourceAssetManager(ArrayList<String> patchAssetsPath) {
        if (patchAssetsPath == null || patchAssetsPath.isEmpty()) {
            return patchAssetManager;
        }

        ArrayList<String> resourcePaths = collectResourceAssetPaths(patchAssetsPath);
        if (resourcePaths.isEmpty()) {
            Log.w(TAG, "No dedicated resource APKs found, falling back to merged assets manager.");
            return patchAssetManager;
        }

        try {
            AssetManager resourceAssetManager = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            for (String path : resourcePaths) {
                int cookie = (Integer) addAssetPath.invoke(resourceAssetManager, path);
                Log.i(TAG, "Resource path [" + path + "], cookie=" + cookie + ".");
            }
            return resourceAssetManager;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to build dedicated resources AssetManager, using merged assets manager.", throwable);
            return patchAssetManager;
        }
    }

    private ArrayList<String> collectResourceAssetPaths(ArrayList<String> patchAssetsPath) {
        ArrayList<String> resourcePaths = new ArrayList<>();
        boolean gameOnlyResources = GameContextBridge.needsGameOnlyResources();
        String launcherSourceDir = super.getApplicationInfo().sourceDir;

        for (String path : patchAssetsPath) {
            if (path == null || !path.endsWith(".apk")) {
                continue;
            }
            if (gameOnlyResources && path.equals(launcherSourceDir)) {
                continue;
            }
            resourcePaths.add(path);
        }

        Log.i(TAG, "Resource mode=" + (gameOnlyResources ? "game-only" : "merged")
                + ", apkCount=" + resourcePaths.size());
        return resourcePaths;
    }

    public boolean hasXboxSupport() {
        return GameContextBridge.hasXboxSupport();
    }

    public boolean needsGameOnlyResources() {
        return GameContextBridge.needsGameOnlyResources();
    }

    public static String getCurrentGamePackageName() {
        return GameContextBridge.getGamePackageName();
    }

    public static String getCurrentGameVersionName() {
        return GameContextBridge.getGameVersionName();
    }

    public static int getCurrentGameVersionCode() {
        return GameContextBridge.getGameVersionCode();
    }

    public static Signature[] getCurrentGameSignatures() {
        return GameContextBridge.getGameSignatures();
    }

    public static String getCurrentGameApkPath() {
        return GameContextBridge.getGameApkPath();
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
            Log.e(TAG, "Legacy dialog requested without instance id!");
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
            Log.w(TAG, "Unsupported legacy dialog id: " + dialogId);
            legacyUserInputStatus = 0;
            return;
        }

        try {
            startActivityForResult(intent, dialogId);
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to start legacy dialog " + dialogId, throwable);
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

    private Properties readLegacyOptions() {
        Properties options = new Properties();
        if (instanceDataPath == null) {
            return options;
        }

        File optionsFile = new File(instanceDataPath, "options.txt");
        if (!optionsFile.isFile()) {
            return options;
        }

        try (FileInputStream input = new FileInputStream(optionsFile)) {
            options.load(input);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to read legacy options from "
                    + optionsFile.getAbsolutePath(), throwable);
        }
        return options;
    }

    public String[] getOptionStrings() { // options
        boolean is06 = isLegacyProfile06();
        ArrayList<String> options = new ArrayList<>();
        Properties savedOptions = readLegacyOptions();

        putString(options, "mp_username", savedOptions.getProperty("mp_username", "Steve"));
        putBoolean(options, savedOptions, "mp_server_visible_default", true);
        putBoolean(options, savedOptions, "gfx_fancygraphics", false);
        putBoolean(options, savedOptions, "gfx_lowquality", false);
        putString(options, "ctrl_sensitivity", sensitivityToGameValue(getInt(savedOptions, "ctrl_sensitivity", 50)));
        putBoolean(options, savedOptions, "ctrl_invertmouse", false);
        putBoolean(options, savedOptions, "ctrl_islefthanded", false);
        putBoolean(options, savedOptions, "ctrl_usetouchscreen", true);
        if (is06) {
            putBoolean(options, savedOptions, "ctrl_usetouchjoypad", false);
        }
        putBoolean(options, savedOptions, "feedback_vibration", true);
        if (is06) {
            putString(options, "game_difficulty", savedOptions.getProperty("game_difficulty", "2"));
        }

        return options.toArray(new String[options.size()]);
    }

    private void putString(ArrayList<String> options, String key, String value) {
        options.add(key);
        options.add(value == null ? "" : value);
    }

    private void putBoolean(ArrayList<String> options, Properties savedOptions, String key, boolean defaultValue) {
        putString(options, key, savedOptions.getProperty(key, Boolean.toString(defaultValue)));
    }

    private int getInt(Properties options, String key, int defaultValue) {
        try {
            return Integer.parseInt(options.getProperty(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String sensitivityToGameValue(int sensitivity) {
        return Double.toString(0.01d * sensitivity);
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
        Log.i(TAG, "MCPE gui profile: " + (is06 ? "0.6.1" : "0.1.3")
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
            Log.i(TAG, "Asset probe OK: " + path + ", available=" + available);
        } catch (Throwable throwable) {
            Log.e(TAG, "Asset probe failed: " + path + " -> " + throwable);
        }
    }

    private void logAssetList(String path) {
        try {
            String[] items = patchAssetManager.list(path);
            Log.i(TAG, "Asset list " + path + " count=" + (items == null ? -1 : items.length));
            if (items != null) {
                for (int i = 0; i < items.length && i < 8; i++) {
                    Log.i(TAG, "Asset list item: " + path + "/" + items[i]);
                }
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "Asset list failed: " + path + " -> " + throwable);
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
        return getImageData(filename, false);
    }

    @Override
    public int[] getImageData(String filename, boolean forced) {
        Bitmap bitmap = null;

        if (filename.startsWith("/")) {
            bitmap = BitmapFactory.decodeFile(filename);
        } else {
            // try internal assets
            if (patchAssetManager != null) {
                try (InputStream inputStream = patchAssetManager.open(filename)) {
                    bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                } catch (Exception ignored) {}
            }

            // check data directory
            if (bitmap == null && forced) {
                // "forced" means look into game data dir
                File external = new File(instanceDataPath, filename);
                if (external.exists()) {
                    bitmap = BitmapFactory.decodeFile(external.getAbsolutePath());
                }
            }
        }

        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[(width * height) + 2];
        pixels[0] = width;
        pixels[1] = height;
        bitmap.getPixels(pixels, 2, width, 0, 0, width, height);
        bitmap.recycle();
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
            Log.i(TAG, "getFileDataBytes(" + filename + ") -> " + source
                    + ", length=" + length + suffix);
            getFileDataLogCount++;
        }
    }

    private void logImageDataResult(String filename, boolean success) {
        if (getImageDataLogCount < 40 || isNeededAsset(filename)) {
            Log.i(TAG, "getImageData(" + filename + ") -> " + success);
            getImageDataLogCount++;
        }
    }

    private boolean isNeededAsset(String filename) {
        return filename.indexOf("models") >= 0
                || filename.indexOf("resourcepacks") >= 0;
    }

    // END: logging for debug
}
