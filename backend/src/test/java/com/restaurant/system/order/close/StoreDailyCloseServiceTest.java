package com.restaurant.system.order.close;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.entity.AuditLog;
import com.restaurant.system.audit.repository.AuditLogRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StoreDailyCloseServiceTest {
    StoreRepository stores = mock(StoreRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    StoreDailyCloseRunRepository runs = mock(StoreDailyCloseRunRepository.class);
    OrderService finish = mock(OrderService.class);
    AuditLogRepository audits = mock(AuditLogRepository.class);
    StoreDailyCloseService service = new StoreDailyCloseService(stores, orders, runs, finish, audits, new ObjectMapper(), "America/Toronto");
    Store store;
    Instant closing = Instant.parse("2026-10-03T02:30:00Z");

    @BeforeEach void setup() {
        store = new Store(); store.id = 1L; store.status = "active";
        when(stores.findById(1L)).thenReturn(Optional.of(store));
        when(runs.reserve(anyLong(), any(), anyString(), any())).thenReturn(true);
        when(orders.findDailyCloseCandidateIds(anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test void at2229NothingIsReservedOrFinished() {
        assertThat(service.closeStore(1L, closing.minusSeconds(60))).isZero();
        verifyNoInteractions(runs, orders, finish, audits);
    }

    @Test void at2230UsesStoreDayAndRequiredAuditWithNoPayment() throws Exception {
        Order order = order(2L); candidates(order);
        assertThat(service.closeStore(1L, closing)).isEqualTo(1);
        verify(finish).completeOrder(2L);
        verify(runs).reserve(1L, LocalDate.of(2026, 10, 2), "America/Toronto", closing);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().action).isEqualTo("AUTO_FINISHED_END_OF_DAY");
        assertThat(audit.getValue().store_id).isEqualTo(1L);
        assertThat(audit.getValue().entity_id).isEqualTo(2L);
        var metadata = new ObjectMapper().readTree(audit.getValue().metadata_json);
        assertThat(metadata.path("table_no").asText()).isEqualTo("T1-A");
        assertThat(metadata.path("reason").asText()).isEqualTo("DAILY_22_30_AUTO_FINISH");
        assertThat(metadata.path("executed_at").asText()).isEqualTo(closing.toString());
        assertThat(audit.getValue().metadata_json).doesNotContain("payment");
    }

    @Test void existingDurableLedgerPreventsAnotherRunAfterRestart() {
        when(runs.reserve(anyLong(), any(), anyString(), any())).thenReturn(false);
        StoreDailyCloseService restarted = new StoreDailyCloseService(stores, orders, runs, finish, audits, new ObjectMapper(), "America/Toronto");
        assertThat(restarted.closeStore(1L, closing.plusSeconds(300))).isZero();
        verifyNoInteractions(orders, finish, audits);
    }

    @Test void first2235RunCatchesUpOnlyTodaysBusinessDate() {
        Order order = order(2L); candidates(order);
        service.closeStore(1L, closing.minusSeconds(60));
        StoreDailyCloseService restarted = new StoreDailyCloseService(stores, orders, runs, finish, audits, new ObjectMapper(), "America/Toronto");
        assertThat(restarted.closeStore(1L, closing.plusSeconds(300))).isEqualTo(1);
        verify(runs).reserve(1L, LocalDate.of(2026, 10, 2), "America/Toronto", closing.plusSeconds(300));
        assertThat(service.closeStore(1L, Instant.parse("2026-10-03T04:10:00Z"))).isZero();
        verify(finish, times(1)).completeOrder(2L);
    }

    @Test void timezoneComesFromStoreIncludingItsDifferentDateAndDst() {
        store.timezone = "America/Vancouver";
        assertThat(service.closeStore(1L, closing)).isZero();
        Instant westClosing = Instant.parse("2026-10-03T05:30:00Z");
        service.closeStore(1L, westClosing);
        verify(runs).reserve(1L, LocalDate.of(2026, 10, 2), "America/Vancouver", westClosing);
        verify(orders).findDailyCloseCandidateIds(1L,
            LocalDateTime.ofInstant(Instant.parse("2026-10-02T07:00:00Z"), ZoneId.systemDefault()),
            LocalDateTime.ofInstant(Instant.parse("2026-10-03T07:00:00Z"), ZoneId.systemDefault()));
        // Toronto's fall-back day has 25 hours, not a fixed 24-hour query window.
        store.timezone = "America/Toronto";
        service.closeStore(1L, Instant.parse("2026-11-02T03:30:00Z"));
        verify(orders).findDailyCloseCandidateIds(1L,
            LocalDateTime.ofInstant(Instant.parse("2026-11-01T04:00:00Z"), ZoneId.systemDefault()),
            LocalDateTime.ofInstant(Instant.parse("2026-11-02T05:00:00Z"), ZoneId.systemDefault()));
    }

    @Test void rechecksLockedOrdersAndIgnoresHistoryDraftUberTakeoutCancelledAndOtherStore() {
        List<Order> ineligible = new ArrayList<>();
        Order completed = order(2L); completed.status = "completed"; ineligible.add(completed);
        Order draft = order(3L); draft.status = "draft"; draft.submitted_at = null; ineligible.add(draft);
        Order uber = order(4L); uber.financial_mode = "EXTERNAL_PLATFORM"; ineligible.add(uber);
        Order uberSource = order(5L); uberSource.external_source = "UBER_EATS"; ineligible.add(uberSource);
        Order takeout = order(6L); takeout.order_type = "takeout"; ineligible.add(takeout);
        Order cancelled = order(7L); cancelled.status = "cancelled"; ineligible.add(cancelled);
        Order history = order(8L); history.submitted_at = history.submitted_at.minusDays(1); ineligible.add(history);
        Order otherStore = order(9L); otherStore.store_id = 2L; ineligible.add(otherStore);
        Order noTable = order(10L); noTable.table_no = " "; ineligible.add(noTable);
        Order staleCompletion = order(11L); staleCompletion.completed_at = staleCompletion.submitted_at; ineligible.add(staleCompletion);
        when(orders.findDailyCloseCandidateIds(anyLong(), any(), any())).thenReturn(ineligible.stream().map(o -> o.id).toList());
        for (Order order : ineligible) when(orders.findByIdForUpdate(order.id)).thenReturn(order);
        assertThat(service.closeStore(1L, closing)).isZero();
        verifyNoInteractions(finish, audits);
    }

    @Test void historicalDraftCreatedDateDoesNotOverrideTodaysFirstSubmission() {
        Order order = order(2L);
        order.created_at = order.submitted_at.minusDays(3);
        candidates(order);
        assertThat(service.closeStore(1L, closing)).isEqualTo(1);
        verify(finish).completeOrder(2L);
    }

    @Test void inactiveOrMasterStoreDoesNotClose() {
        store.lifecycle_status = "DRAFT";
        service.closeStore(1L, closing);
        store.lifecycle_status = "ACTIVE"; store.store_kind = "MASTER_TEMPLATE";
        service.closeStore(1L, closing);
        verifyNoInteractions(runs, orders, finish, audits);
    }

    @Test void invalidConfiguredTimezoneFailsBeforeAnyMutation() {
        store.timezone = "not-a-zone";
        assertThatThrownBy(() -> service.closeStore(1L, closing)).isInstanceOf(DateTimeException.class);
        verifyNoInteractions(runs, orders, finish, audits);
    }

    private Order order(Long id) {
        Order o = new Order(); o.id = id; o.store_id = 1L; o.order_type = "dine_in";
        o.status = "submitted"; o.table_no = "T1-A";
        o.submitted_at = LocalDateTime.ofInstant(Instant.parse("2026-10-02T22:15:00Z"), ZoneId.systemDefault());
        return o;
    }
    private void candidates(Order order) {
        when(orders.findDailyCloseCandidateIds(anyLong(), any(), any())).thenReturn(List.of(order.id));
        when(orders.findByIdForUpdate(order.id)).thenReturn(order);
    }
}
