package com.restaurant.system.owner.menu;

import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceItem;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceOption;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionApplication;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionDisposition;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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

/** Adds profile-selected source options to the transaction-local clone plan. */
@Component
public final class StoreMenuCloneSourceOptionsComposer implements StoreMenuCloneGraphComposer {

    public static final String IDENTITY = "source-options";
    public static final int ORDER = 100;

    private final StoreMenuCloneProfileRegistry profileRegistry;

    public StoreMenuCloneSourceOptionsComposer(StoreMenuCloneProfileRegistry profileRegistry) {
        this.profileRegistry = profileRegistry;
    }

    @Override
    public String identity() {
        return IDENTITY;
    }

    @Override
    public Phase phase() {
        return Phase.SOURCE_OPTIONS;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean supports(String profileCode) {
        return profileRegistry.find(profileCode)
            .filter(StoreMenuCloneSourceOptionsProfile.class::isInstance)
            .isPresent();
    }

    @Override
    public int compose(StoreMenuCloneCompositionContext context) {
        if (!(context.profile() instanceof StoreMenuCloneSourceOptionsProfile profile)) {
            throw invalidProfile("Source option composer requires an explicit profile capability");
        }
        validateContext(context);
        List<PlannedOption> plan = buildPlan(context, profile);
        plan.stream().map(PlannedOption::toOptionPlan).forEach(context::addOption);
        return plan.size();
    }

    private void validateContext(StoreMenuCloneCompositionContext context) {
        if (!Objects.equals(context.sourceStoreId(), context.baseGraph().sourceSnapshot().storeId())) {
            throw sourceInvalid("Source option snapshot Store scope is invalid");
        }
        if (Objects.equals(context.sourceStoreId(), context.targetStoreId())) {
            throw invalidTarget("Source options require distinct source and target Stores");
        }
    }

    private List<PlannedOption> buildPlan(
        StoreMenuCloneCompositionContext context,
        StoreMenuCloneSourceOptionsProfile profile
    ) {
        StoreMenuCloneSnapshot snapshot = context.baseGraph().sourceSnapshot();
        Map<Long, SourceItem> sourceItemsById = consistentSourceItemIndex(snapshot.items());
        Map<String, SourceItem> sourceItemsBySku = uniqueIndexByString(
            List.copyOf(sourceItemsById.values()),
            item -> normalizeSku(item.sku()),
            "Source item SKUs are incomplete or duplicated"
        );
        for (SourceItem item : snapshot.items()) {
            if (!Objects.equals(item.storeId(), context.sourceStoreId())) {
                throw sourceInvalid("Source item Store ownership is invalid");
            }
        }

        Map<Long, SourceOption> optionsById = uniqueIndex(
            snapshot.options(),
            SourceOption::id,
            "Source option IDs are incomplete or duplicated"
        );
        for (SourceOption option : snapshot.options()) {
            if (option.ownerMenuItemId() == null
                || !Objects.equals(option.ownerStoreId(), context.sourceStoreId())) {
                throw sourceInvalid("Source option Store ownership evidence is invalid");
            }
            if (!sourceItemsById.containsKey(option.ownerMenuItemId())) {
                throw sourceInvalid("Source option item ownership evidence is invalid");
            }
            if (option.active() == null) {
                throw sourceInvalid("Source option active state is ambiguous");
            }
        }

        Map<String, Long> targetIdsBySku = context.baseGraph().targetItemIdByTargetSku();
        validateTargetMappings(targetIdsBySku);
        List<ResolvedApplication> applications = resolveApplications(
            profile.sourceOptionApplications(),
            profile.items(),
            sourceItemsBySku,
            targetIdsBySku
        );
        validateApplicationCoverage(profile.items(), sourceItemsBySku, optionsById.values(), applications);

        List<PlannedOption> plan = new ArrayList<>();
        Set<LogicalKey> logicalKeys = new LinkedHashSet<>();
        for (ResolvedApplication application : applications) {
            List<SourceOption> activeOptions = optionsById.values().stream()
                .filter(option -> Objects.equals(option.ownerMenuItemId(), application.sourceItem().id()))
                .filter(option -> Boolean.TRUE.equals(option.active()))
                .toList();
            Map<Long, SourceOption> selected = activeOptions.stream()
                .filter(option -> requireDisposition(option, application.rules()) == SourceOptionDisposition.COPY)
                .collect(Collectors.toMap(
                    SourceOption::id,
                    Function.identity(),
                    (first, duplicate) -> {
                        throw sourceInvalid("Source option selection contains duplicate identities");
                    },
                    LinkedHashMap::new
                ));

            validateCopiedOptionCodes(selected.values());
            validateParentGraph(application.sourceItem(), selected, optionsById, context.sourceStoreId());
            selected.values().stream()
                .sorted(sourceOptionOrder())
                .forEach(option -> {
                    LogicalKey key = new LogicalKey(application.targetItemId(), option.id());
                    String parentOptionCode = option.parentOptionId() == null
                        ? null
                        : optionsById.get(option.parentOptionId()).optionCode();
                    if (!logicalKeys.add(key)) {
                        throw invalidProfile("Source option profile creates a duplicate target application");
                    }
                    plan.add(new PlannedOption(key, parentOptionCode, option));
                });
        }
        return List.copyOf(plan);
    }

    private List<ResolvedApplication> resolveApplications(
        List<SourceOptionApplication> configured,
        List<ItemSelection> itemSelections,
        Map<String, SourceItem> sourceItemsBySku,
        Map<String, Long> targetIdsBySku
    ) {
        if (configured == null) {
            throw invalidProfile("Source option application contract is required");
        }
        List<ResolvedApplication> resolved = new ArrayList<>();
        Set<ApplicationKey> applicationKeys = new LinkedHashSet<>();
        Map<Long, Long> sourceOwnerByTargetId = new LinkedHashMap<>();
        Map<String, ExpectedSourceMapping> expectedSourceByTargetSku = expectedSourceByTargetSku(
            itemSelections,
            sourceItemsBySku
        );
        for (SourceOptionApplication application : configured) {
            if (application == null
                || !isExactNonBlank(application.sourceItemSku())
                || !isExactNonBlank(application.targetItemSku())
                || application.rules() == null
                || application.rules().isEmpty()) {
                throw invalidProfile("Source option applications require source, target, and classification rules");
            }
            String sourceSku = normalizeSku(application.sourceItemSku());
            String targetSku = normalizeSku(application.targetItemSku());
            ApplicationKey applicationKey = new ApplicationKey(sourceSku, targetSku);
            if (!applicationKeys.add(applicationKey)) {
                throw invalidProfile("Source option applications must have unique source-target pairs");
            }
            Map<SelectorKey, SourceOptionDisposition> rules = requireRules(application.rules());
            ExpectedSourceMapping expected = expectedSourceByTargetSku.get(targetSku);
            Long targetItemId = targetIdsBySku.get(targetSku);
            if (expected == null
                || targetItemId == null
                || !Objects.equals(expected.sourceSku(), sourceSku)) {
                throw invalidProfile("Source option application references an unknown source or target item");
            }
            SourceItem sourceItem = expected.sourceItem();
            if (sourceItem == null) {
                if (expected.sourcePolicy() != SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE) {
                    throw invalidProfile("Required source option application has no source item");
                }
                continue;
            }
            Long existingSourceOwner = sourceOwnerByTargetId.putIfAbsent(targetItemId, sourceItem.id());
            if (existingSourceOwner != null && !Objects.equals(existingSourceOwner, sourceItem.id())) {
                throw invalidProfile("A target item cannot combine source options from different source items");
            }
            resolved.add(new ResolvedApplication(
                sourceSku,
                targetSku,
                sourceItem,
                targetItemId,
                rules
            ));
        }
        return resolved.stream()
            .sorted(Comparator
                .comparing(ResolvedApplication::sourceSku)
                .thenComparing(ResolvedApplication::targetSku))
            .toList();
    }

    private Map<String, ExpectedSourceMapping> expectedSourceByTargetSku(
        List<ItemSelection> itemSelections,
        Map<String, SourceItem> sourceItemsBySku
    ) {
        if (itemSelections == null) {
            throw invalidProfile("Source option profile item contract is required");
        }
        Map<String, ExpectedSourceMapping> expected = new LinkedHashMap<>();
        for (ItemSelection selection : itemSelections) {
            if (selection == null || !isExactNonBlank(selection.targetSku())) {
                throw invalidProfile("Source option profile requires exact target item SKUs");
            }
            String sourceSku = normalizeSku(selection.sourceSku());
            if (sourceSku == null) {
                continue;
            }
            String targetSku = normalizeSku(selection.targetSku());
            ExpectedSourceMapping mapping = new ExpectedSourceMapping(
                sourceSku,
                selection.sourcePolicy(),
                sourceItemsBySku.get(sourceSku)
            );
            if (expected.putIfAbsent(targetSku, mapping) != null) {
                throw invalidProfile("Source option profile target item mappings must be unique");
            }
        }
        return Map.copyOf(expected);
    }

    private void validateApplicationCoverage(
        List<ItemSelection> itemSelections,
        Map<String, SourceItem> sourceItemsBySku,
        Collection<SourceOption> options,
        List<ResolvedApplication> applications
    ) {
        Map<String, ExpectedSourceMapping> expectedSourceByTargetSku = expectedSourceByTargetSku(
            itemSelections,
            sourceItemsBySku
        );
        Set<ApplicationKey> configured = applications.stream()
            .map(application -> new ApplicationKey(application.sourceSku(), application.targetSku()))
            .collect(Collectors.toUnmodifiableSet());
        Set<Long> sourceIdsWithActiveOptions = options.stream()
            .filter(option -> Boolean.TRUE.equals(option.active()))
            .map(SourceOption::ownerMenuItemId)
            .collect(Collectors.toUnmodifiableSet());
        for (Map.Entry<String, ExpectedSourceMapping> expected : expectedSourceByTargetSku.entrySet()) {
            SourceItem sourceItem = expected.getValue().sourceItem();
            if (sourceItem != null
                && sourceIdsWithActiveOptions.contains(sourceItem.id())
                && !configured.contains(new ApplicationKey(expected.getValue().sourceSku(), expected.getKey()))) {
                throw invalidProfile("Every reused item with active source options requires a reviewed application");
            }
        }
    }

    private Map<SelectorKey, SourceOptionDisposition> requireRules(List<SourceOptionRule> rules) {
        Map<SelectorKey, SourceOptionDisposition> resolved = new LinkedHashMap<>();
        for (SourceOptionRule rule : rules) {
            if (rule == null
                || !isExactNonBlank(rule.optionType())
                || !isExactNonBlank(rule.optionGroup())
                || rule.disposition() == null) {
                throw invalidProfile("Source option rules require exact type, group, and disposition values");
            }
            SelectorKey key = selectorKey(rule.optionType(), rule.optionGroup());
            if (resolved.putIfAbsent(key, rule.disposition()) != null) {
                throw invalidProfile("Source option rules must be unique reviewed classifications");
            }
        }
        return Map.copyOf(resolved);
    }

    private SourceOptionDisposition requireDisposition(
        SourceOption option,
        Map<SelectorKey, SourceOptionDisposition> rules
    ) {
        SourceOptionDisposition disposition = rules.get(selectorKey(option.optionType(), option.optionGroup()));
        if (disposition == null) {
            throw sourceInvalid("Active source option is not classified by the reviewed profile");
        }
        return disposition;
    }

    private void validateCopiedOptionCodes(Collection<SourceOption> selected) {
        Set<String> codes = new LinkedHashSet<>();
        for (SourceOption option : selected) {
            if (!isExactNonBlank(option.optionCode()) || !codes.add(normalizeOptionCode(option.optionCode()))) {
                throw sourceInvalid("Copied source option codes must be nonblank and unique per target item");
            }
        }
    }

    private void validateParentGraph(
        SourceItem sourceItem,
        Map<Long, SourceOption> selected,
        Map<Long, SourceOption> allOptions,
        Long sourceStoreId
    ) {
        for (SourceOption option : selected.values()) {
            Long parentId = option.parentOptionId();
            if (parentId == null) {
                continue;
            }
            if (Objects.equals(option.id(), parentId)) {
                throw sourceInvalid("Source option cannot reference itself as parent");
            }
            SourceOption parent = allOptions.get(parentId);
            if (parent == null) {
                throw sourceInvalid("Source option parent is missing");
            }
            if (!Objects.equals(parent.ownerStoreId(), sourceStoreId)) {
                throw sourceInvalid("Source option parent belongs to another Store");
            }
            if (!Objects.equals(parent.ownerMenuItemId(), sourceItem.id())) {
                throw sourceInvalid("Source option parent belongs to another menu item");
            }
            if (!Boolean.TRUE.equals(parent.active())) {
                throw sourceInvalid("Source option parent is inactive");
            }
            if (!selected.containsKey(parentId)) {
                throw sourceInvalid("Source option parent is outside the reviewed selection");
            }
        }

        Map<Long, VisitState> states = new HashMap<>();
        for (Long optionId : selected.keySet()) {
            visit(optionId, selected, states);
        }
    }

    private void visit(Long optionId, Map<Long, SourceOption> selected, Map<Long, VisitState> states) {
        VisitState state = states.get(optionId);
        if (state == VisitState.VISITING) {
            throw sourceInvalid("Source option parent graph contains a cycle");
        }
        if (state == VisitState.VISITED) {
            return;
        }
        states.put(optionId, VisitState.VISITING);
        Long parentId = selected.get(optionId).parentOptionId();
        if (parentId != null) {
            visit(parentId, selected, states);
        }
        states.put(optionId, VisitState.VISITED);
    }

    private void validateTargetMappings(Map<String, Long> targetIdsBySku) {
        if (targetIdsBySku == null || targetIdsBySku.isEmpty()) {
            throw invalidTarget("Target item mapping is unavailable");
        }
        Set<Long> targetIds = new LinkedHashSet<>();
        for (Map.Entry<String, Long> entry : targetIdsBySku.entrySet()) {
            if (normalizeSku(entry.getKey()) == null || entry.getValue() == null || !targetIds.add(entry.getValue())) {
                throw invalidTarget("Target item mapping is incomplete or ambiguous");
            }
        }
    }

    private <T> Map<Long, T> uniqueIndex(List<T> values, Function<T, Long> idReader, String message) {
        Map<Long, T> indexed = new LinkedHashMap<>();
        for (T value : values) {
            Long id = value == null ? null : idReader.apply(value);
            if (id == null || indexed.putIfAbsent(id, value) != null) {
                throw sourceInvalid(message);
            }
        }
        return indexed;
    }

    private Map<Long, SourceItem> consistentSourceItemIndex(List<SourceItem> items) {
        Map<Long, SourceItem> indexed = new LinkedHashMap<>();
        for (SourceItem item : items) {
            if (item == null || item.id() == null) {
                throw sourceInvalid("Source item IDs are incomplete");
            }
            SourceItem existing = indexed.putIfAbsent(item.id(), item);
            if (existing != null && !existing.equals(item)) {
                throw sourceInvalid("Repeated source item snapshots are inconsistent");
            }
        }
        return indexed;
    }

    private <T> Map<String, T> uniqueIndexByString(
        List<T> values,
        Function<T, String> keyReader,
        String message
    ) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T value : values) {
            String key = value == null ? null : keyReader.apply(value);
            if (key == null || indexed.putIfAbsent(key, value) != null) {
                throw sourceInvalid(message);
            }
        }
        return indexed;
    }

    private Comparator<SourceOption> sourceOptionOrder() {
        return Comparator
            .comparing(SourceOption::sortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(SourceOption::id);
    }

    private SelectorKey selectorKey(String optionType, String optionGroup) {
        return new SelectorKey(normalizeType(optionType), normalizeGroup(optionGroup));
    }

    private String normalizeType(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeGroup(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSku(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isExactNonBlank(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private OwnerStoreMenuCloneException sourceInvalid(String message) {
        return new OwnerStoreMenuCloneException("SOURCE_OPTION_AMBIGUOUS", HttpStatus.CONFLICT, message);
    }

    private OwnerStoreMenuCloneException invalidProfile(String message) {
        return new OwnerStoreMenuCloneException(
            "TARGET_MENU_VALIDATION_FAILED",
            HttpStatus.UNPROCESSABLE_ENTITY,
            message
        );
    }

    private OwnerStoreMenuCloneException invalidTarget(String message) {
        return new OwnerStoreMenuCloneException(
            "TARGET_MENU_VALIDATION_FAILED",
            HttpStatus.UNPROCESSABLE_ENTITY,
            message
        );
    }

    private record ApplicationKey(String sourceSku, String targetSku) {
    }

    private record ExpectedSourceMapping(
        String sourceSku,
        SourcePolicy sourcePolicy,
        SourceItem sourceItem
    ) {
    }

    private record SelectorKey(String optionType, String optionGroup) {
    }

    private record ResolvedApplication(
        String sourceSku,
        String targetSku,
        SourceItem sourceItem,
        Long targetItemId,
        Map<SelectorKey, SourceOptionDisposition> rules
    ) {
    }

    private record LogicalKey(Long targetItemId, Long sourceOptionId) {
    }

    private record PlannedOption(LogicalKey key, String parentOptionCode, SourceOption source) {

        private StoreMenuClonePlannedOption toOptionPlan() {
            return new StoreMenuClonePlannedOption(
                key.targetItemId(),
                source.id(),
                source.optionType(),
                source.optionCode(),
                source.optionGroup(),
                parentOptionCode,
                source.sortOrder(),
                source.nameZh(),
                source.nameEn(),
                source.priceDelta(),
                source.active()
            );
        }
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
