package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;
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
        org =
                id(
                        "insert into organizations(name,code,status) values ('Uber"
                                + " fixture','UF-'||gen_random_uuid(),'active') returning id");
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
            assertThat(row(id).localOrderId).isNull();
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
        var row = notify(data);
        assertThat(row.status).isEqualTo("RECEIVED");
        assertThat(row.localOrderId).isNull();
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
    void mirrorReleaseMappingFailureBlocksThenFreshRecoveryAfterExplicitMapping() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode) data.at("/cart/items/0")).put("external_data", "");
        var blocked = release(data);
        assertThat(blocked.status).isEqualTo("RELEASED_MAPPING_REQUIRED");
        assertThat(count("orders", "store_id", store)).isZero();
        var rule = new UberEatsMenuMapping();
        rule.kind = "ITEM";
        rule.identifierType = "ID";
        rule.uberIdentifier = "uber-noodle";
        rule.localMenuItemId = item;
        configuration.saveMapping(store, rule, actor);
        imports.recover();
        assertThat(row(blocked.uberOrderId).status).isEqualTo("RELEASED_TO_KITCHEN");
        assertThat(count("orders", "store_id", store)).isOne();
        verify(client, times(2)).getOrder(blocked.uberOrderId);
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
        var pad =
                orderService.createOrReplaceDraftAndSubmit(
                        mapping.map(binding, normalizer.normalize(data)).request(), null);
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
    void acceptedMappingFailureWaitsForMappingChangeWithoutUnboundedGetCalls() {
        mirrorMode();
        var data = payload(UUID.randomUUID().toString());
        ((ObjectNode) data.at("/cart/items/0")).put("external_data", "");
        var waiting = notify(data);
        data.put("current_state", "ACCEPTED");
        due(waiting.id);
        imports.recover();
        var blocked = row(waiting.uberOrderId);
        assertThat(blocked.status).isEqualTo("RELEASED_MAPPING_REQUIRED");
        assertThat(blocked.nextAttemptAt).isNull();
        assertThat(blocked.acceptedObservedAt).isNotNull();
        clearInvocations(client);
        imports.recover();
        verifyNoInteractions(client);
        // More than an inbox page of newer terminal rows must not strand this mapping gate.
        db.update(
                "insert into"
                    + " uber_eats_orders(environment,store_mapping_id,store_id,uber_store_id,uber_order_id,status,mapping_status,attempt_count,cancelled,edit_required,scheduled,next_attempt_at,created_at,updated_at,processing_mode)"
                    + " select"
                    + " 'sandbox',?,?,?,gen_random_uuid()::text,'DENIED','PENDING',0,false,false,false,null,now(),now(),'KITCHEN_MIRROR'"
                    + " from generate_series(1,105)",
                binding.id,
                store,
                uberStore);
        var rule = new UberEatsMenuMapping();
        rule.kind = "ITEM";
        rule.identifierType = "ID";
        rule.uberIdentifier = "uber-noodle";
        rule.localMenuItemId = item;
        configuration.saveMapping(store, rule, actor);
        imports.recover();
        assertThat(row(waiting.uberOrderId).localOrderId).isNotNull();
        verify(client, times(1)).getOrder(waiting.uberOrderId);
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
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            "fixture-webhook-secret".getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
