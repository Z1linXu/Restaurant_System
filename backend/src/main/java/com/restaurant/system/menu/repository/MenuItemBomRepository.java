package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItemBom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemBomRepository extends JpaRepository<MenuItemBom, Long> {

    @Query("select mib from MenuItemBom mib where mib.menu_item_id = :menuItemId")
    List<MenuItemBom> findAllByMenuItemId(@Param("menuItemId") Long menuItemId);
}
