package org.endercore.android;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import java.io.File;

// Files under a temporary folder, game not installed
public final class TestContext extends ContextWrapper {
    private final File root;

    public TestContext(File root) {
        super(null);
        this.root = root;
    }

    @Override
    public File getCodeCacheDir() {
        return getDir("code_cache", 0);
    }

    @Override
    public File getCacheDir() {
        return getDir("cache", 0);
    }

    @Override
    public File getDir(String name, int mode) {
        File dir = new File(root, name);
        dir.mkdirs();
        return dir;
    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        throw new PackageManager.NameNotFoundException();
    }
}
