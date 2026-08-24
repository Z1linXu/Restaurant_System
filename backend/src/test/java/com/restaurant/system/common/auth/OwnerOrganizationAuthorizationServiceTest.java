package com.restaurant.system.common.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerOrganizationAuthorizationServiceTest {

    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private StoreRepository storeRepository;

    private OwnerOrganizationAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new OwnerOrganizationAuthorizationService(
            organizationMembershipRepository,
            organizationRepository,
            storeRepository
        );
        Organization active = new Organization();
        active.id = 100L;
        active.status = "active";
        lenient().when(organizationRepository.findById(100L)).thenReturn(Optional.of(active));
    }

    @Test
    void activeOwnerCanUseSourceStoreInsideOwnOrganization() {
        AuthenticatedUser owner = user(10L, "OWNER");
        Store sourceStore = store(20L, 100L);
        when(organizationMembershipRepository.findFirstByUserIdAndOrganizationId(10L, 100L))
            .thenReturn(Optional.of(ownerMembership(10L, 100L, "OWNER", true)));
        when(storeRepository.findById(20L)).thenReturn(Optional.of(sourceStore));

        Store authorizedStore = authorizationService.requireSourceStoreInOrganization(owner, 100L, 20L);

        assertEquals(20L, authorizedStore.id);
    }

    @Test
    void ownerFromAnotherOrganizationIsDenied() {
        AuthenticatedUser owner = user(10L, "OWNER");
        when(organizationRepository.findById(200L)).thenReturn(Optional.of(organization(200L, "active")));
        when(organizationMembershipRepository.findFirstByUserIdAndOrganizationId(10L, 200L))
            .thenReturn(Optional.of(ownerMembership(10L, 100L, "OWNER", true)));

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireActiveOwnerMembership(owner, 200L)
        );
    }

    @Test
    void arbitraryOwnerUsernameCanUseExactOrganizationMembership() {
        AuthenticatedUser owner = user(10L, "regional_owner_42", "OWNER");
        when(organizationMembershipRepository.findFirstByUserIdAndOrganizationId(10L, 100L))
            .thenReturn(Optional.of(ownerMembership(10L, 100L, "OWNER", true)));

        assertDoesNotThrow(
            () -> authorizationService.requireActiveOwnerMembership(owner, 100L)
        );
    }

    @Test
    void stagingPrefixedNonOwnerIsDeniedEvenWithMatchingOrganizationMembership() {
        AuthenticatedUser nonOwner = user(10L, "STG005_MANAGER_01", "MANAGER");

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireActiveOwnerMembership(nonOwner, 100L)
        );
    }

    @Test
    void frontdeskIsDeniedEvenWithMatchingOrganizationMembership() {
        AuthenticatedUser frontdesk = user(11L, "frontdesk", "FRONTDESK");

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireActiveOwnerMembership(frontdesk, 100L)
        );
    }

    @Test
    void stagingPrefixedOwnerFromWrongOrganizationIsDenied() {
        AuthenticatedUser owner = user(10L, "STG005_OWNER_WRONG_ORG", "OWNER");
        when(organizationRepository.findById(200L)).thenReturn(Optional.of(organization(200L, "active")));
        when(organizationMembershipRepository.findFirstByUserIdAndOrganizationId(10L, 200L))
            .thenReturn(Optional.empty());

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireActiveOwnerMembership(owner, 200L)
        );
    }

    @Test
    void sourceStoreOutsideTargetOrganizationIsDenied() {
        AuthenticatedUser owner = user(10L, "OWNER");
        when(organizationMembershipRepository.findFirstByUserIdAndOrganizationId(10L, 100L))
            .thenReturn(Optional.of(ownerMembership(10L, 100L, "OWNER", true)));
        when(storeRepository.findById(30L)).thenReturn(Optional.of(store(30L, 200L)));

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireSourceStoreInOrganization(owner, 100L, 30L)
        );
    }

    @Test
    void inactiveOwnerMembershipIsDenied() {
        AuthenticatedUser owner = user(10L, "OWNER");
        when(organizationMembershipRepository.findFirstByUserIdAndOrganizationId(10L, 100L))
            .thenReturn(Optional.of(ownerMembership(10L, 100L, "OWNER", false)));

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireActiveOwnerMembership(owner, 100L)
        );
    }

    @Test
    void inactiveOrganizationIsDeniedEvenWithActiveOwnerMembership() {
        AuthenticatedUser owner = user(10L, "OWNER");
        Organization inactive = new Organization();
        inactive.id = 100L;
        inactive.status = "inactive";
        when(organizationRepository.findById(100L)).thenReturn(Optional.of(inactive));

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireActiveOwnerMembership(owner, 100L)
        );
    }

    @Test
    void platformAdminIsNotAnImplicitOwnerOnboardingBypass() {
        AuthenticatedUser platformAdmin = user(99L, "ADMIN");

        assertThrows(
            ForbiddenException.class,
            () -> authorizationService.requireActiveOwnerMembership(platformAdmin, 100L)
        );
    }

    private AuthenticatedUser user(Long userId, String roleCode) {
        return new AuthenticatedUser(userId, null, userId, "user" + userId, "User " + userId, roleCode);
    }

    private AuthenticatedUser user(Long userId, String username, String roleCode) {
        return new AuthenticatedUser(userId, null, userId, username, "User " + userId, roleCode);
    }

    private OrganizationMembership ownerMembership(
        Long userId,
        Long organizationId,
        String roleCode,
        boolean active
    ) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.userId = userId;
        membership.organizationId = organizationId;
        membership.roleCode = roleCode;
        membership.isActive = active;
        return membership;
    }

    private Store store(Long storeId, Long organizationId) {
        Store store = new Store();
        store.id = storeId;
        store.organization_id = organizationId;
        return store;
    }

    private Organization organization(Long organizationId, String status) {
        Organization organization = new Organization();
        organization.id = organizationId;
        organization.status = status;
        return organization;
    }
}
