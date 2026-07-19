package com.restaurant.system.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderItemResponse {

    public Long id;
    public Long menu_item_id;
    public String category_code_snapshot;
    public Long station_id_snapshot;
    public String item_sku_snapshot;
    public String item_name_snapshot_zh;
    public String item_name_snapshot_en;
    public Integer quantity;
    public BigDecimal unit_price;
    public BigDecimal line_amount;
    public Integer combo_group_no;
    public String combo_role;
    public String notes;
    public Boolean is_modified_after_submit;
    public LocalDateTime modified_after_submit_at;
    public Integer added_revision;
    public Long order_update_batch_id;
    public Boolean requires_kitchen_task;
    public Boolean is_beverage_item;
    public Boolean is_kitchen_related_item;
    public String station_code;
    public String task_status;
    public LocalDateTime started_at;
    public LocalDateTime ready_for_pickup_at;
    public LocalDateTime served_at;
    public String beverage_status;
    public String beverage_special_instructions_snapshot;
    public LocalDateTime beverage_started_at;
    public LocalDateTime beverage_ready_at;
    public LocalDateTime beverage_served_at;
    public LocalDateTime beverage_cancelled_at;
    public List<OrderItemOptionResponse> options;
}
