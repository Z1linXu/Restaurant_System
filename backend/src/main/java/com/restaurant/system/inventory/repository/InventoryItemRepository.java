package com.restaurant.system.inventory.repository;

import com.restaurant.system.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id and i.store_id = :storeId")
    Optional<InventoryItem> findByIdAndStoreIdForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);
}
