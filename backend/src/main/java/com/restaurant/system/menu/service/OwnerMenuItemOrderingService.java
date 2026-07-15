package com.restaurant.system.menu.service;

import com.restaurant.system.menu.entity.MenuItem;
import java.util.List;

public interface OwnerMenuItemOrderingService {
    List<MenuItem> reorder(Long storeId, Long categoryId, List<Long> itemIds);
}
