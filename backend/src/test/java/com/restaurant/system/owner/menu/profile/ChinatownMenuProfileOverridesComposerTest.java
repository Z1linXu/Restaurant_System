package com.restaurant.system.owner.menu.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphResult;
import com.restaurant.system.owner.menu.StoreMenuCloneCompositionContext;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceItem;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceOption;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChinatownMenuProfileOverridesComposerTest {

    private static final long TARGET_STORE_ID = 91L;

    private MenuItemOptionRepository repository;
    private ChinatownMenuCloneProfile profile;
    private List<MenuItemOption> saved;
    private AtomicLong generatedId;

    @BeforeEach
    void setUp() {
        repository = mock(MenuItemOptionRepository.class);
        profile = new ChinatownMenuCloneProfile();
        saved = new ArrayList<>();
        generatedId = new AtomicLong(50_000L);
        when(repository.findAllByMenuItemIdsOrdered(anyList())).thenReturn(List.of());
        doAnswer(invocation -> {
            List<MenuItemOption> options = invocation.getArgument(0);
            options.forEach(option -> {
                if (option.id == null) {
                    option.id = generatedId.getAndIncrement();
                }
            });
            saved.addAll(options);
            return options;
        }).when(repository).saveAllAndFlush(anyList());
    }

    @Test
    void createsExactNoodleSizeComboAndTeaEggGraph() {
        ChinatownMenuProfileOverridesComposer composer = composer();

        int count = composer.compose(context(consistentNoodleOptions()));

        assertThat(count).isEqualTo(73);
        assertThat(saved).hasSize(73);
        assertThat(saved).allSatisfy(option -> {
            assertThat(option.id).isNotNull();
            assertThat(option.is_active).isTrue();
            assertThat(option.parent_option_id).isNull();
        });
        assertThat(saved.stream().filter(option -> "NOODLE_TYPE".equals(option.option_group))).hasSize(35);
        assertThat(saved.stream().filter(option -> "SIZE".equals(option.option_group))).hasSize(8);
        assertThat(saved.stream().filter(option -> "COMBO".equals(option.option_group))).hasSize(4);
        assertThat(saved.stream().filter(option -> "COMBO_EGG".equals(option.option_group))).hasSize(4);
        assertThat(saved.stream().filter(option -> "COMBO_SIDE".equals(option.option_group))).hasSize(12);
        // Current catalog/order contracts derive side removals from each referenced
        // standalone side item; PR-E does not recreate legacy child rows.
        assertThat(saved).noneMatch(option -> "COMBO_SIDE_REMOVE".equals(option.option_group));
        assertThat(saved.stream().filter(option -> "tea_egg".equals(option.option_code)
            && "ADD_ON".equals(option.option_group))).hasSize(5);
        assertThat(saved.stream().filter(option -> "extra_meat".equals(option.option_code)
            && "ADD_ON".equals(option.option_group))).hasSize(5);

        Long tendonId = targetIds().get("braised_beef_tendon_noodle");
        assertThat(saved).noneMatch(option -> option.menu_item_id.equals(tendonId)
            && ("SIZE".equals(option.option_group) || "COMBO".equals(option.option_group)));
    }

    @Test
    void normalizesReviewedCopiedAddOnsInsteadOfCreatingDuplicates() {
        Long traditionalId = targetIds().get("traditional_beef_noodle");
        MenuItemOption copied = option(traditionalId, "addon", "tea_egg", "ADD_ON", "旧蛋", "Old Egg", "0.50", 5);
        copied.id = 700L;
        MenuItemOption copiedMeat = option(
            traditionalId, "addon", "extra_meat", "ADD_ON", "旧肉", "Old Meat", "4.25", 6
        );
        copiedMeat.id = 701L;
        when(repository.findAllByMenuItemIdsOrdered(anyList())).thenReturn(List.of(copied, copiedMeat));

        int count = composer().compose(context(consistentNoodleOptions()));

        assertThat(count).isEqualTo(71);
        assertThat(copied.name_zh).isEqualTo("加卤蛋");
        assertThat(copied.name_en).isEqualTo("Extra Tea Egg");
        assertThat(copied.price_delta).isEqualByComparingTo("1.99");
        assertThat(saved).contains(copied);
        assertThat(saved.stream().filter(option -> option.menu_item_id.equals(traditionalId)
            && "tea_egg".equals(option.option_code))).hasSize(1);
        assertThat(copiedMeat.name_zh).isEqualTo("加肉");
        assertThat(copiedMeat.name_en).isEqualTo("Extra Meat");
        assertThat(copiedMeat.price_delta).isEqualByComparingTo("6.99");
        assertThat(saved.stream().filter(option -> option.menu_item_id.equals(traditionalId)
            && "extra_meat".equals(option.option_code))).hasSize(1);
    }

    @Test
    void rejectsMissingOrInconsistentNoodleDefinitionsBeforeWriting() {
        List<SourceOption> missing = consistentNoodleOptions().stream()
            .filter(option -> !"noodle_extra_wide".equals(option.optionCode()))
            .toList();
        assertThatThrownBy(() -> composer().compose(context(missing)))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("missing");
        verify(repository, never()).saveAllAndFlush(anyList());

        SourceOption conflicting = sourceOption(999L, 102L, "noodle_thin", "细", "Different", "0.00");
        List<SourceOption> inconsistent = new ArrayList<>(consistentNoodleOptions());
        inconsistent.add(conflicting);
        assertThatThrownBy(() -> composer().compose(context(inconsistent)))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("ambiguous");
    }

    @Test
    void rejectsExistingGeneratedCodeConflictBeforeWriting() {
        Long traditionalId = targetIds().get("traditional_beef_noodle");
        MenuItemOption conflict = option(
            traditionalId, "addon", "combo", "ADD_ON", "冲突", "Conflict", "0.00", 1
        );
        conflict.id = 701L;
        when(repository.findAllByMenuItemIdsOrdered(anyList())).thenReturn(List.of(conflict));

        assertThatThrownBy(() -> composer().compose(context(consistentNoodleOptions())))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("conflicts");
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void supportIsStrictAndCaseSensitive() {
        ChinatownMenuProfileOverridesComposer composer = composer();
        assertThat(composer.identity()).isEqualTo("chinatown-profile-overrides");
        assertThat(composer.phase()).isEqualTo(com.restaurant.system.owner.menu.StoreMenuCloneGraphComposer.Phase.PROFILE_OVERRIDES);
        assertThat(composer.supports("CHINATOWN_MENU_2026_02_02")).isTrue();
        assertThat(composer.supports("chinatown_menu_2026_02_02")).isFalse();
        assertThat(composer.supports(" CHINATOWN_MENU_2026_02_02")).isFalse();
    }

    private ChinatownMenuProfileOverridesComposer composer() {
        return new ChinatownMenuProfileOverridesComposer(repository);
    }

    private StoreMenuCloneCompositionContext context(List<SourceOption> sourceOptions) {
        List<SourceItem> sourceItems = sourceNoodleItems();
        Map<String, Long> targetIds = targetIds();
        Map<Long, Set<ItemRole>> roles = new LinkedHashMap<>();
        profile.items().forEach(item -> roles.put(targetIds.get(item.targetSku()), item.roles()));
        StoreMenuCloneSnapshot snapshot = new StoreMenuCloneSnapshot(
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            5L,
            11L,
            LocalDateTime.now(),
            List.of(),
            List.of(),
            sourceItems,
            sourceOptions
        );
        StoreMenuCloneBaseGraphResult graph = new StoreMenuCloneBaseGraphResult(
            snapshot,
            Map.of(),
            Map.of(),
            Map.of(),
            targetIds,
            roles
        );
        return new StoreMenuCloneCompositionContext(
            profile,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            graph
        );
    }

    private List<SourceItem> sourceNoodleItems() {
        List<String> skus = List.of(
            "traditional_beef_noodle",
            "braised_beef_tendon_noodle",
            "vegetable_noodle",
            "dan_dan_noodle",
            "zha_jiang_noodle"
        );
        List<SourceItem> items = new ArrayList<>();
        for (int index = 0; index < skus.size(); index++) {
            items.add(new SourceItem(
                101L + index,
                ChinatownMenuCloneProfile.SOURCE_STORE_ID,
                20L,
                30L,
                index == 0 ? " TRADITIONAL_BEEF_NOODLE " : skus.get(index),
                skus.get(index),
                skus.get(index),
                "menu_item",
                BigDecimal.TEN,
                BigDecimal.ONE,
                true,
                false,
                index + 1
            ));
        }
        return items;
    }

    private List<SourceOption> consistentNoodleOptions() {
        List<SourceOption> options = new ArrayList<>();
        List<String> codes = profile.noodleTypeCodes();
        for (int index = 0; index < codes.size(); index++) {
            String code = codes.get(index);
            options.add(sourceOption(1_000L + index, 101L, code, "面型" + index, "Noodle " + index, "0.00"));
        }
        return options;
    }

    private SourceOption sourceOption(
        Long id,
        Long ownerItemId,
        String code,
        String nameZh,
        String nameEn,
        String price
    ) {
        return new SourceOption(
            id,
            ownerItemId,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            "noodle_type",
            code,
            "NOODLE_TYPE",
            null,
            Math.toIntExact(id % 100),
            nameZh,
            nameEn,
            new BigDecimal(price),
            true
        );
    }

    private Map<String, Long> targetIds() {
        Map<String, Long> result = new LinkedHashMap<>();
        AtomicLong next = new AtomicLong(10_000L);
        profile.items().forEach(item -> result.put(item.targetSku(), next.getAndIncrement()));
        return Map.copyOf(result);
    }

    private MenuItemOption option(
        Long itemId,
        String type,
        String code,
        String group,
        String nameZh,
        String nameEn,
        String price,
        int sortOrder
    ) {
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = itemId;
        option.option_type = type;
        option.option_code = code;
        option.option_group = group;
        option.name_zh = nameZh;
        option.name_en = nameEn;
        option.price_delta = new BigDecimal(price);
        option.sort_order = sortOrder;
        option.is_active = true;
        return option;
    }
}
