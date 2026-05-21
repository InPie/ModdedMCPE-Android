package org.endercore.android.operator.instance;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.endercore.android.exception.LauncherException;
import org.endercore.android.exception.NModException;
import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.mod.nmod.NMod;
import org.endercore.android.mod.nmod.overrider.FileOverrider;
import org.endercore.android.mod.nmod.overrider.JsonOverrider;
import org.endercore.android.mod.nmod.overrider.TextOverrider;
import org.endercore.android.operator.GamePackage;
import org.endercore.android.operator.NModManager;
import org.endercore.android.utils.FileUtils;
import org.endercore.android.utils.NModJsonBean;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NModPreparer {
    private static final String TAG = "NModPreparer";
    private static final String STATE_FILE_NAME = "nmods_state.json";

    private final IFileEnvironment fileEnvironment;
    private final NModManager nModManager;
    private final String instanceId;
    private final Gson gson;

    public NModPreparer(IFileEnvironment fileEnvironment, NModManager nModManager, String instanceId) {
        this.fileEnvironment = fileEnvironment;
        this.nModManager = nModManager;
        this.instanceId = instanceId;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void prepare(GamePackage gamePackage) throws LauncherException {
        Log.d(TAG, "Starting NModPreparer for instance: " + instanceId);

        fileEnvironment.setActiveWorkspace(instanceId);

        List<NMod> enabledNMods = nModManager.getEnabledNMods();
        List<String> currentNModIds = new ArrayList<>();
        for (NMod nMod : enabledNMods) {
            currentNModIds.add(nMod.getUUID());
        }

        File nModsCacheDir = new File(fileEnvironment.getCodeCacheDirPathForNMods());
        if (!nModsCacheDir.exists() && !nModsCacheDir.mkdirs()) {
            throw new LauncherException("Failed to create NMods cache directory: " + nModsCacheDir.getAbsolutePath());
        }

        File stateFile = new File(nModsCacheDir, STATE_FILE_NAME);
        boolean needsRebuild = true;

        if (stateFile.exists()) {
            try (FileReader reader = new FileReader(stateFile)) {
                List<String> previousNModIds = gson.fromJson(reader, TypeToken.getParameterized(List.class, String.class).getType());
                if (previousNModIds != null && previousNModIds.equals(currentNModIds)) {
                    Log.d(TAG, "NMods state has not changed. Skipping rebuild.");
                    needsRebuild = false;
                } else {
                    Log.d(TAG, "NMods state changed. Rebuild required.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to read NMods state. Rebuild required.", e);
            }
        } else {
            Log.d(TAG, "NMods state file not found. Rebuild required.");
        }

        if (!needsRebuild) {
            return;
        }

        File assetsDir = new File(fileEnvironment.getCodeCacheDirPathForNModsAssets());
        Log.d(TAG, "Clearing assets cache directory: " + assetsDir.getAbsolutePath());
        FileUtils.removeFiles(assetsDir);

        if (!assetsDir.mkdirs()) {
            throw new LauncherException("Failed to create assets directory: " + assetsDir.getAbsolutePath());
        }

        if (enabledNMods.isEmpty()) {
            Log.d(TAG, "No NMods enabled. Just clearing and updating state.");
            updateStateFile(stateFile, currentNModIds);
            return;
        }

        Log.d(TAG, "Extracting base assets from game apk...");
        List<File> allApkFiles = gamePackage.getAvailableApkFiles();
        for (File apkFile : allApkFiles) {
            try (ZipFile zipFile = new ZipFile(apkFile)) {
                Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("assets/")) {
                        File destFile = new File(assetsDir, name); // .substring("assets/".length())
                        if (entry.isDirectory()) {
                            destFile.mkdirs();
                        } else {
                            destFile.getParentFile().mkdirs();
                            FileUtils.copy(zipFile.getInputStream(entry), destFile);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        Log.d(TAG, "Applying NMod overrides...");
        for (NMod nmod : enabledNMods) {
            Log.d(TAG, "Applying overrides for NMod: " + nmod.getUUID());
            try {
                NModJsonBean.GameSupportData gameSupport = null;
                for (NModJsonBean.GameSupportData gameSupportData : nmod.getPackageManifest().game_supports) {
                    for (String version : gameSupportData.target_game_versions) {
                        if (Pattern.matches(version, gamePackage.getVersionName())) {
                            gameSupport = gameSupportData;
                            break;
                        }
                    }
                    if (gameSupport != null) break;
                }

                if (gameSupport == null) {
                    throw new NModException("Cannot find a proper game version support for NMod " + nmod.getUUID() + ".");
                }

                if (gameSupport.file_overrides != null) {
                    for (NModJsonBean.FileOverrideData fileOverrideData : gameSupport.file_overrides) {
                        FileOverrider overrider = new FileOverrider(assetsDir);
                        overrider.performOverride(new File(nmod.getGameSupportDir(gameSupport.name), "assets"), fileOverrideData.path, null);
                    }
                }
                if (gameSupport.json_overrides != null) {
                    for (NModJsonBean.JsonOverrideData jsonOverrideData : gameSupport.json_overrides) {
                        JsonOverrider overrider = new JsonOverrider(assetsDir);
                        overrider.performOverride(new File(nmod.getGameSupportDir(gameSupport.name), "assets"), jsonOverrideData.path, jsonOverrideData.mode);
                    }
                }
                if (gameSupport.text_overrides != null) {
                    for (NModJsonBean.TextOverrideData textOverrideData : gameSupport.text_overrides) {
                        TextOverrider overrider = new TextOverrider(assetsDir);
                        overrider.performOverride(new File(nmod.getGameSupportDir(gameSupport.name), "assets"), textOverrideData.path, textOverrideData.mode);
                    }
                }
            } catch (NModException e) {
                Log.e(TAG, "Failed to apply overrides for NMod: " + nmod.getUUID(), e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error applying overrides for NMod: " + nmod.getUUID(), e);
            }
        }

        updateStateFile(stateFile, currentNModIds);
        Log.d(TAG, "NModPreparer finished successfully.");
    }

    private void updateStateFile(File stateFile, List<String> currentNModIds) {
        try (FileWriter writer = new FileWriter(stateFile)) {
            gson.toJson(currentNModIds, writer);
            Log.d(TAG, "NMods state updated.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write NMods state file.", e);
        }
    }
}
