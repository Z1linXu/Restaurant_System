package com.restaurant.system.owner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class StoreProfileRegistryTest {

    @Test
    void resolvesOnlyAnExactCaseSensitiveIdentity() {
        StoreProfileDescriptor profile = profile("PROFILE_A", "1", modules());
        StoreProfileRegistry registry = new StoreProfileRegistry(List.of(profile));

        assertThat(registry.find("PROFILE_A", "1")).isPresent();
        assertThat(registry.find("PROFILE_A", "1").orElseThrow()).isNotSameAs(profile);
        assertThat(registry.find("profile_a", "1")).isEmpty();
        assertThat(registry.find(" PROFILE_A", "1")).isEmpty();
        assertThat(registry.find("PROFILE_A", "1 ")).isEmpty();
    }

    @Test
    void identityRejectsBlankOrPaddedValues() {
        assertThatThrownBy(() -> new StoreProfileIdentity(" PROFILE_A", "1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoreProfileIdentity("PROFILE_A", " "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoreProfileIdentity("\u2003PROFILE_A", "1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new StoreProfileIdentity("PROFILE_A", "1"))
            .isEqualTo(new StoreProfileIdentity("PROFILE_A", "1"));
    }

    @Test
    void permitsDifferentVersionsButRejectsAnExactDuplicateIdentity() {
        StoreProfileDescriptor v1 = profile("PROFILE_A", "1", modules());
        StoreProfileDescriptor v2 = profile("PROFILE_A", "2", modules());

        assertThatCode(() -> new StoreProfileRegistry(List.of(v1, v2))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new StoreProfileRegistry(List.of(v1, v1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identities must be unique");
    }

    @Test
    void emptyRegistryIsAValidPreparationState() {
        StoreProfileRegistry registry = new StoreProfileRegistry(List.of());

        assertThat(registry.summaries()).isEmpty();
        assertThat(registry.find("PROFILE_A", "1")).isEmpty();
    }

    @Test
    void springContextStartsWithoutConcreteProfileBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(StoreProfileRegistry.class);
            context.refresh();

            assertThat(context.getBean(StoreProfileRegistry.class).summaries()).isEmpty();
        }
    }

    @Test
    void fingerprintIsStableAcrossModuleAndSetIterationOrder() {
        List<StoreProfileModuleReference> ordered = modules();
        List<StoreProfileModuleReference> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);
        StoreProfileDescriptor first = profile("PROFILE_A", "1", ordered);
        StoreProfileDescriptor second = profile("PROFILE_A", "1", reversed);

        assertThat(first.profileFingerprint()).isEqualTo(second.profileFingerprint());
        assertThat(first.profileFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void referencedConfigurationFingerprintChangesTheStoreProfileFingerprint() {
        StoreProfileDescriptor first = profile("PROFILE_A", "1", modules());
        StoreProfileDescriptor changed = profile("PROFILE_A", "1", List.of(
            new StoreProfileModuleReference(
                StoreProvisioningModuleCode.MENU,
                "MENU_V1",
                StoreProfileModulePolicy.REQUIRED,
                "MENU_PROFILE_V1",
                "MENU_FINGERPRINT_V2"
            ),
            modules().get(1)
        ));

        assertThat(first.profileFingerprint()).isNotEqualTo(changed.profileFingerprint());
    }

    @Test
    void rejectsDuplicateModulesAndInvalidActivationRequirements() {
        StoreProfileModuleReference menu = modules().get(0);
        StoreProfileDescriptor duplicate = profile("DUPLICATE_MODULE", "1", List.of(menu, menu));
        StoreProfileDescriptor invalidActivation = profile(
            "INVALID_ACTIVATION",
            "1",
            List.of(new StoreProfileModuleReference(
                StoreProvisioningModuleCode.MENU,
                "MENU_V1",
                StoreProfileModulePolicy.NOT_APPLICABLE,
                null,
                null
            ))
        );

        assertThatThrownBy(() -> new StoreProfileRegistry(List.of(duplicate)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("complete and unique");
        assertThatThrownBy(() -> new StoreProfileRegistry(List.of(invalidActivation)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Activation requirements");
    }

    @Test
    void rejectsNonCanonicalFingerprintOverrides() {
        StoreProfileDescriptor profile = new SyntheticProfile("PROFILE_A", "1", modules()) {
            @Override
            public String profileFingerprint() {
                return "forged";
            }
        };

        assertThatThrownBy(() -> new StoreProfileRegistry(List.of(profile)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("canonical composition");
    }

    @Test
    void summariesExposeOnlyBoundedProfileMetadata() {
        StoreProfileRegistry registry = new StoreProfileRegistry(List.of(
            profile("PROFILE_B", "2", modules()),
            profile("PROFILE_A", "1", modules())
        ));

        assertThat(registry.summaries()).extracting(StoreProfileSummary::profileCode)
            .containsExactly("PROFILE_A", "PROFILE_B");
        assertThat(registry.summaries().get(0).modules())
            .containsExactly(StoreProvisioningModuleCode.MENU, StoreProvisioningModuleCode.TABLES);
    }

    @Test
    void compositionDefensivelyCopiesModuleAndRequirementCollections() {
        List<StoreProfileModuleReference> mutableModules = new ArrayList<>(modules());
        Set<StoreProvisioningModuleCode> mutableRequirements = new java.util.HashSet<>(
            Set.of(StoreProvisioningModuleCode.MENU)
        );
        StoreProfileComposition composition = new StoreProfileComposition(mutableModules, mutableRequirements);

        mutableModules.clear();
        mutableRequirements.clear();

        assertThat(composition.modules()).hasSize(2);
        assertThat(composition.activationRequirements()).containsExactly(StoreProvisioningModuleCode.MENU);
        assertThatThrownBy(() -> composition.modules().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registryReturnsAnImmutableSnapshotWhenDescriptorStateLaterChanges() {
        MutableProfile mutable = new MutableProfile("PROFILE_A", "1", modules());
        StoreProfileRegistry registry = new StoreProfileRegistry(List.of(mutable));
        StoreProfileDescriptor registered = registry.find("PROFILE_A", "1").orElseThrow();
        String registeredFingerprint = registered.profileFingerprint();

        mutable.composition = new StoreProfileComposition(
            List.of(new StoreProfileModuleReference(
                StoreProvisioningModuleCode.DEVICES,
                "DEVICES_V1",
                StoreProfileModulePolicy.REQUIRED,
                "DEVICE_PROFILE_V1",
                "DEVICE_FINGERPRINT_V1"
            )),
            Set.of(StoreProvisioningModuleCode.DEVICES)
        );

        StoreProfileDescriptor replayed = registry.find("PROFILE_A", "1").orElseThrow();
        assertThat(replayed.profileFingerprint()).isEqualTo(registeredFingerprint);
        assertThat(replayed.composition().modules())
            .extracting(StoreProfileModuleReference::moduleCode)
            .containsExactly(StoreProvisioningModuleCode.MENU, StoreProvisioningModuleCode.TABLES);
    }

    private StoreProfileDescriptor profile(
        String profileCode,
        String profileVersion,
        List<StoreProfileModuleReference> modules
    ) {
        return new SyntheticProfile(profileCode, profileVersion, modules);
    }

    private List<StoreProfileModuleReference> modules() {
        return List.of(
            new StoreProfileModuleReference(
                StoreProvisioningModuleCode.MENU,
                "MENU_V1",
                StoreProfileModulePolicy.REQUIRED,
                "MENU_PROFILE_V1",
                "MENU_FINGERPRINT_V1"
            ),
            new StoreProfileModuleReference(
                StoreProvisioningModuleCode.TABLES,
                "TABLES_V1",
                StoreProfileModulePolicy.MANUAL_AFTER_CREATION,
                "TABLE_PROFILE_V1",
                "TABLE_FINGERPRINT_V1"
            )
        );
    }

    private static class SyntheticProfile implements StoreProfileDescriptor {

        private final String profileCode;
        private final String profileVersion;
        private final StoreProfileComposition composition;

        private SyntheticProfile(
            String profileCode,
            String profileVersion,
            List<StoreProfileModuleReference> modules
        ) {
            this.profileCode = profileCode;
            this.profileVersion = profileVersion;
            this.composition = new StoreProfileComposition(
                modules,
                Set.of(StoreProvisioningModuleCode.MENU)
            );
        }

        @Override
        public String profileCode() {
            return profileCode;
        }

        @Override
        public String profileVersion() {
            return profileVersion;
        }

        @Override
        public StoreProfileComposition composition() {
            return composition;
        }
    }

    private static final class MutableProfile implements StoreProfileDescriptor {

        private final String profileCode;
        private final String profileVersion;
        private StoreProfileComposition composition;

        private MutableProfile(
            String profileCode,
            String profileVersion,
            List<StoreProfileModuleReference> modules
        ) {
            this.profileCode = profileCode;
            this.profileVersion = profileVersion;
            this.composition = new StoreProfileComposition(
                modules,
                Set.of(StoreProvisioningModuleCode.MENU)
            );
        }

        @Override
        public String profileCode() {
            return profileCode;
        }

        @Override
        public String profileVersion() {
            return profileVersion;
        }

        @Override
        public StoreProfileComposition composition() {
            return composition;
        }
    }
}
