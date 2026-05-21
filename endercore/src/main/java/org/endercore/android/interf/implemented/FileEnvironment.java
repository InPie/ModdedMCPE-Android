package org.endercore.android.interf.implemented;

import android.content.Context;

import org.endercore.android.interf.IFileEnvironment;

import java.io.File;

public class FileEnvironment implements IFileEnvironment {
    private final String codeCacheDirPath;
    private final String enderCoreDirPath;
    private final String gameDirPath;

    public final static String DIR_DATA_ROOT = "launcher_data";
    public final static String DIR_GAME_DATA = "minecraft_game";

    public final static String DIR_NMODS = "nmods";
    public final static String DIR_NATIVE_LIBS = "native_libs";
    public final static String DIR_DEX_LIBS = "dex_libs";
    public final static String DIR_DEX_OPT = "opt";
    public final static String DIR_ASSETS = "game_assets";

    public final static String DATA_FILE_ENDERCORE_OPTIONS = "options.json";

    private String activeInstanceId = null;

    public FileEnvironment(Context context) {
        codeCacheDirPath = context.getCodeCacheDir().getPath();
        enderCoreDirPath = context.getDir(DIR_DATA_ROOT, 0).getPath();
        gameDirPath = context.getDir(DIR_GAME_DATA, 0).getPath();
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
        return getEnderCoreDirPath() + java.io.File.separator + "instances";
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
    public String getInnerGameStorageDir() {
        checkWorkspace();
        return getInstancesDirPath() + File.separator + activeInstanceId + File.separator + "data";
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
    public String getCodeCacheDirPathForGameAssets() {
        checkWorkspace();
        return getInstanceCacheDirPath(activeInstanceId) + File.separator + DIR_ASSETS;
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
        return getInstancesDirPath() + File.separator + instanceId + File.separator + "cache";
    }

    @Override
    public String getInstanceNModsCacheDirPath(String instanceId) {
        return getInstancesDirPath() + File.separator + instanceId + File.separator + "nmods_cache";
    }
}
