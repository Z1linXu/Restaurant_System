package com.restaurant.system.menu.service;

import com.restaurant.system.menu.dto.MenuItemComboPolicyRequest;
import com.restaurant.system.menu.dto.MenuItemOptionAdminResponse;
import com.restaurant.system.menu.dto.MenuItemSizeConfigurationRequest;
import com.restaurant.system.menu.dto.StorePricingPolicyPreviewRequest;
import com.restaurant.system.menu.dto.StorePricingPolicyPreviewResponse;
import com.restaurant.system.menu.dto.StorePricingPolicyResponse;
import com.restaurant.system.menu.dto.StorePricingPolicyUpdateRequest;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.pricing.StorePricingPolicy;
import java.math.BigDecimal;
import java.util.List;

public interface StorePricingPolicyService {

    BigDecimal DEFAULT_COMBO_DELTA = new BigDecimal("5.00");

    StorePricingPolicy getEffectivePolicy(Long storeId);

    StorePricingPolicyResponse getPolicyResponse(Long storeId);

    StorePricingPolicyPreviewResponse preview(Long storeId, StorePricingPolicyPreviewRequest request);

    StorePricingPolicyResponse updatePolicy(Long storeId, StorePricingPolicyUpdateRequest request);

    List<MenuItemOptionAdminResponse> updateSizeConfiguration(Long itemId, MenuItemSizeConfigurationRequest request);

    List<MenuItemOptionAdminResponse> updateComboPolicy(Long itemId, MenuItemComboPolicyRequest request);

    MenuItemOption applyEffectiveCatalogPricing(MenuItemOption option, StorePricingPolicy policy);
}
