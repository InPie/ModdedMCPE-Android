package org.endercore.android.operator.instance;

import android.util.Log;

import org.endercore.android.exception.LauncherException;
import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.operator.GamePackage;
import org.endercore.android.utils.CPUArch;
import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class GamePackageBuilder {
    private static final String TAG = "GamePackageBuilder";
    private static final String BUILD_MARKER_FILE_NAME = "build_complete";
    private static final String ASSETS_ENTRY_PREFIX = "assets/";
    private static final String MANIFEST_ENTRY = "AndroidManifest.xml";

    private final IFileEnvironment fileEnvironment;
    private final String instanceId;
    private final boolean packGameAssets;

    public GamePackageBuilder(IFileEnvironment fileEnvironment, String instanceId, boolean packGameAssets) {
        this.fileEnvironment = fileEnvironment;
        this.instanceId = instanceId;
        this.packGameAssets = packGameAssets;
    }

    public void build(GamePackage gamePackage) throws LauncherException {
        Log.d(TAG, "Starting GamePackageBuilder for instance: " + instanceId);

        // Set Workspace
        fileEnvironment.setActiveWorkspace(instanceId);

        File dexDir = new File(fileEnvironment.getCodeCacheDirPathForDex());
        File nativeDir = new File(fileEnvironment.getCodeCacheDirPathForNativeLib());
        File dexOptDir = new File(fileEnvironment.getCodeCacheDirPathForDexOpt());
        File assetsPack = new File(fileEnvironment.getCodeCacheFilePathForGameAssets());
        File cacheDir = new File(fileEnvironment.getInstanceCacheDirPath(instanceId));

        // Written last: a missing marker means an unfinished build
        File buildMarker = getBuildMarkerFile();
        buildMarker.delete();
        Log.d(TAG, "Clearing cache directory...");
        FileUtils.removeFiles(cacheDir);

        try {
            extractGameFiles(gamePackage, dexDir, nativeDir, dexOptDir, assetsPack);
            FileUtils.writeAtomic(buildMarker, getBuildFingerprint(gamePackage));
        } catch (IOException e) {
            FileUtils.removeFiles(cacheDir);
            throw new LauncherException("Failed to extract game files: " + e.getMessage(), e);
        } catch (LauncherException e) {
            FileUtils.removeFiles(cacheDir);
            throw e;
        }

        Log.d(TAG, "GamePackageBuilder finished successfully.");
    }

    public boolean isBuildComplete(GamePackage gamePackage) {
        File buildMarker = getBuildMarkerFile();
        try {
            return buildMarker.isFile() && getBuildFingerprint(gamePackage).equals(FileUtils.readFileAsString(buildMarker));
        } catch (IOException e) {
            Log.w(TAG, "Failed to read build marker: " + buildMarker.getAbsolutePath(), e);
            return false;
        }
    }

    private File getBuildMarkerFile() {
        return new File(fileEnvironment.getInstanceCacheDirPath(instanceId), BUILD_MARKER_FILE_NAME);
    }

    private String getBuildFingerprint(GamePackage gamePackage) {
        return gamePackage.getVersionCode() + ":" + new File(gamePackage.getBaseApkPath()).length() + ":" + packGameAssets;
    }

    private void extractGameFiles(GamePackage gamePackage, File dexDir, File nativeDir, File dexOptDir, File assetsPack) throws LauncherException, IOException {
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

        if (packGameAssets && gamePackage.isVersion015AndAbove()) {
            Log.d(TAG, "Packing game assets...");
            writeAssetsPack(allApkFiles, assetsPack);
        } else {
            Log.d(TAG, "Skipped packing game assets.");
        }
    }

    // A zip is the only asset path form AssetManager accepts on every Android version.
    // Android 8 and below also require a manifest in it.
    private void writeAssetsPack(List<File> apkFiles, File assetsPack) throws IOException {
        Set<String> writtenEntries = new HashSet<>();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(assetsPack))) {
            for (File apkFile : apkFiles) {
                try (ZipFile zipFile = new ZipFile(apkFile)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        boolean isPackEntry = name.startsWith(ASSETS_ENTRY_PREFIX) || name.equals(MANIFEST_ENTRY);
                        if (isPackEntry && !entry.isDirectory() && writtenEntries.add(name)) {
                            ZipEntry packEntry = new ZipEntry(name);
                            packEntry.setMethod(ZipEntry.STORED);
                            packEntry.setSize(entry.getSize());
                            packEntry.setCompressedSize(entry.getSize());
                            packEntry.setCrc(entry.getCrc());
                            output.putNextEntry(packEntry);
                            FileUtils.copy(zipFile.getInputStream(entry), output);
                            output.closeEntry();
                        }
                    }
                }
            }
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
