package com.restaurant.system.owner.provisioning.part2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.PrintingMode;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorePart2ProvisioningServiceImpl implements StorePart2ProvisioningService {

    private final StoreRepository storeRepository;
    private final Part2PlanNormalizer planNormalizer;
    private final StorePart2RequestCoordinator requestCoordinator;
    private final StorePart2ProvisioningWriter writer;
    private final StoreReadinessService readinessService;
    private final StoreActivationRequestCoordinator activationCoordinator;
    private final ObjectMapper objectMapper;

    public StorePart2ProvisioningServiceImpl(
        StoreRepository storeRepository,
        Part2PlanNormalizer planNormalizer,
        StorePart2RequestCoordinator requestCoordinator,
        StorePart2ProvisioningWriter writer,
        StoreReadinessService readinessService,
        StoreActivationRequestCoordinator activationCoordinator,
        ObjectMapper objectMapper
    ) {
        this.storeRepository = storeRepository;
        this.planNormalizer = planNormalizer;
        this.requestCoordinator = requestCoordinator;
        this.writer = writer;
        this.readinessService = readinessService;
        this.activationCoordinator = activationCoordinator;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StorePart2ProvisioningResponse provision(
        AuthenticatedUser actor,
        Long organizationId,
        Long storeId,
        String idempotencyKey,
        StorePart2ProvisioningRequest request
    ) {
        requireActor(actor);
        Store store = requirePart2Store(organizationId, storeId);
        Part2ProvisioningPlan plan = planNormalizer.normalize(request);
        Part2Reservation reservation = requestCoordinator.reserve(
            organizationId,
            storeId,
            idempotencyKey,
            plan,
            actor.userId()
        );
        if (reservation.replayed()) {
            StoreReadinessResponse readiness = readinessService.evaluate(organizationId, storeId);
            return responseFromReservation(reservation, readiness, true, List.of(), List.of());
        }

        try {
            Store lockedStore = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException("PART2_STORE_NOT_FOUND"));
            requirePart2Store(organizationId, lockedStore.id);
            StorePart2ProvisioningWriter.WriteResult result = writer.write(lockedStore, reservation.request().id, plan);
            StoreReadinessResponse readiness = readinessService.evaluate(organizationId, storeId);
            requestCoordinator.complete(reservation.request().id, readiness.evidence_id, result);
            return responseFromWrite(reservation.request(), result, readiness);
        } catch (RuntimeException exception) {
            requestCoordinator.fail(reservation.request().id, errorCode(exception));
            throw exception;
        }
    }

    @Override
    public StoreReadinessResponse readiness(Long organizationId, Long storeId) {
        return readinessService.evaluate(organizationId, storeId);
    }

    @Override
    @Transactional
    public StoreActivationResponse activate(
        AuthenticatedUser actor,
        Long organizationId,
        Long storeId,
        String idempotencyKey,
        StoreActivationRequest request
    ) {
        requireActor(actor);
        Store store = requirePart2Store(organizationId, storeId);
        String expectedFingerprint = request == null ? null : normalize(request.expected_readiness_fingerprint);
        String requestFingerprint = StoreProfileCanonicalJson.sha256Canonical(
            "{\"expected_readiness_fingerprint\":\"" + (expectedFingerprint == null ? "" : expectedFingerprint) + "\"}"
        );
        ActivationReservation reservation = activationCoordinator.reserve(
            organizationId,
            storeId,
            idempotencyKey,
            requestFingerprint,
            expectedFingerprint,
            actor.userId()
        );
        if (reservation.replayed()) {
            StoreReadinessResponse readiness = readinessService.evaluate(organizationId, storeId);
            return activationResponse(reservation.request(), readiness, true);
        }

        try {
            Store lockedStore = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException("PART2_STORE_NOT_FOUND"));
            requirePart2Store(organizationId, lockedStore.id);
            if ("active".equalsIgnoreCase(lockedStore.status)
                || "ACTIVE".equalsIgnoreCase(lockedStore.lifecycle_status)) {
                throw conflict("PART2_STORE_ALREADY_LIVE", "This Store is already Live");
            }
            StoreReadinessResponse readiness = readinessService.evaluate(organizationId, storeId);
            if (!Boolean.TRUE.equals(readiness.ready)) {
                throw conflict("PART2_STORE_NOT_READY", "Store readiness is NOT_READY");
            }
            if (expectedFingerprint != null && !expectedFingerprint.equals(readiness.readiness_fingerprint)) {
                throw conflict("PART2_READINESS_FINGERPRINT_CONFLICT", "Readiness changed; review the current evidence before activation");
            }
            lockedStore.status = "active";
            lockedStore.lifecycle_status = "ACTIVE";
            lockedStore.printing_mode = PrintingMode.MOCK;
            lockedStore.printing_enabled = true;
            lockedStore.updated_at = LocalDateTime.now();
            storeRepository.save(lockedStore);
            activationCoordinator.complete(
                reservation.request().id,
                readiness.evidence_id,
                "PHASE_B_PART2_ACTIVATED",
                "LIVE"
            );
            return activationResponse(reservation.request(), readiness, false);
        } catch (RuntimeException exception) {
            activationCoordinator.fail(reservation.request().id, errorCode(exception));
            throw exception;
        }
    }

    private StorePart2ProvisioningResponse responseFromWrite(
        StoreProvisioningPart2RequestEntity request,
        StorePart2ProvisioningWriter.WriteResult result,
        StoreReadinessResponse readiness
    ) {
        StorePart2ProvisioningResponse response = new StorePart2ProvisioningResponse();
        response.request_id = request.id;
        response.store_id = request.store_id;
        response.status = request.status;
        response.readiness_status = readiness.readiness_status;
        response.replayed = false;
        response.result_code = request.result_code;
        response.error_code = request.error_code;
        response.counts = counts(result.stationCount(), result.tableCount(), result.staffCount(), result.printerRoleCount(), result.deviceCount());
        response.staff = result.staff().stream().map(staff -> {
            StorePart2ProvisioningResponse.StaffResult value = new StorePart2ProvisioningResponse.StaffResult();
            value.user_id = staff.userId();
            value.login_identifier = staff.loginIdentifier();
            value.role_code = staff.roleCode();
            return value;
        }).toList();
        response.synthetic_staff_credentials = result.temporaryStaffCredentials().stream().map(value -> {
            StorePart2ProvisioningResponse.StaffCredential credential = new StorePart2ProvisioningResponse.StaffCredential();
            credential.login_identifier = value.loginIdentifier();
            credential.temporary_password = value.temporaryPassword();
            credential.role_code = value.roleCode();
            return credential;
        }).toList();
        response.synthetic_device_credentials = result.temporaryDeviceCredentials().stream().map(value -> {
            StorePart2ProvisioningResponse.DeviceCredential credential = new StorePart2ProvisioningResponse.DeviceCredential();
            credential.device_id = value.deviceId();
            credential.device_name = value.deviceName();
            credential.device_token = value.deviceToken();
            return credential;
        }).toList();
        response.readiness = readiness;
        return response;
    }

    private StorePart2ProvisioningResponse responseFromReservation(
        Part2Reservation reservation,
        StoreReadinessResponse readiness,
        boolean replayed,
        List<StorePart2ProvisioningResponse.StaffCredential> staffCredentials,
        List<StorePart2ProvisioningResponse.DeviceCredential> deviceCredentials
    ) {
        StorePart2ProvisioningResponse response = new StorePart2ProvisioningResponse();
        response.request_id = reservation.request().id;
        response.store_id = reservation.request().store_id;
        response.status = reservation.request().status;
        response.readiness_status = readiness.readiness_status;
        response.replayed = replayed;
        response.result_code = reservation.request().result_code;
        response.error_code = reservation.request().error_code;
        response.counts = parseCounts(reservation.request().result_json);
        response.staff = List.of();
        response.synthetic_staff_credentials = staffCredentials;
        response.synthetic_device_credentials = deviceCredentials;
        response.readiness = readiness;
        return response;
    }

    private StoreActivationResponse activationResponse(
        StoreActivationRequestEntity request,
        StoreReadinessResponse readiness,
        boolean replayed
    ) {
        StoreActivationResponse response = new StoreActivationResponse();
        response.request_id = request.id;
        response.organization_id = request.organization_id;
        response.store_id = request.store_id;
        response.status = request.status;
        response.target_state = request.target_state == null ? "LIVE" : request.target_state;
        response.replayed = replayed;
        response.result_code = request.result_code;
        response.error_code = request.error_code;
        response.readiness = readiness;
        return response;
    }

    private StorePart2ProvisioningResponse.Counts counts(
        int stations,
        int tables,
        int staff,
        int printerRoles,
        int devices
    ) {
        StorePart2ProvisioningResponse.Counts counts = new StorePart2ProvisioningResponse.Counts();
        counts.station_count = stations;
        counts.table_count = tables;
        counts.staff_count = staff;
        counts.printer_role_count = printerRoles;
        counts.device_count = devices;
        return counts;
    }

    private StorePart2ProvisioningResponse.Counts parseCounts(String json) {
        if (json == null || json.isBlank()) {
            return counts(0, 0, 0, 0, 0);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return counts(
                node.path("station_count").asInt(),
                node.path("table_count").asInt(),
                node.path("staff_count").asInt(),
                node.path("printer_role_count").asInt(),
                node.path("device_count").asInt()
            );
        } catch (Exception exception) {
            return counts(0, 0, 0, 0, 0);
        }
    }

    private Store requirePart2Store(Long organizationId, Long storeId) {
        if (organizationId == null || storeId == null) {
            throw conflict("PART2_SCOPE_REQUIRED", "Organization and Store are required");
        }
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> conflict("PART2_STORE_NOT_FOUND", "Store not found"));
        if (!organizationId.equals(store.organization_id)) {
            throw conflict("PART2_STORE_ORGANIZATION_MISMATCH", "Store does not belong to Organization");
        }
        if (!"PHASE_B_OWNER_PROVISIONING".equalsIgnoreCase(store.provisioning_source)
            || !"VALIDATION_FIXTURE".equalsIgnoreCase(store.store_kind)) {
            throw conflict("PART2_ONLY_VALIDATION_FIXTURE_ALLOWED", "Part 2 runtime is limited to synthetic validation Stores");
        }
        return store;
    }

    private void requireActor(AuthenticatedUser actor) {
        if (actor == null || actor.userId() == null) {
            throw conflict("PART2_ACTOR_REQUIRED", "Owner actor is required");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof OwnerStoreProvisioningException provisioningException) {
            return provisioningException.getErrorCode();
        }
        if (exception instanceof BusinessException) {
            return exception.getMessage();
        }
        return "PHASE_B_PART2_OPERATION_FAILED";
    }

    private OwnerStoreProvisioningException conflict(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.CONFLICT, message);
    }
}
