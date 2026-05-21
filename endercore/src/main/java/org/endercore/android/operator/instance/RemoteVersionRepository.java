package org.endercore.android.operator.instance;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.operator.instance.model.RemoteVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RemoteVersionRepository {
    private static final String TAG = "RemoteVersionRepo";
    // Latest version list
    private static final String VERSIONS_URL = "https://gist.githubusercontent.com/Effently/4c5de3c788f7a6f4ea5e200ebcc7d889/raw/versions.json";
    private static final String VERSIONS_ARM64_URL = "https://gist.githubusercontent.com/Effently/4c5de3c788f7a6f4ea5e200ebcc7d889/raw/versions_arm64.json";
    private static final String CACHE_FILE_NAME = "remote_versions_cache.json";

    private final IFileEnvironment fileEnvironment;
    private final Gson gson;

    public RemoteVersionRepository(IFileEnvironment fileEnvironment) {
        this.fileEnvironment = fileEnvironment;
        this.gson = new Gson();
    }

    public List<RemoteVersion> fetchVersions(boolean is64Bit) {
        String versionsUrl = is64Bit ? VERSIONS_ARM64_URL : VERSIONS_URL;
        
        List<RemoteVersion> versions = new ArrayList<>();
        try {
            Log.d(TAG, "Fetching remote versions from: " + versionsUrl);
            URL url = new URL(versionsUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    Type listType = new TypeToken<List<RemoteVersion>>() {}.getType();
                    versions = gson.fromJson(reader, listType);
                    
                    if (versions != null && !versions.isEmpty()) {
                        saveToCache(versions);
                    }
                }
            } else {
                Log.w(TAG, "Failed to fetch versions. HTTP Code: " + connection.getResponseCode());
                versions = loadFromCache();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching remote versions.", e);
            versions = loadFromCache();
        }

        if (versions == null) {
            versions = new ArrayList<>();
        }
        
        return versions;
    }

    private void saveToCache(List<RemoteVersion> versions) {
        File cacheFile = new File(fileEnvironment.getEnderCoreDirPath(), CACHE_FILE_NAME);
        try (FileWriter writer = new FileWriter(cacheFile)) {
            gson.toJson(versions, writer);
            Log.d(TAG, "Saved versions to cache.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to save versions cache.", e);
        }
    }

    private List<RemoteVersion> loadFromCache() {
        Log.d(TAG, "Loading versions from cache...");
        File cacheFile = new File(fileEnvironment.getEnderCoreDirPath(), CACHE_FILE_NAME);
        if (cacheFile.exists()) {
            try (FileReader reader = new FileReader(cacheFile)) {
                Type listType = new TypeToken<List<RemoteVersion>>() {}.getType();
                return gson.fromJson(reader, listType);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load versions from cache.", e);
            }
        }
        return new ArrayList<>();
    }
}
