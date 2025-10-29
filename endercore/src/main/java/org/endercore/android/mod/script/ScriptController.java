package org.endercore.android.mod.script;

import android.app.Activity;

public class ScriptController {
    static {
        System.loadLibrary("mjscript");
    }

    public static native void executeCustomFunction(Activity activity);
    public static native void shiftPlayer(boolean state);
}
