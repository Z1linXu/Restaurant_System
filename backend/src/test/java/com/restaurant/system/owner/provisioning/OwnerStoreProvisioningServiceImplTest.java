package com.restaurant.system.owner.provisioning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.menu.addon.StoreAddonService;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.master.ChainMasterMenuCatalogService;
import com.restaurant.system.owner.master.ChainMasterMenuCategoryRepository;
import com.restaurant.system.owner.master.ChainMasterMenuOptionRepository;
import com.restaurant.system.owner.master.ChainMasterMenuProductRepository;
import com.restaurant.system.owner.master.ChainMasterMenuVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactRepository;
import com.restaurant.system.owner.profile.StoreProfileContractValidator;
import com.restaurant.system.owner.profile.StoreProfileRepository;
import com.restaurant.system.owner.profile.StoreProfileVersionRepository;
import com.restaurant.system.owner.profile.StoreProfileEntity;
import com.restaurant.system.owner.profile.StoreProfileVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileValidationResult;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
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
    @Mock private StoreAddonService storeAddonService;

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
            materializer,
            storeAddonService
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

    @Test
    void replayReturnsCurrentAddonConflictsWithoutRunningReconciliationAgain() {
        OwnerStoreProvisioningCommand command = command();
        StoreProfileEntity profile = new StoreProfileEntity();
        profile.id = 1L;
        profile.profile_code = command.profileCode();
        StoreProfileVersionEntity profileVersion = new StoreProfileVersionEntity();
        profileVersion.id = 2L;
        profileVersion.profile_version = command.profileVersion();
        profileVersion.schema_version = "STORE_PROFILE_CONTRACT_V1";
        profileVersion.status = "PUBLISHED";
        profileVersion.fingerprint_sha256 = command.profileFingerprintSha256();
        profileVersion.content_json = "{\"master_menu_reference\":{\"master_menu_key\":\"" + command.masterMenuKey()
            + "\",\"master_menu_version\":\"" + command.masterMenuVersion()
            + "\",\"fingerprint_sha256\":\"" + command.masterMenuFingerprintSha256() + "\"}}";
        when(profileRepository.findByProfileCode(command.profileCode())).thenReturn(Optional.of(profile));
        when(profileVersionRepository.findByProfileIdAndProfileVersion(1L, command.profileVersion()))
            .thenReturn(Optional.of(profileVersion));
        when(profileArtifactRepository.findAllByProfileVersionIdOrderByArtifactTypeAscArtifactCodeAsc(2L))
            .thenReturn(List.of());
        when(profileValidator.validate(any(), any(), any(), any(), any(), any()))
            .thenReturn(new StoreProfileValidationResult(true, command.profileFingerprintSha256(), List.of()));
        ChainMasterMenuVersionEntity masterVersion = new ChainMasterMenuVersionEntity();
        masterVersion.id = 3L;
        when(masterMenuCatalogService.requirePublishedVersion(command.organizationId(), command.masterMenuKey(),
            command.masterMenuVersion(), command.masterMenuFingerprintSha256())).thenReturn(masterVersion);
        when(requestCoordinator.reserve(any())).thenReturn(new OwnerStoreProvisioningReservation(
            99L, command.organizationId(), 44L, command.storeName(), command.storeCode(), command.profileCode(),
            command.profileVersion(), command.profileFingerprintSha256(), command.masterMenuKey(),
            command.masterMenuVersion(), command.masterMenuFingerprintSha256(), "COMPLETED", true, "PASS",
            "STORE_CREATED_LIVE", null, new OwnerStoreProvisioningCounts(0, 0, 0, 0, 0, 0, 0)));
        StoreAddonService.Conflict conflict = new StoreAddonService.Conflict("fried_egg", List.of(101L, 102L),
            List.of("煎蛋"), List.of("Fried Egg"), List.of(new BigDecimal("2.00"), new BigDecimal("3.00")), "VALUES_DIFFER");
        when(storeAddonService.getAddons(44L)).thenReturn(new StoreAddonService.AddonList(List.of(), List.of(conflict)));

        OwnerStoreProvisioningResult result = service.provision(command);

        assertThat(result.replayed()).isTrue();
        assertThat(result.addonConflicts()).containsExactly(conflict);
        verify(storeAddonService).getAddons(44L);
        verify(storeAddonService, never()).reconcile(any(), org.mockito.ArgumentMatchers.anyBoolean());
        verifyNoInteractions(materializer);
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
