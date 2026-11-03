package org.endercore.android.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.endercore.android.TestFiles;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

public class FileUtilsTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writeAtomicCreatesFile() throws Exception {
        File target = new File(folder.getRoot(), "options.json");

        FileUtils.writeAtomic(target, "Мир");

        assertEquals("Мир", TestFiles.read(target));
    }

    @Test
    public void writeAtomicReplacesFileAndLeavesNoTempFiles() throws Exception {
        File target = new File(folder.getRoot(), "options.json");
        FileUtils.writeAtomic(target, "old content, longer than the new one");

        FileUtils.writeAtomic(target, "new");

        assertEquals("new", TestFiles.read(target));
        assertArrayEquals(new String[]{"options.json"}, folder.getRoot().list());
    }

    @Test
    public void writeAtomicFailureLeavesTargetAndNoTempFiles() throws Exception {
        // Moving over a non-empty directory fails
        File target = folder.newFolder("target");
        File inside = new File(target, "inside.txt");
        TestFiles.write(inside, "keep");

        assertThrows(IOException.class, () -> FileUtils.writeAtomic(target, "new"));

        assertEquals("keep", TestFiles.read(inside));
        assertArrayEquals(new String[]{"target"}, folder.getRoot().list());
    }

    @Test
    public void writeAtomicFailsWithoutParentDirectory() {
        File target = new File(folder.getRoot(), "missing/options.json");

        assertThrows(IOException.class, () -> FileUtils.writeAtomic(target, "new"));

        assertFalse(target.exists());
    }

    @Test
    public void copyFileCopiesWholeContent() throws Exception {
        byte[] content = new byte[5 * 1024 * 1024];
        new Random(1).nextBytes(content);
        File source = folder.newFile("game.apk");
        Files.write(source.toPath(), content);
        File target = new File(folder.getRoot(), "copy/game.apk");

        FileUtils.copy(source, target);

        assertTrue(target.isFile());
        assertArrayEquals(content, Files.readAllBytes(target.toPath()));
    }
}
