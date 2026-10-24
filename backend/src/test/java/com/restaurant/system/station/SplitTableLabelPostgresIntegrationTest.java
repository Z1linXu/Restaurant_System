package com.restaurant.system.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.restaurant.system.RestaurantSystemApplication;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.order.dto.*;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.printing.service.impl.OrderDispatchOutboxProcessor;
import com.restaurant.system.platform.service.PlatformAdminService;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "SPLIT_LABEL_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:.*")
@SpringBootTest(classes = RestaurantSystemApplication.class, properties = {
    "spring.profiles.active=split-label-test", "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
    "app.seed.runtime-enabled=false", "app.seed.safe-metadata-enabled=false",
    "app.seed.default-users-enabled=false", "app.seed.demo-data-enabled=false",
    "app.seed.membership-supplement-enabled=false", "app.daily-close.enabled=false",
    "UBER_EATS_ENABLED=false", "UBER_EATS_WORKER_ENABLED=false"
})
class SplitTableLabelPostgresIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SPLIT_LABEL_POSTGRES_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("USER"));
        properties.add("spring.datasource.password", () -> "");
    }
    @Autowired JdbcTemplate db;
    @Autowired OrderService orders;
    @Autowired PlatformAdminService tables;
    @Autowired DiningTableRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @MockBean PrintDispatcherService printing;
    @MockBean OrderDispatchOutboxProcessor outbox;
    @MockBean RealtimeEventPublisher realtime;

    @ParameterizedTest
    @ValueSource(strings = {"SAINT_DENIS", "CHINATOWN", "SAINTE_CATHERINE"})
    void insideOutsidePersistsAndBothSlotsSubmitUpdateAndFinishIndependently(String storeName) {
        Fixture fixture = fixture(storeName);
        DiningTable table = fixture.table;
        assertThat(repository.findById(table.id).orElseThrow().split_label_mode).isEqualTo("INSIDE_OUTSIDE");
        OrderResponse a = orders.createOrReplaceDraftAndSubmit(request(fixture, "1-A", true), null);
        OrderResponse b = orders.createOrReplaceDraftAndSubmit(request(fixture, "1-B", true), null);
        assertThat(a.id).isNotEqualTo(b.id);
        assertThat(a.table_no).isEqualTo("1-A");
        assertThat(b.table_no).isEqualTo("1-B");
        assertThat(a.table_display_name).isEqualTo("1里");
        assertThat(b.table_display_name).isEqualTo("1外");
        assertThat(orders.findOpenEditableOrder(fixture.store, "1-A", null).id).isEqualTo(a.id);
        assertThat(orders.findOpenEditableOrder(fixture.store, "1-B", null).id).isEqualTo(b.id);
        CreateOrderUpdateRequest update = new CreateOrderUpdateRequest();
        update.idempotency_key = UUID.randomUUID().toString(); update.items = List.of(line(fixture));
        var first = orders.createOrderUpdate(a.id, update, null);
        var retry = orders.createOrderUpdate(a.id, update, null);
        assertThat(retry.already_processed).isTrue();
        assertThat(first.update_batch_id).isEqualTo(retry.update_batch_id);
        assertThat(orders.getOrderDetail(a.id).items).hasSize(2);
        assertThat(orders.getOrderDetail(b.id).items).hasSize(1);
        assertThat(orders.getOrderDetail(b.id).total_amount).isEqualByComparingTo(b.total_amount);
        verify(printing, times(1)).dispatchOrderUpdateAfterCommit("GRAB", fixture.store, a.id, first.update_batch_id);
        verify(printing, times(1)).dispatchOrderUpdateAfterCommit("FRONTDESK_RECEIPT", fixture.store, a.id, first.update_batch_id);
        table.split_label_mode = "LEFT_RIGHT";
        assertThatThrownBy(() -> tables.saveDiningTable(table)).isInstanceOf(BusinessException.class);
        orders.completeOrder(a.id);
        assertThat(orders.findOpenEditableOrder(fixture.store, "1-A", null)).isNull();
        assertThat(orders.findOpenEditableOrder(fixture.store, "1-B", null).id).isEqualTo(b.id);
        assertThatThrownBy(() -> tables.saveDiningTable(table)).isInstanceOf(BusinessException.class);
        orders.completeOrder(b.id);
        tables.saveDiningTable(table);
        assertThat(repository.findById(table.id).orElseThrow().split_label_mode).isEqualTo("LEFT_RIGHT");
        assertThat(db.queryForList("select table_no from orders where id in (?,?) order by id", String.class, a.id, b.id))
            .containsExactly("1-A", "1-B");
        assertThat(orders.getFrontdeskOrderHistory(fixture.store, List.of("completed"), "dine_in", null, null, null, 20))
            .hasSize(2).allSatisfy(row -> assertThat(row.table_display_name).isIn("1-左", "1-右"));
    }

    @Test
    void emptyDraftAndLegacyCodeAreProtectedAndOtherStoreDoesNotOccupyTheTable() {
        Fixture fixture = fixture("DRAFT_GUARD");
        OrderResponse draft = orders.createOrder(request(fixture, "1-A", false));
        DiningTable changed = fixture.table; changed.split_label_mode = "LEFT_RIGHT";
        assertThatThrownBy(() -> tables.saveDiningTable(changed)).isInstanceOf(BusinessException.class);
        orders.cancelOrder(draft.id);
        Long legacy = db.queryForObject("insert into orders(store_id,order_no,order_type,status,table_no) values (?,?,'dine_in','draft','T12') returning id", Long.class, fixture.store, UUID.randomUUID().toString());
        assertThatThrownBy(() -> tables.saveDiningTable(changed)).isInstanceOf(BusinessException.class);
        orders.cancelOrder(legacy);
        Fixture other = fixture("OTHER_STORE");
        orders.createOrder(request(other, "1-A", false));
        assertThat(tables.saveDiningTable(changed).split_label_mode).isEqualTo("LEFT_RIGHT");
    }

    @Test
    void newDraftHoldsTableLockAndModeChangeRechecksCommittedActiveOrder() throws Exception {
        Fixture fixture = fixture("CONCURRENT_CREATE");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        try {
            Future<?> creation = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                orders.createOrder(request(fixture, "1-A", false)); locked.countDown(); await(release); return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            fixture.table.split_label_mode = "LEFT_RIGHT";
            Future<?> edit = executor.submit(() -> { attempting.countDown(); return tables.saveDiningTable(fixture.table); });
            assertThat(attempting.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> edit.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); creation.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> edit.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            assertThat(repository.findById(fixture.table.id).orElseThrow().split_label_mode).isEqualTo("INSIDE_OUTSIDE");
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void modeChangeHoldsTableLockAndNewDraftSeesCommittedMode() throws Exception {
        Fixture fixture = fixture("CONCURRENT_MODE");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
        try {
            Future<?> edit = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                DiningTable table = repository.findByIdAndStoreIdForUpdate(fixture.table.id, fixture.store);
                table.split_label_mode = "LEFT_RIGHT"; repository.saveAndFlush(table);
                locked.countDown(); await(release); return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<OrderResponse> creation = executor.submit(() -> { attempting.countDown(); return orders.createOrder(request(fixture, "1-A", false)); });
            assertThat(attempting.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> creation.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); edit.get(10, TimeUnit.SECONDS);
            assertThat(creation.get(10, TimeUnit.SECONDS).table_display_name).isEqualTo("1-左");
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private Fixture fixture(String name) {
        String code = "SL-" + UUID.randomUUID();
        Long org = db.queryForObject("insert into organizations(name,code,status) values ('Split label fixture',?,'active') returning id", Long.class, code);
        Long store = db.queryForObject("insert into stores(organization_id,name,code,status,printing_enabled,printing_mode) values (?,?,?,'active',false,'DISABLED') returning id", Long.class, org, name, code);
        Long station = db.queryForObject("insert into stations(store_id,code,name_zh,name_en,is_active) values (?,'COLD','冷菜','Cold',true) returning id", Long.class, store);
        Long category = db.queryForObject("insert into menu_categories(store_id,code,name_zh,is_active) values (?,'COLD_DISH','冷菜',true) returning id", Long.class, store);
        Long item = db.queryForObject("insert into menu_items(store_id,station_id,category_id,sku,item_type,name_zh,name_en,base_price,is_active,is_sold_out,sort_order) values (?,?,?,'split_fixture_item','SINGLE','测试菜','Fixture',10,true,false,0) returning id", Long.class, store, station, category);
        DiningTable table = new DiningTable(); table.store_id = store; table.table_code = "T12"; table.table_name = "1";
        table.area_name = "Fixture"; table.capacity = 4; table.sort_order = 1;
        table.table_config = "split_supported"; table.supports_split = true; table.is_active = true; table.split_label_mode = "INSIDE_OUTSIDE";
        return new Fixture(store, item, station, tables.saveDiningTable(table));
    }

    private CreateOrderRequest request(Fixture fixture, String slot, boolean withItem) {
        CreateOrderRequest request = new CreateOrderRequest(); request.store_id = fixture.store;
        request.order_type = "dine_in"; request.table_no = slot;
        request.items = withItem ? List.of(line(fixture)) : List.of(); return request;
    }

    private CreateOrderItemRequest line(Fixture fixture) {
        CreateOrderItemRequest line = new CreateOrderItemRequest(); line.menu_item_id = fixture.item;
        line.quantity = 1; line.unit_price_snapshot = new BigDecimal("10");
        line.item_name_snapshot_zh = "测试菜"; line.item_name_snapshot_en = "Fixture";
        line.item_sku_snapshot = "split_fixture_item"; line.item_type_snapshot = "SINGLE";
        line.category_code_snapshot = "COLD_DISH"; line.station_id_snapshot = fixture.station; return line;
    }
    private record Fixture(Long store, Long item, Long station, DiningTable table) {}
}
