package com.restaurant.system.printing;

import java.util.List;
import java.util.Set;

public final class PrintModuleCode {

    public static final String GRAB = "GRAB";
    public static final String FRONTDESK_RECEIPT = "FRONTDESK_RECEIPT";
    public static final String HOT_KITCHEN = "HOT_KITCHEN";
    public static final String COLD_KITCHEN = "COLD_KITCHEN";
    public static final String BAR = "BAR";
    public static final String TAKEOUT_RECEIPT = "TAKEOUT_RECEIPT";

    public static final List<String> ALL = List.of(
        GRAB,
        FRONTDESK_RECEIPT,
        HOT_KITCHEN,
        COLD_KITCHEN,
        BAR,
        TAKEOUT_RECEIPT
    );

    public static final Set<String> PHASE_ONE_ENABLED = Set.of(
        GRAB,
        FRONTDESK_RECEIPT
    );

    private PrintModuleCode() {
    }
}
