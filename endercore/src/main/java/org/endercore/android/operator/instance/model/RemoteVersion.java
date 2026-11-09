package org.endercore.android.operator.instance.model;

import com.google.gson.annotations.SerializedName;

public class RemoteVersion {
    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    @SerializedName("isNotTested")
    private boolean isNotTested;

    // Optional SHA-256 of the APK
    @SerializedName("sha256")
    private String sha256;

    public RemoteVersion() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isNotTested() {
        return isNotTested;
    }

    public void setNotTested(boolean notTested) {
        isNotTested = notTested;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
}
