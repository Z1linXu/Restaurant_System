package com.restaurant.system.menu.repository;

import com.restaurant.system.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
}
