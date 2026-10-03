package com.restaurant.system.printing;

import static org.assertj.core.api.Assertions.*;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ContextConfiguration(classes = PrintingReliabilityRepositoryTest.Config.class)
class PrintingReliabilityRepositoryTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan("com.restaurant.system")
    @EnableJpaRepositories("com.restaurant.system")
    static class Config {}
    @Autowired PrintJobRepository jobs;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void realTransportStartsAfterReservationCommitAndFailedCompletionCannotResend() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var store = tx.execute(s -> {
            var row = new com.restaurant.system.user.entity.Store(); row.organization_id = 1L;
            row.name = "Synthetic"; row.code = "TEST_REAL_RESERVATION"; em.persist(row); return row;
        });
        var service = org.mockito.Mockito.mock(com.restaurant.system.printing.service.PrintJobService.class);
        var dispatcher = org.mockito.Mockito.mock(com.restaurant.system.printing.service.PrintDispatcherService.class);
        var source = new PrintJob(); source.id = 777L; source.store_id = store.id; source.order_id = 999L; source.module_code = "GRAB";
        org.mockito.Mockito.when(service.requireJob(777L)).thenReturn(source);
        org.mockito.Mockito.when(service.toResponse(org.mockito.ArgumentMatchers.any())).thenAnswer(i ->
            com.restaurant.system.printing.dto.PrintJobResponse.from(i.getArgument(0), null, null));
        var manual = new com.restaurant.system.printing.service.ManualReprintService(jobs,
            org.mockito.Mockito.mock(com.restaurant.system.order.repository.OrderRepository.class), service, dispatcher, em, transactions, org.mockito.Mockito.mock(com.restaurant.system.modules.StoreModuleAccessEvaluator.class));
        var physicalSends = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(dispatcher.reprintJob(777L, 1L)).thenAnswer(invocation -> {
            var intent = com.restaurant.system.printing.service.ManualReprintService.currentIntent();
            var reservation = new PrintJob(); reservation.store_id = store.id; reservation.order_id = 999L;
            reservation.module_code = "GRAB"; reservation.status = "PRINTING";
            reservation.dispatchSourceKey = intent.sourceKey(); reservation.manualRequestHash = intent.hash();
            jobs.saveAndFlush(reservation);
            com.restaurant.system.printing.service.ManualReprintService.deferRealTransport(() -> {
                // A new transaction observes committed PRINTING intent BEFORE fake transport.
                assertThat(jobs.findByDispatchSourceKey(intent.sourceKey())).isPresent();
                physicalSends.incrementAndGet();
                throw new IllegalStateException("synthetic completion/commit failure after output");
            });
            assertThat(physicalSends).hasValue(0);
            return com.restaurant.system.printing.dto.PrintJobResponse.from(reservation, null, null);
        });
        var request = new com.restaurant.system.printing.dto.ManualReprintRequest(); request.idempotency_key = "real-commit-test";
        // Failed reservation transaction must never execute transport.
        assertThatThrownBy(() -> tx.execute(s -> { manual.reprintJob(777L, request, 1L); throw new IllegalStateException("before commit"); }));
        assertThat(physicalSends).hasValue(0);
        assertThatThrownBy(() -> tx.execute(s -> manual.reprintJob(777L, request, 1L))).hasMessageContaining("after output");
        assertThat(physicalSends).hasValue(1);
        var replay = tx.execute(s -> manual.reprintJob(777L, request, 1L));
        assertThat(replay.status).isEqualTo("PRINTING"); assertThat(physicalSends).hasValue(1);
        tx.executeWithoutResult(s -> { jobs.deleteById(replay.id); em.remove(em.find(com.restaurant.system.user.entity.Store.class, store.id)); });
    }

    private PrintJob job(long device) {
        PrintJob job = new PrintJob(); job.store_id = 1L; job.organization_id = 1L; job.order_id = 1L;
        job.module_code = "GRAB"; job.status = "PENDING"; job.executionMode = "PAD_DIRECT";
        job.created_at = LocalDateTime.now(); job.preferredDeviceId = device;
        job.preferredDeviceUntil = LocalDateTime.now().plusSeconds(10);
        return jobs.saveAndFlush(job);
    }
    @Test void preferenceFiltersBeforePaginationAndIsEnforcedAgainAtClaim() {
        PrintJob a = job(10); PrintJob b = job(20); var now = LocalDateTime.now();
        assertThat(jobs.findPendingPadDirectJobs(1L, 20L, now, PageRequest.of(0, 1))).extracting(x -> x.id).containsExactly(b.id);
        assertThat(jobs.claimPadDirectJob(a.id, 1L, 20L, "b", now, now.plusSeconds(30))).isZero();
        assertThat(jobs.claimPadDirectJob(a.id, 2L, 10L, "cross", now, now.plusSeconds(30))).isZero();
        assertThat(jobs.claimPadDirectJob(a.id, 1L, 10L, "a", now, now.plusSeconds(30))).isOne();
        assertThat(jobs.claimPadDirectJob(a.id, 1L, 20L, "b", now.plusSeconds(11), now.plusSeconds(41))).isZero();
    }
    @Test void expiryAllowsFallbackButOldAttemptCannotStartAfterReclaimAndPrintingNeverFailsOver() {
        PrintJob a = job(10); var later = LocalDateTime.now().plusSeconds(11);
        assertThat(jobs.claimPadDirectJob(a.id, 1L, 20L, "b", later, later.plusSeconds(30))).isOne();
        var expired = later.plusSeconds(31);
        assertThat(jobs.startPadPrint(a.id, 1L, 20L, "b", expired, expired.plusSeconds(300))).isZero();
        assertThat(jobs.claimPadDirectJob(a.id, 1L, 30L, "c", expired, expired.plusSeconds(30))).isOne();
        assertThat(jobs.startPadPrint(a.id, 1L, 20L, "b", expired, expired.plusSeconds(300))).isZero();
        assertThat(jobs.startPadPrint(a.id, 1L, 30L, "wrong", expired, expired.plusSeconds(300))).isZero();
        assertThat(jobs.startPadPrint(a.id, 1L, 30L, "c", expired, expired.plusSeconds(300))).isOne();
        assertThat(jobs.claimPadDirectJob(a.id, 1L, 10L, "a", expired.plusHours(1), expired.plusHours(2))).isZero();
        assertThat(jobs.findPendingPadDirectJobs(1L, 10L, expired.plusHours(1), PageRequest.of(0, 10))).isEmpty();
    }
    @Test void legacyNullAffinityRemainsClaimable() {
        PrintJob job = job(10); job.preferredDeviceId = null; job.preferredDeviceUntil = null; jobs.saveAndFlush(job);
        var now = LocalDateTime.now();
        assertThat(jobs.claimPadDirectJob(job.id, 1L, 20L, "legacy", now, now.plusSeconds(30))).isOne();
    }
}
