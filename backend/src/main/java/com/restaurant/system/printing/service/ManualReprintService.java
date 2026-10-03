package com.restaurant.system.printing.service;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.dto.*;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.security.PrintOriginContext;
import com.restaurant.system.printing.security.PrintRequestAttestation;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** One bounded manual intent -> one new job. Old jobs are never reset. */
@Service
public class ManualReprintService {
    public record Intent(String sourceKey, String hash, TransactionTemplate transportTransaction) {}
    private static final ThreadLocal<Intent> INTENT = new ThreadLocal<>();
    public static Intent currentIntent() { return INTENT.get(); }
    /** REAL side effect starts only AFTER durable job/idempotency/PRINTING commit. */
    public static boolean deferRealTransport(Runnable action) {
        Intent intent = INTENT.get();
        if (intent == null) return false;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Manual transport requires durable reservation transaction");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                intent.transportTransaction().executeWithoutResult(status -> action.run());
            }
        });
        return true;
    }
    public record ActiveJob(Long job_id, String status, String module, Long claimed_device_id,
        Long preferred_device_id, long age_seconds, LocalDateTime created_at, LocalDateTime printing_started_at) {}
    public record Confirmation(String confirmation_fingerprint, List<ActiveJob> active_jobs) {}
    public static class ConfirmationRequired extends RuntimeException {
        public final Confirmation confirmation;
        public ConfirmationRequired(Confirmation confirmation) { super("请确认当前打印任务后再重打"); this.confirmation = confirmation; }
    }
    private final com.restaurant.system.modules.StoreModuleAccessEvaluator modules;
    private final PrintJobRepository jobs;
    private final OrderRepository orders;
    private final PrintJobService jobService;
    private final PrintDispatcherService dispatcher;
    private final EntityManager entityManager;
    private final TransactionTemplate transportTransaction;
    public ManualReprintService(PrintJobRepository jobs, OrderRepository orders,
        PrintJobService jobService, PrintDispatcherService dispatcher, EntityManager entityManager, PlatformTransactionManager transactions, com.restaurant.system.modules.StoreModuleAccessEvaluator modules) {
        this.modules = modules;
        this.jobs = jobs; this.orders = orders;
        this.jobService = jobService; this.dispatcher = dispatcher; this.entityManager = entityManager;
        this.transportTransaction = new TransactionTemplate(transactions);
        this.transportTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional
    public PrintJobResponse reprintJob(Long sourceId, ManualReprintRequest request, Long userId) {
        PrintJob source = jobService.requireJob(sourceId);
        return execute(source.store_id, source.order_id, source.module_code, sourceId, null, request, userId);
    }
    @Transactional
    public PrintJobResponse reprintOrder(Long orderId, OrderReprintRequest request, Long userId) {
        var order = orders.findExistingById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        String module = request == null ? null : request.receipt_type;
        if (!Set.of("GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN").contains(module == null ? "" : module))
            throw new BusinessException("receiptType must be GRAB, FRONTDESK_RECEIPT, or HOT_KITCHEN");
        if (order.kitchenMirror()) {
            if (!Set.of("GRAB", "HOT_KITCHEN").contains(module))
                throw new BusinessException("Kitchen mirror allows only GRAB / HOT_KITCHEN");
            var source = jobs.findByDispatchSourceKey("submit:" + orderId + ":" + module)
                    .filter(job -> order.store_id.equals(job.store_id) && orderId.equals(job.order_id))
                    .orElseThrow(() -> new BusinessException("Original kitchen print job is not ready"));
            return execute(order.store_id, orderId, module, source.id, null, request, userId);
        }
        return execute(order.store_id, orderId, module, null, request, request, userId);
    }
    private PrintJobResponse execute(Long storeId, Long orderId, String module, Long sourceId,
        OrderReprintRequest orderRequest, ManualReprintRequest request, Long userId) {
        if (request == null || request.idempotency_key == null || !request.idempotency_key.matches("[A-Za-z0-9_-]{8,128}"))
            throw new BusinessException("A stable manual reprint idempotency_key is required");
        if (orderId != null) {
            var order = orders.findExistingById(orderId);
            if (order != null && "UBER_EATS".equals(order.external_source))
                modules.requireCapability(storeId, com.restaurant.system.modules.ModuleKeys.UBER_EATS);
        }
        // Both reprint entry points share this lock, including printer test jobs without an order.
        Store store = entityManager.find(Store.class, storeId, LockModeType.PESSIMISTIC_WRITE);
        if (store == null) throw new BusinessException("Store not found");
        Long deviceId = PrintOriginContext.deviceFor(storeId, store.organization_id);
        String sourceKey = "manual:" + storeId + ":" + request.idempotency_key;
        String hash = hash(storeId + "|" + orderId + "|" + module + "|" + sourceId + "|" + userId + "|" + deviceId
            + "|" + (orderRequest == null ? null : orderRequest.printer_id));
        PrintJob replay = jobs.findByDispatchSourceKey(sourceKey).orElse(null);
        if (replay != null) {
            if (!hash.equals(replay.manualRequestHash)) throw new BusinessException("Manual reprint request identity conflict");
            return jobService.toResponse(replay);
        }
        List<PrintJob> active = jobs.findActiveForReprint(storeId, orderId, module);
        if (!active.isEmpty()) {
            String fingerprint = hash(active.stream().map(job -> job.id + ":" + job.status + ":" + job.claimedByDeviceId
                + ":" + job.clientAttemptToken + ":" + job.updated_at).reduce("", (a, b) -> a + "|" + b));
            if (!fingerprint.equals(request.confirmation_fingerprint)) {
                var now = LocalDateTime.now();
                throw new ConfirmationRequired(new Confirmation(fingerprint, active.stream().map(job -> new ActiveJob(
                    job.id, job.status, job.module_code, job.claimedByDeviceId, job.preferredDeviceId,
                    job.created_at == null ? 0 : Math.max(0, Duration.between(job.created_at, now).getSeconds()),
                    job.created_at, job.printingStartedAt)).toList()));
            }
        }
        INTENT.set(new Intent(sourceKey, hash, transportTransaction));
        try {
            return sourceId == null ? dispatcher.reprintOrder(orderId, orderRequest, userId) : dispatcher.reprintJob(sourceId, userId);
        } finally { INTENT.remove(); }
    }
    private static String hash(String value) { return PrintRequestAttestation.sha256(value.getBytes(StandardCharsets.UTF_8)); }
}
