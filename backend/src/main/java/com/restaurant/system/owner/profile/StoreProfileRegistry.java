package com.restaurant.system.owner.profile;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StoreProfileRegistry {

    private final Map<StoreProfileIdentity, StoreProfileDescriptor> profilesByIdentity;

    @Autowired
    public StoreProfileRegistry(ObjectProvider<StoreProfileDescriptor> profiles) {
        this(profiles.orderedStream().toList());
    }

    StoreProfileRegistry(List<StoreProfileDescriptor> profiles) {
        Map<StoreProfileIdentity, StoreProfileDescriptor> indexed = new HashMap<>();
        for (StoreProfileDescriptor profile : profiles) {
            StoreProfileDescriptor snapshot = validateAndSnapshot(profile);
            StoreProfileIdentity identity = new StoreProfileIdentity(snapshot.profileCode(), snapshot.profileVersion());
            if (indexed.put(identity, snapshot) != null) {
                throw new IllegalStateException("Store profile identities must be unique");
            }
        }
        this.profilesByIdentity = Map.copyOf(indexed);
    }

    public Optional<StoreProfileDescriptor> find(String profileCode, String profileVersion) {
        if (!StoreProfileIdentity.isExact(profileCode) || !StoreProfileIdentity.isExact(profileVersion)) {
            return Optional.empty();
        }
        return Optional.ofNullable(profilesByIdentity.get(new StoreProfileIdentity(profileCode, profileVersion)));
    }

    public List<StoreProfileSummary> summaries() {
        return profilesByIdentity.values().stream()
            .sorted(Comparator.comparing(StoreProfileDescriptor::profileCode)
                .thenComparing(StoreProfileDescriptor::profileVersion))
            .map(profile -> new StoreProfileSummary(
                profile.profileCode(),
                profile.profileVersion(),
                profile.profileFingerprint(),
                profile.composition().modules().stream()
                    .map(StoreProfileModuleReference::moduleCode)
                    .sorted()
                    .toList()
            ))
            .toList();
    }

    private StoreProfileDescriptor validateAndSnapshot(StoreProfileDescriptor profile) {
        if (profile == null) {
            throw new IllegalStateException("Store profile descriptors must have an exact identity and composition");
        }
        String profileCode = profile.profileCode();
        String profileVersion = profile.profileVersion();
        StoreProfileComposition composition = profile.composition();
        if (!StoreProfileIdentity.isExact(profileCode)
            || !StoreProfileIdentity.isExact(profileVersion)
            || composition == null) {
            throw new IllegalStateException("Store profile descriptors must have an exact identity and composition");
        }
        StoreProfileDescriptor captured = new RegisteredStoreProfile(
            profileCode,
            profileVersion,
            composition,
            profile.profileFingerprint()
        );
        validateModules(captured.composition());
        String canonicalFingerprint = StoreProfileFingerprint.compute(captured);
        if (!canonicalFingerprint.equals(captured.profileFingerprint())) {
            throw new IllegalStateException("Store profile fingerprint must match its canonical composition");
        }
        return new RegisteredStoreProfile(
            captured.profileCode(),
            captured.profileVersion(),
            captured.composition(),
            canonicalFingerprint
        );
    }

    private void validateModules(StoreProfileComposition composition) {
        if (composition.modules().isEmpty()) {
            throw new IllegalStateException("Store profile must declare at least one provisioning module");
        }
        Set<StoreProvisioningModuleCode> moduleCodes = new HashSet<>();
        Map<StoreProvisioningModuleCode, StoreProfileModuleReference> modulesByCode = new HashMap<>();
        for (StoreProfileModuleReference module : composition.modules()) {
            if (module == null
                || module.moduleCode() == null
                || module.policy() == null
                || !StoreProfileIdentity.isExact(module.contractVersion())
                || !hasValidConfigurationBinding(module)
                || !moduleCodes.add(module.moduleCode())) {
                throw new IllegalStateException("Store profile module references must be complete and unique");
            }
            modulesByCode.put(module.moduleCode(), module);
        }
        for (StoreProvisioningModuleCode requirement : composition.activationRequirements()) {
            StoreProfileModuleReference module = modulesByCode.get(requirement);
            if (requirement == null || module == null || module.policy() == StoreProfileModulePolicy.NOT_APPLICABLE) {
                throw new IllegalStateException("Activation requirements must reference applicable profile modules");
            }
        }
    }

    private boolean hasValidConfigurationBinding(StoreProfileModuleReference module) {
        if (module.policy() == StoreProfileModulePolicy.NOT_APPLICABLE) {
            return module.configurationReference() == null && module.expectedConfigurationFingerprint() == null;
        }
        return StoreProfileIdentity.isExact(module.configurationReference())
            && StoreProfileIdentity.isExact(module.expectedConfigurationFingerprint());
    }

    private record RegisteredStoreProfile(
        String profileCode,
        String profileVersion,
        StoreProfileComposition composition,
        String profileFingerprint
    ) implements StoreProfileDescriptor {
    }
}
