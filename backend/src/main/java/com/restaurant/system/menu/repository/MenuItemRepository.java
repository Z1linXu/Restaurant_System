package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItem;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

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
