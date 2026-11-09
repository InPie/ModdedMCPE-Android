package org.endercore.android.operator.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.endercore.android.TestContext;
import org.endercore.android.TestFiles;
import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.interf.implemented.FileEnvironment;
import org.endercore.android.operator.ApkVerifier;
import org.endercore.android.operator.GamePackage;
import org.endercore.android.operator.instance.model.ApkCheck;
import org.endercore.android.operator.instance.model.GameInstance;
import org.endercore.android.operator.instance.model.InstanceSource;
import org.endercore.android.operator.instance.model.InstanceSourceType;
import org.endercore.android.operator.instance.model.InstanceState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InstanceOperatorTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private TestContext context;
    private IFileEnvironment environment;
    private InstanceOperator operator;

    // PackageManager isn't available in unit tests
    private static final class TestOperator extends InstanceOperator {
        TestOperator(TestContext context, IFileEnvironment environment) {
            super(context, environment);
        }

        @Override
        GamePackage readGamePackage(File apkFile) {
            return new GamePackage(GamePackage.SourceKind.APK_FILE, "com.mojang.minecraftpe", "1.0.0", 1,
                    apkFile.getAbsolutePath(), Collections.singletonList(apkFile), "test");
        }

        @Override
        ApkCheck readSigner(File apkFile) {
            ApkCheck check = new ApkCheck();
            check.setSignerSha256(SIGNER_SHA256);
            check.setSignerName("CN=Test");
            return check;
        }
    }

    private static final String SIGNER_SHA256 = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String MOJANG_SHA256 = "31be40096f931cd7f11d5e262d2b2c437c44385fb4ecbc1013d95a7435816f9c";

    private static final class WaitingCallback implements InstanceOperator.InstallCallback {
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile Exception error;

        @Override
        public void onProgress(int percent) {
        }

        private volatile GameInstance instance;

        @Override
        public void onSuccess(GameInstance instance) {
            this.instance = instance;
            finished.countDown();
        }

        @Override
        public void onError(Exception e) {
            error = e;
            finished.countDown();
        }

        void await() throws InterruptedException {
            assertTrue("Operation did not finish", finished.await(30, TimeUnit.SECONDS));
        }
    }

    @Before
    public void setUp() {
        context = new TestContext(folder.getRoot());
        environment = new FileEnvironment(context);
        operator = new TestOperator(context, environment);
        InstanceOperator.activeOperations.clear();
    }

    @After
    public void tearDown() {
        InstanceOperator.activeOperations.clear();
    }

    private GameInstance saveInstance(String id, InstanceState state, InstanceSourceType sourceType) throws Exception {
        GameInstance instance = new GameInstance();
        instance.setId(id);
        instance.setName(id);
        instance.setState(state);
        instance.setSettings(new JsonObject());
        instance.setSource(new InstanceSource(sourceType));
        operator.getRepository().saveInstance(instance);
        return instance;
    }

    private InstanceState stateOf(String id) {
        return operator.getRepository().getInstance(id).getState();
    }

    private File goodApk() throws Exception {
        File apk = new File(folder.getRoot(), "source/game.apk");
        TestFiles.writeZip(apk, GamePackageBuilderTest.apkEntries());
        return apk;
    }

    private File brokenApk() throws Exception {
        File apk = new File(folder.getRoot(), "source/broken.apk");
        TestFiles.writeZip(apk, Collections.singletonMap("readme.txt", new byte[1]));
        return apk;
    }

    private File instanceZip(File apk) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("instance.json", ("{\"id\":\"old-id\",\"name\":\"My world\",\"state\":\"READY\","
                + "\"apkCheck\":{\"signerSha256\":\"" + MOJANG_SHA256 + "\"}}").getBytes(StandardCharsets.UTF_8));
        entries.put("apk/game.apk", Files.readAllBytes(apk.toPath()));
        entries.put("data/games/world.txt", "world".getBytes(StandardCharsets.UTF_8));
        File zip = new File(folder.getRoot(), "source/instance.zip");
        TestFiles.writeZip(zip, entries);
        return zip;
    }

    private void assertNoImportLeftovers() {
        assertTrue(operator.getRepository().getInstances().isEmpty());
        assertEquals(0, new File(environment.getInstancesDirPath()).list().length);
        assertEquals(0, context.getCacheDir().list().length);
        assertTrue(InstanceOperator.activeOperations.isEmpty());
    }

    @Test
    public void interruptedDownloadCanBeRetried() throws Exception {
        saveInstance("download", InstanceState.DOWNLOADING, InstanceSourceType.REMOTE_APK);

        operator.recoverInterruptedInstances();

        assertEquals(InstanceState.DOWNLOAD_FAILED, stateOf("download"));
    }

    @Test
    public void interruptedPreparationCanBeLaunched() throws Exception {
        GameInstance instance = saveInstance("prepare", InstanceState.PREPARING, InstanceSourceType.MANAGED_APK);
        assertFalse(operator.canLaunch(instance));

        operator.recoverInterruptedInstances();

        assertEquals(InstanceState.PREPARE_FAILED, stateOf("prepare"));
        assertTrue(operator.canLaunch(operator.getRepository().getInstance("prepare")));
    }

    @Test
    public void runningOperationsAreNotTouched() throws Exception {
        saveInstance("download", InstanceState.DOWNLOADING, InstanceSourceType.REMOTE_APK);
        saveInstance("prepare", InstanceState.PREPARING, InstanceSourceType.MANAGED_APK);
        InstanceOperator.activeOperations.add("download");
        InstanceOperator.activeOperations.add("prepare");

        operator.recoverInterruptedInstances();

        assertEquals(InstanceState.DOWNLOADING, stateOf("download"));
        assertEquals(InstanceState.PREPARING, stateOf("prepare"));
    }

    @Test
    public void otherStatesAreNotTouched() throws Exception {
        saveInstance("ready", InstanceState.READY, InstanceSourceType.MANAGED_APK);
        saveInstance("rebuild", InstanceState.REBUILD_REQUIRED, InstanceSourceType.MANAGED_APK);

        operator.recoverInterruptedInstances();

        assertEquals(InstanceState.READY, stateOf("ready"));
        assertEquals(InstanceState.REBUILD_REQUIRED, stateOf("rebuild"));
    }

    @Test
    public void importedApkBecomesReady() throws Exception {
        WaitingCallback callback = new WaitingCallback();

        operator.importLocalApk(goodApk(), true, callback);
        callback.await();

        assertNull(callback.error);
        List<GameInstance> instances = operator.getRepository().getInstances();
        assertEquals(1, instances.size());
        GameInstance instance = instances.get(0);
        assertEquals(InstanceState.READY, instance.getState());
        assertTrue(operator.getManagedApkFile(instance.getId()).isFile());
        assertTrue(operator.isInstancePrepared(instance, operator.readGamePackage(operator.getManagedApkFile(instance.getId()))));
        assertTrue(InstanceOperator.activeOperations.isEmpty());
    }

    @Test
    public void failedApkImportLeavesNothingBehind() throws Exception {
        WaitingCallback callback = new WaitingCallback();

        operator.importLocalApk(brokenApk(), true, callback);
        callback.await();

        assertNotNull(callback.error);
        assertNoImportLeftovers();
    }

    @Test
    public void importedZipKeepsWorldsAndGetsNewIdentity() throws Exception {
        WaitingCallback callback = new WaitingCallback();

        operator.importInstanceZip(instanceZip(goodApk()), callback);
        callback.await();

        assertNull(callback.error);
        List<GameInstance> instances = operator.getRepository().getInstances();
        assertEquals(1, instances.size());
        GameInstance instance = instances.get(0);
        assertEquals("My world", instance.getName());
        assertEquals(InstanceState.READY, instance.getState());
        assertNotEquals("old-id", instance.getId());
        assertEquals(instance.getId(), operator.getRepository().getInstanceDir(instance.getId()).getName());
        assertEquals("world", TestFiles.read(new File(operator.getRepository().getInstanceDir(instance.getId()), "data/games/world.txt")));
        assertEquals(0, context.getCacheDir().list().length);
        assertTrue(InstanceOperator.activeOperations.isEmpty());
    }

    @Test
    public void failedZipImportLeavesNothingBehind() throws Exception {
        WaitingCallback callback = new WaitingCallback();

        operator.importInstanceZip(instanceZip(brokenApk()), callback);
        callback.await();

        assertNotNull(callback.error);
        assertNoImportLeftovers();
    }

    @Test
    public void importStoresApkCheck() throws Exception {
        File apk = goodApk();
        WaitingCallback callback = new WaitingCallback();

        operator.importLocalApk(apk, true, callback);
        callback.await();

        ApkCheck check = operator.getRepository().getInstances().get(0).getApkCheck();
        assertEquals(ApkVerifier.checkSha256(apk, null), check.getApkSha256());
        assertEquals(SIGNER_SHA256, check.getSignerSha256());
        assertEquals(SIGNER_SHA256, callback.instance.getApkCheck().getSignerSha256());
    }

    @Test
    public void zipImportReplacesApkCheckFromZip() throws Exception {
        WaitingCallback callback = new WaitingCallback();

        operator.importInstanceZip(instanceZip(goodApk()), callback);
        callback.await();

        GameInstance instance = operator.getRepository().getInstances().get(0);
        assertEquals(SIGNER_SHA256, instance.getApkCheck().getSignerSha256());
        assertFalse(ApkVerifier.isSignedByMojang(instance.getApkCheck()));
    }

    private GameInstance downloadedInstance() throws Exception {
        GameInstance instance = saveInstance("download", InstanceState.DOWNLOADING, InstanceSourceType.REMOTE_APK);
        TestFiles.writeZip(operator.getManagedApkFile(instance.getId()), GamePackageBuilderTest.apkEntries());
        return instance;
    }

    @Test
    public void downloadMatchingCatalogBecomesReady() throws Exception {
        GameInstance instance = downloadedInstance();
        File apk = operator.getManagedApkFile(instance.getId());
        instance.getSource().setSha256(ApkVerifier.checkSha256(apk, null).toUpperCase());
        WaitingCallback callback = new WaitingCallback();

        operator.finishDownload(instance, apk, callback);
        callback.await();

        assertNull(callback.error);
        assertEquals(InstanceState.READY, stateOf("download"));
        assertEquals(SIGNER_SHA256, operator.getRepository().getInstance("download").getApkCheck().getSignerSha256());
    }

    @Test
    public void downloadDifferingFromCatalogCanBeRetried() throws Exception {
        GameInstance instance = downloadedInstance();
        instance.getSource().setSha256("0123");
        File apk = operator.getManagedApkFile(instance.getId());
        WaitingCallback callback = new WaitingCallback();

        operator.finishDownload(instance, apk, callback);
        callback.await();

        assertNotNull(callback.error);
        assertFalse(apk.exists());
        GameInstance saved = operator.getRepository().getInstance("download");
        assertEquals(InstanceState.DOWNLOAD_FAILED, saved.getState());
        assertEquals(InstanceSourceType.REMOTE_APK, saved.getSource().getType());
        assertNull(saved.getApkCheck());
    }

    @Test
    public void instanceWithoutBuildMarkerIsNotPrepared() throws Exception {
        WaitingCallback callback = new WaitingCallback();
        operator.importLocalApk(goodApk(), true, callback);
        callback.await();
        GameInstance instance = operator.getRepository().getInstances().get(0);
        GamePackage gamePackage = operator.readGamePackage(operator.getManagedApkFile(instance.getId()));

        assertTrue(new File(environment.getInstanceCacheDirPath(instance.getId()), "build_complete").delete());

        assertFalse(operator.isInstancePrepared(instance, gamePackage));
    }
}
