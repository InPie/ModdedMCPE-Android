package com.mojang.minecraftpe;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.lang.reflect.Method;

public final class GameContextBridge {
    private static final String TAG = "EnderCore-GameCtx";

    private static volatile Context appContext;
    private static volatile String gameApkPath;
    private static volatile String gamePackageName;
    private static volatile String gameVersionName;
    private static volatile int gameVersionCode;
    private static volatile Signature[] gameSignatures;
    private static volatile Resources gameResources;
    private static volatile String gameResourcesApkPath;

    private GameContextBridge() {
    }

    public static void init(Context context, String apkPath) {
        Context resolvedContext = context == null ? null : context.getApplicationContext();
        appContext = resolvedContext != null ? resolvedContext : context;

        boolean apkChanged = apkPath == null || !apkPath.equals(gameApkPath);
        gameApkPath = apkPath;
        gamePackageName = null;
        gameVersionName = null;
        gameVersionCode = 0;
        gameSignatures = null;

        if (apkChanged) {
            gameResources = null;
            gameResourcesApkPath = null;
        }

        if (context == null || isBlank(apkPath)) {
            return;
        }

        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, 0);
            if (packageInfo == null) {
                Log.w(TAG, "Failed to cache MCPE package info from " + apkPath);
                return;
            }

            gamePackageName = packageInfo.packageName;
            gameVersionName = packageInfo.versionName;
            gameVersionCode = packageInfo.versionCode;
            Log.i(TAG, "Cached MCPE package info: package=" + gamePackageName
                    + ", version=" + gameVersionName);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to cache MCPE package info from " + apkPath, throwable);
        }
    }

    public static String getGameApkPath() {
        return gameApkPath;
    }

    public static String getGamePackageName() {
        return gamePackageName;
    }

    public static String getGameVersionName() {
        return gameVersionName;
    }

    public static int getGameVersionCode() {
        return gameVersionCode;
    }

    public static Signature[] getGameSignatures() {
        Signature[] signatures = gameSignatures;
        if (signatures != null) {
            return signatures;
        }

        Context context = appContext;
        String apkPath = gameApkPath;
        if (context == null || isBlank(apkPath)) {
            return new Signature[0];
        }

        synchronized (GameContextBridge.class) {
            signatures = gameSignatures;
            if (signatures != null) {
                return signatures;
            }

            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(
                        apkPath,
                        PackageManager.GET_SIGNATURES
                );
                if (packageInfo == null || packageInfo.signatures == null) {
                    gameSignatures = new Signature[0];
                } else {
                    gameSignatures = packageInfo.signatures;
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to load MCPE signatures from " + apkPath, throwable);
                gameSignatures = new Signature[0];
            }
            return gameSignatures;
        }
    }

    public static boolean hasXboxSupport() {
        String versionName = gameVersionName;
        return isBlank(versionName) || compareVersion(versionName, 0, 15) >= 0;
    }

    public static boolean needsGameOnlyResources() {
        String versionName = gameVersionName;
        return !isBlank(versionName) && compareVersion(versionName, 1, 17) >= 0;
    }

    public static Resources getGameResources(Context context) {
        String apkPath = gameApkPath;
        if (isBlank(apkPath)) {
            return null;
        }

        Resources cached = gameResources;
        if (cached != null && apkPath.equals(gameResourcesApkPath)) {
            return cached;
        }

        synchronized (GameContextBridge.class) {
            cached = gameResources;
            if (cached != null && apkPath.equals(gameResourcesApkPath)) {
                return cached;
            }

            Context resourceContext = context != null ? context : appContext;
            if (resourceContext == null) {
                return null;
            }

            try {
                AssetManager assetManager = AssetManager.class.newInstance();
                Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
                Integer cookie = (Integer) addAssetPath.invoke(assetManager, apkPath);
                if (cookie == null || cookie.intValue() == 0) {
                    return null;
                }

                Resources baseResources = resourceContext.getResources();
                Resources createdResources = new Resources(
                        assetManager,
                        baseResources.getDisplayMetrics(),
                        baseResources.getConfiguration()
                );
                gameResources = createdResources;
                gameResourcesApkPath = apkPath;
                return createdResources;
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to create MCPE resources for " + apkPath, throwable);
                return null;
            }
        }
    }

    public static String getResolvedPackageName(Context context) {
        if (!isBlank(gamePackageName)) {
            return gamePackageName;
        }
        Context resolvedContext = context != null ? context : appContext;
        return resolvedContext == null ? null : resolvedContext.getPackageName();
    }

    public static int getIdentifier(Context context, String name, String type) {
        if (isBlank(name) || isBlank(type)) {
            return 0;
        }

        Resources resources = getGameResources(context);
        String packageName = gamePackageName;
        if (resources != null && !isBlank(packageName)) {
            int identifier = resources.getIdentifier(name, type, packageName);
            if (identifier != 0) {
                return identifier;
            }
        }

        Context resolvedContext = context != null ? context : appContext;
        if (resolvedContext == null) {
            return 0;
        }

        try {
            return resolvedContext.getResources().getIdentifier(name, type, resolvedContext.getPackageName());
        } catch (Resources.NotFoundException exception) {
            return 0;
        }
    }

    public static String getString(Context context, String name) {
        if (isBlank(name)) {
            return null;
        }

        Resources resources = getGameResources(context);
        String packageName = gamePackageName;
        if (resources != null && !isBlank(packageName)) {
            int identifier = resources.getIdentifier(name, "string", packageName);
            if (identifier != 0) {
                try {
                    return resources.getString(identifier);
                } catch (Resources.NotFoundException ignored) {
                }
            }
        }

        Context resolvedContext = context != null ? context : appContext;
        if (resolvedContext == null) {
            return null;
        }

        try {
            int identifier = resolvedContext.getResources().getIdentifier(
                    name,
                    "string",
                    resolvedContext.getPackageName()
            );
            return identifier == 0 ? null : resolvedContext.getResources().getString(identifier);
        } catch (Resources.NotFoundException exception) {
            return null;
        }
    }

    public static int getDimensionPixelSize(Context context, String name) {
        if (isBlank(name)) {
            return 0;
        }

        Resources resources = getGameResources(context);
        String packageName = gamePackageName;
        if (resources != null && !isBlank(packageName)) {
            int identifier = resources.getIdentifier(name, "dimen", packageName);
            if (identifier != 0) {
                try {
                    return resources.getDimensionPixelSize(identifier);
                } catch (Resources.NotFoundException ignored) {
                }
            }
        }

        Context resolvedContext = context != null ? context : appContext;
        if (resolvedContext == null) {
            return 0;
        }

        try {
            int identifier = resolvedContext.getResources().getIdentifier(
                    name,
                    "dimen",
                    resolvedContext.getPackageName()
            );
            return identifier == 0 ? 0 : resolvedContext.getResources().getDimensionPixelSize(identifier);
        } catch (Resources.NotFoundException exception) {
            return 0;
        }
    }

    private static int compareVersion(String versionName, int major, int minor) {
        String[] parts = versionName.split("\\.");
        if (parts.length < 2) {
            return -1;
        }

        int currentMajor;
        int currentMinor;
        try {
            currentMajor = Integer.parseInt(parts[0]);
            currentMinor = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            Log.w(TAG, "Failed to parse version name: " + versionName, exception);
            return -1;
        }

        if (currentMajor != major) {
            return currentMajor > major ? 1 : -1;
        }
        if (currentMinor != minor) {
            return currentMinor > minor ? 1 : -1;
        }
        return 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.length() == 0;
    }
}
