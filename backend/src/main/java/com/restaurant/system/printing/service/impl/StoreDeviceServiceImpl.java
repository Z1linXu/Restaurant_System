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
import java.time.Duration;
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
    private static final Duration LAST_SEEN_TOUCH_INTERVAL = Duration.ofSeconds(30);

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
    @Transactional
    public StoreDevice authenticateDevice(Long deviceId, String rawDeviceToken) {
        if (deviceId == null || rawDeviceToken == null || rawDeviceToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Device credentials are required");
        }
        StoreDevice device = storeDeviceRepository.findById(deviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        if (!Boolean.TRUE.equals(device.isActive) || !"ACTIVE".equals(device.status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Device is not active");
        }
        if (!hashToken(rawDeviceToken).equals(device.deviceTokenHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device token");
        }
        touchLastSeenIfStale(device, LocalDateTime.now());
        return device;
    }

    @Override
    @Transactional
    public StoreDeviceResponse heartbeat(Long deviceId, String rawDeviceToken, DeviceHeartbeatRequest request) {
        StoreDevice device = authenticateDevice(deviceId, rawDeviceToken);
        boolean changed = false;
        if (request != null) {
            String appVersion = blankToNull(request.app_version);
            String platform = blankToNull(request.platform);
            if (!equalsNullable(device.appVersion, appVersion)) {
                device.appVersion = appVersion;
                changed = true;
            }
            if (!equalsNullable(device.platform, platform)) {
                device.platform = platform;
                changed = true;
            }
        }
        if (changed) {
            device.updatedAt = LocalDateTime.now();
            storeDeviceRepository.save(device);
        }
        return StoreDeviceResponse.from(device);
    }

    @Override
    public List<StoreDeviceResponse> listStoreDevices(Long storeId) {
        return storeDeviceRepository.findAllByStoreIdOrderByIdAsc(storeId)
            .stream()
            .map(StoreDeviceResponse::from)
            .toList();
    }

    @Override
    @Transactional
    public StoreDeviceResponse renameDevice(Long storeId, Long deviceId, String deviceName) {
        StoreDevice device = requireStoreDevice(storeId, deviceId);
        device.deviceName = normalizeLabel(deviceName, device.deviceName == null || device.deviceName.isBlank() ? "Pad Device" : device.deviceName);
        device.updatedAt = LocalDateTime.now();
        return StoreDeviceResponse.from(storeDeviceRepository.save(device));
    }

    @Override
    @Transactional
    public StoreDeviceResponse disableDevice(Long storeId, Long deviceId) {
        return markDeviceInactive(storeId, deviceId, "DISABLED");
    }

    @Override
    @Transactional
    public StoreDeviceResponse revokeDevice(Long storeId, Long deviceId) {
        return markDeviceInactive(storeId, deviceId, "REVOKED");
    }

    private StoreDeviceResponse markDeviceInactive(Long storeId, Long deviceId, String status) {
        StoreDevice device = requireStoreDevice(storeId, deviceId);
        device.status = status;
        device.isActive = false;
        device.updatedAt = LocalDateTime.now();
        return StoreDeviceResponse.from(storeDeviceRepository.save(device));
    }

    private StoreDevice requireStoreDevice(Long storeId, Long deviceId) {
        if (storeId == null) {
            throw new BusinessException("store_id is required");
        }
        if (deviceId == null) {
            throw new BusinessException("device_id is required");
        }
        return storeDeviceRepository.findByIdAndStoreId(deviceId, storeId)
            .orElseThrow(() -> new BusinessException("Device not found for store"));
    }

    private void touchLastSeenIfStale(StoreDevice device, LocalDateTime now) {
        if (device.lastSeenAt != null && device.lastSeenAt.isAfter(now.minus(LAST_SEEN_TOUCH_INTERVAL))) {
            return;
        }
        device.lastSeenAt = now;
        device.updatedAt = now;
        storeDeviceRepository.save(device);
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
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
