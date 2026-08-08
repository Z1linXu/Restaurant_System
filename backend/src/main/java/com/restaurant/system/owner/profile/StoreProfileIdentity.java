package com.restaurant.system.owner.profile;

public record StoreProfileIdentity(String profileCode, String profileVersion) {

    private static final String EXACT_VALUE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]*";

    public StoreProfileIdentity {
        if (!isExact(profileCode) || !isExact(profileVersion)) {
            throw new IllegalArgumentException("Store profile identity must be exact and non-blank");
        }
    }

    public static boolean isExact(String value) {
        return value != null && value.matches(EXACT_VALUE_PATTERN);
    }
}
