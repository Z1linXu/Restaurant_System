package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleEntity;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleRepository;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationalStoreBaselineProvisionerTest {

    @Mock private DiningTableRepository diningTableRepository;
    @Mock private StoreMembershipRepository storeMembershipRepository;
    @Mock private StoreLogicalPrinterRoleRepository printerRoleRepository;

    private OperationalStoreBaselineProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new OperationalStoreBaselineProvisioner(
            diningTableRepository,
            storeMembershipRepository,
            printerRoleRepository
        );
    }

    @Test
    void createsOnlyStoreLocalOperationalDefaultsAndLeavesHardwareUnbound() {
        Store store = new Store();
        store.id = 18L;
        store.organization_id = 1L;
        store.printing_enabled = true;
        store.printing_mode = "MOCK";
        AuthenticatedUser owner = new AuthenticatedUser(20L, 1L, 7L, "owner", "Owner", "OWNER");

        when(storeMembershipRepository.findFirstByUserIdAndStoreId(20L, 18L)).thenReturn(Optional.empty());
        when(diningTableRepository.findAllByStoreIdAndTableCode(18L, "T01")).thenReturn(List.of());
        when(diningTableRepository.findAllByStoreIdAndTableCode(18L, "T02")).thenReturn(List.of());
        when(printerRoleRepository.findByStoreIdAndRoleCode(any(), any())).thenReturn(Optional.empty());
        when(printerRoleRepository.findByStoreIdAndModuleCode(any(), any())).thenReturn(Optional.empty());

        provisioner.provision(store, owner);

        ArgumentCaptor<StoreMembership> membership = ArgumentCaptor.forClass(StoreMembership.class);
        verify(storeMembershipRepository).save(membership.capture());
        assertThat(membership.getValue().organizationId).isEqualTo(1L);
        assertThat(membership.getValue().storeId).isEqualTo(18L);
        assertThat(membership.getValue().roleCode).isEqualTo("OWNER");

        ArgumentCaptor<DiningTable> tables = ArgumentCaptor.forClass(DiningTable.class);
        verify(diningTableRepository, times(2)).save(tables.capture());
        assertThat(tables.getAllValues()).extracting(table -> table.table_code).containsExactly("T01", "T02");

        ArgumentCaptor<StoreLogicalPrinterRoleEntity> roles = ArgumentCaptor.forClass(StoreLogicalPrinterRoleEntity.class);
        verify(printerRoleRepository, times(2)).save(roles.capture());
        assertThat(roles.getAllValues()).allSatisfy(role -> {
            assertThat(role.mode).isEqualTo("DISABLED");
            assertThat(role.physical_binding_status).isEqualTo("UNBOUND");
            assertThat(role.assigned_printer_id).isNull();
        });
        assertThat(store.printing_enabled).isFalse();
        assertThat(store.printing_mode).isEqualTo("DISABLED");
    }
}
