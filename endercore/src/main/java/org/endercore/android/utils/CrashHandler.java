package org.endercore.android.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";

    private static CrashHandler instance;
    private Context context;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Class<?> fatalActivityClass;

    private CrashHandler() {}

    public static synchronized CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context context, Class<?> fatalActivityClass) {
        this.context = context.getApplicationContext();
        this.fatalActivityClass = fatalActivityClass;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        Log.d(TAG, "Java CrashHandler initialized");
    }

    public void initNative() {
        try {
            initNative(context.getPackageName(), fatalActivityClass.getName());
            Log.d(TAG, "Native CrashHandler initialized");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to initialize native crash handler", e);
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        handleException(e);
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    public void handleException(Throwable e) {
        if (e == null) return;

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();

        startFatalActivity(stackTrace);
    }

    public void startFatalActivity(String message) {
        try {
            String processName = getProcessName();
            String fullMessage = "Process: " + processName + "\n\n" + message;
            
            Intent intent = new Intent(context, fatalActivityClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("FATAL_MESSAGES", fullMessage);
            LaunchContext.addToIntent(intent);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start FatalActivity", e);
        }
    }

    private String getProcessName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return android.app.Application.getProcessName();
        }
        return "Unknown Process";
    }

    public static void onNativeCrash(String reason, String stackTrace) {
        Log.e(TAG, "Native crash detected: " + reason);
        getInstance().startFatalActivity("Native Crash: " + reason + "\n\n" + stackTrace);
    }

    private native void initNative(String packageName, String activityName);
}
