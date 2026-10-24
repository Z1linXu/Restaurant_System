package com.restaurant.system.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.system.RestaurantSystemApplication;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.order.dto.*;
import com.restaurant.system.order.service.IdempotentOrderSubmissionService;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.platform.service.PlatformAdminService;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.impl.OrderDispatchOutboxProcessor;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "SOFT_DELETE_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:.*")
@SpringBootTest(classes = RestaurantSystemApplication.class, properties = {
    "spring.profiles.active=soft-delete-test", "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
    "app.seed.runtime-enabled=false", "app.seed.safe-metadata-enabled=false",
    "app.seed.default-users-enabled=false", "app.seed.demo-data-enabled=false",
    "app.seed.membership-supplement-enabled=false", "app.daily-close.enabled=false",
    "UBER_EATS_ENABLED=false", "UBER_EATS_WORKER_ENABLED=false"
})
class DiningTableSoftDeletePostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SOFT_DELETE_POSTGRES_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("USER"));
        properties.add("spring.datasource.password", () -> "");
    }
    @Autowired JdbcTemplate db;
    @Autowired OrderService orders;
    @Autowired IdempotentOrderSubmissionService idempotent;
    @Autowired PlatformAdminService tables;
    @Autowired DiningTableRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @MockBean PrintDispatcherService printing;
    @MockBean OrderDispatchOutboxProcessor outbox;
    @MockBean RealtimeEventPublisher realtime;

    @ParameterizedTest
    @ValueSource(strings = {"SAINT_DENIS", "CHINATOWN", "SAINTE_CATHERINE"})
    void deletionPersistsHidesTheTableAndPreservesHistoryAndFrozenReceipt(String storeName) {
        Fixture f = fixture(storeName);
        var order = orders.createOrReplaceDraftAndSubmit(request(f, "1-A", true), null);
        orders.completeOrder(order.id);
        Long job = db.queryForObject("insert into print_jobs(store_id,order_id,module_code,status,rendered_text_snapshot,payload_snapshot) values (?,?,'GRAB','PRINTED','FROZEN 1里','frozen-payload') returning id", Long.class, f.store, order.id);
        String before = db.queryForObject("select row_to_json(o)::text from orders o where id=?", String.class, order.id);
        DiningTable deleted = tables.deleteDiningTable(f.table.id, f.store);
        assertThat(deleted.is_active).isFalse();
        assertThat(repository.findById(f.table.id).orElseThrow().is_active).isFalse();
        assertThat(tables.getDiningTables(f.store)).extracting(row -> row.id).doesNotContain(f.table.id);
        assertThat(tables.getOverview(f.store).dining_tables).isEmpty();
        assertThat(orders.getOrderDetail(order.id).table_no).isEqualTo("1-A");
        assertThat(orders.getOrderDetail(order.id).table_display_name).isEqualTo("1里");
        assertThat(orders.getFrontdeskOrderHistory(f.store, List.of("completed"), "dine_in", null, null, null, 20))
            .singleElement().satisfies(row -> assertThat(row.table_display_name).isEqualTo("1里"));
        assertThat(db.queryForObject("select row_to_json(o)::text from orders o where id=?", String.class, order.id)).isEqualTo(before);
        assertThat(db.queryForObject("select rendered_text_snapshot||'|'||payload_snapshot from print_jobs where id=?", String.class, job)).isEqualTo("FROZEN 1里|frozen-payload");
        var deletedAt = repository.findById(f.table.id).orElseThrow().updated_at;
        assertThat(tables.deleteDiningTable(f.table.id, f.store).updated_at).isEqualTo(deletedAt);
    }

    @ParameterizedTest
    @CsvSource(value = {"1,draft", "1-A,submitted", "1-B,preparing", "T12,ready", "T12-A,picked_up", "T12-B,custom_active", "1-A,NULL"}, nullValues = "NULL")
    void everyActiveContextIncludingEmptyDraftAndNullStatusPreventsDeletion(String raw, String status) {
        Fixture f = fixture("ACTIVE_GUARD");
        db.update("insert into orders(store_id,order_no,order_type,status,table_no) values (?,?,'dine_in',?,?)", f.store, UUID.randomUUID().toString(), status, raw);
        assertThatThrownBy(() -> tables.deleteDiningTable(f.table.id, f.store))
            .isInstanceOf(BusinessException.class).hasMessageContaining("TABLE_HAS_ACTIVE_ORDERS");
        assertThat(repository.findById(f.table.id).orElseThrow().is_active).isTrue();
    }

    @ParameterizedTest(name = "legacy context {index}")
    @ValueSource(strings = {" 1 ", " 1-A ", " 1-b ", " T12 ", " T12-a ", "T12-b", "\t1-a\t", "\r\nT12-B\r\n", "\u00011-b\u0001"})
    void legacyCaseAndWhitespaceContextsBlockDeletionModeChangeAndRenameWithoutRewritingRawValues(String raw) {
        Fixture f = fixture("LEGACY_CONTEXT_GUARD");
        String orderType = "\u0001\t DINE_IN \r\n";
        Long order = db.queryForObject("insert into orders(store_id,order_no,order_type,status,table_no) values (?,?,?,'draft',?) returning id", Long.class, f.store, UUID.randomUUID().toString(), orderType, raw);
        assertThatThrownBy(() -> tables.deleteDiningTable(f.table.id, f.store)).hasMessageContaining("TABLE_HAS_ACTIVE_ORDERS");
        f.table.split_label_mode = "LEFT_RIGHT";
        assertThatThrownBy(() -> tables.saveDiningTable(f.table)).hasMessageContaining("Finish all active orders");
        f.table.split_label_mode = "INSIDE_OUTSIDE"; f.table.table_name = "Renamed";
        assertThatThrownBy(() -> tables.saveDiningTable(f.table)).hasMessageContaining("Finish all active orders");
        assertThat(repository.findById(f.table.id).orElseThrow().table_name).isEqualTo("1");
        assertThat(repository.findById(f.table.id).orElseThrow().is_active).isTrue();
        assertThat(db.queryForObject("select table_no from orders where id=?", String.class, order)).isEqualTo(raw);
        assertThat(db.queryForObject("select order_type from orders where id=?", String.class, order)).isEqualTo(orderType);
    }

    @Test
    void cancelledOrdersDoNotBlockAndCrossStoreDeletionCannotTouchTheTable() {
        Fixture f = fixture("CROSS_STORE"), other = fixture("OTHER_STORE");
        db.update("insert into orders(store_id,order_no,order_type,status,table_no) values (?,?,'dine_in','cancelled','1-B')", f.store, UUID.randomUUID().toString());
        orders.createOrder(request(other, "1-A", false));
        assertThatThrownBy(() -> tables.deleteDiningTable(f.table.id, other.store)).isInstanceOf(BusinessException.class);
        assertThat(repository.findById(f.table.id).orElseThrow().is_active).isTrue();
        assertThat(tables.deleteDiningTable(f.table.id, f.store).is_active).isFalse();
        assertThat(tables.getDiningTables(other.store)).extracting(row -> row.id).contains(other.table.id);
    }

    @Test
    void oldClientCannotReactivateOrRecreateDeletedNamesCodesOrSplitAliases() {
        Fixture f = fixture("RESERVED_IDENTITY");
        DiningTable live = table(f.store, "11", "T12");
        live = tables.saveDiningTable(live); // Existing repeated code is supported.
        tables.deleteDiningTable(f.table.id, f.store);
        DiningTable oldClient = table(f.store, "1", "T12"); oldClient.id = f.table.id; oldClient.is_active = true;
        assertThatThrownBy(() -> tables.saveDiningTable(oldClient)).hasMessageContaining("TABLE_INACTIVE");
        for (DiningTable request : List.of(table(f.store, "1", "NEW"), table(f.store, "NEW", "T12"), table(f.store, "1-A", "NEW-A"), table(f.store, "NEW-B", "T12-B"))) {
            assertThatThrownBy(() -> tables.saveDiningTable(request)).hasMessageContaining("TABLE_INACTIVE");
        }
        DiningTable rename = table(f.store, "1", "T12"); rename.id = live.id;
        assertThatThrownBy(() -> tables.saveDiningTable(rename)).hasMessageContaining("TABLE_INACTIVE");
        assertThat(tables.saveDiningTable(live).table_name).isEqualTo("11"); // Unchanged live shared code is not a resurrection.
        assertThat(orders.createOrder(request(f, "11-A", false)).table_no).isEqualTo("11-A");
        assertThatThrownBy(() -> orders.createOrder(request(f, "T12-A", false))).hasMessageContaining("TABLE_INACTIVE");
        Fixture other = fixture("RESERVATION_OTHER_STORE");
        assertThat(orders.createOrder(request(other, "1-A", false)).table_no).isEqualTo("1-A");
    }

    @Test
    void defaultActiveAndLegacyNullAreVisibleButFalseOnlyUsesTheDeleteRoute() {
        Fixture f = fixture("DEFAULT_ACTIVE");
        DiningTable created = table(f.store, "2", "T2"); created.is_active = null;
        assertThat(tables.saveDiningTable(created).is_active).isTrue();
        db.update("update dining_tables set is_active=null where id=?", f.table.id);
        assertThat(tables.getDiningTables(f.store)).extracting(row -> row.id).contains(f.table.id);
        assertThat(orders.createOrder(request(f, "1-A", false)).table_no).isEqualTo("1-A");
        DiningTable falseCreate = table(f.store, "3", "T3"); falseCreate.is_active = false;
        assertThatThrownBy(() -> tables.saveDiningTable(falseCreate)).hasMessageContaining("TABLE_INACTIVE");
        DiningTable falseUpdate = table(f.store, "1", "T12"); falseUpdate.id = f.table.id; falseUpdate.is_active = false;
        assertThatThrownBy(() -> tables.saveDiningTable(falseUpdate)).hasMessageContaining("TABLE_INACTIVE");
    }

    @Test
    void outerWhitespaceInNewOrRenamedIdentitiesIsRejectedWithoutTrimmingAndEmptyFallbackRemainsValid() {
        Fixture f = fixture("IDENTITY_WHITESPACE");
        for (DiningTable request : List.of(table(f.store, " 2 ", "T2"), table(f.store, "2", " T2 "))) {
            assertThatThrownBy(() -> tables.saveDiningTable(request)).hasMessageContaining("TABLE_IDENTITY_WHITESPACE");
        }
        for (DiningTable request : List.of(table(f.store, " 1 ", "T12"), table(f.store, "1", " T12 "))) {
            request.id = f.table.id;
            assertThatThrownBy(() -> tables.saveDiningTable(request)).hasMessageContaining("TABLE_IDENTITY_WHITESPACE");
        }
        DiningTable unchanged = repository.findById(f.table.id).orElseThrow();
        assertThat(unchanged.table_name).isEqualTo("1");
        assertThat(unchanged.table_code).isEqualTo("T12");
        assertThat(tables.getDiningTables(f.store)).hasSize(1);
        assertThat(tables.saveDiningTable(table(f.store, "", "FALLBACK")).table_name).isEmpty();
        assertThat(orders.createOrder(request(f, "FALLBACK-A", false)).table_no).isEqualTo("FALLBACK-A");
    }

    @Test
    void completelyBlankIdentityCannotBeCreatedOrRenamedAndNameOnlyFallbackRemainsValid() {
        Fixture f = fixture("IDENTITY_REQUIRED");
        for (DiningTable request : List.of(table(f.store, null, null), table(f.store, "", ""))) {
            assertThatThrownBy(() -> tables.saveDiningTable(request)).hasMessageContaining("TABLE_IDENTITY_REQUIRED");
            request.id = f.table.id;
            assertThatThrownBy(() -> tables.saveDiningTable(request)).hasMessageContaining("TABLE_IDENTITY_REQUIRED");
        }
        DiningTable unchanged = repository.findById(f.table.id).orElseThrow();
        assertThat(unchanged.table_name).isEqualTo("1");
        assertThat(unchanged.table_code).isEqualTo("T12");
        assertThat(tables.getDiningTables(f.store)).hasSize(1);
        assertThat(tables.saveDiningTable(table(f.store, "NAME_ONLY", "")).table_code).isEmpty();
        assertThat(orders.createOrder(request(f, "NAME_ONLY-A", false)).table_no).isEqualTo("NAME_ONLY-A");
    }

    @Test
    void deletedContextRejectsOfflineNewSubmissionAndLegacyInactiveDraftSubmissionAndContextChange() {
        Fixture f = fixture("STALE_CONTEXT");
        var legacyRequest = request(f, " 1-a ", true); legacyRequest.order_type = " dine_in ";
        var staleDraft = orders.createOrder(legacyRequest);
        assertThat(staleDraft.table_no).isEqualTo(" 1-a ");
        assertThat(staleDraft.order_type).isEqualTo(" dine_in ");
        // A legacy disabled row predating this dedicated DELETE API is still a tombstone.
        db.update("update dining_tables set is_active=false where id=?", f.table.id);
        for (String raw : List.of("1", "1-A", "1-B", "1-a", "1-b", " 1-a ", "T12", "T12-A", "T12-B", "T12-a", "T12-b")) {
            assertThatThrownBy(() -> orders.createOrder(request(f, raw, false))).hasMessageContaining("TABLE_INACTIVE");
            assertThatThrownBy(() -> orders.createOrReplaceDraftAndSubmit(request(f, raw, true), null)).hasMessageContaining("TABLE_INACTIVE");
        }
        var paddedNewRequest = request(f, " 1-b ", true); paddedNewRequest.order_type = " dine_in ";
        assertThatThrownBy(() -> orders.createOrder(paddedNewRequest)).hasMessageContaining("TABLE_INACTIVE");
        assertThatThrownBy(() -> orders.createOrReplaceDraftAndSubmit(paddedNewRequest, null)).hasMessageContaining("TABLE_INACTIVE");
        assertThatThrownBy(() -> orders.submitOrder(staleDraft.id)).hasMessageContaining("TABLE_INACTIVE");
        assertThatThrownBy(() -> orders.createOrReplaceDraftAndSubmit(request(f, "1-A", true), staleDraft.id)).hasMessageContaining("TABLE_INACTIVE");
        var unknownRequest = request(f, "UNCONFIGURED-LEGACY-A", false); unknownRequest.order_type = " dine_in ";
        var unknown = orders.createOrder(unknownRequest);
        assertThat(unknown.table_no).isEqualTo("UNCONFIGURED-LEGACY-A"); // Preserve genuine legacy raw-context compatibility.
        UpdateDraftOrderHeaderRequest move = new UpdateDraftOrderHeaderRequest(); move.table_no = "1-B";
        assertThatThrownBy(() -> orders.updateDraftOrderHeader(unknown.id, move)).hasMessageContaining("TABLE_INACTIVE");
        assertThat(orders.getOrderDetail(unknown.id).table_no).isEqualTo("UNCONFIGURED-LEGACY-A");
        assertThat(orders.getOrderDetail(staleDraft.id).status).isEqualTo("draft");
        assertThat(orders.getOrderDetail(staleDraft.id).table_no).isEqualTo(" 1-a ");
        assertThat(orders.getOrderDetail(unknown.id).order_type).isEqualTo(" dine_in ");
    }

    @Test
    void completedIdempotentReplayRemainsTheSameResultAfterTableDeletion() {
        Fixture f = fixture("IDEMPOTENT_REPLAY");
        IdempotentOrderSubmitRequest request = new IdempotentOrderSubmitRequest();
        request.organization_id = f.org; request.store_id = f.store; request.order_type = "dine_in";
        request.table_no = "1-A"; request.client_order_id = UUID.randomUUID().toString(); request.idempotency_key = request.client_order_id;
        request.menu_revision = 0L; request.items = List.of(line(f)); request.expected_subtotal_amount = BigDecimal.TEN;
        var first = idempotent.submit(f.store, request, null);
        orders.completeOrder(first.order_id);
        tables.deleteDiningTable(f.table.id, f.store);
        var replay = idempotent.submit(f.store, request, null);
        assertThat(replay.replayed).isTrue();
        assertThat(replay.order_id).isEqualTo(first.order_id);
        assertThat(db.queryForObject("select count(*) from orders where store_id=?", Integer.class, f.store)).isEqualTo(1);
    }

    @Test
    void newOrderFirstMakesConcurrentDeleteWaitAndRejectAfterCommit() throws Exception {
        Fixture f = fixture("ORDER_WINS");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        try {
            Future<?> creation = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                orders.createOrder(request(f, "1-A", false)); locked.countDown(); await(release); return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> deletion = executor.submit(() -> { attempting.countDown(); return tables.deleteDiningTable(f.table.id, f.store); });
            assertThat(attempting.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> deletion.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); creation.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> deletion.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            assertThat(repository.findById(f.table.id).orElseThrow().is_active).isTrue();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void deleteFirstMakesConcurrentNewOrderRefreshAndRejectCommittedTombstone() throws Exception {
        Fixture f = fixture("DELETE_WINS");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        try {
            Future<?> deletion = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                tables.deleteDiningTable(f.table.id, f.store); locked.countDown(); await(release); return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> creation = executor.submit(() -> { attempting.countDown(); return orders.createOrder(request(f, "1-B", false)); });
            assertThat(attempting.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> creation.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); deletion.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> creation.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            assertThat(repository.findById(f.table.id).orElseThrow().is_active).isFalse();
            assertThat(db.queryForObject("select count(*) from orders where store_id=?", Integer.class, f.store)).isZero();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private Fixture fixture(String name) {
        String code = "TD-" + UUID.randomUUID();
        Long org = db.queryForObject("insert into organizations(name,code,status) values ('Table delete fixture',?,'active') returning id", Long.class, code);
        Long store = db.queryForObject("insert into stores(organization_id,name,code,status,printing_enabled,printing_mode) values (?,?,?,'active',false,'DISABLED') returning id", Long.class, org, name, code);
        Long station = db.queryForObject("insert into stations(store_id,code,name_zh,name_en,is_active) values (?,'COLD','冷菜','Cold',true) returning id", Long.class, store);
        Long category = db.queryForObject("insert into menu_categories(store_id,code,name_zh,is_active) values (?,'COLD_DISH','冷菜',true) returning id", Long.class, store);
        Long item = db.queryForObject("insert into menu_items(store_id,station_id,category_id,sku,item_type,name_zh,name_en,base_price,is_active,is_sold_out,sort_order) values (?,?,?,'delete_fixture_item','SINGLE','测试菜','Fixture',10,true,false,0) returning id", Long.class, store, station, category);
        return new Fixture(org, store, item, station, tables.saveDiningTable(table(store, "1", "T12")));
    }

    private DiningTable table(Long store, String name, String code) {
        DiningTable table = new DiningTable(); table.store_id = store; table.table_code = code; table.table_name = name;
        table.area_name = "Fixture"; table.capacity = 4; table.sort_order = 1;
        table.table_config = "split_supported"; table.supports_split = true; table.is_active = true; table.split_label_mode = "INSIDE_OUTSIDE";
        return table;
    }

    private CreateOrderRequest request(Fixture f, String slot, boolean withItem) {
        CreateOrderRequest request = new CreateOrderRequest(); request.store_id = f.store;
        request.order_type = "dine_in"; request.table_no = slot; request.items = withItem ? List.of(line(f)) : List.of(); return request;
    }

    private CreateOrderItemRequest line(Fixture f) {
        CreateOrderItemRequest line = new CreateOrderItemRequest(); line.menu_item_id = f.item; line.quantity = 1;
        line.unit_price_snapshot = BigDecimal.TEN; line.item_name_snapshot_zh = "测试菜"; line.item_name_snapshot_en = "Fixture";
        line.item_sku_snapshot = "delete_fixture_item"; line.item_type_snapshot = "SINGLE";
        line.category_code_snapshot = "COLD_DISH"; line.station_id_snapshot = f.station; return line;
    }
    private record Fixture(Long org, Long store, Long item, Long station, DiningTable table) {}
}
