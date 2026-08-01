package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuRevisionServiceImplTest {

    @Mock
    private StoreRepository storeRepository;

    private MenuRevisionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MenuRevisionServiceImpl(storeRepository);
    }

    @Test
    void returnsStoreScopedRevisionAndStableEtag() {
        Store store = new Store();
        store.id = 4L;
        store.organization_id = 8L;
        store.menu_revision = 12L;
        store.menu_updated_at = LocalDateTime.of(2026, 7, 13, 9, 30);
        when(storeRepository.findById(4L)).thenReturn(Optional.of(store));

        var response = service.getRevision(4L);

        assertEquals(4L, response.store_id);
        assertEquals(8L, response.organization_id);
        assertEquals(12L, response.menu_revision);
        assertEquals("menu-4-12-menu-catalog-v3-ca-qc-tax-2026-01", response.etag);
    }

    @Test
    void incrementsRevisionAtomically() {
        Store store = store(4L);
        when(storeRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(4L))).thenReturn(List.of(store));
        when(storeRepository.incrementMenuRevision(4L)).thenReturn(1);

        service.incrementRevision(4L);

        verify(storeRepository).incrementMenuRevision(4L);
    }

    @Test
    void rejectsMissingStoreDuringIncrement() {
        when(storeRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(404L))).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.incrementRevision(404L));
    }

    @Test
    void locksAndIncrementsMultipleStoresInAscendingOrder() {
        Store first = store(2L);
        Store second = store(9L);
        when(storeRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(2L, 9L)))
            .thenReturn(List.of(first, second));
        when(storeRepository.incrementMenuRevision(2L)).thenReturn(1);
        when(storeRepository.incrementMenuRevision(9L)).thenReturn(1);

        service.incrementRevisionsInOrder(List.of(9L, 2L, 9L));

        var inOrder = org.mockito.Mockito.inOrder(storeRepository);
        inOrder.verify(storeRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(2L, 9L));
        inOrder.verify(storeRepository).incrementMenuRevision(2L);
        inOrder.verify(storeRepository).incrementMenuRevision(9L);
    }

    private Store store(Long id) {
        Store store = new Store();
        store.id = id;
        return store;
    }
}
