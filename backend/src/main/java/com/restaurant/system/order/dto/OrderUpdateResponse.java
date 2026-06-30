package com.restaurant.system.order.dto;

public class OrderUpdateResponse {

    public OrderResponse order;
    public Long update_batch_id;
    public Integer revision;
    public Boolean already_processed;
}
