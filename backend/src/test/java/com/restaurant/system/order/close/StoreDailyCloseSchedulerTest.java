package com.restaurant.system.order.close;

import static org.mockito.Mockito.*;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoreDailyCloseSchedulerTest {
    @Test void failedStoreDoesNotPreventOtherStoreAndEveryTickUsesItsOwnTransaction() {
        StoreRepository stores = mock(StoreRepository.class);
        StoreDailyCloseService service = mock(StoreDailyCloseService.class);
        Store first = new Store(); first.id = 1L;
        Store second = new Store(); second.id = 2L;
        when(stores.findAllByStatusIgnoreCase("active")).thenReturn(List.of(first, second));
        Instant now = Instant.parse("2026-10-03T02:35:00Z");
        when(service.closeStore(1L, now)).thenThrow(new IllegalStateException("fixture failure"));
        new StoreDailyCloseScheduler(stores, service).closeDueStores(now);
        verify(service).closeStore(1L, now);
        verify(service).closeStore(2L, now);
    }
}
