package com.restaurant.system.order.close;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.RestaurantSystemApplication;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.printing.service.impl.OrderDispatchOutboxProcessor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "UBER_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(classes = RestaurantSystemApplication.class, properties = {
    "spring.profiles.active=daily-close-test", "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
    "app.seed.runtime-enabled=false", "app.seed.safe-metadata-enabled=false",
    "app.seed.default-users-enabled=false", "app.seed.demo-data-enabled=false",
    "app.seed.membership-supplement-enabled=false", "app.daily-close.enabled=false",
    "UBER_EATS_ENABLED=false"
})
class StoreDailyClosePostgresIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("UBER_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("USER"));
        registry.add("spring.datasource.password", () -> "");
    }
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired com.restaurant.system.order.repository.OrderRepository orderRepository;
    @Autowired JdbcTemplate db;
    @Autowired StoreDailyCloseService close;
    @Autowired OrderService orders;
    @Autowired Flyway flyway;
    @Autowired ObjectMapper json;
    @SpyBean StoreDailyCloseRunRepository runs;
    @MockBean OrderDispatchOutboxProcessor outboxProcessor;
    Long org, store;
    Instant now = Instant.parse("2026-10-03T02:30:00Z");
    LocalDateTime submitted = LocalDateTime.ofInstant(Instant.parse("2026-10-02T22:15:00Z"), ZoneId.systemDefault());

    @BeforeEach void setup() {
        org = db.queryForObject("insert into organizations(name,code,status) values ('Daily close fixture',?,'active') returning id", Long.class, "DCF-" + UUID.randomUUID());
        store = store("America/Toronto");
    }

    @Test void migrationAndRealManualDomainPreserveMoneyAndCloseKitchenWithRequiredAudit() throws Exception {
        assertThat(flyway.info().all()).anySatisfy(m -> assertThat(m.getVersion().getVersion()).isEqualTo("34"));
        Long order = order(store, "dine_in", "submitted", submitted, "IN_STORE");
        Long item = db.queryForObject("""
            insert into order_items(order_id,quantity,status,category_code_snapshot,item_name_snapshot_en,unit_price,line_amount)
            values (?,1,'active','SOUP_NOODLE','Daily close fixture',23.45,23.45) returning id
            """, Long.class, order);
        Long ready = db.queryForObject("insert into kitchen_tasks(store_id,order_id,order_item_id,status,quantity) values (?,?,?,'ready_for_pickup',1) returning id", Long.class, store, order, item);
        Long pending = db.queryForObject("insert into kitchen_tasks(store_id,order_id,order_item_id,status,quantity) values (?,?,?,'pending',1) returning id", Long.class, store, order, item);
        assertThat(close.closeStore(store, now.minusSeconds(60))).isZero();
        assertThat(close.closeStore(store, now)).isEqualTo(1);
        assertThat(status(order)).isEqualTo("completed");
        assertThat(db.queryForObject("select total_amount from orders where id=?", BigDecimal.class, order)).isEqualByComparingTo("23.45");
        assertThat(db.queryForObject("select submitted_at from orders where id=?", LocalDateTime.class, order)).isEqualTo(submitted);
        assertThat(db.queryForObject("select status from kitchen_tasks where id=?", String.class, ready)).isEqualTo("served");
        assertThat(db.queryForObject("select status from kitchen_tasks where id=?", String.class, pending)).isEqualTo("cancelled");
        assertThat(count("store_daily_close_runs", "store_id=? and completed_at is not null", store)).isEqualTo(1);
        String metadata = db.queryForObject("select metadata_json from audit_logs where store_id=? and entity_id=? and action='AUTO_FINISHED_END_OF_DAY'", String.class, store, order);
        var audit = json.readTree(metadata);
        assertThat(audit.path("reason").asText()).isEqualTo("DAILY_22_30_AUTO_FINISH");
        assertThat(audit.path("table_no").asText()).isEqualTo("T1-A");
        assertThat(audit.path("executed_at").asText()).isEqualTo(now.toString());
        assertThat(close.closeStore(store, now.plusSeconds(300))).isZero();
        assertThat(count("audit_logs", "store_id=? and action='AUTO_FINISHED_END_OF_DAY'", store)).isEqualTo(1);
    }

    @Test void candidateQueryExcludesHistoricalDraftCancelledFinishedTakeoutAndUberAndOtherStore() {
        Long eligible = order(store, "dine_in", "preparing", submitted, "IN_STORE");
        List<Long> ignored = new ArrayList<>();
        ignored.add(order(store, "dine_in", "draft", null, "IN_STORE"));
        ignored.add(order(store, "dine_in", "submitted", submitted.minusDays(1), "IN_STORE"));
        ignored.add(order(store, "dine_in", "cancelled", submitted, "IN_STORE"));
        ignored.add(order(store, "dine_in", "completed", submitted, "IN_STORE"));
        ignored.add(order(store, "takeout", "submitted", submitted, "IN_STORE"));
        ignored.add(order(store, "dine_in", "submitted", submitted, "EXTERNAL_PLATFORM"));
        Long externalSource = order(store, "dine_in", "submitted", submitted, "IN_STORE");
        db.update("update orders set external_source='UBER_EATS', external_order_id=? where id=?", UUID.randomUUID().toString(), externalSource);
        ignored.add(externalSource);
        Long otherStore = store("America/Toronto");
        Long otherOrder = order(otherStore, "dine_in", "ready", submitted, "IN_STORE");
        Map<Long, String> before = new HashMap<>(); for (Long id : ignored) before.put(id, status(id));
        assertThat(close.closeStore(store, now)).isEqualTo(1);
        assertThat(status(eligible)).isEqualTo("completed");
        for (Long id : ignored) assertThat(status(id)).isEqualTo(before.get(id));
        assertThat(status(otherOrder)).isEqualTo("ready");
        assertThat(close.closeStore(otherStore, now)).isEqualTo(1);
        assertThat(count("audit_logs", "store_id=? and action='AUTO_FINISHED_END_OF_DAY'", otherStore)).isEqualTo(1);
    }

    @Test void durableReservationSerializesConcurrentWorkersAndLaterRestartRun() throws Exception {
        Long order = order(store, "dine_in", "submitted", submitted, "IN_STORE");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> worker = () -> { start.await(); return close.closeStore(store, now.plusSeconds(300)); };
            Future<Integer> first = pool.submit(worker), second = pool.submit(worker);
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(close.closeStore(store, now.plusSeconds(900))).isZero();
            assertThat(status(order)).isEqualTo("completed");
            assertThat(count("store_daily_close_runs", "store_id=?", store)).isEqualTo(1);
            assertThat(count("audit_logs", "store_id=? and action='AUTO_FINISHED_END_OF_DAY'", store)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void manualFinishRacingAutoCloseCompletesOnlyOnceWithoutChangingMoney() throws Exception {
        Long order = order(store, "dine_in", "submitted", submitted, "IN_STORE");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> manual = pool.submit(() -> {
                try {
                    start.await();
                    orders.completeOrder(order);
                } catch (com.restaurant.system.common.exception.BusinessException alreadyFinished) {
                    assertThat(alreadyFinished.getMessage()).isEqualTo("Order is already completed");
                } catch (InterruptedException interrupted) {
                    throw new IllegalStateException(interrupted);
                }
            });
            Future<Integer> automatic = pool.submit(() -> { start.await(); return close.closeStore(store, now); });
            start.countDown();
            manual.get(15, TimeUnit.SECONDS);
            int automaticallyFinished = automatic.get(15, TimeUnit.SECONDS);
            assertThat(status(order)).isEqualTo("completed");
            assertThat(db.queryForObject("select total_amount from orders where id=?", BigDecimal.class, order)).isEqualByComparingTo("23.45");
            assertThat(count("audit_logs", "store_id=? and action='AUTO_FINISHED_END_OF_DAY'", store)).isEqualTo(automaticallyFinished);
            assertThat(close.closeStore(store, now.plusSeconds(300))).isZero();
        } finally { pool.shutdownNow(); }
    }

    @Test void manualFinishRefreshesEntityPreloadedByAuthorizationBeforeAnotherTransactionClosedIt() throws Exception {
        verifyPreloadedRequestCannotOverwriteDailyClose(false);
    }

    @Test void beverageOnlyCancelRefreshesEntityPreloadedBeforeAnotherTransactionClosedIt() throws Exception {
        verifyPreloadedRequestCannotOverwriteDailyClose(true);
    }

    private void verifyPreloadedRequestCannotOverwriteDailyClose(boolean cancel) throws Exception {
        Long order = order(store, "dine_in", "submitted", submitted, "IN_STORE");
        Long beverage = db.queryForObject("""
            insert into frontdesk_beverage_items(store_id,order_id,status,quantity)
            values (?,?,'pending',1) returning id
            """, Long.class, store, order);
        var requestEntityManager = entityManagerFactory.createEntityManager();
        var holder = new org.springframework.orm.jpa.EntityManagerHolder(requestEntityManager);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // This is the same request-scoped first-level cache used by OSIV when
            // AuthorizationService.requireOrder runs before the domain transaction.
            var preloaded = orderRepository.findExistingById(order);
            assertThat(requestEntityManager.contains(preloaded)).isTrue();
            assertThat(preloaded.status).isEqualTo("submitted");
            assertThat(pool.submit(() -> close.closeStore(store, now)).get(15, TimeUnit.SECONDS)).isEqualTo(1);
            LocalDateTime completedAt = db.queryForObject("select completed_at from orders where id=?", LocalDateTime.class, order);
            assertThat(preloaded.status).isEqualTo("submitted"); // deliberately stale
            assertThatThrownBy(() -> {
                if (cancel) orders.cancelOrder(order); else orders.completeOrder(order);
            }).isInstanceOf(com.restaurant.system.common.exception.BusinessException.class)
                .hasMessage(cancel ? "Completed orders cannot be cancelled" : "Order is already completed");
            assertThat(status(order)).isEqualTo("completed");
            assertThat(db.queryForObject("select completed_at from orders where id=?", LocalDateTime.class, order)).isEqualTo(completedAt);
            assertThat(db.queryForObject("select status from frontdesk_beverage_items where id=?", String.class, beverage)).isEqualTo("cancelled");
            assertThat(count("audit_logs", "store_id=? and action='AUTO_FINISHED_END_OF_DAY'", store)).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            requestEntityManager.close();
            pool.shutdownNow();
        }
    }

    @Test void failedBatchRollsBackOrdersAuditAndReservationThenSafelyRetries() {
        Long order = order(store, "dine_in", "submitted", submitted, "IN_STORE");
        doThrow(new IllegalStateException("fixture commit failure")).when(runs).complete(eq(store), any(), any(), anyInt());
        assertThatThrownBy(() -> close.closeStore(store, now)).hasMessageContaining("fixture commit failure");
        assertThat(status(order)).isEqualTo("submitted");
        assertThat(count("store_daily_close_runs", "store_id=?", store)).isZero();
        assertThat(count("audit_logs", "store_id=? and action='AUTO_FINISHED_END_OF_DAY'", store)).isZero();
        doCallRealMethod().when(runs).complete(eq(store), any(), any(), anyInt());
        assertThat(close.closeStore(store, now.plusSeconds(300))).isEqualTo(1);
        assertThat(status(order)).isEqualTo("completed");
    }

    @Test void localTimezoneGatesEachStoreAndNextDayDoesNotReplayHistoricalOrders() {
        Long west = store("America/Vancouver");
        Long order = order(west, "dine_in", "submitted", submitted, "IN_STORE");
        assertThat(close.closeStore(west, now)).isZero();
        assertThat(close.closeStore(west, now.plusSeconds(3 * 3600))).isEqualTo(1);
        assertThat(status(order)).isEqualTo("completed");
        Long late = order(west, "dine_in", "submitted", submitted, "IN_STORE");
        assertThat(close.closeStore(west, now.plusSeconds(27 * 3600))).isZero();
        assertThat(status(late)).isEqualTo("submitted");
        assertThat(count("audit_logs", "store_id=? and action='AUTO_FINISHED_END_OF_DAY'", west)).isEqualTo(1);
        assertThat(count("store_daily_close_runs", "store_id=?", west)).isEqualTo(2);
    }

    private Long store(String timezone) {
        return db.queryForObject("""
            insert into stores(organization_id,name,code,status,lifecycle_status,timezone,printing_mode,printing_enabled)
            values (?,'Daily close fixture',?,'active','ACTIVE',?,'MOCK',false) returning id
            """, Long.class, org, "DCF-" + UUID.randomUUID(), timezone);
    }
    private Long order(Long storeId, String type, String status, LocalDateTime submittedAt, String financial) {
        return db.queryForObject("""
            insert into orders(store_id,order_no,order_type,status,table_no,submitted_at,created_at,total_amount,subtotal_amount,discount_amount,financial_mode)
            values (?,?,?,?,'T1-A',?,?,23.45,23.45,0,?) returning id
            """, Long.class, storeId, "DCF-" + UUID.randomUUID(), type, status, submittedAt, submitted, financial);
    }
    private String status(Long orderId) { return db.queryForObject("select status from orders where id=?", String.class, orderId); }
    private long count(String table, String where, Object... args) { return db.queryForObject("select count(*) from " + table + " where " + where, Long.class, args); }
}
