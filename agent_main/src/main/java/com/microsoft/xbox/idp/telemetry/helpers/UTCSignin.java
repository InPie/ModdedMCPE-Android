package com.microsoft.xbox.idp.telemetry.helpers;

public final class UTCSignin {
    private static CharSequence activityTitle;

    private UTCSignin() {
    }

    public static CharSequence getCurrentActivity() {
        return activityTitle;
    }

    public static void setCurrentActivity(CharSequence newActivityTitle) {
        activityTitle = newActivityTitle;
    }

    public static void trackXBLSigninStart(String cid, CharSequence activityTitle) {
        setCurrentActivity(activityTitle);
    }

    public static void trackXBLSigninSuccess(String cid, CharSequence activityTitle, boolean createAccount) {
        setCurrentActivity(activityTitle);
    }

    public static void trackMSASigninStart(String cid, boolean isSilent, CharSequence activityTitle) {
        setCurrentActivity(activityTitle);
    }

    public static void trackMSASigninSuccess(String cid, boolean isSilent, CharSequence activityTitle) {
        setCurrentActivity(activityTitle);
    }

    public static void trackSignin(String cid, boolean isSilent, CharSequence activityTitle) {
        setCurrentActivity(activityTitle);
    }

    public static void trackAccountAcquired(String job, String cid, boolean isSilent) {
    }

    public static void trackTicketAcquired(String job, String cid, boolean isSilent) {
    }

    public static void trackPageView(CharSequence activityTitle) {
        setCurrentActivity(activityTitle);
    }
}
