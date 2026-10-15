package com.restaurant.system.owner.profile;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Root Profile authorization is inherited by every version and artifact. */
@Service
public class StoreProfileAccessService {
    private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations;

    public StoreProfileAccessService(OrganizationMembershipRepository memberships, OrganizationRepository organizations) {
        this.memberships = memberships;
        this.organizations = organizations;
    }

    public boolean canRead(AuthenticatedUser actor, StoreProfileEntity profile) {
        if (actor == null || actor.userId() == null || !"OWNER".equalsIgnoreCase(actor.roleCode()) || profile == null) {
            return false;
        }
        return memberships.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(actor.userId()).stream()
            .filter(m -> "OWNER".equalsIgnoreCase(m.roleCode))
            .filter(m -> Boolean.TRUE.equals(m.isActive))
            .filter(m -> availableForOrganization(profile, m.organizationId))
            .anyMatch(m -> organizations.findById(m.organizationId)
                .map(o -> "active".equalsIgnoreCase(o.status)).orElse(false));
    }

    public void requireRead(AuthenticatedUser actor, StoreProfileEntity profile) {
        if (!canRead(actor, profile)) throw unavailable();
    }

    /** Internal provisioning callers must authorize the exact Organization first. */
    public static void requireUse(StoreProfileEntity profile, Long organizationId) {
        if (!availableForOrganization(profile, organizationId)) throw unavailable();
    }

    private static boolean availableForOrganization(StoreProfileEntity profile, Long organizationId) {
        if (profile == null || organizationId == null) return false;
        if ("PUBLIC_PRESET".equals(profile.visibility)) return profile.owner_organization_id == null
            && java.util.List.of("PUBLISHED", "REVIEWED", "READY").contains(profile.status == null ? "" : profile.status);
        return "PRIVATE".equals(profile.visibility) && profile.owner_organization_id != null
            && Objects.equals(profile.owner_organization_id, organizationId);
    }

    private static ForbiddenException unavailable() {
        return new ForbiddenException("Store Profile is not available");
    }
}
