package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.dto.DeviceHeartbeatRequest;
import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.StoreDeviceResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.printing.service.StoreDeviceService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoreDeviceServiceImpl implements StoreDeviceService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StoreDeviceRepository storeDeviceRepository;
    private final StoreRepository storeRepository;

    public StoreDeviceServiceImpl(
        StoreDeviceRepository storeDeviceRepository,
        StoreRepository storeRepository
    ) {
        this.storeDeviceRepository = storeDeviceRepository;
        this.storeRepository = storeRepository;
    }

    @Override
    @Transactional
    public DeviceRegisterResponse registerDevice(DeviceRegisterRequest request) {
        if (request == null || request.store_id == null) {
            throw new BusinessException("store_id is required");
        }
        Store store = storeRepository.findById(request.store_id)
            .orElseThrow(() -> new BusinessException("Store not found"));
        String rawToken = generateDeviceToken();
        LocalDateTime now = LocalDateTime.now();

        StoreDevice device = new StoreDevice();
        device.organizationId = store.organization_id;
        device.storeId = store.id;
        device.deviceName = normalizeLabel(request.device_name, "Pad Device");
        device.deviceType = normalizeLabel(request.device_type, "ANDROID_PAD");
        device.deviceTokenHash = hashToken(rawToken);
        device.status = "ACTIVE";
        device.lastSeenAt = now;
        device.appVersion = blankToNull(request.app_version);
        device.platform = blankToNull(request.platform);
        device.isActive = true;
        device.createdAt = now;
        device.updatedAt = now;
        return DeviceRegisterResponse.from(storeDeviceRepository.save(device), rawToken);
    }

    @Override
    public StoreDevice authenticateDevice(Long deviceId, String rawDeviceToken) {
        if (deviceId == null || rawDeviceToken == null || rawDeviceToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Device credentials are required");
        }
        StoreDevice device = storeDeviceRepository.findById(deviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device credentials"));
        if (!Boolean.TRUE.equals(device.isActive) || !"ACTIVE".equals(device.status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Device is not active");
        }
        if (!hashToken(rawDeviceToken).equals(device.deviceTokenHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device credentials");
        }
        return device;
    }

    @Override
    @Transactional
    public StoreDeviceResponse heartbeat(Long deviceId, String rawDeviceToken, DeviceHeartbeatRequest request) {
        StoreDevice device = authenticateDevice(deviceId, rawDeviceToken);
        device.lastSeenAt = LocalDateTime.now();
        if (request != null) {
            device.appVersion = blankToNull(request.app_version);
            device.platform = blankToNull(request.platform);
        }
        device.updatedAt = device.lastSeenAt;
        return StoreDeviceResponse.from(storeDeviceRepository.save(device));
    }

    @Override
    public List<StoreDeviceResponse> listStoreDevices(Long storeId) {
        return storeDeviceRepository.findAllByStoreIdOrderByIdAsc(storeId)
            .stream()
            .map(StoreDeviceResponse::from)
            .toList();
    }

    private String generateDeviceToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception exception) {
            throw new BusinessException("Failed to hash device token");
        }
    }

    private String normalizeLabel(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
