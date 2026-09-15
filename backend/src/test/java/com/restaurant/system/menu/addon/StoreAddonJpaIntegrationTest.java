package com.restaurant.system.menu.addon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.pricing.StorePricingPolicy;
import com.restaurant.system.menu.pricing.StorePricingPolicyRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.impl.MenuRevisionServiceImpl;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.rules.PrintingDisplayRuleService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:addon_jpa;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = StoreAddonJpaIntegrationTest.JpaSliceConfiguration.class)
@Import({StoreAddonService.class, MenuRevisionServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoreAddonJpaIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired private StoreAddonService service;
    @Autowired private OrganizationRepository organizations;
    @Autowired private StoreRepository stores;
    @Autowired private MenuItemRepository items;
    @Autowired private MenuItemOptionRepository options;
    @Autowired private StorePricingPolicyRepository pricingPolicies;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private PrintingDisplayRuleService printing;

    private TransactionTemplate transaction;
    private Long organizationId;
    private Long storeId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        when(printing.activeContext(anyLong())).thenReturn(PrintingDisplayRuleContext.defaultContext());
        transaction.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            String suffix = UUID.randomUUID().toString();
            Organization organization = new Organization();
            organization.code = "ADDON_JPA_" + suffix;
            organization.name = "Add-on JPA test";
            organization.status = "active";
            organization.created_at = now;
            organization.updated_at = now;
            organizationId = organizations.saveAndFlush(organization).id;

            Store store = new Store();
            store.organization_id = organizationId;
            store.code = "ADDON_JPA_" + suffix;
            store.name = "Add-on JPA test";
            store.status = "active";
            store.printing_enabled = false;
            store.printing_mode = "DISABLED";
            store.menu_revision = 1L;
            store.menu_updated_at = now;
            store.created_at = now;
            store.updated_at = now;
            storeId = stores.saveAndFlush(store).id;

            MenuItem item = new MenuItem();
            item.store_id = storeId;
            item.name_zh = "测试菜品";
            item.name_en = "Test item";
            item.sku = "TEST_ITEM";
            item.base_price = new BigDecimal("10.00");
            item.sort_order = 10;
            item.is_active = true;
            item.is_sold_out = false;
            item.created_at = now;
            item.updated_at = now;
            itemId = items.saveAndFlush(item).id;
        });
    }

    @Test
    void reconciliationFlushesPendingMaterializedJpaValuesBeforeJdbcPlanning() {
        Long optionId = transaction.execute(status -> {
            MenuItemOption option = option("beef", "ADD_ON", "牛肉", "Beef", "2.00");
            options.saveAndFlush(option);
            option.name_zh = "待提交牛肉";
            option.name_en = "Pending materialized beef";
            option.price_delta = new BigDecimal("3.25");
            // JdbcTemplate does not auto-flush this pending JPA change.
            assertThat(jdbc.queryForObject("select name_zh from menu_item_options where id=?", String.class, option.id)).isEqualTo("牛肉");

            var report = service.reconcile(storeId, false);
            assertThat(report.linked_options()).isEqualTo(1);
            var addon = service.getAddons(storeId).addons().get(0);
            assertThat(addon.name_zh()).isEqualTo("待提交牛肉");
            assertThat(addon.price()).isEqualByComparingTo("3.25");
            assertThat(options.findById(option.id).orElseThrow().store_addon_id).isEqualTo(addon.id());
            return option.id;
        });

        MenuItemOption linked = options.findById(optionId).orElseThrow();
        assertThat(linked.store_addon_id).isNotNull();
        assertThat(linked.store_addon_store_id).isEqualTo(storeId);
        assertThat(linked.addon_eligible).isTrue();
        assertThat(stores.findMenuRevisionById(storeId)).isEqualTo(2L);
    }

    @Test
    void catalogPropagationSurvivesLaterJpaOptionMutationWithoutStaleValueOverwrite() {
        Long optionId = transaction.execute(status -> options.saveAndFlush(option("beef", "ADD_ON", "牛肉", "Beef", "2.00")).id);
        service.reconcile(storeId, false);
        Long addonId = service.getAddons(storeId).addons().get(0).id();

        transaction.executeWithoutResult(status -> {
            MenuItemOption before = options.findById(optionId).orElseThrow();
            before.sort_order = 70;
            var request = new StoreAddonService.WriteRequest();
            request.name_zh = "精选牛肉";
            request.name_en = "Premium beef";
            request.price = new BigDecimal("4.25");
            request.active = false;
            service.update(addonId, request);

            // A later JPA edit must load the JDBC-updated values, rather than
            // flushing an old managed snapshot over the catalog materialization.
            MenuItemOption after = options.findById(optionId).orElseThrow();
            assertThat(after.name_zh).isEqualTo("精选牛肉");
            assertThat(after.price_delta).isEqualByComparingTo("4.25");
            assertThat(after.sort_order).isEqualTo(70);
            after.sort_order = 80;
            entityManager.flush();
        });

        MenuItemOption result = options.findById(optionId).orElseThrow();
        assertThat(result.name_zh).isEqualTo("精选牛肉");
        assertThat(result.price_delta).isEqualByComparingTo("4.25");
        assertThat(result.is_active).isFalse();
        assertThat(result.addon_eligible).isTrue();
        assertThat(result.sort_order).isEqualTo(80);
        assertThat(stores.findMenuRevisionById(storeId)).isEqualTo(3L);
    }

    @Test
    void lateOuterTransactionFailureRollsBackJpaJdbcCatalogAndRevisionTogether() {
        Long optionId = transaction.execute(status -> options.saveAndFlush(option("beef", "ADD_ON", "牛肉", "Beef", "2.00")).id);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            MenuItemOption pending = options.findById(optionId).orElseThrow();
            pending.name_zh = "回滚名称";
            service.reconcile(storeId, false);
            assertThat(service.getAddons(storeId).addons()).hasSize(1);
            throw new BusinessException("PROVISIONING_LATE_FAILURE");
        })).isInstanceOf(BusinessException.class).hasMessage("PROVISIONING_LATE_FAILURE");

        MenuItemOption unchanged = options.findById(optionId).orElseThrow();
        assertThat(unchanged.name_zh).isEqualTo("牛肉");
        assertThat(unchanged.store_addon_id).isNull();
        assertThat(unchanged.addon_eligible).isNull();
        assertThat(stores.findMenuRevisionById(storeId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from store_addons where store_id=?", Integer.class, storeId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from organization_addon_definitions where organization_id=?", Integer.class, organizationId)).isZero();
    }

    @Test
    void materializationPricingMirrorBeforeReconciliationPreservesExplicitAddonPriceConflicts() {
        transaction.executeWithoutResult(status -> {
            MenuItem secondItem = new MenuItem();
            secondItem.store_id = storeId;
            secondItem.name_zh = "另一菜品";
            secondItem.name_en = "Second item";
            secondItem.base_price = new BigDecimal("10.00");
            secondItem.is_active = true;
            secondItem.is_sold_out = false;
            secondItem.created_at = LocalDateTime.now();
            secondItem.updated_at = secondItem.created_at;
            items.saveAndFlush(secondItem);
            MenuItemOption first = options.saveAndFlush(option("combo", "ADD_ON", "套餐", "Combo", "2.00"));
            MenuItemOption second = option("combo", "ADD_ON", "套餐", "Combo", "4.00");
            second.menu_item_id = secondItem.id;
            options.saveAndFlush(second);
            MenuItemOption typedSizeAddon = option("size_large", "ADD_ON", "大碗", "Large", "6.25");
            typedSizeAddon.option_type = "size";
            options.saveAndFlush(typedSizeAddon);
            MenuItemOption unrelated = options.saveAndFlush(option("combo", "REMOVE", "套餐", "Combo", "0.50"));
            MenuItemOption genuineCombo = option("size_large", "COMBO", "大碗", "Large", "1.00");
            genuineCombo.option_type = "size";
            options.saveAndFlush(genuineCombo);
            MenuItemOption legacyCombo = options.saveAndFlush(option(null, null, "套餐", "Combo", "1.00"));
            savePricingPolicy();

            // Provisioning materializes pricing and runs this native mirror before
            // reconciliation; conflicting source prices must still be visible then.
            assertThat(pricingPolicies.mirrorPolicyToSizeAndComboOptions(storeId)).isEqualTo(2);
            assertThat(options.findById(genuineCombo.id).orElseThrow().price_delta).isEqualByComparingTo("7.50");
            assertThat(options.findById(legacyCombo.id).orElseThrow().price_delta).isEqualByComparingTo("7.50");
            assertThat(options.findById(unrelated.id).orElseThrow().price_delta).isEqualByComparingTo("0.50");
            assertThat(options.findById(typedSizeAddon.id).orElseThrow().price_delta).isEqualByComparingTo("6.25");

            var dryRun = service.reconcile(storeId, true);
            var applied = service.reconcile(storeId, false);
            var replay = service.reconcile(storeId, false);
            for (var report : java.util.List.of(dryRun, applied, replay)) {
                assertThat(report.conflicts()).singleElement().satisfies(conflict -> {
                    assertThat(conflict.code()).isEqualTo("combo");
                    assertThat(conflict.reason()).isEqualTo("ADDON_BUSINESS_VALUES_CONFLICT");
                    assertThat(conflict.prices()).usingElementComparator(BigDecimal::compareTo)
                        .containsExactlyInAnyOrder(new BigDecimal("2.00"), new BigDecimal("4.00"));
                });
                MenuItemOption firstUnchanged = options.findById(first.id).orElseThrow();
                MenuItemOption secondUnchanged = options.findById(second.id).orElseThrow();
                assertThat(firstUnchanged.store_addon_id).isNull();
                assertThat(secondUnchanged.store_addon_id).isNull();
                assertThat(firstUnchanged.price_delta).isEqualByComparingTo("2.00");
                assertThat(secondUnchanged.price_delta).isEqualByComparingTo("4.00");
            }
            assertThat(applied.linked_options()).isEqualTo(1);
            assertThat(replay.linked_options()).isZero();
            assertThat(service.getAddons(storeId).addons()).singleElement().satisfies(addon -> {
                assertThat(addon.code()).isEqualTo("size_large");
                assertThat(addon.price()).isEqualByComparingTo("6.25");
            });
        });
    }

    @Test
    void nativePricingMirrorLeavesLinkedAddonCodeAndNameComboUnderCatalogAuthority() {
        transaction.executeWithoutResult(status -> {
            MenuItemOption codeCombo = options.saveAndFlush(option("combo", "ADD_ON", "特别加料", "Extra", "4.25"));
            MenuItemOption namedCombo = options.saveAndFlush(option("extra_beef", "ADD_ON", "加牛肉", "Combo", "3.25"));
            MenuItemOption genuineCombo = options.saveAndFlush(option("combo", "COMBO", "套餐", "Combo", "1.00"));
            MenuItemOption size = option("size_regular", "SIZE", "中碗", "Regular", "0.00");
            size.option_type = "size";
            options.saveAndFlush(size);
            assertThat(service.reconcile(storeId, false).linked_options()).isEqualTo(2);

            savePricingPolicy();
            assertThat(pricingPolicies.mirrorPolicyToSizeAndComboOptions(storeId)).isEqualTo(2);

            assertThat(options.findById(codeCombo.id).orElseThrow().price_delta).isEqualByComparingTo("4.25");
            assertThat(options.findById(namedCombo.id).orElseThrow().price_delta).isEqualByComparingTo("3.25");
            assertThat(options.findById(genuineCombo.id).orElseThrow().price_delta).isEqualByComparingTo("7.50");
            assertThat(options.findById(size.id).orElseThrow().price_delta).isEqualByComparingTo("2.50");
        });
    }

    private void savePricingPolicy() {
        StorePricingPolicy policy = new StorePricingPolicy();
        policy.store_id = storeId;
        policy.size_small_delta = BigDecimal.ZERO;
        policy.size_regular_delta = new BigDecimal("2.50");
        policy.size_large_delta = new BigDecimal("5.00");
        policy.combo_delta = new BigDecimal("7.50");
        policy.policy_revision = 1L;
        policy.created_at = LocalDateTime.now();
        policy.updated_at = policy.created_at;
        pricingPolicies.saveAndFlush(policy);
    }

    private MenuItemOption option(String code, String group, String zh, String en, String price) {
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = itemId;
        option.option_type = "addon";
        option.option_code = code;
        option.option_group = group;
        option.name_zh = zh;
        option.name_en = en;
        option.price_delta = new BigDecimal(price);
        option.is_active = true;
        option.sort_order = 10;
        option.created_at = LocalDateTime.now();
        option.updated_at = option.created_at;
        return option;
    }
}
