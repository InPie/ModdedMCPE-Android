-keep class com.mojang.minecraftpe.AgentMainActivity {
    # public android.content.res.AssetManager getPatchAssetManager();
    # public java.lang.String getPatchInternalDataPath();
    # public java.lang.String getPatchExternalDataPath();
    # public boolean hasXboxSupport();
    *;
}

-keep class com.mojang.minecraftpe.GameContextBridge {
    *;
}

-keep class com.mojang.minecraftpe.store.** {
    *;
}

-keep class com.microsoft.onlineid.internal.PackageInfoHelper {
    *;
}

-keep class com.microsoft.onlineid.internal.Resources {
    *;
}

-keep class com.microsoft.xbox.idp.telemetry.helpers.** {
    *;
}

-keep class com.microsoft.xbox.idp.telemetry.utc.model.UTCAdditionalInfoModel {
    *;
}
