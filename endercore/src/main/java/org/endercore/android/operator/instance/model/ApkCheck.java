package org.endercore.android.operator.instance.model;

// Result of the check of a downloaded or imported APK
public class ApkCheck {
    private String apkSha256;
    private String signerSha256;
    private String signerName;

    public ApkCheck() {
    }

    public String getApkSha256() {
        return apkSha256;
    }

    public void setApkSha256(String apkSha256) {
        this.apkSha256 = apkSha256;
    }

    public String getSignerSha256() {
        return signerSha256;
    }

    public void setSignerSha256(String signerSha256) {
        this.signerSha256 = signerSha256;
    }

    public String getSignerName() {
        return signerName;
    }

    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }
}
