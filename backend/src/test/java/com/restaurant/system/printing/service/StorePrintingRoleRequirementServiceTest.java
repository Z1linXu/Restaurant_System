package com.restaurant.system.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleEntity;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleRepository;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.PrintingMode;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorePrintingRoleRequirementServiceTest {

    @Mock private StoreRepository storeRepository;
    @Mock private StoreLogicalPrinterRoleRepository logicalRoleRepository;
    @Mock private PrinterAssignmentRepository assignmentRepository;

    private StorePrintingRoleRequirementService service;

    @BeforeEach
    void setUp() {
        service = new StorePrintingRoleRequirementService(
            storeRepository,
            logicalRoleRepository,
            assignmentRepository
        );
        Store store = new Store();
        store.id = 18L;
        store.organization_id = 1L;
        store.printing_mode = PrintingMode.MOCK;
        when(storeRepository.findById(18L)).thenReturn(Optional.of(store));
    }

    @Test
    void logicalRolesIndependentlyDriveGrabHotAndReceiptRequirements() {
        when(logicalRoleRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.GRAB))
            .thenReturn(Optional.of(role(PrintModuleCode.GRAB, true)));
        when(logicalRoleRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.HOT_KITCHEN))
            .thenReturn(Optional.of(role(PrintModuleCode.HOT_KITCHEN, false)));
        when(logicalRoleRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.FRONTDESK_RECEIPT))
            .thenReturn(Optional.of(role(PrintModuleCode.FRONTDESK_RECEIPT, true)));

        assertThat(service.requirement(18L, PrintModuleCode.GRAB).required()).isTrue();
        assertThat(service.requirement(18L, PrintModuleCode.HOT_KITCHEN).required()).isFalse();
        assertThat(service.requirement(18L, PrintModuleCode.FRONTDESK_RECEIPT).required()).isTrue();
    }

    @Test
    void canonicalLogicalRoleWinsOverConflictingLegacyAssignment() {
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.store_id = 18L;
        assignment.module_code = PrintModuleCode.HOT_KITCHEN;
        assignment.enabled = true;
        lenient().when(assignmentRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.HOT_KITCHEN))
            .thenReturn(Optional.of(assignment));
        when(logicalRoleRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.HOT_KITCHEN))
            .thenReturn(Optional.of(role(PrintModuleCode.HOT_KITCHEN, false)));

        assertThat(service.requirement(18L, PrintModuleCode.HOT_KITCHEN).required()).isFalse();

        assignment.enabled = false;
        when(logicalRoleRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.HOT_KITCHEN))
            .thenReturn(Optional.of(role(PrintModuleCode.HOT_KITCHEN, true)));
        assertThat(service.requirement(18L, PrintModuleCode.HOT_KITCHEN).required()).isTrue();
        verify(assignmentRepository, never()).findByStoreIdAndModuleCode(18L, PrintModuleCode.HOT_KITCHEN);
    }

    @Test
    void enabledLogicalRoleIsRuntimeRequiredEvenWhenReadinessRequiredFlagIsFalse() {
        StoreLogicalPrinterRoleEntity enabledRole = role(PrintModuleCode.GRAB, true);
        enabledRole.required = false;
        when(logicalRoleRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.GRAB))
            .thenReturn(Optional.of(enabledRole));

        assertThat(service.requirement(18L, PrintModuleCode.GRAB).required()).isTrue();
        verify(assignmentRepository, never()).findByStoreIdAndModuleCode(18L, PrintModuleCode.GRAB);
    }

    @Test
    void legacyAssignmentIsFallbackOnlyWhenLogicalRoleIsAbsent() {
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.store_id = 18L;
        assignment.module_code = PrintModuleCode.HOT_KITCHEN;
        assignment.enabled = true;
        when(logicalRoleRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.HOT_KITCHEN))
            .thenReturn(Optional.empty());
        when(assignmentRepository.findByStoreIdAndModuleCode(18L, PrintModuleCode.HOT_KITCHEN))
            .thenReturn(Optional.of(assignment));

        assertThat(service.requirement(18L, PrintModuleCode.HOT_KITCHEN).required()).isTrue();
    }

    private StoreLogicalPrinterRoleEntity role(String moduleCode, boolean enabled) {
        StoreLogicalPrinterRoleEntity role = new StoreLogicalPrinterRoleEntity();
        role.organization_id = 1L;
        role.store_id = 18L;
        role.role_code = moduleCode;
        role.module_code = moduleCode;
        role.display_name = moduleCode;
        role.mode = PrintingMode.MOCK;
        role.enabled = enabled;
        role.required = enabled;
        role.physical_binding_status = "UNBOUND";
        return role;
    }
}
