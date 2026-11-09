package org.endercore.android.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.endercore.android.TestFiles;
import org.endercore.android.exception.LauncherException;
import org.endercore.android.operator.instance.model.ApkCheck;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class ApkVerifierTest {
    // SHA-256 of "abc"
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File file(String content) throws Exception {
        File file = new File(folder.getRoot(), "file");
        TestFiles.write(file, content);
        return file;
    }

    private File apk(String entryName) throws Exception {
        File apk = new File(folder.getRoot(), "game.apk");
        TestFiles.writeZip(apk, Collections.singletonMap(entryName, "so".getBytes(StandardCharsets.UTF_8)));
        return apk;
    }

    @Test
    public void checksumWithoutCatalogValueIsReturned() throws Exception {
        assertEquals(ABC_SHA256, ApkVerifier.checkSha256(file("abc"), null));
        assertEquals(ABC_SHA256, ApkVerifier.checkSha256(file("abc"), " "));
    }

    @Test
    public void checksumIgnoresCaseAndSpaces() throws Exception {
        assertEquals(ABC_SHA256, ApkVerifier.checkSha256(file("abc"), " " + ABC_SHA256.toUpperCase() + "\n"));
    }

    @Test
    public void differentChecksumIsRejected() throws Exception {
        File file = file("abd");

        assertThrows(LauncherException.class, () -> ApkVerifier.checkSha256(file, ABC_SHA256));
    }

    @Test
    public void apkWithGameLibraryIsAccepted() throws Exception {
        ApkVerifier.requireGameLibrary(apk("lib/armeabi-v7a/libminecraftpe.so"));
        ApkVerifier.requireGameLibrary(apk("lib/x86/libminecraftpe.so"));
    }

    @Test
    public void apkWithoutGameLibraryIsRejected() throws Exception {
        File other = apk("lib/armeabi-v7a/libother.so");
        assertThrows(LauncherException.class, () -> ApkVerifier.requireGameLibrary(other));

        File outsideLib = apk("assets/libminecraftpe.so");
        assertThrows(LauncherException.class, () -> ApkVerifier.requireGameLibrary(outsideLib));
    }

    @Test
    public void onlyMojangCertificateCounts() {
        ApkCheck check = new ApkCheck();
        assertFalse(ApkVerifier.isSignedByMojang(check));
        assertFalse(ApkVerifier.isSignedByMojang(null));

        check.setSignerSha256("3d59c1fa1432ce1b316807b600a1bc6bf2ea1b57eca6a6bfb4f18316cbdcd242");
        assertFalse(ApkVerifier.isSignedByMojang(check));

        check.setSignerSha256("31be40096f931cd7f11d5e262d2b2c437c44385fb4ecbc1013d95a7435816f9c");
        assertTrue(ApkVerifier.isSignedByMojang(check));
    }
}
