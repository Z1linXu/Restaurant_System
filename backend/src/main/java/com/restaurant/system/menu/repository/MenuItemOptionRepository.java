package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItemOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemOptionRepository extends JpaRepository<MenuItemOption, Long> {
}
