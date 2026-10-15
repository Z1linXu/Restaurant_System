package com.restaurant.system.inventory.service;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.inventory.entity.InventoryItem;
import com.restaurant.system.inventory.entity.InventoryTransaction;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.inventory.repository.InventoryTransactionRepository;
import com.restaurant.system.order.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryConsumptionService {
    private final InventoryItemRepository items;
    private final InventoryTransactionRepository transactions;

    public InventoryConsumptionService(InventoryItemRepository items, InventoryTransactionRepository transactions) {
        this.items = items;
        this.transactions = transactions;
    }

    /** Locks and ledger participate in the submitting/updating Order's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(Order order, Map<Long, BigDecimal> demand, LocalDateTime now) {
        if (order == null || order.store_id == null || order.id == null) {
            throw new BusinessException("Inventory consumption requires an Order scope");
        }
        var locked = new ArrayList<InventoryItem>();
        // Stable ordering prevents two overlapping BOMs from taking locks in opposite order.
        var ordered = new TreeMap<>(demand);
        for (var entry : ordered.entrySet()) {
            if (entry.getValue() == null || entry.getValue().signum() < 0) {
                throw new BusinessException("Invalid inventory demand");
            }
            InventoryItem item = items.findByIdAndStoreIdForUpdate(entry.getKey(), order.store_id)
                .orElseThrow(() -> new BusinessException("Inventory item is not available for this Store"));
            if (!Boolean.TRUE.equals(item.is_active) || item.current_stock == null
                || item.current_stock.compareTo(entry.getValue()) < 0) {
                throw new BusinessException("Insufficient or unavailable inventory");
            }
            locked.add(item);
        }
        // Validate the entire BOM before changing stock or writing any ledger entries.
        for (InventoryItem item : locked) {
            BigDecimal quantity = ordered.get(item.id);
            if (quantity.signum() == 0) continue;
            var transaction = new InventoryTransaction();
            transaction.inventory_item_id = item.id;
            transaction.operated_by = order.created_by;
            transaction.txn_type = "consume";
            transaction.source_type = "order";
            transaction.source_id = order.id;
            transaction.qty_change = quantity.negate();
            transaction.stock_before = item.current_stock;
            transaction.stock_after = item.current_stock.subtract(quantity);
            transaction.remarks = "Order submission inventory deduction";
            transaction.created_at = now;
            item.current_stock = transaction.stock_after;
            item.updated_at = now;
            items.save(item);
            transactions.save(transaction);
        }
    }
}
