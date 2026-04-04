package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    @Query("""
        select c from MenuCategory c
        where c.store_id = :storeId and c.is_active = true
        order by c.sort_order asc, c.id asc
        """)
    List<MenuCategory> findActiveByStoreId(@Param("storeId") Long storeId);
}
