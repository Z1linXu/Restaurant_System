package com.restaurant.system.owner.service.impl;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneCreatedCounts;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneRequest;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneResponse;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneValidationResponse;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneFailureEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneRequestCoordinator;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservation;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservationCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneService;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionResult;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneValidationCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneValidationResult;
import com.restaurant.system.owner.service.StoreMenuCloneTransactionService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Public orchestration only: authorize, reserve/replay, and delegate to the
 * existing read-only planner or lock-owning transaction service.
 */
@Service
public class OwnerStoreMenuCloneServiceImpl implements OwnerStoreMenuCloneService {

    private final OwnerOrganizationAuthorizationService ownerAuthorization;
    private final StoreRepository storeRepository;
    private final OwnerStoreMenuCloneRequestCoordinator requestCoordinator;
    private final StoreMenuCloneTransactionService transactionService;

    public OwnerStoreMenuCloneServiceImpl(
        OwnerOrganizationAuthorizationService ownerAuthorization,
        StoreRepository storeRepository,
        OwnerStoreMenuCloneRequestCoordinator requestCoordinator,
        StoreMenuCloneTransactionService transactionService
    ) {
        this.ownerAuthorization = ownerAuthorization;
        this.storeRepository = storeRepository;
        this.requestCoordinator = requestCoordinator;
        this.transactionService = transactionService;
    }

    @Override
    public OwnerStoreMenuCloneValidationResponse validateMenuClone(
        Long organizationId,
        Long targetStoreId,
        OwnerStoreMenuCloneRequest request,
        AuthenticatedUser actor
    ) {
        Scope scope = authorizeAndRequireScope(organizationId, targetStoreId, request, actor);
        OwnerStoreMenuCloneValidationResult result = transactionService.validate(
            new OwnerStoreMenuCloneValidationCommand(
                scope.organizationId(), scope.sourceStoreId(), scope.targetStoreId(), scope.profileCode()
            )
        );
        return validationResponse(result);
    }

    @Override
    public OwnerStoreMenuCloneResponse cloneMenu(
        Long organizationId,
        Long targetStoreId,
        String idempotencyKey,
        OwnerStoreMenuCloneRequest request,
        AuthenticatedUser actor
    ) {
        Scope scope = authorizeAndRequireScope(organizationId, targetStoreId, request, actor);
        OwnerStoreMenuCloneReservation reservation = requestCoordinator.reserve(
            new OwnerStoreMenuCloneReservationCommand(
                scope.organizationId(), scope.sourceStoreId(), scope.targetStoreId(),
                idempotencyKey, scope.profileCode(), actor.userId()
            )
        );
        if (reservation.replayed()) {
            return response(reservation);
        }

        try {
            OwnerStoreMenuCloneTransactionResult completed = transactionService.execute(new OwnerStoreMenuCloneTransactionCommand(
                reservation.requestId(), scope.organizationId(), scope.sourceStoreId(),
                scope.targetStoreId(), scope.profileCode(), actor.userId()
            ));
            return response(completed.evidence());
        } catch (RuntimeException exception) {
            requestCoordinator.fail(new OwnerStoreMenuCloneFailureEvidence(
                reservation.requestId(), null, null, safeFailureCode(exception)
            ));
            throw exception;
        }
    }

    private Scope authorizeAndRequireScope(
        Long organizationId,
        Long targetStoreId,
        OwnerStoreMenuCloneRequest request,
        AuthenticatedUser actor
    ) {
        if (organizationId == null || targetStoreId == null || request == null
            || request.source_store_id == null || !isExactNonBlank(request.profile_code)) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Organization, source Store, target Store, and exact profile code are required");
        }
        if (Objects.equals(request.source_store_id, targetStoreId)) {
            throw conflict("SOURCE_TARGET_SAME_STORE", "Source and target Stores must differ");
        }
        try {
            ownerAuthorization.requireSourceStoreInOrganization(actor, organizationId, request.source_store_id);
        } catch (ForbiddenException exception) {
            throw new OwnerStoreMenuCloneException(
                "MENU_CLONE_FORBIDDEN", HttpStatus.FORBIDDEN,
                "Owner, source Store, or Organization access is not available"
            );
        }
        Store target = storeRepository.findById(targetStoreId)
            .orElseThrow(() -> new OwnerStoreMenuCloneException(
                "TARGET_STORE_NOT_FOUND", HttpStatus.NOT_FOUND, "Target Store was not found"
            ));
        if (!Objects.equals(organizationId, target.organization_id)) {
            throw new OwnerStoreMenuCloneException(
                "MENU_CLONE_FORBIDDEN", HttpStatus.FORBIDDEN,
                "Target Store is not available for this Organization"
            );
        }
        return new Scope(organizationId, request.source_store_id, targetStoreId, request.profile_code);
    }

    private OwnerStoreMenuCloneValidationResponse validationResponse(OwnerStoreMenuCloneValidationResult result) {
        OwnerStoreMenuCloneValidationResponse response = new OwnerStoreMenuCloneValidationResponse();
        response.valid = result.valid();
        response.profile_code = result.profileCode();
        response.source_menu_revision = result.sourceMenuRevision();
        response.target_menu_revision = result.targetMenuRevision();
        response.expected = counts(
            result.expectedStationCount(), result.expectedCategoryCount(), result.expectedItemCount(), result.expectedOptionCount()
        );
        response.missing_codes = List.copyOf(result.missingCodes());
        response.duplicate_codes = List.copyOf(result.duplicateCodes());
        response.warnings = List.copyOf(result.warnings());
        return response;
    }

    private OwnerStoreMenuCloneResponse response(OwnerStoreMenuCloneReservation reservation) {
        OwnerStoreMenuCloneResponse response = new OwnerStoreMenuCloneResponse();
        response.clone_request_id = reservation.requestId();
        response.organization_id = reservation.organizationId();
        response.source_store_id = reservation.sourceStoreId();
        response.target_store_id = reservation.targetStoreId();
        response.profile_code = reservation.profileCode();
        response.source_menu_revision = reservation.sourceMenuRevision();
        response.target_revision_before = reservation.targetRevisionBefore();
        response.target_revision_after = reservation.targetRevisionAfter();
        response.status = reservation.status();
        response.replayed = reservation.replayed();
        response.created = counts(
            reservation.createdStationCount(), reservation.createdCategoryCount(),
            reservation.createdItemCount(), reservation.createdOptionCount()
        );
        response.result_code = reservation.resultCode();
        response.warnings = List.of();
        return response;
    }

    private OwnerStoreMenuCloneResponse response(OwnerStoreMenuCloneSuccessEvidence evidence) {
        OwnerStoreMenuCloneResponse response = new OwnerStoreMenuCloneResponse();
        response.clone_request_id = evidence.requestId();
        response.organization_id = evidence.organizationId();
        response.source_store_id = evidence.sourceStoreId();
        response.target_store_id = evidence.targetStoreId();
        response.profile_code = evidence.profileCode();
        response.source_menu_revision = evidence.sourceMenuRevision();
        response.target_revision_before = evidence.targetRevisionBefore();
        response.target_revision_after = evidence.targetRevisionAfter();
        response.status = "COMPLETED";
        response.replayed = false;
        response.created = counts(
            evidence.createdStationCount(), evidence.createdCategoryCount(),
            evidence.createdItemCount(), evidence.createdOptionCount()
        );
        response.result_code = evidence.resultCode();
        response.warnings = List.of();
        return response;
    }

    private OwnerStoreMenuCloneCreatedCounts counts(Integer stations, Integer categories, Integer items, Integer options) {
        return counts(
            stations == null ? 0 : stations, categories == null ? 0 : categories,
            items == null ? 0 : items, options == null ? 0 : options
        );
    }

    private OwnerStoreMenuCloneCreatedCounts counts(int stations, int categories, int items, int options) {
        OwnerStoreMenuCloneCreatedCounts counts = new OwnerStoreMenuCloneCreatedCounts();
        counts.stations = stations;
        counts.categories = categories;
        counts.items = items;
        counts.options = options;
        return counts;
    }

    private String safeFailureCode(RuntimeException exception) {
        return exception instanceof OwnerStoreMenuCloneException cloneException
            ? cloneException.getErrorCode()
            : "MENU_CLONE_FAILED";
    }

    private boolean isExactNonBlank(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private OwnerStoreMenuCloneException badRequest(String code, String message) {
        return new OwnerStoreMenuCloneException(code, HttpStatus.BAD_REQUEST, message);
    }

    private OwnerStoreMenuCloneException conflict(String code, String message) {
        return new OwnerStoreMenuCloneException(code, HttpStatus.CONFLICT, message);
    }

    private record Scope(Long organizationId, Long sourceStoreId, Long targetStoreId, String profileCode) {
    }
}
