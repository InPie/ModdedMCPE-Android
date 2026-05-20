package com.microsoft.xbox.idp.telemetry.helpers;

import com.microsoft.xbox.idp.telemetry.utc.model.UTCAdditionalInfoModel;

public final class UTCPageAction {
    private UTCPageAction() {
    }

    public static void track(String actionName, CharSequence activityTitle) {
    }

    public static void track(String actionName, CharSequence activityTitle, UTCAdditionalInfoModel model) {
    }

    public static void track(String actionName, String onPageName, CharSequence activityTitle, UTCAdditionalInfoModel additionalInfo) {
    }
}
