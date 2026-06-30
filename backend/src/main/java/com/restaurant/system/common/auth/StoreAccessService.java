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

@Service
public class StoreAccessService {

    private final RoleCapabilityRegistry roleCapabilityRegistry;
    private final OrganizationRepository organizationRepository;
    private final StoreRepository storeRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final StoreMembershipRepository storeMembershipRepository;

    public StoreAccessService(
        RoleCapabilityRegistry roleCapabilityRegistry,
        OrganizationRepository organizationRepository,
        StoreRepository storeRepository,
        OrganizationMembershipRepository organizationMembershipRepository,
        StoreMembershipRepository storeMembershipRepository
    ) {
        this.roleCapabilityRegistry = roleCapabilityRegistry;
        this.organizationRepository = organizationRepository;
        this.storeRepository = storeRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.storeMembershipRepository = storeMembershipRepository;
    }

    public boolean canAccessStore(AuthenticatedUser user, Long storeId) {
        if (user == null || storeId == null) {
            return false;
        }
        if (roleCapabilityRegistry.isAdmin(user.roleCode())) {
            return true;
        }

        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            return false;
        }

        if (roleCapabilityRegistry.isOwner(user.roleCode())) {
            if (store.organization_id != null
                && organizationMembershipRepository.existsByUserIdAndOrganizationIdAndIsActiveTrue(user.userId(), store.organization_id)) {
                return true;
            }
            return hasLegacyDefaultStoreFallback(user, storeId);
        }

        if (storeMembershipRepository.existsByUserIdAndStoreIdAndIsActiveTrue(user.userId(), storeId)) {
            return true;
        }
        return hasLegacyDefaultStoreFallback(user, storeId);
    }

    public void requireStoreAccess(AuthenticatedUser user, Long storeId) {
        if (!canAccessStore(user, storeId)) {
            throw new ForbiddenException("Access denied for store " + storeId);
        }
    }

    public List<Store> accessibleStores(AuthenticatedUser user) {
        if (user == null) {
            return List.of();
        }
        if (roleCapabilityRegistry.isAdmin(user.roleCode())) {
            return storeRepository.findAll().stream()
                .sorted(Comparator.comparing((Store store) -> store.id == null ? Long.MAX_VALUE : store.id))
                .toList();
        }

        Map<Long, Store> storesById = new LinkedHashMap<>();
        for (StoreMembership membership : storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(user.userId())) {
            if (membership.storeId == null) {
                continue;
            }
            storeRepository.findById(membership.storeId).ifPresent(store -> storesById.put(store.id, store));
        }

        if (roleCapabilityRegistry.isOwner(user.roleCode())) {
            for (OrganizationMembership membership : organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(user.userId())) {
                if (membership.organizationId == null) {
                    continue;
                }
                for (Store store : storeRepository.findAllByOrganizationIdOrderByIdAsc(membership.organizationId)) {
                    storesById.put(store.id, store);
                }
            }
        }

        if (storesById.isEmpty() && user.storeId() != null && !hasAnyActiveMembership(user.userId())) {
            storeRepository.findById(user.storeId()).ifPresent(store -> storesById.put(store.id, store));
        }

        return List.copyOf(storesById.values());
    }

    public List<Organization> accessibleOrganizations(AuthenticatedUser user) {
        if (user == null) {
            return List.of();
        }
        if (roleCapabilityRegistry.isAdmin(user.roleCode())) {
            return organizationRepository.findAllByOrderByIdAsc();
        }

        Map<Long, Organization> organizationsById = new LinkedHashMap<>();
        for (OrganizationMembership membership : organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(user.userId())) {
            if (membership.organizationId == null) {
                continue;
            }
            organizationRepository.findById(membership.organizationId)
                .ifPresent(organization -> organizationsById.put(organization.id, organization));
        }
        for (Store store : accessibleStores(user)) {
            if (store.organization_id == null || organizationsById.containsKey(store.organization_id)) {
                continue;
            }
            organizationRepository.findById(store.organization_id)
                .ifPresent(organization -> organizationsById.put(organization.id, organization));
        }
        return List.copyOf(organizationsById.values());
    }

    public String roleCodeForStore(AuthenticatedUser user, Store store) {
        if (user == null || store == null) {
            return null;
        }
        if (roleCapabilityRegistry.isAdmin(user.roleCode())) {
            return user.roleCode();
        }
        String storeRole = storeMembershipRepository.findFirstByUserIdAndStoreId(user.userId(), store.id)
            .filter(membership -> Boolean.TRUE.equals(membership.isActive))
            .map(membership -> membership.roleCode)
            .filter(roleCode -> roleCode != null && !roleCode.isBlank())
            .orElse(null);
        if (storeRole != null) {
            return storeRole;
        }
        if (store.organization_id != null) {
            String organizationRole = roleCodeForOrganization(user, store.organization_id);
            if (organizationRole != null) {
                return organizationRole;
            }
        }
        return user.roleCode();
    }

    public String roleCodeForOrganization(AuthenticatedUser user, Long organizationId) {
        if (user == null || organizationId == null) {
            return null;
        }
        if (roleCapabilityRegistry.isAdmin(user.roleCode())) {
            return user.roleCode();
        }
        return organizationMembershipRepository.findFirstByUserIdAndOrganizationId(user.userId(), organizationId)
            .filter(membership -> Boolean.TRUE.equals(membership.isActive))
            .map(membership -> membership.roleCode)
            .filter(roleCode -> roleCode != null && !roleCode.isBlank())
            .orElse(null);
    }

    private boolean hasLegacyDefaultStoreFallback(AuthenticatedUser user, Long storeId) {
        return !hasAnyActiveMembership(user.userId()) && user.storeId() != null && user.storeId().equals(storeId);
    }

    private boolean hasAnyActiveMembership(Long userId) {
        return !storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(userId).isEmpty()
            || !organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(userId).isEmpty();
    }
}
