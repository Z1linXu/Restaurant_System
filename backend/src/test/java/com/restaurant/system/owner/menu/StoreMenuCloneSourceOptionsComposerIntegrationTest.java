package com.restaurant.system.owner.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceItem;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceOption;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionApplication;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionDisposition;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionRule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
@ContextConfiguration(classes = StoreMenuCloneSourceOptionsComposerIntegrationTest.JpaSliceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoreMenuCloneSourceOptionsComposerIntegrationTest {

    private static final long SOURCE_STORE_ID = 41L;
    private static final long TARGET_STORE_ID = 91L;

    @Autowired private MenuItemRepository itemRepository;
    @Autowired private MenuItemOptionRepository optionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNew;

    @BeforeEach
    void cleanDatabase() {
        requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        requiresNew.executeWithoutResult(status -> {
            optionRepository.deleteAll();
            itemRepository.deleteAll();
        });
    }

    @Test
    void createsAReadOnlySameItemParentPlan() {
        Long targetItemId = createTargetItem("target_noodle");
        TestProfile profile = profile();
        StoreMenuCloneCompositionContext context = context(profile, targetItemId);

        Integer count = requiresNew.execute(status -> composer(profile).compose(context));

        assertThat(count).isEqualTo(2);
        assertThat(context.options()).hasSize(2);
        StoreMenuClonePlannedOption parent = context.options().stream()
            .filter(option -> "parent".equals(option.optionCode()))
            .findFirst()
            .orElseThrow();
        StoreMenuClonePlannedOption child = context.options().stream()
            .filter(option -> "child".equals(option.optionCode()))
            .findFirst()
            .orElseThrow();
        assertThat(parent.sourceOptionId()).isEqualTo(7_001L);
        assertThat(child.sourceOptionId()).isEqualTo(7_002L);
        assertThat(child.parentOptionCode()).isEqualTo(parent.optionCode());
        assertThat(child.targetItemId()).isEqualTo(parent.targetItemId()).isEqualTo(targetItemId);
        assertThat(optionRepository.count()).isZero();
    }

    @Test
    void outerTransactionFailureRollsBackBothOptionPasses() {
        Long targetItemId = createTargetItem("target_noodle");
        TestProfile profile = profile();
        StoreMenuCloneCompositionContext context = context(profile, targetItemId);

        assertThatThrownBy(() -> requiresNew.executeWithoutResult(status -> {
            assertThat(composer(profile).compose(context)).isEqualTo(2);
            throw new IllegalStateException("synthetic later composer failure");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("synthetic later composer failure");

        Long remaining = requiresNew.execute(status -> optionRepository.count());
        assertThat(remaining).isZero();
    }

    private StoreMenuCloneSourceOptionsComposer composer(TestProfile profile) {
        return new StoreMenuCloneSourceOptionsComposer(new StoreMenuCloneProfileRegistry(List.of(profile)));
    }

    private Long createTargetItem(String sku) {
        return requiresNew.execute(status -> {
            MenuItem item = new MenuItem();
            item.store_id = TARGET_STORE_ID;
            item.category_id = 11L;
            item.station_id = 21L;
            item.sku = sku;
            item.name_zh = sku;
            item.name_en = sku;
            item.item_type = "menu_item";
            item.base_price = BigDecimal.TEN;
            item.cost_per_item = BigDecimal.ONE;
            item.is_active = true;
            item.is_sold_out = false;
            item.sort_order = 1;
            item.created_at = LocalDateTime.now();
            item.updated_at = item.created_at;
            return itemRepository.saveAndFlush(item).id;
        });
    }

    private StoreMenuCloneCompositionContext context(TestProfile profile, Long targetItemId) {
        SourceItem sourceItem = new SourceItem(
            101L,
            SOURCE_STORE_ID,
            31L,
            41L,
            "source_noodle",
            "source zh",
            "source en",
            "menu_item",
            BigDecimal.TEN,
            BigDecimal.ONE,
            true,
            false,
            1
        );
        SourceOption parent = sourceOption(7_001L, "addon", "parent", "ADD_ON", null, 1);
        SourceOption child = sourceOption(7_002L, "remove", "child", "REMOVE", parent.id(), 2);
        StoreMenuCloneSnapshot snapshot = new StoreMenuCloneSnapshot(
            SOURCE_STORE_ID,
            5L,
            9L,
            LocalDateTime.now(),
            List.of(),
            List.of(),
            List.of(sourceItem),
            List.of(child, parent)
        );
        StoreMenuCloneBaseGraphResult baseGraph = new StoreMenuCloneBaseGraphResult(
            snapshot,
            Map.of(),
            Map.of(),
            Map.of(101L, targetItemId),
            Map.of("target_noodle", targetItemId),
            Map.of(targetItemId, Set.of(ItemRole.NOODLE))
        );
        return new StoreMenuCloneCompositionContext(profile, SOURCE_STORE_ID, TARGET_STORE_ID, baseGraph);
    }

    private SourceOption sourceOption(
        Long id,
        String type,
        String code,
        String group,
        Long parentId,
        Integer sortOrder
    ) {
        return new SourceOption(
            id,
            101L,
            SOURCE_STORE_ID,
            type,
            code,
            group,
            parentId,
            sortOrder,
            code + " zh",
            code + " en",
            new BigDecimal("1.25"),
            true
        );
    }

    private TestProfile profile() {
        return new TestProfile();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackageClasses = {MenuItem.class, MenuItemOption.class})
    @EnableJpaRepositories(basePackageClasses = {MenuItemRepository.class, MenuItemOptionRepository.class})
    static class JpaSliceConfiguration {
    }

    private static final class TestProfile implements StoreMenuCloneSourceOptionsProfile {

        @Override
        public String profileCode() {
            return "TEST_SOURCE_OPTIONS_INTEGRATION_V1";
        }

        @Override
        public Long sourceStoreId() {
            return SOURCE_STORE_ID;
        }

        @Override
        public String profileFingerprint() {
            return "TEST_SOURCE_OPTIONS_INTEGRATION_FINGERPRINT_V1";
        }

        @Override
        public List<CategorySelection> categories() {
            return List.of();
        }

        @Override
        public List<StationSelection> stations() {
            return List.of();
        }

        @Override
        public List<ItemSelection> items() {
            return List.of(new ItemSelection(
                "source_noodle",
                SourcePolicy.REQUIRED_SOURCE_CODE,
                "target_noodle",
                "TEST_CATEGORY",
                "TEST_STATION",
                null,
                "target zh",
                "target en",
                BigDecimal.TEN,
                true,
                false,
                1,
                Set.of(ItemRole.NOODLE)
            ));
        }

        @Override
        public List<SourceOptionApplication> sourceOptionApplications() {
            return List.of(new SourceOptionApplication(
                "source_noodle",
                "target_noodle",
                List.of(
                    new SourceOptionRule("addon", "ADD_ON", SourceOptionDisposition.COPY),
                    new SourceOptionRule("remove", "REMOVE", SourceOptionDisposition.COPY)
                )
            ));
        }
    }
}
