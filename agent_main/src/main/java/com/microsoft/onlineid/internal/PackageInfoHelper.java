package com.microsoft.onlineid.internal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import com.mojang.minecraftpe.GameContextBridge;

public class PackageInfoHelper {
    public static final String AuthenticatorPackageName = "com.microsoft.msa.authenticator";

    public static int getCurrentAppVersionCode(Context applicationContext) {
        String currentGamePackageName = GameContextBridge.getGamePackageName();
        if (currentGamePackageName != null) {
            return GameContextBridge.getGameVersionCode();
        }
        try {
            return applicationContext.getPackageManager()
                    .getPackageInfo(applicationContext.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public static String getCurrentAppVersionName(Context applicationContext) {
        String currentGameVersionName = GameContextBridge.getGameVersionName();
        if (currentGameVersionName != null) {
            return currentGameVersionName;
        }
        try {
            return applicationContext.getPackageManager()
                    .getPackageInfo(applicationContext.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static String getAppVersionName(Context applicationContext, String packageName) {
        String currentGamePackageName = GameContextBridge.getGamePackageName();
        if (packageName != null && packageName.equalsIgnoreCase(currentGamePackageName)) {
            return GameContextBridge.getGameVersionName();
        }
        try {
            PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static boolean isAuthenticatorApp(String packageName) {
        return AuthenticatorPackageName.equalsIgnoreCase(packageName);
    }

    public static boolean isRunningInAuthenticatorApp(Context applicationContext) {
        return isAuthenticatorApp(getResolvedCurrentPackageName(applicationContext));
    }

    public static boolean isAuthenticatorAppInstalled(Context applicationContext) {
        try {
            applicationContext.getPackageManager().getPackageInfo(AuthenticatorPackageName, 128);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isCurrentApp(String packageName, Context applicationContext) {
        return getResolvedCurrentPackageName(applicationContext).equalsIgnoreCase(packageName);
    }

    public static Signature[] getCurrentAppSignatures(Context applicationContext) {
        return getAppSignatures(applicationContext, getResolvedCurrentPackageName(applicationContext));
    }

    public static Signature[] getAppSignatures(Context applicationContext, String packageName) {
        String currentGamePackageName = GameContextBridge.getGamePackageName();
        if (packageName != null && packageName.equalsIgnoreCase(currentGamePackageName)) {
            Signature[] signatures = GameContextBridge.getGameSignatures();
            return signatures != null ? signatures : new Signature[0];
        }
        try {
            return applicationContext.getPackageManager().getPackageInfo(packageName, 64).signatures;
        } catch (PackageManager.NameNotFoundException e) {
            return new Signature[0];
        }
    }

    private static String getResolvedCurrentPackageName(Context applicationContext) {
        String currentGamePackageName = GameContextBridge.getGamePackageName();
        return currentGamePackageName != null ? currentGamePackageName : applicationContext.getPackageName();
    }
}
