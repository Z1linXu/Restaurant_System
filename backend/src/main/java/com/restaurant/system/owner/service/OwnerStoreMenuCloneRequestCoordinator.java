package com.restaurant.system.owner.service;

public interface OwnerStoreMenuCloneRequestCoordinator {

    OwnerStoreMenuCloneReservation reserve(OwnerStoreMenuCloneReservationCommand command);

    OwnerStoreMenuCloneReservation complete(OwnerStoreMenuCloneSuccessEvidence evidence);

    OwnerStoreMenuCloneReservation fail(OwnerStoreMenuCloneFailureEvidence evidence);
}
