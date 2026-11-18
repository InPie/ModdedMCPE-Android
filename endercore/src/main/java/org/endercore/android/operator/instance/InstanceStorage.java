package org.endercore.android.operator.instance;

import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.IOException;

// Folders with the game data of one instance, laid out as on a device. data/ is the private folder
// of the app (/data/data/<package>). external/ is shared storage, which 0.x - 1.17 ask Environment for,
// and the external files folder, which 1.26 asks Context for. sign_in/ keeps the instance's own
// sign-in data, the game reaches it through GameStorage.
public final class InstanceStorage {
    // Version of this layout in GameInstance.storageLayout
    public static final int LAYOUT = 2;

    public static final String DIR_DATA = "data";
    public static final String DIR_EXTERNAL = "external";
    public static final String DIR_SIGN_IN = "sign_in";

    static final String DIR_FILES = "files";
    static final String DIR_CACHE = "cache";
    static final String DIR_NO_BACKUP = "no_backup";
    static final String DIR_DATABASES = "databases";
    static final String DIR_SHARED_PREFS = "shared_prefs";
    static final String DIR_GAMES = "games";
    // 1.0 - 1.17 keep their data in a folder named after the game package inside ANDROID_APPDATA_PATH
    static final String DIR_GAME_PACKAGE = "com.mojang.minecraftpe";
    // 1.26 writes crash dumps next to the files folder
    static final String DIR_CRASH = "crash";
    // Settings of 0.1 - 0.6, which the launcher shows in its own dialogs
    private static final String FILE_LEGACY_OPTIONS = "options.txt";

    // In layout 1 data/ was the files folder and shared storage at once
    private static final String DIR_LAYOUT_1_DATA = "data.layout1";

    private final File instanceDir;

    public InstanceStorage(File instanceDir) {
        this.instanceDir = instanceDir;
    }

    public File getInstanceDir() {
        return instanceDir;
    }

    public File getDataDir() {
        return new File(instanceDir, DIR_DATA);
    }

    public File getFilesDir() {
        return new File(getDataDir(), DIR_FILES);
    }

    public File getCacheDir() {
        return new File(getDataDir(), DIR_CACHE);
    }

    public File getExternalDir() {
        return new File(instanceDir, DIR_EXTERNAL);
    }

    public File getSignInDir() {
        return new File(instanceDir, DIR_SIGN_IN);
    }

    public File getLegacyOptionsFile() {
        return new File(getFilesDir(), FILE_LEGACY_OPTIONS);
    }

    // Copied with the instance. Sign-in data stays with its instance.
    public File[] getGameDataDirs() {
        return new File[]{getDataDir(), getExternalDir()};
    }

    // Moves the data of layout 1. Can run again after an interruption and goes on from where it stopped.
    public void migrateFromLayout1() throws IOException {
        File oldData = new File(instanceDir, DIR_LAYOUT_1_DATA);
        File data = getDataDir();
        if (!oldData.exists() && data.isDirectory()) {
            FileUtils.moveReplacing(data, oldData);
        }
        if (oldData.isDirectory()) {
            FileUtils.moveMerging(new File(oldData, DIR_GAMES), new File(getExternalDir(), DIR_GAMES));
            FileUtils.moveMerging(new File(oldData, DIR_CACHE), getCacheDir());
            FileUtils.moveMerging(new File(oldData, DIR_GAME_PACKAGE), new File(data, DIR_GAME_PACKAGE));
            File[] rest = oldData.listFiles();
            if (rest != null) {
                for (File file : rest) {
                    FileUtils.moveMerging(file, new File(getFilesDir(), file.getName()));
                }
            }
            if (!oldData.delete()) {
                throw new IOException("Failed to remove " + oldData.getAbsolutePath());
            }
        }
        FileUtils.moveMerging(new File(instanceDir, DIR_CRASH), new File(data, DIR_CRASH));
    }
}
