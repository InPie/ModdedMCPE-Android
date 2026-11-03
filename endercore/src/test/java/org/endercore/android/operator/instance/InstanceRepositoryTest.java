package org.endercore.android.operator.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.endercore.android.TestContext;
import org.endercore.android.TestFiles;
import org.endercore.android.interf.implemented.FileEnvironment;
import org.endercore.android.operator.instance.model.GameInstance;
import org.endercore.android.operator.instance.model.InstanceSourceType;
import org.endercore.android.operator.instance.model.InstanceState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public class InstanceRepositoryTest {
    private static final String ID = "abc12345-1.0.0";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private InstanceRepository repository;

    @Before
    public void setUp() {
        repository = new InstanceRepository(new FileEnvironment(new TestContext(folder.getRoot())));
    }

    private void damageMetadata(String content) throws Exception {
        TestFiles.write(repository.getInstanceFile(ID), content);
    }

    private File worldFile() {
        return new File(repository.getInstanceDir(ID), "data/games/world.txt");
    }

    @Test
    public void savedInstanceIsReadBackAfterReplacing() throws Exception {
        GameInstance instance = new GameInstance();
        instance.setId(ID);
        instance.setName("First");
        instance.setState(InstanceState.READY);
        repository.saveInstance(instance);
        instance.setName("Мой мир");
        repository.saveInstance(instance);

        GameInstance loaded = repository.getInstance(ID);

        assertEquals("Мой мир", loaded.getName());
        assertEquals(InstanceState.READY, loaded.getState());
        assertEquals(1, repository.getInstanceDir(ID).list().length);
    }

    @Test
    public void damagedInstanceWithApkStaysLaunchable() throws Exception {
        TestFiles.write(worldFile(), "world");
        TestFiles.write(repository.getManagedApkFile(ID), "apk");
        String damaged = "{\"name\": \"My wo";
        damageMetadata(damaged);

        List<GameInstance> instances = repository.getInstances();

        assertEquals(1, instances.size());
        GameInstance recovered = instances.get(0);
        assertEquals(ID, recovered.getId());
        assertEquals(ID, recovered.getName());
        assertEquals(InstanceState.REBUILD_REQUIRED, recovered.getState());
        assertEquals(InstanceSourceType.MANAGED_APK, recovered.getSource().getType());
        assertEquals("world", TestFiles.read(worldFile()));
        assertEquals(damaged, TestFiles.read(new File(repository.getInstanceDir(ID), "instance.json.damaged")));
    }

    @Test
    public void recoveredMetadataIsPersisted() throws Exception {
        TestFiles.write(repository.getManagedApkFile(ID), "apk");
        damageMetadata("not json");
        repository.getInstance(ID);

        String saved = TestFiles.read(repository.getInstanceFile(ID));

        assertTrue(saved, saved.contains(ID));
        assertEquals(InstanceState.REBUILD_REQUIRED, repository.getInstance(ID).getState());
    }

    @Test
    public void damagedInstanceWithoutApkStaysVisible() throws Exception {
        TestFiles.write(worldFile(), "world");
        damageMetadata("not json");

        GameInstance recovered = repository.getInstance(ID);

        assertEquals(InstanceState.MISSING_SOURCE, recovered.getState());
        assertNull(recovered.getSource());
        assertEquals("world", TestFiles.read(worldFile()));
    }

    @Test
    public void emptyMetadataIsRecovered() throws Exception {
        TestFiles.write(repository.getManagedApkFile(ID), "apk");
        damageMetadata("");

        assertEquals(1, repository.getInstances().size());
    }

    @Test
    public void missingFieldsAreFilledIn() throws Exception {
        damageMetadata("{\"state\":\"STATE_FROM_A_NEWER_VERSION\"}");

        GameInstance instance = repository.getInstance(ID);

        assertEquals(ID, instance.getId());
        assertEquals(ID, instance.getName());
        assertEquals(InstanceState.REBUILD_REQUIRED, instance.getState());
    }

    @Test
    public void folderWithoutMetadataIsNotListed() throws Exception {
        TestFiles.write(worldFile(), "world");

        assertTrue(repository.getInstances().isEmpty());
        assertNull(repository.getInstance(ID));
    }
}
