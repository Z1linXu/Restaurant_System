package com.restaurant.system.order.dto;

public class IdempotentOrderSubmitResponse {

    public String client_order_id;
    public String idempotency_key;
    public String payload_hash;
    public Long order_id;
    public boolean replayed;
    public OrderResponse order;
}
