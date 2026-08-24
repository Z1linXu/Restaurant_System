package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.dto.OwnerBusinessStoreCreateResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningRequest;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCommand;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCounts;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningResult;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningService;
import com.restaurant.system.owner.provisioning.StoreProvisioningPurpose;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerBusinessStoreCreateServiceImplTest {

    @Mock private OwnerStoreProvisioningService provisioningService;
    @Mock private StoreRepository storeRepository;

    @Test
    void delegatesToBusinessPurposeAndReturnsOnlyLiveBusinessStore() {
        AuthenticatedUser owner = new AuthenticatedUser(1L, null, 2L, "owner", "Owner", "OWNER");
        OwnerStoreProvisioningResult result = result(10L, false);
        Store store = store(10L, 100L, "BUSINESS", "active", "ACTIVE");
        when(provisioningService.provision(any())).thenReturn(result);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        OwnerBusinessStoreCreateServiceImpl service = new OwnerBusinessStoreCreateServiceImpl(
            provisioningService,
            storeRepository
        );

        OwnerBusinessStoreCreateResponse response = service.create(owner, 100L, "stable-key", request());

        ArgumentCaptor<OwnerStoreProvisioningCommand> command = ArgumentCaptor.forClass(OwnerStoreProvisioningCommand.class);
        verify(provisioningService).provision(command.capture());
        assertThat(command.getValue().purpose()).isEqualTo(StoreProvisioningPurpose.BUSINESS);
        assertThat(response.is_live).isTrue();
        assertThat(response.store_kind).isEqualTo("BUSINESS");
    }

    @Test
    void rejectsCrossOrganizationOrNonLiveProvisioningResult() {
        when(provisioningService.provision(any())).thenReturn(result(10L, false));
        when(storeRepository.findById(10L))
            .thenReturn(Optional.of(store(10L, 999L, "BUSINESS", "active", "ACTIVE")));
        OwnerBusinessStoreCreateServiceImpl service = new OwnerBusinessStoreCreateServiceImpl(
            provisioningService,
            storeRepository
        );

        assertThatThrownBy(() -> service.create(
            new AuthenticatedUser(1L, null, 2L, "owner", "Owner", "OWNER"),
            100L,
            "stable-key",
            request()
        )).isInstanceOfSatisfying(OwnerStoreProvisioningException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo("BUSINESS_STORE_CREATE_RESULT_INVALID"));
    }

    private OwnerStoreProvisioningRequest request() {
        OwnerStoreProvisioningRequest request = new OwnerStoreProvisioningRequest();
        request.store_name = "Business Store";
        request.store_code = "BUSINESS_STORE";
        return request;
    }

    private OwnerStoreProvisioningResult result(Long storeId, boolean replayed) {
        return new OwnerStoreProvisioningResult(
            20L,
            storeId,
            "COMPLETED",
            replayed,
            "PASS",
            "BUSINESS_STORE_CREATED_LIVE",
            null,
            new OwnerStoreProvisioningCounts(2, 0, 0, 0, 1, 0, 1)
        );
    }

    private Store store(Long id, Long organizationId, String kind, String status, String lifecycle) {
        Store store = new Store();
        store.id = id;
        store.organization_id = organizationId;
        store.name = "Business Store";
        store.code = "BUSINESS_STORE";
        store.store_kind = kind;
        store.status = status;
        store.lifecycle_status = lifecycle;
        return store;
    }
}
