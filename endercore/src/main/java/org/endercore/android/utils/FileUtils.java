package org.endercore.android.utils;

import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class FileUtils {
    public static void copy(File from, File to) throws IOException {
        if (!from.exists())
            throw new IOException("File " + from.getAbsolutePath() + " does not exists.");
        if (!to.exists()) {
            if (to.getParentFile() != null && !to.getParentFile().exists()) {
                boolean mkdirsResult = to.getParentFile().mkdirs();
                if (!mkdirsResult)
                    throw new IOException("Failed to mkdirs: " + to.getParentFile().getAbsolutePath() + ".");
            }
            boolean createdNewFile = to.createNewFile();
            if (!createdNewFile)
                throw new IOException("Failed to create new file at: " + to.getAbsolutePath() + ".");
        } else {
            to.setWritable(true);
        }
        try (FileChannel input = new FileInputStream(from).getChannel();
             FileChannel output = new FileOutputStream(to).getChannel()) {
            long size = input.size();
            long position = 0;
            // transferFrom may copy fewer bytes than requested
            while (position < size) {
                long transferred = output.transferFrom(input, position, size - position);
                if (transferred <= 0)
                    throw new IOException("Failed to copy " + from.getAbsolutePath() + ": stopped at " + position + " of " + size + " bytes.");
                position += transferred;
            }
        }
    }

    public static void copy(InputStream from, File to) throws IOException {
        if (!to.exists()) {
            if (to.getParentFile() != null && !to.getParentFile().exists()) {
                boolean mkdirsResult = to.getParentFile().mkdirs();
                if (!mkdirsResult)
                    throw new IOException("Failed to mkdirs: " + to.getParentFile().getAbsolutePath() + ".");
            }
            boolean createdNewFile = to.createNewFile();
            if (!createdNewFile)
                throw new IOException("Failed to create new file at: " + to.getAbsolutePath() + ".");
        } else {
            to.setWritable(true);
        }
        try (FileOutputStream output = new FileOutputStream(to)) {
            copy(from, output);
        }
    }

    public static void copy(File from, OutputStream to) throws IOException {
        try (FileInputStream input = new FileInputStream(from)) {
            copy(input, to);
        }
    }

    public static void writeAtomic(File target, String content) throws IOException {
        File temp = File.createTempFile(target.getName() + ".", ".tmp", target.getAbsoluteFile().getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(content.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            moveReplacing(temp, target);
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
    }

    public static void moveReplacing(File from, File to) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } else if (!from.renameTo(to)) {
            throw new IOException("Failed to move " + from.getAbsolutePath() + " to " + to.getAbsolutePath() + ".");
        }
    }

    public static void copy(InputStream from, OutputStream to) throws IOException {
        byte[] buffer = new byte[1 << 10];
        int len;
        while ((len = from.read(buffer)) > 0) {
            to.write(buffer, 0, len);
        }
    }

    public static void copyDirectory(File sourceDir, File destinationDir) throws IOException {
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + destinationDir.getAbsolutePath());
        }
        for (File child : children) {
            File target = new File(destinationDir, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target);
            } else {
                copy(child, target);
            }
        }
    }

    public static void removeFiles(File path) {
        if (!path.exists())
            return;
        File[] listFiles = path.listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                if (file.isDirectory())
                    removeFiles(file);
                else {
                    if (!file.delete()) {
                        file.setWritable(true);
                        file.delete();
                    }
                }
            }
        }
        if (!path.delete()) {
            path.setWritable(true);
            path.delete();
        }
    }

    public static String readFileAsString(File file) throws IOException {
        if (!file.exists())
            throw new IOException("File " + file.getAbsolutePath() + " dose not exists.");
        return readFileAsString(new FileReader(file));
    }

    public static String readFileAsString(FileReader input) throws IOException {
        char[] buffer = new char[1024];
        int len;
        StringBuilder builder = new StringBuilder();
        while ((len = input.read(buffer)) > 0) {
            builder.append(buffer, 0, len);
        }
        input.close();
        return builder.toString();
    }
}
