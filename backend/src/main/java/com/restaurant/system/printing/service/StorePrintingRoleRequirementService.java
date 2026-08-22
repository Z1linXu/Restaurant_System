package com.restaurant.system.printing.service;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleEntity;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleRepository;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the Store's enabled printing outputs without treating physical
 * Printer assignment as a prerequisite in endpoint-free MOCK mode.
 *
 * Part 2 logical roles are canonical. An existing assignment row is consulted
 * only for legacy Stores that do not yet have a logical role for that output.
 */
@Service
public class StorePrintingRoleRequirementService {

    private static final List<String> RUNTIME_MODULES = List.of(
        PrintModuleCode.GRAB,
        PrintModuleCode.FRONTDESK_RECEIPT,
        PrintModuleCode.HOT_KITCHEN
    );

    private final StoreRepository storeRepository;
    private final StoreLogicalPrinterRoleRepository logicalRoleRepository;
    private final PrinterAssignmentRepository assignmentRepository;

    public StorePrintingRoleRequirementService(
        StoreRepository storeRepository,
        StoreLogicalPrinterRoleRepository logicalRoleRepository,
        PrinterAssignmentRepository assignmentRepository
    ) {
        this.storeRepository = storeRepository;
        this.logicalRoleRepository = logicalRoleRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<StorePrintingRoleRequirement> requirements(Long storeId) {
        requireStore(storeId);
        return RUNTIME_MODULES.stream().map(moduleCode -> resolveRequirement(storeId, moduleCode)).toList();
    }

    @Transactional(readOnly = true)
    public StorePrintingRoleRequirement requirement(Long storeId, String moduleCode) {
        requireStore(storeId);
        return resolveRequirement(storeId, moduleCode);
    }

    private StorePrintingRoleRequirement resolveRequirement(Long storeId, String moduleCode) {
        String normalized = normalizeModuleCode(moduleCode);
        StoreLogicalPrinterRoleEntity logicalRole = logicalRoleRepository
            .findByStoreIdAndModuleCode(storeId, normalized)
            .orElse(null);
        if (logicalRole != null) {
            return new StorePrintingRoleRequirement(
                normalized,
                Boolean.TRUE.equals(logicalRole.enabled),
                "store_logical_printer_roles"
            );
        }
        PrinterAssignment assignment = assignmentRepository.findByStoreIdAndModuleCode(storeId, normalized).orElse(null);
        if (assignment != null) {
            return new StorePrintingRoleRequirement(
                normalized,
                Boolean.TRUE.equals(assignment.enabled),
                "printer_assignments.enabled (legacy Store fallback)"
            );
        }
        return new StorePrintingRoleRequirement(normalized, false, "no enabled Store printing role");
    }

    private Store requireStore(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("STORE_ID_REQUIRED");
        }
        return storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("Store not found: " + storeId));
    }

    private String normalizeModuleCode(String moduleCode) {
        String normalized = moduleCode == null ? "" : moduleCode.trim().toUpperCase(Locale.ROOT);
        if (!RUNTIME_MODULES.contains(normalized)) {
            throw new BusinessException("Unsupported runtime printing role: " + normalized);
        }
        return normalized;
    }
}
