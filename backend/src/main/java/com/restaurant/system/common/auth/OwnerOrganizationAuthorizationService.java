package com.restaurant.system.common.auth;

import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import org.springframework.stereotype.Service;

/**
 * Authorization boundary for owner-scoped store onboarding.
 *
 * <p>This deliberately does not use the platform ADMIN bypass. Onboarding a
 * store for an organization requires an active OWNER membership in that exact
 * organization.</p>
 */
@Service
public class OwnerOrganizationAuthorizationService {

    private static final String OWNER_ROLE_CODE = "OWNER";

    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final OrganizationRepository organizationRepository;
    private final StoreRepository storeRepository;

    public OwnerOrganizationAuthorizationService(
        OrganizationMembershipRepository organizationMembershipRepository,
        OrganizationRepository organizationRepository,
        StoreRepository storeRepository
    ) {
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.organizationRepository = organizationRepository;
        this.storeRepository = storeRepository;
    }

    public void requireActiveOwnerMembership(AuthenticatedUser actor, Long organizationId) {
        if (actor == null || actor.userId() == null || organizationId == null) {
            throw new ForbiddenException("Access denied. Active owner membership is required");
        }

        // ADMIN is intentionally not an implicit owner-onboarding bypass.
        if (!OWNER_ROLE_CODE.equalsIgnoreCase(actor.roleCode())) {
            throw new ForbiddenException("Access denied. Owner role is required for organization onboarding");
        }
        boolean activeOrganization = organizationRepository.findById(organizationId)
            .map(organization -> "active".equalsIgnoreCase(organization.status))
            .orElse(false);
        if (!activeOrganization) {
            throw new ForbiddenException("Access denied. Active organization is required for Store creation");
        }

        boolean hasActiveOwnerMembership = organizationMembershipRepository
            .findFirstByUserIdAndOrganizationId(actor.userId(), organizationId)
            .filter(membership -> Boolean.TRUE.equals(membership.isActive))
            .filter(membership -> organizationId.equals(membership.organizationId))
            .map(membership -> membership.roleCode)
            .filter(OWNER_ROLE_CODE::equalsIgnoreCase)
            .isPresent();
        if (!hasActiveOwnerMembership) {
            throw new ForbiddenException("Access denied. Active owner membership is required for organization onboarding");
        }
    }

    public Store requireSourceStoreInOrganization(
        AuthenticatedUser actor,
        Long targetOrganizationId,
        Long sourceStoreId
    ) {
        requireActiveOwnerMembership(actor, targetOrganizationId);
        if (sourceStoreId == null) {
            throw new ForbiddenException("Access denied. Source store is required for organization onboarding");
        }

        Store sourceStore = storeRepository.findById(sourceStoreId)
            .orElseThrow(() -> new ForbiddenException("Access denied. Source store is not available"));
        if (!targetOrganizationId.equals(sourceStore.organization_id)) {
            throw new ForbiddenException("Access denied. Source store belongs to a different organization");
        }
        return sourceStore;
    }
}
