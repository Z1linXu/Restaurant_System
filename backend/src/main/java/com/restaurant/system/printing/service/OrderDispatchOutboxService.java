package com.restaurant.system.printing.service;

public interface OrderDispatchOutboxService {

    void enqueue(String moduleCode, Long storeId, Long orderId, Long orderUpdateBatchId);
}
