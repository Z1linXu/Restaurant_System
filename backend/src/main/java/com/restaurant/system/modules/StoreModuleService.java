package com.restaurant.system.modules;

import com.restaurant.system.modules.dto.StoreModuleConfigurationResponse;
import com.restaurant.system.modules.dto.StoreModuleUpdateRequest;

public interface StoreModuleService {

    StoreModuleConfigurationResponse getConfiguration(Long storeId);

    StoreModuleConfigurationResponse updateConfiguration(Long storeId, StoreModuleUpdateRequest request);
}
