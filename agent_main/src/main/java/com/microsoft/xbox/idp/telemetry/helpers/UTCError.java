package com.microsoft.xbox.idp.telemetry.helpers;

public final class UTCError {
    private UTCError() {
    }

    public static void trackUINeeded(String msaJobName, boolean isSilent, UTCTelemetry.CallBackSources source) {
    }

    public static void trackUserCancel(String msaJobName, boolean isSilent, UTCTelemetry.CallBackSources source) {
    }

    public static void trackMSACancel(String msaJobName, boolean isSilent, UTCTelemetry.CallBackSources source) {
    }

    public static void trackSignedOut(String msaJobName, boolean isSilent, UTCTelemetry.CallBackSources source) {
    }

    public static void trackFailure(String jobName, boolean isSilent, UTCTelemetry.CallBackSources source, Exception exception) {
    }

    public static void trackFailure(String jobName, boolean isSilent, UTCTelemetry.CallBackSources source, long errorCode) {
    }

    public static void trackException(Exception ex, String callingSource) {
    }

    public static void trackServiceFailure(String errorName, String pageName, Object httpError) {
    }
}
