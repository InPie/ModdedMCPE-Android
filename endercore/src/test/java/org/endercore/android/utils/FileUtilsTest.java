package org.endercore.android.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.endercore.android.TestFiles;
import org.junit.Assume;
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

    @Test
    public void resolveInsideKeepsNestedPath() throws Exception {
        File dir = folder.getRoot();

        assertEquals(new File(dir, "a/b.txt"), FileUtils.resolveInside(dir, "a/b.txt"));
    }

    @Test
    public void resolveInsideRejectsPathLeavingDirectory() {
        File dir = new File(folder.getRoot(), "mod");

        assertThrows(IOException.class, () -> FileUtils.resolveInside(dir, "../mod-other/b.txt"));
        assertThrows(IOException.class, () -> FileUtils.resolveInside(dir, "a/../../b.txt"));
        assertThrows(IOException.class, () -> FileUtils.resolveInside(dir, ""));
    }

    @Test
    public void moveMergingJoinsFolders() throws Exception {
        TestFiles.write(new File(folder.getRoot(), "from/a/one.txt"), "one");
        TestFiles.write(new File(folder.getRoot(), "to/a/two.txt"), "two");

        FileUtils.moveMerging(new File(folder.getRoot(), "from"), new File(folder.getRoot(), "to"));

        assertEquals("one", TestFiles.read(new File(folder.getRoot(), "to/a/one.txt")));
        assertEquals("two", TestFiles.read(new File(folder.getRoot(), "to/a/two.txt")));
        assertFalse(new File(folder.getRoot(), "from").exists());
    }

    @Test
    public void moveMergingKeepsBothFilesOfOneName() throws Exception {
        File from = new File(folder.getRoot(), "from/one.txt");
        File to = new File(folder.getRoot(), "to/one.txt");
        TestFiles.write(from, "new");
        TestFiles.write(to, "old");

        assertThrows(IOException.class, () -> FileUtils.moveMerging(from.getParentFile(), to.getParentFile()));

        assertEquals("new", TestFiles.read(from));
        assertEquals("old", TestFiles.read(to));
    }

    // Making links needs extra rights on Windows
    private File linkTo(File target, File link) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException(e);
        }
        return link;
    }

    @Test
    public void linksAreRemovedAndCopiedWithoutTheirTargets() throws Exception {
        File target = new File(folder.getRoot(), "shared/sign-in.txt");
        TestFiles.write(target, "keep");
        File dir = folder.newFolder("instance");
        TestFiles.write(new File(dir, "world.txt"), "world");
        linkTo(target.getParentFile(), new File(dir, "xal"));

        assertTrue(FileUtils.isSymbolicLink(new File(dir, "xal")));
        assertFalse(FileUtils.isSymbolicLink(new File(dir, "world.txt")));
        File copy = new File(folder.getRoot(), "copy");
        FileUtils.copyDirectory(dir, copy);
        FileUtils.removeFiles(dir);

        assertArrayEquals(new String[]{"world.txt"}, copy.list());
        assertFalse(dir.exists());
        assertEquals("keep", TestFiles.read(target));
    }
}
