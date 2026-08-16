package com.restaurant.system.owner.provisioning;

public interface OwnerStoreProvisioningRequestCoordinator {

    OwnerStoreProvisioningReservation reserve(ResolvedOwnerStoreProvisioningInput input);

    OwnerStoreProvisioningReservation complete(OwnerStoreProvisioningSuccessEvidence evidence);

    OwnerStoreProvisioningReservation fail(OwnerStoreProvisioningFailureEvidence evidence);
}
