package com.restaurant.system.owner.menu;

import org.springframework.stereotype.Component;

@Component
public final class ChinatownMenuCloneProfile implements StoreMenuCloneProfileDescriptor {

    public static final String PROFILE_CODE = "CHINATOWN_MENU_2026_02_02";
    public static final String CONTRACT_VERSION = "AL003_V1";
    public static final Long SOURCE_STORE_ID = 1L;

    @Override
    public String profileCode() {
        return PROFILE_CODE;
    }

    @Override
    public Long sourceStoreId() {
        return SOURCE_STORE_ID;
    }

    @Override
    public String profileFingerprint() {
        return CONTRACT_VERSION;
    }
}
