package org.endercore.android.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.endercore.android.exception.LauncherException;
import org.endercore.android.operator.instance.model.ApkCheck;
import org.endercore.android.utils.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

// Game APKs for the optional tests are pushed to getExternalFilesDir() as mojang.apk and resigned.apk
@RunWith(AndroidJUnit4.class)
public class ApkVerifierDeviceTest {
    private static final int LOCAL_HEADER = 0x04034b50;

    private Context context;
    private File dir;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        dir = new File(context.getCacheDir(), "apk_verifier");
        FileUtils.removeFiles(dir);
        assertTrue(dir.mkdirs());
    }

    @After
    public void tearDown() {
        FileUtils.removeFiles(dir);
    }

    // The test APK itself, signed with the debug key
    private File signedApk() throws Exception {
        File apk = new File(dir, "signed.apk");
        copy(new File(context.getApplicationInfo().sourceDir), apk, Long.MAX_VALUE);
        return apk;
    }

    private static void copy(File source, File target, long limit) throws Exception {
        try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long left = limit;
            int count;
            while (left > 0 && (count = input.read(buffer, 0, (int) Math.min(buffer.length, left))) != -1) {
                output.write(buffer, 0, count);
                left -= count;
            }
        }
    }

    // Changes one byte inside the data of the entry, keeping the archive readable
    private static void changeEntry(File apk, String entryName) throws Exception {
        byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
        try (RandomAccessFile file = new RandomAccessFile(apk, "rw")) {
            for (long offset = 0; offset < file.length() - 30; offset++) {
                file.seek(offset);
                if (Integer.reverseBytes(file.readInt()) != LOCAL_HEADER) {
                    continue;
                }
                file.seek(offset + 26);
                int nameLength = Short.reverseBytes(file.readShort()) & 0xffff;
                int extraLength = Short.reverseBytes(file.readShort()) & 0xffff;
                byte[] actual = new byte[nameLength];
                file.readFully(actual);
                if (!Arrays.equals(actual, name)) {
                    continue;
                }
                long data = offset + 30 + nameLength + extraLength + 16;
                file.seek(data);
                int value = file.read();
                file.seek(data);
                file.write(value ^ 0xff);
                return;
            }
        }
        throw new AssertionError("Entry not found: " + entryName);
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void signedApkGivesItsSigner() throws Exception {
        ApkCheck check = ApkVerifier.readSigner(context, signedApk());

        byte[] installed = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures[0].toByteArray();
        assertEquals(sha256(installed), check.getSignerSha256());
        assertTrue(check.getSignerName().contains("CN=Android Debug"));
        assertFalse(ApkVerifier.isSignedByMojang(check));
    }

    @Test
    public void changedApkIsRejected() throws Exception {
        File apk = signedApk();
        changeEntry(apk, "classes.dex");

        assertThrows(LauncherException.class, () -> ApkVerifier.readSigner(context, apk));
    }

    @Test
    public void truncatedApkIsRejected() throws Exception {
        File source = new File(context.getApplicationInfo().sourceDir);
        File apk = new File(dir, "truncated.apk");
        copy(source, apk, source.length() / 2);

        assertThrows(LauncherException.class, () -> ApkVerifier.readSigner(context, apk));
    }

    @Test
    public void mojangApkIsRecognised() throws Exception {
        File apk = new File(context.getExternalFilesDir(null), "mojang.apk");
        assumeTrue(apk.isFile());

        ApkCheck check = ApkVerifier.readSigner(context, apk);

        assertTrue(ApkVerifier.isSignedByMojang(check));
        assertTrue(check.getSignerName().contains("O=Mojang AB"));
    }

    @Test
    public void resignedApkIsNotMojang() throws Exception {
        File apk = new File(context.getExternalFilesDir(null), "resigned.apk");
        assumeTrue(apk.isFile());

        assertFalse(ApkVerifier.isSignedByMojang(ApkVerifier.readSigner(context, apk)));
    }

    @Test
    public void changedMojangApkIsRejected() throws Exception {
        File source = new File(context.getExternalFilesDir(null), "mojang.apk");
        assumeTrue(source.isFile());
        File apk = new File(dir, "mojang.apk");
        copy(source, apk, Long.MAX_VALUE);
        changeEntry(apk, "classes.dex");

        assertThrows(LauncherException.class, () -> ApkVerifier.readSigner(context, apk));
    }
}
