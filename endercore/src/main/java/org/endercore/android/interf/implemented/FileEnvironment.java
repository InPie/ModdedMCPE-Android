package org.endercore.android.interf.implemented;

import android.content.Context;

import org.endercore.android.interf.IFileEnvironment;

import java.io.File;

public class FileEnvironment implements IFileEnvironment {
    private final String codeCacheDirPath;
    private final String enderCoreDirPath;

    public final static String DIR_DATA_ROOT = "launcher_data";

    public final static String DIR_NMODS = "nmods";
    public final static String DIR_INSTANCES = "instances";
    public final static String DIR_SHARED_SIGN_IN = "shared_sign_in";
    public final static String DIR_NATIVE_LIBS = "native_libs";
    public final static String DIR_DEX_LIBS = "dex_libs";
    public final static String DIR_DEX_OPT = "opt";
    public final static String DIR_ASSETS = "game_assets";
    public final static String FILE_GAME_ASSETS = "game_assets.zip";

    public final static String DATA_FILE_ENDERCORE_OPTIONS = "options.json";

    private String activeInstanceId = null;

    public FileEnvironment(Context context) {
        codeCacheDirPath = context.getCodeCacheDir().getPath();
        enderCoreDirPath = context.getDir(DIR_DATA_ROOT, 0).getPath();
    }


    @Override
    public String getCodeCacheDirPath() { // deprecated
        return codeCacheDirPath;
    }

    @Override
    public String getEnderCoreDirPath() {
        return enderCoreDirPath;
    }

    @Override
    public String getInstancesDirPath() {
        return getEnderCoreDirPath() + File.separator + DIR_INSTANCES;
    }

    @Override
    public String getInstanceDirPath(String instanceId) {
        return getInstancesDirPath() + File.separator + instanceId;
    }

    @Override
    public String getSharedSignInDirPath() {
        return getEnderCoreDirPath() + File.separator + DIR_SHARED_SIGN_IN;
    }

    @Override
    public String getOptionsFilePath() {
        return getEnderCoreDirPath() + File.separator + DATA_FILE_ENDERCORE_OPTIONS;
    }

    @Override
    public String getNModsDirPath() {
        return getEnderCoreDirPath() + File.separator + DIR_NMODS;
    }

    @Override
    public String getNModDirPathFor(String uuid) {
        return getNModsDirPath() + File.separator + uuid;
    }

    private void checkWorkspace() {
        if (activeInstanceId == null) {
            throw new IllegalStateException("Active instance workspace is not set. Call setActiveWorkspace() first.");
        }
    }

    @Override
    public String getCodeCacheDirPathForDex() {
        checkWorkspace();
        return getInstanceCacheDirPath(activeInstanceId) + File.separator + DIR_DEX_LIBS;
    }

    @Override
    public String getCodeCacheDirPathForNativeLib() {
        checkWorkspace();
        return getInstanceCacheDirPath(activeInstanceId) + File.separator + DIR_NATIVE_LIBS;
    }

    @Override
    public String getCodeCacheDirPathForDexOpt() {
        return getCodeCacheDirPathForDex() + File.separator + DIR_DEX_OPT;
    }

    @Override
    public String getCodeCacheDirPathForNMods() {
        checkWorkspace();
        return getInstanceNModsCacheDirPath(activeInstanceId);
    }

    @Override
    public String getCodeCacheFilePathForGameAssets() {
        checkWorkspace();
        return getInstanceCacheDirPath(activeInstanceId) + File.separator + FILE_GAME_ASSETS;
    }

    @Override
    public String getCodeCacheDirPathForNModsAssets() {
        checkWorkspace();
        return getInstanceNModsCacheDirPath(activeInstanceId) + File.separator + DIR_ASSETS;
    }

    @Override
    public void setActiveWorkspace(String instanceId) {
        this.activeInstanceId = instanceId;
    }

    @Override
    public String getActiveWorkspaceId() {
        return activeInstanceId;
    }

    @Override
    public String getInstanceCacheDirPath(String instanceId) {
        return getInstanceDirPath(instanceId) + File.separator + "cache";
    }

    @Override
    public String getInstanceNModsCacheDirPath(String instanceId) {
        return getInstanceDirPath(instanceId) + File.separator + "nmods_cache";
    }
}
