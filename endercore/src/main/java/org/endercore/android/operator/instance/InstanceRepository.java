package org.endercore.android.operator.instance;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.operator.instance.model.GameInstance;
import org.endercore.android.operator.instance.model.InstanceSource;
import org.endercore.android.operator.instance.model.InstanceSourceType;
import org.endercore.android.operator.instance.model.InstanceState;
import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InstanceRepository {
    private static final String TAG = "InstanceRepository";
    static final String INSTANCE_FILE_NAME = "instance.json";
    static final String MANAGED_APK_PATH = "apk/game.apk";
    private static final String DAMAGED_FILE_NAME = INSTANCE_FILE_NAME + ".damaged";
    private final IFileEnvironment fileEnvironment;
    private final Gson gson;

    public InstanceRepository(IFileEnvironment fileEnvironment) {
        this.fileEnvironment = fileEnvironment;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        File instancesDir = new File(fileEnvironment.getInstancesDirPath());
        if (!instancesDir.exists()) {
            instancesDir.mkdirs();
        }
    }

    public File getInstanceDir(String instanceId) {
        return new File(fileEnvironment.getInstancesDirPath(), instanceId);
    }

    public File getInstanceFile(String instanceId) {
        return new File(getInstanceDir(instanceId), INSTANCE_FILE_NAME);
    }

    public File getManagedApkFile(String instanceId) {
        return new File(getInstanceDir(instanceId), MANAGED_APK_PATH);
    }

    public List<GameInstance> getInstances() {
        List<GameInstance> instances = new ArrayList<>();
        File instancesDir = new File(fileEnvironment.getInstancesDirPath());
        File[] dirs = instancesDir.listFiles();

        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    GameInstance instance = getInstance(dir.getName());
                    if (instance != null) {
                        instances.add(instance);
                    }
                }
            }
        }
        return instances;
    }

    public GameInstance getInstance(String instanceId) {
        File instanceFile = getInstanceFile(instanceId);
        if (!instanceFile.isFile()) {
            return null;
        }

        GameInstance instance = null;
        try (FileReader reader = new FileReader(instanceFile)) {
            instance = gson.fromJson(reader, GameInstance.class);
        } catch (JsonParseException e) {
            Log.e(TAG, "Damaged metadata of instance: " + instanceId, e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read metadata of instance: " + instanceId, e);
            return null;
        }

        if (instance == null) {
            return recoverInstance(instanceId);
        }

        // Id follows the folder name, name and state are required by the UI
        instance.setId(instanceId);
        if (instance.getName() == null) {
            instance.setName(instanceId);
        }
        if (instance.getState() == null) {
            instance.setState(InstanceState.REBUILD_REQUIRED);
        }
        return instance;
    }

    // Restores metadata from the contents of the instance folder
    private GameInstance recoverInstance(String instanceId) {
        File instanceDir = getInstanceDir(instanceId);

        GameInstance instance = new GameInstance();
        instance.setId(instanceId);
        instance.setName(instanceId);
        instance.setSettings(new JsonObject());
        instance.setCreatedAt(instanceDir.lastModified());

        if (getManagedApkFile(instanceId).isFile()) {
            InstanceSource source = new InstanceSource(InstanceSourceType.MANAGED_APK);
            source.setOrigin("recovered");
            instance.setSource(source);
            instance.setState(InstanceState.REBUILD_REQUIRED);
        } else {
            instance.setState(InstanceState.MISSING_SOURCE);
        }

        try {
            FileUtils.copy(getInstanceFile(instanceId), new File(instanceDir, DAMAGED_FILE_NAME));
            saveInstance(instance);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save recovered metadata of instance: " + instanceId, e);
        }
        return instance;
    }

    public void saveInstance(GameInstance instance) throws IOException {
        if (instance == null || instance.getId() == null) {
            throw new IllegalArgumentException("Instance or instance ID cannot be null");
        }

        File instanceDir = getInstanceDir(instance.getId());
        if (!instanceDir.exists() && !instanceDir.mkdirs()) {
            throw new IOException("Failed to create instance directory: " + instanceDir.getAbsolutePath());
        }

        FileUtils.writeAtomic(getInstanceFile(instance.getId()), gson.toJson(instance));
    }

    public boolean deleteInstance(String instanceId) {
        File instanceDir = getInstanceDir(instanceId);
        if (instanceDir.exists()) {
            FileUtils.removeFiles(instanceDir);
            return !instanceDir.exists();
        }
        return true;
    }
}
