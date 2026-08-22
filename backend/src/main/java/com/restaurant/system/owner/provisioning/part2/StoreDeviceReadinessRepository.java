package com.restaurant.system.owner.provisioning.part2;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreDeviceReadinessRepository extends JpaRepository<StoreDeviceReadinessEntity, Long> {

    @Query("select readiness from StoreDeviceReadinessEntity readiness where readiness.device_id = :deviceId")
    Optional<StoreDeviceReadinessEntity> findByDeviceId(@Param("deviceId") Long deviceId);

    @Query("""
        select readiness from StoreDeviceReadinessEntity readiness
        where readiness.store_id = :storeId order by readiness.device_id asc
        """)
    List<StoreDeviceReadinessEntity> findAllByStoreIdOrderByDeviceIdAsc(@Param("storeId") Long storeId);
}
