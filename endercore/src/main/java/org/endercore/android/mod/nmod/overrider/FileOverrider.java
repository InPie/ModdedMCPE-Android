package org.endercore.android.mod.nmod.overrider;

import org.endercore.android.utils.FileUtils;

import java.io.File;
import java.io.IOException;

public final class FileOverrider extends BaseOverrider {
    public FileOverrider(File overridePath) {
        super(overridePath);
    }

    @Override
    public void performOverride(File root, String name, String mode) throws IOException {
        FileUtils.copy(FileUtils.resolveInside(root, name), FileUtils.resolveInside(overridePath, name));
    }
}
