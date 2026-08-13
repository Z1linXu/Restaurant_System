package com.restaurant.system.modules;

import java.util.Set;

public interface StoreModuleCapabilityProvider {

    Set<String> environmentCapabilities(Long storeId);

    Set<String> hardwareCapabilities(Long storeId);
}
