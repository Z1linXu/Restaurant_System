package com.restaurant.system.kitchen.dto;

import java.time.LocalDateTime;

public class KitchenTaskResponse {

    public Long id;
    public Long order_id;
    public Long order_item_id;
    public Long store_id;
    public String station_code;
    public String item_name_snapshot_zh;
    public String item_name_snapshot_en;
    public Integer quantity;
    public String special_instructions_snapshot;
    public String status;
    public Integer priority;
    public LocalDateTime created_at;
    public LocalDateTime started_at;
    public LocalDateTime completed_at;
    public LocalDateTime served_at;
    public LocalDateTime cancelled_at;
}
