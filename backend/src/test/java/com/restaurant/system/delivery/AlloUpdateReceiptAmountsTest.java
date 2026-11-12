package com.restaurant.system.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.pricing.TaxCalculator;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.printing.AlloProductionPrintingPolicy;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.dto.ReceiptPresentation;
import com.restaurant.system.printing.entity.OrderDispatchOutbox;
import com.restaurant.system.printing.renderer.FrontdeskReceiptRenderer;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.user.entity.Store;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlloUpdateReceiptAmountsTest {
    private final StorePresentationService presentation = mock(StorePresentationService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderItemOptionRepository options = mock(OrderItemOptionRepository.class);
    private final OrderDispatchOutboxRepository events = mock(OrderDispatchOutboxRepository.class);
    private final ReceiptContextService contexts = new ReceiptContextService(presentation, orders, items,
        options, events, new ObjectMapper(), mock(AlloProductionPrintingPolicy.class));
    private final FrontdeskReceiptRenderer renderer = new FrontdeskReceiptRenderer();
    private final Store store = new Store();
    private final Order order = new Order();
    private List<OrderItem> lines;

    @BeforeEach
    void setup() {
        store.id = 1L;
        store.code = "ALLO_NOUILLES_TEST";
        store.printing_mode = "MOCK";
        store.name = "SYNTHETIC Allo QA";
        order.id = 10L;
        order.store_id = 1L;
        order.order_type = "dine_in";
        order.table_no = "SYNTHETIC";
        order.discount_amount = BigDecimal.ZERO;
        StorePresentationService.ReceiptSettings settings = new StorePresentationService.ReceiptSettings();
        settings.enabled = true;
        settings.default_language = "en";
        settings.kitchen_station_codes = List.of("BACK_KITCHEN");
        when(presentation.receipts(store)).thenReturn(settings);
        when(presentation.publicView(store)).thenReturn(Map.of("display_name", store.name));
        when(orders.findById(order.id)).thenReturn(Optional.of(order));
        when(options.findAllByOrderItemIds(anyList())).thenReturn(List.of());
    }

    @ParameterizedTest
    @CsvSource({
        "25.48,5.98,0.89,6.87",
        "4.99,3.99,0.59,4.58",
        "16.99,16.99,2.55,19.54",
        "4.99,0.00,0.00,0.00",
        "0.00,4.99,0.75,5.74"
    })
    void newUpdateAllocatesRoundedOrderDifferenceInBothCentDirectionsAndForFreeRefills(
            String previous, String added, String expectedTax, String expectedTotal) {
        arrange(previous, added);
        ReceiptPresentation context = contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L);
        assertEquals(new BigDecimal(added), context.update_subtotal);
        assertEquals(new BigDecimal(expectedTotal), context.update_total);
        assertEquals(0, TaxCalculator.calculateTotal(new BigDecimal(previous)).add(context.update_total)
            .compareTo(order.total_amount));
        String text = render(context);
        assertTrue(text.contains("Subtotal: $" + added));
        assertTrue(text.contains("Tax (order rounding) (14.975%): $" + expectedTax));
        assertTrue(text.contains("Total: $" + expectedTotal));
        assertFalse(text.contains("Previous item"));
    }

    @Test
    void consecutiveUpdatesAndZeroPriceAdditionSumExactlyToThePersistedOrder() {
        lines = new ArrayList<>();
        lines.add(line(1L, "4.99", null));
        BigDecimal receipts = new BigDecimal("5.74");
        long batch = 21;
        for (String amount : List.of("3.99", "1.99", "16.99", "0.00")) {
            lines.add(line(batch, amount, batch));
            persistTotals();
            ReceiptPresentation context = contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, batch);
            receipts = receipts.add(context.update_total);
            assertEquals(0, receipts.compareTo(order.total_amount));
            assertEquals(new BigDecimal(amount), context.update_subtotal);
            batch++;
        }
    }

    @Test
    void cancelledPreviousAndBatchLinesUseTheSameActiveFilterAsOrderAndRenderer() {
        arrange("4.99", "3.99");
        OrderItem oldCancelled = line(12L, "100.00", null);
        OrderItem addedCancelled = line(13L, "50.00", 21L);
        oldCancelled.status = "cancelled";
        addedCancelled.status = "cancelled";
        lines = new ArrayList<>(lines);
        lines.add(oldCancelled);
        lines.add(addedCancelled);
        persistTotals();
        ReceiptPresentation context = contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L);
        assertEquals(new BigDecimal("4.58"), context.update_total);
        assertTrue(render(context).contains("Subtotal: $3.99"));
    }

    @Test
    void persistedEventReplaysFrozenAmountsAfterLiveTotalsOrSettingsChange() {
        arrange("25.48", "5.98");
        ReceiptPresentation frozen = contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L);
        String expected = render(frozen);
        OrderDispatchOutbox event = new OrderDispatchOutbox();
        event.storeId = store.id;
        event.orderId = order.id;
        event.moduleCode = "FRONTDESK_RECEIPT";
        event.routingContext = contexts.serialize(frozen);
        when(events.findBySourceKey("update:10:21:FRONTDESK_RECEIPT")).thenReturn(Optional.of(event));
        order.subtotal_amount = new BigDecimal("999.00");
        order.total_amount = new BigDecimal("999.00");
        clearInvocations(items, orders, presentation);
        ReceiptPresentation replay = contexts.forEvent(store, "FRONTDESK_RECEIPT", order.id, 21L);
        assertEquals(event.routingContext, contexts.serialize(replay));
        assertEquals(expected, render(replay));
        verifyNoInteractions(items, orders, presentation);
    }

    @Test
    void oldContextWithoutEitherFieldKeepsOriginalBatchRoundingAndLabel() {
        arrange("25.48", "5.98");
        ReceiptPresentation legacy = contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L);
        legacy.update_subtotal = null;
        legacy.update_total = null;
        String original = render(contexts.deserialize(contexts.serialize(legacy)));
        assertTrue(original.contains("Tax (14.975%): $0.90"));
        assertTrue(original.contains("Total: $6.88"));
        assertFalse(original.contains("order rounding"));
    }

    @Test
    void partialOrNegativeOrFractionalFrozenAmountsAndLineMismatchFailClosed() {
        arrange("25.48", "5.98");
        ReceiptPresentation context = contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L);
        context.update_total = null;
        assertThrows(BusinessException.class, () -> render(context));
        context.update_total = new BigDecimal("6.87");
        context.update_subtotal = null;
        assertThrows(BusinessException.class, () -> render(context));
        context.update_subtotal = new BigDecimal("5.98");
        context.update_total = new BigDecimal("5.97");
        assertThrows(BusinessException.class, () -> render(context));
        context.update_total = new BigDecimal("6.8701");
        assertThrows(BusinessException.class, () -> render(context));
        context.update_total = new BigDecimal("6.87");
        context.update_subtotal = new BigDecimal("6.00");
        BusinessException mismatch = assertThrows(BusinessException.class, () -> render(context));
        assertEquals("RECEIPT_UPDATE_SUBTOTAL_MISMATCH", mismatch.getMessage());
    }

    @Test
    void unsupportedDiscountOrWholeTotalOrNullAmountsCannotCreateAssumedDelta() {
        arrange("25.48", "5.98");
        order.discount_amount = new BigDecimal("1.00");
        assertThrows(BusinessException.class,
            () -> contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L));
        order.discount_amount = BigDecimal.ZERO;
        order.total_amount = new BigDecimal("36.18");
        assertThrows(BusinessException.class,
            () -> contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L));
        order.total_amount = new BigDecimal("36.17");
        lines.get(1).line_amount = null;
        assertThrows(BusinessException.class,
            () -> contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L));
    }

    @Test
    void initialAndKitchenContextsDoNotAddUpdateMoneyAndOtherStoresKeepLegacyPath() {
        arrange("25.48", "5.98");
        assertFalse(contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, null).hasFrozenUpdateAmounts());
        assertFalse(contexts.freeze(store, "GRAB", order.id, 21L).hasFrozenUpdateAmounts());
        store.code = "LEGACY";
        assertNull(contexts.freeze(store, "FRONTDESK_RECEIPT", order.id, 21L));
    }

    private void arrange(String previous, String added) {
        lines = List.of(line(1L, previous, null), line(2L, added, 21L));
        lines.get(0).item_name_snapshot_en = "Previous item";
        persistTotals();
    }

    private void persistTotals() {
        order.subtotal_amount = lines.stream().filter(line -> !"cancelled".equals(line.status))
            .map(line -> line.line_amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        order.total_amount = TaxCalculator.calculateTotal(order.subtotal_amount);
        when(items.findAllByOrderId(order.id)).thenAnswer(invocation -> List.copyOf(lines));
    }

    private OrderItem line(long id, String amount, Long batch) {
        OrderItem line = new OrderItem();
        line.id = id;
        line.order_id = order.id;
        line.order_update_batch_id = batch;
        line.item_name_snapshot_en = "Synthetic item " + id;
        line.item_sku_snapshot = "SYNTHETIC_" + id;
        line.line_amount = new BigDecimal(amount);
        line.quantity = 1;
        return line;
    }

    private String render(ReceiptPresentation context) {
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = order;
        request.store = store;
        request.order_items = lines;
        request.order_item_options = List.of();
        request.is_update_ticket = true;
        request.receipt_context = context;
        return renderer.render(request).replaceAll("\\[\\[/?[^]]+]]", "");
    }
}
