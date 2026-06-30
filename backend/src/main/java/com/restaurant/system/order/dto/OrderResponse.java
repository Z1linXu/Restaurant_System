package com.restaurant.system.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    public Long id;
    public String order_no;
    public String status;
    public Long store_id;
    public Long created_by;
    public String order_type;
    public String table_no;
    public String pickup_no;
    public BigDecimal subtotal_amount;
    public BigDecimal discount_amount;
    public BigDecimal total_amount;
    public LocalDateTime submitted_at;
    public LocalDateTime ready_at;
    public LocalDateTime completed_at;
    public Boolean is_modified_after_submit;
    public LocalDateTime modified_after_submit_at;
    public Long modified_after_submit_by;
    public Integer current_revision;
    public LocalDateTime created_at;
    public LocalDateTime updated_at;
    public List<OrderItemResponse> items;
    public List<OrderItemResponse> beverage_items;
    public List<OrderItemResponse> kitchen_items;
}
