package org.endercore.android.operator.instance.model;

public class PackageSnapshot {
    private String packageName;
    private String versionName;
    private int versionCode;
    private long apkSize;
    private String apkSha256;
    private long lastReadAt;

    public PackageSnapshot() {
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public long getApkSize() {
        return apkSize;
    }

    public void setApkSize(long apkSize) {
        this.apkSize = apkSize;
    }

    public String getApkSha256() {
        return apkSha256;
    }

    public void setApkSha256(String apkSha256) {
        this.apkSha256 = apkSha256;
    }

    public long getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(long lastReadAt) {
        this.lastReadAt = lastReadAt;
    }
}
