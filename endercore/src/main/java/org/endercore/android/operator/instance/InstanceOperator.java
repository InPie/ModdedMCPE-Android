package org.endercore.android.operator.instance;

import android.content.Context;

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
import java.util.UUID;

public class InstanceOperator {
    private final IFileEnvironment fileEnvironment;
    private final InstanceRepository repository;
    private final Context context;

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

    public boolean isInstancePrepared(String instanceId) {
        File cacheDir = new File(fileEnvironment.getInstanceCacheDirPath(instanceId));
        return new File(cacheDir, "dex_libs/classes.dex").isFile() &&
               new File(cacheDir, "native_libs/libminecraftpe.so").isFile();
    }

    public PackageSnapshot createPackageSnapshot(GamePackage gamePackage) {
        File apkFile = new File(gamePackage.getBaseApkPath());
        PackageSnapshot snapshot = new PackageSnapshot();
        snapshot.setPackageName(gamePackage.getPackageName());
        snapshot.setVersionName(gamePackage.getVersionName());
        snapshot.setVersionCode(gamePackage.getVersionCode());
        snapshot.setApkSize(apkFile.exists() ? apkFile.length() : 0);
        return snapshot;
    }

    public GamePackage resolveGamePackage(GameInstance instance) throws Exception {
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
        GamePackage gamePackage = resolveGamePackage(instance);
        GamePackageBuilder builder = new GamePackageBuilder(fileEnvironment, instance.getId());
        NModPreparer preparer = new NModPreparer(fileEnvironment, EnderCore.getInstance().getNModManager(), instance.getId());

        if (instance.getState() != InstanceState.READY || !isInstancePrepared(instance.getId())) {
            builder.build(gamePackage);
            instance.setState(InstanceState.READY);
        }

        preparer.prepare(gamePackage);
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

    public void installRemoteVersion(RemoteVersion version, InstallCallback callback) {
        String instanceId = UUID.randomUUID().toString().substring(0, 8) + "-" + version.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
        
        GameInstance gameInstance = new GameInstance();
        gameInstance.setId(instanceId);
        gameInstance.setName(version.getName());
        gameInstance.setState(InstanceState.DOWNLOADING);
        gameInstance.setCreatedAt(System.currentTimeMillis());
        
        InstanceSource source = new InstanceSource(InstanceSourceType.REMOTE_APK);
        source.setUrl(version.getUrl());
        source.setLabel(version.getName());
        gameInstance.setSource(source);
        
        try {
            repository.saveInstance(gameInstance);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        File apkFile = getManagedApkFile(instanceId);
        apkFile.getParentFile().mkdirs();
        
        InstanceDownloader downloader = new InstanceDownloader();
        downloader.downloadApk(version.getUrl(), apkFile, new InstanceDownloader.DownloadListener() {
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
                    
                    GamePackageBuilder builder = new GamePackageBuilder(fileEnvironment, instanceId);
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
}
