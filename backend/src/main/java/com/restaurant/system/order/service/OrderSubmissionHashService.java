package com.restaurant.system.order.service;

import com.restaurant.system.order.dto.IdempotentOrderSubmitRequest;

public interface OrderSubmissionHashService {

    String hash(IdempotentOrderSubmitRequest request);
}
