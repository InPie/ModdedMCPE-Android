package org.endercore.android.mod.script;

public class ScriptController {
    static {
        System.loadLibrary("mjscript");
    }

    public static native void executeCustomFunction();
}
