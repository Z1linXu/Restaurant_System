package com.restaurant.system.kitchen.dto;

import java.time.LocalDateTime;
import java.util.List;

public class KdsOrderGroupResponse {

    public Long order_id;
    public String order_no;
    public String table_no;
    public String pickup_no;
    public String order_status;
    public Boolean is_modified_after_submit;
    public LocalDateTime modified_after_submit_at;
    public LocalDateTime created_at;
    public LocalDateTime ready_at;
    public LocalDateTime completed_at;
    public List<KdsOrderItemStatusResponse> items;
}
