package com.restaurant.system.owner.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory graph composition context shared by read-only validation and execution.
 */
public final class StoreMenuCloneCompositionContext {

    private final StoreMenuCloneBaseGraphProfile profile;
    private final Long sourceStoreId;
    private final Long targetStoreId;
    private final StoreMenuCloneBaseGraphResult baseGraph;
    private final Map<OptionKey, StoreMenuClonePlannedOption> options = new LinkedHashMap<>();

    public StoreMenuCloneCompositionContext(
        StoreMenuCloneBaseGraphProfile profile,
        Long sourceStoreId,
        Long targetStoreId,
        StoreMenuCloneBaseGraphResult baseGraph
    ) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.sourceStoreId = Objects.requireNonNull(sourceStoreId, "sourceStoreId");
        this.targetStoreId = Objects.requireNonNull(targetStoreId, "targetStoreId");
        this.baseGraph = Objects.requireNonNull(baseGraph, "baseGraph");
    }

    public StoreMenuCloneBaseGraphProfile profile() {
        return profile;
    }

    public Long sourceStoreId() {
        return sourceStoreId;
    }

    public Long targetStoreId() {
        return targetStoreId;
    }

    public StoreMenuCloneBaseGraphResult baseGraph() {
        return baseGraph;
    }

    public void addOption(StoreMenuClonePlannedOption option) {
        OptionKey key = key(option.targetItemId(), option.optionCode());
        if (options.putIfAbsent(key, option) != null) {
            throw new IllegalStateException("Menu clone option plan contains a duplicate target option code");
        }
    }

    public void replaceOption(StoreMenuClonePlannedOption option) {
        OptionKey key = key(option.targetItemId(), option.optionCode());
        if (!options.containsKey(key)) {
            throw new IllegalStateException("Menu clone option plan replacement target is missing");
        }
        options.put(key, option);
    }

    public Optional<StoreMenuClonePlannedOption> findOption(Long targetItemId, String optionCode) {
        return Optional.ofNullable(options.get(key(targetItemId, optionCode)));
    }

    public List<StoreMenuClonePlannedOption> options() {
        return List.copyOf(options.values());
    }

    private OptionKey key(Long targetItemId, String optionCode) {
        Objects.requireNonNull(targetItemId, "targetItemId");
        if (optionCode == null || optionCode.isBlank()) {
            throw new IllegalArgumentException("optionCode is required");
        }
        return new OptionKey(targetItemId, optionCode.trim().toLowerCase(Locale.ROOT));
    }

    private record OptionKey(Long targetItemId, String optionCode) {
    }
}
