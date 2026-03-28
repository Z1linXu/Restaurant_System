package com.restaurant.system.order.dto;

import java.time.LocalDateTime;

public class FrontdeskBeverageItemResponse {

    public Long beverage_item_id;
    public Long order_id;
    public String order_no;
    public String table_no;
    public String pickup_no;
    public String order_type;
    public Long order_item_id;
    public String item_name_snapshot_zh;
    public String item_name_snapshot_en;
    public Integer quantity;
    public String special_instructions_snapshot;
    public String beverage_status;
    public LocalDateTime created_at;
    public LocalDateTime submitted_at;
    public LocalDateTime updated_at;
    public LocalDateTime started_at;
    public LocalDateTime ready_at;
    public LocalDateTime served_at;
    public LocalDateTime cancelled_at;
}
