package com.restaurant.system.printing.service.impl;

import com.restaurant.system.printing.entity.OrderDispatchOutbox;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrintDispatchOutcome;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("!staging-synthetic-bootstrap")
public class OrderDispatchOutboxProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderDispatchOutboxProcessor.class);
    private static final int BATCH_SIZE = 10;
    private static final long PROCESSING_LEASE_SECONDS = 120L;

    private final OrderDispatchOutboxRepository repository;
    private final PrinterAssignmentRepository printerAssignmentRepository;
    private final PrintDispatcherService printDispatcherService;
    private final Executor printTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> dispatchChains = new ConcurrentHashMap<>();

    public OrderDispatchOutboxProcessor(
        OrderDispatchOutboxRepository repository,
        PrinterAssignmentRepository printerAssignmentRepository,
        PrintDispatcherService printDispatcherService,
        @Qualifier("printTaskExecutor") Executor printTaskExecutor,
        TransactionTemplate transactionTemplate
    ) {
        this.repository = repository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.printDispatcherService = printDispatcherService;
        this.printTaskExecutor = printTaskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${app.printing.dispatch-outbox-poll-ms:1000}", initialDelayString = "${app.printing.dispatch-outbox-initial-delay-ms:2000}")
    public void processDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderDispatchOutbox> events = repository.findDueForDispatch(now, PageRequest.of(0, BATCH_SIZE));
        for (OrderDispatchOutbox event : events) {
            scheduleEvent(event.id, serializationKey(event));
        }
    }

    private void scheduleEvent(Long eventId, String serializationKey) {
        dispatchChains.compute(serializationKey, (key, current) -> {
            CompletableFuture<Void> base = current == null
                ? CompletableFuture.completedFuture(null)
                : current.exceptionally(exception -> null);
            CompletableFuture<Void> scheduled = base.thenRunAsync(() -> processEventById(eventId), printTaskExecutor);
            scheduled.whenComplete((ignored, exception) -> {
                if (exception != null) {
                    logger.error("Order dispatch outbox async event {} failed outside transactional handler", eventId, exception);
                }
                dispatchChains.remove(key, scheduled);
            });
            return scheduled;
        });
    }

    private void processEventById(Long eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            int claimed = repository.claimDueForProcessing(
                eventId,
                now,
                now.plusSeconds(PROCESSING_LEASE_SECONDS)
            );
            if (claimed != 1) {
                return;
            }
            OrderDispatchOutbox event = repository.findById(eventId).orElse(null);
            if (event == null) {
                return;
            }
            try {
                PrintDispatchOutcome outcome = printDispatcherService.dispatchPersistedEvent(
                    event.moduleCode,
                    event.storeId,
                    event.orderId,
                    event.orderUpdateBatchId,
                    event.sourceKey
                );
                if (outcome == null) {
                    // Compatibility for older test doubles; production dispatchers
                    // always return one explicit terminal outcome.
                    outcome = PrintDispatchOutcome.DISPATCHED;
                }
                event.status = outcome.name();
                event.completedAt = LocalDateTime.now();
                event.lastError = switch (outcome) {
                    case DISPATCHED, MOCK_RENDERED -> null;
                    default -> outcome.name();
                };
            } catch (RuntimeException exception) {
                event.status = "PENDING";
                event.attemptCount = event.attemptCount + 1;
                event.lastError = safeError(exception);
                event.nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds(event.attemptCount));
                logger.error("Order dispatch outbox event {} failed; retry {} scheduled", event.id, event.attemptCount, exception);
            }
            event.updatedAt = LocalDateTime.now();
            repository.save(event);
        });
    }

    private String serializationKey(OrderDispatchOutbox event) {
        return printerAssignmentRepository.findByStoreIdAndModuleCode(event.storeId, event.moduleCode)
            .filter(assignment -> Boolean.TRUE.equals(assignment.enabled) && assignment.printer_id != null)
            .map(assignment -> "store:" + event.storeId + "|printer:" + assignment.printer_id)
            .orElseGet(() -> "store:" + event.storeId + "|module:" + event.moduleCode);
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
