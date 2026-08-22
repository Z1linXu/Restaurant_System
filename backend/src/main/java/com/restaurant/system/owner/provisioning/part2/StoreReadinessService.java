package com.restaurant.system.owner.provisioning.part2;

public interface StoreReadinessService {

    StoreReadinessResponse evaluate(Long organizationId, Long storeId);
}
