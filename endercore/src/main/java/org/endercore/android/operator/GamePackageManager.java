package org.endercore.android.operator;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GamePackageManager {
    public static final String PACKAGE_NAME = "com.mojang.minecraftpe";

    private GamePackage gamePackage;
    private boolean gameInstalled;

    public GamePackageManager(Context context) {
        gameInstalled = true;
        try {
            Context gameContext = context.createPackageContext(PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(gameContext.getPackageName(), PackageManager.GET_CONFIGURATIONS);
            
            String versionName = packageInfo.versionName;
            int versionCode = packageInfo.versionCode;
            String packageResourcePath = gameContext.getPackageResourcePath();
            
            List<File> availableApkFiles = new ArrayList<>();
            if (packageResourcePath != null) {
                File resPath = new File(packageResourcePath);
                availableApkFiles.add(resPath); // Always add the base APK
                
                File parentFile = resPath.getParentFile();
                if (parentFile != null) {
                    File[] allApkFiles = parentFile.listFiles();
                    if (allApkFiles != null) {
                        for (File file : allApkFiles) {
                            if (file.isFile() && file.getName().endsWith(".apk") && !file.equals(resPath)) {
                                availableApkFiles.add(file);
                            }
                        }
                    }
                }
            }

            gamePackage = new GamePackage(
                    GamePackage.SourceKind.INSTALLED_PACKAGE,
                    gameContext.getPackageName(),
                    versionName,
                    versionCode,
                    packageResourcePath,
                    availableApkFiles,
                    "Installed " + versionName
            );
        } catch (PackageManager.NameNotFoundException e) {
            gameInstalled = false;
            gamePackage = null;
        }
    }

    public GamePackage getGamePackage() {
        return gamePackage;
    }

    public boolean isGameInstalled() {
        return gameInstalled;
    }

    // Deprecated: use getGamePackage().getVersionName()
    public String getVersionName() {
        return gamePackage != null ? gamePackage.getVersionName() : "";
    }

    // Deprecated: use getGamePackage().getBaseApkPath()
    public String getPackageResourcePath() {
        return gamePackage != null ? gamePackage.getBaseApkPath() : null;
    }
}
