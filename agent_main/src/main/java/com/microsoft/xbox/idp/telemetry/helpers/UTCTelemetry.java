package com.microsoft.xbox.idp.telemetry.helpers;

public final class UTCTelemetry {
    public static final String UNKNOWNPAGE = "Unknown";

    private UTCTelemetry() {
    }

    public enum CallBackSources {
        Account,
        Ticket
    }
}
