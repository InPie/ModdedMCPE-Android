package org.endercore.android.operator.instance.model;

public class InstanceSource {
    private InstanceSourceType type;

    // For REMOTE_APK
    private String url;
    private String label;

    // For MANAGED_APK
    private String origin;

    // For EXTERNAL_APK
    private String apkPath;

    // For INSTALLED_PACKAGE
    private String packageName;

    public InstanceSource() {
    }

    public InstanceSource(InstanceSourceType type) {
        this.type = type;
    }

    public InstanceSourceType getType() {
        return type;
    }

    public void setType(InstanceSourceType type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getApkPath() {
        return apkPath;
    }

    public void setApkPath(String apkPath) {
        this.apkPath = apkPath;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
}
