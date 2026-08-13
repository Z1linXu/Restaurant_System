package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileRegistry;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservation;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservationCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.platform.repository.OwnerStoreMenuCloneRequestRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "AL003_POSTGRES_URL", matches = "jdbc:postgresql:.*")
@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.validate-on-migrate=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = OwnerStoreMenuClonePostgresIntegrationTest.JpaSliceConfiguration.class)
@Import({
    OwnerStoreMenuCloneRequestCoordinatorImpl.class,
    OwnerStoreMenuCloneFingerprint.class,
    StoreMenuCloneProfileRegistry.class,
    ChinatownMenuCloneProfile.class
})
class OwnerStoreMenuClonePostgresIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironment("AL003_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironment("AL003_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("AL003_POSTGRES_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private OwnerStoreMenuCloneRequestCoordinatorImpl coordinator;
    @Autowired
    private OwnerStoreMenuCloneRequestRepository requestRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        requestRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void v1ThroughCurrentFlywayAndExpectedDatabaseObjectsArePresent() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version::integer between 1 and 12 and success",
            Integer.class
        );
        Integer v12Rows = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '12' and success",
            Integer.class
        );
        String constraintDefinition = jdbcTemplate.queryForObject(
            """
            select pg_get_constraintdef(oid)
            from pg_constraint
            where conname = 'uq_owner_store_menu_clone_scope_key'
            """,
            String.class
        );
        String indexDefinition = jdbcTemplate.queryForObject(
            """
            select indexdef
            from pg_indexes
            where schemaname = 'public'
              and indexname = 'idx_owner_store_menu_clone_target_store'
            """,
            String.class
        );
        String storeComboConstraintDefinition = jdbcTemplate.queryForObject(
            """
            select pg_get_constraintdef(oid)
            from pg_constraint
            where conname = 'uq_store_combo_components_store_group_code'
            """,
            String.class
        );

        assertThat(successfulMigrations).isEqualTo(12);
        assertThat(v12Rows).isOne();
        assertThat(constraintDefinition)
            .contains("organization_id", "source_store_id", "target_store_id", "idempotency_key");
        assertThat(indexDefinition).contains("(target_store_id)");
        assertThat(storeComboConstraintDefinition).contains("store_id", "component_group", "component_code");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void completedRequestReplaysAndDifferentFingerprintConflicts() {
        String key = "pg-replay-" + UUID.randomUUID();
        OwnerStoreMenuCloneReservationCommand command = command(key);
        OwnerStoreMenuCloneReservation first = coordinator.reserve(command);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> coordinator.complete(
            new OwnerStoreMenuCloneSuccessEvidence(
                first.requestId(),
                10L,
                ChinatownMenuCloneProfile.SOURCE_STORE_ID,
                20L,
                ChinatownMenuCloneProfile.PROFILE_CODE,
                5L,
                1L,
                2L,
                3,
                4,
                17,
                100,
                "MENU_CLONE_COMPLETED"
            )
        ));

        OwnerStoreMenuCloneReservation replay = coordinator.reserve(command);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.requestId()).isEqualTo(first.requestId());

        jdbcTemplate.update(
            "update owner_store_menu_clone_requests set request_fingerprint = ? where id = ?",
            "f".repeat(64),
            first.requestId()
        );
        Object conflict = capture(() -> coordinator.reserve(command));
        assertThat(conflict).isInstanceOfSatisfying(
            OwnerStoreMenuCloneException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT")
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReservationCreatesOneRowAndReturnsOneInProgressConflict() throws Exception {
        String key = "pg-concurrent-" + UUID.randomUUID();
        OwnerStoreMenuCloneReservationCommand command = command(key);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return capture(() -> coordinator.reserve(command));
                }));
            }
            ready.await();
            start.countDown();

            List<Object> results = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }).toList();

            assertThat(results).filteredOn(OwnerStoreMenuCloneReservation.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(OwnerStoreMenuCloneException.class::isInstance)
                .singleElement()
                .satisfies(result -> assertThat(((OwnerStoreMenuCloneException) result).getErrorCode())
                    .isEqualTo("MENU_CLONE_IN_PROGRESS"));
            assertThat(requestRepository.count()).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    private OwnerStoreMenuCloneReservationCommand command(String key) {
        return new OwnerStoreMenuCloneReservationCommand(
            10L,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            20L,
            key,
            ChinatownMenuCloneProfile.PROFILE_CODE,
            30L
        );
    }

    private Object capture(CheckedSupplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the explicit PostgreSQL integration test");
        }
        return value;
    }

    @FunctionalInterface
    private interface CheckedSupplier {
        Object get();
    }
}
