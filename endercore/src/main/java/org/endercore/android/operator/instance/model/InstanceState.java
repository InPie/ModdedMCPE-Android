package org.endercore.android.operator.instance.model;

public enum InstanceState {
    READY,
    DOWNLOADING,
    DOWNLOAD_FAILED,
    PREPARING,
    PREPARE_FAILED,
    MISSING_SOURCE,
    REBUILD_REQUIRED
}
