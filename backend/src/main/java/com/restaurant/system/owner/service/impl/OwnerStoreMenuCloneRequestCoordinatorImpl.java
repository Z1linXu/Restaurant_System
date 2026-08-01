package com.restaurant.system.owner.service.impl;

import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneFailureEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneRequestCoordinator;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservation;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservationCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.platform.entity.OwnerStoreMenuCloneRequest;
import com.restaurant.system.platform.repository.OwnerStoreMenuCloneRequestRepository;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerStoreMenuCloneRequestCoordinatorImpl implements OwnerStoreMenuCloneRequestCoordinator {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_RESULT_CODE = "MENU_CLONE_COMPLETED";
    private static final String DEFAULT_ERROR_CODE = "MENU_CLONE_FAILED";
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final OwnerStoreMenuCloneRequestRepository requestRepository;
    private final OwnerStoreMenuCloneFingerprint fingerprintService;

    public OwnerStoreMenuCloneRequestCoordinatorImpl(
        OwnerStoreMenuCloneRequestRepository requestRepository,
        OwnerStoreMenuCloneFingerprint fingerprintService
    ) {
        this.requestRepository = requestRepository;
        this.fingerprintService = fingerprintService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OwnerStoreMenuCloneReservation reserve(OwnerStoreMenuCloneReservationCommand command) {
        validateCommand(command);
        String normalizedKey = normalizeIdempotencyKey(command.idempotencyKey());
        String fingerprint = fingerprintService.fingerprint(command);
        LocalDateTime now = LocalDateTime.now();

        int inserted = requestRepository.insertIfAbsent(
            command.organizationId(),
            command.sourceStoreId(),
            command.targetStoreId(),
            normalizedKey,
            fingerprint,
            command.profileCode(),
            command.actorUserId(),
            now
        );

        OwnerStoreMenuCloneRequest request = requestRepository.findForUpdate(
            command.organizationId(),
            command.sourceStoreId(),
            command.targetStoreId(),
            normalizedKey
        ).orElseThrow(() -> new IllegalStateException("Menu clone request reservation was not created"));

        if (!fingerprint.equals(request.requestFingerprint)) {
            throw conflict(
                "IDEMPOTENCY_CONFLICT",
                "This idempotency key was already used with different menu clone content"
            );
        }
        if (STATUS_COMPLETED.equals(request.status)) {
            return reservation(request, true);
        }
        if (STATUS_FAILED.equals(request.status)) {
            throw conflict(
                "MENU_CLONE_RETRY_REQUIRES_VALIDATION",
                "The previous menu clone failed and must be revalidated before retry"
            );
        }
        if (inserted == 0 || !STATUS_PROCESSING.equals(request.status)) {
            throw conflict("MENU_CLONE_IN_PROGRESS", "This menu clone request is already being processed");
        }
        return reservation(request, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OwnerStoreMenuCloneReservation complete(OwnerStoreMenuCloneSuccessEvidence evidence) {
        validateSuccessEvidence(evidence);
        OwnerStoreMenuCloneRequest request = lockRequest(evidence.requestId());
        if (!STATUS_PROCESSING.equals(request.status)) {
            throw conflict("MENU_CLONE_STATE_INVALID", "Menu clone request is not processing");
        }

        LocalDateTime now = LocalDateTime.now();
        request.status = STATUS_COMPLETED;
        request.sourceMenuRevision = evidence.sourceMenuRevision();
        request.targetRevisionBefore = evidence.targetRevisionBefore();
        request.targetRevisionAfter = evidence.targetRevisionAfter();
        request.createdStationCount = evidence.createdStationCount();
        request.createdCategoryCount = evidence.createdCategoryCount();
        request.createdItemCount = evidence.createdItemCount();
        request.createdOptionCount = evidence.createdOptionCount();
        request.resultCode = safeCode(evidence.resultCode(), DEFAULT_RESULT_CODE);
        request.errorCode = null;
        request.updatedAt = now;
        request.completedAt = now;
        requestRepository.save(request);
        return reservation(request, false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OwnerStoreMenuCloneReservation fail(OwnerStoreMenuCloneFailureEvidence evidence) {
        if (evidence == null || evidence.requestId() == null) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Menu clone request ID is required");
        }
        OwnerStoreMenuCloneRequest request = lockRequest(evidence.requestId());
        if (STATUS_COMPLETED.equals(request.status)) {
            throw conflict("MENU_CLONE_STATE_INVALID", "Completed menu clone evidence cannot be replaced");
        }
        if (STATUS_FAILED.equals(request.status)) {
            return reservation(request, false);
        }

        LocalDateTime now = LocalDateTime.now();
        request.status = STATUS_FAILED;
        request.sourceMenuRevision = evidence.sourceMenuRevision();
        request.targetRevisionBefore = evidence.targetRevisionBefore();
        request.targetRevisionAfter = null;
        request.createdStationCount = null;
        request.createdCategoryCount = null;
        request.createdItemCount = null;
        request.createdOptionCount = null;
        request.resultCode = null;
        request.errorCode = safeCode(evidence.errorCode(), DEFAULT_ERROR_CODE);
        request.updatedAt = now;
        request.completedAt = now;
        requestRepository.save(request);
        return reservation(request, false);
    }

    private void validateCommand(OwnerStoreMenuCloneReservationCommand command) {
        if (command == null
            || command.organizationId() == null
            || command.sourceStoreId() == null
            || command.targetStoreId() == null
            || command.actorUserId() == null) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Menu clone scope and actor are required");
        }
        if (!ChinatownMenuCloneProfile.SOURCE_STORE_ID.equals(command.sourceStoreId())) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "The reviewed profile requires source Store 1");
        }
        if (command.sourceStoreId().equals(command.targetStoreId())) {
            throw conflict("SOURCE_TARGET_SAME_STORE", "Source and target stores must differ");
        }
        if (!ChinatownMenuCloneProfile.PROFILE_CODE.equals(command.profileCode())) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Unsupported menu clone profile");
        }
    }

    private void validateSuccessEvidence(OwnerStoreMenuCloneSuccessEvidence evidence) {
        if (evidence == null
            || evidence.requestId() == null
            || evidence.sourceMenuRevision() == null
            || evidence.targetRevisionBefore() == null
            || evidence.targetRevisionAfter() == null) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Complete menu clone evidence is required");
        }
        if (evidence.targetRevisionAfter() != evidence.targetRevisionBefore() + 1) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Target menu revision must increment exactly once");
        }
        requireNonNegative(evidence.createdStationCount(), "station");
        requireNonNegative(evidence.createdCategoryCount(), "category");
        requireNonNegative(evidence.createdItemCount(), "item");
        requireNonNegative(evidence.createdOptionCount(), "option");
    }

    private void requireNonNegative(int value, String label) {
        if (value < 0) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Created " + label + " count cannot be negative");
        }
    }

    private OwnerStoreMenuCloneRequest lockRequest(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> conflict("MENU_CLONE_RESULT_UNAVAILABLE", "Menu clone request evidence is unavailable"));
    }

    private OwnerStoreMenuCloneReservation reservation(OwnerStoreMenuCloneRequest request, boolean replayed) {
        return new OwnerStoreMenuCloneReservation(
            request.id,
            request.organizationId,
            request.sourceStoreId,
            request.targetStoreId,
            request.profileCode,
            request.status,
            replayed,
            request.sourceMenuRevision,
            request.targetRevisionBefore,
            request.targetRevisionAfter,
            request.createdStationCount,
            request.createdCategoryCount,
            request.createdItemCount,
            request.createdOptionCount,
            request.resultCode,
            request.errorCode
        );
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 255) {
            throw badRequest(
                "IDEMPOTENCY_KEY_INVALID",
                "Idempotency-Key is required and must be at most 255 characters"
            );
        }
        return normalized;
    }

    private String safeCode(String value, String fallback) {
        String normalized = value == null ? null : value.trim();
        return normalized != null && SAFE_CODE.matcher(normalized).matches() ? normalized : fallback;
    }

    private OwnerStoreMenuCloneException badRequest(String code, String message) {
        return new OwnerStoreMenuCloneException(code, HttpStatus.BAD_REQUEST, message);
    }

    private OwnerStoreMenuCloneException conflict(String code, String message) {
        return new OwnerStoreMenuCloneException(code, HttpStatus.CONFLICT, message);
    }
}
