package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItemOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemOptionRepository extends JpaRepository<MenuItemOption, Long> {

    @Query("""
        select o from MenuItemOption o
        where o.menu_item_id in :menuItemIds and o.is_active = true
        order by o.id asc
        """)
    List<MenuItemOption> findActiveByMenuItemIds(@Param("menuItemIds") List<Long> menuItemIds);
}
