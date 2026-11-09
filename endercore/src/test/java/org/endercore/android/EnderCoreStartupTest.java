package org.endercore.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class EnderCoreStartupTest {
    private static final String NMOD_UUID = "6f1c3a52-2c1f-4c8e-9d43-0a7b1f2e3d4c";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File optionsFile;

    @Before
    public void setUp() {
        optionsFile = new File(folder.getRoot(), "launcher_data/options.json");
    }

    private EnderCore startCore() throws Exception {
        return TestCore.start(folder.getRoot());
    }

    @Test
    public void damagedOptionsFallBackToDefaults() throws Exception {
        String damaged = "{\"use_nmods\": tru";
        TestFiles.write(optionsFile, damaged);

        EnderCore core = startCore();

        assertFalse(core.getOptionsManager().getUseNMods());
        assertEquals(0, core.getOptionsManager().getInstalledNMods().length);
        assertEquals(damaged, TestFiles.read(optionsFile));
    }

    @Test
    public void emptyOptionsFallBackToDefaults() throws Exception {
        TestFiles.write(optionsFile, "");

        assertEquals(0, startCore().getOptionsManager().getInstalledNMods().length);
    }

    @Test
    public void invalidNModEntriesAreDropped() throws Exception {
        TestFiles.write(optionsFile, "{\"installed_nmods\":[null,{\"enabled\":true}]}");

        assertEquals(0, startCore().getOptionsManager().getInstalledNMods().length);
    }

    @Test
    public void startDoesNotWriteOptions() throws Exception {
        startCore();

        assertFalse(optionsFile.exists());
    }

    @Test
    public void optionsSurviveRestart() throws Exception {
        EnderCore core = startCore();
        core.getOptionsManager().setUseNMods(true);
        core.getOptionsManager().saveDataToFile();

        assertTrue(startCore().getOptionsManager().getUseNMods());
    }

    @Test
    public void autoLicenseIsOffByDefault() throws Exception {
        assertFalse(startCore().getOptionsManager().getAutoLicense());
    }

    @Test
    public void savedAutoLicenseIsKept() throws Exception {
        TestFiles.write(optionsFile, "{\"auto_license\":true}");

        assertTrue(startCore().getOptionsManager().getAutoLicense());
    }

    @Test
    public void damagedNModManifestDoesNotBreakStart() throws Exception {
        TestFiles.write(optionsFile, "{\"installed_nmods\":[{\"uuid\":\"" + NMOD_UUID + "\",\"enabled\":true}]}");
        TestFiles.write(new File(folder.getRoot(), "launcher_data/nmods/" + NMOD_UUID + "/nmod_manifest.json"), "{\"uuid\": ");

        assertTrue(startCore().getNModManager().getAllNMods().isEmpty());
    }

    @Test
    public void emptyNModManifestDoesNotBreakStart() throws Exception {
        TestFiles.write(optionsFile, "{\"installed_nmods\":[{\"uuid\":\"" + NMOD_UUID + "\",\"enabled\":true}]}");
        TestFiles.write(new File(folder.getRoot(), "launcher_data/nmods/" + NMOD_UUID + "/nmod_manifest.json"), "");

        assertTrue(startCore().getNModManager().getAllNMods().isEmpty());
    }
}
