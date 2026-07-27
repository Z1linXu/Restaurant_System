package com.restaurant.system.owner.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.impl.PasswordServiceImpl;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.RoleCapabilityRegistry;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningCommand;
import com.restaurant.system.owner.service.ProvisionedStoreStaff;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingStaffProvisioningServiceImplTest {

    private static final Long ORGANIZATION_ID = 100L;
    private static final Long TARGET_STORE_ID = 200L;
    private static final Long OTHER_STORE_ID = 201L;
    private static final Long USER_ID = 300L;
    private static final String SYNTHETIC_PASSWORD = "SyntheticOnly!Pass9";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private StoreMembershipRepository storeMembershipRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;
    @Captor
    private ArgumentCaptor<User> userCaptor;
    @Captor
    private ArgumentCaptor<UserCredential> credentialCaptor;
    @Captor
    private ArgumentCaptor<StoreMembership> membershipCaptor;

    private OnboardingStaffProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OnboardingStaffProvisioningServiceImpl(
            userRepository,
            userCredentialRepository,
            storeMembershipRepository,
            storeRepository,
            roleRepository,
            new PasswordServiceImpl()
        );
    }

    @Test
    void createsBcryptCredentialAndExactlyOneActiveTargetStoreMembership() {
        configureSuccessfulProvisioning();

        ProvisionedStoreStaff result = service.provision(command("staff-ct-test", "FRONTDESK"));

        verify(userRepository).save(userCaptor.capture());
        verify(userCredentialRepository).save(credentialCaptor.capture());
        verify(storeMembershipRepository, times(1)).save(membershipCaptor.capture());

        User savedUser = userCaptor.getValue();
        UserCredential credential = credentialCaptor.getValue();
        StoreMembership membership = membershipCaptor.getValue();

        assertEquals(USER_ID, result.userId());
        assertEquals(TARGET_STORE_ID, savedUser.getStore_id());
        assertEquals(TARGET_STORE_ID, membership.storeId);
        assertEquals(ORGANIZATION_ID, membership.organizationId);
        assertEquals(USER_ID, membership.userId);
        assertEquals("FRONTDESK", membership.roleCode);
        assertTrue(membership.isActive);
        assertEquals("BCRYPT", credential.passwordAlgorithm);
        assertNotEquals(SYNTHETIC_PASSWORD, credential.passwordHash);
        assertFalse(credential.passwordHash.contains(SYNTHETIC_PASSWORD));
        assertTrue(new PasswordServiceImpl().matches(SYNTHETIC_PASSWORD, credential.passwordHash));
        assertFalse(command("staff-ct-test", "FRONTDESK").toString().contains(SYNTHETIC_PASSWORD));
    }

    @Test
    void grantsWorkspaceAndStoreAccessToTargetStoreOnly() {
        configureSuccessfulProvisioning();
        service.provision(command("staff-ct-test", "FRONTDESK"));
        verify(storeMembershipRepository).save(membershipCaptor.capture());
        StoreMembership targetMembership = membershipCaptor.getValue();

        Store targetStore = store(TARGET_STORE_ID, ORGANIZATION_ID);
        Store otherStore = store(OTHER_STORE_ID, ORGANIZATION_ID);
        when(storeRepository.findById(TARGET_STORE_ID)).thenReturn(Optional.of(targetStore));
        when(storeRepository.findById(OTHER_STORE_ID)).thenReturn(Optional.of(otherStore));
        when(storeMembershipRepository.existsByUserIdAndStoreIdAndIsActiveTrue(USER_ID, TARGET_STORE_ID)).thenReturn(true);
        when(storeMembershipRepository.existsByUserIdAndStoreIdAndIsActiveTrue(USER_ID, OTHER_STORE_ID)).thenReturn(false);
        when(storeMembershipRepository.findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(USER_ID))
            .thenReturn(List.of(targetMembership));

        StoreAccessService storeAccessService = new StoreAccessService(
            new RoleCapabilityRegistry(),
            organizationRepository,
            storeRepository,
            organizationMembershipRepository,
            storeMembershipRepository
        );
        AuthenticatedUser staff = new AuthenticatedUser(
            USER_ID,
            TARGET_STORE_ID,
            10L,
            "staff-ct-test",
            "Synthetic Staff",
            "FRONTDESK"
        );

        assertEquals(List.of(TARGET_STORE_ID), storeAccessService.accessibleStores(staff).stream().map(store -> store.id).toList());
        assertTrue(storeAccessService.canAccessStore(staff, TARGET_STORE_ID));
        assertFalse(storeAccessService.canAccessStore(staff, OTHER_STORE_ID));
    }

    @Test
    void rejectsTargetStoreFromAnotherOrganizationWithoutPersistingCredentials() {
        Store otherOrganizationStore = store(TARGET_STORE_ID, ORGANIZATION_ID + 1);
        when(storeRepository.findById(TARGET_STORE_ID)).thenReturn(Optional.of(otherOrganizationStore));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.provision(command("staff-ct-test", "FRONTDESK"))
        );

        assertFalse(exception.getMessage().contains(SYNTHETIC_PASSWORD));
        verifyNoInteractions(userRepository, userCredentialRepository, storeMembershipRepository, roleRepository);
    }

    private void configureSuccessfulProvisioning() {
        when(storeRepository.findById(TARGET_STORE_ID)).thenReturn(Optional.of(store(TARGET_STORE_ID, ORGANIZATION_ID)));
        when(userRepository.findFirstByUsernameIgnoreCase("staff-ct-test")).thenReturn(Optional.empty());
        when(userCredentialRepository.existsByLoginIdentifierIgnoreCase("staff-ct-test")).thenReturn(false);
        when(roleRepository.findFirstByCodeIgnoreCase(eq("FRONTDESK"))).thenReturn(Optional.of(role(10L, "FRONTDESK")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(USER_ID);
            return user;
        });
    }

    private OnboardingStaffProvisioningCommand command(String loginIdentifier, String roleCode) {
        return new OnboardingStaffProvisioningCommand(
            ORGANIZATION_ID,
            TARGET_STORE_ID,
            roleCode,
            loginIdentifier,
            "Synthetic Staff",
            SYNTHETIC_PASSWORD
        );
    }

    private Store store(Long id, Long organizationId) {
        Store store = new Store();
        store.id = id;
        store.organization_id = organizationId;
        store.status = "active";
        return store;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }
}
