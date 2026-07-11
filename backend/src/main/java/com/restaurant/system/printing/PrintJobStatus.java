package com.restaurant.system.printing;

public final class PrintJobStatus {

    public static final String PENDING = "PENDING";
    public static final String CLAIMED = "CLAIMED";
    public static final String PRINTING = "PRINTING";
    public static final String PRINTED = "PRINTED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    private PrintJobStatus() {
    }
}
