package org.endercore.android.operator;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class GamePackageManager {
    public static final String PACKAGE_NAME = "com.mojang.minecraftpe";

    private final Context context;

    public GamePackageManager(Context context) {
        this.context = context;
    }

    // Not cached: the game can be installed, updated or removed while this process is alive
    public GamePackage getGamePackage() {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        List<File> availableApkFiles = new ArrayList<>();
        availableApkFiles.add(new File(applicationInfo.sourceDir));
        if (applicationInfo.splitSourceDirs != null) {
            for (String splitSourceDir : applicationInfo.splitSourceDirs) {
                availableApkFiles.add(new File(splitSourceDir));
            }
        }

        return new GamePackage(
                GamePackage.SourceKind.INSTALLED_PACKAGE,
                packageInfo.packageName,
                packageInfo.versionName,
                packageInfo.versionCode,
                applicationInfo.sourceDir,
                availableApkFiles,
                "Installed " + packageInfo.versionName
        );
    }
}
