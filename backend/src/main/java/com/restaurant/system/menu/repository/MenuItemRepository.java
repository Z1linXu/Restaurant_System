package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItem;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("select i.store_id from MenuItem i where i.id = :itemId")
    java.util.Optional<Long> findStoreIdById(@Param("itemId") Long itemId);

    @Modifying
    @Query("""
        update MenuItem i set i.default_combo_egg_component_code = :code, i.updated_at = :updatedAt
        where i.id = :itemId and i.store_id = :storeId
        """)
    int updateItemComboEggDefault(
        @Param("itemId") Long itemId,
        @Param("storeId") Long storeId,
        @Param("code") String code,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    @Query("select i from MenuItem i where i.store_id = :storeId order by i.id asc")
    List<MenuItem> findAllByStoreIdOrderByIdAsc(@Param("storeId") Long storeId);

    @Query("select count(i) from MenuItem i where i.store_id = :storeId")
    long countAllByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select count(i) from MenuItem i
        where i.store_id = :storeId and i.category_id = :categoryId
        """)
    long countByStoreIdAndCategoryId(
        @Param("storeId") Long storeId,
        @Param("categoryId") Long categoryId
    );

    @Query("""
        select count(i) from MenuItem i
        where i.store_id = :storeId and i.category_id = :categoryId and i.is_active = true
        """)
    long countActiveByStoreIdAndCategoryId(
        @Param("storeId") Long storeId,
        @Param("categoryId") Long categoryId
    );

    @Query("""
        select count(i) from MenuItem i
        where i.store_id = :storeId and i.station_id = :stationId
        """)
    long countByStoreIdAndStationId(
        @Param("storeId") Long storeId,
        @Param("stationId") Long stationId
    );

    @Query("""
        select count(i) from MenuItem i
        where i.store_id = :storeId and i.station_id = :stationId and i.is_active = true
        """)
    long countActiveByStoreIdAndStationId(
        @Param("storeId") Long storeId,
        @Param("stationId") Long stationId
    );

    @Query("""
        select i from MenuItem i
        where i.store_id = :storeId and i.is_active = true
        order by i.category_id asc, i.sort_order asc, i.id asc
        """)
    List<MenuItem> findActiveByStoreId(@Param("storeId") Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i from MenuItem i
        where i.store_id = :storeId and i.category_id = :categoryId
        order by i.sort_order asc, i.id asc
        """)
    List<MenuItem> findAllByStoreIdAndCategoryIdForUpdate(
        @Param("storeId") Long storeId,
        @Param("categoryId") Long categoryId
    );

    @Query("""
        select coalesce(max(i.sort_order), 0) from MenuItem i
        where i.store_id = :storeId and i.category_id = :categoryId
        """)
    Integer findMaxSortOrder(
        @Param("storeId") Long storeId,
        @Param("categoryId") Long categoryId
    );
}
