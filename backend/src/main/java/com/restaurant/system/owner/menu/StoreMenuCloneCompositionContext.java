package com.restaurant.system.owner.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** In-memory graph composition context shared by read-only validation and execution. */
public final class StoreMenuCloneCompositionContext {

    private final StoreMenuCloneBaseGraphProfile profile;
    private final Long sourceStoreId;
    private final Long targetStoreId;
    private final StoreMenuCloneBaseGraphResult baseGraph;
    private final List<StoreMenuClonePlannedOption> options = new ArrayList<>();

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

    public StoreMenuCloneBaseGraphProfile profile() { return profile; }
    public Long sourceStoreId() { return sourceStoreId; }
    public Long targetStoreId() { return targetStoreId; }
    public StoreMenuCloneBaseGraphResult baseGraph() { return baseGraph; }

    public void addOption(StoreMenuClonePlannedOption option) {
        options.add(option);
    }

    public void replaceOption(StoreMenuClonePlannedOption option) {
        OptionKey key = key(option.targetItemId(), option.optionCode());
        for (int index = options.size() - 1; index >= 0; index--) {
            StoreMenuClonePlannedOption existing = options.get(index);
            if (existing != null && key.equals(key(existing.targetItemId(), existing.optionCode()))) {
                options.set(index, option);
                return;
            }
        }
        throw new IllegalStateException("Menu clone option plan replacement target is missing");
    }

    public Optional<StoreMenuClonePlannedOption> findOption(Long targetItemId, String optionCode) {
        OptionKey key = key(targetItemId, optionCode);
        for (int index = options.size() - 1; index >= 0; index--) {
            StoreMenuClonePlannedOption option = options.get(index);
            if (option != null && key.equals(key(option.targetItemId(), option.optionCode()))) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    public List<StoreMenuClonePlannedOption> options() {
        return List.copyOf(options);
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
