package com.restaurant.system.owner.provisioning;

public record OwnerStoreProvisioningCounts(
    int stationCount,
    int categoryCount,
    int itemCount,
    int optionCount,
    int pricingPolicyCount,
    int comboComponentCount,
    int printingRuleCount
) {
}
