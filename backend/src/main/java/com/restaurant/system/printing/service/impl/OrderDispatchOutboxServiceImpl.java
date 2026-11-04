package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.printing.service.OrderDispatchOutboxService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class OrderDispatchOutboxServiceImpl implements OrderDispatchOutboxService {

    private final OrderDispatchOutboxRepository repository;
    private final StoreRepository storeRepository;

    public OrderDispatchOutboxServiceImpl(
        OrderDispatchOutboxRepository repository,
        StoreRepository storeRepository
    ) {
        this.repository = repository;
        this.storeRepository = storeRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.restaurant.system.delivery.ReceiptContextService receiptContexts;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void enqueue(String moduleCode, Long storeId, Long orderId, Long orderUpdateBatchId) {
        String sourceKey = sourceKey(moduleCode, orderId, orderUpdateBatchId);
        if (repository.findBySourceKey(sourceKey).isPresent()) {
            return;
        }
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("Store not found: " + storeId));
        LocalDateTime now = LocalDateTime.now();
        var receiptContext = receiptContexts == null ? null : receiptContexts.freeze(store,moduleCode,orderId,orderUpdateBatchId);
        int inserted = repository.insertIfAbsent(
            store.organization_id,
            storeId,
            orderId,
            orderUpdateBatchId,
            moduleCode,
            orderUpdateBatchId == null ? "ORDER_SUBMITTED" : "ORDER_UPDATED",
            sourceKey,
            now,
            com.restaurant.system.printing.security.PrintOriginContext.deviceFor(storeId, store.organization_id)
        );
        if (inserted == 1 && receiptContext != null) {
            var event = repository.findBySourceKey(sourceKey).orElseThrow();
            event.routingContext = receiptContexts.serialize(receiptContext);
            repository.save(event);
        }
    }

    static String sourceKey(String moduleCode, Long orderId, Long orderUpdateBatchId) {
        String source = orderUpdateBatchId == null
            ? "submit:" + orderId
            : "update:" + orderId + ":" + orderUpdateBatchId;
        return source + ":" + moduleCode;
    }
}
