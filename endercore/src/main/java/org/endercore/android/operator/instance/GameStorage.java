package org.endercore.android.operator.instance;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

// Folders of the instance that the game process gets instead of the launcher's own. GameStorageContext
// answers the storage methods of Context from here once the launcher installs it before starting the game.
public final class GameStorage {
    private static final String TAG = "EnderCore-GameStorage";
    private static final String PREFERENCES_FILE_SUFFIX = ".xml";
    private static final String BACKUP_FILE_SUFFIX = ".bak";
    private static final String DIR_PREFIX = "app_";
    private static final String EXTERNAL_DIR_CACHE = "cache";
    private static final String EXTERNAL_DIR_OBB = "obb";
    // Fallback names of preferences in the launcher's own folder
    private static final String SHARED_SIGN_IN_PREFERENCES_PREFIX = "shared_sign_in.";
    private static final String INSTANCE_PREFERENCES_PREFIX = "instance.";
    // Preferences of the sign-in libraries: MSA (1.0 - 1.12), ADAL (1.16 - 1.17), XAL (1.16+)
    private static final String[] SIGN_IN_PREFERENCES = {
            "com.microsoft.onlineid", "com.microsoft.aad.adal.", "com.microsoft.xal."
    };
    // Sign-in folders that the game opens by path inside data/
    private static final String[] SIGN_IN_DIRS = {"xal"};

    private static volatile GameStorage installed;
    // Context.getSharedPreferences(File, int): removed from the SDK, still allowed by Android
    private static Method preferencesByFile;
    private static boolean preferencesByFileMissing;

    private final InstanceStorage storage;
    private final File signInDir;
    private final boolean sharedSignIn;

    public GameStorage(InstanceStorage storage, File sharedSignInDir) {
        this.storage = storage;
        this.sharedSignIn = sharedSignInDir != null;
        this.signInDir = sharedSignIn ? sharedSignInDir : storage.getSignInDir();
    }

    public static GameStorage getInstalled() {
        return installed;
    }

    // For the rest of the game process
    public static void install(GameStorage storage) {
        storage.linkSignInDirs();
        installed = storage;
        Log.i(TAG, "Game storage: " + storage.storage.getInstanceDir()
                + ", sign-in: " + storage.signInDir.getAbsolutePath());
    }

    public InstanceStorage getStorage() {
        return storage;
    }

    public File getDataDir() {
        return ensureDir(storage.getDataDir());
    }

    public File getFilesDir() {
        return ensureDir(storage.getFilesDir());
    }

    public File getCacheDir() {
        return ensureDir(storage.getCacheDir());
    }

    public File getNoBackupFilesDir() {
        return ensureDir(new File(storage.getDataDir(), InstanceStorage.DIR_NO_BACKUP));
    }

    public File getDir(String name) {
        return ensureDir(new File(storage.getDataDir(), DIR_PREFIX + name));
    }

    public File getFileStreamPath(String name) {
        return new File(getFilesDir(), name);
    }

    // As in Android, an absolute name is a path of its own
    public File getDatabasePath(String name) {
        if (name.startsWith(File.separator)) {
            return new File(name);
        }
        return new File(getDatabasesDir(), name);
    }

    public File getDatabasesDir() {
        return ensureDir(new File(storage.getDataDir(), InstanceStorage.DIR_DATABASES));
    }

    public File getExternalFilesDir(String type) {
        File dir = type == null ? storage.getExternalDir() : new File(storage.getExternalDir(), type);
        return ensureDir(dir);
    }

    public File getExternalCacheDir() {
        return ensureDir(new File(storage.getExternalDir(), EXTERNAL_DIR_CACHE));
    }

    public File getObbDir() {
        return ensureDir(new File(storage.getExternalDir(), EXTERNAL_DIR_OBB));
    }

    public SharedPreferences getSharedPreferences(Context base, String name, int mode) {
        File file = getSharedPreferencesFile(name);
        Method method = getPreferencesByFile();
        if (method != null) {
            try {
                return (SharedPreferences) method.invoke(base, file, mode);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.e(TAG, "Failed to open preferences " + file.getAbsolutePath(), e);
            }
        }
        return base.getSharedPreferences(getFallbackPreferencesName(name), mode);
    }

    public boolean deleteSharedPreferences(Context base, String name) {
        getSharedPreferences(base, name, Context.MODE_PRIVATE).edit().clear().commit();
        File file = getSharedPreferencesFile(name);
        File backup = new File(file.getPath() + BACKUP_FILE_SUFFIX);
        return (file.delete() || !file.exists()) && (backup.delete() || !backup.exists());
    }

    private File getSharedPreferencesFile(String name) {
        File dataDir = isSignInPreferences(name) ? signInDir : storage.getDataDir();
        File dir = ensureDir(new File(dataDir, InstanceStorage.DIR_SHARED_PREFS));
        return new File(dir, name + PREFERENCES_FILE_SUFFIX);
    }

    private String getFallbackPreferencesName(String name) {
        if (sharedSignIn && isSignInPreferences(name)) {
            return SHARED_SIGN_IN_PREFERENCES_PREFIX + name;
        }
        return INSTANCE_PREFERENCES_PREFIX + storage.getInstanceDir().getName() + "." + name;
    }

    private static boolean isSignInPreferences(String name) {
        for (String prefix : SIGN_IN_PREFERENCES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized Method getPreferencesByFile() {
        if (preferencesByFile == null && !preferencesByFileMissing) {
            try {
                preferencesByFile = Context.class.getMethod("getSharedPreferences", File.class, int.class);
            } catch (NoSuchMethodException e) {
                preferencesByFileMissing = true;
                Log.e(TAG, "Preferences by file are not available, using names with a prefix.", e);
            }
        }
        return preferencesByFile;
    }

    // The game makes these folders inside data/. Each is a link to the sign-in folder in use, so changing
    // that folder changes where sign-in is read from. If the game replaced a link with a folder, the files
    // go to the sign-in folder in use now.
    private void linkSignInDirs() {
        for (String name : SIGN_IN_DIRS) {
            File link = new File(getDataDir(), name);
            File target = ensureDir(new File(signInDir, name));
            try {
                if (FileUtils.isSymbolicLink(link)) {
                    if (link.getCanonicalFile().equals(target.getCanonicalFile())) {
                        continue;
                    }
                    if (!link.delete()) {
                        throw new IOException("Failed to remove the link " + link.getAbsolutePath());
                    }
                } else if (link.exists()) {
                    FileUtils.moveMerging(link, target);
                }
                Os.symlink(target.getAbsolutePath(), link.getAbsolutePath());
            } catch (IOException | ErrnoException e) {
                Log.e(TAG, "Failed to link " + link.getAbsolutePath() + " to " + target.getAbsolutePath(), e);
            }
        }
    }

    private static File ensureDir(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            Log.e(TAG, "Failed to create " + dir.getAbsolutePath());
        }
        return dir;
    }
}
