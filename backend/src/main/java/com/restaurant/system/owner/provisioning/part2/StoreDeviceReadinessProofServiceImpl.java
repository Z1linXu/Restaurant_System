package com.restaurant.system.owner.provisioning.part2;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.service.StoreDeviceService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreDeviceReadinessProofServiceImpl implements StoreDeviceReadinessProofService {

    private final StoreDeviceService storeDeviceService;
    private final StoreDeviceReadinessRepository readinessRepository;
    private final PhaseBProvisioningRuntimeGate runtimeGate;
    private final StoreRepository storeRepository;

    public StoreDeviceReadinessProofServiceImpl(
        StoreDeviceService storeDeviceService,
        StoreDeviceReadinessRepository readinessRepository,
        PhaseBProvisioningRuntimeGate runtimeGate,
        StoreRepository storeRepository
    ) {
        this.storeDeviceService = storeDeviceService;
        this.readinessRepository = readinessRepository;
        this.runtimeGate = runtimeGate;
        this.storeRepository = storeRepository;
    }

    @Override
    @Transactional
    public DeviceReadinessProofResponse record(Long deviceId, String rawToken, DeviceReadinessProofRequest request) {
        runtimeGate.requireEnabled();
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, rawToken);
        if (device.storeId == null) {
            throw new BusinessException("PART2_DEVICE_STORE_REQUIRED");
        }
        Store store = storeRepository.findById(device.storeId)
            .orElseThrow(() -> new BusinessException("PART2_DEVICE_STORE_NOT_FOUND"));
        if (!"PHASE_B_OWNER_PROVISIONING".equalsIgnoreCase(store.provisioning_source)
            || !"VALIDATION_FIXTURE".equalsIgnoreCase(store.store_kind)) {
            throw new BusinessException("PART2_ONLY_VALIDATION_FIXTURE_ALLOWED");
        }
        if (store.organization_id == null || !store.organization_id.equals(device.organizationId)) {
            throw new BusinessException("PART2_DEVICE_ORGANIZATION_MISMATCH");
        }
        String workerStatus = request == null || request.worker_status == null || request.worker_status.isBlank()
            ? "HEALTHY"
            : request.worker_status.trim().toUpperCase();
        boolean trustedBuild = request == null || request.trusted_build == null || request.trusted_build;
        LocalDateTime now = LocalDateTime.now();
        StoreDeviceReadinessEntity readiness = readinessRepository.findByDeviceId(device.id)
            .orElseGet(StoreDeviceReadinessEntity::new);
        readiness.organization_id = device.organizationId;
        readiness.store_id = device.storeId;
        readiness.device_id = device.id;
        readiness.contract_version = "PHASE_B_PART2_DEVICE_READINESS_V1";
        readiness.trusted_build = trustedBuild;
        readiness.worker_status = workerStatus;
        readiness.proof_status = trustedBuild && "HEALTHY".equals(workerStatus) ? "PASS" : "NOT_READY";
        readiness.last_heartbeat_at = device.lastSeenAt == null ? now : device.lastSeenAt;
        readiness.checked_at = now;
        readiness.expires_at = now.plusMinutes(15);
        readiness.evidence_json = "{\"trusted_build\":" + trustedBuild
            + ",\"worker_status\":\"" + escape(workerStatus)
            + "\",\"proof_status\":\"" + readiness.proof_status + "\"}";
        readiness.created_at = readiness.created_at == null ? now : readiness.created_at;
        readiness.updated_at = now;
        readinessRepository.save(readiness);

        DeviceReadinessProofResponse response = new DeviceReadinessProofResponse();
        response.device_id = device.id;
        response.store_id = device.storeId;
        response.proof_status = readiness.proof_status;
        response.worker_status = readiness.worker_status;
        response.trusted_build = readiness.trusted_build;
        response.last_heartbeat_at = readiness.last_heartbeat_at;
        response.expires_at = readiness.expires_at;
        return response;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
