package com.restaurant.system.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "frontdesk_beverage_items")
public class FrontdeskBeverageItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "order_id")
    public Long order_id;

    @Column(name = "order_item_id")
    public Long order_item_id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "item_name_snapshot_zh")
    public String item_name_snapshot_zh;

    @Column(name = "item_name_snapshot_en")
    public String item_name_snapshot_en;

    @Column(name = "special_instructions_snapshot")
    public String special_instructions_snapshot;

    @Column(name = "status")
    public String status;

    @Column(name = "quantity")
    public Integer quantity;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "started_at")
    public LocalDateTime started_at;

    @Column(name = "ready_at")
    public LocalDateTime ready_at;

    @Column(name = "served_at")
    public LocalDateTime served_at;

    @Column(name = "cancelled_at")
    public LocalDateTime cancelled_at;
}
