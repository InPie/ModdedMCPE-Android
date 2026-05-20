package com.microsoft.xbox.idp.telemetry.helpers;

import com.microsoft.xbox.idp.telemetry.utc.model.UTCAdditionalInfoModel;

public final class UTCPageView {
    private UTCPageView() {
    }

    public static int getSize() {
        return 0;
    }

    public static String getCurrentPage() {
        return UTCTelemetry.UNKNOWNPAGE;
    }

    public static String getPreviousPage() {
        return UTCTelemetry.UNKNOWNPAGE;
    }

    public static void addPage(String newPage) {
    }

    public static void removePage() {
    }

    public static void track(String toPage, CharSequence activityTitle) {
    }

    public static void track(String toPage, CharSequence activityTitle, UTCAdditionalInfoModel additionalInfo) {
    }
}
