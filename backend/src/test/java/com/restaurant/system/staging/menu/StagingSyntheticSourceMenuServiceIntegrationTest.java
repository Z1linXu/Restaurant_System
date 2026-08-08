package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.impl.MenuRevisionServiceImpl;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneGraphComposer;
import com.restaurant.system.owner.menu.StoreMenuCloneOptionPlanValidator;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileRegistry;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsComposer;
import com.restaurant.system.owner.menu.profile.ChinatownMenuProfileOverridesComposer;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneRequestCoordinator;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneValidationCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneValidationResult;
import com.restaurant.system.owner.service.impl.StoreMenuCloneTransactionServiceImpl;
import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapGuard;
import com.restaurant.system.staging.bootstrap.entity.StagingSyntheticBootstrapRequest;
import com.restaurant.system.staging.bootstrap.repository.StagingSyntheticBootstrapRequestRepository;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("staging-synthetic-bootstrap")
@ContextConfiguration(classes = StagingSyntheticSourceMenuServiceIntegrationTest.JpaSliceConfiguration.class)
@Import({
    StagingSyntheticBootstrapGuard.class,
    StagingSyntheticSourceMenuGuard.class,
    StagingSyntheticSourceMenuManifestFactory.class,
    StagingSyntheticSourceMenuPlanner.class,
    StagingSyntheticSourceMenuGraphWriter.class,
    StagingSyntheticSourceMenuServiceImpl.class,
    MenuRevisionServiceImpl.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StagingSyntheticSourceMenuServiceIntegrationTest {

    private static final long ORGANIZATION_ID = 83L;
    private static final String RUNTIME_SHA = "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c";
    private static final String TOOL_SHA = "1111111111111111111111111111111111111111";

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired private StagingSyntheticSourceMenuService service;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StagingSyntheticBootstrapRequestRepository bootstrapRequestRepository;
    @Autowired private MenuCategoryRepository categoryRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private MenuItemRepository itemRepository;
    @Autowired private MenuItemOptionRepository optionRepository;
    @Autowired private MenuRevisionService menuRevisionService;
    @Autowired private StagingSyntheticSourceMenuGraphWriter graphWriter;
    @Autowired private StagingSyntheticSourceMenuManifestFactory manifestFactory;
    @Autowired private StagingSyntheticSourceMenuPlanner planner;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private StagingSyntheticSourceMenuCommitHook commitHook;

    @BeforeEach
    void cleanDatabase() {
        bootstrapRequestRepository.deleteAll();
        optionRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();
        stationRepository.deleteAll();
        storeRepository.deleteAll();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            entityManager.createNativeQuery("ALTER TABLE stores ALTER COLUMN id RESTART WITH 1").executeUpdate()
        );
    }

    @Test
    void firstApplyCreatesOneGraphAndExactReplayKeepsRevision() {
        Store source = sourceStore();

        StagingSyntheticSourceMenuResult planned = service.plan(spec());
        StagingSyntheticSourceMenuResult created = service.apply(spec());
        StagingSyntheticSourceMenuResult replay = service.apply(spec());

        assertThat(source.id).isEqualTo(1L);
        assertThat(planned.resultCode()).isEqualTo("STG005_SOURCE_MENU_READY_TO_CREATE");
        assertThat(created.replayed()).isFalse();
        assertThat(created.revisionBefore()).isEqualTo(1L);
        assertThat(created.revisionAfter()).isEqualTo(2L);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.revisionBefore()).isEqualTo(2L);
        assertThat(replay.revisionAfter()).isEqualTo(2L);
        assertThat(replay.manifestFingerprint()).isEqualTo(created.manifestFingerprint());
        assertCounts(source.id, 4, 3, 13, 38);
    }

    @Test
    void partialGraphFailsClosedWithoutRepairOrRevisionChange() {
        Store source = sourceStore();
        MenuCategory partial = new MenuCategory();
        partial.store_id = source.id;
        partial.code = "PARTIAL";
        partial.name_zh = "STG005_PARTIAL";
        partial.name_en = "STG005_PARTIAL";
        partial.sort_order = 1;
        partial.is_active = true;
        partial.created_at = LocalDateTime.now();
        partial.updated_at = partial.created_at;
        categoryRepository.saveAndFlush(partial);

        assertThatThrownBy(() -> service.apply(spec()))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_SOURCE_MENU_GRAPH_CONFLICT")
            );

        assertThat(categoryRepository.countAllByStoreId(source.id)).isOne();
        assertThat(stationRepository.countAllByStoreId(source.id)).isZero();
        assertThat(itemRepository.countAllByStoreId(source.id)).isZero();
        assertThat(storeRepository.findMenuRevisionById(source.id)).isEqualTo(1L);
    }

    @Test
    void matchingStoreWithoutCompletedBootstrapProvenanceIsRejected() {
        Store source = store("STG005_SRC_R01", "active");

        assertThatThrownBy(() -> service.apply(spec()))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_SOURCE_MENU_BOOTSTRAP_UNAVAILABLE")
            );

        assertCounts(source.id, 0, 0, 0, 0);
        assertThat(storeRepository.findMenuRevisionById(source.id)).isEqualTo(1L);
    }

    @Test
    void danglingParentAndEnabledBarTaskModeAreRejected() {
        Store source = sourceStore();
        source.enable_bar_kitchen_tasks = true;
        storeRepository.saveAndFlush(source);

        assertThatThrownBy(() -> service.apply(spec()))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_SOURCE_MENU_STORE_STATE_REJECTED")
            );

        source.enable_bar_kitchen_tasks = false;
        storeRepository.saveAndFlush(source);
        service.apply(spec());
        var option = optionRepository.findAll().get(0);
        option.parent_option_id = 999999L;
        optionRepository.saveAndFlush(option);

        assertThatThrownBy(() -> service.apply(spec()))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_SOURCE_MENU_GRAPH_CONFLICT")
            );
        assertThat(storeRepository.findMenuRevisionById(source.id)).isEqualTo(2L);
    }

    @Test
    void cloneRelevantCostDriftIsNotAcceptedAsReplay() {
        Store source = sourceStore();
        service.apply(spec());
        var item = itemRepository.findAllByStoreIdOrderByIdAsc(source.id).stream()
            .filter(candidate -> candidate.sku.equals("traditional_beef_noodle"))
            .findFirst()
            .orElseThrow();
        item.cost_per_item = new BigDecimal("2.00");
        itemRepository.saveAndFlush(item);

        assertThatThrownBy(() -> service.apply(spec()))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_SOURCE_MENU_GRAPH_CONFLICT")
            );
        assertThat(storeRepository.findMenuRevisionById(source.id)).isEqualTo(2L);
    }

    @Test
    void graphWriterPersistsParentOptionIdsBeforeReplayReads() {
        Store source = store("STG005_SRC_R01", "active");
        StagingSyntheticSourceMenuManifest original = manifestFactory.create();
        List<StagingSyntheticSourceMenuManifest.Option> options = original.options().stream()
            .map(option -> option.itemSku().equals("traditional_beef_noodle")
                && option.optionCode().equals("extra_meat")
                ? new StagingSyntheticSourceMenuManifest.Option(
                    option.itemSku(), option.optionType(), option.optionGroup(), option.optionCode(),
                    "tea_egg", option.nameZh(), option.nameEn(), option.priceDelta(), option.active(),
                    option.sortOrder()
                )
                : option)
            .toList();
        StagingSyntheticSourceMenuManifest withParent = new StagingSyntheticSourceMenuManifest(
            original.manifestCode(), original.manifestVersion(), original.topologyNamespace(),
            original.categories(), original.stations(), original.items(), options
        );
        planner.plan(withParent);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            graphWriter.persist(source.id, withParent)
        );

        List<MenuItemOption> persisted = optionRepository.findAll();
        MenuItemOption parent = persisted.stream()
            .filter(option -> "tea_egg".equals(option.option_code))
            .findFirst()
            .orElseThrow();
        MenuItemOption child = persisted.stream()
            .filter(option -> "extra_meat".equals(option.option_code))
            .findFirst()
            .orElseThrow();
        assertThat(parent.parent_option_id).isNull();
        assertThat(child.parent_option_id).isEqualTo(parent.id);
    }

    @Test
    void lateFailureRollsBackWholeGraphAndRevision() {
        Store source = sourceStore();
        doThrow(new IllegalStateException("synthetic late failure"))
            .when(commitHook)
            .beforeRevisionIncrement();

        assertThatThrownBy(() -> service.apply(spec()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("synthetic late failure");

        assertCounts(source.id, 0, 0, 0, 0);
        assertThat(storeRepository.findMenuRevisionById(source.id)).isEqualTo(1L);
    }

    @Test
    void concurrentApplySerializesToOneCreateAndOneReplay() throws Exception {
        Store source = sourceStore();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<StagingSyntheticSourceMenuResult> task = () -> {
            ready.countDown();
            start.await();
            return service.apply(spec());
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<StagingSyntheticSourceMenuResult> first = executor.submit(task);
            Future<StagingSyntheticSourceMenuResult> second = executor.submit(task);
            ready.await();
            start.countDown();
            List<StagingSyntheticSourceMenuResult> results = List.of(first.get(), second.get());

            assertThat(results).filteredOn(StagingSyntheticSourceMenuResult::replayed).hasSize(1);
            assertThat(results).filteredOn(result -> !result.replayed()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertCounts(source.id, 4, 3, 13, 38);
        assertThat(storeRepository.findMenuRevisionById(source.id)).isEqualTo(2L);
    }

    @Test
    void appliedGraphPassesExistingAl003ReadOnlyValidation() {
        Store source = sourceStore();
        service.apply(spec());
        Store target = targetStore();
        ChinatownMenuCloneProfile profile = new ChinatownMenuCloneProfile();
        StoreMenuCloneProfileRegistry registry = new StoreMenuCloneProfileRegistry(List.of(profile));
        List<StoreMenuCloneGraphComposer> composers = List.of(
            new StoreMenuCloneSourceOptionsComposer(registry),
            new ChinatownMenuProfileOverridesComposer()
        );
        StoreMenuCloneTransactionServiceImpl cloneService = new StoreMenuCloneTransactionServiceImpl(
            storeRepository,
            categoryRepository,
            stationRepository,
            itemRepository,
            optionRepository,
            menuRevisionService,
            mock(OwnerStoreMenuCloneRequestCoordinator.class),
            registry,
            composers,
            new StoreMenuCloneOptionPlanValidator()
        );

        OwnerStoreMenuCloneValidationResult validation = cloneService.validate(
            new OwnerStoreMenuCloneValidationCommand(
                ORGANIZATION_ID,
                source.id,
                target.id,
                ChinatownMenuCloneProfile.PROFILE_CODE
            )
        );

        assertThat(validation.valid()).isTrue();
        assertThat(validation.expectedCategoryCount()).isEqualTo(4);
        assertThat(validation.expectedStationCount()).isEqualTo(3);
        assertThat(validation.expectedItemCount()).isEqualTo(17);
        assertThat(validation.expectedOptionCount()).isEqualTo(74);
        assertThat(validation.missingCodes()).isEmpty();
        assertThat(validation.duplicateCodes()).isEmpty();
        assertThat(validation.warnings()).isEmpty();
        assertCounts(target.id, 0, 0, 0, 0);
    }

    private Store sourceStore() {
        Store source = store("STG005_SRC_R01", "active");
        LocalDateTime now = LocalDateTime.now();
        StagingSyntheticBootstrapRequest request = new StagingSyntheticBootstrapRequest();
        request.runId = "STG005_RUN_R01";
        request.requestFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        request.status = "COMPLETED";
        request.runtimeSha = RUNTIME_SHA;
        request.toolSha = TOOL_SHA;
        request.organizationId = ORGANIZATION_ID;
        request.sourceStoreId = source.id;
        request.ownerUserId = 701L;
        request.resultCode = "STG005_SYNTHETIC_BOOTSTRAP_READY";
        request.createdAt = now;
        request.updatedAt = now;
        request.completedAt = now;
        bootstrapRequestRepository.saveAndFlush(request);
        return source;
    }

    private Store targetStore() {
        return store("STG005_TARGET_R01", "inactive");
    }

    private Store store(String code, String status) {
        LocalDateTime now = LocalDateTime.now();
        Store store = new Store();
        store.organization_id = ORGANIZATION_ID;
        store.code = code;
        store.name = code;
        store.status = status;
        store.enable_bar_kitchen_tasks = false;
        store.printing_enabled = false;
        store.printing_mode = "DISABLED";
        store.menu_revision = 1L;
        store.menu_updated_at = now;
        store.created_at = now;
        store.updated_at = now;
        return storeRepository.saveAndFlush(store);
    }

    private StagingSyntheticSourceMenuSpec spec() {
        return new StagingSyntheticSourceMenuSpec(1L, "STG005_SRC_R01", RUNTIME_SHA, TOOL_SHA);
    }

    private void assertCounts(
        Long storeId,
        long categories,
        long stations,
        long items,
        int options
    ) {
        assertThat(categoryRepository.countAllByStoreId(storeId)).isEqualTo(categories);
        assertThat(stationRepository.countAllByStoreId(storeId)).isEqualTo(stations);
        assertThat(itemRepository.countAllByStoreId(storeId)).isEqualTo(items);
        List<Long> itemIds = itemRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .map(item -> item.id)
            .toList();
        int optionCount = itemIds.isEmpty()
            ? 0
            : optionRepository.findAllByStoreIdAndMenuItemIdsOrdered(storeId, itemIds).size();
        assertThat(optionCount).isEqualTo(options);
    }
}
