package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("""
        select i from MenuItem i
        where i.store_id = :storeId and i.is_active = true
        order by i.category_id asc, i.id asc
        """)
    List<MenuItem> findActiveByStoreId(@Param("storeId") Long storeId);
}
