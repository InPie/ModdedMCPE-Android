package org.endercore.android.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Reads the libraries an ELF shared library needs, and writes an empty library that only needs others
public final class ElfFile {
    private static final int ELF_MAGIC = 0x464c457f;
    private static final int CLASS_32 = 1;
    private static final int CLASS_64 = 2;
    private static final int DATA_LITTLE_ENDIAN = 1;
    private static final int ET_DYN = 3;
    private static final int EV_CURRENT = 1;
    private static final int PT_LOAD = 1;
    private static final int PT_DYNAMIC = 2;
    private static final int PT_GNU_STACK = 0x6474e551;
    private static final int PF_W = 2;
    private static final int PF_R = 4;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_HASH = 5;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_DYNSYM = 11;
    private static final int SHF_WRITE = 1;
    private static final int SHF_ALLOC = 2;
    private static final long DT_NULL = 0;
    private static final long DT_NEEDED = 1;
    private static final long DT_HASH = 4;
    private static final long DT_STRTAB = 5;
    private static final long DT_SYMTAB = 6;
    private static final long DT_STRSZ = 10;
    private static final long DT_SYMENT = 11;
    private static final long DT_SONAME = 14;
    private static final long DT_RUNPATH = 29;
    private static final int SEGMENT_ALIGN = 0x10000;

    private ElfFile() {
    }

    public static List<String> readNeededLibraries(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            FileChannel channel = input.getChannel();
            Header header = readHeader(channel, file);
            boolean is64 = header.is64;
            ByteOrder order = header.order;

            List<long[]> loads = new ArrayList<>();
            long dynamicOffset = -1;
            long dynamicSize = 0;
            for (int i = 0; i < header.programHeaderCount; i++) {
                ByteBuffer entry = read(channel, header.programHeaderOffset + (long) i * header.programHeaderSize,
                        header.programHeaderSize, order);
                int type = entry.getInt(0);
                long offset = is64 ? entry.getLong(8) : unsigned(entry.getInt(4));
                long address = is64 ? entry.getLong(16) : unsigned(entry.getInt(8));
                long size = is64 ? entry.getLong(32) : unsigned(entry.getInt(16));
                if (type == PT_LOAD) {
                    loads.add(new long[]{address, offset, size});
                } else if (type == PT_DYNAMIC) {
                    dynamicOffset = offset;
                    dynamicSize = size;
                }
            }
            if (dynamicOffset < 0) {
                return new ArrayList<>();
            }

            int entrySize = is64 ? 16 : 8;
            ByteBuffer dynamic = read(channel, dynamicOffset, (int) dynamicSize, order);
            List<Long> neededOffsets = new ArrayList<>();
            long stringTableAddress = -1;
            for (int position = 0; position + entrySize <= dynamicSize; position += entrySize) {
                long tag = is64 ? dynamic.getLong(position) : dynamic.getInt(position);
                long value = is64 ? dynamic.getLong(position + 8) : unsigned(dynamic.getInt(position + 4));
                if (tag == DT_NULL) {
                    break;
                } else if (tag == DT_NEEDED) {
                    neededOffsets.add(value);
                } else if (tag == DT_STRTAB) {
                    stringTableAddress = value;
                }
            }
            if (neededOffsets.isEmpty()) {
                return new ArrayList<>();
            }
            long stringTableOffset = toFileOffset(loads, stringTableAddress);
            if (stringTableOffset < 0) {
                throw new IOException("String table is outside of loaded segments: " + file.getName());
            }

            List<String> needed = new ArrayList<>();
            for (long nameOffset : neededOffsets) {
                needed.add(readString(channel, stringTableOffset + nameOffset));
            }
            return needed;
        }
    }

    // Writes a library with no code that needs the given libraries, found through runPath.
    // Class, machine and flags come from template, so the library loads in the same process.
    public static void writeNeedingLibrary(File target, File template, String soname, String runPath, List<String> needed) throws IOException {
        Header header;
        try (RandomAccessFile input = new RandomAccessFile(template, "r")) {
            header = readHeader(input.getChannel(), template);
        }
        if (header.order != ByteOrder.LITTLE_ENDIAN) {
            throw new IOException("Big-endian ELF is not supported: " + template.getName());
        }
        boolean is64 = header.is64;
        int headerSize = is64 ? 64 : 52;
        int programHeaderSize = is64 ? 56 : 32;
        int sectionHeaderSize = is64 ? 64 : 40;
        int symbolSize = is64 ? 24 : 16;
        int dynamicEntrySize = is64 ? 16 : 8;
        int programHeaderCount = 3;

        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        strings.write(0);
        int sonameIndex = appendString(strings, soname);
        int runPathIndex = appendString(strings, runPath);
        List<Integer> neededIndexes = new ArrayList<>();
        for (String name : needed) {
            neededIndexes.add(appendString(strings, name));
        }
        byte[] dynamicStrings = strings.toByteArray();

        ByteArrayOutputStream sectionNames = new ByteArrayOutputStream();
        sectionNames.write(0);
        int dynsymName = appendString(sectionNames, ".dynsym");
        int hashName = appendString(sectionNames, ".hash");
        int dynstrName = appendString(sectionNames, ".dynstr");
        int dynamicName = appendString(sectionNames, ".dynamic");
        int shstrtabName = appendString(sectionNames, ".shstrtab");
        byte[] sectionNameBytes = sectionNames.toByteArray();

        int dynamicCount = needed.size() + 8;
        int symtabOffset = align(headerSize + programHeaderCount * programHeaderSize, 8);
        int hashOffset = align(symtabOffset + symbolSize, 4);
        int hashSize = 16;
        int strtabOffset = hashOffset + hashSize;
        int dynamicOffset = align(strtabOffset + dynamicStrings.length, 8);
        int dynamicSize = dynamicCount * dynamicEntrySize;
        int loadedSize = dynamicOffset + dynamicSize;
        int shstrtabOffset = loadedSize;
        int sectionHeaderOffset = align(shstrtabOffset + sectionNameBytes.length, 8);
        int fileSize = sectionHeaderOffset + 6 * sectionHeaderSize;

        ByteBuffer out = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0, ELF_MAGIC);
        out.put(4, (byte) (is64 ? CLASS_64 : CLASS_32));
        out.put(5, (byte) DATA_LITTLE_ENDIAN);
        out.put(6, (byte) EV_CURRENT);
        out.position(16);
        out.putShort((short) ET_DYN);
        out.putShort((short) header.machine);
        out.putInt(EV_CURRENT);
        putAddress(out, is64, 0);
        putAddress(out, is64, headerSize);
        putAddress(out, is64, sectionHeaderOffset);
        out.putInt(header.flags);
        out.putShort((short) headerSize);
        out.putShort((short) programHeaderSize);
        out.putShort((short) programHeaderCount);
        out.putShort((short) sectionHeaderSize);
        out.putShort((short) 6);
        out.putShort((short) 5);

        out.position(headerSize);
        putProgramHeader(out, is64, PT_LOAD, PF_R | PF_W, 0, loadedSize, SEGMENT_ALIGN);
        putProgramHeader(out, is64, PT_DYNAMIC, PF_R | PF_W, dynamicOffset, dynamicSize, is64 ? 8 : 4);
        putProgramHeader(out, is64, PT_GNU_STACK, PF_R | PF_W, 0, 0, 16);

        // The symbol table holds only the null symbol
        out.position(hashOffset);
        out.putInt(1);
        out.putInt(1);
        out.putInt(0);
        out.putInt(0);

        out.position(strtabOffset);
        out.put(dynamicStrings);

        out.position(dynamicOffset);
        for (int index : neededIndexes) {
            putDynamic(out, is64, DT_NEEDED, index);
        }
        putDynamic(out, is64, DT_SONAME, sonameIndex);
        putDynamic(out, is64, DT_RUNPATH, runPathIndex);
        putDynamic(out, is64, DT_HASH, hashOffset);
        putDynamic(out, is64, DT_STRTAB, strtabOffset);
        putDynamic(out, is64, DT_SYMTAB, symtabOffset);
        putDynamic(out, is64, DT_STRSZ, dynamicStrings.length);
        putDynamic(out, is64, DT_SYMENT, symbolSize);
        putDynamic(out, is64, DT_NULL, 0);

        out.position(shstrtabOffset);
        out.put(sectionNameBytes);

        out.position(sectionHeaderOffset + sectionHeaderSize);
        putSectionHeader(out, is64, dynsymName, SHT_DYNSYM, SHF_ALLOC, symtabOffset, symbolSize, 3, 1, 8, symbolSize);
        putSectionHeader(out, is64, hashName, SHT_HASH, SHF_ALLOC, hashOffset, hashSize, 1, 0, 4, 4);
        putSectionHeader(out, is64, dynstrName, SHT_STRTAB, SHF_ALLOC, strtabOffset, dynamicStrings.length, 0, 0, 1, 0);
        putSectionHeader(out, is64, dynamicName, SHT_DYNAMIC, SHF_ALLOC | SHF_WRITE, dynamicOffset, dynamicSize, 3, 0, 8, dynamicEntrySize);
        putSectionHeader(out, is64, shstrtabName, SHT_STRTAB, 0, shstrtabOffset, sectionNameBytes.length, 0, 0, 1, 0);

        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(out.array());
        }
    }

    private static final class Header {
        boolean is64;
        ByteOrder order;
        int machine;
        int flags;
        long programHeaderOffset;
        int programHeaderSize;
        int programHeaderCount;
    }

    private static Header readHeader(FileChannel channel, File file) throws IOException {
        ByteBuffer ident = read(channel, 0, 16, ByteOrder.LITTLE_ENDIAN);
        if (ident.getInt(0) != ELF_MAGIC) {
            throw new IOException("Not an ELF file: " + file.getName());
        }
        int elfClass = ident.get(4);
        if (elfClass != CLASS_32 && elfClass != CLASS_64) {
            throw new IOException("Unknown ELF class " + elfClass + ": " + file.getName());
        }
        Header header = new Header();
        header.is64 = elfClass == CLASS_64;
        header.order = ident.get(5) == DATA_LITTLE_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer buffer = read(channel, 0, header.is64 ? 64 : 52, header.order);
        header.machine = buffer.getShort(18) & 0xffff;
        header.programHeaderOffset = header.is64 ? buffer.getLong(32) : unsigned(buffer.getInt(28));
        header.flags = buffer.getInt(header.is64 ? 48 : 36);
        header.programHeaderSize = buffer.getShort(header.is64 ? 54 : 42) & 0xffff;
        header.programHeaderCount = buffer.getShort(header.is64 ? 56 : 44) & 0xffff;
        return header;
    }

    private static int appendString(ByteArrayOutputStream strings, String value) {
        int index = strings.size();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        strings.write(bytes, 0, bytes.length);
        strings.write(0);
        return index;
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    private static void putAddress(ByteBuffer out, boolean is64, long value) {
        if (is64) {
            out.putLong(value);
        } else {
            out.putInt((int) value);
        }
    }

    private static void putProgramHeader(ByteBuffer out, boolean is64, int type, int flags, long offset, long size, long alignment) {
        out.putInt(type);
        if (is64) {
            out.putInt(flags);
            out.putLong(offset);
            out.putLong(offset);
            out.putLong(offset);
            out.putLong(size);
            out.putLong(size);
            out.putLong(alignment);
        } else {
            out.putInt((int) offset);
            out.putInt((int) offset);
            out.putInt((int) offset);
            out.putInt((int) size);
            out.putInt((int) size);
            out.putInt(flags);
            out.putInt((int) alignment);
        }
    }

    private static void putDynamic(ByteBuffer out, boolean is64, long tag, long value) {
        putAddress(out, is64, tag);
        putAddress(out, is64, value);
    }

    // Sections are loaded at their file offsets
    private static void putSectionHeader(ByteBuffer out, boolean is64, int name, int type, int flags, long offset,
                                         long size, int link, int info, int alignment, int entrySize) {
        out.putInt(name);
        out.putInt(type);
        putAddress(out, is64, flags);
        putAddress(out, is64, (flags & SHF_ALLOC) != 0 ? offset : 0);
        putAddress(out, is64, offset);
        putAddress(out, is64, size);
        out.putInt(link);
        out.putInt(info);
        putAddress(out, is64, alignment);
        putAddress(out, is64, entrySize);
    }

    private static long toFileOffset(List<long[]> loads, long address) {
        for (long[] load : loads) {
            if (address >= load[0] && address < load[0] + load[2]) {
                return address - load[0] + load[1];
            }
        }
        return -1;
    }

    private static String readString(FileChannel channel, long offset) throws IOException {
        StringBuilder builder = new StringBuilder();
        ByteBuffer buffer = ByteBuffer.allocate(64);
        long position = offset;
        while (true) {
            buffer.clear();
            int count = channel.read(buffer, position);
            if (count <= 0) {
                throw new IOException("Unterminated string at " + offset);
            }
            for (int i = 0; i < count; i++) {
                byte b = buffer.get(i);
                if (b == 0) {
                    return builder.toString();
                }
                builder.append((char) (b & 0xff));
            }
            position += count;
        }
    }

    private static ByteBuffer read(FileChannel channel, long offset, int size, ByteOrder order) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size).order(order);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, offset + buffer.position()) <= 0) {
                throw new IOException("Unexpected end of ELF file at " + (offset + buffer.position()));
            }
        }
        buffer.flip();
        return buffer;
    }

    private static long unsigned(int value) {
        return value & 0xffffffffL;
    }
}
