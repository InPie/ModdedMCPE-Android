package org.endercore.android.operator.instance.model;

import com.google.gson.JsonObject;

public class GameInstance {
    private String id;
    private String name;
    private InstanceSource source;
    private PackageSnapshot packageSnapshot;
    private ApkCheck apkCheck;
    private InstanceState state;
    private JsonObject settings;
    private long createdAt;
    private Long lastPlayedAt;
    // InstanceStorage.LAYOUT of the instance folder, 0 for layout 1
    private int storageLayout;

    public GameInstance() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InstanceSource getSource() {
        return source;
    }

    public void setSource(InstanceSource source) {
        this.source = source;
    }

    public PackageSnapshot getPackageSnapshot() {
        return packageSnapshot;
    }

    public void setPackageSnapshot(PackageSnapshot packageSnapshot) {
        this.packageSnapshot = packageSnapshot;
    }

    public ApkCheck getApkCheck() {
        return apkCheck;
    }

    public void setApkCheck(ApkCheck apkCheck) {
        this.apkCheck = apkCheck;
    }

    public InstanceState getState() {
        return state;
    }

    public void setState(InstanceState state) {
        this.state = state;
    }

    public JsonObject getSettings() {
        return settings;
    }

    public void setSettings(JsonObject settings) {
        this.settings = settings;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastPlayedAt() {
        return lastPlayedAt;
    }

    public void setLastPlayedAt(Long lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }

    public int getStorageLayout() {
        return storageLayout;
    }

    public void setStorageLayout(int storageLayout) {
        this.storageLayout = storageLayout;
    }
}
