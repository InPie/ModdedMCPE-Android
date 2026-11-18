package org.endercore.android.utils;

import android.content.Intent;

public final class LaunchContext {
    public static final String EXTRA_INSTANCE_ID = "org.endercore.android.extra.INSTANCE_ID";
    public static final String EXTRA_RESOLUTION_SCALE = "org.endercore.android.extra.RESOLUTION_SCALE";
    // Answers of the launcher's dialogs for 0.1 - 0.6
    public static final String EXTRA_LEGACY_INPUT_VALUES = "me.effently.moddedmcpe.extra.LEGACY_INPUT_VALUES";

    private static String activeInstanceId;

    private LaunchContext() {
    }

    public static synchronized void setActiveInstanceId(String instanceId) {
        activeInstanceId = instanceId;
    }

    public static synchronized void addToIntent(Intent intent) {
        if (activeInstanceId != null && !activeInstanceId.trim().isEmpty()) {
            intent.putExtra(EXTRA_INSTANCE_ID, activeInstanceId);
        }
    }

    public static String getInstanceId(Intent intent) {
        return intent != null ? intent.getStringExtra(EXTRA_INSTANCE_ID) : null;
    }
}
