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
public class StorePart2RequestCoordinator {

    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final StorePart2RequestRepository repository;
    private final ObjectMapper objectMapper;

    public StorePart2RequestCoordinator(StorePart2RequestRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Part2Reservation reserve(
        Long organizationId,
        Long storeId,
        String idempotencyKey,
        Part2ProvisioningPlan plan,
        Long actorUserId
    ) {
        String key = normalizeKey(idempotencyKey);
        LocalDateTime now = LocalDateTime.now();
        int inserted = repository.insertIfAbsent(
            organizationId,
            storeId,
            key,
            plan.fingerprint(),
            plan.sanitizedJson(),
            actorUserId,
            now
        );
        StoreProvisioningPart2RequestEntity request = repository.findForUpdate(organizationId, storeId, key)
            .orElseThrow(() -> conflict("PART2_RESERVATION_MISSING", "Part 2 request reservation was not created"));
        if (!plan.fingerprint().equals(request.request_fingerprint)) {
            throw conflict("PART2_IDEMPOTENCY_CONFLICT", "This idempotency key was used with different Part 2 content");
        }
        if (COMPLETED.equals(request.status)) {
            return new Part2Reservation(request, true);
        }
        if (FAILED.equals(request.status)) {
            throw conflict("PART2_RETRY_REQUIRES_NEW_KEY", "A failed Part 2 request requires a new idempotency key");
        }
        if (inserted == 0 || !PROCESSING.equals(request.status)) {
            throw conflict("PART2_REQUEST_IN_PROGRESS", "This Part 2 request is already processing");
        }
        return new Part2Reservation(request, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StoreProvisioningPart2RequestEntity complete(
        Long requestId,
        Long evidenceId,
        StorePart2ProvisioningWriter.WriteResult result
    ) {
        StoreProvisioningPart2RequestEntity request = lock(requestId);
        if (!PROCESSING.equals(request.status)) {
            throw conflict("PART2_REQUEST_STATE_INVALID", "Part 2 request is not processing");
        }
        LocalDateTime now = LocalDateTime.now();
        request.status = COMPLETED;
        request.result_code = "PHASE_B_PART2_PROVISIONED";
        request.error_code = null;
        request.readiness_evidence_id = evidenceId;
        request.result_json = resultJson(result);
        request.updated_at = now;
        request.completed_at = now;
        return repository.save(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoreProvisioningPart2RequestEntity fail(Long requestId, String errorCode) {
        StoreProvisioningPart2RequestEntity request = lock(requestId);
        if (COMPLETED.equals(request.status)) {
            throw conflict("PART2_REQUEST_STATE_INVALID", "Completed Part 2 evidence cannot be replaced");
        }
        if (FAILED.equals(request.status)) {
            return request;
        }
        LocalDateTime now = LocalDateTime.now();
        request.status = FAILED;
        request.error_code = safeCode(errorCode, "PHASE_B_PART2_PROVISIONING_FAILED");
        request.updated_at = now;
        request.completed_at = now;
        return repository.save(request);
    }

    private StoreProvisioningPart2RequestEntity lock(Long requestId) {
        return repository.findByIdForUpdate(requestId)
            .orElseThrow(() -> conflict("PART2_RESULT_UNAVAILABLE", "Part 2 request evidence is unavailable"));
    }

    private String resultJson(StorePart2ProvisioningWriter.WriteResult result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "station_count", result.stationCount(),
                "table_count", result.tableCount(),
                "staff_count", result.staffCount(),
                "printer_role_count", result.printerRoleCount(),
                "device_count", result.deviceCount()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Part 2 result evidence cannot be serialized", exception);
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
