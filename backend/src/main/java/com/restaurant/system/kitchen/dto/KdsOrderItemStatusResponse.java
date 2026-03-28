package com.restaurant.system.kitchen.dto;

import java.time.LocalDateTime;

public class KdsOrderItemStatusResponse {

    public Long order_item_id;
    public String category_code_snapshot;
    public String item_name_snapshot_zh;
    public String item_name_snapshot_en;
    public Integer quantity;
    public Boolean is_modified_after_submit;
    public LocalDateTime modified_after_submit_at;
    public String station_code;
    public Boolean requires_kitchen_task;
    public String task_status;
    public String special_instructions_snapshot;
    public LocalDateTime started_at;
    public LocalDateTime completed_at;
    public LocalDateTime served_at;
}
