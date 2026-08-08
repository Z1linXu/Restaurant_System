package com.restaurant.system.owner.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

final class StoreProfileFingerprint {

    private StoreProfileFingerprint() {
    }

    static String compute(StoreProfileDescriptor profile) {
        StoreProfileComposition composition = profile.composition();
        StringBuilder canonical = new StringBuilder();
        append(canonical, "profileCode");
        append(canonical, profile.profileCode());
        append(canonical, "profileVersion");
        append(canonical, profile.profileVersion());
        append(canonical, "moduleCount");
        append(canonical, String.valueOf(composition.modules().size()));
        composition.modules().stream()
            .sorted(Comparator.comparing(reference -> reference.moduleCode().name()))
            .forEach(reference -> {
                append(canonical, "moduleCode");
                append(canonical, reference.moduleCode().name());
                append(canonical, "contractVersion");
                append(canonical, reference.contractVersion());
                append(canonical, "policy");
                append(canonical, reference.policy().name());
                append(canonical, "configurationReference");
                append(canonical, reference.configurationReference());
                append(canonical, "expectedConfigurationFingerprint");
                append(canonical, reference.expectedConfigurationFingerprint());
            });
        append(canonical, "activationRequirementCount");
        append(canonical, String.valueOf(composition.activationRequirements().size()));
        composition.activationRequirements().stream()
            .map(Enum::name)
            .sorted()
            .forEach(value -> {
                append(canonical, "activationRequirement");
                append(canonical, value);
            });
        return sha256(canonical.toString());
    }

    private static void append(StringBuilder target, String value) {
        String normalized = value == null ? "" : value;
        target.append(normalized.length()).append(':').append(normalized);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                hex.append(String.format("%02x", valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
