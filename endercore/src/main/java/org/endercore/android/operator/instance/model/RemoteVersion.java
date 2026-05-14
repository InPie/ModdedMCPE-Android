package org.endercore.android.operator.instance.model;

import com.google.gson.annotations.SerializedName;

public class RemoteVersion {
    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    @SerializedName("isNotTested")
    private boolean isNotTested;

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
}
