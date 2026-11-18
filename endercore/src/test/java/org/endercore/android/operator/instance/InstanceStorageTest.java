package org.endercore.android.operator.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.endercore.android.TestFiles;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class InstanceStorageTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File instanceDir;
    private InstanceStorage storage;

    @Before
    public void setUp() {
        instanceDir = new File(folder.getRoot(), "abc12345-1.1.5");
        storage = new InstanceStorage(instanceDir);
    }

    private void write(String path, String content) throws Exception {
        TestFiles.write(new File(instanceDir, path), content);
    }

    private String read(String path) throws Exception {
        return TestFiles.read(new File(instanceDir, path));
    }

    @Test
    public void layout1MovesToItsPlaces() throws Exception {
        write("data/games/com.mojang/minecraftWorlds/w/level.dat", "world");
        write("data/options.txt", "legacy options");
        write("data/cache/minecraftpe/item", "cache");
        write("data/com.mojang.minecraftpe/games/com.mojang/minecraftpe/options.txt", "1.x options");
        write("data/AFRequestCache/request", "request");
        write("crash/dump.dmp", "dump");

        storage.migrateFromLayout1();

        assertEquals("world", read("external/games/com.mojang/minecraftWorlds/w/level.dat"));
        assertEquals("legacy options", TestFiles.read(storage.getLegacyOptionsFile()));
        assertEquals("cache", read("data/cache/minecraftpe/item"));
        assertEquals("1.x options", read("data/com.mojang.minecraftpe/games/com.mojang/minecraftpe/options.txt"));
        assertEquals("request", read("data/files/AFRequestCache/request"));
        assertEquals("dump", read("data/crash/dump.dmp"));
        assertFalse(new File(instanceDir, "crash").exists());
        assertFalse(new File(instanceDir, "data/games").exists());
        assertEquals(2, instanceDir.list().length);
    }

    @Test
    public void interruptedMigrationGoesOn() throws Exception {
        write("data.layout1/options.txt", "legacy options");
        write("data.layout1/games/world.txt", "world");
        write("data/files/AFRequestCache/request", "moved before the interruption");

        storage.migrateFromLayout1();

        assertEquals("legacy options", TestFiles.read(storage.getLegacyOptionsFile()));
        assertEquals("world", read("external/games/world.txt"));
        assertEquals("moved before the interruption", read("data/files/AFRequestCache/request"));
        assertFalse(new File(instanceDir, "data.layout1").exists());
    }

    @Test
    public void instanceWithoutDataHasNothingToMove() throws Exception {
        write("instance.json", "{}");

        storage.migrateFromLayout1();

        assertEquals(1, instanceDir.list().length);
        assertTrue(new File(instanceDir, "instance.json").isFile());
    }
}
