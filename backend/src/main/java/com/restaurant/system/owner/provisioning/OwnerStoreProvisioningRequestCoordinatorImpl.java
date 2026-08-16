package com.restaurant.system.owner.provisioning;

import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerStoreProvisioningRequestCoordinatorImpl implements OwnerStoreProvisioningRequestCoordinator {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String VALIDATION_PENDING = "PENDING";
    private static final String VALIDATION_PASS = "PASS";
    private static final String VALIDATION_WARNING = "WARNING";
    private static final String RESULT_CODE = "PHASE_B_STORE_PROVISIONED";
    private static final String ERROR_CODE = "PHASE_B_STORE_PROVISIONING_FAILED";
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final OwnerStoreProvisioningRequestRepository requestRepository;

    public OwnerStoreProvisioningRequestCoordinatorImpl(
        OwnerStoreProvisioningRequestRepository requestRepository
    ) {
        this.requestRepository = requestRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OwnerStoreProvisioningReservation reserve(ResolvedOwnerStoreProvisioningInput input) {
        validateInput(input);
        OwnerStoreProvisioningCommand command = input.command();
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        LocalDateTime now = LocalDateTime.now();
        int inserted = requestRepository.insertIfAbsent(
            command.organizationId(),
            idempotencyKey,
            input.requestFingerprint(),
            command.storeName().trim(),
            command.storeCode().trim(),
            command.profileCode(),
            command.profileVersion(),
            command.profileFingerprintSha256(),
            command.masterMenuKey(),
            command.masterMenuVersion(),
            command.masterMenuFingerprintSha256(),
            command.actor().userId(),
            now
        );

        OwnerStoreProvisioningRequestEntity request = requestRepository.findForUpdate(
            command.organizationId(),
            idempotencyKey
        ).orElseThrow(() -> new IllegalStateException("Provisioning request reservation was not created"));

        if (!input.requestFingerprint().equals(request.request_fingerprint)) {
            throw conflict("IDEMPOTENCY_CONFLICT", "This idempotency key was already used with different Store content");
        }
        if (STATUS_COMPLETED.equals(request.status)) {
            return reservation(request, true);
        }
        if (STATUS_FAILED.equals(request.status)) {
            throw conflict(
                "STORE_PROVISIONING_RETRY_REQUIRES_NEW_KEY",
                "The previous Store provisioning request failed and requires a new idempotency key"
            );
        }
        if (inserted == 0 || !STATUS_PROCESSING.equals(request.status)) {
            throw conflict("STORE_PROVISIONING_IN_PROGRESS", "This Store provisioning request is already processing");
        }
        return reservation(request, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OwnerStoreProvisioningReservation complete(OwnerStoreProvisioningSuccessEvidence evidence) {
        if (evidence == null || evidence.requestId() == null || evidence.storeId() == null || evidence.counts() == null) {
            throw badRequest("STORE_PROVISIONING_EVIDENCE_INVALID", "Provisioning success evidence is required");
        }
        OwnerStoreProvisioningRequestEntity request = lockRequest(evidence.requestId());
        if (!STATUS_PROCESSING.equals(request.status)) {
            throw conflict("STORE_PROVISIONING_STATE_INVALID", "Provisioning request is not processing");
        }
        if (!Objects.equals(request.organization_id, evidence.organizationId())
            || !Objects.equals(request.profile_code, evidence.profileCode())
            || !Objects.equals(request.profile_version, evidence.profileVersion())
            || !Objects.equals(request.profile_fingerprint_sha256, evidence.profileFingerprintSha256())
            || !Objects.equals(request.master_menu_key, evidence.masterMenuKey())
            || !Objects.equals(request.master_menu_version, evidence.masterMenuVersion())
            || !Objects.equals(request.master_menu_fingerprint_sha256, evidence.masterMenuFingerprintSha256())) {
            throw conflict("STORE_PROVISIONING_STATE_INVALID", "Provisioning completion scope does not match reservation");
        }

        String validationStatus = successfulValidationStatus(evidence.validationStatus());
        LocalDateTime now = LocalDateTime.now();
        request.status = STATUS_COMPLETED;
        request.store_id = evidence.storeId();
        request.validation_status = validationStatus;
        request.result_code = safeCode(evidence.resultCode(), RESULT_CODE);
        request.error_code = null;
        request.created_station_count = evidence.counts().stationCount();
        request.created_category_count = evidence.counts().categoryCount();
        request.created_item_count = evidence.counts().itemCount();
        request.created_option_count = evidence.counts().optionCount();
        request.created_pricing_policy_count = evidence.counts().pricingPolicyCount();
        request.created_combo_component_count = evidence.counts().comboComponentCount();
        request.created_printing_rule_count = evidence.counts().printingRuleCount();
        request.updated_at = now;
        request.completed_at = now;
        requestRepository.save(request);
        return reservation(request, false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OwnerStoreProvisioningReservation fail(OwnerStoreProvisioningFailureEvidence evidence) {
        if (evidence == null || evidence.requestId() == null) {
            throw badRequest("STORE_PROVISIONING_EVIDENCE_INVALID", "Provisioning request ID is required");
        }
        OwnerStoreProvisioningRequestEntity request = lockRequest(evidence.requestId());
        if (STATUS_COMPLETED.equals(request.status)) {
            throw conflict("STORE_PROVISIONING_STATE_INVALID", "Completed provisioning evidence cannot be replaced");
        }
        if (STATUS_FAILED.equals(request.status)) {
            return reservation(request, false);
        }

        LocalDateTime now = LocalDateTime.now();
        request.status = STATUS_FAILED;
        request.store_id = evidence.storeId();
        request.validation_status = "FAILED";
        request.result_code = null;
        request.error_code = safeCode(evidence.errorCode(), ERROR_CODE);
        request.updated_at = now;
        request.completed_at = now;
        requestRepository.save(request);
        return reservation(request, false);
    }

    private void validateInput(ResolvedOwnerStoreProvisioningInput input) {
        if (input == null || input.command() == null || input.command().actor() == null
            || input.command().actor().userId() == null || input.requestFingerprint() == null) {
            throw badRequest("STORE_PROVISIONING_REQUEST_INVALID", "Provisioning scope and actor are required");
        }
    }

    private OwnerStoreProvisioningRequestEntity lockRequest(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> conflict("STORE_PROVISIONING_RESULT_UNAVAILABLE", "Provisioning request evidence is unavailable"));
    }

    private OwnerStoreProvisioningReservation reservation(OwnerStoreProvisioningRequestEntity request, boolean replayed) {
        return new OwnerStoreProvisioningReservation(
            request.id,
            request.organization_id,
            request.store_id,
            request.store_name,
            request.store_code,
            request.profile_code,
            request.profile_version,
            request.profile_fingerprint_sha256,
            request.master_menu_key,
            request.master_menu_version,
            request.master_menu_fingerprint_sha256,
            request.status,
            replayed,
            request.validation_status == null ? VALIDATION_PENDING : request.validation_status,
            request.result_code,
            request.error_code,
            new OwnerStoreProvisioningCounts(
                value(request.created_station_count),
                value(request.created_category_count),
                value(request.created_item_count),
                value(request.created_option_count),
                value(request.created_pricing_policy_count),
                value(request.created_combo_component_count),
                value(request.created_printing_rule_count)
            )
        );
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 255) {
            throw badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key is required and must be at most 255 characters");
        }
        return normalized;
    }

    private String safeCode(String value, String fallback) {
        String normalized = value == null ? null : value.trim();
        return normalized != null && SAFE_CODE.matcher(normalized).matches() ? normalized : fallback;
    }

    private String successfulValidationStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (VALIDATION_PASS.equals(normalized) || VALIDATION_WARNING.equals(normalized)) {
            return normalized;
        }
        throw conflict(
            "STORE_PROVISIONING_VALIDATION_STATUS_INVALID",
            "Successful provisioning evidence must have PASS or WARNING validation status"
        );
    }

    private OwnerStoreProvisioningException badRequest(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.BAD_REQUEST, message);
    }

    private OwnerStoreProvisioningException conflict(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.CONFLICT, message);
    }
}
