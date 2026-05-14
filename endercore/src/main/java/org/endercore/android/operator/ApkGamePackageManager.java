package org.endercore.android.operator;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ApkGamePackageManager {

    public static GamePackage getGamePackageFromApk(Context context, File apkFile) throws Exception {
        if (!apkFile.exists() || !apkFile.isFile()) {
            throw new Exception("APK file does not exist or is not a file: " + apkFile.getAbsolutePath());
        }

        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);

        if (packageInfo == null) {
            throw new Exception("Failed to parse package archive info from: " + apkFile.getAbsolutePath());
        }

        String packageName = packageInfo.packageName;
        String versionName = packageInfo.versionName;
        int versionCode = packageInfo.versionCode;
        String baseApkPath = apkFile.getAbsolutePath();

        List<File> availableApkFiles = new ArrayList<>();
        availableApkFiles.add(apkFile);

        return new GamePackage(
                GamePackage.SourceKind.APK_FILE,
                packageName,
                versionName,
                versionCode,
                baseApkPath,
                availableApkFiles,
                "APK " + versionName
        );
    }
}
