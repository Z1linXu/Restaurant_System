package com.restaurant.system.owner.menu.profile;

import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile.ComboRule;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile.ComboSideRule;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile.GeneratedOptionRule;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile.SizeRule;
import com.restaurant.system.owner.menu.StoreMenuCloneCompositionContext;
import com.restaurant.system.owner.menu.StoreMenuCloneGraphComposer;
import com.restaurant.system.owner.menu.StoreMenuClonePlannedOption;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceItem;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceOption;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Creates the reviewed Chinatown-only option overrides after generic source options are copied. */
@Component
public final class ChinatownMenuProfileOverridesComposer implements StoreMenuCloneGraphComposer {

    public static final String IDENTITY = "chinatown-profile-overrides";
    public static final int ORDER = 100;

    @Override
    public String identity() {
        return IDENTITY;
    }

    @Override
    public Phase phase() {
        return Phase.PROFILE_OVERRIDES;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean supports(String profileCode) {
        return ChinatownMenuCloneProfile.PROFILE_CODE.equals(profileCode);
    }

    @Override
    public int compose(StoreMenuCloneCompositionContext context) {
        ChinatownMenuCloneProfile profile = requireProfile(context);
        Map<String, Long> targetIds = requireTargetItems(profile, context.baseGraph().targetItemIdByTargetSku());
        List<Long> targetItemIds = targetIds.values().stream().sorted().toList();
        List<StoreMenuClonePlannedOption> existing = context.options();
        ExistingOptions existingOptions = indexExisting(existing, targetItemIds);
        List<PlannedOption> plan = new ArrayList<>();
        List<StoreMenuClonePlannedOption> updates = new ArrayList<>();

        Map<String, NoodleDefinition> noodleDefinitions = resolveNoodleDefinitions(profile, context);
        for (String targetSku : noodleTargetSkus(profile)) {
            Long targetItemId = targetIds.get(targetSku);
            for (String code : profile.noodleTypeCodes()) {
                NoodleDefinition definition = noodleDefinitions.get(code);
                add(plan, existingOptions, targetItemId, new GeneratedOptionRule(
                    definition.optionType(),
                    definition.optionGroup(),
                    definition.optionCode(),
                    definition.nameZh(),
                    definition.nameEn(),
                    definition.priceDelta(),
                    profile.noodleTypeCodes().indexOf(code) + 1
                ));
            }
            for (SizeRule size : profile.sizeRulesFor(targetSku)) {
                add(plan, existingOptions, targetItemId, profile.sizeOption(size));
            }
            ensureReviewedAddOn(profile.teaEggAddOn(), existingOptions, plan, updates, targetItemId);
            ensureReviewedAddOn(profile.extraMeatAddOn(), existingOptions, plan, updates, targetItemId);
        }

        for (ComboRule combo : profile.combos()) {
            Long targetItemId = requireTargetId(targetIds, combo.mainItemSku());
            add(plan, existingOptions, targetItemId, profile.comboOption());
            add(plan, existingOptions, targetItemId, profile.comboTeaEggOption());
            for (ComboSideRule side : combo.sides()) {
                add(plan, existingOptions, targetItemId, profile.comboSideOption(side));
            }
        }

        validateCompleteProfileGraph(profile, targetIds, existingOptions, plan, updates);
        updates.forEach(context::replaceOption);
        plan.stream().map(PlannedOption::toOptionPlan).forEach(context::addOption);
        return plan.size();
    }

    private ChinatownMenuCloneProfile requireProfile(StoreMenuCloneCompositionContext context) {
        if (!(context.profile() instanceof ChinatownMenuCloneProfile profile)
            || !Objects.equals(context.sourceStoreId(), ChinatownMenuCloneProfile.SOURCE_STORE_ID)
            || Objects.equals(context.sourceStoreId(), context.targetStoreId())
            || !ChinatownMenuCloneProfile.PROFILE_CODE.equals(profile.profileCode())) {
            throw invalidProfile("Chinatown override context is invalid");
        }
        return profile;
    }

    private Map<String, Long> requireTargetItems(ChinatownMenuCloneProfile profile, Map<String, Long> raw) {
        if (raw == null || raw.size() != profile.items().size()) {
            throw invalidTarget("Chinatown target item mapping is incomplete");
        }
        Map<String, Long> resolved = new LinkedHashMap<>();
        Set<Long> ids = new LinkedHashSet<>();
        for (var item : profile.items()) {
            Long id = raw.get(item.targetSku());
            if (id == null || !ids.add(id)) {
                throw invalidTarget("Chinatown target item mapping is incomplete");
            }
            resolved.put(item.targetSku(), id);
        }
        if (!resolved.keySet().equals(raw.keySet())) {
            throw invalidTarget("Chinatown target item mapping contains an unreviewed SKU");
        }
        return Map.copyOf(resolved);
    }

    private ExistingOptions indexExisting(List<StoreMenuClonePlannedOption> options, List<Long> targetItemIds) {
        Set<Long> allowed = Set.copyOf(targetItemIds);
        Map<OptionKey, StoreMenuClonePlannedOption> byCode = new LinkedHashMap<>();
        for (StoreMenuClonePlannedOption option : options) {
            if (option == null || !allowed.contains(option.targetItemId()) || !exact(option.optionCode())) {
                throw invalidTarget("Existing target option evidence is incomplete");
            }
            OptionKey key = new OptionKey(option.targetItemId(), normalizeCode(option.optionCode()));
            if (byCode.putIfAbsent(key, option) != null) {
                throw invalidTarget("Existing target option codes are duplicated");
            }
        }
        return new ExistingOptions(byCode);
    }

    private Map<String, NoodleDefinition> resolveNoodleDefinitions(
        ChinatownMenuCloneProfile profile,
        StoreMenuCloneCompositionContext context
    ) {
        Map<Long, SourceItem> sourceItems = context.baseGraph().sourceSnapshot().items().stream()
            .filter(item -> item != null && item.id() != null)
            .collect(Collectors.toMap(SourceItem::id, Function.identity(), (first, duplicate) -> {
                throw sourceInvalid("Source noodle item identities are duplicated");
            }, LinkedHashMap::new));
        Set<String> sourceNoodleSkus = profile.items().stream()
            .filter(item -> item.roles().contains(com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole.NOODLE))
            .map(item -> normalizeSku(item.sourceSku()))
            .collect(Collectors.toUnmodifiableSet());
        Set<Long> sourceNoodleIds = sourceItems.values().stream()
            .filter(item -> sourceNoodleSkus.contains(normalizeSku(item.sku())))
            .map(SourceItem::id)
            .collect(Collectors.toUnmodifiableSet());

        Map<String, List<SourceOption>> definitions = context.baseGraph().sourceSnapshot().options().stream()
            .filter(option -> Boolean.TRUE.equals(option.active()))
            .filter(option -> sourceNoodleIds.contains(option.ownerMenuItemId()))
            .filter(option -> profile.noodleTypeCodes().contains(option.optionCode()))
            .collect(Collectors.groupingBy(SourceOption::optionCode, LinkedHashMap::new, Collectors.toList()));

        Map<String, NoodleDefinition> resolved = new LinkedHashMap<>();
        for (String code : profile.noodleTypeCodes()) {
            List<SourceOption> candidates = definitions.getOrDefault(code, List.of());
            if (candidates.isEmpty()) {
                throw sourceInvalid("A reviewed noodle type definition is missing");
            }
            NoodleDefinition definition = definition(candidates.get(0));
            if (!"noodle_type".equals(normalizeType(definition.optionType()))
                || !"NOODLE_TYPE".equals(normalizeGroup(definition.optionGroup()))
                || candidates.stream().map(this::definition).anyMatch(candidate -> !candidate.equals(definition))) {
                throw sourceInvalid("A reviewed noodle type definition is ambiguous");
            }
            resolved.put(code, definition);
        }
        return Map.copyOf(resolved);
    }

    private NoodleDefinition definition(SourceOption option) {
        if (!exact(option.optionCode()) || !exact(option.nameZh()) || !exact(option.nameEn())
            || !exact(option.optionType()) || !exact(option.optionGroup()) || option.priceDelta() == null) {
            throw sourceInvalid("A reviewed noodle type definition is incomplete");
        }
        return new NoodleDefinition(
            option.optionType(), option.optionGroup(), option.optionCode(), option.nameZh(), option.nameEn(),
            option.priceDelta().setScale(2)
        );
    }

    private void ensureReviewedAddOn(
        GeneratedOptionRule rule,
        ExistingOptions existing,
        List<PlannedOption> plan,
        List<StoreMenuClonePlannedOption> updates,
        Long targetItemId
    ) {
        StoreMenuClonePlannedOption current = existing.get(targetItemId, rule.optionCode());
        if (current == null) {
            add(plan, existing, targetItemId, rule);
            return;
        }
        if (!"addon".equals(normalizeType(current.optionType()))
            || !"ADD_ON".equals(normalizeGroup(current.optionGroup()))
            || current.parentOptionCode() != null) {
            throw invalidTarget("Reviewed add-on conflicts with a copied target option");
        }
        updates.add(new StoreMenuClonePlannedOption(
            targetItemId,
            current.sourceOptionId(),
            rule.optionType(),
            rule.optionCode(),
            rule.optionGroup(),
            null,
            rule.sortOrder(),
            rule.nameZh(),
            rule.nameEn(),
            rule.priceDelta(),
            true
        ));
    }

    private void add(
        List<PlannedOption> plan,
        ExistingOptions existing,
        Long targetItemId,
        GeneratedOptionRule rule
    ) {
        requireRule(rule);
        OptionKey key = new OptionKey(targetItemId, normalizeCode(rule.optionCode()));
        if (existing.byCode().containsKey(key)
            || plan.stream().anyMatch(option -> option.key().equals(key))) {
            throw invalidTarget("A generated profile option conflicts with an existing option code");
        }
        plan.add(new PlannedOption(key, rule));
    }

    private void requireRule(GeneratedOptionRule rule) {
        if (rule == null || !exact(rule.optionType()) || !exact(rule.optionGroup()) || !exact(rule.optionCode())
            || !exact(rule.nameZh()) || !exact(rule.nameEn()) || rule.priceDelta() == null
            || rule.priceDelta().signum() < 0 || rule.sortOrder() < 1) {
            throw invalidProfile("Generated Chinatown option rule is invalid");
        }
    }

    private void validateCompleteProfileGraph(
        ChinatownMenuCloneProfile profile,
        Map<String, Long> targetIds,
        ExistingOptions existing,
        List<PlannedOption> plan,
        List<StoreMenuClonePlannedOption> updates
    ) {
        Set<OptionKey> effective = new LinkedHashSet<>(existing.byCode().keySet());
        plan.stream().map(PlannedOption::key).forEach(effective::add);
        updates.forEach(option -> effective.add(new OptionKey(option.targetItemId(), normalizeCode(option.optionCode()))));
        for (String noodleSku : noodleTargetSkus(profile)) {
            Long itemId = targetIds.get(noodleSku);
            profile.noodleTypeCodes().forEach(code -> requireKey(effective, itemId, code));
            profile.sizeRulesFor(noodleSku).forEach(size -> requireKey(effective, itemId, size.optionCode()));
            requireKey(effective, itemId, ChinatownMenuCloneProfile.TEA_EGG_ADD_ON_CODE);
            requireKey(effective, itemId, ChinatownMenuCloneProfile.EXTRA_MEAT_ADD_ON_CODE);
        }
        for (ComboRule combo : profile.combos()) {
            Long itemId = targetIds.get(combo.mainItemSku());
            requireKey(effective, itemId, ChinatownMenuCloneProfile.COMBO_CODE);
            requireKey(effective, itemId, ChinatownMenuCloneProfile.COMBO_TEA_EGG_CODE);
            combo.sides().forEach(side -> requireKey(effective, itemId, side.optionCode()));
        }
    }

    private List<String> noodleTargetSkus(ChinatownMenuCloneProfile profile) {
        return profile.items().stream()
            .filter(item -> item.roles().contains(com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole.NOODLE))
            .map(item -> item.targetSku())
            .toList();
    }

    private void requireKey(Set<OptionKey> keys, Long itemId, String code) {
        if (!keys.contains(new OptionKey(itemId, normalizeCode(code)))) {
            throw invalidTarget("Generated Chinatown option graph is incomplete");
        }
    }

    private Long requireTargetId(Map<String, Long> targetIds, String sku) {
        Long id = targetIds.get(sku);
        if (id == null) {
            throw invalidTarget("Chinatown target item mapping is incomplete");
        }
        return id;
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSku(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeType(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeGroup(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean exact(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private OwnerStoreMenuCloneException sourceInvalid(String message) {
        return new OwnerStoreMenuCloneException("SOURCE_OPTION_AMBIGUOUS", HttpStatus.CONFLICT, message);
    }

    private OwnerStoreMenuCloneException invalidProfile(String message) {
        return new OwnerStoreMenuCloneException(
            "TARGET_MENU_VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message
        );
    }

    private OwnerStoreMenuCloneException invalidTarget(String message) {
        return new OwnerStoreMenuCloneException(
            "TARGET_MENU_VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message
        );
    }

    private record OptionKey(Long targetItemId, String optionCode) {
    }

    private record ExistingOptions(Map<OptionKey, StoreMenuClonePlannedOption> byCode) {
        private StoreMenuClonePlannedOption get(Long targetItemId, String optionCode) {
            return byCode.get(new OptionKey(targetItemId, optionCode.toLowerCase(Locale.ROOT)));
        }
    }

    private record NoodleDefinition(
        String optionType,
        String optionGroup,
        String optionCode,
        String nameZh,
        String nameEn,
        BigDecimal priceDelta
    ) {
    }

    private record PlannedOption(OptionKey key, GeneratedOptionRule rule) {
        private StoreMenuClonePlannedOption toOptionPlan() {
            return new StoreMenuClonePlannedOption(
                key.targetItemId(),
                null,
                rule.optionType(),
                rule.optionCode(),
                rule.optionGroup(),
                null,
                rule.sortOrder(),
                rule.nameZh(),
                rule.nameEn(),
                rule.priceDelta(),
                true
            );
        }
    }
}
