package com.restaurant.system.menu.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.platform.service.impl.PlatformAdminServiceImpl;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = MenuRevisionLockIntegrationTest.JpaSliceConfiguration.class)
@Import({MenuRevisionServiceImpl.class, PlatformAdminServiceImpl.class})
class MenuRevisionLockIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired private MenuRevisionService menuRevisionService;
    @Autowired private PlatformAdminServiceImpl platformAdminService;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reverseStoreRequestsCompleteWithoutDeadlock() throws Exception {
        Store first = createStore("LOCK_FIRST");
        Store second = createStore("LOCK_SECOND");
        CountDownLatch start = new CountDownLatch(1);

        Future<?> forward = executor.submit(() -> incrementAfter(start, List.of(first.id, second.id)));
        Future<?> reverse = executor.submit(() -> incrementAfter(start, List.of(second.id, first.id)));
        start.countDown();

        assertThatCode(() -> forward.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThatCode(() -> reverse.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThat(storeRepository.findById(first.id).orElseThrow().menu_revision).isEqualTo(3L);
        assertThat(storeRepository.findById(second.id).orElseThrow().menu_revision).isEqualTo(3L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sourceStoreLockBlocksStationMutationUntilSnapshotTransactionCompletes() throws Exception {
        Store source = createStore("LOCK_SOURCE");
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);

        Future<?> snapshot = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            menuRevisionService.lockStoresInOrder(List.of(source.id));
            lockAcquired.countDown();
            await(releaseLock);
        }));
        assertThat(lockAcquired.await(2, TimeUnit.SECONDS)).isTrue();

        Future<?> mutation = executor.submit(() -> {
            mutationStarted.countDown();
            Station station = new Station();
            station.store_id = source.id;
            station.code = "HOT";
            station.name = "Hot Kitchen";
            platformAdminService.saveStation(station);
        });
        assertThat(mutationStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThrows(TimeoutException.class, () -> mutation.get(250, TimeUnit.MILLISECONDS));

        releaseLock.countDown();
        snapshot.get(5, TimeUnit.SECONDS);
        mutation.get(5, TimeUnit.SECONDS);

        assertThat(stationRepository.findAll()).hasSize(1);
        Store updated = storeRepository.findById(source.id).orElseThrow();
        assertThat(updated.menu_revision).isEqualTo(2L);
        assertThat(updated.menu_updated_at).isAfterOrEqualTo(source.menu_updated_at);
    }

    private void incrementAfter(CountDownLatch start, List<Long> storeIds) {
        await(start);
        new TransactionTemplate(transactionManager).executeWithoutResult(
            status -> menuRevisionService.incrementRevisionsInOrder(storeIds)
        );
    }

    private Store createStore(String code) {
        Store store = new Store();
        store.organization_id = 1L;
        store.code = code;
        store.name = code;
        store.status = "active";
        store.printing_enabled = false;
        store.printing_mode = "DISABLED";
        store.menu_revision = 1L;
        store.menu_updated_at = LocalDateTime.now();
        store.created_at = store.menu_updated_at;
        store.updated_at = store.menu_updated_at;
        return storeRepository.saveAndFlush(store);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for synthetic concurrency gate");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for synthetic concurrency gate", exception);
        }
    }
}
