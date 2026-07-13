package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.StoreDeviceResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StoreDeviceServiceImplTest {

    @Mock
    private StoreDeviceRepository storeDeviceRepository;
    @Mock
    private StoreRepository storeRepository;

    private StoreDeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StoreDeviceServiceImpl(storeDeviceRepository, storeRepository);
    }

    @Test
    void authenticateDeviceTouchesLastSeenWhenStale() {
        RegisteredDevice registered = registerDevice();
        LocalDateTime staleLastSeen = LocalDateTime.now().minusMinutes(2);
        registered.device.lastSeenAt = staleLastSeen;
        when(storeDeviceRepository.findById(registered.device.id)).thenReturn(Optional.of(registered.device));
        clearInvocations(storeDeviceRepository);

        StoreDevice authenticated = service.authenticateDevice(registered.device.id, registered.rawToken);

        assertTrue(authenticated.lastSeenAt.isAfter(staleLastSeen));
        verify(storeDeviceRepository).save(registered.device);
    }

    @Test
    void authenticateDeviceSkipsLastSeenWriteWhenRecent() {
        RegisteredDevice registered = registerDevice();
        LocalDateTime recentLastSeen = LocalDateTime.now().minusSeconds(5);
        registered.device.lastSeenAt = recentLastSeen;
        when(storeDeviceRepository.findById(registered.device.id)).thenReturn(Optional.of(registered.device));
        clearInvocations(storeDeviceRepository);

        StoreDevice authenticated = service.authenticateDevice(registered.device.id, registered.rawToken);

        assertEquals(recentLastSeen, authenticated.lastSeenAt);
        verify(storeDeviceRepository, never()).save(any(StoreDevice.class));
    }

    @Test
    void authenticateDeviceRejectsRevokedDevice() {
        RegisteredDevice registered = registerDevice();
        registered.device.status = "REVOKED";
        registered.device.isActive = false;
        when(storeDeviceRepository.findById(registered.device.id)).thenReturn(Optional.of(registered.device));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.authenticateDevice(registered.device.id, registered.rawToken)
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void renameDeviceTrimsNameWithinStore() {
        StoreDevice device = activeDevice();
        when(storeDeviceRepository.findByIdAndStoreId(device.id, device.storeId)).thenReturn(Optional.of(device));
        when(storeDeviceRepository.save(any(StoreDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreDeviceResponse response = service.renameDevice(device.storeId, device.id, "  Expo Pad  ");

        assertEquals("Expo Pad", response.device_name);
        verify(storeDeviceRepository).save(device);
    }

    @Test
    void disableDeviceSoftDeactivatesWithoutDeleting() {
        StoreDevice device = activeDevice();
        when(storeDeviceRepository.findByIdAndStoreId(device.id, device.storeId)).thenReturn(Optional.of(device));
        when(storeDeviceRepository.save(any(StoreDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreDeviceResponse response = service.disableDevice(device.storeId, device.id);

        assertEquals("DISABLED", response.status);
        assertFalse(Boolean.TRUE.equals(response.is_active));
        verify(storeDeviceRepository).save(device);
    }

    @Test
    void revokeDeviceSoftDeactivatesWithoutDeleting() {
        StoreDevice device = activeDevice();
        when(storeDeviceRepository.findByIdAndStoreId(device.id, device.storeId)).thenReturn(Optional.of(device));
        when(storeDeviceRepository.save(any(StoreDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreDeviceResponse response = service.revokeDevice(device.storeId, device.id);

        assertEquals("REVOKED", response.status);
        assertFalse(Boolean.TRUE.equals(response.is_active));
        verify(storeDeviceRepository).save(device);
    }

    private RegisteredDevice registerDevice() {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 7L;
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        final StoreDevice[] saved = new StoreDevice[1];
        when(storeDeviceRepository.save(any(StoreDevice.class))).thenAnswer(invocation -> {
            StoreDevice device = invocation.getArgument(0);
            if (device.id == null) {
                device.id = 90L;
            }
            saved[0] = device;
            return device;
        });

        DeviceRegisterRequest request = new DeviceRegisterRequest();
        request.store_id = 1L;
        request.device_name = "Restaurant Pad";
        request.device_type = "ANDROID_PAD";
        request.app_version = "debug";
        request.platform = "ANDROID";

        DeviceRegisterResponse response = service.registerDevice(request);
        return new RegisteredDevice(saved[0], response.device_token);
    }

    private StoreDevice activeDevice() {
        StoreDevice device = new StoreDevice();
        device.id = 90L;
        device.storeId = 1L;
        device.organizationId = 7L;
        device.deviceName = "Restaurant Pad";
        device.deviceType = "ANDROID_PAD";
        device.status = "ACTIVE";
        device.isActive = true;
        device.createdAt = LocalDateTime.now().minusDays(1);
        device.updatedAt = device.createdAt;
        return device;
    }

    private record RegisteredDevice(StoreDevice device, String rawToken) {
    }
}
