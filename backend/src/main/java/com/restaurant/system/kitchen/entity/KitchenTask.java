package com.restaurant.system.kitchen.entity;

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
@Table(name = "kitchen_tasks")
public class KitchenTask {

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

    @Column(name = "station_code")
    public String station_code;

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

    @Column(name = "priority")
    public Integer priority;

    @Column(name = "started_at")
    public LocalDateTime started_at;

    @Column(name = "completed_at")
    public LocalDateTime completed_at;

    @Column(name = "served_at")
    public LocalDateTime served_at;

    @Column(name = "cancelled_at")
    public LocalDateTime cancelled_at;

    @Column(name = "created_at")
    public LocalDateTime created_at;
}
