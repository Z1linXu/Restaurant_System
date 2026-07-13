package com.restaurant.system.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitResponse;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.entity.OrderSubmissionRequest;
import com.restaurant.system.order.exception.OrderSubmissionException;
import com.restaurant.system.order.repository.OrderSubmissionRequestRepository;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotentOrderSubmissionServiceImplTest {

    @Mock
    private OrderSubmissionRequestRepository submissionRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;
    @Mock
    private OrderService orderService;

    private final Map<String, OrderSubmissionRequest> records = new HashMap<>();
    private final Map<String, Thread> recordOwners = new HashMap<>();
    private IdempotentOrderSubmissionServiceImpl service;
    private MenuItem item;

    @BeforeEach
    void setUp() {
        service = new IdempotentOrderSubmissionServiceImpl(
            submissionRepository,
            storeRepository,
            menuItemRepository,
            menuItemOptionRepository,
            new OrderSubmissionHashServiceImpl(new ObjectMapper()),
            orderService
        );

        Store store = new Store();
        store.id = 1L;
        store.organization_id = 7L;
        store.menu_revision = 4L;
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        Store secondStore = new Store();
        secondStore.id = 2L;
        secondStore.organization_id = 7L;
        secondStore.menu_revision = 4L;
        when(storeRepository.findById(2L)).thenReturn(Optional.of(secondStore));

        item = new MenuItem();
        item.id = 20L;
        item.store_id = 1L;
        item.base_price = new BigDecimal("12.50");
        item.is_active = true;
        item.is_sold_out = false;
        when(menuItemRepository.findById(20L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(20L)).thenReturn(List.of());

        when(submissionRepository.insertIfAbsent(anyLong(), anyLong(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                Long storeId = invocation.getArgument(1);
                String idempotencyKey = invocation.getArgument(2);
                String key = recordKey(storeId, idempotencyKey);
                synchronized (records) {
                    if (records.containsKey(key)) return 0;
                    OrderSubmissionRequest record = new OrderSubmissionRequest();
                    record.organizationId = invocation.getArgument(0);
                    record.storeId = storeId;
                    record.idempotencyKey = idempotencyKey;
                    record.clientOrderId = invocation.getArgument(3);
                    record.payloadHash = invocation.getArgument(4);
                    record.status = "PROCESSING";
                    record.createdAt = invocation.getArgument(5);
                    record.updatedAt = record.createdAt;
                    records.put(key, record);
                    recordOwners.put(key, Thread.currentThread());
                    return 1;
                }
            });
        when(submissionRepository.findForUpdate(anyLong(), any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            String idempotencyKey = invocation.getArgument(1);
            String key = recordKey(storeId, idempotencyKey);
            OrderSubmissionRequest record;
            synchronized (records) {
                record = records.get(key);
            }
            if (record != null && recordOwners.get(key) != Thread.currentThread()) {
                synchronized (record) {
                    while (!"COMPLETED".equals(record.status)) {
                        record.wait(1000L);
                    }
                }
            }
            return Optional.ofNullable(record);
        });
        when(submissionRepository.save(any(OrderSubmissionRequest.class))).thenAnswer(invocation -> {
            OrderSubmissionRequest record = invocation.getArgument(0);
            synchronized (records) {
                records.put(recordKey(record.storeId, record.idempotencyKey), record);
            }
            synchronized (record) {
                record.notifyAll();
            }
            return record;
        });

        OrderResponse order = new OrderResponse();
        order.id = 99L;
        order.store_id = 1L;
        order.status = "preparing";
        when(orderService.createOrReplaceDraftAndSubmit(any(), any())).thenReturn(order);
        when(orderService.getOrderDetail(99L)).thenReturn(order);
    }

    @Test
    void sameKeySubmittedTwentyTimesCreatesOneOrderAndReplaysResult() {
        IdempotentOrderSubmitRequest request = request("client-order-1", "less soup");

        for (int index = 0; index < 20; index++) {
            IdempotentOrderSubmitResponse response = service.submit(1L, request, 5L);
            assertEquals(99L, response.order_id);
            if (index == 0) assertFalse(response.replayed);
            else assertTrue(response.replayed);
        }

        verify(orderService, times(1)).createOrReplaceDraftAndSubmit(any(), any());
    }

    @Test
    void sameKeySubmittedConcurrentlyCreatesOneOrder() throws Exception {
        IdempotentOrderSubmitRequest request = request("concurrent-order", null);
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<IdempotentOrderSubmitResponse>> futures = java.util.stream.IntStream.range(0, requestCount)
                .mapToObj(index -> executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return service.submit(1L, request, 5L);
                }))
                .toList();
            start.countDown();

            for (Future<IdempotentOrderSubmitResponse> future : futures) {
                assertEquals(99L, future.get(10, TimeUnit.SECONDS).order_id);
            }
        } finally {
            executor.shutdownNow();
        }

        verify(orderService, times(1)).createOrReplaceDraftAndSubmit(any(), any());
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsIdempotencyConflict() {
        service.submit(1L, request("client-order-1", "less soup"), 5L);

        OrderSubmissionException exception = assertThrows(
            OrderSubmissionException.class,
            () -> service.submit(1L, request("client-order-1", "extra soup"), 5L)
        );

        assertEquals("IDEMPOTENCY_CONFLICT", exception.getErrorCode());
        assertEquals(409, exception.getStatus().value());
        verify(orderService, times(1)).createOrReplaceDraftAndSubmit(any(), any());
    }

    @Test
    void staleMenuAndSoldOutItemAreConflictsBeforeOrderCreation() {
        IdempotentOrderSubmitRequest stale = request("stale-key", null);
        stale.menu_revision = 3L;
        OrderSubmissionException staleException = assertThrows(
            OrderSubmissionException.class,
            () -> service.submit(1L, stale, 5L)
        );
        assertEquals("MENU_REVISION_STALE", staleException.getErrorCode());

        item.is_sold_out = true;
        OrderSubmissionException soldOutException = assertThrows(
            OrderSubmissionException.class,
            () -> service.submit(1L, request("sold-out-key", null), 5L)
        );
        assertEquals("ITEM_SOLD_OUT", soldOutException.getErrorCode());
        verify(orderService, times(0)).createOrReplaceDraftAndSubmit(any(), any());
    }

    @Test
    void changedPriceReturnsStableConflictCode() {
        IdempotentOrderSubmitRequest request = request("price-key", null);
        request.expected_subtotal_amount = new BigDecimal("11.00");

        OrderSubmissionException exception = assertThrows(
            OrderSubmissionException.class,
            () -> service.submit(1L, request, 5L)
        );

        assertEquals("PRICE_CHANGED", exception.getErrorCode());
        verify(orderService, times(0)).createOrReplaceDraftAndSubmit(any(), any());
    }

    @Test
    void sameKeyIsIsolatedByStore() {
        service.submit(1L, request("shared-key", null), 5L);

        item.store_id = 2L;
        IdempotentOrderSubmitRequest second = request("shared-key", null);
        second.store_id = 2L;
        service.submit(2L, second, 5L);

        verify(orderService, times(2)).createOrReplaceDraftAndSubmit(any(), any());
    }

    private IdempotentOrderSubmitRequest request(String clientOrderId, String notes) {
        CreateOrderItemRequest orderItem = new CreateOrderItemRequest();
        orderItem.menu_item_id = 20L;
        orderItem.quantity = 1;
        orderItem.combo_role = "standalone";
        orderItem.notes = notes;
        orderItem.options = List.of();

        IdempotentOrderSubmitRequest request = new IdempotentOrderSubmitRequest();
        request.client_order_id = clientOrderId;
        request.organization_id = 7L;
        request.store_id = 1L;
        request.order_type = "dine_in";
        request.table_no = "T1";
        request.menu_revision = 4L;
        request.expected_subtotal_amount = new BigDecimal("12.50");
        request.items = List.of(orderItem);
        return request;
    }

    private String recordKey(Long storeId, String idempotencyKey) {
        return storeId + ":" + idempotencyKey;
    }
}
