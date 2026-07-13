package com.restaurant.system.order.service;

import com.restaurant.system.order.dto.IdempotentOrderSubmitRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitResponse;

public interface IdempotentOrderSubmissionService {

    IdempotentOrderSubmitResponse submit(Long pathStoreId, IdempotentOrderSubmitRequest request, Long userId);
}
