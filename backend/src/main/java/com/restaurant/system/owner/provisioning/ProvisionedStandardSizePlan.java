package com.restaurant.system.owner.provisioning;

import com.restaurant.system.menu.pricing.StandardSize;
import com.restaurant.system.owner.master.ChainMasterMenuOptionEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class ProvisionedStandardSizePlan {

    private ProvisionedStandardSizePlan() {
    }

    static Map<String, Decision> from(List<ChainMasterMenuOptionEntity> options) {
        Map<SizeKey, List<Candidate>> candidatesBySize = new LinkedHashMap<>();
        for (ChainMasterMenuOptionEntity option : options) {
            resolveSize(option).ifPresent(size -> candidatesBySize
                .computeIfAbsent(new SizeKey(option.master_product_key, size), ignored -> new ArrayList<>())
                .add(new Candidate(option, size)));
        }

        Map<String, Decision> decisions = new LinkedHashMap<>();
        for (List<Candidate> candidates : candidatesBySize.values()) {
            boolean anyActive = candidates.stream()
                .anyMatch(candidate -> Boolean.TRUE.equals(candidate.option().default_active));
            Candidate canonical = candidates.stream()
                .filter(candidate -> StandardSize.fromCode(candidate.option().code).isPresent())
                .min(candidateOrder())
                .orElseGet(() -> anyActive
                    ? candidates.stream()
                        .filter(candidate -> Boolean.TRUE.equals(candidate.option().default_active))
                        .min(candidateOrder())
                        .orElseThrow()
                    : null);

            for (Candidate candidate : candidates) {
                boolean isCanonical = candidate == canonical;
                decisions.put(
                    candidate.option().master_option_key,
                    new Decision(candidate.size(), isCanonical, isCanonical && anyActive)
                );
            }
        }
        return decisions;
    }

    static String stableLegacyCode(ChainMasterMenuOptionEntity option, StandardSize size) {
        String identity = firstNonBlank(option.option_ref, option.master_option_key);
        String normalizedIdentity = identity.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        return size.code + "_legacy_" + normalizedIdentity;
    }

    private static Optional<StandardSize> resolveSize(ChainMasterMenuOptionEntity option) {
        if (option == null || !("SIZE".equalsIgnoreCase(option.option_group)
            || "size".equalsIgnoreCase(option.option_type))) {
            return Optional.empty();
        }
        return StandardSize.fromOption(option.code, option.name_zh, option.name_en);
    }

    private static Comparator<Candidate> candidateOrder() {
        return Comparator
            .comparing((Candidate candidate) -> !Boolean.TRUE.equals(candidate.option().default_active))
            .thenComparing(candidate -> candidate.option().sort_order == null
                ? Integer.MAX_VALUE
                : candidate.option().sort_order)
            .thenComparing(candidate -> candidate.option().master_option_key);
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    record Decision(StandardSize size, boolean canonical, boolean active) {
    }

    private record Candidate(ChainMasterMenuOptionEntity option, StandardSize size) {
    }

    private record SizeKey(String masterProductKey, StandardSize size) {
    }
}
