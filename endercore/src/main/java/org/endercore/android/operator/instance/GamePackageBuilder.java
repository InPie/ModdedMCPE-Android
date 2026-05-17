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

        Log.d(TAG, "Clearing cache directories...");
        
        // Clear old cache
        FileUtils.removeFiles(dexDir);
        FileUtils.removeFiles(nativeDir);
        
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
            } catch (IOException e) {
                Log.w(TAG, "Failed to read APK for native libs: " + apkFile.getAbsolutePath(), e);
            }
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

        if (gamePackage.isVersion015AndAbove()) {
            Log.d(TAG, "Extracting all game assets...");
            File assetsDir = new File(fileEnvironment.getCodeCacheDirPathForAssets());
            if (!assetsDir.exists() && !assetsDir.mkdirs()) {
                Log.w(TAG, "Failed to create assets cache directory");
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
                } catch (IOException e) {
                    Log.w(TAG, "Failed to extract assets from APK: " + apkFile.getAbsolutePath(), e);
                }
            }
        } else {
            Log.d(TAG, "Skipped assets extraction for older game version.");
        }

        Log.d(TAG, "GamePackageBuilder finished successfully.");
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

    private boolean extractFileFromApks(List<File> apkFiles, String zipPath, File destFile) {
        for (File apkFile : apkFiles) {
            try (ZipFile zipFile = new ZipFile(apkFile)) {
                ZipEntry entry = zipFile.getEntry(zipPath);
                if (entry != null) {
                    FileUtils.copy(zipFile.getInputStream(entry), destFile);
                    return true;
                }
            } catch (IOException ignored) {
            }
        }
        return false;
    }
}
