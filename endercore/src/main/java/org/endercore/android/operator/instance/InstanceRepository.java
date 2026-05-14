package org.endercore.android.operator.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.operator.instance.model.GameInstance;
import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InstanceRepository {
    private static final String INSTANCE_FILE_NAME = "instance.json";
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

    public List<GameInstance> getInstances() {
        List<GameInstance> instances = new ArrayList<>();
        File instancesDir = new File(fileEnvironment.getInstancesDirPath());
        File[] dirs = instancesDir.listFiles();
        
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    File instanceFile = new File(dir, INSTANCE_FILE_NAME);
                    if (instanceFile.exists() && instanceFile.isFile()) {
                        try (FileReader reader = new FileReader(instanceFile)) {
                            GameInstance instance = gson.fromJson(reader, GameInstance.class);
                            if (instance != null) {
                                instances.add(instance);
                            }
                        } catch (Exception e) {
                            // If corrupted or unable to read, skip this instance
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return instances;
    }

    public GameInstance getInstance(String instanceId) {
        File instanceFile = getInstanceFile(instanceId);
        if (instanceFile.exists() && instanceFile.isFile()) {
            try (FileReader reader = new FileReader(instanceFile)) {
                return gson.fromJson(reader, GameInstance.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void saveInstance(GameInstance instance) throws IOException {
        if (instance == null || instance.getId() == null) {
            throw new IllegalArgumentException("Instance or instance ID cannot be null");
        }

        File instanceDir = getInstanceDir(instance.getId());
        if (!instanceDir.exists() && !instanceDir.mkdirs()) {
            throw new IOException("Failed to create instance directory: " + instanceDir.getAbsolutePath());
        }

        File instanceFile = getInstanceFile(instance.getId());
        try (FileWriter writer = new FileWriter(instanceFile)) {
            gson.toJson(instance, writer);
        }
    }

    public boolean deleteInstance(String instanceId) {
        File instanceDir = getInstanceDir(instanceId);
        if (instanceDir.exists()) {
            return FileUtils.removeFiles(instanceDir);
        }
        return true;
    }
}
