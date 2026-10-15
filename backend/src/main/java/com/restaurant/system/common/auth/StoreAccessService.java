package com.restaurant.system.common.auth;

import com.restaurant.system.user.entity.Store;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Business scope is explicit; platform ADMIN is never an implicit merchant grant. */
@Service
public class StoreAccessService {
    private final RoleCapabilityRegistry roleCapabilityRegistry;
    private final OrganizationRepository organizationRepository;
    private final StoreRepository storeRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final StoreMembershipRepository storeMembershipRepository;

    public StoreAccessService(RoleCapabilityRegistry roles, OrganizationRepository organizations,
        StoreRepository stores, OrganizationMembershipRepository organizationsMemberships,
        StoreMembershipRepository storeMemberships) {
        this.roleCapabilityRegistry = roles;
        this.organizationRepository = organizations;
        this.storeRepository = stores;
        this.organizationMembershipRepository = organizationsMemberships;
        this.storeMembershipRepository = storeMemberships;
    }

    public boolean canAccessStore(AuthenticatedUser user, Long storeId) {
        return user != null && storeId != null && storeRepository.findById(storeId)
            .map(store -> roleCodeForStore(user, store) != null).orElse(false);
    }

    public void requireStoreAccess(AuthenticatedUser user, Long storeId) {
        if (!canAccessStore(user, storeId)) throw new ForbiddenException("Access denied for store");
    }

    public List<Store> accessibleStores(AuthenticatedUser user) {
        if (!businessUser(user)) return List.of();
        Map<Long, Store> candidates = new LinkedHashMap<>();
        for (StoreMembership membership : storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(user.userId())) {
            if (membership.storeId != null) storeRepository.findById(membership.storeId)
                .ifPresent(store -> candidates.put(store.id, store));
        }
        if (roleCapabilityRegistry.isOwner(user.roleCode())) {
            for (OrganizationMembership membership : organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(user.userId())) {
                if (membership.organizationId != null && "OWNER".equalsIgnoreCase(membership.roleCode)) {
                    for (Store store : storeRepository.findAllByOrganizationIdOrderByIdAsc(membership.organizationId))
                        candidates.put(store.id, store);
                }
            }
        }
        if (hasLegacyDefaultStoreFallback(user, user.storeId())) storeRepository.findById(user.storeId())
            .ifPresent(store -> candidates.put(store.id, store));
        return candidates.values().stream().filter(store -> roleCodeForStore(user, store) != null)
            .sorted(Comparator.comparing(store -> store.id)).toList();
    }

    public List<Organization> accessibleOrganizations(AuthenticatedUser user) {
        if (!businessUser(user)) return List.of();
        Map<Long, Organization> result = new LinkedHashMap<>();
        for (OrganizationMembership membership : organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(user.userId())) {
            if (roleCodeForOrganization(user, membership.organizationId) != null)
                organizationRepository.findById(membership.organizationId).ifPresent(org -> result.put(org.id, org));
        }
        for (Store store : accessibleStores(user)) {
            if (store.organization_id != null) organizationRepository.findById(store.organization_id)
                .ifPresent(org -> result.put(org.id, org));
        }
        return List.copyOf(result.values());
    }

    public String roleCodeForStore(AuthenticatedUser user, Store store) {
        if (!businessUser(user) || store == null || store.id == null) return null;
        if (store.organization_id != null && !organizationRepository.findById(store.organization_id)
            .map(org -> "active".equalsIgnoreCase(org.status)).orElse(false)) return null;
        if ("OWNER".equals(roleCodeForOrganization(user, store.organization_id))) return "OWNER";
        StoreMembership membership = storeMembershipRepository.findFirstByUserIdAndStoreId(user.userId(), store.id).orElse(null);
        if (membership != null) {
            if (!Boolean.TRUE.equals(membership.isActive) || !java.util.Objects.equals(store.organization_id, membership.organizationId)) return null;
            return narrowerRole(user.roleCode(), membership.roleCode);
        }
        return hasLegacyDefaultStoreFallback(user, store.id) ? user.roleCode().toUpperCase() : null;
    }

    public String roleCodeForOrganization(AuthenticatedUser user, Long organizationId) {
        if (!businessUser(user) || organizationId == null || !roleCapabilityRegistry.isOwner(user.roleCode())) return null;
        boolean activeOrg = organizationRepository.findById(organizationId)
            .map(org -> "active".equalsIgnoreCase(org.status)).orElse(false);
        if (!activeOrg) return null;
        return organizationMembershipRepository.findFirstByUserIdAndOrganizationId(user.userId(), organizationId)
            .filter(member -> Boolean.TRUE.equals(member.isActive) && organizationId.equals(member.organizationId))
            .filter(member -> "OWNER".equalsIgnoreCase(member.roleCode)).map(member -> "OWNER").orElse(null);
    }

    /** Return the weaker preset. Incomparable/conflicting presets fail closed. */
    private String narrowerRole(String globalRole, String scopeRole) {
        var global = roleCapabilityRegistry.getCapabilities(globalRole);
        var scoped = roleCapabilityRegistry.getCapabilities(scopeRole);
        if (global.isEmpty() || scoped.isEmpty()) return null;
        if (global.containsAll(scoped)) return scopeRole.toUpperCase();
        if (scoped.containsAll(global)) return globalRole.toUpperCase();
        return null;
    }

    private boolean businessUser(AuthenticatedUser user) {
        return user != null && user.userId() != null && !roleCapabilityRegistry.isAdmin(user.roleCode())
            && !roleCapabilityRegistry.getCapabilities(user.roleCode()).isEmpty();
    }

    private boolean hasLegacyDefaultStoreFallback(AuthenticatedUser user, Long storeId) {
        return businessUser(user) && storeId != null && storeId.equals(user.storeId())
            && !storeMembershipRepository.existsByUserId(user.userId())
            && !organizationMembershipRepository.existsByUserId(user.userId());
    }

    public boolean hasCapabilityForStore(AuthenticatedUser user, Store store, Capability... capabilities) {
        String role=roleCodeForStore(user,store);
        return role != null && java.util.Arrays.stream(capabilities)
            .anyMatch(capability -> roleCapabilityRegistry.hasCapability(role,capability));
    }

    public AuthenticatedUser forStore(AuthenticatedUser user, Long storeId) {
        Store store = storeId == null ? null : storeRepository.findById(storeId).orElse(null);
        String role = roleCodeForStore(user, store);
        if (role == null) throw new ForbiddenException("Access denied for store");
        return new AuthenticatedUser(user.userId(), storeId, user.roleId(), user.username(), user.fullName(), role);
    }

    /** Account mutations must include dormant grants that a new global role could revive. */
    public void requireManageIdentity(AuthenticatedUser actor, Long targetUserId) {
        if (!businessUser(actor) || targetUserId == null) throw new ForbiddenException("Shared account is outside the authorized staff scope");
        for (StoreMembership member : storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(targetUserId)) {
            Store store = member.storeId == null ? null : storeRepository.findById(member.storeId).orElse(null);
            if (store == null || !java.util.Objects.equals(store.organization_id, member.organizationId)
                || !hasCapabilityForStore(actor, store, Capability.ADMIN_USER_ROLE_MANAGE)) {
                throw new ForbiddenException("Shared account is outside the authorized staff scope");
            }
        }
        for (OrganizationMembership member : organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(targetUserId)) {
            boolean owner = "OWNER".equals(roleCodeForOrganization(actor, member.organizationId));
            boolean staffScope = member.organizationId != null && !"OWNER".equalsIgnoreCase(member.roleCode)
                && accessibleStores(actor).stream().anyMatch(store -> member.organizationId.equals(store.organization_id)
                    && hasCapabilityForStore(actor, store, Capability.ADMIN_USER_ROLE_MANAGE));
            if (!owner && !staffScope) throw new ForbiddenException("Shared account is outside the authorized staff scope");
        }
    }
}
