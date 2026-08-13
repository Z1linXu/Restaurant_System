package com.restaurant.system.menu.dto;

import java.math.BigDecimal;

public class StorePricingPolicyResponse {

    public Long store_id;
    public Long policy_revision;
    public BigDecimal size_small_delta;
    public BigDecimal size_regular_delta;
    public BigDecimal size_large_delta;
    public BigDecimal combo_delta;
}
