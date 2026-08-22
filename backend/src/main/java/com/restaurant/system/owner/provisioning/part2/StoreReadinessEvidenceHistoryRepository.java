package com.restaurant.system.owner.provisioning.part2;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreReadinessEvidenceHistoryRepository
    extends JpaRepository<StoreReadinessEvidenceHistoryEntity, Long> {
}
