package org.endercore.android.operator;

import java.io.File;
import java.util.List;

public class GamePackage {
    public enum SourceKind {
        INSTALLED_PACKAGE,
        APK_FILE
    }

    private final SourceKind sourceKind;
    private final String packageName;
    private final String versionName;
    private final int versionCode;
    private final String baseApkPath;
    private final List<File> availableApkFiles;
    private final String sourceLabel;

    public GamePackage(SourceKind sourceKind, String packageName, String versionName, int versionCode, String baseApkPath, List<File> availableApkFiles, String sourceLabel) {
        this.sourceKind = sourceKind;
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.baseApkPath = baseApkPath;
        this.availableApkFiles = availableApkFiles;
        this.sourceLabel = sourceLabel;
    }

    public SourceKind getSourceKind() {
        return sourceKind;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getBaseApkPath() {
        return baseApkPath;
    }

    public List<File> getAvailableApkFiles() {
        return availableApkFiles;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public boolean isVersion015AndAbove() {
        // if (versionName == null || versionName.trim().isEmpty()) {
        //     return false;
        // }

        if (versionName.startsWith("0.")) {
            try {
                String[] parts = versionName.split("\\.");
                if (parts.length > 1) {
                    int minor = Integer.parseInt(parts[1]);
                    if (minor >= 15) {
                        return true;
                    }
                }

                return false;
            } catch (Exception ignored) {
                return false;
            }
        } else {
            // 1.0.0 and above
            return true;
        }
    }
}
