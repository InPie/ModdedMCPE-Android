package org.endercore.android.operator.instance;

import android.util.Log;

import org.endercore.android.exception.LauncherException;
import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.operator.GamePackage;
import org.endercore.android.utils.CPUArch;
import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GamePackageBuilder {
    private static final String TAG = "GamePackageBuilder";
    private static final String BUILD_MARKER_FILE_NAME = "build_complete";

    private final IFileEnvironment fileEnvironment;
    private final String instanceId;

    public GamePackageBuilder(IFileEnvironment fileEnvironment, String instanceId) {
        this.fileEnvironment = fileEnvironment;
        this.instanceId = instanceId;
    }

    public void build(GamePackage gamePackage) throws LauncherException {
        Log.d(TAG, "Starting GamePackageBuilder for instance: " + instanceId);

        // Set Workspace
        fileEnvironment.setActiveWorkspace(instanceId);

        File dexDir = new File(fileEnvironment.getCodeCacheDirPathForDex());
        File nativeDir = new File(fileEnvironment.getCodeCacheDirPathForNativeLib());
        File dexOptDir = new File(fileEnvironment.getCodeCacheDirPathForDexOpt());
        File assetsDir = new File(fileEnvironment.getCodeCacheDirPathForGameAssets());

        // Written last: a missing marker means an unfinished build
        File buildMarker = getBuildMarkerFile();
        buildMarker.delete();

        try {
            extractGameFiles(gamePackage, dexDir, nativeDir, dexOptDir, assetsDir);
            FileUtils.writeAtomic(buildMarker, getPackageFingerprint(gamePackage));
        } catch (IOException e) {
            clearCache(dexDir, nativeDir, assetsDir);
            throw new LauncherException("Failed to extract game files: " + e.getMessage(), e);
        } catch (LauncherException e) {
            clearCache(dexDir, nativeDir, assetsDir);
            throw e;
        }

        Log.d(TAG, "GamePackageBuilder finished successfully.");
    }

    public boolean isBuildComplete(GamePackage gamePackage) {
        File buildMarker = getBuildMarkerFile();
        try {
            return buildMarker.isFile() && getPackageFingerprint(gamePackage).equals(FileUtils.readFileAsString(buildMarker));
        } catch (IOException e) {
            Log.w(TAG, "Failed to read build marker: " + buildMarker.getAbsolutePath(), e);
            return false;
        }
    }

    private File getBuildMarkerFile() {
        return new File(fileEnvironment.getInstanceCacheDirPath(instanceId), BUILD_MARKER_FILE_NAME);
    }

    private String getPackageFingerprint(GamePackage gamePackage) {
        return gamePackage.getVersionCode() + ":" + new File(gamePackage.getBaseApkPath()).length();
    }

    private void clearCache(File... cacheDirs) {
        Log.d(TAG, "Clearing cache directories...");
        for (File cacheDir : cacheDirs) {
            FileUtils.removeFiles(cacheDir);
        }
    }

    private void extractGameFiles(GamePackage gamePackage, File dexDir, File nativeDir, File dexOptDir, File assetsDir) throws LauncherException, IOException {
        // Clear old cache
        clearCache(dexDir, nativeDir, assetsDir);

        if (!dexDir.exists() && !dexDir.mkdirs()) {
            throw new LauncherException("Failed to create dex cache directory: " + dexDir.getAbsolutePath());
        }
        if (!nativeDir.exists() && !nativeDir.mkdirs()) {
            throw new LauncherException("Failed to create native libs cache directory: " + nativeDir.getAbsolutePath());
        }
        if (!dexOptDir.exists() && !dexOptDir.mkdirs()) {
            throw new LauncherException("Failed to create dex opt cache directory: " + dexOptDir.getAbsolutePath());
        }

        List<File> allApkFiles = gamePackage.getAvailableApkFiles();
        if (allApkFiles == null || allApkFiles.isEmpty()) {
            throw new LauncherException("No APK files available in GamePackage.");
        }

        Log.d(TAG, "Finding target ABI...");

        // Find target arch
        String targetArch = findTargetArch(allApkFiles);
        if (targetArch == null) {
            throw new LauncherException("Failed to find supported ABI in the game package.");
        }
        Log.d(TAG, "Selected ABI: " + targetArch);

        Log.d(TAG, "Extracting native libraries...");

        // Extract All Native Libs for target ABI
        for (File apkFile : allApkFiles) {
            try (ZipFile zipFile = new ZipFile(apkFile)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith("lib/" + targetArch + "/") && entryName.endsWith(".so")) {
                        String libName = entryName.substring(entryName.lastIndexOf('/') + 1);
                        File destFile = new File(nativeDir, libName);
                        if (!destFile.exists()) {
                            FileUtils.copy(zipFile.getInputStream(entry), destFile);
                            Log.d(TAG, "Extracted native lib: " + libName);
                        }
                    }
                }
            }
        }

        File mainGameLib = new File(nativeDir, "libminecraftpe.so");
        if (!mainGameLib.isFile()) {
            throw new LauncherException("Critical error: main game library (libminecraftpe.so) was not found for the selected ABI (" + targetArch + "). The APK might be corrupted or missing required libraries for this architecture.");
        }

        Log.d(TAG, "Extracting dex files...");

        // Extract Dex Files
        for (int i = 0; i < 10; ++i) {
            String dexName = "classes" + (i == 0 ? "" : i) + ".dex";
            boolean copied = extractFileFromApks(allApkFiles, dexName, new File(dexDir, dexName));
            if (copied) {
                Log.d(TAG, "Extracted dex file: " + dexName);
            }
        }

        if (!new File(dexDir, "classes.dex").isFile()) {
            throw new LauncherException("Critical error: classes.dex was not found in the game package.");
        }

        if (gamePackage.isVersion015AndAbove()) {
            Log.d(TAG, "Extracting all game assets...");
            if (!assetsDir.exists() && !assetsDir.mkdirs()) {
                throw new LauncherException("Failed to create assets cache directory: " + assetsDir.getAbsolutePath());
            }
            for (File apkFile : allApkFiles) {
                try (ZipFile zipFile = new ZipFile(apkFile)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith("assets/")) {
                            File destFile = new File(assetsDir, name.substring("assets/".length()));
                            if (entry.isDirectory()) {
                                destFile.mkdirs();
                            } else {
                                File parent = destFile.getParentFile();
                                if (parent != null && !parent.exists()) {
                                    parent.mkdirs();
                                }
                                FileUtils.copy(zipFile.getInputStream(entry), destFile);
                            }
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Skipped assets extraction for older game version.");
        }
    }

    private String findTargetArch(List<File> apkFiles) {
        String[] supportedAbis = CPUArch.getSystemSupportedAbis();
        for (String abiItem : supportedAbis) {
            for (File apkFile : apkFiles) {
                try (ZipFile zipFile = new ZipFile(apkFile)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.getName().startsWith("lib/" + abiItem) && CPUArch.isEnderCoreSupportedAbi(abiItem)) {
                            return abiItem;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private boolean extractFileFromApks(List<File> apkFiles, String zipPath, File destFile) throws IOException {
        for (File apkFile : apkFiles) {
            try (ZipFile zipFile = new ZipFile(apkFile)) {
                ZipEntry entry = zipFile.getEntry(zipPath);
                if (entry != null) {
                    FileUtils.copy(zipFile.getInputStream(entry), destFile);
                    return true;
                }
            }
        }
        return false;
    }
}
