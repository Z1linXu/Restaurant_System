package com.restaurant.system.modules;

import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.printing.PrintingMode;
import com.restaurant.system.printing.PrintingRuntimePolicyProperties;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class StoreModuleCapabilityProviderImpl implements StoreModuleCapabilityProvider {

    private final FeatureFlagService featureFlagService;
    private final PrintingRuntimePolicyProperties printingRuntimePolicy;
    private final StoreRepository storeRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final PrinterAssignmentRepository printerAssignmentRepository;
    private final StoreDeviceRepository storeDeviceRepository;

    public StoreModuleCapabilityProviderImpl(
        FeatureFlagService featureFlagService,
        PrintingRuntimePolicyProperties printingRuntimePolicy,
        StoreRepository storeRepository,
        PrinterConfigRepository printerConfigRepository,
        PrinterAssignmentRepository printerAssignmentRepository,
        StoreDeviceRepository storeDeviceRepository
    ) {
        this.featureFlagService = featureFlagService;
        this.printingRuntimePolicy = printingRuntimePolicy;
        this.storeRepository = storeRepository;
        this.printerConfigRepository = printerConfigRepository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.storeDeviceRepository = storeDeviceRepository;
    }

    @Override
    public Set<String> environmentCapabilities(Long storeId) {
        Set<String> capabilities = new LinkedHashSet<>();
        capabilities.add("AUTH_RUNTIME");
        capabilities.add("DATABASE");
        capabilities.add("WEBSOCKET_RUNTIME");
        if (featureFlagService.isEnabled(FeaturePackage.CORE_POS)) {
            capabilities.add("CORE_POS_RUNTIME");
        }
        if (featureFlagService.isEnabled(FeaturePackage.ADMIN)) {
            capabilities.add("ADMIN_RUNTIME");
        }
        if (featureFlagService.isEnabled(FeaturePackage.PRINTING)) {
            capabilities.add("PRINTING_FEATURE_FLAG");
        }
        if (!printingRuntimePolicy.getAllowedModes().isEmpty()) {
            capabilities.add("PRINT_MODE_RUNTIME");
        }
        if (featureFlagService.isEnabled(FeaturePackage.ANALYTICS)) {
            capabilities.add("ANALYTICS_FEATURE_FLAG");
        }
        if (featureFlagService.isEnabled(FeaturePackage.KDS)) {
            capabilities.add("KDS_FEATURE_FLAG");
        }
        return Set.copyOf(capabilities);
    }

    @Override
    public Set<String> hardwareCapabilities(Long storeId) {
        Set<String> capabilities = new LinkedHashSet<>();
        capabilities.add("TOUCH_CLIENT");
        if (hasLogicalPrinterTopology(storeId)) {
            capabilities.add("PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT");
        }
        if (hasPadDirectReadiness(storeId)) {
            capabilities.add("PAD_DEVICE_FOR_PAD_DIRECT");
        }
        return Set.copyOf(capabilities);
    }

    private boolean hasLogicalPrinterTopology(Long storeId) {
        if (storeId == null) {
            return false;
        }
        boolean hasEnabledPrinter = printerConfigRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .anyMatch(printer -> Boolean.TRUE.equals(printer.enabled));
        boolean hasEnabledAssignment = printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .anyMatch(this::isEnabledAssignment);
        return hasEnabledPrinter && hasEnabledAssignment;
    }

    private boolean hasPadDirectReadiness(Long storeId) {
        if (storeId == null) {
            return false;
        }
        Store store = storeRepository.findById(storeId).orElse(null);
        String printingMode = store == null ? null : PrintingMode.normalize(store.printing_mode);
        if (PrintingMode.PAD_DIRECT.equals(printingMode)) {
            return hasActivePadDevice(storeId);
        }
        return true;
    }

    private boolean isEnabledAssignment(PrinterAssignment assignment) {
        return assignment != null && Boolean.TRUE.equals(assignment.enabled) && assignment.printer_id != null;
    }

    private boolean hasActivePadDevice(Long storeId) {
        return storeDeviceRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .anyMatch(this::isActivePadDevice);
    }

    private boolean isActivePadDevice(StoreDevice device) {
        if (device == null || !Boolean.TRUE.equals(device.isActive)) {
            return false;
        }
        String deviceType = device.deviceType == null ? "" : device.deviceType.trim();
        String platform = device.platform == null ? "" : device.platform.trim();
        return "PAD".equalsIgnoreCase(deviceType)
            || "ANDROID".equalsIgnoreCase(platform)
            || "IPAD".equalsIgnoreCase(platform);
    }
}
