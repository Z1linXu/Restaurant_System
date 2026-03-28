package com.restaurant.system.kitchen.dto;

import java.time.LocalDateTime;
import java.util.List;

public class KdsTaskDisplayResponse {

    public Long task_id;
    public Long order_id;
    public String order_no;
    public String table_no;
    public String pickup_no;
    public String station_code;
    public String item_name_snapshot_zh;
    public String item_name_snapshot_en;
    public Integer quantity;
    public Boolean order_modified_after_submit;
    public LocalDateTime order_modified_after_submit_at;
    public Boolean item_modified_after_submit;
    public LocalDateTime item_modified_after_submit_at;
    public String status;
    public String special_instructions_snapshot;
    public String size_label;
    public String noodle_type_label;
    public List<String> extra_flags;
    public LocalDateTime created_at;
    public LocalDateTime started_at;
    public LocalDateTime completed_at;
    public LocalDateTime served_at;
}
