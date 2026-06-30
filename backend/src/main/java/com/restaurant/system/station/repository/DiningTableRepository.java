package com.restaurant.system.station.repository;

import com.restaurant.system.station.entity.DiningTable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    @Query("select d from DiningTable d where d.store_id = :storeId order by d.sort_order asc, d.id asc")
    List<DiningTable> findAllByStoreIdOrderBySortOrderAscIdAsc(@Param("storeId") Long storeId);

    @Query("select d from DiningTable d where d.store_id = :storeId and d.table_code = :tableCode")
    DiningTable findByStoreIdAndTableCode(@Param("storeId") Long storeId, @Param("tableCode") String tableCode);

    @Query("select count(d) from DiningTable d where d.store_id = :storeId and d.is_active = true")
    long countActiveByStoreId(@Param("storeId") Long storeId);
}
