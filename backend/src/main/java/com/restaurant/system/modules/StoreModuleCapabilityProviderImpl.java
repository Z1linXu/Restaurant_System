package com.restaurant.system.modules;

import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.PrintingMode;
import com.restaurant.system.printing.PrintingRuntimePolicyProperties;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.printing.service.StorePrintingRoleRequirement;
import com.restaurant.system.printing.service.StorePrintingRoleRequirementService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StoreModuleCapabilityProviderImpl implements StoreModuleCapabilityProvider {

    private final FeatureFlagService featureFlagService;
    private final PrintingRuntimePolicyProperties printingRuntimePolicy;
    private final StoreRepository storeRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final PrinterAssignmentRepository printerAssignmentRepository;
    private final StoreDeviceRepository storeDeviceRepository;
    private final StorePrintingRoleRequirementService printingRoleRequirementService;

    public StoreModuleCapabilityProviderImpl(
        FeatureFlagService featureFlagService,
        PrintingRuntimePolicyProperties printingRuntimePolicy,
        StoreRepository storeRepository,
        PrinterConfigRepository printerConfigRepository,
        PrinterAssignmentRepository printerAssignmentRepository,
        StoreDeviceRepository storeDeviceRepository,
        StorePrintingRoleRequirementService printingRoleRequirementService
    ) {
        this.featureFlagService = featureFlagService;
        this.printingRuntimePolicy = printingRuntimePolicy;
        this.storeRepository = storeRepository;
        this.printerConfigRepository = printerConfigRepository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.storeDeviceRepository = storeDeviceRepository;
        this.printingRoleRequirementService = printingRoleRequirementService;
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
        return hardwareReadiness(storeId).stream()
            .filter(StoreHardwareCapabilityReadiness::dependencySatisfied)
            .map(StoreHardwareCapabilityReadiness::capabilityKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<StoreHardwareCapabilityReadiness> hardwareReadiness(Long storeId) {
        List<StoreHardwareCapabilityReadiness> readiness = new java.util.ArrayList<>();
        readiness.add(readiness(
            HardwareCapabilityKeys.TOUCH_CLIENT,
            HardwareReadinessState.VERIFIED,
            true,
            "HARDWARE_CAPABILITY",
            "WEB_OR_TABLET_CLIENT_RUNTIME",
            "Touch-capable web client is the current POS delivery model."
        ));

        Store store = storeId == null ? null : storeRepository.findById(storeId).orElse(null);
        String printingMode = store == null ? null : PrintingMode.normalize(store.printing_mode);
        LogicalPrinterTopology topology = logicalPrinterTopology(storeId);
        Map<String, StorePrintingRoleRequirement> roleRequirements = store == null
            ? Map.of()
            : printingRoleRequirementService.requirements(storeId).stream()
                .collect(Collectors.toMap(StorePrintingRoleRequirement::moduleCode, Function.identity()));
        readiness.add(printReadiness(HardwareCapabilityKeys.PRINT_GRAB, PrintModuleCode.GRAB, printingMode, topology, roleRequirements));
        readiness.add(printReadiness(
            HardwareCapabilityKeys.PRINT_FRONTDESK_RECEIPT,
            PrintModuleCode.FRONTDESK_RECEIPT,
            printingMode,
            topology,
            roleRequirements
        ));
        readiness.add(printReadiness(
            HardwareCapabilityKeys.PRINT_HOT_KITCHEN,
            PrintModuleCode.HOT_KITCHEN,
            printingMode,
            topology,
            roleRequirements
        ));

        List<StoreDevice> devices = storeId == null
            ? List.of()
            : storeDeviceRepository.findAllByStoreIdOrderByIdAsc(storeId);
        boolean hasActivePad = devices.stream().anyMatch(this::isActivePadDevice);
        readiness.add(readiness(
            HardwareCapabilityKeys.PAD_DEVICE,
            hasActivePad ? HardwareReadinessState.CONFIGURED : HardwareReadinessState.UNCONFIGURED,
            false,
            "PHYSICAL_DEVICE_BINDING",
            "store_devices",
            hasActivePad ? "At least one active Store-scoped Pad device exists." : "No active Store-scoped Pad device is currently enrolled."
        ));
        readiness.add(readiness(
            HardwareCapabilityKeys.DEVICE_ENROLLMENT,
            HardwareReadinessState.CONFIGURED,
            false,
            "ENVIRONMENT_CAPABILITY",
            "StoreDeviceService",
            "Store-scoped device registration, heartbeat and authentication services are present."
        ));

        boolean padDirectRequired = PrintingMode.PAD_DIRECT.equals(printingMode);
        readiness.add(readiness(
            HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT,
            !padDirectRequired
                ? HardwareReadinessState.NOT_REQUIRED
                : hasActivePad ? HardwareReadinessState.CONFIGURED : HardwareReadinessState.UNCONFIGURED,
            padDirectRequired,
            "PHYSICAL_DEVICE_BINDING",
            "stores.printing_mode + store_devices",
            padDirectRequired
                ? "PAD_DIRECT mode requires an active Store-scoped Pad print client."
                : "Current print mode does not require a physical Pad Direct print client."
        ));

        return List.copyOf(readiness);
    }

    private LogicalPrinterTopology logicalPrinterTopology(Long storeId) {
        if (storeId == null) {
            return new LogicalPrinterTopology(Map.of(), Map.of());
        }
        Map<Long, PrinterConfig> printersById = printerConfigRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .filter(printer -> printer.id != null)
            .collect(Collectors.toMap(printer -> printer.id, Function.identity(), (left, right) -> left));
        Map<String, PrinterAssignment> assignmentsByModule = printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .filter(this::isEnabledAssignment)
            .filter(assignment -> assignment.module_code != null && !assignment.module_code.isBlank())
            .collect(Collectors.toMap(
                assignment -> assignment.module_code.trim().toUpperCase(),
                Function.identity(),
                (left, right) -> left
            ));
        return new LogicalPrinterTopology(printersById, assignmentsByModule);
    }

    private StoreHardwareCapabilityReadiness printReadiness(
        String capability,
        String moduleCode,
        String printingMode,
        LogicalPrinterTopology topology,
        Map<String, StorePrintingRoleRequirement> roleRequirements
    ) {
        StorePrintingRoleRequirement requirement = roleRequirements.getOrDefault(
            moduleCode,
            new StorePrintingRoleRequirement(moduleCode, false, "Store unavailable")
        );
        if (!requirement.required() || PrintingMode.DISABLED.equals(printingMode)) {
            return readiness(
                capability,
                HardwareReadinessState.NOT_REQUIRED,
                false,
                "STORE_OPERATIONAL_PRINTING_ROLE",
                requirement.source(),
                moduleCode + " is disabled for the current Store runtime and does not block Printing."
            );
        }
        if (PrintingMode.MOCK.equals(printingMode)) {
            return readiness(
                capability,
                HardwareReadinessState.CONFIGURED,
                true,
                "ENDPOINT_FREE_MOCK_RUNTIME",
                requirement.source(),
                moduleCode + " is enabled and renders endpoint-free MOCK PrintJobs."
            );
        }
        PrinterAssignment assignment = topology.assignmentsByModule().get(moduleCode);
        PrinterConfig printer = assignment == null ? null : topology.printersById().get(assignment.printer_id);
        boolean configured = printer != null && Boolean.TRUE.equals(printer.enabled);
        return readiness(
            capability,
            configured ? HardwareReadinessState.CONFIGURED : HardwareReadinessState.UNCONFIGURED,
            true,
            "PHYSICAL_PRINTER_BINDING",
            "printer_configs + printer_assignments",
            configured
                ? moduleCode + " routes to an enabled logical printer."
                : moduleCode + " is missing an enabled logical printer assignment."
        );
    }

    private boolean isEnabledAssignment(PrinterAssignment assignment) {
        return assignment != null && Boolean.TRUE.equals(assignment.enabled) && assignment.printer_id != null;
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

    private StoreHardwareCapabilityReadiness readiness(
        String capabilityKey,
        HardwareReadinessState readinessState,
        boolean requiredByCurrentRuntime,
        String layer,
        String source,
        String note
    ) {
        return new StoreHardwareCapabilityReadiness(
            capabilityKey,
            readinessState,
            requiredByCurrentRuntime,
            layer,
            source,
            note
        );
    }

    private record LogicalPrinterTopology(
        Map<Long, PrinterConfig> printersById,
        Map<String, PrinterAssignment> assignmentsByModule
    ) {
    }
}
