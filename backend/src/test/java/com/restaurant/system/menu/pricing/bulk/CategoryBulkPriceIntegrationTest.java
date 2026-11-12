package com.restaurant.system.menu.pricing.bulk;

import static com.restaurant.system.menu.pricing.bulk.CategoryBulkPriceModels.*;
import static org.assertj.core.api.Assertions.*;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.pricing.StorePricingPolicy;
import com.restaurant.system.menu.pricing.StorePricingPolicyRepository;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.impl.MenuRevisionServiceImpl;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.service.impl.PlatformAdminServiceImpl;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "spring.datasource.url=jdbc:h2:mem:category_bulk_jpa;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CategoryBulkPriceIntegrationTest.JpaConfiguration.class)
@Import({CategoryBulkPriceService.class, CategoryBulkPriceRepository.class, MenuRevisionServiceImpl.class, PlatformAdminServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryBulkPriceIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @Autowired private CategoryBulkPriceService service;
    @Autowired private MenuRevisionService revisions;
    @Autowired private PlatformAdminServiceImpl singleItems;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private OrganizationRepository organizations;
    @Autowired private StoreRepository stores;
    @Autowired private MenuCategoryRepository categories;
    @Autowired private MenuItemRepository items;
    @Autowired private MenuItemOptionRepository options;
    @Autowired private StorePricingPolicyRepository policies;
    @MockBean private com.restaurant.system.menu.remove.StoreRemoveService removeService;
    @MockBean private com.restaurant.system.menu.remove.StoreRemoveOptionResolver removeResolver;
    @MockBean private com.restaurant.system.common.realtime.RealtimeEventPublisher realtime;

    private Long storeId;
    private Long otherStoreId;
    private Long categoryId;
    private Long otherCategoryId;
    private Long inactiveCategoryId;
    private Long itemId;
    private Long secondItemId;
    private Long inactiveItemId;
    private Long refillId;
    private Long comboId;
    private Long offerId;
    private TransactionTemplate transaction;

    @BeforeAll void applyExactForwardMigrationOnH2() throws Exception {
        // Hibernate creates the mapped capability; remove that empty column to exercise the exact V42 migration.
        jdbc.execute("alter table store_pricing_policies drop column category_bulk_pricing_enabled");
        try (var connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V42__add_category_bulk_pricing.sql"));
        }
    }

    @BeforeEach void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now().minusSeconds(10);
            Organization organization = new Organization(); organization.code = "BULK_" + suffix;
            organization.name = "Category price test"; organization.status = "active"; organization.created_at = now; organization.updated_at = now;
            Long organizationId = organizations.saveAndFlush(organization).id;
            storeId = store(organizationId, "A_" + suffix, now);
            otherStoreId = store(organizationId, "B_" + suffix, now);
            categoryId = category(storeId, "FOOD", true, now);
            otherCategoryId = category(otherStoreId, "FOOD", true, now);
            inactiveCategoryId = category(storeId, "INACTIVE", false, now);
            StorePricingPolicy policy = policy(storeId, now); policy.category_bulk_pricing_enabled = true;
            policies.saveAndFlush(policy);
            policies.saveAndFlush(policy(otherStoreId, now));
            itemId = item(storeId, categoryId, "MAIN", "FOOD", "10.00", true, null, now);
            secondItemId = item(storeId, categoryId, "DRINK", "DRINK", "3.50", true, null, now);
            inactiveItemId = item(storeId, categoryId, "INACTIVE", "FOOD", "8.00", false, null, now);
            refillId = item(storeId, categoryId, "REFILL", "FOOD", "0.00", true, "SOUP_NOODLE", now);
            comboId = item(storeId, categoryId, "COMBO", "COMBO", "18.00", true, null, now);
            offerId = item(storeId, categoryId, "OFFER", "FIXED_OFFER", "20.00", true, null, now);
            item(otherStoreId, otherCategoryId, "OTHER", "FOOD", "12.00", true, null, now);
            option(itemId, "egg", "ADD_ON", null, "2.50", now);
            option(itemId, "size_large", "SIZE", "noodle_shape", "0.00", now);
            jdbc.update("insert into orders(store_id,order_no,financial_mode,status,subtotal_amount,total_amount) values (?,?, 'IN_STORE','completed',10.00,11.50)", storeId, "HISTORY_" + suffix);
            Long orderId = jdbc.queryForObject("select id from orders where store_id=?", Long.class, storeId);
            jdbc.update("insert into order_items(order_id,menu_item_id,item_name_snapshot_en,unit_price,line_amount,quantity) values (?,?, 'Original item',10.00,10.00,1)", orderId, itemId);
            jdbc.update("insert into print_jobs(organization_id,store_id,order_id,status,payload_snapshot,rendered_text_snapshot) values (?,?,?,'PRINTED','original payload','original receipt')", organizationId, storeId, orderId);
        });
    }

    @Test void capabilitiesUseExplicitOptInAndNonTemplateCanonicalSizeOnly() {
        assertThat(service.capabilities(storeId).category_bulk_enabled()).isTrue();
        assertThat(service.capabilities(storeId).standard_size_enabled()).isFalse();
        assertThat(service.capabilities(otherStoreId).category_bulk_enabled()).isFalse();
        assertThat(policies.findByStoreId(otherStoreId).orElseThrow().category_bulk_pricing_enabled).isFalse();
        transaction.executeWithoutResult(status -> option(itemId, "size_regular", "SIZE", null, "0.00", LocalDateTime.now()));
        assertThat(service.capabilities(storeId).standard_size_enabled()).isTrue();
        assertThat(service.capabilities(otherStoreId).standard_size_enabled()).isFalse();
    }

    @Test void realApplyChangesOnlyEligibleBasePricesAndOneRevisionAndPreservesHistory() {
        var invariantBefore = invariants();
        Preview preview = service.preview(request("INCREASE", "1.25", false));
        assertThat(preview.affected_item_count()).isEqualTo(2);
        assertThat(preview.excluded_items()).extracting(ExcludedItem::reason).containsExactlyInAnyOrder("INACTIVE_ITEM", "REFILL_ITEM", "FIXED_OFFER", "FIXED_OFFER");
        assertThat(revision()).isEqualTo(1);
        ApplyResult result = service.apply(apply(preview, "first-save"), actor());
        assertThat(price(itemId)).isEqualByComparingTo("11.25");
        assertThat(price(secondItemId)).isEqualByComparingTo("4.75");
        assertThat(price(inactiveItemId)).isEqualByComparingTo("8.00");
        assertThat(price(refillId)).isEqualByComparingTo("0.00");
        assertThat(price(comboId)).isEqualByComparingTo("18.00");
        assertThat(price(offerId)).isEqualByComparingTo("20.00");
        assertThat(result.menu_revision()).isEqualTo(2);
        assertThat(revision()).isEqualTo(2);
        assertThat(count("store_category_price_adjustments")).isEqualTo(1);
        assertThat(count("audit_logs")).isEqualTo(1);
        assertThat(invariants()).isEqualTo(invariantBefore);
        assertThat(jdbc.queryForObject("select metadata_json from audit_logs where store_id=?", String.class, storeId)).contains("11.25", "old_base_price", "first-save");
    }

    @Test void sameRequestReplayAndSamePreviewWithNewKeyCannotChargeTwice() {
        ApplyRequest request = apply(service.preview(request("INCREASE", "1", false)), "stable-save");
        ApplyResult first = service.apply(request, actor());
        jdbc.update("update store_category_price_adjustment_previews set expires_at=? where preview_id=?", Timestamp.from(Instant.now().minusSeconds(1)), request.preview_id);
        assertThat(service.apply(request, actor())).isEqualTo(first);
        assertThat(price(itemId)).isEqualByComparingTo("11.00");
        assertThat(revision()).isEqualTo(2);
        assertThat(count("store_category_price_adjustments")).isEqualTo(1);
        assertThat(count("audit_logs")).isEqualTo(1);
        request.idempotency_key = "another-save";
        assertError(() -> service.apply(request, actor()), "CATEGORY_PRICING_PREVIEW_EXPIRED");
        request.idempotency_key = "stable-save"; request.amount = new BigDecimal("2");
        assertError(() -> service.apply(request, actor()), "CATEGORY_PRICING_IDEMPOTENCY_CONFLICT");
    }

    @Test void mixedStoreOrInactiveCategoryAndNegativePriceRejectEntireBatch() {
        var request = request("INCREASE", "1", false); request.category_ids = List.of(categoryId, otherCategoryId);
        assertError(() -> service.preview(request), "CATEGORY_PRICING_CATEGORY_INVALID");
        request.category_ids = List.of(categoryId, inactiveCategoryId);
        assertError(() -> service.preview(request), "CATEGORY_PRICING_CATEGORY_INVALID");
        request.category_ids = List.of(categoryId); request.direction = "DECREASE"; request.amount = new BigDecimal("4");
        assertError(() -> service.preview(request), "CATEGORY_PRICING_NEGATIVE_PRICE");
        assertThat(price(itemId)).isEqualByComparingTo("10.00");
        assertThat(price(secondItemId)).isEqualByComparingTo("3.50");
        assertThat(revision()).isEqualTo(1);
        assertThat(count("store_category_price_adjustments")).isZero();
        assertThat(count("audit_logs")).isZero();
    }

    @Test void includeInactiveDoesNotIncludeRefillsOrFixedCombos() {
        Preview preview = service.preview(request("DECREASE", "1.50", true));
        assertThat(preview.items()).extracting(PriceChange::item_id).containsExactlyInAnyOrder(itemId, secondItemId, inactiveItemId);
        service.apply(apply(preview, "inactive-save"), actor());
        assertThat(price(inactiveItemId)).isEqualByComparingTo("6.50");
        assertThat(price(refillId)).isEqualByComparingTo("0.00");
        assertThat(price(comboId)).isEqualByComparingTo("18.00");
        assertThat(price(offerId)).isEqualByComparingTo("20.00");
    }

    @Test void fingerprintDetectsDirectPriceChangeEvenWithoutRevisionAdvance() {
        var request = apply(service.preview(request("INCREASE", "1", false)), "stale-save");
        jdbc.update("update menu_items set base_price=12.00 where id=?", itemId);
        assertError(() -> service.apply(request, actor()), "CATEGORY_PRICING_PREVIEW_STALE");
        assertThat(price(secondItemId)).isEqualByComparingTo("3.50");
        assertThat(revision()).isEqualTo(1);
        assertThat(count("store_category_price_adjustments")).isZero();
    }

    @Test void failedStrictAuditRollsBackEveryPriceRevisionAndLedgerWrite() {
        var request = apply(service.preview(request("INCREASE", "1", false)), "audit-failure");
        jdbc.execute("alter table audit_logs add constraint reject_category_price_audit check (action <> 'MENU_CATEGORY_BASE_PRICES_ADJUSTED')");
        try {
            assertThatThrownBy(() -> service.apply(request, actor())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("alter table audit_logs drop constraint reject_category_price_audit");
        }
        assertUnchangedAfterFailure();
    }

    @Test void failedLedgerInsertAlsoRollsBackPricesRevisionAndSuccessfulAudit() {
        var request = apply(service.preview(request("INCREASE", "1", false)), "ledger-failure");
        jdbc.execute("alter table store_category_price_adjustments add constraint reject_category_price_key check (idempotency_key <> 'ledger-failure')");
        try {
            assertThatThrownBy(() -> service.apply(request, actor())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("alter table store_category_price_adjustments drop constraint reject_category_price_key");
        }
        assertUnchangedAfterFailure();
    }

    @Test void simultaneousSameKeyRequestsCommitExactlyOnce() throws Exception {
        var request = apply(service.preview(request("INCREASE", "1", false)), "parallel-save");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { await(start); return service.apply(request, actor()); });
            var second = pool.submit(() -> { await(start); return service.apply(request, actor()); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(price(itemId)).isEqualByComparingTo("11.00");
            assertThat(revision()).isEqualTo(2);
            assertThat(count("store_category_price_adjustments")).isEqualTo(1);
            assertThat(count("audit_logs")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void actualSingleItemEditSharesStoreLockAndMakesWaitingBulkPreviewStale() throws Exception {
        var request = apply(service.preview(request("INCREASE", "1", false)), "single-item-race");
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var bulkStarted = new CountDownLatch(1);
        try {
            var edit = pool.submit(() -> transaction.executeWithoutResult(status -> {
                revisions.lockStoresInOrder(List.of(storeId));
                locked.countDown(); await(release);
                MenuItem update = items.findById(itemId).orElseThrow();
                update.base_price = new BigDecimal("12.00");
                singleItems.saveMenuItem(update);
            }));
            assertThat(locked.await(3, TimeUnit.SECONDS)).isTrue();
            var bulk = pool.submit(() -> { bulkStarted.countDown(); return service.apply(request, actor()); });
            assertThat(bulkStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> bulk.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); edit.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> bulk.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(CategoryPricingException.class);
            assertThat(price(itemId)).isEqualByComparingTo("12.00");
            assertThat(price(secondItemId)).isEqualByComparingTo("3.50");
            assertThat(revision()).isEqualTo(2);
            assertThat(count("store_category_price_adjustments")).isZero();
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    private void assertUnchangedAfterFailure() {
        assertThat(price(itemId)).isEqualByComparingTo("10.00");
        assertThat(price(secondItemId)).isEqualByComparingTo("3.50");
        assertThat(revision()).isEqualTo(1);
        assertThat(count("store_category_price_adjustments")).isZero();
        assertThat(count("audit_logs")).isZero();
    }
    private Map<String, Object> invariants() {
        return Map.of("options", jdbc.queryForList("select * from menu_item_options where menu_item_id=? order by id", itemId),
            "history", jdbc.queryForList("select i.unit_price,i.line_amount,i.item_name_snapshot_en from order_items i join orders o on o.id=i.order_id where o.store_id=?", storeId),
            "orders", jdbc.queryForList("select subtotal_amount,total_amount,status from orders where store_id=?", storeId),
            "printing", jdbc.queryForList("select status,cast(payload_snapshot as varchar) payload,cast(rendered_text_snapshot as varchar) receipt from print_jobs where store_id=?", storeId),
            "policy", jdbc.queryForList("select * from store_pricing_policies where store_id=?", storeId),
            "other_store", jdbc.queryForList("select * from menu_items where store_id=? order by id", otherStoreId),
            "item_fields", jdbc.queryForList("select id,store_id,category_id,station_id,sku,name_zh,name_en,name_fr,item_type,is_active,is_sold_out,refill_category_code from menu_items where store_id=? order by id", storeId));
    }
    private Long store(Long organization, String code, LocalDateTime now) {
        Store store = new Store(); store.organization_id = organization; store.code = code; store.name = code; store.status = "active";
        store.printing_enabled = false; store.printing_mode = "DISABLED"; store.menu_revision = 1L; store.menu_updated_at = now; store.created_at = now; store.updated_at = now;
        return stores.saveAndFlush(store).id;
    }
    private Long category(Long store, String code, boolean active, LocalDateTime now) {
        MenuCategory category = new MenuCategory(); category.store_id = store; category.code = code; category.name_zh = "分类";
        category.name_en = code; category.is_active = active; category.created_at = now; category.updated_at = now;
        return categories.saveAndFlush(category).id;
    }
    private Long item(Long store, Long category, String sku, String type, String price, boolean active, String refill, LocalDateTime now) {
        MenuItem item = new MenuItem(); item.store_id = store; item.category_id = category; item.sku = sku; item.item_type = type;
        item.name_zh = "菜品" + sku; item.name_en = sku; item.base_price = new BigDecimal(price); item.is_active = active; item.is_sold_out = false;
        item.refill_category_code = refill; item.created_at = now; item.updated_at = now;
        return items.saveAndFlush(item).id;
    }
    private void option(Long item, String code, String group, String templateGroup, String delta, LocalDateTime now) {
        MenuItemOption option = new MenuItemOption(); option.menu_item_id = item; option.option_code = code; option.option_group = group;
        option.template_group_code = templateGroup; option.option_type = "SIZE".equals(group) ? "size" : "addon"; option.name_zh = code;
        option.price_delta = new BigDecimal(delta); option.is_active = true; option.created_at = now; option.updated_at = now; options.saveAndFlush(option);
    }
    private StorePricingPolicy policy(Long store, LocalDateTime now) {
        StorePricingPolicy policy = new StorePricingPolicy(); policy.store_id = store; policy.size_small_delta = new BigDecimal("-2");
        policy.size_regular_delta = BigDecimal.ZERO; policy.size_large_delta = new BigDecimal("2"); policy.combo_delta = new BigDecimal("5");
        policy.policy_revision = 1L; policy.created_at = now; policy.updated_at = now; return policy;
    }
    private PreviewRequest request(String direction, String amount, boolean includeInactive) {
        var request = new PreviewRequest(); request.store_id = storeId; request.category_ids = List.of(categoryId); request.direction = direction;
        request.amount = new BigDecimal(amount); request.currency = "CAD"; request.include_inactive = includeInactive; return request;
    }
    private ApplyRequest apply(Preview preview, String key) {
        var request = new ApplyRequest(); request.store_id = preview.store_id(); request.category_ids = preview.category_ids();
        request.direction = preview.direction(); request.amount = preview.amount(); request.currency = preview.currency(); request.include_inactive = preview.include_inactive();
        request.preview_id = preview.preview_id(); request.expected_revision = preview.menu_revision(); request.fingerprint = preview.fingerprint(); request.idempotency_key = key; return request;
    }
    private AuthenticatedUser actor() { return new AuthenticatedUser(77L, storeId, 1L, "owner", "Owner", "OWNER"); }
    private BigDecimal price(Long id) { return jdbc.queryForObject("select base_price from menu_items where id=?", BigDecimal.class, id); }
    private long revision() { return jdbc.queryForObject("select menu_revision from stores where id=?", Long.class, storeId); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table + " where store_id=?", Integer.class, storeId); }
    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CategoryPricingException.class, error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic concurrency gate timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
