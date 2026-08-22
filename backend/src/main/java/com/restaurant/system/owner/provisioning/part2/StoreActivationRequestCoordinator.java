package com.restaurant.system.owner.provisioning.part2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreActivationRequestCoordinator {

    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final StoreActivationRequestRepository repository;
    private final ObjectMapper objectMapper;

    public StoreActivationRequestCoordinator(StoreActivationRequestRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActivationReservation reserve(
        Long organizationId,
        Long storeId,
        String idempotencyKey,
        String requestFingerprint,
        String expectedReadinessFingerprint,
        Long actorUserId
    ) {
        String key = normalizeKey(idempotencyKey);
        LocalDateTime now = LocalDateTime.now();
        int inserted = repository.insertIfAbsent(
            organizationId,
            storeId,
            key,
            requestFingerprint,
            expectedReadinessFingerprint,
            actorUserId,
            now
        );
        StoreActivationRequestEntity request = repository.findForUpdate(organizationId, storeId, key)
            .orElseThrow(() -> conflict("ACTIVATION_RESERVATION_MISSING", "Activation request reservation was not created"));
        if (!requestFingerprint.equals(request.request_fingerprint)) {
            throw conflict("ACTIVATION_IDEMPOTENCY_CONFLICT", "This activation key was used with different content");
        }
        if (COMPLETED.equals(request.status)) {
            return new ActivationReservation(request, true);
        }
        if (FAILED.equals(request.status)) {
            throw conflict("ACTIVATION_RETRY_REQUIRES_NEW_KEY", "A failed activation requires a new idempotency key");
        }
        if (inserted == 0 || !PROCESSING.equals(request.status)) {
            throw conflict("ACTIVATION_IN_PROGRESS", "This activation request is already processing");
        }
        return new ActivationReservation(request, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StoreActivationRequestEntity complete(
        Long requestId,
        Long evidenceId,
        String resultCode,
        String targetState
    ) {
        StoreActivationRequestEntity request = lock(requestId);
        if (!PROCESSING.equals(request.status)) {
            throw conflict("ACTIVATION_STATE_INVALID", "Activation request is not processing");
        }
        LocalDateTime now = LocalDateTime.now();
        request.status = COMPLETED;
        request.target_state = targetState;
        request.readiness_evidence_id = evidenceId;
        request.result_code = safeCode(resultCode, "PHASE_B_PART2_ACTIVATED");
        request.error_code = null;
        request.result_json = json(Map.of("target_state", targetState, "result_code", request.result_code));
        request.updated_at = now;
        request.completed_at = now;
        return repository.save(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoreActivationRequestEntity fail(Long requestId, String errorCode) {
        StoreActivationRequestEntity request = lock(requestId);
        if (COMPLETED.equals(request.status)) {
            throw conflict("ACTIVATION_STATE_INVALID", "Completed activation evidence cannot be replaced");
        }
        if (FAILED.equals(request.status)) {
            return request;
        }
        LocalDateTime now = LocalDateTime.now();
        request.status = FAILED;
        request.error_code = safeCode(errorCode, "PHASE_B_PART2_ACTIVATION_FAILED");
        request.updated_at = now;
        request.completed_at = now;
        return repository.save(request);
    }

    private StoreActivationRequestEntity lock(Long requestId) {
        return repository.findByIdForUpdate(requestId)
            .orElseThrow(() -> conflict("ACTIVATION_RESULT_UNAVAILABLE", "Activation evidence is unavailable"));
    }

    private String json(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Activation result evidence cannot be serialized", exception);
        }
    }

    private String normalizeKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 255) {
            throw conflict("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key is required and must be at most 255 characters");
        }
        return normalized;
    }

    private String safeCode(String value, String fallback) {
        String normalized = value == null ? null : value.trim().toUpperCase();
        return normalized != null && SAFE_CODE.matcher(normalized).matches() ? normalized : fallback;
    }

    private OwnerStoreProvisioningException conflict(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.CONFLICT, message);
    }
}
