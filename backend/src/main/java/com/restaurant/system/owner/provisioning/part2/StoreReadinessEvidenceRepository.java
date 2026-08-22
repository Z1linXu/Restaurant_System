package com.restaurant.system.owner.provisioning.part2;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreReadinessEvidenceRepository extends JpaRepository<StoreReadinessEvidenceEntity, Long> {

    @Query("select evidence from StoreReadinessEvidenceEntity evidence where evidence.store_id = :storeId")
    Optional<StoreReadinessEvidenceEntity> findByStoreId(@Param("storeId") Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evidence from StoreReadinessEvidenceEntity evidence where evidence.store_id = :storeId")
    Optional<StoreReadinessEvidenceEntity> findByStoreIdForUpdate(@Param("storeId") Long storeId);
}
