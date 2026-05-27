package org.endercore.android.utils;

import android.os.Build;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class CPUArch {
    public static final int ARCH_ARM_32 = 0x1;
    public static final int ARCH_ARM_64 = 0x2;
    public static final int ARCH_X86_32 = 0x3;
    public static final int ARCH_X86_64 = 0x4;
    public static final int ARCH_OTHERS = 0x0;

    public static final String ARCH_NAME_ARM_32 = "armeabi-v7a";
    public static final String ARCH_NAME_ARM_64 = "arm64-v8a";
    public static final String ARCH_NAME_X86_32 = "x86";
    public static final String ARCH_NAME_X86_64 = "x86_64";
    public static final String ARCH_NAME_OTHERS = "others";

    public static int getBitForABI(@NotNull String str) {
        switch (str) {
            case "armeabi":
                return 1 << 0;
            case "armeabi-v7a":
                return 1 << 1;
            case "arm64-v8a":
                return 1 << 2;
            case "x86":
                return 1 << 3;
            case "x86_64":
                return 1 << 4;
            case "mips":
                return 1 << 5;
            case "mips64":
                return 1 << 6;
            default:
                Log.w("EnderCore", "Unknown ABI: " + str);
                return 0;
        }
    }

    public static int getELFArch(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] elfHeader = new byte[64];
        int bytesRead = fileInputStream.read(elfHeader);
        fileInputStream.close();
        if (bytesRead < 52) {
            throw new IOException("Could not read this elf file. Bytes read: " + bytesRead);
        }
        int machineIdentifier = ((elfHeader[0x13] & 0xFF) << 8) | (elfHeader[0x12] & 0xFF);
        
        Log.d("EnderCore-CPUArch", "Debug ELF Machine Identifier: " + machineIdentifier + " (0x" + Integer.toHexString(machineIdentifier) + ") for file: " + file.getAbsolutePath());

        switch (machineIdentifier) {
            case 40: // EM_ARM
                return ARCH_ARM_32;
            case 183: // EM_AARCH64
                return ARCH_ARM_64;
            case 3: // EM_386
                return ARCH_X86_32;
            case 62: // EM_X86_64
                return ARCH_X86_64;
            default:
                return ARCH_OTHERS;
        }
    }

    public static String getArchName(int archId) {
        switch (archId) {
            case ARCH_ARM_32:
                return ARCH_NAME_ARM_32;
            case ARCH_ARM_64:
                return ARCH_NAME_ARM_64;
            case ARCH_X86_32:
                return ARCH_NAME_X86_32;
            case ARCH_X86_64:
                return ARCH_NAME_X86_64;
        }
        return ARCH_NAME_OTHERS;
    }

    public static String[] getSystemSupportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (android.os.Process.is64Bit()) {
                return Build.SUPPORTED_64_BIT_ABIS;
            } else {
                return Build.SUPPORTED_32_BIT_ABIS;
            }
        }
        return Build.SUPPORTED_ABIS;
    }

    public static boolean isEnderCoreSupportedAbi(String abiName) {
        return abiName.equals(ARCH_NAME_ARM_32) || abiName.equals(ARCH_NAME_ARM_64) || abiName.equals(ARCH_NAME_X86_32) || abiName.equals(ARCH_NAME_X86_64);
    }

    public static boolean is64BitArch(String abiName) {
        return abiName.equals(ARCH_NAME_ARM_64) || abiName.equals(ARCH_NAME_X86_64);
    }
}
