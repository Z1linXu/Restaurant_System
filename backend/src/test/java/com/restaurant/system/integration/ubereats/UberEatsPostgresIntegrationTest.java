package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.restaurant.system.RestaurantSystemApplication;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.integration.ubereats.client.*;
import com.restaurant.system.integration.ubereats.config.*;
import com.restaurant.system.integration.ubereats.entity.*;
import com.restaurant.system.integration.ubereats.mapping.*;
import com.restaurant.system.integration.ubereats.repository.*;
import com.restaurant.system.integration.ubereats.service.*;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.order.dto.*;
import com.restaurant.system.order.repository.*;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.renderer.GrabReceiptRenderer;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.impl.OrderDispatchOutboxProcessor;
import com.restaurant.system.user.repository.StoreRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@EnabledIfEnvironmentVariable(named = "UBER_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(
        classes = RestaurantSystemApplication.class,
        properties = {
            "spring.profiles.active=uber-test",
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.show-sql=false",
            "app.seed.runtime-enabled=false",
            "app.seed.safe-metadata-enabled=false",
            "app.seed.default-users-enabled=false",
            "app.seed.demo-data-enabled=false",
            "app.seed.membership-supplement-enabled=false",
            "app.auth.x-user-id-fallback-enabled=false",
            "UBER_EATS_ENABLED=true",
            "UBER_EATS_WORKER_ENABLED=false",
            "UBER_EATS_CLIENT_ID=fixture-client",
            "UBER_EATS_CLIENT_SECRET=fixture-oauth-secret",
            "UBER_EATS_WEBHOOK_SIGNING_KEY=fixture-webhook-secret",
            "app.features.printing=true",
            "app.features.kds=true",
            "app.features.core-pos=true"
        })
@AutoConfigureMockMvc
class UberEatsPostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("UBER_TEST_POSTGRES_URL"));
        r.add("spring.datasource.username", () -> System.getenv("USER"));
        r.add("spring.datasource.password", () -> "");
    }

    @Autowired UberEatsProperties config;
    @Autowired UberEatsProcessingGate processingGate;
    @Autowired JdbcTemplate db;
    @Autowired Flyway flyway;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired UberEatsWebhookService webhook;
    @Autowired UberEatsOrderImportService imports;
    @Autowired UberEatsOrderTransactions tx;
    @Autowired UberEatsOrderRepository inbox;
    @Autowired UberEatsEventRepository events;
    @Autowired UberEatsStoreMappingRepository bindings;
    @Autowired UberEatsMenuMappingService mapping;
    @Autowired UberEatsOrderNormalizer normalizer;
    @Autowired UberEatsConfigurationService configuration;
    @Autowired com.restaurant.system.menu.addon.StoreAddonService addons;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderItemOptionRepository options;
    @Autowired KitchenTaskRepository tasks;
    @Autowired PrintDispatcherService dispatcher;
    @Autowired PrintJobRepository jobs;
    @Autowired OrderDispatchOutboxRepository outbox;
    @Autowired StoreRepository stores;
    @Autowired GrabReceiptRenderer grab;
    @Autowired PlatformTransactionManager transactions;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("taskScheduler")
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler defaultScheduler;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("uberEatsScheduler")
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler uberScheduler;

    @Autowired com.restaurant.system.printing.service.PadPrintJobService padPrinting;
    @Autowired com.restaurant.system.printing.repository.StoreDeviceRepository devices;
    @MockBean UberEatsOrderClient client;
    @MockBean OrderDispatchOutboxProcessor outboxProcessor;
    Long store, org, item, station, category, inventory, actorId;
    UberEatsStoreMapping binding;
    String uberStore;
    AuthenticatedUser actor;

    @BeforeEach
    void fixture() {
        // Scheduling remains disabled by the test annotation; direct service calls opt in.
        config.enabled = true;
        config.workerEnabled = true;
        config.scopes = "eats.order eats.store.orders.read";
        config.testWebhookClientId = "";
        config.testWebhookStoreId = "";
        config.processingMode = "ORDER_MANAGER";
        config.environment = "sandbox";
        config.runtimeEnvironment = "local";
        config.setClientSecret("fixture-oauth-secret");
        config.setWebhookSigningKey("fixture-webhook-secret");
        processingGate.notBefore = "";
        processingGate.storeCutovers = "";
        org =
                id(
                        "insert into organizations(name,code,status) values ('Uber"
                                + " fixture','UF-'||gen_random_uuid(),'active') returning id");
        createStoreFixture(org);
    }

    void createStoreFixture(Long organizationId) {
        org = organizationId;
        store =
                id(
                        "insert into"
                            + " stores(organization_id,name,code,status,lifecycle_status,printing_enabled,printing_mode,enable_bar_kitchen_tasks)"
                            + " values (?,'Uber"
                            + " fixture','UF-'||gen_random_uuid(),'active','ACTIVE',true,'MOCK',false)"
                            + " returning id",
                        org);
        for (String module :
                List.of(
                        "UBER_EATS",
                        "ORDERING_POS",
                        "MENU",
                        "MENU_MANAGEMENT",
                        "PRINTING",
                        "KDS",
                        "ORDER_HISTORY",
                        "TABLE_MANAGEMENT",
                        "STAFF_ACCESS",
                        "STORE_ADMINISTRATION"))
            db.update(
                    "insert into store_modules(store_id,module_key,enabled,created_at,updated_at)"
                            + " values (?,?,true,now(),now())",
                    store,
                    module);
        Long role =
                id(
                        "insert into roles(code,name) values ('FRONTDESK','Uber fixture frontdesk')"
                                + " returning id");
        actorId =
                id(
                        "insert into users(store_id,role_id,username,full_name,status) values"
                                + " (?,?,'fixture-'||gen_random_uuid(),'Fixture Staff','active')"
                                + " returning id",
                        store,
                        role);
        db.update(
                "insert into"
                    + " store_memberships(user_id,store_id,role_code,is_active,created_at,updated_at)"
                    + " values (?,?,'FRONTDESK',true,now(),now())",
                actorId,
                store);
        actor =
                new AuthenticatedUser(
                        actorId, store, role, "fixture", "Fixture Staff", "FRONTDESK");
        station =
                id(
                        "insert into stations(store_id,code,name,is_active) values"
                                + " (?,'NOODLE','面档',true) returning id",
                        store);
        category =
                id(
                        "insert into"
                            + " menu_categories(store_id,code,name_zh,name_en,is_active,sort_order)"
                            + " values (?,'SOUP_NOODLE','汤面','Soup noodles',true,1) returning id",
                        store);
        item =
                id(
                        "insert into"
                            + " menu_items(store_id,category_id,station_id,sku,name_zh,name_en,base_price,is_active,is_sold_out,item_type,sort_order)"
                            + " values (?,?,?,'traditional_beef_noodle','牛肉面','Traditional Beef"
                            + " Noodle',12.50,true,false,'food',1) returning id",
                        store,
                        category,
                        station);
        option(item, "size_large", "SIZE", "size", "大碗", 2);
        option(item, "fried_egg", "ADD_ON", "addon", "加煎蛋", 2);
        option(item, "remove_cilantro", "REMOVE", "remove", "走香菜", 0);
        option(item, "noodle_type_2", "NOODLE_TYPE", "noodle_type", "二细", 0);
        option(item, "spicy_mild", "SPICY_LEVEL", "spicy_level", "微辣", 0);
        inventory =
                id(
                        "insert into"
                            + " inventory_items(store_id,code,name,current_stock,is_active,item_level,item_type,unit)"
                            + " values (?,'beef','Beef',100,true,'raw_material','count','portion')"
                            + " returning id",
                        store);
        db.update(
                "insert into menu_item_bom(menu_item_id,inventory_item_id,qty_per_unit) values"
                        + " (?,?,1)",
                item,
                inventory);
        Long printer =
                id(
                        "insert into"
                            + " printer_configs(store_id,name,printer_type,ip_address,port,enabled,paper_width_mm,timeout_ms,font_size,text_encoding)"
                            + " values (?,'Fixture"
                            + " only','ESC_POS_TCP','127.0.0.1',9,true,80,100,'MEDIUM','GB18030')"
                            + " returning id",
                        store);
        for (String module : List.of("GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN"))
            db.update(
                    "insert into"
                        + " printer_assignments(store_id,printer_id,module_code,enabled,font_size)"
                        + " values (?,?,?,true,'MEDIUM')",
                    store,
                    printer,
                    module);
        uberStore = UUID.randomUUID().toString();
        binding = new UberEatsStoreMapping();
        binding.environment = "sandbox";
        binding.uberStoreId = uberStore;
        binding.storeId = store;
        binding.organizationId = org;
        binding.enabled = true;
        binding.createdAt = LocalDateTime.now();
        bindings.save(binding);
    }

    @Test
    void uberAndPadCatalogResolveSameCanonicalAddonDespiteCorruptedLegacyCopies() {
        Long optionId=db.queryForObject("select id from menu_item_options where menu_item_id=? and option_code='fried_egg'",Long.class,item);
        addons.reconcile(store,false);
        Long addonId=addons.getAddons(store).addons().get(0).id();
        var update=new com.restaurant.system.menu.addon.StoreAddonService.WriteRequest();
        update.name_zh="加煎蛋"; update.name_en="Extra Fried Egg";
        update.price=new java.math.BigDecimal("1.99"); update.active=true;
        addons.update(addonId,update);
        db.update("update menu_item_options set option_code='corrupt_copy',name_zh='错误',price_delta=9.99,is_active=false where id=?",optionId);
        var result=mapping.mapMirror(binding,normalizer.normalize(payload(UUID.randomUUID().toString())));
        assertThat(result.errors()).isEmpty();
        var selected=result.request().items.get(0).options.stream().filter(o -> optionId.equals(o.option_id)).findFirst().orElseThrow();
        assertThat(selected.option_code_snapshot).isEqualTo("fried_egg");
        assertThat(selected.option_name_snapshot_zh).isEqualTo("加煎蛋");
        assertThat(selected.option_price_snapshot).isEqualByComparingTo("1.99");
        var order=orderService.createKitchenMirror(result.request(), UUID.randomUUID().toString(), "CANONICAL", null);
        var frozen=options.findAllByOrderItemIds(items.findAllByOrderId(order.id).stream().map(i -> i.id).toList()).stream()
            .filter(o -> optionId.equals(o.option_id)).findFirst().orElseThrow();
        assertThat(frozen.option_code_snapshot).isEqualTo("fried_egg");
        assertThat(frozen.option_name_snapshot_en).isEqualTo("Extra Fried Egg");
        assertThat(frozen.price_delta).isZero(); // Kitchen Mirror deliberately has no POS financial amount.
        update.name_zh="新名称"; update.price=new java.math.BigDecimal("2.99");
        addons.update(addonId,update);
        assertThat(options.findById(frozen.id).orElseThrow().option_name_snapshot_zh).isEqualTo("加煎蛋");
        assertThat(options.findById(frozen.id).orElseThrow().price_delta).isZero();
        assertThat(addons.getItemAddons(item,store)).anySatisfy(a -> {assertThat(a.id()).isEqualTo(addonId);assertThat(a.enabled()).isTrue();});
    }

    @Test
    void migrationsAndUniqueConstraints() {
        assertThatCode(flyway::validate).doesNotThrowAnyException();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(
                        db.queryForObject(
                                "select count(*) from flyway_schema_history where version = '29'"
                                        + " and success",
                                Integer.class))
                .isOne();
        assertThat(
                        db.queryForObject(
                                "select count(*) from pg_constraint where conname in"
                                    + " ('uq_uber_event','uq_uber_order','uq_uber_local_order','uq_uber_menu_key','uq_uber_store')",
                                Integer.class))
                .isEqualTo(5);
    }

    @Test
    void webhookHttpSignatureMalformedUnknownAndFastDurableAck() throws Exception {
        byte[] raw =
                event(
                        UUID.randomUUID().toString(),
                        "orders.notification",
                        UUID.randomUUID().toString());
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(raw)
                                .header("X-Uber-Signature", sign(raw))
                                .header("X-Environment", "sandbox"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        verifyNoInteractions(client);
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(raw)
                                .header("X-Uber-Signature", sign(raw))
                                .header("X-Environment", "Sandbox"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(raw)
                                .header("X-Uber-Signature", sign(raw)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(raw)
                                .header("X-Uber-Signature", sign(raw))
                                .header("X-Environment", "unknown"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(raw)
                                .header("X-Environment", "sandbox"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(raw)
                                .header("X-Uber-Signature", "0".repeat(64))
                                .header("X-Environment", "sandbox"))
                .andExpect(status().isUnauthorized());
        byte[] malformed = "{".getBytes(StandardCharsets.UTF_8);
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(malformed)
                                .header("X-Uber-Signature", sign(malformed))
                                .header("X-Environment", "sandbox"))
                .andExpect(status().isBadRequest());
        byte[] unknown =
                event(UUID.randomUUID().toString(), "orders.future", UUID.randomUUID().toString());
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(unknown)
                                .header("X-Uber-Signature", sign(unknown))
                                .header("X-Environment", "sandbox"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/integrations/uber-eats/webhook")
                                .content(raw)
                                .header("X-Uber-Signature", sign(raw))
                                .header("X-Environment", "production"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiveOnlyDurablyHoldsWebhookAndDuplicatesWithoutAnyProcessingSideEffects() throws Exception {
        config.workerEnabled = false;
        String orderId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        byte[] raw = event(eventId, "orders.notification", orderId);
        for (int duplicate = 0; duplicate < 2; duplicate++)
            mvc.perform(post("/api/v1/integrations/uber-eats/webhook")
                            .content(raw)
                            .header("X-Uber-Signature", sign(raw))
                            .header("X-Environment", "sandbox"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));
        var durable = events.findByEnvironmentAndEventId("sandbox", eventId).orElseThrow();
        assertThat(durable.status).isEqualTo("HELD_RECEIVE_ONLY");
        assertThat(count("uber_eats_events", "id", durable.id)).isOne();
        imports.processEvents();
        imports.recover();
        assertNoKitchenPipeline();
        assertThat(count("uber_eats_orders", "store_id", store)).isZero();
        verifyNoInteractions(client);

        // Enabling processing must not silently promote a receive-only backlog.
        config.workerEnabled = true;
        processingGate.notBefore = "2026-09-01T00:00:00Z";
        receive(raw);
        imports.processEvents();
        imports.recover();
        assertThat(events.findById(durable.id).orElseThrow().status).isEqualTo("HELD_RECEIVE_ONLY");
        assertThat(count("uber_eats_orders", "store_id", store)).isZero();
        assertNoKitchenPipeline();
        verifyNoInteractions(client);
    }

    @Test
    void pausedWorkerBlocksManualDecisionRetryAndDirectTransactionSubmission() {
        var row = notify(payload(UUID.randomUUID().toString()));
        db.update("update uber_eats_orders set status='UBER_ACCEPTED',accepted_at=now(),next_attempt_at=now() where id=?", row.id);
        config.workerEnabled = false;
        clearInvocations(client);
        String cancelEvent = UUID.randomUUID().toString();
        receive(event(cancelEvent, "orders.cancel", row.uberOrderId));
        imports.processEvents();
        imports.recover();
        assertThat(events.findByEnvironmentAndEventId("sandbox", cancelEvent).orElseThrow().status)
                .isEqualTo("HELD_RECEIVE_ONLY");
        assertThat(row(row.uberOrderId).cancelled).isFalse();
        assertThatThrownBy(() -> imports.decide(store, row.id, actorId, "ACCEPT", null))
                .isInstanceOf(UberEatsException.class).hasMessage("UBER_PROCESSING_PAUSED");
        assertThatThrownBy(() -> tx.retryLocal(store, row.id))
                .isInstanceOf(UberEatsException.class).hasMessage("UBER_PROCESSING_PAUSED");
        assertThatThrownBy(() -> tx.submitLocal(row.id))
                .isInstanceOf(UberEatsException.class).hasMessage("UBER_PROCESSING_PAUSED");
        assertThat(row(row.uberOrderId).status).isEqualTo("UBER_ACCEPTED");
        assertThat(row(row.uberOrderId).localOrderId).isNull();
        assertNoKitchenPipeline();
        verifyNoInteractions(client);
    }

    @Test
    void productionHmacRequiresExplicitSigningKeyAndStrictProductionEnvironment() throws Exception {
        config.workerEnabled = false;
        config.processingMode = "KITCHEN_MIRROR";
        config.scopes = "eats.store eats.store.orders.read";
        config.environment = "production";
        config.runtimeEnvironment = "production";
        config.setWebhookSigningKey("fixture-production-signing-key");
        config.validate();
        byte[] raw = event(UUID.randomUUID().toString(), "orders.notification", UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/integrations/uber-eats/webhook").content(raw)
                        .header("X-Uber-Signature", sign(raw, "fixture-production-signing-key"))
                        .header("X-Environment", "production"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        for (String wrongKey : List.of("fixture-oauth-secret", "fixture-webhook-secret"))
            mvc.perform(post("/api/v1/integrations/uber-eats/webhook").content(raw)
                            .header("X-Uber-Signature", sign(raw, wrongKey))
                            .header("X-Environment", "production"))
                    .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/integrations/uber-eats/webhook").content(raw)
                        .header("X-Uber-Signature", sign(raw, "fixture-production-signing-key"))
                        .header("X-Environment", "sandbox"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/integrations/uber-eats/webhook").content(raw)
                        .header("X-Uber-Signature", sign(raw, "fixture-production-signing-key")))
                .andExpect(status().isBadRequest());
        assertNoKitchenPipeline();
        verifyNoInteractions(client);
    }

    @Test
    void webhookDatabaseFailureNeverAcknowledgesAnUnpersistedEvent() throws Exception {
        config.workerEnabled = false;
        String eventId = UUID.randomUUID().toString();
        String constraint = "fixture_event_failure_" + UUID.randomUUID().toString().replace("-", "");
        byte[] raw = event(eventId, "orders.notification", UUID.randomUUID().toString());
        // Exact-event CHECK forces a real PostgreSQL failure without affecting other fixture rows.
        db.execute("alter table uber_eats_events add constraint " + constraint
                + " check (event_id <> '" + eventId + "')");
        try {
            mvc.perform(post("/api/v1/integrations/uber-eats/webhook").content(raw)
                            .header("X-Uber-Signature", sign(raw))
                            .header("X-Environment", "sandbox"))
                    .andExpect(status().is5xxServerError());
            assertThat(events.findByEnvironmentAndEventId("sandbox", eventId)).isEmpty();
            assertNoKitchenPipeline();
            verifyNoInteractions(client);
        } finally {
            db.execute("alter table uber_eats_events drop constraint " + constraint);
        }
    }

    @Test
    void webhookGetReturnsMethodNotAllowedAndAdvertisesPost() throws Exception {
        mvc.perform(get("/api/v1/integrations/uber-eats/webhook"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")));
        assertThat(events.findFirstByEnvironmentAndUberStoreIdOrderByIdDesc("sandbox", uberStore)).isEmpty();
        assertNoKitchenPipeline();
        verifyNoInteractions(client);
    }

    @Test
    void cutoverHoldsLegacyEventWithoutFetchingOrderAndDuplicatesCannotBypass() {
        mirrorMode();
        String orderId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        byte[] raw = event(eventId, "orders.notification", orderId);
        config.workerEnabled = false;
        receive(raw);
        config.workerEnabled = true;
        var saved = events.findByEnvironmentAndEventId("sandbox", eventId).orElseThrow();
        db.update("update uber_eats_events set status='PENDING',created_at=?,next_attempt_at=now() where id=?", LocalDateTime.ofInstant(Instant.parse("2026-08-31T23:59:59Z"), ZoneId.systemDefault()), saved.id);
        processingGate.notBefore = "2026-09-01T00:00:00Z";
        imports.processEvents();
        var held = events.findById(saved.id).orElseThrow();
        assertThat(held.status).isEqualTo("HELD_CUTOVER");
        assertThat(held.errorCode).isEqualTo("CUTOVER_EVENT_BEFORE_START");
        receive(raw);
        imports.processEvents();
        imports.recover();
        assertThat(events.findById(saved.id).orElseThrow().status).isEqualTo("HELD_CUTOVER");
        assertThat(count("uber_eats_orders", "store_id", store)).isZero();
        assertNoKitchenPipeline();
        verifyNoInteractions(client);
    }

    @Test
    void freshNotificationCannotImportAnOrderPlacedBeforeCutover() {
        mirrorMode();
        processingGate.notBefore = "2026-09-01T00:00:00Z";
        String orderId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        var oldOrder = payload(orderId).put("current_state", "ACCEPTED")
                .put("placed_at", "2026-08-31T23:59:59Z");
        when(client.getOrder(orderId)).thenReturn(oldOrder);
        receive(event(eventId, "orders.notification", orderId));
        imports.processEvents();
        var held = events.findByEnvironmentAndEventId("sandbox", eventId).orElseThrow();
        assertThat(held.status).isEqualTo("HELD_CUTOVER");
        assertThat(held.errorCode).isEqualTo("CUTOVER_ORDER_BEFORE_START");
        assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", orderId)).isEmpty();
        receive(event(UUID.randomUUID().toString(), "orders.release", orderId));
        imports.processEvents();
        imports.recover();
        assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", orderId)).isEmpty();
        assertNoKitchenPipeline();
        verify(client, never()).accept(anyString(), anyString());
        verify(client, never()).deny(anyString(), anyString());
    }

    @Test
    void cutoverHoldsMissingBlankAndMalformedOrderPlacementWithoutKitchenRelease() {
        mirrorMode();
        processingGate.notBefore = "2026-09-01T00:00:00Z";
        for (String invalid : List.of("missing", "", "not-a-date")) {
            String orderId = UUID.randomUUID().toString();
            String eventId = UUID.randomUUID().toString();
            var source = payload(orderId).put("current_state", "ACCEPTED");
            if ("missing".equals(invalid)) source.remove("placed_at");
            else source.put("placed_at", invalid);
            when(client.getOrder(orderId)).thenReturn(source);
            receive(event(eventId, "orders.notification", orderId));
            imports.processEvents();
            var held = events.findByEnvironmentAndEventId("sandbox", eventId).orElseThrow();
            assertThat(held.status).isEqualTo("HELD_CUTOVER");
            assertThat(held.errorCode).isEqualTo("CUTOVER_ORDER_BEFORE_START");
            assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", orderId)).isEmpty();
        }
        assertNoKitchenPipeline();
        verify(client, never()).accept(anyString(), anyString());
        verify(client, never()).deny(anyString(), anyString());
    }

    @Test
    void pausedCancellationLedgerWinsBeforeFreshGetCanReleaseKitchenOrder() {
        mirrorMode();
        var source = payload(UUID.randomUUID().toString());
        var waiting = notify(source);
        config.workerEnabled = false;
        String eventId = UUID.randomUUID().toString();
        receive(event(eventId, "orders.cancel", waiting.uberOrderId));
        assertThat(events.findByEnvironmentAndEventId("sandbox", eventId).orElseThrow().status)
                .isEqualTo("HELD_RECEIVE_ONLY");
        assertThat(row(waiting.uberOrderId).cancelled).isFalse();
        config.workerEnabled = true;
        processingGate.notBefore = "2026-09-01T00:00:00Z";
        clearInvocations(client);
        source.put("current_state", "ACCEPTED");
        // Recovery can run before the held cancellation's worker page. Its durable receipt wins.
        assertThat(tx.refreshMirror(waiting.id, normalizer.normalize(source))).isFalse();
        assertThat(row(waiting.uberOrderId).cancelled).isTrue();
        assertThat(row(waiting.uberOrderId).localOrderId).isNull();
        imports.processEvents();
        assertThat(events.findByEnvironmentAndEventId("sandbox", eventId).orElseThrow().status)
                .isEqualTo("COMPLETED");
        assertThat(row(waiting.uberOrderId).status).isEqualTo("CANCELLED");
        assertNoKitchenPipeline();
        verifyNoInteractions(client);
    }

    @Test
    void pausedCancellationBeyondWorkerPagePreventsAcceptedRecoveryFromReleasingKitchen() {
        mirrorMode();
        var source = payload(UUID.randomUUID().toString());
        var waiting = notify(source);
        List<String> earlierEvents = new ArrayList<>();
        try {
            for (int index = 0; index < 12; index++) {
                String orderId = UUID.randomUUID().toString();
                String eventId = UUID.randomUUID().toString();
                when(client.getOrder(orderId)).thenReturn(payload(orderId)); // all remain CREATED
                receive(event(eventId, "orders.notification", orderId));
                earlierEvents.add(eventId);
            }
            config.workerEnabled = false;
            String cancelEvent = UUID.randomUUID().toString();
            receive(event(cancelEvent, "orders.cancel", waiting.uberOrderId));
            assertThat(row(waiting.uberOrderId).cancelled).isFalse();
            config.workerEnabled = true;
            clearInvocations(client);
            imports.processEvents();
            for (int index = 0; index < earlierEvents.size(); index++)
                assertThat(events.findByEnvironmentAndEventId("sandbox", earlierEvents.get(index))
                                .orElseThrow().status)
                        .isEqualTo(index < 10 ? "COMPLETED" : "PENDING");
            assertThat(events.findByEnvironmentAndEventId("sandbox", cancelEvent).orElseThrow().status)
                    .isEqualTo("HELD_RECEIVE_ONLY"); // the disruptive event was beyond the first page
            assertThat(row(waiting.uberOrderId).cancelled).isFalse();

            source.put("current_state", "ACCEPTED");
            when(client.getOrder(waiting.uberOrderId)).thenReturn(source);
            due(waiting.id);
            imports.recover();
            assertThat(row(waiting.uberOrderId).cancelled).isTrue();
            assertThat(row(waiting.uberOrderId).status).isEqualTo("CANCELLED");
            assertThat(row(waiting.uberOrderId).localOrderId).isNull();
            assertThat(events.findByEnvironmentAndEventId("sandbox", cancelEvent).orElseThrow().status)
                    .isEqualTo("HELD_RECEIVE_ONLY"); // recovery consulted the ledger, not event processing
            verify(client, never()).getOrder(waiting.uberOrderId);
            verify(client, never()).accept(anyString(), anyString());
            verify(client, never()).deny(anyString(), anyString());
            assertNoKitchenPipeline();
        } finally {
            // These deliberately pending fixtures must not enter a later test's worker page.
            // Scope cleanup to this test's unique Store/UUID; preserve all other fixture rows.
            config.workerEnabled = true;
            db.update("update uber_eats_events set status='COMPLETED' where environment='sandbox' and uber_store_id=?", uberStore);
            db.update("update uber_eats_orders set next_attempt_at=null where store_id=?", store);
        }

    }

    @Test
    void cutoverStopsLegacyRecoveryBeforeRemoteReadOrLocalSubmission() {
        mirrorMode();
        var row = notify(payload(UUID.randomUUID().toString()));
        db.update("update uber_eats_orders set status='MIRROR_LOCAL_FAILED',accepted_observed_at=now(),next_attempt_at=now() where id=?", row.id);
        processingGate.notBefore = "2026-09-17T00:00:00Z";
        clearInvocations(client);
        imports.recover();
        var held = row(row.uberOrderId);
        assertThat(held.status).isEqualTo("CUTOVER_REVIEW_REQUIRED");
        assertThat(held.nextAttemptAt).isNull();
        assertThat(held.localOrderId).isNull();
        assertNoKitchenPipeline();
        verifyNoInteractions(client);
    }

    @Test
    void postCutoverMirrorImportsExactlyOnceAndPreservesCancellationOfExistingKitchenOrder() {
        mirrorMode();
        var row = release(payload(UUID.randomUUID().toString()));
        dispatch(row.localOrderId);
        Long previousLocal = row.localOrderId;
        long previousJobs = count("print_jobs", "order_id", previousLocal);
        // A later cutover must not suppress cancellation of an already-imported old order.
        processingGate.notBefore = "2026-09-17T00:00:00Z";
        receive(event(UUID.randomUUID().toString(), "orders.cancel", row.uberOrderId));
        imports.processEvents();
        assertThat(row(row.uberOrderId).status).isEqualTo("CANCELLED_AFTER_RELEASE");
        assertThat(row(row.uberOrderId).localOrderId).isEqualTo(previousLocal);
        assertThat(count("print_jobs", "order_id", previousLocal)).isEqualTo(previousJobs);

        String newId = UUID.randomUUID().toString();
        var fresh = payload(newId).put("placed_at", "2026-09-17T00:00:01Z");
        var imported = release(fresh);
        dispatch(imported.localOrderId);
        notify(fresh);
        imports.recover();
        dispatch(imported.localOrderId);
        assertThat(row(newId).localOrderId).isEqualTo(imported.localOrderId);
        assertThat(count("orders", "store_id", store)).isEqualTo(2);
        assertThat(count("kitchen_tasks", "order_id", imported.localOrderId)).isOne();
        assertThat(count("inventory_transactions", "source_id", imported.localOrderId)).isOne();
        assertThat(count("print_jobs", "order_id", imported.localOrderId)).isEqualTo(2);
        verify(client, never()).accept(anyString(), anyString());
        verify(client, never()).deny(anyString(), anyString());
    }

    void assertNoKitchenPipeline() {
        assertThat(count("orders", "store_id", store)).isZero();
        assertThat(count("kitchen_tasks", "store_id", store)).isZero();
        assertThat(count("order_dispatch_outbox", "store_id", store)).isZero();
        assertThat(count("print_jobs", "store_id", store)).isZero();
        assertThat(count("inventory_transactions", "inventory_item_id", inventory)).isZero();
    }

    @Test
    void testWebhookCompatibilityRequiresExactPairSignatureAndSandboxIdentity() throws Exception {
        mirrorMode();
        String id = UUID.randomUUID().toString();
        byte[] raw = event(UUID.randomUUID().toString(), "orders.notification", id);
        var source = payload(id);
        try {
            config.runtimeEnvironment = "staging";
            config.testWebhookClientId = config.clientId;
            config.testWebhookStoreId = uberStore;
            config.validate();
            when(client.getOrder(id)).thenReturn(source);
            mvc.perform(
                            post("/api/v1/integrations/uber-eats/webhook")
                                    .content(raw)
                                    .header("X-Uber-Signature", "0".repeat(64))
                                    .header("X-Environment", "production"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(client);
            source.with("store").put("id", UUID.randomUUID().toString());
            mvc.perform(
                            post("/api/v1/integrations/uber-eats/webhook")
                                    .content(raw)
                                    .header("X-Uber-Signature", sign(raw))
                                    .header("X-Environment", "production"))
                    .andExpect(status().isBadRequest());
            assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", id)).isEmpty();
            when(client.getOrder(id)).thenThrow(new UberEatsApiException(503));
            mvc.perform(
                            post("/api/v1/integrations/uber-eats/webhook")
                                    .content(raw)
                                    .header("X-Uber-Signature", sign(raw))
                                    .header("X-Environment", "production"))
                    .andExpect(status().isServiceUnavailable());
            assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", id)).isEmpty();
            doAnswer(
                            invocation -> {
                                var independent = new TransactionTemplate(transactions);
                                independent.setPropagationBehavior(
                                        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                                independent.executeWithoutResult(
                                        status ->
                                                db.update(
                                                        "update uber_eats_store_mappings set"
                                                                + " processing_mode='ORDER_MANAGER'"
                                                                + " where id=?",
                                                        binding.id));
                                return payload(id);
                            })
                    .when(client)
                    .getOrder(id);
            mvc.perform(
                            post("/api/v1/integrations/uber-eats/webhook")
                                    .content(raw)
                                    .header("X-Uber-Signature", sign(raw))
                                    .header("X-Environment", "production"))
                    .andExpect(status().isBadRequest());
            assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", id)).isEmpty();
            assertThat(
                            events.findByEnvironmentAndEventId(
                                    "sandbox", json.readTree(raw).path("event_id").asText()))
                    .isEmpty();
            mirrorMode();
            doReturn(payload(id)).when(client).getOrder(id);
            mvc.perform(
                            post("/api/v1/integrations/uber-eats/webhook")
                                    .content(raw)
                                    .header("X-Uber-Signature", sign(raw))
                                    .header("X-Environment", "production"))
                    .andExpect(status().isOk());
            assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", id)).isEmpty();
            imports.processEvents();
            assertThat(row(id).status).isEqualTo("WAITING_FOR_ACCEPTANCE");
            for (String wrong :
                    List.of("missing-app", "production-runtime", "wrong-store", "production-api")) {
                config.runtimeEnvironment = "staging";
                config.environment = "sandbox";
                config.testWebhookClientId = config.clientId;
                config.testWebhookStoreId = uberStore;
                if (wrong.equals("missing-app")) config.testWebhookClientId = "";
                if (wrong.equals("production-runtime")) config.runtimeEnvironment = "production";
                if (wrong.equals("wrong-store"))
                    config.testWebhookStoreId = UUID.randomUUID().toString();
                if (wrong.equals("production-api")) config.environment = "production";
                assertThat(config.allowsTestWebhook("production", uberStore)).isFalse();
            }
        } finally {
            config.runtimeEnvironment = "local";
            config.environment = "sandbox";
            config.testWebhookClientId = "";
            config.testWebhookStoreId = "";
        }
    }

    @Test
    void duplicateEventNotificationAndConcurrentAcceptCreateOnePipeline() throws Exception {
        String id = UUID.randomUUID().toString();
        var order = payload(id);
        order.put("order_manager_client_id", "redacted-manager");
        when(client.getOrder(id)).thenReturn(order);
        byte[] raw = event(UUID.randomUUID().toString(), "orders.notification", id);
        receive(raw);
        receive(raw);
        receive(event(UUID.randomUUID().toString(), "orders.notification", id));
        imports.processEvents();
        var row = row(id);
        assertThat(row.status).isEqualTo("PENDING");
        Long decisionId = row.id;
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a =
                    pool.submit(
                            () -> {
                                start.await();
                                return imports.decide(store, decisionId, actorId, "ACCEPT", null);
                            });
            var b =
                    pool.submit(
                            () -> {
                                start.await();
                                return imports.decide(store, decisionId, actorId, "ACCEPT", null);
                            });
            start.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        row = row(id);
        assertThat(row.status).isEqualTo("ACCEPTED");
        assertThat(count("orders", "store_id", store)).isOne();
        assertThat(count("kitchen_tasks", "order_id", row.localOrderId)).isOne();
        assertThat(count("order_dispatch_outbox", "order_id", row.localOrderId)).isEqualTo(3);
        assertThat(count("inventory_transactions", "source_id", row.localOrderId)).isOne();
        verify(client, times(1)).accept(eq(id), anyString());
        receive(raw);
        imports.processEvents();
        assertThat(count("orders", "store_id", store)).isOne();
        assertThat(
                        db.queryForObject(
                                "select external_source from orders where id=?",
                                String.class,
                                row.localOrderId))
                .isEqualTo("UBER_EATS");
    }

    @Test
    void unknownItemAndUnknownModifierBlockAcceptAndPreserveDetails() {
        String id = UUID.randomUUID().toString();
        var source = payload(id);
        ((ObjectNode) source.at("/cart/items/0")).put("external_data", "unknown_item");
        var row = notify(source);
        assertThat(row.status).isEqualTo("MAPPING_REQUIRED");
        imports.decide(store, row.id, actorId, "ACCEPT", null);
        verify(client, never()).accept(anyString(), anyString());
        assertThat(count("orders", "store_id", store)).isZero();
        String next = UUID.randomUUID().toString();
        var source2 = payload(next);
        ((ObjectNode) source2.at("/cart/items/0/selected_modifier_groups/0/selected_items/1"))
                .put("external_data", "unknown_modifier");
        row = notify(source2);
        assertThat(row.mappingError).contains("Fried Egg", "MODIFIER_MAPPING_MISSING");
        imports.decide(store, row.id, actorId, "ACCEPT", null);
        verify(client, never()).accept(anyString(), anyString());
        assertThat(count("print_jobs", "store_id", store)).isZero();
    }

    @Test
    void itemAndModifierExplicitMappingsRemapAutomatically() {
        var payload = payload(UUID.randomUUID().toString());
        ((ObjectNode) payload.at("/cart/items/0")).put("external_data", "");
        var row = notify(payload);
        assertThat(row.status).isEqualTo("MAPPING_REQUIRED");
        var rule = new UberEatsMenuMapping();
        rule.kind = "ITEM";
        rule.identifierType = "ID";
        rule.uberIdentifier = "uber-noodle";
        rule.localMenuItemId = item;
        configuration.saveMapping(
                store,
                rule,
                new AuthenticatedUser(actorId, store, 1L, "fixture", "Fixture Owner", "OWNER"));
        assertThat(row(row.uberOrderId).status).isEqualTo("PENDING");
        assertThat(accept(row).status).isEqualTo("ACCEPTED");
    }

    @Test
    void uberFailureDoesNotCreateLocalAndTimeoutReconcilesWithoutSecondAccept() {
        var row = notify(payload(UUID.randomUUID().toString()));
        doThrow(new UberEatsApiException(401))
                .when(client)
                .accept(eq(row.uberOrderId), anyString());
        assertThat(accept(row).status).isEqualTo("PENDING");
        assertThat(count("orders", "store_id", store)).isZero();
        doThrow(new UberEatsApiException(0)).when(client).accept(eq(row.uberOrderId), anyString());
        assertThat(accept(row).status).isEqualTo("ACCEPTING");
        var remote = payload(row.uberOrderId);
        remote.put("current_state", "ACCEPTED");
        remote.put("external_reference_id", UberEatsOrderImportService.reference(row));
        when(client.getOrder(row.uberOrderId)).thenReturn(remote);
        due(row.id);
        imports.recover();
        assertThat(row(row.uberOrderId).status).isEqualTo("ACCEPTED");
        verify(client, times(2)).accept(eq(row.uberOrderId), anyString());
    }

    @Test
    void localTransactionFailureRollsBackAndRecoveryDoesNotRepeatUberAccept() {
        var row = notify(payload(UUID.randomUUID().toString()));
        db.update("update stations set is_active=false where id=?", station);
        assertThat(accept(row).status).isEqualTo("LOCAL_FAILED");
        assertThat(count("orders", "store_id", store)).isZero();
        assertThat(count("order_dispatch_outbox", "store_id", store)).isZero();
        db.update("update stations set is_active=true where id=?", station);
        due(row.id);
        imports.recover();
        assertThat(row(row.uberOrderId).status).isEqualTo("ACCEPTED");
        verify(client, times(1)).accept(eq(row.uberOrderId), anyString());
    }

    @Test
    void cancellationBeforeAndAfterAndEditNeverDeleteOrMutateKitchen() {
        var before = notify(payload(UUID.randomUUID().toString()));
        receive(event(UUID.randomUUID().toString(), "orders.cancel", before.uberOrderId));
        assertThat(row(before.uberOrderId).status).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> accept(before)).isInstanceOf(UberEatsException.class);
        assertThat(count("orders", "store_id", store)).isZero();
        var after = accept(notify(payload(UUID.randomUUID().toString())));
        dispatch(after.localOrderId);
        long jobCount = count("print_jobs", "order_id", after.localOrderId);
        receive(event(UUID.randomUUID().toString(), "orders.cancel", after.uberOrderId));
        assertThat(row(after.uberOrderId).status).isEqualTo("CANCELLED_REVIEW_REQUIRED");
        assertThat(count("kitchen_tasks", "order_id", after.localOrderId)).isOne();
        assertThat(count("print_jobs", "order_id", after.localOrderId)).isEqualTo(jobCount);
        receive(
                event(
                        UUID.randomUUID().toString(),
                        "orders.customer_order_edit",
                        after.uberOrderId));
        assertThat(row(after.uberOrderId).status).isEqualTo("EDIT_REVIEW_REQUIRED");
        assertThat(count("order_items", "order_id", after.localOrderId)).isOne();
    }

    @Test
    void wrongStoreAndUnconfiguredStoreNeverImport() {
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode) data.path("store")).put("id", UUID.randomUUID().toString());
        String mismatchedOrder = data.path("id").asText();
        String eventId = UUID.randomUUID().toString();
        when(client.getOrder(mismatchedOrder)).thenReturn(data);
        receive(event(eventId, "orders.notification", mismatchedOrder));
        imports.processEvents();
        assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", mismatchedOrder)).isEmpty();
        assertThat(events.findByEnvironmentAndEventId("sandbox", eventId).orElseThrow().errorCode)
                .isEqualTo("UBER_RESPONSE_STORE_MISMATCH");
        assertNoKitchenPipeline();
        uberStore = UUID.randomUUID().toString();
        data = payload(UUID.randomUUID().toString());
        String id = data.path("id").asText();
        when(client.getOrder(id)).thenReturn(data);
        receive(event(UUID.randomUUID().toString(), "orders.notification", id));
        imports.processEvents();
        assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", id)).isEmpty();
    }

    @Test
    void storeAuthorizationAndNoSecretInResponses() throws Exception {
        var row = notify(payload(UUID.randomUUID().toString()));
        mvc.perform(get("/api/v1/stores/" + store + "/integrations/uber-eats/orders"))
                .andExpect(status().isUnauthorized());
        var stranger =
                new AuthenticatedUser(-99L, store + 99999, 1L, "stranger", "Stranger", "FRONTDESK");
        mvc.perform(
                        get("/api/v1/stores/" + store + "/integrations/uber-eats/orders")
                                .requestAttr(
                                        RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE,
                                        stranger))
                .andExpect(status().isForbidden());
        String body =
                mvc.perform(
                                get("/api/v1/stores/" + store + "/integrations/uber-eats/orders")
                                        .requestAttr(
                                                RequestUserContextService
                                                        .AUTHENTICATED_USER_ATTRIBUTE,
                                                actor))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body)
                .doesNotContain(
                        "fixture-webhook-secret",
                        "access_token",
                        "client_secret",
                        "phone",
                        "eater");
        assertThatThrownBy(() -> tx.scoped(store + 99999, row.id))
                .isInstanceOf(UberEatsException.class);
    }

    @Test
    void disabledUberModuleBlocksAllScopedEndpointsAndCrossStoreAccess() throws Exception {
        var row = notify(payload(UUID.randomUUID().toString()));
        db.update("update store_memberships set role_code='OWNER' where user_id=? and store_id=?", actorId, store);
        db.update("insert into organization_memberships(user_id,organization_id,role_code,is_active,created_at,updated_at) values (?,?,'OWNER',true,now(),now())", actorId, org);
        var owner = new AuthenticatedUser(actorId, store, actor.roleId(), "fixture", "Owner", "OWNER");
        String base = "/api/v1/stores/" + store + "/integrations/uber-eats";
        mvc.perform(get(base + "/connection").requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner)).andExpect(status().isOk());
        mvc.perform(get(base + "/mapping-catalog").requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner)).andExpect(status().isOk());
        db.update("update store_modules set enabled=false where store_id=? and module_key='UBER_EATS'", store);
        for (String path : List.of("/orders", "/connection", "/mapping-catalog", "/mapping-options/" + item))
            mvc.perform(get(base + path).requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("MODULE_DISABLED"));
        for (String path : List.of("/mapping-preview", "/orders/" + row.id + "/accept", "/orders/" + row.id + "/deny", "/orders/" + row.id + "/retry-local"))
            mvc.perform(post(base + path).contentType("application/json").content("{}")
                .requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("MODULE_DISABLED"));
        for (String path : List.of("/store", "/processing-mode", "/mappings"))
            mvc.perform(put(base + path).contentType("application/json").content("{}")
                .requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("MODULE_DISABLED"));
        var foreign = new AuthenticatedUser(-999L, store + 99999, 1L, "foreign", "Foreign", "OWNER");
        mvc.perform(get(base + "/orders").requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, foreign)).andExpect(status().isForbidden());
        assertThatThrownBy(() -> configuration.bind(store, UUID.randomUUID().toString(), owner))
            .isInstanceOf(com.restaurant.system.modules.ModuleAccessException.class);
        assertThatThrownBy(() -> tx.requireMapping(binding.id)).isInstanceOf(com.restaurant.system.modules.ModuleAccessException.class);
    }

    @Test
    void disabledStoreCannotImportWebhookOrRecoverEvenWithStaleActiveBinding() {
        var row = notify(payload(UUID.randomUUID().toString()));
        db.update("update store_modules set enabled=false where store_id=? and module_key='UBER_EATS'", store);
        var source = payload(UUID.randomUUID().toString()); String id = source.path("id").asText();
        clearInvocations(client);
        receive(event(UUID.randomUUID().toString(), "orders.notification", id));
        assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", id)).isEmpty();
        imports.processEvents();
        assertThat(inbox.findByEnvironmentAndUberOrderId("sandbox", id)).isEmpty();
        verify(client, never()).getOrder(id);
        db.update("update uber_eats_orders set status='WAITING_FOR_ACCEPTANCE',next_attempt_at=now() where id=?", row.id);
        assertThat(tx.claimRecovery(row.id)).isFalse();
        assertThat(tx.scoped(store, row.id).localOrderId).isNull();
    }

    @Test
    void disabledStoreBacklogDoesNotStarveEnabledStoreEventsOrRecovery() {
        // Preserve a real earlier page of disabled work; enabled Store must still be selected.
        var disabledRows = new ArrayList<UberEatsOrder>();
        for (int n=0;n<10;n++) disabledRows.add(notify(payload(UUID.randomUUID().toString())));
        db.update("update uber_eats_orders set status='WAITING_FOR_ACCEPTANCE',next_attempt_at=now() where store_id=?", store);
        db.update("update uber_eats_events set status='PENDING',next_attempt_at=now() where uber_store_id=?", uberStore);
        Long disabledStore=store;
        db.update("update store_modules set enabled=false where store_id=? and module_key='UBER_EATS'", disabledStore);
        fixture();
        var source=payload(UUID.randomUUID().toString());
        String id=source.path("id").asText();
        when(client.getOrder(id)).thenReturn(source);
        receive(event(UUID.randomUUID().toString(), "orders.notification", id));
        assertThat(events.due("sandbox",LocalDateTime.now().plusSeconds(1),PageRequest.of(0,10)))
            .extracting(e -> e.uberOrderId).contains(id)
            .doesNotContainAnyElementsOf(disabledRows.stream().map(r -> r.uberOrderId).toList());
        imports.processEvents();
        var enabled=row(id);
        db.update("update uber_eats_orders set status='WAITING_FOR_ACCEPTANCE',next_attempt_at=now() where id=?",enabled.id);
        assertThat(inbox.due("sandbox",LocalDateTime.now().plusSeconds(1),PageRequest.of(0,10)))
            .extracting(r -> r.id).contains(enabled.id)
            .doesNotContainAnyElementsOf(disabledRows.stream().map(r -> r.id).toList());
        db.update("update store_modules set enabled=true where store_id=? and module_key='UBER_EATS'",disabledStore);
        assertThat(events.due("sandbox",LocalDateTime.now().plusSeconds(1),PageRequest.of(0,10))).hasSize(10);
        assertThat(inbox.due("sandbox",LocalDateTime.now().plusSeconds(1),PageRequest.of(0,10)))
            .extracting(r -> r.id).containsAll(disabledRows.stream().map(r -> r.id).toList());
        db.update("update store_modules set enabled=false where store_id=? and module_key='MENU'",disabledStore);
        // Invalid enabled configuration is deferred, not allowed to occupy every next page.
        var blockedEvents=events.due("sandbox",LocalDateTime.now(),PageRequest.of(0,10));
        assertThat(blockedEvents).hasSize(10);
        for (var e:blockedEvents) assertThat(tx.claimEvent(e.id)).isFalse();
        for (var r:disabledRows) assertThat(tx.claimRecovery(r.id)).isFalse();
        assertThat(events.due("sandbox",LocalDateTime.now(),PageRequest.of(0,10))).isEmpty();
        assertThat(inbox.due("sandbox",LocalDateTime.now(),PageRequest.of(0,10)))
            .extracting(r -> r.id).contains(enabled.id)
            .doesNotContainAnyElementsOf(disabledRows.stream().map(r -> r.id).toList());
        db.update("update store_modules set enabled=false where store_id=? and module_key='UBER_EATS'",disabledStore);
    }

    @Test
    void twoActiveStoresUseIndependentMappingsAndKitchenRoutes() throws Exception {
        var a = fixtureStore();
        createStoreFixture(a.organization());
        var b = fixtureStore();
        try {
            prepareIndependentMirrorStore(a, "A", "127.0.0.1", 9);
            prepareIndependentMirrorStore(b, "B", "127.0.0.2", 10);
            var sourceA = mappedStorePayload(a, UUID.randomUUID().toString(), "STORE_A_NOTE_ONLY");
            var sourceB = mappedStorePayload(b, UUID.randomUUID().toString(), "STORE_B_NOTE_ONLY");
            useFixtureStore(a);
            var waitingA = notify(sourceA);
            useFixtureStore(b);
            var waitingB = notify(sourceB);
            assertThat(waitingA.status).isEqualTo("WAITING_FOR_ACCEPTANCE");
            assertThat(waitingB.status).isEqualTo("WAITING_FOR_ACCEPTANCE");
            assertThat(count("orders", "store_id", a.store())).isZero();
            assertThat(count("orders", "store_id", b.store())).isZero();

            // A may be accepted while B is still CREATED; each Store has its own gate.
            useFixtureStore(a);
            var importedA = release(sourceA);
            dispatch(importedA.localOrderId);
            assertThat(row(waitingB.uberOrderId).localOrderId).isNull();
            useFixtureStore(b);
            var importedB = release(sourceB);
            dispatch(importedB.localOrderId);
            for (var entry : List.of(a, b)) {
                useFixtureStore(entry);
                var source = entry.store().equals(a.store()) ? sourceA : sourceB;
                var imported = entry.store().equals(a.store()) ? importedA : importedB;
                receive(event(UUID.randomUUID().toString(), "orders.notification", imported.uberOrderId));
                receive(event(UUID.randomUUID().toString(), "orders.release", imported.uberOrderId));
                imports.processEvents();
                imports.recover();
                dispatch(imported.localOrderId);
                dispatch(imported.localOrderId);
                assertThat(count("orders", "store_id", entry.store())).isOne();
                assertThat(count("kitchen_tasks", "order_id", imported.localOrderId)).isOne();
                assertThat(count("inventory_transactions", "inventory_item_id", entry.inventory())).isOne();
                var localItems = items.findAllByOrderId(imported.localOrderId);
                assertThat(localItems).hasSize(1).allSatisfy(line -> {
                    assertThat(line.menu_item_id).isEqualTo(entry.item());
                    assertThat(line.item_name_snapshot_zh).isEqualTo("门店" + (entry == a ? "A" : "B") + "牛肉面");
                });
                var addonOption = options.findAllByOrderItemIds(localItems.stream().map(line -> line.id).toList())
                        .stream().filter(option -> "fried_egg".equals(option.option_code_snapshot)).findFirst().orElseThrow();
                Long addonId = addons.getAddons(entry.store()).addons().stream()
                        .filter(addon -> "fried_egg".equals(addon.code())).findFirst().orElseThrow().id();
                assertThat(db.queryForObject("select store_addon_id from menu_item_options where id=?", Long.class, addonOption.option_id))
                        .isEqualTo(addonId);
                assertThat(db.queryForObject("select store_addon_store_id from menu_item_options where id=?", Long.class, addonOption.option_id))
                        .isEqualTo(entry.store());
                assertThat(addonOption.option_name_snapshot_zh).isEqualTo(entry == a ? "A店加煎蛋" : "B店加煎蛋");
                String ownNote = source.at("/cart/special_instructions").asText();
                String foreignNote = entry == a ? "STORE_B_NOTE_ONLY" : "STORE_A_NOTE_ONLY";
                assertThat(orders.findById(imported.localOrderId).orElseThrow().external_order_note_snapshot).isEqualTo(ownNote);
                var storeJobs = jobs.findAllByStoreIdAndOrderId(entry.store(), imported.localOrderId);
                assertThat(storeJobs).hasSize(2).allSatisfy(job -> {
                    assertThat(job.store_id).isEqualTo(entry.store());
                    assertThat(job.organization_id).isEqualTo(a.organization());
                    assertThat(job.executionMode).isEqualTo("PAD_DIRECT");
                    assertThat(job.status).isEqualTo("PENDING");
                    assertThat(job.rendered_text_snapshot).contains(ownNote).doesNotContain(foreignNote);
                    assertThat(db.queryForObject("select store_id from printer_configs where id=?", Long.class, job.printer_id))
                            .isEqualTo(entry.store());
                });
            }
            Long addonA = addons.getAddons(a.store()).addons().get(0).id();
            Long addonB = addons.getAddons(b.store()).addons().get(0).id();
            assertThat(addonA).isNotEqualTo(addonB);
            assertThat(db.queryForObject("select organization_addon_definition_id from store_addons where id=?", Long.class, addonA))
                    .isEqualTo(db.queryForObject("select organization_addon_definition_id from store_addons where id=?", Long.class, addonB));
            var deviceA = fixtureDevice(a, "A");
            var deviceB = fixtureDevice(b, "B");
            var pendingA = padPrinting.listPendingJobs(deviceA, a.store(), 25);
            var pendingB = padPrinting.listPendingJobs(deviceB, b.store(), 25);
            assertThat(pendingA).hasSize(2).allSatisfy(job -> {
                assertThat(job.store_id).isEqualTo(a.store());
                assertThat(job.order_id).isEqualTo(importedA.localOrderId);
                assertThat(job.printer_endpoint).isEqualTo("127.0.0.1:9");
            });
            assertThat(pendingB).hasSize(2).allSatisfy(job -> {
                assertThat(job.store_id).isEqualTo(b.store());
                assertThat(job.order_id).isEqualTo(importedB.localOrderId);
                assertThat(job.printer_endpoint).isEqualTo("127.0.0.2:10");
            });
            assertThatThrownBy(() -> padPrinting.listPendingJobs(deviceA, b.store(), 25))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403));
            var claim = new com.restaurant.system.printing.dto.PadPrintJobClaimRequest();
            claim.client_attempt_token = "fixture-cross-store-claim";
            assertThatThrownBy(() -> padPrinting.claimJob(deviceA, pendingB.get(0).id, claim))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403));
            assertThat(jobs.findById(pendingB.get(0).id).orElseThrow().claimedByDeviceId).isNull();
            verify(client, never()).accept(anyString(), anyString());
            verify(client, never()).deny(anyString(), anyString());
        } finally {
            finishFixtureWork(a, b);
            useFixtureStore(a);
        }
    }

    @Test
    void sameOrgStoreEndpointsRejectForeignOrderMappingAndBinding() throws Exception {
        var a = fixtureStore();
        createStoreFixture(a.organization());
        var b = fixtureStore();
        try {
            prepareIndependentMirrorStore(a, "A", "127.0.0.1", 9);
            prepareIndependentMirrorStore(b, "B", "127.0.0.2", 10);
            useFixtureStore(a);
            var rowA = notify(mappedStorePayload(a, UUID.randomUUID().toString(), "A private note"));
            useFixtureStore(b);
            var rowB = notify(mappedStorePayload(b, UUID.randomUUID().toString(), "B private note"));
            String baseA = "/api/v1/stores/" + a.store() + "/integrations/uber-eats";
            String baseB = "/api/v1/stores/" + b.store() + "/integrations/uber-eats";
            mvc.perform(get(baseB + "/connection").requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, a.actor()))
                    .andExpect(status().isForbidden());
            db.update("update store_memberships set role_code='OWNER' where user_id=? and store_id=?", a.actorId(), a.store());
            db.update("insert into store_memberships(user_id,store_id,role_code,is_active,created_at,updated_at) values (?,?,'OWNER',true,now(),now())", a.actorId(), b.store());
            db.update("insert into organization_memberships(user_id,organization_id,role_code,is_active,created_at,updated_at) values (?,?,'OWNER',true,now(),now())", a.actorId(), a.organization());
            var owner = new AuthenticatedUser(a.actorId(), a.store(), a.actor().roleId(), "fixture", "Shared Owner", "OWNER");
            mvc.perform(get(baseA + "/connection").requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.store.storeId").value(a.store()));
            mvc.perform(get(baseB + "/connection").requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.store.storeId").value(b.store()));
            mvc.perform(post(baseA + "/orders/" + rowB.id + "/retry-local")
                            .requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                    .andExpect(status().isForbidden());
            mvc.perform(get(baseA + "/mapping-options/" + b.item())
                            .requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                    .andExpect(status().isConflict());
            var foreignMapping = new UberEatsMenuMapping();
            foreignMapping.kind = "ITEM";
            foreignMapping.identifierType = "ID";
            foreignMapping.uberIdentifier = "foreign-local-item";
            foreignMapping.localMenuItemId = b.item();
            mvc.perform(put(baseA + "/mappings").contentType("application/json")
                            .content(json.writeValueAsString(foreignMapping))
                            .requestAttr(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, owner))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("LOCAL_ITEM_NOT_IN_STORE"));
            var admin = new AuthenticatedUser(a.actorId(), a.store(), a.actor().roleId(), "fixture", "Platform Admin", "ADMIN");
            assertThatThrownBy(() -> configuration.bind(a.store(), b.uberStore(), admin))
                    .isInstanceOf(UberEatsException.class).hasMessage("UBER_STORE_ALREADY_BOUND");
            Long addonB = addons.getAddons(b.store()).addons().get(0).id();
            assertThatThrownBy(() -> addons.setEligibility(a.item(), addonB, true, a.store()))
                    .hasMessageContaining("ADDON_STORE_MISMATCH");
            assertThat(tx.scoped(a.store(), rowA.id).localOrderId).isNull();
            assertThat(tx.scoped(b.store(), rowB.id).localOrderId).isNull();
            assertThat(configuration.connection(a.store()).mappings()).allSatisfy(rule -> assertThat(rule.localMenuItemId).isEqualTo(a.item()));
            assertThat(configuration.connection(b.store()).mappings()).allSatisfy(rule -> assertThat(rule.localMenuItemId).isEqualTo(b.item()));
            assertThat(count("orders", "store_id", a.store())).isZero();
            assertThat(count("orders", "store_id", b.store())).isZero();
            verify(client, never()).accept(anyString(), anyString());
            verify(client, never()).deny(anyString(), anyString());
        } finally {
            finishFixtureWork(a, b);
            useFixtureStore(a);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MODULE_OFF", "BINDING_OFF"})
    void disabledStoreCannotPauseOtherStoreMirror(String disabledMode) {
        var a = fixtureStore();
        createStoreFixture(a.organization());
        var b = fixtureStore();
        try {
            prepareIndependentMirrorStore(a, "A", "127.0.0.1", 9);
            prepareIndependentMirrorStore(b, "B", "127.0.0.2", 10);
            useFixtureStore(a);
            var waitingA = notify(mappedStorePayload(a, UUID.randomUUID().toString(), "A pending"));
            for (int index = 0; index < 12; index++) {
                var source = mappedStorePayload(a, UUID.randomUUID().toString(), "A backlog");
                when(client.getOrder(source.path("id").asText())).thenReturn(source);
                receive(event(UUID.randomUUID().toString(), "orders.notification", source.path("id").asText()));
            }
            due(waitingA.id);
            if ("MODULE_OFF".equals(disabledMode))
                db.update("update store_modules set enabled=false where store_id=? and module_key='UBER_EATS'", a.store());
            else db.update("update uber_eats_store_mappings set enabled=false where id=?", a.binding().id);
            clearInvocations(client);
            var sourceB = mappedStorePayload(b, UUID.randomUUID().toString(), "B continues");
            useFixtureStore(b);
            var waitingB = notify(sourceB);
            assertThat(waitingB.status).isEqualTo("WAITING_FOR_ACCEPTANCE");
            sourceB.put("current_state", "ACCEPTED");
            due(waitingB.id);
            imports.recover();
            var importedB = row(waitingB.uberOrderId);
            assertThat(importedB.status).isEqualTo("RELEASED_TO_KITCHEN");
            dispatch(importedB.localOrderId);
            assertThat(row(waitingA.uberOrderId).localOrderId).isNull();
            assertThat(count("orders", "store_id", a.store())).isZero();
            assertThat(count("print_jobs", "store_id", a.store())).isZero();
            assertThat(count("inventory_transactions", "inventory_item_id", a.inventory())).isZero();
            assertThat(count("orders", "store_id", b.store())).isOne();
            assertThat(count("print_jobs", "store_id", b.store())).isEqualTo(2);
            assertThat(count("kitchen_tasks", "order_id", importedB.localOrderId)).isOne();
            assertThat(db.queryForObject("select count(*) from uber_eats_events where uber_store_id=? and status='PENDING'", Long.class, a.uberStore())).isEqualTo(12);
            verify(client, never()).getOrder(waitingA.uberOrderId);
            verify(client, never()).accept(anyString(), anyString());
            verify(client, never()).deny(anyString(), anyString());
        } finally {
            finishFixtureWork(a, b);
            useFixtureStore(a);
        }
    }

    record FixtureStore(Long store, Long organization, Long item, Long station, Long category,
                        Long inventory, Long actorId, AuthenticatedUser actor,
                        UberEatsStoreMapping binding, String uberStore) {}

    FixtureStore fixtureStore() {
        return new FixtureStore(store, org, item, station, category, inventory, actorId, actor, binding, uberStore);
    }

    void useFixtureStore(FixtureStore value) {
        store = value.store(); org = value.organization(); item = value.item();
        station = value.station(); category = value.category(); inventory = value.inventory();
        actorId = value.actorId(); actor = value.actor(); binding = value.binding(); uberStore = value.uberStore();
    }

    void prepareIndependentMirrorStore(FixtureStore value, String label, String host, int port) {
        useFixtureStore(value);
        config.processingMode = "KITCHEN_MIRROR";
        config.scopes = "eats.store eats.store.orders.read";
        mirrorMode();
        db.update("update stores set printing_mode='PAD_DIRECT' where id=?", store);
        db.update("update menu_items set name_zh=? where id=?", "门店" + label + "牛肉面", item);
        db.update("update printer_configs set ip_address=?,port=? where store_id=?", host, port, store);
        addons.reconcile(store, false);
        Long addonId = addons.getAddons(store).addons().stream().filter(addon -> "fried_egg".equals(addon.code())).findFirst().orElseThrow().id();
        var update = new com.restaurant.system.menu.addon.StoreAddonService.WriteRequest();
        update.name_zh = label + "店加煎蛋"; update.name_en = label + " Extra Fried Egg";
        update.price = new java.math.BigDecimal("A".equals(label) ? "1.99" : "2.49"); update.active = true;
        addons.update(addonId, update);
        var itemRule = new UberEatsMenuMapping();
        itemRule.kind = "ITEM"; itemRule.identifierType = "ID"; itemRule.uberIdentifier = "uber-noodle";
        itemRule.localMenuItemId = item;
        configuration.saveMapping(store, itemRule, actor);
        var addonRule = new UberEatsMenuMapping();
        addonRule.kind = "MODIFIER"; addonRule.identifierType = "ID"; addonRule.uberIdentifier = "egg";
        addonRule.uberItemId = "uber-noodle"; addonRule.localMenuItemId = item;
        addonRule.localOptionCode = "fried_egg"; addonRule.localOptionGroup = "ADD_ON";
        configuration.saveMapping(store, addonRule, actor);
    }

    ObjectNode mappedStorePayload(FixtureStore value, String orderId, String note) {
        useFixtureStore(value);
        var source = payload(orderId);
        ((ObjectNode) source.at("/cart/items/0")).put("external_data", "");
        ((ObjectNode) source.at("/cart/items/0/selected_modifier_groups/0/selected_items/1")).put("external_data", "");
        source.withObject("/cart").put("special_instructions", note);
        return source;
    }

    com.restaurant.system.printing.entity.StoreDevice fixtureDevice(FixtureStore value, String label) {
        var device = new com.restaurant.system.printing.entity.StoreDevice();
        device.storeId = value.store(); device.organizationId = value.organization();
        device.deviceName = "Fixture " + label; device.deviceType = "ANDROID_PAD";
        device.deviceTokenHash = "fixture-only-" + UUID.randomUUID();
        device.status = "ACTIVE"; device.isActive = true;
        device.createdAt = LocalDateTime.now(); device.updatedAt = device.createdAt;
        return devices.save(device);
    }

    void finishFixtureWork(FixtureStore... values) {
        for (var value : values) {
            db.update("update uber_eats_events set status='COMPLETED' where environment='sandbox' and uber_store_id=?", value.uberStore());
            db.update("update uber_eats_orders set next_attempt_at=null where store_id=?", value.store());
        }
    }

    @Test
    void grabSemanticParityWithPadAndMockOutboxIdempotency() {
        var source = payload(UUID.randomUUID().toString());
        var row = accept(notify(source));
        assertThat(row.status).isEqualTo("ACCEPTED");
        var request = new CreateOrderRequest();
        request.store_id = store;
        var padItem = new CreateOrderItemRequest();
        padItem.menu_item_id = item;
        padItem.quantity = 1;
        padItem.item_name_snapshot_zh = "牛肉面";
        padItem.item_name_snapshot_en = "Traditional Beef Noodle";
        padItem.item_sku_snapshot = "traditional_beef_noodle";
        padItem.category_code_snapshot = "SOUP_NOODLE";
        padItem.station_id_snapshot = station;
        padItem.item_type_snapshot = "food";
        padItem.unit_price_snapshot = new java.math.BigDecimal("12.50");
        padItem.combo_role = "standalone";
        for (String code : List.of("size_large", "fried_egg", "remove_cilantro")) {
            var stored =
                    db.queryForMap(
                            "select * from menu_item_options where menu_item_id=? and"
                                    + " option_code=?",
                            item,
                            code);
            var o = new CreateOrderItemOptionRequest();
            o.option_id = ((Number) stored.get("id")).longValue();
            o.option_code_snapshot = code;
            o.option_group_snapshot = (String) stored.get("option_group");
            o.option_type_snapshot = (String) stored.get("option_type");
            o.option_name_snapshot_zh = (String) stored.get("name_zh");
            o.option_name_snapshot_en = (String) stored.get("name_en");
            o.option_price_snapshot = (java.math.BigDecimal) stored.get("price_delta");
            o.quantity = 1;
            padItem.options.add(o);
        }
        request.items.add(padItem);
        request.order_type = "pickup";
        request.pickup_no = "P-FIXTURE";
        request.created_by = actorId;
        var pad = orderService.createOrReplaceDraftAndSubmit(request, null);
        var uberItems = items.findAllByOrderId(row.localOrderId);
        var padItems = items.findAllByOrderId(pad.id);
        assertThat(uberItems.get(0).item_name_snapshot_zh).isEqualTo("牛肉面");
        assertThat(tasks.findAllByOrderId(row.localOrderId).get(0).special_instructions_snapshot)
                .isEqualTo(tasks.findAllByOrderId(pad.id).get(0).special_instructions_snapshot)
                .contains("+煎", "走香");
        assertThat(render(row.localOrderId)).isEqualTo(render(pad.id)).contains("+煎", "走香");
        dispatch(row.localOrderId);
        dispatch(row.localOrderId);
        assertThat(count("print_jobs", "order_id", row.localOrderId)).isEqualTo(3);
        assertThat(
                        db.queryForList(
                                "select status from print_jobs where order_id=?",
                                String.class,
                                row.localOrderId))
                .containsOnly("PRINTED");
        assertThat(
                        db.queryForObject(
                                "select rendered_text_snapshot from print_jobs where order_id=? and"
                                        + " module_code='GRAB'",
                                String.class,
                                row.localOrderId))
                .contains("UBER EATS #FIX01", "+煎", "走香");
    }

    @Test
    void padDirectJobsPendingAndDisabledOrderSurvives() {
        db.update("update stores set printing_mode='PAD_DIRECT' where id=?", store);
        var row = accept(notify(payload(UUID.randomUUID().toString())));
        dispatch(row.localOrderId);
        assertThat(
                        db.queryForList(
                                "select status from print_jobs where order_id=?",
                                String.class,
                                row.localOrderId))
                .containsOnly("PENDING");
        assertThat(
                        db.queryForList(
                                "select rendered_text_snapshot from print_jobs where order_id=?",
                                String.class,
                                row.localOrderId))
                .allMatch(s -> s != null && !s.isBlank());
        db.update(
                "update stores set printing_mode='DISABLED',printing_enabled=false where id=?",
                store);
        var disabled = accept(notify(payload(UUID.randomUUID().toString())));
        dispatch(disabled.localOrderId);
        assertThat(orders.findById(disabled.localOrderId)).isPresent();
        assertThat(disabled.status).isEqualTo("ACCEPTED");
    }

    @Test
    void allModifierGroupsComboParentAndThreeItemIdentities() {
        id(
                "insert into stations(store_id,code,name,is_active) values (?,'COLD','凉菜',true)"
                        + " returning id",
                store);
        Long side =
                id(
                        "insert into"
                            + " menu_items(store_id,category_id,station_id,sku,name_zh,name_en,base_price,is_active,is_sold_out,item_type,sort_order)"
                            + " values"
                            + " (?,?,?,'cucumber_salad','拍黄瓜','Cucumber',4,true,false,'food',2)"
                            + " returning id",
                        store,
                        category,
                        station);
        Long rice =
                id(
                        "insert into"
                            + " menu_items(store_id,category_id,station_id,sku,name_zh,name_en,base_price,is_active,is_sold_out,item_type,sort_order)"
                            + " values (?,?,?,'fried_rice','炒饭','Fried"
                            + " Rice',10,true,false,'food',3) returning id",
                        store,
                        category,
                        station);
        option(side, "remove_garlic", "REMOVE", "remove", "走蒜", 0);
        option(item, "combo", "COMBO", "addon", "套餐", 5);
        Long eggGroup =
                id(
                        "insert into"
                            + " store_combo_groups(store_id,group_code,name_zh,name_en,selection_rule,required,enabled,display_order,created_at,updated_at)"
                            + " values"
                            + " (?,'COMBO_EGG','蛋','Egg','EXACTLY_ONE',true,true,1,now(),now())"
                            + " returning id",
                        store);
        Long sideGroup =
                id(
                        "insert into"
                            + " store_combo_groups(store_id,group_code,name_zh,name_en,selection_rule,required,enabled,display_order,created_at,updated_at)"
                            + " values"
                            + " (?,'COMBO_SIDE','小菜','Side','EXACTLY_ONE',true,true,2,now(),now())"
                            + " returning id",
                        store);
        db.update(
                "insert into"
                    + " store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,enabled,display_order,business_behavior,created_at,updated_at)"
                    + " values (?,?,'COMBO_EGG','combo_fried_egg','煎蛋','Fried"
                    + " egg',true,1,'NO_KITCHEN_TASK',now(),now())",
                store,
                eggGroup);
        db.update(
                "insert into"
                    + " store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,enabled,display_order,linked_menu_item_id,business_behavior,created_at,updated_at)"
                    + " values"
                    + " (?,?,'COMBO_SIDE','combo_cucumber_salad','拍黄瓜','Cucumber',true,1,?,'NO_KITCHEN_TASK',now(),now())",
                store,
                sideGroup,
                side);
        db.update(
                "update store_combo_groups set default_component_code='combo_fried_egg' where id=?",
                eggGroup);
        db.update(
                "update store_combo_groups set default_component_code='combo_cucumber_salad' where"
                        + " id=?",
                sideGroup);
        var rule = new UberEatsMenuMapping();
        rule.kind = "MODIFIER";
        rule.identifierType = "ID";
        rule.uberItemId = "uber-noodle";
        rule.uberIdentifier = "no-garlic";
        rule.localMenuItemId = item;
        rule.localOptionCode = "remove_garlic";
        rule.localOptionGroup = "COMBO_SIDE_REMOVE";
        rule.parentOptionCode = "combo_cucumber_salad";
        configuration.saveMapping(
                store,
                rule,
                new AuthenticatedUser(actorId, store, 1L, "fixture", "Fixture Owner", "OWNER"));
        var data = payload(UUID.randomUUID().toString());
        var mods = (ArrayNode) data.at("/cart/items/0/selected_modifier_groups/0/selected_items");
        addModifier(mods, "thin", "noodle_type_2", "Thin");
        addModifier(mods, "mild", "spicy_mild", "Mild");
        addModifier(mods, "combo", "combo", "Combo");
        addModifier(mods, "combo-egg", "combo_fried_egg", "Combo egg");
        addModifier(mods, "combo-side", "combo_cucumber_salad", "Combo cucumber");
        var child =
                ((ObjectNode) mods.get(mods.size() - 1))
                        .putArray("selected_modifier_groups")
                        .addObject()
                        .putArray("selected_items");
        addModifier(child, "no-garlic", "", "No garlic");
        var lines = (ArrayNode) data.at("/cart/items");
        lines.addObject()
                .put("id", "uber-side")
                .put("external_data", "cucumber_salad")
                .put("title", "Cucumber")
                .put("quantity", 1);
        lines.addObject()
                .put("id", "uber-rice")
                .put("external_data", "fried_rice")
                .put("title", "Rice")
                .put("quantity", 1);
        var mapped = mapping.map(binding, normalizer.normalize(data));
        assertThat(mapped.errors()).isEmpty();
        assertThat(mapped.request().items)
                .extracting(i -> i.item_name_snapshot_zh)
                .containsExactly("牛肉面", "拍黄瓜", "炒饭");
        assertThat(mapped.request().items.get(0).options)
                .extracting(o -> o.option_group_snapshot)
                .contains(
                        "SIZE",
                        "NOODLE_TYPE",
                        "SPICY_LEVEL",
                        "ADD_ON",
                        "REMOVE",
                        "COMBO",
                        "COMBO_EGG",
                        "COMBO_SIDE",
                        "COMBO_SIDE_REMOVE");
        var row = accept(notify(data));
        if ("LOCAL_FAILED".equals(row.status)) tx.submitLocal(row.id);
        assertThat(row.status).isEqualTo("ACCEPTED");
        assertThat(
                        options.findAllByOrderItemIds(
                                items.findAllByOrderId(row.localOrderId).stream()
                                        .map(i -> i.id)
                                        .toList()))
                .anyMatch(
                        o ->
                                "COMBO_SIDE_REMOVE".equals(o.option_group_snapshot)
                                        && "走蒜".equals(o.option_name_snapshot_zh));
        dispatch(row.localOrderId);
    }

    @Test
    void padDirectActualDatabaseClaimPayloadCompleteAndAmbiguousFailure() {
        db.update("update stores set printing_mode='PAD_DIRECT' where id=?", store);
        var row = accept(notify(payload(UUID.randomUUID().toString())));
        dispatch(row.localOrderId);
        var device = new com.restaurant.system.printing.entity.StoreDevice();
        device.storeId = store;
        device.organizationId = org;
        device.deviceName = "Fixture Pad";
        device.deviceType = "ANDROID_PAD";
        device.deviceTokenHash = "fixture-only-hash-" + UUID.randomUUID();
        device.status = "ACTIVE";
        device.isActive = true;
        device.createdAt = LocalDateTime.now();
        device.updatedAt = device.createdAt;
        device = devices.save(device);
        var pending = padPrinting.listPendingJobs(device, store, 25);
        assertThat(pending).hasSize(3);
        for (var job : pending) {
            var claim = new com.restaurant.system.printing.dto.PadPrintJobClaimRequest();
            claim.client_attempt_token = "fixture-" + job.id;
            claim.lease_seconds = 60;
            padPrinting.claimJob(device, job.id, claim);
            var start = new com.restaurant.system.printing.dto.PadPrintJobStartPrintRequest();
            start.client_attempt_token = claim.client_attempt_token;
            start.lease_seconds = 60;
            padPrinting.startPrint(device, job.id, start);
            var payload = padPrinting.getPayload(device, job.id);
            assertThat(payload.printer_endpoint).isEqualTo("127.0.0.1:9");
            assertThat(payload.escpos_payload_base64).isNotBlank();
            assertThat(payload.rendered_text_snapshot)
                    .contains("UBER EATS #FIX01")
                    .doesNotContain("Walk-in", "桌号");
            var done = new com.restaurant.system.printing.dto.PadPrintJobCompleteRequest();
            done.client_attempt_token = claim.client_attempt_token;
            done.raw_result = "SIMULATED_ACK_NO_PHYSICAL_PRINT";
            padPrinting.completeJob(device, job.id, done);
            padPrinting.completeJob(device, job.id, done);
        }
        assertThat(
                        db.queryForList(
                                "select status from print_jobs where order_id=?",
                                String.class,
                                row.localOrderId))
                .containsOnly("PRINTED");
        dispatch(row.localOrderId);
        assertThat(count("print_jobs", "order_id", row.localOrderId)).isEqualTo(3);
        var ambiguous = notify(payload(UUID.randomUUID().toString()));
        doThrow(new UberEatsApiException(0))
                .when(client)
                .accept(eq(ambiguous.uberOrderId), anyString());
        accept(ambiguous);
        due(ambiguous.id);
        imports.recover();
        assertThat(row(ambiguous.uberOrderId).status).isEqualTo("DECISION_REVIEW_REQUIRED");
        assertThatThrownBy(() -> accept(ambiguous)).isInstanceOf(UberEatsException.class);
        verify(client, times(1)).accept(eq(ambiguous.uberOrderId), anyString());
    }

    @Test
    void unsupportedStructuredRequestsAndRemovedDefaultsNeverDisappear() {
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode) data.at("/cart/items/0")).put("fulfillment_action", "REPLACE_FOR_ME");
        var result = mapping.map(binding, normalizer.normalize(data));
        assertThat(result.errors()).anyMatch(e -> e.contains("FULFILLMENT_ACTION"));
        ((ObjectNode) data.at("/cart/items/0")).remove("fulfillment_action");
        ((ObjectNode) data.at("/cart/items/0/selected_modifier_groups/0"))
                .putArray("removed_items")
                .addObject()
                .put("id", "default-herb")
                .put("title", "Default herb")
                .put("quantity", 0);
        assertThat(mapping.map(binding, normalizer.normalize(data)).errors())
                .anyMatch(e -> e.contains("MODIFIER_MAPPING_MISSING"));
        var rule = new UberEatsMenuMapping();
        rule.kind = "REMOVED_MODIFIER";
        rule.identifierType = "ID";
        rule.uberItemId = "uber-noodle";
        rule.uberIdentifier = "default-herb";
        rule.localMenuItemId = item;
        rule.localOptionCode = "remove_cilantro";
        rule.localOptionGroup = "REMOVE";
        configuration.saveMapping(
                store,
                rule,
                new AuthenticatedUser(actorId, store, 1L, "fixture", "Fixture Owner", "OWNER"));
        ((ArrayNode) data.at("/cart/items/0/selected_modifier_groups/0/selected_items")).remove(2);
        assertThat(mapping.map(binding, normalizer.normalize(data)).errors()).isEmpty();
        var accepted = accept(notify(data));
        assertThat(accepted.status).isEqualTo("ACCEPTED");
        assertThat(render(accepted.localOrderId)).contains("走香");
    }

    @Test
    void regularNotificationMonotonicallyReleasesScheduledOrdersInBothDeliveryOrders() {
        for (boolean regularFirst : List.of(true, false)) {
            String id = UUID.randomUUID().toString();
            when(client.getOrder(id)).thenReturn(payload(id));
            byte[] regular = event(UUID.randomUUID().toString(), "orders.notification", id);
            byte[] scheduled =
                    event(UUID.randomUUID().toString(), "orders.scheduled.notification", id);
            if (regularFirst) {
                receive(regular);
                receive(scheduled);
            } else {
                receive(scheduled);
                imports.processEvents();
                assertThat(row(id).status).isEqualTo("SCHEDULED_REVIEW_REQUIRED");
                receive(regular);
            }
            imports.processEvents();
            assertThat(row(id).status).isEqualTo("PENDING");
            receive(scheduled);
            receive(event(UUID.randomUUID().toString(), "orders.scheduled.notification", id));
            imports.processEvents();
            assertThat(row(id).scheduled).isFalse();
            assertThat(accept(row(id)).status).isEqualTo("ACCEPTED");
        }
    }

    @Test
    void blockedUberSchedulerDoesNotDelayDefaultPrintScheduler() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var defaultRan = new CountDownLatch(1);
        try {
            uberScheduler.execute(
                    () -> {
                        entered.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            defaultScheduler.schedule(defaultRan::countDown, java.time.Instant.now());
            assertThat(defaultRan.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(release.getCount()).isOne();
        } finally {
            release.countDown();
        }
    }

    @Test
    void denyCallsUberAndDoesNotCreateLocal() {
        var remote = payload(UUID.randomUUID().toString());
        remote.put("order_manager_client_id", "redacted-manager");
        var row = notify(remote);
        assertThat(imports.decide(store, row.id, actorId, "DENY", "CAPACITY").status)
                .isEqualTo("DENIED");
        imports.decide(store, row.id, actorId, "DENY", "CAPACITY");
        verify(client, times(1)).deny(row.uberOrderId, "CAPACITY");
        assertThat(count("orders", "store_id", store)).isZero();
    }

    @Test
    void remoteManagerPermissionDenialNeverCreatesLocalSideEffects() {
        var remote = payload(UUID.randomUUID().toString());
        remote.put("order_manager_client_id", "redacted-manager");
        var row = notify(remote);
        doThrow(new UberEatsApiException(403))
                .when(client)
                .accept(eq(row.uberOrderId), anyString());
        var result = accept(row);
        assertThat(result.localOrderId).isNull();
        assertThat(result.status).isEqualTo("PENDING");
        assertThat(count("orders", "store_id", store)).isZero();
        assertThat(count("kitchen_tasks", "store_id", store)).isZero();
        assertThat(count("print_jobs", "store_id", store)).isZero();
        verify(client, times(1)).accept(eq(row.uberOrderId), anyString());
    }

    @Test
    void durablePendingEventResumesWithNewServiceInstance() {
        String id = UUID.randomUUID().toString();
        when(client.getOrder(id)).thenReturn(payload(id));
        receive(event(UUID.randomUUID().toString(), "orders.notification", id));
        new UberEatsOrderImportService(newConfig(), client, normalizer, tx, events, inbox)
                .processEvents();
        assertThat(row(id).status).isEqualTo("PENDING");
    }

    UberEatsProperties newConfig() {
        var c = new UberEatsProperties();
        c.enabled = true;
        c.clientId = "fixture-client";
        return c;
    }

    @Autowired com.restaurant.system.printing.service.ManualReprintService manualReprint;
    @Autowired UberEatsKitchenView kitchenView;
    @Autowired com.restaurant.system.analytics.service.AnalyticsAggregationService analytics;

    void mirrorMode() {
        binding.processingMode = "KITCHEN_MIRROR";
        bindings.save(binding);
    }

    UberEatsOrder release(ObjectNode data) {
        String id = data.path("id").asText();
        data.put("current_state", "ACCEPTED");
        when(client.getOrder(id)).thenReturn(data);
        receive(event(UUID.randomUUID().toString(), "orders.release", id));
        imports.processEvents();
        imports.recover();
        return row(id);
    }

    @Test
    void mirrorNotificationSavesFinancialSnapshotWithoutAnyKitchenOrRemoteDecision() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        data.withObject("/eater").put("first_name", "Ashton").put("last_name", "Zhang");
        var charges = data.withObject("/payment/charges");
        for (String key : List.of("total", "sub_total", "tax", "total_fee"))
            charges.putObject(key)
                    .put("amount", key.equals("total") ? 1999 : 100)
                    .put("currency_code", "CAD");
        var row = notify(data);
        assertThat(row.processingMode).isEqualTo("KITCHEN_MIRROR");
        assertThat(row.status).isEqualTo("WAITING_FOR_ACCEPTANCE");
        assertThat(row.customerDisplayName).isEqualTo("Ashton Z");
        assertThat(row.financialTotalMinor).isEqualTo(1999);
        assertThat(row.financialCurrency).isEqualTo("CAD");
        assertThat(row.rawFinancialSnapshotJson).contains("1999");
        assertThat(row.itemFinancialSnapshotJson).isNull(); // CREATED is not a frozen sale.
        assertThat(row.rawOrderSnapshotJson).doesNotContain("private-not-stored", "Zhang");
        assertThat(count("orders", "store_id", store)).isZero();
        assertThat(count("order_dispatch_outbox", "store_id", store)).isZero();
        assertThatThrownBy(() -> imports.decide(store, row.id, actorId, "ACCEPT", null))
                .hasMessageContaining("KITCHEN_MIRROR_REMOTE_DECISION_DISABLED");
        assertThatThrownBy(() -> imports.decide(store, row.id, actorId, "DENY", "OTHER"))
                .hasMessageContaining("KITCHEN_MIRROR_REMOTE_DECISION_DISABLED");
        verify(client, never()).accept(anyString(), anyString());
        verify(client, never()).deny(anyString(), anyString());
        var released = release(data);
        assertThat(released.status).isEqualTo("RELEASED_TO_KITCHEN");
        assertThat(released.financialTotalMinor).isEqualTo(1999);
        assertThat(orders.findById(released.localOrderId).orElseThrow().total_amount).isZero();
        assertThat(items.findAllByOrderId(released.localOrderId))
                .allSatisfy(i -> assertThat(i.unit_price).isZero());
        dispatch(released.localOrderId);
        assertThat(jobs.findAllByStoreIdAndOrderId(store, released.localOrderId))
                .allSatisfy(
                        j ->
                                assertThat(j.rendered_text_snapshot)
                                        .contains("UBER - Ashton Z")
                                        .doesNotContain("Zhang", "private-not-stored"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MAPPED", "RAW", "MIXED"})
    void tenItemNotesKeepOrderScopeAndFrozenReprints(String mode) {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        String cartNote = "Please make everything less salty";
        data.withObject("/cart").put("special_instructions", "  Please make everything\r\n less  salty  ");
        ObjectNode template = ((ObjectNode) data.at("/cart/items/0")).deepCopy();
        var lines = (ArrayNode) data.at("/cart/items");
        lines.removeAll();
        for (int n = 0; n < 10; n++) {
            ObjectNode line = template.deepCopy().put("id", "note-root-" + n);
            if (mode.equals("RAW") || mode.equals("MIXED") && n >= 7)
                line.put("external_data", "").put("title", "Raw note item " + n);
            line.put("special_instructions", n == 0 ? "No cilantro" : n == 1 ? "Extra soup"
                    : n == 2 ? "Please make everything\nless salty" : n == 3 ? "no onion"
                    : n == 4 ? "no onion please" : "");
            ((ObjectNode) line.at("/selected_modifier_groups/0/selected_items/0"))
                    .put("special_instructions", n == 5 ? cartNote : "");
            lines.add(line);
        }
        var row = release(data);
        assertThat(row.localOrderId).isNotNull();
        var local = orders.findById(row.localOrderId).orElseThrow();
        assertThat(local.external_order_note_snapshot).isEqualTo(cartNote);
        var frozen = tx.decode(row.localRequestJson, CreateOrderRequest.class);
        assertThat(frozen.external_order_note_snapshot).isEqualTo(cartNote);
        assertThat(frozen.items).hasSize(10).allSatisfy(i -> {
            assertThat(i.notes == null ? "" : i.notes).doesNotContain(cartNote);
            if (i.external_kitchen_snapshot != null)
                assertThat(json.valueToTree(i.external_kitchen_snapshot).toString()).doesNotContain(cartNote);
        });
        assertThat(kitchenView.orderNote(row)).isEqualTo(cartNote);
        assertThat(kitchenView.rawItems(row)).doesNotContain("No cilantro", "Extra soup", cartNote);
        dispatch(row.localOrderId);
        var originals = jobs.findAllByStoreIdAndOrderId(store, row.localOrderId);
        assertThat(originals).hasSize(mode.equals("RAW") ? 1 : 2);
        assertThat(originals).allSatisfy(job -> {
            String rendered = job.rendered_text_snapshot;
            assertThat(rendered.split(java.util.regex.Pattern.quote(cartNote), -1).length - 1).isOne();
            assertThat(rendered).contains("订单备注：", "No cilantro", "Extra soup", "no onion", "no onion please");
            assertThat(rendered.indexOf("订单备注：")).isLessThan(rendered.lastIndexOf("外卖"));
        });
        // A later upstream response cannot change either the local or request note snapshot.
        data.withObject("/cart").put("special_instructions", "Changed upstream later");
        notify(data);
        assertThat(orders.findById(row.localOrderId).orElseThrow().external_order_note_snapshot).isEqualTo(cartNote);
        assertThat(row(row.uberOrderId).localRequestJson).isEqualTo(row.localRequestJson);
        db.update("update orders set external_order_note_snapshot='Changed local later' where id=?", row.localOrderId);
        db.update("update order_items set notes='Changed item later' where order_id=?", row.localOrderId);
        clearInvocations(client);
        for (var original : originals) {
            var request = new com.restaurant.system.printing.dto.OrderReprintRequest();
            request.receipt_type = original.module_code;
            request.idempotency_key = UUID.randomUUID().toString();
            var reprint = manualReprint.reprintOrder(row.localOrderId, request, actorId);
            assertThat(jobs.findById(reprint.id).orElseThrow().rendered_text_snapshot)
                    .isEqualTo(original.rendered_text_snapshot);
        }
        verifyNoInteractions(client);
    }

    @Test
    void itemOnlyNotesAndLegacyManagerOrderNoteArePreservedWithoutFuzzyDedupe() {
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode) data.at("/cart/items/0")).put("special_instructions", "No soup please");
        var preview = mapping.map(bindings.findById(binding.id).orElseThrow(), normalizer.normalize(data));
        assertThat(preview.request().external_order_note_snapshot).isEmpty();
        assertThat(preview.request().items.get(0).notes).isEqualTo("No soup please");
        data.withObject("/cart").put("special_instructions", "no onion");
        ((ObjectNode) data.at("/cart/items/0")).put("special_instructions", "no onion please");
        var accepted = accept(notify(data));
        assertThat(orders.findById(accepted.localOrderId).orElseThrow().external_order_note_snapshot).isEqualTo("no onion");
        assertThat(items.findAllByOrderId(accepted.localOrderId).get(0).notes).isEqualTo("no onion please");
        dispatch(accepted.localOrderId);
        assertThat(jobs.findByDispatchSourceKey("submit:" + accepted.localOrderId + ":GRAB").orElseThrow().rendered_text_snapshot)
                .contains("订单备注：", "no onion please");
    }

    @Test
    void heldAcceptedFinancialSnapshotCannotBeRepricedByRefreshOrRemap() throws Exception {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        data.put("current_state", "ACCEPTED");
        data.withObject("/cart").putArray("fulfillment_issues").add("review");
        var charges = data.withObject("/payment/charges");
        charges.putObject("sub_total").put("amount", 1000).put("currency_code", "CAD");
        charges.putObject("tax").put("amount", 150).put("currency_code", "CAD");
        charges.putObject("total").put("amount", 1150).put("currency_code", "CAD");
        ((ObjectNode) data.path("cart").path("items").get(0)).putObject("price")
            .putObject("total_price").put("amount", 1000).put("currency_code", "CAD");
        var held = notify(data);
        assertThat(held.status).isEqualTo("RELEASED_MAPPING_REQUIRED");
        assertThat(held.localOrderId).isNull();
        String frozen = held.itemFinancialSnapshotJson;
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(frozen).path("revenue_minor").asLong()).isEqualTo(1150);
        charges.withObject("/sub_total").put("amount", 2000);
        charges.withObject("/tax").put("amount", 300);
        charges.withObject("/total").put("amount", 2300);
        ((ObjectNode) data.path("cart").path("items").get(0).path("price").path("total_price")).put("amount", 2000);
        tx.remapStore(store);
        tx.refreshMirror(held.id, normalizer.normalize(data));
        assertThat(row(held.uberOrderId).itemFinancialSnapshotJson).isEqualTo(frozen);
        assertThat(row(held.uberOrderId).localOrderId).isNull();
        assertThat(count("orders", "store_id", store)).isZero();
        assertThat(count("print_jobs", "store_id", store)).isZero();
    }

    @Test
    void
            mirrorReleaseWithoutNotificationDuplicateConcurrentAndRestartCreateExactlyOneKitchenPipeline()
                    throws Exception {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var row = release(data);
        assertThat(row.status).isEqualTo("RELEASED_TO_KITCHEN");
        Long local = row.localOrderId;
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> tx.submitLocal(row.id));
            var b = pool.submit(() -> tx.submitLocal(row.id));
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        release(data);
        notify(data);
        new UberEatsOrderImportService(newProperties(), client, normalizer, tx, events, inbox)
                .recover();
        assertThat(row(data.path("id").asText()).localOrderId).isEqualTo(local);
        assertThat(count("orders", "store_id", store)).isOne();
        assertThat(count("kitchen_tasks", "order_id", local)).isOne();
        assertThat(count("production_tasks", "order_id", local)).isOne();
        assertThat(count("inventory_transactions", "source_id", local)).isOne();
        assertThat(
                        db.queryForList(
                                "select module_code from order_dispatch_outbox where order_id=?",
                                String.class,
                                local))
                .containsExactlyInAnyOrder("GRAB", "HOT_KITCHEN");
        dispatch(local);
        dispatch(local);
        assertThat(count("print_jobs", "order_id", local)).isEqualTo(2);
        assertThat(kitchenView.state(row(data.path("id").asText())).status()).isEqualTo("PRINTED");
        assertThat(jobs.findAllByStoreIdAndOrderId(store, local))
                .allSatisfy(j -> assertThat(j.rendered_text_snapshot).contains("UBER - FIX01"));
        verify(client, never()).accept(anyString(), anyString());
        verify(client, never()).deny(anyString(), anyString());
    }

    UberEatsProperties newProperties() {
        var p = new UberEatsProperties();
        p.enabled = true;
        return p;
    }

    @Test
    void unmappedRootReleasesRawGrabAndLaterMappingCannotRewriteHistory() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode) data.at("/cart/items/0")).put("external_data", "").put("title", "Lanzhou Beef Chow Mein (Beef)");
        var released = release(data);
        assertThat(released.status).isEqualTo("KITCHEN_SENT_WITH_MAPPING_WARNINGS");
        assertThat(released.mappingStatus).isEqualTo("PARTIALLY_MAPPED");
        assertThat(released.mappingError).contains("UNMAPPED_ROUTE_REVIEW");
        assertThat(count("orders", "store_id", store)).isOne();
        assertThat(count("inventory_transactions", "source_id", released.localOrderId)).isZero();
        var raw = items.findAllByOrderId(released.localOrderId).get(0);
        assertThat(raw.menu_item_id).isNull();
        assertThat(raw.externalKitchenSnapshot.itemId()).isEqualTo("uber-noodle");
        assertThat(tasks.findAllByOrderId(released.localOrderId)).allSatisfy(t -> assertThat(t.station_code).isEqualTo("RAW_UBER_FALLBACK"));
        dispatch(released.localOrderId);
        var printed = jobs.findAllByStoreIdAndOrderId(store, released.localOrderId);
        assertThat(printed).hasSize(1);
        assertThat(printed.get(0).module_code).isEqualTo("GRAB");
        assertThat(printed.get(0).rendered_text_snapshot).contains("Lanzhou Beef Chow Mein (Beef)", "Large", "Fried Egg", "No Cilantro", "未映射 Uber 菜", "外卖");
        var rule = new UberEatsMenuMapping(); rule.kind = "ITEM"; rule.identifierType = "ID";
        rule.uberIdentifier = "uber-noodle"; rule.localMenuItemId = item;
        configuration.saveMapping(store, rule, actor);
        imports.recover();
        var request = new com.restaurant.system.printing.dto.OrderReprintRequest();
        request.receipt_type = "GRAB"; request.idempotency_key = UUID.randomUUID().toString();
        var copy = manualReprint.reprintOrder(released.localOrderId, request, actorId);
        assertThat(jobs.findById(copy.id).orElseThrow().rendered_text_snapshot).isEqualTo(printed.get(0).rendered_text_snapshot);
        assertThat(count("orders", "store_id", store)).isOne();
        verify(client, never()).accept(anyString(), anyString());
    }

    @Test
    void mixedKnownAndRawLinesPreserveEveryUnknownModifierAndOnlyKnownHotRouting() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var selected = (ArrayNode) data.at("/cart/items/0/selected_modifier_groups/0/selected_items");
        addModifier(selected, "unknown-mod", "", "Unknown Modifier");
        var nested = ((ObjectNode) selected.get(3)).putArray("selected_modifier_groups").addObject().putArray("selected_items");
        addModifier(nested, "child-mod", "", "Nested Unknown");
        var raw = ((ArrayNode)data.at("/cart/items")).addObject();
        raw.put("id", "unknown-root").put("title", "Unknown Uber Item").put("quantity", 2);
        addModifier(raw.putArray("selected_modifier_groups").addObject().putArray("selected_items"), "raw-egg", "", "Add Fried Egg");
        var released = release(data); dispatch(released.localOrderId);
        assertThat(released.status).isEqualTo("KITCHEN_SENT_WITH_MAPPING_WARNINGS");
        assertThat(items.findAllByOrderId(released.localOrderId)).hasSize(2);
        var known = items.findAllByOrderId(released.localOrderId).stream().filter(i -> i.menu_item_id != null).findFirst().orElseThrow();
        assertThat(known.item_sku_snapshot).isEqualTo("traditional_beef_noodle");
        assertThat(options.findAllByOrderItemIds(List.of(known.id))).extracting(o -> o.option_code_snapshot).containsExactlyInAnyOrder("size_large", "fried_egg", "remove_cilantro");
        var grabJob = jobs.findByDispatchSourceKey("submit:"+released.localOrderId+":GRAB").orElseThrow();
        var hotJob = jobs.findByDispatchSourceKey("submit:"+released.localOrderId+":HOT_KITCHEN").orElseThrow();
        assertThat(grabJob.rendered_text_snapshot).contains("Unknown Modifier x1", "Nested Unknown x1", "Unknown Uber Item x2", "Add Fried Egg x1", "外卖");
        assertThat(hotJob.rendered_text_snapshot).contains("Unknown Modifier", "Nested Unknown", "外卖").doesNotContain("Unknown Uber Item", "Add Fried Egg");
        assertThat(jobs.findAllByStoreIdAndOrderId(store, released.localOrderId)).hasSize(2);
        assertThat(kitchenView.rawItems(released)).contains("Unknown Uber Item ×2", "Unknown Modifier ×1");
        assertThat(kitchenView.state(released).status()).isEqualTo("KITCHEN_SENT_WITH_MAPPING_WARNINGS");
    }

    @Test
    void unknownChildRetainsKnownParentContextWithoutDuplicatingMappedParent() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var egg = (ObjectNode)data.at("/cart/items/0/selected_modifier_groups/0/selected_items/1");
        addModifier(egg.putArray("selected_modifier_groups").addObject().putArray("selected_items"),
                "unknown-child", "", "No Salt");
        var row = release(data); dispatch(row.localOrderId);
        var line = items.findAllByOrderId(row.localOrderId).get(0);
        assertThat(options.findAllByOrderItemIds(List.of(line.id)).stream()
                .filter(o -> "fried_egg".equals(o.option_code_snapshot))).hasSize(1);
        var raw = line.externalKitchenSnapshot.modifiers().get(0);
        assertThat(raw.id()).isEqualTo("unknown-child");
        assertThat(raw.parentContext()).hasSize(1);
        assertThat(raw.parentContext().get(0).id()).isEqualTo(egg.path("id").asText());
        var text = jobs.findByDispatchSourceKey("submit:"+row.localOrderId+":GRAB").orElseThrow().rendered_text_snapshot;
        assertThat(text).contains("（选项归属：Fried Egg）", "No Salt x1").doesNotContain("Fried Egg x1");
        assertThat(kitchenView.rawItems(row)).contains("选项归属：Fried Egg", "No Salt ×1");
    }

    @Test
    void unknownRequiredSizeKeepsKnownEggAndRemoveAndPreservesRawChoice() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode)data.at("/cart/items/0/selected_modifier_groups/0/selected_items/0"))
            .put("id", "unknown-size").put("external_data", "").put("title", "Unknown Size");
        var row = release(data);
        assertThat(row.status).isEqualTo("KITCHEN_SENT_WITH_MAPPING_WARNINGS");
        var line = items.findAllByOrderId(row.localOrderId).get(0);
        assertThat(options.findAllByOrderItemIds(List.of(line.id))).extracting(o -> o.option_code_snapshot)
            .containsExactlyInAnyOrder("fried_egg", "remove_cilantro");
        dispatch(row.localOrderId);
        assertThat(jobs.findByDispatchSourceKey("submit:"+row.localOrderId+":GRAB").orElseThrow().rendered_text_snapshot)
            .contains("Unknown Size", "外卖").doesNotContain("Fried Egg", "No Cilantro");
    }

    @Test
    void rawFallbackDoesNotAllowInvalidNestedQuantityOrOrdinaryOrderInjection() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode)data.at("/cart/items/0")).put("id", "raw-root").put("external_data", "");
        ((ObjectNode)data.at("/cart/items/0/selected_modifier_groups/0/selected_items/0")).put("quantity", -1);
        var row = release(data);
        assertThat(row.localOrderId).isNull();
        assertThat(row.status).isEqualTo("RELEASED_MAPPING_REQUIRED");
        var safe = payload(UUID.randomUUID().toString());
        ((ObjectNode)safe.at("/cart/items/0")).put("id", "raw-root").put("external_data", "");
        var request = mapping.mapMirror(binding, normalizer.normalize(safe)).request();
        request.external_order_note_snapshot = null; // Isolate the raw-item injection guard.
        assertThatThrownBy(() -> orderService.createOrReplaceDraftAndSubmit(request, null))
            .hasMessageContaining("External kitchen fallback requires Kitchen Mirror");
        assertThat(count("orders", "store_id", store)).isZero();
    }

    @Test
    void normalAndMirrorHotJobsShareRealDevicePendingClaimStartPayloadCompleteApi() throws Exception {
        db.update("update stores set printing_mode='PAD_DIRECT' where id=?", store);
        var normalRequest = mapping.map(binding, normalizer.normalize(payload(UUID.randomUUID().toString()))).request();
        normalRequest.order_type = "takeout";
        normalRequest.external_order_note_snapshot = null; // Ordinary Pad has no external metadata.
        var normal = orderService.createOrReplaceDraftAndSubmit(normalRequest, null); dispatch(normal.id);
        mirrorMode();
        var mirror = release(payload(UUID.randomUUID().toString())); dispatch(mirror.localOrderId);
        var device = new com.restaurant.system.printing.entity.StoreDevice();
        device.storeId=store; device.organizationId=org; device.deviceName="Fixture Pad"; device.deviceType="PAD"; device.platform="ANDROID";
        String token="fixture-device-"+UUID.randomUUID();
        device.deviceTokenHash=Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        device.status="ACTIVE"; device.isActive=true; device.createdAt=LocalDateTime.now(); device.updatedAt=device.createdAt;
        device=devices.save(device);
        var result=mvc.perform(get("/api/v1/stores/"+store+"/printing/jobs/pending").header("X-Device-Id",device.id).header("X-Device-Token",token)).andExpect(status().isOk()).andReturn();
        var ids=new HashSet<Long>(); json.readTree(result.getResponse().getContentAsString()).path("data").forEach(j->ids.add(j.path("id").asLong()));
        for (Long orderId : List.of(normal.id, mirror.localOrderId)) {
            var job=jobs.findByDispatchSourceKey("submit:"+orderId+":HOT_KITCHEN").orElseThrow();
            assertThat(ids).contains(job.id);
            String body="{\"client_attempt_token\":\"fixture-attempt-"+job.id+"\"}";
            for(String step:List.of("claim","start-print")) mvc.perform(post("/api/v1/printing/jobs/"+job.id+"/"+step).header("X-Device-Id",device.id).header("X-Device-Token",token).contentType("application/json").content(body)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/printing/jobs/"+job.id+"/payload").header("X-Device-Id",device.id).header("X-Device-Token",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.printer_host").value("127.0.0.1"));
            mvc.perform(post("/api/v1/printing/jobs/"+job.id+"/complete").header("X-Device-Id",device.id).header("X-Device-Token",token).contentType("application/json").content(body)).andExpect(status().isOk());
            assertThat(jobs.findById(job.id).orElseThrow().status).isEqualTo("PRINTED");
        }
        assertThat(jobs.findAllByStoreIdAndOrderId(store, mirror.localOrderId)).noneMatch(j->"FRONTDESK_RECEIPT".equals(j.module_code));
    }

    @Test
    void mirrorFailedLocalTransactionRollsBackAndRestartRecoveryNeverAccepts() {
        mirrorMode();
        db.update("update stations set is_active=false where id=?", station);
        var row = release(payload(UUID.randomUUID().toString()));
        assertThat(row.status).isEqualTo("MIRROR_LOCAL_FAILED");
        assertThat(count("orders", "store_id", store)).isZero();
        assertThat(count("order_dispatch_outbox", "store_id", store)).isZero();
        db.update("update stations set is_active=true where id=?", station);
        due(row.id);
        new UberEatsOrderImportService(newProperties(), client, normalizer, tx, events, inbox)
                .recover();
        assertThat(row(row.uberOrderId).status).isEqualTo("RELEASED_TO_KITCHEN");
        assertThat(count("orders", "store_id", store)).isOne();
        verify(client, never()).accept(anyString(), anyString());
    }

    @Test
    void mirrorCancelBeforeReleaseAndCancelEditAfterReleasePreserveKitchenHistory() {
        mirrorMode();
        var beforeData = payload(UUID.randomUUID().toString());
        var before = notify(beforeData);
        receive(event(UUID.randomUUID().toString(), "orders.cancel", before.uberOrderId));
        release(beforeData);
        assertThat(row(before.uberOrderId).status).isEqualTo("CANCELLED");
        assertThat(count("orders", "store_id", store)).isZero();
        var after = release(payload(UUID.randomUUID().toString()));
        dispatch(after.localOrderId);
        receive(event(UUID.randomUUID().toString(), "orders.cancel", after.uberOrderId));
        assertThat(row(after.uberOrderId).status).isEqualTo("CANCELLED_AFTER_RELEASE");
        assertThat(kitchenView.state(row(after.uberOrderId)).status())
                .isEqualTo("CANCELLED_AFTER_RELEASE");
        receive(
                event(
                        UUID.randomUUID().toString(),
                        "orders.customer_order_edit",
                        after.uberOrderId));
        imports.processEvents();
        assertThat(row(after.uberOrderId).status).isEqualTo("EDIT_REVIEW_REQUIRED");
        assertThat(count("print_jobs", "order_id", after.localOrderId)).isEqualTo(2);
        assertThat(count("kitchen_tasks", "order_id", after.localOrderId)).isOne();
    }

    @Test
    void mirrorGrabOnlyKitchenReprintsAreSnapshotSafeAndReceiptsAndCashierAreRejected() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var mods = (ArrayNode) data.at("/cart/items/0/selected_modifier_groups/0/selected_items");
        mods.remove(1); // no fried egg -> no HOT_KITCHEN
        var row = release(data);
        dispatch(row.localOrderId);
        assertThat(jobs.findAllByStoreIdAndOrderId(store, row.localOrderId))
                .extracting(j -> j.module_code)
                .containsExactly("GRAB");
        assertThat(kitchenView.state(row).hotKitchen()).isEqualTo("NOT_REQUIRED");
        var original = jobs.findAllByStoreIdAndOrderId(store, row.localOrderId).get(0);
        db.update("update menu_items set name_zh='后来改名' where id=?", item);
        var request = new com.restaurant.system.printing.dto.OrderReprintRequest();
        request.receipt_type = "GRAB";
        request.idempotency_key = UUID.randomUUID().toString();
        var reprinted = manualReprint.reprintOrder(row.localOrderId, request, actorId);
        var replay = manualReprint.reprintOrder(row.localOrderId, request, actorId);
        assertThat(replay.id).isEqualTo(reprinted.id);
        assertThat(jobs.findById(reprinted.id).orElseThrow().rendered_text_snapshot)
                .isEqualTo(original.rendered_text_snapshot);
        request.receipt_type = "FRONTDESK_RECEIPT";
        assertThatThrownBy(() -> manualReprint.reprintOrder(row.localOrderId, request, actorId))
                .hasMessageContaining("only GRAB");
        assertThatThrownBy(
                        () ->
                                dispatcher.dispatchPersistedEvent(
                                        "FRONTDESK_RECEIPT",
                                        store,
                                        row.localOrderId,
                                        null,
                                        "forbidden-receipt"))
                .hasMessageContaining("only GRAB");
        assertThatThrownBy(() -> orderService.completeOrder(row.localOrderId))
                .hasMessageContaining("cashier");
        assertThatThrownBy(() -> orderService.cancelOrder(row.localOrderId))
                .hasMessageContaining("cashier");
        assertThat(orderService.getFrontdeskOrderBoard(store, null, null, null, null, null))
                .isEmpty();
        assertThat(dispatcher.getOrderPrintOptions(row.localOrderId))
                .extracting(o -> o.module_code)
                .doesNotContain("FRONTDESK_RECEIPT");
    }

    @Test
    void mirrorHotReprintUsesOriginalSnapshotAndPrintFailuresRemainVisible() {
        mirrorMode();
        var row = release(payload(UUID.randomUUID().toString()));
        dispatch(row.localOrderId);
        var original =
                jobs.findByDispatchSourceKey("submit:" + row.localOrderId + ":HOT_KITCHEN")
                        .orElseThrow();
        db.update("update menu_item_options set name_zh='后来修改' where menu_item_id=?", item);
        var request = new com.restaurant.system.printing.dto.OrderReprintRequest();
        request.receipt_type = "HOT_KITCHEN";
        request.idempotency_key = UUID.randomUUID().toString();
        var newJob = manualReprint.reprintOrder(row.localOrderId, request, actorId);
        assertThat(jobs.findById(newJob.id).orElseThrow().rendered_text_snapshot)
                .isEqualTo(original.rendered_text_snapshot);
        db.update(
                "update print_jobs set status='FAILED' where order_id=? and"
                        + " module_code='HOT_KITCHEN'",
                row.localOrderId);
        assertThat(kitchenView.state(row).status()).isEqualTo("PRINT_PARTIAL");
        db.update("update print_jobs set status='FAILED' where order_id=?", row.localOrderId);
        assertThat(kitchenView.state(row).status()).isEqualTo("PRINT_FAILED");
    }

    @Test
    void mirrorFinancialSnapshotIsExcludedFromEveryInStoreAnalyticsSummary() {
        mirrorMode();
        var row = release(payload(UUID.randomUUID().toString()));
        db.update(
                "update orders set"
                    + " status='completed',completed_at=now(),subtotal_amount=999,total_amount=999"
                    + " where id=?",
                row.localOrderId);
        analytics.rebuildForDate(LocalDate.now(), store);
        assertThat(
                        db.queryForObject(
                                "select gross_sales from sales_daily_summary where store_id=?",
                                java.math.BigDecimal.class,
                                store))
                .isZero();
        assertThat(count("menu_item_sales_summary", "store_id", store)).isZero();
        assertThat(
                        db.queryForObject(
                                "select sales_amount from store_performance_summary where"
                                        + " store_id=?",
                                java.math.BigDecimal.class,
                                store))
                .isZero();
    }

    @Test
    void mirrorReleaseRejectsCrossStoreFreshResponseAndUnacceptedState() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        String id = data.path("id").asText();
        when(client.getOrder(id)).thenReturn(data);
        receive(event(UUID.randomUUID().toString(), "orders.release", id));
        imports.processEvents();
        imports.recover();
        assertThat(row(id).status).isEqualTo("WAITING_FOR_ACCEPTANCE");
        data.put("current_state", "ACCEPTED");
        data.withObject("/store").put("id", UUID.randomUUID().toString());
        receive(event(UUID.randomUUID().toString(), "orders.release", id));
        imports.processEvents();
        imports.recover();
        assertThat(count("orders", "store_id", store)).isZero();
    }

    @Test
    void mirrorExplicitNoOpResolvesOnlyConfiguredParentAndUnknownModifierStillBlocks() {
        mirrorMode();
        db.update("update menu_items set sku='beef_chow_mein' where id=?", item);
        for (String modifier :
                List.of(
                        "54c53703-db68-4044-85a3-5f4962715566",
                        "d1f4a19b-fd7c-4cdf-863a-581391172f23")) {
            var rule = new UberEatsMenuMapping();
            rule.kind = "MODIFIER";
            rule.identifierType = "ID";
            rule.uberIdentifier = modifier;
            rule.uberItemId = "uber-noodle";
            rule.localMenuItemId = item;
            rule.mappingAction = "NO_OP";
            rule.actionReason = "INGREDIENT_NOT_USED";
            configuration.saveMapping(store, rule, actor);
        }
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode) data.at("/cart/items/0")).put("external_data", "beef_chow_mein");
        var mods = (ArrayNode) data.at("/cart/items/0/selected_modifier_groups/0/selected_items");
        mods.removeAll();
        addModifier(mods, "large", "size_large", "Large");
        addModifier(mods, "54c53703-db68-4044-85a3-5f4962715566", "", "Non Coriander");
        addModifier(mods, "d1f4a19b-fd7c-4cdf-863a-581391172f23", "", "Non Scallion");
        var mapped = mapping.map(binding, normalizer.normalize(data));
        assertThat(mapped.errors()).isEmpty();
        assertThat(mapped.request().items.get(0).options)
                .extracting(o -> o.option_code_snapshot)
                .containsExactly("size_large");
        addModifier(mods, "unknown", "", "Unknown modifier");
        assertThat(mapping.map(binding, normalizer.normalize(data)).valid()).isFalse();
        mods.remove(mods.size() - 1);
        ((ObjectNode) data.at("/cart/items/0")).put("id", "another-parent");
        assertThat(mapping.map(binding, normalizer.normalize(data)).valid()).isFalse();
    }

    @Test
    void mirrorComboRootInjectsComboWithBothEggsAndThreeConfirmedSides() {
        mirrorMode();
        id(
                "insert into stations(store_id,code,name,is_active) values (?,'COLD','凉菜',true)"
                        + " returning id",
                store);
        Long side =
                id(
                        "insert into"
                            + " menu_items(store_id,category_id,station_id,sku,name_zh,name_en,base_price,is_active,is_sold_out,item_type,sort_order)"
                            + " values"
                            + " (?,?,?,'cucumber_salad','拍黄瓜','Cucumber',4,true,false,'food',2)"
                            + " returning id",
                        store,
                        category,
                        station);
        Long rice =
                id(
                        "insert into"
                            + " menu_items(store_id,category_id,station_id,sku,name_zh,name_en,base_price,is_active,is_sold_out,item_type,sort_order)"
                            + " values (?,?,?,'fried_rice','炒饭','Fried"
                            + " Rice',10,true,false,'food',3) returning id",
                        store,
                        category,
                        station);
        option(side, "remove_garlic", "REMOVE", "remove", "走蒜", 0);
        option(item, "combo", "COMBO", "addon", "套餐", 5);
        Long eggGroup =
                id(
                        "insert into"
                            + " store_combo_groups(store_id,group_code,name_zh,name_en,selection_rule,required,enabled,display_order,created_at,updated_at)"
                            + " values"
                            + " (?,'COMBO_EGG','蛋','Egg','EXACTLY_ONE',true,true,1,now(),now())"
                            + " returning id",
                        store);
        Long sideGroup =
                id(
                        "insert into"
                            + " store_combo_groups(store_id,group_code,name_zh,name_en,selection_rule,required,enabled,display_order,created_at,updated_at)"
                            + " values"
                            + " (?,'COMBO_SIDE','小菜','Side','EXACTLY_ONE',true,true,2,now(),now())"
                            + " returning id",
                        store);
        db.update(
                "insert into"
                    + " store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,enabled,display_order,business_behavior,created_at,updated_at)"
                    + " values (?,?,'COMBO_EGG','combo_fried_egg','煎蛋','Fried"
                    + " egg',true,1,'NO_KITCHEN_TASK',now(),now())",
                store,
                eggGroup);
        db.update(
                "insert into"
                    + " store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,enabled,display_order,linked_menu_item_id,business_behavior,created_at,updated_at)"
                    + " values"
                    + " (?,?,'COMBO_SIDE','combo_cucumber_salad','拍黄瓜','Cucumber',true,1,?,'NO_KITCHEN_TASK',now(),now())",
                store,
                sideGroup,
                side);
        db.update(
                "update store_combo_groups set default_component_code='combo_fried_egg' where id=?",
                eggGroup);
        db.update(
                "update store_combo_groups set default_component_code='combo_cucumber_salad' where"
                        + " id=?",
                sideGroup);

        db.update(
                "insert into"
                    + " store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,enabled,display_order,business_behavior,created_at,updated_at)"
                    + " values (?,?,'COMBO_EGG','combo_tea_egg','茶叶蛋','Tea"
                    + " egg',true,2,'NO_KITCHEN_TASK',now(),now())",
                store,
                eggGroup);
        for (String sku : List.of("edamame", "shredded_potato")) {
            Long sideItem =
                    id(
                            "insert into"
                                + " menu_items(store_id,category_id,station_id,sku,name_zh,name_en,base_price,is_active,is_sold_out,item_type,sort_order)"
                                + " values (?,?,?,?,'小菜',?,4,true,false,'food',4) returning id",
                            store,
                            category,
                            station,
                            sku,
                            sku);
            db.update(
                    "insert into"
                        + " store_combo_components(store_id,group_id,component_group,component_code,name_zh,name_en,enabled,display_order,linked_menu_item_id,business_behavior,created_at,updated_at)"
                        + " values"
                        + " (?,?,'COMBO_SIDE',?,'小菜',?,true,2,?,'NO_KITCHEN_TASK',now(),now())",
                    store,
                    sideGroup,
                    "combo_" + sku,
                    sku,
                    sideItem);
        }
        var rule = new UberEatsMenuMapping();
        rule.kind = "ITEM";
        rule.identifierType = "ID";
        rule.uberIdentifier = "uber-noodle";
        rule.localMenuItemId = item;
        rule.itemMappingMode = "COMBO_ROOT";
        configuration.saveMapping(store, rule, actor);
        for (String egg : List.of("combo_tea_egg", "combo_fried_egg")) {
            for (String sideCode :
                    List.of("combo_cucumber_salad", "combo_edamame", "combo_shredded_potato")) {
                var data = payload(UUID.randomUUID().toString());
                ((ObjectNode) data.at("/cart/items/0")).put("external_data", "");
                var mods =
                        (ArrayNode)
                                data.at("/cart/items/0/selected_modifier_groups/0/selected_items");
                mods.removeAll();
                addModifier(mods, "large", "size_large", "Large");
                addModifier(mods, "egg-component", egg, egg);
                addModifier(mods, "side-component", sideCode, sideCode);
                var mapped = mapping.map(binding, normalizer.normalize(data));
                assertThat(mapped.errors()).isEmpty();
                assertThat(mapped.request().items.get(0).options)
                        .extracting(o -> o.option_code_snapshot)
                        .containsExactlyInAnyOrder("combo", "size_large", egg, sideCode);
                var row = release(data);
                assertThat(row.status).isEqualTo("RELEASED_TO_KITCHEN");
                assertThat(
                                options.findAllByOrderItemIds(
                                        items.findAllByOrderId(row.localOrderId).stream()
                                                .map(i -> i.id)
                                                .toList()))
                        .extracting(o -> o.option_code_snapshot)
                        .contains("combo", egg, sideCode);
                mapped.request().external_order_note_snapshot = null;
                var pad = orderService.createOrReplaceDraftAndSubmit(mapped.request(), null);
                assertThat(tasks.findAllByOrderId(row.localOrderId))
                        .extracting(t -> t.special_instructions_snapshot)
                        .containsExactlyElementsOf(
                                tasks.findAllByOrderId(pad.id).stream()
                                        .map(t -> t.special_instructions_snapshot)
                                        .toList());
                assertThat(render(row.localOrderId)).isEqualTo(render(pad.id));
                assertThat(
                                db.queryForList(
                                        "select module_code from order_dispatch_outbox where"
                                                + " order_id=?",
                                        String.class,
                                        row.localOrderId))
                        .doesNotContain("FRONTDESK_RECEIPT");
            }
        }
    }

    @Test
    void confirmedNoodleStableIdsPreserveDistinctChineseKitchenSemantics() {
        String[][] noodles = {
            {"Fine", "noodle_capillary", "毛细"},
            {"One Fine", "noodle_thin", "细"},
            {"Two Fine", "noodle_erxi", "二细"},
            {"Three Fine", "noodle_sanxi", "三细"},
            {"Leek Leaves", "noodle_leek_leaf", "韭叶"},
            {"Wide", "noodle_wide", "宽"},
            {"Big Wide", "noodle_extra_wide", "大宽"}
        };
        for (String[] noodle : noodles) {
            option(item, noodle[1], "NOODLE_TYPE", "noodle_type", noodle[2], 0);
            var rule = new UberEatsMenuMapping();
            rule.kind = "MODIFIER";
            rule.identifierType = "ID";
            rule.uberIdentifier = "stable-" + noodle[1];
            rule.uberItemId = "uber-noodle";
            rule.localMenuItemId = item;
            rule.localOptionCode = noodle[1];
            rule.localOptionGroup = "NOODLE_TYPE";
            configuration.saveMapping(store, rule, actor);
            var data = payload(UUID.randomUUID().toString());
            var mods =
                    (ArrayNode) data.at("/cart/items/0/selected_modifier_groups/0/selected_items");
            addModifier(mods, rule.uberIdentifier, "", noodle[0]);
            var mapped = mapping.map(binding, normalizer.normalize(data));
            assertThat(mapped.errors()).isEmpty();
            assertThat(mapped.request().items.get(0).options)
                    .anySatisfy(
                            o -> {
                                assertThat(o.option_code_snapshot).isEqualTo(noodle[1]);
                                assertThat(o.option_name_snapshot_zh).isEqualTo(noodle[2]);
                            });
        }
    }

    @Test
    void mirrorTodayQueryHonorsUtcReleaseAndLegacyCreationAcrossMidnightAndDst() {
        mirrorMode();
        var row = release(payload(UUID.randomUUID().toString()));
        for (LocalDate date :
                List.of(
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 3, 8),
                        LocalDate.of(2026, 11, 1))) {
            var zone = ZoneId.of("America/Toronto");
            var day = UberEatsInboxDay.of(date, zone, zone);
            var at =
                    date.atTime(0, 30)
                            .atZone(zone)
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime();
            db.update(
                    "update uber_eats_orders set"
                        + " kitchen_dispatched_at=null,accepted_observed_at=null,released_at=?,status='RELEASED_TO_KITCHEN'"
                        + " where id=?",
                    at,
                    row.id);
            assertThat(
                            inbox.todayInbox(
                                    "sandbox",
                                    store,
                                    day.start(),
                                    day.end(),
                                    day.createdStart(),
                                    day.createdEnd(),
                                    PageRequest.of(0, 200)))
                    .extracting(o -> o.id)
                    .contains(row.id);
            db.update("update uber_eats_orders set released_at=? where id=?", day.end(), row.id);
            assertThat(
                            inbox.todayInbox(
                                    "sandbox",
                                    store,
                                    day.start(),
                                    day.end(),
                                    day.createdStart(),
                                    day.createdEnd(),
                                    PageRequest.of(0, 200)))
                    .extracting(o -> o.id)
                    .doesNotContain(row.id);
            db.update(
                    "update uber_eats_orders set released_at=null,placed_at=null,created_at=? where"
                            + " id=?",
                    date.atTime(0, 30),
                    row.id);
            assertThat(
                            inbox.todayInbox(
                                    "sandbox",
                                    store,
                                    day.start(),
                                    day.end(),
                                    day.createdStart(),
                                    day.createdEnd(),
                                    PageRequest.of(0, 200)))
                    .extracting(o -> o.id)
                    .contains(row.id);
        }
        assertThat(
                        Duration.between(
                                        UberEatsInboxDay.of(
                                                        LocalDate.of(2026, 3, 8),
                                                        ZoneId.of("America/Toronto"),
                                                        ZoneOffset.UTC)
                                                .start(),
                                        UberEatsInboxDay.of(
                                                        LocalDate.of(2026, 3, 8),
                                                        ZoneId.of("America/Toronto"),
                                                        ZoneOffset.UTC)
                                                .end())
                                .toHours())
                .isEqualTo(23);
        assertThat(
                        Duration.between(
                                        UberEatsInboxDay.of(
                                                        LocalDate.of(2026, 11, 1),
                                                        ZoneId.of("America/Toronto"),
                                                        ZoneOffset.UTC)
                                                .start(),
                                        UberEatsInboxDay.of(
                                                        LocalDate.of(2026, 11, 1),
                                                        ZoneId.of("America/Toronto"),
                                                        ZoneOffset.UTC)
                                                .end())
                                .toHours())
                .isEqualTo(25);
    }

    @Test
    void mirrorDoesNotConsumeOrdinaryPosHistoryLimit() {
        var data = payload(UUID.randomUUID().toString());
        var padRequest = mapping.map(binding, normalizer.normalize(data)).request();
        padRequest.external_order_note_snapshot = null;
        var pad = orderService.createOrReplaceDraftAndSubmit(padRequest, null);
        mirrorMode();
        release(data);
        assertThat(orderService.getFrontdeskTodayOrderHistory(store, 1))
                .extracting(o -> o.order_id)
                .containsExactly(pad.id);
    }

    @Test
    void mirrorMissingEmptyAndPartialChargesKeepLastObservedAmountsAndRawResponseTruth() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var charges = data.withObject("/payment/charges");
        charges.putObject("total").put("amount", 1999).put("currency_code", "CAD");
        charges.putObject("tax").put("amount", 199).put("currency_code", "CAD");
        var first = notify(data);
        data.remove("payment");
        notify(data);
        assertThat(row(first.uberOrderId).financialTotalMinor).isEqualTo(1999);
        data.withObject("/payment/charges");
        notify(data);
        assertThat(row(first.uberOrderId).financialTotalMinor).isEqualTo(1999);
        assertThat(row(first.uberOrderId).rawFinancialSnapshotJson).contains("1999");
        data.withObject("/payment/charges")
                .putObject("tax")
                .put("amount", 250)
                .put("currency_code", "CAD");
        var released = release(data);
        assertThat(released.financialTotalMinor).isEqualTo(1999);
        assertThat(released.financialTaxMinor).isEqualTo(250);
        assertThat(released.financialCurrency).isEqualTo("CAD");
        JsonNode savedCharges = tx.decode(released.rawFinancialSnapshotJson, JsonNode.class);
        assertThat(savedCharges).isEqualTo(data.at("/payment/charges"));
    }

    UberEatsOrder accept(UberEatsOrder row) {
        return imports.decide(store, row.id, actorId, "ACCEPT", null);
    }

    @Test
    void acceptedPollingSurvivesRestartAndLateReleaseSharesExactlyOneKitchenGate()
            throws Exception {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var waiting = notify(data);
        assertThat(waiting.status).isEqualTo("WAITING_FOR_ACCEPTANCE");
        assertThat(waiting.acceptancePollExpiresAt).isAfter(waiting.acceptancePollStartedAt);
        assertThat(waiting.acceptedObservedAt).isNull();
        assertThat(waiting.currentState).isEqualTo("CREATED");
        clearInvocations(client);
        imports.recover();
        verifyNoInteractions(client); // Persisted due time survives worker iterations.
        due(waiting.id);
        assertThat(tx.claimRecovery(waiting.id)).isTrue(); // Crash after durable lease claim.
        var restarted =
                new UberEatsOrderImportService(
                        newProperties(), client, normalizer, tx, events, inbox);
        restarted.recover();
        verifyNoInteractions(client);
        data.put("current_state", "ACCEPTED");
        due(waiting.id); // Expired lease on the isolated PostgreSQL fixture.
        restarted.recover();
        var accepted = row(waiting.uberOrderId);
        assertThat(accepted.status).isEqualTo("RELEASED_TO_KITCHEN");
        assertThat(accepted.acceptedObservedAt).isNotNull();
        assertThat(accepted.acceptedAt).isNull(); // Never pretend this was our remote Accept.
        assertThat(accepted.releasedAt).isNull(); // No invented release webhook.
        assertThat(accepted.acceptancePollCount).isEqualTo(2);
        var observed = accepted.acceptedObservedAt;
        Long local = accepted.localOrderId;
        release(data);
        notify(data);
        var pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> tx.submitLocal(accepted.id)).get(20, TimeUnit.SECONDS);
            pool.submit(() -> tx.submitLocal(accepted.id)).get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(row(waiting.uberOrderId).acceptedObservedAt).isEqualTo(observed);
        assertThat(row(waiting.uberOrderId).localOrderId).isEqualTo(local);
        assertThat(count("orders", "store_id", store)).isOne();
        assertThat(count("kitchen_tasks", "order_id", local)).isOne();
        assertThat(count("inventory_transactions", "source_id", local)).isOne();
        dispatch(local);
        dispatch(local);
        assertThat(jobs.findAllByStoreIdAndOrderId(store, local))
                .hasSize(2)
                .allSatisfy(j -> assertThat(j.module_code).isIn("GRAB", "HOT_KITCHEN"));
        clearInvocations(client);
        restarted.recover();
        verifyNoInteractions(client);
        verify(client, never()).accept(anyString(), anyString());
        verify(client, never()).deny(anyString(), anyString());
    }

    @Test
    void acceptedPollingStopsForEveryTerminalOrUnknownStateWithoutPrinting() {
        mirrorMode();
        for (String state : List.of("DENIED", "CANCELED", "FINISHED", "UNKNOWN", "UNRECOGNIZED")) {
            var data = payload(UUID.randomUUID().toString());
            var waiting = notify(data);
            data.put("current_state", state);
            due(waiting.id);
            imports.recover();
            var stopped = row(waiting.uberOrderId);
            assertThat(stopped.status)
                    .isEqualTo(
                            state.equals("DENIED")
                                    ? "DENIED"
                                    : state.equals("CANCELED")
                                            ? "CANCELLED"
                                            : "EXTERNAL_STATE_REVIEW_REQUIRED");
            assertThat(stopped.currentState).isEqualTo(state);
            assertThat(stopped.nextAttemptAt).isNull();
            assertThat(stopped.localOrderId).isNull();
            clearInvocations(client);
            imports.recover();
            verifyNoInteractions(client);
            data.put("current_state", "ACCEPTED");
            release(data);
            assertThat(row(waiting.uberOrderId).localOrderId).isNull();
        }
        assertThat(count("orders", "store_id", store)).isZero();
    }

    @Test
    void acceptedPollingHasDurableAttemptDeadlineAndHttpBackoffLimits() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var waiting = notify(data);
        due(waiting.id);
        when(client.getOrder(waiting.uberOrderId)).thenThrow(new UberEatsApiException(429));
        var before = LocalDateTime.now();
        imports.recover();
        var retried = row(waiting.uberOrderId);
        assertThat(retried.acceptancePollCount).isEqualTo(1);
        assertThat(retried.nextAttemptAt).isAfter(before.plusSeconds(59));
        assertThat(retried.lastError).isEqualTo("UBER_ACCEPTANCE_CHECK_UNAVAILABLE");
        db.update("update uber_eats_orders set acceptance_poll_count=40 where id=?", waiting.id);
        due(waiting.id);
        clearInvocations(client);
        imports.recover();
        verifyNoInteractions(client);
        assertThat(row(waiting.uberOrderId).status).isEqualTo("ACCEPTANCE_REVIEW_REQUIRED");
        var deadlineData = payload(UUID.randomUUID().toString());
        var deadline = notify(deadlineData);
        db.update(
                "update uber_eats_orders set acceptance_poll_expires_at=? where id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                deadline.id);
        due(deadline.id);
        clearInvocations(client);
        imports.recover();
        verifyNoInteractions(client);
        assertThat(row(deadline.uberOrderId).status).isEqualTo("ACCEPTANCE_REVIEW_REQUIRED");
        var rejected = notify(payload(UUID.randomUUID().toString()));
        due(rejected.id);
        when(client.getOrder(rejected.uberOrderId)).thenThrow(new UberEatsApiException(403));
        imports.recover();
        assertThat(row(rejected.uberOrderId).status).isEqualTo("ACCEPTANCE_REVIEW_REQUIRED");
        assertThat(row(rejected.uberOrderId).lastError)
                .isEqualTo("UBER_ACCEPTANCE_CHECK_REJECTED_403");
        assertThat(count("orders", "store_id", store)).isZero();
    }

    @Test
    void pollingContinuesCreatedButCannotDowngradeAnAcceptedCheckpoint() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        var waiting = notify(data);
        due(waiting.id);
        imports.recover();
        assertThat(row(waiting.uberOrderId).status).isEqualTo("WAITING_FOR_ACCEPTANCE");
        assertThat(row(waiting.uberOrderId).acceptancePollCount).isEqualTo(1);
        data.put("current_state", "ACCEPTED");
        assertThat(tx.refreshMirror(waiting.id, normalizer.normalize(data))).isTrue();
        data.put("current_state", "CREATED");
        tx.refreshMirror(waiting.id, normalizer.normalize(data));
        assertThat(row(waiting.uberOrderId).status).isEqualTo("MIRROR_READY");
        assertThat(row(waiting.uberOrderId).currentState).isEqualTo("ACCEPTED");
        tx.submitLocal(waiting.id);
        assertThat(count("orders", "store_id", store)).isOne();
    }

    @Test
    void acceptedUnmappedRootDispatchesOnceWithoutWaitingForMappingMutation() {
        mirrorMode(); var data=payload(UUID.randomUUID().toString());
        ((ObjectNode)data.at("/cart/items/0")).put("external_data", "");
        var waiting=notify(data); data.put("current_state", "ACCEPTED"); due(waiting.id); imports.recover();
        var sent=row(waiting.uberOrderId);
        assertThat(sent.status).isEqualTo("KITCHEN_SENT_WITH_MAPPING_WARNINGS");
        assertThat(sent.localOrderId).isNotNull(); assertThat(sent.acceptedObservedAt).isNotNull();
        clearInvocations(client); imports.recover(); verifyNoInteractions(client);
        assertThat(count("orders", "store_id", store)).isOne();
    }

    @Test
    void scheduledAcceptedOrderWaitsForRegularNotificationAndDisruptiveEventWinsPolling() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        data.put("current_state", "ACCEPTED");
        String id = data.path("id").asText();
        when(client.getOrder(id)).thenReturn(data);
        receive(event(UUID.randomUUID().toString(), "orders.scheduled.notification", id));
        imports.processEvents();
        imports.recover();
        assertThat(row(id).status).isEqualTo("SCHEDULED_REVIEW_REQUIRED");
        assertThat(count("orders", "store_id", store)).isZero();
        var zone = ZoneId.of("America/Toronto");
        var today = LocalDate.now(zone);
        var yesterday =
                today.minusDays(1)
                        .atStartOfDay(zone)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
        db.update(
                "update uber_eats_orders set accepted_observed_at=?,placed_at=? where id=?",
                yesterday,
                yesterday,
                row(id).id);
        notify(data);
        imports.recover();
        assertThat(row(id).localOrderId).isNotNull();
        assertThat(row(id).acceptedObservedAt).isEqualTo(yesterday);
        var day = UberEatsInboxDay.of(today, zone, ZoneId.systemDefault());
        assertThat(
                        inbox.todayInbox(
                                "sandbox",
                                store,
                                day.start(),
                                day.end(),
                                day.createdStart(),
                                day.createdEnd(),
                                PageRequest.of(0, 200)))
                .extracting(o -> o.id)
                .contains(row(id).id);
        var pendingData = payload(UUID.randomUUID().toString());
        var waiting = notify(pendingData);
        receive(
                event(
                        UUID.randomUUID().toString(),
                        "orders.customer_order_edit",
                        waiting.uberOrderId));
        pendingData.put("current_state", "ACCEPTED");
        tx.refreshMirror(waiting.id, normalizer.normalize(pendingData));
        assertThat(row(waiting.uberOrderId).status).isEqualTo("EDIT_REVIEW_REQUIRED");
        assertThat(row(waiting.uberOrderId).localOrderId).isNull();
    }

    @Test
    void mappingPreviewUsesRuntimeCatalogAndRulesWithoutWritingOrderAndRequiresOwnerScope()
            throws Exception {
        mirrorMode();
        var source = payload(UUID.randomUUID().toString());
        String url = "/api/v1/stores/" + store + "/integrations/uber-eats/mapping-preview";
        mvc.perform(post(url).contentType("application/json").content(source.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(url)
                                .contentType("application/json")
                                .content(source.toString())
                                .requestAttr(
                                        RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE,
                                        actor))
                .andExpect(status().isForbidden());
        db.update(
                "update store_memberships set role_code='OWNER' where user_id=? and store_id=?",
                actorId,
                store);
        db.update(
                "insert into"
                    + " organization_memberships(user_id,organization_id,role_code,is_active,created_at,updated_at)"
                    + " values (?,?,'OWNER',true,now(),now())",
                actorId,
                org);
        var owner =
                new AuthenticatedUser(
                        actorId, store, actor.roleId(), "fixture", "Fixture Owner", "OWNER");
        var response =
                mvc.perform(
                                post(url)
                                        .contentType("application/json")
                                        .content(source.toString())
                                        .requestAttr(
                                                RequestUserContextService
                                                        .AUTHENTICATED_USER_ATTRIBUTE,
                                                owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(json.readTree(response).path("data").path("errors")).isEmpty();
        source.withObject("/store").put("id", UUID.randomUUID().toString());
        response =
                mvc.perform(
                                post(url)
                                        .contentType("application/json")
                                        .content(source.toString())
                                        .requestAttr(
                                                RequestUserContextService
                                                        .AUTHENTICATED_USER_ATTRIBUTE,
                                                owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(response).contains("STORE_MISMATCH");
        assertThat(count("orders", "store_id", store)).isZero();
        assertThat(count("uber_eats_orders", "store_id", store)).isZero();
    }

    UberEatsOrder notify(ObjectNode payload) {
        String id = payload.path("id").asText();
        when(client.getOrder(id)).thenReturn(payload);
        receive(event(UUID.randomUUID().toString(), "orders.notification", id));
        imports.processEvents();
        return row(id);
    }

    UberEatsOrder row(String id) {
        return inbox.findByEnvironmentAndUberOrderId("sandbox", id).orElseThrow();
    }

    void due(Long id) {
        db.update(
                "update uber_eats_orders set next_attempt_at=now()-interval '1 second' where id=?",
                id);
    }

    void dispatch(Long id) {
        for (var e :
                db.queryForList(
                        "select module_code,source_key from order_dispatch_outbox where order_id=?"
                                + " order by id",
                        id))
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            s ->
                                    dispatcher.dispatchPersistedEvent(
                                            (String) e.get("module_code"),
                                            store,
                                            id,
                                            null,
                                            (String) e.get("source_key")));
    }

    String render(Long id) {
        var req = new PrintRenderRequest();
        req.store = stores.findById(store).orElseThrow();
        req.order = new com.restaurant.system.order.entity.Order();
        req.order.order_no = "PARITY";
        req.order.order_type = "delivery";
        req.order.submitted_at = LocalDateTime.of(2026, 1, 1, 12, 0);
        req.order_items = items.findAllByOrderId(id);
        req.order_item_options =
                options.findAllByOrderItemIds(req.order_items.stream().map(i -> i.id).toList());
        req.kitchen_tasks = tasks.findAllByOrderId(id);
        req.printing_rules = PrintingDisplayRuleContext.defaultContext();
        return grab.render(req);
    }

    Long id(String sql, Object... args) {
        return db.queryForObject(sql, Long.class, args);
    }

    long count(String table, String field, Long id) {
        return db.queryForObject(
                "select count(*) from " + table + " where " + field + "=?", Long.class, id);
    }

    Long option(Long item, String code, String group, String type, String name, int price) {
        return id(
                "insert into"
                    + " menu_item_options(menu_item_id,option_code,option_group,option_type,name_zh,name_en,price_delta,is_active)"
                    + " values (?,?,?,?,?,?,?,true) returning id",
                item,
                code,
                group,
                type,
                name,
                code,
                price);
    }

    ObjectNode payload(String id) {
        var root = json.createObjectNode();
        root.put("id", id);
        root.put("display_id", "FIX01");
        root.put("current_state", "CREATED");
        root.put("order_manager_client_id", "fixture-client");
        root.put("type", "DELIVERY_BY_UBER");
        root.put("placed_at", "2026-09-16T12:00:00-04:00");
        root.putObject("store").put("id", uberStore);
        root.putObject("eater").put("phone", "private-not-stored");
        var line = root.putObject("cart").putArray("items").addObject();
        line.put("id", "uber-noodle");
        line.put("external_data", "traditional_beef_noodle");
        line.put("title", "Traditional Beef Noodle");
        line.put("quantity", 1);
        var selected =
                line.putArray("selected_modifier_groups").addObject().putArray("selected_items");
        addModifier(selected, "large", "size_large", "Large");
        addModifier(selected, "egg", "fried_egg", "Fried Egg");
        addModifier(selected, "cilantro", "remove_cilantro", "No Cilantro");
        return root;
    }

    void addModifier(ArrayNode array, String id, String code, String title) {
        array.addObject()
                .put("id", id)
                .put("external_data", code)
                .put("title", title)
                .put("quantity", 1);
    }

    byte[] event(String eventId, String type, String orderId) {
        return ("{\"event_id\":\""
                        + eventId
                        + "\",\"event_type\":\""
                        + type
                        + "\",\"meta\":{\"user_id\":\""
                        + uberStore
                        + "\",\"resource_id\":\""
                        + orderId
                        + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    void receive(byte[] raw) {
        webhook.receive(raw, sign(raw), "sandbox");
    }

    String sign(byte[] raw) {
        return sign(raw, "fixture-webhook-secret");
    }

    String sign(byte[] raw, String key) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            key.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
