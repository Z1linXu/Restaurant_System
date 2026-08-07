package com.restaurant.system.owner.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoreMenuCloneSourceOptionsComposerTest {

    private static final long SOURCE_STORE_ID = 41L;
    private static final long TARGET_STORE_ID = 91L;
    private static final String PROFILE_CODE = "TEST_SOURCE_OPTIONS_V1";
    private static final SourceOptionRule ADD_ON = copyRule("addon", "ADD_ON");
    private static final SourceOptionRule REMOVE = copyRule("remove", "REMOVE");
    private static final SourceOptionRule SIZE_OVERRIDE = overrideRule("size", "SIZE");

    @Test
    void copiesSelectedActiveValuesAndMapsParentsInTwoPasses() {
        SourceItem source = sourceItem(101L, "source_noodle");
        SourceOption parent = option(1_001L, source.id(), "addon", "extra_meat", "ADD_ON", null, 5, true);
        SourceOption child = option(1_002L, source.id(), "remove", "no_sauce", "REMOVE", parent.id(), 2, true);
        SourceOption inactive = option(1_003L, source.id(), "addon", "inactive", "ADD_ON", null, 1, false);
        SourceOption profileOverride = option(1_004L, source.id(), "size", "large", "SIZE", null, 3, true);
        TestProfile profile = profile(List.of(application(
            "source_noodle",
            "target_noodle",
            ADD_ON,
            REMOVE,
            SIZE_OVERRIDE
        )));

        StoreMenuCloneCompositionContext context = context(
            profile,
            List.of(source),
            List.of(parent, child, inactive, profileOverride),
            Map.of("target_noodle", 501L)
        );
        int count = composer(profile).compose(context);

        assertThat(count).isEqualTo(2);
        assertThat(context.options())
            .extracting(StoreMenuClonePlannedOption::optionCode)
            .containsExactly("no_sauce", "extra_meat");
        StoreMenuClonePlannedOption plannedChild = context.options().get(0);
        assertThat(plannedChild.parentOptionCode()).isEqualTo("extra_meat");
        assertThat(plannedChild.targetItemId()).isEqualTo(501L);
        assertThat(plannedChild.optionType()).isEqualTo(child.optionType());
        assertThat(plannedChild.optionGroup()).isEqualTo(child.optionGroup());
        assertThat(plannedChild.nameZh()).isEqualTo(child.nameZh());
        assertThat(plannedChild.nameEn()).isEqualTo(child.nameEn());
        assertThat(plannedChild.priceDelta()).isEqualByComparingTo(child.priceDelta());
        assertThat(plannedChild.active()).isTrue();
    }

    @Test
    void appliesOneSourceGraphToMultipleReviewedTargetItemsDeterministically() {
        SourceItem source = sourceItem(101L, "source_noodle");
        SourceOption parent = option(2_001L, source.id(), "addon", "parent", "ADD_ON", null, 1, true);
        SourceOption child = option(2_002L, source.id(), "remove", "child", "REMOVE", parent.id(), 2, true);
        TestProfile profile = profile(List.of(
            application("source_noodle", "target_b", ADD_ON, REMOVE),
            application("source_noodle", "target_a", ADD_ON, REMOVE)
        ));

        StoreMenuCloneCompositionContext context = context(
            profile,
            List.of(source, source),
            List.of(child, parent),
            Map.of("target_b", 502L, "target_a", 501L)
        );
        int count = composer(profile).compose(context);

        assertThat(count).isEqualTo(4);
        assertThat(context.options())
            .extracting(StoreMenuClonePlannedOption::targetItemId, StoreMenuClonePlannedOption::optionCode)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(501L, "parent"),
                org.assertj.core.groups.Tuple.tuple(501L, "child"),
                org.assertj.core.groups.Tuple.tuple(502L, "parent"),
                org.assertj.core.groups.Tuple.tuple(502L, "child")
            );
        assertThat(context.options().get(1).parentOptionCode()).isEqualTo("parent");
        assertThat(context.options().get(3).parentOptionCode()).isEqualTo("parent");
    }

    @Test
    void rejectsInconsistentRepeatedSourceItemSnapshots() {
        SourceItem source = sourceItem(101L, "source_noodle");
        SourceItem conflicting = new SourceItem(
            source.id(),
            source.storeId(),
            source.categoryId(),
            source.stationId(),
            "different_sku",
            source.nameZh(),
            source.nameEn(),
            source.itemType(),
            source.basePrice(),
            source.costPerItem(),
            source.active(),
            source.soldOut(),
            source.sortOrder()
        );
        TestProfile profile = profile(List.of(application(
            "source_noodle",
            "target_noodle",
            ADD_ON
        )));

        assertThatThrownBy(() -> composer(profile).compose(context(
            profile,
            List.of(source, conflicting),
            List.of(option(2_101L, source.id(), "addon", "extra", "ADD_ON", null, 1, true)),
            Map.of("target_noodle", 501L)
        )))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("Repeated source item snapshots are inconsistent");
    }

    @Test
    void filtersInactiveAndProfileOverrideOptionsWithoutWritingThem() {
        SourceItem source = sourceItem(101L, "source_noodle");
        SourceOption selected = option(3_001L, source.id(), "addon", "selected", "ADD_ON", null, 1, true);
        SourceOption inactive = option(3_002L, source.id(), "addon", "inactive", "ADD_ON", null, 2, false);
        SourceOption overridden = option(3_003L, source.id(), "size", "large", "SIZE", null, 3, true);
        TestProfile profile = profile(List.of(application(
            "source_noodle",
            "target_noodle",
            ADD_ON,
            SIZE_OVERRIDE
        )));

        StoreMenuCloneCompositionContext context = context(
            profile,
            List.of(source),
            List.of(overridden, inactive, selected),
            Map.of("target_noodle", 501L)
        );
        int count = composer(profile).compose(context);

        assertThat(count).isOne();
        assertThat(context.options()).extracting(StoreMenuClonePlannedOption::optionCode).containsExactly("selected");
    }

    @Test
    void rejectsMissingApplicationForReusedItemWithActiveOptions() {
        SourceItem source = sourceItem(101L, "source_noodle");
        SourceOption option = option(3_101L, source.id(), "addon", "extra", "ADD_ON", null, 1, true);
        TestProfile profile = profileWithItems(
            List.of(itemSelection("source_noodle", "target_a")),
            List.of()
        );

        assertRejectedBeforeWrite(
            profile,
            List.of(source),
            List.of(option),
            "requires a reviewed application"
        );
    }

    @Test
    void conditionallyAppliesOptionsForCloneIfActiveOrCreateItems() {
        SourceOptionApplication application = application("optional_side", "target_a", ADD_ON);
        TestProfile profile = profileWithItems(
            List.of(itemSelection("optional_side", "target_a", SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE)),
            List.of(application)
        );

        int absentCount = composer(profile).compose(context(
            profile,
            List.of(),
            List.of(),
            Map.of("target_a", 501L)
        ));

        SourceItem source = sourceItem(101L, "optional_side");
        SourceOption option = option(3_151L, source.id(), "addon", "extra", "ADD_ON", null, 1, true);
        StoreMenuCloneCompositionContext presentContext = context(
            profile,
            List.of(source),
            List.of(option),
            Map.of("target_a", 501L)
        );
        int presentCount = composer(profile).compose(presentContext);

        assertThat(absentCount).isZero();
        assertThat(presentCount).isOne();
        assertThat(presentContext.options()).extracting(StoreMenuClonePlannedOption::optionCode).containsExactly("extra");
    }

    @Test
    void validatesConditionalApplicationContractWhenOptionalSourceItemIsAbsent() {
        SourceOptionApplication application = application("optional_side", "target_a", ADD_ON);
        TestProfile profile = profileWithItems(
            List.of(itemSelection("optional_side", "target_a", SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE)),
            List.of(application, application)
        );

        assertThatThrownBy(() -> composer(profile).compose(context(
            profile,
            List.of(),
            List.of(),
            Map.of("target_a", 501L)
        )))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("unique source-target pairs");
    }

    @Test
    void rejectsBlankDuplicateCodesAndAmbiguousActiveStateBeforeWriting() {
        SourceItem source = sourceItem(101L, "source_noodle");
        TestProfile profile = profile(List.of(application("source_noodle", "target_a", ADD_ON)));
        SourceOption duplicateA = option(3_201L, source.id(), "addon", "DUPLICATE", "ADD_ON", null, 1, true);
        SourceOption duplicateB = option(3_202L, source.id(), "addon", "duplicate", "ADD_ON", null, 2, true);
        assertRejectedBeforeWrite(profile, List.of(source), List.of(duplicateA, duplicateB), "codes");

        SourceOption blank = option(3_203L, source.id(), "addon", " ", "ADD_ON", null, 1, true);
        assertRejectedBeforeWrite(profile, List.of(source), List.of(blank), "codes");

        SourceOption ambiguous = new SourceOption(
            3_204L,
            source.id(),
            SOURCE_STORE_ID,
            "addon",
            "ambiguous",
            "ADD_ON",
            null,
            1,
            "ambiguous zh",
            "ambiguous en",
            BigDecimal.ZERO,
            null
        );
        assertRejectedBeforeWrite(profile, List.of(source), List.of(ambiguous), "active state");
    }

    @Test
    void rejectsMissingCrossItemAndCrossStoreParentsBeforeWriting() {
        SourceItem first = sourceItem(101L, "source_a");
        SourceItem second = sourceItem(102L, "source_b");
        TestProfile profile = profile(List.of(application("source_a", "target_a", ADD_ON, REMOVE)));

        assertRejectedBeforeWrite(profile, List.of(first), List.of(
            option(4_001L, first.id(), "remove", "child", "REMOVE", 499_999L, 2, true)
        ), "Source option parent is missing");

        SourceOption otherItemParent = option(4_101L, second.id(), "addon", "parent", "ADD_ON", null, 1, true);
        SourceOption crossItemChild = option(
            4_102L, first.id(), "remove", "child", "REMOVE", otherItemParent.id(), 2, true
        );
        assertRejectedBeforeWrite(
            profile,
            List.of(first, second),
            List.of(otherItemParent, crossItemChild),
            "another menu item"
        );

        SourceOption foreign = new SourceOption(
            4_201L,
            first.id(),
            999L,
            "addon",
            "foreign",
            "ADD_ON",
            null,
            1,
            "foreign",
            "Foreign",
            BigDecimal.ZERO,
            true
        );
        assertRejectedBeforeWrite(profile, List.of(first), List.of(foreign), "Store ownership");
    }

    @Test
    void rejectsInactiveOrUnselectedParentAndParentCyclesBeforeWriting() {
        SourceItem source = sourceItem(101L, "source_a");
        TestProfile both = profile(List.of(application("source_a", "target_a", ADD_ON, REMOVE)));
        SourceOption inactiveParent = option(5_001L, source.id(), "addon", "parent", "ADD_ON", null, 1, false);
        SourceOption child = option(5_002L, source.id(), "remove", "child", "REMOVE", inactiveParent.id(), 2, true);
        assertRejectedBeforeWrite(both, List.of(source), List.of(inactiveParent, child), "parent is inactive");

        TestProfile removeOnly = profile(List.of(application(
            "source_a",
            "target_a",
            overrideRule("addon", "ADD_ON"),
            REMOVE
        )));
        SourceOption activeParent = option(5_101L, source.id(), "addon", "parent", "ADD_ON", null, 1, true);
        SourceOption selectedChild = option(5_102L, source.id(), "remove", "child", "REMOVE", activeParent.id(), 2, true);
        assertRejectedBeforeWrite(
            removeOnly,
            List.of(source),
            List.of(activeParent, selectedChild),
            "outside the reviewed selection"
        );

        SourceOption first = option(5_201L, source.id(), "addon", "first", "ADD_ON", 5_202L, 1, true);
        SourceOption second = option(5_202L, source.id(), "remove", "second", "REMOVE", first.id(), 2, true);
        assertRejectedBeforeWrite(both, List.of(source), List.of(first, second), "contains a cycle");

        SourceOption self = option(5_301L, source.id(), "addon", "self", "ADD_ON", 5_301L, 1, true);
        assertRejectedBeforeWrite(both, List.of(source), List.of(self), "reference itself");
    }

    @Test
    void rejectsDuplicateAndMalformedProfileInputsBeforeWriting() {
        SourceItem source = sourceItem(101L, "source_a");
        SourceOption duplicateA = option(6_001L, source.id(), "addon", "first", "ADD_ON", null, 1, true);
        SourceOption duplicateB = option(6_001L, source.id(), "addon", "second", "ADD_ON", null, 2, true);
        TestProfile profile = profile(List.of(application("source_a", "target_a", ADD_ON)));
        assertRejectedBeforeWrite(profile, List.of(source), List.of(duplicateA, duplicateB), "duplicated");

        TestProfile missingDisposition = profile(List.of(application(
            "source_a",
            "target_a",
            new SourceOptionRule("addon", "ADD_ON", null)
        )));
        assertRejectedBeforeWrite(missingDisposition, List.of(source), List.of(duplicateA), "disposition");

        TestProfile unclassifiedActiveOption = profile(List.of(application(
            "source_a",
            "target_a",
            REMOVE
        )));
        assertRejectedBeforeWrite(
            unclassifiedActiveOption,
            List.of(source),
            List.of(duplicateA),
            "not classified"
        );

        TestProfile duplicateApplication = profile(List.of(
            application("source_a", "target_a", ADD_ON),
            application("source_a", "target_a", REMOVE)
        ));
        assertRejectedBeforeWrite(duplicateApplication, List.of(source), List.of(duplicateA), "unique source-target");
    }

    @Test
    void exposesStableComposerSlotAndUsesExactRegistryProfileCode() {
        TestProfile profile = profile(List.of());
        StoreMenuCloneSourceOptionsComposer composer = composer(profile);

        assertThat(composer.identity()).isEqualTo("source-options");
        assertThat(composer.phase()).isEqualTo(StoreMenuCloneGraphComposer.Phase.SOURCE_OPTIONS);
        assertThat(composer.order()).isEqualTo(100);
        assertThat(composer.supports(PROFILE_CODE)).isTrue();
        assertThat(composer.supports(PROFILE_CODE.toLowerCase())).isFalse();
        assertThat(composer.supports(" " + PROFILE_CODE)).isFalse();
    }

    private void assertRejectedBeforeWrite(
        TestProfile profile,
        List<SourceItem> sourceItems,
        List<SourceOption> sourceOptions,
        String messagePart
    ) {
        assertThatThrownBy(() -> composer(profile).compose(context(
            profile,
            sourceItems,
            sourceOptions,
            Map.of("target_a", 501L)
        )))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining(messagePart);
    }

    private StoreMenuCloneSourceOptionsComposer composer(TestProfile profile) {
        return new StoreMenuCloneSourceOptionsComposer(new StoreMenuCloneProfileRegistry(List.of(profile)));
    }

    private StoreMenuCloneCompositionContext context(
        TestProfile profile,
        List<SourceItem> sourceItems,
        List<SourceOption> sourceOptions,
        Map<String, Long> targetIdsBySku
    ) {
        StoreMenuCloneSnapshot snapshot = new StoreMenuCloneSnapshot(
            SOURCE_STORE_ID,
            7L,
            3L,
            LocalDateTime.now(),
            List.of(),
            List.of(),
            sourceItems,
            sourceOptions
        );
        Map<Long, Set<ItemRole>> roles = targetIdsBySku.values().stream()
            .collect(java.util.stream.Collectors.toMap(id -> id, id -> Set.of(ItemRole.NOODLE)));
        return new StoreMenuCloneCompositionContext(
            profile,
            SOURCE_STORE_ID,
            TARGET_STORE_ID,
            new StoreMenuCloneBaseGraphResult(
                snapshot,
                Map.of(),
                Map.of(),
                Map.of(),
                targetIdsBySku,
                roles
            )
        );
    }

    private TestProfile profile(List<SourceOptionApplication> applications) {
        Map<String, ItemSelection> itemsByTargetSku = new java.util.LinkedHashMap<>();
        for (SourceOptionApplication application : applications) {
            itemsByTargetSku.putIfAbsent(
                application.targetItemSku(),
                itemSelection(application.sourceItemSku(), application.targetItemSku())
            );
        }
        return new TestProfile(PROFILE_CODE, SOURCE_STORE_ID, List.copyOf(itemsByTargetSku.values()), applications);
    }

    private TestProfile profileWithItems(
        List<ItemSelection> items,
        List<SourceOptionApplication> applications
    ) {
        return new TestProfile(PROFILE_CODE, SOURCE_STORE_ID, items, applications);
    }

    private ItemSelection itemSelection(String sourceSku, String targetSku) {
        return itemSelection(sourceSku, targetSku, SourcePolicy.REQUIRED_SOURCE_CODE);
    }

    private ItemSelection itemSelection(String sourceSku, String targetSku, SourcePolicy sourcePolicy) {
        return new ItemSelection(
            sourceSku,
            sourcePolicy,
            targetSku,
            "TEST_CATEGORY",
            "TEST_STATION",
            null,
            targetSku + " zh",
            targetSku + " en",
            BigDecimal.TEN,
            true,
            false,
            1,
            Set.of(ItemRole.NOODLE)
        );
    }

    private SourceOptionApplication application(
        String sourceSku,
        String targetSku,
        SourceOptionRule... rules
    ) {
        return new SourceOptionApplication(sourceSku, targetSku, List.of(rules));
    }

    private static SourceOptionRule copyRule(String optionType, String optionGroup) {
        return new SourceOptionRule(optionType, optionGroup, SourceOptionDisposition.COPY);
    }

    private static SourceOptionRule overrideRule(String optionType, String optionGroup) {
        return new SourceOptionRule(optionType, optionGroup, SourceOptionDisposition.PROFILE_OVERRIDE);
    }

    private SourceItem sourceItem(Long id, String sku) {
        return new SourceItem(
            id,
            SOURCE_STORE_ID,
            11L,
            21L,
            sku,
            sku + " zh",
            sku + " en",
            "menu_item",
            BigDecimal.TEN,
            BigDecimal.ONE,
            true,
            false,
            1
        );
    }

    private SourceOption option(
        Long id,
        Long ownerItemId,
        String type,
        String code,
        String group,
        Long parentId,
        Integer sortOrder,
        boolean active
    ) {
        return new SourceOption(
            id,
            ownerItemId,
            SOURCE_STORE_ID,
            type,
            code,
            group,
            parentId,
            sortOrder,
            code + " zh",
            code + " en",
            new BigDecimal("1.25"),
            active
        );
    }

    private record TestProfile(
        String profileCode,
        Long sourceStoreId,
        List<ItemSelection> items,
        List<SourceOptionApplication> sourceOptionApplications
    ) implements StoreMenuCloneSourceOptionsProfile {

        private TestProfile {
            items = List.copyOf(items);
            sourceOptionApplications = List.copyOf(sourceOptionApplications);
        }

        @Override
        public String profileFingerprint() {
            return "TEST_SOURCE_OPTIONS_FINGERPRINT_V1";
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
            return items;
        }
    }
}
