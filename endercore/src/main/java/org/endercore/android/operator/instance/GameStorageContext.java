package org.endercore.android.operator.instance;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

// Base context of the launcher's Application and of AgentMainActivity. While GameStorage is installed,
// files, caches, databases and preferences are those of the instance, otherwise of the launcher.
public class GameStorageContext extends ContextWrapper {
    public GameStorageContext(Context base) {
        super(base);
    }

    private static GameStorage storage() {
        return GameStorage.getInstalled();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        GameStorage storage = storage();
        if (storage == null) {
            return super.getApplicationInfo();
        }
        ApplicationInfo info = new ApplicationInfo(super.getApplicationInfo());
        info.dataDir = storage.getDataDir().getAbsolutePath();
        return info;
    }

    @Override
    public File getDataDir() {
        GameStorage storage = storage();
        return storage == null ? super.getDataDir() : storage.getDataDir();
    }

    @Override
    public File getFilesDir() {
        GameStorage storage = storage();
        return storage == null ? super.getFilesDir() : storage.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        GameStorage storage = storage();
        return storage == null ? super.getCacheDir() : storage.getCacheDir();
    }

    @Override
    public File getNoBackupFilesDir() {
        GameStorage storage = storage();
        return storage == null ? super.getNoBackupFilesDir() : storage.getNoBackupFilesDir();
    }

    @Override
    public File getDir(String name, int mode) {
        GameStorage storage = storage();
        return storage == null ? super.getDir(name, mode) : storage.getDir(name);
    }

    @Override
    public File getFileStreamPath(String name) {
        GameStorage storage = storage();
        return storage == null ? super.getFileStreamPath(name) : storage.getFileStreamPath(name);
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        GameStorage storage = storage();
        return storage == null ? super.openFileInput(name) : new FileInputStream(storage.getFileStreamPath(name));
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        GameStorage storage = storage();
        if (storage == null) {
            return super.openFileOutput(name, mode);
        }
        return new FileOutputStream(storage.getFileStreamPath(name), (mode & MODE_APPEND) != 0);
    }

    @Override
    public boolean deleteFile(String name) {
        GameStorage storage = storage();
        return storage == null ? super.deleteFile(name) : storage.getFileStreamPath(name).delete();
    }

    @Override
    public String[] fileList() {
        GameStorage storage = storage();
        return storage == null ? super.fileList() : listNames(storage.getFilesDir());
    }

    @Override
    public File getDatabasePath(String name) {
        GameStorage storage = storage();
        return storage == null ? super.getDatabasePath(name) : storage.getDatabasePath(name);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return openOrCreateDatabase(name, mode, factory, null);
    }

    // The flags follow ContextImpl
    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                                               DatabaseErrorHandler errorHandler) {
        GameStorage storage = storage();
        if (storage == null) {
            return super.openOrCreateDatabase(name, mode, factory, errorHandler);
        }
        int flags = SQLiteDatabase.CREATE_IF_NECESSARY;
        if ((mode & MODE_ENABLE_WRITE_AHEAD_LOGGING) != 0) {
            flags |= SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
        }
        if ((mode & MODE_NO_LOCALIZED_COLLATORS) != 0) {
            flags |= SQLiteDatabase.NO_LOCALIZED_COLLATORS;
        }
        return SQLiteDatabase.openDatabase(storage.getDatabasePath(name).getPath(), factory, flags, errorHandler);
    }

    @Override
    public boolean deleteDatabase(String name) {
        GameStorage storage = storage();
        return storage == null ? super.deleteDatabase(name) : SQLiteDatabase.deleteDatabase(storage.getDatabasePath(name));
    }

    @Override
    public String[] databaseList() {
        GameStorage storage = storage();
        return storage == null ? super.databaseList() : listNames(storage.getDatabasesDir());
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        GameStorage storage = storage();
        return storage == null ? super.getSharedPreferences(name, mode)
                : storage.getSharedPreferences(getBaseContext(), name, mode);
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        GameStorage storage = storage();
        return storage == null ? super.deleteSharedPreferences(name)
                : storage.deleteSharedPreferences(getBaseContext(), name);
    }

    @Override
    public File getExternalFilesDir(String type) {
        GameStorage storage = storage();
        return storage == null ? super.getExternalFilesDir(type) : storage.getExternalFilesDir(type);
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        GameStorage storage = storage();
        return storage == null ? super.getExternalFilesDirs(type) : new File[]{storage.getExternalFilesDir(type)};
    }

    @Override
    public File getExternalCacheDir() {
        GameStorage storage = storage();
        return storage == null ? super.getExternalCacheDir() : storage.getExternalCacheDir();
    }

    @Override
    public File[] getExternalCacheDirs() {
        GameStorage storage = storage();
        return storage == null ? super.getExternalCacheDirs() : new File[]{storage.getExternalCacheDir()};
    }

    @Override
    public File getObbDir() {
        GameStorage storage = storage();
        return storage == null ? super.getObbDir() : storage.getObbDir();
    }

    @Override
    public File[] getObbDirs() {
        GameStorage storage = storage();
        return storage == null ? super.getObbDirs() : new File[]{storage.getObbDir()};
    }

    private static String[] listNames(File dir) {
        String[] names = dir.list();
        return names == null ? new String[0] : names;
    }
}
