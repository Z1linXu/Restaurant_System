package com.restaurant.system.kitchen.dto;

import java.time.LocalDateTime;
import java.util.List;

public class FrontdeskBeverageOrderResponse {

    public Long order_id;
    public String order_no;
    public String table_no;
    public String pickup_no;
    public String order_status;
    public LocalDateTime created_at;
    public List<KdsOrderItemStatusResponse> items;
}
