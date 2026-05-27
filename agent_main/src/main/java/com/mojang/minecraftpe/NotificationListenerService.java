package com.mojang.minecraftpe;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

// 1.19+
public class NotificationListenerService extends Service {

    public native void nativePushNotificationReceived(int type, String title, String description, String data);

    public static String getDeviceRegistrationToken() {
        return "";
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
