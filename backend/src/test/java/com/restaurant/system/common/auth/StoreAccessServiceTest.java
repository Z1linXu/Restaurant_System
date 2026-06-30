package com.restaurant.system.common.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreAccessServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;
    @Mock
    private StoreMembershipRepository storeMembershipRepository;

    private StoreAccessService storeAccessService;

    @BeforeEach
    void setUp() {
        storeAccessService = new StoreAccessService(
            new RoleCapabilityRegistry(),
            organizationRepository,
            storeRepository,
            organizationMembershipRepository,
            storeMembershipRepository
        );
    }

    @Test
    void staffCanAccessActiveStoreMembershipOnly() {
        AuthenticatedUser staff = user(1L, 10L, "FRONTDESK");
        Store store = store(10L, 100L);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(storeMembershipRepository.existsByUserIdAndStoreIdAndIsActiveTrue(1L, 10L)).thenReturn(true);

        assertTrue(storeAccessService.canAccessStore(staff, 10L));

        Store otherStore = store(20L, 200L);
        when(storeRepository.findById(20L)).thenReturn(Optional.of(otherStore));
        when(storeMembershipRepository.existsByUserIdAndStoreIdAndIsActiveTrue(1L, 20L)).thenReturn(false);
        when(storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(1L))
            .thenReturn(List.of(storeMembership(1L, 10L, "FRONTDESK")));

        assertFalse(storeAccessService.canAccessStore(staff, 20L));
    }

    @Test
    void ownerCanAccessStoresInsideOrganizationMembership() {
        AuthenticatedUser owner = user(2L, 10L, "OWNER");
        Store store = store(11L, 100L);
        when(storeRepository.findById(11L)).thenReturn(Optional.of(store));
        when(organizationMembershipRepository.existsByUserIdAndOrganizationIdAndIsActiveTrue(2L, 100L)).thenReturn(true);

        assertTrue(storeAccessService.canAccessStore(owner, 11L));

        Store otherStore = store(22L, 200L);
        when(storeRepository.findById(22L)).thenReturn(Optional.of(otherStore));
        when(organizationMembershipRepository.existsByUserIdAndOrganizationIdAndIsActiveTrue(2L, 200L)).thenReturn(false);
        when(storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(2L)).thenReturn(List.of());
        when(organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(2L))
            .thenReturn(List.of(organizationMembership(2L, 100L, "OWNER")));

        assertFalse(storeAccessService.canAccessStore(owner, 22L));
    }

    @Test
    void adminCanAccessAnyStore() {
        assertTrue(storeAccessService.canAccessStore(user(3L, null, "ADMIN"), 999L));
    }

    @Test
    void accessibleStoresUsesLegacyDefaultOnlyWhenNoMembershipExists() {
        AuthenticatedUser user = user(4L, 10L, "FRONTDESK");
        Store defaultStore = store(10L, 100L);

        when(storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(4L)).thenReturn(List.of());
        when(organizationMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(4L)).thenReturn(List.of());
        when(storeRepository.findById(10L)).thenReturn(Optional.of(defaultStore));

        assertTrue(storeAccessService.accessibleStores(user).stream().anyMatch(store -> Long.valueOf(10L).equals(store.id)));

        when(storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(4L))
            .thenReturn(List.of(storeMembership(4L, 20L, "FRONTDESK")));
        when(storeRepository.findById(20L)).thenReturn(Optional.empty());

        assertTrue(storeAccessService.accessibleStores(user).isEmpty());
    }

    private AuthenticatedUser user(Long userId, Long storeId, String roleCode) {
        return new AuthenticatedUser(userId, storeId, userId, "user" + userId, "User " + userId, roleCode);
    }

    private Store store(Long storeId, Long organizationId) {
        Store store = new Store();
        store.id = storeId;
        store.organization_id = organizationId;
        store.status = "active";
        return store;
    }

    private StoreMembership storeMembership(Long userId, Long storeId, String roleCode) {
        StoreMembership membership = new StoreMembership();
        membership.userId = userId;
        membership.storeId = storeId;
        membership.roleCode = roleCode;
        membership.isActive = true;
        return membership;
    }

    private OrganizationMembership organizationMembership(Long userId, Long organizationId, String roleCode) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.userId = userId;
        membership.organizationId = organizationId;
        membership.roleCode = roleCode;
        membership.isActive = true;
        return membership;
    }
}
