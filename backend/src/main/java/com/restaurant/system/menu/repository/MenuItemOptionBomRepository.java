package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItemOptionBom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemOptionBomRepository extends JpaRepository<MenuItemOptionBom, Long> {

    @Query("select miob from MenuItemOptionBom miob where miob.menu_item_option_id = :menuItemOptionId")
    List<MenuItemOptionBom> findAllByMenuItemOptionId(@Param("menuItemOptionId") Long menuItemOptionId);
}
