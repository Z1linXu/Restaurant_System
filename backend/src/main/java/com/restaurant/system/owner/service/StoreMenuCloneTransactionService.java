package com.restaurant.system.owner.service;

public interface StoreMenuCloneTransactionService {

    OwnerStoreMenuCloneValidationResult validate(OwnerStoreMenuCloneValidationCommand command);

    OwnerStoreMenuCloneTransactionResult execute(OwnerStoreMenuCloneTransactionCommand command);
}
