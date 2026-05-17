package org.endercore.android.interf;

public interface IFileEnvironment {
    String getCodeCacheDirPath();

    String getEnderCoreDirPath();

    String getInstancesDirPath();

    String getOptionsFilePath();

    String getNModsDirPath();

    String getNModDirPathFor(String uuid);

    String getCodeCacheDirPathForDex();

    String getCodeCacheDirPathForNativeLib();

    String getInnerGameStorageDir();

    String getCodeCacheDirPathForDexOpt();

    String getCodeCacheDirPathForNMods();

    String getCodeCacheDirPathForGameAssets();

    String getCodeCacheDirPathForNModsAssets();

    void setActiveWorkspace(String instanceId);

    String getActiveWorkspaceId();

    String getInstanceCacheDirPath(String instanceId);

    String getInstanceNModsCacheDirPath(String instanceId);
}
