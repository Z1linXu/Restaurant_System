package com.restaurant.system.owner.menu.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphResult;
import com.restaurant.system.owner.menu.StoreMenuCloneCompositionContext;
import com.restaurant.system.owner.menu.StoreMenuClonePlannedOption;
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
import org.junit.jupiter.api.Test;

class ChinatownMenuProfileOverridesComposerTest {

    private static final long TARGET_STORE_ID = 91L;

    private ChinatownMenuCloneProfile profile;

    ChinatownMenuProfileOverridesComposerTest() {
        profile = new ChinatownMenuCloneProfile();
    }

    @Test
    void createsExactNoodleSizeComboAndTeaEggGraph() {
        ChinatownMenuProfileOverridesComposer composer = composer();

        StoreMenuCloneCompositionContext context = context(consistentNoodleOptions());
        int count = composer.compose(context);

        assertThat(count).isEqualTo(73);
        assertThat(context.options()).hasSize(73);
        assertThat(context.options()).allSatisfy(option -> {
            assertThat(option.active()).isTrue();
            assertThat(option.parentOptionCode()).isNull();
        });
        assertThat(context.options().stream().filter(option -> "NOODLE_TYPE".equals(option.optionGroup()))).hasSize(35);
        assertThat(context.options().stream().filter(option -> "SIZE".equals(option.optionGroup()))).hasSize(8);
        assertThat(context.options().stream().filter(option -> "COMBO".equals(option.optionGroup()))).hasSize(4);
        assertThat(context.options().stream().filter(option -> "COMBO_EGG".equals(option.optionGroup()))).hasSize(4);
        assertThat(context.options().stream().filter(option -> "COMBO_SIDE".equals(option.optionGroup()))).hasSize(12);
        // Current catalog/order contracts derive side removals from each referenced
        // standalone side item; PR-E does not recreate legacy child rows.
        assertThat(context.options()).noneMatch(option -> "COMBO_SIDE_REMOVE".equals(option.optionGroup()));
        assertThat(context.options().stream().filter(option -> "tea_egg".equals(option.optionCode())
            && "ADD_ON".equals(option.optionGroup()))).hasSize(5);
        assertThat(context.options().stream().filter(option -> "extra_meat".equals(option.optionCode())
            && "ADD_ON".equals(option.optionGroup()))).hasSize(5);

        Long tendonId = targetIds().get("braised_beef_tendon_noodle");
        assertThat(context.options()).noneMatch(option -> option.targetItemId().equals(tendonId)
            && ("SIZE".equals(option.optionGroup()) || "COMBO".equals(option.optionGroup())));
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
        StoreMenuCloneCompositionContext context = context(consistentNoodleOptions(), List.of(copied, copiedMeat));
        int count = composer().compose(context);

        assertThat(count).isEqualTo(71);
        StoreMenuClonePlannedOption teaEgg = context.findOption(traditionalId, "tea_egg").orElseThrow();
        assertThat(teaEgg.nameZh()).isEqualTo("加卤蛋");
        assertThat(teaEgg.nameEn()).isEqualTo("Extra Tea Egg");
        assertThat(teaEgg.priceDelta()).isEqualByComparingTo("1.99");
        assertThat(context.options().stream().filter(option -> option.targetItemId().equals(traditionalId)
            && "tea_egg".equals(option.optionCode()))).hasSize(1);
        StoreMenuClonePlannedOption extraMeat = context.findOption(traditionalId, "extra_meat").orElseThrow();
        assertThat(extraMeat.nameZh()).isEqualTo("加肉");
        assertThat(extraMeat.nameEn()).isEqualTo("Extra Meat");
        assertThat(extraMeat.priceDelta()).isEqualByComparingTo("6.99");
        assertThat(context.options().stream().filter(option -> option.targetItemId().equals(traditionalId)
            && "extra_meat".equals(option.optionCode()))).hasSize(1);
    }

    @Test
    void rejectsMissingOrInconsistentNoodleDefinitionsBeforeWriting() {
        List<SourceOption> missing = consistentNoodleOptions().stream()
            .filter(option -> !"noodle_extra_wide".equals(option.optionCode()))
            .toList();
        assertThatThrownBy(() -> composer().compose(context(missing)))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("missing");

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
        StoreMenuCloneCompositionContext context = context(consistentNoodleOptions(), List.of(conflict));
        assertThatThrownBy(() -> composer().compose(context))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("conflicts");
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
        return new ChinatownMenuProfileOverridesComposer();
    }

    private StoreMenuCloneCompositionContext context(List<SourceOption> sourceOptions) {
        return context(sourceOptions, List.of());
    }

    private StoreMenuCloneCompositionContext context(
        List<SourceOption> sourceOptions,
        List<MenuItemOption> existingOptions
    ) {
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
        StoreMenuCloneCompositionContext context = new StoreMenuCloneCompositionContext(
            profile,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            graph
        );
        existingOptions.forEach(option -> context.addOption(new StoreMenuClonePlannedOption(
            option.menu_item_id, option.id, option.option_type, option.option_code, option.option_group, null,
            option.sort_order, option.name_zh, option.name_en, option.price_delta, option.is_active
        )));
        return context;
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
