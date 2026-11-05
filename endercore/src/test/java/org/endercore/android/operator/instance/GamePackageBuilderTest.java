package org.endercore.android.operator.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.endercore.android.TestContext;
import org.endercore.android.TestFiles;
import org.endercore.android.exception.LauncherException;
import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.interf.implemented.FileEnvironment;
import org.endercore.android.operator.GamePackage;
import org.endercore.android.utils.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GamePackageBuilderTest {
    private static final String ID = "abc12345-1.0.0";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private IFileEnvironment environment;
    private GamePackageBuilder builder;

    public static Map<String, byte[]> apkEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("lib/armeabi-v7a/libminecraftpe.so", "native".getBytes(StandardCharsets.UTF_8));
        entries.put("classes.dex", "dex".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/images/title.png", "png".getBytes(StandardCharsets.UTF_8));
        entries.put("AndroidManifest.xml", "manifest".getBytes(StandardCharsets.UTF_8));
        entries.put("res/raw/other.txt", "other".getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    @Before
    public void setUp() {
        environment = new FileEnvironment(new TestContext(folder.getRoot()));
        builder = new GamePackageBuilder(environment, ID, true);
    }

    private File newApk(String name, Map<String, byte[]> entries) throws Exception {
        File apk = new File(folder.getRoot(), name);
        TestFiles.writeZip(apk, entries);
        return apk;
    }

    private GamePackage gamePackage(int versionCode, File... apks) {
        List<File> files = Arrays.asList(apks);
        return new GamePackage(GamePackage.SourceKind.APK_FILE, "com.mojang.minecraftpe", "1.0.0", versionCode,
                apks[0].getAbsolutePath(), files, "test");
    }

    private File nativeLib() {
        return new File(environment.getCodeCacheDirPathForNativeLib(), "libminecraftpe.so");
    }

    private File assetsPack() {
        return new File(environment.getInstanceCacheDirPath(ID), FileEnvironment.FILE_GAME_ASSETS);
    }

    private static String readEntry(ZipFile zipFile, String name) throws Exception {
        try (InputStream input = zipFile.getInputStream(zipFile.getEntry(name));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            FileUtils.copy(input, output);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Test
    public void successfulBuildIsComplete() throws Exception {
        GamePackage gamePackage = gamePackage(1, newApk("game.apk", apkEntries()));

        builder.build(gamePackage);

        assertTrue(builder.isBuildComplete(gamePackage));
        assertEquals("native", TestFiles.read(nativeLib()));
    }

    @Test
    public void assetsPackKeepsApkLayout() throws Exception {
        builder.build(gamePackage(1, newApk("game.apk", apkEntries())));

        try (ZipFile pack = new ZipFile(assetsPack())) {
            assertEquals(2, pack.size());
            assertEquals("png", readEntry(pack, "assets/images/title.png"));
            assertEquals("manifest", readEntry(pack, "AndroidManifest.xml"));
            assertEquals(ZipEntry.STORED, pack.getEntry("assets/images/title.png").getMethod());
        }
    }

    @Test
    public void assetsPackTakesFirstOfDuplicateEntries() throws Exception {
        Map<String, byte[]> splitEntries = new LinkedHashMap<>();
        splitEntries.put("assets/images/title.png", "split".getBytes(StandardCharsets.UTF_8));

        builder.build(gamePackage(1, newApk("game.apk", apkEntries()), newApk("split.apk", splitEntries)));

        try (ZipFile pack = new ZipFile(assetsPack())) {
            assertEquals("png", readEntry(pack, "assets/images/title.png"));
        }
    }

    @Test
    public void assetsAreNotPackedWhenDisabled() throws Exception {
        GamePackageBuilder withoutPack = new GamePackageBuilder(environment, ID, false);
        GamePackage gamePackage = gamePackage(1, newApk("game.apk", apkEntries()));

        withoutPack.build(gamePackage);

        assertTrue(withoutPack.isBuildComplete(gamePackage));
        assertFalse(assetsPack().exists());
    }

    @Test
    public void changingAssetsPackSettingRequiresRebuild() throws Exception {
        GamePackage gamePackage = gamePackage(1, newApk("game.apk", apkEntries()));
        builder.build(gamePackage);

        assertFalse(new GamePackageBuilder(environment, ID, false).isBuildComplete(gamePackage));
    }

    @Test
    public void buildIsIncompleteBeforeBuilding() throws Exception {
        assertFalse(builder.isBuildComplete(gamePackage(1, newApk("game.apk", apkEntries()))));
    }

    @Test
    public void replacedPackageRequiresRebuild() throws Exception {
        File apk = newApk("game.apk", apkEntries());
        builder.build(gamePackage(1, apk));

        assertFalse(builder.isBuildComplete(gamePackage(2, apk)));

        Map<String, byte[]> biggerEntries = apkEntries();
        biggerEntries.put("assets/new.png", new byte[1024]);
        assertFalse(builder.isBuildComplete(gamePackage(1, newApk("game.apk", biggerEntries))));
    }

    @Test
    public void damagedSplitFailsBuildAndClearsCache() throws Exception {
        File split = new File(folder.getRoot(), "split.apk");
        TestFiles.write(split, "this is not a zip file");
        GamePackage gamePackage = gamePackage(1, newApk("game.apk", apkEntries()), split);

        assertThrows(LauncherException.class, () -> builder.build(gamePackage));

        assertFalse(builder.isBuildComplete(gamePackage));
        assertFalse(nativeLib().exists());
    }

    @Test
    public void failedRebuildInvalidatesPreviousBuild() throws Exception {
        File apk = newApk("game.apk", apkEntries());
        GamePackage gamePackage = gamePackage(1, apk);
        builder.build(gamePackage);

        Map<String, byte[]> withoutLib = apkEntries();
        withoutLib.remove("lib/armeabi-v7a/libminecraftpe.so");
        withoutLib.put("lib/armeabi-v7a/libother.so", new byte[1]);
        TestFiles.writeZip(apk, withoutLib);

        assertThrows(LauncherException.class, () -> builder.build(gamePackage));

        assertFalse(builder.isBuildComplete(gamePackage));
        assertFalse(new File(environment.getInstanceCacheDirPath(ID)).exists());
    }

    @Test
    public void missingDexFailsBuild() throws Exception {
        Map<String, byte[]> withoutDex = apkEntries();
        withoutDex.remove("classes.dex");
        GamePackage gamePackage = gamePackage(1, newApk("game.apk", withoutDex));

        assertThrows(LauncherException.class, () -> builder.build(gamePackage));
    }

    @Test
    public void rebuildRemovesEverythingFromOldCache() throws Exception {
        File staleDir = new File(environment.getInstanceCacheDirPath(ID), FileEnvironment.DIR_ASSETS);
        TestFiles.write(new File(staleDir, "images/title.png"), "stale");

        builder.build(gamePackage(1, newApk("game.apk", apkEntries())));

        assertFalse(staleDir.exists());
    }

    @Test
    public void assetsStayInBuiltInstanceWhenWorkspaceChangesDuringBuild() throws Exception {
        File apk = newApk("game.apk", apkEntries());
        GamePackage gamePackage = new GamePackage(GamePackage.SourceKind.APK_FILE, "com.mojang.minecraftpe", "1.0.0", 1,
                apk.getAbsolutePath(), Collections.singletonList(apk), "test") {
            @Override
            public boolean isVersion015AndAbove() {
                environment.setActiveWorkspace("other");
                return true;
            }
        };

        builder.build(gamePackage);

        assertTrue(assetsPack().isFile());
        assertFalse(new File(environment.getInstanceCacheDirPath("other")).exists());
    }
}
