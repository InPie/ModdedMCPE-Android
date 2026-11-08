package org.endercore.android.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.endercore.android.EnderCore;
import org.endercore.android.TestCore;
import org.endercore.android.TestFiles;
import org.endercore.android.exception.NModException;
import org.endercore.android.mod.nmod.NMod;
import org.endercore.android.mod.nmod.NModPackage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class NModManagerTest {
    private static final String UUID = "6f1c3a52-2c1f-4c8e-9d43-0a7b1f2e3d4c";
    private static final String MANIFEST = "{\"uuid\":\"" + UUID + "\",\"name\":\"Test\",\"version_name\":\"1.0\","
            + "\"version_code\":1,\"min_sdk_version\":1,\"target_sdk_version\":1,\"manifest_version\":1,\"game_supports\":[]}";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private EnderCore core;

    @Before
    public void setUp() throws Exception {
        core = TestCore.start(folder.getRoot());
    }

    private NModPackage newPackage(String entryName, String content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(NMod.MANIFEST_PATH, MANIFEST.getBytes(StandardCharsets.UTF_8));
        entries.put(entryName, content.getBytes(StandardCharsets.UTF_8));
        File file = new File(folder.getRoot(), "packages/test.nmod");
        TestFiles.writeZip(file, entries);
        return new NModPackage(file);
    }

    private File installationDir() {
        return new File(core.getFileEnvironment().getNModDirPathFor(UUID));
    }

    @Test
    public void installUnpacksFiles() throws Exception {
        core.getNModManager().installNMod(newPackage("android/data.txt", "data"));

        assertEquals("data", TestFiles.read(new File(installationDir(), "android/data.txt")));
        assertEquals(1, core.getNModManager().getAllNMods().size());
    }

    @Test
    public void installRejectsEntriesLeavingModFolder() throws Exception {
        NModPackage nmodPackage = newPackage("../escaped.txt", "escaped");

        assertThrows(NModException.class, () -> core.getNModManager().installNMod(nmodPackage));

        assertFalse(new File(installationDir().getParentFile(), "escaped.txt").exists());
        assertTrue(core.getNModManager().getAllNMods().isEmpty());
    }

    @Test
    public void uninstallRemovesOnlyNMod() throws Exception {
        core.getNModManager().installNMod(newPackage("android/data.txt", "data"));

        core.getNModManager().uninstallNMod(UUID);

        assertTrue(core.getNModManager().getAllNMods().isEmpty());
        assertFalse(installationDir().exists());
        assertEquals(0, core.getOptionsManager().getInstalledNMods().length);
    }
}
