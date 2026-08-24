package com.restaurant.system.owner.provisioning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.master.ChainMasterMenuCatalogService;
import com.restaurant.system.owner.master.ChainMasterMenuCategoryRepository;
import com.restaurant.system.owner.master.ChainMasterMenuOptionRepository;
import com.restaurant.system.owner.master.ChainMasterMenuProductRepository;
import com.restaurant.system.owner.profile.StoreProfileArtifactRepository;
import com.restaurant.system.owner.profile.StoreProfileContractValidator;
import com.restaurant.system.owner.profile.StoreProfileRepository;
import com.restaurant.system.owner.profile.StoreProfileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OwnerStoreProvisioningServiceImplTest {

    @Mock private FeatureFlagService featureFlagService;
    @Mock private PhaseBProvisioningRuntimeGate runtimeGate;
    @Mock private OwnerOrganizationAuthorizationService authorizationService;
    @Mock private StoreProfileRepository profileRepository;
    @Mock private StoreProfileVersionRepository profileVersionRepository;
    @Mock private StoreProfileArtifactRepository profileArtifactRepository;
    @Mock private StoreProfileContractValidator profileValidator;
    @Mock private ChainMasterMenuCatalogService masterMenuCatalogService;
    @Mock private ChainMasterMenuCategoryRepository masterCategoryRepository;
    @Mock private ChainMasterMenuProductRepository masterProductRepository;
    @Mock private ChainMasterMenuOptionRepository masterOptionRepository;
    @Mock private OwnerStoreProvisioningFingerprint fingerprintService;
    @Mock private OwnerStoreProvisioningRequestCoordinator requestCoordinator;
    @Mock private OwnerStoreProvisioningMaterializer materializer;

    private OwnerStoreProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OwnerStoreProvisioningServiceImpl(
            featureFlagService,
            runtimeGate,
            authorizationService,
            profileRepository,
            profileVersionRepository,
            profileArtifactRepository,
            profileValidator,
            masterMenuCatalogService,
            masterCategoryRepository,
            masterProductRepository,
            masterOptionRepository,
            fingerprintService,
            requestCoordinator,
            materializer
        );
    }

    @Test
    void serviceEnforcesPlatformAndRuntimeGatesBeforeAuthorizationOrProvisioningWork() {
        doThrow(new FeatureDisabledException(FeaturePackage.PLATFORM))
            .when(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provision(command()))
            .isInstanceOf(FeatureDisabledException.class);

        verify(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);
        verifyNoInteractions(
            runtimeGate,
            authorizationService,
            profileRepository,
            profileVersionRepository,
            profileArtifactRepository,
            masterMenuCatalogService,
            requestCoordinator,
            materializer
        );
    }

    @Test
    void serviceEnforcesRuntimeGateBeforeAuthorizationOrProvisioningWork() {
        doThrow(new OwnerStoreProvisioningException(
                "PHASE_B_PROVISIONING_DISABLED",
                HttpStatus.FORBIDDEN,
                "Phase B Store provisioning is not enabled in this runtime"
            ))
            .when(runtimeGate).requireEnabled();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provision(command()))
            .isInstanceOfSatisfying(OwnerStoreProvisioningException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                    .isEqualTo("PHASE_B_PROVISIONING_DISABLED"));

        verify(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);
        verify(runtimeGate).requireEnabled();
        verifyNoInteractions(
            authorizationService,
            profileRepository,
            profileVersionRepository,
            profileArtifactRepository,
            masterMenuCatalogService,
            requestCoordinator,
            materializer
        );
    }

    @Test
    void businessCreationSkipsStagingRuntimeGatesAndUsesOrganizationOwnerAuthorization() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provision(businessCommand()))
            .isInstanceOf(OwnerStoreProvisioningException.class)
            .hasMessageContaining("Store Profile not found");

        verify(featureFlagService, never()).requireEnabled(any());
        verify(runtimeGate, never()).requireEnabled();
        verify(authorizationService).requireActiveOwnerMembership(
            businessCommand().actor(),
            businessCommand().organizationId()
        );
    }

    private OwnerStoreProvisioningCommand command() {
        return new OwnerStoreProvisioningCommand(
            new AuthenticatedUser(20L, null, 1L, "owner", "Owner", "OWNER"),
            10L,
            "phase-b-key",
            "Phase B Validation Store",
            "PHASE_B_VALIDATION_STORE",
            "ST_DENIS_CANONICAL_PROFILE",
            "v2",
            "p".repeat(64),
            "LANZHOU_CHAIN_MASTER_MENU",
            "v1",
            "m".repeat(64)
        );
    }

    private OwnerStoreProvisioningCommand businessCommand() {
        OwnerStoreProvisioningCommand command = command();
        return new OwnerStoreProvisioningCommand(
            command.actor(),
            command.organizationId(),
            "business-key",
            "Business Store",
            "BUSINESS_STORE",
            command.profileCode(),
            command.profileVersion(),
            command.profileFingerprintSha256(),
            command.masterMenuKey(),
            command.masterMenuVersion(),
            command.masterMenuFingerprintSha256(),
            StoreProvisioningPurpose.BUSINESS
        );
    }
}
