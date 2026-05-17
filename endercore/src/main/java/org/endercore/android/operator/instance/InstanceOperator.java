package org.endercore.android.operator.instance;

import android.util.Log;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.endercore.android.EnderCore;
import org.endercore.android.exception.LauncherException;
import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.operator.ApkGamePackageManager;
import org.endercore.android.operator.GamePackage;
import org.endercore.android.operator.instance.model.GameInstance;
import org.endercore.android.operator.instance.model.InstanceSource;
import org.endercore.android.operator.instance.model.InstanceSourceType;
import org.endercore.android.operator.instance.model.InstanceState;
import org.endercore.android.operator.instance.model.PackageSnapshot;
import org.endercore.android.operator.instance.model.RemoteVersion;
import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class InstanceOperator {
    private static final String TAG = "InstanceOperator";

    private static final String INSTANCE_FILE_NAME = "instance.json";

    private final IFileEnvironment fileEnvironment;
    private final InstanceRepository repository;
    private final Context context;
    private final Gson gson = new Gson();

    public InstanceOperator(Context context, IFileEnvironment fileEnvironment) {
        this.context = context;
        this.fileEnvironment = fileEnvironment;
        this.repository = new InstanceRepository(fileEnvironment);
    }

    public InstanceRepository getRepository() {
        return repository;
    }

    public File getManagedApkFile(String instanceId) {
        return new File(repository.getInstanceDir(instanceId), "apk/game.apk");
    }

    // used in showInstanceInfo
    public boolean isInstancePrepared(GameInstance instance) throws Exception {
        return isInstancePrepared(instance, resolveGamePackage(instance));
    }

    public boolean isInstancePrepared(GameInstance instance, GamePackage gamePackage) throws Exception {
        File cacheDir = new File(fileEnvironment.getInstanceCacheDirPath(instance.getId()));

        fileEnvironment.setActiveWorkspace(instance.getId());
        File dexDir = new File(fileEnvironment.getCodeCacheDirPathForDex());
        File nativeDir = new File(fileEnvironment.getCodeCacheDirPathForNativeLib());
        File dexOptDir = new File(fileEnvironment.getCodeCacheDirPathForDexOpt());

        boolean baseFilesReady = cacheDir.exists() &&
            dexDir.exists() &&
            nativeDir.exists() &&
            dexOptDir.exists() &&
            new File(dexDir, "classes.dex").isFile() &&
            new File(nativeDir, "libminecraftpe.so").isFile();

        Log.d(TAG, "isBaseFilesReady: " + baseFilesReady);


        boolean assetsFilesReady = true;
        if (gamePackage.isVersion015AndAbove()) {
            assetsFilesReady = new File(fileEnvironment.getCodeCacheDirPathForGameAssets()).isDirectory();
        } else {
            Log.d(TAG, "getVersionName: " + gamePackage.getVersionName());
        }
        Log.d(TAG, "isAssetsFilesReady: " + assetsFilesReady);

        return baseFilesReady && assetsFilesReady;
    }

    public PackageSnapshot createPackageSnapshot(GamePackage gamePackage) {
        File apkFile = new File(gamePackage.getBaseApkPath());
        PackageSnapshot snapshot = new PackageSnapshot();
        snapshot.setPackageName(gamePackage.getPackageName());
        snapshot.setVersionName(gamePackage.getVersionName());
        snapshot.setVersionCode(gamePackage.getVersionCode());
        snapshot.setApkSize(apkFile.exists() ? apkFile.length() : 0);
        snapshot.setLastReadAt(System.currentTimeMillis());
        return snapshot;
    }

    public boolean canLaunch(GameInstance instance) {
        if (instance == null || instance.getState() == null || instance.getSource() == null) {
            return false;
        }
        return instance.getState() == InstanceState.READY ||
                instance.getState() == InstanceState.REBUILD_REQUIRED ||
                instance.getState() == InstanceState.PREPARE_FAILED;
    }

    public GamePackage resolveGamePackage(GameInstance instance) throws Exception {
        if (!canLaunch(instance)) {
            throw new LauncherException("Instance is not ready to launch: " + instance.getName());
        }

        InstanceSource source = instance.getSource();
        if (source == null) {
            throw new LauncherException("Instance source is missing: " + instance.getId());
        }
        
        switch (source.getType()) {
            case MANAGED_APK: {
                File apkFile = getManagedApkFile(instance.getId());
                return ApkGamePackageManager.getGamePackageFromApk(context, apkFile);
            }
            case EXTERNAL_APK: {
                if (source.getApkPath() == null) {
                    throw new LauncherException("External APK path is missing: " + instance.getId());
                }
                return ApkGamePackageManager.getGamePackageFromApk(context, new File(source.getApkPath()));
            }
            case INSTALLED_PACKAGE: {
                GamePackage pkg = EnderCore.getInstance().getGamePackageManager().getGamePackage();
                if (pkg == null) {
                    throw new LauncherException("Installed MCPE package is not available.");
                }
                return pkg;
            }
            case REMOTE_APK: {
                throw new LauncherException("Remote instance is not downloaded yet: " + instance.getName());
            }
            default:
                throw new LauncherException("Unsupported instance source: " + source.getType());
        }
    }

    public GamePackage prepareInstance(GameInstance instance) throws Exception {
        fileEnvironment.setActiveWorkspace(instance.getId());
        GamePackage gamePackage = resolveGamePackage(instance);
        GamePackageBuilder builder = new GamePackageBuilder(fileEnvironment, instance.getId());

        if (instance.getState() != InstanceState.READY || !isInstancePrepared(instance, gamePackage)) {
            builder.build(gamePackage);
            instance.setState(InstanceState.READY);
            Log.d(TAG, "Instance was built.");

        } else {
            Log.d(TAG, "Instance building was skipped.");
        }

        if (EnderCore.getInstance().getOptionsManager().getUseNMods()) {
            NModPreparer preparer = new NModPreparer(fileEnvironment, EnderCore.getInstance().getNModManager(), instance.getId());
            preparer.prepare(gamePackage);
        }

        instance.setPackageSnapshot(createPackageSnapshot(gamePackage));
        instance.setLastPlayedAt(System.currentTimeMillis());
        repository.saveInstance(instance);
        
        return gamePackage;
    }

    public String duplicateInstance(GameInstance instance) throws Exception {
        InstanceSource source = instance.getSource();
        if (source == null) {
            throw new IllegalStateException("Instance source is missing.");
        }
        
        String newId = UUID.randomUUID().toString().substring(0, 8);
        GameInstance duplicate = new GameInstance();
        duplicate.setId(newId);
        duplicate.setName(instance.getName() + " (Copy)");
        duplicate.setState(InstanceState.REBUILD_REQUIRED);
        duplicate.setCreatedAt(System.currentTimeMillis());
        
        if (instance.getSettings() != null) {
            duplicate.setSettings(instance.getSettings().deepCopy());
        } else {
            duplicate.setSettings(new JsonObject());
        }
        
        duplicate.setPackageSnapshot(instance.getPackageSnapshot());

        switch (source.getType()) {
            case MANAGED_APK: {
                File srcApk = getManagedApkFile(instance.getId());
                File destApk = getManagedApkFile(newId);
                destApk.getParentFile().mkdirs();
                FileUtils.copy(srcApk, destApk);
                
                InstanceSource newSource = new InstanceSource(InstanceSourceType.MANAGED_APK);
                newSource.setOrigin(source.getOrigin());
                newSource.setUrl(source.getUrl());
                newSource.setLabel(source.getLabel());
                duplicate.setSource(newSource);
                break;
            }
            case INSTALLED_PACKAGE: {
                GamePackage pkg = EnderCore.getInstance().getGamePackageManager().getGamePackage();
                if (pkg == null) {
                    throw new IllegalStateException("Installed MCPE package is not available.");
                }
                File destApk = getManagedApkFile(newId);
                destApk.getParentFile().mkdirs();
                FileUtils.copy(new File(pkg.getBaseApkPath()), destApk);
                
                InstanceSource newSource = new InstanceSource(InstanceSourceType.MANAGED_APK);
                newSource.setOrigin("installed_copy");
                duplicate.setSource(newSource);
                duplicate.setPackageSnapshot(createPackageSnapshot(pkg));
                break;
            }
            case EXTERNAL_APK: {
                InstanceSource newSource = new InstanceSource(InstanceSourceType.EXTERNAL_APK);
                newSource.setApkPath(source.getApkPath());
                duplicate.setSource(newSource);
                break;
            }
            case REMOTE_APK: {
                throw new IllegalStateException("Remote APK is not downloaded yet.");
            }
        }

        File srcDataDir = new File(repository.getInstanceDir(instance.getId()), "data");
        if (srcDataDir.exists() && srcDataDir.isDirectory()) {
            File destDataDir = new File(repository.getInstanceDir(newId), "data");
            FileUtils.copyDirectory(srcDataDir, destDataDir);
        }

        repository.saveInstance(duplicate);
        return newId;
    }

    public void deleteInstance(String instanceId) {
        repository.deleteInstance(instanceId);
    }
    
    public interface InstallCallback {
        void onProgress(int percent);
        void onSuccess();
        void onError(Exception e);
    }

    public Runnable installRemoteVersion(RemoteVersion version, InstallCallback callback) {
        String instanceId = UUID.randomUUID().toString().substring(0, 8) + "-" + version.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
        
        GameInstance gameInstance = new GameInstance();
        gameInstance.setId(instanceId);
        gameInstance.setName(version.getName());
        gameInstance.setState(InstanceState.DOWNLOADING);
        gameInstance.setCreatedAt(System.currentTimeMillis());
        gameInstance.setSettings(new JsonObject());
        
        InstanceSource source = new InstanceSource(InstanceSourceType.REMOTE_APK);
        source.setUrl(version.getUrl());
        source.setLabel(version.getName());
        gameInstance.setSource(source);
        
        try {
            repository.saveInstance(gameInstance);
        } catch (Exception e) {
            callback.onError(e);
            return () -> {};
        }

        return startDownloadForInstance(gameInstance, callback);
    }

    public Runnable retryInstance(GameInstance gameInstance, InstallCallback callback) {
        if (gameInstance.getSource() == null || gameInstance.getSource().getType() != InstanceSourceType.REMOTE_APK) {
            callback.onError(new IllegalStateException("Cannot retry this instance: not a remote APK."));
            return () -> {};
        }
        gameInstance.setState(InstanceState.DOWNLOADING);
        try {
            repository.saveInstance(gameInstance);
        } catch (Exception e) {
            callback.onError(e);
            return () -> {};
        }
        return startDownloadForInstance(gameInstance, callback);
    }

    private Runnable startDownloadForInstance(GameInstance gameInstance, InstallCallback callback) {
        File apkFile = getManagedApkFile(gameInstance.getId());
        apkFile.getParentFile().mkdirs();
        
        InstanceDownloader downloader = new InstanceDownloader();
        InstanceDownloader.DownloadTask task = downloader.downloadApk(context, gameInstance.getSource().getUrl(), apkFile, new InstanceDownloader.DownloadListener() {
            @Override
            public void onProgress(int percent) {
                callback.onProgress(percent);
            }

            @Override
            public void onSuccess(File downloadedFile) {
                try {
                    gameInstance.setState(InstanceState.PREPARING);
                    gameInstance.getSource().setType(InstanceSourceType.MANAGED_APK);
                    gameInstance.getSource().setOrigin("remote");
                    repository.saveInstance(gameInstance);

                    GamePackage gamePackage = ApkGamePackageManager.getGamePackageFromApk(context, downloadedFile);
                    gameInstance.setPackageSnapshot(createPackageSnapshot(gamePackage));
                    
                    GamePackageBuilder builder = new GamePackageBuilder(fileEnvironment, gameInstance.getId());
                    builder.build(gamePackage);
                    
                    gameInstance.setState(InstanceState.READY);
                    repository.saveInstance(gameInstance);
                    
                    callback.onSuccess();
                } catch (Exception e) {
                    gameInstance.setState(InstanceState.PREPARE_FAILED);
                    try {
                        repository.saveInstance(gameInstance);
                    } catch (Exception ignored) {}
                    callback.onError(e);
                }
            }

            @Override
            public void onError(Exception e) {
                gameInstance.setState(InstanceState.DOWNLOAD_FAILED);
                try {
                    repository.saveInstance(gameInstance);
                } catch (Exception ignored) {}
                callback.onError(e);
            }
        });
        
        return task::cancel;
    }

    public void importLocalApk(File apkFile, boolean copy, InstallCallback callback) {
        new Thread(() -> {
            try {
                GamePackage gamePackage = ApkGamePackageManager.getGamePackageFromApk(context, apkFile);
                String instanceId = UUID.randomUUID().toString().substring(0, 8) + "-" + gamePackage.getVersionName().replaceAll("[^a-zA-Z0-9.-]", "_");
                
                GameInstance gameInstance = new GameInstance();
                gameInstance.setId(instanceId);
                gameInstance.setName("Custom " + gamePackage.getVersionName());
                gameInstance.setState(InstanceState.PREPARING);
                gameInstance.setCreatedAt(System.currentTimeMillis());
                gameInstance.setSettings(new JsonObject());
                
                if (copy) {
                    File destApk = getManagedApkFile(instanceId);
                    destApk.getParentFile().mkdirs();
                    FileUtils.copy(apkFile, destApk);
                    
                    InstanceSource source = new InstanceSource(InstanceSourceType.MANAGED_APK);
                    source.setOrigin("imported");
                    gameInstance.setSource(source);
                } else {
                    InstanceSource source = new InstanceSource(InstanceSourceType.EXTERNAL_APK);
                    source.setApkPath(apkFile.getAbsolutePath());
                    gameInstance.setSource(source);
                }
                
                gameInstance.setPackageSnapshot(createPackageSnapshot(gamePackage));
                repository.saveInstance(gameInstance);
                
                GamePackageBuilder builder = new GamePackageBuilder(fileEnvironment, instanceId);
                builder.build(gamePackage);
                
                gameInstance.setState(InstanceState.READY);
                repository.saveInstance(gameInstance);
                
                callback.onSuccess();
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    public void exportInstanceZip(String instanceId, File destinationFile) throws IOException {
        File instanceDir = repository.getInstanceDir(instanceId);
        if (!new File(instanceDir, INSTANCE_FILE_NAME).isFile()) {
            throw new IOException("Instance metadata not found: " + instanceId);
        }
        if (destinationFile.getParentFile() != null && !destinationFile.getParentFile().exists()) {
            destinationFile.getParentFile().mkdirs();
        }
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(destinationFile))) {
            addToZip(instanceDir, instanceDir, output);
        }
    }

    public void importInstanceZip(File zipFile, InstallCallback callback) {
        new Thread(() -> {
            File tempDir = new File(context.getCacheDir(), "instance_zip_import_" + System.currentTimeMillis());
            try {
                callback.onProgress(0);
                unzipInstance(zipFile, tempDir);

                File instanceFile = new File(tempDir, INSTANCE_FILE_NAME);
                File apkFile = new File(tempDir, "apk/game.apk");
                if (!instanceFile.isFile()) {
                    throw new IOException("ZIP does not contain instance.json.");
                }
                if (!apkFile.isFile()) {
                    throw new IOException("ZIP does not contain apk/game.apk.");
                }

                GameInstance imported;
                try (FileReader reader = new FileReader(instanceFile)) {
                    imported = gson.fromJson(reader, GameInstance.class);
                }
                if (imported == null) {
                    throw new IOException("Failed to read instance.json.");
                }

                callback.onProgress(35);
                GamePackage gamePackage = ApkGamePackageManager.getGamePackageFromApk(context, apkFile);
                String newId = UUID.randomUUID().toString().substring(0, 8) + "-" + safeIdPart(gamePackage.getVersionName());
                File finalDir = repository.getInstanceDir(newId);
                if (finalDir.exists()) {
                    FileUtils.removeFiles(finalDir);
                }
                FileUtils.copyDirectory(tempDir, finalDir);

                imported.setId(newId);
                if (imported.getName() == null || imported.getName().trim().isEmpty()) {
                    imported.setName("Imported " + gamePackage.getVersionName());
                }
                imported.setState(InstanceState.PREPARING);
                imported.setCreatedAt(System.currentTimeMillis());
                imported.setLastPlayedAt(null);
                InstanceSource source = new InstanceSource(InstanceSourceType.MANAGED_APK);
                source.setOrigin("zip_import");
                imported.setSource(source);
                imported.setPackageSnapshot(createPackageSnapshot(gamePackage));
                if (imported.getSettings() == null) {
                    imported.setSettings(new JsonObject());
                }
                repository.saveInstance(imported);

                callback.onProgress(70);
                GamePackage finalPackage = ApkGamePackageManager.getGamePackageFromApk(context, getManagedApkFile(newId));
                GamePackageBuilder builder = new GamePackageBuilder(fileEnvironment, newId);
                builder.build(finalPackage);
                imported.setPackageSnapshot(createPackageSnapshot(finalPackage));
                imported.setState(InstanceState.READY);
                repository.saveInstance(imported);

                callback.onProgress(100);
                callback.onSuccess();
            } catch (Exception e) {
                callback.onError(e);
            } finally {
                FileUtils.removeFiles(tempDir);
            }
        }).start();
    }

    private void addToZip(File rootDir, File file, ZipOutputStream output) throws IOException {
        String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
        if (relativePath.startsWith("cache/") || relativePath.startsWith("nmods_cache/")) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(rootDir, child, output);
                }
            }
            return;
        }

        output.putNextEntry(new ZipEntry(relativePath));
        FileUtils.copy(file, output);
        output.closeEntry();
    }

    private void unzipInstance(File zipFile, File destinationDir) throws IOException {
        if (destinationDir.exists()) {
            FileUtils.removeFiles(destinationDir);
        }
        if (!destinationDir.mkdirs()) {
            throw new IOException("Failed to create temp import directory: " + destinationDir.getAbsolutePath());
        }

        String destinationPath = destinationDir.getCanonicalPath() + File.separator;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                File target = new File(destinationDir, entry.getName());
                if (!target.getCanonicalPath().startsWith(destinationPath)) {
                    throw new IOException("Invalid ZIP entry path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    FileUtils.copy(input, target);
                }
                input.closeEntry();
            }
        }
    }

    private String safeIdPart(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "imported";
        }
        return value.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    public void ensureInstalledGameInstanceExists() {
        GamePackage installedPackage = EnderCore.getInstance().getGamePackageManager().getGamePackage();
        try {
            if (installedPackage != null) {
                GameInstance instance = repository.getInstance("installed-minecraftpe");
                if (instance == null) {
                    instance = new GameInstance();
                    instance.setId("installed-minecraftpe");
                    instance.setCreatedAt(System.currentTimeMillis());
                    instance.setSettings(new JsonObject());
                }
                instance.setName("Installed " + (installedPackage.getVersionName() != null ? installedPackage.getVersionName() : "MCPE"));
                InstanceSource source = new InstanceSource(InstanceSourceType.INSTALLED_PACKAGE);
                source.setPackageName(installedPackage.getPackageName());
                instance.setSource(source);
                instance.setPackageSnapshot(createPackageSnapshot(installedPackage));
                instance.setState(isInstancePrepared(instance, installedPackage) ? InstanceState.READY : InstanceState.REBUILD_REQUIRED);
                repository.saveInstance(instance);
            } else {
                GameInstance instance = repository.getInstance("installed-minecraftpe");
                if (instance != null) {
                    instance.setState(InstanceState.MISSING_SOURCE);
                    repository.saveInstance(instance);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
