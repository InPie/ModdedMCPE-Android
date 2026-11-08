package org.endercore.android;

import java.io.File;
import java.lang.reflect.Constructor;

public final class TestCore {
    private TestCore() {
    }

    // A separate EnderCore with its files under root
    public static EnderCore start(File root) throws Exception {
        Constructor<EnderCore> constructor = EnderCore.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        EnderCore core = constructor.newInstance();
        core.initialize(new TestContext(root), EnderCore.MODE_PUBLIC);
        return core;
    }
}
