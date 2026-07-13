package com.restaurant.system.printing.service.impl;

import com.restaurant.system.printing.entity.OrderDispatchOutbox;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.printing.service.PrintDispatcherService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderDispatchOutboxProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderDispatchOutboxProcessor.class);
    private static final int BATCH_SIZE = 10;

    private final OrderDispatchOutboxRepository repository;
    private final PrintDispatcherService printDispatcherService;

    public OrderDispatchOutboxProcessor(
        OrderDispatchOutboxRepository repository,
        PrintDispatcherService printDispatcherService
    ) {
        this.repository = repository;
        this.printDispatcherService = printDispatcherService;
    }

    @Scheduled(fixedDelayString = "${app.printing.dispatch-outbox-poll-ms:1000}", initialDelayString = "${app.printing.dispatch-outbox-initial-delay-ms:2000}")
    @Transactional
    public void processDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderDispatchOutbox> events = repository.findDueForUpdate(now, PageRequest.of(0, BATCH_SIZE));
        for (OrderDispatchOutbox event : events) {
            try {
                printDispatcherService.dispatchPersistedEvent(
                    event.moduleCode,
                    event.storeId,
                    event.orderId,
                    event.orderUpdateBatchId,
                    event.sourceKey
                );
                event.status = "COMPLETED";
                event.completedAt = LocalDateTime.now();
                event.lastError = null;
            } catch (RuntimeException exception) {
                event.attemptCount = event.attemptCount + 1;
                event.lastError = safeError(exception);
                event.nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds(event.attemptCount));
                logger.error("Order dispatch outbox event {} failed; retry {} scheduled", event.id, event.attemptCount, exception);
            }
            event.updatedAt = LocalDateTime.now();
            repository.save(event);
        }
    }

    private long backoffSeconds(int attemptCount) {
        return Math.min(60L, 1L << Math.min(attemptCount, 6));
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
