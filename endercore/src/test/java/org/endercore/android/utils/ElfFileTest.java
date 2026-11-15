package org.endercore.android.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.endercore.android.TestFiles;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ElfFileTest {
    private static final int EM_ARM = 40;
    private static final int EM_AARCH64 = 183;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // ELF header only, enough to serve as a template
    private File template(boolean is64, int machine) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(is64 ? 64 : 52).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0, 0x464c457f);
        header.put(4, (byte) (is64 ? 2 : 1));
        header.put(5, (byte) 1);
        header.put(6, (byte) 1);
        header.putShort(16, (short) 3);
        header.putShort(18, (short) machine);
        File file = new File(folder.getRoot(), "template" + machine + ".so");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(header.array());
        }
        return file;
    }

    private List<String> roundTrip(boolean is64, int machine, List<String> needed) throws Exception {
        File library = new File(folder.getRoot(), "group" + machine + ".so");
        ElfFile.writeNeedingLibrary(library, template(is64, machine), "libgroup.so", "/data/libs", needed);
        return ElfFile.readNeededLibraries(library);
    }

    @Test
    public void written64BitLibraryNeedsGivenLibraries() throws Exception {
        List<String> needed = Arrays.asList("libminecraftpe.so", "libMallocRedirect.so", "libc++_shared.so");

        assertEquals(needed, roundTrip(true, EM_AARCH64, needed));
    }

    @Test
    public void written32BitLibraryNeedsGivenLibraries() throws Exception {
        List<String> needed = Arrays.asList("libminecraftpe.so", "libfmod.so");

        assertEquals(needed, roundTrip(false, EM_ARM, needed));
    }

    @Test
    public void writtenLibraryKeepsTemplateMachine() throws Exception {
        File library = new File(folder.getRoot(), "group.so");
        ElfFile.writeNeedingLibrary(library, template(false, EM_ARM), "libgroup.so", "/data/libs",
                Collections.singletonList("libminecraftpe.so"));

        ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(library.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, header.get(4));
        assertEquals(EM_ARM, header.getShort(18));
    }

    @Test
    public void notElfIsRejected() throws Exception {
        File file = new File(folder.getRoot(), "text.so");
        TestFiles.write(file, "not an elf file at all, just some text");

        assertThrows(IOException.class, () -> ElfFile.readNeededLibraries(file));
    }
}
