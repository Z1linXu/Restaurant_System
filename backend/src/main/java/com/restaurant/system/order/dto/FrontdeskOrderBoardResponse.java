package com.restaurant.system.order.dto;

import java.time.LocalDateTime;

public class FrontdeskOrderBoardResponse {

    public Long order_id;
    public String order_no;
    public String order_type;
    public String table_no;
    public String pickup_no;
    public String order_status;
    public Boolean is_modified_after_submit;
    public LocalDateTime modified_after_submit_at;
    public LocalDateTime submitted_at;
    public LocalDateTime updated_at;
    public Integer total_item_count;
    public Integer ready_item_count;
    public Integer beverage_pending_count;
    public Integer kitchen_pending_count;
}
