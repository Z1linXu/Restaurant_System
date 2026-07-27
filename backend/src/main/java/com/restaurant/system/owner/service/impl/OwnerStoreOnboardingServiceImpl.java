package com.restaurant.system.owner.service.impl;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingResponse;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingStaffRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingStaffResponse;
import com.restaurant.system.owner.exception.OwnerStoreOnboardingException;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningCommand;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningService;
import com.restaurant.system.owner.service.OwnerStoreOnboardingService;
import com.restaurant.system.owner.service.ProvisionedStoreStaff;
import com.restaurant.system.platform.repository.OwnerStoreOnboardingRequestRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerStoreOnboardingServiceImpl implements OwnerStoreOnboardingService {

    private static final String REQUEST_PROCESSING = "PROCESSING";
    private static final String REQUEST_COMPLETED = "COMPLETED";
    private static final String STORE_STATUS_INACTIVE = "inactive";
    private static final String RESULT_PENDING_CONFIGURATION = "PENDING_MENU_AND_PRINT_CONFIGURATION";
    private static final Set<String> SUPPORTED_STAFF_ROLES = Set.of("MANAGER", "FRONTDESK");

    private final OwnerOrganizationAuthorizationService ownerOrganizationAuthorizationService;
    private final OwnerStoreOnboardingRequestRepository onboardingRequestRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordService passwordService;
    private final OnboardingStaffProvisioningService staffProvisioningService;

    public OwnerStoreOnboardingServiceImpl(
        OwnerOrganizationAuthorizationService ownerOrganizationAuthorizationService,
        OwnerStoreOnboardingRequestRepository onboardingRequestRepository,
        StoreRepository storeRepository,
        UserRepository userRepository,
        UserCredentialRepository userCredentialRepository,
        PasswordService passwordService,
        OnboardingStaffProvisioningService staffProvisioningService
    ) {
        this.ownerOrganizationAuthorizationService = ownerOrganizationAuthorizationService;
        this.onboardingRequestRepository = onboardingRequestRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.passwordService = passwordService;
        this.staffProvisioningService = staffProvisioningService;
    }

    @Override
    @Transactional
    public OwnerStoreOnboardingResponse onboard(
        Long organizationId,
        String idempotencyKey,
        OwnerStoreOnboardingRequest request,
        AuthenticatedUser actor
    ) {
        requireRequest(organizationId, request);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(organizationId, request);

        // Verify owner and source scope before writing an idempotency record.
        ownerOrganizationAuthorizationService.requireSourceStoreInOrganization(
            actor,
            organizationId,
            request.source_store_id
        );

        LocalDateTime now = LocalDateTime.now();
        int inserted = onboardingRequestRepository.insertIfAbsent(
            organizationId,
            normalizedKey,
            requestFingerprint,
            now
        );
        com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest onboardingRequest = onboardingRequestRepository
            .findForUpdate(organizationId, normalizedKey)
            .orElseThrow(() -> new IllegalStateException("Onboarding idempotency record was not created"));

        if (!requestFingerprint.equals(onboardingRequest.requestFingerprint)) {
            throw conflict(
                "IDEMPOTENCY_CONFLICT",
                "This idempotency key was already used with different onboarding content"
            );
        }
        if (REQUEST_COMPLETED.equals(onboardingRequest.status) && onboardingRequest.storeId != null) {
            Store existingStore = storeRepository.findById(onboardingRequest.storeId)
                .orElseThrow(() -> conflict("ONBOARDING_RESULT_UNAVAILABLE", "Completed onboarding result is unavailable"));
            ensureReplayPasswordsMatch(existingStore.id, request.staff);
            return response(onboardingRequest, existingStore, responseStaff(existingStaff(request.staff)), request.source_store_id, true);
        }
        if (inserted == 0 || !REQUEST_PROCESSING.equals(onboardingRequest.status)) {
            throw conflict("ONBOARDING_REQUEST_IN_PROGRESS", "This onboarding request is already being processed");
        }

        assertStoreCodeAvailable(organizationId, request.store_code);
        Store store = createInactiveStore(organizationId, request, now);
        List<ProvisionedStoreStaff> staff = provisionStaff(organizationId, store.id, request.staff);

        onboardingRequest.storeId = store.id;
        onboardingRequest.status = REQUEST_COMPLETED;
        onboardingRequest.resultCode = RESULT_PENDING_CONFIGURATION;
        onboardingRequest.errorCode = null;
        onboardingRequest.completedAt = LocalDateTime.now();
        onboardingRequest.updatedAt = onboardingRequest.completedAt;
        onboardingRequestRepository.save(onboardingRequest);

        return response(onboardingRequest, store, responseStaff(staff), request.source_store_id, false);
    }

    private void requireRequest(Long organizationId, OwnerStoreOnboardingRequest request) {
        if (organizationId == null || request == null) {
            throw badRequest("ONBOARDING_REQUEST_INVALID", "Organization and onboarding request are required");
        }
        if (request.source_store_id == null) {
            throw badRequest("ONBOARDING_REQUEST_INVALID", "Source store is required");
        }
        if (normalizeText(request.store_name) == null || normalizeText(request.store_code) == null) {
            throw badRequest("ONBOARDING_REQUEST_INVALID", "Store name and code are required");
        }
        if (request.staff == null || request.staff.isEmpty()) {
            throw badRequest("ONBOARDING_REQUEST_INVALID", "At least one staff account is required");
        }

        Set<String> loginIdentifiers = new HashSet<>();
        for (OwnerStoreOnboardingStaffRequest staff : request.staff) {
            if (staff == null) {
                throw badRequest("ONBOARDING_STAFF_INVALID", "Staff account is required");
            }
            String loginIdentifier = normalizeText(staff.login_identifier);
            String roleCode = normalizeRole(staff.role_code);
            if (loginIdentifier == null || normalizeText(staff.initial_password) == null) {
                throw badRequest("ONBOARDING_STAFF_INVALID", "Staff login and password are required");
            }
            if (!SUPPORTED_STAFF_ROLES.contains(roleCode)) {
                throw badRequest("ONBOARDING_STAFF_ROLE_INVALID", "Only MANAGER and FRONTDESK staff roles are supported");
            }
            if (!loginIdentifiers.add(loginIdentifier.toLowerCase(Locale.ROOT))) {
                throw badRequest("ONBOARDING_STAFF_INVALID", "Staff login identifiers must be unique");
            }
        }
    }

    private void assertStoreCodeAvailable(Long organizationId, String requestedCode) {
        String normalizedCode = normalizeCode(requestedCode);
        boolean alreadyExists = storeRepository.findAllByOrganizationIdOrderByIdAsc(organizationId).stream()
            .map(store -> normalizeCode(store.code))
            .anyMatch(normalizedCode::equals);
        if (alreadyExists) {
            throw conflict("STORE_CODE_CONFLICT", "A store with this code already exists in the organization");
        }
    }

    private Store createInactiveStore(
        Long organizationId,
        OwnerStoreOnboardingRequest request,
        LocalDateTime now
    ) {
        Store store = new Store();
        store.organization_id = organizationId;
        store.name = normalizeText(request.store_name);
        store.code = normalizeCode(request.store_code);
        // Menu, printer assignments, and devices are configured in later approved loops.
        store.status = STORE_STATUS_INACTIVE;
        store.enable_bar_kitchen_tasks = false;
        store.printing_enabled = false;
        store.printing_mode = "DISABLED";
        store.created_at = now;
        store.updated_at = now;
        return storeRepository.save(store);
    }

    private List<ProvisionedStoreStaff> provisionStaff(
        Long organizationId,
        Long storeId,
        List<OwnerStoreOnboardingStaffRequest> staffRequests
    ) {
        List<ProvisionedStoreStaff> staff = new ArrayList<>();
        for (OwnerStoreOnboardingStaffRequest staffRequest : staffRequests) {
            staff.add(staffProvisioningService.provision(new OnboardingStaffProvisioningCommand(
                organizationId,
                storeId,
                normalizeRole(staffRequest.role_code),
                normalizeText(staffRequest.login_identifier),
                normalizeText(staffRequest.full_name),
                staffRequest.initial_password
            )));
        }
        return staff;
    }

    private void ensureReplayPasswordsMatch(Long storeId, List<OwnerStoreOnboardingStaffRequest> staffRequests) {
        for (OwnerStoreOnboardingStaffRequest staffRequest : staffRequests) {
            String loginIdentifier = normalizeText(staffRequest.login_identifier);
            User user = userRepository.findFirstByUsernameIgnoreCase(loginIdentifier)
                .filter(candidate -> storeId.equals(candidate.getStore_id()))
                .orElseThrow(() -> conflict("ONBOARDING_RESULT_UNAVAILABLE", "Completed onboarding staff result is unavailable"));
            UserCredential credential = userCredentialRepository.findFirstByLoginIdentifierIgnoreCase(loginIdentifier)
                .filter(candidate -> user.getId().equals(candidate.userId))
                .orElseThrow(() -> conflict("ONBOARDING_RESULT_UNAVAILABLE", "Completed onboarding credential result is unavailable"));
            if (!passwordService.matches(staffRequest.initial_password, credential.passwordHash)) {
                throw conflict(
                    "IDEMPOTENCY_CONFLICT",
                    "This idempotency key was already used with different onboarding content"
                );
            }
        }
    }

    private List<ProvisionedStoreStaff> existingStaff(List<OwnerStoreOnboardingStaffRequest> staffRequests) {
        return staffRequests.stream()
            .map(staffRequest -> userRepository.findFirstByUsernameIgnoreCase(normalizeText(staffRequest.login_identifier))
                .map(user -> new ProvisionedStoreStaff(
                    user.getId(),
                    user.getStore_id(),
                    user.getUsername(),
                    normalizeRole(staffRequest.role_code)
                ))
                .orElseThrow(() -> conflict("ONBOARDING_RESULT_UNAVAILABLE", "Completed onboarding staff result is unavailable")))
            .toList();
    }

    private OwnerStoreOnboardingResponse response(
        com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest onboardingRequest,
        Store store,
        List<OwnerStoreOnboardingStaffResponse> staff,
        Long sourceStoreId,
        boolean replayed
    ) {
        OwnerStoreOnboardingResponse response = new OwnerStoreOnboardingResponse();
        response.onboarding_request_id = onboardingRequest.id;
        response.organization_id = onboardingRequest.organizationId;
        response.source_store_id = sourceStoreId;
        response.store_id = store.id;
        response.store_name = store.name;
        response.store_code = store.code;
        response.store_status = store.status;
        response.onboarding_status = onboardingRequest.status;
        response.result_code = onboardingRequest.resultCode;
        response.replayed = replayed;
        response.staff = staff;
        return response;
    }

    private List<OwnerStoreOnboardingStaffResponse> responseStaff(List<ProvisionedStoreStaff> staff) {
        return staff.stream().map(provisioned -> {
            OwnerStoreOnboardingStaffResponse response = new OwnerStoreOnboardingStaffResponse();
            response.user_id = provisioned.userId();
            response.login_identifier = provisioned.loginIdentifier();
            response.role_code = provisioned.roleCode();
            return response;
        }).toList();
    }

    private String fingerprint(Long organizationId, OwnerStoreOnboardingRequest request) {
        List<String> staff = request.staff.stream()
            .map(staffRequest -> String.join(
                "|",
                normalizeText(staffRequest.login_identifier),
                normalizeText(staffRequest.full_name) == null ? "" : normalizeText(staffRequest.full_name),
                normalizeRole(staffRequest.role_code)
            ))
            .sorted(Comparator.naturalOrder())
            .toList();
        String canonical = String.join(
            "\n",
            "organization=" + organizationId,
            "sourceStore=" + request.source_store_id,
            "storeName=" + normalizeText(request.store_name),
            "storeCode=" + normalizeCode(request.store_code),
            "staff=" + String.join(";", staff)
        );
        return sha256(canonical);
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                hex.append(String.format("%02x", valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = normalizeText(value);
        if (normalized == null || normalized.length() > 255) {
            throw badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key is required and must be at most 255 characters");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeRole(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private OwnerStoreOnboardingException badRequest(String code, String message) {
        return new OwnerStoreOnboardingException(code, HttpStatus.BAD_REQUEST, message);
    }

    private OwnerStoreOnboardingException conflict(String code, String message) {
        return new OwnerStoreOnboardingException(code, HttpStatus.CONFLICT, message);
    }
}
