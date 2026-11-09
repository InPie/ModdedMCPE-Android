package org.endercore.android.operator;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.endercore.android.exception.LauncherException;
import org.endercore.android.operator.instance.model.ApkCheck;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkVerifier {
    // CN=Mojang AB, O=Mojang AB, C=SE, found on the original APKs of 0.6.1 to 1.5.1.2
    private static final Set<String> MOJANG_CERTIFICATES = Collections.singleton(
            "31be40096f931cd7f11d5e262d2b2c437c44385fb4ecbc1013d95a7435816f9c");

    private ApkVerifier() {
    }

    public static boolean isSignedByMojang(ApkCheck check) {
        return check != null && MOJANG_CERTIFICATES.contains(check.getSignerSha256());
    }

    // Throws if the APK differs from expectedSha256, which can be null
    public static String checkSha256(File apk, String expectedSha256) throws IOException, LauncherException {
        String sha256 = sha256(apk);
        if (expectedSha256 != null && !expectedSha256.trim().isEmpty() && !expectedSha256.trim().equalsIgnoreCase(sha256)) {
            throw new LauncherException("APK checksum does not match the catalog. Expected " + expectedSha256.trim() + ", got " + sha256 + ".");
        }
        return sha256;
    }

    public static void requireGameLibrary(File apk) throws IOException, LauncherException {
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("lib/") && name.endsWith("/" + GamePackage.GAME_LIBRARY)) {
                    return;
                }
            }
        }
        throw new LauncherException("APK has no " + GamePackage.GAME_LIBRARY + ", it is not a Minecraft APK.");
    }

    // PackageManager verifies the signature of the whole APK before it returns the certificates
    public static ApkCheck readSigner(Context context, File apk) throws LauncherException {
        Signature[] signers = readSigners(context.getPackageManager(), apk.getAbsolutePath());
        if (signers == null || signers.length == 0) {
            throw new LauncherException("APK signature is missing or broken, the file may be damaged or changed.");
        }
        byte[] encoded = signers[0].toByteArray();
        ApkCheck check = new ApkCheck();
        check.setSignerSha256(toHex(newSha256().digest(encoded)));
        try {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded));
            check.setSignerName(certificate.getSubjectX500Principal().getName());
        } catch (CertificateException e) {
            throw new LauncherException("APK signing certificate can't be read.", e);
        }
        return check;
    }

    // Android 9 and 10 fill signingInfo of an archive only together with GET_SIGNATURES
    @SuppressWarnings("deprecation")
    private static Signature[] readSigners(PackageManager packageManager, String path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = packageManager.getPackageArchiveInfo(path,
                    PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES);
            if (info == null) {
                return null;
            }
            return info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : info.signatures;
        }
        PackageInfo info = packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES);
        return info != null ? info.signatures : null;
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.US, "%02x", b));
        }
        return builder.toString();
    }
}
