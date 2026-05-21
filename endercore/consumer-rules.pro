-keepattributes Signature

-keep class com.google.gson.reflect.TypeToken {
    *;
}

-keep class * extends com.google.gson.reflect.TypeToken {
    *;
}

-keep class org.endercore.android.operator.instance.model.** {
    *;
}

-keep class org.endercore.android.operator.OptionsManager$OptionsJsonBean {
    *;
}

-keep class org.endercore.android.operator.OptionsManager$NModOptionsElement {
    *;
}

-keep class org.endercore.android.utils.NModJsonBean$* {
    *;
}

-keep class org.endercore.android.utils.CrashHandler {
    # public static org.endercore.android.utils.CrashHandler getInstance();
    # public void initNative();
    # public static void onNativeCrash(java.lang.String, java.lang.String);
    # private native boolean initNative(java.lang.String, java.lang.String);
    *;
}

-keep class org.endercore.android.mod.script.ScriptController {
    # public static native void executeCustomFunction(android.app.Activity);
    *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}
