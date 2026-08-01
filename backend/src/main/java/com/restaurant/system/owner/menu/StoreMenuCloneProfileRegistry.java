package com.restaurant.system.owner.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StoreMenuCloneProfileRegistry {

    private final Map<String, StoreMenuCloneProfileDescriptor> profilesByCode;

    public StoreMenuCloneProfileRegistry(List<StoreMenuCloneProfileDescriptor> profiles) {
        Map<String, StoreMenuCloneProfileDescriptor> indexed = new HashMap<>();
        for (StoreMenuCloneProfileDescriptor profile : profiles) {
            String code = exactValue(profile == null ? null : profile.profileCode());
            if (code == null
                || profile.sourceStoreId() == null
                || exactValue(profile.profileFingerprint()) == null
                || indexed.put(code, profile) != null) {
                throw new IllegalStateException("Store menu clone profile descriptors must be complete and unique");
            }
        }
        this.profilesByCode = Map.copyOf(indexed);
    }

    public Optional<StoreMenuCloneProfileDescriptor> find(String profileCode) {
        return Optional.ofNullable(profilesByCode.get(profileCode));
    }

    private String exactValue(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            return null;
        }
        return value;
    }
}
