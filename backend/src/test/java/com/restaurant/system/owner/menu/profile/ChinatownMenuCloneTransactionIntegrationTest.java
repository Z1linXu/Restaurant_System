package com.restaurant.system.owner.menu.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.impl.MenuRevisionServiceImpl;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneCompositionContext;
import com.restaurant.system.owner.menu.StoreMenuCloneGraphComposer;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileRegistry;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsComposer;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneRequestCoordinator;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionResult;
import com.restaurant.system.owner.service.impl.StoreMenuCloneTransactionServiceImpl;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
@ContextConfiguration(classes = ChinatownMenuCloneTransactionIntegrationTest.JpaSliceConfiguration.class)
@Import(MenuRevisionServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChinatownMenuCloneTransactionIntegrationTest {

    private static final long ORGANIZATION_ID = 83L;

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuCategoryRepository categoryRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private MenuItemRepository itemRepository;
    @Autowired private MenuItemOptionRepository optionRepository;
    @Autowired private MenuRevisionService menuRevisionService;
    @Autowired private PlatformTransactionManager transactionManager;

    private final OwnerStoreMenuCloneRequestCoordinator coordinator = mock(
        OwnerStoreMenuCloneRequestCoordinator.class
    );

    @BeforeEach
    void cleanDatabase() {
        transaction().executeWithoutResult(status -> {
            optionRepository.deleteAll();
            itemRepository.deleteAll();
            categoryRepository.deleteAll();
            stationRepository.deleteAll();
            storeRepository.deleteAll();
        });
    }

    @Test
    void composesReviewedChinatownGraphAndRollsBackLateFailure() {
        SourceFixture fixture = sourceFixture();
        assertThat(fixture.source().id).isEqualTo(ChinatownMenuCloneProfile.SOURCE_STORE_ID);
        SourceState sourceBefore = sourceState(fixture.source().id);
        ChinatownMenuCloneProfile profile = new ChinatownMenuCloneProfile();

        OwnerStoreMenuCloneTransactionResult result = execute(
            service(profile, standardComposers(profile)),
            command(901L, fixture.source().id, fixture.successTarget().id)
        );

        assertThat(result.evidence().createdCategoryCount()).isEqualTo(4);
        assertThat(result.evidence().createdStationCount()).isEqualTo(3);
        assertThat(result.evidence().createdItemCount()).isEqualTo(17);
        assertThat(result.evidence().createdOptionCount()).isEqualTo(74);
        assertThat(result.evidence().targetRevisionBefore()).isEqualTo(1L);
        assertThat(result.evidence().targetRevisionAfter()).isEqualTo(2L);
        assertThat(sourceState(fixture.source().id)).isEqualTo(sourceBefore);

        List<MenuItem> targetItems = itemRepository.findAllByStoreIdOrderByIdAsc(fixture.successTarget().id);
        assertThat(targetItems).extracting(item -> item.sku)
            .containsExactlyElementsOf(profile.items().stream().map(item -> item.targetSku()).toList());
        assertThat(targetItems).anySatisfy(item -> {
            assertThat(item.sku).isEqualTo("seven_up");
            assertThat(item.name_en).isEqualTo("7 Up");
            assertThat(item.base_price).isEqualByComparingTo("3.00");
        });

        List<Long> targetItemIds = targetItems.stream().map(item -> item.id).toList();
        List<MenuItemOption> targetOptions = optionRepository.findAllByMenuItemIdsOrdered(targetItemIds);
        assertThat(targetOptions).hasSize(74);
        assertThat(targetOptions.stream().filter(option -> "NOODLE_TYPE".equals(option.option_group))).hasSize(35);
        assertThat(targetOptions.stream().filter(option -> "SIZE".equals(option.option_group))).hasSize(8);
        assertThat(targetOptions.stream().filter(option -> "COMBO".equals(option.option_group))).hasSize(4);
        assertThat(targetOptions.stream().filter(option -> "COMBO_EGG".equals(option.option_group))).hasSize(4);
        assertThat(targetOptions.stream().filter(option -> "COMBO_SIDE".equals(option.option_group))).hasSize(12);
        assertThat(targetOptions).noneMatch(option -> "COMBO_SIDE_REMOVE".equals(option.option_group));
        assertThat(targetOptions.stream().filter(option -> "tea_egg".equals(option.option_code)
            && "ADD_ON".equals(option.option_group))).hasSize(5);
        assertThat(targetOptions.stream().filter(option -> "extra_meat".equals(option.option_code)
            && "ADD_ON".equals(option.option_group))).hasSize(5).allSatisfy(option ->
                assertThat(option.price_delta).isEqualByComparingTo("6.99")
            );
        assertThat(targetOptions).anySatisfy(option -> {
            assertThat(option.option_code).isEqualTo("tea_egg");
            assertThat(option.option_group).isEqualTo("ADD_ON");
            assertThat(option.price_delta).isEqualByComparingTo("1.99");
        });
        assertThat(targetOptions).anySatisfy(option -> {
            assertThat(option.option_code).isEqualTo("extra_meat");
            assertThat(option.option_group).isEqualTo("ADD_ON");
            assertThat(option.price_delta).isEqualByComparingTo("6.99");
        });
        verify(coordinator, times(1)).complete(any(OwnerStoreMenuCloneSuccessEvidence.class));

        StoreMenuCloneGraphComposer lateFailure = new StoreMenuCloneGraphComposer() {
            @Override
            public String identity() {
                return "late-profile-failure";
            }

            @Override
            public Phase phase() {
                return Phase.PROFILE_OVERRIDES;
            }

            @Override
            public int order() {
                return 200;
            }

            @Override
            public boolean supports(String profileCode) {
                return ChinatownMenuCloneProfile.PROFILE_CODE.equals(profileCode);
            }

            @Override
            public int compose(StoreMenuCloneCompositionContext context) {
                throw new IllegalStateException("synthetic late failure");
            }
        };
        List<StoreMenuCloneGraphComposer> failingComposers = new ArrayList<>(standardComposers(profile));
        failingComposers.add(lateFailure);
        long optionCountBeforeFailure = optionRepository.count();

        assertThatThrownBy(() -> execute(
            service(profile, failingComposers),
            command(902L, fixture.source().id, fixture.failureTarget().id)
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("synthetic late failure");

        assertThat(categoryRepository.countAllByStoreId(fixture.failureTarget().id)).isZero();
        assertThat(stationRepository.countAllByStoreId(fixture.failureTarget().id)).isZero();
        assertThat(itemRepository.countAllByStoreId(fixture.failureTarget().id)).isZero();
        assertThat(optionRepository.count()).isEqualTo(optionCountBeforeFailure);
        assertThat(storeRepository.findMenuRevisionById(fixture.failureTarget().id)).isEqualTo(1L);
        assertThat(sourceState(fixture.source().id)).isEqualTo(sourceBefore);
        verify(coordinator, times(1)).complete(any(OwnerStoreMenuCloneSuccessEvidence.class));
    }

    private List<StoreMenuCloneGraphComposer> standardComposers(ChinatownMenuCloneProfile profile) {
        StoreMenuCloneProfileRegistry registry = new StoreMenuCloneProfileRegistry(List.of(profile));
        return List.of(
            new StoreMenuCloneSourceOptionsComposer(optionRepository, registry),
            new ChinatownMenuProfileOverridesComposer(optionRepository)
        );
    }

    private StoreMenuCloneTransactionServiceImpl service(
        ChinatownMenuCloneProfile profile,
        List<StoreMenuCloneGraphComposer> composers
    ) {
        return new StoreMenuCloneTransactionServiceImpl(
            storeRepository,
            categoryRepository,
            stationRepository,
            itemRepository,
            optionRepository,
            menuRevisionService,
            coordinator,
            new StoreMenuCloneProfileRegistry(List.of(profile)),
            composers
        );
    }

    private OwnerStoreMenuCloneTransactionCommand command(Long requestId, Long sourceStoreId, Long targetStoreId) {
        return new OwnerStoreMenuCloneTransactionCommand(
            requestId,
            ORGANIZATION_ID,
            sourceStoreId,
            targetStoreId,
            ChinatownMenuCloneProfile.PROFILE_CODE,
            77L
        );
    }

    private OwnerStoreMenuCloneTransactionResult execute(
        StoreMenuCloneTransactionServiceImpl service,
        OwnerStoreMenuCloneTransactionCommand command
    ) {
        return transaction().execute(status -> service.execute(command));
    }

    private SourceFixture sourceFixture() {
        Store source = store("ST_DENIS_SOURCE", "active");
        Store successTarget = store("CHINATOWN_TARGET", "inactive");
        Store failureTarget = store("ROLLBACK_TARGET", "inactive");

        MenuCategory soup = category(source, "SOUP_NOODLE", 1);
        MenuCategory dry = category(source, "DRY_NOODLE", 2);
        MenuCategory side = category(source, "SOURCE_SIDE", 3);
        MenuCategory drink = category(source, "DRINK", 4);
        Station noodle = station(source, "NOODLE", 1);
        Station cold = station(source, "COLD", 2);
        Station bar = station(source, "BAR_SOURCE", 3);

        List<MenuItem> noodles = List.of(
            item(source, soup, noodle, "traditional_beef_noodle", 1),
            item(source, soup, noodle, "braised_beef_tendon_noodle", 2),
            item(source, soup, noodle, "vegetable_noodle", 3),
            item(source, dry, noodle, "dan_dan_noodle", 1),
            item(source, dry, noodle, "zha_jiang_noodle", 2)
        );
        item(source, side, cold, "braised_beef_shank_salad", 1);
        MenuItem cucumber = item(source, side, cold, "cucumber_salad", 2);
        item(source, side, cold, "edamame", 3);
        item(source, side, cold, "shredded_potato", 4);
        item(source, drink, bar, "coke", 1);
        item(source, drink, bar, "diet_coke", 2);
        item(source, drink, bar, "ice_tea", 3);
        item(source, drink, bar, "chinese_herbal_tea", 4);

        ChinatownMenuCloneProfile profile = new ChinatownMenuCloneProfile();
        for (MenuItem noodleItem : noodles) {
            int order = 1;
            for (String code : profile.noodleTypeCodes()) {
                option(noodleItem, "noodle_type", "NOODLE_TYPE", code, order++, "0.00");
            }
        }
        option(noodles.get(0), "addon", "ADD_ON", "tea_egg", 100, "0.50");
        option(noodles.get(0), "addon", "ADD_ON", "extra_meat", 101, "4.25");
        option(cucumber, "remove", "REMOVE", "remove_garlic", 1, "0.00");
        source.menu_revision = 12L;
        source.menu_updated_at = LocalDateTime.now();
        source = storeRepository.saveAndFlush(source);
        return new SourceFixture(source, successTarget, failureTarget);
    }

    private Store store(String code, String status) {
        LocalDateTime now = LocalDateTime.now();
        Store store = new Store();
        store.organization_id = ORGANIZATION_ID;
        store.code = code;
        store.name = code;
        store.status = status;
        store.printing_enabled = false;
        store.printing_mode = "DISABLED";
        store.menu_revision = 1L;
        store.menu_updated_at = now;
        store.created_at = now;
        store.updated_at = now;
        return storeRepository.saveAndFlush(store);
    }

    private MenuCategory category(Store store, String code, int sortOrder) {
        MenuCategory category = new MenuCategory();
        category.store_id = store.id;
        category.code = code;
        category.name_zh = code;
        category.name_en = code;
        category.sort_order = sortOrder;
        category.is_active = true;
        category.created_at = LocalDateTime.now();
        category.updated_at = category.created_at;
        return categoryRepository.saveAndFlush(category);
    }

    private Station station(Store store, String code, int sortOrder) {
        Station station = new Station();
        station.store_id = store.id;
        station.code = code;
        station.name = code;
        station.sort_order = sortOrder;
        station.is_active = true;
        station.created_at = LocalDateTime.now();
        station.updated_at = station.created_at;
        return stationRepository.saveAndFlush(station);
    }

    private MenuItem item(
        Store store,
        MenuCategory category,
        Station station,
        String sku,
        int sortOrder
    ) {
        MenuItem item = new MenuItem();
        item.store_id = store.id;
        item.category_id = category.id;
        item.station_id = station.id;
        item.sku = sku;
        item.name_zh = sku;
        item.name_en = sku;
        item.item_type = "menu_item";
        item.base_price = BigDecimal.TEN;
        item.cost_per_item = BigDecimal.ONE;
        item.is_active = true;
        item.is_sold_out = false;
        item.sort_order = sortOrder;
        item.created_at = LocalDateTime.now();
        item.updated_at = item.created_at;
        return itemRepository.saveAndFlush(item);
    }

    private void option(
        MenuItem item,
        String type,
        String group,
        String code,
        int sortOrder,
        String price
    ) {
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = item.id;
        option.option_type = type;
        option.option_group = group;
        option.option_code = code;
        option.name_zh = code;
        option.name_en = code;
        option.price_delta = new BigDecimal(price);
        option.sort_order = sortOrder;
        option.is_active = true;
        option.created_at = LocalDateTime.now();
        option.updated_at = option.created_at;
        optionRepository.saveAndFlush(option);
    }

    private SourceState sourceState(Long sourceStoreId) {
        List<MenuItem> items = itemRepository.findAllByStoreIdOrderByIdAsc(sourceStoreId);
        return new SourceState(
            storeRepository.findMenuRevisionById(sourceStoreId),
            categoryRepository.countAllByStoreId(sourceStoreId),
            stationRepository.countAllByStoreId(sourceStoreId),
            itemRepository.countAllByStoreId(sourceStoreId),
            optionRepository.findAllByMenuItemIdsOrdered(items.stream().map(item -> item.id).toList()).size()
        );
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record SourceFixture(Store source, Store successTarget, Store failureTarget) {
    }

    private record SourceState(
        Long revision,
        long categoryCount,
        long stationCount,
        long itemCount,
        int optionCount
    ) {
    }
}
