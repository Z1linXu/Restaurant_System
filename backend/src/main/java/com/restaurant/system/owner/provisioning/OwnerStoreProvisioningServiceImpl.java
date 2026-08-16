package com.restaurant.system.owner.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.master.ChainMasterMenuCatalogService;
import com.restaurant.system.owner.master.ChainMasterMenuCategoryRepository;
import com.restaurant.system.owner.master.ChainMasterMenuOptionRepository;
import com.restaurant.system.owner.master.ChainMasterMenuProductRepository;
import com.restaurant.system.owner.master.ChainMasterMenuVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactInput;
import com.restaurant.system.owner.profile.StoreProfileArtifactRepository;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.owner.profile.StoreProfileContractValidator;
import com.restaurant.system.owner.profile.StoreProfileEntity;
import com.restaurant.system.owner.profile.StoreProfileRepository;
import com.restaurant.system.owner.profile.StoreProfileValidationResult;
import com.restaurant.system.owner.profile.StoreProfileVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileVersionRepository;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OwnerStoreProvisioningServiceImpl implements OwnerStoreProvisioningService {

    private static final String DEFAULT_PROFILE_CODE = "ST_DENIS_CANONICAL_PROFILE";
    private static final String DEFAULT_PROFILE_VERSION = "v2";
    private static final Pattern STORE_CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_:-]{1,127}");

    private final FeatureFlagService featureFlagService;
    private final PhaseBProvisioningRuntimeGate runtimeGate;
    private final OwnerOrganizationAuthorizationService authorizationService;
    private final StoreProfileRepository profileRepository;
    private final StoreProfileVersionRepository profileVersionRepository;
    private final StoreProfileArtifactRepository profileArtifactRepository;
    private final StoreProfileContractValidator profileValidator;
    private final ChainMasterMenuCatalogService masterMenuCatalogService;
    private final ChainMasterMenuCategoryRepository masterCategoryRepository;
    private final ChainMasterMenuProductRepository masterProductRepository;
    private final ChainMasterMenuOptionRepository masterOptionRepository;
    private final OwnerStoreProvisioningFingerprint fingerprintService;
    private final OwnerStoreProvisioningRequestCoordinator requestCoordinator;
    private final OwnerStoreProvisioningMaterializer materializer;

    public OwnerStoreProvisioningServiceImpl(
        FeatureFlagService featureFlagService,
        PhaseBProvisioningRuntimeGate runtimeGate,
        OwnerOrganizationAuthorizationService authorizationService,
        StoreProfileRepository profileRepository,
        StoreProfileVersionRepository profileVersionRepository,
        StoreProfileArtifactRepository profileArtifactRepository,
        StoreProfileContractValidator profileValidator,
        ChainMasterMenuCatalogService masterMenuCatalogService,
        ChainMasterMenuCategoryRepository masterCategoryRepository,
        ChainMasterMenuProductRepository masterProductRepository,
        ChainMasterMenuOptionRepository masterOptionRepository,
        OwnerStoreProvisioningFingerprint fingerprintService,
        OwnerStoreProvisioningRequestCoordinator requestCoordinator,
        OwnerStoreProvisioningMaterializer materializer
    ) {
        this.featureFlagService = featureFlagService;
        this.runtimeGate = runtimeGate;
        this.authorizationService = authorizationService;
        this.profileRepository = profileRepository;
        this.profileVersionRepository = profileVersionRepository;
        this.profileArtifactRepository = profileArtifactRepository;
        this.profileValidator = profileValidator;
        this.masterMenuCatalogService = masterMenuCatalogService;
        this.masterCategoryRepository = masterCategoryRepository;
        this.masterProductRepository = masterProductRepository;
        this.masterOptionRepository = masterOptionRepository;
        this.fingerprintService = fingerprintService;
        this.requestCoordinator = requestCoordinator;
        this.materializer = materializer;
    }

    @Override
    public OwnerStoreProvisioningResult provision(OwnerStoreProvisioningCommand command) {
        validateCommandBasics(command);
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        runtimeGate.requireEnabled();
        authorizationService.requireActiveOwnerMembership(command.actor(), command.organizationId());
        ResolvedOwnerStoreProvisioningInput input = resolve(command);
        OwnerStoreProvisioningReservation reservation = requestCoordinator.reserve(input);
        if (reservation.replayed()) {
            return toResult(reservation);
        }
        try {
            return materializer.materialize(reservation, input);
        } catch (RuntimeException exception) {
            requestCoordinator.fail(new OwnerStoreProvisioningFailureEvidence(
                reservation.requestId(),
                null,
                errorCode(exception)
            ));
            throw exception;
        }
    }

    private ResolvedOwnerStoreProvisioningInput resolve(OwnerStoreProvisioningCommand command) {
        validateCommandBasics(command);
        String profileCode = defaultIfBlank(command.profileCode(), DEFAULT_PROFILE_CODE);
        String profileVersionKey = defaultIfBlank(command.profileVersion(), DEFAULT_PROFILE_VERSION);
        String masterMenuKey = defaultIfBlank(
            command.masterMenuKey(),
            ChainMasterMenuCatalogService.INITIAL_MASTER_MENU_KEY
        );
        String masterMenuVersion = defaultIfBlank(
            command.masterMenuVersion(),
            ChainMasterMenuCatalogService.INITIAL_MASTER_MENU_VERSION
        );

        StoreProfileEntity profile = profileRepository.findByProfileCode(profileCode)
            .orElseThrow(() -> badRequest("STORE_PROFILE_NOT_FOUND", "Store Profile not found"));
        StoreProfileVersionEntity profileVersion = profileVersionRepository
            .findByProfileIdAndProfileVersion(profile.id, profileVersionKey)
            .orElseThrow(() -> badRequest("STORE_PROFILE_VERSION_NOT_FOUND", "Store Profile version not found"));
        List<StoreProfileArtifactEntity> artifacts = profileArtifactRepository
            .findAllByProfileVersionIdOrderByArtifactTypeAscArtifactCodeAsc(profileVersion.id);
        String profileFingerprint = defaultIfBlank(command.profileFingerprintSha256(), profileVersion.fingerprint_sha256);
        if (!Objects.equals(profileFingerprint, profileVersion.fingerprint_sha256)) {
            throw badRequest("STORE_PROFILE_FINGERPRINT_MISMATCH", "Store Profile fingerprint mismatch");
        }
        requireReadyProfile(profileVersion);
        validateProfile(profile, profileVersion, artifacts);

        String masterFingerprint = defaultIfBlank(
            command.masterMenuFingerprintSha256(),
            ChainMasterMenuCatalogService.INITIAL_MASTER_MENU_FINGERPRINT
        );
        ChainMasterMenuVersionEntity masterVersion = masterMenuCatalogService.requirePublishedVersion(
            command.organizationId(),
            masterMenuKey,
            masterMenuVersion,
            masterFingerprint
        );
        validateProfileMasterReference(profileVersion, masterMenuKey, masterMenuVersion, masterFingerprint);

        OwnerStoreProvisioningCommand resolvedCommand = new OwnerStoreProvisioningCommand(
            command.actor(),
            command.organizationId(),
            command.idempotencyKey(),
            command.storeName().trim(),
            command.storeCode().trim(),
            profileCode,
            profileVersionKey,
            profileFingerprint,
            masterMenuKey,
            masterMenuVersion,
            masterFingerprint
        );
        String requestFingerprint = fingerprintService.fingerprint(resolvedCommand);
        return new ResolvedOwnerStoreProvisioningInput(
            resolvedCommand,
            profileVersion,
            artifacts,
            masterVersion,
            masterCategoryRepository.findAllByVersionOrdered(masterVersion.id),
            masterProductRepository.findAllByVersionOrdered(masterVersion.id),
            masterOptionRepository.findAllByVersionOrdered(masterVersion.id),
            requestFingerprint
        );
    }

    private void validateCommandBasics(OwnerStoreProvisioningCommand command) {
        if (command == null || command.actor() == null || command.actor().userId() == null
            || command.organizationId() == null || isBlank(command.idempotencyKey())
            || isBlank(command.storeName()) || isBlank(command.storeCode())) {
            throw badRequest("STORE_PROVISIONING_REQUEST_INVALID", "Provisioning scope, Store identity and actor are required");
        }
        if (!STORE_CODE_PATTERN.matcher(command.storeCode().trim()).matches()) {
            throw badRequest("STORE_CODE_INVALID", "Store code must be exact, uppercase and URL-safe");
        }
    }

    private void requireReadyProfile(StoreProfileVersionEntity profileVersion) {
        if (!List.of("READY", "REVIEWED", "PUBLISHED").contains(profileVersion.status)) {
            throw badRequest("STORE_PROFILE_VERSION_NOT_READY", "Store Profile version is not ready");
        }
    }

    private void validateProfile(
        StoreProfileEntity profile,
        StoreProfileVersionEntity profileVersion,
        List<StoreProfileArtifactEntity> artifacts
    ) {
        List<StoreProfileArtifactInput> artifactInputs = artifacts.stream()
            .map(artifact -> new StoreProfileArtifactInput(
                artifact.artifact_type,
                artifact.artifact_code,
                artifact.artifact_version,
                artifact.content_json,
                artifact.fingerprint_sha256
            ))
            .toList();
        StoreProfileValidationResult validation = profileValidator.validate(
            profile.profile_code,
            profileVersion.profile_version,
            profileVersion.schema_version,
            profileVersion.content_json,
            profileVersion.fingerprint_sha256,
            artifactInputs
        );
        if (!validation.valid()) {
            throw badRequest("STORE_PROFILE_VALIDATION_FAILED", validation.issues().toString());
        }
    }

    private void validateProfileMasterReference(
        StoreProfileVersionEntity profileVersion,
        String masterMenuKey,
        String masterMenuVersion,
        String masterFingerprint
    ) {
        JsonNode reference = StoreProfileCanonicalJson.parse(profileVersion.content_json).path("master_menu_reference");
        if (!masterMenuKey.equals(reference.path("master_menu_key").asText(null))
            || !masterMenuVersion.equals(reference.path("master_menu_version").asText(null))
            || !masterFingerprint.equals(reference.path("fingerprint_sha256").asText(null))) {
            throw badRequest("STORE_PROFILE_MASTER_REFERENCE_MISMATCH", "Profile Master Menu reference mismatch");
        }
    }

    private OwnerStoreProvisioningResult toResult(OwnerStoreProvisioningReservation reservation) {
        return new OwnerStoreProvisioningResult(
            reservation.requestId(),
            reservation.storeId(),
            reservation.status(),
            reservation.replayed(),
            reservation.validationStatus(),
            reservation.resultCode(),
            reservation.errorCode(),
            reservation.counts()
        );
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof OwnerStoreProvisioningException provisioningException) {
            return provisioningException.getErrorCode();
        }
        return "PHASE_B_STORE_PROVISIONING_FAILED";
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private OwnerStoreProvisioningException badRequest(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.BAD_REQUEST, message);
    }
}
