package com.restaurant.system.modules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record HardwareCapabilityCatalogDefinition(
    String catalogVersion,
    List<HardwareCapabilityDefinition> capabilities,
    Map<String, HardwareCapabilityDefinition> capabilitiesByKey,
    Map<String, Set<String>> canonicalKeysBySupportedKey
) {
    public HardwareCapabilityCatalogDefinition {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        capabilitiesByKey = capabilitiesByKey == null ? Map.of() : Map.copyOf(capabilitiesByKey);
        canonicalKeysBySupportedKey = canonicalKeysBySupportedKey == null
            ? Map.of()
            : canonicalKeysBySupportedKey.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                    entry -> normalize(entry.getKey()),
                    entry -> Set.copyOf(entry.getValue())
                ));
    }

    public boolean supports(String capabilityKeyOrAlias) {
        return !canonicalKeys(capabilityKeyOrAlias).isEmpty();
    }

    public Set<String> canonicalKeys(String capabilityKeyOrAlias) {
        if (capabilityKeyOrAlias == null || capabilityKeyOrAlias.isBlank()) {
            return Set.of();
        }
        return canonicalKeysBySupportedKey.getOrDefault(normalize(capabilityKeyOrAlias), Set.of());
    }

    public Optional<HardwareCapabilityDefinition> capability(String capabilityKey) {
        return Optional.ofNullable(capabilitiesByKey.get(normalize(capabilityKey)));
    }

    public Set<String> canonicalCapabilityKeys() {
        return new LinkedHashSet<>(capabilitiesByKey.keySet());
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
