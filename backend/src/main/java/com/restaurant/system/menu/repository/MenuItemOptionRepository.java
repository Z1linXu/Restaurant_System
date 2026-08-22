package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItemOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemOptionRepository extends JpaRepository<MenuItemOption, Long> {

    @Query("""
        select o from MenuItemOption o join MenuItem i on i.id = o.menu_item_id
        where i.store_id = :storeId
        order by o.id asc
        """)
    List<MenuItemOption> findAllByStoreIdOrderByIdAsc(@Param("storeId") Long storeId);

    @Query("""
        select o from MenuItemOption o join MenuItem i on i.id = o.menu_item_id
        where i.store_id = :storeId and o.menu_item_id in :menuItemIds
        order by
            o.menu_item_id asc,
            case when o.sort_order is null then 1 else 0 end asc,
            o.sort_order asc,
            o.id asc
        """)
    List<MenuItemOption> findAllByStoreIdAndMenuItemIdsOrdered(
        @Param("storeId") Long storeId,
        @Param("menuItemIds") List<Long> menuItemIds
    );

    @Query("""
        select o from MenuItemOption o join MenuItem i on i.id = o.menu_item_id
        where i.store_id = :storeId and o.is_active = true
        order by
            o.menu_item_id asc,
            case when o.sort_order is null then 1 else 0 end asc,
            o.sort_order asc,
            o.id asc
        """)
    List<MenuItemOption> findActiveByStoreIdOrdered(@Param("storeId") Long storeId);

    @Query("""
        select case when count(o) > 0 then true else false end
        from MenuItemOption o join MenuItem i on i.id = o.menu_item_id
        where i.store_id = :storeId
          and o.is_active = true
          and (
            upper(coalesce(o.option_group, '')) = 'COMBO'
            or lower(coalesce(o.option_code, '')) = 'combo'
            or (
                lower(coalesce(o.option_type, '')) = 'addon'
                and (o.name_zh = '套餐' or lower(coalesce(o.name_en, '')) = 'combo')
            )
          )
        """)
    boolean existsActiveComboAllowedByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select o from MenuItemOption o join MenuItem i on i.id = o.menu_item_id
        where i.store_id = :storeId and o.id in :optionIds
        order by o.id asc
        """)
    List<MenuItemOption> findAllByStoreIdAndIdInOrderByIdAsc(
        @Param("storeId") Long storeId,
        @Param("optionIds") List<Long> optionIds
    );

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
