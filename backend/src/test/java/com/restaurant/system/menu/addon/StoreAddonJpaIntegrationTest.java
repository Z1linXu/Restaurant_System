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
    "spring.jpa.show-sql=false",
    "app.template.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = StoreAddonJpaIntegrationTest.JpaSliceConfiguration.class)
@Import({StoreAddonService.class, StoreAddonOptionResolver.class,
    com.restaurant.system.menu.remove.StoreRemoveOptionResolver.class,
    com.restaurant.system.menu.template.TemplateMenuService.class,
    com.restaurant.system.delivery.AlloMenuImporter.class, MenuRevisionServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoreAddonJpaIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
        @org.springframework.context.annotation.Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
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
    @MockBean private com.restaurant.system.menu.template.TemplateComboAdapter comboAdapter;
    @MockBean private com.restaurant.system.menu.service.StorePricingPolicyService pricing;
    @Autowired private com.restaurant.system.menu.template.TemplateMenuService templateMenus;
    @Autowired private StoreAddonOptionResolver resolver;
    @Autowired private com.restaurant.system.menu.repository.MenuCategoryRepository categories;
    @Autowired private com.restaurant.system.delivery.AlloMenuImporter importer;

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
    void templateAddonReadsAndValidatesCurrentCatalogAndStaleSaveCannotRestoreOldValues() {
        prepareTemplateCategory();
        var request = new StoreAddonService.WriteRequest();
        request.store_id=storeId;request.code="extra_beef";request.name_zh="加牛肉";request.name_en="Extra Beef";
        request.price=new BigDecimal("2.00");request.active=true;
        var canonical=service.create(request);
        var definition=templateDefinition("ADD_ON",canonical.code(),"2.00");
        definition.groups.get(0).options.get(0).store_addon_id=canonical.id();
        definition.groups.get(0).options.get(0).name_fr="Bœuf supplémentaire";
        var stale=templateMenus.saveItem(storeId,itemId,definition);
        var oldSelection=selection(stale.groups.get(0).options.get(0));
        request.name_zh="精选牛肉";request.name_en="Premium beef";request.price=new BigDecimal("3.25");
        service.update(canonical.id(),request);
        var current=templateMenus.getItem(storeId,itemId).groups.get(0).options.get(0);
        assertThat(current.name_zh).isEqualTo("精选牛肉");
        assertThat(current.name_en).isEqualTo("Premium beef");
        assertThat(current.name_fr).isEqualTo("Bœuf supplémentaire");
        assertThat(current.price_delta).isEqualByComparingTo("3.25");
        assertThatThrownBy(()->templateMenus.validateSelection(storeId,oldSelection)).hasMessage("ADDON_CATALOG_CHANGED");
        templateMenus.validateSelection(storeId,selection(current));

        var saved=templateMenus.saveItem(storeId,itemId,stale).groups.get(0).options.get(0);
        assertThat(saved.price_delta).isEqualByComparingTo("3.25");
        assertThat(saved.name_zh).isEqualTo("精选牛肉");
        assertThat(saved.name_fr).isEqualTo("Bœuf supplémentaire");
        assertThat(service.getAddons(storeId).addons().get(0).price()).isEqualByComparingTo("3.25");
        MenuItemOption raw=options.findById(saved.id).orElseThrow();
        assertThat(raw.price_delta).isEqualByComparingTo("3.25");
        assertThat(raw.addon_eligible).isTrue();

        request.active=false;service.update(canonical.id(),request);
        var inactive=templateMenus.getItem(storeId,itemId);
        assertThat(inactive.groups.get(0).options.get(0).is_active).isFalse();
        assertThat(inactive.groups.get(0).options.get(0).addon_eligible).isTrue();
        templateMenus.saveItem(storeId,itemId,inactive);
        assertThat(options.findById(saved.id).orElseThrow().addon_eligible).isTrue();
        request.active=true;service.update(canonical.id(),request);
        assertThat(templateMenus.getItem(storeId,itemId).groups.get(0).options.get(0).is_active).isTrue();
    }

    @Test
    void eligibilityOnAnExplicitEmptyTemplateCreatesAVisibleCanonicalChoice() {
        prepareTemplateCategory();
        templateMenus.saveItem(storeId,itemId,new com.restaurant.system.menu.template.TemplateMenuModels.Groups());
        var request=new StoreAddonService.WriteRequest();request.store_id=storeId;request.code="extra_egg";
        request.name_zh="加蛋";request.name_en="Extra Egg";request.price=new BigDecimal("1.25");request.active=true;
        var canonical=service.create(request);
        service.setEligibility(itemId,canonical.id(),true);
        var groups=templateMenus.getItem(storeId,itemId);
        assertThat(groups.groups).hasSize(1);
        var choice=groups.groups.get(0).options.get(0);
        assertThat(choice.store_addon_id).isEqualTo(canonical.id());
        assertThat(choice.price_delta).isEqualByComparingTo("1.25");
        templateMenus.validateSelection(storeId,selection(choice));
        service.setEligibility(itemId,canonical.id(),false);
        assertThatThrownBy(()->templateMenus.validateSelection(storeId,selection(choice))).hasMessage("ADDON_ASSIGNMENT_UNAVAILABLE");
    }

    @Test
    void customSpicyAndSizeChoicesWithMatchingCodesKeepIndependentPricesAndTranslations() {
        prepareTemplateCategory();
        var request=new StoreAddonService.WriteRequest();request.store_id=storeId;request.code="shared_code";
        request.name_zh="共享加料";request.name_en="Catalog";request.price=new BigDecimal("9.25");request.active=true;
        service.create(request);
        for(String kind:java.util.List.of("CUSTOM","SPICY_LEVEL","SIZE")) {
            var definition=templateDefinition(kind,"shared_code","0.50");
            definition.groups.get(0).options.get(0).name_fr="Choix indépendant";
            var choice=templateMenus.saveItem(storeId,itemId,definition).groups.get(0).options.get(0);
            assertThat(choice.store_addon_id).isNull();
            assertThat(choice.price_delta).isEqualByComparingTo("0.50");
            assertThat(choice.name_fr).isEqualTo("Choix indépendant");
            assertThat(resolver.requiresAssignment(storeId,kind,"shared_code")).isTrue();
            assertThat(templateMenus.isIndependentChoice(storeId,itemId,options.findById(choice.id).orElseThrow())).isTrue();
            templateMenus.validateSelection(storeId,selection(choice));
        }
    }

    @Test
    void ownerSaveCannotCreateAnUnlinkedPaidAddonOrUnlinkedLibraryDefinition() {
        prepareTemplateCategory();
        var definition=templateDefinition("ADD_ON","not_in_catalog","2.50");
        long revision=stores.findMenuRevisionById(storeId);
        assertThatThrownBy(()->templateMenus.saveItem(storeId,itemId,definition)).hasMessage("ADDON_ASSIGNMENT_REQUIRED");
        assertThat(options.findAllByMenuItemIdOrdered(itemId)).isEmpty();
        assertThat(templateMenus.getItem(storeId,itemId).groups).isEmpty();
        assertThat(items.findById(itemId).orElseThrow().template_option_groups_enabled).isFalse();
        assertThat(stores.findMenuRevisionById(storeId)).isEqualTo(revision);
        assertThatThrownBy(()->templateMenus.saveLibrary(storeId,definition)).hasMessage("ADDON_ASSIGNMENT_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from template_store_configuration where store_id=?",Long.class,storeId)).isZero();
        assertThat(service.getAddons(storeId).addons()).isEmpty();
    }

    @Test
    void ownerSaveCannotConvertAnExistingUnlinkedCustomChoiceIntoAnAddon() {
        prepareTemplateCategory();
        var request=new StoreAddonService.WriteRequest();request.store_id=storeId;request.code="shared_code";
        request.name_zh="加料";request.name_en="Catalog";request.price=new BigDecimal("3.25");request.active=true;
        service.create(request);
        var definition=templateMenus.saveItem(storeId,itemId,templateDefinition("CUSTOM","shared_code","0.50"));
        long revision=stores.findMenuRevisionById(storeId);
        var existing=definition.groups.get(0).options.get(0);
        definition.groups.get(0).kind="ADD_ON";
        assertThatThrownBy(()->templateMenus.saveItem(storeId,itemId,definition)).hasMessage("ADDON_ASSIGNMENT_REQUIRED");
        var unchanged=templateMenus.getItem(storeId,itemId);
        assertThat(unchanged.groups.get(0).kind).isEqualTo("CUSTOM");
        assertThat(unchanged.groups.get(0).options.get(0).id).isEqualTo(existing.id);
        assertThat(unchanged.groups.get(0).options.get(0).store_addon_id).isNull();
        assertThat(unchanged.groups.get(0).options.get(0).price_delta).isEqualByComparingTo("0.50");
        assertThat(stores.findMenuRevisionById(storeId)).isEqualTo(revision);
    }

    @Test
    void actualAlloImportLinksCanonicalAddonsAndUnchangedReplayDoesNotWriteOrLoseOwnerPrices() {
        jdbc.execute("create table if not exists merchant_menu_import_entries(store_id bigint,source_key varchar(160),entity_kind varchar(32),entity_id bigint,source_hash varchar(64),applied_hash varchar(64),imported_at timestamp default current_timestamp,primary key(store_id,source_key))");
        jdbc.update("update stores set code='ALLO_NOUILLES_TEST' where id=?",storeId);
        jdbc.update("insert into stations(store_id,code,name,is_active) values(?,'BACK_KITCHEN','Synthetic kitchen',true)",storeId);
        var source=syntheticAlloSource();
        assertThat(importer.apply(storeId,source).created()).isEqualTo(34);
        assertThat(service.getAddons(storeId).addons()).hasSize(13);
        assertThat(jdbc.queryForObject("select count(*) from menu_item_options o join menu_items i on i.id=o.menu_item_id where i.store_id=? and o.store_addon_id is not null and o.template_group_code='paid_addons'",Long.class,storeId)).isEqualTo(39L);
        long revision=stores.findMenuRevisionById(storeId);
        var ledger=jdbc.queryForList("select * from merchant_menu_import_entries where store_id=? order by source_key",storeId);
        var repeat=importer.apply(storeId,source);
        assertThat(repeat.unchanged()).isEqualTo(34);
        assertThat(repeat.created()+repeat.updated()+repeat.ownerEditedPreserved()).isZero();
        assertThat(stores.findMenuRevisionById(storeId)).isEqualTo(revision);
        assertThat(jdbc.queryForList("select * from merchant_menu_import_entries where store_id=? order by source_key",storeId)).isEqualTo(ledger);
        var addon=service.getAddons(storeId).addons().get(0);
        var update=new StoreAddonService.WriteRequest();update.name_zh="店主名称";update.name_en="Owner label";update.price=new BigDecimal("4.25");update.active=true;
        service.update(addon.id(),update);
        revision=stores.findMenuRevisionById(storeId);
        assertThat(importer.apply(storeId,source).ownerEditedPreserved()).isEqualTo(3);
        assertThat(stores.findMenuRevisionById(storeId)).isEqualTo(revision);
        assertThat(service.getAddons(storeId).addons().stream().filter(a->a.id().equals(addon.id())).findFirst().orElseThrow().price()).isEqualByComparingTo("4.25");
    }

    private com.fasterxml.jackson.databind.JsonNode syntheticAlloSource() {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var source=mapper.createObjectNode();
        source.put("schema_version","ALLO_NOUILLES_SOURCE_MENU_V1");source.put("currency","CAD");source.put("menu_version","synthetic");
        var categories=source.putArray("categories");for(String code:java.util.List.of("NOODLE","RICE","CAT0","CAT1","CAT2"))categories.add(syntheticNames(mapper,code));
        var items=source.putArray("items");for(int n=0;n<32;n++){var row=syntheticNames(mapper,"ITEM"+n);row.put("category_code","CAT0");row.put("base_price","10.00");if(n<3)row.putArray("addon_group_codes").add("PAID");items.add(row);}
        var group=syntheticNames(mapper,"PAID");var references=group.putArray("addon_codes");var addons=source.putArray("addons");
        for(int n=0;n<13;n++){String code="extra_"+n;var row=syntheticNames(mapper,code);row.put("addon_group_code","PAID");row.put("price","2.00");addons.add(row);references.add(code);}
        source.putArray("addon_groups").add(group);source.putArray("option_groups");source.putArray("combo_offers");source.putObject("free_refills").put("cross_eligibility","SAME_CATEGORY_ONLY");return source;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode syntheticNames(com.fasterxml.jackson.databind.ObjectMapper mapper,String code) {
        var row=mapper.createObjectNode();row.put("code",code);row.put("name_zh","合成 "+code);row.put("name_en","Synthetic "+code);row.put("name_fr","Synthétique "+code);return row;
    }

    private void prepareTemplateCategory() {
        transaction.executeWithoutResult(status->{
            var category=new com.restaurant.system.menu.entity.MenuCategory();category.store_id=storeId;category.code="SYNTHETIC";
            category.name_zh="测试";category.name_en="Test";category.is_active=true;category.sort_order=0;
            category.created_at=LocalDateTime.now();category.updated_at=category.created_at;
            category=categories.saveAndFlush(category);
            var item=items.findById(itemId).orElseThrow();item.category_id=category.id;items.saveAndFlush(item);
        });
    }

    private com.restaurant.system.menu.template.TemplateMenuModels.Groups templateDefinition(String kind,String code,String price) {
        var definition=new com.restaurant.system.menu.template.TemplateMenuModels.Groups();
        var group=new com.restaurant.system.menu.template.TemplateMenuModels.Group();group.group_instance_code="group_"+kind.toLowerCase(java.util.Locale.ROOT);group.kind=kind;group.name="Synthetic";group.selection_mode="MULTIPLE";
        var choice=new com.restaurant.system.menu.template.TemplateMenuModels.Option();choice.option_code=code;choice.name_zh="原名称";choice.name_en="Original";choice.price_delta=new BigDecimal(price);
        group.options.add(choice);definition.groups.add(group);return definition;
    }

    private com.restaurant.system.order.dto.CreateOrderItemRequest selection(com.restaurant.system.menu.template.TemplateMenuModels.Option choice) {
        var line=new com.restaurant.system.order.dto.CreateOrderItemRequest();line.menu_item_id=itemId;line.quantity=1;line.unit_price_snapshot=new BigDecimal("10.00");
        var selected=new com.restaurant.system.order.dto.CreateOrderItemOptionRequest();selected.option_id=choice.id;selected.option_code_snapshot=choice.option_code;selected.option_group_snapshot=choice.option_group;selected.option_price_snapshot=choice.price_delta;selected.quantity=1;
        line.options=java.util.List.of(selected);return line;
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
