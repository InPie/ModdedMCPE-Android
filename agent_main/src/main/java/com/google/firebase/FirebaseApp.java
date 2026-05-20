package com.google.firebase;

import android.content.Context;

import com.google.firebase.FirebaseAppLifecycleListener;

import java.util.Collections;
import java.util.List;

public class FirebaseApp {
    public static final String DEFAULT_APP_NAME = "[DEFAULT]";

    private static volatile FirebaseApp instance = null;

    private final Context applicationContext;
    private final String name;
    private final FirebaseOptions options;
    private boolean dataCollectionDefaultEnabled = false;

    public interface BackgroundStateChangeListener {
        void onBackgroundStateChanged(boolean background);
    }

    protected FirebaseApp(Context applicationContext, String name, FirebaseOptions options) {
        this.applicationContext = applicationContext;
        this.name = name;
        this.options = options;
    }

    public static synchronized FirebaseApp initializeApp(Context context) {
        if (instance == null) {
            Context appContext = context != null ? context.getApplicationContext() : null;
            instance = new FirebaseApp(appContext, DEFAULT_APP_NAME, null);
        }
        return instance;
    }

    public static synchronized FirebaseApp initializeApp(Context context, FirebaseOptions options) {
        if (instance == null) {
            Context appContext = context != null ? context.getApplicationContext() : null;
            instance = new FirebaseApp(appContext, DEFAULT_APP_NAME, options);
        }
        return instance;
    }

    public static synchronized FirebaseApp initializeApp(
            Context context,
            FirebaseOptions options,
            String name
    ) {
        if (instance == null) {
            Context appContext = context != null ? context.getApplicationContext() : null;
            instance = new FirebaseApp(
                    appContext,
                    name != null ? name : DEFAULT_APP_NAME,
                    options
            );
        }
        return instance;
    }

    public static synchronized FirebaseApp getInstance() {
        if (instance == null) {
            instance = new FirebaseApp(null, DEFAULT_APP_NAME, null);
        }
        return instance;
    }

    public static synchronized FirebaseApp getInstance(String name) {
        FirebaseApp app = getInstance();
        if (name == null || app.name.equals(name)) {
            return app;
        }
        return new FirebaseApp(app.applicationContext, name, app.options);
    }

    public static synchronized List<FirebaseApp> getApps(Context context) {
        return Collections.singletonList(initializeApp(context));
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    public String getName() {
        return name;
    }

    public FirebaseOptions getOptions() {
        return options;
    }

    public void delete() {
    }

    public <T> T get(Class<T> anInterface) {
        return null;
    }

    public void setAutomaticResourceManagementEnabled(boolean enabled) {
    }

    public boolean isDataCollectionDefaultEnabled() {
        return dataCollectionDefaultEnabled;
    }

    public void setDataCollectionDefaultEnabled(Boolean enabled) {
        dataCollectionDefaultEnabled = Boolean.TRUE.equals(enabled);
    }

    public void setDataCollectionDefaultEnabled(boolean enabled) {
        dataCollectionDefaultEnabled = enabled;
    }

    public boolean isDefaultApp() {
        return DEFAULT_APP_NAME.equals(name);
    }

    public void addBackgroundStateChangeListener(BackgroundStateChangeListener listener) {
    }

    public void removeBackgroundStateChangeListener(BackgroundStateChangeListener listener) {
    }

    public String getPersistenceKey() {
        return name;
    }

    public void addLifecycleEventListener(FirebaseAppLifecycleListener listener) {
    }

    public void removeLifecycleEventListener(FirebaseAppLifecycleListener listener) {
    }

    public static synchronized void clearInstancesForTest() {
        instance = null;
    }

    public static String getPersistenceKey(String name, FirebaseOptions options) {
        return name != null ? name : DEFAULT_APP_NAME;
    }
}
