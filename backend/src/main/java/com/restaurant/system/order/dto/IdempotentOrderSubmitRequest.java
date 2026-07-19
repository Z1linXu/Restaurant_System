package com.restaurant.system.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class IdempotentOrderSubmitRequest {

    @NotBlank
    public String client_order_id;

    public String idempotency_key;

    public String local_draft_id;

    public String device_id;

    @NotNull
    public Long organization_id;

    @NotNull
    public Long store_id;

    public Long server_order_id;

    @NotBlank
    public String order_type;

    public String table_no;

    public String pickup_no;

    @NotNull
    public Long menu_revision;

    public BigDecimal expected_subtotal_amount;

    @Valid
    @NotEmpty
    public List<CreateOrderItemRequest> items = new ArrayList<>();
}
