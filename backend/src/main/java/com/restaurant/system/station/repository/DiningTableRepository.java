package com.restaurant.system.station.repository;

import com.restaurant.system.station.entity.DiningTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiningTable d where d.id = :id and d.store_id = :storeId")
    DiningTable findByIdAndStoreIdForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    @Query("""
        select d from DiningTable d where d.store_id = :storeId
          and (d.table_name = :tableName or
            ((d.table_name is null or trim(d.table_name) = '') and d.table_code = :tableName))
        order by d.id
        """)
    List<DiningTable> findAllByStoreIdAndTableName(@Param("storeId") Long storeId, @Param("tableName") String tableName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d from DiningTable d where d.store_id = :storeId
          and (d.table_name = :tableName or
            ((d.table_name is null or trim(d.table_name) = '') and d.table_code = :tableName))
        order by d.id
        """)
    List<DiningTable> findAllByStoreIdAndTableNameForUpdate(@Param("storeId") Long storeId, @Param("tableName") String tableName);

    @Query("select d from DiningTable d where d.store_id = :storeId order by d.sort_order asc, d.id asc")
    List<DiningTable> findAllByStoreIdOrderBySortOrderAscIdAsc(@Param("storeId") Long storeId);

    @Query("select d from DiningTable d where d.store_id = :storeId and d.table_code = :tableCode")
    DiningTable findByStoreIdAndTableCode(@Param("storeId") Long storeId, @Param("tableCode") String tableCode);

    @Query("select d from DiningTable d where d.store_id = :storeId and d.table_code = :tableCode order by d.id asc")
    List<DiningTable> findAllByStoreIdAndTableCode(
        @Param("storeId") Long storeId,
        @Param("tableCode") String tableCode
    );

    @Query("select count(d) from DiningTable d where d.store_id = :storeId and d.is_active = true")
    long countActiveByStoreId(@Param("storeId") Long storeId);
}
