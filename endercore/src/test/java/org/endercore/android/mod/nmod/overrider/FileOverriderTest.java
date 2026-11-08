package org.endercore.android.mod.nmod.overrider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.endercore.android.TestFiles;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class FileOverriderTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void overrideReplacesAsset() throws Exception {
        File root = new File(folder.getRoot(), "mod/assets");
        File assets = new File(folder.getRoot(), "cache/assets");
        TestFiles.write(new File(root, "images/title.png"), "mod");
        TestFiles.write(new File(assets, "images/title.png"), "game");

        new FileOverrider(assets).performOverride(root, "images/title.png", null);

        assertEquals("mod", TestFiles.read(new File(assets, "images/title.png")));
    }

    @Test
    public void overrideLeavingAssetsIsRejected() throws Exception {
        File root = new File(folder.getRoot(), "mod/assets");
        File assets = new File(folder.getRoot(), "cache/assets");
        TestFiles.write(new File(root, "../escaped.txt"), "escaped");
        assets.mkdirs();

        assertThrows(IOException.class, () -> new FileOverrider(assets).performOverride(root, "../escaped.txt", null));

        assertFalse(new File(assets, "../escaped.txt").exists());
    }
}
