package com.restaurant.system.printing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.printing.service.*;
import com.restaurant.system.printing.dto.*;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class ManualReprintServiceTest {
    final PrintJobRepository jobs = mock(PrintJobRepository.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final PrintJobService jobService = mock(PrintJobService.class);
    final PrintDispatcherService dispatcher = mock(PrintDispatcherService.class);
    final EntityManager em = mock(EntityManager.class);
    final ManualReprintService service = new ManualReprintService(jobs, orders, jobService, dispatcher, em,
        mock(org.springframework.transaction.PlatformTransactionManager.class));
    final PrintJob original = new PrintJob();
    final Map<String, PrintJob> durable = new HashMap<>();
    ManualReprintServiceTest() {
        original.id = 7L; original.store_id = 1L; original.order_id = 4L; original.module_code = "GRAB";
        original.status = "PRINTED"; original.rendered_text_snapshot = "FROZEN";
        Store store = new Store(); store.id = 1L; store.organization_id = 2L;
        when(em.find(Store.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(store);
        when(jobService.requireJob(7L)).thenReturn(original);
        when(jobs.findByDispatchSourceKey(anyString())).thenAnswer(i -> Optional.ofNullable(durable.get(i.getArgument(0))));
        when(jobService.toResponse(any())).thenAnswer(i -> PrintJobResponse.from(i.getArgument(0), null, null));
        when(dispatcher.reprintJob(7L, 3L)).thenAnswer(i -> {
            var intent = ManualReprintService.currentIntent();
            PrintJob job = new PrintJob(); job.id = 100L + durable.size(); job.status = "PENDING";
            job.manualRequestHash = intent.hash(); durable.put(intent.sourceKey(), job);
            return PrintJobResponse.from(job, null, null);
        });
    }
    ManualReprintRequest request(String key) { var r = new ManualReprintRequest(); r.idempotency_key = key; return r; }
    @Test void lostResponseReplayReturnsSameNewJobAndChangedRequestConflicts() {
        var request = request("manual-key-1");
        var first = service.reprintJob(7L, request, 3L);
        assertThat(service.reprintJob(7L, request, 3L).id).isEqualTo(first.id).isNotEqualTo(7L);
        assertThatThrownBy(() -> service.reprintJob(7L, request, 99L)).hasMessageContaining("identity conflict");
        verify(dispatcher, times(1)).reprintJob(7L, 3L);
        assertThat(original.status).isEqualTo("PRINTED"); assertThat(original.rendered_text_snapshot).isEqualTo("FROZEN");
        assertThat(ManualReprintService.currentIntent()).isNull();
    }
    @Test void activeConfirmationRechecksOwnershipAndSecondExplicitIntentCreatesNewJob() {
        PrintJob active = new PrintJob(); active.id = 9L; active.status = "PRINTING"; active.module_code = "GRAB";
        active.claimedByDeviceId = 20L; active.created_at = LocalDateTime.now().minusSeconds(20);
        active.printingStartedAt = LocalDateTime.now();
        when(jobs.findActiveForReprint(1L, 4L, "GRAB")).thenReturn(List.of(active));
        var request = request("manual-key-2");
        var denied = catchThrowableOfType(() -> service.reprintJob(7L, request, 3L), ManualReprintService.ConfirmationRequired.class);
        assertThat(denied.confirmation.active_jobs().get(0).claimed_device_id()).isEqualTo(20L);
        verifyNoInteractions(dispatcher);
        request.confirmation_fingerprint = denied.confirmation.confirmation_fingerprint();
        active.claimedByDeviceId = 30L;
        var changed = catchThrowableOfType(() -> service.reprintJob(7L, request, 3L), ManualReprintService.ConfirmationRequired.class);
        request.confirmation_fingerprint = changed.confirmation.confirmation_fingerprint();
        long first = service.reprintJob(7L, request, 3L).id;
        request.idempotency_key = "manual-key-3";
        assertThat(service.reprintJob(7L, request, 3L).id).isNotEqualTo(first);
    }
    @Test void invalidIntentDoesNotDispatch() {
        assertThatThrownBy(() -> service.reprintJob(7L, request(""), 3L)).hasMessageContaining("idempotency_key");
        verifyNoInteractions(dispatcher);
    }
}
