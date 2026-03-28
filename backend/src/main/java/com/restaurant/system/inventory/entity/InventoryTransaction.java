package com.restaurant.system.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGSERIAL")
    public Long id;

    @Column(name = "inventory_item_id")
    public Long inventory_item_id;

    @Column(name = "operated_by")
    public Long operated_by;

    @Column(name = "txn_type")
    public String txn_type;

    @Column(name = "source_type")
    public String source_type;

    @Column(name = "source_id")
    public Long source_id;

    @Column(name = "qty_change")
    public BigDecimal qty_change;

    @Column(name = "stock_before")
    public BigDecimal stock_before;

    @Column(name = "stock_after")
    public BigDecimal stock_after;

    @Column(name = "remarks")
    public String remarks;

    @Column(name = "created_at")
    public LocalDateTime created_at;
}
