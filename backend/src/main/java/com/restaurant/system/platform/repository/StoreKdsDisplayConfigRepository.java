package com.restaurant.system.platform.repository;

import com.restaurant.system.platform.entity.StoreKdsDisplayConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreKdsDisplayConfigRepository extends JpaRepository<StoreKdsDisplayConfig, Long> {
    @Query("select c from StoreKdsDisplayConfig c where c.store_id = :storeId order by c.id asc")
    List<StoreKdsDisplayConfig> findAllByStoreIdOrderByIdAsc(@Param("storeId") Long storeId);

    @Query("select c from StoreKdsDisplayConfig c where c.store_id = :storeId and c.screen_code = :screenCode")
    StoreKdsDisplayConfig findByStoreIdAndScreenCode(@Param("storeId") Long storeId, @Param("screenCode") String screenCode);
}
