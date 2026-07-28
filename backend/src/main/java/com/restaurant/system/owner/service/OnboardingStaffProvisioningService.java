package com.restaurant.system.owner.service;

/**
 * Creates the human identity records required for a newly provisioned store.
 *
 * <p>This component is intentionally internal to the onboarding transaction.
 * It does not expose, log, or retain the supplied password after credential
 * hashing.</p>
 */
public interface OnboardingStaffProvisioningService {

    ProvisionedStoreStaff provision(OnboardingStaffProvisioningCommand command);
}
