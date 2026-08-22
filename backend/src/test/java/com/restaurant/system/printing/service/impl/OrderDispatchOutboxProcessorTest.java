package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.printing.entity.OrderDispatchOutbox;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrintDispatchOutcome;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderDispatchOutboxProcessorTest {

    @Mock
    private OrderDispatchOutboxRepository repository;
    @Mock
    private PrinterAssignmentRepository printerAssignmentRepository;
    @Mock
    private PrintDispatcherService printDispatcherService;

    private OrderDispatchOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderDispatchOutboxProcessor(
            repository,
            printerAssignmentRepository,
            printDispatcherService,
            Runnable::run,
            transactionTemplate()
        );
    }

    @Test
    void persistedEventIsDispatchedOnceAndMarkedCompleted() {
        OrderDispatchOutbox event = event();
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(event));
        when(repository.claimDueForProcessing(eq(event.id), any(), any())).thenReturn(1);
        when(repository.findById(event.id)).thenReturn(Optional.of(event));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(event.storeId, event.moduleCode))
            .thenReturn(Optional.of(assignment(event.storeId, event.moduleCode, 4L)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.processDueEvents();

        verify(printDispatcherService).dispatchPersistedEvent("GRAB", 1L, 9L, null, "submit:9:GRAB");
        assertEquals("DISPATCHED", event.status);
        assertNotNull(event.completedAt);
    }

    @Test
    void skippedDispatchIsTerminalAndCannotMasqueradeAsSuccess() {
        OrderDispatchOutbox event = event();
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(event));
        when(repository.claimDueForProcessing(eq(event.id), any(), any())).thenReturn(1);
        when(repository.findById(event.id)).thenReturn(Optional.of(event));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(printDispatcherService.dispatchPersistedEvent(any(), any(), any(), any(), any()))
            .thenReturn(PrintDispatchOutcome.SKIPPED);

        processor.processDueEvents();

        assertEquals("SKIPPED", event.status);
        assertEquals("SKIPPED", event.lastError);
        assertNotNull(event.completedAt);
    }

    @Test
    void unexpectedDispatchFailureRemainsPendingWithBackoff() {
        OrderDispatchOutbox event = event();
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(event));
        when(repository.claimDueForProcessing(eq(event.id), any(), any())).thenReturn(1);
        when(repository.findById(event.id)).thenReturn(Optional.of(event));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(event.storeId, event.moduleCode))
            .thenReturn(Optional.of(assignment(event.storeId, event.moduleCode, 4L)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("backend restart"))
            .when(printDispatcherService)
            .dispatchPersistedEvent(any(), any(), any(), any(), any());

        processor.processDueEvents();

        assertEquals("PENDING", event.status);
        assertEquals(1, event.attemptCount);
        assertEquals("backend restart", event.lastError);
        assertNotNull(event.nextAttemptAt);
    }

    @Test
    void samePrinterEventsKeepFifoOrder() {
        OrderDispatchOutbox first = event(1L, "GRAB", "submit:9:GRAB");
        OrderDispatchOutbox second = event(2L, "HOT_KITCHEN", "submit:9:HOT_KITCHEN");
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(first, second));
        when(repository.claimDueForProcessing(any(), any(), any())).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(first));
        when(repository.findById(2L)).thenReturn(Optional.of(second));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(eq(1L), any()))
            .thenReturn(Optional.of(assignment(1L, "GRAB", 10L)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.processDueEvents();

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(printDispatcherService);
        inOrder.verify(printDispatcherService).dispatchPersistedEvent("GRAB", 1L, 9L, null, "submit:9:GRAB");
        inOrder.verify(printDispatcherService).dispatchPersistedEvent("HOT_KITCHEN", 1L, 9L, null, "submit:9:HOT_KITCHEN");
    }

    @Test
    void slowPrinterFailureDoesNotKeepUnrelatedPrinterPending() {
        OrderDispatchOutbox slow = event(1L, "GRAB", "submit:9:GRAB");
        OrderDispatchOutbox unrelated = event(2L, "FRONTDESK_RECEIPT", "submit:9:FRONTDESK_RECEIPT");
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(slow, unrelated));
        when(repository.claimDueForProcessing(any(), any(), any())).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(slow));
        when(repository.findById(2L)).thenReturn(Optional.of(unrelated));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(1L, "GRAB"))
            .thenReturn(Optional.of(assignment(1L, "GRAB", 10L)));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(1L, "FRONTDESK_RECEIPT"))
            .thenReturn(Optional.of(assignment(1L, "FRONTDESK_RECEIPT", 11L)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("printer offline"))
            .when(printDispatcherService)
            .dispatchPersistedEvent(eq("GRAB"), any(), any(), any(), any());

        processor.processDueEvents();

        assertEquals("PENDING", slow.status);
        assertEquals("DISPATCHED", unrelated.status);
    }

    @Test
    void differentPrinterEventsCanDispatchConcurrentlyWithinExecutorBound() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        processor = new OrderDispatchOutboxProcessor(
            repository,
            printerAssignmentRepository,
            printDispatcherService,
            executor,
            transactionTemplate()
        );
        OrderDispatchOutbox slow = event(1L, "GRAB", "submit:9:GRAB");
        OrderDispatchOutbox unrelated = event(2L, "FRONTDESK_RECEIPT", "submit:9:FRONTDESK_RECEIPT");
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch unrelatedStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(slow, unrelated));
        when(repository.claimDueForProcessing(any(), any(), any())).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(slow));
        when(repository.findById(2L)).thenReturn(Optional.of(unrelated));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(1L, "GRAB"))
            .thenReturn(Optional.of(assignment(1L, "GRAB", 10L)));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(1L, "FRONTDESK_RECEIPT"))
            .thenReturn(Optional.of(assignment(1L, "FRONTDESK_RECEIPT", 11L)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doAnswer(invocation -> {
                slowStarted.countDown();
                assertTrue(releaseSlow.await(2, TimeUnit.SECONDS));
                return null;
            })
            .when(printDispatcherService)
            .dispatchPersistedEvent(eq("GRAB"), any(), any(), any(), any());
        org.mockito.Mockito.doAnswer(invocation -> {
                unrelatedStarted.countDown();
                return null;
            })
            .when(printDispatcherService)
            .dispatchPersistedEvent(eq("FRONTDESK_RECEIPT"), any(), any(), any(), any());

        try {
            processor.processDueEvents();

            assertTrue(slowStarted.await(1, TimeUnit.SECONDS));
            assertTrue(unrelatedStarted.await(1, TimeUnit.SECONDS));
            releaseSlow.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        } finally {
            releaseSlow.countDown();
            executor.shutdownNow();
        }
        assertEquals("DISPATCHED", slow.status);
        assertEquals("DISPATCHED", unrelated.status);
    }

    @Test
    void duplicateSchedulerObservationDoesNotDuplicateDispatchWhenClaimFails() {
        OrderDispatchOutbox event = event();
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(event), List.of(event));
        when(repository.claimDueForProcessing(eq(event.id), any(), any())).thenReturn(1, 0);
        when(repository.findById(event.id)).thenReturn(Optional.of(event));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(event.storeId, event.moduleCode))
            .thenReturn(Optional.of(assignment(event.storeId, event.moduleCode, 4L)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.processDueEvents();
        processor.processDueEvents();

        verify(printDispatcherService, times(1)).dispatchPersistedEvent("GRAB", 1L, 9L, null, "submit:9:GRAB");
        assertEquals("DISPATCHED", event.status);
    }

    @Test
    void samePrinterFailureKeepsLaterEventOrderedAndDoesNotRetryBeforeBackoff() {
        OrderDispatchOutbox first = event(1L, "GRAB", "submit:9:GRAB");
        OrderDispatchOutbox second = event(2L, "HOT_KITCHEN", "submit:9:HOT_KITCHEN");
        when(repository.findDueForDispatch(any(), any())).thenReturn(List.of(first, second), List.of(first));
        when(repository.claimDueForProcessing(any(), any(), any())).thenReturn(1, 1, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(first));
        when(repository.findById(2L)).thenReturn(Optional.of(second));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(eq(1L), any()))
            .thenReturn(Optional.of(assignment(1L, "GRAB", 10L)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("printer offline"))
            .doReturn(PrintDispatchOutcome.DISPATCHED)
            .when(printDispatcherService)
            .dispatchPersistedEvent(eq("GRAB"), any(), any(), any(), any());

        processor.processDueEvents();
        processor.processDueEvents();

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(printDispatcherService);
        inOrder.verify(printDispatcherService).dispatchPersistedEvent("GRAB", 1L, 9L, null, "submit:9:GRAB");
        inOrder.verify(printDispatcherService).dispatchPersistedEvent("HOT_KITCHEN", 1L, 9L, null, "submit:9:HOT_KITCHEN");
        verify(printDispatcherService, times(1)).dispatchPersistedEvent(eq("GRAB"), any(), any(), any(), any());
        assertEquals("PENDING", first.status);
        assertEquals("DISPATCHED", second.status);
    }

    private OrderDispatchOutbox event() {
        return event(1L, "GRAB", "submit:9:GRAB");
    }

    private OrderDispatchOutbox event(Long id, String moduleCode, String sourceKey) {
        OrderDispatchOutbox event = new OrderDispatchOutbox();
        event.id = id;
        event.organizationId = 7L;
        event.storeId = 1L;
        event.orderId = 9L;
        event.moduleCode = moduleCode;
        event.eventType = "ORDER_SUBMITTED";
        event.sourceKey = sourceKey;
        event.status = "PENDING";
        event.attemptCount = 0;
        event.nextAttemptAt = LocalDateTime.now();
        event.createdAt = LocalDateTime.now();
        event.updatedAt = event.createdAt;
        return event;
    }

    private PrinterAssignment assignment(Long storeId, String moduleCode, Long printerId) {
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.store_id = storeId;
        assignment.module_code = moduleCode;
        assignment.printer_id = printerId;
        assignment.enabled = true;
        return assignment;
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
