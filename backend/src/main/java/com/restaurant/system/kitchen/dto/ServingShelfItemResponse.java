package com.restaurant.system.kitchen.dto;

import java.time.LocalDateTime;

public class ServingShelfItemResponse {

    public Long task_id;
    public Long order_id;
    public Long order_item_id;
    public String order_no;
    public String order_type;
    public String table_no;
    public String pickup_no;
    public String category_code_snapshot;
    public String item_name_snapshot_zh;
    public String item_name_snapshot_en;
    public Integer quantity;
    public String special_instructions_snapshot;
    public String size_label;
    public LocalDateTime created_at;
    public LocalDateTime ready_for_pickup_at;
}
