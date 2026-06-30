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
        order by
            case when o.sort_order is null then 1 else 0 end asc,
            o.sort_order asc,
            o.id asc
        """)
    List<MenuItemOption> findActiveByMenuItemIds(@Param("menuItemIds") List<Long> menuItemIds);

    @Query("""
        select o from MenuItemOption o
        where o.menu_item_id = :menuItemId
        order by
            case when o.sort_order is null then 1 else 0 end asc,
            o.sort_order asc,
            o.id asc
        """)
    List<MenuItemOption> findAllByMenuItemIdOrdered(@Param("menuItemId") Long menuItemId);

    @Query("""
        select o from MenuItemOption o
        where o.menu_item_id in :menuItemIds
        order by
            o.menu_item_id asc,
            case when o.sort_order is null then 1 else 0 end asc,
            o.sort_order asc,
            o.id asc
        """)
    List<MenuItemOption> findAllByMenuItemIdsOrdered(@Param("menuItemIds") List<Long> menuItemIds);
}
