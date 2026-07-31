package com.restaurant.system.staging.bootstrap;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningCommand;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningService;
import com.restaurant.system.owner.service.ProvisionedStoreStaff;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.staging.bootstrap.entity.StagingSyntheticBootstrapRequest;
import com.restaurant.system.staging.bootstrap.repository.StagingSyntheticBootstrapRequestRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile(StagingSyntheticBootstrapGuard.BOOTSTRAP_PROFILE)
public class StagingSyntheticBootstrapServiceImpl implements StagingSyntheticBootstrapService {

    private static final String OWNER_ROLE_CODE = "OWNER";
    private static final String REQUEST_PROCESSING = "PROCESSING";
    private static final String REQUEST_COMPLETED = "COMPLETED";
    private static final String RESULT_READY = "STG005_SYNTHETIC_BOOTSTRAP_READY";

    private final StagingSyntheticBootstrapGuard guard;
    private final StagingSyntheticBootstrapRequestRepository bootstrapRequestRepository;
    private final OrganizationRepository organizationRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final OnboardingStaffProvisioningService staffProvisioningService;

    public StagingSyntheticBootstrapServiceImpl(
        StagingSyntheticBootstrapGuard guard,
        StagingSyntheticBootstrapRequestRepository bootstrapRequestRepository,
        OrganizationRepository organizationRepository,
        StoreRepository storeRepository,
        UserRepository userRepository,
        UserCredentialRepository userCredentialRepository,
        OrganizationMembershipRepository organizationMembershipRepository,
        StoreMembershipRepository storeMembershipRepository,
        RoleRepository roleRepository,
        PasswordService passwordService,
        OnboardingStaffProvisioningService staffProvisioningService
    ) {
        this.guard = guard;
        this.bootstrapRequestRepository = bootstrapRequestRepository;
        this.organizationRepository = organizationRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
        this.staffProvisioningService = staffProvisioningService;
    }

    @Override
    @Transactional
    public StagingSyntheticBootstrapResult bootstrap(
        StagingSyntheticBootstrapSpec requestedSpec,
        String rawPassword
    ) {
        StagingSyntheticBootstrapSpec spec = normalize(requestedSpec);
        guard.validateSpec(spec);
        requirePassword(rawPassword);
        String fingerprint = fingerprint(spec);

        StagingSyntheticBootstrapRequest existing = bootstrapRequestRepository
            .findForUpdate(spec.runId())
            .orElse(null);
        if (existing != null) {
            return replay(existing, spec, fingerprint, rawPassword);
        }

        assertIdentifiersAvailable(spec);
        LocalDateTime now = LocalDateTime.now();
        StagingSyntheticBootstrapRequest bootstrapRequest = new StagingSyntheticBootstrapRequest();
        bootstrapRequest.runId = spec.runId();
        bootstrapRequest.requestFingerprint = fingerprint;
        bootstrapRequest.status = REQUEST_PROCESSING;
        bootstrapRequest.runtimeSha = spec.runtimeSha();
        bootstrapRequest.toolSha = spec.toolSha();
        bootstrapRequest.createdAt = now;
        bootstrapRequest.updatedAt = now;
        bootstrapRequest = bootstrapRequestRepository.saveAndFlush(bootstrapRequest);

        Organization organization = createOrganization(spec, now);
        Store sourceStore = createSourceStore(spec, organization.id, now);
        ProvisionedStoreStaff owner = staffProvisioningService.provision(
            new OnboardingStaffProvisioningCommand(
                organization.id,
                sourceStore.id,
                OWNER_ROLE_CODE,
                spec.ownerLoginIdentifier(),
                spec.ownerFullName(),
                rawPassword
            )
        );
        createOrganizationMembership(organization.id, owner.userId(), now);

        bootstrapRequest.organizationId = organization.id;
        bootstrapRequest.sourceStoreId = sourceStore.id;
        bootstrapRequest.ownerUserId = owner.userId();
        bootstrapRequest.status = REQUEST_COMPLETED;
        bootstrapRequest.resultCode = RESULT_READY;
        bootstrapRequest.errorCode = null;
        bootstrapRequest.completedAt = LocalDateTime.now();
        bootstrapRequest.updatedAt = bootstrapRequest.completedAt;
        bootstrapRequest = bootstrapRequestRepository.save(bootstrapRequest);

        return result(bootstrapRequest, false);
    }

    private StagingSyntheticBootstrapResult replay(
        StagingSyntheticBootstrapRequest request,
        StagingSyntheticBootstrapSpec spec,
        String fingerprint,
        String rawPassword
    ) {
        if (!fingerprint.equals(request.requestFingerprint)
            || !spec.runtimeSha().equals(request.runtimeSha)
            || !spec.toolSha().equals(request.toolSha)) {
            throw conflict();
        }
        if (!REQUEST_COMPLETED.equals(request.status)
            || request.organizationId == null
            || request.sourceStoreId == null
            || request.ownerUserId == null) {
            throw new StagingSyntheticBootstrapException(
                "STG005_BOOTSTRAP_RESULT_UNAVAILABLE",
                "Bootstrap result is incomplete and requires Owner review"
            );
        }

        Organization organization = organizationRepository.findById(request.organizationId)
            .orElseThrow(this::resultUnavailable);
        Store store = storeRepository.findById(request.sourceStoreId)
            .orElseThrow(this::resultUnavailable);
        User user = userRepository.findById(request.ownerUserId)
            .orElseThrow(this::resultUnavailable);
        UserCredential credential = userCredentialRepository
            .findFirstByLoginIdentifierIgnoreCase(spec.ownerLoginIdentifier())
            .filter(candidate -> request.ownerUserId.equals(candidate.userId))
            .orElseThrow(this::resultUnavailable);
        Role ownerRole = roleRepository.findFirstByCodeIgnoreCase(OWNER_ROLE_CODE)
            .orElseThrow(this::resultUnavailable);

        boolean topologyMatches = spec.organizationName().equals(organization.name)
            && spec.organizationCode().equalsIgnoreCase(organization.code)
            && "active".equalsIgnoreCase(organization.status)
            && request.organizationId.equals(store.organization_id)
            && spec.sourceStoreName().equals(store.name)
            && spec.sourceStoreCode().equalsIgnoreCase(store.code)
            && "active".equalsIgnoreCase(store.status)
            && Boolean.FALSE.equals(store.printing_enabled)
            && "DISABLED".equalsIgnoreCase(store.printing_mode)
            && request.sourceStoreId.equals(user.getStore_id())
            && ownerRole.getId().equals(user.getRole_id())
            && spec.ownerLoginIdentifier().equals(user.getUsername())
            && spec.ownerFullName().equals(user.getFull_name())
            && "active".equalsIgnoreCase(user.getStatus())
            && Boolean.TRUE.equals(credential.isActive)
            && "BCRYPT".equalsIgnoreCase(credential.passwordAlgorithm)
            && organizationMembershipRepository
                .findFirstByUserIdAndOrganizationId(user.getId(), organization.id)
                .filter(membership -> Boolean.TRUE.equals(membership.isActive))
                .map(membership -> membership.roleCode)
                .filter(OWNER_ROLE_CODE::equalsIgnoreCase)
                .isPresent()
            && storeMembershipRepository
                .findFirstByUserIdAndStoreId(user.getId(), store.id)
                .filter(membership -> Boolean.TRUE.equals(membership.isActive))
                .map(membership -> membership.roleCode)
                .filter(OWNER_ROLE_CODE::equalsIgnoreCase)
                .isPresent();
        if (!topologyMatches) {
            throw resultUnavailable();
        }
        if (!passwordService.matches(rawPassword, credential.passwordHash)) {
            throw conflict();
        }
        return result(request, true);
    }

    private Organization createOrganization(StagingSyntheticBootstrapSpec spec, LocalDateTime now) {
        Organization organization = new Organization();
        organization.name = spec.organizationName();
        organization.code = spec.organizationCode();
        organization.status = "active";
        organization.created_at = now;
        organization.updated_at = now;
        return organizationRepository.save(organization);
    }

    private Store createSourceStore(
        StagingSyntheticBootstrapSpec spec,
        Long organizationId,
        LocalDateTime now
    ) {
        Store store = new Store();
        store.organization_id = organizationId;
        store.name = spec.sourceStoreName();
        store.code = spec.sourceStoreCode();
        store.status = "active";
        store.enable_bar_kitchen_tasks = false;
        store.printing_enabled = false;
        store.printing_mode = "DISABLED";
        store.menu_revision = 1L;
        store.menu_updated_at = now;
        store.created_at = now;
        store.updated_at = now;
        return storeRepository.save(store);
    }

    private void createOrganizationMembership(Long organizationId, Long userId, LocalDateTime now) {
        Role ownerRole = roleRepository.findFirstByCodeIgnoreCase(OWNER_ROLE_CODE)
            .orElseThrow(() -> new StagingSyntheticBootstrapException(
                "STG005_BOOTSTRAP_OWNER_ROLE_MISSING",
                "OWNER role is required before synthetic bootstrap"
            ));
        OrganizationMembership membership = new OrganizationMembership();
        membership.organizationId = organizationId;
        membership.userId = userId;
        membership.roleId = ownerRole.getId();
        membership.roleCode = OWNER_ROLE_CODE;
        membership.isActive = true;
        membership.createdAt = now;
        membership.updatedAt = now;
        organizationMembershipRepository.save(membership);
    }

    private void assertIdentifiersAvailable(StagingSyntheticBootstrapSpec spec) {
        if (organizationRepository.findFirstByCodeIgnoreCase(spec.organizationCode()).isPresent()
            || !storeRepository.findAllByCodeIgnoreCase(spec.sourceStoreCode()).isEmpty()
            || userRepository.findFirstByUsernameIgnoreCase(spec.ownerLoginIdentifier()).isPresent()
            || userCredentialRepository.existsByLoginIdentifierIgnoreCase(spec.ownerLoginIdentifier())) {
            throw new StagingSyntheticBootstrapException(
                "STG005_BOOTSTRAP_IDENTIFIER_CONFLICT",
                "A synthetic bootstrap identifier already exists without this run record"
            );
        }
    }

    private StagingSyntheticBootstrapSpec normalize(StagingSyntheticBootstrapSpec spec) {
        if (spec == null) {
            return null;
        }
        return new StagingSyntheticBootstrapSpec(
            normalizeText(spec.runId()),
            normalizeText(spec.organizationName()),
            normalizeCode(spec.organizationCode()),
            normalizeText(spec.sourceStoreName()),
            normalizeCode(spec.sourceStoreCode()),
            normalizeText(spec.ownerLoginIdentifier()),
            normalizeText(spec.ownerFullName()),
            normalizeText(spec.runtimeSha()),
            normalizeText(spec.toolSha())
        );
    }

    private void requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 12 || rawPassword.length() > 256) {
            throw new StagingSyntheticBootstrapException(
                "STG005_BOOTSTRAP_PASSWORD_INVALID",
                "A runtime password between 12 and 256 characters is required"
            );
        }
    }

    private String fingerprint(StagingSyntheticBootstrapSpec spec) {
        String canonical = String.join(
            "\n",
            spec.runId(),
            spec.organizationName(),
            spec.organizationCode(),
            spec.sourceStoreName(),
            spec.sourceStoreCode(),
            spec.ownerLoginIdentifier(),
            spec.ownerFullName(),
            spec.runtimeSha(),
            spec.toolSha()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private StagingSyntheticBootstrapResult result(
        StagingSyntheticBootstrapRequest request,
        boolean replayed
    ) {
        return new StagingSyntheticBootstrapResult(
            request.id,
            request.runId,
            request.organizationId,
            request.sourceStoreId,
            request.ownerUserId,
            request.runtimeSha,
            request.toolSha,
            request.resultCode,
            replayed
        );
    }

    private StagingSyntheticBootstrapException conflict() {
        return new StagingSyntheticBootstrapException(
            "STG005_BOOTSTRAP_IDEMPOTENCY_CONFLICT",
            "This run ID was already used with different bootstrap content"
        );
    }

    private StagingSyntheticBootstrapException resultUnavailable() {
        return new StagingSyntheticBootstrapException(
            "STG005_BOOTSTRAP_RESULT_UNAVAILABLE",
            "Bootstrap result is incomplete and requires Owner review"
        );
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeCode(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
