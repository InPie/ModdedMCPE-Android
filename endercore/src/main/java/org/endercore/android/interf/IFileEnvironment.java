package org.endercore.android.interf;

public interface IFileEnvironment {
    String getCodeCacheDirPath();

    String getEnderCoreDirPath();

    String getInstancesDirPath();

    String getInstanceDirPath(String instanceId);

    // Sign-in data that all instances read when the launcher shares it
    String getSharedSignInDirPath();

    String getOptionsFilePath();

    String getNModsDirPath();

    String getNModDirPathFor(String uuid);

    String getCodeCacheDirPathForDex();

    String getCodeCacheDirPathForNativeLib();

    String getCodeCacheDirPathForDexOpt();

    String getCodeCacheDirPathForNMods();

    String getCodeCacheFilePathForGameAssets();

    String getCodeCacheDirPathForNModsAssets();

    void setActiveWorkspace(String instanceId);

    String getActiveWorkspaceId();

    String getInstanceCacheDirPath(String instanceId);

    String getInstanceNModsCacheDirPath(String instanceId);
}
