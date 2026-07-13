package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.printing.entity.OrderDispatchOutbox;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.printing.service.PrintDispatcherService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderDispatchOutboxProcessorTest {

    @Mock
    private OrderDispatchOutboxRepository repository;
    @Mock
    private PrintDispatcherService printDispatcherService;

    private OrderDispatchOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderDispatchOutboxProcessor(repository, printDispatcherService);
    }

    @Test
    void persistedEventIsDispatchedOnceAndMarkedCompleted() {
        OrderDispatchOutbox event = event();
        when(repository.findDueForUpdate(any(), any())).thenReturn(List.of(event));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.processDueEvents();

        verify(printDispatcherService).dispatchPersistedEvent("GRAB", 1L, 9L, null, "submit:9:GRAB");
        assertEquals("COMPLETED", event.status);
        assertNotNull(event.completedAt);
    }

    @Test
    void unexpectedDispatchFailureRemainsPendingWithBackoff() {
        OrderDispatchOutbox event = event();
        when(repository.findDueForUpdate(any(), any())).thenReturn(List.of(event));
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

    private OrderDispatchOutbox event() {
        OrderDispatchOutbox event = new OrderDispatchOutbox();
        event.id = 1L;
        event.organizationId = 7L;
        event.storeId = 1L;
        event.orderId = 9L;
        event.moduleCode = "GRAB";
        event.eventType = "ORDER_SUBMITTED";
        event.sourceKey = "submit:9:GRAB";
        event.status = "PENDING";
        event.attemptCount = 0;
        event.nextAttemptAt = LocalDateTime.now();
        event.createdAt = LocalDateTime.now();
        event.updatedAt = event.createdAt;
        return event;
    }
}
