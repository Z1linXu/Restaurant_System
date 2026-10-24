package com.restaurant.system.station.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.restaurant.system.order.entity.Order;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.repository.DiningTableRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiningTableDisplayServiceTest {
    private final DiningTableRepository tables = mock(DiningTableRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final DiningTableDisplayService service = new DiningTableDisplayService(tables, entityManager);

    @Test
    void modesAreScopedByExactStoreAndTableNameInsteadOfRepeatedCodes() {
        when(tables.findAllByStoreIdAndTableName(1L, "10")).thenReturn(List.of(table(1L, "10", "T12", null)));
        when(tables.findAllByStoreIdAndTableName(1L, "11")).thenReturn(List.of(table(2L, "11", "T12", "INSIDE_OUTSIDE")));
        when(tables.findAllByStoreIdAndTableName(2L, "11")).thenReturn(List.of(table(3L, "11", "T12", "LEFT_RIGHT")));
        assertThat(service.displayName(1L, "10-A")).isEqualTo("10-左");
        assertThat(service.displayName(1L, "10-B")).isEqualTo("10-右");
        assertThat(service.displayName(1L, "11-A")).isEqualTo("11里");
        assertThat(service.displayName(1L, "11-B")).isEqualTo("11外");
        assertThat(service.displayName(2L, "11-A")).isEqualTo("11-左");
        verify(tables, never()).findAllByStoreIdAndTableCode(any(), anyString());
    }

    @Test
    void literalNamesAndOnlyTerminalSeatIdentityArePreserved() {
        when(tables.findAllByStoreIdAndTableName(1L, "1-A")).thenReturn(List.of(table(1L, "1-A", "T1", "INSIDE_OUTSIDE")));
        when(tables.findAllByStoreIdAndTableName(1L, "1里")).thenReturn(List.of(table(2L, "1里", "T2", "LEFT_RIGHT")));
        when(tables.findAllByStoreIdAndTableName(1L, "13外")).thenReturn(List.of(table(3L, "13外", "T3", "LEFT_RIGHT")));
        when(tables.findAllByStoreIdAndTableName(1L, "Zone-A-1")).thenReturn(List.of(table(4L, "Zone-A-1", "T4", "INSIDE_OUTSIDE")));
        assertThat(service.displayName(1L, "1-A")).isEqualTo("1-A");
        assertThat(service.displayName(1L, "1里")).isEqualTo("1里");
        assertThat(service.displayName(1L, "13外")).isEqualTo("13外");
        assertThat(service.displayName(1L, "Zone-A-1-B")).isEqualTo("Zone-A-1外");
        assertThat(service.displayName(1L, "1-左")).isEqualTo("1-左");
        assertThat(service.displayName(1L, "1-A-extra")).isEqualTo("1-A-extra");
    }

    @Test
    void legacyAndAmbiguousContextsUseDefaultDisplayWithoutGuessingAnotherTablesMode() {
        when(tables.findAllByStoreIdAndTableName(1L, "1")).thenReturn(List.of(
            table(1L, "1", "T1", "INSIDE_OUTSIDE"), table(2L, "1", "T2", "LEFT_RIGHT")));
        assertThat(service.displayName(1L, "1-A")).isEqualTo("1-左");
        assertThat(service.displayName(1L, "T3-A")).isEqualTo("T3-左");
        assertThat(service.displayName(1L, "T3")).isEqualTo("T3");
        assertThat(service.displayName(1L, null)).isNull();
        assertThat(service.displayName(1L, " ")).isEqualTo(" ");
    }

    @Test
    void currentProjectionDoesNotMutateOrderIdentityOrRequireAnOrderColumn() throws Exception {
        var row = table(1L, "1", "T1", "INSIDE_OUTSIDE");
        when(tables.findAllByStoreIdAndTableName(1L, "1")).thenReturn(List.of(row));
        var order = new Order(); order.store_id = 1L; order.order_type = "dine_in"; order.table_no = "1-B";
        service.apply(order);
        assertThat(order.table_no).isEqualTo("1-B");
        assertThat(order.table_display_name).isEqualTo("1外");
        row.split_label_mode = "LEFT_RIGHT";
        service.apply(order);
        assertThat(order.table_display_name).isEqualTo("1-右");
        assertThat(Order.class.getField("table_display_name").isAnnotationPresent(jakarta.persistence.Transient.class)).isTrue();
    }

    @Test
    void oldAndNewContextsAreLockedOnceInIdOrderAndRefreshedUnderLock() {
        var old = table(8L, "Old", "T8", "LEFT_RIGHT");
        var next = table(2L, "Next", "T2", "INSIDE_OUTSIDE");
        when(tables.findAllByStoreIdAndTableName(1L, "Old")).thenReturn(List.of(old));
        when(tables.findAllByStoreIdAndTableName(1L, "Next")).thenReturn(List.of(next));
        when(tables.findByIdAndStoreIdForUpdate(2L, 1L)).thenReturn(next);
        when(tables.findByIdAndStoreIdForUpdate(8L, 1L)).thenReturn(old);
        service.lockContexts(1L, "Old-A", "Next-B", "Old-B");
        var order = inOrder(tables, entityManager);
        order.verify(tables).findByIdAndStoreIdForUpdate(2L, 1L);
        order.verify(entityManager).refresh(next, LockModeType.PESSIMISTIC_WRITE);
        order.verify(tables).findByIdAndStoreIdForUpdate(8L, 1L);
        order.verify(entityManager).refresh(old, LockModeType.PESSIMISTIC_WRITE);
        verify(tables, times(1)).findByIdAndStoreIdForUpdate(8L, 1L);
    }

    @Test
    void ambiguousNamedContextIsRejectedBeforeMutation() {
        when(tables.findAllByStoreIdAndTableName(1L, "1")).thenReturn(List.of(
            table(1L, "1", "T1", null), table(2L, "1", "T2", null)));
        assertThatThrownBy(() -> service.lockContexts(1L, "1-A")).hasMessageContaining("AMBIGUOUS_TABLE_CONTEXT");
        verify(tables, never()).findByIdAndStoreIdForUpdate(any(), any());
    }

    @Test
    void legacyCodeConservativelyLocksAllMatchesWithoutChangingItsDisplayMeaning() {
        var ten = table(1L, "10", "T12", "INSIDE_OUTSIDE");
        var eleven = table(2L, "11", "T12", "LEFT_RIGHT");
        when(tables.findAllByStoreIdAndTableCode(1L, "T12")).thenReturn(List.of(eleven, ten));
        when(tables.findByIdAndStoreIdForUpdate(1L, 1L)).thenReturn(ten);
        when(tables.findByIdAndStoreIdForUpdate(2L, 1L)).thenReturn(eleven);
        service.lockContexts(1L, "T12-A");
        var order = inOrder(tables);
        order.verify(tables).findByIdAndStoreIdForUpdate(1L, 1L);
        order.verify(tables).findByIdAndStoreIdForUpdate(2L, 1L);
        assertThat(service.displayName(1L, "T12-A")).isEqualTo("T12-左");
    }

    @Test
    void concurrentRenameIsDetectedAfterLockRefresh() {
        var row = table(1L, "1", "T1", null);
        when(tables.findAllByStoreIdAndTableName(1L, "1")).thenReturn(List.of(row));
        when(tables.findByIdAndStoreIdForUpdate(1L, 1L)).thenReturn(row);
        doAnswer(invocation -> { row.table_name = "renamed"; return null; }).when(entityManager).refresh(row, LockModeType.PESSIMISTIC_WRITE);
        assertThatThrownBy(() -> service.lockContexts(1L, "1-A")).hasMessageContaining("TABLE_CONTEXT_CHANGED");
    }

    @Test
    void concurrentDeletionIsDetectedAfterLockRefreshWhileHistoryStillDisplaysItsMode() {
        var row = table(1L, "1", "T1", "INSIDE_OUTSIDE"); row.is_active = true;
        when(tables.findAllByStoreIdAndTableName(1L, "1")).thenReturn(List.of(row));
        when(tables.findByIdAndStoreIdForUpdate(1L, 1L)).thenReturn(row);
        doAnswer(invocation -> { row.is_active = false; return null; }).when(entityManager).refresh(row, LockModeType.PESSIMISTIC_WRITE);
        assertThatThrownBy(() -> service.lockContexts(1L, "1-A")).hasMessageContaining("TABLE_INACTIVE");
        assertThat(service.displayName(1L, "1-A")).isEqualTo("1里");
    }

    @Test
    void lowerCaseSeatsLockTheOriginalCaseBaseAndDeletedRowsAreNotUnknownLegacyContexts() {
        var row = table(1L, "MixedCase", "T1", "INSIDE_OUTSIDE"); row.is_active = false;
        when(tables.findAllByStoreIdAndTableName(1L, "MixedCase")).thenReturn(List.of(row));
        when(tables.findByIdAndStoreIdForUpdate(1L, 1L)).thenReturn(row);
        assertThatThrownBy(() -> service.lockContexts(1L, " MixedCase-a ")).hasMessageContaining("TABLE_INACTIVE");
        assertThatThrownBy(() -> service.lockContexts(1L, "MixedCase-b")).hasMessageContaining("TABLE_INACTIVE");
        verify(tables, never()).findAllByStoreIdAndTableName(1L, "MIXEDCASE");
    }

    @Test
    void exactLiteralLowerCaseSeatNameWinsOverAnInactiveBaseTable() {
        var literal = table(2L, "1-a", "LITERAL", "LEFT_RIGHT"); literal.is_active = true;
        when(tables.findAllByStoreIdAndTableName(1L, "1-a")).thenReturn(List.of(literal));
        when(tables.findByIdAndStoreIdForUpdate(2L, 1L)).thenReturn(literal);
        service.lockContexts(1L, "1-a");
        verify(tables, never()).findAllByStoreIdAndTableName(1L, "1");
        assertThat(service.displayName(1L, "1-a")).isEqualTo("1-a");
    }

    private DiningTable table(Long id, String name, String code, String mode) {
        var table = new DiningTable(); table.id = id; table.store_id = 1L;
        table.table_name = name; table.table_code = code; table.split_label_mode = mode;
        return table;
    }
}
